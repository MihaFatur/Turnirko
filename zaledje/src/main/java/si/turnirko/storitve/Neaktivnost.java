/* Pravilo odbitka za neaktivnost - cista funkcija casa, brez baze.

   Kdor dolgo ne igra, se praviloma poslabsa, njegova stevilka pa ostane taka,
   kot je bila na zadnji tekmi. Nemski TTR to resuje z odbitkom; mi enako, le
   mnogo manjsim: izmerjeno na 91.741 tekmah odsotnost res znizuje moc, a za
   okrog 10-30 tock, ne za 80.

   Odbitki so KUMULATIVNI po stopnjah in se ustavijo - po dveh letih se ne
   odbija vec. Igralec, ki se ne vrne, tako ne tone v neskoncnost; s lestvice
   ga tako ali tako umakne pravilo 18 mesecev.

   Vsak odbitek ima datum, ko ZAPADE (zadnji podatek + 6/12/24 mesecev), in
   ne datuma, ko ga je kdo vpisal - le tako ga zna ponovni preracun postaviti
   na isto mesto v casovno vrsto.

   "Zadnji podatek" in ne "zadnja tekma": od V25 je drugi vir svezine zunanja
   uvrstitev (glej RatingStanje.svezOb). Odbiti tocke stevilki, ki je bila
   pravkar prepisana z zunanje lestvice, bi pomenilo dvakrat placati isto
   odsotnost - zunanja lestvica jo namrec ze uposteva. */
package si.turnirko.storitve;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class Neaktivnost {

    /* Stopnje odbitka: po koliko mesecih in koliko tock se takrat odbije.
       Skupaj torej -10, -25 in -40. */
    private static final int[] MESECI = { 6, 12, 24 };
    private static final int[] ODBITEK = { 10, 15, 15 };

    /* Po koliko mesecih brez tekme igralec izgine z JAVNE lestvice. Njegova
       stevilka ostane - na profilu in v zgodovini je se vedno vse vidno. */
    public static final int MESECEV_DO_SKRITJA = 18;

    /* En zapadel odbitek. */
    public record Odbitek(LocalDateTime velja, int tock) {}

    private Neaktivnost() {}

    /* Odbitki, ki zapadejo po zadnji tekmi do vkljucno danega trenutka.
       preskoci pove, koliko stopenj je ze uveljavljenih (da se isti odbitek ne
       zapise dvakrat). */
    public static List<Odbitek> zapadli(LocalDateTime zadnjaTekma, LocalDateTime doKdaj,
                                        int preskoci) {
        List<Odbitek> odbitki = new ArrayList<>();
        if (zadnjaTekma == null || doKdaj == null) {
            return odbitki;
        }
        for (int stopnja = preskoci; stopnja < MESECI.length; stopnja++) {
            LocalDateTime zapade = zadnjaTekma.plusMonths(MESECI[stopnja]);
            if (zapade.isAfter(doKdaj)) {
                break; // stopnje so narascajoce, naslednje so se dlje
            }
            odbitki.add(new Odbitek(zapade, ODBITEK[stopnja]));
        }
        return odbitki;
    }

    /* Koliko stopenj je skupaj. */
    public static int stopenj() {
        return MESECI.length;
    }

    /* Ali je igralec s tem casom zadnjega podatka o njem se na javni
       lestvici. Klicalci podajo RatingStanje.svezOb - torej poznejsega od
       zadnje tekme in zadnje zunanje uvrstitve: oboje je podatek o igralcu,
       lestvica pa sprasuje, ali je stevilka se sveza.

       Skrijemo samo tistega, o katerem VEMO, da dolgo ne igra. Kadar termina
       ni (igralec je odigral samo tekme brez znanega datuma ali pa se ni igral
       nicesar), ostane na lestvici: iz nevednosti ne smemo sklepati na
       neaktivnost - sicer bi igralec s stotimi tekmami brez termina izginil,
       kot da ga ni bilo. */
    public static boolean naJavniLestvici(LocalDateTime zadnjiPodatek, LocalDateTime zdaj) {
        return zadnjiPodatek == null
                || !zadnjiPodatek.plusMonths(MESECEV_DO_SKRITJA).isBefore(zdaj);
    }
}
