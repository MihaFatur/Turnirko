/* Serija koncnice lige (V28): par ekip v enem krogu koncnice, ki igrata,
   dokler ena ne doseze liga.koncnicaZmag zmag (npr. na dve zmagi).

   Krogi tecejo od 1 (prvi krog, npr. polfinale) do finala; zmagovalec para p
   gre v naslednjem krogu v par ceil(p/2) - liho mesto na prvo stran, sodo na
   drugo, isto pravilo kot izlocilna mreza turnirja. Ekipi vnaprej ustvarjene
   serije visjega kroga ostaneta prazni, dokler ju ne doloci prejsnji krog.

   Mesto na lestvici rednega dela je zapisano (mesto1/mesto2), ker odloca
   domace pravice - lestvica pa se po koncu rednega dela ne sme vec brati kot
   vir, ce bi se kdaj popravil star rezultat. */
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
import jakarta.persistence.Version;

@Entity
@Table(name = "serija_koncnice")
public class SerijaKoncnice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_liga", nullable = false)
    private Liga liga;

    @Column(name = "krog", nullable = false)
    private int krog;

    @Column(name = "par", nullable = false)
    private int par;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_ekipa_1")
    private Ekipa ekipa1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_ekipa_2")
    private Ekipa ekipa2;

    @Column(name = "mesto_1")
    private Integer mesto1;

    @Column(name = "mesto_2")
    private Integer mesto2;

    @Column(name = "zmage_1", nullable = false)
    private int zmage1 = 0;

    @Column(name = "zmage_2", nullable = false)
    private int zmage2 = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_zmagovalec")
    private Ekipa zmagovalec;

    @Version
    @Column(name = "verzija", nullable = false)
    private long verzija;

    protected SerijaKoncnice() {}

    public SerijaKoncnice(Liga liga, int krog, int par) {
        this.liga = liga;
        this.krog = krog;
        this.par = par;
    }

    public Long getId() { return id; }
    public Liga getLiga() { return liga; }
    public int getKrog() { return krog; }
    public int getPar() { return par; }

    public Ekipa getEkipa1() { return ekipa1; }
    public Ekipa getEkipa2() { return ekipa2; }

    public Integer getMesto1() { return mesto1; }
    public Integer getMesto2() { return mesto2; }

    /* Vpise ekipo na stran serije skupaj z njenim mestom po rednem delu. */
    public void nastaviStran(int stran, Ekipa ekipa, Integer mesto) {
        if (stran == 1) {
            this.ekipa1 = ekipa;
            this.mesto1 = mesto;
        } else {
            this.ekipa2 = ekipa;
            this.mesto2 = mesto;
        }
    }

    public int getZmage1() { return zmage1; }
    public void setZmage1(int zmage1) { this.zmage1 = zmage1; }

    public int getZmage2() { return zmage2; }
    public void setZmage2(int zmage2) { this.zmage2 = zmage2; }

    public Ekipa getZmagovalec() { return zmagovalec; }
    public void setZmagovalec(Ekipa zmagovalec) { this.zmagovalec = zmagovalec; }

    /* Ali sta obe ekipi serije ze znani. */
    public boolean imaObeEkipi() { return ekipa1 != null && ekipa2 != null; }

    public boolean jeOdlocena() { return zmagovalec != null; }
}
