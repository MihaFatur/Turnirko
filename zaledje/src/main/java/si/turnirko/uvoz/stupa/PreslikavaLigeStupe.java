/* Liga iz Stupe v Turnirko: liga -> ekipe s kadrom -> srecanja rednega dela
   -> koncnica (serije in tekme serij).

   Preslikava, ki ni ocitna:

   * KONCNICA je pri viru izlocilna stopnja ("PLAY-OFF", pri kvalifikacijah
     med ligami "QUALIFYING") s kroga "Semi"/"Final" in tekmami z oznako
     leg 1..3. Turnirko jo zapise kot serije (krog, par) in srecanja serij -
     lestvica rednega dela jih tako ne steje (prejsnji uvoz jih je zlozil v
     1. in 2. kolo in 1. SNTL moskih je imela napacno lestvico).
   * Tretja tekma serije, ki je ni bilo treba odigrati, je pri viru NOT_STARTED
     z meta.auto_skip_reason - v Turnirku je ni (enako kot pri zivi koncnici).
   * Stran 1 serije je bolje uvrscena ekipa rednega dela: uradno mesto iz
     skupine vira, kjer ga ni, lestvica Turnirka.
   * Ekipe in srecanja obdrzijo id ob ponovnem uvozu (povezave), vsebina
     srecanj se zapise znova. */
package si.turnirko.uvoz.stupa;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.fasterxml.jackson.databind.JsonNode;

import si.turnirko.dto.LestvicaEkipeDto;
import si.turnirko.modeli.Ekipa;
import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.Klub;
import si.turnirko.modeli.Liga;
import si.turnirko.modeli.PredlogaLige;
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.modeli.SerijaKoncnice;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.Srecanje;
import si.turnirko.modeli.StatusSrecanja;
import si.turnirko.modeli.StatusTekmovanja;
import si.turnirko.modeli.VirTekmovanja;
import si.turnirko.modeli.ZunanjaPovezava;
import si.turnirko.storitve.LestvicaLigeStoritev;
import si.turnirko.uvoz.UvozOblike;

final class PreslikavaLigeStupe {

    private final RepozitorijiUvoza repo;
    private final IdentitetaStupe identiteta;
    private final SrecanjaStupe srecanja;
    private final LestvicaLigeStoritev lestvice;
    private final PorociloUvoza porocilo;
    private final SledUvoza sled;

    PreslikavaLigeStupe(RepozitorijiUvoza repo, IdentitetaStupe identiteta, SrecanjaStupe srecanja,
                        LestvicaLigeStoritev lestvice, PorociloUvoza porocilo, SledUvoza sled) {
        this.repo = repo;
        this.identiteta = identiteta;
        this.srecanja = srecanja;
        this.lestvice = lestvice;
        this.porocilo = porocilo;
        this.sled = sled;
    }

