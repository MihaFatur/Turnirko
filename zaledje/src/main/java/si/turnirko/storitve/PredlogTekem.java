/* Zasedenost miz in predlog naslednje tekme (M1, M2; docs/pravila/mize.md).

   Cist razred brez Springa in baze: MizeStoritev sestavi posnetek turnirja (vse
   tekme dogodkov v teku in tekme, ki so koncale danes), ta razred pa iz njega
   izpelje, katera miza je zasedena in kdo na njej igra, kdo je zaseden, kdo caka,
   da sodi (porazenec sodi naslednjo tekmo na svoji mizi), in v kaksnem vrstnem
   redu so pripravljene tekme predlagane za proste mize. Vsako pravilo je tako
   mogoce preveriti s testom brez baze (PredlogTekemTest).

   Razvrstitev pripravljenih tekem (vsako merilo odloci le pri izenacenju prejsnjega):
   (0) tekma z udelezencem, ki zdaj igra ali sodi, ni predlagana (ostane na seznamu
       z razlogom, organizator jo sme vseeno poklicati);
   (1) tekma brez porazenca, ki caka na sojenje na svoji mizi, pred tekmo s takim;
   (2) ZAOSTANEK: koliko kol ima skupina (v izlocilnem delu mreza) od kola te
       tekme do konca. Lastnik 23. 11. 2026: zaostala skupina ima prednost pred
       pocitkom. Steje skupina, ne igralec: prej je stel igralec z najvec
       preostalimi tekmami in skupine po 3 (2 tekmi na igralca) so zdrsnile za vse
       skupine po 4 (3 tekme), ceprav imata obe 3 kola. Zdaj ima 1. kolo obeh enako
       (3 kola do konca); zaostala tekma nizjega kola gre pred visje kolo drugih;
       vecja skupina (po 5: 5 kol) pride prej, da konca hkrati z ostalimi. Ocene
       izlocilnega dela ni vec: kategorije se izmenjujejo po kolih (lastnik
       23. 11. 2026), ne gre najprej tista z daljso potjo naprej;
   (3) POCITEK: tekma, v kateri je vsak udelezenec koncal pred vsaj N min (ali se
       ni igral), pred tekmo z utrujenim;
   (4) nizje kolo prej (vrstni red kol v skupini);
   (5) NAJDLJE CAKA: tekma, katere udelezenec z najkrajsim premorom caka najdlje
       (kdor se ni igral, caka najdlje);
   (6) dogodek, skupina (1, 2, 3 ...), pozicija, id - stalen vrstni red, da se
       predlog med osvezitvami ne premetava; v istem kolu gredo tekme po skupinah
       kot na listkih (lastnik 23. 11. 2026).
   Ce je organizator vrsto uredil sam (rocni vrstni red), velja njegov red pred vsemi
   merili (glej poRocnemRedu). */
package si.turnirko.storitve;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import si.turnirko.modeli.FazaTekme;
import si.turnirko.modeli.IzidTekme;
import si.turnirko.modeli.StatusTekme;

public final class PredlogTekem {

    /* Koliko predlogov dobi vsaka prosta miza (prvi je njen, ostala sta zamenjavi). */
    static final int PREDLOGOV_NA_MIZO = 3;

    private PredlogTekem() {}

    // ---------- vhod ----------

    /* Oseba (igralec). Zasedenost, pocitek in sodnik so po osebi, ne po prijavi:
       isti igralec igra posamicno, dvojice in morda se drug dogodek. */
    public record Oseba(long id, String ime) {}

    /* Stran tekme: prijava (igralec, par ali ekipa) in osebe, ki pri njej igrajo.
       Pri ekipi so to igralci v postavi srecanja (prazno, dokler postave ni). */
    public record Stran(long idPrijave, String ime, List<Oseba> osebe) {

        public Stran {
            osebe = List.copyOf(osebe);
        }
    }

    public enum Vrsta { POSAMICNO, DVOJICE, EKIPNO }

