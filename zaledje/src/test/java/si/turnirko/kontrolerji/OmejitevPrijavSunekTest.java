/* Sunek hkratnih zahtev z ISTIMI zastarelimi poverilnicami (zavihek, v katerem je
   geslo ze spremenjeno drugje: stran naloži ~10 poizvedb naenkrat) ne sme
   zakleniti racuna, ugibanje gesla (RAZLICNA gesla) pa ga se vedno.

   Test tece brez transakcije: zahteve gredo vzporedno iz vec niti, vsaka pa mora
   racun videti, zato ga je treba v bazo zapisati zares (in ga potem pobrisati). */
package si.turnirko.kontrolerji;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.UporabnikRepozitorij;
import si.turnirko.storitve.OmejevalnikPoskusov;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OmejitevPrijavSunekTest {

    private static final String GESLO = "pravoGeslo123";
    private static final String POT = "/api/v1/auth/me";

    @Autowired MockMvc mockMvc;
    @Autowired UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired PasswordEncoder kodirnik;
    @Autowired OmejevalnikPoskusov omejevalnik;

    private final String oznaka = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private final List<Uporabnik> racuni = new ArrayList<>();

    @BeforeEach
    void pripravi() {
        omejevalnik.pocistiVse();
    }

    @AfterEach
    void pospravi() {
        omejevalnik.pocistiVse();
        uporabnikRepozitorij.deleteAll(racuni);
    }

    @Test
    void sunekIstegaNapacnegaGeslaRacunaNeZaklene() throws Exception {
        String ime = racun("sunek");

        // 12 hkratnih zahtev z istim napacnim geslom (vec kot meja 10 za racun z naslova)
        List<Integer> izidi = hkrati(12, i -> ime, i -> "zastarelo");
        assertEquals(List.of(401, 401, 401, 401, 401, 401, 401, 401, 401, 401, 401, 401), izidi,
                "sunek je prisel do preverbe gesla, nobena zahteva ni bila zavrnjena z 429");

        // lastnik z istega naslova se z novim, pravilnim geslom prijavi
        assertEquals(200, stanje(ime, GESLO));
    }

    /* Sunek se steje enkrat, ne nic: devet RAZLICNIH napacnih gesel zraven ga
       pripelje do meje 10. */
    @Test
    void sunekStejeKotEnPoskusUgibanjaOstaleStejejoPoStarem() throws Exception {
        String ime = racun("stejeEnkrat");

        hkrati(12, i -> ime, i -> "zastarelo");
        for (int i = 0; i < 8; i++) {
            assertEquals(401, stanje(ime, "ugibanje" + i));
        }
        // sunek (1) + 8 razlicnih = 9 < 10: pravo geslo se vedno pride skozi ...
        assertEquals(200, stanje(ime, GESLO));

        // ... uspesna prijava je stevec pobrisala; znova: sunek (1) + 9 razlicnih = 10
        hkrati(12, i -> ime, i -> "zastarelo");
        for (int i = 0; i < 9; i++) {
            assertEquals(401, stanje(ime, "ugibanje" + i));
        }
        assertEquals(429, stanje(ime, GESLO), "10 razlicnih poskusov racun se vedno zaklene");
    }

    @Test
    void desetRazlicnihNapacnihGeselVednoZaklene() throws Exception {
        String ime = racun("ugibanje");

        List<Integer> izidi = hkrati(10, i -> ime, i -> "ugibanje" + i);
        assertEquals(List.of(401, 401, 401, 401, 401, 401, 401, 401, 401, 401), izidi);

        assertEquals(429, stanje(ime, GESLO));
    }

    /* Meja za naslov IP ostane: ponavljanje istega gesla je sicer brez
       informacije za ugibanje, a vsaka zavrnitev stane BCrypt, zato se za naslov
       steje vsaka. */
    @Test
    void ponavljanjeIstegaGeslaSeVednoStejeZaNaslovIp() throws Exception {
        String ime = racun("naslov");
        String drug = racun("drugRacun");

        for (int i = 0; i < 50; i++) {
            assertEquals(401, stanje(ime, "zastarelo"));
        }
        assertEquals(429, stanje(drug, GESLO), "naslov s 50 neuspehi je blokiran za vse racune");
    }

    /* Meja za racun z vseh naslovov ostane: isto geslo z vsakega naslova se steje
       (za racun z naslova je to ena zahteva, a skupni stevec racuna steje vse). */
    @Test
    void skupnaMejaRacunaOstaneKotJe() throws Exception {
        String ime = racun("skupna");

        for (int i = 0; i < 100; i++) {
            String naslov = "10.1." + (i / 5) + "." + i;
            assertEquals(401, mockMvc.perform(get(POT)
                            .with(z -> { z.setRemoteAddr(naslov); return z; })
                            .header("Authorization", basic(ime, "zastarelo")))
                    .andReturn().getResponse().getStatus());
        }
        assertEquals(429, mockMvc.perform(get(POT)
                        .with(z -> { z.setRemoteAddr("10.200.0.1"); return z; })
                        .header("Authorization", basic(ime, GESLO)))
                .andReturn().getResponse().getStatus());
    }

    private String racun(String predpona) {
        String ime = predpona + "-" + oznaka + "@test.si";
        Uporabnik u = new Uporabnik(ime, kodirnik.encode(GESLO), Vloga.NAVIJAC);
        u.setStatus(StatusRacuna.POTRJEN);
        u.setAktiven(true);
        u.setEmailPotrjenOb(LocalDateTime.now());
        racuni.add(uporabnikRepozitorij.save(u));
        return ime;
    }

    private int stanje(String ime, String geslo) throws Exception {
        return mockMvc.perform(get(POT).header("Authorization", basic(ime, geslo)))
                .andReturn().getResponse().getStatus();
    }

    /* n zahtev naenkrat (vsi se sprostijo s skupne zapore); vrne stanja po vrsti. */
    private List<Integer> hkrati(int n, java.util.function.IntFunction<String> ime,
                                 java.util.function.IntFunction<String> geslo) throws Exception {
        ExecutorService niti = Executors.newFixedThreadPool(n);
        try {
            CountDownLatch pripravljeni = new CountDownLatch(n);
            CountDownLatch zacetek = new CountDownLatch(1);
            List<Future<Integer>> rezultati = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                int k = i;
                Callable<Integer> zahteva = () -> {
                    pripravljeni.countDown();
                    zacetek.await();
                    return stanje(ime.apply(k), geslo.apply(k));
                };
                rezultati.add(niti.submit(zahteva));
            }
            pripravljeni.await();
            zacetek.countDown();
            List<Integer> izidi = new ArrayList<>();
            for (Future<Integer> r : rezultati) {
                izidi.add(r.get());
            }
            return izidi;
        } finally {
            niti.shutdownNow();
        }
    }

    private static String basic(String ime, String geslo) {
        return "Basic " + Base64.getEncoder().encodeToString(
                (ime + ":" + geslo).getBytes(StandardCharsets.UTF_8));
    }
}
