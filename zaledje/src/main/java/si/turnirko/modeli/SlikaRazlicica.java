/* Bajti ene razlicice slike (V54). Bere jih samo pot, ki sliko postreze
   (SlikeStoritev.vsebina); nobena druga entiteta nanjo ne kaze, zato se nikoli
   ne nalozijo mimogrede. */
package si.turnirko.modeli;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "slika_razlicica")
public class SlikaRazlicica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /* Stevilka in ne entiteta: razlicica se bere po zgostitvi slike, opis slike
       pa zraven ni potreben. */
    @Column(name = "id_slika", nullable = false, updatable = false)
    private Long idSlika;

    @Enumerated(EnumType.STRING)
    @Column(name = "velikost", nullable = false, updatable = false)
    private VelikostSlike velikost;

    /* "image/png" ali "image/jpeg" - doloci jo streznik ob zapisu. */
    @Column(name = "vrsta", nullable = false, updatable = false)
    private String vrsta;

    @Column(name = "sirina", nullable = false, updatable = false)
    private int sirina;

    @Column(name = "visina", nullable = false, updatable = false)
    private int visina;

    @Column(name = "vsebina", nullable = false, updatable = false)
    private byte[] vsebina;

    protected SlikaRazlicica() {
    }

    public SlikaRazlicica(Long idSlika, VelikostSlike velikost, String vrsta,
                          int sirina, int visina, byte[] vsebina) {
        this.idSlika = idSlika;
        this.velikost = velikost;
        this.vrsta = vrsta;
        this.sirina = sirina;
        this.visina = visina;
        this.vsebina = vsebina;
    }

    public Long getId() { return id; }
    public Long getIdSlika() { return idSlika; }
    public VelikostSlike getVelikost() { return velikost; }
    public String getVrsta() { return vrsta; }
    public int getSirina() { return sirina; }
    public int getVisina() { return visina; }
    public byte[] getVsebina() { return vsebina; }
}
