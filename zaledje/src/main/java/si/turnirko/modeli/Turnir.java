/* Turnir je prireditev (kraj, dvorana, datumi). Posamezna tekmovanja
   na turnirju so DOGODKI (npr. "clani posamicno") - igralci se
   prijavljajo na dogodke, ne na turnir. */
package si.turnirko.modeli;

import java.time.LocalDate;
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
@Table(name = "turnir")
public class Turnir {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "ime", nullable = false)
    private String ime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "postna_st")
    private Kraj kraj;

    @Column(name = "dvorana")
    private String dvorana;

    @Column(name = "datum_zacetka")
    private LocalDate datumZacetka;

    @Column(name = "datum_konca")
    private LocalDate datumKonca;

    /* Status vedno doloca streznik, nikoli odjemalec. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StatusTekmovanja status = StatusTekmovanja.PRIPRAVA;

    @Column(name = "opombe")
    private String opombe;

    /* Optimisticno zaklepanje - prepreci, da bi si dva socasna
       zapisa tiho prepisala podatke. */
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

    public Kraj getKraj() { return kraj; }
    public void setKraj(Kraj kraj) { this.kraj = kraj; }

    public String getDvorana() { return dvorana; }
    public void setDvorana(String dvorana) { this.dvorana = dvorana; }

    public LocalDate getDatumZacetka() { return datumZacetka; }
    public void setDatumZacetka(LocalDate datumZacetka) { this.datumZacetka = datumZacetka; }

    public LocalDate getDatumKonca() { return datumKonca; }
    public void setDatumKonca(LocalDate datumKonca) { this.datumKonca = datumKonca; }

    public StatusTekmovanja getStatus() { return status; }
    public void setStatus(StatusTekmovanja status) { this.status = status; }

    public String getOpombe() { return opombe; }
    public void setOpombe(String opombe) { this.opombe = opombe; }

    public LocalDateTime getUstvarjenOb() { return ustvarjenOb; }
}
