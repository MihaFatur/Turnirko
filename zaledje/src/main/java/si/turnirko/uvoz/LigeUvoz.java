/* Uvoz ligaskih (ekipnih) tekmovanj: liga -> ekipe + kader -> srecanja ->
   postave -> posamicne tekme srecanja.

   Preslikava, ki ni ocitna:

   * SRECANJE je pri Stupi navadna tekma med udelezencema tipa "ekipa", njene
     posamicne tekme pa so v polju sub_matches. Zato so tekme lige v posnetku
     dve ravni globoko in jih navadno branje tekem sploh ne vidi.

   * POSTAVA se ne prenese kot seznam, ampak se PREBERE IZ OZNAK IGRALCEV v
     posamicnih tekmah: participant_label je "A"/"B"/"C" za domace, "X"/"Y"/"Z"
     za goste in "DA1"/"DA2"/"DB1"/"DB2" za para dvojic. Igralec, ki nastopi v
     dvojicah, dobi v postavi zastavico v_dvojici.

   * VRSTNI RED TEKEM se ne domneva iz formata, ampak se prepise tak, kot je
     zapisan v viru. V podatkih NTZS so trije razlicni razporedi (glej
     PRIMERJAVA_FORMATOV spodaj) in Turnirkov enum FormatSrecanja pozna samo
     enega - ce bi razpored racunali iz enuma, bi si dve ligi izmislili tekme,
     ki jih ni bilo. Oznaka tekme (npr. "B-X") je v shemi prosto besedilo prav
     zato, da to lahko naredimo. */
package si.turnirko.uvoz;

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

public class LigeUvoz {

    private final LigaRepozitorij ligaRepozitorij;
    private final EkipaRepozitorij ekipaRepozitorij;
    private final KaderEkipeRepozitorij kaderRepozitorij;
    private final SrecanjeRepozitorij srecanjeRepozitorij;
    private final PostavaSrecanjaRepozitorij postavaRepozitorij;
    private final TekmaSrecanjaRepozitorij tekmaSrecanjaRepozitorij;
    private final SifrantiUvoz sifranti;
    private final UvozPorocilo porocilo;

    /* Kazalci na uvozene posamicne tekme lig za poznejsi obracun ELO
       (identifikatorji, ne entitete - te so po shranjevanju odklopljene). */
    private final List<EloUvoz.VrstaTekme> uvozeneTekme = new ArrayList<>();

