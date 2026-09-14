/* Ekipna tekma iz Stupe v srecanje Turnirka: postava, posamicne tekme, nizi
   in kader. Deli jo ligasko srecanje in ekipna tekma turnirja, ker je zapis
   pri viru v obeh primerih isti (tekma med ekipama s podtekmami).

   Pasti vira, ki jih ta razred razresi:

   * DOMACA EKIPA ni nujno prva udelezenka tekme. V koncnici 1. SNTL in na
     ekipnem DP je ekipa z mesti A/B/C vcasih druga po vrstnem redu - odloca
     OZNAKA igralcev (A/B/C in DA1/DA2 = domaci, X/Y/Z in DB1/DB2 = gostje),
     ne vrstni red.

   * OZNAKE so treh vrst:
       crke      A, B, C : X, Y, Z (vecina podatkov),
       igralci   A1..A3 : B1..B3 (pokal NTZS 2026 - A1 je prvi igralec
                 domacih, torej mesto A),
       tekme     A1..A9 : B1..B9 (ekipno kadetinje 2024/25 - stevilka je
                 zaporedna tekma, mesto pa pove sele razpored formata).
     Dvojice so "DA1+DA2" : "DB1+DB2" (tudi s tipkarsko "DB1+DB1") ali pa kar
     crke para ("B+C" : "Y+Z").

   * FORMAT se prebere iz dejanskega zaporedja tekem vseh srecanj
     tekmovanja in se ne ugiba iz imena. Neodigrane tekme na koncu (srecanje
     odloceno prej) nimajo igralcev in se ujemajo s cimerkoli. */
package si.turnirko.uvoz.stupa;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;

import si.turnirko.dto.NizVnos;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.modeli.Ekipa;
import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.IzidTekme;
import si.turnirko.modeli.KaderEkipe;
import si.turnirko.modeli.NizSrecanja;
import si.turnirko.modeli.PostavaSrecanja;
import si.turnirko.modeli.Srecanje;
import si.turnirko.modeli.StatusTekmeSrecanja;
import si.turnirko.modeli.StranEkipe;
import si.turnirko.modeli.TekmaSrecanja;
import si.turnirko.modeli.TipTekmeSrecanja;
import si.turnirko.repozitoriji.KaderEkipeRepozitorij;
import si.turnirko.repozitoriji.NizSrecanjaRepozitorij;
import si.turnirko.repozitoriji.PostavaSrecanjaRepozitorij;
import si.turnirko.repozitoriji.TekmaSrecanjaRepozitorij;
import si.turnirko.storitve.NiziPravila;

final class SrecanjaStupe {

    enum Slog { CRKE, IGRALCI, TEKME, BREZ_OZNAK }

    /* Kdo je v ekipni tekmi domaci in kako so oznaceni igralci. */
    record Oznake(long idDomacih, long idGostov, Slog slog) {}

    /* Dobljene posamicne tekme vsake strani. */
    record Rezultat(int dobljeneDomaci, int dobljeneGost) {}

    private static final Pattern Z_STEVILKO = Pattern.compile("^([AB])(\\d+)$");

    private final IdentitetaStupe identiteta;
    private final PostavaSrecanjaRepozitorij postavaRepozitorij;
    private final TekmaSrecanjaRepozitorij tekmaSrecanjaRepozitorij;
    private final NizSrecanjaRepozitorij nizSrecanjaRepozitorij;
    private final PorociloUvoza porocilo;

    SrecanjaStupe(IdentitetaStupe identiteta, PostavaSrecanjaRepozitorij postavaRepozitorij,
                  TekmaSrecanjaRepozitorij tekmaSrecanjaRepozitorij, NizSrecanjaRepozitorij nizSrecanjaRepozitorij,
                  PorociloUvoza porocilo) {
        this.identiteta = identiteta;
        this.postavaRepozitorij = postavaRepozitorij;
        this.tekmaSrecanjaRepozitorij = tekmaSrecanjaRepozitorij;
        this.nizSrecanjaRepozitorij = nizSrecanjaRepozitorij;
        this.porocilo = porocilo;
    }

    // ---------- Oznake in format ----------

