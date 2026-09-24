/* Edino mesto s cenami placilnih paketov. Klice ga ustvarjanje Stripe
   checkout seje (PlacilaStoritev) in vsak predogled cene v vmesniku - dve
   kopiji stevilk bi se sceasoma razsle.

   Cena je LOCENA od trenutne cene v tem razredu, ko je narocnina enkrat
   sklenjena: Stripe checkout uporabi "price_data" (cena je vpisana v
   posamezno narocnino), zato poznejsa sprememba tu ne spremeni ze
   sklenjenih narocnin - njihova cena ostane zapisana v Narocnina.cenaObSklenitvi. */
package si.turnirko.storitve;

import java.time.LocalDate;

import org.springframework.stereotype.Service;

import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.modeli.CiklusPlacila;
import si.turnirko.modeli.Paket;
import si.turnirko.modeli.StarostniPas;

@Service
public class CenikStoritev {

    /* Igralec Premium, mesecna cena po starosti ob registraciji (11. clen PST
       - starostni pas U21 ali mlajsi). */
    static final double PREMIUM_MESECNO_MLAJSI = 3.99;
    static final double PREMIUM_MESECNO_STAREJSI = 4.99;

    /* Letna narocnina je FIKSNA cena (odlocitev lastnika, 24. 9. 2026), ne
       izracun: ~10 % ceneje kot dvanajst mesecnih (47,88 -> 42,99; 59,88 ->
       53,49). Vmesnik pokaze oznako "-10 %" in isti stevilki (api/tipi.ts). */
    static final double PREMIUM_LETNO_MLAJSI = 42.99;
    static final double PREMIUM_LETNO_STAREJSI = 53.49;

    /* Organizatorski paketi so SAMO letni. */
    static final double ORGANIZATOR_BASIC_LETNO = 89.99;
    static final double ORGANIZATOR_PLUS_LETNO = 169.99;
    static final double ORGANIZATOR_PRO_LETNO = 249.99;

    /* Cena izbranega paketa v EUR; 0 za BREZPLACNO. starejsiOd21 se upostevi
       samo pri PREMIUM in je sicer lahko null. */
    public double cena(Paket paket, CiklusPlacila ciklus, Boolean starejsiOd21) {
        return switch (paket) {
            case BREZPLACNO -> 0.0;
            case PREMIUM -> cenaPremium(ciklus, Boolean.TRUE.equals(starejsiOd21));
            case ORGANIZATOR_BASIC -> cenaOrganizatorja(ciklus, ORGANIZATOR_BASIC_LETNO);
            case ORGANIZATOR_PLUS -> cenaOrganizatorja(ciklus, ORGANIZATOR_PLUS_LETNO);
            case ORGANIZATOR_PRO -> cenaOrganizatorja(ciklus, ORGANIZATOR_PRO_LETNO);
        };
    }

    /* Meja med U21-ceno in ceno za starejse: U21 pas (in mladinski pod njim)
       je "mlajsi", CLANI in VETERANI so "starejsi". Isto pravilo (in isti
       datum rojstva, brez povezave z zapisom igralca) uporablja PlacilaStoritev
       ob registraciji IN ob poznejsi nadgradnji obstojecega racuna. */
    public boolean stariEnaindvajset(LocalDate datumRojstva) {
        StarostniPas pas = StarostniPas.izpelji(datumRojstva, LocalDate.now());
        return pas == StarostniPas.CLANI || pas == StarostniPas.VETERANI;
    }

    private double cenaPremium(CiklusPlacila ciklus, boolean starejsiOd21) {
        if (ciklus == null) {
            throw new NeveljavenVnosIzjema("Za Premium izberi mesecno ali letno placevanje.");
        }
        if (ciklus == CiklusPlacila.LETNO) {
            return starejsiOd21 ? PREMIUM_LETNO_STAREJSI : PREMIUM_LETNO_MLAJSI;
        }
        return starejsiOd21 ? PREMIUM_MESECNO_STAREJSI : PREMIUM_MESECNO_MLAJSI;
    }

    private double cenaOrganizatorja(CiklusPlacila ciklus, double letnaCena) {
        if (ciklus != CiklusPlacila.LETNO) {
            throw new NeveljavenVnosIzjema("Organizatorski paketi so na voljo samo letno.");
        }
        return zaokrozi(letnaCena);
    }

    /* Na cente - mnozenje z doubli sicer pusti npr. 43.89000000000001. */
    private static double zaokrozi(double znesek) {
        return Math.round(znesek * 100) / 100.0;
    }
}
