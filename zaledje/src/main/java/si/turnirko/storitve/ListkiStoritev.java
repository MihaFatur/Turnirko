/* Sled natisnjenih listkov tekem turnirskega dogodka.

   Organizator listke natisne ze med igro, rezultat pa se vnese sele pozneje.
   Brez sledi bi se ob naslednjem tisku v isti kupcek pomesali listki, ki so ze
   na mizah, in tisti, ki jih sodniki se nimajo.

   Oznaka je samo opomnik organizatorju (tekma.listek_natisnjen_ob, V67): na
   potek tekme, status in rezultat ne vpliva, listek se da natisniti znova, ob
   napaki pa oznako organizator odstrani. Brskalnik ne pove, ali je tisk res
   stekel, zato oznako vmesnik zapise sele, ko organizator to potrdi. */
package si.turnirko.storitve;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.KodeListkovDto;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.modeli.Turnir;
import si.turnirko.repozitoriji.TekmaRepozitorij;
import si.turnirko.repozitoriji.TurnirRepozitorij;

@Service
public class ListkiStoritev {

    private final TekmaRepozitorij tekmaRepozitorij;
    private final TurnirRepozitorij turnirRepozitorij;
    private final LastnistvoStoritev lastnistvo;
    private final SkrivnostListka skrivnost;

    public ListkiStoritev(TekmaRepozitorij tekmaRepozitorij, TurnirRepozitorij turnirRepozitorij,
                          LastnistvoStoritev lastnistvo, SkrivnostListka skrivnost) {
        this.tekmaRepozitorij = tekmaRepozitorij;
        this.turnirRepozitorij = turnirRepozitorij;
        this.lastnistvo = lastnistvo;
        this.skrivnost = skrivnost;
    }

    /* Vsebina QR kode za listek vsake tekme dogodka (id tekme -> »TK:<id>:<skrivnost>«)
       in stopnja sodniske potrditve turnirja: ko sodniki slikajo listke, gre na
       hrbtno stran lista koda do strani za sodnika. Skrivnost da sodniku pravico
       poslati izid (S1/S2), zato jo dobi samo organizator (lastnik) turnirja ali
       admin - javni podatki tekme je nimajo. */
    @Transactional(readOnly = true)
    public KodeListkovDto kode(Long idDogodka) {
        Turnir turnir = turnirRepozitorij.najdiZLastnistvomPoDogodku(idDogodka)
                .orElseThrow(() -> new NiNajdenoIzjema("Dogodek z id " + idDogodka + " ne obstaja."));
        lastnistvo.preveriTurnir(turnir);
        Map<Long, String> kode = new LinkedHashMap<>();
        for (Long id : tekmaRepozitorij.idjiZaDogodek(idDogodka)) {
            kode.put(id, skrivnost.vsebinaQr(id));
        }
        return new KodeListkovDto(turnir.getSodnikiListki(), kode);
    }

    /* Tekme dogodka oznaci kot natisnjene (zdaj) ali neoznaci (null). Id, ki ne
       pripada dogodku, se tiho preskoci - vrne stevilo dejansko spremenjenih
       tekem. Samo organizator (lastnik) turnirja ali admin. */
    @Transactional
    public int oznaci(Long idDogodka, List<Long> idjiTekem, boolean natisnjeno) {
        lastnistvo.preveriTurnirPoDogodku(idDogodka);
        if (idjiTekem == null || idjiTekem.isEmpty()) {
            return 0;
        }
        return tekmaRepozitorij.nastaviListekNatisnjen(
                idDogodka, idjiTekem, natisnjeno ? LocalDateTime.now() : null);
    }
}
