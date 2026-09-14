/* Nacrti poizvedb po dnevniku ratinga, ki iscejo po TEKMAH.

   Baza nima statistik (ANALYZE), zato sqlite o stolpcu `sistem` ne ve, da ima
   eno samo vrednost, in enakost na njem sodi za zelo selektivno. Indeksi, ki
   se zacnejo s `sistem`, so zato na videz boljsi od indeksa po tekmi in
   poizvedba "vrstice teh tekem" preleti ves dnevnik. Nobena funkcionalna
   preverba tega ne opazi - izid je isti, le na polni bazi je stokrat
   pocasnejsi. Zato ta test preveri nacrt pravega SQL-a Hibernata.

   Hkrati drzi obratno obljubo: graf profila (najdiZaIgralca) mora se naprej
   iskati po igralcu. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;

import si.turnirko.modeli.RatingStanje;

class NacrtiDnevnikaTest extends IntegracijskiTest {

    private static final String SISTEM = RatingStanje.SISTEM_TURNIRKO;
    private static final List<Long> IDJI = List.of(1L, 2L);

    private static final String PO_TEKMI = "USING INDEX idx_rating_zgodovina_tekma (id_tekma=?)";
    private static final String PO_TEKMI_SRECANJA =
            "USING INDEX idx_rating_zgodovina_tekma_srecanja (id_tekma_srecanja=?)";

    @Autowired private DataSource podatkovniVir;

    /* Prikaz "+16 / -16" ob tekmah (domaca stran, mreza, dvoboj), rating pred
       tekmo na profilu in brisanje obracunov ob ponovnem preracunu. */
    @Test
    void poizvedbePoSeznamuTekemIscejoPoIndeksuTekme() {
        var dnevnik = ratingZgodovinaRepozitorij;
        assertAll(
                bere("spremembeZaTekme", () -> dnevnik.spremembeZaTekme(IDJI, SISTEM), PO_TEKMI),
                bere("ratingiPredTurnirskimi", () -> dnevnik.ratingiPredTurnirskimi(IDJI, SISTEM), PO_TEKMI),
                bere("pobrisiZaTekme", () -> dnevnik.pobrisiZaTekme(SISTEM, IDJI), PO_TEKMI),
                bere("spremembeZaTekmeSrecanja",
                        () -> dnevnik.spremembeZaTekmeSrecanja(IDJI, SISTEM), PO_TEKMI_SRECANJA),
                bere("ratingiPredLigaskimi",
                        () -> dnevnik.ratingiPredLigaskimi(IDJI, SISTEM), PO_TEKMI_SRECANJA),
                bere("pobrisiZaTekmeSrecanja",
                        () -> dnevnik.pobrisiZaTekmeSrecanja(SISTEM, IDJI), PO_TEKMI_SRECANJA));
    }

    /* Zavihek "Zanimivosti": tekme turnirja oz. lige se najdejo prek dogodkov
       in srecanj, dnevnik pa se bere po tekmi - ne preleti se ves po casu, da
       bi se izognil razvrscanju nekaj sto vrstic. */
    @Test
    void spremembeTekmovanjaBerejoDnevnikPoTekmi() {
        var dnevnik = ratingZgodovinaRepozitorij;
        assertAll(
                bere("spremembeZaTurnir", () -> dnevnik.spremembeZaTurnir(1L, SISTEM), PO_TEKMI),
                bere("spremembeZaLigo", () -> dnevnik.spremembeZaLigo(1L, SISTEM), PO_TEKMI_SRECANJA));
    }

    /* Graf profila: iskanje po igralcu, sistem in igralec oba v indeksu. */
    @Test
    void dnevnikIgralcaIscePoIgralcu() {
        String vrstica = vrsticaDnevnika(() -> ratingZgodovinaRepozitorij.najdiZaIgralca(1L, SISTEM));
        assertTrue(vrstica.contains(
                        "USING INDEX idx_rating_zgodovina_igralec_velja (sistem=? AND id_igralec=?)"),
                "najdiZaIgralca: " + vrstica);
    }

    private Executable bere(String poizvedba, Runnable dejanje, String pricakovano) {
        return () -> {
            String vrstica = vrsticaDnevnika(dejanje);
            assertTrue(vrstica.contains(pricakovano), poizvedba + ": " + vrstica);
        };
    }

    private String vrsticaDnevnika(Runnable dejanje) {
        String sql = NacrtPoizvedbe.poslaniSql(dejanje, "rating_zgodovina");
        return NacrtPoizvedbe.vrsticaTabele(podatkovniVir, sql, "rating_zgodovina", SISTEM);
    }
}
