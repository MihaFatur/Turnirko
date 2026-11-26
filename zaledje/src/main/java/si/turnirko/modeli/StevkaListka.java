/* Stevka za ucenje (V68): 28 x 28 sivin in prava vrednost. Brez imen in brez
   fotografije - samo okence s stevilko. Na teh se mreza stevk nocno dotrenira
   (UcenjeIzListkov).

   Vir je potrjen listek (idBranje; vrednost je potrdil organizator) ali list
   vadnice stevk (idVadnica, V70; vrednost je vrstica lista, okence je celica).
   Stevke vadnice gredo v ucenje sele po odobritvi admina (odobreno).

   popravljena (V72, F6.2): celico je organizator izrecno popravil ali vpisal
   (bralnik je prebral drugo ali nic) ali jo je popravil poznejsi popravek izida.
   Taka oznaka je mocna - prav tam se je mreza zmotila - in pri ucenju steje dvojno. */
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

    @Column(name = "id_vadnica")
    private Long idVadnica;

    /* Potrjen ligaski zapisnik (V79): pisava iz dvorane kot pri listkih. */
    @Column(name = "id_branje_zapisnika")
    private Long idBranjeZapisnika;

    /* Okence lista vadnice (vrstica * 10 + stolpec); pri listku celica listka
       (UcenjeIzListkov.celicaListka: niz * 2 + stolpec, rezultat 100 + stolpec, V72),
       pri starejsih stevkah listkov null. */
    @Column(name = "celica")
    private Short celica;

    @Column(name = "slika", nullable = false, updatable = false)
    private byte[] slika;

    @Column(name = "vrednost", nullable = false)
    private short vrednost;

    @Column(name = "mreza_prav", nullable = false)
    private boolean mrezaPrav;

    @Column(name = "odobreno", nullable = false)
    private boolean odobreno = true;

    @Column(name = "popravljena", nullable = false)
    private boolean popravljena;

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

    /* Stevka potrjenega ligaskega zapisnika (V79): vrednost je potrdil organizator;
       celica = 10 * tekma + 2 * niz + stran (0 domaci, 1 gost). */
    public static StevkaListka zZapisnika(Long idBranjeZapisnika, int celica, byte[] slika, int vrednost,
                                         boolean mrezaPrav, LocalDateTime ustvarjenoOb) {
        StevkaListka s = new StevkaListka(null, slika, vrednost, mrezaPrav, ustvarjenoOb);
        s.idBranjeZapisnika = idBranjeZapisnika;
        s.celica = (short) celica;
        return s;
    }

    /* Stevka lista vadnice: do odobritve ni v ucenju. */
    public static StevkaListka zVadnice(Long idVadnica, int celica, byte[] slika, int vrednost, boolean mrezaPrav,
                                        LocalDateTime ustvarjenoOb) {
        StevkaListka s = new StevkaListka(null, slika, vrednost, mrezaPrav, ustvarjenoOb);
        s.idVadnica = idVadnica;
        s.celica = (short) celica;
        s.odobreno = false;
        return s;
    }

    public Long getId() { return id; }
    public Long getIdBranje() { return idBranje; }
    public Long getIdVadnica() { return idVadnica; }
    public Long getIdBranjeZapisnika() { return idBranjeZapisnika; }

    /* Stevka s prave pisave iz dvorane: potrjen listek ali ligaski zapisnik. */
    public boolean jeIzDvorane() {
        return idBranje != null || idBranjeZapisnika != null;
    }
    public Short getCelica() { return celica; }
    public byte[] getSlika() { return slika; }
    public int getVrednost() { return vrednost; }
    public boolean isMrezaPrav() { return mrezaPrav; }
    public boolean isOdobreno() { return odobreno; }
    public LocalDateTime getUstvarjenoOb() { return ustvarjenoOb; }
    public boolean isPopravljena() { return popravljena; }

    /* Mocna oznaka (F6.2), nastavi se pred zapisom. */
    public StevkaListka popravljena(boolean popravljena) {
        this.popravljena = popravljena;
        return this;
    }

    /* Celica listka (F6.1), nastavi se pred zapisom. */
    public StevkaListka celica(int celica) {
        this.celica = (short) celica;
        return this;
    }
}
