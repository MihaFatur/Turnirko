/* Ekipa je nastop kluba v ligi. En klub ima lahko vec ekip (Savinja 1, 2),
   locenih po zaporedni stevilki. Igralce, ki smejo nastopati, hrani kader. */
package si.turnirko.modeli;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "ekipa")
public class Ekipa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_liga", nullable = false)
    private Liga liga;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_klub", nullable = false)
    private Klub klub;

    @Column(name = "zaporedna", nullable = false)
    private int zaporedna = 1;

    /* Prikazano ime; ce null, se sestavi iz kluba in zaporedne. */
    @Column(name = "ime")
    private String ime;

    @Version
    @Column(name = "verzija", nullable = false)
    private long verzija;

    protected Ekipa() {}

    public Ekipa(Liga liga, Klub klub, int zaporedna, String ime) {
        this.liga = liga;
        this.klub = klub;
        this.zaporedna = zaporedna;
        this.ime = ime;
    }

    public Long getId() { return id; }

    public Liga getLiga() { return liga; }
    public Klub getKlub() { return klub; }

    public int getZaporedna() { return zaporedna; }
    public void setZaporedna(int zaporedna) { this.zaporedna = zaporedna; }

    public String getIme() { return ime; }
    public void setIme(String ime) { this.ime = ime; }

    /* Ime za prikaz: lastno ime ali "Klub N" (N samo, ce ima klub vec ekip). */
    public String prikazanoIme() {
        if (ime != null && !ime.isBlank()) return ime;
        return klub.getIme() + " " + zaporedna;
    }
}
