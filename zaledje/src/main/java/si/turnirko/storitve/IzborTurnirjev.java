/* Kateri turnirji stojijo na domaci strani igralca s Premium - in zakaj.

   Gost in igralec brez paketa vidita najnovejse turnirje. Igralec s Premium
   pa vidi turnirje, ki se ga ticejo, vsak z razlogom, ki ga vmesnik izpise ob
   vrstici (sicer bi bil izbor videti nakljucen):

     1. PRIHAJAJOCI: najblizji, na katerega je ze prijavljen, sicer najblizji,
        ki bi mu utegnil biti primeren - po ravni (igralcu rekreativne ravni
        rekreativen, sele nato klubski; igralcu z uradnimi tekmami uradni ali
        klubski) in po razpisu (vsaj en dogodek, ki dopusca njegov spol in
        starost). Kaj je "rekreativna raven", odloci klicatelj
        (DomaciTurnirjiStoritev).
     2. ZADNJI: turnir, na katerem je nazadnje nastopil.
     3. ZANIMIVI do skupaj SKUPAJ, a vsaj NAJMANJ_ZANIMIVIH: v teku, nato
        tisti, kjer igrajo klubski kolegi, nato primerni po ravni in razpisu,
        nato tisti, katerih razpis ga vsaj dopusca - vse v oknu OKNO_DNI okoli
        danes, blizji prej. Ce jih ni dovolj, zapolnijo najnovejsi. Turnir,
        katerega razpis ga ne dopusca (U13 za odraslega), pride v postev samo
        do NAJMANJ_ZANIMIVIH - raje krajsi sklop kot polnilo brez pomena.

   Cista funkcija (brez baze), zato je pravilo mogoce preveriti brez priprave
   turnirjev; podatke zbere DomaciTurnirjiStoritev. */
package si.turnirko.storitve;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import si.turnirko.dto.DomovTurnirDto.Razlog;
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.modeli.Spol;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.StatusTekmovanja;

public final class IzborTurnirjev {

    /* Toliko vrstic ima sklop Turnirji na domaci strani (isto kot za gosta). */
    public static final int SKUPAJ = 4;

    /* "In se dva ali tri zanimive": brez prihajajocega ali zadnjega jih je tri. */
    public static final int NAJMANJ_ZANIMIVIH = 2;

    /* Zanimivo je, kar se je zgodilo ali se bo zgodilo v dveh mesecih. */
    public static final int OKNO_DNI = 60;

    /* Mladinska kategorija v prostem besedilu razpisa: "U15", "U-13",
       "1. OT ZA CLANE U-21". */
    private static final Pattern MLADINSKA = Pattern.compile("U\\s*-?\\s*(\\d{2})");

    /* Starostna meja z besedo: "do 21 let" (mlajsi), "nad 60 let" (vsaj). */
    private static final Pattern DO_LET = Pattern.compile("DO\\s*(\\d{2})\\s*LET");
    private static final Pattern NAD_LET = Pattern.compile("NAD\\s*(\\d{2})");

    /* Veterani so od 40 let (StarostniPas). */
    private static final int LET_VETERAN = 40;

    /* Turnir, kot ga izbor potrebuje. primernRazpis = vsaj en dogodek dopusca
       igralcev spol in starost (turnir brez dogodkov velja za primernega -
       razpisa se ne poznamo). moj = igralec je na njem prijavljen oz. je
       nastopil. */
    public record Kandidat(Long id, LocalDate datum, StatusTekmovanja status, RavenTekmovanja raven,
                           boolean primernRazpis, boolean kolegi, boolean moj) {}

    public record Izbira(Long id, Razlog razlog) {}

    private IzborTurnirjev() {}

