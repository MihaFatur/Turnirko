/* Turnir iz Stupe v Turnirko: turnir -> dogodki (kategorije) -> prijave,
   skupine, tekme, nizi; pri ekipnem dogodku se ekipe, kadri in srecanja.

   Sistem tekmovanja se PREBERE IZ STOPENJ (format_id 1 = skupine, 2 =
   izlocilni), imena stopenj pa odlocajo le tam, kjer drugega podatka ni:
     skupine                         -> KROZNI (ena skupina) / SKUPINE (vec)
     skupine, nato skupine           -> SKUPINE_ZA_MESTA (finalne skupine
                                        "1.-4. MESTO" dolocajo prvo mesto)
     izlocilni                       -> IZLOCILNI
     skupine, nato izlocilni         -> SKUPINE_IZLOCILNI
   Dodatne izlocilne stopnje: "Consolation" je tolazilna mreza (faza
   TOLAZILNI), "ZA 3. MESTO" tekma za tretje mesto (porazenca polfinalov),
   "ZA 1. MESTO" pa pravo finale, ki nadomesti zastarelo finale glavne mreze
   (pokal NTZS 2026 ima v glavni mrezi finale, ki pri viru ostaja "v igri").

   Pasti vira:
   * Prosti prehodi (bye) v tekmah vira sploh niso zapisani - mesto v mrezi je
     prazno. Turnirko jih zapise kot tekmo PROSTO, sicer bi mreza imela luknje.
   * Stran tekme v mrezi ni nujno stran izvora: ce je zmagovalec zgornje
     tekme zapisan na drugem mestu, se strani zamenjata.
   * W.o. nosi izmisljene nize 11:0 - izid je BREZ_BOJA, nizov ni.
   * Finalne skupine ekipnih DP vsebujejo KOPIJE dvobojev iz predtekmovanja
     (isti izid, pri starejsih dogodkih celo s podtekmami). Taka tekma dobi
     preneseni izid (id_prenesena) brez srecanja - sicer bi isti dvoboj v
     ratingu stel dvakrat.
   * Neodigrana tekma dogodka, ki je mimo, se ne bo odigrala (odstop,
     zastarela tekma) in se izpusti. */
package si.turnirko.uvoz.stupa;

import java.text.Normalizer;
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
import si.turnirko.dto.VrsticaLestviceDto;
import si.turnirko.modeli.Disciplina;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.Ekipa;
import si.turnirko.modeli.FazaTekme;
import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.IzidTekme;
import si.turnirko.modeli.Klub;
import si.turnirko.modeli.Niz;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.modeli.SistemTekmovanja;
import si.turnirko.modeli.Skupina;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.Srecanje;
import si.turnirko.modeli.StatusSrecanja;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.StatusTekmovanja;
import si.turnirko.modeli.Tekma;
import si.turnirko.modeli.Turnir;
import si.turnirko.modeli.VirTekmovanja;
import si.turnirko.modeli.VlogaIzvora;
import si.turnirko.modeli.ZunanjaPovezava;
import si.turnirko.storitve.RazvrstitevStoritev;
import si.turnirko.storitve.SkupineStoritev;
import si.turnirko.uvoz.KoncnaMestaUvoza;
import si.turnirko.uvoz.UvozOblike;

final class PreslikavaTurnirjaStupe {

    private enum VrstaIzlocilne { GLAVNA, TOLAZILNA, ZA_PRVO, ZA_TRETJE }

    /* Mesto tekme v mrezi pred zapisom; tekma == null je prosti prehod. */
    private record Postavitev(FazaTekme faza, int kolo, int pozicija, JsonNode tekma, Prijava prosta,
                              boolean zaTretje) {
        String kljuc() {
            return kljuc(faza, kolo, pozicija);
        }

        static String kljuc(FazaTekme faza, int kolo, int pozicija) {
            return faza + "|" + kolo + "|" + pozicija;
        }
    }

    private static final Pattern PRVO_MESTO = Pattern.compile("^\\D*?(\\d+)\\s*\\.?\\s*[-–]\\s*\\d+");

    private final RepozitorijiUvoza repo;
    private final IdentitetaStupe identiteta;
    private final SrecanjaStupe srecanja;
    private final RazvrstitevStoritev razvrstitev;
    private final PorociloUvoza porocilo;
    private final SledUvoza sled;

    PreslikavaTurnirjaStupe(RepozitorijiUvoza repo, IdentitetaStupe identiteta, SrecanjaStupe srecanja,
                            RazvrstitevStoritev razvrstitev, PorociloUvoza porocilo, SledUvoza sled) {
        this.repo = repo;
        this.identiteta = identiteta;
        this.srecanja = srecanja;
        this.razvrstitev = razvrstitev;
        this.porocilo = porocilo;
        this.sled = sled;
    }

    // ---------------------------------------------------------------------
    // Turnir
    // ---------------------------------------------------------------------

    Turnir uvozi(PosnetekDogodka p) {
        Turnir turnir = PovezaveStupe.lokalni(repo.povezave(), ZunanjaPovezava.Vrsta.TURNIR, p.id())
                .flatMap(id -> repo.turnirji().findById(id))
                .orElseGet(Turnir::new);
        turnir.setIme(imeTekmovanja(p.ime(), p.id()));
        turnir.setDatumZacetka(p.zacetek());
        turnir.setDatumKonca(p.konec());
        turnir.setDvorana(UvozOblike.prirezi(p.prizorisce(), 120));
        // koledar NTZS je uradno tekmovanje (privzetek entitete je klubsko)
        turnir.setRaven(RavenTekmovanja.URADNO);
        turnir.setVir(VirTekmovanja.STUPA);
        turnir.setOpombe("Uvoz iz Stupa Events (id " + p.id() + ")");
        turnir = repo.turnirji().save(turnir);
        PovezaveStupe.povezi(repo.povezave(), ZunanjaPovezava.Vrsta.TURNIR, p.id(), turnir.getId());
        sled.idTurnir = turnir.getId();
        porocilo.prestej("turnirjev");

        identiteta.najaviImena(imenaOseb(p));

        List<JsonNode> kategorije = new ArrayList<>(p.kategorije());
        kategorije.sort(Comparator.comparingInt((JsonNode k) -> k.path("order").asInt())
                .thenComparingLong(k -> k.path("id").asLong()));
        Set<Long> ohranjeni = new HashSet<>();
        List<Dogodek> dogodki = new ArrayList<>();
        for (JsonNode kategorija : kategorije) {
            Dogodek d = uvoziKategorijo(p, turnir, kategorija);
            if (d != null) {
                ohranjeni.add(d.getId());
                dogodki.add(d);
            }
        }
        for (Dogodek star : repo.dogodki().findByTurnirIdOrderByIdAsc(turnir.getId())) {
            if (!ohranjeni.contains(star.getId())) {
                repo.em().flush();
                repo.em().detach(star);
                CiscenjeUvoza.dogodek(repo.em(), star.getId());
                porocilo.opozori("dogodek, ki ga vir ne pozna vec, je izbrisan", star.getIme());
            }
        }

        if (dogodki.isEmpty()) {
            // turnir, ki se ni zacel: vir ima razpis brez zrebov. Zapis je dovoljen
            // (turnir v pripravi, tekmovanja prinese ponovni uvoz), admin pa mora
            // vedeti, da uvaza prazen turnir in ne rezultatov.
            porocilo.opozori("vir se nima nobenega tekmovanja - zapisan bo le turnir v pripravi, brez dogodkov",
                    turnir.getIme());
        }
        turnir.setStatus(dogodki.isEmpty() ? StatusTekmovanja.PRIPRAVA
                : dogodki.stream().allMatch(d -> d.getStatus() == StatusTekmovanja.ZAKLJUCEN) ? StatusTekmovanja.ZAKLJUCEN
                : dogodki.stream().allMatch(d -> d.getStatus() == StatusTekmovanja.PRIPRAVA) ? StatusTekmovanja.PRIPRAVA
                : StatusTekmovanja.V_TEKU);
        return repo.turnirji().save(turnir);
    }

