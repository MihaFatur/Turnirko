/* Dnevnik VSEH sprememb ratinga (append-only).
   Iz njega je mogoce rekonstruirati vsako preteklo stanje,
   obenem pa je varovalka: ce za tekmo zapis ze obstaja,
   se ta tekma ne sme obracunati se enkrat. */
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

@Entity
@Table(name = "rating_zgodovina")
public class RatingZgodovina {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_igralec", nullable = false)
    private Igralec igralec;

    @Column(name = "sistem", nullable = false)
    private String sistem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_tekma")
    private Tekma tekma;

    /* Alternativni vir spremembe: posamicna tekma ligaskega srecanja.
       Zapis se veze bodisi na turnirsko tekmo bodisi na ligasko - nikoli na obe. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_tekma_srecanja")
    private TekmaSrecanja tekmaSrecanja;

    /* Sprememba ratinga (npr. +16 ali -16). */
    @Column(name = "sprememba", nullable = false)
    private int sprememba;

    @Column(name = "nova_vrednost", nullable = false)
    private int novaVrednost;

    /* Zakaj je zapis nastal, kadar ni posledica tekme (NULL pri tekmah). */
    @Enumerated(EnumType.STRING)
    @Column(name = "razlog")
    private RazlogSpremembe razlog;

    /* Ali je igralec tekmo dobil (1) ali izgubil (0); pri postavitvenem zapisu
       (brez tekme) NULL. Iz spremembe tock izida ni mogoce zanesljivo prebrati:
       sprememba je lahko tudi 0, ko favorit premaga mnogo sibkejsega in se
       zaokrozi na nic. Izid potrebuje uvrstitev novinca (UvrstitevNovinca),
       uporablja pa ga tudi razlaga spremembe v vmesniku. */
    @Column(name = "tocke")
    private Integer tocke;

    /* Sestavine, iz katerih je sprememba nastala:
       sprememba = k x margina x teza x (tocke - pricakovano).
       Prazne, kadar sprememba ne nastane po tem obrazcu - pri postavitvi in
       odbitku za neaktivnost (tekme ni) in pri UVRSTITVI NOVINCA, kjer se
       rating izracuna znova iz vseh izidov prvega dne. Vrstica, ki ima tekmo
       in nima k, je torej natanko uvrstitev. */
    @Column(name = "k")
    private Integer k;

    @Column(name = "margina")
    private Double margina;

    @Column(name = "teza")
    private Double teza;

    @Column(name = "pricakovano")
    private Double pricakovano;

    /* Od kod je stevilka in zakaj je bil poseg potreben. Pri ZUNANJI
       UVRSTITVI sta oba OBVEZNA (varuje ju tudi CHECK v V25) in oba javno
       vidna na profilu: rocno vpisana stevilka brez zapisanega vira je videti
       kot naklonjenost, z virom pa je trditev, ki jo lahko vsak preveri. */
    @Column(name = "vir")
    private String vir;

    @Column(name = "pojasnilo")
    private String pojasnilo;

    /* Kdaj sprememba VELJA: cas tekme, ki jo je povzrocila - ne cas zapisa.
       Po njem tecejo preracun od datuma, graf napredka in odbitki za
       neaktivnost. Pretvornik je nujen, ker gonilnik sqlite-jdbc uro sicer
       odreze (glej CasKotBesedilo). */
    @Convert(converter = CasKotBesedilo.class)
    @Column(name = "velja_ob", nullable = false)
    private LocalDateTime veljaOb;

    @Column(name = "ustvarjen_ob", nullable = false, updatable = false)
    private LocalDateTime ustvarjenOb;

    @PrePersist
    void obShranjevanju() {
        ustvarjenOb = LocalDateTime.now();
        if (veljaOb == null) {
            veljaOb = ustvarjenOb;
        }
    }

    protected RatingZgodovina() {}

    public RatingZgodovina(Igralec igralec, String sistem, Tekma tekma, int sprememba,
                           int novaVrednost, LocalDateTime veljaOb, boolean zmaga) {
        this.igralec = igralec;
        this.sistem = sistem;
        this.tekma = tekma;
        this.sprememba = sprememba;
        this.novaVrednost = novaVrednost;
        this.veljaOb = veljaOb;
        this.tocke = zmaga ? 1 : 0;
    }

