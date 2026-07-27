/* Posamicna tekma znotraj ekipnega srecanja (dvojice ali posamicna).
   Igralca vezemo NEPOSREDNO na igralca (ne na prijavo kot pri turnirjih),
   ker v ligi nastopajo iz kadra ekipe; pri dvojicah sta na strani dva igralca.
   V klubski ELO stejejo samo POSAMICNE tekme. */
package si.turnirko.modeli;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "tekma_srecanja")
public class TekmaSrecanja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_srecanje", nullable = false)
    private Srecanje srecanje;

    @Column(name = "zaporedje", nullable = false)
    private int zaporedje;

    @Enumerated(EnumType.STRING)
    @Column(name = "tip", nullable = false)
    private TipTekmeSrecanja tip;

    @Column(name = "oznaka", nullable = false)
    private String oznaka;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_igralec_domaci")
    private Igralec igralecDomaci;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_igralec_domaci2")
    private Igralec igralecDomaci2;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_igralec_gost")
    private Igralec igralecGost;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_igralec_gost2")
    private Igralec igralecGost2;

    @Column(name = "stevilo_nizov", nullable = false)
    private int steviloNizov;

    @Column(name = "dobljeni_nizi_domaci", nullable = false)
    private int dobljeniNiziDomaci = 0;

    @Column(name = "dobljeni_nizi_gost", nullable = false)
    private int dobljeniNiziGost = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "zmagovalec_stran")
    private StranEkipe zmagovalecStran;

    @Enumerated(EnumType.STRING)
    @Column(name = "izid_tip")
    private IzidTekme izidTip;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StatusTekmeSrecanja status = StatusTekmeSrecanja.CAKA;

    @Version
    @Column(name = "verzija", nullable = false)
    private long verzija;

    protected TekmaSrecanja() {}

    public TekmaSrecanja(Srecanje srecanje, int zaporedje, TipTekmeSrecanja tip,
                         String oznaka, int steviloNizov) {
        this.srecanje = srecanje;
        this.zaporedje = zaporedje;
        this.tip = tip;
        this.oznaka = oznaka;
        this.steviloNizov = steviloNizov;
    }

    /* Stevilo dobljenih nizov, potrebnih za zmago (npr. 3 pri "najboljsi od 5"). */
    public int nizovZaZmago() { return steviloNizov / 2 + 1; }

    public Long getId() { return id; }
    public Srecanje getSrecanje() { return srecanje; }

    public int getZaporedje() { return zaporedje; }
    public TipTekmeSrecanja getTip() { return tip; }
    public String getOznaka() { return oznaka; }

    public Igralec getIgralecDomaci() { return igralecDomaci; }
    public void setIgralecDomaci(Igralec igralecDomaci) { this.igralecDomaci = igralecDomaci; }

    public Igralec getIgralecDomaci2() { return igralecDomaci2; }
    public void setIgralecDomaci2(Igralec igralecDomaci2) { this.igralecDomaci2 = igralecDomaci2; }

    public Igralec getIgralecGost() { return igralecGost; }
    public void setIgralecGost(Igralec igralecGost) { this.igralecGost = igralecGost; }

    public Igralec getIgralecGost2() { return igralecGost2; }
    public void setIgralecGost2(Igralec igralecGost2) { this.igralecGost2 = igralecGost2; }

    public int getSteviloNizov() { return steviloNizov; }
    public void setSteviloNizov(int steviloNizov) { this.steviloNizov = steviloNizov; }

    public int getDobljeniNiziDomaci() { return dobljeniNiziDomaci; }
    public void setDobljeniNiziDomaci(int v) { this.dobljeniNiziDomaci = v; }

    public int getDobljeniNiziGost() { return dobljeniNiziGost; }
    public void setDobljeniNiziGost(int v) { this.dobljeniNiziGost = v; }

    public StranEkipe getZmagovalecStran() { return zmagovalecStran; }
    public void setZmagovalecStran(StranEkipe zmagovalecStran) { this.zmagovalecStran = zmagovalecStran; }

    public IzidTekme getIzidTip() { return izidTip; }
    public void setIzidTip(IzidTekme izidTip) { this.izidTip = izidTip; }

    public StatusTekmeSrecanja getStatus() { return status; }
    public void setStatus(StatusTekmeSrecanja status) { this.status = status; }
}
