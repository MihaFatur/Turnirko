/* Razveljavitev zapisnika (DELETE /srecanja/{id}/zapisnik) skozi varnostno
   verigo: gost je ne sme, organizator, ki lige ni ustvaril, tudi ne, admin pa
   srecanje vrne v razpored. Storitvena pravila so v RazveljavitevZapisnikaTest;
   tu je pot in to, da je veriga ne pogoltne. */
package si.turnirko.kontrolerji;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import si.turnirko.dto.EkipaVnos;
import si.turnirko.dto.KaderVnos;
import si.turnirko.dto.LigaVnos;
import si.turnirko.dto.PostavaVnos;
import si.turnirko.dto.SrecanjePodrobnoDto;
import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Klub;
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.modeli.Spol;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.StatusSrecanja;
import si.turnirko.modeli.StranEkipe;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.IgralecRepozitorij;
import si.turnirko.repozitoriji.KlubRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;
import si.turnirko.storitve.LigaStoritev;
import si.turnirko.storitve.SrecanjeStoritev;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RazveljavitevZapisnikaDostopTest {

    private static final String GESLO = "preizkusnoGeslo123";

    @Autowired MockMvc mockMvc;
    @Autowired LigaStoritev ligaStoritev;
    @Autowired SrecanjeStoritev srecanjeStoritev;
    @Autowired KlubRepozitorij klubRepozitorij;
    @Autowired IgralecRepozitorij igralecRepozitorij;
    @Autowired UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired PasswordEncoder kodirnik;

    @Test
    void gostNeRazveljavi() throws Exception {
        Long s = srecanjesPostavo();

        mockMvc.perform(delete("/api/v1/srecanja/" + s + "/zapisnik"))
                .andExpect(status().isUnauthorized());

        assertEquals(StatusSrecanja.POTEKA, srecanjeStoritev.podrobno(s).srecanje().status());
    }

    @Test
    void tujOrganizatorNeRazveljavi() throws Exception {
        Long s = srecanjesPostavo();
        racun("tuj-organizator", Vloga.ORGANIZATOR);

        mockMvc.perform(delete("/api/v1/srecanja/" + s + "/zapisnik")
                        .header("Authorization", basic("tuj-organizator")))
                .andExpect(status().isForbidden());

        assertEquals(StatusSrecanja.POTEKA, srecanjeStoritev.podrobno(s).srecanje().status());
    }

    @Test
    void adminVrneSrecanjeVRazpored() throws Exception {
        Long s = srecanjesPostavo();
        racun("admin-zapisnik", Vloga.ADMIN);

        mockMvc.perform(delete("/api/v1/srecanja/" + s + "/zapisnik")
                        .header("Authorization", basic("admin-zapisnik")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.srecanje.status").value("RAZPORED"))
                .andExpect(jsonPath("$.tekme").isEmpty());
    }

    /* Liga brez lastnika (ustvarjena brez prijave) in prvo srecanje z vpisano
       postavo - zapisnik, ki se ga da razveljaviti. */
    private Long srecanjesPostavo() {
        Long liga = ligaStoritev.ustvari(new LigaVnos("Dostop test", "2026/27", SpolKategorija.MOSKI,
                FormatSrecanja.SAVINJA, 5, null, false, 2, 1, 0, true, false,
                RavenTekmovanja.KLUBSKO, false, null, null, null)).id();
        for (String ime : List.of("Klub D1", "Klub D2")) {
            Klub klub = klubRepozitorij.save(new Klub(ime, null));
            var ekipa = ligaStoritev.dodajEkipo(liga, new EkipaVnos(klub.getId(), null, null));
            for (int i = 1; i <= 2; i++) {
                Igralec ig = new Igralec();
                ig.setIme("Ig" + i);
                ig.setPriimek(ime.replace(" ", "") + i);
                ig.setSpol(Spol.MOSKI);
                ig.setDatumRojstva(java.time.LocalDate.of(1990, 1, 1));
                ig = igralecRepozitorij.save(ig);
                ligaStoritev.dodajVKader(ekipa.id(), new KaderVnos(ig.getId(), i));
            }
        }
        ligaStoritev.generirajRazpored(liga);
        Long s = srecanjeStoritev.zaLigo(liga).get(0).id();

        SrecanjePodrobnoDto p = srecanjeStoritev.podrobno(s);
        List<PostavaVnos.MestoVnos> mesta = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            mesta.add(new PostavaVnos.MestoVnos(StranEkipe.DOMACI, p.pozicijeDomaci().get(i),
                    p.kaderDomaci().get(i).idIgralec(), true));
            mesta.add(new PostavaVnos.MestoVnos(StranEkipe.GOST, p.pozicijeGost().get(i),
                    p.kaderGost().get(i).idIgralec(), true));
        }
        srecanjeStoritev.nastaviPostavo(s, new PostavaVnos(mesta));
        return s;
    }

    private void racun(String prijavnoIme, Vloga vloga) {
        Uporabnik u = new Uporabnik(prijavnoIme, kodirnik.encode(GESLO), vloga);
        u.setStatus(StatusRacuna.POTRJEN);
        uporabnikRepozitorij.save(u);
    }

    private static String basic(String prijavnoIme) {
        String par = prijavnoIme + ":" + GESLO;
        return "Basic " + Base64.getEncoder()
                .encodeToString(par.getBytes(StandardCharsets.UTF_8));
    }
}