    /* Tekma v posnetku. skupina = oznaka skupine (A, B ... ali 1, 2 ...), null zunaj
       skupin; doloca vrstni red skupin v istem kolu. */
    public record Tekma(long id, long idDogodka, boolean dogodekVTeku, Vrsta vrsta,
                        FazaTekme faza, Long idSkupina, Long idZreb, int kolo, int pozicija,
                        StatusTekme status, IzidTekme izid, boolean prenesena,
                        Stran stran1, Stran stran2, Long idZmagovalca,
                        Integer miza, Integer miza2, LocalDateTime poklicanaOb,
                        LocalDateTime koncanaOb, Oseba sodnik, String skupina) {

        boolean naMizi(int stevilka) {
            return status == StatusTekme.V_IGRI
                    && (Objects.equals(miza, stevilka) || Objects.equals(miza2, stevilka));
        }

        List<Stran> strani() {
            List<Stran> s = new ArrayList<>(2);
            if (stran1 != null) s.add(stran1);
            if (stran2 != null) s.add(stran2);
            return s;
        }

        /* Igrana tekma, ki steje za pocitek: ne prosti prehod, ne prenesen izid. */
        boolean igrana() {
            return status == StatusTekme.KONCANA && koncanaOb != null
                    && izid != IzidTekme.PROSTO && !prenesena;
        }
    }

    /* rocniRed = vrstni red vrste, ki ga je organizator nastavil s premikom tekme
       (prazen = samodejni). */
    public record Nastavitve(int steviloMiz, Set<Integer> izklopljene, boolean porazenecSodi,
                             int pocitekMin, List<Long> rocniRed) {

        public Nastavitve {
            izklopljene = Set.copyOf(izklopljene);
            rocniRed = rocniRed == null ? List.of() : List.copyOf(rocniRed);
        }

        public Nastavitve(int steviloMiz, Set<Integer> izklopljene, boolean porazenecSodi, int pocitekMin) {
            this(steviloMiz, izklopljene, porazenecSodi, pocitekMin, List.of());
        }
    }

    // ---------- izhod ----------

    /* Pripravljena tekma v vrstnem redu predloga. zaseden = kdo od udelezencev zdaj
       igra ali sodi (tekma ni predlagana); razlogi so za organizatorja. */
    public record Kandidat(Tekma tekma, boolean zaseden, List<String> razlogi, Set<Long> osebe) {}

    /* Miza: zasedena (tekma), prosta (prostaOd = konec zadnje tekme, null = se ni
       bila v rabi) ali izklopljena. predlogi = id-ji kandidatov, prvi je predlog za
       to mizo. cakajociSodnik = porazenec zadnje tekme, ki naj sodi naslednjo. */
    public record Miza(int stevilka, boolean izklopljena, Tekma tekma, LocalDateTime prostaOd,
                       Oseba cakajociSodnik, List<Long> predlogi) {}

    public record Rezultat(List<Miza> mize, List<Kandidat> kandidati) {}

    // ---------- izracun ----------

    public static Rezultat izracunaj(List<Tekma> tekme, Nastavitve nastavitve, LocalDateTime zdaj) {
        return new Izracun(tekme, nastavitve, zdaj).izracunaj();
    }

    /* Kdo je zaseden (igra ali sodi tekmo v igri) - po osebi in po prijavi; idTekme
       pove, katera tekma ga zaseda (tekma sama sebe ne zaseda). */
    private record Zasedenost(long idTekme, String opis) {}

    private static final class Izracun {

        private final List<Tekma> tekme;
        private final Nastavitve nastavitve;
        private final LocalDateTime zdaj;

        private final Map<Long, Zasedenost> zasedeneOsebe = new HashMap<>();
        private final Map<Long, Zasedenost> zasedenePrijave = new HashMap<>();
        private final Map<Long, LocalDateTime> konecOsebe = new HashMap<>();
        private final Map<Long, LocalDateTime> konecPrijave = new HashMap<>();
        private final Map<Long, LocalDateTime> zadnjiKlicOsebe = new HashMap<>();
        /* oseba -> miza, na kateri naj sodi (porazenec sodi) */
        private final Map<Long, Integer> cakajociSodniki = new HashMap<>();

