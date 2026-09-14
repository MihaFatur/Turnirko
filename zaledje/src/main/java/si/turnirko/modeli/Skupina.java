/* Skupina v skupinskem delu tekmovanja. Znotraj skupine igra vsak z vsakim.

   Skupina ima STOPNJO (V28): 1 je predtekmovanje, visje stopnje so skupine,
   ki nastanejo iz uvrstitev prejsnje (finalne skupine za mesta pri sistemu
   SKUPINE_ZA_MESTA, druga skupinska stopnja uvozenih ekipnih prvenstev).
   Finalna skupina za mesta nosi PRVO MESTO, ki ga odloca: zmagovalec skupine
   za 5.-8. mesto je peti na dogodku. */
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

@Entity
@Table(name = "skupina")
public class Skupina {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_dogodek", nullable = false)
    private Dogodek dogodek;

    /* Oznaka skupine: 'A', 'B', 'C'... (enolicna na dogodku). */
    @Column(name = "oznaka", nullable = false)
    private String oznaka;

    /* 1 = predtekmovalna skupina; visje stopnje nastanejo iz uvrstitev. */
    @Column(name = "stopnja", nullable = false)
    private int stopnja = 1;

    /* Ime za prikaz, kadar oznaka ne pove dovolj ("1.–4. mesto"). */
    @Column(name = "ime")
    private String ime;

    /* Najvisje mesto, ki ga skupina odloca (null = skupina ne odloca koncnih mest). */
    @Column(name = "prvo_mesto")
    private Integer prvoMesto;

    protected Skupina() {}

    public Skupina(Dogodek dogodek, String oznaka) {
        this.dogodek = dogodek;
        this.oznaka = oznaka;
    }

    public Long getId() { return id; }

    public Dogodek getDogodek() { return dogodek; }

    public String getOznaka() { return oznaka; }
    public void setOznaka(String oznaka) { this.oznaka = oznaka; }

    public int getStopnja() { return stopnja; }
    public void setStopnja(int stopnja) { this.stopnja = stopnja; }

    public String getIme() { return ime; }
    public void setIme(String ime) { this.ime = ime; }

    public Integer getPrvoMesto() { return prvoMesto; }
    public void setPrvoMesto(Integer prvoMesto) { this.prvoMesto = prvoMesto; }
}
