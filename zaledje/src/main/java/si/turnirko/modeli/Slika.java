/* Nalozena slika (V54) - samo opis, brez bajtov (ti so v SlikaRazlicica).
   Liga in turnir jo naložita ob vsakem branju, zato mora ostati majhna: kdor bi
   sem dodal vsebino, bi seznam lig ob vsakem ogledu bral megabajte.

   Zgostitev je SHA-256 razlicice TISK in je javni naslov slike
   (/api/v1/slike/{zgostitev}): ista vsebina ima vedno isti naslov, zato ga
   brskalnik sme hraniti za vedno, nova slika pa je nov naslov. */
package si.turnirko.modeli;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "slika")
public class Slika {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "zgostitev", nullable = false, unique = true, updatable = false)
    private String zgostitev;

    @Enumerated(EnumType.STRING)
    @Column(name = "namen", nullable = false, updatable = false)
    private NamenSlike namen;

    /* Meri razlicice TISK - vmesnik iz njiju ve razmerje stranic, preden se
       slika nalozi, in ji pripravi prostor. */
    @Column(name = "sirina", nullable = false, updatable = false)
    private int sirina;

    @Column(name = "visina", nullable = false, updatable = false)
    private int visina;

    /* Stevilka in ne entiteta (kot UporabaKodePopusta.idUporabnik): racun se
       lahko izbrise, slika pa ostane, ker jo nosi tekmovanje. Ista datoteka,
       nalozena znova, sliko prepise na zadnjega, ki jo je nalozil (glej
       SlikeStoritev.nalozi). */
    @Column(name = "id_nalozil")
    private Long idNalozil;

    @Convert(converter = CasKotBesedilo.class)
    @Column(name = "ustvarjena_ob", nullable = false, updatable = false)
    private LocalDateTime ustvarjenaOb;

    protected Slika() {
    }

    public Slika(String zgostitev, NamenSlike namen, int sirina, int visina, Long idNalozil) {
        this.zgostitev = zgostitev;
        this.namen = namen;
        this.sirina = sirina;
        this.visina = visina;
        this.idNalozil = idNalozil;
    }

    @PrePersist
    void obShranjevanju() {
        if (ustvarjenaOb == null) {
            ustvarjenaOb = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public String getZgostitev() { return zgostitev; }
    public NamenSlike getNamen() { return namen; }
    public int getSirina() { return sirina; }
    public int getVisina() { return visina; }
    public Long getIdNalozil() { return idNalozil; }
    public void setIdNalozil(Long idNalozil) { this.idNalozil = idNalozil; }
    public LocalDateTime getUstvarjenaOb() { return ustvarjenaOb; }
}
