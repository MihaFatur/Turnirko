/* Trenutna vrednost ratinga igralca v danem sistemu.
   "Sistem" je odprto besedilo: 'TURNIRKO' zdaj, kasneje npr. 'NTZS_TOCKE'.
   Vsaka sprememba mora imeti tudi zapis v rating_zgodovina. */
package si.turnirko.modeli;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
@Table(name = "rating_stanje")
public class RatingStanje {

    /* Ime sistema za Turnirko rating. */
    public static final String SISTEM_TURNIRKO = "TURNIRKO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_igralec", nullable = false)
    private Igralec igralec;

    @Column(name = "sistem", nullable = false)
    private String sistem;

    @Column(name = "vrednost", nullable = false)
    private int vrednost;

    @Column(name = "st_tekem", nullable = false)
    private int stTekem = 0;

    /* Cas zadnje obracunane tekme (velja_ob, ne cas vnosa) - iz njega se
       prepozna vrnitev po daljsi odsotnosti. Pretvornik je nujen iz istega
       razloga kot drugod: gonilnik sqlite-jdbc bi uro odrezal. */
    @Convert(converter = CasKotBesedilo.class)
    @Column(name = "zadnja_tekma_ob")
    private LocalDateTime zadnjaTekmaOb;

    /* Koliko tekem ima igralec se povisan K zaradi vrnitve (0 = nobene). */
    @Column(name = "preostanek_vrnitve", nullable = false)
    private int preostanekVrnitve = 0;

    /* Ali je rating POSTAVIL clovek in ne rezultati. Postavljenega igralca
       uvrstitev novinca ne povozi: kdor je rekel "ta igralec je 1500", je s tem
       povedal vec, kot pove njegov prvi turnir pri nas. */
    @Column(name = "postavljen", nullable = false)
    private boolean postavljen = false;

    /* Cas zadnje zunanje uvrstitve - drugi vir svezine poleg tekme.
       Loceno od zadnjaTekmaOb namenoma: tisto polje pomeni "zadnja TEKMA" in
       po njem se prepozna vrnitev po odsotnosti (visji K), ki se nanasa na
       igralca in ne na svezino nase stevilke. */
    @Convert(converter = CasKotBesedilo.class)
    @Column(name = "zunanja_uvrstitev_ob")
    private LocalDateTime zunanjaUvrstitevOb;

    /* Cas prve obracunane tekme. Dokler igralec igra na ta isti dan, je v
       obdobju UVRSTITVE: rating se mu ne sesteva po tekmah, ampak se vsakic
       znova izracuna iz vseh izidov tega dne (glej UvrstitevNovinca). */
    @Convert(converter = CasKotBesedilo.class)
    @Column(name = "prva_tekma_ob")
    private LocalDateTime prvaTekmaOb;

    @Version
    @Column(name = "verzija", nullable = false)
    private long verzija;

    protected RatingStanje() {}

    public RatingStanje(Igralec igralec, String sistem, int vrednost) {
        this.igralec = igralec;
        this.sistem = sistem;
        this.vrednost = vrednost;
    }

    public Long getId() { return id; }
    public Igralec getIgralec() { return igralec; }
    public String getSistem() { return sistem; }

    public int getVrednost() { return vrednost; }
    public void setVrednost(int vrednost) { this.vrednost = vrednost; }

    public int getStTekem() { return stTekem; }
    /* Postavi stevec neposredno - potrebuje ga ponovni preracun, ki stanje
       obnovi iz dnevnika. Redni obracun uporablja povecajStTekem(). */
    public void setStTekem(int stTekem) { this.stTekem = stTekem; }

    public void povecajStTekem() { this.stTekem++; }

    public LocalDateTime getZadnjaTekmaOb() { return zadnjaTekmaOb; }
    public void setZadnjaTekmaOb(LocalDateTime zadnjaTekmaOb) { this.zadnjaTekmaOb = zadnjaTekmaOb; }

    public int getPreostanekVrnitve() { return preostanekVrnitve; }
    public void setPreostanekVrnitve(int preostanekVrnitve) { this.preostanekVrnitve = preostanekVrnitve; }

    public boolean isPostavljen() { return postavljen; }
    public void setPostavljen(boolean postavljen) { this.postavljen = postavljen; }

    public LocalDateTime getPrvaTekmaOb() { return prvaTekmaOb; }
    public void setPrvaTekmaOb(LocalDateTime prvaTekmaOb) { this.prvaTekmaOb = prvaTekmaOb; }

    public LocalDateTime getZunanjaUvrstitevOb() { return zunanjaUvrstitevOb; }
    public void setZunanjaUvrstitevOb(LocalDateTime ob) { this.zunanjaUvrstitevOb = ob; }

    /* Kdaj smo o igralcu nazadnje kaj IZVEDELI: pozneje od zadnje tekme in
       zadnje zunanje uvrstitve. Po tem merita odbitek za neaktivnost in umik
       z javne lestvice - obema gre za svezino stevilke, ne za to, od kod je
       prisla. Sicer bi stevilki, pravkar prepisani z ITTF lestvice, odbili 40
       tock za odsotnost, ki jo ta stevilka ze uposteva, igralca pa bi kljub
       temu skrili z lestvice. */
    public LocalDateTime svezOb() {
        if (zunanjaUvrstitevOb == null) {
            return zadnjaTekmaOb;
        }
        if (zadnjaTekmaOb == null || zunanjaUvrstitevOb.isAfter(zadnjaTekmaOb)) {
            return zunanjaUvrstitevOb;
        }
        return zadnjaTekmaOb;
    }
}
