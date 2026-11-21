/* Trenutna pogostost izidov nizov za branje listkov (F2.1). Izracuna se ob
   zagonu in vsako noc (OpravilaBranjaListkov) z eno poizvedbo GROUP BY nad
   tabelo niz; do prvega izracuna in v bazi z malo nizi (nova namestitev) velja
   vgrajena porazdelitev (PogostostNizov.VGRAJENA).

   beta pove, koliko zgodovina steje (0 = nic, 1 = polno); nastavi se z
   turnirko.branje-listkov.beta in se umeri na zlatem naboru (F1). */
package si.turnirko.storitve;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.branje.PogostostNizov;
import si.turnirko.repozitoriji.NizRepozitorij;

@Component
public class PogostostNizovStoritev {

    private static final Logger log = LoggerFactory.getLogger(PogostostNizovStoritev.class);

    /* Najmanj veljavnih nizov, da zgodovina baze zamenja vgrajeno porazdelitev. */
    static final long NAJMANJ_NIZOV = 2000;

    private final NizRepozitorij nizi;
    private final double beta;
    private volatile PogostostNizov trenutna = PogostostNizov.VGRAJENA;

    public PogostostNizovStoritev(NizRepozitorij nizi,
                                  @Value("${turnirko.branje-listkov.beta:" + PogostostNizov.PRIVZETA_BETA + "}") double beta) {
        this.nizi = nizi;
        // tipkarska napaka v nastavitvah ne sme obrniti branja (beta < 0 bi dajala
        // prednost redkim izidom, velika beta bi preglasila pisavo)
        if (!(beta >= 0 && beta <= 1)) {
            log.warn("Branje listkov: turnirko.branje-listkov.beta = {} ni med 0 in 1, velja {}", beta,
                    PogostostNizov.PRIVZETA_BETA);
            beta = PogostostNizov.PRIVZETA_BETA;
        }
        this.beta = beta;
    }

    public PogostostNizov trenutna() {
        return trenutna;
    }

    public double beta() {
        return beta;
    }

    /* Prebere pogostost iz baze; ob premalo nizih ostane vgrajena. */
    @Transactional(readOnly = true)
    public PogostostNizov osvezi() {
        PogostostNizov nova = izVrstic(nizi.pogostostIzidov());
        if (nova.nizov() < NAJMANJ_NIZOV) {
            log.info("Branje listkov: v bazi je {} nizov, pogostost ostane vgrajena", nova.nizov());
            return trenutna;
        }
        trenutna = nova;
        log.info("Branje listkov: pogostost izidov iz {} nizov", nova.nizov());
        return nova;
    }

    /* Vrstice [zmagovalec, porazenec, stevilo] v porazdelitev; neveljavni nizi
       (popravki, ki niso niz po pravilih) se izpustijo. */
    static PogostostNizov izVrstic(List<Object[]> vrstice) {
        long[] stevilo11 = new long[10];
        long[] podaljski = new long[PogostostNizov.NAJVEC_PODALJSEK - 11];
        for (Object[] v : vrstice) {
            int zmagovalec = ((Number) v[0]).intValue();
            int porazenec = ((Number) v[1]).intValue();
            long stevilo = ((Number) v[2]).longValue();
            if (zmagovalec == 11 && porazenec >= 0 && porazenec <= 9) {
                stevilo11[porazenec] += stevilo;
            } else if (zmagovalec >= 12 && zmagovalec <= PogostostNizov.NAJVEC_PODALJSEK
                    && porazenec == zmagovalec - 2) {
                podaljski[zmagovalec - 12] += stevilo;
            }
        }
        return new PogostostNizov(stevilo11, podaljski);
    }
}
