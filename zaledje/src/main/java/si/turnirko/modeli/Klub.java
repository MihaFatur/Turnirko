/* Namiznoteniski klub - sifrant. */
package si.turnirko.modeli;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "klub")
public class Klub {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "ime", nullable = false)
    private String ime;

    /* Kratko ime za izpise, npr. "NTK SAV" - neobvezno. */
    @Column(name = "kratica")
    private String kratica;

    protected Klub() {}

    public Klub(String ime, String kratica) {
        this.ime = ime;
        this.kratica = kratica;
    }

    public Long getId() { return id; }
    public String getIme() { return ime; }
    public void setIme(String ime) { this.ime = ime; }
    public String getKratica() { return kratica; }
    public void setKratica(String kratica) { this.kratica = kratica; }
}
