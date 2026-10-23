/* Predlog prevzema praznega turnirja iz koledarja (PrevzemKoledarja): na isti
   dan je vec kandidatov, predlaga se samo tisti, ki je po imenu nedvoumen. */
package si.turnirko.uvoz.stupa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import si.turnirko.modeli.Turnir;

class PrevzemKoledarjaTest {

    private static final LocalDate DAN = LocalDate.of(2026, 11, 28);
    private final AtomicLong id = new AtomicLong();

    @Test
    void kategorijaLociDvaTurnirjaIstegaDne() {
        Turnir u15 = turnir("2. OT ZA U-15", DAN);
        Turnir u21 = turnir("2. OT ZA U-21", DAN);
        assertEquals(u15, predlog("2. OT ZA KADETE U-15, ŽALEC, 28. 11. 2026", u21, u15));
        assertEquals(u21, predlog("2. OT ZA ČLANE U-21, ŽALEC, 28. 11. 2026", u15, u21));
    }

    @Test
    void drugaKategorijaAliZaporednaStevilkaSeNePredlagaNikoli() {
        assertTrue(PrevzemKoledarja.ujemanje("2. OT ZA KADETE U-15", "2. OT ZA U-21") < 0);
        assertTrue(PrevzemKoledarja.ujemanje("1. TOP ZA U-19", "2. TOP ZA U-19") < 0);
        assertNull(predlog("2. OT ZA KADETE U-15", turnir("2. OT ZA U-21", DAN)));
    }

    @Test
    void okrajsaveZvezeSeUjemajo() {
        Turnir kval = turnir("KVALIFIKACIJE ZA EKIPNO DP U-19", DAN);
        Turnir dp = turnir("DP ZA U-19", DAN);
        assertEquals(kval, predlog("VEČ KRAJEV KVAL. EKIPNO DP - U19", dp, kval));
        assertEquals(dp, predlog("36. DP ZA MLADINCE U-19, CELJE", kval, dp));
    }

    /* Klubski turnir istega dne nima s turnirjem zveze nic skupnega v imenu. */
    @Test
    void brezUjemanjaImenaNiPredloga() {
        assertNull(predlog("1. OT ZA ČLANE IN ČLANICE, MUTA", turnir("Savinja Tour – Jesenski turnir", DAN)));
    }

    @Test
    void dvaEnakaKandidataNistaLocljiva() {
        assertNull(predlog("2. OT ZA KADETE U-15", turnir("2. OT ZA U-15", DAN), turnir("2. OT ZA U-15", DAN)));
    }

    @Test
    void obEnakemImenuOdlociBlizjiDatum() {
        Turnir tocen = turnir("2. OT ZA U-15", DAN);
        Turnir premaknjen = turnir("2. OT ZA U-15", DAN.plusDays(2));
        assertEquals(tocen, predlog("2. OT ZA KADETE U-15", premaknjen, tocen));
    }

    private Turnir predlog(String imeVira, Turnir... prazni) {
        return PrevzemKoledarja.predlog(PrevzemKoledarja.razvrsti(imeVira, DAN, List.of(prazni)));
    }

    private Turnir turnir(String ime, LocalDate zacetek) {
        Turnir t = new Turnir();
        ReflectionTestUtils.setField(t, "id", id.incrementAndGet());
        t.setIme(ime);
        t.setDatumZacetka(zacetek);
        t.setDatumKonca(zacetek);
        return t;
    }
}