        Izracun(List<Tekma> tekme, Nastavitve nastavitve, LocalDateTime zdaj) {
            this.tekme = tekme;
            this.nastavitve = nastavitve;
            this.zdaj = zdaj;
        }

        Rezultat izracunaj() {
            zberiZasedenost();
            zberiKonce();
            List<Miza> mize = mize();
            List<Kandidat> kandidati = kandidati();
            return new Rezultat(razporediPredloge(mize, kandidati), kandidati);
        }

        private void zberiZasedenost() {
            for (Tekma t : tekme) {
                if (t.status() != StatusTekme.V_IGRI) {
                    continue;
                }
                String kje = t.miza() == null ? "" : " na mizi " + t.miza();
                for (Stran s : t.strani()) {
                    zasedenePrijave.putIfAbsent(s.idPrijave(), new Zasedenost(t.id(), s.ime() + " igra" + kje));
                    for (Oseba o : s.osebe()) {
                        zasedeneOsebe.putIfAbsent(o.id(), new Zasedenost(t.id(), o.ime() + " igra" + kje));
                    }
                }
                if (t.sodnik() != null) {
                    zasedeneOsebe.putIfAbsent(t.sodnik().id(),
                            new Zasedenost(t.id(), t.sodnik().ime() + " sodi" + kje));
                }
            }
        }

        /* Konec zadnje igrane tekme po osebi in po prijavi (pocitek, cakanje) in
           zadnji klic po osebi (porazenec, ki je od tedaj ze igral ali sodil, ne
           caka vec na sojenje). */
        private void zberiKonce() {
            for (Tekma t : tekme) {
                if (t.igrana()) {
                    for (Stran s : t.strani()) {
                        konecPrijave.merge(s.idPrijave(), t.koncanaOb(), Izracun::kasnejsi);
                        for (Oseba o : s.osebe()) {
                            konecOsebe.merge(o.id(), t.koncanaOb(), Izracun::kasnejsi);
                        }
                    }
                }
                if (t.poklicanaOb() != null) {
                    for (Stran s : t.strani()) {
                        for (Oseba o : s.osebe()) {
                            zadnjiKlicOsebe.merge(o.id(), t.poklicanaOb(), Izracun::kasnejsi);
                        }
                    }
                    if (t.sodnik() != null) {
                        zadnjiKlicOsebe.merge(t.sodnik().id(), t.poklicanaOb(), Izracun::kasnejsi);
                    }
                }
            }
        }

        private List<Miza> mize() {
            List<Miza> mize = new ArrayList<>();
            for (int st = 1; st <= nastavitve.steviloMiz(); st++) {
                final int stevilka = st;
                boolean izklopljena = nastavitve.izklopljene().contains(st);
                Tekma naMizi = tekme.stream().filter(t -> t.naMizi(stevilka))
                        .min(Comparator.comparing(Tekma::id)).orElse(null);
                Tekma zadnja = tekme.stream()
                        .filter(t -> t.status() == StatusTekme.KONCANA && t.koncanaOb() != null
                                && (Objects.equals(t.miza(), stevilka) || Objects.equals(t.miza2(), stevilka)))
                        .max(Comparator.comparing(Tekma::koncanaOb).thenComparing(Tekma::id))
                        .orElse(null);
                Oseba sodnik = null;
                if (nastavitve.porazenecSodi() && !izklopljena && naMizi == null) {
                    sodnik = porazenecZaSodnika(zadnja);
                    if (sodnik != null) {
                        cakajociSodniki.put(sodnik.id(), stevilka);
                    }
                }
                mize.add(new Miza(stevilka, izklopljena, naMizi,
                        naMizi == null && zadnja != null ? zadnja.koncanaOb() : null, sodnik, List.of()));
            }
            return mize;
        }

