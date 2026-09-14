/* Testi odbitka za neaktivnost skozi celo pot: stanje -> dnevnik -> lestvica. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import si.turnirko.dto.LestvicaIgralcaDto;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.RatingStanje;
import si.turnirko.modeli.RatingZgodovina;
import si.turnirko.modeli.RazlogSpremembe;
import si.turnirko.repozitoriji.RatingZgodovinaRepozitorij;

class NeaktivnostStoritevTest extends IntegracijskiTest {

    @Autowired private NeaktivnostStoritev neaktivnostStoritev;
    @Autowired private RatingZgodovinaRepozitorij zgodovinaRepozitorij;
    @Autowired private StatistikaStoritev statistikaStoritev;

    /* Igralec z znanim ratingom in znanim dnem zadnje tekme. */
    private RatingStanje igralecZZadnjoTekmo(String ime, int rating, LocalDateTime zadnja) {
        Igralec igralec = noviIgralec(ime, "Neaktivni");
        RatingStanje stanje = new RatingStanje(igralec, RatingStanje.SISTEM_TURNIRKO, rating);
        stanje.setZadnjaTekmaOb(zadnja);
        stanje.setPrvaTekmaOb(zadnja);
        stanje.setStTekem(40);
        return ratingStanjeRepozitorij.save(stanje);
    }

    private List<RatingZgodovina> odbitki(Igralec igralec) {
        return zgodovinaRepozitorij
                .najdiZaIgralca(igralec.getId(), RatingStanje.SISTEM_TURNIRKO).stream()
                .filter(z -> z.getRazlog() == RazlogSpremembe.NEAKTIVNOST)
                .toList();
    }

    @Test
    void poSestihMesecihOdbijeDesetPoDvehLetihSkupajStirideset() {
        LocalDateTime zadnja = LocalDateTime.now().minusMonths(30);
        RatingStanje stanje = igralecZZadnjoTekmo("Tri", 1500, zadnja);

        int odbito = neaktivnostStoritev.uveljavi(stanje, LocalDateTime.now());

        assertEquals(40, odbito, "skupaj -10, -15 in -15");
        assertEquals(1460, stanje.getVrednost());
        List<RatingZgodovina> zapisi = odbitki(stanje.getIgralec());
        assertEquals(3, zapisi.size(), "vsaka stopnja ima svoj zapis");
        assertEquals(zadnja.plusMonths(6), zapisi.get(0).getVeljaOb(),
                "zapis velja na dan zapadlosti, ne na dan vpisa");
        assertEquals(-10, zapisi.get(0).getSprememba());
        assertEquals(-15, zapisi.get(2).getSprememba());
    }

    /* Dnevno opravilo tece vsak dan - isti odbitek se ne sme zapisati dvakrat. */
    @Test
    void ponovniZagonNeOdbijeDvakrat() {
        RatingStanje stanje = igralecZZadnjoTekmo("Dvakrat", 1500,
                LocalDateTime.now().minusMonths(13));

        assertEquals(25, neaktivnostStoritev.uveljavi(stanje, LocalDateTime.now()));
        assertEquals(0, neaktivnostStoritev.uveljavi(stanje, LocalDateTime.now()),
                "drugi zagon istega dne ne sme odbiti nicesar");
        assertEquals(2, odbitki(stanje.getIgralec()).size());
        assertEquals(1475, stanje.getVrednost());
    }

    /* Kdor igra redno, ne izgubi nicesar. */
    @Test
    void aktivenIgralecNeIzgubiNicesar() {
        RatingStanje stanje = igralecZZadnjoTekmo("Reden", 1500,
                LocalDateTime.now().minusMonths(3));
        assertEquals(0, neaktivnostStoritev.uveljavi(stanje, LocalDateTime.now()));
        assertTrue(odbitki(stanje.getIgralec()).isEmpty());
    }

    /* Rating ne sme pod spodnjo mejo niti zaradi odbitka. */
    @Test
    void odbitekNeSpustiPodSpodnjoMejo() {
        RatingStanje stanje = igralecZZadnjoTekmo("Dno", 115,
                LocalDateTime.now().minusMonths(30));
        neaktivnostStoritev.uveljavi(stanje, LocalDateTime.now());
        assertEquals(100, stanje.getVrednost());
    }

    /* Po 18 mesecih igralca na javni lestvici ni vec - njegova stevilka pa
       ostane v bazi in na profilu. */
    @Test
    void poOsemnajstihMesecihGaNiNaJavniLestvici() {
        RatingStanje ostane = igralecZZadnjoTekmo("Aktiven", 1400,
                LocalDateTime.now().minusMonths(17));
        RatingStanje izgine = igralecZZadnjoTekmo("Izginuli", 1900,
                LocalDateTime.now().minusMonths(19));

        List<LestvicaIgralcaDto> lestvica = statistikaStoritev.globalnaLestvica();

        assertTrue(imaRating(lestvica, ostane.getIgralec().getId()),
                "17 mesecev je se znotraj praga");
        assertFalse(imaRating(lestvica, izgine.getIgralec().getId()),
                "19 mesecev brez tekme pomeni, da na javni lestvici ratinga ni vec");
        assertEquals(1900, izgine.getVrednost(), "stevilka v bazi ostane nedotaknjena");
    }

    private boolean imaRating(List<LestvicaIgralcaDto> lestvica, Long idIgralca) {
        return lestvica.stream()
                .anyMatch(v -> v.idIgralca().equals(idIgralca) && v.rating() != null);
    }
}
