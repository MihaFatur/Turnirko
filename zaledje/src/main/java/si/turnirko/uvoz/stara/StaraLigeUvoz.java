/* Uvoz ligaskih (ekipnih) tekmovanj s stare strani NTZS: liga -> ekipe +
   kader -> srecanja -> postave -> posamicne tekme srecanja.

   "Liga" je tu vsaka podliga vira: poleg SNTL tudi pokal (PTRS) in ekipni
   drzavni prvenstvi mladincev in kadetov (EDPM, EDPK). Vir jih vodi enako -
   kot razpored kol z zapisniki srecanj - in tako se preslikajo tudi v
   Turnirko. Kvalifikacijske skupine in koncnice so pri viru SAMOSTOJNE
   podlige ("sntl2021m1", "sntl2021m1_q", "sntl2021m1_po") in ostanejo
   samostojne lige, ker imajo vsaka svoj razpored in svojo lestvico.

   Preslikava, ki ni ocitna:

   * FORMAT SRECANJA se ne domneva iz imena lige, ampak se PREBERE IZ
     DEJANSKEGA razporeda. Isto tekmovanje je skozi leta menjalo format:
     1. SNTL moski je leta 2021 igral devet posamicnih brez dvojic, danes
     dvojice in sest. Vzamemo najdaljse srecanje lige (pri hitro odlocenih se
     zadnje tekme ne igrajo) in zaporedje oznak primerjamo z razporedi, ki jih
     pozna FormatSrecanja.

   * TERMIN je last srecanja, ne kola: 2. SNTL odigra dve koli v istem dnevu
     na enem prizoriscu (dopoldne in popoldne), zato ima vsako srecanje svojo
     uro iz zapisnika. Ta ura je hkrati tisto, po cemer se tekme uvrstijo v
     casovno vrsto za obracun ELO. */
package si.turnirko.uvoz.stara;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

import si.turnirko.modeli.Ekipa;
import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.IzidTekme;
import si.turnirko.modeli.KaderEkipe;
import si.turnirko.modeli.Klub;
import si.turnirko.modeli.Liga;
import si.turnirko.modeli.PostavaSrecanja;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.Srecanje;
import si.turnirko.modeli.StatusSrecanja;
import si.turnirko.modeli.StatusTekmeSrecanja;
import si.turnirko.modeli.StatusTekmovanja;
import si.turnirko.modeli.StranEkipe;
import si.turnirko.modeli.TekmaSrecanja;
import si.turnirko.modeli.TipTekmeSrecanja;
import si.turnirko.repozitoriji.EkipaRepozitorij;
import si.turnirko.repozitoriji.KaderEkipeRepozitorij;
import si.turnirko.repozitoriji.LigaRepozitorij;
import si.turnirko.repozitoriji.PostavaSrecanjaRepozitorij;
import si.turnirko.repozitoriji.SrecanjeRepozitorij;
import si.turnirko.repozitoriji.TekmaSrecanjaRepozitorij;
import si.turnirko.uvoz.EloUvoz;
import si.turnirko.uvoz.SifrantiUvoz;
import si.turnirko.uvoz.UvozOblike;
import si.turnirko.uvoz.UvozPorocilo;
import si.turnirko.uvoz.ZbirnikSifrantov;

public class StaraLigeUvoz {

    private final LigaRepozitorij ligaRepozitorij;
    private final EkipaRepozitorij ekipaRepozitorij;
    private final KaderEkipeRepozitorij kaderRepozitorij;
    private final SrecanjeRepozitorij srecanjeRepozitorij;
    private final PostavaSrecanjaRepozitorij postavaRepozitorij;
    private final TekmaSrecanjaRepozitorij tekmaSrecanjaRepozitorij;
    private final SifrantiUvoz sifranti;
    private final UvozPorocilo porocilo;

    private final List<EloUvoz.VrstaTekme> uvozeneTekme = new ArrayList<>();

