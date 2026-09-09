/* Jakostni vrstni red pred zrebom - in pri sistemu SKUPINE (format TOP) se
   crta reza z izborom najboljsih N.

   Vrstni red odloca vse, kar zreb pocne z jakostjo: kdo je nosilec skupine,
   kdo je 1. nosilec mreze, kdo pade pod crto reza. Vmesnik ga zato izpise in
   pusti urejati, predogled razreza pa dobi od streznika, da mu ni treba
   ponavljati domenskih pravil (koliko jih igra, kako se razrezejo skupine,
   kdaj razrez ni izvedljiv). */
package si.turnirko.dto;

import java.util.List;

public record IzborDto(
        /* Koliko najboljsih igra = stevilo skupin x velikost skupine.
           Kjer izbora ni (igrajo vsi), je enako stevilu prijavljenih. */
        int meja,
        int prijavljenih,
        /* Koliko jih dejansko igra (manj od meje, ce je prijav premalo). */
        int igra,
        /* Katera mesta zajame katera skupina - samo pri formatu TOP, kjer je
           razporeditev zaporedna in torej vnaprej znana. Pri zrebanih
           skupinah je seznam prazen: kdo pride v katero skupino, se odloci
           sele ob zrebu. */
        List<SkupinaPredogledDto> skupine,
        /* Zakaj zreb (se) ni mogoc; null pomeni, da je vse pripravljeno. */
        String zadrzek,
        /* Ali vrstni red odloca tudi o IZBORU (format TOP): kdor je pod crto,
           postane rezerva. Drugod igrajo vsi in vrstni red doloca le nosilce. */
        boolean crtaReza,
        /* Koliko skupin bo sestavil zreb; null pri cisti izlocilni mrezi. */
        Integer steviloSkupin
) {

    /* Ena skupina v predogledu: katera mesta jakostne lestvice zajame. */
    public record SkupinaPredogledDto(String oznaka, int velikost, int odMesta, int doMesta) {}
}