    Liga uvozi(PosnetekDogodka p) {
        if (p.kategorije().size() != 1) {
            porocilo.napaka("liga mora imeti natanko eno kategorijo", p.ime() + ": " + p.kategorije().size());
            return null;
        }
        JsonNode kat = p.kategorije().get(0);
        List<JsonNode> stopnje = p.stopnjeKategorije(kat.path("id").asLong());
        List<JsonNode> redne = stopnje.stream().filter(s -> s.path("format_id").asInt() == 1).toList();
        List<JsonNode> izlocilne = stopnje.stream().filter(s -> s.path("format_id").asInt() == 2).toList();
        if (redne.size() > 1 || izlocilne.size() > 1) {
            porocilo.napaka("liga z vec rednimi deli ali vec koncnicami", p.ime());
            return null;
        }
        List<JsonNode> redneTekme = new ArrayList<>();
        redne.forEach(s -> p.skupineStopnje(s.path("id").asLong())
                .forEach(g -> redneTekme.addAll(p.tekmeSkupine(g.path("id").asLong()))));
        List<JsonNode> tekmeKoncnice = new ArrayList<>();
        izlocilne.forEach(s -> p.skupineStopnje(s.path("id").asLong())
                .forEach(g -> tekmeKoncnice.addAll(p.tekmeSkupine(g.path("id").asLong()))));
        List<JsonNode> vse = new ArrayList<>(redneTekme);
        vse.addAll(tekmeKoncnice);

        FormatSrecanja format = SrecanjaStupe.dolociFormat(vse, true, porocilo, p.ime());
        if (format == null) {
            if (vse.stream().anyMatch(t -> !t.path("sub_matches").isEmpty())) {
                return null; // napaka je ze v porocilu
            }
            format = FormatSrecanja.SNTL;
            porocilo.opozori("format srecanj pri viru se ni znan (privzet SNTL)", p.ime());
        }

        Liga liga = PovezaveStupe.lokalni(repo.povezave(), ZunanjaPovezava.Vrsta.LIGA, p.id())
                .flatMap(id -> repo.lige().findById(id))
                .orElseGet(Liga::new);
        String ime = UvozOblike.prirezi(p.ime() == null ? null : p.ime().replaceAll("\\s+", " "), 80);
        liga.setIme(ime == null || ime.length() < 3 ? "Liga " + p.id() : ime);
        liga.setSezona(UvozOblike.prirezi(p.imeSezone(), 20));
        liga.setSpolKategorija(switch (kat.path("gender_id").asInt()) {
            case 1 -> SpolKategorija.MOSKI;
            case 2 -> SpolKategorija.ZENSKE;
            default -> SpolKategorija.MESANO;
        });
        liga.setFormatSrecanja(format);
        liga.setSteviloNizov(steviloNizov(vse));
        liga.setZmagZaSrecanje(format.stTekem() / 2 + 1);
        // pri sodem stevilu tekem (SNTL: 10) se srecanje lahko konca 5 : 5 -
        // vir tak izid zapise brez zmagovalca in ga tockuje kot neodlocenega
        liga.setDovoljenoNeodloceno(format.stTekem() % 2 == 0);
        liga.setDvokrozno(redneTekme.stream().anyMatch(t -> t.path("leg").asInt(0) >= 2) || dvokroznoPoKolih(p, redne));
        liga.setRaven(RavenTekmovanja.URADNO);
        liga.setVir(VirTekmovanja.STUPA);
        liga.setPredlogaListka(liga.getIme().trim().startsWith("1.") ? PredlogaLige.SNTL_1 : PredlogaLige.SNTL_23);
        liga.setZacetekPrvegaKola(zacetek(p, vse));
        liga.setRazmikDni(null);

        int krogov = 0;
        int zmag = 0;
        if (!izlocilne.isEmpty()) {
            JsonNode sk = izlocilne.get(0);
            krogov = (int) tekmeKoncnice.stream().map(t -> t.path("round").path("order").asInt()).distinct().count();
            int kol = p.skupineStopnje(sk.path("id").asLong()).stream()
                    .mapToInt(g -> p.kolaSkupine(g.path("id").asLong()).size()).max().orElse(0);
            krogov = Math.max(krogov, kol);
            int tekemSerije = sk.path("configuration").path("legs").asInt(0);
            for (JsonNode t : tekmeKoncnice) {
                tekemSerije = Math.max(tekemSerije, t.path("leg").asInt(1));
            }
            zmag = (Math.max(1, tekemSerije) + 1) / 2;
            int ekip = 1 << Math.max(1, krogov);
            if (ekip != 2 && ekip != 4 && ekip != 8) {
                porocilo.napaka("koncnica lige z " + ekip + " ekipami - Turnirko pozna 2, 4 ali 8", p.ime());
                return null;
            }
            liga.setKoncnicaEkip(ekip);
            liga.setKoncnicaZmag(Math.min(4, zmag));
        } else {
            liga.setKoncnicaEkip(null);
            liga.setKoncnicaZmag(null);
        }
        liga.setStatus(StatusTekmovanja.PRIPRAVA);
        liga = repo.lige().save(liga);
        PovezaveStupe.povezi(repo.povezave(), ZunanjaPovezava.Vrsta.LIGA, p.id(), liga.getId());
        sled.idLiga = liga.getId();
        porocilo.prestej("lig");
        identiteta.najaviImena(PreslikavaTurnirjaStupe.imenaOseb(p));

        LocalDate datum = p.zacetek();
        SrecanjaStupe.Kadri kadri = new SrecanjaStupe.Kadri();
        Map<Long, Ekipa> ekipe = ekipe(p, kat, liga, vse, kadri, datum);

        Set<Long> ohranjenaSrecanja = new HashSet<>();
        List<Srecanje> vsaSrecanja = new ArrayList<>();
        for (JsonNode t : redneTekme) {
            Srecanje s = srecanje(p, liga, t, format, ekipe, kadri, Math.max(1, t.path("round").path("order").asInt(1)), null, null);
            if (s != null) {
                ohranjenaSrecanja.add(s.getId());
                vsaSrecanja.add(s);
            }
        }
        // pred koncnico: mesta rednega dela brez uradnih mest berejo lestvico
        sled.tockovanjeSkladno = tockovanje(p, liga, redne, ekipe, vsaSrecanja);
        liga = repo.lige().save(liga);
        if (!izlocilne.isEmpty()) {
            repo.em().flush();
            koncnica(p, liga, redne, tekmeKoncnice, format, ekipe, kadri, krogov, ohranjenaSrecanja, vsaSrecanja);
        }
        kadri.shrani(repo.kadri(), porocilo, false);

        // srecanja in ekipe prejsnjega uvoza, ki jih vir ne pozna vec
        repo.em().flush();
        for (Srecanje s : repo.srecanja().najdiZaLigo(liga.getId())) {
            if (!ohranjenaSrecanja.contains(s.getId())) {
                repo.em().detach(s);
                CiscenjeUvoza.srecanjeLige(repo.em(), s.getId());
                porocilo.opozori("srecanje, ki ga vir ne pozna vec, je izbrisano", "id " + s.getId());
            }
        }
        Set<Long> ohranjeneEkipe = new HashSet<>();
        ekipe.values().forEach(e -> ohranjeneEkipe.add(e.getId()));
        for (Ekipa e : repo.ekipe().najdiZaLigo(liga.getId())) {
            if (!ohranjeneEkipe.contains(e.getId())) {
                repo.em().detach(e);
                CiscenjeUvoza.ekipaLige(repo.em(), e.getId());
                porocilo.opozori("ekipa, ki je vir ne pozna vec, je izbrisana", e.prikazanoIme());
            }
        }

        liga.setStatus(vsaSrecanja.isEmpty() ? StatusTekmovanja.PRIPRAVA
                : vsaSrecanja.stream().allMatch(s -> s.getStatus() == StatusSrecanja.KONCANO)
                        && (izlocilne.isEmpty() || koncnicaOdlocena(liga))
                        ? StatusTekmovanja.ZAKLJUCEN : StatusTekmovanja.V_TEKU);
        return repo.lige().save(liga);
    }

