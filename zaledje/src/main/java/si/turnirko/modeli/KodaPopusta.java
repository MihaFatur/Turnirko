/* Koda popusta, ki jo oseba vpise ob registraciji (V53, KodePopustaStoritev).
   Admin jo nastavi in ji doloci ugodnost; vrstica ni zgodovina porab (to je
   UporabaKodePopusta), ampak definicija: koda, kaj prinese, do kdaj velja in
   kolikokrat se sme uporabiti.

   Ugodnost (vrsta, meseci, odstotek, paket, zaVlogo) se po nastanku ne
   spreminja - kdor se je registriral, je dobil, kar je koda takrat obljubljala.
   Spremenljivi so le opis, rok, najvecje stevilo uporab in aktivnost. */
package si.turnirko.modeli;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "koda_popusta")
public class KodaPopusta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /* Normalizirana oblika (velike crke, brez presledkov) - glej
       KodePopustaStoritev.normaliziraj. */
    @Column(name = "koda", nullable = false, unique = true, updatable = false)
    private String koda;

    @Column(name = "opis")
    private String opis;

    @Enumerated(EnumType.STRING)
    @Column(name = "vrsta", nullable = false, updatable = false)
    private VrstaKodePopusta vrsta;

    /* PREMIUM_MESECI, ORGANIZATOR_MESECI: koliko mesecev paketa brez placila. */
    @Column(name = "meseci", updatable = false)
    private Integer meseci;

    /* ODSTOTEK: popust na prvo placilo (1-75). */
    @Column(name = "odstotek", updatable = false)
    private Integer odstotek;

    /* ORGANIZATOR_MESECI: organizatorski paket, ki ga koda podeli. */
    @Enumerated(EnumType.STRING)
    @Column(name = "paket", updatable = false)
    private Paket paket;

    /* ODSTOTEK: koda velja samo za to vlogo; null = za igralce in organizatorje. */
    @Enumerated(EnumType.STRING)
    @Column(name = "za_vlogo", updatable = false)
    private Vloga zaVlogo;

    /* Zadnji dan, ko se koda sprejme (vkljucno); null = brez roka. */
    @Column(name = "velja_do")
    private LocalDate veljaDo;

    /* Null = neomejeno. */
    @Column(name = "najvec_uporab")
    private Integer najvecUporab;

    @Column(name = "aktivna", nullable = false)
    private boolean aktivna = true;

    /* Stripov kupon te kode (samo ODSTOTEK); nastane ob prvem placilu. */
    @Column(name = "stripe_kupon_id")
    private String stripeKuponId;

    @Convert(converter = CasKotBesedilo.class)
    @Column(name = "ustvarjena_ob", nullable = false, updatable = false)
    private LocalDateTime ustvarjenaOb;

    protected KodaPopusta() {}

    public KodaPopusta(String koda, VrstaKodePopusta vrsta, LocalDateTime ustvarjenaOb) {
        this.koda = koda;
        this.vrsta = vrsta;
        this.ustvarjenaOb = ustvarjenaOb;
    }

    public Long getId() { return id; }

    public String getKoda() { return koda; }

    public String getOpis() { return opis; }
    public void setOpis(String opis) { this.opis = opis; }

    public VrstaKodePopusta getVrsta() { return vrsta; }

    public Integer getMeseci() { return meseci; }
    public void setMeseci(Integer meseci) { this.meseci = meseci; }

    public Integer getOdstotek() { return odstotek; }
    public void setOdstotek(Integer odstotek) { this.odstotek = odstotek; }

    public Paket getPaket() { return paket; }
    public void setPaket(Paket paket) { this.paket = paket; }

    public Vloga getZaVlogo() { return zaVlogo; }
    public void setZaVlogo(Vloga zaVlogo) { this.zaVlogo = zaVlogo; }

    public LocalDate getVeljaDo() { return veljaDo; }
    public void setVeljaDo(LocalDate veljaDo) { this.veljaDo = veljaDo; }

    public Integer getNajvecUporab() { return najvecUporab; }
    public void setNajvecUporab(Integer najvecUporab) { this.najvecUporab = najvecUporab; }

    public boolean isAktivna() { return aktivna; }
    public void setAktivna(boolean aktivna) { this.aktivna = aktivna; }

    public String getStripeKuponId() { return stripeKuponId; }
    public void setStripeKuponId(String stripeKuponId) { this.stripeKuponId = stripeKuponId; }

    public LocalDateTime getUstvarjenaOb() { return ustvarjenaOb; }

    /* Rok je zadnji dan, ko koda se velja. */
    public boolean jePotekla(LocalDate danes) {
        return veljaDo != null && danes.isAfter(veljaDo);
    }
}
