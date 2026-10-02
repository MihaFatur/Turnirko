/* Zapis popravkov datumov Stupe (src/main/resources/uvoz/stupa-popravki.json).
   Popravek brez vira ali z nemogocim datumom bi tiho sel v rating vseh
   igralcev tekme, zato se zapis preveri ob vsakem prevodu, ne sele ob uvozu. */
package si.turnirko.uvoz.stupa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class PopravkiStupeTest {

    private final PopravkiStupe popravki = PopravkiStupe.privzeti();

    /* Stupa ima 1. TOP-8 mlajsih kadetov (61) na dan naknadnega vnosa 31. 8.
       2025; turnir je bil 31. 5. 2025 (koledar ntzs.si). */
    @Test
    void napacenDatumTekmovanjaJePopravljen() {
        PopravkiStupe.Dogodek top8 = popravki.dogodek(61).orElseThrow();
        assertEquals(LocalDate.of(2025, 5, 31), top8.zacetek());
        assertEquals(LocalDate.of(2025, 5, 31), top8.konec());
        assertTrue(top8.vir().contains("ntzs.si"));

        // kvalifikacije za ekipno DP U-17: en dan, ne razpon 22.-23. 11.
        for (long id : new long[] { 160, 161, 162, 163 }) {
            assertEquals(LocalDate.of(2025, 11, 23), popravki.dogodek(id).orElseThrow().zacetek());
        }
        assertTrue(popravki.dogodek(245).isEmpty(), "pravilen dogodek popravka nima");
    }

    /* 1. SNTL zenske 2024/25 (Stupa 169) je bila v Stupo vnesena brez terminov;
       vseh 30 tekem dobi datum iz koledarja liga odbora in porocil zveze, kjer
       je zapisnik, pa iz zapisnika. */
    @Test
    void ligaBrezTerminovDobiTerminTekmoZaTekmo() {
        assertEquals(LocalDateTime.of(2024, 10, 5, 17, 0), popravki.tekma(42751).orElseThrow().cas(),
                "1. krog Logatec - Arrigoni (skeniran zapisnik: 5. 10. 2024 ob 17.00)");
        assertEquals(LocalDateTime.of(2025, 1, 11, 17, 0), popravki.tekma(42776).orElseThrow().cas(),
                "Vrtojba - Kajuh, prestavljeno s 14. 12. 2024");
        assertEquals(LocalDateTime.of(2024, 11, 8, 17, 0), popravki.tekma(42755).orElseThrow().cas(),
                "Kajuh - Arrigoni: zapisnik ima petek 8. 11., koledar soboto 9. 11.");
    }

    /* 2., 3. in 4. SNTL moski ter 2. SNTL zenske 2024/25 (Stupa 157, 168, 167,
       174): vsaka tekma ima termin iz uradnega koledarja NTZS. */
    @Test
    void ostaleLige2425ImajoTerminIzUradnegaKoledarja() {
        // 2. SNTL: jutranji termin 1. kroga potrjuje tudi start_time pri Stupi
        assertEquals(LocalDateTime.of(2024, 10, 5, 10, 0), popravki.tekma(40564).orElseThrow().cas());
        // 3. SNTL: derbi para Zalec I - Sobota v 2. krogu
        assertEquals(LocalDateTime.of(2024, 10, 26, 10, 0), popravki.tekma(41577).orElseThrow().cas());
        // 4. SNTL: glava lista ima 14.12.2025, koledar NTK Savinja 14. 12. 2024
        assertEquals(LocalDateTime.of(2024, 12, 14, 10, 0), popravki.tekma(41384).orElseThrow().cas());
        // 2. SNTL zenske: 5. krog 1. 2. 2025 (tudi koledar ntzs.si)
        assertEquals(LocalDateTime.of(2025, 2, 1, 10, 0), popravki.tekma(44269).orElseThrow().cas());
        assertEquals(30 + 90 + 90 + 90 + 30 + 72, popravki.steviloTekem());
    }

    /* Kolo srecanja je uradni krog iz koledarja, ne krog vira (ta pri ligah
       2024/25 ni kolo). Prestavljena tekma ostane v svojem krogu; tekma, ki ji
       vir pove pravi cas (1. SNTL moski), dobi samo krog in obdrzi svoj cas. */
    @Test
    void koloJeUradniKrog(@org.junit.jupiter.api.io.TempDir java.nio.file.Path mapa) throws Exception {
        assertEquals(1, popravki.tekma(40564).orElseThrow().kolo(), "2. SNTL: derbi Savinja ZIT - Tempo v 1. krogu");
        PopravkiStupe.Tekma prestavljena = popravki.tekma(42776).orElseThrow();
        assertEquals(7, prestavljena.kolo(), "Vrtojba - Kajuh je tekma 7. kroga");
        assertEquals(LocalDateTime.of(2025, 1, 11, 17, 0), prestavljena.cas(), "odigrana pa 11. 1. 2025");
        PopravkiStupe.Tekma samoKolo = popravki.tekma(31898).orElseThrow();
        assertEquals(2, samoKolo.kolo());
        assertEquals(null, samoKolo.cas(), "1. SNTL moski: cas ima vir");

        for (String ime : PosnetekDogodka.DATOTEKE) {
            java.nio.file.Files.writeString(mapa.resolve(ime), "{\"data\": [], \"matches\": []}");
        }
        com.fasterxml.jackson.databind.ObjectMapper json = new com.fasterxml.jackson.databind.ObjectMapper();
        PosnetekDogodka prvaLiga = PosnetekDogodka.beri(mapa, json.readTree(
                "{\"id\": 100, \"event_start_date\": \"2024-10-05\", \"event_end_date\": \"2025-04-19\"}"),
                "2024/25", LocalDate.of(2025, 5, 1));
        com.fasterxml.jackson.databind.JsonNode tekma = json.readTree(
                "{\"id\": 31898, \"start_time\": \"2024-10-05T10:30:00\"}");
        assertEquals(2, prvaLiga.koloTekme(tekma, 13), "krog vira (13) ne velja");
        assertEquals(LocalDateTime.of(2024, 10, 5, 10, 30), prvaLiga.casTekme(tekma), "cas vira ostane");
        assertEquals(5, prvaLiga.koloTekme(json.readTree("{\"id\": 1}"), 5), "brez popravka velja krog vira");
    }

    /* Premaknjen zacetek zavrze case tekem vira (ostanek naknadnega vnosa),
       popravljen samo konec (DP 29.-30. 3. 2025, ki ga ima Stupa enodnevnega,
       Stupa 70) pa jih obdrzi. */
    @Test
    void samoKonecDogodkaCasovTekemNeZavrze(@org.junit.jupiter.api.io.TempDir java.nio.file.Path mapa)
            throws Exception {
        for (String ime : PosnetekDogodka.DATOTEKE) {
            java.nio.file.Files.writeString(mapa.resolve(ime), "{\"data\": [], \"matches\": []}");
        }
        com.fasterxml.jackson.databind.ObjectMapper json = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode tekma = json.readTree(
                "{\"id\": 1, \"start_time\": \"2025-03-29T10:00:00\"}");

        PosnetekDogodka dp = PosnetekDogodka.beri(mapa, json.readTree(
                "{\"id\": 70, \"event_start_date\": \"2025-03-29\", \"event_end_date\": \"2025-03-29\"}"),
                "2024/25", LocalDate.of(2025, 4, 1));
        assertEquals(LocalDate.of(2025, 3, 30), dp.konec());
        assertEquals(LocalDateTime.of(2025, 3, 29, 10, 0), dp.casTekme(tekma));

        PosnetekDogodka premaknjen = PosnetekDogodka.beri(mapa, json.readTree(
                "{\"id\": 61, \"event_start_date\": \"2025-08-31\", \"event_end_date\": \"2025-08-31\"}"),
                "2024/25", LocalDate.of(2025, 9, 1));
        assertEquals(null, premaknjen.casTekme(tekma), "cas tekme na dan naknadnega vnosa ne velja");
    }

    /* Vsak popravek ima vir; datumi lezijo v sezoni, ki jo popravljajo. */
    @Test
    void zapisSeNaloziInJeSkladen() {
        PopravkiStupe znova = PopravkiStupe.beri(PopravkiStupe.VIR);
        assertEquals(popravki.steviloTekem(), znova.steviloTekem());
        // vsi popravki tekem so lige 2024/25 (zdaj edine brez terminov pri viru)
        for (PopravkiStupe.Tekma t : popravki.vseTekme()) {
            assertTrue(!t.vir().isBlank());
            assertTrue(t.cas() != null || t.kolo() != null);
            assertTrue(t.cas() == null || (t.cas().isAfter(LocalDateTime.of(2024, 9, 1, 0, 0))
                    && t.cas().isBefore(LocalDateTime.of(2025, 7, 1, 0, 0))), "sezona 2024/25: " + t);
            assertTrue(t.kolo() == null || (t.kolo() >= 1 && t.kolo() <= 18), "kolo: " + t);
        }
    }
}
