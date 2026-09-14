/* Uskladitev po zapisu: kar je v bazi, se primerja z virom - tekmo za tekmo.

   Tece v isti transakciji kot zapis, a po izpraznjeni in pocisceni seji, zato
   bere tisto, kar je res v bazi, in ne predmetov, ki jih je preslikava drzala
   v pomnilniku. Obvezne preverbe (tekme, zmagovalci, izidi, mreza, srecanja,
   tocke lestvice) ob neujemanju uvoz ustavijo; primerjava uradnih mest v
   skupinah in na lestvici ga ne - pravilo razvrscanja izenacenih je v Turnirku
   zavestno drugacno (razlika namesto kolicnika), admin pa neujemanje vidi. */
package si.turnirko.uvoz.stupa;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;

import si.turnirko.dto.LestvicaEkipeDto;
import si.turnirko.dto.VrsticaLestviceDto;
import si.turnirko.modeli.FazaTekme;
import si.turnirko.modeli.IzidTekme;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.SerijaKoncnice;
import si.turnirko.modeli.Skupina;
import si.turnirko.modeli.Srecanje;
import si.turnirko.modeli.StatusSrecanja;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.StatusTekmeSrecanja;
import si.turnirko.modeli.Tekma;
import si.turnirko.modeli.TekmaSrecanja;
import si.turnirko.modeli.VlogaIzvora;
import si.turnirko.storitve.LestvicaLigeStoritev;
import si.turnirko.storitve.RazvrstitevStoritev;
import si.turnirko.storitve.SkupineStoritev;
import si.turnirko.uvoz.UvozOblike;

final class UskladitevStupe {

    private static final int NAJVEC_PODROBNOSTI = 8;

    private final RepozitorijiUvoza repo;
    private final RazvrstitevStoritev razvrstitev;
    private final LestvicaLigeStoritev lestvice;
    private final PorociloUvoza porocilo;

    UskladitevStupe(RepozitorijiUvoza repo, RazvrstitevStoritev razvrstitev, LestvicaLigeStoritev lestvice,
                    PorociloUvoza porocilo) {
        this.repo = repo;
        this.razvrstitev = razvrstitev;
        this.lestvice = lestvice;
        this.porocilo = porocilo;
    }

    // ---------------------------------------------------------------------
    // Turnir
    // ---------------------------------------------------------------------

    void turnir(PosnetekDogodka p, SledUvoza sled) {
        repo.em().flush();
        repo.em().clear();
        Map<Long, Long> udelezenecPoPrijavi = obrni(sled.prijave);

        for (JsonNode kat : p.kategorije()) {
            Long idDogodek = sled.dogodki.get(kat.path("id").asLong());
            if (idDogodek == null) {
                continue;
            }
            String ime = UvozOblike.ocisti(kat.path("category_display_label").asText(kat.path("category_description").asText("")));
            List<Tekma> tekme = repo.tekme().najdiZaDogodek(idDogodek);
            Map<Long, Tekma> poId = new HashMap<>();
            tekme.forEach(t -> poId.put(t.getId(), t));

            List<String> neujemanja = new ArrayList<>();
            int odigranihVira = 0;
            List<JsonNode> skupineVira = new ArrayList<>();
            for (JsonNode s : p.stopnjeKategorije(kat.path("id").asLong())) {
                for (JsonNode g : p.skupineStopnje(s.path("id").asLong())) {
                    if (s.path("format_id").asInt() == 1) {
                        skupineVira.add(g);
                    }
                    for (JsonNode t : p.tekmeSkupine(g.path("id").asLong())) {
                        long idVira = t.path("id").asLong();
                        if (sled.izpuscene.contains(idVira)) {
                            continue;
                        }
                        Tekma tk = poId.get(sled.tekme.get(idVira));
                        if (tk == null) {
                            neujemanja.add("ni zapisana: tekma " + idVira);
                            continue;
                        }
                        if (!"SCORED".equals(t.path("status").asText())) {
                            if (tk.getStatus() == StatusTekme.KONCANA) {
                                neujemanja.add("neodigrana pri viru, koncana v bazi: tekma " + idVira);
                            }
                            continue;
                        }
                        odigranihVira++;
                        preveriTekmo(t, tk, sled, neujemanja);
                    }
                }
            }
            long koncanih = tekme.stream()
                    .filter(t -> t.getStatus() == StatusTekme.KONCANA && t.getIzidTip() != IzidTekme.PROSTO).count();
            if (koncanih != odigranihVira) {
                neujemanja.add("odigranih tekem v bazi " + koncanih + ", pri viru " + odigranihVira);
            }
            porocilo.preverba("Tekme", ime + ": " + odigranihVira + " odigranih tekem z istim zmagovalcem in izidom",
                    neujemanja.isEmpty(), podrobnosti(neujemanja));

            preveriMrezo(ime, tekme, poId);
            preveriSrecanja(p, ime, tekme, sled);
            preveriSkupine(ime, skupineVira, tekme, idDogodek, sled, udelezenecPoPrijavi);
        }
    }

