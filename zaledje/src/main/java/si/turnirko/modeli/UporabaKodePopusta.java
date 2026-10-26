/* Racun, ki je ob registraciji vpisal kodo popusta (V53). Vrstica nastane v
   trenutku, ko racun nastane (prosta registracija) oz. ko Stripe potrdi
   placilo (placljiva), in gre skupaj z racunom (ON DELETE CASCADE): nepotrjena
   registracija, ki jo po 48 urah izbrise nocno ciscenje, tako vrne mesto kodi.

   Racun je tu SAMO stevilka, ne entiteta: Hibernate ob izpisu naleti na
   nalozeno vrstico, ki kaze na izbrisan racun (nova registracija zamenja
   nepotrjen ostanek, admin izbrise racun), ceprav bi baza vrstico odnesla sama. */
package si.turnirko.modeli;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "uporaba_kode_popusta")
public class UporabaKodePopusta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_koda", nullable = false, updatable = false)
    private KodaPopusta koda;

    @Column(name = "id_uporabnik", nullable = false, unique = true, updatable = false)
    private Long idUporabnik;

    @Convert(converter = CasKotBesedilo.class)
    @Column(name = "uporabljena_ob", nullable = false, updatable = false)
    private LocalDateTime uporabljenaOb;

    protected UporabaKodePopusta() {}

    public UporabaKodePopusta(KodaPopusta koda, Long idUporabnik, LocalDateTime uporabljenaOb) {
        this.koda = koda;
        this.idUporabnik = idUporabnik;
        this.uporabljenaOb = uporabljenaOb;
    }

    public Long getId() { return id; }
    public KodaPopusta getKoda() { return koda; }
    public Long getIdUporabnik() { return idUporabnik; }
    public LocalDateTime getUporabljenaOb() { return uporabljenaOb; }
}
