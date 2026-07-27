/* Igralec, upravicen nastopati za ekipo v ligi (kader). Nastop v postavi
   srecanja je mogoc le za igralce iz kadra. */
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

@Entity
@Table(name = "kader_ekipe")
public class KaderEkipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_ekipa", nullable = false)
    private Ekipa ekipa;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_igralec", nullable = false)
    private Igralec igralec;

    /* Jakostni vrstni red v ekipi (1 = najboljsi); neobvezno. */
    @Column(name = "vrstni_red")
    private Integer vrstniRed;

    protected KaderEkipe() {}

    public KaderEkipe(Ekipa ekipa, Igralec igralec, Integer vrstniRed) {
        this.ekipa = ekipa;
        this.igralec = igralec;
        this.vrstniRed = vrstniRed;
    }

    public Long getId() { return id; }
    public Ekipa getEkipa() { return ekipa; }
    public Igralec getIgralec() { return igralec; }

    public Integer getVrstniRed() { return vrstniRed; }
    public void setVrstniRed(Integer vrstniRed) { this.vrstniRed = vrstniRed; }
}
