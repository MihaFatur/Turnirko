/* Liga, ki jo uporabnik spremlja - osebna nastavitev racuna, ne zapis o
   tekmovanju. Sklop "Moje lige" na domaci strani prikaze samo te lige.

   Zapis namenoma nima povezav na entiteti (Uporabnik, Liga), ampak samo
   njuna id-ja: tabela sluzi enemu vprasanju ("katere lige spremlja racun X")
   in bi jo povezavi le prisilila v nalaganje celotnih zapisov. */
package si.turnirko.modeli;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "spremljana_liga")
@IdClass(SpremljanaLiga.Kljuc.class)
public class SpremljanaLiga {

    @Id
    @Column(name = "id_racun", nullable = false)
    private Long idRacun;

    @Id
    @Column(name = "id_liga", nullable = false)
    private Long idLiga;

    @Column(name = "dodano_ob", nullable = false, updatable = false)
    private LocalDateTime dodanoOb;

    protected SpremljanaLiga() {}

    public SpremljanaLiga(Long idRacun, Long idLiga) {
        this.idRacun = idRacun;
        this.idLiga = idLiga;
    }

    @PrePersist
    void obShranjevanju() {
        dodanoOb = LocalDateTime.now();
    }

    public Long getIdRacun() { return idRacun; }
    public Long getIdLiga() { return idLiga; }
    public LocalDateTime getDodanoOb() { return dodanoOb; }

    /* Sestavljeni kljuc (racun + liga); JPA zanj zahteva razred z brezargumentnim
       konstruktorjem, zato zapis (record) ne pride v postev. */
    public static class Kljuc implements Serializable {
        private Long idRacun;
        private Long idLiga;

        public Kljuc() {}

        public Kljuc(Long idRacun, Long idLiga) {
            this.idRacun = idRacun;
            this.idLiga = idLiga;
        }

        @Override
        public boolean equals(Object drugi) {
            if (this == drugi) return true;
            if (!(drugi instanceof Kljuc kljuc)) return false;
            return Objects.equals(idRacun, kljuc.idRacun) && Objects.equals(idLiga, kljuc.idLiga);
        }

        @Override
        public int hashCode() {
            return Objects.hash(idRacun, idLiga);
        }
    }
}