    private void preveriTekmo(JsonNode t, Tekma tk, SledUvoza sled, List<String> neujemanja) {
        long idVira = t.path("id").asLong();
        if (tk.getStatus() != StatusTekme.KONCANA) {
            neujemanja.add("odigrana pri viru, v bazi ne: tekma " + idVira);
            return;
        }
        Long zmagovalec = sled.prijave.get(t.path("winner").asLong(0));
        if (zmagovalec != null && (tk.getZmagovalec() == null || !zmagovalec.equals(tk.getZmagovalec().getId()))) {
            neujemanja.add("drug zmagovalec: tekma " + idVira);
        }
        // ekipna tekma: izid so dobljene podtekme (lahko neseste pri viru)
        Map<Long, Integer> izidEkipne = t.path("sub_matches").isEmpty() ? null : SrecanjaStupe.izidEkipneTekme(t);
        for (JsonNode s : t.path("participants")) {
            Long idPrijave = sled.prijave.get(s.path("participant_id").asLong());
            int vir = izidEkipne == null ? s.path("sets_won").asInt()
                    : izidEkipne.getOrDefault(s.path("participant_id").asLong(), 0);
            Integer zapis = idPrijave == null ? null
                    : tk.getPrijava1() != null && idPrijave.equals(tk.getPrijava1().getId()) ? tk.getDobljeniNizi1()
                    : tk.getPrijava2() != null && idPrijave.equals(tk.getPrijava2().getId()) ? tk.getDobljeniNizi2()
                    : null;
            if (zapis == null || zapis != vir) {
                neujemanja.add("drug izid: tekma " + idVira);
                return;
            }
        }
        boolean brezBoja = false;
        for (JsonNode s : t.path("participants")) {
            brezBoja |= s.path("walkover").asBoolean(false);
        }
        if (brezBoja != (tk.getIzidTip() == IzidTekme.BREZ_BOJA)) {
            neujemanja.add("brez boja se ne ujema: tekma " + idVira);
        }
        if (sled.prenesene.contains(idVira) != tk.jePrenesena()) {
            neujemanja.add("prenos izida se ne ujema: tekma " + idVira);
        }
    }

    /* Kdor napreduje, mora biti v naslednji tekmi mreze. */
    private void preveriMrezo(String ime, List<Tekma> tekme, Map<Long, Tekma> poId) {
        List<String> neujemanja = new ArrayList<>();
        int povezav = 0;
        for (Tekma t : tekme) {
            if (t.getFaza() == FazaTekme.SKUPINA) {
                continue;
            }
            for (int stran = 1; stran <= 2; stran++) {
                Long idIzvora = stran == 1 ? t.getIdIzvorTekma1() : t.getIdIzvorTekma2();
                VlogaIzvora vloga = stran == 1 ? t.getVlogaIzvora1() : t.getVlogaIzvora2();
                Tekma izvor = idIzvora == null ? null : poId.get(idIzvora);
                if (izvor == null || izvor.getStatus() != StatusTekme.KONCANA || izvor.getZmagovalec() == null) {
                    continue;
                }
                povezav++;
                Prijava pricakovan = vloga == VlogaIzvora.PORAZENEC ? izvor.porazenec() : izvor.getZmagovalec();
                Prijava dejanski = stran == 1 ? t.getPrijava1() : t.getPrijava2();
                if (pricakovan != null && dejanski != null && !pricakovan.getId().equals(dejanski.getId())) {
                    neujemanja.add("kolo " + t.getKolo() + ", mesto " + t.getPozicija() + ", stran " + stran);
                }
            }
        }
        if (povezav > 0) {
            porocilo.preverba("Mreza", ime + ": " + povezav + " napredovanj v mrezi", neujemanja.isEmpty(),
                    podrobnosti(neujemanja));
        }
    }

