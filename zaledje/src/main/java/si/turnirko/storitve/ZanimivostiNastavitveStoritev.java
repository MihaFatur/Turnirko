/* Nastavitev zavihka »Zanimivosti«: kateri razdelki so gledalcem vidni.

   Skrivanje ne briše podatkov - StatistikaTekmovanjaStoritev razdelek samo
   izpusti iz izpisa, organizator ga lahko kadarkoli spet vklopi. Seznam
   razdelkov je odvisen od tekmovanja (liga nima prvega naslova, turnir nima
   srečanj na nož), zato zavrnemo razdelek, ki na tem tekmovanju ne nastane.

   Branje je javno kot ostali pogledi tekmovanja. Pisanje sme lastnik in admin,
   tudi pri uvoženem tekmovanju: zavihek je prikaz, ne pravilo tekmovanja. */
package si.turnirko.storitve;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.ZanimivostiNastavitveDto;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.modeli.Liga;
import si.turnirko.modeli.PostavkaZanimivosti;
import si.turnirko.modeli.Turnir;
import si.turnirko.repozitoriji.LigaRepozitorij;
import si.turnirko.repozitoriji.TurnirRepozitorij;

@Service
public class ZanimivostiNastavitveStoritev {

    private final TurnirRepozitorij turnirRepozitorij;
    private final LigaRepozitorij ligaRepozitorij;
    private final LastnistvoStoritev lastnistvoStoritev;

    public ZanimivostiNastavitveStoritev(TurnirRepozitorij turnirRepozitorij,
                                         LigaRepozitorij ligaRepozitorij,
                                         LastnistvoStoritev lastnistvoStoritev) {
        this.turnirRepozitorij = turnirRepozitorij;
        this.ligaRepozitorij = ligaRepozitorij;
        this.lastnistvoStoritev = lastnistvoStoritev;
    }

    @Transactional(readOnly = true)
    public ZanimivostiNastavitveDto zaTurnir(Long idTurnir) {
        Turnir turnir = najdiTurnir(idTurnir);
        return dto(false, turnir.getRaven().steje(), turnir.getSkriteZanimivosti());
    }

    @Transactional(readOnly = true)
    public ZanimivostiNastavitveDto zaLigo(Long idLiga) {
        Liga liga = najdiLigo(idLiga);
        return dto(true, liga.getRaven().steje(), liga.getSkriteZanimivosti());
    }

    /* Nabor je celoten: kar ni na seznamu, je vidno. Razdelek, ki na tekmovanju
       ne nastane, zavrnemo, ne tiho izpustimo - sicer bi organizator mislil, da
       je nekaj skrito, kar na njegovem tekmovanju sploh ni. */
    @Transactional
    public ZanimivostiNastavitveDto shraniZaTurnir(Long idTurnir, List<PostavkaZanimivosti> skrite) {
        lastnistvoStoritev.preveriTurnirZaPrikazPoId(idTurnir);
        Turnir turnir = najdiTurnir(idTurnir);
        List<PostavkaZanimivosti> izbrane = skrite == null ? List.of() : skrite;
        preveriRazdelke(izbrane, PostavkaZanimivosti.zaTekmovanje(false, turnir.getRaven().steje()));
        turnir.setSkriteZanimivosti(izbrane);
        return dto(false, turnir.getRaven().steje(), turnir.getSkriteZanimivosti());
    }

    @Transactional
    public ZanimivostiNastavitveDto shraniZaLigo(Long idLiga, List<PostavkaZanimivosti> skrite) {
        lastnistvoStoritev.preveriLigaZaPrikazPoId(idLiga);
        Liga liga = najdiLigo(idLiga);
        List<PostavkaZanimivosti> izbrane = skrite == null ? List.of() : skrite;
        preveriRazdelke(izbrane, PostavkaZanimivosti.zaTekmovanje(true, liga.getRaven().steje()));
        liga.setSkriteZanimivosti(izbrane);
        return dto(true, liga.getRaven().steje(), liga.getSkriteZanimivosti());
    }

    private Turnir najdiTurnir(Long idTurnir) {
        return turnirRepozitorij.findById(idTurnir)
                .orElseThrow(() -> new NiNajdenoIzjema("Turnir z id " + idTurnir + " ne obstaja."));
    }

    private Liga najdiLigo(Long idLiga) {
        return ligaRepozitorij.findById(idLiga)
                .orElseThrow(() -> new NiNajdenoIzjema("Liga z id " + idLiga + " ne obstaja."));
    }

    private static void preveriRazdelke(List<PostavkaZanimivosti> skrite,
                                        List<PostavkaZanimivosti> mozni) {
        for (PostavkaZanimivosti p : skrite) {
            if (!mozni.contains(p)) {
                throw new NeveljavenVnosIzjema("Razdelek " + p + " na tem tekmovanju ni.");
            }
        }
    }

    /* Razdelki, ki na tekmovanju nastanejo, in med njimi skriti - ta del se
       ponudi organizatorju v obrazcu. Skritih, ki na tekmovanju ne nastanejo
       (ostanek stare nastavitve), v odgovor ne damo. */
    private static ZanimivostiNastavitveDto dto(boolean jeLiga, boolean stejeVElo,
                                                Set<PostavkaZanimivosti> skrite) {
        List<PostavkaZanimivosti> razdelki = PostavkaZanimivosti.zaTekmovanje(jeLiga, stejeVElo);
        return new ZanimivostiNastavitveDto(razdelki,
                razdelki.stream().filter(skrite::contains).toList());
    }
}
