/* Listek zlatega nabora (V69): poravnan listek po potrditvi, na katerem so
   ostali samo piksli celic s tockami (imena, glava in noga so beli), in potrjen
   izid v stolpcih listka. Na naboru se meri bralnik (MeritevNabora). Brez imen,
   zato brez roka hrambe; najvec ZlatiNabor.NAJVEC listkov, nakljucen vzorec. */
package si.turnirko.modeli;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "zlati_listek")
public class ZlatiListek {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_branje")
    private Long idBranje;

    /* PNG v sivinah, GeometrijaListka.PX_NA_MM px/mm. */
    @Column(name = "slika", nullable = false, updatable = false)
    private byte[] slika;

    /* JSON ZlatiNabor.Celice: lega celic v px slike. */
    @Column(name = "celice", nullable = false, updatable = false)
    private String celice;

    /* JSON ZlatiNabor.Resnica: potrjene tocke v stolpcih listka. */
    @Column(name = "resnica", nullable = false)
    private String resnica;

    @Column(name = "stevilo_nizov", nullable = false)
    private short steviloNizov;

    @Column(name = "model")
    private String model;

    /* Nakljucen kljuc vzorca: ostanejo listki z najmanjsimi kljuci. */
    @Column(name = "kljuc", nullable = false, updatable = false)
    private double kljuc;

    @Column(name = "ustvarjeno_ob", nullable = false, updatable = false)
    private LocalDateTime ustvarjenoOb;

    protected ZlatiListek() {}

    public ZlatiListek(Long idBranje, byte[] slika, String celice, String resnica, int steviloNizov,
                       String model, double kljuc, LocalDateTime ustvarjenoOb) {
        this.idBranje = idBranje;
        this.slika = slika;
        this.celice = celice;
        this.resnica = resnica;
        this.steviloNizov = (short) steviloNizov;
        this.model = model;
        this.kljuc = kljuc;
        this.ustvarjenoOb = ustvarjenoOb;
    }

    public Long getId() { return id; }
    public Long getIdBranje() { return idBranje; }
    public byte[] getSlika() { return slika; }
    public String getCelice() { return celice; }
    public String getResnica() { return resnica; }
    public int getSteviloNizov() { return steviloNizov; }
    public String getModel() { return model; }
    public double getKljuc() { return kljuc; }
    public LocalDateTime getUstvarjenoOb() { return ustvarjenoOb; }
}