    // ---------------------------------------------------------------------
    // Kategorija -> dogodek
    // ---------------------------------------------------------------------

    private Dogodek uvoziKategorijo(PosnetekDogodka p, Turnir turnir, JsonNode kat) {
        long idKat = kat.path("id").asLong();
        String imeKat = imeKategorije(kat);
        List<JsonNode> stopnje = p.stopnjeKategorije(idKat);
        if (stopnje.isEmpty()) {
            porocilo.prestej("kategorij brez tekmovanja pri viru (izpuscene)");
            return null;
        }
        int tip = kat.path("participant_type_id").asInt();
        Disciplina disciplina = switch (tip) {
            case 1 -> Disciplina.POSAMICNO;
            case 2 -> Disciplina.EKIPNO;
            case 3, 4 -> Disciplina.DVOJICE;
            default -> null;
        };
        if (disciplina == null) {
            porocilo.napaka("kategorija z neznano vrsto udelezencev", imeKat + " (vrsta " + tip + ")");
            return null;
        }

        List<JsonNode> skupinske = stopnje.stream().filter(s -> s.path("format_id").asInt() == 1).toList();
        List<JsonNode> izlocilne = stopnje.stream().filter(s -> s.path("format_id").asInt() == 2).toList();
        if (skupinske.size() + izlocilne.size() != stopnje.size()) {
            porocilo.napaka("stopnja z neznanim formatom", imeKat);
            return null;
        }
        SistemTekmovanja sistem;
        if (izlocilne.isEmpty()) {
            sistem = skupinske.size() > 1 ? SistemTekmovanja.SKUPINE_ZA_MESTA
                    : p.skupineStopnje(skupinske.get(0).path("id").asLong()).size() > 1
                            ? SistemTekmovanja.SKUPINE : SistemTekmovanja.KROZNI;
        } else {
            sistem = skupinske.isEmpty() ? SistemTekmovanja.IZLOCILNI : SistemTekmovanja.SKUPINE_IZLOCILNI;
        }
        if (disciplina == Disciplina.DVOJICE && sistem != SistemTekmovanja.IZLOCILNI) {
            porocilo.napaka("dvojice v skupinah - Turnirko jih igra samo v izlocilni mrezi", imeKat);
            return null;
        }

        List<JsonNode> tekme = new ArrayList<>();
        for (JsonNode s : stopnje) {
            for (JsonNode g : p.skupineStopnje(s.path("id").asLong())) {
                tekme.addAll(p.tekmeSkupine(g.path("id").asLong()));
            }
        }

        FormatSrecanja format = null;
        if (disciplina == Disciplina.EKIPNO) {
            format = SrecanjaStupe.dolociFormat(tekme, false, porocilo, imeKat);
            if (format == null) {
                if (tekme.stream().anyMatch(t -> !t.path("sub_matches").isEmpty())) {
                    return null; // napaka je ze v porocilu
                }
                format = FormatSrecanja.EKIPNI_DP;
                porocilo.opozori("format ekipnih srecanj pri viru se ni znan (privzet EKIPNI_DP)", imeKat);
            }
        }

        Dogodek d = PovezaveStupe.lokalni(repo.povezave(), ZunanjaPovezava.Vrsta.DOGODEK, idKat)
                .flatMap(id -> repo.dogodki().findById(id))
                .orElseGet(Dogodek::new);
        d.setTurnir(turnir);
        d.setIme(imeKat);
        d.setDisciplina(disciplina);
        d.setSpolKategorija(spolKategorije(kat, tip));
        d.setStarostnaKategorija(UvozOblike.prirezi(kat.path("category_description").asText(null), 40));
        d.setSistemTekmovanja(sistem);
        d.setPrivzetoSteviloNizov(privzetoSteviloNizov(tekme, disciplina));
        d.setFormatSrecanja(format);
        d.setZmagZaSrecanje(format == null ? null : format.stTekem() / 2 + 1);
        d.setTekmaZaTretjeMesto(false);
        d.setSteviloSkupin(null);
        d.setVelikostSkupine(null);
        if (sistem == SistemTekmovanja.SKUPINE) {
            // omejitev CHECK zahteva obe nastavitvi ze ob vpisu dogodka
            List<JsonNode> skupineTop = p.skupineStopnje(skupinske.get(0).path("id").asLong());
            int najvec = 2;
            for (JsonNode g : skupineTop) {
                Set<Long> clani = new HashSet<>();
                g.path("participants").forEach(c -> clani.add(c.path("participant_id").asLong()));
                p.tekmeSkupine(g.path("id").asLong()).forEach(t -> t.path("participants")
                        .forEach(s -> clani.add(s.path("participant_id").asLong())));
                najvec = Math.max(najvec, clani.size());
            }
            d.setSteviloSkupin(Math.min(26, skupineTop.size()));
            d.setVelikostSkupine(Math.min(24, najvec));
        }
        d.setStatus(StatusTekmovanja.PRIPRAVA);
        d = repo.dogodki().save(d);
        PovezaveStupe.povezi(repo.povezave(), ZunanjaPovezava.Vrsta.DOGODEK, idKat, d.getId());
        sled.dogodki.put(idKat, d.getId());
        porocilo.prestej("dogodkov");

        Kategorija k = new Kategorija(p, turnir, d, kat, imeKat, format);
        k.prijave(tekme);
        k.skupine(skupinske, sistem);
        k.tekmeSkupin(skupinske);
        k.tekmeMrez(izlocilne);
        k.zakljuci(sistem);
        return d;
    }

    /* Delovno stanje ene kategorije. */
    private final class Kategorija {
        private final PosnetekDogodka p;
        private final Turnir turnir;
        private final Dogodek d;
        private final JsonNode kat;
        private final String imeKat;
        private final FormatSrecanja format;
        private final LocalDate datum;

