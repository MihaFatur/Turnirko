/* Eno branje sodniskega listka s fotografije (V68).

   Branje je PREDLOG izida in njegova sled, ne izid: v tekmo se zapise sele,
   ko ga organizator potrdi (BranjeListkovStoritev.potrdi -> TekmaStoritev).
   Fotografija je v SlikaBranjaListka, da je seznam branj ne nalaga. */
package si.turnirko.modeli;

import java.time.LocalDateTime;

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

@Entity
@Table(name = "branje_listka")
public class BranjeListka {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_turnir", nullable = false)
    private Turnir turnir;

    /* Prepoznana (ali izbrana) tekma; prazna, dokler je ni. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_tekma")
    private Tekma tekma;

    @Enumerated(EnumType.STRING)
    @Column(name = "stanje", nullable = false)
    private StanjeBranja stanje = StanjeBranja.V_VRSTI;

    @Enumerated(EnumType.STRING)
    @Column(name = "prepoznava")
    private PrepoznavaTekme prepoznava;

    /* JSON bralnika (PrebranListek), surovo. */
    @Column(name = "prebrano")
    private String prebrano;

    /* Lega celic na poravnanem listku (F4.1, V71): JSON CeliceListkaDto. */
    @Column(name = "celice")
    private String celice;

    /* JSON potrjenega izida (VnosRezultata). */
    @Column(name = "potrjeno")
    private String potrjeno;

    /* Ali je organizator pred potrditvijo kaj spremenil - merilo natancnosti. */
    @Column(name = "popravljeno")
    private Boolean popravljeno;

    @Column(name = "napaka")
    private String napaka;

    @Column(name = "model")
    private String model;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_nalozil")
    private Uporabnik nalozil;

    @Column(name = "ustvarjeno_ob", nullable = false)
    private LocalDateTime ustvarjenoOb;

    @Column(name = "prebrano_ob")
    private LocalDateTime prebranoOb;

    @Column(name = "zakljuceno_ob")
    private LocalDateTime zakljucenoOb;

    @Column(name = "slika_izbrisana_ob")
    private LocalDateTime slikaIzbrisanaOb;

    /* Nakljucna kontrola (V69): predlog ni ponujen za en dotik, pregleda se v celoti. */
    @Column(name = "kontrola", nullable = false)
    private boolean kontrola;

    /* Ali je bil predlog ob potrditvi pripravljen za en dotik; null = pred V69. */
    @Column(name = "pripravljen")
    private Boolean pripravljen;

    @Column(name = "cas_pregleda_ms")
    private Integer casPregledaMs;

    @Enumerated(EnumType.STRING)
    @Column(name = "nacin_potrditve")
    private NacinPotrditve nacinPotrditve;

    /* Nakljucni kljuc posiljanja s telefona (F5.2, V73): ponovljeno posiljanje iste
       fotografije vrne to branje namesto dvojnika. Po 24 urah se pobrise. */
    @Column(name = "kljuc_posiljanja", length = 64)
    private String kljucPosiljanja;

    /* Sodniska pot (S2, V75): fotografijo je poslal sodnik brez prijave (nalozil je
       null). Pravico za potrditev ima samo telefon z zetonom - v bazi je SHA-256. */
    @Column(name = "poslal_sodnik", nullable = false)
    private boolean poslalSodnik;

    @Column(name = "zeton_sodnika")
    private byte[] zetonSodnika;

    @Column(name = "zeton_velja_do")
    private LocalDateTime zetonVeljaDo;

    @Column(name = "sodnik_potrdil_ob")
    private LocalDateTime sodnikPotrdilOb;

    /* Izid, kot ga je potrdil sodnik (JSON VnosRezultata); pri predpregledu ga
       organizator potrdi z enim dotikom. */
    @Column(name = "sodnikov_izid")
    private String sodnikovIzid;

    @Column(name = "sodnik_spremenil")
    private Boolean sodnikSpremenil;

    @Column(name = "miza_sodnika")
    private Integer mizaSodnika;

    protected BranjeListka() {}

    public BranjeListka(Turnir turnir, Uporabnik nalozil, LocalDateTime ustvarjenoOb) {
        this.turnir = turnir;
        this.nalozil = nalozil;
        this.ustvarjenoOb = ustvarjenoOb;
    }

