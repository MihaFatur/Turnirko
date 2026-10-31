/* Zakljucen turnir, ki ga je obvestilo po turnirju ze obdelalo (V58): vsak
   turnir gre ven najvec enkrat, tudi ce se izid kasneje popravi. Turnir brez
   spremljevalcev je obdelan z nic prejemniki - sicer bi ga opravilo vsako uro
   znova preverjalo.

   Kot ObvestiloKola brez povezave na entiteto: tabela odgovarja na eno samo
   vprasanje ("je turnir T ze obdelan"). */
package si.turnirko.modeli;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "obvestilo_turnirja")
public class ObvestiloTurnirja {

    @Id
    @Column(name = "id_turnir", nullable = false)
    private Long idTurnir;

    @Convert(converter = CasKotBesedilo.class)
    @Column(name = "obdelano_ob", nullable = false)
    private LocalDateTime obdelanoOb;

    @Column(name = "prejemnikov", nullable = false)
    private int prejemnikov;

    protected ObvestiloTurnirja() {}

    public ObvestiloTurnirja(Long idTurnir, LocalDateTime obdelanoOb, int prejemnikov) {
        this.idTurnir = idTurnir;
        this.obdelanoOb = obdelanoOb;
        this.prejemnikov = prejemnikov;
    }

    public Long getIdTurnir() { return idTurnir; }
    public LocalDateTime getObdelanoOb() { return obdelanoOb; }
    public int getPrejemnikov() { return prejemnikov; }
}
