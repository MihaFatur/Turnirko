/* Kaj sme racun glede na svoj placilni paket (NarocninaStoritev):
   igralec vidi zasebno statistiko samo s Premium, organizator sme na sezono
   ustvariti toliko turnirjev/lig, kolikor dovoljuje njegov paket (Basic 1
   liga / 2 turnirja, Plus 3/5, Pro 5/10) - meja se sezono, ki se zacne
   1. julija (isti rez kot StarostniPas), ponastavi na 0. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.PrepovedanoIzjema;
import si.turnirko.modeli.Liga;
import si.turnirko.modeli.Narocnina;
import si.turnirko.modeli.Paket;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.StatusNarocnine;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.Turnir;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.LigaRepozitorij;
import si.turnirko.repozitoriji.NarocninaRepozitorij;
import si.turnirko.repozitoriji.TurnirRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NarocninaStoritevTest {

    @Autowired UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired NarocninaRepozitorij narocninaRepozitorij;
    @Autowired TurnirRepozitorij turnirRepozitorij;
    @Autowired LigaRepozitorij ligaRepozitorij;
    @Autowired NarocninaStoritev narocnina;
    @Autowired EntityManager em;

    // ---------- Igralec: imaPremium ----------

    @Test
    void igralecBrezNarocnineNiPremium() {
        Uporabnik i = igralec("brez@test");
        assertFalse(narocnina.imaPremium(i));
    }

    @Test
    void igralecZAktivnoNarocninoJePremium() {
        Uporabnik i = igralec("premium@test");
        narocnina(i, Paket.PREMIUM, StatusNarocnine.AKTIVNA, null);
        assertTrue(narocnina.imaPremium(i));
    }

    @Test
    void igralecZZapadloNarocninoNiPremium() {
        Uporabnik i = igralec("zapadel@test");
        narocnina(i, Paket.PREMIUM, StatusNarocnine.ZAPADLA, null);
        assertFalse(narocnina.imaPremium(i));
    }

    /* Preklic ugasne obnovo, ne takojsnjega dostopa - kdor je placal, obdrzi
       Premium do konca ze placanega obdobja. */
    @Test
    void preklicanaVeljaDoKoncaObdobja() {
        Uporabnik i = igralec("preklic@test");
        narocnina(i, Paket.PREMIUM, StatusNarocnine.PREKLICANA, LocalDateTime.now().plusDays(5));
        assertTrue(narocnina.imaPremium(i));
    }

    @Test
    void preklicanaPoKoncuObdobjaNiVecPremium() {
        Uporabnik i = igralec("preklicPotekel@test");
        narocnina(i, Paket.PREMIUM, StatusNarocnine.PREKLICANA, LocalDateTime.now().minusDays(1));
        assertFalse(narocnina.imaPremium(i));
    }

    // ---------- Organizator: omejitve na sezono ----------

    @Test
    void organizatorBrezPaketaNeSmeUstvarjatiTurnirjaNitiLige() {
        Uporabnik o = organizator("brezpaketa@test");
        assertThrows(PrepovedanoIzjema.class, () -> narocnina.preveriOmejitevTurnirja(o));
        assertThrows(PrepovedanoIzjema.class, () -> narocnina.preveriOmejitevLige(o));
    }

    /* Basic: 1 tekoca liga / 2 turnirja na sezono. */
    @Test
    void basicDovoliDvaTurnirjaNatretjegaZavrne() {
        Uporabnik o = organizator("basic@test");
        narocnina(o, Paket.ORGANIZATOR_BASIC, StatusNarocnine.AKTIVNA, null);

        turnir(o); // 1. ustvarjen - preverba za DRUGEGA se prehaja
        assertDoesNotThrow(() -> narocnina.preveriOmejitevTurnirja(o));
        turnir(o); // 2. ustvarjen - meja (2) je zdaj DOSEZENA, tretjega ni vec
        assertThrows(DomenskaIzjema.class, () -> narocnina.preveriOmejitevTurnirja(o));
    }

    @Test
    void basicDovoliEnoLigoNaDrugoZavrne() {
        Uporabnik o = organizator("basicLiga@test");
        narocnina(o, Paket.ORGANIZATOR_BASIC, StatusNarocnine.AKTIVNA, null);

        assertDoesNotThrow(() -> narocnina.preveriOmejitevLige(o));
        liga(o); // 1.
        assertThrows(DomenskaIzjema.class, () -> narocnina.preveriOmejitevLige(o));
    }

    /* Plus (3/5) in Pro (5/10) imajo visje meje - preverimo samo stevilko, ki
       ju loci od Basic. */
    @Test
    void plusInProImataVisjoMejoOdBasic() {
        Uporabnik plus = organizator("plus@test");
        narocnina(plus, Paket.ORGANIZATOR_PLUS, StatusNarocnine.AKTIVNA, null);
        for (int i = 0; i < 4; i++) {
            turnir(plus);
        }
        assertDoesNotThrow(() -> narocnina.preveriOmejitevTurnirja(plus), "Plus dovoli 5 turnirjev");

        Uporabnik pro = organizator("pro@test");
        narocnina(pro, Paket.ORGANIZATOR_PRO, StatusNarocnine.AKTIVNA, null);
        for (int i = 0; i < 9; i++) {
            turnir(pro);
        }
        assertDoesNotThrow(() -> narocnina.preveriOmejitevTurnirja(pro), "Pro dovoli 10 turnirjev");
    }

    /* Jedro sezonskega reza: turnir iz PREJSNJE sezone (pred 1. julijem) se v
       tekoco mejo ne steje - drugace bi organizator meja nikoli ne obnovila. */
    @Test
    void turnirIzPrejsnjeSezoneSeNeStejeVMejo() {
        Uporabnik o = organizator("stariTurnirji@test");
        narocnina(o, Paket.ORGANIZATOR_BASIC, StatusNarocnine.AKTIVNA, null);

        Turnir prvi = turnir(o);
        Turnir drugi = turnir(o);
        backdateUstvarjenOb("turnir", prvi.getId(), "2020-04-01T10:00:00");
        backdateUstvarjenOb("turnir", drugi.getId(), "2020-04-01T10:00:00");
        em.flush();
        em.clear();

        // oba obstojeca turnirja sta iz sezone 2019/20 - tekoca sezona ima 0
        assertDoesNotThrow(() -> narocnina.preveriOmejitevTurnirja(o));
    }

    // ---------- Pomozno ----------

    /* CAKA in ne POTRJEN: shema zahteva, da ima POTRJEN igralec povezan
       zapis (id_igralec) - imaPremium() pa je od povezave neodvisen, zato
       tu ni potrebna. */
    private Uporabnik igralec(String prijavnoIme) {
        Uporabnik u = new Uporabnik(prijavnoIme, "{bcrypt}x", Vloga.IGRALEC);
        u.setStatus(StatusRacuna.CAKA);
        return uporabnikRepozitorij.save(u);
    }

    private Uporabnik organizator(String prijavnoIme) {
        Uporabnik u = new Uporabnik(prijavnoIme, "{bcrypt}x", Vloga.ORGANIZATOR);
        u.setStatus(StatusRacuna.POTRJEN);
        return uporabnikRepozitorij.save(u);
    }

    private Narocnina narocnina(Uporabnik u, Paket paket, StatusNarocnine status, LocalDateTime obdobjeDo) {
        Narocnina n = new Narocnina(u, paket);
        n.setStatus(status);
        n.setTrenutnoObdobjeDo(obdobjeDo);
        return narocninaRepozitorij.save(n);
    }

    private Turnir turnir(Uporabnik lastnik) {
        Turnir t = new Turnir();
        t.setIme("Testni turnir");
        t.setUstvaril(lastnik);
        return turnirRepozitorij.save(t);
    }

    private Liga liga(Uporabnik lastnik) {
        Liga l = new Liga();
        l.setIme("Testna liga");
        l.setSpolKategorija(SpolKategorija.MESANO);
        l.setUstvaril(lastnik);
        return ligaRepozitorij.save(l);
    }

    private void backdateUstvarjenOb(String tabela, Long id, String zapis) {
        em.createNativeQuery("UPDATE " + tabela + " SET ustvarjen_ob = ?1 WHERE id = ?2")
                .setParameter(1, zapis)
                .setParameter(2, id)
                .executeUpdate();
    }
}
