/* Prvi uvoz turnirja iz Stupe prevzame prazen turnir iz koledarja namesto
   novega (PrevzemKoledarja): predogled ponudi kandidate in predlaga pravega,
   uvoz zapise v isti turnir in drugega turnirja ne ustvari.

   Stupa je nadomescena (posnetek dogodka 245 iz testnih virov), zato tece
   tudi predogled brez omrezja. Namenoma brez @Transactional (kot
   UvozStupeTokTest): uvoz ima svoje transakcije; test za seboj pocisti sam. */
package si.turnirko.uvoz.stupa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.List;
import java.util.zip.GZIPInputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import si.turnirko.dto.IzidUvozaDto;
import si.turnirko.dto.PredogledUvozaDto;
import si.turnirko.dto.UvozZahtevaDto;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.modeli.UvozZagon;

@SpringBootTest
@ActiveProfiles("test")
class UvozPrevzemKoledarjaTest {

    private static final Path POSNETKI;
    private static final LocalDate DAN = LocalDate.of(2026, 9, 12); // dogodek 245

    static {
        try {
            POSNETKI = Files.createTempDirectory("turnirko-posnetki");
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void mapaPosnetkov(DynamicPropertyRegistry r) {
        r.add("turnirko.uvoz.posnetki", POSNETKI::toString);
    }

    @MockitoBean StupaOdjemalec odjemalec;
    @Autowired UvozStupeStoritev uvoz;
    @Autowired JdbcTemplate jdbc;

    private long u15;
    private long u21;
    private long dalec;
    private long zacet;

    @BeforeEach
    void koledarInStupa() throws Exception {
        jdbc.update("INSERT OR IGNORE INTO kraj (postna_st, ime) VALUES (2325, 'Kidričevo')");
        jdbc.update("INSERT INTO klub (id, ime) VALUES (9001, 'NTK Koledar')");
        u15 = turnir("1. OT ZA U-15", DAN, "PRIPRAVA");
        u21 = turnir("1. OT ZA U-21", DAN, "PRIPRAVA");
        dalec = turnir("2. OT ZA U-15", DAN.plusDays(30), "PRIPRAVA");
        zacet = turnir("1. OT ZA U-15 (zreb)", DAN, "V_TEKU");

        ObjectMapper json = new ObjectMapper();
        JsonNode vrstica = json.readTree(beri("dogodek.json"));
        JsonNode sezona = json.readTree(beri("sezona.json"));
        when(odjemalec.dogodki()).thenReturn(List.of(vrstica));
        when(odjemalec.sezone()).thenReturn(List.of(sezona));
        doAnswer(k -> {
            razpakiraj(k.getArgument(2));
            return null;
        }).when(odjemalec).posnemiDogodek(any(), any(), any());
    }

    @Test
    void predogledPonudiKandidateInPredlagaIstoKategorijo() {
        PredogledUvozaDto p = uvoz.predogled(245, null);

        assertEquals(List.of(u15, u21), p.kandidatiPrevzema().stream()
                .map(PredogledUvozaDto.KandidatPrevzema::id).toList(),
                "isti dan, prazna, v pripravi; dalec in zacet nista kandidata");
        assertEquals(u15, p.predlaganPrevzem());
        assertEquals("Kidričevo", p.kandidatiPrevzema().get(0).kraj());
    }

    @Test
    void uvozPrevzameTurnirInNeUstvariDrugega() {
        PredogledUvozaDto p = uvoz.predogled(245, null);
        int prej = stevilo("SELECT COUNT(*) FROM turnir");

        IzidUvozaDto izid = uvoz.uvozi(245, new UvozZahtevaDto(p.posnetek(), List.of(), false, u15));

        assertEquals(UvozZagon.Izid.USPEH, izid.izid(), () -> izid.napaka() + " " + izid.porocilo());
        assertEquals(u15, izid.idTurnir());
        assertEquals(prej, stevilo("SELECT COUNT(*) FROM turnir"), "nov turnir ni nastal");
        assertEquals(1, stevilo("SELECT COUNT(*) FROM turnir WHERE id = " + u15
                + " AND vir = 'STUPA' AND ime LIKE '1. OT ZA KADETE U-15%' AND id_klub_lastnik IS NULL"
                + " AND id_ustvaril IS NULL AND postna_st = 2325"),
                "ime in vir iz Stupe, lastnik gre, kraj iz koledarja ostane");
        assertTrue(stevilo("SELECT COUNT(*) FROM dogodek WHERE id_turnir = " + u15) > 0);
        assertEquals(1, izid.porocilo().stevci().get("turnirjev iz koledarja, ki jih je prevzel uvoz"));

        // drugi uvoz gre po povezavi - kandidatov ni vec, prevzem ni dovoljen
        PredogledUvozaDto znova = uvoz.predogled(245, null);
        assertTrue(znova.kandidatiPrevzema().isEmpty());
        assertNull(znova.predlaganPrevzem());
        assertThrows(NeveljavenVnosIzjema.class,
                () -> uvoz.uvozi(245, new UvozZahtevaDto(znova.posnetek(), List.of(), true, u21)));
    }

    @Test
    void prevzetiSeSmeSamoKandidata() {
        PredogledUvozaDto p = uvoz.predogled(245, null);
        for (long tuj : List.of(dalec, zacet)) {
            assertThrows(NeveljavenVnosIzjema.class,
                    () -> uvoz.uvozi(245, new UvozZahtevaDto(p.posnetek(), List.of(), false, tuj)));
        }
        assertEquals(0, stevilo("SELECT COUNT(*) FROM uvoz_zagon"), "zavrnjena zahteva ne pusti zagona");
    }

    /* Brez izbire uvoz naredi nov turnir kot doslej; koledar ostane nedotaknjen. */
    @Test
    void brezPrevzemaNastaneNovTurnir() {
        PredogledUvozaDto p = uvoz.predogled(245, null);
        int prej = stevilo("SELECT COUNT(*) FROM turnir");

        IzidUvozaDto izid = uvoz.uvozi(245, new UvozZahtevaDto(p.posnetek(), List.of(), false));

        assertEquals(UvozZagon.Izid.USPEH, izid.izid());
        assertEquals(prej + 1, stevilo("SELECT COUNT(*) FROM turnir"));
        assertEquals(0, stevilo("SELECT COUNT(*) FROM turnir WHERE id = " + u15 + " AND vir IS NOT NULL"));
    }

    @AfterEach
    void pocisti() {
        for (String sql : List.of(
                "DELETE FROM rating_zgodovina", "DELETE FROM rating_stanje", "DELETE FROM niz_srecanja",
                "DELETE FROM tekma_srecanja", "DELETE FROM postava_srecanja", "DELETE FROM srecanje",
                "DELETE FROM serija_koncnice", "DELETE FROM niz",
                "UPDATE tekma SET id_izvor_tekma_1 = NULL, id_izvor_tekma_2 = NULL, id_prenesena = NULL",
                "DELETE FROM tekma", "DELETE FROM prijava", "DELETE FROM kader_ekipe", "DELETE FROM ekipa",
                "DELETE FROM skupina", "DELETE FROM dogodek", "DELETE FROM turnir", "DELETE FROM liga",
                "DELETE FROM zunanja_povezava", "DELETE FROM uvoz_zagon", "DELETE FROM igralec",
                "DELETE FROM klub", "DELETE FROM kraj WHERE postna_st = 2325")) {
            jdbc.update(sql);
        }
    }

    // ---------- Pomozno ----------

    private long turnir(String ime, LocalDate dan, String status) {
        jdbc.update("INSERT INTO turnir (ime, postna_st, datum_zacetka, datum_konca, status, verzija, ustvarjen_ob,"
                + " id_klub_lastnik, raven) VALUES (?, 2325, ?, ?, ?, 0, '2026-10-23', 9001, 'URADNO')",
                ime, dan.toString(), dan.toString(), status);
        return jdbc.queryForObject("SELECT id FROM turnir WHERE ime = ? AND datum_zacetka = ?", Long.class,
                ime, dan.toString());
    }

    private int stevilo(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    private static byte[] beri(String ime) throws Exception {
        Path gz = Path.of(UvozPrevzemKoledarjaTest.class.getResource("/stupa/245/" + ime + ".gz").toURI());
        try (InputStream in = new GZIPInputStream(Files.newInputStream(gz))) {
            return in.readAllBytes();
        }
    }

    private static void razpakiraj(Path mapa) throws Exception {
        Files.createDirectories(mapa);
        Path izvor = Path.of(UvozPrevzemKoledarjaTest.class.getResource("/stupa/245").toURI());
        try (var datoteke = Files.list(izvor)) {
            for (Path gz : datoteke.toList()) {
                try (InputStream in = new GZIPInputStream(Files.newInputStream(gz))) {
                    Files.copy(in, mapa.resolve(gz.getFileName().toString().replace(".gz", "")),
                            StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
