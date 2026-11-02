/* Igralec, ki ga uporabnik spremlja - osebna nastavitev racuna, ne zapis o
   tekmovanju. Sklop "Moj igralec" na domaci strani prikaze samo te igralce.

   Kot SpremljanaLiga zapis namenoma nima povezav na entiteti, ampak samo
   njuna id-ja: tabela sluzi enemu vprasanju ("katere igralce spremlja racun
   X") in bi jo povezavi le prisilili v nalaganje celotnih zapisov. */
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
@Table(name = "spremljan_igralec")
@IdClass(SpremljanIgralec.Kljuc.class)
public class SpremljanIgralec {

    @Id
    @Column(name = "id_racun", nullable = false)
    private Long idRacun;

    @Id
    @Column(name = "id_igralec", nullable = false)
    private Long idIgralec;

    @Column(name = "dodano_ob", nullable = false, updatable = false)
    private LocalDateTime dodanoOb;

    protected SpremljanIgralec() {}

    public SpremljanIgralec(Long idRacun, Long idIgralec) {
        this.idRacun = idRacun;
        this.idIgralec = idIgralec;
    }

    @PrePersist
    void obShranjevanju() {
        dodanoOb = LocalDateTime.now();
    }

    public Long getIdRacun() { return idRacun; }
    public Long getIdIgralec() { return idIgralec; }
    public LocalDateTime getDodanoOb() { return dodanoOb; }

    /* Sestavljeni kljuc (racun + igralec); JPA zanj zahteva razred z
       brezargumentnim konstruktorjem, zato zapis (record) ne pride v postev. */
    public static class Kljuc implements Serializable {
        private Long idRacun;
        private Long idIgralec;

        public Kljuc() {}

        public Kljuc(Long idRacun, Long idIgralec) {
            this.idRacun = idRacun;
            this.idIgralec = idIgralec;
        }

        @Override
        public boolean equals(Object drugi) {
            if (this == drugi) return true;
            if (!(drugi instanceof Kljuc kljuc)) return false;
            return Objects.equals(idRacun, kljuc.idRacun) && Objects.equals(idIgralec, kljuc.idIgralec);
        }

        @Override
        public int hashCode() {
            return Objects.hash(idRacun, idIgralec);
        }
    }
}
