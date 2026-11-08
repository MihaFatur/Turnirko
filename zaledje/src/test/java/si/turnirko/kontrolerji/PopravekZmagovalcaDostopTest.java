/* Popravek zmagovalca skozi varnostno verigo: gost in tuj organizator ne smeta
   niti do predogleda, admin pa z neveljavno oz. manjkajoco izbiro ne spremeni
   nicesar. Pravila popravka so v PopravekZmagovalcaTest; tu je pot in to, da je
   veriga ne pogoltne. */
package si.turnirko.kontrolerji;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import si.turnirko.dto.VnosRezultata;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.modeli.SistemTekmovanja;
import si.turnirko.modeli.Spol;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.Turnir;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.DogodekRepozitorij;
import si.turnirko.repozitoriji.IgralecRepozitorij;
import si.turnirko.repozitoriji.PrijavaRepozitorij;
import si.turnirko.repozitoriji.TekmaRepozitorij;
import si.turnirko.repozitoriji.TurnirRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;
import si.turnirko.storitve.TekmaStoritev;
import si.turnirko.storitve.ZrebStoritev;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PopravekZmagovalcaDostopTest {

    private static final String GESLO = "preizkusnoGeslo123";
    private static final String OBRNJEN = """
            {"dobljeniNizi1":0,"dobljeniNizi2":3}
            """;

    @Autowired MockMvc mockMvc;
    @Autowired ZrebStoritev zrebStoritev;
    @Autowired TekmaStoritev tekmaStoritev;
    @Autowired TurnirRepozitorij turnirRepozitorij;
    @Autowired DogodekRepozitorij dogodekRepozitorij;
    @Autowired PrijavaRepozitorij prijavaRepozitorij;
    @Autowired TekmaRepozitorij tekmaRepozitorij;
    @Autowired IgralecRepozitorij igralecRepozitorij;
    @Autowired UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired PasswordEncoder kodirnik;

    @Test
    void gostNiDoPredogledaNiDoPopravka() throws Exception {
        Long tekma = koncanoPolfinale();

        mockMvc.perform(post("/api/v1/tekme/" + tekma + "/rezultat/predogled")
                        .contentType(MediaType.APPLICATION_JSON).content(OBRNJEN))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/tekme/" + tekma + "/rezultat")
                        .contentType(MediaType.APPLICATION_JSON).content(OBRNJEN))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tujOrganizatorNeSme() throws Exception {
        Long tekma = koncanoPolfinale();
        racun("tuj-popravek", Vloga.ORGANIZATOR);

        mockMvc.perform(post("/api/v1/tekme/" + tekma + "/rezultat/predogled")
                        .header("Authorization", basic("tuj-popravek"))
                        .contentType(MediaType.APPLICATION_JSON).content(OBRNJEN))
                .andExpect(status().isForbidden());
    }

    /* Finale je odigrano, zato popravek brez izbire ne gre, z izbiro pa gre. */
    @Test
    void adminDobiPredogledInPotrebujeIzbiro() throws Exception {
        Long tekma = koncanoPolfinale();
        racun("admin-popravek", Vloga.ADMIN);

        mockMvc.perform(post("/api/v1/tekme/" + tekma + "/rezultat/predogled")
                        .header("Authorization", basic("admin-popravek"))
                        .contentType(MediaType.APPLICATION_JSON).content(OBRNJEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zamenjanZmagovalec").value(true))
                .andExpect(jsonPath("$.izbire[0]").value("IZBRISI_IZIDE"))
                .andExpect(jsonPath("$.izbire[1]").value("ZAMENJAJ_IGRALCA"))
                .andExpect(jsonPath("$.odvisneZIzidom[0].nizi1").value(3));

        mockMvc.perform(put("/api/v1/tekme/" + tekma + "/rezultat")
                        .header("Authorization", basic("admin-popravek"))
                        .contentType(MediaType.APPLICATION_JSON).content(OBRNJEN))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/api/v1/tekme/" + tekma + "/rezultat?nacin=IZBRISI_IZIDE")
                        .header("Authorization", basic("admin-popravek"))
                        .contentType(MediaType.APPLICATION_JSON).content(OBRNJEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dobljeniNizi2").value(3));
    }

    /* Turnir brez lastnika, mreza stirih, vse odigrano. Vrne prvo polfinale. */
    private Long koncanoPolfinale() {
        Turnir turnir = new Turnir();
        turnir.setIme("Dostop popravek");
        turnir.setDatumZacetka(LocalDate.now());
        turnir.setRaven(RavenTekmovanja.URADNO);
        turnirRepozitorij.save(turnir);

        Dogodek dogodek = new Dogodek();
        dogodek.setTurnir(turnir);
        dogodek.setIme("Clani");
        dogodek.setSpolKategorija(SpolKategorija.MOSKI);
        dogodek.setPrivzetoSteviloNizov(5);
        dogodek.setSistemTekmovanja(SistemTekmovanja.IZLOCILNI);
        dogodekRepozitorij.save(dogodek);
        for (int i = 1; i <= 4; i++) {
            Igralec igralec = new Igralec();
            igralec.setIme("Ig" + i);
            igralec.setPriimek("Popravek" + i);
            igralec.setSpol(Spol.MOSKI);
            igralec.setDatumRojstva(LocalDate.of(1990, 1, 1));
            prijavaRepozitorij.save(new Prijava(dogodek, igralecRepozitorij.save(igralec)));
        }
        zrebStoritev.izvediZreb(dogodek.getId());

        Long prvo = null;
        for (int krog = 0; krog < 2; krog++) {
            for (var tekma : tekmaRepozitorij.najdiZaDogodek(dogodek.getId())) {
                if (tekma.getStatus() == StatusTekme.PRIPRAVLJENA) {
                    tekmaStoritev.vnesiRezultat(tekma.getId(), new VnosRezultata(null, 3, 0, null, null));
                    prvo = prvo == null ? tekma.getId() : prvo;
                }
            }
        }
        return prvo;
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
