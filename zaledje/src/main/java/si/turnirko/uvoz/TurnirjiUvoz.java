/* Uvoz turnirjev: turnir -> dogodki (kategorije) -> prijave -> tekme -> nizi.

   Preslikava, ki ni ocitna:

   * DOGODEK je pri Stupi "event_category". En turnir ima vec kategorij
     (clani, clanice, dvojice...) - natanko tako, kot ima Turnirkov turnir
     vec dogodkov.

   * SISTEM TEKMOVANJA se ne prenese, ampak se PREBERE IZ STOPENJ. Stupa ima
     format_id 1 (skupine / krozni) in 2 (izlocilni); kombinacija obojega je
     SKUPINE_IZLOCILNI, sama skupina z eno skupino je KROZNI, z vec skupinami
     SKUPINE, sam izlocilni pa IZLOCILNI. Imena stopenj so prosto besedilo
     ("FINALE 5-8", "KVALI", "CONSULATION") in se nanje ni mogoce zanesti.

   * IZLOCILNA MREZA: Stupa poveznic med tekmami ne poslje, poslje pa kolo
     (round.order) in mesto v kolu (match.order), pri cemer je match.order
     mesto v POLNI mrezi - "Round of 64" ima lahko samo dve tekmi s stevilkama
     2 in 31, ker so ostalo prosti prehodi. Zato velja obicajno pravilo
     napredovanja: tekma (kolo r, mesto p) vodi v (kolo r+1, mesto ceil(p/2)),
     liho mesto na prvo, sodo na drugo. Preverjeno na drzavnem prvenstvu 2025.

   * TOCKE NIZOV so v Stupi polja stalne dolzine, dopolnjena z niclami
     (npr. [11,11,11,0,0] za zmago 3:0). Nize s skupno vsoto 0 zato izpustimo -
     sicer bi v Turnirko vpisali nize, ki jih ni bilo. */
package si.turnirko.uvoz;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.Disciplina;
import si.turnirko.modeli.FazaTekme;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.IzidTekme;
import si.turnirko.modeli.Klub;
import si.turnirko.modeli.Niz;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.SistemTekmovanja;
import si.turnirko.modeli.Skupina;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.StatusTekmovanja;
import si.turnirko.modeli.Tekma;
import si.turnirko.modeli.Turnir;
import si.turnirko.modeli.VlogaIzvora;
import si.turnirko.repozitoriji.DogodekRepozitorij;
import si.turnirko.repozitoriji.NizRepozitorij;
import si.turnirko.repozitoriji.PrijavaRepozitorij;
import si.turnirko.repozitoriji.SkupinaRepozitorij;
import si.turnirko.repozitoriji.TekmaRepozitorij;
import si.turnirko.repozitoriji.TurnirRepozitorij;

public class TurnirjiUvoz {

    private final TurnirRepozitorij turnirRepozitorij;
    private final DogodekRepozitorij dogodekRepozitorij;
    private final SkupinaRepozitorij skupinaRepozitorij;
    private final PrijavaRepozitorij prijavaRepozitorij;
    private final TekmaRepozitorij tekmaRepozitorij;
    private final NizRepozitorij nizRepozitorij;
    private final SifrantiUvoz sifranti;
    private final UvozPorocilo porocilo;

    /* Kazalci na uvozene tekme za poznejsi obracun ELO. Hranimo identifikatorje
       in ne entitet: te so po shranjevanju odklopljene, obracun pa bere leno
       nalozene povezave in jih zato naloZi znova. */
    private final List<EloUvoz.VrstaTekme> uvozeneTekme = new ArrayList<>();

    public TurnirjiUvoz(TurnirRepozitorij turnirRepozitorij, DogodekRepozitorij dogodekRepozitorij,
                        SkupinaRepozitorij skupinaRepozitorij, PrijavaRepozitorij prijavaRepozitorij,
                        TekmaRepozitorij tekmaRepozitorij, NizRepozitorij nizRepozitorij,
                        SifrantiUvoz sifranti, UvozPorocilo porocilo) {
        this.turnirRepozitorij = turnirRepozitorij;
        this.dogodekRepozitorij = dogodekRepozitorij;
        this.skupinaRepozitorij = skupinaRepozitorij;
        this.prijavaRepozitorij = prijavaRepozitorij;
        this.tekmaRepozitorij = tekmaRepozitorij;
        this.nizRepozitorij = nizRepozitorij;
        this.sifranti = sifranti;
        this.porocilo = porocilo;
    }

