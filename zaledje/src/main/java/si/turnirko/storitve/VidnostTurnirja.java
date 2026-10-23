/* Kateri turnirji stojijo na seznamu turnirjev (in na domaci strani, ki bere
   isti seznam). Koledar sezone prinese turnirje za vse leto naprej (oktober
   2026: ~50 iz koledarja NTZS in NTK Savinja), seznam pa bi se napolnil s
   praznimi prihodnjimi turnirji, ki gledalcu se nic ne povedo. Zato gost in
   igralec turnirja V PRIPRAVI ne vidita, dokler je do njega vec kot teden dni
   (odlocitev lastnika, 23. 10. 2026). Admin in organizator vidita vse - turnirje
   pripravljata in morata jih najti.

   Skrit ni zaklenjen: stran turnirja ostane dosegljiva po povezavi, koledar pa
   prihodnje turnirje kaze naprej (tam so prav prihodnji dogodki namen).
   "V pripravi" je stanje, kot ga vidi gledalec (StatusTekmovanja.zaPrikaz):
   tudi turnir z zrebom pred datumom. Turnir brez datuma ostane - iz nevednosti
   ne sklepamo, da je dalec. */
package si.turnirko.storitve;

import java.time.LocalDate;
import java.time.ZoneId;

import si.turnirko.modeli.StatusTekmovanja;
import si.turnirko.modeli.Turnir;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;

public final class VidnostTurnirja {

    /* Koliko dni pred zacetkom se turnir v pripravi pokaze vsem. */
    public static final int DNI_VNAPREJ = 7;

    private static final ZoneId SLOVENIJA = ZoneId.of("Europe/Ljubljana");

    private VidnostTurnirja() {}

    public static boolean vidiVse(Uporabnik jaz) {
        return jaz != null && (jaz.getVloga() == Vloga.ADMIN || jaz.getVloga() == Vloga.ORGANIZATOR);
    }

    public static boolean naSeznamu(Turnir turnir, Uporabnik jaz) {
        return vidiVse(jaz) || !predalec(turnir, LocalDate.now(SLOVENIJA));
    }

    /* Turnir v pripravi, ki se zacne vec kot DNI_VNAPREJ dni po danasnjem. */
    static boolean predalec(Turnir turnir, LocalDate danes) {
        LocalDate zacetek = turnir.getDatumZacetka();
        return zacetek != null
                && StatusTekmovanja.zaPrikaz(turnir.getStatus(), zacetek, danes) == StatusTekmovanja.PRIPRAVA
                && zacetek.isAfter(danes.plusDays(DNI_VNAPREJ));
    }
}
