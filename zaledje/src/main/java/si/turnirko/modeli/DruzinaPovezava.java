/* Povezava navijaca z igralcem do 21 let (V59, »druzina«): racun s placljivim
   paketom Navijac vidi zasebno statistiko povezanega igralca, igralcev racun pa
   dobi Premium. Povezavo vedno potrdi administrator (DruzinaStoritev).

   Kot SpremljanIgralec brez povezav na entiteti: tabela odgovarja na dve
   vprasanji - "koga vse ima ta racun v druzini" in "kdo vse ima tega igralca v
   druzini" - in bi jo povezavi le prisilili v nalaganje celotnih zapisov. */
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
import jakarta.persistence.Table;

@Entity
@Table(name = "druzina_povezava")
public class DruzinaPovezava {

    public enum Status { CAKA, POTRJENA, ZAVRNJENA }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "id_racun", nullable = false, updatable = false)
    private Long idRacun;

    @Column(name = "id_igralec", nullable = false, updatable = false)
    private Long idIgralec;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Convert(converter = CasKotBesedilo.class)
    @Column(name = "zahtevana_ob", nullable = false, updatable = false)
    private LocalDateTime zahtevanaOb;

    @Convert(converter = CasKotBesedilo.class)
    @Column(name = "odlocena_ob")
    private LocalDateTime odlocenaOb;

    /* Zadnji trenutek, ko povezava se velja, ker je igralec prerasel pas U21 (konec
       placanega obdobja navijaca). Prazno = brez roka. */
    @Convert(converter = CasKotBesedilo.class)
    @Column(name = "velja_do")
    private LocalDateTime veljaDo;

    protected DruzinaPovezava() {}

    public DruzinaPovezava(Long idRacun, Long idIgralec, LocalDateTime zahtevanaOb) {
        this.idRacun = idRacun;
        this.idIgralec = idIgralec;
        this.status = Status.CAKA;
        this.zahtevanaOb = zahtevanaOb;
    }

    /* Ali povezava po svojem stanju velja ob danem trenutku. Paket navijaca je pogoj
       zase (DruzinaStoritev): tu je samo to, kar je zapisano v vrstici. */
    public boolean veljaOb(LocalDateTime trenutek) {
        return status == Status.POTRJENA && (veljaDo == null || !trenutek.isAfter(veljaDo));
    }

    public Long getId() { return id; }
    public Long getIdRacun() { return idRacun; }
    public Long getIdIgralec() { return idIgralec; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public LocalDateTime getZahtevanaOb() { return zahtevanaOb; }
    public LocalDateTime getOdlocenaOb() { return odlocenaOb; }
    public void setOdlocenaOb(LocalDateTime odlocenaOb) { this.odlocenaOb = odlocenaOb; }
    public LocalDateTime getVeljaDo() { return veljaDo; }
    public void setVeljaDo(LocalDateTime veljaDo) { this.veljaDo = veljaDo; }
}