        private final Map<Long, Prijava> prijave = new HashMap<>();
        private final Map<Long, Ekipa> ekipe = new HashMap<>();
        private final Map<Long, Skupina> skupine = new HashMap<>();
        private final List<Skupina> vseSkupine = new ArrayList<>();
        private final List<Tekma> vseTekme = new ArrayList<>();
        private final Map<String, Tekma> zadnjaMedsebojna = new HashMap<>();
        /* zapis vira za vsako shranjeno tekmo - za prepoznavo kopij v finalnih skupinah */
        private final Map<Long, JsonNode> virTekme = new HashMap<>();
        private final SrecanjaStupe.Kadri kadri = new SrecanjaStupe.Kadri();
        private final Map<Long, Integer> uradnaMesta = new HashMap<>();
        private int globalnaPozicija = 0;
        private int finaleKolo = 1;

        Kategorija(PosnetekDogodka p, Turnir turnir, Dogodek d, JsonNode kat, String imeKat, FormatSrecanja format) {
            this.p = p;
            this.turnir = turnir;
            this.d = d;
            this.kat = kat;
            this.imeKat = imeKat;
            this.format = format;
            this.datum = turnir.getDatumZacetka();
        }

        // ---------- Prijave ----------

        void prijave(List<JsonNode> tekme) {
            Set<Long> vTekmah = new HashSet<>();
            for (JsonNode t : tekme) {
                for (JsonNode s : t.path("participants")) {
                    vTekmah.add(s.path("participant_id").asLong());
                }
            }
            long idKategorijeUdelezencev = kat.path("category_id").asLong();
            List<JsonNode> udelezenci = p.udelezenci().stream()
                    .filter(u -> u.path("category_id").asLong() == idKategorijeUdelezencev)
                    .sorted(Comparator.comparingLong(u -> u.path("id").asLong()))
                    .toList();
            Map<Long, Prijava> poIgralcu = new HashMap<>();
            Map<Long, Integer> zaporednaPoKlubu = new HashMap<>();
            Set<String> imenaProstih = new HashSet<>();

            for (JsonNode u : udelezenci) {
                long id = u.path("id").asLong();
                boolean igral = vTekmah.contains(id);
                if (u.path("is_deleted").asBoolean(false) && !igral) {
                    porocilo.prestej("izbrisanih prijav pri viru (izpuscene)");
                    continue;
                }
                if (u.path("is_excluded").asBoolean(false) && !igral) {
                    // prijava, ki jo je organizator izkljucil (2. OT U17 2026 ima dve), v
                    // zreb ni prisla - brez skupine bi stala med udelezenci; izkljucen, ki
                    // je igral (ekipni DP U17 2026), ostane, ker so njegove tekme odigrane
                    porocilo.prestej("izkljucenih prijav pri viru (izpuscene)");
                    continue;
                }
                List<JsonNode> det = SrecanjaStupe.urejeni(u.path("event_participant_details")).stream()
                        .filter(x -> !x.path("is_deleted").asBoolean(false))
                        .toList();
                Prijava pr;
                switch (d.getDisciplina()) {
                    case POSAMICNO -> {
                        if (det.isEmpty()) {
                            neuporabnaPrijava(u, igral, "prijava brez igralca");
                            continue;
                        }
                        IdentitetaStupe.Oseba o = IdentitetaStupe.oseba(det.get(0));
                        Igralec igralec = identiteta.igralec(o, datum);
                        if (igralec == null) {
                            continue;
                        }
                        Prijava obstojeca = poIgralcu.get(igralec.getId());
                        if (obstojeca != null) {
                            prijave.put(id, obstojeca);
                            porocilo.opozori("isti igralec dvakrat prijavljen v kategoriji (zdruzeno)",
                                    imeKat + ": " + igralec.polnoIme());
                            continue;
                        }
                        pr = new Prijava(d, igralec);
                        Klub klub = identiteta.klub(o.klub());
                        pr.setKlubObPrijavi(klub != null ? klub : igralec.getKlub());
                        poIgralcu.put(igralec.getId(), pr);
                    }
                    case DVOJICE -> {
                        if (det.size() < 2) {
                            neuporabnaPrijava(u, igral, "par brez soigralca");
                            continue;
                        }
                        IdentitetaStupe.Oseba o1 = IdentitetaStupe.oseba(det.get(0));
                        IdentitetaStupe.Oseba o2 = IdentitetaStupe.oseba(det.get(1));
                        Igralec i1 = identiteta.igralec(o1, datum);
                        Igralec i2 = identiteta.igralec(o2, datum);
                        if (i1 == null || i2 == null) {
                            continue;
                        }
                        if (i1.getId().equals(i2.getId()) || poIgralcu.containsKey(i1.getId())
                                || poIgralcu.containsKey(i2.getId())) {
                            porocilo.napaka("igralec v dveh parih iste kategorije", imeKat + ": "
                                    + i1.polnoIme() + " / " + i2.polnoIme());
                            continue;
                        }
                        pr = new Prijava(d, i1);
                        pr.nastaviSoigralca(i2);
                        Klub k1 = identiteta.klub(o1.klub());
                        Klub k2 = identiteta.klub(o2.klub());
                        pr.setKlubObPrijavi(k1 != null ? k1 : i1.getKlub());
                        pr.setKlubObPrijavi2(k2 != null ? k2 : i2.getKlub());
                        poIgralcu.put(i1.getId(), pr);
                        poIgralcu.put(i2.getId(), pr);
                    }
                    default -> {
                        Klub klub = identiteta.klub(klubEkipe(u));
                        String ime = UvozOblike.prirezi(u.path("participant_name").asText(null), 60);
                        if (ime == null || ime.length() < 2) {
                            ime = klub != null ? UvozOblike.prirezi(klub.getIme(), 60) : "Ekipa " + id;
                        }
                        int zaporedna = 1;
                        if (klub != null) {
                            zaporedna = zaporednaPoKlubu.merge(klub.getId(), 1, Integer::sum);
                        } else {
                            String osnovno = ime;
                            int n = 2;
                            while (!imenaProstih.add(ime)) {
                                ime = UvozOblike.prirezi(osnovno, 55) + " (" + n++ + ")";
                            }
                        }
                        Ekipa ekipa = repo.ekipe().save(new Ekipa(d, klub, zaporedna, ime));
                        ekipe.put(id, ekipa);
                        sled.ekipe.put(id, ekipa.getId());
                        for (JsonNode clan : det) {
                            Igralec igralec = identiteta.igralec(IdentitetaStupe.oseba(clan), datum);
                            kadri.dodaj(ekipa, igralec, clan.path("order").isInt() && clan.path("order").asInt() > 0
                                    ? clan.path("order").asInt() : null);
                        }
                        pr = new Prijava(d, ekipa);
                        porocilo.prestej("ekip");
                    }
                }
                if (u.path("seed").isInt() && u.path("seed").asInt() > 0) {
                    pr.setStNosilca(u.path("seed").asInt());
                }
                boolean odjavljen = u.path("is_withdrawn").asBoolean(false);
                pr.setStatus(!odjavljen ? Prijava.StatusPrijave.PRIJAVLJEN
                        : igral ? Prijava.StatusPrijave.ODSTOPIL : Prijava.StatusPrijave.ODJAVLJEN);
                if (u.path("cumulative_standing").isInt() && u.path("cumulative_standing").asInt() > 0) {
                    uradnaMesta.put(id, u.path("cumulative_standing").asInt());
                }
                Prijava shranjena = repo.prijave().save(pr);
                prijave.put(id, shranjena);
                sled.prijave.put(id, shranjena.getId());
                porocilo.prestej("prijav");
            }
            for (Long id : vTekmah) {
                if (!prijave.containsKey(id) && id != 0) {
                    JsonNode u = p.udelezenec(id);
                    porocilo.napaka("udelezenec tekme ni med prijavami kategorije", imeKat + ": "
                            + (u == null ? "id " + id : u.path("participant_name").asText()));
                }
            }
        }

