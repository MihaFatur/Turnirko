/* Pricakovana vrednost ratinga za dano starost in spol - izhodisce novinca.

   Tabela je POSNETEK dejanske porazdelitve (mediana umerjenih in aktivnih
   igralcev), ne izpeljanka, ki bi se osvezevala sama. Sidro, izracunano iz
   lestvice, ki so jo oblikovali novinci, zasidrani po prejsnjem sidru, je
   povratna zanka - izmerjeno je lestvico v 14 letih poseslo za 157 tock.
   Osvezitev je zato zavestna odlocitev administratorja. */
package si.turnirko.modeli;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "starostno_sidro")
@IdClass(StarostnoSidro.Kljuc.class)
public class StarostnoSidro {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "spol", nullable = false)
    private Spol spol;

    /* Starost po 11. clenu PST (leto sezone minus letnica rojstva) - isto
       merilo kot StarostniPas, da sta igralec in sidro merjena enako. */
    @Id
    @Column(name = "starost", nullable = false)
    private int starost;

    @Column(name = "vrednost", nullable = false)
    private int vrednost;

    protected StarostnoSidro() {}

    public StarostnoSidro(Spol spol, int starost, int vrednost) {
        this.spol = spol;
        this.starost = starost;
        this.vrednost = vrednost;
    }

    public Spol getSpol() { return spol; }
    public int getStarost() { return starost; }
    public int getVrednost() { return vrednost; }
    public void setVrednost(int vrednost) { this.vrednost = vrednost; }

    /* Sestavljeni kljuc (spol + starost). */
    public static class Kljuc implements Serializable {
        private Spol spol;
        private int starost;

        public Kljuc() {}

        public Kljuc(Spol spol, int starost) {
            this.spol = spol;
            this.starost = starost;
        }

        @Override
        public boolean equals(Object drugi) {
            if (this == drugi) return true;
            if (!(drugi instanceof Kljuc k)) return false;
            return starost == k.starost && spol == k.spol;
        }

        @Override
        public int hashCode() {
            return Objects.hash(spol, starost);
        }
    }
}
