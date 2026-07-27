/* Napredovanje po mrezi: ko se tekma konca, vpise zmagovalca (oz. porazenca,
   npr. za tekmo za 3. mesto) v tekme, ki se nanjo sklicujejo prek
   eksplicitnih povezav idIzvorTekma1/2.

   Vpis v slot je izveden z ATOMARNIM pogojnim UPDATE stavkom v bazi:
   ce se dve sosednji tekmi koncata socasno, obe vpisujeta v isto tekmo
   naslednjega kola in navadno "preberi-spremeni-shrani" bi lahko en vpis
   tiho izgubilo - pogojni UPDATE tega ne dopusca. */
package si.turnirko.storitve;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.Tekma;
import si.turnirko.modeli.VlogaIzvora;
import si.turnirko.repozitoriji.TekmaRepozitorij;

@Service
public class NapredovanjeStoritev {

    private final TekmaRepozitorij tekmaRepozitorij;

    public NapredovanjeStoritev(TekmaRepozitorij tekmaRepozitorij) {
        this.tekmaRepozitorij = tekmaRepozitorij;
    }

    /* Razsiri izid koncane tekme v vse tekme, ki so od nje odvisne. */
    @Transactional
    public void razsiriIzKoncane(Tekma koncana) {
        // zmagovalca in porazenca dolocimo, se preden se lotimo posodobitev
        Prijava zmagovalec = koncana.getZmagovalec();
        Prijava porazenec = koncana.porazenec();

        List<Tekma> odvisne = tekmaRepozitorij.najdiOdvisne(koncana.getId());

        for (Tekma odvisna : odvisne) {
            if (koncana.getId().equals(odvisna.getIdIzvorTekma1())) {
                Prijava udelezenec = izberi(zmagovalec, porazenec, odvisna.getVlogaIzvora1());
                if (udelezenec != null) {
                    tekmaRepozitorij.vpisiVSlot1(odvisna.getId(), udelezenec);
                }
            }
            if (koncana.getId().equals(odvisna.getIdIzvorTekma2())) {
                Prijava udelezenec = izberi(zmagovalec, porazenec, odvisna.getVlogaIzvora2());
                if (udelezenec != null) {
                    tekmaRepozitorij.vpisiVSlot2(odvisna.getId(), udelezenec);
                }
            }
            // ce sta zdaj znana oba udelezenca, tekma postane PRIPRAVLJENA
            tekmaRepozitorij.oznaciPripravljenoCeStaOba(odvisna.getId());
        }
    }

    private Prijava izberi(Prijava zmagovalec, Prijava porazenec, VlogaIzvora vloga) {
        return vloga == VlogaIzvora.PORAZENEC ? porazenec : zmagovalec;
    }
}
