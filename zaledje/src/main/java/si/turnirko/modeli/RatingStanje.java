/* Trenutna vrednost ratinga igralca v danem sistemu.
   "Sistem" je odprto besedilo: 'KLUBSKI_ELO' zdaj, kasneje npr. 'NTZS_TOCKE'.
   Vsaka sprememba mora imeti tudi zapis v rating_zgodovina. */
package si.turnirko.modeli;

import jakarta.persistence.Column;
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

    /* Ime sistema za klubski ELO rating. */
    public static final String SISTEM_KLUBSKI_ELO = "KLUBSKI_ELO";

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
    public void povecajStTekem() { this.stTekem++; }
}
