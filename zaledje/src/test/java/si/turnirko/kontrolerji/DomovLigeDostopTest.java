/* Domaca stran prepozna prijavljenega igralca tudi na JAVNI poti.

   GET /api/v1/domov/lige je odprt gostu, igralceve lige pa so odvisne od tega,
   KDO je zahtevo poslal - to je mogoce le, ce varnostna veriga glavo
   Authorization prebere tudi na poti, ki prijave ne zahteva. Storitveni test
   (DomovLigeIgralcevihEkipTest) klice storitev z rocno postavljenim
   kontekstom in tega ne pokrije: ce bi kdo pot prestavil ali ji izrecno
   izklopil prijavo, bi ostal zelen, igralec pa bi brez opozorila videl le
   adminov izbor. */
package si.turnirko.kontrolerji;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.EkipaVnos;
import si.turnirko.dto.KaderVnos;
import si.turnirko.dto.LigaVnos;
import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Narocnina;
import si.turnirko.modeli.Paket;
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.modeli.Spol;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.StatusNarocnine;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.IgralecRepozitorij;
import si.turnirko.repozitoriji.NarocninaRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;
import si.turnirko.storitve.LigaStoritev;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DomovLigeDostopTest {

    private static final String GESLO = "preizkusnoGeslo123";
    private static final String PRIJAVA = "premium-domov";

    @Autowired MockMvc mockMvc;
    @Autowired LigaStoritev ligaStoritev;
    @Autowired IgralecRepozitorij igralecRepozitorij;
    @Autowired UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired NarocninaRepozitorij narocninaRepozitorij;
    @Autowired PasswordEncoder kodirnik;

    @Test
    void premiumIgralecPrekHttpVidiLigeSvojeEkipe() throws Exception {
        Long moja = ligaZIgralcem("Rekreativna liga", premiumIgralec());
        ligaNaDomaci("1. SNTL moski");

        mockMvc.perform(get("/api/v1/domov/lige").header("Authorization", basic()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(moja))
                .andExpect(jsonPath("$[0].izEkipe").value(true));
    }

    /* Ista zahteva brez prijave: adminov izbor, in liga ni "moja". */
    @Test
    void gostPrekHttpVidiAdminovIzbor() throws Exception {
        ligaZIgralcem("Rekreativna liga", premiumIgralec());
        Long izpostavljena = ligaNaDomaci("1. SNTL moski");

        mockMvc.perform(get("/api/v1/domov/lige"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(izpostavljena))
                .andExpect(jsonPath("$[0].izEkipe").value(false));
    }

    // ---------- Pomozno ----------

    private Igralec premiumIgralec() {
        Igralec igralec = new Igralec();
        igralec.setIme("Ana");
        igralec.setPriimek("Novak");
        igralec.setSpol(Spol.ZENSKI);
        igralec.setDatumRojstva(LocalDate.of(2000, 1, 1));
        igralecRepozitorij.save(igralec);

        Uporabnik u = new Uporabnik(PRIJAVA, kodirnik.encode(GESLO), Vloga.IGRALEC);
        u.setStatus(StatusRacuna.POTRJEN);
        u.setIgralec(igralec);
        uporabnikRepozitorij.save(u);
        Narocnina n = new Narocnina(u, Paket.PREMIUM);
        n.setStatus(StatusNarocnine.AKTIVNA);
        narocninaRepozitorij.save(n);
        return igralec;
    }

    private Long ustvariLigo(String ime) {
        LigaVnos v = new LigaVnos(ime, "25/26", SpolKategorija.MOSKI, FormatSrecanja.SAVINJA, 5,
                null, false, 2, 1, 0, true, false, RavenTekmovanja.URADNO, false, null, null, null);
        return ligaStoritev.ustvari(v).id();
    }

    private Long ligaZIgralcem(String ime, Igralec igralec) {
        Long idLiga = ustvariLigo(ime);
        Long idEkipa = ligaStoritev.dodajEkipo(idLiga, new EkipaVnos(null, null, ime + " ekipa")).id();
        ligaStoritev.dodajVKader(idEkipa, new KaderVnos(igralec.getId(), 1));
        return idLiga;
    }

    private Long ligaNaDomaci(String ime) {
        Long idLiga = ustvariLigo(ime);
        ligaStoritev.nastaviNaDomaci(idLiga, true);
        return idLiga;
    }

    private static String basic() {
        String par = PRIJAVA + ":" + GESLO;
        return "Basic " + Base64.getEncoder()
                .encodeToString(par.getBytes(StandardCharsets.UTF_8));
    }
}