    /* Ekipna tekma: srecanje z istimi posamicnimi tekmami kot pri viru. */
    private void preveriSrecanja(PosnetekDogodka p, String ime, List<Tekma> tekme, SledUvoza sled) {
        Map<Long, JsonNode> tekmeVira = new HashMap<>();
        p.tekme().forEach(t -> tekmeVira.put(t.path("id").asLong(), t));
        List<String> neujemanja = new ArrayList<>();
        int srecanj = 0;
        Map<Long, Long> tekmaPoViru = obrni(sled.tekme);
        for (Tekma t : tekme) {
            Long idVira = tekmaPoViru.get(t.getId());
            Long idSrecanje = idVira == null ? null : sled.srecanja.get(idVira);
            if (idSrecanje == null) {
                continue;
            }
            srecanj++;
            Srecanje s = repo.srecanja().findById(idSrecanje).orElse(null);
            if (s == null) {
                neujemanja.add("srecanje ni zapisano: tekma " + idVira);
                continue;
            }
            JsonNode vir = tekmeVira.get(idVira);
            int odigranihVira = 0;
            for (JsonNode pod : vir.path("sub_matches")) {
                if ("SCORED".equals(pod.path("status").asText())) {
                    odigranihVira++;
                }
            }
            List<TekmaSrecanja> posamicne = repo.tekmeSrecanj().najdiZaSrecanje(idSrecanje);
            long koncanih = posamicne.stream().filter(x -> x.getStatus() == StatusTekmeSrecanja.KONCANA).count();
            if (koncanih != odigranihVira) {
                neujemanja.add("posamicnih tekem " + koncanih + ", pri viru " + odigranihVira + ": tekma " + idVira);
            }
            preveriIgralce(vir, posamicne, neujemanja, idVira);
            if (s.getStatus() == StatusSrecanja.KONCANO && s.getDobljeneDomaci() + s.getDobljeneGost()
                    != t.getDobljeniNizi1() + t.getDobljeniNizi2()) {
                neujemanja.add("izid srecanja " + s.getDobljeneDomaci() + ":" + s.getDobljeneGost()
                        + " ni izid tekme " + t.getDobljeniNizi1() + ":" + t.getDobljeniNizi2());
            }
        }
        if (srecanj > 0) {
            porocilo.preverba("Srecanja", ime + ": " + srecanj + " ekipnih srecanj s posamicnimi tekmami vira",
                    neujemanja.isEmpty(), podrobnosti(neujemanja));
        }
    }

    /* Uradna mesta v skupinah proti razvrstitvi Turnirka. */
    private void preveriSkupine(String ime, List<JsonNode> skupineVira, List<Tekma> tekme, Long idDogodek,
                                SledUvoza sled, Map<Long, Long> udelezenecPoPrijavi) {
        List<Prijava> prijave = repo.prijave().najdiZaDogodek(idDogodek);
        int preverjenih = 0;
        List<String> neujemanja = new ArrayList<>();
        for (JsonNode g : skupineVira) {
            Map<Long, Integer> uradna = new HashMap<>();
            for (JsonNode c : g.path("participants")) {
                if (c.path("group_rank").isInt()) {
                    uradna.put(c.path("participant_id").asLong(), c.path("group_rank").asInt());
                }
            }
            Long idSkupina = sled.skupine.get(g.path("id").asLong());
            if (uradna.isEmpty()) {
                continue;
            }
            List<Tekma> tekmeSkupine = tekme.stream()
                    .filter(t -> t.getFaza() == FazaTekme.SKUPINA && Objects.equals(t.getIdSkupina(), idSkupina))
                    .toList();
            if (tekmeSkupine.isEmpty() || tekmeSkupine.stream().anyMatch(t -> t.getStatus() != StatusTekme.KONCANA)) {
                continue;
            }
            List<Prijava> clani;
            if (idSkupina == null) {
                clani = prijave.stream().filter(pr -> pr.getStatus() == Prijava.StatusPrijave.PRIJAVLJEN).toList();
            } else {
                Skupina sk = repo.skupine().findById(idSkupina).orElseThrow();
                clani = SkupineStoritev.clani(sk, prijave, tekme);
            }
            preverjenih++;
            List<String> razlike = new ArrayList<>();
            for (VrsticaLestviceDto v : razvrstitev.lestvica(clani, tekmeSkupine)) {
                Integer uradno = uradna.get(udelezenecPoPrijavi.get(v.idPrijave()));
                if (uradno != null && !uradno.equals(v.mesto())) {
                    razlike.add(v.polnoIme() + " " + v.mesto() + ". (uradno " + uradno + ".)");
                }
            }
            if (!razlike.isEmpty()) {
                neujemanja.add(UvozOblike.ocisti(g.path("name").asText("")) + ": " + String.join(", ", razlike));
            }
        }
        if (preverjenih > 0) {
            porocilo.preverbaRazvrstitve("Skupine", ime + ": " + preverjenih + " skupin po uradnem vrstnem redu",
                    neujemanja.isEmpty(), podrobnosti(neujemanja));
        }
    }

