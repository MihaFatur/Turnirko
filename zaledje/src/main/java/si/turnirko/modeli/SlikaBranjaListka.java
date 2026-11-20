/* Fotografija listka enega branja (V68), JPEG brez metapodatkov. Bere jo samo
   pot, ki sliko postreze organizatorju, in bralnik; po 30 dneh od turnirja jo
   nocno opravilo izbrise (BranjeListkovStoritev.izbrisiStareSlike). */
package si.turnirko.modeli;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "branje_listka_slika")
public class SlikaBranjaListka {

    @Id
    @Column(name = "id_branje")
    private Long idBranje;

    @Column(name = "vsebina", nullable = false, updatable = false)
    private byte[] vsebina;

    @Column(name = "sirina", nullable = false)
    private int sirina;

    @Column(name = "visina", nullable = false)
    private int visina;

    protected SlikaBranjaListka() {}

    public SlikaBranjaListka(Long idBranje, byte[] vsebina, int sirina, int visina) {
        this.idBranje = idBranje;
        this.vsebina = vsebina;
        this.sirina = sirina;
        this.visina = visina;
    }

    public Long getIdBranje() { return idBranje; }
    public byte[] getVsebina() { return vsebina; }
    public int getSirina() { return sirina; }
    public int getVisina() { return visina; }
}
