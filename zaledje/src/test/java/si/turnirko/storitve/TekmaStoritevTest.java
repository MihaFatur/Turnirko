/* Testi vnosa rezultatov: avtomat stanj, napredovanje, rating,
   posebni izidi in zakljucevanje dogodka. */
package si.turnirko.storitve;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import si.turnirko.dto.VnosRezultata;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.IzidTekme;
import si.turnirko.modeli.RatingStanje;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.StatusTekmovanja;
import si.turnirko.modeli.Tekma;
import si.turnirko.modeli.Turnir;

class TekmaStoritevTest extends IntegracijskiTest {

    @BeforeEach
    void deterministicniZreb() {
        zrebStoritev.nastaviNakljucje(new Random(42));
    }

    /* Pripravi dogodek s 4 igralci in izvedenim zrebom. */
    private Dogodek dogodekZZrebom() {
        Dogodek dogodek = pripraviDogodek(4);
        zrebStoritev.izvediZreb(dogodek.getId());
        return dogodek;
    }

    private Tekma prvaPripravljena(Long idDogodka) {
        return tekmeDogodka(idDogodka).stream()
                .filter(t -> t.getStatus() == StatusTekme.PRIPRAVLJENA)
                .findFirst().orElseThrow();
    }

    private VnosRezultata rezultat(int nizi1, int nizi2) {
        return new VnosRezultata(null, nizi1, nizi2, null, null);
    }

    @Test
    void normalenVnosNapredujeZmagovalca() {
        Dogodek dogodek = dogodekZZrebom();
        Tekma polfinale = prvaPripravljena(dogodek.getId());
        Long idZmagovalca = polfinale.getPrijava1().getId();

        tekmaStoritev.vnesiRezultat(polfinale.getId(), rezultat(3, 1));

        List<Tekma> tekme = tekmeDogodka(dogodek.getId());
        Tekma koncana = tekme.stream().filter(t -> t.getId().equals(polfinale.getId())).findFirst().orElseThrow();
        assertEquals(StatusTekme.KONCANA, koncana.getStatus());
        assertEquals(IzidTekme.IGRANO, koncana.getIzidTip());
        assertEquals(idZmagovalca, koncana.getZmagovalec().getId());

        // zmagovalec je vpisan v finale
        Tekma finale = tekme.stream().filter(t -> t.getKolo() == 2).findFirst().orElseThrow();
        assertTrue((finale.getPrijava1() != null && finale.getPrijava1().getId().equals(idZmagovalca))
                || (finale.getPrijava2() != null && finale.getPrijava2().getId().equals(idZmagovalca)));
    }

    @Test
    void ratingSeObracunaNatankoEnkrat() {
        Dogodek dogodek = dogodekZZrebom();
        Tekma tekma = prvaPripravljena(dogodek.getId());
        Long idIgralca1 = tekma.getPrijava1().getIgralec().getId();

        tekmaStoritev.vnesiRezultat(tekma.getId(), rezultat(3, 0));

        RatingStanje stanje = ratingStanjeRepozitorij
                .findByIgralecIdAndSistem(idIgralca1, RatingStanje.SISTEM_KLUBSKI_ELO)
                .orElseThrow();
        // dva novinca (K=48), gladka zmaga 3:0 z margino po presenecenju (~1,19)
        assertEquals(1028, stanje.getVrednost(), "1000 + 28 za gladko zmago 3:0 med novincema");
        assertTrue(ratingZgodovinaRepozitorij.existsByTekmaId(tekma.getId()));
    }

    @Test
    void ponovniVnosNaKoncanoTekmoJeZavrnjen() {
        Dogodek dogodek = dogodekZZrebom();
        Tekma tekma = prvaPripravljena(dogodek.getId());
        tekmaStoritev.vnesiRezultat(tekma.getId(), rezultat(3, 1));

        assertThrows(DomenskaIzjema.class,
                () -> tekmaStoritev.vnesiRezultat(tekma.getId(), rezultat(0, 3)),
                "vnos na koncano tekmo mora biti zavrnjen - scitimo rating in mrezo");
    }

    @Test
    void vnosNaTekmoBrezIgralcevJeZavrnjen() {
        Dogodek dogodek = dogodekZZrebom();
        Tekma finale = tekmeDogodka(dogodek.getId()).stream()
                .filter(t -> t.getKolo() == 2).findFirst().orElseThrow();
        assertThrows(DomenskaIzjema.class,
                () -> tekmaStoritev.vnesiRezultat(finale.getId(), rezultat(3, 0)));
    }

