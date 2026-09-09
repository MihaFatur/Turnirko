/* Posamezen niz ligaske tekme s tockami (npr. 11:7). Dvojnik razreda Niz za
   turnirske tekme - loceni sta zato, ker je locena ze tekma sama
   (tekma / tekma_srecanja) in tako obe vezi ostaneta obvezni.
   Strani sta imenovani domaci/gost, ne 1/2, ker je v ligi stran ekipe. */
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
@Table(name = "niz_srecanja")
public class NizSrecanja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_tekma_srecanja", nullable = false)
    private TekmaSrecanja tekma;

    @Column(name = "zaporedna_st", nullable = false)
    private int zaporednaSt;

    @Column(name = "tocke_domaci", nullable = false)
    private int tockeDomaci;

    @Column(name = "tocke_gost", nullable = false)
    private int tockeGost;

    protected NizSrecanja() {}

    public NizSrecanja(TekmaSrecanja tekma, int zaporednaSt, int tockeDomaci, int tockeGost) {
        this.tekma = tekma;
        this.zaporednaSt = zaporednaSt;
        this.tockeDomaci = tockeDomaci;
        this.tockeGost = tockeGost;
    }

    public Long getId() { return id; }
    public TekmaSrecanja getTekma() { return tekma; }
    public int getZaporednaSt() { return zaporednaSt; }
    public int getTockeDomaci() { return tockeDomaci; }
    public int getTockeGost() { return tockeGost; }
}
