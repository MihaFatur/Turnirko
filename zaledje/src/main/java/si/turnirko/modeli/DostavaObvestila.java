/* Dostava obvestila po kolu ali po turnirju enemu prejemniku (V66).

   Vrstica nastane ob prvem poskusu dostave. Prejemnik je urejen, ko je
   sporocilo poslano ali ko so poskusi izcrpani; dokler kdo se caka, kolo
   oziroma turnir ni zapisan kot obdelan (ObvestiloKola, ObvestiloTurnirja) in
   opravilo ga naslednjo uro poskusi znova - samo zanj.

   Kot ObvestiloKola brez povezav na entitete: racun in tekmovanje sta samo
   stevilki, vrstice pa ob izbrisu racuna, lige ali turnirja odnese baza
   (ON DELETE CASCADE). Vrstica je ali za kolo (idLiga + kolo) ali za turnir. */
package si.turnirko.modeli;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "dostava_obvestila")
public class DostavaObvestila {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "id_liga")
    private Long idLiga;

    @Column(name = "kolo")
    private Integer kolo;

    @Column(name = "id_turnir")
    private Long idTurnir;

    @Column(name = "id_racun", nullable = false)
    private Long idRacun;

    @Column(name = "poslano_ob")
    private LocalDateTime poslanoOb;

    @Column(name = "poskusov", nullable = false)
    private int poskusov;

    @Column(name = "zadnji_poskus_ob", nullable = false)
    private LocalDateTime zadnjiPoskusOb;

    protected DostavaObvestila() {}

    public DostavaObvestila(Long idLiga, Integer kolo, Long idTurnir, Long idRacun) {
        this.idLiga = idLiga;
        this.kolo = kolo;
        this.idTurnir = idTurnir;
        this.idRacun = idRacun;
    }

    /* En poskus dostave: uspeh zapise cas, neuspeh samo poveca stevec. */
    public void zabeleziPoskus(boolean poslano, LocalDateTime ob) {
        poskusov++;
        zadnjiPoskusOb = ob;
        if (poslano) {
            poslanoOb = ob;
        }
    }

    public boolean jePoslano() {
        return poslanoOb != null;
    }

    public Long getId() { return id; }
    public Long getIdLiga() { return idLiga; }
    public Integer getKolo() { return kolo; }
    public Long getIdTurnir() { return idTurnir; }
    public Long getIdRacun() { return idRacun; }
    public LocalDateTime getPoslanoOb() { return poslanoOb; }
    public int getPoskusov() { return poskusov; }
    public LocalDateTime getZadnjiPoskusOb() { return zadnjiPoskusOb; }
}
