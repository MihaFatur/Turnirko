/* Srecanje dveh ekip v enem kolu lige. Sestavlja ga vec posamicnih tekem
   (tekma_srecanja) v vrstnem redu, ki ga doloca format lige. Dobljene tekme
   vsake strani so denormaliziran povzetek za hitro lestvico. */
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
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "srecanje")
public class Srecanje {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_liga", nullable = false)
    private Liga liga;

    @Column(name = "kolo", nullable = false)
    private int kolo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_ekipa_domaci", nullable = false)
    private Ekipa ekipaDomaci;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_ekipa_gost", nullable = false)
    private Ekipa ekipaGost;

    @Column(name = "dobljene_domaci", nullable = false)
    private int dobljeneDomaci = 0;

    @Column(name = "dobljene_gost", nullable = false)
    private int dobljeneGost = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StatusSrecanja status = StatusSrecanja.RAZPORED;

    /* Termin kola z uro; gonilnik bi jo brez pretvornika odrezal (glej
       CasKotBesedilo). */
    @Convert(converter = CasKotBesedilo.class)
    @Column(name = "predviden_zacetek")
    private LocalDateTime predvidenZacetek;

    @Column(name = "odigran_ob")
    private LocalDateTime odigranOb;

    @Version
    @Column(name = "verzija", nullable = false)
    private long verzija;

    protected Srecanje() {}

    public Srecanje(Liga liga, int kolo, Ekipa ekipaDomaci, Ekipa ekipaGost) {
        this.liga = liga;
        this.kolo = kolo;
        this.ekipaDomaci = ekipaDomaci;
        this.ekipaGost = ekipaGost;
    }

    public Long getId() { return id; }

    public Liga getLiga() { return liga; }
    public int getKolo() { return kolo; }
    public void setKolo(int kolo) { this.kolo = kolo; }

    public Ekipa getEkipaDomaci() { return ekipaDomaci; }
    public Ekipa getEkipaGost() { return ekipaGost; }

    public int getDobljeneDomaci() { return dobljeneDomaci; }
    public void setDobljeneDomaci(int dobljeneDomaci) { this.dobljeneDomaci = dobljeneDomaci; }

    public int getDobljeneGost() { return dobljeneGost; }
    public void setDobljeneGost(int dobljeneGost) { this.dobljeneGost = dobljeneGost; }

    public StatusSrecanja getStatus() { return status; }
    public void setStatus(StatusSrecanja status) { this.status = status; }

    public LocalDateTime getPredvidenZacetek() { return predvidenZacetek; }
    public void setPredvidenZacetek(LocalDateTime predvidenZacetek) { this.predvidenZacetek = predvidenZacetek; }

    public LocalDateTime getOdigranOb() { return odigranOb; }
    public void setOdigranOb(LocalDateTime odigranOb) { this.odigranOb = odigranOb; }
}
