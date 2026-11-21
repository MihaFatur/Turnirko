/* Predlog izida iz prebranega listka (PredlogIzidaListka).

   Domenska pravila, ki jih ta test varuje:
   - pravilno prebran listek je »pripravljen« (potrditev z enim dotikom);
   - prazne vrstice na koncu so neodigrani nizi, ne napaka;
   - neveljaven niz, nepopoln niz, niz po odlocitvi, premalo nizov in celica
     Rezultat, ki se ne ujema s tockami, so opozorila - predlog ni pripravljen;
   - negotova celica (popravljeno, precrtano) predlog zadrzi, tudi ce je veljaven;
   - obrnjen listek (levi stolpec = prijava 2) zamenja strani tock in zmagovalca;
   - posebni izidi (brez boja, predaja) so vedno opozorilo;
   - listek brez tock, samo z rezultatom, da izid v nizih. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import si.turnirko.dto.PredlogIzidaDto;
import si.turnirko.modeli.IzidTekme;

class PredlogIzidaListkaTest {

    @Test
    void pravilnoPrebranListekJePripravljen() {
        PredlogIzidaDto p = PredlogIzidaListka.sestavi(listek(3, 1, n(11, 7), n(9, 11), n(11, 5), n(11, 8), prazen()), 5, false);

        assertTrue(p.pripravljen(), p.opozorila().toString());
        assertEquals(IzidTekme.IGRANO, p.izidTip());
        assertEquals(3, p.dobljeniNizi1());
        assertEquals(1, p.dobljeniNizi2());
        assertEquals(4, p.nizi().size(), "prazna vrstica na koncu odpade");
        assertEquals(9, p.nizi().get(1).tocke1());
        assertEquals(11, p.nizi().get(1).tocke2());
    }

    @Test
    void podaljsanNizJeVeljaven() {
        PredlogIzidaDto p = PredlogIzidaListka.sestavi(listek(2, 0, n(12, 10), n(15, 13), prazen()), 3, false);
        assertTrue(p.pripravljen(), p.opozorila().toString());
    }

    @Test
    void neveljavenNizJeOpozorilo() {
        // 11:10 je nemogoc niz - napacno prebrana stevka ali napaka sodnika
        PredlogIzidaDto p = PredlogIzidaListka.sestavi(listek(3, 0, n(11, 10), n(11, 5), n(11, 3)), 5, false);

        assertFalse(p.pripravljen());
        assertTrue(p.nizi().getFirst().neveljaven());
        assertTrue(vsebuje(p, "1. niz (11:10) ni veljaven"), p.opozorila().toString());
    }

    @Test
    void nepopolnNizJeOpozorilo() {
        PredlogIzidaDto p = PredlogIzidaListka.sestavi(listek(3, 0, n(11, 5), n(11, null), n(11, 3)), 5, false);
        assertFalse(p.pripravljen());
        assertTrue(vsebuje(p, "2. niz ni v celoti prebran"));
    }

    @Test
    void rezultatSeMoraUjematiSTockami() {
        // tocke dajo 3:1, sodnik je v celico Rezultat napisal 3:2
        PredlogIzidaDto p = PredlogIzidaListka.sestavi(listek(3, 2, n(11, 7), n(9, 11), n(11, 5), n(11, 8)), 5, false);
        assertFalse(p.pripravljen());
        assertTrue(vsebuje(p, "Rezultat na listku (3:2) se ne ujema"), p.opozorila().toString());
    }

    @Test
    void premaloNizovZaZmago() {
        // 2:1 pri tekmi na 5 nizov: en niz manjka ali je prebran napacno
        PredlogIzidaDto p = PredlogIzidaListka.sestavi(listek(null, null, n(11, 7), n(9, 11), n(11, 5)), 5, false);
        assertFalse(p.pripravljen());
        assertTrue(vsebuje(p, "Nihče nima 3 dobljenih nizov"), p.opozorila().toString());
    }

    @Test
    void nizPoOdlocitviJeOpozorilo() {
        PredlogIzidaDto p = PredlogIzidaListka.sestavi(listek(null, null, n(11, 7), n(11, 9), n(9, 11)), 3, false);
        assertFalse(p.pripravljen());
        assertTrue(vsebuje(p, "odločena že po 2. nizu"), p.opozorila().toString());
    }

    @Test
    void negotovaCelicaZadrziPredlog() {
        PredlogIzidaDto p = PredlogIzidaListka.sestavi(listek(2, 0,
                new PrebranListek.Niz(11, 7, false, true), n(11, 4), prazen()), 3, false);
        assertFalse(p.pripravljen());
        assertTrue(p.opozorila().isEmpty(), "negotovost ni opozorilo, je oznaka celice");
        assertTrue(p.nizi().getFirst().negotovo2());
    }

    @Test
    void obrnjenListekZamenjaStrani() {
        // levi stolpec je prijava 2: tocke levega gredo v tocke2
        PredlogIzidaDto p = PredlogIzidaListka.sestavi(listek(2, 1,
                new PrebranListek.Niz(11, 7, true, false), n(9, 11), n(11, 5)), 3, true);
        assertEquals(1, p.dobljeniNizi1());
        assertEquals(2, p.dobljeniNizi2());
        assertEquals(7, p.nizi().getFirst().tocke1());
        assertEquals(11, p.nizi().getFirst().tocke2());
        assertTrue(p.nizi().getFirst().negotovo2(), "oznaka gre s celico");
    }

    @Test
    void brezBojaJeVednoOpozoriloInZmagovalecSledi() {
        PrebranListek l = new PrebranListek(true, null, "Novak", "Kranjc", false, null, null,
                List.of(prazen(), prazen(), prazen()), null, null, false, "BREZ_BOJA", "DESNO", null);
        PredlogIzidaDto p = PredlogIzidaListka.sestavi(l, 5, false);
        assertEquals(IzidTekme.BREZ_BOJA, p.izidTip());
        assertEquals(2, p.zmagovalecStran());
        assertFalse(p.pripravljen());

        assertEquals(1, PredlogIzidaListka.sestavi(l, 5, true).zmagovalecStran(), "obrnjeno: desno je prijava 1");
    }

    @Test
    void predajaObdrziDelniIzid() {
        PrebranListek l = new PrebranListek(true, null, null, null, false, null, null,
                List.of(n(11, 7), n(5, 3)), null, null, false, "PREDAJA", "LEVO", null);
        PredlogIzidaDto p = PredlogIzidaListka.sestavi(l, 5, false);
        assertEquals(IzidTekme.PREDAJA, p.izidTip());
        assertEquals(1, p.zmagovalecStran());
        assertFalse(p.pripravljen());
    }

    @Test
    void listekBrezTockDaIzidVNizih() {
        PredlogIzidaDto p = PredlogIzidaListka.sestavi(listek(3, 1, prazen(), prazen(), prazen(), prazen(), prazen()), 5, false);
        assertEquals(3, p.dobljeniNizi1());
        assertEquals(1, p.dobljeniNizi2());
        assertTrue(p.nizi().isEmpty());
        assertTrue(p.pripravljen(), p.opozorila().toString());

        PredlogIzidaDto nemogoc = PredlogIzidaListka.sestavi(listek(3, 3, prazen()), 5, false);
        assertFalse(nemogoc.pripravljen());
    }

    @Test
    void niListkaJeOpozorilo() {
        PrebranListek l = new PrebranListek(false, null, null, null, false, null, null,
                List.of(), null, null, false, "NI", null, null);
        PredlogIzidaDto p = PredlogIzidaListka.sestavi(l, 5, false);
        assertFalse(p.pripravljen());
        assertTrue(vsebuje(p, "ni prepoznan sodniški listek"));
    }

    @Test
    void opombaBralnikaJeOpozorilo() {
        PrebranListek l = new PrebranListek(true, null, null, null, false, null, null,
                List.of(n(11, 7), n(11, 9)), 2, 0, false, "NI", null, "Pripis ob 2. nizu.");
        PredlogIzidaDto p = PredlogIzidaListka.sestavi(l, 3, false);
        assertFalse(p.pripravljen());
        assertTrue(vsebuje(p, "Opomba bralnika: Pripis ob 2. nizu."));
    }

    @Test
    void manjkajocaVrsticaNiTiha() {
        // bralnik ni nasel ene vrstice (prst cez celico Rezultata): zadnji niz je postal Rezultat
        PredlogIzidaDto p = PredlogIzidaListka.sestavi(listek(1, 2, n(11, 7), n(9, 11), n(11, 5), n(5, 11)), 5, false);
        assertFalse(p.pripravljen());
        assertTrue(vsebuje(p, "Prebranih je 4 vrstic nizov, listek tekme jih ima 5"), p.opozorila().toString());
    }

    // ---------- Pomozno ----------

    private static PrebranListek listek(Integer rezultatLevo, Integer rezultatDesno, PrebranListek.Niz... nizi) {
        return new PrebranListek(true, "T-1", "Ana Novak", "Tim Kranjc", false, "Člani", "Skupina A",
                new ArrayList<>(Arrays.asList(nizi)), rezultatLevo, rezultatDesno, false, "NI", null, null);
    }

    private static PrebranListek.Niz n(Integer levo, Integer desno) {
        return new PrebranListek.Niz(levo, desno, false, false);
    }

    private static PrebranListek.Niz prazen() {
        return new PrebranListek.Niz(null, null, false, false);
    }

    private static boolean vsebuje(PredlogIzidaDto p, String del) {
        return p.opozorila().stream().anyMatch(o -> o.contains(del));
    }
}
