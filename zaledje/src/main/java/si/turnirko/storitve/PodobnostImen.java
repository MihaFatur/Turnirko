/* Pravila, po katerih se vpisano ime igralca ujema z obstojecim (IgralciStoritev.
   najdiPodobne). Namen je opozoriti na dvojnika PRED vpisom, ne ga prepreciti:
   dva zapisa za isto osebo razbijeta zgodovino tekem in ratinga na dva dela,
   isto ime pa imata lahko tudi dve razlicni osebi (Novak, Horvat), zato odlocitev
   ostane cloveku.

   Pravila so namenoma ozka. Opozorilo, ki se pokaze prepogosto, se preneha
   brati - zato se razlikuje najvec ENA beseda, in to samo pri daljsih besedah
   (»Tina« proti »Nina« je ena crka, a sta to dve osebi). */
package si.turnirko.storitve;

import java.time.LocalDate;

import si.turnirko.modeli.PrimerjavaDatuma;
import si.turnirko.modeli.Spol;
import si.turnirko.modeli.UjemanjeImena;

final class PodobnostImen {

    /* Od te dolzine (krajse od besed) naprej se ena crka razlike steje za
       tipkarsko napako, od druge pa dve. Krajse besede se ujemajo samo, ce so
       enake: pri stirih crkah ena razlika pogosteje pomeni drugo ime (Tina,
       Nina) kot napako. */
    static final int ENA_NAPAKA_OD = 5;
    static final int DVE_NAPAKI_OD = 9;

    /* Krajsa oblika imena (»Miha« / »Mihael«): krajse ime ima vsaj toliko
       crk, daljse pa od njega vec najvec toliko. »Jan« / »Janja« tako ni
       kandidat. */
    static final int NAJKRAJSA_KRATKA_OBLIKA = 4;
    static final int NAJVEC_DODANIH_CRK = 3;

    private PodobnostImen() {}

    /* Ime in priimek v primerljivi obliki (glej jedro). */
    record Ime(String ime, String priimek) {

        static Ime iz(String ime, String priimek) {
            return new Ime(jedro(ime), jedro(priimek));
        }

        boolean jePrazno() {
            return ime.isEmpty() || priimek.isEmpty();
        }
    }

    /* Mala crka brez sumnikov, locila (vezaj, pika, apostrof) so presledek:
       »Novak-Kos« in »Novak  Kos« sta ista beseda, »Kovač« in »Kovac« tudi.
       Ista normalizacija kot pri povezavi racuna z igralcem
       (RacuniStoritev.normaliziraj), dopolnjena z »đ«, ki ga razclenitev
       Unicode ne razstavi. */
    static String jedro(String vrednost) {
        return RacuniStoritev.normaliziraj(vrednost)
                .replace('đ', 'd')
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
    }

    /* Kako se vpis ujema z obstojecim igralcem; null, kadar se ne.

       Spol je neobvezen in ime zozi samo, kadar sta znana OBA in se
       razlikujeta: »Matej« in »Mateja« sta dve osebi, ena crka razlike pa bi ju
       sicer zdruzila. Pri enakem ali neznanem spolu ostane ujemanje, kakrsno je
       - napacno vpisan spol tako dvojnika ne skrije (isto ime se ujema vedno).
       Priimek spola ne pozna: slovenski priimki se po spolu ne spreminjajo. */
    static UjemanjeImena ujemanje(Ime vpis, Spol spolVpisa, Ime obstojec, Spol spolObstojecega) {
        if (vpis.jePrazno() || obstojec.jePrazno()) {
            return null;
        }
        boolean enakSpol = spolVpisa == null || spolObstojecega == null || spolVpisa == spolObstojecega;

        int ime = enakSpol ? razlikaImena(vpis.ime(), obstojec.ime())
                : (vpis.ime().equals(obstojec.ime()) ? 0 : -1);
        int priimek = razlika(vpis.priimek(), obstojec.priimek());
        if (ime >= 0 && priimek >= 0 && ime + priimek <= 1) {
            return ime + priimek == 0 ? UjemanjeImena.ISTO : UjemanjeImena.PODOBNO;
        }

        int imeKotPriimek = razlika(vpis.ime(), obstojec.priimek());
        int priimekKotIme = razlika(vpis.priimek(), obstojec.ime());
        if (imeKotPriimek >= 0 && priimekKotIme >= 0 && imeKotPriimek + priimekKotIme <= 1) {
            return UjemanjeImena.OBRNJENO;
        }
        return null;
    }

