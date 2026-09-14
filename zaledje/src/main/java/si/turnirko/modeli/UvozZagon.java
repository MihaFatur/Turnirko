/* En zagon uvoza dogodka iz zunanjega vira (V27): kdo, kdaj, iz katerega
   posnetka in s kaksnim izidom. Brez dnevnika na vprasanje "od kod ta
   rezultat" ni odgovora. */
package si.turnirko.modeli;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "uvoz_zagon")
public class UvozZagon {

    public enum Izid {
        /* zapisano in usklajeno z virom */
        USPEH,
        /* posnetek je isti kot pri zadnjem uspesnem uvozu - nic za zapisati */
        BREZ_SPREMEMB,
        /* preverba je nasla napako ali nerazreseno odlocitev - nic ni zapisano */
        ZAVRNJENO,
        /* nepricakovana napaka med zapisom - transakcija je razveljavljena */
        NAPAKA
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "vir", nullable = false)
    private VirTekmovanja vir;

    @Column(name = "zunanji_id", nullable = false)
    private String zunanjiId;

    @Column(name = "ime", nullable = false)
    private String ime;

    @Column(name = "zgostitev", nullable = false)
    private String zgostitev;

    @Column(name = "izvedel")
    private String izvedel;

    @Convert(converter = CasKotBesedilo.class)
    @Column(name = "zacetek_ob", nullable = false)
    private LocalDateTime zacetekOb;

    @Convert(converter = CasKotBesedilo.class)
    @Column(name = "konec_ob")
    private LocalDateTime konecOb;

    @Enumerated(EnumType.STRING)
    @Column(name = "izid", nullable = false)
    private Izid izid;

    /* Stevci in ugotovitve porocila (JSON). */
    @Column(name = "povzetek")
    private String povzetek;

    protected UvozZagon() {}

    public UvozZagon(VirTekmovanja vir, String zunanjiId, String ime, String zgostitev, String izvedel,
                     LocalDateTime zacetekOb) {
        this.vir = vir;
        this.zunanjiId = zunanjiId;
        this.ime = ime;
        this.zgostitev = zgostitev;
        this.izvedel = izvedel;
        this.zacetekOb = zacetekOb;
        this.izid = Izid.NAPAKA;
    }

    public Long getId() { return id; }
    public VirTekmovanja getVir() { return vir; }
    public String getZunanjiId() { return zunanjiId; }
    public String getIme() { return ime; }
    public String getZgostitev() { return zgostitev; }
    public String getIzvedel() { return izvedel; }
    public LocalDateTime getZacetekOb() { return zacetekOb; }
    public LocalDateTime getKonecOb() { return konecOb; }
    public Izid getIzid() { return izid; }
    public String getPovzetek() { return povzetek; }

    public void zakljuci(Izid izid, String povzetek) {
        this.izid = izid;
        this.povzetek = povzetek;
        this.konecOb = LocalDateTime.now();
    }
}
