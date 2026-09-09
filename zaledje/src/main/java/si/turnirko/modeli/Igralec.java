/* Igralec. Trenutni rating NI atribut igralca - zivi v tabeli rating_stanje,
   ker ima igralec lahko vec ratingov (klubski ELO, kasneje tocke NTZS).
   Igralcev ne brisemo (izgubili bi zgodovino tekem) - le arhiviramo. */
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "igralec")
public class Igralec {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "ime", nullable = false)
    private String ime;

    @Column(name = "priimek", nullable = false)
    private String priimek;

    @Enumerated(EnumType.STRING)
    @Column(name = "spol", nullable = false)
    private Spol spol;

    @Column(name = "datum_rojstva", nullable = false)
    private LocalDate datumRojstva;

    @Column(name = "email")
    private String email;

    @Column(name = "telefonska_st")
    private String telefonskaSt;

    @Enumerated(EnumType.STRING)
    @Column(name = "igralna_roka")
    private IgralnaRoka igralnaRoka;

    /* Registrska stevilka pri NTZS, ce jo igralec ima. */
    @Column(name = "ntzs_licenca")
    private String ntzsLicenca;

    @Column(name = "drzavljanstvo", nullable = false)
    private String drzavljanstvo = "SLO";

    @Column(name = "naslov")
    private String naslov;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "postna_st")
    private Kraj kraj;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_klub")
    private Klub klub;

    /* Mehko brisanje: arhiviran igralec ne sme na nove prijave,
       njegova zgodovina tekem pa ostane. */
    @Column(name = "arhiviran", nullable = false)
    private boolean arhiviran = false;

    @Column(name = "ustvarjen_ob", nullable = false, updatable = false)
    private LocalDateTime ustvarjenOb;

    @Column(name = "posodobljen_ob")
    private LocalDateTime posodobljenOb;

    @PrePersist
    void obShranjevanju() {
        ustvarjenOb = LocalDateTime.now();
    }

    @PreUpdate
    void obPosodobitvi() {
        posodobljenOb = LocalDateTime.now();
    }

    public Long getId() { return id; }

    public String getIme() { return ime; }
    public void setIme(String ime) { this.ime = ime; }

    public String getPriimek() { return priimek; }
    public void setPriimek(String priimek) { this.priimek = priimek; }

    /* Polno ime za izpise, npr. "Janez Novak". Slovensko se oseba imenuje
       ime-priimek in tak je vsak izpis v aplikaciji; obrnjeni vrstni red je
       samo urejevalni kljuc - zanj je abecedno(). */
    public String polnoIme() { return ime + " " + priimek; }

    /* Urejevalni kljuc "Novak Janez": seznami igralcev tecejo po priimku,
       izpisujejo pa polnoIme(). Ta niz ne sme nikoli v vmesnik. */
    public String abecedno() { return priimek + " " + ime; }

    public Spol getSpol() { return spol; }
    public void setSpol(Spol spol) { this.spol = spol; }

    public LocalDate getDatumRojstva() { return datumRojstva; }
    public void setDatumRojstva(LocalDate datumRojstva) { this.datumRojstva = datumRojstva; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getTelefonskaSt() { return telefonskaSt; }
    public void setTelefonskaSt(String telefonskaSt) { this.telefonskaSt = telefonskaSt; }

    public IgralnaRoka getIgralnaRoka() { return igralnaRoka; }
    public void setIgralnaRoka(IgralnaRoka igralnaRoka) { this.igralnaRoka = igralnaRoka; }

    public String getNtzsLicenca() { return ntzsLicenca; }
    public void setNtzsLicenca(String ntzsLicenca) { this.ntzsLicenca = ntzsLicenca; }

    public String getDrzavljanstvo() { return drzavljanstvo; }
    public void setDrzavljanstvo(String drzavljanstvo) { this.drzavljanstvo = drzavljanstvo; }

    public String getNaslov() { return naslov; }
    public void setNaslov(String naslov) { this.naslov = naslov; }

    public Kraj getKraj() { return kraj; }
    public void setKraj(Kraj kraj) { this.kraj = kraj; }

    public Klub getKlub() { return klub; }
    public void setKlub(Klub klub) { this.klub = klub; }

    public boolean isArhiviran() { return arhiviran; }
    public void setArhiviran(boolean arhiviran) { this.arhiviran = arhiviran; }

    public LocalDateTime getUstvarjenOb() { return ustvarjenOb; }
    public LocalDateTime getPosodobljenOb() { return posodobljenOb; }
}