        private void neuporabnaPrijava(JsonNode u, boolean igral, String razlog) {
            if (igral) {
                porocilo.napaka(razlog, imeKat + ": " + u.path("participant_name").asText());
            } else {
                porocilo.prestej("prijav brez uporabnih podatkov (izpuscene)");
            }
        }

        // ---------- Skupine ----------

        void skupine(List<JsonNode> skupinske, SistemTekmovanja sistem) {
            if (sistem == SistemTekmovanja.KROZNI) {
                return;
            }
            Set<String> oznake = new HashSet<>();
            int stopnja = 0;
            for (JsonNode s : skupinske) {
                stopnja++;
                List<JsonNode> skupineStopnje = p.skupineStopnje(s.path("id").asLong());
                String imeStopnje = UvozOblike.ocisti(s.path("name").asText(null));
                Integer prvoMesto = stopnja > 1 && skupineStopnje.size() == 1 ? prvoMesto(imeStopnje) : null;
                int zaporedna = 0;
                for (JsonNode g : skupineStopnje) {
                    zaporedna++;
                    String crka = crka(zaporedna);
                    String oznaka = stopnja == 1 ? crka : prvoMesto != null ? "M" + prvoMesto : stopnja + crka;
                    if (oznaka.length() > 3 || !oznake.add(oznaka)) {
                        oznaka = String.valueOf(stopnja * 100 + zaporedna).substring(0, 3);
                        while (!oznake.add(oznaka)) {
                            oznaka = String.valueOf(Integer.parseInt(oznaka) + 1);
                        }
                    }
                    Skupina sk = new Skupina(d, oznaka);
                    sk.setStopnja(stopnja);
                    if (stopnja > 1 && imeStopnje != null) {
                        sk.setIme(UvozOblike.prirezi(skupineStopnje.size() == 1 ? imeStopnje : imeStopnje + " " + crka, 40));
                    }
                    sk.setPrvoMesto(prvoMesto);
                    sk = repo.skupine().save(sk);
                    skupine.put(g.path("id").asLong(), sk);
                    vseSkupine.add(sk);
                    sled.skupine.put(g.path("id").asLong(), sk.getId());
                    porocilo.prestej("skupin");

                    // clanstvo v predtekmovalni skupini: uradni seznam, sicer tekme
                    // (visje stopnje clanstvo nosijo samo tekme - SkupineStoritev.clani)
                    if (stopnja == 1) {
                        Set<Long> clani = new HashSet<>();
                        g.path("participants").forEach(c -> clani.add(c.path("participant_id").asLong()));
                        if (clani.isEmpty()) {
                            for (JsonNode t : p.tekmeSkupine(g.path("id").asLong())) {
                                t.path("participants").forEach(x -> clani.add(x.path("participant_id").asLong()));
                            }
                        }
                        for (Long idClana : clani) {
                            Prijava pr = prijave.get(idClana);
                            if (pr != null && pr.getIdSkupina() == null) {
                                pr.setIdSkupina(sk.getId());
                            }
                        }
                    }
                }
            }
        }

        // ---------- Tekme skupin ----------

        void tekmeSkupin(List<JsonNode> skupinske) {
            int stopnja = 0;
            for (JsonNode s : skupinske) {
                stopnja++;
                for (JsonNode g : p.skupineStopnje(s.path("id").asLong())) {
                    Skupina sk = skupine.get(g.path("id").asLong());
                    for (JsonNode t : p.tekmeSkupine(g.path("id").asLong())) {
                        tekmaSkupine(t, sk, stopnja, UvozOblike.ocisti(g.path("name").asText(null)));
                    }
                }
            }
        }

        private void tekmaSkupine(JsonNode t, Skupina sk, int stopnja, String imeSkupine) {
            List<JsonNode> strani = SrecanjaStupe.urejeni(t.path("participants"));
            if (strani.size() < 2) {
                porocilo.prestej("prostih terminov v skupinah");
                sled.izpuscene.add(t.path("id").asLong());
                return;
            }
            String opis = opisTekme(t, imeSkupine);
            Prijava p1 = prijave.get(strani.get(0).path("participant_id").asLong());
            Prijava p2 = prijave.get(strani.get(1).path("participant_id").asLong());
            if (p1 == null || p2 == null) {
                sled.izpuscene.add(t.path("id").asLong());
                return; // napaka je ze v porocilu (udelezenec ni med prijavami)
            }
            boolean odigrana = "SCORED".equals(t.path("status").asText());
            if (!odigrana && p.jeMimo()) {
                porocilo.opozori("neodigrana tekma koncanega dogodka (izpuscena)", opis);
                sled.izpuscene.add(t.path("id").asLong());
                return;
            }
            Tekma tekma = novaTekma(FazaTekme.SKUPINA, Math.max(1, t.path("round").path("order").asInt(1)),
                    ++globalnaPozicija);
            tekma.setIdSkupina(sk == null ? null : sk.getId());

            if (stopnja > 1 && odigrana) {
                Tekma prejsnja = zadnjaMedsebojna.get(par(p1, p2));
                if (prejsnja != null) {
                    int n1 = strani.get(0).path("sets_won").asInt();
                    int n2 = strani.get(1).path("sets_won").asInt();
                    // kopija ima isti izid IN iste posamicne tekme; pravi ponovni
                    // dvoboj z enakim izidom ima drugacne tocke nizov
                    if (istIzid(prejsnja, p1, n1, p2, n2) && isteTekmeSrecanja(virTekme.get(prejsnja.getId()), t)) {
                        prenesi(tekma, prejsnja, p1, p2);
                        Tekma shranjena = repo.tekme().save(tekma);
                        vseTekme.add(shranjena);
                        sled.tekme.put(t.path("id").asLong(), shranjena.getId());
                        sled.prenesene.add(t.path("id").asLong());
                        porocilo.prestej("prenesenih izidov v finalnih skupinah");
                        return;
                    }
                    porocilo.opozori("ponovljen dvoboj z drugacnim izidom kot v prejsnji stopnji (zapisan kot nov)", opis);
                }
            }
            izid(tekma, t, strani, p1, p2, opis);
            Tekma shranjena = shrani(tekma, t, strani, opis);
            if (shranjena.getStatus() == StatusTekme.KONCANA) {
                zadnjaMedsebojna.put(par(p1, p2), shranjena);
            }
        }