    // ---------- Ekipe ----------

    private Map<Long, Ekipa> ekipe(PosnetekDogodka p, JsonNode kat, Liga liga, List<JsonNode> tekme,
                                   SrecanjaStupe.Kadri kadri, LocalDate datum) {
        Set<Long> vTekmah = new HashSet<>();
        for (JsonNode t : tekme) {
            t.path("participants").forEach(s -> vTekmah.add(s.path("participant_id").asLong()));
        }
        long idKategorijeUdelezencev = kat.path("category_id").asLong();
        List<JsonNode> udelezenci = new ArrayList<>();
        for (JsonNode u : p.udelezenci()) {
            boolean igrala = vTekmah.contains(u.path("id").asLong());
            if (igrala) {
                udelezenci.add(u);
            } else if (u.path("category_id").asLong() != idKategorijeUdelezencev || u.path("is_deleted").asBoolean(false)) {
                continue;
            } else if (u.path("is_excluded").asBoolean(false)) {
                // izkljucena prijava (2. SNTL M 2025/26: podvojena prijava kluba) ni v
                // skupini in ne igra - kot ekipa bi stala na lestvici z 0 srecanji
                porocilo.prestej("izkljucenih ekip pri viru (izpuscene)");
            } else {
                udelezenci.add(u);
            }
        }
        udelezenci.sort(Comparator.comparingLong(u -> u.path("id").asLong()));

        // obstojece ekipe najprej na zacasne vrednosti, da prestevilcenje ne trci z UNIQUE
        List<Ekipa> obstojece = repo.ekipe().najdiZaLigo(liga.getId());
        for (Ekipa e : obstojece) {
            e.setZaporedna(1000 + e.getZaporedna());
            e.setIme(e.jeProsta() ? "#" + e.getId() : e.getIme());
        }
        repo.em().flush();

        Map<Long, Ekipa> ekipe = new HashMap<>();
        Map<Long, Integer> zaporednaPoKlubu = new HashMap<>();
        Set<String> imena = new HashSet<>();
        for (JsonNode u : udelezenci) {
            long id = u.path("id").asLong();
            Klub klub = identiteta.klub(klubEkipe(u));
            String ime = UvozOblike.prirezi(u.path("participant_name").asText(null), 60);
            if (ime == null || ime.length() < 2) {
                ime = klub != null ? UvozOblike.prirezi(klub.getIme(), 60) : "Ekipa " + id;
            }
            String osnovno = ime;
            int n = 2;
            while (!imena.add(ime.toUpperCase())) {
                ime = UvozOblike.prirezi(osnovno, 55) + " (" + n++ + ")";
            }
            int zaporedna = klub == null ? 1 : zaporednaPoKlubu.merge(klub.getId(), 1, Integer::sum);
            final String koncnoIme = ime;
            Ekipa ekipa = PovezaveStupe.lokalni(repo.povezave(), ZunanjaPovezava.Vrsta.EKIPA, id)
                    .flatMap(x -> repo.ekipe().findById(x))
                    .filter(e -> e.getLiga() != null && e.getLiga().getId().equals(liga.getId()))
                    .map(e -> {
                        e.setKlub(klub);
                        e.setZaporedna(zaporedna);
                        e.setIme(koncnoIme);
                        return e;
                    })
                    .orElseGet(() -> new Ekipa(liga, klub, zaporedna, koncnoIme));
            ekipa = repo.ekipe().save(ekipa);
            PovezaveStupe.povezi(repo.povezave(), ZunanjaPovezava.Vrsta.EKIPA, id, ekipa.getId());
            ekipe.put(id, ekipa);
            sled.ekipe.put(id, ekipa.getId());
            porocilo.prestej("ekip");
            for (JsonNode clan : SrecanjaStupe.urejeni(u.path("event_participant_details"))) {
                if (clan.path("is_deleted").asBoolean(false)) {
                    continue;
                }
                kadri.dodaj(ekipa, identiteta.igralec(IdentitetaStupe.oseba(clan), datum),
                        clan.path("order").asInt(0) > 0 ? clan.path("order").asInt() : null);
            }
        }
        for (Long id : vTekmah) {
            if (id != 0 && !ekipe.containsKey(id)) {
                porocilo.napaka("ekipa v srecanju ni med ekipami lige", p.ime() + ": udelezenec " + id);
            }
        }
        return ekipe;
    }