    /* 0 = besedi sta enaki, 1 = podobni, -1 = razlicni. */
    private static int razlika(String a, String b) {
        if (a.equals(b)) {
            return 0;
        }
        return razdalja(a, b) <= dovoljenaRazdalja(a, b) || jePredponaBesed(a, b) ? 1 : -1;
    }

    /* Isto za ime: poleg tipkarske napake tudi krajsa oblika (»Miha« / »Mihael«). */
    private static int razlikaImena(String a, String b) {
        int razlika = razlika(a, b);
        return razlika == -1 && jeKratkaOblika(a, b) ? 1 : razlika;
    }

    private static int dovoljenaRazdalja(String a, String b) {
        int dolzina = Math.min(a.length(), b.length());
        if (dolzina >= DVE_NAPAKI_OD) {
            return 2;
        }
        return dolzina >= ENA_NAPAKA_OD ? 1 : 0;
    }

    /* »Ana« / »Ana Marija«, »Novak« / »Novak Kos«: ena je cela zacetna beseda
       druge (dve imeni, dvojni priimek, priimek po poroki). */
    private static boolean jePredponaBesed(String a, String b) {
        return b.startsWith(a + " ") || a.startsWith(b + " ");
    }

    private static boolean jeKratkaOblika(String a, String b) {
        boolean aKrajsa = a.length() <= b.length();
        String krajsa = aKrajsa ? a : b;
        String daljsa = aKrajsa ? b : a;
        int dodanih = daljsa.length() - krajsa.length();
        return krajsa.length() >= NAJKRAJSA_KRATKA_OBLIKA
                && dodanih >= 1 && dodanih <= NAJVEC_DODANIH_CRK
                && daljsa.startsWith(krajsa);
    }

    /* Urejevalna razdalja z zamenjavo sosednjih crk (»Marjia« / »Marija« je
       ena napaka, ne dve): najmanj vstavitev, brisanj, zamenjav in
       zamenjav sosednjih crk, ki spremenijo a v b. */
    static int razdalja(String a, String b) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            d[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int strosek = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1),
                        d[i - 1][j - 1] + strosek);
                if (i > 1 && j > 1
                        && a.charAt(i - 1) == b.charAt(j - 2)
                        && a.charAt(i - 2) == b.charAt(j - 1)) {
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
                }
            }
        }
        return d[a.length()][b.length()];
    }

    /* Datum vpisa proti datumu obstojecega igralca (samo za administratorja). */
    static PrimerjavaDatuma primerjajDatum(LocalDate vpis, LocalDate obstojec) {
        if (vpis.equals(obstojec)) {
            return PrimerjavaDatuma.ENAK;
        }
        int razlicnih = (vpis.getYear() != obstojec.getYear() ? 1 : 0)
                + (vpis.getMonthValue() != obstojec.getMonthValue() ? 1 : 0)
                + (vpis.getDayOfMonth() != obstojec.getDayOfMonth() ? 1 : 0);
        boolean dnevInMesecZamenjana = vpis.getYear() == obstojec.getYear()
                && vpis.getDayOfMonth() == obstojec.getMonthValue()
                && vpis.getMonthValue() == obstojec.getDayOfMonth();
        return razlicnih == 1 || dnevInMesecZamenjana
                ? PrimerjavaDatuma.PODOBEN : PrimerjavaDatuma.DRUG;
    }
}
