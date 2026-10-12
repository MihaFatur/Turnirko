/* Vrstica dnevnika racuna (stran Dostopi): kaj se je z racunom zgodilo in
   kdaj. Besedilo se zapise ob dogodku in se nikoli ne racuna nazaj - ime
   igralca, klub in paket se pozneje spremenijo, zapis o preteklosti ne.

   Namenoma brez povezave na Uporabnik (samo id): tabela odgovarja na eno
   vprasanje (»kaj je bilo z racunom X«) in ob izbrisu racuna izgine z njim
   (ON DELETE CASCADE v shemi). */
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
@Table(name = "racun_dogodek")
public class RacunDogodek {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_racun", nullable = false)
    private Long idRacun;

    @Convert(converter = CasKotBesedilo.class)
    @Column(name = "ob", nullable = false)
    private LocalDateTime ob;

    @Column(name = "tekst", nullable = false)
    private String tekst;

    protected RacunDogodek() {}

    public RacunDogodek(Long idRacun, LocalDateTime ob, String tekst) {
        this.idRacun = idRacun;
        this.ob = ob;
        this.tekst = tekst;
    }

    public Long getId() { return id; }
    public Long getIdRacun() { return idRacun; }
    public LocalDateTime getOb() { return ob; }
    public String getTekst() { return tekst; }
}
