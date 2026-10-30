/* Pokrovitelj lige ali turnirja (V56): logotip iz knjižnice (Slika, V54), ime,
   neobvezna povezava in mesto v traku. Natanko eno od lige in turnirja (CHECK
   v shemi). Seznam se shrani v celoti (PokroviteljiStoritev.shrani), zato
   vrstica nima svojih urejevalnih metod. */
package si.turnirko.modeli;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "pokrovitelj")
public class Pokrovitelj {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_liga", updatable = false)
    private Liga liga;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_turnir", updatable = false)
    private Turnir turnir;

    /* EAGER kot logotip tekmovanja: opis brez bajtov, DTO ga nosi. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_slika", nullable = false, updatable = false)
    private Slika slika;

    @Column(name = "ime", nullable = false, updatable = false)
    private String ime;

    @Column(name = "povezava", updatable = false)
    private String povezava;

    @Column(name = "glavni", nullable = false, updatable = false)
    private boolean glavni;

    @Column(name = "vrstni_red", nullable = false, updatable = false)
    private int vrstniRed;

    protected Pokrovitelj() {
    }

    public Pokrovitelj(Liga liga, Turnir turnir, Slika slika, String ime, String povezava,
                       boolean glavni, int vrstniRed) {
        this.liga = liga;
        this.turnir = turnir;
        this.slika = slika;
        this.ime = ime;
        this.povezava = povezava;
        this.glavni = glavni;
        this.vrstniRed = vrstniRed;
    }

    public Long getId() { return id; }
    public Slika getSlika() { return slika; }
    public String getIme() { return ime; }
    public String getPovezava() { return povezava; }
    public boolean isGlavni() { return glavni; }
    public int getVrstniRed() { return vrstniRed; }
}