    // ---------- Srecanja ----------

    private Srecanje srecanje(PosnetekDogodka p, Liga liga, JsonNode t, FormatSrecanja format, Map<Long, Ekipa> ekipe,
                              SrecanjaStupe.Kadri kadri, int kolo, SerijaKoncnice serija, Integer tekmaVSeriji) {
        List<JsonNode> strani = SrecanjaStupe.urejeni(t.path("participants"));
        String opis = opis(p, t, ekipe);
        if (strani.size() < 2) {
            porocilo.prestej("prostih terminov (liho stevilo ekip)");
            sled.izpuscene.add(t.path("id").asLong());
            return null;
        }
        String status = t.path("status").asText();
        boolean odigrano = "SCORED".equals(status);
        if (!odigrano && serija != null && t.path("meta").hasNonNull("auto_skip_reason")) {
            porocilo.prestej("tekem koncnice, ki jih ni bilo treba odigrati");
            sled.izpuscene.add(t.path("id").asLong());
            return null;
        }
        if (!odigrano && p.jeMimo()) {
            porocilo.opozori("neodigrano srecanje koncane lige (izpusceno)", opis);
            sled.izpuscene.add(t.path("id").asLong());
            return null;
        }
        SrecanjaStupe.Oznake o = SrecanjaStupe.oznake(t);
        Ekipa domaci = ekipe.get(o.idDomacih());
        Ekipa gost = ekipe.get(o.idGostov());
        if (domaci == null || gost == null || domaci.getId().equals(gost.getId())) {
            sled.izpuscene.add(t.path("id").asLong());
            return null; // napaka je ze v porocilu (ekipa ni med ekipami lige)
        }
        Srecanje s = PovezaveStupe.lokalni(repo.povezave(), ZunanjaPovezava.Vrsta.SRECANJE, t.path("id").asLong())
                .flatMap(id -> repo.srecanja().findById(id))
                .filter(x -> x.getLiga() != null && x.getLiga().getId().equals(liga.getId()))
                .orElse(null);
        if (s == null) {
            s = new Srecanje(liga, kolo, domaci, gost);
        } else {
            s.setKolo(kolo);
            s.nastaviEkipi(domaci, gost);
        }
        s.nastaviSerijo(serija, tekmaVSeriji);
        s.setPredvidenZacetek(UvozOblike.casovniZig(t.path("start_time").asText(null)));
        s.setOdigranOb(null);
        s.setStatus(odigrano ? StatusSrecanja.KONCANO
                : "IN_PROGRESS".equals(status) ? StatusSrecanja.POTEKA : StatusSrecanja.RAZPORED);
        // vir oznaci tekmo brez borbe z walkover na strani zmagovalca (izid 5 : 0 ali 4 : 0)
        s.setBrezBoja(odigrano && strani.stream().anyMatch(x -> x.path("walkover").asBoolean(false)));
        s.setDobljeneDomaci(0);
        s.setDobljeneGost(0);
        s = repo.srecanja().save(s);
        PovezaveStupe.povezi(repo.povezave(), ZunanjaPovezava.Vrsta.SRECANJE, t.path("id").asLong(), s.getId());
        sled.srecanja.put(t.path("id").asLong(), s.getId());

        Map<Long, Integer> izidVira = SrecanjaStupe.izidEkipneTekme(t);
        int virDoma = izidVira.getOrDefault(o.idDomacih(), 0);
        int virGost = izidVira.getOrDefault(o.idGostov(), 0);
        if (!t.path("sub_matches").isEmpty()) {
            SrecanjaStupe.Rezultat r = srecanja.zapisi(s, t, o, format, p.zacetek(), ekipe, kadri, odigrano, opis);
            s.setDobljeneDomaci(r.dobljeneDomaci());
            s.setDobljeneGost(r.dobljeneGost());
            if (odigrano && (r.dobljeneDomaci() != virDoma || r.dobljeneGost() != virGost)) {
                porocilo.napaka("izid srecanja se ne ujema s posamicnimi tekmami", opis + ": vir " + virDoma + ":"
                        + virGost + ", posamicne " + r.dobljeneDomaci() + ":" + r.dobljeneGost());
            }
        } else if (odigrano) {
            // srecanje brez posamicnih tekem (b.b.): izid je izid vira
            s.setDobljeneDomaci(virDoma);
            s.setDobljeneGost(virGost);
            porocilo.prestej(strani.stream().anyMatch(x -> x.path("walkover").asBoolean(false))
                    ? "srecanj brez boja" : "srecanj brez posamicnih tekem");
        }
        if (odigrano) {
            porocilo.prestej(serija == null ? "odigranih srecanj" : "odigranih tekem koncnice");
        }
        return repo.srecanja().save(s);
    }

