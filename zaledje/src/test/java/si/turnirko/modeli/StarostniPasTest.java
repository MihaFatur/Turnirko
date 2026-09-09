/* Starostni pas je izpeljan, ne shranjen - zato ga mora varovati test.
   Pravilo je 11. clen PST: starost se doloci na 31. december v letu, v
   katerem se sezona ZACNE, in igralec mora biti na ta dan mlajsi od stevilke
   kategorije. Od januarja do junija zato se vedno velja letnica prejsnjega
   leta - to je tisto, kar se ob nepazljivi spremembi najprej pokvari. */
package si.turnirko.modeli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class StarostniPasTest {

    /* Oktober 2026 -> sezona 2026/27, starost se meri na 31. 12. 2026. */
    private static final LocalDate JESEN = LocalDate.of(2026, 10, 4);
    /* Marec 2027 je SE VEDNO sezona 2026/27 - referenca ostane leto 2026. */
    private static final LocalDate POMLAD = LocalDate.of(2027, 3, 1);

    @Test
    void pasJeNajozjiKiMuIgralecUstreza() {
        // 10 let v letu 2026 -> U11 (in ne U13, ceprav je tudi tja mlajsi)
        assertEquals(StarostniPas.U11, StarostniPas.izpelji(LocalDate.of(2016, 5, 1), JESEN));
        assertEquals(StarostniPas.U13, StarostniPas.izpelji(LocalDate.of(2014, 5, 1), JESEN));
        assertEquals(StarostniPas.U15, StarostniPas.izpelji(LocalDate.of(2012, 5, 1), JESEN));
        assertEquals(StarostniPas.U17, StarostniPas.izpelji(LocalDate.of(2010, 5, 1), JESEN));
        assertEquals(StarostniPas.U19, StarostniPas.izpelji(LocalDate.of(2008, 5, 1), JESEN));
        assertEquals(StarostniPas.U21, StarostniPas.izpelji(LocalDate.of(2006, 5, 1), JESEN));
    }

    /* Meja je koledarsko leto sezone in ne rojstni dan: kdor v letu sezone
       dopolni 15, ni vec U15 - tudi ce ima rojstni dan sele decembra. */
    @Test
    void mejaTecePoLetnici() {
        assertEquals(StarostniPas.U15, StarostniPas.izpelji(LocalDate.of(2012, 12, 31), JESEN));
        assertEquals(StarostniPas.U17, StarostniPas.izpelji(LocalDate.of(2011, 1, 1), JESEN));
    }

    /* Jedro pravila: spomladi tece ISTA sezona, zato se pas ne premakne.
       Igralec letnika 2012 je U15 jeseni 2026 in se marca 2027. */
    @Test
    void spomladiVeljaSePrejsnjaSezona() {
        assertEquals(StarostniPas.U15, StarostniPas.izpelji(LocalDate.of(2012, 5, 1), POMLAD));
        // sezona 2027/28 se zacne julija - takrat isti igralec preide v U17
        assertEquals(StarostniPas.U17,
                StarostniPas.izpelji(LocalDate.of(2012, 5, 1), LocalDate.of(2027, 7, 1)));
    }

    @Test
    void clanInVeteran() {
        assertEquals(StarostniPas.CLANI, StarostniPas.izpelji(LocalDate.of(2005, 5, 1), JESEN));
        assertEquals(StarostniPas.CLANI, StarostniPas.izpelji(LocalDate.of(1987, 5, 1), JESEN));
        // 40 let v letu sezone -> veteran
        assertEquals(StarostniPas.VETERANI, StarostniPas.izpelji(LocalDate.of(1986, 12, 31), JESEN));
    }

    @Test
    void brezLetnicePasaNi() {
        assertNull(StarostniPas.izpelji(null, JESEN));
    }
}