    public static List<Izbira> izberi(List<Kandidat> kandidati, boolean rekreativnaRaven, LocalDate danes) {
        List<Izbira> izbrani = new ArrayList<>();
        Set<Long> ze = new HashSet<>();

        prihajajoci(kandidati, rekreativnaRaven, danes).ifPresent(i -> dodaj(izbrani, ze, i));
        zadnji(kandidati, danes).ifPresent(k -> dodaj(izbrani, ze, new Izbira(k.id(), Razlog.ZADNJI)));

        int prostih = Math.max(NAJMANJ_ZANIMIVIH, SKUPAJ - izbrani.size());
        List<Izbira> vOknu = kandidati.stream()
                .filter(k -> !ze.contains(k.id()))
                .filter(k -> k.status() == StatusTekmovanja.V_TEKU
                        || (k.datum() != null && Math.abs(dni(danes, k.datum())) <= OKNO_DNI))
                .sorted(Comparator.comparingInt((Kandidat k) -> -tocke(k, rekreativnaRaven))
                        .thenComparingLong(k -> k.datum() == null ? 0 : Math.abs(dni(danes, k.datum())))
                        .thenComparing(Kandidat::id, Comparator.reverseOrder()))
                .map(k -> new Izbira(k.id(), razlog(k, rekreativnaRaven)))
                .toList();
        /* Premalo dogajanja v oknu (poletje): zapolnijo najnovejsi. */
        List<Izbira> najnovejsi = kandidati.stream()
                .filter(k -> !ze.contains(k.id()))
                .sorted(Comparator.comparing(Kandidat::datum, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Kandidat::id, Comparator.reverseOrder()))
                .map(k -> new Izbira(k.id(), Razlog.OSTALO))
                .toList();
        List<Izbira> poVrsti = new ArrayList<>(vOknu);
        poVrsti.addAll(najnovejsi);

        /* Najprej samo turnirji, katerih razpis igralca dopusca. Mladinski
           turnir odraslemu ni "zanimiv", ceprav je nedaven - raje krajsi
           sklop. Do najmanjsega stevila zanimivih pa sme tudi tak. */
        Set<Long> dopusca = new HashSet<>();
        kandidati.stream().filter(Kandidat::primernRazpis).forEach(k -> dopusca.add(k.id()));
        int dodanih = 0;
        for (Izbira i : poVrsti) {
            if (dodanih == prostih) break;
            if (dopusca.contains(i.id()) && dodaj(izbrani, ze, i)) dodanih++;
        }
        for (Izbira i : poVrsti) {
            if (dodanih >= NAJMANJ_ZANIMIVIH) break;
            if (dodaj(izbrani, ze, i)) dodanih++;
        }
        return izbrani;
    }

    /* Najblizji prihajajoci: najprej tisti, na katerega je prijavljen, sicer
       primeren. Rekreativcu rekreativen in sele brez njega klubski (klubski
       turnirji mesajo registrirane igralce in rekreativce); tekmovalcu uradni
       ali klubski, kar je blizje - rekreativni turnir ni zanj. */
    private static Optional<Izbira> prihajajoci(List<Kandidat> kandidati, boolean rekreativnaRaven,
                                                LocalDate danes) {
        List<Kandidat> prihajajo = kandidati.stream()
                .filter(k -> k.status() == StatusTekmovanja.PRIPRAVA && k.datum() != null
                        && !k.datum().isBefore(danes))
                .sorted(Comparator.comparing(Kandidat::datum).thenComparing(Kandidat::id))
                .toList();
        Optional<Kandidat> prijavljen = prihajajo.stream().filter(Kandidat::moj).findFirst();
        if (prijavljen.isPresent()) {
            return prijavljen.map(k -> new Izbira(k.id(), Razlog.PRIJAVLJEN));
        }
        List<Predicate<Kandidat>> poVrsti = rekreativnaRaven
                ? List.of(k -> k.raven() == RavenTekmovanja.REKREATIVNO,
                          k -> k.raven() == RavenTekmovanja.KLUBSKO)
                : List.of(k -> k.raven() == RavenTekmovanja.URADNO || k.raven() == RavenTekmovanja.KLUBSKO);
        for (Predicate<Kandidat> raven : poVrsti) {
            Optional<Kandidat> k = prihajajo.stream()
                    .filter(raven)
                    .filter(Kandidat::primernRazpis)
                    .findFirst();
            if (k.isPresent()) {
                return k.map(x -> new Izbira(x.id(), Razlog.PRIHAJA_PRIMEREN));
            }
        }
        return Optional.empty();
    }

    /* Zadnji turnir, na katerem je nastopil: zacet (ne v pripravi) in ne v
       prihodnosti. */
    private static Optional<Kandidat> zadnji(List<Kandidat> kandidati, LocalDate danes) {
        return kandidati.stream()
                .filter(Kandidat::moj)
                .filter(k -> k.status() != StatusTekmovanja.PRIPRAVA && k.datum() != null
                        && !k.datum().isAfter(danes))
                .max(Comparator.comparing(Kandidat::datum).thenComparing(Kandidat::id));
    }

