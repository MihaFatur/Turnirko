/* Razlicica mreze stevk iz nocnega ucenja (V68). Sprejeta je samo, ce na
   preizkusnih stevkah listkov bere bolje od prejsnje; v uporabi je zadnja sprejeta,
   brez nje vgrajena (resources/branje/stevke.bin). */
package si.turnirko.modeli;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "mreza_stevk")
public class MrezaStevkZapis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "utezi", nullable = false, updatable = false)
    private byte[] utezi;

    @Column(name = "vzorcev", nullable = false)
    private int vzorcev;

    @Column(name = "natancnost_prej", nullable = false)
    private float natancnostPrej;

    @Column(name = "natancnost", nullable = false)
    private float natancnost;

    @Column(name = "sprejeta", nullable = false)
    private boolean sprejeta;

    @Column(name = "ustvarjeno_ob", nullable = false)
    private LocalDateTime ustvarjenoOb;

    protected MrezaStevkZapis() {}

    public MrezaStevkZapis(byte[] utezi, int vzorcev, float natancnostPrej, float natancnost,
                           boolean sprejeta, LocalDateTime ustvarjenoOb) {
        this.utezi = utezi;
        this.vzorcev = vzorcev;
        this.natancnostPrej = natancnostPrej;
        this.natancnost = natancnost;
        this.sprejeta = sprejeta;
        this.ustvarjenoOb = ustvarjenoOb;
    }

    public Long getId() { return id; }
    public byte[] getUtezi() { return utezi; }
    public int getVzorcev() { return vzorcev; }
    public float getNatancnostPrej() { return natancnostPrej; }
    public float getNatancnost() { return natancnost; }
    public boolean isSprejeta() { return sprejeta; }
    public LocalDateTime getUstvarjenoOb() { return ustvarjenoOb; }
}
