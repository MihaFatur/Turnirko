/* Vsak igralec v seznamu nosi SVOJ rating.

   Seznami igralcev (javni, podrobni, podobni ob vpisu, pari cistke) preberejo
   ratinge vseh igralcev z eno poizvedbo in jih nato pripisejo zapisom. Do nov.
   2026 je pripis za vsakega igralca preiskal ves seznam ratingov (kvadratno,
   najdba C3 pregleda kakovosti); zdaj gre prek mape po id-ju igralca. Test
   drzi, kar se pri tem ne sme spremeniti: igralec dobi svojo vrednost in svoje
   stevilo tekem, igralec brez ratinga pa prazno vrednost in nic tekem - ne
   sosedove. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import si.turnirko.dto.DvojnikIgralcaDto;
import si.turnirko.dto.IgralecDto;
import si.turnirko.dto.IgralecJavniDto;
import si.turnirko.dto.PodobenIgralecJavniDto;
import si.turnirko.dto.PodobniIgralciVnos;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.RatingStanje;
import si.turnirko.modeli.Spol;

class RatingiVSeznamihIgralcevTest extends IntegracijskiTest {

    private static final int IGRALCEV = 30;

    @Autowired private IgralciStoritev igralciStoritev;
    @Autowired private DvojnikiIgralcevStoritev dvojnikiStoritev;

    /* Vsak tretji igralec ostane brez ratinga; ostali dobijo vrednost in
       stevilo tekem, ki ju je mogoce izracunati iz zaporedne stevilke. */
    private List<Igralec> igralciZRatingi(String priimek) {
        List<Igralec> igralci = new ArrayList<>();
        for (int i = 0; i < IGRALCEV; i++) {
            Igralec igralec = noviIgralec("Seznam", priimek + (char) ('a' + i / 26) + (char) ('a' + i % 26));
            igralci.add(igralec);
            if (i % 3 != 0) {
                rating(igralec, 1000 + i, i);
            }
        }
        return igralci;
    }

    private void rating(Igralec igralec, int vrednost, int stTekem) {
        RatingStanje stanje = new RatingStanje(igralec, RatingStanje.SISTEM_TURNIRKO, vrednost);
        stanje.setStTekem(stTekem);
        ratingStanjeRepozitorij.save(stanje);
    }

    @Test
    void javniSeznamPripiseVsakemuNjegovRating() {
        List<Igralec> igralci = igralciZRatingi("Javnitest");

        Map<Long, IgralecJavniDto> poId = igralciStoritev.seznam().stream()
                .collect(Collectors.toMap(IgralecJavniDto::id, Function.identity()));

        for (int i = 0; i < IGRALCEV; i++) {
            IgralecJavniDto dto = poId.get(igralci.get(i).getId());
            if (i % 3 == 0) {
                assertNull(dto.rating(), "igralec brez ratinga ne sme dobiti tujega");
                assertEquals(0, dto.steviloTekem());
            } else {
                assertEquals(1000 + i, dto.rating());
                assertEquals(i, dto.steviloTekem());
            }
        }
    }

    @Test
    void podrobniSeznamPripiseVsakemuNjegovRating() {
        List<Igralec> igralci = igralciZRatingi("Podrobnitest");

        Map<Long, IgralecDto> poId = igralciStoritev.seznamPodrobno().stream()
                .collect(Collectors.toMap(IgralecDto::id, Function.identity()));

        for (int i = 0; i < IGRALCEV; i++) {
            IgralecDto dto = poId.get(igralci.get(i).getId());
            if (i % 3 == 0) {
                assertNull(dto.rating());
                assertEquals(0, dto.steviloTekem());
            } else {
                assertEquals(1000 + i, dto.rating());
                assertEquals(i, dto.steviloTekem());
            }
        }
    }

    @Test
    void podobniObVpisuNosijoVsakSvojRating() {
        Igralec prvi = noviIgralec("Podobnik", "Ratingovec");
        Igralec drugi = noviIgralec("Podobnik", "Ratingovec");
        Igralec brez = noviIgralec("Podobnik", "Ratingovec");
        rating(prvi, 1410, 4);
        rating(drugi, 1720, 9);

        Map<Long, IgralecJavniDto> poId = igralciStoritev
                .najdiPodobne(new PodobniIgralciVnos("Podobnik", "Ratingovec", Spol.MOSKI, null)).stream()
                .map(PodobenIgralecJavniDto::igralec)
                .collect(Collectors.toMap(IgralecJavniDto::id, Function.identity()));

        assertEquals(1410, poId.get(prvi.getId()).rating());
        assertEquals(4, poId.get(prvi.getId()).steviloTekem());
        assertEquals(1720, poId.get(drugi.getId()).rating());
        assertEquals(9, poId.get(drugi.getId()).steviloTekem());
        assertNull(poId.get(brez.getId()).rating());
        assertEquals(0, poId.get(brez.getId()).steviloTekem());
    }

    @Test
    void parCistkeNosiRatingaObehInLeviImaVecTekem() {
        Igralec manj = noviIgralec("Cistka", "Dvojnikovic");
        Igralec vec = noviIgralec("Cistka", "Dvojnikovic");
        Igralec brezA = noviIgralec("Cistka", "Brezratinga");
        Igralec brezB = noviIgralec("Cistka", "Brezratinga");
        rating(manj, 1300, 2);
        rating(vec, 1650, 40);

        List<DvojnikIgralcaDto> pari = dvojnikiStoritev.pari();

        DvojnikIgralcaDto zRatingom = par(pari, manj, vec);
        assertEquals(vec.getId(), zRatingom.levi().id(), "levi je tisti z vec tekmami");
        assertEquals(1650, zRatingom.levi().rating());
        assertEquals(40, zRatingom.levi().steviloTekem());
        assertEquals(1300, zRatingom.desni().rating());
        assertEquals(2, zRatingom.desni().steviloTekem());

        DvojnikIgralcaDto brez = par(pari, brezA, brezB);
        assertNull(brez.levi().rating());
        assertNull(brez.desni().rating());
        assertEquals(brezA.getId(), brez.levi().id(), "pri enakem stevilu tekem je levi manjsi id");
    }

    private static DvojnikIgralcaDto par(List<DvojnikIgralcaDto> pari, Igralec a, Igralec b) {
        String kljuc = Math.min(a.getId(), b.getId()) + "-" + Math.max(a.getId(), b.getId());
        return pari.stream().filter(p -> p.id().equals(kljuc)).findFirst().orElseThrow();
    }
}
