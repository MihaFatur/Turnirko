/* Poti brez prijave (registracija, kode, pozabljeno geslo) in blokada po
   prevec neuspelih prijavah.

   Dvoje mora drzati cez zico, ne le v storitvi: (1) odgovor registracije je
   enak, ce naslov ze obstaja - iz njega ni mogoce sestaviti seznama racunov;
   (2) racun s prevec napacnimi gesli dobi 429, se preden se geslo preveri.
   Oboje varuje varnostna veriga oz. filter, zato test tece skozi MockMvc. */
package si.turnirko.kontrolerji;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import si.turnirko.dto.RegistracijaVnos;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.repozitoriji.UporabnikRepozitorij;
import si.turnirko.storitve.OmejevalnikPoskusov;
import si.turnirko.storitve.RegistracijaStoritev;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AvtentikacijaDostopTest {

    private static final String REGISTRACIJA = """
            {"ime":"Ana","priimek":"Novak","email":"%s","geslo":"geslo1234",
             "organizator":false,"datumRojstva":"1990-05-20"}
            """;

    @Autowired MockMvc mockMvc;
    @Autowired UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired RegistracijaStoritev registracija;
    @Autowired OmejevalnikPoskusov omejevalnik;

    @BeforeEach
    @AfterEach
    void pocistiOmejitve() {
        omejevalnik.pocistiVse();
    }

    @Test
    void registracijaJeBrezPrijaveInNeRazkrijeObstojaNaslova() throws Exception {
        String prvi = mockMvc.perform(post("/api/v1/auth/registracija")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTRACIJA.formatted("ana@test.si")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(uporabnikRepozitorij.findByUporabniskoIme("ana@test.si").isPresent());
        // odgovor ne nosi nicesar o racunu (id, status), samo kar je vpisal klicatelj
        assertFalse(prvi.contains("\"id\""), prvi);
        assertFalse(prvi.contains("status"), prvi);

        omejevalnik.pocistiVse();
        String drugi = mockMvc.perform(post("/api/v1/auth/registracija")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTRACIJA.formatted("ana@test.si")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(prvi, drugi, "isti odgovor, ne glede na to, ali naslov ze obstaja");
    }

    @Test
    void napacnaKodaInNeznanNaslovDobitaIstoNapako() throws Exception {
        registracija.registriraj(new RegistracijaVnos("Ana", "Novak", null, "ana@test.si",
                "geslo1234", false, LocalDate.of(1990, 5, 20), null), "127.0.0.1");

        String zaObstojeci = mockMvc.perform(post("/api/v1/auth/potrdi-eposto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@test.si\",\"koda\":\"000000\"}"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
        String zaNeznanega = mockMvc.perform(post("/api/v1/auth/potrdi-eposto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nihce@test.si\",\"koda\":\"000000\"}"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertEquals(zaObstojeci, zaNeznanega);
        assertTrue(zaObstojeci.contains(RegistracijaStoritev.NAPACNA_KODA));
    }

    @Test
    void pozabljenoGesloInNovoGesloStaBrezPrijave() throws Exception {
        mockMvc.perform(post("/api/v1/auth/pozabljeno-geslo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nihce@test.si\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/auth/novo-geslo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nihce@test.si\",\"koda\":\"123456\",\"geslo\":\"novoGeslo1\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nepotrjenRacunSePrijaviABrezPravic() throws Exception {
        registracija.registriraj(new RegistracijaVnos("Ana", "Novak", null, "ana@test.si",
                "geslo1234", false, LocalDate.of(1990, 5, 20), null), "127.0.0.1");

        String profil = mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", basic("ana@test.si", "geslo1234")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(profil.contains("\"emailPotrjen\":false"), profil);

        mockMvc.perform(get("/api/v1/racuni")
                        .header("Authorization", basic("ana@test.si", "geslo1234")))
                .andExpect(status().isForbidden());
    }

    @Test
    void prevecNapacnihGeselBlokiraRacun() throws Exception {
        registracija.registriraj(new RegistracijaVnos("Ana", "Novak", null, "blokada@test.si",
                "geslo1234", false, LocalDate.of(1990, 5, 20), null), "127.0.0.1");

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/api/v1/auth/me")
                            .header("Authorization", basic("blokada@test.si", "napacno" + i)))
                    .andExpect(status().isUnauthorized());
        }
        // enajsti poskus ne pride do preverbe gesla - tudi s pravim geslom ne
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", basic("blokada@test.si", "geslo1234")))
                .andExpect(status().isTooManyRequests());

        // z drugega naslova se lastnik se vedno prijavi: kdor ugiba, ne more
        // tujega racuna zakleniti za vse
        mockMvc.perform(get("/api/v1/auth/me")
                        .with(zahteva -> { zahteva.setRemoteAddr("10.9.9.9"); return zahteva; })
                        .header("Authorization", basic("blokada@test.si", "geslo1234")))
                .andExpect(status().isOk());

        // drug racun ni prizadet (blokada je na racun, ne na streznik)
        Uporabnik admin = uporabnikRepozitorij.findAll().stream()
                .filter(u -> u.getVloga() == si.turnirko.modeli.Vloga.ADMIN).findFirst().orElseThrow();
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", basic(admin.getUporabniskoIme(), "napacno")))
                .andExpect(status().isUnauthorized());
    }

    /* Porazdeljeno ugibanje (veliko naslovov) ustavi skupna meja racuna. */
    @Test
    void ugibanjeZVecNaslovovZaklenePriSkupniMeji() throws Exception {
        registracija.registriraj(new RegistracijaVnos("Ana", "Novak", null, "skupna@test.si",
                "geslo1234", false, LocalDate.of(1990, 5, 20), null), "127.0.0.1");

        for (int i = 0; i < 100; i++) {
            String naslov = "10.0." + (i / 5) + "." + i;
            mockMvc.perform(get("/api/v1/auth/me")
                            .with(zahteva -> { zahteva.setRemoteAddr(naslov); return zahteva; })
                            .header("Authorization", basic("skupna@test.si", "napacno")))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(get("/api/v1/auth/me")
                        .with(zahteva -> { zahteva.setRemoteAddr("10.200.0.1"); return zahteva; })
                        .header("Authorization", basic("skupna@test.si", "geslo1234")))
                .andExpect(status().isTooManyRequests());

        // novo geslo s kodo po posti blokado odpravi
        omejevalnik.pocistiPrijaveRacuna("skupna@test.si");
        mockMvc.perform(get("/api/v1/auth/me")
                        .with(zahteva -> { zahteva.setRemoteAddr("10.200.0.1"); return zahteva; })
                        .header("Authorization", basic("skupna@test.si", "geslo1234")))
                .andExpect(status().isOk());
    }

    @Test
    void uspesnaPrijavaPobriseStevecNeuspehov() throws Exception {
        registracija.registriraj(new RegistracijaVnos("Ana", "Novak", null, "stevec@test.si",
                "geslo1234", false, LocalDate.of(1990, 5, 20), null), "127.0.0.1");

        for (int i = 0; i < 9; i++) {
            mockMvc.perform(get("/api/v1/auth/me")
                            .header("Authorization", basic("stevec@test.si", "napacno")))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", basic("stevec@test.si", "geslo1234")))
                .andExpect(status().isOk());
        for (int i = 0; i < 9; i++) {
            mockMvc.perform(get("/api/v1/auth/me")
                            .header("Authorization", basic("stevec@test.si", "napacno")))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", basic("stevec@test.si", "geslo1234")))
                .andExpect(status().isOk());
    }

    /* Potrjen navijac se prijavi s pravico NAVIJAC: osebne poti (izbor lig,
       obvestila) so mu odprte, upravne ne, Premium (paket za igralca) se ne
       da kupiti. */
    @Test
    void potrjenNavijacImaOsebnePotiInNicVec() throws Exception {
        registracija.registriraj(new RegistracijaVnos("Mojca", "Kos", null, "mojca@test.si",
                "geslo1234", false, LocalDate.of(1975, 3, 8), null, null, true), "127.0.0.1");
        Uporabnik u = uporabnikRepozitorij.najdiZVsem("mojca@test.si").orElseThrow();
        u.setEmailPotrjenOb(java.time.LocalDateTime.now());
        u.setStatus(si.turnirko.modeli.StatusRacuna.POTRJEN);
        uporabnikRepozitorij.save(u);
        String prijava = basic("mojca@test.si", "geslo1234");

        String profil = mockMvc.perform(get("/api/v1/auth/me").header("Authorization", prijava))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(profil.contains("\"vloga\":\"NAVIJAC\""), profil);
        assertTrue(profil.contains("\"status\":\"POTRJEN\""), profil);

        mockMvc.perform(get("/api/v1/domov/moje-lige").header("Authorization", prijava))
                .andExpect(status().isOk());
        // spremljani igralci (V57) so osebna pot kot spremljane lige
        mockMvc.perform(get("/api/v1/domov/moji-igralci").header("Authorization", prijava))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/domov/moji-igralci-povzetki").header("Authorization", prijava))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/obvestila").header("Authorization", prijava))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/racuni").header("Authorization", prijava))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/organizator/pregled").header("Authorization", prijava))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/placila/nadgradnja").header("Authorization", prijava)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paket\":\"PREMIUM\",\"ciklus\":\"LETNO\"}"))
                .andExpect(status().isBadRequest());
    }

    /* Gost izbora igralcev nima: poti so osebne in zahtevajo prijavo (pravilo mora
       stati pred splosnim "GET je javen", sicer bi gost bral tuji izbor). */
    @Test
    void gostNimaSpremljanihIgralcev() throws Exception {
        mockMvc.perform(get("/api/v1/domov/moji-igralci")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/domov/moji-igralci-povzetki")).andExpect(status().isUnauthorized());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/domov/moji-igralci/1"))
                .andExpect(status().isUnauthorized());
    }

    /* Druzina (V59): pregled in zahteva sta osebni (paket preveri storitev), seznam zahtev in
       odlocitve so samo adminova - navijac, ki bi sam potrdil svojo povezavo, bi zaobsel
       edino pravilo, ki varuje zasebno statistiko tujega otroka. */
    @Test
    void druzinaJeOsebnaOdlocitveJeSamoAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/druzina")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/druzina/admin")).andExpect(status().isUnauthorized());

        registracija.registriraj(new RegistracijaVnos("Oce", "Kos", null, "oce@test.si",
                "geslo1234", false, LocalDate.of(1975, 3, 8), null, null, true), "127.0.0.1");
        Uporabnik u = uporabnikRepozitorij.najdiZVsem("oce@test.si").orElseThrow();
        u.setEmailPotrjenOb(java.time.LocalDateTime.now());
        u.setStatus(si.turnirko.modeli.StatusRacuna.POTRJEN);
        uporabnikRepozitorij.save(u);
        String prijava = basic("oce@test.si", "geslo1234");

        mockMvc.perform(get("/api/v1/druzina").header("Authorization", prijava))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/druzina/admin").header("Authorization", prijava))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/druzina/1/potrdi").header("Authorization", prijava))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/druzina/1/razveze").header("Authorization", prijava))
                .andExpect(status().isForbidden());
        // brez placljivega paketa zahteve ni
        mockMvc.perform(post("/api/v1/druzina").header("Authorization", prijava)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idIgralec\":1}"))
                .andExpect(status().isForbidden());
    }

    /* Osebni koledar: povezava racuna je osebna, sam koledar .ics je javen GET z zetonom. */
    @Test
    void koledarIcsJeJavenSamoSZetonom() throws Exception {
        mockMvc.perform(get("/api/v1/moj-koledar")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/koledar-ics/neznan-zeton.ics")).andExpect(status().isNotFound());
    }

    /* Potisna obvestila (V60): javni kljuc VAPID je javen GET (prazen, dokler streznik kljucev nima),
       vpis naprave in preizkus zahtevata prijavo. */
    @Test
    void potisnaObvestilaJavniKljucJeJavenOstaloZahtevaPrijavo() throws Exception {
        mockMvc.perform(get("/api/v1/push/kljuc")).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/push/naprava").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endpoint\":\"https://fcm.googleapis.com/x\",\"keys\":{\"p256dh\":\"a\",\"auth\":\"b\"}}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/push/preizkus")).andExpect(status().isUnauthorized());
    }

    private static String basic(String ime, String geslo) {
        return "Basic " + Base64.getEncoder().encodeToString(
                (ime + ":" + geslo).getBytes(StandardCharsets.UTF_8));
    }
}
