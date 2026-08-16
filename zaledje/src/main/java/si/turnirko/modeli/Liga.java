/* Liga je sezonsko ekipno tekmovanje med klubi (SNTL, rekreacijske lige).
   Konfiguracija je namenoma prilagodljiva: format srecanja, stevilo nizov,
   prag zmag za konec srecanja, tockovanje in stetje v ELO se izberejo ob
   ustvarjanju. Lige so lahko povezane (id_visja_liga) za prehode med sezonami. */
package si.turnirko.modeli;

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

    /* Igralec sme biti v kadru le ene ekipe v tej ligi. */
    @Column(name = "prepoved_dvojne_registracije", nullable = false)
    private boolean prepovedDvojneRegistracije = false;

    /* Ali posamicne tekme lige stejejo v klubski ELO (dvojice nikoli). */
    @Column(name = "steje_v_elo", nullable = false)
    private boolean stejeVElo = true;

    /* Predloga uradnega ekipnega zapisnika za natis (1. SNTL oz. 2./3. SNTL). */
    @Enumerated(EnumType.STRING)
    @Column(name = "predloga_listka", nullable = false)
    private PredlogaLige predlogaListka = PredlogaLige.SNTL_23;

    /* Seme terminov: kdaj se igra prvo kolo in na koliko dni sledijo naslednja.
       Iz njiju se ob generiranju razporeda izracunajo predvideni zacetki
       srecanj; kasnejsi rocni popravki posameznih kol semena ne spremenijo
       (prestavljeno kolo ne sme prestaviti vseh naslednjih). Ura velja za celo
       kolo, 00:00 pomeni "ura ni dolocena". */
    @Convert(converter = CasKotBesedilo.class)
    @Column(name = "zacetek_prvega_kola")
    private LocalDateTime zacetekPrvegaKola;

    @Column(name = "razmik_dni")
    private Integer razmikDni;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_visja_liga")
    private Liga visjaLiga;

    @Column(name = "st_napreduje", nullable = false)
    private int stNapreduje = 0;

    @Column(name = "st_izpade", nullable = false)
    private int stIzpade = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StatusTekmovanja status = StatusTekmovanja.PRIPRAVA;

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

    public boolean isDovoljenoNeodloceno() { return dovoljenoNeodloceno; }
    public void setDovoljenoNeodloceno(boolean dovoljenoNeodloceno) { this.dovoljenoNeodloceno = dovoljenoNeodloceno; }

    public boolean isPrepovedDvojneRegistracije() { return prepovedDvojneRegistracije; }
    public void setPrepovedDvojneRegistracije(boolean v) { this.prepovedDvojneRegistracije = v; }

    public boolean isStejeVElo() { return stejeVElo; }
    public void setStejeVElo(boolean stejeVElo) { this.stejeVElo = stejeVElo; }

    public PredlogaLige getPredlogaListka() { return predlogaListka; }
    public void setPredlogaListka(PredlogaLige predlogaListka) { this.predlogaListka = predlogaListka; }

    public LocalDateTime getZacetekPrvegaKola() { return zacetekPrvegaKola; }
    public void setZacetekPrvegaKola(LocalDateTime zacetekPrvegaKola) { this.zacetekPrvegaKola = zacetekPrvegaKola; }

    public Integer getRazmikDni() { return razmikDni; }
    public void setRazmikDni(Integer razmikDni) { this.razmikDni = razmikDni; }

    public Liga getVisjaLiga() { return visjaLiga; }
    public void setVisjaLiga(Liga visjaLiga) { this.visjaLiga = visjaLiga; }

    public int getStNapreduje() { return stNapreduje; }
    public void setStNapreduje(int stNapreduje) { this.stNapreduje = stNapreduje; }

    public int getStIzpade() { return stIzpade; }
    public void setStIzpade(int stIzpade) { this.stIzpade = stIzpade; }

    public StatusTekmovanja getStatus() { return status; }
    public void setStatus(StatusTekmovanja status) { this.status = status; }

    public Uporabnik getUstvaril() { return ustvaril; }
    public void setUstvaril(Uporabnik ustvaril) { this.ustvaril = ustvaril; }

    public Klub getKlubLastnik() { return klubLastnik; }
    public void setKlubLastnik(Klub klubLastnik) { this.klubLastnik = klubLastnik; }

    public LocalDateTime getUstvarjenOb() { return ustvarjenOb; }
}