    public StaraLigeUvoz(LigaRepozitorij ligaRepozitorij, EkipaRepozitorij ekipaRepozitorij,
                         KaderEkipeRepozitorij kaderRepozitorij, SrecanjeRepozitorij srecanjeRepozitorij,
                         PostavaSrecanjaRepozitorij postavaRepozitorij,
                         TekmaSrecanjaRepozitorij tekmaSrecanjaRepozitorij,
                         SifrantiUvoz sifranti, UvozPorocilo porocilo) {
        this.ligaRepozitorij = ligaRepozitorij;
        this.ekipaRepozitorij = ekipaRepozitorij;
        this.kaderRepozitorij = kaderRepozitorij;
        this.srecanjeRepozitorij = srecanjeRepozitorij;
        this.postavaRepozitorij = postavaRepozitorij;
        this.tekmaSrecanjaRepozitorij = tekmaSrecanjaRepozitorij;
        this.sifranti = sifranti;
        this.porocilo = porocilo;
    }

    public List<EloUvoz.VrstaTekme> uvozeneTekme() {
        return uvozeneTekme;
    }

    public void uvozi(JsonNode vir) {
        List<JsonNode> kola = StaraArhiv.kola(vir);
        if (kola.isEmpty()) {
            return;
        }

        FormatSrecanja format = dolociFormat(vir);

        Liga liga = new Liga();
        liga.setIme(UvozOblike.prirezi(vir.path("ime").asText(), 80));
        liga.setSezona(UvozOblike.prirezi(vir.path("sezona").asText(null), 20));
        liga.setSpolKategorija("ZENSKE".equals(vir.path("spol").asText())
                ? SpolKategorija.ZENSKE : SpolKategorija.MOSKI);
        liga.setFormatSrecanja(format);
        liga.setSteviloNizov(5);
        liga.setStatus(StatusTekmovanja.ZAKLJUCEN);
        liga.setZacetekPrvegaKola(prviTermin(kola));
        // Srecanje se v vseh razporedih NTZS konca pri vecini tekem
        // (SNTL_BREZ_DVOJIC 5 od 9, OLIMPIJSKI 3 od 5).
        liga.setZmagZaSrecanje(format.stTekem() / 2 + 1);
        liga = ligaRepozitorij.save(liga);
        porocilo.prestej("lig");

        Map<String, Ekipa> ekipe = ustvariEkipe(liga, vir);
        // Kader zbiramo cez celo ligo in ga zapisemo na koncu: isti igralec
        // nastopi v desetih kolih, tabela kader_ekipe pa ima UNIQUE (ekipa, igralec).
        Map<Long, Map<Long, Igralec>> kader = new LinkedHashMap<>();
        zabelezikaderIzVira(vir, ekipe, kader);

        for (JsonNode kolo : kola) {
            for (JsonNode s : kolo.path("srecanja")) {
                uvoziSrecanje(liga, kolo.path("st").asInt(1), s, ekipe, kader);
            }
        }
        shraniKader(ekipe, kader);
    }

    /* Format lige iz dejanskega razporeda: vzamemo srecanje z najvec tekmami
       (tam so vidna vsa mesta razporeda) in zaporedje oznak primerjamo z
       razporedi, ki jih pozna FormatSrecanja.

       Kadar se ne ujema noben - nobeno srecanje lige ni bilo odigrano do konca -
       vzamemo format, katerega razpored se z opazenim ZACNE. Takih je lahko
       vec: "A-X, B-Y, C-Z" je zacetek tako SNTL_BREZ_DVOJIC kot OLIMPIJSKI.
       Odloci PRAG ZMAG: srecanje se konca, ko ena stran doseze vecino tekem,
       zato je format, ki se konca sele pri petih zmagah, izkljucen, ce so bile
       odigrane samo tri tekme. Med preostalimi vzamemo najkrajsega - dolgega
       ne bi bilo mogoce koncati tam, kjer se je koncal. */
    private FormatSrecanja dolociFormat(JsonNode vir) {
        List<String> najdaljse = List.of();
        for (JsonNode kolo : StaraArhiv.kola(vir)) {
            for (JsonNode s : kolo.path("srecanja")) {
                List<String> oznake = new ArrayList<>();
                for (JsonNode t : s.path("tekme")) {
                    oznake.add(oznakaTekme(t));
                }
                if (oznake.size() > najdaljse.size()) {
                    najdaljse = oznake;
                }
            }
        }
        if (najdaljse.isEmpty()) {
            return FormatSrecanja.SNTL_BREZ_DVOJIC;
        }

        for (FormatSrecanja kandidat : FormatSrecanja.values()) {
            if (razpored(kandidat).equals(najdaljse)) {
                return kandidat;
            }
        }

        int odigranih = najdaljse.size();
        FormatSrecanja najboljsi = null;
        for (FormatSrecanja kandidat : FormatSrecanja.values()) {
            List<String> pricakovan = razpored(kandidat);
            if (pricakovan.size() < odigranih
                    || !pricakovan.subList(0, odigranih).equals(najdaljse)) {
                continue;
            }
            // srecanje se konca pri vecini tekem - format, ki bi zahteval vec
            // odigranih, tega srecanja ne bi mogel koncati tam, kjer se je
            if (pricakovan.size() / 2 + 1 > odigranih) {
                continue;
            }
            if (najboljsi == null || pricakovan.size() < najboljsi.stTekem()) {
                najboljsi = kandidat;
            }
        }
        if (najboljsi != null) {
            porocilo.opozori("razpored lige je nepopoln (nobeno srecanje ni bilo odigrano do konca)",
                    vir.path("id").asText() + ": " + String.join(", ", najdaljse) + " -> " + najboljsi);
            return najboljsi;
        }

        porocilo.opozori("razpored lige se ne ujema z nobenim formatom",
                vir.path("id").asText() + ": " + String.join(", ", najdaljse));
        return FormatSrecanja.SNTL_BREZ_DVOJIC;
    }

