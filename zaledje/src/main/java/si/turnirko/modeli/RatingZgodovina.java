/* Dnevnik VSEH sprememb ratinga (append-only).
   Iz njega je mogoce rekonstruirati vsako preteklo stanje,
   obenem pa je varovalka: ce za tekmo zapis ze obstaja,
   se ta tekma ne sme obracunati se enkrat. */
package si.turnirko.modeli;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    @Column(name = "ustvarjen_ob", nullable = false, updatable = false)
    private LocalDateTime ustvarjenOb;

    @PrePersist
    void obShranjevanju() {
        ustvarjenOb = LocalDateTime.now();
    }

    protected RatingZgodovina() {}

    public RatingZgodovina(Igralec igralec, String sistem, Tekma tekma, int sprememba, int novaVrednost) {
        this.igralec = igralec;
        this.sistem = sistem;
        this.tekma = tekma;
        this.sprememba = sprememba;
        this.novaVrednost = novaVrednost;
    }

    public RatingZgodovina(Igralec igralec, String sistem, TekmaSrecanja tekmaSrecanja,
                           int sprememba, int novaVrednost) {
        this.igralec = igralec;
        this.sistem = sistem;
        this.tekmaSrecanja = tekmaSrecanja;
        this.sprememba = sprememba;
        this.novaVrednost = novaVrednost;
    }

    /* Postavitveni (zacetni) rating: sprememba ni vezana na tekmo, zato ostaneta
       oba vira (tekma, tekmaSrecanja) prazna. */
    public RatingZgodovina(Igralec igralec, String sistem, int sprememba, int novaVrednost) {
        this.igralec = igralec;
        this.sistem = sistem;
        this.sprememba = sprememba;
        this.novaVrednost = novaVrednost;
    }

    public Long getId() { return id; }
    public Igralec getIgralec() { return igralec; }
    public String getSistem() { return sistem; }
    public Tekma getTekma() { return tekma; }
    public TekmaSrecanja getTekmaSrecanja() { return tekmaSrecanja; }
    public int getSprememba() { return sprememba; }
    public int getNovaVrednost() { return novaVrednost; }
    public LocalDateTime getUstvarjenOb() { return ustvarjenOb; }
}
