package si.turnirko.prenos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import si.turnirko.prenos.Pretvorba.Opombe;
import si.turnirko.prenos.Pretvorba.Tip;
import si.turnirko.prenos.Pretvorba.Zavrnitev;

/* Pravila pretvorbe vrednosti SQLite -> PostgreSQL: kar se ne ujema, se zavrne
   z razlogom, nikoli se ne pretvori v NULL ali priblizek. */
class PretvorbaTest {

    private final Opombe opombe = new Opombe();

    private Object pretvori(Tip tip, Object vrednost) {
        return Pretvorba.izSqlite(tip, vrednost, "t.s", opombe);
    }

    @Test
    void logicneVrednostiSoSamoNicInEna() {
        assertEquals(false, pretvori(Tip.LOGICNO, 0L));
        assertEquals(true, pretvori(Tip.LOGICNO, 1));
        assertThrows(Zavrnitev.class, () -> pretvori(Tip.LOGICNO, 2L));
        assertThrows(Zavrnitev.class, () -> pretvori(Tip.LOGICNO, "true"));
    }

    @Test
    void celaStevilaMorajoUstrezatiObsegu() {
        assertEquals(Long.MAX_VALUE, pretvori(Tip.CELO64, Long.MAX_VALUE));
        assertEquals(7L, pretvori(Tip.CELO32, 7));
        assertThrows(Zavrnitev.class, () -> pretvori(Tip.CELO32, 3_000_000_000L));
        assertThrows(Zavrnitev.class, () -> pretvori(Tip.CELO64, 1.5));
        assertThrows(Zavrnitev.class, () -> pretvori(Tip.CELO64, "12"));
    }

    @Test
    void decimalnoPrejmeDoubleInCeloStevilo() {
        assertEquals(1.25, pretvori(Tip.DECIMALNO, 1.25));
        assertEquals(3.0, pretvori(Tip.DECIMALNO, 3L));
        assertEquals(1L, opombe.stevci().get("t.s : celo stevilo v stolpcu double"));
        assertThrows(Zavrnitev.class, () -> pretvori(Tip.DECIMALNO, "1,5"));
    }

    @Test
    void besediloZavrneNulInNebesedilo() {
        assertEquals("Čeh", pretvori(Tip.BESEDILO, "Čeh"));
        assertThrows(Zavrnitev.class, () -> pretvori(Tip.BESEDILO, "a\0b"));
        assertThrows(Zavrnitev.class, () -> pretvori(Tip.BESEDILO, 5L));
    }

    @Test
    void datumJeSamoLetoMesecDan() {
        assertEquals(LocalDate.of(2026, 10, 5), pretvori(Tip.DATUM, "2026-10-05"));
        assertThrows(Zavrnitev.class, () -> pretvori(Tip.DATUM, "2026-10-05T18:00"));
        assertThrows(Zavrnitev.class, () -> pretvori(Tip.DATUM, "2026-02-30"));
        assertThrows(Zavrnitev.class, () -> pretvori(Tip.DATUM, "5. 10. 2026"));
    }

    @Test
    void casVsehStirihOblikPostaneLocalDateTime() {
        assertEquals(LocalDateTime.of(2026, 10, 5, 0, 0), pretvori(Tip.CAS, "2026-10-05"));
        assertEquals(LocalDateTime.of(2026, 10, 5, 18, 30), pretvori(Tip.CAS, "2026-10-05T18:30"));
        assertEquals(LocalDateTime.of(2026, 10, 5, 8, 15, 20), pretvori(Tip.CAS, "2026-10-05 08:15:20"));
        assertEquals(LocalDateTime.of(2026, 10, 5, 8, 15, 20, 123_000_000),
                pretvori(Tip.CAS, "2026-10-05T08:15:20.123"));
    }

    @Test
    void decimalkePodMilisekundoSeOdrezejoInStejejo() {
        Opombe lokalne = new Opombe();
        Object cas = Pretvorba.izSqlite(Tip.CAS, "2026-10-05T08:15:20.123456789", "uvoz.zacetek", lokalne);
        assertEquals(LocalDateTime.of(2026, 10, 5, 8, 15, 20, 123_000_000), cas);
        assertEquals(1L, lokalne.stevci().get("uvoz.zacetek : odrezano na milisekunde"));
        // celi milisekundi nista odrezek
        Pretvorba.izSqlite(Tip.CAS, "2026-10-05T08:15:20.500", "uvoz.zacetek", lokalne);
        assertEquals(1L, lokalne.stevci().get("uvoz.zacetek : odrezano na milisekunde"));
    }

    @Test
    void casKiGaNiMogoceRazbratiSeZavrne() {
        assertThrows(Zavrnitev.class, () -> pretvori(Tip.CAS, "včeraj"));
        assertThrows(Zavrnitev.class, () -> pretvori(Tip.CAS, "2026-13-01T10:00"));
        assertThrows(Zavrnitev.class, () -> pretvori(Tip.CAS, 1_790_000_000_000L));
    }

    @Test
    void bajtiSoLahkoSamoBlob() {
        byte[] slika = {(byte) 0x89, 0x50, 0x00, 0x5c};
        assertEquals(slika, pretvori(Tip.BAJTI, slika));
        assertThrows(Zavrnitev.class, () -> pretvori(Tip.BAJTI, "besedilo"));
        StringBuilder sb = new StringBuilder();
        Pretvorba.zapisi(sb, slika);
        // v besedilnem COPY je "\x" zapisan z dvojno poševnico
        assertEquals("\\\\x8950005c", sb.toString());
    }

    @Test
    void kanonicniZapisUbezneZnakeCopy() {
        StringBuilder sb = new StringBuilder();
        Pretvorba.zapisi(sb, "a\tb\nc\\d\re");
        assertEquals("a\\tb\\nc\\\\d\\re", sb.toString());
        sb.setLength(0);
        Pretvorba.zapisi(sb, null);
        assertEquals("\\N", sb.toString());
        sb.setLength(0);
        Pretvorba.zapisi(sb, LocalDateTime.of(2026, 10, 5, 8, 5));
        assertEquals("2026-10-05 08:05:00.000", sb.toString());
        sb.setLength(0);
        Pretvorba.zapisi(sb, true);
        assertEquals("t", sb.toString());
    }
}