    /* Domaca ekipa po oznakah igralcev; brez oznak (tekma brez podtekem) je
       domaca prva udelezenka. */
    static Oznake oznake(JsonNode tekma) {
        List<JsonNode> strani = urejeni(tekma.path("participants"));
        long prva = strani.isEmpty() ? 0 : strani.get(0).path("participant_id").asLong();
        long druga = strani.size() < 2 ? 0 : strani.get(1).path("participant_id").asLong();

        Map<Long, Integer> glasoviDoma = new HashMap<>();
        Map<Long, Integer> glasoviGost = new HashMap<>();
        int najvecjaStevilka = 0;
        boolean crke = false;
        for (JsonNode pod : tekma.path("sub_matches")) {
            for (JsonNode stran : pod.path("participants")) {
                long id = stran.path("participant_id").asLong();
                for (JsonNode pd : stran.path("participant_details")) {
                    String oznaka = oznaka(pd);
                    if (oznaka == null) {
                        continue;
                    }
                    Matcher m = Z_STEVILKO.matcher(oznaka);
                    if (oznaka.matches("[ABC]") || oznaka.matches("DA\\d")) {
                        glasoviDoma.merge(id, 1, Integer::sum);
                        crke |= oznaka.length() == 1;
                    } else if (oznaka.matches("[XYZ]") || oznaka.matches("DB\\d")) {
                        glasoviGost.merge(id, 1, Integer::sum);
                        crke |= oznaka.length() == 1;
                    } else if (m.matches()) {
                        (m.group(1).equals("A") ? glasoviDoma : glasoviGost).merge(id, 1, Integer::sum);
                        najvecjaStevilka = Math.max(najvecjaStevilka, Integer.parseInt(m.group(2)));
                    }
                }
            }
        }
        Slog slog = najvecjaStevilka > 3 ? Slog.TEKME
                : najvecjaStevilka > 0 ? Slog.IGRALCI
                : crke ? Slog.CRKE : Slog.BREZ_OZNAK;
        if (glasoviDoma.isEmpty() && glasoviGost.isEmpty()) {
            return new Oznake(prva, druga, slog);
        }
        int drugaDoma = glasoviDoma.getOrDefault(druga, 0) - glasoviGost.getOrDefault(druga, 0);
        int prvaDoma = glasoviDoma.getOrDefault(prva, 0) - glasoviGost.getOrDefault(prva, 0);
        return drugaDoma > prvaDoma ? new Oznake(druga, prva, slog) : new Oznake(prva, druga, slog);
    }

    /* Zaporedje tekem srecanja za prepoznavo formata: "dvojice", "A-X", "?"
       (posamicna brez znanih mest) ali "*" (neodigrana brez igralcev). */
    static List<String> zaporedje(JsonNode tekma, Oznake o) {
        List<String> r = new ArrayList<>();
        for (JsonNode pod : urejeni(tekma.path("sub_matches"))) {
            JsonNode domaci = stran(pod, o.idDomacih());
            JsonNode gost = stran(pod, o.idGostov());
            if (domaci == null || gost == null
                    || domaci.path("participant_details").isEmpty() || gost.path("participant_details").isEmpty()) {
                r.add("*");
            } else if (jeDvojice(pod)) {
                r.add("dvojice");
            } else if (o.slog() == Slog.TEKME) {
                r.add("?");
            } else {
                String d = mesto(domaci, o.slog(), true);
                String g = mesto(gost, o.slog(), false);
                r.add(d == null || g == null ? "?" : d + "-" + g);
            }
        }
        return r;
    }

    /* Format, ki se ujema z zaporedji VSEH srecanj; null, ce takega ni. */
    static FormatSrecanja dolociFormat(List<JsonNode> tekme, boolean samoLigaski, PorociloUvoza porocilo, String opis) {
        List<List<String>> zaporedja = new ArrayList<>();
        int najdaljse = 0;
        for (JsonNode t : tekme) {
            if (t.path("sub_matches").isEmpty()) {
                continue;
            }
            List<String> z = zaporedje(t, oznake(t));
            zaporedja.add(z);
            najdaljse = Math.max(najdaljse, z.size());
        }
        if (zaporedja.isEmpty()) {
            return null;
        }
        List<FormatSrecanja> kandidati = new ArrayList<>();
        for (FormatSrecanja f : FormatSrecanja.values()) {
            if (samoLigaski && f.samoZaTurnir()) {
                continue;
            }
            if (zaporedja.stream().allMatch(z -> ujema(z, f))) {
                kandidati.add(f);
            }
        }
        if (kandidati.isEmpty()) {
            porocilo.napaka("razpored tekem srecanja ne ustreza nobenemu formatu", opis + ": "
                    + zaporedja.stream().max(Comparator.comparingInt(List::size)).orElse(List.of()));
            return null;
        }
        final int dolzina = najdaljse;
        List<FormatSrecanja> tocni = kandidati.stream().filter(f -> f.stTekem() == dolzina).toList();
        List<FormatSrecanja> izbor = tocni.isEmpty() ? kandidati : tocni;
        if (izbor.size() > 1) {
            porocilo.opozori("format srecanja ni enolicen (izbran prvi)", opis + ": " + izbor);
        }
        return izbor.get(0);
    }