    public List<EloUvoz.VrstaTekme> uvozeneTekme() {
        return uvozeneTekme;
    }

    public void uvozi(StupaArhiv arhiv, JsonNode dogodekStupe) {
        long idStupe = dogodekStupe.path("id").asLong();
        LocalDate datumTurnirja = UvozOblike.datum(dogodekStupe.path("event_start_date").asText(null));

        Turnir turnir = new Turnir();
        turnir.setIme(UvozOblike.prirezi(dogodekStupe.path("name").asText(), 80));
        turnir.setDatumZacetka(datumTurnirja);
        turnir.setDatumKonca(UvozOblike.datum(dogodekStupe.path("event_end_date").asText(null)));
        turnir.setStatus(StatusTekmovanja.ZAKLJUCEN);
        turnir.setOpombe("Uvoz iz Stupa Events (id " + idStupe + ")");
        turnir = turnirRepozitorij.save(turnir);
        porocilo.prestej("turnirjev");

        // Kljuci za povezovanje: tekma pozna skupino, skupina stopnjo,
        // stopnja pa kategorijo. Brez teh dveh preslikav tekme ni mogoce
        // pripisati pravemu dogodku.
        Map<Long, JsonNode> stopnjePoId = new HashMap<>();
        for (JsonNode s : arhiv.stopnje(idStupe)) {
            stopnjePoId.put(s.path("id").asLong(), s);
        }
        Map<Long, Long> stopnjaSkupine = new HashMap<>();
        for (JsonNode g : arhiv.skupine(idStupe)) {
            stopnjaSkupine.put(g.path("id").asLong(), g.path("stage_id").asLong());
        }
        Kontekst kontekst = new Kontekst(stopnjePoId, stopnjaSkupine, datumTurnirja);

        List<JsonNode> vseTekme = arhiv.tekme(idStupe);
        List<JsonNode> udelezenci = arhiv.udelezenci(idStupe);

        boolean vsajEnaKategorija = false;
        for (JsonNode kategorija : arhiv.kategorije(idStupe)) {
            if (kategorija.path("participant_type_id").asInt() != 1) {
                // dvojice (3) in ekipe (2/4): Turnirkova shema jih na turnirju
                // se ne zna zapisati - prijava veze natanko enega igralca
                porocilo.prestej("izpuscene kategorije (dvojice/ekipe)");
                continue;
            }
            vsajEnaKategorija = true;
            uvoziKategorijo(turnir, kategorija, kontekst, vseTekme, udelezenci);
        }
        if (!vsajEnaKategorija) {
            porocilo.opozori("turnir brez posamicne kategorije",
                    dogodekStupe.path("name").asText() + " (id " + idStupe + ")");
        }
    }

    private void uvoziKategorijo(Turnir turnir, JsonNode kategorija, Kontekst kontekst,
                                 List<JsonNode> vseTekme, List<JsonNode> udelezenci) {
        long idKategorije = kategorija.path("event_category_id").asLong(kategorija.path("id").asLong());

        List<JsonNode> stopnje = kontekst.stopnjePoId().values().stream()
                .filter(s -> s.path("event_category_id").asLong() == idKategorije)
                .sorted(Comparator.comparingInt(s -> s.path("order").asInt()))
                .toList();

        List<JsonNode> tekme = vseTekme.stream()
                .filter(t -> {
                    JsonNode s = kontekst.stopnjaTekme(t);
                    return s != null && s.path("event_category_id").asLong() == idKategorije;
                })
                .toList();

        List<JsonNode> prijavljeni = udelezenci.stream()
                .filter(u -> u.path("participant_type_id").asInt() == 1)
                // Stupa izbrisane prijave obdrzi v seznamu (mehak izbris) in ob
                // ponovni prijavi doda nov zapis - brez tega filtra bi isti
                // igralec v kategoriji nastopil dvakrat
                .filter(u -> !u.path("is_deleted").asBoolean(false))
                .filter(u -> u.path("category_id").asLong() == kategorija.path("category_id").asLong())
                .toList();

        if (tekme.isEmpty() && prijavljeni.isEmpty()) {
            return;
        }

        Dogodek dogodek = ustvariDogodek(turnir, kategorija, stopnje, tekme, kontekst);
        Map<Long, Prijava> prijave = ustvariPrijave(dogodek, prijavljeni);
        Map<Long, Skupina> skupine = ustvariSkupine(dogodek, tekme, kontekst);
        ustvariTekme(dogodek, stopnje, tekme, kontekst, prijave, skupine);
    }