    // ---------------------------------------------------------------------
    // Liga
    // ---------------------------------------------------------------------

    void liga(PosnetekDogodka p, SledUvoza sled) {
        if (sled.idLiga == null) {
            return;
        }
        repo.em().flush();
        repo.em().clear();
        Long idLiga = sled.idLiga;
        Map<Long, Srecanje> srecanja = new HashMap<>();
        repo.srecanja().najdiZaLigo(idLiga).forEach(s -> srecanja.put(s.getId(), s));

        List<String> neujemanja = new ArrayList<>();
        int odigranihVira = 0;
        int posamicnihVira = 0;
        long posamicnihBaza = 0;
        List<JsonNode> redneSkupine = new ArrayList<>();
        for (JsonNode kat : p.kategorije()) {
            for (JsonNode st : p.stopnjeKategorije(kat.path("id").asLong())) {
                for (JsonNode g : p.skupineStopnje(st.path("id").asLong())) {
                    if (st.path("format_id").asInt() == 1) {
                        redneSkupine.add(g);
                    }
                    for (JsonNode t : p.tekmeSkupine(g.path("id").asLong())) {
                        long idVira = t.path("id").asLong();
                        if (sled.izpuscene.contains(idVira)) {
                            continue;
                        }
                        Srecanje s = srecanja.get(sled.srecanja.get(idVira));
                        if (s == null) {
                            neujemanja.add("ni zapisano: srecanje " + idVira);
                            continue;
                        }
                        if (!"SCORED".equals(t.path("status").asText())) {
                            continue;
                        }
                        odigranihVira++;
                        if (s.getStatus() != StatusSrecanja.KONCANO) {
                            neujemanja.add("odigrano pri viru, v bazi ne: srecanje " + idVira);
                            continue;
                        }
                        Map<Long, Integer> izidVira = SrecanjaStupe.izidEkipneTekme(t);
                        for (JsonNode stran : t.path("participants")) {
                            Long idEkipa = sled.ekipe.get(stran.path("participant_id").asLong());
                            int vir = izidVira.getOrDefault(stran.path("participant_id").asLong(), 0);
                            int zapis = s.getEkipaDomaci().getId().equals(idEkipa) ? s.getDobljeneDomaci()
                                    : s.getEkipaGost().getId().equals(idEkipa) ? s.getDobljeneGost() : -1;
                            if (zapis != vir) {
                                neujemanja.add("drug izid: srecanje " + idVira + " (" + UvozOblike.ocisti(t.path("round").path("name").asText("")) + ")");
                                break;
                            }
                        }
                        for (JsonNode pod : t.path("sub_matches")) {
                            if ("SCORED".equals(pod.path("status").asText())) {
                                posamicnihVira++;
                            }
                        }
                        List<TekmaSrecanja> posamicne = repo.tekmeSrecanj().najdiZaSrecanje(s.getId());
                        posamicnihBaza += posamicne.stream()
                                .filter(x -> x.getStatus() == StatusTekmeSrecanja.KONCANA).count();
                        preveriIgralce(t, posamicne, neujemanja, idVira);
                    }
                }
            }
        }
        long koncanihBaza = srecanja.values().stream().filter(s -> s.getStatus() == StatusSrecanja.KONCANO).count();
        if (koncanihBaza != odigranihVira) {
            neujemanja.add("odigranih srecanj v bazi " + koncanihBaza + ", pri viru " + odigranihVira);
        }
        porocilo.preverba("Srecanja", p.ime() + ": " + odigranihVira + " odigranih srecanj z istim izidom",
                neujemanja.isEmpty(), podrobnosti(neujemanja));
        porocilo.preverba("Posamicne tekme", p.ime() + ": " + posamicnihVira + " odigranih posamicnih tekem in dvojic",
                posamicnihVira == posamicnihBaza, posamicnihVira == posamicnihBaza ? null
                        : "v bazi " + posamicnihBaza + ", pri viru " + posamicnihVira);

        preveriLestvico(p, idLiga, redneSkupine, sled);
        preveriSerije(p, idLiga, sled);
    }

