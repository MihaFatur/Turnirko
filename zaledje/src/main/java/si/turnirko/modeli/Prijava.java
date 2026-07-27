/* Prijava igralca na dogodek.
   Tekme se sklicujejo na PRIJAVE (ne neposredno na igralce), ker prijava
   hrani posnetek kluba in ratinga ob zrebu, v prihodnosti pa bo lahko
   predstavljala tudi par (dvojice) brez spremembe tabele tekem. */
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "prijava")
public class Prijava {

    /* PRIJAVLJEN      - igra (oz. bo igral),
       ODJAVLJEN       - odjavil se je sam pred zrebom,
       DISKVALIFICIRAN - disciplinski ukrep,
       REZERVA         - prijavljen je, a ni prisel v izbor najboljsih N
                         (sistem SKUPINE); ob odpovedi pred zrebom lahko
                         se vskoci,
       ODSTOPIL        - odstopil je med turnirjem; ze odigrane tekme
                         obveljajo, preostale dobijo nasprotniki. */
    public enum StatusPrijave { PRIJAVLJEN, ODJAVLJEN, DISKVALIFICIRAN, REZERVA, ODSTOPIL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_dogodek", nullable = false)
    private Dogodek dogodek;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_igralec", nullable = false)
    private Igralec igralec;

    /* Posnetek kluba OB PRIJAVI - zgodovina ostane pravilna
       tudi ce igralec kasneje prestopi. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_klub_ob_prijavi")
    private Klub klubObPrijavi;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StatusPrijave status = StatusPrijave.PRIJAVLJEN;

    /* Mesto na jakostni lestvici dogodka (1 = najmocnejsi); null = ni dolocen.
       Pri sistemu SKUPINE po njem tece izbor najboljsih N in zaporedna
       razporeditev v skupine, zato ga administrator uredi pred zrebom. */
    @Column(name = "st_nosilca")
    private Integer stNosilca;

    /* Posnetek ratinga ob zrebu - za sledljivost in ponovljivost zreba. */
    @Column(name = "rating_ob_zrebu")
    private Integer ratingObZrebu;

    /* Skupina, v katero je igralca uvrstil zreb, in njegovo mesto v njej. */
    @Column(name = "id_skupina")
    private Long idSkupina;

    @Column(name = "mesto_v_skupini")
    private Integer mestoVSkupini;

    /* Koncna uvrstitev na dogodku (1 = zmagovalec). */
    @Column(name = "koncno_mesto")
    private Integer koncnoMesto;

    @Column(name = "placano", nullable = false)
    private boolean placano = false;

    @Column(name = "prijavljen_ob", nullable = false, updatable = false)
    private LocalDateTime prijavljenOb;

    @Version
    @Column(name = "verzija", nullable = false)
    private long verzija;

    @PrePersist
    void obShranjevanju() {
        prijavljenOb = LocalDateTime.now();
    }

    protected Prijava() {}

    public Prijava(Dogodek dogodek, Igralec igralec) {
        this.dogodek = dogodek;
        this.igralec = igralec;
        this.klubObPrijavi = igralec.getKlub();
    }

    public Long getId() { return id; }
    public Dogodek getDogodek() { return dogodek; }
    public Igralec getIgralec() { return igralec; }
    public Klub getKlubObPrijavi() { return klubObPrijavi; }
    /* Ob ponovni prijavi po odjavi se posnetek kluba osvezi (moznost prestopa). */
    public void setKlubObPrijavi(Klub klubObPrijavi) { this.klubObPrijavi = klubObPrijavi; }

    public StatusPrijave getStatus() { return status; }
    public void setStatus(StatusPrijave status) { this.status = status; }

    public Integer getStNosilca() { return stNosilca; }
    public void setStNosilca(Integer stNosilca) { this.stNosilca = stNosilca; }

    public Integer getRatingObZrebu() { return ratingObZrebu; }
    public void setRatingObZrebu(Integer ratingObZrebu) { this.ratingObZrebu = ratingObZrebu; }

    public Long getIdSkupina() { return idSkupina; }
    public void setIdSkupina(Long idSkupina) { this.idSkupina = idSkupina; }

    public Integer getMestoVSkupini() { return mestoVSkupini; }
    public void setMestoVSkupini(Integer mestoVSkupini) { this.mestoVSkupini = mestoVSkupini; }

    public Integer getKoncnoMesto() { return koncnoMesto; }
    public void setKoncnoMesto(Integer koncnoMesto) { this.koncnoMesto = koncnoMesto; }

    public boolean isPlacano() { return placano; }
    public void setPlacano(boolean placano) { this.placano = placano; }

    public LocalDateTime getPrijavljenOb() { return prijavljenOb; }
}
