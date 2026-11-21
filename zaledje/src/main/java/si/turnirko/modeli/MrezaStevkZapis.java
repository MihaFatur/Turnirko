/* Razlicica mreze stevk iz ucenja (V68, zgodovina V72). Sprejeta je samo, ce na
   preizkusnih stevkah, zlatem naboru in MNIST ne bere slabse od prejsnje (UcenjeMreze);
   v uporabi je zadnja sprejeta, ki ni umaknjena, brez nje vgrajena
   (resources/branje/stevke.bin). Prekinjeno ucenje nima utezi. */
package si.turnirko.modeli;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "mreza_stevk")
public class MrezaStevkZapis {

    /* Kdo je ucenje sprozil: nocni urnik ali admin z »Uci zdaj«. */
    public enum Nacin { NOC, ROCNO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "utezi", updatable = false)
    private byte[] utezi;

    @Column(name = "vzorcev", nullable = false)
    private int vzorcev;

    @Column(name = "natancnost_prej", nullable = false)
    private float natancnostPrej;

    /* null pri prekinjenem ucenju */
    @Column(name = "natancnost")
    private Float natancnost;

    @Column(name = "sprejeta", nullable = false)
    private boolean sprejeta;

    @Column(name = "ustvarjeno_ob", nullable = false)
    private LocalDateTime ustvarjenoOb;

    @Enumerated(EnumType.STRING)
    @Column(name = "nacin", nullable = false)
    private Nacin nacin = Nacin.NOC;

    @Column(name = "preizkusnih")
    private Integer preizkusnih;

    @Column(name = "mnist_prej")
    private Float mnistPrej;

    @Column(name = "mnist")
    private Float mnist;

    @Column(name = "zlatih")
    private Integer zlatih;

    @Column(name = "zlati_prej")
    private Float zlatiPrej;

    @Column(name = "zlati")
    private Float zlati;

    @Column(name = "trajanje_ms")
    private Integer trajanjeMs;

    @Column(name = "razlog")
    private String razlog;

    @Column(name = "umaknjena_ob")
    private LocalDateTime umaknjenaOb;

    protected MrezaStevkZapis() {}

    public MrezaStevkZapis(byte[] utezi, int vzorcev, float natancnostPrej, Float natancnost,
                           boolean sprejeta, LocalDateTime ustvarjenoOb) {
        this.utezi = utezi;
        this.vzorcev = vzorcev;
        this.natancnostPrej = natancnostPrej;
        this.natancnost = natancnost;
        this.sprejeta = sprejeta;
        this.ustvarjenoOb = ustvarjenoOb;
    }

    /* Meritve ucenja poleg preizkusnih stevk (V72). */
    public MrezaStevkZapis meritve(Nacin nacin, int preizkusnih, Float mnistPrej, Float mnist,
                                   Integer zlatih, Float zlatiPrej, Float zlati, int trajanjeMs, String razlog) {
        this.nacin = nacin;
        this.preizkusnih = preizkusnih;
        this.mnistPrej = mnistPrej;
        this.mnist = mnist;
        this.zlatih = zlatih;
        this.zlatiPrej = zlatiPrej;
        this.zlati = zlati;
        this.trajanjeMs = trajanjeMs;
        this.razlog = razlog;
        return this;
    }

    public Long getId() { return id; }
    public byte[] getUtezi() { return utezi; }
    public int getVzorcev() { return vzorcev; }
    public float getNatancnostPrej() { return natancnostPrej; }
    public Float getNatancnost() { return natancnost; }
    public boolean isSprejeta() { return sprejeta; }
    public LocalDateTime getUstvarjenoOb() { return ustvarjenoOb; }
    public Nacin getNacin() { return nacin; }
    public Integer getPreizkusnih() { return preizkusnih; }
    public Float getMnistPrej() { return mnistPrej; }
    public Float getMnist() { return mnist; }
    public Integer getZlatih() { return zlatih; }
    public Float getZlatiPrej() { return zlatiPrej; }
    public Float getZlati() { return zlati; }
    public Integer getTrajanjeMs() { return trajanjeMs; }
    public String getRazlog() { return razlog; }
    public LocalDateTime getUmaknjenaOb() { return umaknjenaOb; }

    public void setUmaknjenaOb(LocalDateTime umaknjenaOb) { this.umaknjenaOb = umaknjenaOb; }
}
