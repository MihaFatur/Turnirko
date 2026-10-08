/* Pravilo izbora turnirjev za domaco stran igralca s Premium (IzborTurnirjev)
   in okno razpredelnice lige okoli igralceve ekipe (DomovStoritev.okno).
   Ciste funkcije, brez baze. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import si.turnirko.dto.DomovTurnirDto.Razlog;
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.modeli.Spol;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.StatusTekmovanja;

class IzborTurnirjevTest {

    private static final LocalDate DANES = LocalDate.of(2026, 10, 7);
    private long naslednjiId = 1;

    private IzborTurnirjev.Kandidat turnir(int cez, StatusTekmovanja status, RavenTekmovanja raven) {
        return new IzborTurnirjev.Kandidat(naslednjiId++, DANES.plusDays(cez), status, raven, true, false, false);
    }

    private static IzborTurnirjev.Kandidat moj(IzborTurnirjev.Kandidat k) {
        return new IzborTurnirjev.Kandidat(k.id(), k.datum(), k.status(), k.raven(), k.primernRazpis(), k.kolegi(), true);
    }

    private static IzborTurnirjev.Kandidat kolegi(IzborTurnirjev.Kandidat k) {
        return new IzborTurnirjev.Kandidat(k.id(), k.datum(), k.status(), k.raven(), k.primernRazpis(), true, k.moj());
    }

    private static IzborTurnirjev.Kandidat neprimeren(IzborTurnirjev.Kandidat k) {
        return new IzborTurnirjev.Kandidat(k.id(), k.datum(), k.status(), k.raven(), false, k.kolegi(), k.moj());
    }

    /* Rekreativcu najblizji REKREATIVNI, ceprav je uradni blizje. */
    @Test
    void rekreativecDobiRekreativenPrihajajociTurnir() {
        var uradni = turnir(3, StatusTekmovanja.PRIPRAVA, RavenTekmovanja.URADNO);
        var rekreativni = turnir(20, StatusTekmovanja.PRIPRAVA, RavenTekmovanja.REKREATIVNO);

        var izbor = IzborTurnirjev.izberi(List.of(uradni, rekreativni), true, DANES);

        assertEquals(new IzborTurnirjev.Izbira(rekreativni.id(), Razlog.PRIHAJA_PRIMEREN), izbor.get(0));
    }

    /* Brez rekreativnega rekreativec dobi klubskega (mesana tekmovanja). */
    @Test
    void rekreativecBrezRekreativnegaDobiKlubskega() {
        var uradni = turnir(3, StatusTekmovanja.PRIPRAVA, RavenTekmovanja.URADNO);
        var klubski = turnir(10, StatusTekmovanja.PRIPRAVA, RavenTekmovanja.KLUBSKO);

        assertEquals(klubski.id(), IzborTurnirjev.izberi(List.of(uradni, klubski), true, DANES).get(0).id());
    }

    /* Tekmovalcu najblizji uradni ali klubski - rekreativni turnir ni zanj,
       turnir z razpisom, ki ga ne dopusca (U13 za odraslega), pa tudi ne. */
    @Test
    void tekmovalecDobiNajblizjiUradniAliKlubskiSPrimernimRazpisom() {
        var rekreativni = turnir(2, StatusTekmovanja.PRIPRAVA, RavenTekmovanja.REKREATIVNO);
        var mladinski = neprimeren(turnir(4, StatusTekmovanja.PRIPRAVA, RavenTekmovanja.URADNO));
        var klubski = turnir(9, StatusTekmovanja.PRIPRAVA, RavenTekmovanja.KLUBSKO);
        var uradni = turnir(12, StatusTekmovanja.PRIPRAVA, RavenTekmovanja.URADNO);

        var izbor = IzborTurnirjev.izberi(List.of(rekreativni, mladinski, klubski, uradni), false, DANES);

        assertEquals(new IzborTurnirjev.Izbira(klubski.id(), Razlog.PRIHAJA_PRIMEREN), izbor.get(0));
    }

    /* Prijava pove vec kot ugibanje o primernosti. */
    @Test
    void prijavaPrevladaNadPrimernim() {
        var primeren = turnir(3, StatusTekmovanja.PRIPRAVA, RavenTekmovanja.URADNO);
        var prijavljen = moj(turnir(15, StatusTekmovanja.PRIPRAVA, RavenTekmovanja.REKREATIVNO));

        assertEquals(new IzborTurnirjev.Izbira(prijavljen.id(), Razlog.PRIJAVLJEN),
                IzborTurnirjev.izberi(List.of(primeren, prijavljen), false, DANES).get(0));
    }

    /* Zadnji je najnovejsi zaceti turnir, na katerem je nastopil. */
    @Test
    void zadnjiJeNajnovejsiTurnirNaKateremJeNastopil() {
        var star = moj(turnir(-200, StatusTekmovanja.ZAKLJUCEN, RavenTekmovanja.URADNO));
        var zadnji = moj(turnir(-30, StatusTekmovanja.ZAKLJUCEN, RavenTekmovanja.URADNO));
        var drugih = turnir(-5, StatusTekmovanja.ZAKLJUCEN, RavenTekmovanja.URADNO);

        var izbor = IzborTurnirjev.izberi(List.of(star, zadnji, drugih), false, DANES);

        assertEquals(new IzborTurnirjev.Izbira(zadnji.id(), Razlog.ZADNJI), izbor.get(0));
    }

    /* Prihajajoci, zadnji in dva zanimiva - skupaj stiri, vsak enkrat; v teku
       je pred klubskimi kolegi. */
    @Test
    void stiriVrsticeVsakTurnirEnkrat() {
        List<IzborTurnirjev.Kandidat> k = new ArrayList<>();
        var prihaja = turnir(10, StatusTekmovanja.PRIPRAVA, RavenTekmovanja.URADNO);
        var zadnji = moj(turnir(-14, StatusTekmovanja.ZAKLJUCEN, RavenTekmovanja.URADNO));
        var kolegi = kolegi(turnir(-3, StatusTekmovanja.ZAKLJUCEN, RavenTekmovanja.URADNO));
        var vTeku = turnir(0, StatusTekmovanja.V_TEKU, RavenTekmovanja.REKREATIVNO);
        var drug = turnir(-40, StatusTekmovanja.ZAKLJUCEN, RavenTekmovanja.URADNO);
        k.addAll(List.of(prihaja, zadnji, kolegi, vTeku, drug));

        var izbor = IzborTurnirjev.izberi(k, false, DANES);

        assertEquals(List.of(
                new IzborTurnirjev.Izbira(prihaja.id(), Razlog.PRIHAJA_PRIMEREN),
                new IzborTurnirjev.Izbira(zadnji.id(), Razlog.ZADNJI),
                new IzborTurnirjev.Izbira(vTeku.id(), Razlog.V_TEKU),
                new IzborTurnirjev.Izbira(kolegi.id(), Razlog.KOLEGI)), izbor);
    }

    /* Brez prihajajocega in zadnjega so zanimivi trije - skupaj spet stiri.
       Ko v oknu dveh mesecev ni dovolj, zapolnijo najnovejsi. */
    @Test
    void brezPosebnihSoZanimiviTrijeInJihZapolnijoNajnovejsi() {
        var lani = turnir(-400, StatusTekmovanja.ZAKLJUCEN, RavenTekmovanja.URADNO);
        var predletom = turnir(-300, StatusTekmovanja.ZAKLJUCEN, RavenTekmovanja.URADNO);
        var nedavno = turnir(-20, StatusTekmovanja.ZAKLJUCEN, RavenTekmovanja.URADNO);

        var izbor = IzborTurnirjev.izberi(List.of(lani, predletom, nedavno), false, DANES);

        assertEquals(List.of(nedavno.id(), predletom.id(), lani.id()),
                izbor.stream().map(IzborTurnirjev.Izbira::id).toList());
        assertEquals(Razlog.PRIMEREN, izbor.get(0).razlog());
        assertEquals(Razlog.OSTALO, izbor.get(1).razlog());
    }

    /* Med zanimivimi je turnir, ki ga razpis dopusca, pred mladinskim, ki ga
       ne - tudi ce je mladinski blizje. */
    @Test
    void zanimiviZRazpisomZanjSoPredMladinskimi() {
        var mladinski = neprimeren(turnir(-2, StatusTekmovanja.ZAKLJUCEN, RavenTekmovanja.URADNO));
        var odprt = turnir(-20, StatusTekmovanja.ZAKLJUCEN, RavenTekmovanja.URADNO);

        var izbor = IzborTurnirjev.izberi(List.of(mladinski, odprt), true, DANES);

        assertEquals(List.of(odprt.id(), mladinski.id()),
                izbor.stream().map(IzborTurnirjev.Izbira::id).toList());
    }

    /* Kadar je primernih dovolj, mladinskega sploh ni - sklop je raje krajsi. */
    @Test
    void neprimerenRazpisSamoDoNajmanjsegaStevila() {
        var mladinski = neprimeren(turnir(-2, StatusTekmovanja.ZAKLJUCEN, RavenTekmovanja.URADNO));
        var prvi = turnir(-20, StatusTekmovanja.ZAKLJUCEN, RavenTekmovanja.URADNO);
        var drugi = turnir(-30, StatusTekmovanja.ZAKLJUCEN, RavenTekmovanja.KLUBSKO);

        var izbor = IzborTurnirjev.izberi(List.of(mladinski, prvi, drugi), true, DANES);

        assertEquals(List.of(drugi.id(), prvi.id()),
                izbor.stream().map(IzborTurnirjev.Izbira::id).toList(),
                "klubski je rekreativni ravni primeren, uradni le dopuscen - mladinskega ni");
    }

    @Test
    void razpisDopuscaPoSpoluInStarosti() {
        assertFalse(IzborTurnirjev.razpisDopusca(SpolKategorija.MOSKI, "U-13", Spol.MOSKI, 30));
        assertTrue(IzborTurnirjev.razpisDopusca(SpolKategorija.MOSKI, "U-13", Spol.MOSKI, 12));
        assertTrue(IzborTurnirjev.razpisDopusca(SpolKategorija.MOSKI, "1. OT ZA CLANE U-21", Spol.MOSKI, 20));
        assertFalse(IzborTurnirjev.razpisDopusca(SpolKategorija.ZENSKE, "CLANI", Spol.MOSKI, 30));
        assertTrue(IzborTurnirjev.razpisDopusca(SpolKategorija.KDORKOLI, "CLANI", Spol.ZENSKI, 30));
        assertTrue(IzborTurnirjev.razpisDopusca(SpolKategorija.MESANO, null, Spol.ZENSKI, 30));
        assertFalse(IzborTurnirjev.razpisDopusca(SpolKategorija.MOSKI, "Veterani 40+", Spol.MOSKI, 35));
        assertTrue(IzborTurnirjev.razpisDopusca(SpolKategorija.MOSKI, "U15", Spol.MOSKI, null),
                "neznana starost ne izloci - ne ugibamo proti igralcu");
        // imena kategorij iz uvozene zgodovine in besedne meje
        assertFalse(IzborTurnirjev.razpisDopusca(SpolKategorija.MOSKI, "KADETI - 1 SKUPINA", Spol.MOSKI, 40));
        assertTrue(IzborTurnirjev.razpisDopusca(SpolKategorija.MOSKI, "kadeti", Spol.MOSKI, 14));
        assertFalse(IzborTurnirjev.razpisDopusca(SpolKategorija.MOSKI, "mlajši kadeti", Spol.MOSKI, 14));
        assertFalse(IzborTurnirjev.razpisDopusca(SpolKategorija.MOSKI, "člani do 21 let", Spol.MOSKI, 30));
        assertTrue(IzborTurnirjev.razpisDopusca(SpolKategorija.KDORKOLI, "Do 60 let", Spol.MOSKI, 40));
        assertFalse(IzborTurnirjev.razpisDopusca(SpolKategorija.KDORKOLI, "Nad 60 let", Spol.MOSKI, 40));
        assertFalse(IzborTurnirjev.razpisDopusca(SpolKategorija.MOSKI, "DEČKI - U11", Spol.MOSKI, 40));
    }

    /* Okno razpredelnice: ena gor in ena dol, na vrhu dve dol, na dnu dve gor. */
    @Test
    void oknoOkoliEkipe() {
        List<Integer> mesta = List.of(1, 2, 3, 4, 5, 6);

        assertEquals(List.of(1, 2, 3), DomovStoritev.okno(mesta, 0, 3));
        assertEquals(List.of(3, 4, 5), DomovStoritev.okno(mesta, 3, 3));
        assertEquals(List.of(4, 5, 6), DomovStoritev.okno(mesta, 5, 3));
        assertEquals(List.of(1, 2), DomovStoritev.okno(List.of(1, 2), 1, 3));
    }
}