    // ---------- Tockovanje ----------

    /* Mogoca tockovanja {zmaga, neodloceno, poraz}: privzeto 2-1-0 prvo, nato
       po oddaljenosti od njega. Poraz nikoli ne prinese vec kot neodloceno in
       neodloceno ne vec kot zmaga. */
    private static final List<int[]> TOCKOVANJA = tockovanja();

    private static List<int[]> tockovanja() {
        List<int[]> r = new ArrayList<>();
        for (int zmaga = 1; zmaga <= 3; zmaga++) {
            for (int neodloceno = 0; neodloceno <= zmaga; neodloceno++) {
                for (int poraz = 0; poraz <= neodloceno && poraz < zmaga; poraz++) {
                    r.add(new int[] {zmaga, neodloceno, poraz});
                }
            }
        }
        r.sort(Comparator.comparingInt((int[] t) -> Math.abs(t[0] - 2) + Math.abs(t[1] - 1) + t[2]));
        return r;
    }

    /* Tockovanje lige po uradni lestvici vira. Pravila SNTL se med sezonami
       razlikujejo (1. SNTL moskih 2024/25 je tocko dajala tudi za poraz,
       2025/26 ne vec), zato jih preslikava ne privzame: poisce zmago,
       neodloceno, poraz in odbitek za poraz brez borbe, s katerimi izkupicki
       uvozenih srecanj dajo natanko uradne tocke VSEH ekip. Odbitek ena je
       pravilo SNTL (clen 20 Pravil igranja) in ima prednost, kadar ga podatki
       ne dolocijo (poraza brez borbe ni bilo). Kadar se ne ujema nobeno (vir
       sam s sabo ni skladen, kazenske tocke ali "tocke", ki niso tocke),
       ostane 2-1-0 z odbitkom ena in uskladitev tock uvoza ne ustavi - izidi
       srecanj so preverjeni vsak posebej, razhaja se samo tockovanje vira.
       Vrne, ali je tockovanje vira skladno. */
    private boolean tockovanje(PosnetekDogodka p, Liga liga, List<JsonNode> redne, Map<Long, Ekipa> ekipe,
                               List<Srecanje> srecanja) {
        liga.setTockeZmaga(2);
        liga.setTockeNeodloceno(1);
        liga.setTockePoraz(0);
        liga.setOdbitekBrezBoja(1);
        Map<Long, Integer> uradno = new HashMap<>();
        for (JsonNode s : redne) {
            for (JsonNode g : p.skupineStopnje(s.path("id").asLong())) {
                for (JsonNode c : g.path("participants")) {
                    Ekipa e = ekipe.get(c.path("participant_id").asLong());
                    if (e != null && c.path("group_points").isNumber()) {
                        uradno.put(e.getId(), c.path("group_points").asInt());
                    }
                }
            }
        }
        if (uradno.isEmpty()) {
            return true;
        }
        // zmage, neodlocena, porazi in porazi brez borbe po ekipah
        Map<Long, int[]> izkupicki = new HashMap<>();
        for (Srecanje s : srecanja) {
            if (s.getStatus() != StatusSrecanja.KONCANO) {
                continue;
            }
            int primerjava = Integer.compare(s.getDobljeneDomaci(), s.getDobljeneGost());
            int[] domaci = izkupicki.computeIfAbsent(s.getEkipaDomaci().getId(), k -> new int[4]);
            int[] gost = izkupicki.computeIfAbsent(s.getEkipaGost().getId(), k -> new int[4]);
            domaci[1 - primerjava]++;
            gost[1 + primerjava]++;
            if (s.isBrezBoja() && primerjava != 0) {
                (primerjava < 0 ? domaci : gost)[3]++;
            }
        }
        for (int[] t : TOCKOVANJA) {
            for (int odbitek : new int[] {1, 0}) {
                boolean ujema = uradno.entrySet().stream().allMatch(v -> {
                    int[] i = izkupicki.getOrDefault(v.getKey(), new int[4]);
                    return t[0] * i[0] + t[1] * i[1] + t[2] * i[2] - odbitek * i[3] == v.getValue();
                });
                if (!ujema) {
                    continue;
                }
                liga.setTockeZmaga(t[0]);
                liga.setTockeNeodloceno(t[1]);
                liga.setTockePoraz(t[2]);
                liga.setOdbitekBrezBoja(odbitek);
                if (t != TOCKOVANJA.get(0) || odbitek != 1) {
                    porocilo.opozori("tockovanje lige prebrano iz uradne lestvice", p.ime() + ": zmaga " + t[0]
                            + ", neodloceno " + t[1] + ", poraz " + t[2] + ", odbitek za poraz brez borbe " + odbitek);
                }
                return true;
            }
        }
        porocilo.opozori("uradne tocke ne sledijo nobenemu tockovanju (ostane zmaga 2, neodloceno 1, poraz 0,"
                + " odbitek za poraz brez borbe 1)", p.ime());
        return false;
    }

