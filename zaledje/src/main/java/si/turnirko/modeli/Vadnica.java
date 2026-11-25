/* List vadnice stevk (V70): ena oseba je v okenca prepisala stevke 0-9 in
   stevila 10-13. Id je id lista iz QR kode (izbere ga vmesnik ob tisku). Slika
   je poravnan list, na katerem so ohranjena samo okenca (brez imen, list jih
   tudi nima). Stevke so v stevka_listka (id_vadnica). */
package si.turnirko.modeli;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "vadnica")
public class Vadnica {

    @Id
    private Long id;

    @Column(name = "razlicica", nullable = false)
    private short razlicica;

    /* Seznam listov slike ne bere (VadnicaRepozitorij.seznam je projekcija). */
    @Column(name = "slika", nullable = false)
    private byte[] slika;

    @Column(name = "stevk", nullable = false)
    private int stevk;

    @Column(name = "praznih", nullable = false)
    private int praznih;

    @Column(name = "preskocenih", nullable = false)
    private int preskocenih;

    @Column(name = "odobrena", nullable = false)
    private boolean odobrena;

    @Column(name = "ustvarjeno_ob", nullable = false)
    private LocalDateTime ustvarjenoOb;

    @Column(name = "odobreno_ob")
    private LocalDateTime odobrenoOb;

    protected Vadnica() {}

    public Vadnica(Long id, int razlicica) {
        this.id = id;
        this.razlicica = (short) razlicica;
    }

    /* Nova fotografija istega (se neodobrenega) lista zamenja prejsnjo. Razlicica je
       iz kode QR nove fotografije: stevke so razrezane po njeni postavitvi, pregled jih
       razvrsti po njej. */
    public void nastaviBranje(int razlicica, byte[] slika, int stevk, int praznih, int preskocenih,
                              LocalDateTime ob) {
        this.razlicica = (short) razlicica;
        this.slika = slika;
        this.stevk = stevk;
        this.praznih = praznih;
        this.preskocenih = preskocenih;
        this.ustvarjenoOb = ob;
    }

    public void odobri(int stevk, LocalDateTime ob) {
        this.stevk = stevk;
        this.odobrena = true;
        this.odobrenoOb = ob;
    }

    public Long getId() { return id; }
    public int getRazlicica() { return razlicica; }
    public byte[] getSlika() { return slika; }
    public int getStevk() { return stevk; }
    public int getPraznih() { return praznih; }
    public int getPreskocenih() { return preskocenih; }
    public boolean isOdobrena() { return odobrena; }
    public LocalDateTime getUstvarjenoOb() { return ustvarjenoOb; }
    public LocalDateTime getOdobrenoOb() { return odobrenoOb; }
}