    public LigeUvoz(LigaRepozitorij ligaRepozitorij, EkipaRepozitorij ekipaRepozitorij,
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

    public void uvozi(StupaArhiv arhiv, JsonNode dogodekStupe, String imeSezone) {
        long idStupe = dogodekStupe.path("id").asLong();
        List<JsonNode> srecanja = arhiv.tekme(idStupe);
        if (srecanja.isEmpty()) {
            return;
        }

        Liga liga = new Liga();
        liga.setIme(UvozOblike.prirezi(dogodekStupe.path("name").asText(), 80));
        liga.setSezona(UvozOblike.prirezi(imeSezone, 20));
        liga.setSpolKategorija(spolLige(arhiv, idStupe, dogodekStupe));
        FormatSrecanja format = dolociFormat(srecanja, dogodekStupe, porocilo);
        liga.setFormatSrecanja(format);
        liga.setSteviloNizov(5);
        liga.setStatus(StatusTekmovanja.ZAKLJUCEN);
        liga.setZacetekPrvegaKola(UvozOblike.casovniZig(dogodekStupe.path("event_start_date").asText(null)));
        // V vseh treh razporedih NTZS se srecanje konca pri vecini tekem
        // (SNTL 6 od 10, SNTL_PRVA 4 od 7, SNTL_BREZ_DVOJIC 5 od 9) - preverjeno
        // na podatkih: nobeno srecanje nima manj odigranih tekem od tega praga.
        liga.setZmagZaSrecanje(format.stTekem() / 2 + 1);
        liga = ligaRepozitorij.save(liga);
        porocilo.prestej("lig");

        Map<Long, Ekipa> ekipe = ustvariEkipe(liga, srecanja);
        Map<Long, Map<Long, Igralec>> kader = new LinkedHashMap<>();


        for (JsonNode s : srecanja) {
            uvoziSrecanje(liga, s, ekipe, kader);
        }
        shraniKader(ekipe, kader);
    }

    /* Format lige ugotovimo iz DEJANSKEGA razporeda in ne iz imena tekmovanja.
       Vzamemo srecanje z najvec tekmami (tam so vidna vsa mesta razporeda,
       tudi tista, ki se pri hitro odlocenih srecanjih ne igrajo) in zaporedje
       oznak primerjamo z razporedi, ki jih pozna FormatSrecanja. */
    private FormatSrecanja dolociFormat(List<JsonNode> srecanja, JsonNode dogodekStupe,
                                        UvozPorocilo porocilo) {
        JsonNode najdaljse = srecanja.stream()
                .max(Comparator.comparingInt(s -> s.path("sub_matches").size()))
                .orElse(null);
        if (najdaljse == null || najdaljse.path("sub_matches").isEmpty()) {
            return FormatSrecanja.SNTL;
        }

        List<JsonNode> podtekme = new ArrayList<>();
        najdaljse.path("sub_matches").forEach(podtekme::add);
        podtekme.sort(Comparator.comparingInt(p -> p.path("order").asInt()));

        List<String> oznake = new ArrayList<>();
        for (JsonNode p : podtekme) {
            List<JsonNode> strani = urejeneStrani(p);
            if (strani.size() < 2) {
                continue;
            }
            boolean dvojice = p.path("match_type").asInt() == 3 || jeDvojice(strani);
            oznake.add(dvojice ? "dvojice" : (oznakaMesta(strani.get(0)) + "-" + oznakaMesta(strani.get(1))));
        }

        for (FormatSrecanja kandidat : FormatSrecanja.values()) {
            List<String> pricakovane = kandidat.razpored().stream()
                    .map(FormatSrecanja.MestoTekme::oznaka).toList();
            if (pricakovane.equals(oznake)) {
                return kandidat;
            }
        }
        porocilo.opozori("razpored lige se ne ujema z nobenim formatom",
                dogodekStupe.path("name").asText() + ": " + String.join(", ", oznake));
        return FormatSrecanja.SNTL;
    }

    /* Spol lige preberemo iz kategorije prve tekme; ce je ni, iz imena. */
    private SpolKategorija spolLige(StupaArhiv arhiv, long idStupe, JsonNode dogodekStupe) {
        for (JsonNode t : arhiv.tekme(idStupe)) {
            int gender = t.path("category").path("gender_id").asInt(0);
            if (gender > 0) {
                return UvozOblike.spolKategorija(gender);
            }
        }
        String ime = dogodekStupe.path("name").asText("").toUpperCase();
        if (ime.contains("ŽENSK")) {
            return SpolKategorija.ZENSKE;
        }
        return ime.contains("MOŠK") ? SpolKategorija.MOSKI : SpolKategorija.MESANO;
    }

    private Map<Long, Ekipa> ustvariEkipe(Liga liga, List<JsonNode> srecanja) {
        // Ekipe se v podatkih pojavijo samo kot udelezenci srecanj, zato jih
        // poberemo od tam; vrstni red je urejen po id-ju, da je uvoz ponovljiv.
        Map<Long, JsonNode> surove = new LinkedHashMap<>();
        for (JsonNode s : srecanja) {
            for (JsonNode u : s.path("participants")) {
                surove.putIfAbsent(u.path("participant_id").asLong(), u);
            }
        }

        Map<Long, Ekipa> ekipe = new HashMap<>();
        Map<String, Integer> zaporednaVKlubu = new HashMap<>();
        Set<String> zasedenaImena = new HashSet<>();

        for (Map.Entry<Long, JsonNode> vnos : surove.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList()) {

            JsonNode u = vnos.getValue();
            String idKluba = idKluba(u);
            Klub klub = (idKluba == null) ? null : sifranti.klubi().get(idKluba);

            // Ime ekipe iz Stupe je edino, po cemer ekipi loci bralec razporeda
            // ("Savinja 1" proti "Savinja 2"), zato ga vedno zapisemo.
            String ime = UvozOblike.prirezi(u.path("participant_name").asText(null), 60);
            if (ime == null || ime.length() < 2) {
                ime = (klub == null) ? ("Ekipa " + vnos.getKey()) : klub.getIme();
            }
            // shema zahteva enolicno prikazano ime v ligi
            String osnovno = ime;
            int stevec = 2;
            while (!zasedenaImena.add(ime)) {
                ime = UvozOblike.prirezi(osnovno + " (" + stevec++ + ")", 60);
            }

            int zaporedna = 1;
            if (klub != null) {
                zaporedna = zaporednaVKlubu.merge(idKluba, 1, Integer::sum);
            }
            ekipe.put(vnos.getKey(), ekipaRepozitorij.save(new Ekipa(liga, klub, zaporedna, ime)));
            porocilo.prestej("ekip");
        }
        return ekipe;
    }

    private String idKluba(JsonNode udelezenec) {
        for (JsonNode s : udelezenec.path("selected_parents")) {
            long id = s.path("user_role_id").asLong(0);
            if (id != 0) {
                return ZbirnikSifrantov.kljuc(ZbirnikSifrantov.VIR_STUPA, id);
            }
        }
        return null;
    }

    private void uvoziSrecanje(Liga liga, JsonNode s, Map<Long, Ekipa> ekipe,
                               Map<Long, Map<Long, Igralec>> kader) {
        List<JsonNode> strani = new ArrayList<>();
        s.path("participants").forEach(strani::add);
        strani.sort(Comparator.comparingInt(p -> p.path("order").asInt()));
        if (strani.isEmpty()) {
            // prazen termin v kolu: pri lihem stevilu ekip ena vsako kolo pocije
            porocilo.prestej("prostih terminov (liho stevilo ekip)");
            return;
        }
        if (strani.size() < 2) {
            porocilo.opozori("srecanje samo z eno ekipo", "stupa tekma " + s.path("id").asLong());
            return;
        }

        Ekipa domaci = ekipe.get(strani.get(0).path("participant_id").asLong());
        Ekipa gost = ekipe.get(strani.get(1).path("participant_id").asLong());
        if (domaci == null || gost == null || domaci.getId().equals(gost.getId())) {
            porocilo.opozori("srecanje z neznano ekipo", "stupa tekma " + s.path("id").asLong());
            return;
        }

        Srecanje srecanje = new Srecanje(liga, Math.max(1, s.path("round").path("order").asInt(1)),
                domaci, gost);
        srecanje.setPredvidenZacetek(UvozOblike.casovniZig(s.path("start_time").asText(null)));

        List<JsonNode> podtekme = new ArrayList<>();
        s.path("sub_matches").forEach(podtekme::add);
        podtekme.sort(Comparator.comparingInt(p -> p.path("order").asInt()));

        int dobljeneDomaci = 0;
        int dobljeneGost = 0;
        for (JsonNode p : podtekme) {
            if (!"SCORED".equals(p.path("status").asText(""))) {
                continue;
            }
            List<JsonNode> pStrani = urejeneStrani(p);
            if (pStrani.size() < 2) {
                continue;
            }
            if (pStrani.get(0).path("sets_won").asInt() > pStrani.get(1).path("sets_won").asInt()) {
                dobljeneDomaci++;
            } else {
                dobljeneGost++;
            }
        }
        srecanje.setDobljeneDomaci(dobljeneDomaci);
        srecanje.setDobljeneGost(dobljeneGost);
        srecanje.setStatus(podtekme.isEmpty() ? StatusSrecanja.RAZPORED
                : (dobljeneDomaci + dobljeneGost > 0 ? StatusSrecanja.KONCANO : StatusSrecanja.POTEKA));
        Srecanje shranjeno = srecanjeRepozitorij.save(srecanje);
        porocilo.prestej("srecanj");

        Map<String, Igralec> postava = new LinkedHashMap<>();
        Set<Long> vDvojicah = new HashSet<>();
        for (JsonNode p : podtekme) {
            uvoziTekmoSrecanja(shranjeno, p, postava, vDvojicah, kader, domaci, gost);
        }
        shraniPostavo(shranjeno, postava, vDvojicah);
    }

    private void uvoziTekmoSrecanja(Srecanje srecanje, JsonNode p, Map<String, Igralec> postava,
                                    Set<Long> vDvojicah, Map<Long, Map<Long, Igralec>> kader,
                                    Ekipa domaci, Ekipa gost) {
        List<JsonNode> strani = urejeneStrani(p);
        if (strani.size() < 2) {
            return;
        }
        // match_type 3 = dvojice, 1 = posamicna; dodatno preverimo se po tem,
        // ali ima stran dva igralca, ker je tip pri nekaterih ligah prazen
        boolean dvojice = p.path("match_type").asInt() == 3 || jeDvojice(strani);

        List<Igralec> domaciIgralci = igralci(strani.get(0));
        List<Igralec> gostIgralci = igralci(strani.get(1));
        zabelezi(kader, domaci, domaciIgralci);
        zabelezi(kader, gost, gostIgralci);

        String oznakaD = oznakaMesta(strani.get(0));
        String oznakaG = oznakaMesta(strani.get(1));
        String oznaka = dvojice ? "dvojice" : (oznakaD + "-" + oznakaG);

        if (dvojice) {
            domaciIgralci.forEach(i -> vDvojicah.add(i.getId()));
            gostIgralci.forEach(i -> vDvojicah.add(i.getId()));
        } else {
            if (!domaciIgralci.isEmpty()) {
                postava.putIfAbsent("DOMACI|" + oznakaD, domaciIgralci.get(0));
            }
            if (!gostIgralci.isEmpty()) {
                postava.putIfAbsent("GOST|" + oznakaG, gostIgralci.get(0));
            }
        }

        TekmaSrecanja tekma = new TekmaSrecanja(srecanje, Math.max(1, p.path("order").asInt(1)),
                dvojice ? TipTekmeSrecanja.DVOJICE : TipTekmeSrecanja.POSAMICNA,
                oznaka, steviloNizov(strani));

        tekma.setIgralecDomaci(domaciIgralci.isEmpty() ? null : domaciIgralci.get(0));
        tekma.setIgralecGost(gostIgralci.isEmpty() ? null : gostIgralci.get(0));
        if (dvojice) {
            tekma.setIgralecDomaci2(domaciIgralci.size() > 1 ? domaciIgralci.get(1) : null);
            tekma.setIgralecGost2(gostIgralci.size() > 1 ? gostIgralci.get(1) : null);
        }

        int niziD = strani.get(0).path("sets_won").asInt();
        int niziG = strani.get(1).path("sets_won").asInt();
        tekma.setDobljeniNiziDomaci(niziD);
        tekma.setDobljeniNiziGost(niziG);

        if ("SCORED".equals(p.path("status").asText(""))) {
            tekma.setStatus(StatusTekmeSrecanja.KONCANA);
            tekma.setZmagovalecStran(niziD > niziG ? StranEkipe.DOMACI : StranEkipe.GOST);
            // Brez boja je tudi tekma, na kateri ena ekipa ni postavila igralca
            // (ekipa je prisla neposedena) - ne le izrecno oznacen walkover.
            // Locevanje je pomembno: BREZ_BOJA se ne obracuna v ELO.
            boolean brezBoja = strani.stream().anyMatch(u -> u.path("walkover").asBoolean(false))
                    || domaciIgralci.isEmpty() || gostIgralci.isEmpty();
            tekma.setIzidTip(brezBoja ? IzidTekme.BREZ_BOJA : IzidTekme.IGRANO);
        } else {
            // srecanje je bilo odloceno prej - te tekme se po pravilih ne igra
            tekma.setStatus(StatusTekmeSrecanja.NEODIGRANA);
        }

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

    /* Kader se zbira med branjem srecanj (kdor je nastopil, je v kadru) in se
       zapise sele na koncu lige - isti igralec nastopi v desetih kolih, tabela
       kader_ekipe pa ima UNIQUE (ekipa, igralec). */
    private void shraniKader(Map<Long, Ekipa> ekipe, Map<Long, Map<Long, Igralec>> kader) {
        Map<Long, Ekipa> poId = new HashMap<>();
        ekipe.values().forEach(e -> poId.put(e.getId(), e));

        for (Map.Entry<Long, Map<Long, Igralec>> vnos : kader.entrySet()) {
            Ekipa ekipa = poId.get(vnos.getKey());
            if (ekipa == null) {
                continue;
            }
            for (Igralec igralec : vnos.getValue().values()) {
                kaderRepozitorij.save(new KaderEkipe(ekipa, igralec, null));
                porocilo.prestej("igralcev v kadrih");
            }
        }
    }

    private void zabelezi(Map<Long, Map<Long, Igralec>> kader, Ekipa ekipa, List<Igralec> igralci) {
        Map<Long, Igralec> zbir = kader.computeIfAbsent(ekipa.getId(), k -> new LinkedHashMap<>());
        igralci.forEach(i -> zbir.putIfAbsent(i.getId(), i));
    }

    private List<JsonNode> urejeneStrani(JsonNode tekma) {
        List<JsonNode> strani = new ArrayList<>();
        tekma.path("participants").forEach(strani::add);
        strani.sort(Comparator.comparingInt(p -> p.path("order").asInt()));
        return strani;
    }

    /* Dvojice prepoznamo po tem, da ima stran dva igralca. */
    private boolean jeDvojice(List<JsonNode> strani) {
        return strani.get(0).path("participant_details").size() > 1;
    }

    private List<Igralec> igralci(JsonNode stran) {
        List<Igralec> najdeni = new ArrayList<>();
        for (JsonNode pd : stran.path("participant_details")) {
            Igralec i = sifranti.igralci().get(
                    ZbirnikSifrantov.kljuc(ZbirnikSifrantov.VIR_STUPA, pd.path("user_role_id").asLong()));
            if (i != null && najdeni.stream().noneMatch(x -> x.getId().equals(i.getId()))) {
                najdeni.add(i);
            }
        }
        return najdeni;
    }

    /* Mesto v postavi iz oznake igralca ("A", "B", "C" / "X", "Y", "Z").
       Pri dvojicah so oznake "DA1"/"DA2"/"DB1"/"DB2" in mesta ne povedo. */
    private String oznakaMesta(JsonNode stran) {
        for (JsonNode pd : stran.path("participant_details")) {
            String oznaka = UvozOblike.ocisti(pd.path("participant_label").asText(null));
            if (oznaka != null && oznaka.length() == 1) {
                return oznaka;
            }
        }
        return "?";
    }

    private int steviloNizov(List<JsonNode> strani) {
        int dolzina = strani.get(0).path("sets").size();
        if (dolzina <= 3) {
            return 3;
        }
        return dolzina <= 5 ? 5 : 7;
    }
}
