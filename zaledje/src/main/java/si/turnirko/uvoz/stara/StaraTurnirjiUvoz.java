/* Uvoz turnirjev s stare strani NTZS: turnir -> dogodki (discipline) ->
   prijave -> skupine -> tekme -> nizi.

   Preslikava, ki ni ocitna:

   * DISCIPLINA (pri viru "cat", npr. "kadeti posamezno") je Turnirkov DOGODEK.
     En turnir jih ima vec, tako kot ima Turnirkov turnir vec dogodkov.
     Dvojice in ekipne discipline pretvorba ze izpusti - Turnirkova prijava
     veze natanko enega igralca.

   * SISTEM TEKMOVANJA se ne prenese, ampak se PREBERE IZ STOPENJ. Vir pozna
     stopnje "Kvalifikacije" (skupine), "Top sistem" (krozno) in "Finalni del"
     (izlocilni); kombinacija skupinskega in izlocilnega dela je
     SKUPINE_IZLOCILNI, sam skupinski del z eno skupino KROZNI, z vec
     skupinami SKUPINE, sam izlocilni pa IZLOCILNI.

   * IZLOCILNA MREZA: povezav med tekmami vir ne poslje, poslje pa kolo in
     mesto v POLNI mrezi (prvo kolo tekmovanja s 36 igralci ima mesta 7-26,
     ker je ostalo prostih prehodov). Zato velja obicajno pravilo napredovanja:
     tekma (kolo r, mesto p) vodi v (kolo r+1, mesto ceil(p/2)).

   * TOCKE NIZOV so pri viru zapisane kot tocke POraZenca niza s predznakom
     ("3:1 (11,-9,8,5)"); pretvorba jih razvije v pravi par tock obeh igralcev,
     tu jih samo prepisemo. */
package si.turnirko.uvoz.stara;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

import si.turnirko.modeli.Disciplina;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.FazaTekme;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.IzidTekme;
import si.turnirko.modeli.Niz;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.SistemTekmovanja;
import si.turnirko.modeli.Skupina;
import si.turnirko.modeli.SpolKategorija;
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
import si.turnirko.uvoz.EloUvoz;
import si.turnirko.uvoz.SifrantiUvoz;
import si.turnirko.uvoz.UvozOblike;
import si.turnirko.uvoz.UvozPorocilo;
import si.turnirko.uvoz.ZbirnikSifrantov;

public class StaraTurnirjiUvoz {

    private final TurnirRepozitorij turnirRepozitorij;
    private final DogodekRepozitorij dogodekRepozitorij;
    private final SkupinaRepozitorij skupinaRepozitorij;
    private final PrijavaRepozitorij prijavaRepozitorij;
    private final TekmaRepozitorij tekmaRepozitorij;
    private final NizRepozitorij nizRepozitorij;
    private final SifrantiUvoz sifranti;
    private final UvozPorocilo porocilo;

    private final List<EloUvoz.VrstaTekme> uvozeneTekme = new ArrayList<>();

