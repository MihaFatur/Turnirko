/* Socialni dokaz v oglasu Igralec Premium: "N igralcev iz tvojega kluba ze ima
   Premium". Stevilo mora biti resnicno, zato test drzi vsako pravilo, po katerem
   se steje: kdo je placnik (veljavna narocnina igralca), koliko jih mora biti,
   da se vrstica sploh pokaze, in kateri klub ima prednost.

   Zadnji test gre skozi pravo varnostno verigo: pot je javna (gost oglas vidi),
   odgovor pa nosi samo stevilo in ne imen ali id-jev placnikov. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import si.turnirko.dto.PremiumDokazDto;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Klub;
import si.turnirko.modeli.Narocnina;
import si.turnirko.modeli.Paket;
import si.turnirko.modeli.StatusNarocnine;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.NarocninaRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

@AutoConfigureMockMvc
class PremiumDokazTest extends IntegracijskiTest {

    @Autowired private PremiumDokazStoritev dokaz;
    @Autowired private UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired private NarocninaRepozitorij narocninaRepozitorij;
    @Autowired private MockMvc mockMvc;

    private int zaporedna = 0;

    @AfterEach
    void pocisti() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void brezPlacnikovVrsticeNi() {
        assertEquals(PremiumDokazDto.PRAZEN, dokaz.dokaz());
    }

    /* Pri enem ali dveh bi ga v malem klubu brali kot "tisti dva" in za gledalca
       ne bi bil dokaz. */
    @Test
    void podMejoStevilaNeIzpise() {
        Klub klub = klub("Savinja");
        for (int i = 0; i < PremiumDokazStoritev.NAJMANJ - 1; i++) {
            placnik(klub, StatusNarocnine.AKTIVNA, null);
        }

        assertEquals(PremiumDokazDto.PRAZEN, dokaz.dokaz());
    }

    @Test
    void gostVidiSkupnoStevilo() {
        Klub a = klub("Savinja");
        Klub b = klub("Maribor");
        placnik(a, StatusNarocnine.AKTIVNA, null);
        placnik(a, StatusNarocnine.AKTIVNA, null);
        placnik(b, StatusNarocnine.AKTIVNA, null);
        placnik(null, StatusNarocnine.AKTIVNA, null);

        assertEquals(new PremiumDokazDto(4, false), dokaz.dokaz());
    }

    /* Klub je moc dokaz ("moji soigralci"), zato ima prednost pred skupnim. */
    @Test
    void prijavljenemuSePokazeStevilloIzNjegovegaKluba() {
        Klub moj = klub("Savinja");
        Klub drug = klub("Maribor");
        for (int i = 0; i < 3; i++) {
            placnik(moj, StatusNarocnine.AKTIVNA, null);
        }
        placnik(drug, StatusNarocnine.AKTIVNA, null);
        placnik(drug, StatusNarocnine.AKTIVNA, null);
        prijava(brezPaketa(moj));

        assertEquals(new PremiumDokazDto(3, true), dokaz.dokaz());
    }

    /* Ko je klub pod mejo, je posten skupni podatek boljsi od praznine. */
    @Test
    void klubPodMejoPadeNaSkupnoStevilo() {
        Klub moj = klub("Savinja");
        Klub drug = klub("Maribor");
        placnik(moj, StatusNarocnine.AKTIVNA, null);
        for (int i = 0; i < 3; i++) {
            placnik(drug, StatusNarocnine.AKTIVNA, null);
        }
        prijava(brezPaketa(moj));

        assertEquals(new PremiumDokazDto(4, false), dokaz.dokaz());
    }

    /* Preklicana narocnina velja do konca placanega obdobja; potekla ne steje,
       in tudi placnik, ki ni igralec, ne. */
    @Test
    void stejeSamoKdorPremiumSeVedVelja() {
        Klub klub = klub("Savinja");
        placnik(klub, StatusNarocnine.AKTIVNA, null);
        placnik(klub, StatusNarocnine.PREKLICANA, LocalDateTime.now().plusDays(10));
        placnik(klub, StatusNarocnine.PREKLICANA, LocalDateTime.now().minusDays(1));
        placnik(klub, StatusNarocnine.ZAPADLA, null);
        placnik(klub, StatusNarocnine.CAKA_PLACILO, null);
        organizatorZPremium();

        assertEquals(PremiumDokazDto.PRAZEN, dokaz.dokaz(),
                "veljata samo dva placnika, pod mejo 3");

        placnik(klub, StatusNarocnine.AKTIVNA, null);
        assertEquals(new PremiumDokazDto(3, false), dokaz.dokaz());
    }

    @Test
    void potJeJavnaInNosiSamoStevilo() throws Exception {
        Klub klub = klub("Savinja");
        for (int i = 0; i < 3; i++) {
            placnik(klub, StatusNarocnine.AKTIVNA, null);
        }

        mockMvc.perform(get("/api/v1/premium/dokaz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stevilo").value(3))
                .andExpect(jsonPath("$.izKluba").value(false))
                .andExpect(jsonPath("$.length()").value(2));
    }

    // ---------- Pomozno ----------

    private Klub klub(String ime) {
        return klubRepozitorij.save(new Klub(ime, null));
    }

    private Uporabnik racun(Igralec igralec) {
        Uporabnik u = new Uporabnik("dokaz" + (++zaporedna) + "@test.si", "{bcrypt}x", Vloga.IGRALEC);
        u.setStatus(StatusRacuna.POTRJEN);
        u.setIgralec(igralec);
        return uporabnikRepozitorij.save(u);
    }

    private Igralec igralecKluba(Klub klub) {
        Igralec igralec = noviIgralec("Ime" + (++zaporedna), "Priimek" + zaporedna);
        igralec.setKlub(klub);
        return igralecRepozitorij.save(igralec);
    }

    /* Placnik Premium: racun igralca (z ali brez kluba) in narocnina v danem stanju. */
    private void placnik(Klub klub, StatusNarocnine status, LocalDateTime obdobjeDo) {
        Uporabnik u = racun(igralecKluba(klub));
        Narocnina n = new Narocnina(u, Paket.PREMIUM);
        n.setStatus(status);
        n.setTrenutnoObdobjeDo(obdobjeDo);
        narocninaRepozitorij.save(n);
    }

    private Uporabnik brezPaketa(Klub klub) {
        return racun(igralecKluba(klub));
    }

    /* Premium pri organizatorju ni mogoc v pravem sistemu, a stetje se ne sme
       zanesti na to: gleda vlogo racuna. */
    private void organizatorZPremium() {
        Uporabnik u = new Uporabnik("organizator" + (++zaporedna) + "@test.si", "{bcrypt}x", Vloga.ORGANIZATOR);
        u.setStatus(StatusRacuna.POTRJEN);
        uporabnikRepozitorij.save(u);
        Narocnina n = new Narocnina(u, Paket.PREMIUM);
        n.setStatus(StatusNarocnine.AKTIVNA);
        narocninaRepozitorij.save(n);
    }

    private void prijava(Uporabnik u) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(u.getUporabniskoIme(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_IGRALEC"))));
    }
}
