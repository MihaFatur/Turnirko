/* Srecanje dveh ekip. Sestavlja ga vec posamicnih tekem (tekma_srecanja) v
   vrstnem redu, ki ga doloca format. Dobljene tekme vsake strani so
   denormaliziran povzetek za hitro lestvico.

   Srecanje pripada natanko enemu tekmovanju (V28):
   - LIGI: srecanje kola rednega dela ali tekma serije koncnice (serija),
   - TEKMI TURNIRJA: ekipna tekma v mrezi ali skupini ekipnega dogodka; njen
     izid (zmagovalec, dobljene tekme) se po koncu srecanja prepise v tekmo.
   Pravila igranja (format, stevilo nizov, prag zmag, raven) zato srecanje
   bere iz tekmovanja, kateremu pripada - glej pravila(). */
package si.turnirko.modeli;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "srecanje")
public class Srecanje {

    /* Pravila igranja srecanja, prebrana iz tekmovanja, kateremu pripada. */
    public record Pravila(FormatSrecanja format, int steviloNizov, Integer zmagZaSrecanje,
                          RavenTekmovanja raven) {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /* Liga srecanja; prazna pri ekipni tekmi turnirja. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_liga")
    private Liga liga;

    /* Ekipna tekma turnirja, katere izid je to srecanje; prazna pri ligi. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_tekma")
    private Tekma tekma;

    @Column(name = "kolo", nullable = false)
    private int kolo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_ekipa_domaci", nullable = false)
    private Ekipa ekipaDomaci;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_ekipa_gost", nullable = false)
    private Ekipa ekipaGost;

    @Column(name = "dobljene_domaci", nullable = false)
    private int dobljeneDomaci = 0;

    @Column(name = "dobljene_gost", nullable = false)
    private int dobljeneGost = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StatusSrecanja status = StatusSrecanja.RAZPORED;

    /* Srecanje ni bilo odigrano, izid je registriran v skodo ekipe, ki ni
       nastopila - porazenec izgubi se liga.odbitekBrezBoja tock (V29). */
    @Column(name = "brez_boja", nullable = false)
    private boolean brezBoja = false;

    /* Termin kola z uro; gonilnik bi jo brez pretvornika odrezal (glej
       CasKotBesedilo). */
    @Convert(converter = CasKotBesedilo.class)
    @Column(name = "predviden_zacetek")
    private LocalDateTime predvidenZacetek;

    /* Ob kateri uri kola se srecanje igra (V32, 0 = prva ura lige); prazno pri
       kolu kroznega sistema. Hrani se posebej, ker zacetka ob zrebu morda se
       ni, organizator pa ga sme prestaviti - uro lige mora srecanje vseeno
       poznati. */
    @Column(name = "ura_v_kolu")
    private Integer uraVKolu;

    @Column(name = "odigran_ob")
    private LocalDateTime odigranOb;

    /* Serija koncnice, ki ji srecanje pripada; prazna pri rednem delu. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_serija")
    private SerijaKoncnice serija;

    /* Zaporedna tekma v seriji (1, 2, 3 ...); prazna pri rednem delu. */
    @Column(name = "tekma_v_seriji")
    private Integer tekmaVSeriji;

    @Version
    @Column(name = "verzija", nullable = false)
    private long verzija;

    protected Srecanje() {}

    public Srecanje(Liga liga, int kolo, Ekipa ekipaDomaci, Ekipa ekipaGost) {
        this.liga = liga;
        this.kolo = kolo;
        this.ekipaDomaci = ekipaDomaci;
        this.ekipaGost = ekipaGost;
    }

    /* Srecanje ekipne tekme turnirja. Kolo je kolo tekme v mrezi ali skupini -
       stolpec je obvezen, pomen pa ima samo pri ligi. */
    public Srecanje(Tekma tekma, Ekipa ekipaDomaci, Ekipa ekipaGost) {
        this.tekma = tekma;
        this.kolo = Math.max(1, tekma.getKolo());
        this.ekipaDomaci = ekipaDomaci;
        this.ekipaGost = ekipaGost;
    }

    public Long getId() { return id; }

    public Liga getLiga() { return liga; }
    public Tekma getTekma() { return tekma; }

    /* Ali je srecanje ekipna tekma turnirja (in ne ligasko srecanje). */
    public boolean jeTurnirsko() { return tekma != null; }

    public int getKolo() { return kolo; }
    public void setKolo(int kolo) { this.kolo = kolo; }

    public Ekipa getEkipaDomaci() { return ekipaDomaci; }
    public Ekipa getEkipaGost() { return ekipaGost; }

    /* Zamenja domacina in gosta - samo za srecanje koncnice, ki se se ni
       zacelo (domace pravice v seriji ureja organizator). */
    public void zamenjajStrani() {
        Ekipa prej = ekipaDomaci;
        ekipaDomaci = ekipaGost;
        ekipaGost = prej;
    }

    /* Ekipi srecanja pri ponovnem uvozu iz vira: srecanje obdrzi id (naslov
       zapisnika), vsebino pa prepise vir. */
    public void nastaviEkipi(Ekipa domaci, Ekipa gost) {
        this.ekipaDomaci = domaci;
        this.ekipaGost = gost;
    }

    public int getDobljeneDomaci() { return dobljeneDomaci; }
    public void setDobljeneDomaci(int dobljeneDomaci) { this.dobljeneDomaci = dobljeneDomaci; }

    public int getDobljeneGost() { return dobljeneGost; }
    public void setDobljeneGost(int dobljeneGost) { this.dobljeneGost = dobljeneGost; }

    public boolean isBrezBoja() { return brezBoja; }
    public void setBrezBoja(boolean brezBoja) { this.brezBoja = brezBoja; }

    public StatusSrecanja getStatus() { return status; }
    public void setStatus(StatusSrecanja status) { this.status = status; }

    public LocalDateTime getPredvidenZacetek() { return predvidenZacetek; }
    public void setPredvidenZacetek(LocalDateTime predvidenZacetek) { this.predvidenZacetek = predvidenZacetek; }

    public Integer getUraVKolu() { return uraVKolu; }
    public void setUraVKolu(Integer uraVKolu) { this.uraVKolu = uraVKolu; }

    public LocalDateTime getOdigranOb() { return odigranOb; }
    public void setOdigranOb(LocalDateTime odigranOb) { this.odigranOb = odigranOb; }

    public SerijaKoncnice getSerija() { return serija; }
    public Integer getTekmaVSeriji() { return tekmaVSeriji; }

    /* Uvrsti srecanje v serijo koncnice kot n-to tekmo serije. */
    public void nastaviSerijo(SerijaKoncnice serija, Integer tekmaVSeriji) {
        this.serija = serija;
        this.tekmaVSeriji = tekmaVSeriji;
    }

    /* Ali je srecanje del koncnice (in ne rednega dela lige). */
    public boolean jeKoncnica() { return serija != null; }

    /* Pravila igranja: pri ligi pravila lige, pri ekipni tekmi turnirja
       pravila ekipnega dogodka in raven turnirja. Klicalec mora imeti ligo
       oz. tekmo z dogodkom in turnirjem nalozeno (join fetch). */
    public Pravila pravila() {
        if (liga != null) {
            return new Pravila(liga.getFormatSrecanja(), liga.getSteviloNizov(),
                    liga.getZmagZaSrecanje(), liga.getRaven());
        }
        Dogodek dogodek = tekma.getDogodek();
        return new Pravila(dogodek.getFormatSrecanja(), dogodek.getPrivzetoSteviloNizov(),
                dogodek.getZmagZaSrecanje(), dogodek.getTurnir().getRaven());
    }

    /* Dan tekmovanja za srecanje brez vpisanega termina: pri ekipni tekmi
       turnirja je to dan turnirja (vir pove samo dan - enako kot pri
       turnirskih tekmah posameznikov). */
    public LocalDate datumTurnirja() {
        return tekma == null ? null : tekma.getDogodek().getTurnir().getDatumZacetka();
    }
}
