/* Ekipa je nastop v ligi. Praviloma nastopa klub - en klub ima lahko vec ekip
   (Savinja 1, 2), locenih po zaporedni stevilki. Ekipa pa je lahko tudi PROSTA
   (brez kluba): zasedba, ki v registru klubov nima zapisa in nastopa samo v tej
   ligi (rekreacijske in medpodjetniske lige). Taka ekipa se poimenuje sama, z
   lastnim imenom. Igralce, ki smejo nastopati, v obeh primerih hrani kader -
   ti so vedno iz skupnega registra igralcev. */
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
@Table(name = "ekipa")
public class Ekipa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_liga", nullable = false)
    private Liga liga;

    /* Prazen pri prosti ekipi - ta v registru klubov nima zapisa. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_klub")
    private Klub klub;

    @Column(name = "zaporedna", nullable = false)
    private int zaporedna = 1;

    /* Prikazano ime; pri klubski ekipi neobvezno (sestavi se iz kluba in
       zaporedne), pri prosti obvezno - drugega poimenovanja nima. */
    @Column(name = "ime")
    private String ime;

    @Version
    @Column(name = "verzija", nullable = false)
    private long verzija;

    protected Ekipa() {}

    /* Klub sme biti null (prosta ekipa); takrat je ime obvezno - to preveri
       LigaStoritev, zadnja obramba pa je omejitev CHECK v shemi. */
    public Ekipa(Liga liga, Klub klub, int zaporedna, String ime) {
        this.liga = liga;
        this.klub = klub;
        this.zaporedna = zaporedna;
        this.ime = ime;
    }

    public Long getId() { return id; }

    public Liga getLiga() { return liga; }
    public Klub getKlub() { return klub; }

    public int getZaporedna() { return zaporedna; }
    public void setZaporedna(int zaporedna) { this.zaporedna = zaporedna; }

    public String getIme() { return ime; }
    public void setIme(String ime) { this.ime = ime; }

    /* Ali je ekipa prosta - brez zapisa v registru klubov. */
    public boolean jeProsta() { return klub == null; }

    /* Ime za prikaz: lastno ime ali "Klub N" (N samo, ce ima klub vec ekip).
       Prosta ekipa pride vedno do prve veje - brez kluba je ime edino, kar
       ima, in zato ob vpisu obvezno. */
    public String prikazanoIme() {
        if (ime != null && !ime.isBlank()) return ime;
        return klub.getIme() + " " + zaporedna;
    }
}
