/* Katera tekma turnirja je na listku, kadar kode ni mogoce prebrati - po imenih,
   ki jih je prebral bralnik.

   To je tretja pot (za QR kodo in natisnjeno stevilko): prazen listek, na
   katerega je sodnik imena napisal z roko, ali fotografija z odrezanim
   vogalom. Kandidati so samo tekme, ki cakajo na izid, zato jih je malo.

   Pravila so namenoma previdna, ker napacna tekma pomeni napacen izid v zrebu:
   - Priimek odloca, ime samo potrdi (sodnik pogosto napise »Novak A.« ali samo
     priimek). Ime se ujema tudi z zacetnico.
   - Rocni zapis dovoli eno crko razlike pri daljsem priimku (»Kovacic« /
     »Kovacis«), ne pri kratkem (»Kos« / »Kot« sta dva igralca).
   - Tekma je izbrana sama samo, kadar se priimka ujemata na OBEH straneh in je
     druga najboljsa tekma ocitno slabsa. Sicer izbere organizator s seznama.
   - Strani sta lahko zamenjani (»obrnjeno«): sodnik je imeni napisal v drugem
     vrstnem redu; tocke gredo tedaj na drugo stran tekme.
   Kategorija z listka (»U15 decki«) samo loci isti par v dveh dogodkih. */
package si.turnirko.storitve;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public final class UjemanjeTekmeListka {

    /* Ocena, pri kateri se priimka ujemata na obeh straneh (glej ocenaOsebe). */
    static final double PRAG_IZBIRE = 0.7;
    /* Za koliko mora biti najboljsa tekma boljsa od druge, da je izbrana sama. */
    static final double PREDNOST = 0.2;
    /* Kandidati pod to oceno se organizatorju ne ponudijo. */
    static final double PRAG_PONUDBE = 0.3;
    static final int NAJVEC_KANDIDATOV = 5;
    private static final double BONUS_KATEGORIJE = 0.05;

    private UjemanjeTekmeListka() {}

    public record Oseba(String ime, String priimek) {}

    /* Tekma, ki caka na izid: osebe vsake strani (par ima dve) in ime dogodka. */
    public record Kandidat(Long idTekme, List<Oseba> stran1, List<Oseba> stran2, String dogodek) {}

    /* ocena = slabsa od obeh strani (odloca o samodejni izbiri); povprecje =
       obe strani skupaj (odloca o ponudbi: ena berljiva stran zadosca, da
       organizator tekmo vidi med kandidati). */
    public record Zadetek(Long idTekme, boolean obrnjeno, double ocena, double povprecje) {}

    /* Izid iskanja: izbrana tekma (ali null) in kandidati za organizatorja. */
    public record Izid(Zadetek izbrana, List<Zadetek> kandidati) {}

    public static Izid najdi(String imeLevo, String imeDesno, String kategorija, List<Kandidat> kandidati) {
        List<Zadetek> ocenjeni = new ArrayList<>();
        for (Kandidat k : kandidati) {
            double levo1 = ocena(imeLevo, k.stran1());
            double desno2 = ocena(imeDesno, k.stran2());
            double levo2 = ocena(imeLevo, k.stran2());
            double desno1 = ocena(imeDesno, k.stran1());
            double naravnost = levo1 + desno2;
            double obrat = levo2 + desno1;
            boolean obrnjeno = obrat > naravnost;
            double ocena = obrnjeno ? Math.min(levo2, desno1) : Math.min(levo1, desno2);
            double povprecje = Math.max(naravnost, obrat) / 2;
            if (kategorija != null && ujemaKategorijo(kategorija, k.dogodek())) {
                ocena += BONUS_KATEGORIJE;
                povprecje += BONUS_KATEGORIJE;
            }
            if (povprecje >= PRAG_PONUDBE) {
                ocenjeni.add(new Zadetek(k.idTekme(), obrnjeno, ocena, povprecje));
            }
        }
        ocenjeni.sort(Comparator.comparingDouble(Zadetek::ocena).reversed()
                .thenComparing(Comparator.comparingDouble(Zadetek::povprecje).reversed())
                .thenComparing(Zadetek::idTekme));
        List<Zadetek> ponujeni = ocenjeni.subList(0, Math.min(NAJVEC_KANDIDATOV, ocenjeni.size()));

        Zadetek izbrana = null;
        if (!ocenjeni.isEmpty() && ocenjeni.getFirst().ocena() >= PRAG_IZBIRE) {
            double druga = ocenjeni.size() > 1 ? ocenjeni.get(1).ocena() : 0;
            if (ocenjeni.getFirst().ocena() - druga >= PREDNOST) {
                izbrana = ocenjeni.getFirst();
            }
        }
        return new Izid(izbrana, List.copyOf(ponujeni));
    }

    /* Kako dobro zapis z listka opise stran tekme (0 do 1); par mora imeti
       oba igralca. */
    static double ocena(String zapis, List<Oseba> osebe) {
        if (zapis == null || zapis.isBlank() || osebe.isEmpty()) {
            return 0;
        }
        List<String> besede = besede(zapis);
        double vsota = 0;
        for (Oseba oseba : osebe) {
            vsota += ocenaOsebe(besede, oseba);
        }
        return vsota / osebe.size();
    }

    /* Priimek 0,7 (vse njegove besede so v zapisu), ime 0,3 (beseda ali zacetnica). */
    private static double ocenaOsebe(List<String> besede, Oseba oseba) {
        List<String> priimek = besede(oseba.priimek());
        List<String> ime = besede(oseba.ime());
        boolean priimekOk = !priimek.isEmpty()
                && priimek.stream().allMatch(p -> besede.stream().anyMatch(b -> podobna(b, p)));
        boolean imeOk = !ime.isEmpty() && besede.stream().anyMatch(b ->
                ime.stream().anyMatch(i -> podobna(b, i) || (b.length() == 1 && i.charAt(0) == b.charAt(0))));
        return (priimekOk ? 0.7 : 0) + (imeOk ? 0.3 : 0);
    }

    private static boolean ujemaKategorijo(String kategorija, String dogodek) {
        if (dogodek == null) {
            return false;
        }
        List<String> zDogodka = besede(dogodek);
        List<String> zListka = besede(kategorija);
        return !zListka.isEmpty() && zListka.stream().allMatch(zDogodka::contains);
    }

    private static List<String> besede(String vrednost) {
        if (vrednost == null) {
            return List.of();
        }
        String jedro = PodobnostImen.jedro(vrednost);
        return jedro.isEmpty() ? List.of() : Arrays.asList(jedro.split(" "));
    }

    /* Enaki besedi ali ena crka razlike pri besedi s 5 ali vec crkami. */
    static boolean podobna(String a, String b) {
        if (a.equals(b)) {
            return true;
        }
        if (Math.min(a.length(), b.length()) < PodobnostImen.ENA_NAPAKA_OD
                || Math.abs(a.length() - b.length()) > 1) {
            return false;
        }
        return razdalja(a, b) <= 1;
    }

    private static int razdalja(String a, String b) {
        int[] prejsnja = new int[b.length() + 1];
        int[] trenutna = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prejsnja[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            trenutna[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int zamenjava = prejsnja[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                trenutna[j] = Math.min(zamenjava, Math.min(prejsnja[j] + 1, trenutna[j - 1] + 1));
            }
            int[] t = prejsnja;
            prejsnja = trenutna;
            trenutna = t;
        }
        return prejsnja[b.length()];
    }
}
