/* Kraj s postno stevilko - sifrant. */
package si.turnirko.modeli;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "kraj")
public class Kraj {

    /* Postna stevilka je naravni kljuc - ne generira se samodejno. */
    @Id
    @Column(name = "postna_st")
    private Integer postnaSt;

    @Column(name = "ime", nullable = false)
    private String ime;

    protected Kraj() {}

    public Kraj(Integer postnaSt, String ime) {
        this.postnaSt = postnaSt;
        this.ime = ime;
    }

    public Integer getPostnaSt() { return postnaSt; }
    public String getIme() { return ime; }
    public void setIme(String ime) { this.ime = ime; }
}
