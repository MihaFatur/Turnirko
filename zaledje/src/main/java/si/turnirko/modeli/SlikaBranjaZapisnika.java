/* Fotografija ligaskega zapisnika (V79), JPEG brez metapodatkov, in poravnan list
   v sivinah za izreze ob poljih pregleda. Po 30 dneh jo nocno opravilo izbrise
   (BranjeZapisnikovStoritev.izbrisiStareSlike). */
package si.turnirko.modeli;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "branje_zapisnika_slika")
public class SlikaBranjaZapisnika {

    @Id
    @Column(name = "id_branje")
    private Long idBranje;

    @Column(name = "vsebina", nullable = false, updatable = false)
    private byte[] vsebina;

    @Column(name = "sirina", nullable = false)
    private int sirina;

    @Column(name = "visina", nullable = false)
    private int visina;

    @Column(name = "poravnana")
    private byte[] poravnana;

    protected SlikaBranjaZapisnika() {}

    public SlikaBranjaZapisnika(Long idBranje, byte[] vsebina, int sirina, int visina) {
        this.idBranje = idBranje;
        this.vsebina = vsebina;
        this.sirina = sirina;
        this.visina = visina;
    }

    public Long getIdBranje() { return idBranje; }
    public byte[] getVsebina() { return vsebina; }
    public byte[] getPoravnana() { return poravnana; }
    public void setPoravnana(byte[] poravnana) { this.poravnana = poravnana; }
}
