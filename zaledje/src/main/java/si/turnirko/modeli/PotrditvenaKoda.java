/* Sestmestna koda, poslana po e-posti: potrditev naslova, soglasje skrbnika
   ali pozabljeno geslo. Shranjena je samo zgostitev (kot pri geslu), ziva je
   najvec ena na racun in namen.

   Milijon moznosti brez omejitve ni nobena ovira, zato kodo varujeta
   veljavnost (potece_ob) in stevec poskusov: po NAJVEC_POSKUSOV napacnih
   vnosih koda propade in je treba zahtevati novo. */
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
@Table(name = "potrditvena_koda")
public class PotrditvenaKoda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_racun", nullable = false)
    private Uporabnik racun;

    @Enumerated(EnumType.STRING)
    @Column(name = "namen", nullable = false)
    private NamenKode namen;

    @Column(name = "koda_hash", nullable = false)
    private String kodaHash;

    /* Ura je del pomena: brez nje bi koda potekla ze ob nastanku (potek je
       10 minut po nastanku). */
    @Column(name = "ustvarjen_ob", nullable = false, updatable = false)
    private LocalDateTime ustvarjenOb;

    @Column(name = "potece_ob", nullable = false)
    private LocalDateTime poteceOb;

    @Column(name = "poskusi", nullable = false)
    private int poskusi = 0;

    @Column(name = "porabljen_ob")
    private LocalDateTime porabljenOb;

    protected PotrditvenaKoda() {}

    public PotrditvenaKoda(Uporabnik racun, NamenKode namen, String kodaHash,
                           LocalDateTime ustvarjenOb, LocalDateTime poteceOb) {
        this.racun = racun;
        this.namen = namen;
        this.kodaHash = kodaHash;
        this.ustvarjenOb = ustvarjenOb;
        this.poteceOb = poteceOb;
    }

    /* Koda, ki jo je se mogoce vpisati: ni porabljena in ni potekla. */
    public boolean jeZiva(LocalDateTime zdaj) {
        return porabljenOb == null && poteceOb.isAfter(zdaj);
    }

    public Long getId() { return id; }
    public Uporabnik getRacun() { return racun; }
    public NamenKode getNamen() { return namen; }
    public String getKodaHash() { return kodaHash; }
    public LocalDateTime getUstvarjenOb() { return ustvarjenOb; }

    public LocalDateTime getPoteceOb() { return poteceOb; }
    public void setPoteceOb(LocalDateTime poteceOb) { this.poteceOb = poteceOb; }

    public int getPoskusi() { return poskusi; }
    public void setPoskusi(int poskusi) { this.poskusi = poskusi; }

    public LocalDateTime getPorabljenOb() { return porabljenOb; }
    public void setPorabljenOb(LocalDateTime porabljenOb) { this.porabljenOb = porabljenOb; }
}
