/* Branje in pisanje zunanjih povezav s Stupo. Tekmovanje, dogodek, ekipa lige
   in srecanje lige ob ponovnem uvozu obdrzijo svoj id - na njih kazejo naslovi
   strani (/turnirji/12, /srecanja/345), ki jih ljudje delijo. */
package si.turnirko.uvoz.stupa;

import java.util.Optional;

import si.turnirko.modeli.VirTekmovanja;
import si.turnirko.modeli.ZunanjaPovezava;
import si.turnirko.repozitoriji.ZunanjaPovezavaRepozitorij;

final class PovezaveStupe {

    private PovezaveStupe() {}

    static Optional<Long> lokalni(ZunanjaPovezavaRepozitorij povezave, ZunanjaPovezava.Vrsta vrsta, Object zunanji) {
        return povezave.findByVirAndVrstaAndZunanjiId(VirTekmovanja.STUPA, vrsta, String.valueOf(zunanji))
                .map(ZunanjaPovezava::getIdLokalni);
    }

    static void povezi(ZunanjaPovezavaRepozitorij povezave, ZunanjaPovezava.Vrsta vrsta, Object zunanji, Long lokalni) {
        Optional<ZunanjaPovezava> obstojeca =
                povezave.findByVirAndVrstaAndZunanjiId(VirTekmovanja.STUPA, vrsta, String.valueOf(zunanji));
        if (obstojeca.isEmpty()) {
            povezave.save(new ZunanjaPovezava(VirTekmovanja.STUPA, vrsta, String.valueOf(zunanji), lokalni));
        } else if (!obstojeca.get().getIdLokalni().equals(lokalni)) {
            obstojeca.get().setIdLokalni(lokalni);
            povezave.save(obstojeca.get());
        }
    }
}
