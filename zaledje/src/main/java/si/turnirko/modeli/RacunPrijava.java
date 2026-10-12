/* Ena prijava racuna (stran Dostopi: zadnja prijava, prijave v 30 dneh).
   Prijava je prva overjena zahteva po vsaj 30 minutah mirovanja - veriga je
   brez stanja, zato druge seje ni. Glej RacunDnevnikStoritev. */
package si.turnirko.modeli;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "racun_prijava")
public class RacunPrijava {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_racun", nullable = false)
    private Long idRacun;

    @Convert(converter = CasKotBesedilo.class)
    @Column(name = "ob", nullable = false)
    private LocalDateTime ob;

    protected RacunPrijava() {}

    public RacunPrijava(Long idRacun, LocalDateTime ob) {
        this.idRacun = idRacun;
        this.ob = ob;
    }

    public Long getId() { return id; }
    public Long getIdRacun() { return idRacun; }
    public LocalDateTime getOb() { return ob; }
}