    private static boolean ravenPrimerna(RavenTekmovanja raven, boolean rekreativnaRaven) {
        return rekreativnaRaven
                ? raven == RavenTekmovanja.REKREATIVNO || raven == RavenTekmovanja.KLUBSKO
                : raven == RavenTekmovanja.URADNO || raven == RavenTekmovanja.KLUBSKO;
    }

    private static boolean primeren(Kandidat k, boolean rekreativnaRaven) {
        return ravenPrimerna(k.raven(), rekreativnaRaven) && k.primernRazpis();
    }

    private static int tocke(Kandidat k, boolean rekreativnaRaven) {
        return (k.status() == StatusTekmovanja.V_TEKU ? 100 : 0)
                + (k.kolegi() ? 50 : 0)
                + (primeren(k, rekreativnaRaven) ? 20 : 0)
                /* razpis, ki ga dopusca, je pred tistim, ki ga ne (U13 za
                   odraslega ni "zanimiv", ceprav je nedaven) */
                + (k.primernRazpis() ? 5 : 0);
    }

    private static Razlog razlog(Kandidat k, boolean rekreativnaRaven) {
        if (k.status() == StatusTekmovanja.V_TEKU) return Razlog.V_TEKU;
        if (k.kolegi()) return Razlog.KOLEGI;
        if (primeren(k, rekreativnaRaven)) return Razlog.PRIMEREN;
        return Razlog.OSTALO;
    }

    /* Doda turnir, ce ga se ni; vrne, ali ga je dodal. */
    private static boolean dodaj(List<Izbira> izbrani, Set<Long> ze, Izbira i) {
        if (ze.add(i.id())) {
            izbrani.add(i);
            return true;
        }
        return false;
    }

    private static long dni(LocalDate od, LocalDate doKdaj) {
        return ChronoUnit.DAYS.between(od, doKdaj);
    }

    /* Ali dogodek dopusca igralca. Spol: moski in zenske dogodki samo svojim,
       odprti in mesani (pari) vsem. Starost iz prostega besedila razpisa
       (merjeno na dan turnirja po pravilu PST):
         - "U15", "U-13", "clani U-21": mlajsi od stevilke;
         - "do 21 let", "do 60 let": mlajsi od stevilke; "nad 60 let": vsaj toliko;
         - "veterani": od 40 let;
         - imena iz uvozene zgodovine (stara stran NTZS): decki/deklice U11,
           mlajsi kadeti U13, kadeti U15, mladinci U19;
         - vse drugo (clani, absolutno, odprto) je odprto.
       Neznan spol ali starost ne izlocita - ne ugibamo proti igralcu. */
    public static boolean razpisDopusca(SpolKategorija spolKategorija, String starostnaKategorija,
                                        Spol spol, Integer starost) {
        if (spol != null && spolKategorija != null) {
            if (spolKategorija == SpolKategorija.MOSKI && spol != Spol.MOSKI) return false;
            if (spolKategorija == SpolKategorija.ZENSKE && spol != Spol.ZENSKI) return false;
        }
        if (starostnaKategorija == null || starost == null) {
            return true;
        }
        String besedilo = Normalizer.normalize(starostnaKategorija, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT);
        Matcher m = MLADINSKA.matcher(besedilo);
        if (m.find()) {
            return starost < Integer.parseInt(m.group(1));
        }
        m = DO_LET.matcher(besedilo);
        if (m.find()) {
            return starost < Integer.parseInt(m.group(1));
        }
        m = NAD_LET.matcher(besedilo);
        if (m.find()) {
            return starost >= Integer.parseInt(m.group(1));
        }
        if (besedilo.contains("VETERAN")) {
            return starost >= LET_VETERAN;
        }
        if (besedilo.contains("DECK") || besedilo.contains("DEKLIC")) return starost < 11;
        if (besedilo.contains("MLAJS") && besedilo.contains("KADET")) return starost < 13;
        if (besedilo.contains("KADET")) return starost < 15;
        if (besedilo.contains("MLADIN")) return starost < 19;
        return true;
    }
}