    private static List<String> razpored(FormatSrecanja format) {
        return format.razpored().stream().map(FormatSrecanja.MestoTekme::oznaka).toList();
    }

    private static String oznakaTekme(JsonNode tekma) {
        if ("DVOJICE".equals(tekma.path("tip").asText())) {
            return "dvojice";
        }
        return tekma.path("pozD").asText("?") + "-" + tekma.path("pozG").asText("?");
    }

    private LocalDateTime prviTermin(List<JsonNode> kola) {
        return kola.stream()
                .flatMap(k -> {
                    List<JsonNode> s = new ArrayList<>();
                    k.path("srecanja").forEach(s::add);
                    return s.stream();
                })
                .map(this::termin)
                .filter(t -> t != null)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    /* Datum in ura srecanja iz zapisnika; kadar ure ni, polnoc. */
    private LocalDateTime termin(JsonNode srecanje) {
        String datum = srecanje.path("datum").asText(null);
        if (datum == null || datum.isBlank()) {
            return null;
        }
        String ura = srecanje.path("ura").asText(null);
        return UvozOblike.casovniZig(
                (ura == null || ura.isBlank()) ? datum : (datum + "T" + ura + ":00"));
    }

    private Map<String, Ekipa> ustvariEkipe(Liga liga, JsonNode vir) {
        Map<String, Ekipa> ekipe = new LinkedHashMap<>();
        Map<Long, Integer> zaporednaVKlubu = new HashMap<>();
        Set<String> zasedenaImena = new HashSet<>();

        for (JsonNode e : vir.path("ekipe")) {
            String slug = e.path("slug").asText(null);
            if (slug == null) {
                continue;
            }
            String slugKluba = e.path("klub").asText(null);
            Klub klub = (slugKluba == null) ? null
                    : sifranti.klubi().get(ZbirnikSifrantov.kljuc(ZbirnikSifrantov.VIR_STARA, slugKluba));

            // Ime ekipe je edino, po cemer ekipi loci bralec razporeda
            // ("Savinja I" proti "Savinja II"), zato ga vedno zapisemo.
            String ime = UvozOblike.prirezi(UvozOblike.ocisti(e.path("ime").asText(null)), 60);
            if (ime == null || ime.length() < 2) {
                ime = (klub == null) ? ("Ekipa " + slug) : klub.getIme();
            }
            String osnovno = ime;
            int stevec = 2;
            while (!zasedenaImena.add(ime)) {
                ime = UvozOblike.prirezi(osnovno + " (" + stevec++ + ")", 60);
            }

            int zaporedna = (klub == null) ? 1 : zaporednaVKlubu.merge(klub.getId(), 1, Integer::sum);
            ekipe.put(slug, ekipaRepozitorij.save(new Ekipa(liga, klub, zaporedna, ime)));
            porocilo.prestej("ekip");
        }
        return ekipe;
    }

    /* Kader s strani ekipe. Vir ga navede v celoti, tudi za igralce, ki v
       posnetih srecanjih niso nastopili; tiste, ki so, dodajo srecanja sama. */
    private void zabelezikaderIzVira(JsonNode vir, Map<String, Ekipa> ekipe,
                                     Map<Long, Map<Long, Igralec>> kader) {
        for (JsonNode e : vir.path("ekipe")) {
            Ekipa ekipa = ekipe.get(e.path("slug").asText(null));
            if (ekipa == null) {
                continue;
            }
            for (JsonNode idIgralca : e.path("kader")) {
                Igralec igralec = igralec(idIgralca.asText(null));
                if (igralec != null) {
                    kader.computeIfAbsent(ekipa.getId(), k -> new LinkedHashMap<>())
                            .putIfAbsent(igralec.getId(), igralec);
                }
            }
        }
    }

    private void uvoziSrecanje(Liga liga, int kolo, JsonNode vir, Map<String, Ekipa> ekipe,
                               Map<Long, Map<Long, Igralec>> kader) {
        Ekipa domaci = ekipe.get(vir.path("domaci").asText(null));
        Ekipa gost = ekipe.get(vir.path("gost").asText(null));
        if (domaci == null || gost == null || domaci.getId().equals(gost.getId())) {
            porocilo.opozori("srecanje z neznano ekipo", vir.path("id").asText());
            return;
        }

        Srecanje srecanje = new Srecanje(liga, Math.max(1, kolo), domaci, gost);
        srecanje.setPredvidenZacetek(termin(vir));
        srecanje.setDobljeneDomaci(Math.max(0, vir.path("izidD").asInt(0)));
        srecanje.setDobljeneGost(Math.max(0, vir.path("izidG").asInt(0)));
        srecanje.setStatus(StatusSrecanja.KONCANO);
        Srecanje shranjeno = srecanjeRepozitorij.save(srecanje);
        porocilo.prestej("srecanj");

        Map<String, Igralec> postava = new LinkedHashMap<>();
        Set<Long> vDvojicah = new HashSet<>();
        for (JsonNode t : vir.path("tekme")) {
            uvoziTekmoSrecanja(shranjeno, t, postava, vDvojicah, kader, domaci, gost);
        }
        shraniPostavo(shranjeno, postava, vDvojicah);
    }

    private void uvoziTekmoSrecanja(Srecanje srecanje, JsonNode vir, Map<String, Igralec> postava,
                                    Set<Long> vDvojicah, Map<Long, Map<Long, Igralec>> kader,
                                    Ekipa domaci, Ekipa gost) {
        boolean dvojice = "DVOJICE".equals(vir.path("tip").asText());
        List<Igralec> domaciIgralci = igralci(vir.path("igralciD"));
        List<Igralec> gostIgralci = igralci(vir.path("igralciG"));
        if (domaciIgralci.isEmpty() || gostIgralci.isEmpty()) {
            porocilo.opozori("tekma srecanja brez igralcev (izpuscena)", srecanje.getId() + " / "
                    + vir.path("st").asInt());
            return;
        }
        zabelezi(kader, domaci, domaciIgralci);
        zabelezi(kader, gost, gostIgralci);

        String oznakaD = vir.path("pozD").asText("?");
        String oznakaG = vir.path("pozG").asText("?");
        if (dvojice) {
            domaciIgralci.forEach(i -> vDvojicah.add(i.getId()));
            gostIgralci.forEach(i -> vDvojicah.add(i.getId()));
        } else {
            postava.putIfAbsent("DOMACI|" + oznakaD, domaciIgralci.get(0));
            postava.putIfAbsent("GOST|" + oznakaG, gostIgralci.get(0));
        }

        int niziD = vir.path("nizi1").asInt();
        int niziG = vir.path("nizi2").asInt();
        if (niziD == niziG) {
            porocilo.opozori("tekma srecanja brez zmagovalca pri viru (izpuscena)",
                    srecanje.getId() + " / " + vir.path("st").asInt());
            return;
        }

        TekmaSrecanja tekma = new TekmaSrecanja(srecanje, Math.max(1, vir.path("st").asInt(1)),
                dvojice ? TipTekmeSrecanja.DVOJICE : TipTekmeSrecanja.POSAMICNA,
                oznakaTekme(vir), steviloNizov(niziD + niziG));
        tekma.setIgralecDomaci(domaciIgralci.get(0));
        tekma.setIgralecGost(gostIgralci.get(0));
        if (dvojice) {
            tekma.setIgralecDomaci2(domaciIgralci.size() > 1 ? domaciIgralci.get(1) : null);
            tekma.setIgralecGost2(gostIgralci.size() > 1 ? gostIgralci.get(1) : null);
        }
        tekma.setDobljeniNiziDomaci(niziD);
        tekma.setDobljeniNiziGost(niziG);
        tekma.setStatus(StatusTekmeSrecanja.KONCANA);
        tekma.setZmagovalecStran(niziD > niziG ? StranEkipe.DOMACI : StranEkipe.GOST);
        tekma.setIzidTip(IzidTekme.IGRANO);

        TekmaSrecanja shranjena = tekmaSrecanjaRepozitorij.save(tekma);
        porocilo.prestej("tekem (ligaskih)");
        uvozeneTekme.add(EloUvoz.VrstaTekme.ligaska(shranjena.getId(),
                srecanje.getPredvidenZacetek(), srecanje.getKolo(), shranjena.getZaporedje()));
    }

    /* Postavo zapisemo sele, ko so znane vse tekme srecanja: sele takrat vemo,
       kdo je igral dvojice. Shema ima UNIQUE po mestu in po igralcu na strani,
       zato podvojitve (menjava igralca sredi srecanja) izpustimo z opozorilom. */
    private void shraniPostavo(Srecanje srecanje, Map<String, Igralec> postava, Set<Long> vDvojicah) {
        Set<String> zasedeno = new HashSet<>();
        for (Map.Entry<String, Igralec> vnos : postava.entrySet()) {
            String[] deli = vnos.getKey().split("\\|");
            StranEkipe stran = StranEkipe.valueOf(deli[0]);
            String mesto = deli[1];
            Igralec igralec = vnos.getValue();

            if (mesto.length() != 1) {
                continue; // oznaka mesta je v shemi natanko en znak
            }
            if (!zasedeno.add(stran + "|" + igralec.getId())) {
                porocilo.opozori("isti igralec na dveh mestih v postavi",
                        "srecanje " + srecanje.getId() + ", " + igralec.polnoIme());
                continue;
            }
            postavaRepozitorij.save(new PostavaSrecanja(srecanje, stran, mesto, igralec,
                    vDvojicah.contains(igralec.getId())));
            porocilo.prestej("mest v postavi");
        }
    }

    private void shraniKader(Map<String, Ekipa> ekipe, Map<Long, Map<Long, Igralec>> kader) {
        Map<Long, Ekipa> poId = new HashMap<>();
        ekipe.values().forEach(e -> poId.put(e.getId(), e));

        for (Map.Entry<Long, Map<Long, Igralec>> vnos : kader.entrySet()) {
            if (poId.get(vnos.getKey()) == null) {
                continue;
            }
            for (Igralec igralec : vnos.getValue().values()) {
                kaderRepozitorij.save(new KaderEkipe(poId.get(vnos.getKey()), igralec, null));
                porocilo.prestej("igralcev v kadrih");
            }
        }
    }

    private void zabelezi(Map<Long, Map<Long, Igralec>> kader, Ekipa ekipa, List<Igralec> igralci) {
        Map<Long, Igralec> zbir = kader.computeIfAbsent(ekipa.getId(), k -> new LinkedHashMap<>());
        igralci.forEach(i -> zbir.putIfAbsent(i.getId(), i));
    }

    private List<Igralec> igralci(JsonNode idji) {
        List<Igralec> najdeni = new ArrayList<>();
        for (JsonNode id : idji) {
            Igralec i = igralec(id.asText(null));
            if (i != null && najdeni.stream().noneMatch(x -> x.getId().equals(i.getId()))) {
                najdeni.add(i);
            }
        }
        return najdeni;
    }

    private Igralec igralec(String idVira) {
        if (idVira == null || idVira.isBlank()) {
            return null;
        }
        return sifranti.igralci().get(ZbirnikSifrantov.kljuc(ZbirnikSifrantov.VIR_STARA, idVira));
    }

    private int steviloNizov(int odigranihNizov) {
        if (odigranihNizov <= 3) {
            return 3;
        }
        return odigranihNizov <= 5 ? 5 : 7;
    }
}
