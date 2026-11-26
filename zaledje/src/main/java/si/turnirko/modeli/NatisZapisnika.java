/* Natis ligaskega zapisnika za bralnik (V79): kaj je bilo na listu ob tisku.
   QR na listu nosi samo id natisa in skrivnost; bralnik iz natisa izve obliko,
   razlicico postavitve, format, nize in kadra po vrsticah (id igralcev v JSON
   seznamu, v vrstnem redu vrstic; prazna vrstica za novega igralca ni v seznamu).
   Kader se lahko po tisku spremeni, natisnjen list pa ostane berljiv. */
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

import si.turnirko.branje.GeometrijaZapisnika;

@Entity
@Table(name = "natis_zapisnika")
public class NatisZapisnika {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_srecanje", nullable = false)
    private Srecanje srecanje;

    @Enumerated(EnumType.STRING)
    @Column(name = "oblika", nullable = false)
    private GeometrijaZapisnika.Oblika oblika;

    @Column(name = "razlicica", nullable = false)
    private int razlicica;

    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false)
    private FormatSrecanja format;

    @Column(name = "st_nizov", nullable = false)
    private int steviloNizov;

    /* JSON seznam id igralcev po vrsticah kadra. */
    @Column(name = "kader_domaci", nullable = false)
    private String kaderDomaci;

    @Column(name = "kader_gost", nullable = false)
    private String kaderGost;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_ustvaril")
    private Uporabnik ustvaril;

    @Column(name = "ustvarjeno_ob", nullable = false)
    private LocalDateTime ustvarjenoOb;

    protected NatisZapisnika() {}

    public NatisZapisnika(Srecanje srecanje, GeometrijaZapisnika.Oblika oblika, int razlicica, FormatSrecanja format,
                          int steviloNizov, String kaderDomaci, String kaderGost, Uporabnik ustvaril,
                          LocalDateTime ustvarjenoOb) {
        this.srecanje = srecanje;
        this.oblika = oblika;
        this.razlicica = razlicica;
        this.format = format;
        this.steviloNizov = steviloNizov;
        this.kaderDomaci = kaderDomaci;
        this.kaderGost = kaderGost;
        this.ustvaril = ustvaril;
        this.ustvarjenoOb = ustvarjenoOb;
    }

    public Long getId() { return id; }
    public Srecanje getSrecanje() { return srecanje; }
    public GeometrijaZapisnika.Oblika getOblika() { return oblika; }
    public int getRazlicica() { return razlicica; }
    public FormatSrecanja getFormat() { return format; }
    public int getSteviloNizov() { return steviloNizov; }
    public String getKaderDomaci() { return kaderDomaci; }
    public String getKaderGost() { return kaderGost; }
    public LocalDateTime getUstvarjenoOb() { return ustvarjenoOb; }
}