        /* Porazenec zadnje tekme na mizi, ki naj sodi naslednjo (lastnik 23. 11. 2026).
           Samo po tekmi posameznikov (po dvojicah nihce), samo po odigrani tekmi ali
           predaji (pri brez boja porazenca ni bilo), samo isti dan, in samo, ce od
           tedaj ni ze igral ali sodil in zdaj ni zaseden. */
        private Oseba porazenecZaSodnika(Tekma zadnja) {
            if (zadnja == null || zadnja.vrsta() != Vrsta.POSAMICNO || zadnja.prenesena()
                    || (zadnja.izid() != IzidTekme.IGRANO && zadnja.izid() != IzidTekme.PREDAJA)
                    || zadnja.idZmagovalca() == null
                    || !zadnja.koncanaOb().toLocalDate().equals(zdaj.toLocalDate())) {
                return null;
            }
            Stran porazenec = zadnja.stran1() != null && zadnja.stran1().idPrijave() == zadnja.idZmagovalca()
                    ? zadnja.stran2() : zadnja.stran1();
            if (porazenec == null || porazenec.osebe().size() != 1) {
                return null;
            }
            Oseba oseba = porazenec.osebe().get(0);
            if (zasedeneOsebe.containsKey(oseba.id())) {
                return null;
            }
            LocalDateTime klic = zadnjiKlicOsebe.get(oseba.id());
            if (klic != null && klic.isAfter(zadnja.koncanaOb())) {
                return null;
            }
            return oseba;
        }

        private List<Kandidat> kandidati() {
            record Ocena(Kandidat kandidat, boolean zaseden, boolean konfliktSodnika, int zaostanek,
                         boolean spocit, long cakanje) {}
            List<Ocena> ocene = new ArrayList<>();
            for (Tekma t : tekme) {
                if (!jeKandidat(t)) {
                    continue;
                }
                Set<Long> osebe = new HashSet<>();
                t.strani().forEach(s -> s.osebe().forEach(o -> osebe.add(o.id())));

                List<String> zasedeni = new ArrayList<>();
                for (Stran s : t.strani()) {
                    Zasedenost z = zasedenePrijave.get(s.idPrijave());
                    if (z != null && z.idTekme() != t.id()) {
                        zasedeni.add(z.opis());
                    }
                    for (Oseba o : s.osebe()) {
                        Zasedenost zo = zasedeneOsebe.get(o.id());
                        if (zo != null && zo.idTekme() != t.id() && !zasedeni.contains(zo.opis())) {
                            zasedeni.add(zo.opis());
                        }
                    }
                }
                List<String> sodniki = new ArrayList<>();
                for (Stran s : t.strani()) {
                    for (Oseba o : s.osebe()) {
                        Integer miza = cakajociSodniki.get(o.id());
                        if (miza != null) {
                            sodniki.add(o.ime() + " naj sodi na mizi " + miza);
                        }
                    }
                }

                int zaostanek = kolaDoKonca(t);

                // pocitek in cakanje: udelezenec z najkrajsim premorom
                Long najkrajsiPremor = null;
                Stran utrujen = null;
                for (Stran s : t.strani()) {
                    LocalDateTime konec = konecStrani(s);
                    if (konec == null) {
                        continue;
                    }
                    long minute = Math.max(0, Duration.between(konec, zdaj).toMinutes());
                    if (najkrajsiPremor == null || minute < najkrajsiPremor) {
                        najkrajsiPremor = minute;
                        utrujen = s;
                    }
                }
                boolean spocit = najkrajsiPremor == null || najkrajsiPremor >= nastavitve.pocitekMin();
                long cakanje = najkrajsiPremor == null ? Long.MAX_VALUE : najkrajsiPremor;

                List<String> razlogi = new ArrayList<>(zasedeni);
                razlogi.addAll(sodniki);
                razlogi.add((t.faza() == FazaTekme.SKUPINA ? "do konca skupine še " : "do konca mreže še ")
                        + zaostanek + " " + kol(zaostanek));
                if (!spocit) {
                    razlogi.add(utrujen.ime() + ": zadnja tekma pred " + najkrajsiPremor + " min");
                } else if (najkrajsiPremor != null) {
                    razlogi.add("premor " + najkrajsiPremor + " min");
                } else {
                    razlogi.add("prva tekma");
                }
                if (t.faza() == FazaTekme.SKUPINA) {
                    razlogi.add(t.kolo() + ". kolo");
                }
                Kandidat k = new Kandidat(t, !zasedeni.isEmpty(), List.copyOf(razlogi), Set.copyOf(osebe));
                ocene.add(new Ocena(k, !zasedeni.isEmpty(), !sodniki.isEmpty(), zaostanek, spocit, cakanje));
            }
            ocene.sort(Comparator.comparing(Ocena::zaseden)
                    .thenComparing(Ocena::konfliktSodnika)
                    .thenComparing(Ocena::zaostanek, Comparator.reverseOrder())
                    .thenComparing(Ocena::spocit, Comparator.reverseOrder())
                    .thenComparing(o -> o.kandidat().tekma().kolo())
                    .thenComparing(Ocena::cakanje, Comparator.reverseOrder())
                    .thenComparing(o -> o.kandidat().tekma().idDogodka())
                    .thenComparing(o -> o.kandidat().tekma().skupina(), PredlogTekem::primerjajSkupini)
                    .thenComparing(o -> o.kandidat().tekma().pozicija())
                    .thenComparing(o -> o.kandidat().tekma().id()));
            return poRocnemRedu(ocene.stream().map(Ocena::kandidat).toList());
        }