    private void preveriLestvico(PosnetekDogodka p, Long idLiga, List<JsonNode> redneSkupine, SledUvoza sled) {
        Map<Long, int[]> uradno = new HashMap<>();
        for (JsonNode g : redneSkupine) {
            for (JsonNode c : g.path("participants")) {
                Long idEkipa = sled.ekipe.get(c.path("participant_id").asLong());
                if (idEkipa != null && c.path("group_points").isNumber()) {
                    uradno.put(idEkipa, new int[] {c.path("group_points").asInt(), c.path("group_rank").asInt(0)});
                }
            }
        }
        if (uradno.isEmpty()) {
            return;
        }
        List<String> tocke = new ArrayList<>();
        List<String> mesta = new ArrayList<>();
        for (LestvicaEkipeDto v : lestvice.lestvica(idLiga)) {
            int[] u = uradno.get(v.idEkipa());
            if (u == null) {
                continue;
            }
            if (u[0] != v.tocke()) {
                tocke.add(v.ekipa() + ": " + v.tocke() + " tock (uradno " + u[0] + ")");
            }
            if (u[1] > 0 && u[1] != v.mesto()) {
                mesta.add(v.ekipa() + ": " + v.mesto() + ". (uradno " + u[1] + ".)");
            }
        }
        String opisTock = p.ime() + ": tocke rednega dela se ujemajo z uradno lestvico";
        if (sled.tockovanjeSkladno) {
            porocilo.preverba("Lestvica", opisTock, tocke.isEmpty(), podrobnosti(tocke));
        } else {
            // uradne tocke ne sledijo nobenemu tockovanju - izidi so preverjeni zgoraj
            porocilo.preverbaRazvrstitve("Lestvica", opisTock + " (tockovanje vira ni skladno)",
                    tocke.isEmpty(), podrobnosti(tocke));
        }
        porocilo.preverbaRazvrstitve("Lestvica", p.ime() + ": vrstni red rednega dela po uradni lestvici",
                mesta.isEmpty(), podrobnosti(mesta));
    }

