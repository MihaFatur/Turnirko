/* Bajti profilne slike (V56): VELIKA (480 x 480) za glavo profila, MALA
   (160 x 160) za 1 na 1 in stopničke. Obe JPEG. Bere jih samo pot, ki sliko
   postreže. */
package si.turnirko.modeli;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "profilna_slika_vsebina")
public class ProfilnaSlikaVsebina {

    public static final String VELIKA = "VELIKA";
    public static final String MALA = "MALA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "id_profilna_slika", nullable = false, updatable = false)
    private Long idProfilnaSlika;

    @Column(name = "velikost", nullable = false, updatable = false)
    private String velikost;

    @Column(name = "vsebina", nullable = false, updatable = false)
    private byte[] vsebina;

    protected ProfilnaSlikaVsebina() {
    }

    public ProfilnaSlikaVsebina(Long idProfilnaSlika, String velikost, byte[] vsebina) {
        this.idProfilnaSlika = idProfilnaSlika;
        this.velikost = velikost;
        this.vsebina = vsebina;
    }

    public Long getId() { return id; }
    public Long getIdProfilnaSlika() { return idProfilnaSlika; }
    public String getVelikost() { return velikost; }
    public byte[] getVsebina() { return vsebina; }
}
