/* Trenutno stanje placilnega paketa enega racuna - ena vrstica na uporabnika,
   ki se ob obnovi/preklicu/nadgradnji PREPISE (Stripe je dnevnik dogodkov,
   ta vrstica je samo trenutno stanje - isto razmerje kot rating_stanje do
   rating_zgodovina).

   Racun za placljiv paket (PREMIUM ali kateri koli ORGANIZATOR_*) v tabeli
   uporabnik nastane SELE, ko Stripe webhook potrdi placilo - do takrat
   vrstica tu sploh ne obstaja. Odsotnost vrstice pri IGRALCU pomeni
   BREZPLACNO (privzeto, brez potrebe po zapisu). */
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "narocnina")
public class Narocnina {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_uporabnik", nullable = false, unique = true)
    private Uporabnik uporabnik;

    @Enumerated(EnumType.STRING)
    @Column(name = "paket", nullable = false)
    private Paket paket;

    @Enumerated(EnumType.STRING)
    @Column(name = "ciklus")
    private CiklusPlacila ciklus;

    /* Cena OB SKLENITVI - ostane, tudi ce se cenik pozneje spremeni. */
    @Column(name = "cena_ob_sklenitvi")
    private Double cenaObSklenitvi;

    /* Samo pri PREMIUM: ali je bil racun ob registraciji starejsi od 21 let
       (starostni pas U21 ali mlajsi -> false). Sled, po kateri je bila cena
       izracunana - glej CenikStoritev. */
    @Column(name = "starejsi_od_21")
    private Boolean starejsiOd21;

    @Column(name = "stripe_customer_id")
    private String stripeCustomerId;

    @Column(name = "stripe_narocnina_id", unique = true)
    private String stripeNarocninaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StatusNarocnine status;

    @Convert(converter = CasKotBesedilo.class)
    @Column(name = "zacetek_ob")
    private LocalDateTime zacetekOb;

    /* Konec placanega obdobja (Stripe current_period_end); po preklicu
       narocnina ostane AKTIVNA do tega datuma. */
    @Convert(converter = CasKotBesedilo.class)
    @Column(name = "trenutno_obdobje_do")
    private LocalDateTime trenutnoObdobjeDo;

    @Convert(converter = CasKotBesedilo.class)
    @Column(name = "ustvarjena_ob", nullable = false, updatable = false)
    private LocalDateTime ustvarjenaOb;

    @Convert(converter = CasKotBesedilo.class)
    @Column(name = "posodobljena_ob", nullable = false)
    private LocalDateTime posodobljenaOb;

    @PrePersist
    void obShranjevanju() {
        LocalDateTime zdaj = LocalDateTime.now();
        ustvarjenaOb = zdaj;
        posodobljenaOb = zdaj;
    }

    @PreUpdate
    void obPosodobitvi() {
        posodobljenaOb = LocalDateTime.now();
    }

    protected Narocnina() {}

    public Narocnina(Uporabnik uporabnik, Paket paket) {
        this.uporabnik = uporabnik;
        this.paket = paket;
    }

    public Long getId() { return id; }

    public Uporabnik getUporabnik() { return uporabnik; }
    public void setUporabnik(Uporabnik uporabnik) { this.uporabnik = uporabnik; }

    public Paket getPaket() { return paket; }
    public void setPaket(Paket paket) { this.paket = paket; }

    public CiklusPlacila getCiklus() { return ciklus; }
    public void setCiklus(CiklusPlacila ciklus) { this.ciklus = ciklus; }

    public Double getCenaObSklenitvi() { return cenaObSklenitvi; }
    public void setCenaObSklenitvi(Double cenaObSklenitvi) { this.cenaObSklenitvi = cenaObSklenitvi; }

    public Boolean getStarejsiOd21() { return starejsiOd21; }
    public void setStarejsiOd21(Boolean starejsiOd21) { this.starejsiOd21 = starejsiOd21; }

    public String getStripeCustomerId() { return stripeCustomerId; }
    public void setStripeCustomerId(String stripeCustomerId) { this.stripeCustomerId = stripeCustomerId; }

    public String getStripeNarocninaId() { return stripeNarocninaId; }
    public void setStripeNarocninaId(String stripeNarocninaId) { this.stripeNarocninaId = stripeNarocninaId; }

    public StatusNarocnine getStatus() { return status; }
    public void setStatus(StatusNarocnine status) { this.status = status; }

    public LocalDateTime getZacetekOb() { return zacetekOb; }
    public void setZacetekOb(LocalDateTime zacetekOb) { this.zacetekOb = zacetekOb; }

    public LocalDateTime getTrenutnoObdobjeDo() { return trenutnoObdobjeDo; }
    public void setTrenutnoObdobjeDo(LocalDateTime trenutnoObdobjeDo) { this.trenutnoObdobjeDo = trenutnoObdobjeDo; }

    public LocalDateTime getUstvarjenaOb() { return ustvarjenaOb; }

    public LocalDateTime getPosodobljenaOb() { return posodobljenaOb; }

    /* Ali narocnina zdaj daje dostop do svojih ugodnosti: AKTIVNA vedno,
       PREKLICANA se do konca placanega obdobja (uporabnik je placal, samo
       obnovitev je izklopil). */
    public boolean jeVeljavna(LocalDateTime zdaj) {
        if (status == StatusNarocnine.AKTIVNA) {
            return true;
        }
        return status == StatusNarocnine.PREKLICANA
                && trenutnoObdobjeDo != null && trenutnoObdobjeDo.isAfter(zdaj);
    }
}