    // ---------- Koncnica ----------

    private void koncnica(PosnetekDogodka p, Liga liga, List<JsonNode> redne, List<JsonNode> tekme, FormatSrecanja format,
                          Map<Long, Ekipa> ekipe, SrecanjaStupe.Kadri kadri, int krogov, Set<Long> ohranjena,
                          List<Srecanje> vsaSrecanja) {
        // krogi po vrsti kol vira (Semi, Final ...)
        List<Integer> kola = tekme.stream().map(t -> t.path("round").path("order").asInt()).distinct().sorted().toList();
        int zamik = krogov - kola.size();
        Map<Long, Integer> mesta = mestaRednegaDela(p, redne, liga, ekipe);

        int ekip = liga.getKoncnicaEkip();
        Map<String, SerijaKoncnice> serije = new TreeMap<>();
        for (int krog = 1; krog <= krogov; krog++) {
            for (int par = 1; par <= Math.max(1, ekip >> krog); par++) {
                serije.put(krog + "|" + par, repo.serije().save(new SerijaKoncnice(liga, krog, par)));
            }
        }
        Map<String, List<JsonNode>> poSerijah = new TreeMap<>();
        for (JsonNode t : tekme) {
            int krog = kola.indexOf(t.path("round").path("order").asInt()) + 1 + zamik;
            poSerijah.computeIfAbsent(krog + "|" + Math.max(1, t.path("order").asInt(1)), k -> new ArrayList<>()).add(t);
        }
        for (Map.Entry<String, List<JsonNode>> vnos : poSerijah.entrySet()) {
            SerijaKoncnice serija = serije.get(vnos.getKey());
            if (serija == null) {
                porocilo.napaka("tekma koncnice zunaj mreze koncnice", p.ime() + ": " + vnos.getKey());
                continue;
            }
            List<JsonNode> tekmeSerije = new ArrayList<>(vnos.getValue());
            tekmeSerije.sort(Comparator.comparingInt(t -> t.path("leg").asInt(1)));
            List<Long> strani = new ArrayList<>();
            for (JsonNode t : tekmeSerije) {
                for (JsonNode s : SrecanjaStupe.urejeni(t.path("participants"))) {
                    long id = s.path("participant_id").asLong();
                    if (id != 0 && !strani.contains(id)) {
                        strani.add(id);
                    }
                }
            }
            if (strani.size() > 2) {
                porocilo.napaka("serija koncnice z vec kot dvema ekipama", p.ime() + ": " + vnos.getKey());
                continue;
            }
            if (strani.size() == 2) {
                Integer m0 = mesta.get(strani.get(0));
                Integer m1 = mesta.get(strani.get(1));
                boolean obrni = m0 != null && m1 != null && m1 < m0;
                long prva = obrni ? strani.get(1) : strani.get(0);
                long druga = obrni ? strani.get(0) : strani.get(1);
                serija.nastaviStran(1, ekipe.get(prva), mesta.get(prva));
                serija.nastaviStran(2, ekipe.get(druga), mesta.get(druga));
            } else if (strani.size() == 1) {
                serija.nastaviStran(1, ekipe.get(strani.get(0)), mesta.get(strani.get(0)));
            }
            int zmage1 = 0;
            int zmage2 = 0;
            for (JsonNode t : tekmeSerije) {
                Srecanje s = srecanje(p, liga, t, format, ekipe, kadri, serija.getKrog(), serija,
                        Math.max(1, t.path("leg").asInt(1)));
                if (s == null) {
                    continue;
                }
                ohranjena.add(s.getId());
                vsaSrecanja.add(s);
                if (s.getStatus() == StatusSrecanja.KONCANO && s.getDobljeneDomaci() != s.getDobljeneGost()) {
                    Ekipa zmagala = s.getDobljeneDomaci() > s.getDobljeneGost() ? s.getEkipaDomaci() : s.getEkipaGost();
                    if (serija.getEkipa1() != null && zmagala.getId().equals(serija.getEkipa1().getId())) {
                        zmage1++;
                    } else {
                        zmage2++;
                    }
                }
            }
            serija.setZmage1(zmage1);
            serija.setZmage2(zmage2);
            if (zmage1 >= liga.getKoncnicaZmag()) {
                serija.setZmagovalec(serija.getEkipa1());
            } else if (zmage2 >= liga.getKoncnicaZmag()) {
                serija.setZmagovalec(serija.getEkipa2());
            }
            repo.serije().save(serija);
            porocilo.prestej("serij koncnice");
        }
    }

