/* Stevka potrjenega listka (V68): 28 x 28 sivin in vrednost, ki jo je potrdil
   organizator. Brez imen in brez fotografije - samo okence s stevilko. Na teh
   se mreza stevk nocno dotrenira (UcenjeIzListkov). */
package si.turnirko.modeli;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "stevka_listka")
public class StevkaListka {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_branje")
    private Long idBranje;

    @Column(name = "slika", nullable = false, updatable = false)
    private byte[] slika;

    @Column(name = "vrednost", nullable = false)
    private short vrednost;

    @Column(name = "mreza_prav", nullable = false)
    private boolean mrezaPrav;

    @Column(name = "ustvarjeno_ob", nullable = false)
    private LocalDateTime ustvarjenoOb;

    protected StevkaListka() {}

    public StevkaListka(Long idBranje, byte[] slika, int vrednost, boolean mrezaPrav, LocalDateTime ustvarjenoOb) {
        this.idBranje = idBranje;
        this.slika = slika;
        this.vrednost = (short) vrednost;
        this.mrezaPrav = mrezaPrav;
        this.ustvarjenoOb = ustvarjenoOb;
    }

    public Long getId() { return id; }
    public Long getIdBranje() { return idBranje; }
    public byte[] getSlika() { return slika; }
    public int getVrednost() { return vrednost; }
    public boolean isMrezaPrav() { return mrezaPrav; }
    public LocalDateTime getUstvarjenoOb() { return ustvarjenoOb; }
}