    @Test
    void neveljavniRezultatiSoZavrnjeni() {
        Dogodek dogodek = dogodekZZrebom();
        Tekma tekma = prvaPripravljena(dogodek.getId());

        assertThrows(NeveljavenVnosIzjema.class,
                () -> tekmaStoritev.vnesiRezultat(tekma.getId(), rezultat(2, 2)));
        assertThrows(NeveljavenVnosIzjema.class,
                () -> tekmaStoritev.vnesiRezultat(tekma.getId(), rezultat(2, 1)));
        assertThrows(NeveljavenVnosIzjema.class,
                () -> tekmaStoritev.vnesiRezultat(tekma.getId(), rezultat(3, 3)));
    }

    @Test
    void celotenTurnirDoZmagovalca() {
        Dogodek dogodek = dogodekZZrebom();

        // odigramo obe tekmi 1. kola
        for (Tekma tekma : tekmeDogodka(dogodek.getId())) {
            if (tekma.getStatus() == StatusTekme.PRIPRAVLJENA && tekma.getKolo() == 1) {
                tekmaStoritev.vnesiRezultat(tekma.getId(), rezultat(3, 0));
            }
        }

        Tekma finale = tekmeDogodka(dogodek.getId()).stream()
                .filter(t -> t.getKolo() == 2).findFirst().orElseThrow();
        assertEquals(StatusTekme.PRIPRAVLJENA, finale.getStatus(), "oba finalista morata biti znana");

        tekmaStoritev.vnesiRezultat(finale.getId(), rezultat(3, 2));

        // status preberemo sveze iz baze - atomarne posodobitve napredovanja
        // ocistijo predpomnilnik in stare reference niso vec azurne
        assertEquals(StatusTekmovanja.ZAKLJUCEN,
                dogodekRepozitorij.findById(dogodek.getId()).orElseThrow().getStatus(),
                "po finalu se dogodek zakljuci");

        // koncni mesti sta dodeljeni
        Tekma odigranoFinale = tekmeDogodka(dogodek.getId()).stream()
                .filter(t -> t.getKolo() == 2).findFirst().orElseThrow();
        assertEquals(1, odigranoFinale.getZmagovalec().getKoncnoMesto());
        assertEquals(2, odigranoFinale.porazenec().getKoncnoMesto());

        // zdaj je mogoce zakljuciti tudi turnir
        Long idTurnirja = dogodek.getTurnir().getId();
        turnirjiStoritev.zakljuci(idTurnirja);
        assertEquals(StatusTekmovanja.ZAKLJUCEN,
                turnirRepozitorij.findById(idTurnirja).orElseThrow().getStatus());
    }

    @Test
    void brezBojaNeObracunaRatinga() {
        Dogodek dogodek = dogodekZZrebom();
        Tekma tekma = prvaPripravljena(dogodek.getId());

        tekmaStoritev.vnesiRezultat(tekma.getId(),
                new VnosRezultata(IzidTekme.BREZ_BOJA, null, null, 1, null));

        Tekma koncana = tekmaRepozitorij.najdiZVsem(tekma.getId()).orElseThrow();
        assertEquals(IzidTekme.BREZ_BOJA, koncana.getIzidTip());
        assertNotNull(koncana.getZmagovalec());
        assertEquals(false, ratingZgodovinaRepozitorij.existsByTekmaId(tekma.getId()),
                "w.o. ne sme vplivati na rating");
    }

    @Test
    void turnirBrezEloNeObracunaRatinga() {
        Dogodek dogodek = pripraviDogodek(4);
        // izklopi ELO na turnirju (organizator to izbere ob ustvarjanju)
        Turnir turnir = dogodek.getTurnir();
        turnir.setStejeVElo(false);
        turnirRepozitorij.save(turnir);

        zrebStoritev.izvediZreb(dogodek.getId());
        Tekma tekma = prvaPripravljena(dogodek.getId());
        tekmaStoritev.vnesiRezultat(tekma.getId(), rezultat(3, 0));

        assertFalse(ratingZgodovinaRepozitorij.existsByTekmaId(tekma.getId()),
                "turnir brez ELO ne sme obracunati klubskega ratinga");
    }

