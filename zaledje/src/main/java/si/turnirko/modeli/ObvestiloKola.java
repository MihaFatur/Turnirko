/* Kolo lige, ki ga je obvestilo po kolu ze obdelalo (V50): vsako kolo gre ven
   najvec enkrat, tudi ce se izid kasneje popravi ali zapisnik razveljavi in
   vnese znova. Kolo, ki ga preskocimo (preskoceno), je obdelano brez poste.

   Kot SpremljanaLiga brez povezav na entiteto: tabela odgovarja na eno samo
   vprasanje ("je kolo K lige L ze obdelano"). */
package si.turnirko.modeli;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "obvestilo_kola")
@IdClass(ObvestiloKola.Kljuc.class)
public class ObvestiloKola {

    @Id
    @Column(name = "id_liga", nullable = false)
    private Long idLiga;

    @Id
    @Column(name = "kolo", nullable = false)
    private int kolo;

    @Convert(converter = CasKotBesedilo.class)
    @Column(name = "obdelano_ob", nullable = false)
    private LocalDateTime obdelanoOb;

    @Column(name = "prejemnikov", nullable = false)
    private int prejemnikov;

    @Column(name = "preskoceno", nullable = false)
    private boolean preskoceno;

    protected ObvestiloKola() {}

    public ObvestiloKola(Long idLiga, int kolo, LocalDateTime obdelanoOb, int prejemnikov, boolean preskoceno) {
        this.idLiga = idLiga;
        this.kolo = kolo;
        this.obdelanoOb = obdelanoOb;
        this.prejemnikov = prejemnikov;
        this.preskoceno = preskoceno;
    }

    public Long getIdLiga() { return idLiga; }
    public int getKolo() { return kolo; }
    public LocalDateTime getObdelanoOb() { return obdelanoOb; }
    public int getPrejemnikov() { return prejemnikov; }
    public boolean isPreskoceno() { return preskoceno; }

    public static class Kljuc implements Serializable {
        private Long idLiga;
        private int kolo;

        public Kljuc() {}

        public Kljuc(Long idLiga, int kolo) {
            this.idLiga = idLiga;
            this.kolo = kolo;
        }

        @Override
        public boolean equals(Object drugi) {
            if (this == drugi) return true;
            if (!(drugi instanceof Kljuc kljuc)) return false;
            return Objects.equals(idLiga, kljuc.idLiga) && kolo == kljuc.kolo;
        }

        @Override
        public int hashCode() {
            return Objects.hash(idLiga, kolo);
        }
    }
}