    private Dogodek ustvariDogodek(Turnir turnir, JsonNode kategorija, List<JsonNode> stopnje,
                                   List<JsonNode> tekme, Kontekst kontekst) {
        String ime = UvozOblike.ocisti(kategorija.path("category_display_label").asText(null));
        if (ime == null) {
            ime = UvozOblike.ocisti(kategorija.path("category_description").asText(null));
        }
        if (ime == null || ime.length() < 3) {
            ime = "Kategorija " + kategorija.path("id").asLong();
        }

        long skupinskih = stopnje.stream().filter(s -> s.path("format_id").asInt() == 1).count();
        long izlocilnih = stopnje.stream().filter(s -> s.path("format_id").asInt() == 2).count();
        long stSkupin = tekme.stream()
                .filter(t -> jeSkupinska(t, kontekst))
                .map(t -> t.path("group_id").asLong())
                .distinct().count();

        SistemTekmovanja sistem;
        if (skupinskih > 0 && izlocilnih > 0) {
            sistem = SistemTekmovanja.SKUPINE_IZLOCILNI;
        } else if (skupinskih > 0) {
            sistem = (stSkupin <= 1) ? SistemTekmovanja.KROZNI : SistemTekmovanja.SKUPINE;
        } else {
            sistem = SistemTekmovanja.IZLOCILNI;
        }

        Dogodek dogodek = new Dogodek();
        dogodek.setTurnir(turnir);
        dogodek.setIme(UvozOblike.prirezi(ime, 60));
        dogodek.setDisciplina(Disciplina.POSAMICNO);
        dogodek.setSpolKategorija(UvozOblike.spolKategorija(kategorija.path("gender_id").asInt()));
        dogodek.setStarostnaKategorija(
                UvozOblike.prirezi(kategorija.path("category_description").asText(null), 40));
        dogodek.setSistemTekmovanja(sistem);
        dogodek.setPrivzetoSteviloNizov(privzetoSteviloNizov(tekme));
        dogodek.setStatus(StatusTekmovanja.ZAKLJUCEN);
        Dogodek shranjen = dogodekRepozitorij.save(dogodek);
        porocilo.prestej("dogodkov (kategorij)");
        return shranjen;
    }

    private Map<Long, Prijava> ustvariPrijave(Dogodek dogodek, List<JsonNode> prijavljeni) {
        Map<Long, Prijava> prijave = new HashMap<>();
        for (JsonNode u : prijavljeni) {
            String kljucIgralca = ZbirnikSifrantov.kljuc(ZbirnikSifrantov.VIR_STUPA, u.path("ref_id").asLong());
            Igralec igralec = sifranti.igralci().get(kljucIgralca);
            if (igralec == null) {
                porocilo.opozori("prijava brez igralca v sifrantu", u.path("participant_name").asText());
                continue;
            }
            // shema ima UNIQUE (dogodek, igralec) - isti igralec se v isti
            // kategoriji ne more pojaviti dvakrat
            if (prijave.values().stream().anyMatch(p -> p.getIgralec().getId().equals(igralec.getId()))) {
                porocilo.opozori("podvojena prijava (izpuscena)",
                        u.path("participant_name").asText() + " v " + dogodek.getIme());
                continue;
            }

            Prijava prijava = new Prijava(dogodek, igralec);
            if (u.path("seed").isInt() && u.path("seed").asInt() > 0) {
                prijava.setStNosilca(u.path("seed").asInt());
            }
            prijava.setStatus(u.path("is_withdrawn").asBoolean(false)
                    ? Prijava.StatusPrijave.ODJAVLJEN : Prijava.StatusPrijave.PRIJAVLJEN);
            // klub beremo iz sifranta, ne prek igralec.getKlub() - entiteta je
            // po shranjevanju odklopljena in leno polje bi poCilo
            prijava.setKlubObPrijavi(sifranti.klubIgralca(kljucIgralca));
            prijave.put(u.path("id").asLong(), prijavaRepozitorij.save(prijava));
            porocilo.prestej("prijav");
        }
        return prijave;
    }

