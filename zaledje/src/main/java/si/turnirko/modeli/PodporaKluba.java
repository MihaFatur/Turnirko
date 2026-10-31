/* Klub, ki ga racun s paketom Navijac podpira (V61). Brez povezav na entiteti kot
   SpremljanIgralec: tabela odgovarja na dve vprasanji - "kateri klub podpira racun"
   in "kdo podpira klub". Racun podpira najvec en klub (primarni kljuc). */
package si.turnirko.modeli;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "podpora_kluba")
public class PodporaKluba {

    @Id
    @Column(name = "id_racun", nullable = false)
    private Long idRacun;

    @Column(name = "id_klub", nullable = false)
    private Long idKlub;

    @Convert(converter = CasKotBesedilo.class)
    @Column(name = "od", nullable = false)
    private LocalDateTime od;

    protected PodporaKluba() {}

    public PodporaKluba(Long idRacun, Long idKlub, LocalDateTime od) {
        this.idRacun = idRacun;
        this.idKlub = idKlub;
        this.od = od;
    }

    public Long getIdRacun() { return idRacun; }
    public Long getIdKlub() { return idKlub; }
    public void setIdKlub(Long idKlub) { this.idKlub = idKlub; }
    public LocalDateTime getOd() { return od; }
    public void setOd(LocalDateTime od) { this.od = od; }
}
