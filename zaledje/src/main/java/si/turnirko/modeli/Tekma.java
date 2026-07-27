/* Tekma. Kljucne zasnove:
   - kolo + pozicija dolocata mesto v mrezi (kolo 1 = prvo kolo),
   - napredovanje NI izracunano s formulo, ampak z EKSPLICITNIMI povezavami:
     idIzvorTekma1/2 + vlogaIzvora1/2 povesta, od kod prideta igralca
     (omogoca tudi tekmo za 3. mesto: "porazenec polfinala 1"),
   - izidTip loci normalno odigrane tekme od posebnih izidov
     (PROSTO/bye, BREZ_BOJA/w.o., PREDAJA, DISKVALIFIKACIJA). */
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
import jakarta.persistence.Version;

@Entity
@Table(name = "tekma")
public class Tekma {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_dogodek", nullable = false)
    private Dogodek dogodek;

    @Enumerated(EnumType.STRING)
    @Column(name = "faza", nullable = false)
    private FazaTekme faza = FazaTekme.GLAVNI;

    @Column(name = "id_skupina")
    private Long idSkupina;

    @Column(name = "kolo", nullable = false)
    private int kolo;

    @Column(name = "pozicija", nullable = false)
    private int pozicija;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_prijava_1")
    private Prijava prijava1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_prijava_2")
    private Prijava prijava2;

    /* Eksplicitni povezavi napredovanja: iz katere tekme in v kaksni vlogi
       (zmagovalec/porazenec) prideta igralca v to tekmo. */
    @Column(name = "id_izvor_tekma_1")
    private Long idIzvorTekma1;

    @Enumerated(EnumType.STRING)
    @Column(name = "vloga_izvora_1")
    private VlogaIzvora vlogaIzvora1;

    @Column(name = "id_izvor_tekma_2")
    private Long idIzvorTekma2;

    @Enumerated(EnumType.STRING)
    @Column(name = "vloga_izvora_2")
    private VlogaIzvora vlogaIzvora2;

    /* "Najboljsi od N nizov" za TO tekmo (za zmago je potrebnih N/2+1 nizov). */
    @Column(name = "stevilo_nizov", nullable = false)
    private int steviloNizov;

    @Column(name = "dobljeni_nizi_1", nullable = false)
    private int dobljeniNizi1 = 0;

    @Column(name = "dobljeni_nizi_2", nullable = false)
    private int dobljeniNizi2 = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_zmagovalec_prijava")
    private Prijava zmagovalec;

    @Enumerated(EnumType.STRING)
    @Column(name = "izid_tip")
    private IzidTekme izidTip;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StatusTekme status = StatusTekme.CAKA;

    @Column(name = "miza")
    private Integer miza;

    @Column(name = "predviden_zacetek")
    private LocalDateTime predvidenZacetek;

    /* Optimisticno zaklepanje - dva socasna vnosa rezultata se ne moreta
       tiho prepisati; drugi dobi napako in mora poskusiti znova. */
    @Version
    @Column(name = "verzija", nullable = false)
    private long verzija;

    public Long getId() { return id; }

    public Dogodek getDogodek() { return dogodek; }
    public void setDogodek(Dogodek dogodek) { this.dogodek = dogodek; }

    public FazaTekme getFaza() { return faza; }
    public void setFaza(FazaTekme faza) { this.faza = faza; }

    public Long getIdSkupina() { return idSkupina; }
    public void setIdSkupina(Long idSkupina) { this.idSkupina = idSkupina; }

    public int getKolo() { return kolo; }
    public void setKolo(int kolo) { this.kolo = kolo; }

    public int getPozicija() { return pozicija; }
    public void setPozicija(int pozicija) { this.pozicija = pozicija; }

    public Prijava getPrijava1() { return prijava1; }
    public void setPrijava1(Prijava prijava1) { this.prijava1 = prijava1; }

    public Prijava getPrijava2() { return prijava2; }
    public void setPrijava2(Prijava prijava2) { this.prijava2 = prijava2; }

    public Long getIdIzvorTekma1() { return idIzvorTekma1; }
    public void setIdIzvorTekma1(Long idIzvorTekma1) { this.idIzvorTekma1 = idIzvorTekma1; }

    public VlogaIzvora getVlogaIzvora1() { return vlogaIzvora1; }
    public void setVlogaIzvora1(VlogaIzvora vlogaIzvora1) { this.vlogaIzvora1 = vlogaIzvora1; }

    public Long getIdIzvorTekma2() { return idIzvorTekma2; }
    public void setIdIzvorTekma2(Long idIzvorTekma2) { this.idIzvorTekma2 = idIzvorTekma2; }

    public VlogaIzvora getVlogaIzvora2() { return vlogaIzvora2; }
    public void setVlogaIzvora2(VlogaIzvora vlogaIzvora2) { this.vlogaIzvora2 = vlogaIzvora2; }

    public int getSteviloNizov() { return steviloNizov; }
    public void setSteviloNizov(int steviloNizov) { this.steviloNizov = steviloNizov; }

    /* Stevilo dobljenih nizov, potrebnih za zmago (npr. 3 pri "najboljsi od 5"). */
    public int nizovZaZmago() { return steviloNizov / 2 + 1; }

    public int getDobljeniNizi1() { return dobljeniNizi1; }
    public void setDobljeniNizi1(int dobljeniNizi1) { this.dobljeniNizi1 = dobljeniNizi1; }

    public int getDobljeniNizi2() { return dobljeniNizi2; }
    public void setDobljeniNizi2(int dobljeniNizi2) { this.dobljeniNizi2 = dobljeniNizi2; }

    public Prijava getZmagovalec() { return zmagovalec; }
    public void setZmagovalec(Prijava zmagovalec) { this.zmagovalec = zmagovalec; }

    /* Porazenec tekme - potreben za tekmo za 3. mesto. */
    public Prijava porazenec() {
        if (zmagovalec == null) return null;
        return zmagovalec.getId().equals(prijava1 != null ? prijava1.getId() : null) ? prijava2 : prijava1;
    }

    public IzidTekme getIzidTip() { return izidTip; }
    public void setIzidTip(IzidTekme izidTip) { this.izidTip = izidTip; }

    public StatusTekme getStatus() { return status; }
    public void setStatus(StatusTekme status) { this.status = status; }

    public Integer getMiza() { return miza; }
    public void setMiza(Integer miza) { this.miza = miza; }

    public LocalDateTime getPredvidenZacetek() { return predvidenZacetek; }
    public void setPredvidenZacetek(LocalDateTime predvidenZacetek) { this.predvidenZacetek = predvidenZacetek; }
}