    private Map<Long, Skupina> ustvariSkupine(Dogodek dogodek, List<JsonNode> tekme, Kontekst kontekst) {
        Map<Long, Skupina> skupine = new HashMap<>();
        List<Long> idjiSkupin = tekme.stream()
                .filter(t -> jeSkupinska(t, kontekst))
                .map(t -> t.path("group_id").asLong())
                .distinct().sorted().toList();

        int zaporedna = 0;
        for (Long idSkupine : idjiSkupin) {
            // oznaka je crka po vrsti (A, B, C...); imena skupin v Stupi so
            // "Group 1", "Group 2" in za prikaz v Turnirku niso primerna
            String oznaka = zaporedna < 26
                    ? String.valueOf((char) ('A' + zaporedna))
                    : "S" + (zaporedna + 1);
            skupine.put(idSkupine, skupinaRepozitorij.save(new Skupina(dogodek, oznaka)));
            zaporedna++;
        }
        if (dogodek.getSistemTekmovanja() == SistemTekmovanja.SKUPINE && !idjiSkupin.isEmpty()) {
            dogodek.setSteviloSkupin(idjiSkupin.size());
            // ta dogodek se pozneje ne shranjuje vec; sicer bi bilo treba
            // prevzeti vrnjeni predmet zaradi optimisticnega zaklepanja
            dogodekRepozitorij.save(dogodek);
        }
        porocilo.prestej("skupin", idjiSkupin.size());
        return skupine;
    }

    private void ustvariTekme(Dogodek dogodek, List<JsonNode> stopnje, List<JsonNode> tekme,
                              Kontekst kontekst, Map<Long, Prijava> prijave,
                              Map<Long, Skupina> skupine) {

        // Med izlocilnimi stopnjami je glavna tista z najvec tekmami; ostale
        // (tolazilne, za mesta) gredo v fazo TOLAZILNI, da si ne zasedejo
        // istih mest v mrezi.
        long idGlavneStopnje = stopnje.stream()
                .filter(s -> s.path("format_id").asInt() == 2)
                .max(Comparator.comparingLong(s -> steviloTekemStopnje(s, tekme, kontekst)))
                .map(s -> s.path("id").asLong())
                .orElse(-1L);

        // Zasedena mesta v mrezi: shema ima UNIQUE (dogodek, faza, kolo, pozicija),
        // vec stopenj iste faze pa lahko trci. Trke resimo s premikom pozicije.
        Map<String, Boolean> zasedeno = new HashMap<>();
        // (stopnja, kolo, mesto) -> tekma; potrebno za povezave napredovanja
        Map<String, Tekma> poMestu = new HashMap<>();

        for (JsonNode t : tekme.stream()
                .sorted(Comparator.<JsonNode>comparingInt(t -> t.path("round").path("order").asInt())
                        .thenComparingInt(t -> t.path("order").asInt()))
                .toList()) {

            Long idStopnje = kontekst.idStopnjeTekme(t);
            boolean skupinska = jeSkupinska(t, kontekst);
            FazaTekme faza = skupinska ? FazaTekme.SKUPINA
                    : (idStopnje != null && idStopnje == idGlavneStopnje ? FazaTekme.GLAVNI : FazaTekme.TOLAZILNI);

            int kolo = Math.max(1, t.path("round").path("order").asInt(1));
            int pozicija = Math.max(1, t.path("order").asInt(1));
            while (zasedeno.containsKey(faza + "|" + kolo + "|" + pozicija)) {
                pozicija++;
            }
            zasedeno.put(faza + "|" + kolo + "|" + pozicija, true);

            List<JsonNode> strani = new ArrayList<>();
            t.path("participants").forEach(strani::add);
            strani.sort(Comparator.comparingInt(p -> p.path("order").asInt()));

            Tekma tekma = new Tekma();
            tekma.setDogodek(dogodek);
            tekma.setFaza(faza);
            if (skupinska) {
                Skupina s = skupine.get(t.path("group_id").asLong());
                tekma.setIdSkupina(s == null ? null : s.getId());
            }
            tekma.setKolo(kolo);
            tekma.setPozicija(pozicija);
            tekma.setSteviloNizov(steviloNizov(strani));

            Prijava prva = strani.size() > 0 ? prijave.get(strani.get(0).path("participant_id").asLong()) : null;
            Prijava druga = strani.size() > 1 ? prijave.get(strani.get(1).path("participant_id").asLong()) : null;
            tekma.setPrijava1(prva);
            tekma.setPrijava2(druga);

            if (strani.size() > 1) {
                tekma.setDobljeniNizi1(strani.get(0).path("sets_won").asInt());
                tekma.setDobljeniNizi2(strani.get(1).path("sets_won").asInt());
            }

            String status = t.path("status").asText("");
            boolean brezBoja = strani.stream().anyMatch(p -> p.path("walkover").asBoolean(false));
            if ("SCORED".equals(status)) {
                tekma.setStatus(StatusTekme.KONCANA);
                tekma.setIzidTip(brezBoja ? IzidTekme.BREZ_BOJA : IzidTekme.IGRANO);
                long idZmagovalca = t.path("winner").asLong(0);
                Prijava zmagovalec = prijave.get(idZmagovalca);
                tekma.setZmagovalec(zmagovalec);
                if (zmagovalec == null && idZmagovalca != 0) {
                    porocilo.opozori("tekma brez znanega zmagovalca", "stupa tekma " + t.path("id").asLong());
                }
            } else if ("IN_PROGRESS".equals(status)) {
                tekma.setStatus(StatusTekme.V_IGRI);
            } else {
                tekma.setStatus(prva != null && druga != null ? StatusTekme.PRIPRAVLJENA : StatusTekme.CAKA);
            }

            Tekma shranjena = tekmaRepozitorij.save(tekma);
            porocilo.prestej("tekem (turnirskih)");
            uvozeneTekme.add(EloUvoz.VrstaTekme.turnirska(
                    shranjena.getId(), kontekst.datumTurnirja(), !skupinska, kolo, pozicija));

            if (strani.size() > 1) {
                shraniNize(shranjena, strani.get(0), strani.get(1));
            }
            if (!skupinska && idStopnje != null) {
                poMestu.put(idStopnje + "|" + kolo + "|" + t.path("order").asInt(1), shranjena);
            }
        }

        povezijMrezo(poMestu);
    }