        private void prenesi(Tekma nova, Tekma prejsnja, Prijava p1, Prijava p2) {
            boolean enako = prejsnja.getPrijava1().getId().equals(p1.getId());
            nova.setPrijava1(p1);
            nova.setPrijava2(p2);
            nova.setDobljeniNizi1(enako ? prejsnja.getDobljeniNizi1() : prejsnja.getDobljeniNizi2());
            nova.setDobljeniNizi2(enako ? prejsnja.getDobljeniNizi2() : prejsnja.getDobljeniNizi1());
            nova.setSteviloNizov(prejsnja.getSteviloNizov());
            nova.setZmagovalec(prejsnja.getZmagovalec());
            nova.setIzidTip(prejsnja.getIzidTip());
            nova.setStatus(StatusTekme.KONCANA);
            nova.setIdPrenesena(prejsnja.getId());
        }

        /* Posamicne tekme kopije so enake posamicnim tekmam izvirnika (tocke vseh
           nizov na obeh straneh). Kopija brez podtekem se ujema z vsakim. */
        private boolean isteTekmeSrecanja(JsonNode izvirnik, JsonNode kopija) {
            if (kopija.path("sub_matches").isEmpty()) {
                return true;
            }
            if (izvirnik == null || izvirnik.path("sub_matches").size() != kopija.path("sub_matches").size()) {
                return false;
            }
            List<JsonNode> a = SrecanjaStupe.urejeni(izvirnik.path("sub_matches"));
            List<JsonNode> b = SrecanjaStupe.urejeni(kopija.path("sub_matches"));
            for (int i = 0; i < a.size(); i++) {
                for (JsonNode stran : a.get(i).path("participants")) {
                    JsonNode par = null;
                    for (JsonNode s : b.get(i).path("participants")) {
                        if (s.path("participant_id").asLong() == stran.path("participant_id").asLong()) {
                            par = s;
                        }
                    }
                    if (par == null || par.path("sets_won").asInt() != stran.path("sets_won").asInt()
                            || !par.path("points").equals(stran.path("points"))) {
                        return false;
                    }
                }
            }
            return true;
        }

        private boolean istIzid(Tekma prejsnja, Prijava p1, int n1, Prijava p2, int n2) {
            boolean enako = prejsnja.getPrijava1().getId().equals(p1.getId());
            int prej1 = enako ? prejsnja.getDobljeniNizi1() : prejsnja.getDobljeniNizi2();
            int prej2 = enako ? prejsnja.getDobljeniNizi2() : prejsnja.getDobljeniNizi1();
            return prej1 == n1 && prej2 == n2 && prejsnja.getPrijava2() != null
                    && Set.of(prejsnja.getPrijava1().getId(), prejsnja.getPrijava2().getId())
                            .equals(Set.of(p1.getId(), p2.getId()));
        }

        // ---------- Izlocilne mreze ----------

        void tekmeMrez(List<JsonNode> izlocilne) {
            if (izlocilne.isEmpty()) {
                return;
            }
            Map<JsonNode, VrstaIzlocilne> vrste = new LinkedHashMap<>();
            boolean glavnaZe = false;
            for (JsonNode s : izlocilne) {
                VrstaIzlocilne v = vrstaIzlocilne(s.path("name").asText(""), glavnaZe);
                if (v == VrstaIzlocilne.GLAVNA) {
                    glavnaZe = true;
                } else if (v == VrstaIzlocilne.TOLAZILNA && !jeTolazilnaPoImenu(s.path("name").asText(""))) {
                    porocilo.opozori("druga izlocilna stopnja brez znanega pomena (zapisana kot tolazilna mreza)",
                            imeKat + ": " + s.path("name").asText());
                }
                vrste.put(s, v);
            }
            // finale glavne mreze: kolo "Final", sicer kolo za zadnjim
            for (Map.Entry<JsonNode, VrstaIzlocilne> e : vrste.entrySet()) {
                if (e.getValue() != VrstaIzlocilne.GLAVNA) {
                    continue;
                }
                for (JsonNode g : p.skupineStopnje(e.getKey().path("id").asLong())) {
                    List<JsonNode> kola = p.kolaSkupine(g.path("id").asLong());
                    int najvec = 0;
                    for (JsonNode t : p.tekmeSkupine(g.path("id").asLong())) {
                        najvec = Math.max(najvec, t.path("round").path("order").asInt());
                    }
                    for (JsonNode k : kola) {
                        najvec = Math.max(najvec, k.path("order").asInt());
                    }
                    JsonNode zadnje = kola.isEmpty() ? null : kola.get(kola.size() - 1);
                    boolean zadnjeJeFinale = zadnje != null && zadnje.path("name").asText("").trim().equalsIgnoreCase("Final");
                    finaleKolo = zadnjeJeFinale ? Math.max(1, zadnje.path("order").asInt()) : najvec + 1;
                }
            }

            Map<String, Postavitev> poMestu = new LinkedHashMap<>();
            Set<String> izVira = new HashSet<>();
            for (Map.Entry<JsonNode, VrstaIzlocilne> e : vrste.entrySet()) {
                VrstaIzlocilne vrsta = e.getValue();
                for (JsonNode g : p.skupineStopnje(e.getKey().path("id").asLong())) {
                    for (JsonNode t : p.tekmeSkupine(g.path("id").asLong())) {
                        String opis = opisTekme(t, e.getKey().path("name").asText());
                        Postavitev post = switch (vrsta) {
                            case GLAVNA -> new Postavitev(FazaTekme.GLAVNI, Math.max(1, t.path("round").path("order").asInt(1)),
                                    Math.max(1, t.path("order").asInt(1)), t, null, false);
                            case TOLAZILNA -> new Postavitev(FazaTekme.TOLAZILNI, Math.max(1, t.path("round").path("order").asInt(1)),
                                    Math.max(1, t.path("order").asInt(1)), t, null, false);
                            case ZA_PRVO -> new Postavitev(FazaTekme.GLAVNI, finaleKolo, 1, t, null, false);
                            case ZA_TRETJE -> new Postavitev(FazaTekme.TOLAZILNI, finaleKolo, 1, t, null, true);
                        };
                        if (vrsta == VrstaIzlocilne.ZA_PRVO) {
                            Postavitev zastarelo = poMestu.remove(post.kljuc());
                            if (zastarelo != null) {
                                porocilo.opozori("finale glavne mreze nadomesca tekma stopnje ZA 1. MESTO",
                                        imeKat + ": " + opisTekme(zastarelo.tekma(), "glavna mreza"));
                                sled.izpuscene.add(zastarelo.tekma().path("id").asLong());
                            }
                        }
                        if (vrsta == VrstaIzlocilne.ZA_TRETJE) {
                            d.setTekmaZaTretjeMesto(true);
                            int poz = 1;
                            while (poMestu.containsKey(Postavitev.kljuc(FazaTekme.TOLAZILNI, finaleKolo, poz))) {
                                poz++;
                            }
                            post = new Postavitev(FazaTekme.TOLAZILNI, finaleKolo, poz, t, null, true);
                        }
                        izVira.add(post.kljuc());
                        if (SrecanjaStupe.urejeni(t.path("participants")).isEmpty()) {
                            sled.izpuscene.add(t.path("id").asLong());
                            continue;
                        }
                        if (!"SCORED".equals(t.path("status").asText()) && p.jeMimo()) {
                            porocilo.opozori("neodigrana tekma koncanega dogodka (izpuscena)", opis);
                            sled.izpuscene.add(t.path("id").asLong());
                            continue;
                        }
                        if (poMestu.containsKey(post.kljuc())) {
                            porocilo.napaka("dve tekmi na istem mestu mreze", opis);
                            continue;
                        }
                        poMestu.put(post.kljuc(), post);
                    }
                }
            }
            dopolniProstePrehode(poMestu, izVira);

            List<Postavitev> urejene = new ArrayList<>(poMestu.values());
            // GLAVNI pred TOLAZILNI: tekma za 3. mesto potrebuje shranjena polfinala
            urejene.sort(Comparator.comparingInt((Postavitev x) -> x.faza() == FazaTekme.GLAVNI ? 0 : 1)
                    .thenComparing(Postavitev::zaTretje)
                    .thenComparingInt(Postavitev::kolo)
                    .thenComparingInt(Postavitev::pozicija));
            Map<String, Tekma> shranjene = new HashMap<>();
            for (Postavitev post : urejene) {
                Tekma tekma = zapisiMesto(post, shranjene);
                if (tekma != null) {
                    shranjene.put(post.kljuc(), tekma);
                }
            }
        }