    /* Zmagovalec vsake serije po zmagah v tekmah vira. */
    private void preveriSerije(PosnetekDogodka p, Long idLiga, SledUvoza sled) {
        List<SerijaKoncnice> serije = repo.serije().najdiZaLigo(idLiga);
        if (serije.isEmpty()) {
            return;
        }
        Map<Long, Long> ekipaPoViru = sled.ekipe;
        Map<String, Map<Long, Integer>> zmagePoSerijah = new HashMap<>();
        for (JsonNode t : p.tekme()) {
            JsonNode st = p.stopnjaTekme(t);
            if (st == null || st.path("format_id").asInt() != 2 || !"SCORED".equals(t.path("status").asText())) {
                continue;
            }
            long zmagovalec = t.path("winner").asLong(0);
            if (zmagovalec == 0) {
                List<JsonNode> strani = SrecanjaStupe.urejeni(t.path("participants"));
                if (strani.size() == 2 && strani.get(0).path("sets_won").asInt() != strani.get(1).path("sets_won").asInt()) {
                    zmagovalec = (strani.get(0).path("sets_won").asInt() > strani.get(1).path("sets_won").asInt()
                            ? strani.get(0) : strani.get(1)).path("participant_id").asLong();
                }
            }
            String kljuc = t.path("round").path("order").asInt() + "|" + t.path("order").asInt();
            zmagePoSerijah.computeIfAbsent(kljuc, k -> new HashMap<>()).merge(zmagovalec, 1, Integer::sum);
        }
        Integer potrebno = repo.lige().findById(idLiga).orElseThrow().getKoncnicaZmag();
        List<String> neujemanja = new ArrayList<>();
        int odlocenih = 0;
        List<Integer> kola = p.tekme().stream().filter(t -> {
            JsonNode st = p.stopnjaTekme(t);
            return st != null && st.path("format_id").asInt() == 2;
        }).map(t -> t.path("round").path("order").asInt()).distinct().sorted().toList();
        int stKrogov = serije.stream().mapToInt(SerijaKoncnice::getKrog).max().orElse(0);
        for (Map.Entry<String, Map<Long, Integer>> e : zmagePoSerijah.entrySet()) {
            String[] deli = e.getKey().split("\\|");
            int krog = kola.indexOf(Integer.parseInt(deli[0])) + 1 + (stKrogov - kola.size());
            int par = Integer.parseInt(deli[1]);
            Long zmagovalecVira = e.getValue().entrySet().stream()
                    .filter(z -> potrebno != null && z.getValue() >= potrebno)
                    .map(z -> ekipaPoViru.get(z.getKey())).findFirst().orElse(null);
            SerijaKoncnice serija = serije.stream().filter(s -> s.getKrog() == krog && s.getPar() == par).findFirst().orElse(null);
            Long zmagovalecBaza = serija == null || serija.getZmagovalec() == null ? null : serija.getZmagovalec().getId();
            if (zmagovalecVira != null) {
                odlocenih++;
            }
            if (!Objects.equals(zmagovalecVira, zmagovalecBaza)) {
                neujemanja.add("krog " + krog + ", par " + par);
            }
        }
        porocilo.preverba("Koncnica", p.ime() + ": " + odlocenih + " odlocenih serij z istim zmagovalcem",
                neujemanja.isEmpty(), podrobnosti(neujemanja));
    }

    /* Odigrana posamicna tekma vira z igralci na obeh straneh mora biti v bazi
       odigrana tekma z vsemi igralci - sicer bi brez njih izpadla iz ratinga
       in statistike, stevila pa bi se se vedno ujemala. */
    private static void preveriIgralce(JsonNode vir, List<TekmaSrecanja> posamicne, List<String> neujemanja, long idVira) {
        Map<Integer, TekmaSrecanja> poZaporedju = new HashMap<>();
        posamicne.forEach(x -> poZaporedju.put(x.getZaporedje(), x));
        for (JsonNode pod : vir.path("sub_matches")) {
            if (!"SCORED".equals(pod.path("status").asText()) || pod.path("participants").size() != 2) {
                continue;
            }
            boolean preskoci = false;
            for (JsonNode s : pod.path("participants")) {
                preskoci |= s.path("walkover").asBoolean(false) || s.path("participant_details").isEmpty();
            }
            if (preskoci) {
                continue;
            }
            TekmaSrecanja ts = poZaporedju.get(pod.path("order").asInt());
            boolean dvojice = SrecanjaStupe.jeDvojice(pod);
            if (ts == null || ts.getIzidTip() != IzidTekme.IGRANO || ts.getIgralecDomaci() == null
                    || ts.getIgralecGost() == null
                    || (dvojice && (ts.getIgralecDomaci2() == null || ts.getIgralecGost2() == null))) {
                neujemanja.add("posamicna tekma brez igralca: srecanje " + idVira + ", tekma " + pod.path("order").asInt());
            }
        }
    }

    // ---------------------------------------------------------------------

    private static Map<Long, Long> obrni(Map<Long, Long> m) {
        Map<Long, Long> r = new HashMap<>();
        m.forEach((k, v) -> r.putIfAbsent(v, k));
        return r;
    }

    private static String podrobnosti(List<String> neujemanja) {
        if (neujemanja.isEmpty()) {
            return null;
        }
        List<String> prvi = neujemanja.subList(0, Math.min(NAJVEC_PODROBNOSTI, neujemanja.size()));
        return String.join("; ", prvi) + (neujemanja.size() > prvi.size()
                ? " ... (se " + (neujemanja.size() - prvi.size()) + ")" : "");
    }
}
