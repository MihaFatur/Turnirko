/* Uvoz stare strani NTZS nad zapisi v obliki pretvorbe (pretvori.mjs).

   Preverja, kar je revizija oktobra 2026 nasla narobe in kar je zdaj del
   uvoza: srecanje brez borbe, tekma srecanja brez boja, prenesen izid
   ekipnega DP (brez posamicnih tekem - sicer gre ista tekma dvakrat v
   rating), tocke nizov ligaskih tekem, dvojice turnirjev kot ena prijava
   para, kategorija discipline in ponovni uvoz NA MESTU (id-ji tekmovanj
   ostanejo, vsebina se ne podvoji). */
package si.turnirko.uvoz.stara;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.modeli.Liga;
import si.turnirko.modeli.Turnir;
import si.turnirko.repozitoriji.DogodekRepozitorij;
import si.turnirko.repozitoriji.EkipaRepozitorij;
import si.turnirko.repozitoriji.IgralecRepozitorij;
import si.turnirko.repozitoriji.KaderEkipeRepozitorij;
import si.turnirko.repozitoriji.KlubRepozitorij;
import si.turnirko.repozitoriji.LigaRepozitorij;
import si.turnirko.repozitoriji.NizRepozitorij;
import si.turnirko.repozitoriji.NizSrecanjaRepozitorij;
import si.turnirko.repozitoriji.PostavaSrecanjaRepozitorij;
import si.turnirko.repozitoriji.PrijavaRepozitorij;
import si.turnirko.repozitoriji.SkupinaRepozitorij;
import si.turnirko.repozitoriji.SrecanjeRepozitorij;
import si.turnirko.repozitoriji.TekmaRepozitorij;
import si.turnirko.repozitoriji.TekmaSrecanjaRepozitorij;
import si.turnirko.repozitoriji.TurnirRepozitorij;
import si.turnirko.repozitoriji.ZunanjaPovezavaRepozitorij;
import si.turnirko.storitve.PreracunRatingaStoritev;
import si.turnirko.storitve.RazvrstitevStoritev;
import si.turnirko.storitve.VrstaRatinskeTekme;
import si.turnirko.uvoz.SifrantiUvoz;
import si.turnirko.uvoz.UvozPorocilo;
import si.turnirko.uvoz.ZbirnikSifrantov;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StaraUvozTest {

    @Autowired KlubRepozitorij klubi;
    @Autowired IgralecRepozitorij igralci;
    @Autowired TurnirRepozitorij turnirji;
    @Autowired DogodekRepozitorij dogodki;
    @Autowired SkupinaRepozitorij skupine;
    @Autowired PrijavaRepozitorij prijave;
    @Autowired TekmaRepozitorij tekme;
    @Autowired NizRepozitorij nizi;
    @Autowired LigaRepozitorij lige;
    @Autowired EkipaRepozitorij ekipe;
    @Autowired KaderEkipeRepozitorij kadri;
    @Autowired SrecanjeRepozitorij srecanja;
    @Autowired PostavaSrecanjaRepozitorij postave;
    @Autowired TekmaSrecanjaRepozitorij tekmeSrecanj;
    @Autowired NizSrecanjaRepozitorij niziSrecanj;
    @Autowired ZunanjaPovezavaRepozitorij povezave;
    @Autowired RazvrstitevStoritev razvrstitev;
    @Autowired PreracunRatingaStoritev preracun;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;

    private final ObjectMapper json = new ObjectMapper();
    private UvozPorocilo porocilo;
    private SifrantiUvoz sifranti;

    @BeforeEach
    void sifrant() {
        ZbirnikSifrantov z = new ZbirnikSifrantov();
        z.dodajKlub("stara:ntk_a", "NTK Testni A", null);
        z.dodajKlub("stara:ntk_b", "NTK Testni B", null);
        String[][] osebe = {
                {"901", "Ana", "Prva", "ZENSKI", "ntk_a"}, {"902", "Bor", "Drugi", "MOSKI", "ntk_a"},
                {"903", "Cene", "Tretji", "MOSKI", "ntk_a"}, {"904", "Dan", "Cetrti", "MOSKI", "ntk_b"},
                {"905", "Eva", "Peta", "ZENSKI", "ntk_b"}, {"906", "Fran", "Sesti", "MOSKI", "ntk_b"}};
        for (String[] o : osebe) {
            z.dodajIgralca(new ZbirnikSifrantov.SurovIgralec("stara:" + o[0], o[1] + " " + o[2], o[1], o[2],
                    LocalDate.of(2001, 1, 1), true, o[3], null, "SLO", "stara:" + o[4], LocalDate.of(2016, 1, 1)));
        }
        porocilo = new UvozPorocilo();
        sifranti = new SifrantiUvoz(klubi, igralci, povezave, porocilo, l -> Optional.empty());
        sifranti.uvozi(z);
    }

    private StaraLigeUvoz ligeUvoz() {
        return new StaraLigeUvoz(lige, ekipe, kadri, srecanja, postave, tekmeSrecanj, niziSrecanj, povezave,
                sifranti, porocilo);
    }

    private StaraTurnirjiUvoz turnirjiUvoz() {
        return new StaraTurnirjiUvoz(turnirji, dogodki, skupine, prijave, tekme, nizi, povezave, razvrstitev,
                sifranti, porocilo);
    }

    private static String tekma(int st, String d, String g, int n1, int n2, String tocke) {
        return """
                {"st": %d, "tip": "POSAMICNA", "pozD": "%s", "pozG": "%s", "igralciD": ["%s"], "igralciG": ["%s"],
                 "nizi1": %d, "nizi2": %d, "tocke": %s}""".formatted(st, st == 1 ? "A" : "B", st == 1 ? "X" : "Y",
                d, g, n1, n2, tocke);
    }

    /* Liga s stirimi srecanji: odigrano (z nizi), brez borbe, s tekmo brez
       boja in prenesen izid iz druge podlige. */
    private JsonNode liga() throws Exception {
        String odigrano = """
                {"id": "test_ak1-01-01-02", "datum": "2016-01-10", "ura": "10:00", "izidD": 2, "izidG": 0,
                 "domaci": "t_a", "gost": "t_b", "tekme": [%s, %s]}"""
                .formatted(tekma(1, "902", "904", 3, 0, "[[11,5],[11,3],[11,5]]"),
                        tekma(2, "903", "906", 3, 2, "[[8,11],[11,5],[15,17],[11,6],[11,9]]"));
        String brezBorbe = """
                {"id": "test_ak1-02-01-02", "datum": "2016-01-17", "ura": "10:00", "izidD": 5, "izidG": 0,
                 "brezBoja": true, "domaci": "t_a", "gost": "t_b", "tekme": []}""";
        String zBrezBoja = """
                {"id": "test_ak1-03-01-02", "datum": "2016-01-24", "ura": "10:00", "izidD": 1, "izidG": 1,
                 "domaci": "t_b", "gost": "t_a", "tekme": [%s,
                 {"st": 2, "tip": "POSAMICNA", "pozD": "B", "pozG": "Y", "igralciD": [], "igralciG": ["903"],
                  "nizi1": 0, "nizi2": 3, "brezBoja": true, "zmagovalec": "G"}]}"""
                .formatted(tekma(1, "904", "902", 3, 1, "[[11,9],[9,11],[11,7],[11,4]]"));
        String preneseno = """
                {"id": "test_ak2-01-01-02", "datum": "2016-01-10", "ura": "12:00", "izidD": 0, "izidG": 2,
                 "domaci": "t_b", "gost": "t_a", "prenesenoIz": "test_ak2-01-01-02", "tekme": [%s]}"""
                .formatted(tekma(1, "906", "903", 0, 3, "[[4,11],[7,11],[5,11]]"));
        return json.readTree("""
                {"id": "test_ak1", "sezona": "2015/16", "ime": "Ekipno DP testno", "spol": "MOSKI",
                 "ekipe": [{"slug": "t_a", "ime": "NTK Testni A", "klub": "ntk_a", "kader": ["902", "903"]},
                           {"slug": "t_b", "ime": "NTK Testni B", "klub": "ntk_b", "kader": ["904", "906"]}],
                 "kola": [{"st": 1, "datum": "2016-01-10", "srecanja": [%s, %s]},
                          {"st": 2, "datum": "2016-01-17", "srecanja": [%s]},
                          {"st": 3, "datum": "2016-01-24", "srecanja": [%s]}]}"""
                .formatted(odigrano, preneseno, brezBorbe, zBrezBoja));
    }

    private int stej(String sql, Object... parametri) {
        return jdbc.queryForObject(sql, Integer.class, parametri);
    }

    @Test
    void ligaZapiseBrezBorbePreneseniIzidInNize() throws Exception {
        Liga liga = ligeUvoz().uvozi(liga(), null);
        em.flush();
        long id = liga.getId();

        assertEquals(4, stej("SELECT count(*) FROM srecanje WHERE id_liga = ?", id));
        assertEquals(1, stej("SELECT count(*) FROM srecanje WHERE id_liga = ? AND brez_boja = 1", id));
        assertEquals(0, stej("SELECT count(*) FROM tekma_srecanja t JOIN srecanje s ON s.id = t.id_srecanje"
                + " WHERE s.id_liga = ? AND s.brez_boja = 1", id), "srecanje brez borbe nima tekem");
        assertEquals(1, stej("SELECT count(*) FROM srecanje WHERE id_liga = ? AND prenesen = 1", id));
        assertEquals(0, stej("SELECT count(*) FROM tekma_srecanja t JOIN srecanje s ON s.id = t.id_srecanje"
                + " WHERE s.id_liga = ? AND s.prenesen = 1", id), "prenesen izid nima posamicnih tekem");
        assertEquals(1, stej("SELECT count(*) FROM tekma_srecanja t JOIN srecanje s ON s.id = t.id_srecanje"
                + " WHERE s.id_liga = ? AND t.izid_tip = 'BREZ_BOJA'", id));
        assertEquals(3 + 5 + 4, stej("SELECT count(*) FROM niz_srecanja n JOIN tekma_srecanja t"
                + " ON t.id = n.id_tekma_srecanja JOIN srecanje s ON s.id = t.id_srecanje WHERE s.id_liga = ?", id),
                "tocke vseh odigranih tekem");

        // v rating gredo samo tri odigrane tekme: ne tekma brez boja ne prenesena
        Set<Long> ratinske = preracun.vsaVrsta().stream().filter(VrstaRatinskeTekme::ligaska)
                .map(VrstaRatinskeTekme::id).collect(Collectors.toSet());
        List<Long> vLigi = jdbc.queryForList("SELECT t.id FROM tekma_srecanja t JOIN srecanje s"
                + " ON s.id = t.id_srecanje WHERE s.id_liga = ? AND t.izid_tip = 'IGRANO'", Long.class, id);
        assertEquals(3, vLigi.size());
        assertTrue(ratinske.containsAll(vLigi));
        assertEquals(3, ratinske.stream().filter(vLigi::contains).count());
        assertFalse(ratinske.containsAll(jdbc.queryForList("SELECT t.id FROM tekma_srecanja t JOIN srecanje s"
                + " ON s.id = t.id_srecanje WHERE s.id_liga = ? AND t.izid_tip = 'BREZ_BOJA'", Long.class, id)));
    }

    /* Zapisniki 2012/13 imajo neodigrane nize kot "9:0". Nizi, ki ne
       prestanejo pravil vnosa, se ne zapisejo - izid tekme ostane. */
    @Test
    void nizi_kiSeNeUjemajoZIzidomSeNeZapisejo() throws Exception {
        JsonNode vir = json.readTree("""
                {"id": "test_2012", "sezona": "2012/13", "ime": "1. SNTL testno", "spol": "MOSKI",
                 "ekipe": [{"slug": "t_a", "ime": "NTK Testni A", "klub": "ntk_a", "kader": []},
                           {"slug": "t_b", "ime": "NTK Testni B", "klub": "ntk_b", "kader": []}],
                 "kola": [{"st": 1, "datum": "2012-09-22", "srecanja": [
                   {"id": "test_2012-01-01-01", "datum": "2012-09-22", "izidD": 2, "izidG": 0,
                    "domaci": "t_a", "gost": "t_b", "tekme": [%s, %s]}]}]}"""
                .formatted(tekma(1, "902", "904", 3, 0, "[[11,3],[11,7],[11,9],[9,0]]"),
                        tekma(2, "903", "906", 3, 1, "[[11,5],[9,11],[11,7],[11,8]]")));
        long id = ligeUvoz().uvozi(vir, null).getId();
        em.flush();

        assertEquals(2, stej("SELECT count(*) FROM tekma_srecanja t JOIN srecanje s ON s.id = t.id_srecanje"
                + " WHERE s.id_liga = ? AND t.izid_tip = 'IGRANO'", id), "obe tekmi ostaneta");
        assertEquals(4, stej("SELECT count(*) FROM niz_srecanja n JOIN tekma_srecanja t ON t.id = n.id_tekma_srecanja"
                + " JOIN srecanje s ON s.id = t.id_srecanje WHERE s.id_liga = ?", id),
                "samo nizi druge tekme; prva ima neodigran niz 9:0");
    }

    /* Popravek na mestu: id lige ostane, povezava je ena, vsebina ni podvojena. */
    @Test
    void ponovniUvozLigeNaMestu() throws Exception {
        Liga prvic = ligeUvoz().uvozi(liga(), null);
        em.flush();
        long id = prvic.getId();
        int tekemPrej = stej("SELECT count(*) FROM tekma_srecanja t JOIN srecanje s ON s.id = t.id_srecanje"
                + " WHERE s.id_liga = ?", id);

        CiscenjeStare.liga(em, id);
        em.clear();
        Liga drugic = ligeUvoz().uvozi(liga(), lige.findById(id).orElseThrow());
        em.flush();

        assertEquals(id, drugic.getId());
        assertEquals(1, stej("SELECT count(*) FROM zunanja_povezava WHERE vir = 'STARA_NTZS' AND vrsta = 'LIGA'"
                + " AND zunanji_id = 'test_ak1'"));
        assertEquals(2, stej("SELECT count(*) FROM ekipa WHERE id_liga = ?", id));
        assertEquals(4, stej("SELECT count(*) FROM srecanje WHERE id_liga = ?", id));
        assertEquals(tekemPrej, stej("SELECT count(*) FROM tekma_srecanja t JOIN srecanje s"
                + " ON s.id = t.id_srecanje WHERE s.id_liga = ?", id));
    }

    private JsonNode turnir() throws Exception {
        return json.readTree("""
                {"id": "9901", "sezona": "2015", "ime": "DP testno", "kraj": "Ljubljana", "datumOd": "2016-03-19",
                 "datumDo": "2016-03-20", "kategorija": "člani", "discipline": [
                  {"cat": "1", "oznaka": "mladinci posamezno", "spol": "MOSKI", "prijave": [], "uvrstitve": [],
                   "tekme": [
                    {"st": 1, "skupinska": false, "krog": 1, "pozicija": 1, "igralec1": "902", "igralec2": "904",
                     "nizi1": 3, "nizi2": 1, "izid": "IGRANO", "tocke": [[11,5],[9,11],[11,3],[11,8]]},
                    {"st": 2, "skupinska": false, "krog": 1, "pozicija": 2, "igralec1": "903", "igralec2": "906",
                     "nizi1": 3, "nizi2": 0, "izid": "IGRANO", "tocke": [[11,5],[11,3],[11,8]]},
                    {"st": 3, "skupinska": false, "krog": 2, "pozicija": 1, "igralec1": "902", "igralec2": "903",
                     "nizi1": 0, "nizi2": 3, "izid": "IGRANO", "tocke": [[5,11],[3,11],[8,11]]}]},
                  {"cat": "2", "oznaka": "mešane dvojice", "spol": "MESANO", "dvojice": true, "prijave": [],
                   "uvrstitve": [], "tekme": [
                    {"st": 1, "skupinska": false, "krog": 1, "pozicija": 1, "igralec1": "901", "igralec2": "905",
                     "par1": ["902", "901"], "par2": ["904", "905"], "nizi1": 3, "nizi2": 2, "izid": "IGRANO",
                     "tocke": [[11,5],[9,11],[11,3],[8,11],[11,9]]}]}]}""");
    }

    @Test
    void dvojiceSoEnaPrijavaParaInNeGredoVRating() throws Exception {
        Turnir turnir = turnirjiUvoz().uvozi(turnir(), null);
        em.flush();
        long id = turnir.getId();

        assertEquals(1, stej("SELECT count(*) FROM dogodek WHERE id_turnir = ? AND disciplina = 'DVOJICE'"
                + " AND spol_kategorija = 'MESANO' AND sistem_tekmovanja = 'IZLOCILNI'", id));
        assertEquals("mladinci", jdbc.queryForObject("SELECT starostna_kategorija FROM dogodek WHERE id_turnir = ?"
                + " AND disciplina = 'POSAMICNO'", String.class, id), "kategorija discipline, ne turnirja");
        assertEquals(2, stej("SELECT count(*) FROM prijava p JOIN dogodek d ON d.id = p.id_dogodek"
                + " WHERE d.id_turnir = ? AND d.disciplina = 'DVOJICE' AND p.id_igralec_2 IS NOT NULL", id),
                "par je ena prijava z dvema igralcema");
        assertEquals(1, stej("SELECT count(*) FROM tekma t JOIN dogodek d ON d.id = t.id_dogodek"
                + " WHERE d.id_turnir = ? AND d.disciplina = 'DVOJICE'", id));

        Set<Long> ratinske = preracun.vsaVrsta().stream().filter(v -> !v.ligaska())
                .map(VrstaRatinskeTekme::id).collect(Collectors.toSet());
        List<Long> posamicne = jdbc.queryForList("SELECT t.id FROM tekma t JOIN dogodek d ON d.id = t.id_dogodek"
                + " WHERE d.id_turnir = ? AND d.disciplina = 'POSAMICNO' AND t.izid_tip = 'IGRANO'", Long.class, id);
        Long dvojic = jdbc.queryForObject("SELECT t.id FROM tekma t JOIN dogodek d ON d.id = t.id_dogodek"
                + " WHERE d.id_turnir = ? AND d.disciplina = 'DVOJICE'", Long.class, id);
        assertEquals(3, posamicne.size());
        assertTrue(ratinske.containsAll(posamicne));
        assertFalse(ratinske.contains(dvojic));
    }

    @Test
    void ponovniUvozTurnirjaNaMestu() throws Exception {
        long id = turnirjiUvoz().uvozi(turnir(), null).getId();
        em.flush();

        CiscenjeStare.turnir(em, id);
        em.clear();
        Turnir drugic = turnirjiUvoz().uvozi(turnir(), turnirji.findById(id).orElseThrow());
        em.flush();

        assertEquals(id, drugic.getId());
        assertEquals(1, stej("SELECT count(*) FROM zunanja_povezava WHERE vir = 'STARA_NTZS' AND vrsta = 'TURNIR'"
                + " AND zunanji_id = '9901'"));
        assertEquals(2, stej("SELECT count(*) FROM dogodek WHERE id_turnir = ?", id));
        assertEquals(4, stej("SELECT count(*) FROM tekma t JOIN dogodek d ON d.id = t.id_dogodek"
                + " WHERE d.id_turnir = ? AND t.status = 'KONCANA' AND t.izid_tip = 'IGRANO'", id));
    }

    @Test
    void kategorijaDisciplineIzOznake() {
        assertEquals("mlajši kadeti", StaraTurnirjiUvoz.kategorijaDiscipline("mlajše kadetinje posamezno", "člani"));
        assertEquals("kadeti", StaraTurnirjiUvoz.kategorijaDiscipline("kadetinje dvojice", "člani"));
        assertEquals("člani do 21 let", StaraTurnirjiUvoz.kategorijaDiscipline("člani do 21 let dvojice", "člani"));
        assertEquals("člani", StaraTurnirjiUvoz.kategorijaDiscipline("članice posamezno", "mladinci"));
        assertEquals("kadeti", StaraTurnirjiUvoz.kategorijaDiscipline("posamezno", "kadeti"));
    }
}
