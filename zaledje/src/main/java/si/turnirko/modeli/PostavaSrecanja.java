/* Postava: dodelitev igralca iz kadra na mesto v srecanju.
   Domaci zasedejo mesta A/B/C, gostje X/Y/Z; oznaka v_dvojici pove, kdo
   sestavlja par za dvojice (pri SNTL 2 od 3, pri Corbillon oba igralca). */
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

@Entity
@Table(name = "postava_srecanja")
public class PostavaSrecanja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_srecanje", nullable = false)
    private Srecanje srecanje;

    @Enumerated(EnumType.STRING)
    @Column(name = "stran", nullable = false)
    private StranEkipe stran;

    /* Mesto: 'A','B','C' za domace, 'X','Y','Z' za goste. */
    @Column(name = "pozicija", nullable = false)
    private String pozicija;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_igralec", nullable = false)
    private Igralec igralec;

    @Column(name = "v_dvojici", nullable = false)
    private boolean vDvojici = false;

    protected PostavaSrecanja() {}

    public PostavaSrecanja(Srecanje srecanje, StranEkipe stran, String pozicija,
                           Igralec igralec, boolean vDvojici) {
        this.srecanje = srecanje;
        this.stran = stran;
        this.pozicija = pozicija;
        this.igralec = igralec;
        this.vDvojici = vDvojici;
    }

    public Long getId() { return id; }
    public Srecanje getSrecanje() { return srecanje; }
    public StranEkipe getStran() { return stran; }
    public String getPozicija() { return pozicija; }
    public Igralec getIgralec() { return igralec; }
    public boolean isVDvojici() { return vDvojici; }
}
