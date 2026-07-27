/* Uporabnik sistema: administrator (sodnik/organizator) ali igralec.
   Gostje niso uporabniki - nimajo zapisa in dostopajo brez prijave
   (samo branje). Geslo je shranjeno kot BCrypt zgostitev, nikoli v cistopisu.

   Prijavno ime (uporabnisko_ime) je pri administratorju obicajno ime, pri
   igralcu pa njegova e-posta - en sam enolicen stolpec, da prijava nima
   dveh vzporednih identifikatorjev, ki bi lahko razpadla narazen.

   Racun igralca ob registraciji dobi status CAKA in ni povezan z zapisom v
   sifrantu; povezavo (in s tem dostop do lastne statistike) mu dodeli
   administrator ob potrditvi. */
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

    /* Kar je oseba navedla ob registraciji - podlaga administratorju, da
       racun poveze s pravim igralcem. Po potrditvi ostane kot sled. */
    @Column(name = "prijavljeno_ime")
    private String prijavljenoIme;

    @Column(name = "prijavljeni_priimek")
    private String prijavljeniPriimek;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_klub_zelja")
    private Klub klubZelja;

    @Column(name = "aktiven", nullable = false)
    private boolean aktiven = true;

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

    public String getPrijavljenoIme() { return prijavljenoIme; }
    public void setPrijavljenoIme(String prijavljenoIme) { this.prijavljenoIme = prijavljenoIme; }

    public String getPrijavljeniPriimek() { return prijavljeniPriimek; }
    public void setPrijavljeniPriimek(String prijavljeniPriimek) { this.prijavljeniPriimek = prijavljeniPriimek; }

    public Klub getKlubZelja() { return klubZelja; }
    public void setKlubZelja(Klub klubZelja) { this.klubZelja = klubZelja; }

    public boolean isAktiven() { return aktiven; }
    public void setAktiven(boolean aktiven) { this.aktiven = aktiven; }

    public LocalDateTime getUstvarjenOb() { return ustvarjenOb; }
}
