/* Uporabnik sistema: administrator (sodnik/organizator) ali igralec.
   Gostje niso uporabniki - nimajo zapisa in dostopajo brez prijave
   (samo branje). Geslo je shranjeno kot BCrypt zgostitev, nikoli v cistopisu.

   Prijavno ime (uporabnisko_ime) je pri administratorju obicajno ime, pri
   igralcu pa njegova e-posta - en sam enolicen stolpec, da prijava nima
   dveh vzporednih identifikatorjev, ki bi lahko razpadla narazen.

   Racun igralca ob registraciji dobi status CAKA in ni povezan z zapisom v
   sifrantu. Naslov steje sele, ko lastnik vpise kodo s poste
   (email_potrjen_ob); mlajsi od 15 let potrebujejo se soglasje skrbnika
   (email_skrbnika + skrbnik_potrjen_ob). Povezavo z igralcem (in s tem
   dostop do lastne statistike) naredi administrator ali pa nastane
   samodejno, ce se ime, priimek in datum rojstva ujemajo z natanko enim
   igralcem brez racuna (vir_povezave). */
package si.turnirko.modeli;

import java.time.LocalDate;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "uporabnik")
public class Uporabnik {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "uporabnisko_ime", nullable = false, unique = true)
    private String uporabniskoIme;

    /* BCrypt zgostitev gesla (nikoli cistopis). */
    @Column(name = "geslo_hash", nullable = false)
    private String gesloHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "vloga", nullable = false)
    private Vloga vloga = Vloga.ADMIN;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StatusRacuna status = StatusRacuna.POTRJEN;

    /* Igralec, ki mu racun pripada. Dokler ga administrator ne poveze, je
       prazen in racun ne vidi nobene zasebne statistike. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_igralec")
    private Igralec igralec;

    /* Klub, ki mu uporabnik pripada. Uporablja se pri organizatorju: turnirje
       in lige, ki jih ustvari, sme soupravljati vsak organizator istega kluba.
       Neobvezen - organizator je lahko brez kluba (takrat upravlja samo svoje).
       Doloci ga administrator ob potrditvi racuna. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_klub")
    private Klub klub;

    /* Kar je oseba navedla ob registraciji - podlaga administratorju, da
       racun poveze s pravim igralcem, in samodejni povezavi. Po potrditvi
       ostane kot sled. Datum rojstva je osebni podatek: javno nikoli. */
    @Column(name = "prijavljeno_ime")
    private String prijavljenoIme;

    @Column(name = "prijavljeni_priimek")
    private String prijavljeniPriimek;

    @Column(name = "prijavljeni_datum_rojstva")
    private LocalDate prijavljeniDatumRojstva;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_klub_zelja")
    private Klub klubZelja;

    /* Kdaj je lastnik naslova vpisal kodo s poste; NULL = naslov se ni potrjen.
       Casi racuna so besedilo s pretvornikom (CasKotBesedilo): gonilnik bi
       sicer uro zavrgel, nocno ciscenje pa steje ure (48 h), ne dni. */
    @Convert(converter = CasKotBesedilo.class)
    @Column(name = "email_potrjen_ob")
    private LocalDateTime emailPotrjenOb;

    /* Naslov starsa oz. skrbnika (samo pri mlajsih od 15 let ob registraciji)
       in kdaj je skrbnikova koda bila vpisana. */
    @Column(name = "email_skrbnika")
    private String emailSkrbnika;

    @Convert(converter = CasKotBesedilo.class)
    @Column(name = "skrbnik_potrjen_ob")
    private LocalDateTime skrbnikPotrjenOb;

    /* Kdo je racun povezal z igralcem in kdaj; prazno, dokler povezave ni. */
    @Enumerated(EnumType.STRING)
    @Column(name = "vir_povezave")
    private VirPovezave virPovezave;

    @Convert(converter = CasKotBesedilo.class)
    @Column(name = "povezan_ob")
    private LocalDateTime povezanOb;

    @Column(name = "aktiven", nullable = false)
    private boolean aktiven = true;

    /* Stari zapisi so brez ure ("2026-08-16"); pretvornik jih bere kot polnoc,
       ISO besedilo pa se leksikalno primerja pravilno tudi z njimi. */
    @Convert(converter = CasKotBesedilo.class)
    @Column(name = "ustvarjen_ob", nullable = false, updatable = false)
    private LocalDateTime ustvarjenOb;

    @PrePersist
    void obShranjevanju() {
        ustvarjenOb = LocalDateTime.now();
    }

    protected Uporabnik() {}

    public Uporabnik(String uporabniskoIme, String gesloHash, Vloga vloga) {
        this.uporabniskoIme = uporabniskoIme;
        this.gesloHash = gesloHash;
        this.vloga = vloga;
    }

    /* Ali racun sme videti zasebno statistiko svojega igralca. */
    public boolean jePotrjenIgralec() {
        return vloga == Vloga.IGRALEC && status == StatusRacuna.POTRJEN && igralec != null;
    }

    /* Ali je racun potrjen organizator (sme ustvarjati in upravljati svoja
       tekmovanja). Za razliko od igralca ne potrebuje povezave na sifrant. */
    public boolean jePotrjenOrganizator() {
        return vloga == Vloga.ORGANIZATOR && status == StatusRacuna.POTRJEN;
    }

    /* Ali je lastnik naslova vpisal kodo s poste. */
    public boolean jeEmailPotrjen() {
        return emailPotrjenOb != null;
    }

    /* Ali racun se caka na skrbnikovo kodo (samo mlajsi od 15 let). */
    public boolean cakaSkrbnika() {
        return emailSkrbnika != null && skrbnikPotrjenOb == null;
    }

    public Long getId() { return id; }

    public String getUporabniskoIme() { return uporabniskoIme; }
    public void setUporabniskoIme(String uporabniskoIme) { this.uporabniskoIme = uporabniskoIme; }

    public String getGesloHash() { return gesloHash; }
    public void setGesloHash(String gesloHash) { this.gesloHash = gesloHash; }

    public Vloga getVloga() { return vloga; }
    public void setVloga(Vloga vloga) { this.vloga = vloga; }

    public StatusRacuna getStatus() { return status; }
    public void setStatus(StatusRacuna status) { this.status = status; }

    public Igralec getIgralec() { return igralec; }
    public void setIgralec(Igralec igralec) { this.igralec = igralec; }

    public Klub getKlub() { return klub; }
    public void setKlub(Klub klub) { this.klub = klub; }

    public String getPrijavljenoIme() { return prijavljenoIme; }
    public void setPrijavljenoIme(String prijavljenoIme) { this.prijavljenoIme = prijavljenoIme; }

    public String getPrijavljeniPriimek() { return prijavljeniPriimek; }
    public void setPrijavljeniPriimek(String prijavljeniPriimek) { this.prijavljeniPriimek = prijavljeniPriimek; }

    public LocalDate getPrijavljeniDatumRojstva() { return prijavljeniDatumRojstva; }
    public void setPrijavljeniDatumRojstva(LocalDate datum) { this.prijavljeniDatumRojstva = datum; }

    public Klub getKlubZelja() { return klubZelja; }
    public void setKlubZelja(Klub klubZelja) { this.klubZelja = klubZelja; }

    public LocalDateTime getEmailPotrjenOb() { return emailPotrjenOb; }
    public void setEmailPotrjenOb(LocalDateTime emailPotrjenOb) { this.emailPotrjenOb = emailPotrjenOb; }

    public String getEmailSkrbnika() { return emailSkrbnika; }
    public void setEmailSkrbnika(String emailSkrbnika) { this.emailSkrbnika = emailSkrbnika; }

    public LocalDateTime getSkrbnikPotrjenOb() { return skrbnikPotrjenOb; }
    public void setSkrbnikPotrjenOb(LocalDateTime skrbnikPotrjenOb) { this.skrbnikPotrjenOb = skrbnikPotrjenOb; }

    public VirPovezave getVirPovezave() { return virPovezave; }
    public void setVirPovezave(VirPovezave virPovezave) { this.virPovezave = virPovezave; }

    public LocalDateTime getPovezanOb() { return povezanOb; }
    public void setPovezanOb(LocalDateTime povezanOb) { this.povezanOb = povezanOb; }

    public boolean isAktiven() { return aktiven; }
    public void setAktiven(boolean aktiven) { this.aktiven = aktiven; }

    public LocalDateTime getUstvarjenOb() { return ustvarjenOb; }
}
