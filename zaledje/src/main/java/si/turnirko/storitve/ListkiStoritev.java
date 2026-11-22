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
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.repozitoriji.TekmaRepozitorij;

@Service
public class ListkiStoritev {

    private final TekmaRepozitorij tekmaRepozitorij;
    private final LastnistvoStoritev lastnistvo;
    private final SkrivnostListka skrivnost;

    public ListkiStoritev(TekmaRepozitorij tekmaRepozitorij, LastnistvoStoritev lastnistvo,
                          SkrivnostListka skrivnost) {
        this.tekmaRepozitorij = tekmaRepozitorij;
        this.lastnistvo = lastnistvo;
        this.skrivnost = skrivnost;
    }

    /* Vsebina QR kode za listek vsake tekme dogodka (id tekme -> »TK:<id>:<skrivnost>«).
       Skrivnost da sodniku pravico poslati izid (S1/S2), zato jo dobi samo
       organizator (lastnik) turnirja ali admin - javni podatki tekme je nimajo. */
    @Transactional(readOnly = true)
    public java.util.Map<Long, String> kode(Long idDogodka) {
        lastnistvo.preveriTurnirPoDogodku(idDogodka);
        java.util.Map<Long, String> kode = new java.util.LinkedHashMap<>();
        for (Long id : tekmaRepozitorij.idjiZaDogodek(idDogodka)) {
            kode.put(id, skrivnost.vsebinaQr(id));
        }
        return kode;
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