    /* Poveze tekme izlocilne mreze: (kolo r, mesto p) -> (kolo r+1, mesto ceil(p/2)).
       Liho mesto pride v prvo mesto naslednje tekme, sodo v drugo.

       Obe povezavi se postavita, preden se tekma shrani. Vsak save() nad
       odklopljeno entiteto namrec poveca verzijo v bazi, lokalni predmet pa
       ostane na stari - drugi save() iste tekme bi zato padel na optimisticnem
       zaklepanju (StaleObjectStateException). */
    private void povezijMrezo(Map<String, Tekma> poMestu) {
        for (Map.Entry<String, Tekma> vnos : poMestu.entrySet()) {
            String[] deli = vnos.getKey().split("\\|");
            long idStopnje = Long.parseLong(deli[0]);
            int kolo = Integer.parseInt(deli[1]);
            int mesto = Integer.parseInt(deli[2]);

            Tekma tekma = vnos.getValue();
            Tekma izvor1 = poMestu.get(idStopnje + "|" + (kolo - 1) + "|" + (mesto * 2 - 1));
            Tekma izvor2 = poMestu.get(idStopnje + "|" + (kolo - 1) + "|" + (mesto * 2));
            if (izvor1 == null && izvor2 == null) {
                continue;
            }
            if (izvor1 != null) {
                tekma.setIdIzvorTekma1(izvor1.getId());
                tekma.setVlogaIzvora1(VlogaIzvora.ZMAGOVALEC);
                porocilo.prestej("povezav v mrezi");
            }
            if (izvor2 != null) {
                tekma.setIdIzvorTekma2(izvor2.getId());
                tekma.setVlogaIzvora2(VlogaIzvora.ZMAGOVALEC);
                porocilo.prestej("povezav v mrezi");
            }
            tekmaRepozitorij.save(tekma);
        }
    }

