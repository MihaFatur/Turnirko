/* Podatki obrazca placljive registracije, dokler Stripe ne potrdi placila
   (V48). Racun nastane sele v webhooku; do takrat so podatki tu, v Stripu pa
   je samo zeton - Stripove dogodke bere tudi Moj Racun, ki gesla, datuma
   rojstva in naslova skrbnika ne potrebuje. */
package si.turnirko.modeli;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "cakajoca_registracija")
public class CakajocaRegistracija {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "zeton", nullable = false, unique = true, updatable = false)
    private String zeton;

    @Column(name = "ime", nullable = false)
    private String ime;

    @Column(name = "priimek", nullable = false)
    private String priimek;

    @Column(name = "id_klub")
    private Long idKlub;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "geslo_hash", nullable = false)
    private String gesloHash;

    @Column(name = "organizator", nullable = false)
    private boolean organizator;

    @Column(name = "datum_rojstva")
    private LocalDate datumRojstva;

    @Column(name = "email_skrbnika")
    private String emailSkrbnika;

    /* Ura steje (ciscenje po dneh od nastanka), zato pretvornik. */
    @Convert(converter = CasKotBesedilo.class)
    @Column(name = "ustvarjena_ob", nullable = false, updatable = false)
    private LocalDateTime ustvarjenaOb;

    protected CakajocaRegistracija() {}

    public CakajocaRegistracija(String zeton, String ime, String priimek, Long idKlub, String email,
                                String gesloHash, boolean organizator, LocalDate datumRojstva,
                                String emailSkrbnika, LocalDateTime ustvarjenaOb) {
        this.zeton = zeton;
        this.ime = ime;
        this.priimek = priimek;
        this.idKlub = idKlub;
        this.email = email;
        this.gesloHash = gesloHash;
        this.organizator = organizator;
        this.datumRojstva = datumRojstva;
        this.emailSkrbnika = emailSkrbnika;
        this.ustvarjenaOb = ustvarjenaOb;
    }

    public Long getId() { return id; }
    public String getZeton() { return zeton; }
    public String getIme() { return ime; }
    public String getPriimek() { return priimek; }
    public Long getIdKlub() { return idKlub; }
    public String getEmail() { return email; }
    public String getGesloHash() { return gesloHash; }
    public boolean isOrganizator() { return organizator; }
    public LocalDate getDatumRojstva() { return datumRojstva; }
    public String getEmailSkrbnika() { return emailSkrbnika; }
    public LocalDateTime getUstvarjenaOb() { return ustvarjenaOb; }
}
