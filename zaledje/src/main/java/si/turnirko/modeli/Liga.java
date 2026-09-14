/* Liga je sezonsko ekipno tekmovanje med klubi (SNTL, rekreacijske lige).
   Konfiguracija je namenoma prilagodljiva: format srecanja, stevilo nizov,
   prag zmag za konec srecanja, tockovanje in stetje v rating se izberejo ob
   ustvarjanju. Lige so lahko povezane (id_visja_liga) za prehode med sezonami. */
package si.turnirko.modeli;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "liga")
public class Liga {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "ime", nullable = false)
    private String ime;

    @Column(name = "sezona")
    private String sezona;

    @Enumerated(EnumType.STRING)
    @Column(name = "spol_kategorija", nullable = false)
    private SpolKategorija spolKategorija;

    @Enumerated(EnumType.STRING)
    @Column(name = "format_srecanja", nullable = false)
    private FormatSrecanja formatSrecanja = FormatSrecanja.SNTL;

    @Column(name = "stevilo_nizov", nullable = false)
    private int steviloNizov = 5;

    /* Prvi do N dobljenih tekem konca srecanje; null = odigrajo se vse tekme. */
    @Column(name = "zmag_za_srecanje")
    private Integer zmagZaSrecanje;

    @Column(name = "dvokrozno", nullable = false)
    private boolean dvokrozno = true;

    @Column(name = "tocke_zmaga", nullable = false)
    private int tockeZmaga = 2;

    @Column(name = "tocke_neodloceno", nullable = false)
    private int tockeNeodloceno = 1;

    @Column(name = "tocke_poraz", nullable = false)
    private int tockePoraz = 0;

    @Column(name = "dovoljeno_neodloceno", nullable = false)
    private boolean dovoljenoNeodloceno = true;

    /* Koliko tock se ekipi, ki izgubi brez borbe, odsteje od skupnega stevila
       (Pravila SNTL: ena). 0 = pravila lige odbitka nimajo. */
    @Column(name = "odbitek_brez_boja", nullable = false)
    private int odbitekBrezBoja = 0;

    /* Igralec sme biti v kadru le ene ekipe v tej ligi. */
    @Column(name = "prepoved_dvojne_registracije", nullable = false)
    private boolean prepovedDvojneRegistracije = false;

    /* Raven tekmovanja doloci tezo posamicnih tekem lige v Turnirko ratingu
       (dvojice ne stejejo nikoli). Privzeto KLUBSKO - glej Turnir.raven. */
    @Enumerated(EnumType.STRING)
    @Column(name = "raven", nullable = false)
    private RavenTekmovanja raven = RavenTekmovanja.KLUBSKO;

    /* Enakomerna razvrstitev ekip: ekipe dobijo jakostni vrstni red
       (Ekipa.stNosilca), zreb pa jih po njem zveze v pare - i-ta ekipa
       zgornje polovice z i-to ekipo spodnje. Par nastopa kot celota: v
       vsakem krogu odigra oba dvoboja proti istemu nasprotnemu paru, zato
       vsaka ekipa v krogu dobi enega nasprotnika iz zgornje in enega iz
       spodnje polovice (glej RazporedStoritev). */
    @Column(name = "enakomerna_razvrstitev", nullable = false)
    private boolean enakomernaRazvrstitev = false;

    /* Ali je razpored VPISAL organizator namesto zreba. Na potek lige ne
       vpliva (srecanja so ista vrsta zapisa kot pri generiranem zrebu), je pa
       javna: liga, ki se je doslej vodila na roke, ima pare ze razdeljene in
       razposlane igralcem, zato mora stran povedati, da to ni nov naklucni
       zreb. Pise jo samo LigaStoritev - glej migracijo V26. */
    @Column(name = "rocni_zreb", nullable = false)
    private boolean rocniZreb = false;

    /* Predloga uradnega ekipnega zapisnika za natis (1. SNTL oz. 2./3. SNTL). */

    @Enumerated(EnumType.STRING)
    @Column(name = "predloga_listka", nullable = false)
    private PredlogaLige predlogaListka = PredlogaLige.SNTL_23;

    /* Seme terminov: kdaj se igra prvo kolo in na koliko dni sledijo naslednja.
       Iz njiju se ob generiranju razporeda izracunajo predvideni zacetki
       srecanj; kasnejsi rocni popravki posameznih kol semena ne spremenijo
       (prestavljeno kolo ne sme prestaviti vseh naslednjih). Ura je ura
       prvega srecanja kola, 00:00 pomeni "ura ni dolocena". */
    @Convert(converter = CasKotBesedilo.class)
    @Column(name = "zacetek_prvega_kola")
    private LocalDateTime zacetekPrvegaKola;

    @Column(name = "razmik_dni")
    private Integer razmikDni;

    /* Ure srecanj v kolu (V31), zapis "18:30,19:45": kolo je vecer s toliko
       srecanji zapored, i-to se zacne ob i-ti uri, in ekipa v njem sme igrati
       veckrat (nikoli dvakrat ob isti uri). Prazno = kolo kroznega sistema -
       vsaka ekipa enkrat, vsa srecanja ob uri iz semena. Besedilo in ne svoja
       tabela, ker je seznam del pravil lige in se nikoli ne bere brez nje. */
    @Column(name = "ure_srecanj")
    private String ureSrecanj;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_visja_liga")
    private Liga visjaLiga;

    /* Koncnica po rednem delu (V28): koliko najboljsih ekip jo igra (2, 4 ali
       8) in koliko zmag potrebuje ekipa za zmago v seriji (1 = ena tekma,
       2 = na dve zmagi ...). Oboje prazno pomeni ligo brez koncnice.
       Pravila koncnice so del pravil tekmovanja, zato jih kot vse ostalo ureja
       LigaStoritev.uredi (samo v pripravi). */
    @Column(name = "koncnica_ekip")
    private Integer koncnicaEkip;

    @Column(name = "koncnica_zmag")
    private Integer koncnicaZmag;

    /* Od kod je liga prisla (V27). Prazen = nastala je v Turnirku; sicer je
       uvozena in samo za branje (glej VirTekmovanja, LastnistvoStoritev). */
    @Enumerated(EnumType.STRING)
    @Column(name = "vir")
    private VirTekmovanja vir;

    @Column(name = "st_napreduje", nullable = false)
    private int stNapreduje = 0;

    @Column(name = "st_izpade", nullable = false)
    private int stIzpade = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StatusTekmovanja status = StatusTekmovanja.PRIPRAVA;

    /* Ali liga stoji v sklopu "Lige" na domaci strani. Urednistvo in ne
       pravilo tekmovanja (glej V17): ureja jo samo admin in samo prek
       LigaStoritev.nastaviNaDomaci, ki pazi na mejo dveh lig. */
    @Column(name = "na_domaci", nullable = false)
    private boolean naDomaci = false;

    /* Lastnistvo: racun, ki je ligo ustvaril, in posnetek njegovega kluba ob
       nastanku. Po njiju storitve razsodijo, kdo sme urejati - organizator sme
       svoje lige in tiste svojega kluba. Adminove lige nimajo lastnika. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_ustvaril")
    private Uporabnik ustvaril;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_klub_lastnik")
    private Klub klubLastnik;

    @Version
    @Column(name = "verzija", nullable = false)
    private long verzija;

    @Column(name = "ustvarjen_ob", nullable = false, updatable = false)
    private LocalDateTime ustvarjenOb;

    @PrePersist
    void obShranjevanju() {
        ustvarjenOb = LocalDateTime.now();
    }

    public Long getId() { return id; }

    public String getIme() { return ime; }
    public void setIme(String ime) { this.ime = ime; }

    public String getSezona() { return sezona; }
    public void setSezona(String sezona) { this.sezona = sezona; }

    public SpolKategorija getSpolKategorija() { return spolKategorija; }
    public void setSpolKategorija(SpolKategorija spolKategorija) { this.spolKategorija = spolKategorija; }

    public FormatSrecanja getFormatSrecanja() { return formatSrecanja; }
    public void setFormatSrecanja(FormatSrecanja formatSrecanja) { this.formatSrecanja = formatSrecanja; }

    public int getSteviloNizov() { return steviloNizov; }
    public void setSteviloNizov(int steviloNizov) { this.steviloNizov = steviloNizov; }

    public Integer getZmagZaSrecanje() { return zmagZaSrecanje; }
    public void setZmagZaSrecanje(Integer zmagZaSrecanje) { this.zmagZaSrecanje = zmagZaSrecanje; }

    public boolean isDvokrozno() { return dvokrozno; }
    public void setDvokrozno(boolean dvokrozno) { this.dvokrozno = dvokrozno; }

    public int getTockeZmaga() { return tockeZmaga; }
    public void setTockeZmaga(int tockeZmaga) { this.tockeZmaga = tockeZmaga; }

    public int getTockeNeodloceno() { return tockeNeodloceno; }
    public void setTockeNeodloceno(int tockeNeodloceno) { this.tockeNeodloceno = tockeNeodloceno; }

    public int getTockePoraz() { return tockePoraz; }
    public void setTockePoraz(int tockePoraz) { this.tockePoraz = tockePoraz; }

    public int getOdbitekBrezBoja() { return odbitekBrezBoja; }
    public void setOdbitekBrezBoja(int odbitekBrezBoja) { this.odbitekBrezBoja = odbitekBrezBoja; }

    public boolean isDovoljenoNeodloceno() { return dovoljenoNeodloceno; }
    public void setDovoljenoNeodloceno(boolean dovoljenoNeodloceno) { this.dovoljenoNeodloceno = dovoljenoNeodloceno; }

    public boolean isPrepovedDvojneRegistracije() { return prepovedDvojneRegistracije; }
    public void setPrepovedDvojneRegistracije(boolean v) { this.prepovedDvojneRegistracije = v; }

    public RavenTekmovanja getRaven() { return raven; }
    public void setRaven(RavenTekmovanja raven) { this.raven = raven; }

    public boolean isEnakomernaRazvrstitev() { return enakomernaRazvrstitev; }
    public void setEnakomernaRazvrstitev(boolean v) { this.enakomernaRazvrstitev = v; }

    public boolean isRocniZreb() { return rocniZreb; }
    public void setRocniZreb(boolean rocniZreb) { this.rocniZreb = rocniZreb; }

    public PredlogaLige getPredlogaListka() { return predlogaListka; }

    public void setPredlogaListka(PredlogaLige predlogaListka) { this.predlogaListka = predlogaListka; }

    public LocalDateTime getZacetekPrvegaKola() { return zacetekPrvegaKola; }
    public void setZacetekPrvegaKola(LocalDateTime zacetekPrvegaKola) { this.zacetekPrvegaKola = zacetekPrvegaKola; }

    public Integer getRazmikDni() { return razmikDni; }
    public void setRazmikDni(Integer razmikDni) { this.razmikDni = razmikDni; }

    /* null = kolo kroznega sistema; sicer ure srecanj kola po vrsti. */
    public List<LocalTime> getUreSrecanj() {
        if (ureSrecanj == null || ureSrecanj.isBlank()) {
            return null;
        }
        return Arrays.stream(ureSrecanj.split(",")).map(String::trim).map(LocalTime::parse).toList();
    }

    public void setUreSrecanj(List<LocalTime> ure) {
        this.ureSrecanj = ure == null || ure.isEmpty()
                ? null
                : ure.stream()
                        .map(u -> u.truncatedTo(ChronoUnit.MINUTES).toString())
                        .collect(Collectors.joining(","));
    }

    public Liga getVisjaLiga() { return visjaLiga; }
    public void setVisjaLiga(Liga visjaLiga) { this.visjaLiga = visjaLiga; }

    public Integer getKoncnicaEkip() { return koncnicaEkip; }
    public void setKoncnicaEkip(Integer koncnicaEkip) { this.koncnicaEkip = koncnicaEkip; }

    public Integer getKoncnicaZmag() { return koncnicaZmag; }
    public void setKoncnicaZmag(Integer koncnicaZmag) { this.koncnicaZmag = koncnicaZmag; }

    /* Ali liga po rednem delu igra koncnico. */
    public boolean imaKoncnico() { return koncnicaEkip != null; }

    public VirTekmovanja getVir() { return vir; }
    public void setVir(VirTekmovanja vir) { this.vir = vir; }

    /* Ali je liga uvozena iz zunanjega vira in torej samo za branje. */
    public boolean jeUvozena() { return vir != null; }

    public int getStNapreduje() { return stNapreduje; }
    public void setStNapreduje(int stNapreduje) { this.stNapreduje = stNapreduje; }

    public int getStIzpade() { return stIzpade; }
    public void setStIzpade(int stIzpade) { this.stIzpade = stIzpade; }

    public StatusTekmovanja getStatus() { return status; }
    public void setStatus(StatusTekmovanja status) { this.status = status; }

    public boolean isNaDomaci() { return naDomaci; }
    public void setNaDomaci(boolean naDomaci) { this.naDomaci = naDomaci; }

    public Uporabnik getUstvaril() { return ustvaril; }
    public void setUstvaril(Uporabnik ustvaril) { this.ustvaril = ustvaril; }

    public Klub getKlubLastnik() { return klubLastnik; }
    public void setKlubLastnik(Klub klubLastnik) { this.klubLastnik = klubLastnik; }

    public LocalDateTime getUstvarjenOb() { return ustvarjenOb; }
}