    private static boolean ujema(List<String> zaporedje, FormatSrecanja f) {
        List<FormatSrecanja.MestoTekme> razpored = f.razpored();
        if (zaporedje.size() > razpored.size()) {
            return false;
        }
        for (int i = 0; i < zaporedje.size(); i++) {
            String z = zaporedje.get(i);
            FormatSrecanja.MestoTekme m = razpored.get(i);
            boolean ok = switch (z) {
                case "*" -> true;
                case "?" -> m.tip() == TipTekmeSrecanja.POSAMICNA;
                case "dvojice" -> m.tip() == TipTekmeSrecanja.DVOJICE;
                default -> m.tip() == TipTekmeSrecanja.POSAMICNA && z.equals(m.oznaka());
            };
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    // ---------- Zapis srecanja ----------

    /* Zapise posamicne tekme, nize in postavo shranjenega srecanja ter zbere
       kader. koncano pove, ali je ekipna tekma pri viru odigrana (takrat so
       neodigrane podtekme NEODIGRANA, sicer CAKA). */
    Rezultat zapisi(Srecanje s, JsonNode tekma, Oznake o, FormatSrecanja format, LocalDate datum,
                    Map<Long, Ekipa> ekipe, Kadri kadri, boolean koncano, String opis) {
        Ekipa domacaEkipa = ekipe.get(o.idDomacih());
        Ekipa gostujocaEkipa = ekipe.get(o.idGostov());
        Map<String, Igralec> postava = new LinkedHashMap<>();
        Set<Long> vDvojicah = new HashSet<>();
        Set<Integer> zasedeno = new HashSet<>();
        int dobljeneDomaci = 0;
        int dobljeneGost = 0;
        int zaporedna = 0;

        for (JsonNode pod : urejeni(tekma.path("sub_matches"))) {
            zaporedna++;
            JsonNode domaci = stran(pod, o.idDomacih());
            JsonNode gost = stran(pod, o.idGostov());
            if (domaci == null || gost == null) {
                porocilo.napaka("podtekma brez obeh ekip", opis + ", podtekma " + pod.path("id").asLong());
                continue;
            }
            int zaporedje = pod.path("order").asInt(zaporedna);
            if (zaporedje < 1 || !zasedeno.add(zaporedje)) {
                zaporedje = zaporedna;
                while (!zasedeno.add(zaporedje)) {
                    zaporedje++;
                }
            }
            boolean dvojice = jeDvojice(pod);
            List<Igralec> igralciDoma = igralci(domaci, datum);
            List<Igralec> igralciGost = igralci(gost, datum);
            igralciDoma.forEach(i -> kadri.dodaj(domacaEkipa, i, null));
            igralciGost.forEach(i -> kadri.dodaj(gostujocaEkipa, i, null));

            String mestoDoma = null;
            String mestoGost = null;
            if (!dvojice) {
                if (o.slog() == Slog.TEKME) {
                    if (format != null && zaporedje <= format.stTekem()
                            && format.razpored().get(zaporedje - 1).tip() == TipTekmeSrecanja.POSAMICNA) {
                        mestoDoma = format.razpored().get(zaporedje - 1).domaci();
                        mestoGost = format.razpored().get(zaporedje - 1).gost();
                    }
                } else {
                    mestoDoma = mesto(domaci, o.slog(), true);
                    mestoGost = mesto(gost, o.slog(), false);
                }
            }
            String oznaka = dvojice ? "dvojice"
                    : (mestoDoma == null ? "?" : mestoDoma) + "-" + (mestoGost == null ? "?" : mestoGost);

            TekmaSrecanja ts = new TekmaSrecanja(s, zaporedje,
                    dvojice ? TipTekmeSrecanja.DVOJICE : TipTekmeSrecanja.POSAMICNA, oznaka,
                    steviloNizov(domaci.path("sets").size()));
            ts.setIgralecDomaci(igralciDoma.isEmpty() ? null : igralciDoma.get(0));
            ts.setIgralecGost(igralciGost.isEmpty() ? null : igralciGost.get(0));
            if (dvojice) {
                ts.setIgralecDomaci2(igralciDoma.size() > 1 ? igralciDoma.get(1) : null);
                ts.setIgralecGost2(igralciGost.size() > 1 ? igralciGost.get(1) : null);
                igralciDoma.forEach(i -> vDvojicah.add(i.getId()));
                igralciGost.forEach(i -> vDvojicah.add(i.getId()));
            } else {
                if (mestoDoma != null && !igralciDoma.isEmpty()) {
                    postava.putIfAbsent("DOMACI|" + mestoDoma, igralciDoma.get(0));
                }
                if (mestoGost != null && !igralciGost.isEmpty()) {
                    postava.putIfAbsent("GOST|" + mestoGost, igralciGost.get(0));
                }
            }

            List<NizVnos> nizi = List.of();
            if ("SCORED".equals(pod.path("status").asText())) {
                int d = domaci.path("sets_won").asInt();
                int g = gost.path("sets_won").asInt();
                int pricakovanih = dvojice ? 2 : 1;
                boolean neznanIgralec = (!domaci.path("participant_details").isEmpty() && igralciDoma.size() < pricakovanih)
                        || (!gost.path("participant_details").isEmpty() && igralciGost.size() < pricakovanih);
                if (neznanIgralec) {
                    // tekma brez igralca ne sme v rating - uskladitev jo steje kot neujemanje
                    porocilo.opozori("igralec podtekme ni v registru (tekma zapisana brez boja)",
                            opis + ", podtekma " + pod.path("id").asLong());
                }
                // brez boja je tudi tekma, za katero ekipa ni postavila igralca
                boolean brezBoja = domaci.path("walkover").asBoolean(false) || gost.path("walkover").asBoolean(false)
                        || igralciDoma.isEmpty() || igralciGost.isEmpty() || neznanIgralec;
                StranEkipe zmagovalec = zmagovalnaStran(pod.path("winner").asLong(0), o, d, g);
                if (zmagovalec == null) {
                    porocilo.napaka("podtekma brez zmagovalca", opis + ", podtekma " + pod.path("id").asLong()
                            + " (" + d + ":" + g + ")");
                    continue;
                }
                /* Zmagovalec brez nizov (0 : 0): podtekma je bila odigrana, zapisan
                   pa je samo zmagovalec. Zapise se kot odigrana z 0 : 0, kar rating
                   bere kot "samo zmagovalec" (IzidTekme.samoZmagovalec). Izenacenje
                   z nizi (npr. 1 : 1) pa je protislovje vira. */
                if (!brezBoja && d == 0 && g == 0) {
                    porocilo.opozori("podtekma z zapisanim zmagovalcem brez nizov (odigrana, izid po nizih ni znan)",
                            opis + ", podtekma " + pod.path("id").asLong());
                } else if (!brezBoja && (d == g || (zmagovalec == StranEkipe.DOMACI) != (d > g))) {
                    porocilo.napaka("zmagovalec podtekme se ne ujema z nizi", opis + ", podtekma "
                            + pod.path("id").asLong() + " (" + d + ":" + g + ")");
                }
                ts.setStatus(StatusTekmeSrecanja.KONCANA);
                ts.setIzidTip(brezBoja ? IzidTekme.BREZ_BOJA : IzidTekme.IGRANO);
                ts.setZmagovalecStran(zmagovalec);
                ts.setDobljeniNiziDomaci(d);
                ts.setDobljeniNiziGost(g);
                if (zmagovalec == StranEkipe.DOMACI) {
                    dobljeneDomaci++;
                } else {
                    dobljeneGost++;
                }
                porocilo.prestej(ts.getTip() == TipTekmeSrecanja.DVOJICE ? "odigranih dvojic v srecanjih"
                        : "odigranih posamicnih tekem v srecanjih");
                if (!brezBoja) {
                    nizi = nizi(domaci.path("points"), gost.path("points"), d, g, ts.getSteviloNizov(),
                            opis + ", podtekma " + pod.path("id").asLong());
                }
            } else {
                ts.setStatus(koncano ? StatusTekmeSrecanja.NEODIGRANA : StatusTekmeSrecanja.CAKA);
            }
            TekmaSrecanja shranjena = tekmaSrecanjaRepozitorij.save(ts);
            for (int i = 0; i < nizi.size(); i++) {
                nizSrecanjaRepozitorij.save(new NizSrecanja(shranjena, i + 1, nizi.get(i).tocke1(), nizi.get(i).tocke2()));
            }
            porocilo.prestej("nizov v srecanjih", nizi.size());
        }

        Set<String> igralciNaStrani = new HashSet<>();
        for (Map.Entry<String, Igralec> vnos : postava.entrySet()) {
            String[] deli = vnos.getKey().split("\\|");
            StranEkipe stran = StranEkipe.valueOf(deli[0]);
            if (!igralciNaStrani.add(stran + "|" + vnos.getValue().getId())) {
                porocilo.opozori("isti igralec na dveh mestih postave (drugo mesto izpusceno)",
                        opis + ": " + vnos.getValue().polnoIme());
                continue;
            }
            postavaRepozitorij.save(new PostavaSrecanja(s, stran, deli[1], vnos.getValue(),
                    vDvojicah.contains(vnos.getValue().getId())));
        }
        return new Rezultat(dobljeneDomaci, dobljeneGost);
    }

    private List<Igralec> igralci(JsonNode stran, LocalDate datum) {
        List<Igralec> r = new ArrayList<>();
        for (JsonNode pd : urejeni(stran.path("participant_details"))) {
            Igralec i = identiteta.igralec(IdentitetaStupe.oseba(pd), datum);
            if (i != null && r.stream().noneMatch(x -> x.getId().equals(i.getId()))) {
                r.add(i);
            }
        }
        return r;
    }

    /* Izid ekipne tekme pri viru: dobljene podtekme po udelezencu. Vir ga
       obicajno zapise v sets_won obeh strani ekipne tekme. Kadar sta obe 0,
       odigrane podtekme pa imajo zmagovalce (srecanja z zapisanimi samo
       zmagovalci), izid ni bil sestet - takrat se presteje iz zmagovalcev
       podtekem, sicer bi bilo srecanje 5 : 1 zapisano kot 0 : 0. */
    static Map<Long, Integer> izidEkipneTekme(JsonNode tekma) {
        Map<Long, Integer> izid = new HashMap<>();
        List<JsonNode> strani = urejeni(tekma.path("participants"));
        int sesteto = 0;
        for (JsonNode s : strani) {
            izid.put(s.path("participant_id").asLong(), s.path("sets_won").asInt());
            sesteto += s.path("sets_won").asInt();
        }
        if (sesteto > 0) {
            return izid;
        }
        Map<Long, Integer> poPodtekmah = new HashMap<>();
        strani.forEach(s -> poPodtekmah.put(s.path("participant_id").asLong(), 0));
        for (JsonNode pod : tekma.path("sub_matches")) {
            long zmagovalec = pod.path("winner").asLong(0);
            if ("SCORED".equals(pod.path("status").asText()) && poPodtekmah.containsKey(zmagovalec)) {
                poPodtekmah.merge(zmagovalec, 1, Integer::sum);
            }
        }
        return poPodtekmah;
    }

    private static StranEkipe zmagovalnaStran(long idZmagovalca, Oznake o, int d, int g) {
        if (idZmagovalca != 0 && idZmagovalca == o.idDomacih()) {
            return StranEkipe.DOMACI;
        }
        if (idZmagovalca != 0 && idZmagovalca == o.idGostov()) {
            return StranEkipe.GOST;
        }
        if (d == g) {
            return null;
        }
        return d > g ? StranEkipe.DOMACI : StranEkipe.GOST;
    }

    // ---------- Skupno: nizi, oznake, strani ----------

    /* Tocke nizov iz polj vira. Polja so stalne dolzine in dopolnjena z
       niclami (niz z vsoto 0 ni obstajal); neveljavne tocke (negativne ali
       taksne, ki jih NiziPravila zavrnejo) se ne zapisejo - tekma ostane z
       dobljenimi nizi, v porocilu pa je opozorilo. */
    List<NizVnos> nizi(JsonNode tocke1, JsonNode tocke2, int dobljeni1, int dobljeni2, int steviloNizov, String opis) {
        List<NizVnos> nizi = new ArrayList<>();
        int dolzina = Math.min(tocke1.size(), tocke2.size());
        for (int i = 0; i < dolzina; i++) {
            int a = tocke1.path(i).asInt();
            int b = tocke2.path(i).asInt();
            if (a < 0 || b < 0) {
                porocilo.opozori("negativne tocke niza pri viru (nizi izpusceni)", opis + ": " + tocke1 + " / " + tocke2);
                return List.of();
            }
            if (a + b > 0) {
                nizi.add(new NizVnos(a, b));
            }
        }
        if (nizi.isEmpty()) {
            return nizi;
        }
        try {
            NiziPravila.preveri(nizi, dobljeni1, dobljeni2, steviloNizov / 2 + 1);
        } catch (NeveljavenVnosIzjema e) {
            porocilo.opozori("tocke nizov pri viru niso veljavne (nizi izpusceni)", opis + ": " + e.getMessage());
            return List.of();
        }
        return nizi;
    }

    static int steviloNizov(int dolzinaPolja) {
        if (dolzinaPolja <= 3 && dolzinaPolja > 0) {
            return 3;
        }
        return dolzinaPolja <= 5 ? 5 : 7;
    }

    static boolean jeDvojice(JsonNode podtekma) {
        if (podtekma.path("match_type").asInt() == 3) {
            return true;
        }
        for (JsonNode stran : podtekma.path("participants")) {
            if (stran.path("participant_details").size() > 1) {
                return true;
            }
        }
        return false;
    }

    private static JsonNode stran(JsonNode podtekma, long idEkipe) {
        for (JsonNode s : podtekma.path("participants")) {
            if (s.path("participant_id").asLong() == idEkipe) {
                return s;
            }
        }
        return null;
    }

    /* Mesto igralca na strani (A/B/C oz. X/Y/Z) po slogu oznak. */
    private static String mesto(JsonNode stran, Slog slog, boolean domaci) {
        for (JsonNode pd : stran.path("participant_details")) {
            String oznaka = oznaka(pd);
            if (oznaka == null) {
                continue;
            }
            if (slog == Slog.CRKE && oznaka.length() == 1) {
                return oznaka;
            }
            Matcher m = Z_STEVILKO.matcher(oznaka);
            if (slog == Slog.IGRALCI && m.matches()) {
                int n = Integer.parseInt(m.group(2));
                if (n >= 1 && n <= 3) {
                    return String.valueOf((char) ((domaci ? 'A' : 'X') + n - 1));
                }
            }
        }
        return null;
    }

    private static String oznaka(JsonNode pd) {
        String o = pd.path("participant_label").asText(null);
        return o == null || o.isBlank() || "null".equals(o) ? null : o.trim().toUpperCase();
    }

    static List<JsonNode> urejeni(JsonNode seznam) {
        List<JsonNode> r = new ArrayList<>();
        seznam.forEach(r::add);
        r.sort(Comparator.comparingInt((JsonNode n) -> n.path("order").asInt()));
        return r;
    }

    // ---------- Kader ----------

    /* Kader ekip tekmovanja: kdor je na seznamu ekipe ali je igral za ekipo.
       Zapise se enkrat na koncu (UNIQUE ekipa+igralec). */
    static final class Kadri {
        private final Map<Long, Ekipa> ekipe = new LinkedHashMap<>();
        private final Map<Long, LinkedHashMap<Long, Object[]>> clani = new LinkedHashMap<>();

        void dodaj(Ekipa ekipa, Igralec igralec, Integer vrstniRed) {
            if (ekipa == null || igralec == null) {
                return;
            }
            ekipe.putIfAbsent(ekipa.getId(), ekipa);
            clani.computeIfAbsent(ekipa.getId(), k -> new LinkedHashMap<>())
                    .putIfAbsent(igralec.getId(), new Object[] {igralec, vrstniRed});
        }

        void shrani(KaderEkipeRepozitorij repozitorij, PorociloUvoza porocilo, boolean enaEkipaNaIgralca) {
            Map<Long, String> ekipaIgralca = new HashMap<>();
            for (Map.Entry<Long, LinkedHashMap<Long, Object[]>> vnos : clani.entrySet()) {
                Ekipa ekipa = ekipe.get(vnos.getKey());
                for (Object[] clan : vnos.getValue().values()) {
                    Igralec igralec = (Igralec) clan[0];
                    String prejsnja = ekipaIgralca.putIfAbsent(igralec.getId(), ekipa.prikazanoIme());
                    if (enaEkipaNaIgralca && prejsnja != null) {
                        porocilo.opozori("igralec v kadru dveh ekip istega dogodka",
                                igralec.polnoIme() + ": " + prejsnja + ", " + ekipa.prikazanoIme());
                    }
                    repozitorij.save(new KaderEkipe(ekipa, igralec, (Integer) clan[1]));
                    porocilo.prestej("igralcev v kadrih");
                }
            }
        }
    }
}