    /* Mesta rednega dela: uradna iz skupine vira, sicer lestvica Turnirka. */
    private Map<Long, Integer> mestaRednegaDela(PosnetekDogodka p, List<JsonNode> redne, Liga liga, Map<Long, Ekipa> ekipe) {
        Map<Long, Integer> mesta = new HashMap<>();
        for (JsonNode s : redne) {
            for (JsonNode g : p.skupineStopnje(s.path("id").asLong())) {
                for (JsonNode c : g.path("participants")) {
                    if (c.path("group_rank").isInt()) {
                        mesta.put(c.path("participant_id").asLong(), c.path("group_rank").asInt());
                    }
                }
            }
        }
        if (!mesta.isEmpty() || redne.isEmpty()) {
            return mesta;
        }
        Map<Long, Long> udelezenecPoEkipi = new HashMap<>();
        ekipe.forEach((idUdelezenca, e) -> udelezenecPoEkipi.put(e.getId(), idUdelezenca));
        for (LestvicaEkipeDto v : lestvice.lestvica(liga.getId())) {
            Long id = udelezenecPoEkipi.get(v.idEkipa());
            if (id != null) {
                mesta.put(id, v.mesto());
            }
        }
        return mesta;
    }

    private boolean koncnicaOdlocena(Liga liga) {
        return repo.serije().najdiZaLigo(liga.getId()).stream()
                .filter(s -> s.getKrog() == Integer.numberOfTrailingZeros(liga.getKoncnicaEkip()))
                .allMatch(s -> s.getZmagovalec() != null);
    }