    public StaraTurnirjiUvoz(TurnirRepozitorij turnirRepozitorij, DogodekRepozitorij dogodekRepozitorij,
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

    public void uvozi(JsonNode vir) {
        LocalDate zacetek = UvozOblike.datum(vir.path("datumOd").asText(null));

        Turnir turnir = new Turnir();
        turnir.setIme(UvozOblike.prirezi(vir.path("ime").asText(), 80));
        turnir.setDatumZacetka(zacetek);
        turnir.setDatumKonca(UvozOblike.datum(vir.path("datumDo").asText(null)));
        // Vir pove ime kraja ("Cirkovce"), Turnirkov kraj pa je zapis iz
        // sifranta s postno stevilko, ki je vir nima. Da se prizorisce ne
        // izgubi, gre v polje dvorana - vmesnik izpise kraj in dvorano skupaj,
        // zato bralec vidi prav tisto, kar je pisalo pri viru.
        turnir.setDvorana(UvozOblike.prirezi(UvozOblike.ocisti(vir.path("kraj").asText(null)), 60));
        turnir.setStatus(StatusTekmovanja.ZAKLJUCEN);
        turnir.setOpombe("Uvoz s stara.ntzs.si (tekmovanje " + vir.path("id").asText() + ")");
        turnir = turnirRepozitorij.save(turnir);
        porocilo.prestej("turnirjev");

        for (JsonNode disciplina : vir.path("discipline")) {
            uvoziDisciplino(turnir, disciplina, vir.path("kategorija").asText(null), zacetek);
        }
    }

    private void uvoziDisciplino(Turnir turnir, JsonNode vir, String starostnaKategorija,
                                 LocalDate datum) {
        List<JsonNode> tekme = seznam(vir.path("tekme"));
        if (tekme.isEmpty()) {
            return;
        }

        Dogodek dogodek = ustvariDogodek(turnir, vir, tekme, starostnaKategorija);
        Map<String, Prijava> prijave = ustvariPrijave(dogodek, vir, tekme);
        Map<Integer, Skupina> skupine = ustvariSkupine(dogodek, tekme);
        ustvariTekme(dogodek, tekme, prijave, skupine, datum);
    }

    private Dogodek ustvariDogodek(Turnir turnir, JsonNode vir, List<JsonNode> tekme,
                                   String starostnaKategorija) {
        boolean skupinski = tekme.stream().anyMatch(t -> t.path("skupinska").asBoolean());
        boolean izlocilni = tekme.stream().anyMatch(t -> !t.path("skupinska").asBoolean());
        long stSkupin = tekme.stream()
                .filter(t -> t.path("skupinska").asBoolean())
                .map(t -> t.path("skupina").asInt(0))
                .distinct().count();

        SistemTekmovanja sistem;
        if (skupinski && izlocilni) {
            sistem = SistemTekmovanja.SKUPINE_IZLOCILNI;
        } else if (skupinski) {
            sistem = (stSkupin <= 1) ? SistemTekmovanja.KROZNI : SistemTekmovanja.SKUPINE;
        } else {
            sistem = SistemTekmovanja.IZLOCILNI;
        }

        Dogodek dogodek = new Dogodek();
        dogodek.setTurnir(turnir);
        dogodek.setIme(UvozOblike.prirezi(vir.path("oznaka").asText("disciplina"), 60));
        dogodek.setDisciplina(Disciplina.POSAMICNO);
        dogodek.setSpolKategorija("ZENSKI".equals(vir.path("spol").asText())
                ? SpolKategorija.ZENSKE : SpolKategorija.MOSKI);
        dogodek.setStarostnaKategorija(UvozOblike.prirezi(starostnaKategorija, 40));
        dogodek.setSistemTekmovanja(sistem);
        dogodek.setPrivzetoSteviloNizov(privzetoSteviloNizov(tekme));
        dogodek.setStatus(StatusTekmovanja.ZAKLJUCEN);
        if (sistem == SistemTekmovanja.SKUPINE && stSkupin > 0) {
            dogodek.setSteviloSkupin((int) stSkupin);
        }
        Dogodek shranjen = dogodekRepozitorij.save(dogodek);
        porocilo.prestej("dogodkov (disciplin)");
        return shranjen;
    }

    /* Prijave beremo s seznama prijav vira (tam sta klub in mesto v jakostnem
       vrstnem redu), dopolnimo pa jih z vsemi, ki so v tekmah, prijave pa
       nimajo - brez prijave tekme ni mogoce zapisati. */
    private Map<String, Prijava> ustvariPrijave(Dogodek dogodek, JsonNode vir, List<JsonNode> tekme) {
        Map<String, Prijava> prijave = new LinkedHashMap<>();

        for (JsonNode p : vir.path("prijave")) {
            String id = p.path("igralec").asText(null);
            Prijava prijava = ustvariPrijavo(dogodek, id, prijave);
            if (prijava == null) {
                continue;
            }
            if (p.path("rang").isInt() && p.path("rang").asInt() > 0) {
                prijava.setStNosilca(p.path("rang").asInt());
            }
            if (p.path("odjavljen").asBoolean(false)) {
                prijava.setStatus(Prijava.StatusPrijave.ODJAVLJEN);
            }
            prijave.put(id, prijavaRepozitorij.save(prijava));
            porocilo.prestej("prijav");
        }

        for (JsonNode t : tekme) {
            for (String polje : List.of("igralec1", "igralec2")) {
                String id = t.path(polje).asText(null);
                if (id == null || prijave.containsKey(id)) {
                    continue;
                }
                Prijava prijava = ustvariPrijavo(dogodek, id, prijave);
                if (prijava != null) {
                    prijave.put(id, prijavaRepozitorij.save(prijava));
                    porocilo.prestej("prijav");
                    porocilo.prestej("prijav, sestavljenih iz tekem (vir jih v prijavah nima)");
                }
            }
        }
        return prijave;
    }

    private Prijava ustvariPrijavo(Dogodek dogodek, String idVira, Map<String, Prijava> ze) {
        if (idVira == null || ze.containsKey(idVira)) {
            return null;
        }
        String kljuc = ZbirnikSifrantov.kljuc(ZbirnikSifrantov.VIR_STARA, idVira);
        Igralec igralec = sifranti.igralci().get(kljuc);
        if (igralec == null) {
            porocilo.opozori("prijava brez igralca v sifrantu", kljuc);
            return null;
        }
        // shema ima UNIQUE (dogodek, igralec): ista oseba se v isti disciplini
        // ne more pojaviti dvakrat, tudi ce ima vir zanjo dva zapisa
        if (ze.values().stream().anyMatch(p -> p.getIgralec().getId().equals(igralec.getId()))) {
            porocilo.opozori("podvojena prijava (izpuscena)",
                    igralec.polnoIme() + " v " + dogodek.getIme());
            return null;
        }
        Prijava prijava = new Prijava(dogodek, igralec);
        prijava.setStatus(Prijava.StatusPrijave.PRIJAVLJEN);
        prijava.setKlubObPrijavi(sifranti.klubIgralca(kljuc));
        return prijava;
    }

    private Map<Integer, Skupina> ustvariSkupine(Dogodek dogodek, List<JsonNode> tekme) {
        List<Integer> stevilke = tekme.stream()
                .filter(t -> t.path("skupinska").asBoolean())
                .map(t -> t.path("skupina").asInt(0))
                .filter(s -> s > 0)
                .distinct().sorted().toList();

        Map<Integer, Skupina> skupine = new HashMap<>();
        int zaporedna = 0;
        for (Integer stevilka : stevilke) {
            // oznaka je crka po vrsti (A, B, C ...); vir skupine samo osteva
            String oznaka = zaporedna < 26
                    ? String.valueOf((char) ('A' + zaporedna))
                    : "S" + (zaporedna + 1);
            skupine.put(stevilka, skupinaRepozitorij.save(new Skupina(dogodek, oznaka)));
            zaporedna++;
        }
        porocilo.prestej("skupin", stevilke.size());
        return skupine;
    }

    private void ustvariTekme(Dogodek dogodek, List<JsonNode> tekme, Map<String, Prijava> prijave,
                              Map<Integer, Skupina> skupine, LocalDate datum) {
        // Shema ima UNIQUE (dogodek, faza, kolo, pozicija). V skupinskem delu
        // vse skupine oStevilcijo tekme od 1 naprej, zato se mesta prekrivajo -
        // trke resimo s premikom mesta, kot pri uvozu iz Stupe.
        Set<String> zasedeno = new HashSet<>();
        Map<String, Tekma> poMestu = new HashMap<>();

        List<JsonNode> urejene = new ArrayList<>(tekme);
        urejene.sort(Comparator
                .comparingInt((JsonNode t) -> t.path("skupinska").asBoolean() ? 0 : 1)
                .thenComparingInt(t -> t.path("krog").asInt(1))
                .thenComparingInt(t -> t.path("skupina").asInt(0))
                .thenComparingInt(t -> t.path("pozicija").asInt(1)));

        for (JsonNode t : urejene) {
            boolean skupinska = t.path("skupinska").asBoolean();
            FazaTekme faza = skupinska ? FazaTekme.SKUPINA : FazaTekme.GLAVNI;
            int kolo = Math.max(1, t.path("krog").asInt(1));
            int mestoVMrezi = Math.max(1, t.path("pozicija").asInt(1));

            int pozicija = mestoVMrezi;
            while (!zasedeno.add(faza + "|" + kolo + "|" + pozicija)) {
                pozicija++;
            }

            Prijava prva = prijave.get(t.path("igralec1").asText(null));
            Prijava druga = prijave.get(t.path("igralec2").asText(null));
            if (prva == null || druga == null) {
                porocilo.opozori("tekma brez obeh prijav (izpuscena)",
                        dogodek.getIme() + " #" + t.path("st").asInt());
                continue;
            }

            int nizi1 = t.path("nizi1").asInt();
            int nizi2 = t.path("nizi2").asInt();
            if (nizi1 == nizi2) {
                // brez zmagovalca tekma v Turnirku ne obstane; take zapise
                // pusti vir za neodigrane pare in v zgodovino ne sodijo
                porocilo.opozori("tekma brez zmagovalca pri viru (izpuscena)",
                        dogodek.getIme() + " #" + t.path("st").asInt());
                continue;
            }

            Tekma tekma = new Tekma();
            tekma.setDogodek(dogodek);
            tekma.setFaza(faza);
            if (skupinska) {
                Skupina s = skupine.get(t.path("skupina").asInt(0));
                tekma.setIdSkupina(s == null ? null : s.getId());
            }
            tekma.setKolo(kolo);
            tekma.setPozicija(pozicija);
            tekma.setSteviloNizov(steviloNizov(nizi1 + nizi2));
            tekma.setPrijava1(prva);
            tekma.setPrijava2(druga);
            tekma.setDobljeniNizi1(nizi1);
            tekma.setDobljeniNizi2(nizi2);
            tekma.setStatus(StatusTekme.KONCANA);
            tekma.setIzidTip("BREZ_BOJA".equals(t.path("izid").asText()) ? IzidTekme.BREZ_BOJA : IzidTekme.IGRANO);
            tekma.setZmagovalec(nizi1 > nizi2 ? prva : druga);

            Tekma shranjena = tekmaRepozitorij.save(tekma);
            porocilo.prestej("tekem (turnirskih)");
            uvozeneTekme.add(EloUvoz.VrstaTekme.turnirska(
                    shranjena.getId(), datum, !skupinska, kolo, pozicija));

            shraniNize(shranjena, t);
            if (!skupinska) {
                poMestu.put(kolo + "|" + mestoVMrezi, shranjena);
            }
        }
        povezijMrezo(poMestu);
    }

    /* Poveze tekme izlocilne mreze: (kolo r, mesto p) -> (kolo r+1, ceil(p/2)).
       Liho mesto pride v prvo mesto naslednje tekme, sodo v drugo. Obe povezavi
       se postavita, preden se tekma shrani: vsak save() nad odklopljeno
       entiteto poveca verzijo v bazi, drugi save() iste tekme bi zato padel na
       optimisticnem zaklepanju. */
    private void povezijMrezo(Map<String, Tekma> poMestu) {
        for (Map.Entry<String, Tekma> vnos : poMestu.entrySet()) {
            String[] deli = vnos.getKey().split("\\|");
            int kolo = Integer.parseInt(deli[0]);
            int mesto = Integer.parseInt(deli[1]);

            Tekma izvor1 = poMestu.get((kolo - 1) + "|" + (mesto * 2 - 1));
            Tekma izvor2 = poMestu.get((kolo - 1) + "|" + (mesto * 2));
            if (izvor1 == null && izvor2 == null) {
                continue;
            }
            Tekma tekma = vnos.getValue();
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

    private void shraniNize(Tekma tekma, JsonNode t) {
        int zaporedna = 0;
        for (JsonNode niz : t.path("tocke")) {
            if (niz.size() < 2) {
                continue;
            }
            zaporedna++;
            nizRepozitorij.save(new Niz(tekma, zaporedna, niz.path(0).asInt(), niz.path(1).asInt()));
            porocilo.prestej("nizov");
        }
        int pricakovano = tekma.getDobljeniNizi1() + tekma.getDobljeniNizi2();
        if (zaporedna > 0 && zaporedna != pricakovano) {
            porocilo.opozori("stevilo nizov se ne ujema s povzetkom",
                    "tekma " + tekma.getId() + ": nizov " + zaporedna + ", povzetek " + pricakovano);
        }
    }

    /* "Najboljsi od N": Turnirko dovoli samo 3, 5 ali 7. */
    private int steviloNizov(int odigranihNizov) {
        if (odigranihNizov <= 3) {
            return 3;
        }
        return odigranihNizov <= 5 ? 5 : 7;
    }

    private int privzetoSteviloNizov(List<JsonNode> tekme) {
        Map<Integer, Integer> stetje = new HashMap<>();
        for (JsonNode t : tekme) {
            stetje.merge(steviloNizov(t.path("nizi1").asInt() + t.path("nizi2").asInt()), 1, Integer::sum);
        }
        return stetje.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse(5);
    }

    private static List<JsonNode> seznam(JsonNode vozlisce) {
        List<JsonNode> r = new ArrayList<>();
        if (vozlisce != null && vozlisce.isArray()) {
            vozlisce.forEach(r::add);
        }
        return r;
    }
}
