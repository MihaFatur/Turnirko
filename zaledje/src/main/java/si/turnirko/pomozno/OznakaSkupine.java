/* Oznaka skupine po vrsti.

   Skupine se oštevilčijo od 1 do števila skupin: »1«, »2«, »3« … (odločitev
   lastnika, 23. 11. 2026; prej so bile črke). Za ljudi to pomeni »Skupina 1«,
   »Skupina 2«; nosilec N je v skupini N, zmagovalec skupine 1 je 1. nosilec
   izločilnega dela.

   Oznaka je besedilo, zato bi ORDER BY oznaka dal »10« pred »2«. Vrstni red
   skupin zato določa `SkupinaRepozitorij.findByDogodekIdOrderByOznakaAsc`
   (najprej krajša oznaka, nato abecedno) in vmesnik pri razvrščanju uporablja
   numerično primerjavo (`localeCompare(…, { numeric: true })`).

   Že zapisane skupine ostanejo, kot so (A, B, C …): migracije, ki bi
   preimenovala oznake obstoječih dogodkov, ni. Vrstni red po (dolžina, oznaka)
   jih razvrsti pravilno tudi, ko so v bazi hkrati črke in številke.

   Izjema je SV regija: njena »Skupina 1, 2 …« je nivo, zato bi številke znotraj
   nje (»Skupina 1, skupina 1«) zamenjale pomen. Tam ostanejo črke po slovenski
   abecedi brez Č, Š, Ž (A … P, R, S, T, U, V, Z; `crkaPoVrsti`). */
package si.turnirko.pomozno;

import si.turnirko.izjeme.DomenskaIzjema;

public final class OznakaSkupine {

    /* Stolpec skupina.oznaka dovoli 3 znake. */
    public static final int NAJVEC = 999;

    public static final String ABECEDA = "ABCDEFGHIJKLMNOPRSTUVZ";

    /* Črk je 22; z Z pred črko (ZA, ZB …) jih je 66 (največ 3 znaki). */
    public static final int NAJVEC_CRK = 3 * ABECEDA.length();

    private OznakaSkupine() {}

    /* Oznaka skupine z danim indeksom: 0 -> »1«, 1 -> »2« … */
    public static String zaIndeks(int indeks) {
        if (indeks < 0) {
            throw new IllegalArgumentException("Indeks skupine ne more biti negativen: " + indeks);
        }
        if (indeks >= NAJVEC) {
            throw new DomenskaIzjema("Skupin je preveč: največ jih je " + NAJVEC + ".");
        }
        return String.valueOf(indeks + 1);
    }

    /* Črka skupine po vrsti (samo SV regija): 0 -> »A«, 15 -> »P«, 16 -> »R«.
       Ko črk zmanjka, se pred črko doda Z (ZA, ZB … ZZ, ZZA …), ker se tako
       abecedni vrstni red ujema z vrstnim redom skupin; dvočrkovni AA bi stal
       pred B. */
    public static String crkaPoVrsti(int indeks) {
        if (indeks < 0) {
            throw new IllegalArgumentException("Indeks skupine ne more biti negativen: " + indeks);
        }
        if (indeks >= NAJVEC_CRK) {
            throw new DomenskaIzjema("Skupin je preveč: največ jih je " + NAJVEC_CRK + ".");
        }
        int dolzina = ABECEDA.length();
        StringBuilder oznaka = new StringBuilder();
        while (indeks >= dolzina) {
            oznaka.append('Z');
            indeks -= dolzina;
        }
        return oznaka.append(ABECEDA.charAt(indeks)).toString();
    }
}
