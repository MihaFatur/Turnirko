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
       - starostni pas U21 ali mlajsi). Cene od 21. 10. 2026 (odlocitev
       lastnika; prej 3,99/4,99 in letno 42,99/53,49). Mesecna cena mora
       pokriti Stripovo fiksno provizijo 0,25 EUR na placilo - zato ne pod
       1,99 (pri 0,99 je Stripe pobral ~27 %). */
    static final double PREMIUM_MESECNO_MLAJSI = 1.99;
    static final double PREMIUM_MESECNO_STAREJSI = 2.99;

    /* Letna narocnina je FIKSNA cena (odlocitev lastnika), ne izracun: ~58 %
       oz. ~44 % ceneje kot dvanajst mesecnih (23,88 -> 9,99; 35,88 -> 19,99).
       Vmesnik oznako prihranka izpelje iz prikazanih cen (api/tipi.ts) in jo
       zaokrozi navzdol, da ne obljubi vec, kot kupec prihrani. */
    static final double PREMIUM_LETNO_MLAJSI = 9.99;
    static final double PREMIUM_LETNO_STAREJSI = 19.99;

    /* Navijac (V58) je SAMO letni: pri mesecni ceni okoli 1,49-1,99 EUR bi Stripe
       pobral 15-19 % (0,25 EUR + 2,2 %) - isti razlog kot spodnja meja pri Premium.
       Cena je ena za vse: starsi ni igralec, zato starostnega pasu ni. */
    static final double NAVIJAC_LETNO = 19.99;

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
            case NAVIJAC -> cenaNavijaca(ciklus);
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

    private double cenaNavijaca(CiklusPlacila ciklus) {
        if (ciklus != CiklusPlacila.LETNO) {
            throw new NeveljavenVnosIzjema("Paket Navijac je na voljo samo letno.");
        }
        return NAVIJAC_LETNO;
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
