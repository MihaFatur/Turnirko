/* Celoten tok uvoza nad posnetkom, kot ga sprozi admin: zapis se POTRDI,
   rating se preracuna, dnevnik zabelezi zagon - in drugi uvoz istega
   posnetka ne naredi nicesar (BREZ_SPREMEMB).

   Namenoma brez @Transactional: uvoz uporablja svoje transakcije (zapis,
   dnevnik, preracun v paketih) in samo tako se pokaze, da se med sabo ne
   zaklenejo (SQLite ima enega pisca). Test zato za seboj pocisti sam. */
package si.turnirko.uvoz.stupa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.zip.GZIPInputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import si.turnirko.dto.IzidUvozaDto;
import si.turnirko.dto.UvozZahtevaDto;
import si.turnirko.modeli.UvozZagon;
import si.turnirko.repozitoriji.UvozZagonRepozitorij;

@SpringBootTest
@ActiveProfiles("test")
class UvozStupeTokTest {

    private static final Path POSNETKI;

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

    @Autowired UvozStupeStoritev uvoz;
    @Autowired UvozZagonRepozitorij zagoni;
    @Autowired JdbcTemplate jdbc;

    @Test
    void uvozSePotrdiPreracunaRatingInSeNePonovi() throws Exception {
        String oznaka = "245-20260913-080000";
        razpakiraj(245, POSNETKI.resolve(oznaka));

        IzidUvozaDto izid = uvoz.uvozi(245, new UvozZahtevaDto(oznaka, List.of(), false));
        assertEquals(UvozZagon.Izid.USPEH, izid.izid(), () -> izid.napaka() + " " + izid.porocilo());
        assertNull(izid.napaka());
        assertNotNull(izid.idTurnir());
        assertTrue(izid.preracunanihTekem() > 150, "obracunane so vse odigrane tekme (brez prostih prehodov)");
        Integer zapisov = jdbc.queryForObject(
                "SELECT COUNT(*) FROM rating_zgodovina z JOIN tekma t ON t.id = z.id_tekma", Integer.class);
        assertEquals(2 * izid.preracunanihTekem(), zapisov, "vsaka tekma dva zapisa");

        IzidUvozaDto ponovno = uvoz.uvozi(245, new UvozZahtevaDto(oznaka, List.of(), false));
        assertEquals(UvozZagon.Izid.BREZ_SPREMEMB, ponovno.izid());
        assertEquals(List.of(UvozZagon.Izid.BREZ_SPREMEMB, UvozZagon.Izid.USPEH),
                zagoni.findAll().stream().sorted((a, b) -> Long.compare(b.getId(), a.getId()))
                        .map(UvozZagon::getIzid).toList());
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
                "DELETE FROM klub")) {
            jdbc.update(sql);
        }
    }

    private static void razpakiraj(long id, Path mapa) throws Exception {
        Files.createDirectories(mapa);
        Path izvor = Path.of(UvozStupeTokTest.class.getResource("/stupa/" + id).toURI());
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
