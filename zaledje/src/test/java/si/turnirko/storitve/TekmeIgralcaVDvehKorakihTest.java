/* Tekme igralca za profil: branje v dveh korakih vrne iste tekme kot merilo.

   Profil je tekme igralca do nov. 2026 bral z eno poizvedbo, ki je zdruzila
   vse tekme z vsemi stranmi in sele nato obdrzala njegove. Zdaj repozitorij
   najprej prebere id-je tekem, v katerih igralec nastopa, nato isto poizvedbo
   omeji nanje (TekmaRepozitorij.najdiZaIgralca). Prvi korak je namenoma sirsi
   izbor - test drzi, da je res sirsi (nobena igralceva tekma ne izpade, na
   kateri koli strani je) in da merila drugega koraka ostanejo: odigrana,
   posamicna, najnovejsa prva.

   Pricakovanje je sestavljeno NEODVISNO, iz vseh tekem dogodka. Dvojice in
   soigralca v drugem stolpcu drzi DvojiceTest, ligaske tekme ProfilStoritevTest. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Comparator;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import si.turnirko.dto.VnosRezultata;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.Tekma;

class TekmeIgralcaVDvehKorakihTest extends IntegracijskiTest {

    @BeforeEach
    void deterministicniZreb() {
        zrebStoritev.nastaviNakljucje(new Random(42));
    }

    @Test
    void vsakIgralecDobiSvojeOdigraneTekmeNaObehStraneh() {
        Dogodek dogodek = pripraviDogodek(8);
        zrebStoritev.izvediZreb(dogodek.getId());
        // drug turnir z istim stevilom tekem: njegove tekme ne smejo med te
        Dogodek sosed = pripraviDogodek(4);
        zrebStoritev.izvediZreb(sosed.getId());
        odigraj(sosed.getId(), 3);

        // cetrtfinala in en polfinale; drugi polfinale in finale ostaneta neodigrana
        odigraj(dogodek.getId(), 5);

        List<Tekma> vse = tekmeDogodka(dogodek.getId());
        boolean nekdoNaDrugiStrani = false;
        for (Prijava prijava : prijavePoVrsti(dogodek.getId())) {
            Long idIgralca = prijava.getIgralec().getId();
            List<Long> pricakovane = vse.stream()
                    .filter(t -> t.getStatus() == StatusTekme.KONCANA)
                    .filter(t -> t.getIzidTip() == null || t.getIzidTip().jeOdigrana())
                    .filter(t -> jeStran(t.getPrijava1(), idIgralca) || jeStran(t.getPrijava2(), idIgralca))
                    .map(Tekma::getId)
                    .sorted(Comparator.reverseOrder())
                    .toList();

            assertEquals(pricakovane,
                    tekmaRepozitorij.najdiZaIgralca(idIgralca).stream().map(Tekma::getId).toList(),
                    "tekme igralca " + idIgralca);
            assertFalse(pricakovane.isEmpty(), "vsak je odigral vsaj cetrtfinale");
            assertTrue(tekmaRepozitorij.idjiTekemIgralca(idIgralca).containsAll(pricakovane),
                    "prvi korak je sirsi izbor");
            nekdoNaDrugiStrani |= vse.stream().anyMatch(t -> t.getStatus() == StatusTekme.KONCANA
                    && jeStran(t.getPrijava2(), idIgralca));
        }
        assertTrue(nekdoNaDrugiStrani, "test mora zajeti tudi igralca na drugi strani tekme");
    }

    /* Tekma brez boja (odstop) je koncana, a ni odigrana: v statistiko igralca
       ne sodi, tudi ce je v sirsem izboru prvega koraka. */
    @Test
    void tekmaBrezBojaOstaneZunajCepravJeVIzboru() {
        Dogodek dogodek = pripraviDogodek(4);
        zrebStoritev.izvediZreb(dogodek.getId());
        Tekma polfinale = tekmeDogodka(dogodek.getId()).stream()
                .filter(t -> t.getStatus() == StatusTekme.PRIPRAVLJENA)
                .findFirst().orElseThrow();
        Long ostane = polfinale.getPrijava2().getIgralec().getId();

        tekmaStoritev.odstopiIgralca(polfinale.getPrijava1().getId());

        assertTrue(tekmaRepozitorij.idjiTekemIgralca(ostane).contains(polfinale.getId()));
        assertTrue(tekmaRepozitorij.najdiZaIgralca(ostane).isEmpty());
    }

    private static boolean jeStran(Prijava prijava, Long idIgralca) {
        return prijava != null && prijava.getIgralec() != null
                && prijava.getIgralec().getId().equals(idIgralca);
    }

    private void odigraj(Long idDogodka, int najvec) {
        for (int i = 0; i < najvec; i++) {
            Tekma tekma = tekmeDogodka(idDogodka).stream()
                    .filter(t -> t.getStatus() == StatusTekme.PRIPRAVLJENA)
                    .findFirst().orElse(null);
            if (tekma == null) {
                return;
            }
            tekmaStoritev.vnesiRezultat(tekma.getId(), new VnosRezultata(null, 3, 0, null, null));
        }
    }
}