    public RatingZgodovina(Igralec igralec, String sistem, TekmaSrecanja tekmaSrecanja,
                           int sprememba, int novaVrednost, LocalDateTime veljaOb,
                           boolean zmaga) {
        this.igralec = igralec;
        this.sistem = sistem;
        this.tekmaSrecanja = tekmaSrecanja;
        this.sprememba = sprememba;
        this.novaVrednost = novaVrednost;
        this.veljaOb = veljaOb;
        this.tocke = zmaga ? 1 : 0;
    }

    /* Postavitveni (zacetni) rating: sprememba ni vezana na tekmo, zato ostaneta
       oba vira (tekma, tekmaSrecanja) prazna in zapis velja takoj. */
    public RatingZgodovina(Igralec igralec, String sistem, int sprememba, int novaVrednost) {
        this.igralec = igralec;
        this.sistem = sistem;
        this.sprememba = sprememba;
        this.novaVrednost = novaVrednost;
        this.veljaOb = LocalDateTime.now();
        this.razlog = RazlogSpremembe.POSTAVITEV;
    }

    /* Odbitek za neaktivnost: velja na dan, ko je ZAPADEL, in ne na dan vpisa -
       le tako ga zna ponovni preracun postaviti na isto mesto v casovno vrsto. */
    public RatingZgodovina(Igralec igralec, String sistem, int sprememba, int novaVrednost,
                           LocalDateTime veljaOb, RazlogSpremembe razlog) {
        this.igralec = igralec;
        this.sistem = sistem;
        this.sprememba = sprememba;
        this.novaVrednost = novaVrednost;
        this.veljaOb = veljaOb;
        this.razlog = razlog;
    }

    /* Zunanja uvrstitev: stevilka z zunanje lestvice, z obveznim virom in
       pojasnilom. Velja ob svojem casu (ne ob casu vpisa), ker jo mora
       ponovni preracun odigrati na istem mestu med tekmami. */
    public static RatingZgodovina zunanjaUvrstitev(Igralec igralec, String sistem,
                                                   int sprememba, int novaVrednost,
                                                   LocalDateTime veljaOb,
                                                   String vir, String pojasnilo) {
        RatingZgodovina zapis = new RatingZgodovina(igralec, sistem, sprememba, novaVrednost,
                veljaOb, RazlogSpremembe.ZUNANJA_UVRSTITEV);
        zapis.vir = vir;
        zapis.pojasnilo = pojasnilo;
        return zapis;
    }

    /* Popravi zapisano spremembo. Potrebuje jo ponovni preracun: zunanja
       uvrstitev pove, na KATERO vrednost je igralec postavljen, razlika do
       prejsnje pa je odvisna od vsega, kar se je zgodilo prej - in prav to
       preracun odigra znova. */
    public void popraviSpremembo(int sprememba) {
        this.sprememba = sprememba;
    }

    public Long getId() { return id; }
    public Igralec getIgralec() { return igralec; }
    public String getSistem() { return sistem; }
    public Tekma getTekma() { return tekma; }
    public TekmaSrecanja getTekmaSrecanja() { return tekmaSrecanja; }
    public int getSprememba() { return sprememba; }
    public int getNovaVrednost() { return novaVrednost; }
    public LocalDateTime getVeljaOb() { return veljaOb; }
    public Integer getTocke() { return tocke; }
    public Integer getK() { return k; }
    public Double getMargina() { return margina; }
    public Double getTeza() { return teza; }
    public Double getPricakovano() { return pricakovano; }
    public String getVir() { return vir; }
    public String getPojasnilo() { return pojasnilo; }

    /* Zapise sestavine spremembe. Locena metoda in ne se dva konstruktorja:
       konstruktorjev je ze pet, sestavin pa ni vedno (glej polja). */
    public RatingZgodovina zRazclenitvijo(Integer k, Double margina, Double teza,
                                          Double pricakovano) {
        this.k = k;
        this.margina = margina;
        this.teza = teza;
        this.pricakovano = pricakovano;
        return this;
    }
    public RazlogSpremembe getRazlog() { return razlog; }
    public LocalDateTime getUstvarjenOb() { return ustvarjenOb; }
}
