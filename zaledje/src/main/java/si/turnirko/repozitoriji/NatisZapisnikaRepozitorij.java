package si.turnirko.repozitoriji;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import si.turnirko.modeli.NatisZapisnika;

public interface NatisZapisnikaRepozitorij extends JpaRepository<NatisZapisnika, Long> {

    /* Natisi srecanja (enak natis se ne podvaja: NatisZapisnikovStoritev ga najde med njimi). */
    @Query("SELECT n FROM NatisZapisnika n WHERE n.srecanje.id = :idSrecanje ORDER BY n.id")
    List<NatisZapisnika> zaSrecanje(@Param("idSrecanje") Long idSrecanje);

    /* Natis s srecanjem, ekipama in ligo (bralnik, predlog). */
    @Query("""
            SELECT n FROM NatisZapisnika n
            JOIN FETCH n.srecanje s
            JOIN FETCH s.ekipaDomaci ed LEFT JOIN FETCH ed.klub JOIN FETCH s.ekipaGost eg LEFT JOIN FETCH eg.klub
            LEFT JOIN FETCH s.liga
            LEFT JOIN FETCH s.tekma tk LEFT JOIN FETCH tk.dogodek dg LEFT JOIN FETCH dg.turnir
            WHERE n.id = :id
            """)
    Optional<NatisZapisnika> najdiPodrobno(@Param("id") Long id);
}