        /* Prosti prehodi: mesto predhodne tekme, ki ga vir nima, udelezenca
           tekme pa nobena predhodna tekma ni pripeljala. */
        private void dopolniProstePrehode(Map<String, Postavitev> poMestu, Set<String> izVira) {
            for (Postavitev x : new ArrayList<>(poMestu.values())) {
                if (x.zaTretje() || x.kolo() < 2 || x.tekma() == null) {
                    continue;
                }
                String k1 = Postavitev.kljuc(x.faza(), x.kolo() - 1, 2 * x.pozicija() - 1);
                String k2 = Postavitev.kljuc(x.faza(), x.kolo() - 1, 2 * x.pozicija());
                boolean ima1 = poMestu.containsKey(k1) || izVira.contains(k1);
                boolean ima2 = poMestu.containsKey(k2) || izVira.contains(k2);
                if (ima1 && ima2) {
                    continue;
                }
                List<Prijava> udelezenca = new ArrayList<>();
                for (JsonNode s : SrecanjaStupe.urejeni(x.tekma().path("participants"))) {
                    Prijava pr = prijave.get(s.path("participant_id").asLong());
                    if (pr != null) {
                        udelezenca.add(pr);
                    }
                }
                Prijava izPrve = ima1 && poMestu.containsKey(k1) ? zmagovalecVira(poMestu.get(k1)) : null;
                Prijava izDruge = ima2 && poMestu.containsKey(k2) ? zmagovalecVira(poMestu.get(k2)) : null;
                List<Prijava> prosti = udelezenca.stream()
                        .filter(pr -> (izPrve == null || !pr.getId().equals(izPrve.getId()))
                                && (izDruge == null || !pr.getId().equals(izDruge.getId())))
                        .toList();
                int i = 0;
                if (!ima1 && i < prosti.size()) {
                    poMestu.put(k1, new Postavitev(x.faza(), x.kolo() - 1, 2 * x.pozicija() - 1, null, prosti.get(i++), false));
                }
                if (!ima2 && i < prosti.size()) {
                    poMestu.put(k2, new Postavitev(x.faza(), x.kolo() - 1, 2 * x.pozicija(), null, prosti.get(i), false));
                }
            }
        }

        private Prijava zmagovalecVira(Postavitev post) {
            if (post.tekma() == null) {
                return post.prosta();
            }
            return prijave.get(post.tekma().path("winner").asLong(0));
        }

        private Tekma zapisiMesto(Postavitev post, Map<String, Tekma> shranjene) {
            Tekma tekma = novaTekma(post.faza(), post.kolo(), post.pozicija());
            if (post.tekma() == null) {
                tekma.setPrijava1(post.prosta());
                tekma.setSteviloNizov(d.getPrivzetoSteviloNizov());
                tekma.setStatus(StatusTekme.KONCANA);
                tekma.setIzidTip(IzidTekme.PROSTO);
                tekma.setZmagovalec(post.prosta());
                Tekma shranjena = repo.tekme().save(tekma);
                vseTekme.add(shranjena);
                porocilo.prestej("prostih prehodov");
                return shranjena;
            }
            JsonNode t = post.tekma();
            String opis = opisTekme(t, post.faza() == FazaTekme.GLAVNI ? "mreza" : "tolazilna mreza");
            List<JsonNode> strani = new ArrayList<>(SrecanjaStupe.urejeni(t.path("participants")));
            Prijava p1 = strani.size() > 0 ? prijave.get(strani.get(0).path("participant_id").asLong()) : null;
            Prijava p2 = strani.size() > 1 ? prijave.get(strani.get(1).path("participant_id").asLong()) : null;

            Tekma izvor1;
            Tekma izvor2;
            VlogaIzvora vloga;
            if (post.zaTretje()) {
                izvor1 = shranjene.get(Postavitev.kljuc(FazaTekme.GLAVNI, finaleKolo - 1, 1));
                izvor2 = shranjene.get(Postavitev.kljuc(FazaTekme.GLAVNI, finaleKolo - 1, 2));
                vloga = VlogaIzvora.PORAZENEC;
            } else {
                izvor1 = post.kolo() > 1 ? shranjene.get(Postavitev.kljuc(post.faza(), post.kolo() - 1, 2 * post.pozicija() - 1)) : null;
                izvor2 = post.kolo() > 1 ? shranjene.get(Postavitev.kljuc(post.faza(), post.kolo() - 1, 2 * post.pozicija())) : null;
                vloga = VlogaIzvora.ZMAGOVALEC;
            }
            if (izvor1 != null) {
                tekma.setIdIzvorTekma1(izvor1.getId());
                tekma.setVlogaIzvora1(vloga);
            }
            if (izvor2 != null) {
                tekma.setIdIzvorTekma2(izvor2.getId());
                tekma.setVlogaIzvora2(vloga);
            }
            // stran tekme sledi izvoru: kdor pride iz zgornje tekme, je prvi
            Prijava iz1 = izvor1 == null ? null : vloga == VlogaIzvora.ZMAGOVALEC ? izvor1.getZmagovalec() : izvor1.porazenec();
            Prijava iz2 = izvor2 == null ? null : vloga == VlogaIzvora.ZMAGOVALEC ? izvor2.getZmagovalec() : izvor2.porazenec();
            if (p1 != null && p2 != null && ((iz1 != null && iz1.getId().equals(p2.getId()))
                    || (iz2 != null && iz2.getId().equals(p1.getId())))) {
                Prijava zacasna = p1;
                p1 = p2;
                p2 = zacasna;
                JsonNode s = strani.get(0);
                strani.set(0, strani.get(1));
                strani.set(1, s);
            }
            if (p1 == null && p2 == null) {
                porocilo.napaka("tekma mreze brez udelezencev", opis);
                return null;
            }
            if (strani.size() < 2) {
                // polovicno znana tekma dogodka, ki se tece
                tekma.setPrijava1(p1);
                tekma.setSteviloNizov(d.getPrivzetoSteviloNizov());
                tekma.setStatus(StatusTekme.CAKA);
                Tekma shranjena = repo.tekme().save(tekma);
                vseTekme.add(shranjena);
                sled.tekme.put(t.path("id").asLong(), shranjena.getId());
                return shranjena;
            }
            izid(tekma, t, strani, p1, p2, opis);
            return shrani(tekma, t, strani, opis);
        }

