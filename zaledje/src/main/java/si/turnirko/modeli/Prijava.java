/* Prijava na dogodek - TEKMOVALNA ENOTA.
   Tekme se sklicujejo na PRIJAVE (ne neposredno na igralce), ker prijava
   hrani posnetek kluba in ratinga ob zrebu, predvsem pa zato, ker pri
   disciplini DVOJICE predstavlja PAR, pri disciplini EKIPNO pa EKIPO (V28):
   tabele tekem zato ni bilo treba spreminjati in zreb, mreza, skupine in
   koncna mesta tecejo po isti kodi.

   Pri dvojicah se igralci prijavijo posamicno (vsak svoja vrstica), pare pa
   pred zrebom sestavi organizator: vrstica prvega igralca dobi soigralca,
   vrstica drugega izgine. Prijava brez soigralca je torej na dogodku dvojic
   "prijavljen igralec brez para" in v zreb ne gre.

   Ekipna prijava igralca NIMA - kdo sme igrati, pove kader ekipe, kdo je
   igral, pa srecanje ekipne tekme. Zato igralci() pri njej vrne prazen
   seznam in vsaka koda, ki iz prijave bere igralca, mora ekipno prijavo
   izlociti (statistika posameznika jo izloci ze po disciplini dogodka). */
package si.turnirko.modeli;

import java.time.LocalDateTime;
import java.util.List;

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

    /* Igralec posamicne prijave oz. prvi igralec para; prazen pri ekipi. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_igralec")
    private Igralec igralec;

    /* Ekipa (samo pri disciplini EKIPNO); prazna pri igralcu in paru. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_ekipa")
    private Ekipa ekipa;

    /* Drugi igralec para (samo pri disciplini DVOJICE). Prazen pomeni
       posamicno prijavo oz. igralca, ki soigralca se nima. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_igralec_2")
    private Igralec igralec2;

    /* Posnetek kluba OB PRIJAVI - zgodovina ostane pravilna
       tudi ce igralec kasneje prestopi. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_klub_ob_prijavi")
    private Klub klubObPrijavi;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_klub_ob_prijavi_2")
    private Klub klubObPrijavi2;

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

    @Column(name = "rating_ob_zrebu_2")
    private Integer ratingObZrebu2;

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

    /* Ekipna prijava: posnetek kluba je klub ekipe (prosta ekipa ga nima). */
    public Prijava(Dogodek dogodek, Ekipa ekipa) {
        this.dogodek = dogodek;
        this.ekipa = ekipa;
        this.klubObPrijavi = ekipa.getKlub();
    }

    public Long getId() { return id; }
    public Dogodek getDogodek() { return dogodek; }
    public Igralec getIgralec() { return igralec; }
    public Ekipa getEkipa() { return ekipa; }

    /* Ali prijava nosi ekipo (ekipni dogodek) in ne igralca. */
    public boolean jeEkipa() { return ekipa != null; }
    public Klub getKlubObPrijavi() { return klubObPrijavi; }
    /* Ob ponovni prijavi po odjavi se posnetek kluba osvezi (moznost prestopa). */
    public void setKlubObPrijavi(Klub klubObPrijavi) { this.klubObPrijavi = klubObPrijavi; }

    public Igralec getIgralec2() { return igralec2; }
    public Klub getKlubObPrijavi2() { return klubObPrijavi2; }
    /* Posnetek kluba soigralca iz vira (uvoz): klub ob tistem nastopu in ne
       danasnji klub igralca. */
    public void setKlubObPrijavi2(Klub klubObPrijavi2) { this.klubObPrijavi2 = klubObPrijavi2; }

    /* Poveze prijavo v par: drugi igralec s svojim posnetkom kluba.
       null razdruzi par in prijava spet velja za enega samega igralca. */
    public void nastaviSoigralca(Igralec soigralec) {
        this.igralec2 = soigralec;
        this.klubObPrijavi2 = soigralec != null ? soigralec.getKlub() : null;
        if (soigralec == null) {
            this.ratingObZrebu2 = null;
        }
    }

    /* Ali je prijava sestavljen par (in ne samo prijavljen igralec). */
    public boolean jePar() { return igralec2 != null; }

    /* Igralci te prijave: eden ali dva; pri ekipi nobeden (igralce nosi kader). */
    public List<Igralec> igralci() {
        if (ekipa != null) {
            return List.of();
        }
        return igralec2 == null ? List.of(igralec) : List.of(igralec, igralec2);
    }

    public boolean vsebujeIgralca(Long idIgralca) {
        if (ekipa != null) {
            return false;
        }
        return igralec.getId().equals(idIgralca)
                || (igralec2 != null && igralec2.getId().equals(idIgralca));
    }

    /* Ime tekmovalne enote za izpise: "Ana Novak", "Ana Novak / Eva Zajc"
       oz. ime ekipe. */
    public String prikazanoIme() {
        if (ekipa != null) {
            return ekipa.prikazanoIme();
        }
        return igralec2 == null ? igralec.polnoIme()
                : igralec.polnoIme() + " / " + igralec2.polnoIme();
    }

    /* Kratek izpis (npr. za vrstico "zadnji izid"): "Novak", "Novak/Zajc"
       oz. ime ekipe - ekipa krajse oblike imena nima. */
    public String prikazaniPriimek() {
        if (ekipa != null) {
            return ekipa.prikazanoIme();
        }
        return igralec2 == null ? igralec.getPriimek()
                : igralec.getPriimek() + "/" + igralec2.getPriimek();
    }

    public StatusPrijave getStatus() { return status; }
    public void setStatus(StatusPrijave status) { this.status = status; }

    public Integer getStNosilca() { return stNosilca; }
    public void setStNosilca(Integer stNosilca) { this.stNosilca = stNosilca; }

    public Integer getRatingObZrebu() { return ratingObZrebu; }
    public void setRatingObZrebu(Integer ratingObZrebu) { this.ratingObZrebu = ratingObZrebu; }

    public Integer getRatingObZrebu2() { return ratingObZrebu2; }
    public void setRatingObZrebu2(Integer ratingObZrebu2) { this.ratingObZrebu2 = ratingObZrebu2; }

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