    /* Nizi iz polj tock. Polja so stalne dolzine in dopolnjena z niclami,
       zato niz s skupno vsoto 0 pomeni "tega niza ni bilo".

       Negativne tocke so napaka vnosa pri viru (v podatkih NTZS se pojavi
       npr. [-7, 7, 10, 0, 0]). Takih ne popravljamo z ugibanjem - tekma se
       uvozi z dobljenimi nizi, tocke po nizih pa izpustimo in zabelezimo. */
    private void shraniNize(Tekma tekma, JsonNode prva, JsonNode druga) {
        JsonNode tocke1 = prva.path("points");
        JsonNode tocke2 = druga.path("points");
        int najvec = Math.min(tocke1.size(), tocke2.size());

        for (int i = 0; i < najvec; i++) {
            if (tocke1.path(i).asInt() < 0 || tocke2.path(i).asInt() < 0) {
                porocilo.opozori("negativne tocke niza pri viru (nizi izpusceni)",
                        "tekma " + tekma.getId() + ": " + tocke1 + " / " + tocke2);
                return;
            }
        }

        int zaporedna = 0;
        for (int i = 0; i < najvec; i++) {
            int t1 = tocke1.path(i).asInt();
            int t2 = tocke2.path(i).asInt();
            if (t1 + t2 == 0) {
                continue;
            }
            zaporedna++;
            nizRepozitorij.save(new Niz(tekma, zaporedna, t1, t2));
            porocilo.prestej("nizov");
        }
        int pricakovano = tekma.getDobljeniNizi1() + tekma.getDobljeniNizi2();
        if (zaporedna > 0 && zaporedna != pricakovano) {
            porocilo.opozori("stevilo nizov se ne ujema s povzetkom",
                    "tekma " + tekma.getId() + ": nizov " + zaporedna + ", povzetek " + pricakovano);
        }
    }

    /* Ali tekma spada v skupinski del. Odloca format stopnje, do katere pridemo
       prek skupine (tekma -> group_id -> stage_id -> format_id): 1 = skupine,
       2 = izlocilni. Imena stopenj in skupin so prosto besedilo in za to niso
       uporabna. */
    private boolean jeSkupinska(JsonNode tekma, Kontekst kontekst) {
        JsonNode stopnja = kontekst.stopnjaTekme(tekma);
        return stopnja != null && stopnja.path("format_id").asInt() == 1;
    }

    /* Preslikave znotraj enega dogodka Stupe: tekma pozna le skupino, dogodek
       pa je dolocen sele prek stopnje. Nosi tudi datum turnirja, ker ga
       potrebuje casovna vrsta za obracun ELO. */
    private record Kontekst(Map<Long, JsonNode> stopnjePoId, Map<Long, Long> stopnjaSkupine,
                            LocalDate datumTurnirja) {

        Long idStopnjeTekme(JsonNode tekma) {
            return stopnjaSkupine.get(tekma.path("group_id").asLong());
        }

        JsonNode stopnjaTekme(JsonNode tekma) {
            Long id = idStopnjeTekme(tekma);
            return id == null ? null : stopnjePoId.get(id);
        }
    }

    private long steviloTekemStopnje(JsonNode stopnja, List<JsonNode> tekme, Kontekst kontekst) {
        long id = stopnja.path("id").asLong();
        return tekme.stream()
                .filter(t -> {
                    Long s = kontekst.idStopnjeTekme(t);
                    return s != null && s == id;
                })
                .count();
    }

    /* "Najboljsi od N": dolzina polja nizov pove format tekme. Turnirko dovoli
       samo 3, 5 ali 7 - redke tekme z drugacnimi polji zaokrozimo navzgor. */
    private int steviloNizov(List<JsonNode> strani) {
        int dolzina = strani.isEmpty() ? 5 : strani.get(0).path("sets").size();
        if (dolzina <= 3) {
            return 3;
        }
        if (dolzina <= 5) {
            return 5;
        }
        if (dolzina > 7) {
            porocilo.opozori("nenavadna dolzina polja nizov", "dolzina " + dolzina + " -> 7");
        }
        return 7;
    }

    private int privzetoSteviloNizov(List<JsonNode> tekme) {
        Map<Integer, Integer> stetje = new HashMap<>();
        for (JsonNode t : tekme) {
            for (JsonNode p : t.path("participants")) {
                stetje.merge(p.path("sets").size(), 1, Integer::sum);
                break;
            }
        }
        int najpogostejsa = stetje.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse(5);
        if (najpogostejsa <= 3) {
            return 3;
        }
        return najpogostejsa <= 5 ? 5 : 7;
    }
}