        // ---------- Izid in zapis tekme ----------

        private void izid(Tekma tekma, JsonNode t, List<JsonNode> strani, Prijava p1, Prijava p2, String opis) {
            tekma.setPrijava1(p1);
            tekma.setPrijava2(p2);
            tekma.setSteviloNizov(d.getDisciplina() == Disciplina.EKIPNO ? d.getPrivzetoSteviloNizov()
                    : SrecanjaStupe.steviloNizov(strani.get(0).path("sets").size()));
            String status = t.path("status").asText();
            if ("SCORED".equals(status)) {
                // ekipna tekma: izid so dobljene podtekme (pri viru lahko nesesteti)
                Map<Long, Integer> izidEkipne = t.path("sub_matches").isEmpty() ? null : SrecanjaStupe.izidEkipneTekme(t);
                int n1 = izidEkipne == null ? strani.get(0).path("sets_won").asInt()
                        : izidEkipne.getOrDefault(strani.get(0).path("participant_id").asLong(), 0);
                int n2 = izidEkipne == null ? strani.get(1).path("sets_won").asInt()
                        : izidEkipne.getOrDefault(strani.get(1).path("participant_id").asLong(), 0);
                boolean brezBoja = strani.stream().anyMatch(s -> s.path("walkover").asBoolean(false));
                Prijava zmagovalec = prijave.get(t.path("winner").asLong(0));
                if (zmagovalec == null || !(zmagovalec == p1 || zmagovalec == p2)) {
                    zmagovalec = n1 == n2 ? null : n1 > n2 ? p1 : p2;
                }
                if (zmagovalec == null) {
                    porocilo.napaka("odigrana tekma brez zmagovalca", opis + " (" + n1 + ":" + n2 + ")");
                    return;
                }
                /* Zapisan samo zmagovalec (0 : 0): odigrana tekma brez znanega izida
                   po nizih - rating jo bere kot IzidTekme.samoZmagovalec. */
                if (!brezBoja && n1 == 0 && n2 == 0) {
                    porocilo.opozori("tekma z zapisanim zmagovalcem brez nizov (odigrana, izid po nizih ni znan)", opis);
                } else if (!brezBoja && (n1 == n2 || (zmagovalec == p1) != (n1 > n2))) {
                    porocilo.napaka("zmagovalec tekme se ne ujema z nizi", opis + " (" + n1 + ":" + n2 + ")");
                }
                tekma.setDobljeniNizi1(n1);
                tekma.setDobljeniNizi2(n2);
                tekma.setZmagovalec(zmagovalec);
                tekma.setIzidTip(brezBoja ? IzidTekme.BREZ_BOJA : IzidTekme.IGRANO);
                tekma.setStatus(StatusTekme.KONCANA);
            } else if ("IN_PROGRESS".equals(status)) {
                tekma.setStatus(StatusTekme.V_IGRI);
            } else {
                tekma.setStatus(p1 != null && p2 != null ? StatusTekme.PRIPRAVLJENA : StatusTekme.CAKA);
            }
        }

        private Tekma shrani(Tekma tekma, JsonNode t, List<JsonNode> strani, String opis) {
            Tekma shranjena = repo.tekme().save(tekma);
            vseTekme.add(shranjena);
            virTekme.put(shranjena.getId(), t);
            sled.tekme.put(t.path("id").asLong(), shranjena.getId());
            if (shranjena.getStatus() == StatusTekme.KONCANA) {
                porocilo.prestej(d.getDisciplina() == Disciplina.EKIPNO ? "odigranih ekipnih tekem"
                        : d.getDisciplina() == Disciplina.DVOJICE ? "odigranih tekem dvojic" : "odigranih tekem");
            }
            boolean igrano = shranjena.getIzidTip() == IzidTekme.IGRANO || shranjena.getStatus() == StatusTekme.V_IGRI;
            if (d.getDisciplina() != Disciplina.EKIPNO) {
                if (shranjena.getStatus() == StatusTekme.KONCANA && igrano) {
                    List<NizVnos> nizi = srecanja.nizi(strani.get(0).path("points"), strani.get(1).path("points"),
                            shranjena.getDobljeniNizi1(), shranjena.getDobljeniNizi2(), shranjena.getSteviloNizov(), opis);
                    for (int i = 0; i < nizi.size(); i++) {
                        repo.nizi().save(new Niz(shranjena, i + 1, nizi.get(i).tocke1(), nizi.get(i).tocke2()));
                    }
                    porocilo.prestej("nizov", nizi.size());
                }
                return shranjena;
            }
            if (igrano && !t.path("sub_matches").isEmpty()) {
                SrecanjaStupe.Oznake o = SrecanjaStupe.oznake(t);
                Ekipa domaci = ekipe.get(o.idDomacih());
                Ekipa gost = ekipe.get(o.idGostov());
                if (domaci == null || gost == null) {
                    porocilo.napaka("ekipna tekma z ekipo, ki ni prijavljena", opis);
                    return shranjena;
                }
                Srecanje s = new Srecanje(shranjena, domaci, gost);
                s.setPredvidenZacetek(UvozOblike.casovniZig(t.path("start_time").asText(null)) != null
                        ? UvozOblike.casovniZig(t.path("start_time").asText(null))
                        : datum == null ? null : datum.atStartOfDay());
                boolean koncano = shranjena.getStatus() == StatusTekme.KONCANA;
                s.setStatus(koncano ? StatusSrecanja.KONCANO : StatusSrecanja.POTEKA);
                s = repo.srecanja().save(s);
                SrecanjaStupe.Rezultat r = srecanja.zapisi(s, t, o, format, datum, ekipe, kadri, koncano, opis);
                s.setDobljeneDomaci(r.dobljeneDomaci());
                s.setDobljeneGost(r.dobljeneGost());
                repo.srecanja().save(s);
                sled.srecanja.put(t.path("id").asLong(), s.getId());
                if (koncano) {
                    int zaDomace = domaci == shranjena.getPrijava1().getEkipa() ? shranjena.getDobljeniNizi1() : shranjena.getDobljeniNizi2();
                    int zaGoste = domaci == shranjena.getPrijava1().getEkipa() ? shranjena.getDobljeniNizi2() : shranjena.getDobljeniNizi1();
                    if (zaDomace != r.dobljeneDomaci() || zaGoste != r.dobljeneGost()) {
                        porocilo.napaka("izid ekipne tekme se ne ujema s posamicnimi tekmami", opis + ": vir "
                                + zaDomace + ":" + zaGoste + ", posamicne " + r.dobljeneDomaci() + ":" + r.dobljeneGost());
                    }
                }
            } else if (shranjena.getStatus() == StatusTekme.KONCANA && shranjena.getIzidTip() == IzidTekme.IGRANO) {
                porocilo.opozori("ekipna tekma brez posamicnih tekem pri viru (zapisan samo izid)", opis);
            }
            return shranjena;
        }

