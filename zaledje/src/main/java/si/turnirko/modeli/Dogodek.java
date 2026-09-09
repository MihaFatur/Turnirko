/* Dogodek - posamezno tekmovanje na turnirju,
   npr. "Clani posamicno" ali "Kadetinje posamicno". */
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
@Table(name = "dogodek")
public class Dogodek {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_turnir", nullable = false)
    private Turnir turnir;

    @Column(name = "ime", nullable = false)
    private String ime;

    @Enumerated(EnumType.STRING)
    @Column(name = "disciplina", nullable = false)
    private Disciplina disciplina = Disciplina.POSAMICNO;

    @Enumerated(EnumType.STRING)
    @Column(name = "spol_kategorija", nullable = false)
    private SpolKategorija spolKategorija;

    /* Prosto besedilo, npr. 'U15', 'CLANI', 'VETERANI 40+'. */
    @Column(name = "starostna_kategorija")
    private String starostnaKategorija;

    @Enumerated(EnumType.STRING)
    @Column(name = "sistem_tekmovanja", nullable = false)
    private SistemTekmovanja sistemTekmovanja = SistemTekmovanja.IZLOCILNI;

    /* "Najboljsi od N nizov" - privzeto za tekme tega dogodka;
       posamezna tekma lahko vrednost povozi (npr. finale na 7). */
    @Column(name = "privzeto_stevilo_nizov", nullable = false)
    private int privzetoSteviloNizov = 5;

    /* Nastavitvi skupinskega dela - uporabljata se samo pri sistemu SKUPINE.
       Njun zmnozek je meja izbora: toliko najboljsih prijavljenih igra.
       Pri drugih sistemih ostaneta prazni. */
    @Column(name = "stevilo_skupin")
    private Integer steviloSkupin;

    @Column(name = "velikost_skupine")
    private Integer velikostSkupine;

    @Column(name = "prijavnina")
    private Double prijavnina;

    @Column(name = "rok_prijave")
    private LocalDate rokPrijave;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StatusTekmovanja status = StatusTekmovanja.PRIPRAVA;

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

    public Turnir getTurnir() { return turnir; }
    public void setTurnir(Turnir turnir) { this.turnir = turnir; }

    public String getIme() { return ime; }
    public void setIme(String ime) { this.ime = ime; }

    public Disciplina getDisciplina() { return disciplina; }
    public void setDisciplina(Disciplina disciplina) { this.disciplina = disciplina; }

    /* Ali je tekmovalna enota par (in torej prijava nosi dva igralca). */
    public boolean jeDvojice() { return disciplina.jeDvojice(); }

    public SpolKategorija getSpolKategorija() { return spolKategorija; }
    public void setSpolKategorija(SpolKategorija spolKategorija) { this.spolKategorija = spolKategorija; }

    public String getStarostnaKategorija() { return starostnaKategorija; }
    public void setStarostnaKategorija(String starostnaKategorija) { this.starostnaKategorija = starostnaKategorija; }

    public SistemTekmovanja getSistemTekmovanja() { return sistemTekmovanja; }
    public void setSistemTekmovanja(SistemTekmovanja sistemTekmovanja) { this.sistemTekmovanja = sistemTekmovanja; }

    public int getPrivzetoSteviloNizov() { return privzetoSteviloNizov; }
    public void setPrivzetoSteviloNizov(int privzetoSteviloNizov) { this.privzetoSteviloNizov = privzetoSteviloNizov; }

    public Integer getSteviloSkupin() { return steviloSkupin; }
    public void setSteviloSkupin(Integer steviloSkupin) { this.steviloSkupin = steviloSkupin; }

    public Integer getVelikostSkupine() { return velikostSkupine; }
    public void setVelikostSkupine(Integer velikostSkupine) { this.velikostSkupine = velikostSkupine; }

    /* Koliko najboljsih prijavljenih pride v izbor (sistem SKUPINE).
       0 pomeni, da dogodek ni skupinski oz. nastavitvi se nista dolocena. */
    public int mejaIzbora() {
        if (sistemTekmovanja != SistemTekmovanja.SKUPINE
                || steviloSkupin == null || velikostSkupine == null) {
            return 0;
        }
        return steviloSkupin * velikostSkupine;
    }

    public Double getPrijavnina() { return prijavnina; }
    public void setPrijavnina(Double prijavnina) { this.prijavnina = prijavnina; }

    public LocalDate getRokPrijave() { return rokPrijave; }
    public void setRokPrijave(LocalDate rokPrijave) { this.rokPrijave = rokPrijave; }

    public StatusTekmovanja getStatus() { return status; }
    public void setStatus(StatusTekmovanja status) { this.status = status; }

    public LocalDateTime getUstvarjenOb() { return ustvarjenOb; }
}