        /* (R) ROCNI VRSTNI RED (lastnik 24. 11. 2026): organizator tekmo v vrsti povlece
           na drugo mesto in shrani se vrsta, kot jo je videl. Tekme s tega seznama gredo
           naprej v njegovem vrstnem redu - tudi zasedene, ki jih razporeditev po mizah
           tako ali tako preskoci, in ne glede na pocitek: kar je organizator uredil,
           ostane, dokler ne izbere samodejnega reda. Tekme, ki jih na seznamu ni (pripravile
           so se pozneje), sledijo v samodejnem vrstnem redu (stabilno razvrscanje). */
        private List<Kandidat> poRocnemRedu(List<Kandidat> samodejno) {
            if (nastavitve.rocniRed().isEmpty()) {
                return samodejno;
            }
            Map<Long, Integer> mesto = new HashMap<>();
            for (Long id : nastavitve.rocniRed()) {
                mesto.putIfAbsent(id, mesto.size());
            }
            List<Kandidat> red = new ArrayList<>(samodejno);
            red.sort(Comparator.comparing(k -> mesto.getOrDefault(k.tekma().id(), Integer.MAX_VALUE)));
            return red;
        }

        /* Pripravljena tekma dogodka v teku z obema udelezencema; ekipna tekma, ki ze
           tece (postava je vpisana), a nima mize, se lahko pokliče naknadno. */
        private static boolean jeKandidat(Tekma t) {
            if (!t.dogodekVTeku() || t.stran1() == null || t.stran2() == null) {
                return false;
            }
            return t.status() == StatusTekme.PRIPRAVLJENA
                    || (t.vrsta() == Vrsta.EKIPNO && t.status() == StatusTekme.V_IGRI && t.miza() == null);
        }

        /* Koliko kol ima skupina oz. mreza te tekme od njenega kola do konca
           (vkljucno z njim). Posnetek vsebuje vse tekme dogodka v teku, zato je
           zadnje kolo skupine znano tudi, ko je ze odigrano. */
        private int kolaDoKonca(Tekma t) {
            int zadnjeKolo = tekme.stream()
                    .filter(u -> u.idDogodka() == t.idDogodka() && u.faza() == t.faza()
                            && Objects.equals(u.idSkupina(), t.idSkupina())
                            && Objects.equals(u.idZreb(), t.idZreb()))
                    .mapToInt(Tekma::kolo).max().orElse(t.kolo());
            return Math.max(1, zadnjeKolo - t.kolo() + 1);
        }

        private LocalDateTime konecStrani(Stran s) {
            LocalDateTime konec = konecPrijave.get(s.idPrijave());
            for (Oseba o : s.osebe()) {
                konec = kasnejsi(konec, konecOsebe.get(o.id()));
            }
            return konec;
        }