        // ---------- Zakljucek ----------

        void zakljuci(SistemTekmovanja sistem) {
            kadri.shrani(repo.kadri(), porocilo, true);
            d.setStatus(vseTekme.isEmpty() ? StatusTekmovanja.PRIPRAVA
                    : vseTekme.stream().allMatch(t -> t.getStatus() == StatusTekme.KONCANA)
                            ? StatusTekmovanja.ZAKLJUCEN : StatusTekmovanja.V_TEKU);

            List<Prijava> vsePrijave = prijave.values().stream().distinct().toList();
            KoncnaMestaUvoza.mestaVSkupinah(vseSkupine, vsePrijave, vseTekme, razvrstitev);

            // koncna mesta: uradna, kjer jih vir ima; sicer po pravilu sistema
            if (!uradnaMesta.isEmpty()) {
                uradnaMesta.forEach((idUdelezenca, mesto) -> {
                    Prijava pr = prijave.get(idUdelezenca);
                    if (pr != null) {
                        pr.setKoncnoMesto(mesto);
                    }
                });
            } else if (d.getStatus() == StatusTekmovanja.ZAKLJUCEN) {
                KoncnaMestaUvoza.koncnaMesta(sistem, vseSkupine, vsePrijave, vseTekme, razvrstitev);
            }
            repo.prijave().saveAll(vsePrijave);
            repo.dogodki().save(d);
        }

        // ---------- Pomozno ----------

        private Tekma novaTekma(FazaTekme faza, int kolo, int pozicija) {
            Tekma t = new Tekma();
            t.setDogodek(d);
            t.setFaza(faza);
            t.setKolo(kolo);
            t.setPozicija(pozicija);
            t.setSteviloNizov(d.getPrivzetoSteviloNizov());
            return t;
        }

        private String opisTekme(JsonNode t, String kje) {
            List<String> imena = new ArrayList<>();
            for (JsonNode s : SrecanjaStupe.urejeni(t.path("participants"))) {
                JsonNode u = p.udelezenec(s.path("participant_id").asLong());
                imena.add(u == null ? "?" : UvozOblike.ocisti(u.path("participant_name").asText("?")));
            }
            return imeKat + " · " + (kje == null ? "" : kje + " · ") + "kolo " + t.path("round").path("order").asInt()
                    + ": " + String.join(" – ", imena) + " [Stupa " + t.path("id").asLong() + "]";
        }
    }

    // ---------------------------------------------------------------------
    // Pomozno
    // ---------------------------------------------------------------------

    private static VrstaIzlocilne vrstaIzlocilne(String ime, boolean glavnaZe) {
        String n = brezSumnikov(ime).toUpperCase();
        if (n.matches(".*ZA\\s*1\\s*\\.?\\s*MESTO.*")) {
            return VrstaIzlocilne.ZA_PRVO;
        }
        if (n.matches(".*ZA\\s*3\\s*\\.?\\s*MESTO.*")) {
            return VrstaIzlocilne.ZA_TRETJE;
        }
        if (jeTolazilnaPoImenu(ime)) {
            return VrstaIzlocilne.TOLAZILNA;
        }
        return glavnaZe ? VrstaIzlocilne.TOLAZILNA : VrstaIzlocilne.GLAVNA;
    }

    private static boolean jeTolazilnaPoImenu(String ime) {
        String n = brezSumnikov(ime).toUpperCase();
        return n.contains("CONS") || n.contains("TOLAZ");
    }

    /* Prvo mesto finalne skupine iz imena stopnje: "1.-4. MESTO", "Pos 5-8",
       "5-8", "1.-4- MESTO". */
    static Integer prvoMesto(String ime) {
        if (ime == null) {
            return null;
        }
        Matcher m = PRVO_MESTO.matcher(ime.trim());
        return m.find() ? Integer.valueOf(m.group(1)) : null;
    }

    private static String par(Prijava a, Prijava b) {
        long x = Math.min(a.getId(), b.getId());
        long y = Math.max(a.getId(), b.getId());
        return x + "|" + y;
    }

    private static String crka(int zaporedna) {
        return zaporedna <= 26 ? String.valueOf((char) ('A' + zaporedna - 1)) : "S" + zaporedna;
    }

    private static String imeTekmovanja(String ime, long id) {
        String i = UvozOblike.prirezi(ime == null ? null : ime.replaceAll("\\s+", " "), 80);
        return i == null || i.length() < 3 ? "Turnir " + id : i;
    }

    private static String imeKategorije(JsonNode kat) {
        String ime = UvozOblike.ocisti(kat.path("category_display_label").asText(null));
        if (ime == null) {
            ime = UvozOblike.ocisti(kat.path("category_description").asText(null));
        }
        if (ime == null) {
            ime = UvozOblike.ocisti(kat.path("name").asText(null));
        }
        ime = UvozOblike.prirezi(ime == null ? null : ime.replaceAll("\\s+", " "), 60);
        return ime == null || ime.length() < 3 ? "Kategorija " + kat.path("id").asLong() : ime;
    }

    private static SpolKategorija spolKategorije(JsonNode kat, int tip) {
        String opis = brezSumnikov(kat.path("category_description").asText("") + " "
                + kat.path("category_display_label").asText("")).toUpperCase();
        if (tip == 4 || (tip == 3 && (opis.contains("MESAN") || kat.path("gender_id").asInt() == 3))) {
            return SpolKategorija.MESANO;
        }
        return switch (kat.path("gender_id").asInt()) {
            case 1 -> SpolKategorija.MOSKI;
            case 2 -> SpolKategorija.ZENSKE;
            default -> SpolKategorija.KDORKOLI;
        };
    }

    private static int privzetoSteviloNizov(List<JsonNode> tekme, Disciplina disciplina) {
        Map<Integer, Integer> stetje = new HashMap<>();
        for (JsonNode t : tekme) {
            List<JsonNode> vir = new ArrayList<>();
            if (disciplina == Disciplina.EKIPNO) {
                t.path("sub_matches").forEach(vir::add);
            } else {
                vir.add(t);
            }
            for (JsonNode x : vir) {
                JsonNode prva = x.path("participants").path(0);
                if (prva.has("sets") && prva.path("sets").size() > 0) {
                    stetje.merge(SrecanjaStupe.steviloNizov(prva.path("sets").size()), 1, Integer::sum);
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

    static List<String> imenaOseb(PosnetekDogodka p) {
        List<String> imena = new ArrayList<>();
        for (JsonNode u : p.udelezenci()) {
            u.path("event_participant_details").forEach(pd -> imena.add(pd.path("name").asText(null)));
        }
        for (JsonNode t : p.tekme()) {
            for (JsonNode pod : t.path("sub_matches")) {
                pod.path("participants").forEach(s -> s.path("participant_details")
                        .forEach(pd -> imena.add(pd.path("name").asText(null))));
            }
        }
        return imena;
    }

    static String brezSumnikov(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
