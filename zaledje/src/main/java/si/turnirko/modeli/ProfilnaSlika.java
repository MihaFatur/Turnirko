/* Profilna slika igralca (V56) - samo opis, bajti so v ProfilnaSlikaVsebina.

   Pripada računu (Premium je last računa), kaže pa se pri igralcu, s katerim je
   račun povezan. Slika je osebni podatek, ki ga je igralec dal sam: ob izbrisu
   (njegovem ali adminovem) gredo tudi bajti, ob izteku Premium se le skrije. */
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "profilna_slika")
public class ProfilnaSlika {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_uporabnik", nullable = false, updatable = false)
    private Uporabnik uporabnik;

    /* SHA-256 velike različice - javni naslov /api/v1/slike/profil/{zgostitev}. */
    @Column(name = "zgostitev", nullable = false, unique = true, updatable = false)
    private String zgostitev;

    @Enumerated(EnumType.STRING)
    @Column(name = "stanje", nullable = false)
    private StanjeProfilneSlike stanje;

    @Convert(converter = CasKotBesedilo.class)
    @Column(name = "nalozena_ob", nullable = false, updatable = false)
    private LocalDateTime nalozenaOb;

    @Convert(converter = CasKotBesedilo.class)
    @Column(name = "objavljena_ob")
    private LocalDateTime objavljenaOb;

    protected ProfilnaSlika() {
    }

    public ProfilnaSlika(Uporabnik uporabnik, String zgostitev, StanjeProfilneSlike stanje) {
        this.uporabnik = uporabnik;
        this.zgostitev = zgostitev;
        this.stanje = stanje;
        if (stanje == StanjeProfilneSlike.OBJAVLJENA) {
            this.objavljenaOb = LocalDateTime.now();
        }
    }

    @PrePersist
    void obShranjevanju() {
        if (nalozenaOb == null) {
            nalozenaOb = LocalDateTime.now();
        }
    }

    /* Admin sliko, ki je čakala, objavi. */
    public void objavi() {
        this.stanje = StanjeProfilneSlike.OBJAVLJENA;
        this.objavljenaOb = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Uporabnik getUporabnik() { return uporabnik; }
    public String getZgostitev() { return zgostitev; }
    public StanjeProfilneSlike getStanje() { return stanje; }
    public LocalDateTime getNalozenaOb() { return nalozenaOb; }
    public LocalDateTime getObjavljenaOb() { return objavljenaOb; }
}