        /* Prvi predlog vsake proste mize je njen. Najprej pridejo na vrsto mize, kjer
           porazenec caka, da sodi (prej sproscena prej) - tam je sodnik ze ob mizi;
           nato ostale po stevilki, da se dvorana polni po vrsti. Dve prosti mizi ne
           dobita istega prvega predloga in ne tekem z isto osebo (obeh ne bi bilo
           mogoce poklicati). Ostala predloga sta zamenjavi: naslednji prosti
           kandidati, ki niso prvi predlog nobene mize. */
        private List<Miza> razporediPredloge(List<Miza> mize, List<Kandidat> kandidati) {
            List<Miza> proste = mize.stream().filter(m -> !m.izklopljena() && m.tekma() == null)
                    .sorted(Comparator.comparing((Miza m) -> m.cakajociSodnik() == null)
                            .thenComparing(m -> m.cakajociSodnik() == null ? null : m.prostaOd(),
                                    Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(Miza::stevilka))
                    .toList();
            Map<Integer, Long> prvi = new LinkedHashMap<>();
            Set<Long> dodeljene = new HashSet<>();
            Set<Long> zasedeOsebe = new HashSet<>();
            Set<Long> zasedePrijave = new HashSet<>();
            for (Miza m : proste) {
                for (Kandidat k : kandidati) {
                    if (k.zaseden() || dodeljene.contains(k.tekma().id())) {
                        continue;
                    }
                    boolean trk = k.osebe().stream().anyMatch(zasedeOsebe::contains)
                            || k.tekma().strani().stream().anyMatch(s -> zasedePrijave.contains(s.idPrijave()));
                    if (trk) {
                        continue;
                    }
                    prvi.put(m.stevilka(), k.tekma().id());
                    dodeljene.add(k.tekma().id());
                    zasedeOsebe.addAll(k.osebe());
                    // porazenec, ki bo sodil to tekmo, ni v predlogu druge mize (oboje
                    // naenkrat bi »Pokliči predloge« poklical igralca, ki sodi)
                    if (m.cakajociSodnik() != null && !k.osebe().contains(m.cakajociSodnik().id())) {
                        zasedeOsebe.add(m.cakajociSodnik().id());
                    }
                    k.tekma().strani().forEach(s -> zasedePrijave.add(s.idPrijave()));
                    break;
                }
            }
            List<Miza> rezultat = new ArrayList<>();
            for (Miza m : mize) {
                Long lastni = prvi.get(m.stevilka());
                if (lastni == null) {
                    rezultat.add(m);
                    continue;
                }
                List<Long> predlogi = new ArrayList<>();
                predlogi.add(lastni);
                for (Kandidat k : kandidati) {
                    if (predlogi.size() >= PREDLOGOV_NA_MIZO) {
                        break;
                    }
                    if (!k.zaseden() && !dodeljene.contains(k.tekma().id())) {
                        predlogi.add(k.tekma().id());
                    }
                }
                rezultat.add(new Miza(m.stevilka(), m.izklopljena(), m.tekma(), m.prostaOd(),
                        m.cakajociSodnik(), List.copyOf(predlogi)));
            }
            return rezultat;
        }

        private static LocalDateTime kasnejsi(LocalDateTime a, LocalDateTime b) {
            if (a == null) return b;
            if (b == null) return a;
            return a.isAfter(b) ? a : b;
        }
    }

    /* »ima še 1 tekmo, 2 tekmi, 3 tekme, 5 tekem« */
    /* »se 1 kolo, 2 kola, 3 kola, 4 kola, 5 kol« */
    static String kol(int n) {
        int ostanek = n % 100;
        if (ostanek == 1) return "kolo";
        if (ostanek >= 2 && ostanek <= 4) return "kola";
        return "kol";
    }

    /* Skupine po vrsti kot na listkih: krajsa oznaka prej (9 pred 10 pri
       ostevilcenih skupinah, Z pred ZA v SV regiji; pomozno/OznakaSkupine), nato
       po abecedi - isto pravilo kot SkupinaRepozitorij.findByDogodekIdOrderByOznakaAsc.
       Tekme brez skupine za skupinami. */
    static int primerjajSkupini(String a, String b) {
        if (a == null || b == null) {
            return a == null ? (b == null ? 0 : 1) : -1;
        }
        int dolzina = Integer.compare(a.length(), b.length());
        return dolzina != 0 ? dolzina : a.compareTo(b);
    }
}
