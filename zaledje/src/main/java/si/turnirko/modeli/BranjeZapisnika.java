/* Eno branje ligaskega zapisnika s fotografije (V79). Sled kot pri listkih
   (BranjeListka): prepis bralnika, predlog, potrjeni zapis in kdo je kaj naredil.
   Zapis v srecanje gre vedno po isti poti kot rocni vnos (ZapisZapisnikaStoritev). */
package si.turnirko.modeli;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "branje_zapisnika")
public class BranjeZapisnika {

    public enum Stanje {
        V_VRSTI, PREBRANO, NAPAKA, ZAPISANO, ZAVRZENO;

        public boolean jeOdprto() {
            return this == V_VRSTI || this == PREBRANO || this == NAPAKA;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_nalozil")
    private Uporabnik nalozil;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_natis")
    private NatisZapisnika natis;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_srecanje")
    private Srecanje srecanje;

    @Enumerated(EnumType.STRING)
    @Column(name = "stanje", nullable = false)
    private Stanje stanje = Stanje.V_VRSTI;

    /* Organizator je ob nalaganju dovolil samodejni zapis zanesljivo prebranega. */
    @Column(name = "samodejno", nullable = false)
    private boolean samodejno;

    @Column(name = "zapisano_samodejno", nullable = false)
    private boolean zapisanoSamodejno;

    @Column(name = "zanesljivo")
    private Boolean zanesljivo;

    /* JSON PrebranZapisnik. */
    @Column(name = "prebrano")
    private String prebrano;

    /* JSON CeliceZapisnikaDto (lega na poravnanem listu). */
    @Column(name = "celice")
    private String celice;

    /* JSON zapisa, ki je sel v srecanje (ZapisZapisnikaVnos). */
    @Column(name = "potrjeno")
    private String potrjeno;

    @Column(name = "popravljeno")
    private Boolean popravljeno;

    @Column(name = "napaka")
    private String napaka;

    /* Razlog, zakaj se zanesljiv zapisnik ni zapisal sam (srecanje je ze vpisano ...). */
    @Column(name = "opomba")
    private String opomba;

    @Column(name = "model")
    private String model;

    @Column(name = "kljuc_posiljanja")
    private String kljucPosiljanja;

    @Column(name = "ustvarjeno_ob", nullable = false)
    private LocalDateTime ustvarjenoOb;

    @Column(name = "prebrano_ob")
    private LocalDateTime prebranoOb;

    @Column(name = "zakljuceno_ob")
    private LocalDateTime zakljucenoOb;

    @Column(name = "slika_izbrisana_ob")
    private LocalDateTime slikaIzbrisanaOb;

    protected BranjeZapisnika() {}

    public BranjeZapisnika(Uporabnik nalozil, boolean samodejno, LocalDateTime ustvarjenoOb) {
        this.nalozil = nalozil;
        this.samodejno = samodejno;
        this.ustvarjenoOb = ustvarjenoOb;
    }

    public Long getId() { return id; }
    public Uporabnik getNalozil() { return nalozil; }
    public NatisZapisnika getNatis() { return natis; }
    public void setNatis(NatisZapisnika natis) { this.natis = natis; }
    public Srecanje getSrecanje() { return srecanje; }
    public void setSrecanje(Srecanje srecanje) { this.srecanje = srecanje; }
    public Stanje getStanje() { return stanje; }
    public void setStanje(Stanje stanje) { this.stanje = stanje; }
    public boolean isSamodejno() { return samodejno; }
    public boolean isZapisanoSamodejno() { return zapisanoSamodejno; }
    public void setZapisanoSamodejno(boolean zapisanoSamodejno) { this.zapisanoSamodejno = zapisanoSamodejno; }
    public Boolean getZanesljivo() { return zanesljivo; }
    public void setZanesljivo(Boolean zanesljivo) { this.zanesljivo = zanesljivo; }
    public String getPrebrano() { return prebrano; }
    public void setPrebrano(String prebrano) { this.prebrano = prebrano; }
    public String getCelice() { return celice; }
    public void setCelice(String celice) { this.celice = celice; }
    public String getPotrjeno() { return potrjeno; }
    public void setPotrjeno(String potrjeno) { this.potrjeno = potrjeno; }
    public Boolean getPopravljeno() { return popravljeno; }
    public void setPopravljeno(Boolean popravljeno) { this.popravljeno = popravljeno; }
    public String getNapaka() { return napaka; }
    public void setNapaka(String napaka) { this.napaka = napaka; }
    public String getOpomba() { return opomba; }
    public void setOpomba(String opomba) { this.opomba = opomba; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getKljucPosiljanja() { return kljucPosiljanja; }
    public void setKljucPosiljanja(String kljucPosiljanja) { this.kljucPosiljanja = kljucPosiljanja; }
    public LocalDateTime getUstvarjenoOb() { return ustvarjenoOb; }
    public LocalDateTime getPrebranoOb() { return prebranoOb; }
    public void setPrebranoOb(LocalDateTime prebranoOb) { this.prebranoOb = prebranoOb; }
    public LocalDateTime getZakljucenoOb() { return zakljucenoOb; }
    public void setZakljucenoOb(LocalDateTime zakljucenoOb) { this.zakljucenoOb = zakljucenoOb; }
    public LocalDateTime getSlikaIzbrisanaOb() { return slikaIzbrisanaOb; }
    public void setSlikaIzbrisanaOb(LocalDateTime slikaIzbrisanaOb) { this.slikaIzbrisanaOb = slikaIzbrisanaOb; }
}
