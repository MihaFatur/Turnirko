/* Dostop do nizov (tock posameznih nizov tekme). */
package si.turnirko.repozitoriji;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import si.turnirko.modeli.Niz;

public interface NizRepozitorij extends JpaRepository<Niz, Long> {

    List<Niz> findByTekmaIdOrderByZaporednaStAsc(Long idTekma);

    /* Pogostost izidov nizov za branje listkov (PogostostNizovStoritev): vrstice
       [tocke zmagovalca niza, tocke porazenca, stevilo]. Samo normalno odigrane in
       koncane tekme turnirjev z znanim datumom (1. 1. 1900 je zapis »ni znan«);
       veljavnost niza preveri klicatelj. Brez osebnih podatkov. */
    @Query(value = """
            SELECT GREATEST(n.tocke_1, n.tocke_2), LEAST(n.tocke_1, n.tocke_2), COUNT(*)
            FROM niz n
            JOIN tekma t ON t.id = n.id_tekma
            JOIN dogodek d ON d.id = t.id_dogodek
            JOIN turnir tu ON tu.id = d.id_turnir
            WHERE t.izid_tip = 'IGRANO' AND t.status = 'KONCANA' AND tu.datum_zacetka > DATE '1900-01-01'
            GROUP BY 1, 2
            """, nativeQuery = true)
    List<Object[]> pogostostIzidov();

    /* Nizi vec tekem hkrati - za statistiko tock na profilu igralca.
       Vrne vrstice [idTekme, tocke1, tocke2]; entitet ne vracamo, ker bi
       vsaka potegnila se tekmo. */
    @Query("SELECT n.tekma.id, n.tocke1, n.tocke2 FROM Niz n WHERE n.tekma.id IN :idjiTekem")
    List<Object[]> tockeZaTekme(@Param("idjiTekem") Collection<Long> idjiTekem);

    /* Enako kot tockeZaTekme, a z zaporedno stevilko niza - razrez po nizu
       mora vedeti, ali gre za prvi, drugi ... Vrne [idTekme, zaporedna, tocke1, tocke2]. */
    @Query("SELECT n.tekma.id, n.zaporednaSt, n.tocke1, n.tocke2 FROM Niz n WHERE n.tekma.id IN :idjiTekem")
    List<Object[]> zaporedneTockeZaTekme(@Param("idjiTekem") Collection<Long> idjiTekem);

    /* Nizi CELEGA turnirja, urejeni po tekmi in zaporedju - za zavihek
       statistike (obrat, najdaljsi niz, sestevek tock).

       Zakaj po turnirju in ne po seznamu id-jev: turnir ima lahko nekaj sto
       tekem, sqlite pa ima omejitev stevila vezanih parametrov - seznam v IN
       bi pri velikem turnirju pocil. Zaporedna stevilka je v izbiri, ker brez
       vrstnega reda nizov ni mogoce prepoznati obrata. */
    @Query("""
            SELECT n.tekma.id, n.zaporednaSt, n.tocke1, n.tocke2
            FROM Niz n
            WHERE n.tekma.dogodek.turnir.id = :idTurnir
            ORDER BY n.tekma.id, n.zaporednaSt
            """)
    List<Object[]> tockeZaTurnir(@Param("idTurnir") Long idTurnir);

    /* Nizi enega dogodka po tekmi in zaporedju - za mrezo dogodka, kjer
       gledalec s klikom na koncano tekmo odpre tocke po nizih. Po dogodku in
       ne po seznamu id-jev iz istega razloga kot tockeZaTurnir. */
    @Query("""
            SELECT n.tekma.id, n.zaporednaSt, n.tocke1, n.tocke2
            FROM Niz n
            WHERE n.tekma.dogodek.id = :idDogodek
            ORDER BY n.tekma.id, n.zaporednaSt
            """)
    List<Object[]> tockeZaDogodek(@Param("idDogodek") Long idDogodek);
}
