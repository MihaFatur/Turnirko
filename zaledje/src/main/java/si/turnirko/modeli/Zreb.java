/* Zreb enega nivoja sistema SV_REGIJA: glavni (indeks 0), tolazilni (1),
   tretji (2) ... Vanj pridejo igralci iz skupin nivoja po parih rangov: prvo-
   in drugouvrsceni v glavni zreb, tretje- in cetrtouvrsceni v tolazilni.

   Zreb se igra za VSA mesta: porazenca vsakega kola se srecata med seboj, do
   zadnjega para. Zato je mesto, ki ga odloca zmagovalec zreba (prvoMesto),
   ABSOLUTNO mesto na dogodku: zreb drugega nivoja se zacne za zadnjim mestom
   prvega.

   Zreb nastane ob zrebu skupin (takrat je znano, koliko igralcev pride vanj),
   tekme pa sele, ko so odigrane vse skupine nivoja (zgrajen). */
package si.turnirko.modeli;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "zreb")
public class Zreb {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_dogodek", nullable = false)
    private Dogodek dogodek;

    @Column(name = "nivo", nullable = false)
    private int nivo;

    /* 0 = glavni zreb, 1 = tolazilni, 2 = tretji ... */
    @Column(name = "indeks", nullable = false)
    private int indeks;

    /* Najvisje mesto na dogodku, ki ga odloca zmagovalec zreba. */
    @Column(name = "prvo_mesto", nullable = false)
    private int prvoMesto;

    /* Koliko igralcev pride v zreb; mreza je najblizja potenca 2 nad tem. */
    @Column(name = "st_udelezencev", nullable = false)
    private int stUdelezencev;

    @Column(name = "zgrajen", nullable = false)
    private boolean zgrajen = false;

    /* Razpored mest je vpisal organizator, ne zreb. Javna oznaka, enako kot
       pri ligi: kdor je razpored dobil po posti, mora videti, da je isti. */
    @Column(name = "rocni", nullable = false)
    private boolean rocni = false;

    /* Prijave po mestih v mrezi od vrha navzdol (CSV, prazno = prosto mesto).
       Iz tekem se razporeda ne da zanesljivo prebrati - prosti prehodi tekem ne
       ustvarijo -, rocno urejanje pa mora izhajati iz trenutnega stanja. */
    @Column(name = "razpored")
    private String razpored;

    @Version
    @Column(name = "verzija", nullable = false)
    private long verzija;

    protected Zreb() {}

    public Zreb(Dogodek dogodek, int nivo, int indeks, int prvoMesto, int stUdelezencev) {
        this.dogodek = dogodek;
        this.nivo = nivo;
        this.indeks = indeks;
        this.prvoMesto = prvoMesto;
        this.stUdelezencev = stUdelezencev;
    }

    public Long getId() { return id; }
    public Dogodek getDogodek() { return dogodek; }
    public int getNivo() { return nivo; }
    public int getIndeks() { return indeks; }

    public int getPrvoMesto() { return prvoMesto; }
    public void setPrvoMesto(int prvoMesto) { this.prvoMesto = prvoMesto; }

    public int getStUdelezencev() { return stUdelezencev; }
    public void setStUdelezencev(int stUdelezencev) { this.stUdelezencev = stUdelezencev; }

    public boolean isZgrajen() { return zgrajen; }
    public void setZgrajen(boolean zgrajen) { this.zgrajen = zgrajen; }

    public boolean isRocni() { return rocni; }
    public void setRocni(boolean rocni) { this.rocni = rocni; }

    public String getRazpored() { return razpored; }
    public void setRazpored(String razpored) { this.razpored = razpored; }

    /* Razpored kot seznam id-jev prijav po mestih (null = prosto mesto). */
    public java.util.List<Long> razporedKotSeznam() {
        java.util.List<Long> mesta = new java.util.ArrayList<>();
        if (razpored == null) {
            return mesta;
        }
        for (String del : razpored.split(",", -1)) {
            mesta.add(del.isBlank() ? null : Long.valueOf(del.trim()));
        }
        return mesta;
    }

    /* Glavni zreb stoji v fazi GLAVNI, vsi ostali v TOLAZILNI. */
    public FazaTekme faza() { return faza(indeks); }

    public static FazaTekme faza(int indeks) {
        return indeks == 0 ? FazaTekme.GLAVNI : FazaTekme.TOLAZILNI;
    }

    /* Velikost mreze: najmanjsa potenca stevila 2, ki sprejme vse udelezence. */
    public int velikostMreze() { return velikostMreze(stUdelezencev); }

    public static int velikostMreze(int stUdelezencev) {
        int potenca = 1;
        while (potenca < stUdelezencev) {
            potenca *= 2;
        }
        return potenca;
    }

    /* Zadnje mesto, ki ga zreb odloca. */
    public int zadnjeMesto() { return prvoMesto + stUdelezencev - 1; }

    public String ime() { return ime(indeks); }

    public static String ime(int indeks) {
        return switch (indeks) {
            case 0 -> "Glavni žreb";
            case 1 -> "Tolažilni žreb";
            default -> (indeks + 1) + ". žreb";
        };
    }
}
