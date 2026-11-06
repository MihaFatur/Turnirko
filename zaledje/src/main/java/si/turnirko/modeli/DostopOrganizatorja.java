/* Dodatni dostop organizatorja do turnirja ali lige (V62), ki ga odobri admin.

   Brez tega organizator upravlja le tekmovanja, ki jih je ustvaril, in
   tekmovanja svojega kluba (LastnistvoStoritev). Vrstica doda tretjo pot in
   pove, ali tekmovanje steje v mejo organizatorjevega paketa.

   Vrstica je lahko tudi o lastniku, ki je tekmovanje ustvaril: takrat dostopa
   ne spremeni (lastnik ga ima vedno), samo prepise, ali tekmovanje steje.

   Namenoma samo id-ji, brez povezav: tabela odgovarja na vprasanji »kdo ima
   dostop do tekmovanja« in »katera tekmovanja steje racun«, kontrolerji pa
   delajo z DTO-ji izven transakcije. Ob izbrisu tekmovanja ali racuna vrstica
   izgine z njim (ON DELETE CASCADE v shemi). */
package si.turnirko.modeli;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "dostop_organizatorja")
public class DostopOrganizatorja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_turnir", updatable = false)
    private Long idTurnir;

    @Column(name = "id_liga", updatable = false)
    private Long idLiga;

    @Column(name = "id_racun", nullable = false, updatable = false)
    private Long idRacun;

    @Column(name = "steje_v_mejo", nullable = false)
    private boolean stejeVMejo;

    /* Od kdaj vrstica steje v mejo sezone. Pri dodeljenem tekmovanju je to
       trenutek dodelitve; sprememba »steje / ne steje« ga ne premakne. */
    @Column(name = "dodeljen_ob", nullable = false)
    private LocalDateTime dodeljenOb;

    protected DostopOrganizatorja() {}

    private DostopOrganizatorja(Long idTurnir, Long idLiga, Long idRacun, boolean stejeVMejo,
                                LocalDateTime dodeljenOb) {
        this.idTurnir = idTurnir;
        this.idLiga = idLiga;
        this.idRacun = idRacun;
        this.stejeVMejo = stejeVMejo;
        this.dodeljenOb = dodeljenOb;
    }

    public static DostopOrganizatorja zaTurnir(Long idTurnir, Long idRacun, boolean stejeVMejo,
                                               LocalDateTime dodeljenOb) {
        return new DostopOrganizatorja(idTurnir, null, idRacun, stejeVMejo, dodeljenOb);
    }

    public static DostopOrganizatorja zaLigo(Long idLiga, Long idRacun, boolean stejeVMejo,
                                             LocalDateTime dodeljenOb) {
        return new DostopOrganizatorja(null, idLiga, idRacun, stejeVMejo, dodeljenOb);
    }

    public Long getId() { return id; }
    public Long getIdTurnir() { return idTurnir; }
    public Long getIdLiga() { return idLiga; }
    public Long getIdRacun() { return idRacun; }
    public boolean isStejeVMejo() { return stejeVMejo; }
    public void setStejeVMejo(boolean stejeVMejo) { this.stejeVMejo = stejeVMejo; }
    public LocalDateTime getDodeljenOb() { return dodeljenOb; }
}
