/* Kateri zapis v Turnirku je kateri zapis pri zunanjem viru (V27).

   Brez povezav je vsak ponovni uvoz ustvaril dvojnike - stari uvoznik jih je
   drzal samo v pomnilniku in je zato zahteval prazno bazo. Povezava je "vec
   zunanjih na enega lokalnega": Stupa isto osebo vcasih vodi pod dvema
   user_role_id, oba pa morata kazati na istega igralca. */
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
@Table(name = "zunanja_povezava")
public class ZunanjaPovezava {

    public enum Vrsta {
        IGRALEC, KLUB, TURNIR, DOGODEK, PRIJAVA, EKIPA, SKUPINA, TEKMA, LIGA, SRECANJE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "vir", nullable = false)
    private VirTekmovanja vir;

    @Enumerated(EnumType.STRING)
    @Column(name = "vrsta", nullable = false)
    private Vrsta vrsta;

    /* Identifikator pri viru kot besedilo - vir ga lahko sestavi. */
    @Column(name = "zunanji_id", nullable = false)
    private String zunanjiId;

    @Column(name = "id_lokalni", nullable = false)
    private Long idLokalni;

    protected ZunanjaPovezava() {}

    public ZunanjaPovezava(VirTekmovanja vir, Vrsta vrsta, String zunanjiId, Long idLokalni) {
        this.vir = vir;
        this.vrsta = vrsta;
        this.zunanjiId = zunanjiId;
        this.idLokalni = idLokalni;
    }

    public Long getId() { return id; }
    public VirTekmovanja getVir() { return vir; }
    public Vrsta getVrsta() { return vrsta; }
    public String getZunanjiId() { return zunanjiId; }
    public Long getIdLokalni() { return idLokalni; }
    public void setIdLokalni(Long idLokalni) { this.idLokalni = idLokalni; }
}
