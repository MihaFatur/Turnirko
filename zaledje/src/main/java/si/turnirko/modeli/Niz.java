/* Posamezen niz tekme s tockami (npr. 11:7).
   Vnos tock po nizih je za klubske turnirje neobvezen,
   za uradne pa obvezen - to doloca aplikacija. */
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
@Table(name = "niz")
public class Niz {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_tekma", nullable = false)
    private Tekma tekma;

    @Column(name = "zaporedna_st", nullable = false)
    private int zaporednaSt;

    @Column(name = "tocke_1", nullable = false)
    private int tocke1;

    @Column(name = "tocke_2", nullable = false)
    private int tocke2;

    protected Niz() {}

    public Niz(Tekma tekma, int zaporednaSt, int tocke1, int tocke2) {
        this.tekma = tekma;
        this.zaporednaSt = zaporednaSt;
        this.tocke1 = tocke1;
        this.tocke2 = tocke2;
    }

    public Long getId() { return id; }
    public Tekma getTekma() { return tekma; }
    public int getZaporednaSt() { return zaporednaSt; }
    public int getTocke1() { return tocke1; }
    public int getTocke2() { return tocke2; }
}