    public Long getId() { return id; }
    public Turnir getTurnir() { return turnir; }
    public Tekma getTekma() { return tekma; }
    public void setTekma(Tekma tekma) { this.tekma = tekma; }
    public StanjeBranja getStanje() { return stanje; }
    public void setStanje(StanjeBranja stanje) { this.stanje = stanje; }
    public PrepoznavaTekme getPrepoznava() { return prepoznava; }
    public void setPrepoznava(PrepoznavaTekme prepoznava) { this.prepoznava = prepoznava; }
    public String getPrebrano() { return prebrano; }
    public String getCelice() { return celice; }
    public void setCelice(String celice) { this.celice = celice; }
    public void setPrebrano(String prebrano) { this.prebrano = prebrano; }
    public String getPotrjeno() { return potrjeno; }
    public void setPotrjeno(String potrjeno) { this.potrjeno = potrjeno; }
    public Boolean getPopravljeno() { return popravljeno; }
    public void setPopravljeno(Boolean popravljeno) { this.popravljeno = popravljeno; }
    public String getNapaka() { return napaka; }
    public void setNapaka(String napaka) { this.napaka = napaka; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public Uporabnik getNalozil() { return nalozil; }
    public boolean isPoslalSodnik() { return poslalSodnik; }
    public void setPoslalSodnik(boolean poslalSodnik) { this.poslalSodnik = poslalSodnik; }

    public byte[] getZetonSodnika() { return zetonSodnika; }
    public void setZetonSodnika(byte[] zetonSodnika) { this.zetonSodnika = zetonSodnika; }

    public LocalDateTime getZetonVeljaDo() { return zetonVeljaDo; }
    public void setZetonVeljaDo(LocalDateTime zetonVeljaDo) { this.zetonVeljaDo = zetonVeljaDo; }

    public LocalDateTime getSodnikPotrdilOb() { return sodnikPotrdilOb; }
    public void setSodnikPotrdilOb(LocalDateTime sodnikPotrdilOb) { this.sodnikPotrdilOb = sodnikPotrdilOb; }

    public String getSodnikovIzid() { return sodnikovIzid; }
    public void setSodnikovIzid(String sodnikovIzid) { this.sodnikovIzid = sodnikovIzid; }

    public Boolean getSodnikSpremenil() { return sodnikSpremenil; }
    public void setSodnikSpremenil(Boolean sodnikSpremenil) { this.sodnikSpremenil = sodnikSpremenil; }

    public Integer getMizaSodnika() { return mizaSodnika; }
    public void setMizaSodnika(Integer mizaSodnika) { this.mizaSodnika = mizaSodnika; }

    public String getKljucPosiljanja() { return kljucPosiljanja; }
    public void setKljucPosiljanja(String kljucPosiljanja) { this.kljucPosiljanja = kljucPosiljanja; }
    public LocalDateTime getUstvarjenoOb() { return ustvarjenoOb; }
    public LocalDateTime getPrebranoOb() { return prebranoOb; }
    public void setPrebranoOb(LocalDateTime prebranoOb) { this.prebranoOb = prebranoOb; }
    public LocalDateTime getZakljucenoOb() { return zakljucenoOb; }
    public void setZakljucenoOb(LocalDateTime zakljucenoOb) { this.zakljucenoOb = zakljucenoOb; }
    public LocalDateTime getSlikaIzbrisanaOb() { return slikaIzbrisanaOb; }
    public void setSlikaIzbrisanaOb(LocalDateTime slikaIzbrisanaOb) { this.slikaIzbrisanaOb = slikaIzbrisanaOb; }
    public boolean isKontrola() { return kontrola; }
    public void setKontrola(boolean kontrola) { this.kontrola = kontrola; }
    public Boolean getPripravljen() { return pripravljen; }
    public void setPripravljen(Boolean pripravljen) { this.pripravljen = pripravljen; }
    public Integer getCasPregledaMs() { return casPregledaMs; }
    public void setCasPregledaMs(Integer casPregledaMs) { this.casPregledaMs = casPregledaMs; }
    public NacinPotrditve getNacinPotrditve() { return nacinPotrditve; }
    public void setNacinPotrditve(NacinPotrditve nacinPotrditve) { this.nacinPotrditve = nacinPotrditve; }
}