    // ---------- Pomozno ----------

    private static boolean dvokroznoPoKolih(PosnetekDogodka p, List<JsonNode> redne) {
        for (JsonNode s : redne) {
            for (JsonNode g : p.skupineStopnje(s.path("id").asLong())) {
                Set<Long> ekipe = new HashSet<>();
                p.tekmeSkupine(g.path("id").asLong()).forEach(t -> t.path("participants")
                        .forEach(x -> ekipe.add(x.path("participant_id").asLong())));
                int kol = p.kolaSkupine(g.path("id").asLong()).size();
                int n = ekipe.size() + (ekipe.size() % 2);
                if (n > 1 && kol >= 2 * (n - 1)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static LocalDateTime zacetek(PosnetekDogodka p, List<JsonNode> tekme) {
        LocalDateTime najzgodnejsi = null;
        for (JsonNode t : tekme) {
            LocalDateTime c = UvozOblike.casovniZig(t.path("start_time").asText(null));
            if (c != null && (najzgodnejsi == null || c.isBefore(najzgodnejsi))) {
                najzgodnejsi = c;
            }
        }
        return najzgodnejsi != null ? najzgodnejsi : p.zacetek() == null ? null : p.zacetek().atStartOfDay();
    }

    private static int steviloNizov(List<JsonNode> tekme) {
        Map<Integer, Integer> stetje = new HashMap<>();
        for (JsonNode t : tekme) {
            for (JsonNode pod : t.path("sub_matches")) {
                int dolzina = pod.path("participants").path(0).path("sets").size();
                if (dolzina > 0) {
                    stetje.merge(SrecanjaStupe.steviloNizov(dolzina), 1, Integer::sum);
                }
            }
        }
        return stetje.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(5);
    }

    private static IdentitetaStupe.KlubStupe klubEkipe(JsonNode u) {
        IdentitetaStupe.KlubStupe k = IdentitetaStupe.klub(u.path("participant_parents"));
        if (k != null) {
            return k;
        }
        for (JsonNode s : u.path("selected_parents")) {
            String ime = UvozOblike.ocisti(s.path("parent_name").asText(null));
            if (ime != null && "Club".equalsIgnoreCase(s.path("parent_role").asText())) {
                return new IdentitetaStupe.KlubStupe(0, ime, UvozOblike.ocisti(s.path("parent_abbr").asText(null)));
            }
        }
        return null;
    }

    private static String opis(PosnetekDogodka p, JsonNode t, Map<Long, Ekipa> ekipe) {
        List<String> imena = new ArrayList<>();
        for (JsonNode s : SrecanjaStupe.urejeni(t.path("participants"))) {
            JsonNode u = p.udelezenec(s.path("participant_id").asLong());
            imena.add(u == null ? "?" : UvozOblike.ocisti(u.path("participant_name").asText("?")));
        }
        return p.ime() + " · " + t.path("round").path("name").asText("") + (t.path("leg").isInt() ? " (tekma "
                + t.path("leg").asInt() + ")" : "") + ": " + String.join(" – ", imena) + " [Stupa " + t.path("id").asLong() + "]";
    }
}
