/* Skupina v skupinskem delu tekmovanja (sistem SKUPINE_IZLOCILNI).
   Znotraj skupine igra vsak z vsakim; najboljsi napredujejo v izlocilni del. */
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

@Entity
@Table(name = "skupina")
public class Skupina {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_dogodek", nullable = false)
    private Dogodek dogodek;

    /* Oznaka skupine: 'A', 'B', 'C'... */
    @Column(name = "oznaka", nullable = false)
    private String oznaka;

    protected Skupina() {}

    public Skupina(Dogodek dogodek, String oznaka) {
        this.dogodek = dogodek;
        this.oznaka = oznaka;
    }

    public Long getId() { return id; }

    public Dogodek getDogodek() { return dogodek; }

    public String getOznaka() { return oznaka; }
    public void setOznaka(String oznaka) { this.oznaka = oznaka; }
}