    @Test
    void predajaObracunaRating() {
        Dogodek dogodek = dogodekZZrebom();
        Tekma tekma = prvaPripravljena(dogodek.getId());

        tekmaStoritev.vnesiRezultat(tekma.getId(),
                new VnosRezultata(IzidTekme.PREDAJA, 2, 1, 1, null));

        assertTrue(ratingZgodovinaRepozitorij.existsByTekmaId(tekma.getId()),
                "predaja je igrana tekma - rating se obracuna");
    }

    @Test
    void tockeNizovSePreverijoInShranijo() {
        Dogodek dogodek = dogodekZZrebom();
        Tekma tekma = prvaPripravljena(dogodek.getId());

        // neveljaven niz: 10:9 ni koncan niz
        assertThrows(NeveljavenVnosIzjema.class, () -> tekmaStoritev.vnesiRezultat(tekma.getId(),
                new VnosRezultata(null, 3, 0, null, List.of(
                        new VnosRezultata.NizVnos(11, 5),
                        new VnosRezultata.NizVnos(10, 9),
                        new VnosRezultata.NizVnos(11, 7)))));

        // stevilo nizov se ne ujema z rezultatom
        assertThrows(NeveljavenVnosIzjema.class, () -> tekmaStoritev.vnesiRezultat(tekma.getId(),
                new VnosRezultata(null, 3, 0, null, List.of(
                        new VnosRezultata.NizVnos(11, 5),
                        new VnosRezultata.NizVnos(11, 7)))));

        // veljaven vnos s podaljsano igro (12:10)
        tekmaStoritev.vnesiRezultat(tekma.getId(),
                new VnosRezultata(null, 3, 0, null, List.of(
                        new VnosRezultata.NizVnos(11, 5),
                        new VnosRezultata.NizVnos(12, 10),
                        new VnosRezultata.NizVnos(11, 0))));

        assertEquals(3, nizRepozitorij.findByTekmaIdOrderByZaporednaStAsc(tekma.getId()).size());
    }

    @Test
    void nizeVNemogocemVrstnemReduZavrne() {
        Dogodek dogodek = dogodekZZrebom();
        Tekma tekma = prvaPripravljena(dogodek.getId());

        // 11:4, 11:7, 11:8, 8:11 pri izidu 3:1: zmagovalec je imel 3 nize ze
        // po tretjem nizu (3:0), zato se cetrti niz sploh ne bi igral
        assertThrows(NeveljavenVnosIzjema.class, () -> tekmaStoritev.vnesiRezultat(tekma.getId(),
                new VnosRezultata(null, 3, 1, null, List.of(
                        new VnosRezultata.NizVnos(11, 4),
                        new VnosRezultata.NizVnos(11, 7),
                        new VnosRezultata.NizVnos(11, 8),
                        new VnosRezultata.NizVnos(8, 11)))),
                "niz po odloceni tekmi mora biti zavrnjen");

        // isti izid 3:1 z mogocim vrstnim redom (porazenec dobi drugi niz) je veljaven
        tekmaStoritev.vnesiRezultat(tekma.getId(),
                new VnosRezultata(null, 3, 1, null, List.of(
                        new VnosRezultata.NizVnos(11, 4),
                        new VnosRezultata.NizVnos(8, 11),
                        new VnosRezultata.NizVnos(11, 7),
                        new VnosRezultata.NizVnos(11, 8))));
        assertEquals(4, nizRepozitorij.findByTekmaIdOrderByZaporednaStAsc(tekma.getId()).size());
    }

    @Test
    void celotenTurnirSPetimiIgralciInProstimiMesti() {
        Dogodek dogodek = pripraviDogodek(5);
        zrebStoritev.izvediZreb(dogodek.getId());

        // igramo, dokler so na voljo pripravljene tekme
        boolean seIgra = true;
        while (seIgra) {
            List<Tekma> pripravljene = tekmeDogodka(dogodek.getId()).stream()
                    .filter(t -> t.getStatus() == StatusTekme.PRIPRAVLJENA).toList();
            seIgra = !pripravljene.isEmpty();
            for (Tekma tekma : pripravljene) {
                tekmaStoritev.vnesiRezultat(tekma.getId(), rezultat(3, 1));
            }
        }

        assertEquals(StatusTekmovanja.ZAKLJUCEN, dogodekRepozitorij.findById(dogodek.getId())
                .orElseThrow().getStatus(), "turnir s prostimi mesti se mora izteci do konca");
    }
}
