/* Zagon zgodovinskega uvoza NTZS v prazno bazo.

   Zgodovina je pri viru razdeljena na dvoje in uvozimo jo v ENEM zagonu:
    * stara stran <https://stara.ntzs.si> - sezone 2012/13 do 2023/24
      (razbrana s skripto uvoz-stara-ntzs/pretvori.mjs),
    * Stupa Events <https://ntzseventsott.stupaevents.com> - od 2024/25 naprej
      (posnetek uvoz-stupa/posnetek.ps1).

   Tekmovanja Stupe gredo skozi ISTO preslikavo kot sinhronizacija med sezono
   (UvozStupeStoritev.izvedi, samodejni nacin istovetnosti) - dogodek za
   dogodkom, vsak v svoji transakciji in z uskladitvijo z virom. Zgodovinski
   uvoz in sprotni uvoz tako ne moreta zapisati istega dogodka razlicno, dnevnik
   uvoza (uvoz_zagon) pa po izgradnji ze pozna vse uvozene dogodke.

   Zaganja se takole (profil "uvoz" - ob navadnem zagonu se ne sprozi):

     cd zaledje
     mvnw spring-boot:run -Dspring-boot.run.profiles=uvoz

   Vrstni red korakov ni poljuben:
    1. sifranti stare strani (klubi, igralci) - na njih se sklicujejo turnirji
       in lige stare strani; osebe iz Stupe nastajajo sproti v koraku 2 in se z
       igralci stare strani povezejo po licenci, datumu rojstva in spolu,
    2. tekmovanja obeh virov po datumu v enem koledarju - kot so si sledila,
    3. rating: popoln preracun (PreracunRatingaStoritev), ker je zaporedna
       kolicina in mora videti vse tekme v casovnem zaporedju. */
package si.turnirko.uvoz;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import si.turnirko.dto.PorociloUvozaDto;
import si.turnirko.modeli.UvozZagon;
import si.turnirko.modeli.VirTekmovanja;
import si.turnirko.repozitoriji.DogodekRepozitorij;
import si.turnirko.repozitoriji.EkipaRepozitorij;
import si.turnirko.repozitoriji.IgralecRepozitorij;
import si.turnirko.repozitoriji.KaderEkipeRepozitorij;
import si.turnirko.repozitoriji.KlubRepozitorij;
import si.turnirko.repozitoriji.LigaRepozitorij;
import si.turnirko.repozitoriji.NizRepozitorij;
import si.turnirko.repozitoriji.PostavaSrecanjaRepozitorij;
import si.turnirko.repozitoriji.PrijavaRepozitorij;
import si.turnirko.repozitoriji.SkupinaRepozitorij;
import si.turnirko.repozitoriji.SrecanjeRepozitorij;
import si.turnirko.repozitoriji.TekmaRepozitorij;
import si.turnirko.repozitoriji.TekmaSrecanjaRepozitorij;
import si.turnirko.repozitoriji.TurnirRepozitorij;
import si.turnirko.repozitoriji.UvozZagonRepozitorij;
import si.turnirko.repozitoriji.ZunanjaPovezavaRepozitorij;
import si.turnirko.storitve.PreracunRatingaStoritev;
import si.turnirko.storitve.RazvrstitevStoritev;
import si.turnirko.uvoz.stara.StaraArhiv;
import si.turnirko.uvoz.stara.StaraLigeUvoz;
import si.turnirko.uvoz.stara.StaraTurnirjiUvoz;
import si.turnirko.uvoz.stara.StaraZbirnik;
import si.turnirko.uvoz.stupa.IdentitetaStupe;
import si.turnirko.uvoz.stupa.PosnetekDogodka;
import si.turnirko.uvoz.stupa.UvozStupeStoritev;
import si.turnirko.uvoz.stupa.ZnaneOsebeStupe;

@Component
@Profile(UvozUkaz.PROFIL)
public class UvozUkaz implements ApplicationRunner {

    public static final String PROFIL = "uvoz";

    private static final Logger dnevnik = LoggerFactory.getLogger(UvozUkaz.class);

    /* Eno tekmovanje v skupnem koledarju obeh virov. */
    private record Tekmovanje(LocalDate zacetek, String opis, Runnable uvozi) {}

    private final KlubRepozitorij klubRepozitorij;
    private final IgralecRepozitorij igralecRepozitorij;
    private final TurnirRepozitorij turnirRepozitorij;
    private final DogodekRepozitorij dogodekRepozitorij;
    private final SkupinaRepozitorij skupinaRepozitorij;
    private final PrijavaRepozitorij prijavaRepozitorij;
    private final TekmaRepozitorij tekmaRepozitorij;
    private final NizRepozitorij nizRepozitorij;
    private final LigaRepozitorij ligaRepozitorij;
    private final EkipaRepozitorij ekipaRepozitorij;
    private final KaderEkipeRepozitorij kaderRepozitorij;
    private final SrecanjeRepozitorij srecanjeRepozitorij;
    private final PostavaSrecanjaRepozitorij postavaRepozitorij;
    private final TekmaSrecanjaRepozitorij tekmaSrecanjaRepozitorij;
    private final ZunanjaPovezavaRepozitorij povezave;
    private final UvozZagonRepozitorij zagoni;
    private final RazvrstitevStoritev razvrstitev;
    private final UvozStupeStoritev uvozStupe;
    private final PreracunRatingaStoritev preracun;
    private final TransactionTemplate transakcija;
    private final ObjectMapper json = new ObjectMapper();

    /* Privzetki so poti iz zaledje/ - uvoz se zaganja od tam. Obe mapi sta
       ZUNAJ repozitorija (posnetka sta velika in nista koda). */
    @Value("${turnirko.uvoz.mapa:../../uvoz-stupa/surovo}")
    private String mapaStupe;

    @Value("${turnirko.uvoz.stara-mapa:../../uvoz-stara-ntzs/pretvorjeno}")
    private String mapaStare;

    @Value("${turnirko.uvoz.porocilo:../../uvoz-porocilo.csv}")
    private String potPorocila;

    @Value("${turnirko.uvoz.zahtevaj-prazno-bazo:true}")
    private boolean zahtevajPraznoBazo;

    public UvozUkaz(KlubRepozitorij klubRepozitorij, IgralecRepozitorij igralecRepozitorij,
                    TurnirRepozitorij turnirRepozitorij, DogodekRepozitorij dogodekRepozitorij,
                    SkupinaRepozitorij skupinaRepozitorij, PrijavaRepozitorij prijavaRepozitorij,
                    TekmaRepozitorij tekmaRepozitorij, NizRepozitorij nizRepozitorij,
                    LigaRepozitorij ligaRepozitorij, EkipaRepozitorij ekipaRepozitorij,
                    KaderEkipeRepozitorij kaderRepozitorij, SrecanjeRepozitorij srecanjeRepozitorij,
                    PostavaSrecanjaRepozitorij postavaRepozitorij,
                    TekmaSrecanjaRepozitorij tekmaSrecanjaRepozitorij, ZunanjaPovezavaRepozitorij povezave,
                    UvozZagonRepozitorij zagoni, RazvrstitevStoritev razvrstitev, UvozStupeStoritev uvozStupe,
                    PreracunRatingaStoritev preracun, PlatformTransactionManager upravitelj) {
        this.klubRepozitorij = klubRepozitorij;
        this.igralecRepozitorij = igralecRepozitorij;
        this.turnirRepozitorij = turnirRepozitorij;
        this.dogodekRepozitorij = dogodekRepozitorij;
        this.skupinaRepozitorij = skupinaRepozitorij;
        this.prijavaRepozitorij = prijavaRepozitorij;
        this.tekmaRepozitorij = tekmaRepozitorij;
        this.nizRepozitorij = nizRepozitorij;
        this.ligaRepozitorij = ligaRepozitorij;
        this.ekipaRepozitorij = ekipaRepozitorij;
        this.kaderRepozitorij = kaderRepozitorij;
        this.srecanjeRepozitorij = srecanjeRepozitorij;
        this.postavaRepozitorij = postavaRepozitorij;
        this.tekmaSrecanjaRepozitorij = tekmaSrecanjaRepozitorij;
        this.povezave = povezave;
        this.zagoni = zagoni;
        this.razvrstitev = razvrstitev;
        this.uvozStupe = uvozStupe;
        this.preracun = preracun;
        this.transakcija = new TransactionTemplate(upravitelj);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (zahtevajPraznoBazo && (igralecRepozitorij.count() > 0 || turnirRepozitorij.count() > 0)) {
            dnevnik.error("Baza ni prazna ({} igralcev, {} turnirjev). Uvoz zahteva prazno bazo.",
                    igralecRepozitorij.count(), turnirRepozitorij.count());
            return;
        }
        StaraArhiv stara = Files.isDirectory(Path.of(mapaStare)) ? new StaraArhiv(Path.of(mapaStare)) : null;
        Path stupa = Files.isDirectory(Path.of(mapaStupe)) ? Path.of(mapaStupe) : null;
        if (stara == null && stupa == null) {
            dnevnik.error("Ni nobenega vira ({} , {}). Uvoz odpade.", mapaStare, mapaStupe);
            return;
        }
        UvozPorocilo porocilo = new UvozPorocilo();

        List<JsonNode> dogodkiStupe = stupa == null ? List.of() : izberiTekmovanja(stupa, porocilo);
        Map<Long, String> imenaSezon = stupa == null ? Map.of() : imenaSezon(stupa);
        // osebe, kot jih poznajo vsi dogodki skupaj (podatki pri viru so po prijavah)
        Map<Long, IdentitetaStupe.Oseba> znaneOsebe = stupa == null ? Map.of() : ZnaneOsebeStupe.zberi(
                dogodkiStupe.stream().map(d -> new ZnaneOsebeStupe.Posnetek(
                        stupa.resolve("dogodki").resolve(d.path("id").asText()),
                        UvozOblike.datum(d.path("event_start_date").asText(null)))).toList());
        dnevnik.info("Znanih oseb Stupe: {}.", znaneOsebe.size());

        // 1. sifranti stare strani (letnik, ki ga stara stran nima, dopolni Stupa po licenci)
        ZbirnikSifrantov zbirnik = new ZbirnikSifrantov();
        if (stara != null) {
            StaraZbirnik.dodaj(stara, zbirnik, porocilo);
        }
        Map<String, SifrantiUvoz.Dopolnilo> dopolnila = stupa == null ? Map.of() : dopolnilaPoLicenci(stupa, dogodkiStupe);
        SifrantiUvoz sifranti = new SifrantiUvoz(klubRepozitorij, igralecRepozitorij, povezave, porocilo,
                licenca -> Optional.ofNullable(dopolnila.get(licenca)));
        sifranti.uvozi(zbirnik);
        dnevnik.info("Sifranti stare strani: {} klubov, {} igralcev.", porocilo.stevec("klubov"), porocilo.stevec("igralcev"));

        // 2. tekmovanja obeh virov v enem koledarju, po datumu
        StaraTurnirjiUvoz turnirjiStare = new StaraTurnirjiUvoz(turnirRepozitorij, dogodekRepozitorij,
                skupinaRepozitorij, prijavaRepozitorij, tekmaRepozitorij, nizRepozitorij, povezave, razvrstitev,
                sifranti, porocilo);
        StaraLigeUvoz ligeStare = new StaraLigeUvoz(ligaRepozitorij, ekipaRepozitorij, kaderRepozitorij,
                srecanjeRepozitorij, postavaRepozitorij, tekmaSrecanjaRepozitorij, povezave, sifranti, porocilo);

        List<Tekmovanje> koledar = new ArrayList<>();
        if (stara != null) {
            for (String sezona : stara.sezone()) {
                for (JsonNode t : stara.turnirji(sezona)) {
                    koledar.add(new Tekmovanje(UvozOblike.datum(t.path("datumOd").asText(null)),
                            "stara/turnir " + t.path("id").asText() + " " + t.path("ime").asText(),
                            () -> turnirjiStare.uvozi(t)));
                }
                for (JsonNode l : stara.lige(sezona)) {
                    koledar.add(new Tekmovanje(UvozOblike.datum(StaraArhiv.zacetekLige(l)),
                            "stara/liga " + l.path("id").asText(), () -> ligeStare.uvozi(l)));
                }
            }
        }
        for (JsonNode d : dogodkiStupe) {
            koledar.add(new Tekmovanje(UvozOblike.datum(d.path("event_start_date").asText(null)),
                    "stupa/" + d.path("id").asLong() + " " + d.path("name").asText(),
                    () -> uvoziStupo(stupa, d, imenaSezon.get(d.path("season_id").asLong()), znaneOsebe, porocilo)));
        }
        koledar.sort(Comparator.comparing(Tekmovanje::zacetek, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(Tekmovanje::opis));
        dnevnik.info("Za uvoz izbranih {} tekmovanj.", koledar.size());

        // en pokvarjen zapis pri viru ne sme podreti celotnega uvoza
        for (Tekmovanje t : koledar) {
            try {
                t.uvozi().run();
                dnevnik.info("Uvozeno {} - {}", t.zacetek(), t.opis());
            } catch (RuntimeException e) {
                porocilo.opozori("tekmovanje se ni uvozilo", t.opis() + ": " + e);
                dnevnik.error("Tekmovanje {} se ni uvozilo", t.opis(), e);
            }
        }

        // 3. rating: popoln preracun v casovnem zaporedju vseh tekem
        dnevnik.info("Preracun ratinga ...");
        PreracunRatingaStoritev.Porocilo rating = preracun.preracunajOd(null);
        porocilo.prestej("obracunanih tekem (rating)", rating.obracunanihTekem());

        Path porociloDatoteka = Path.of(potPorocila);
        porocilo.zapisi(porociloDatoteka);
        dnevnik.info(porocilo.povzetek());
        dnevnik.info("Podrobno porocilo: {}", porociloDatoteka.toAbsolutePath());
    }

    /* En dogodek Stupe skozi preslikavo sinhronizacije, v svoji transakciji. */
    private void uvoziStupo(Path koren, JsonNode vrstica, String sezona, Map<Long, IdentitetaStupe.Oseba> znaneOsebe,
                            UvozPorocilo porocilo) {
        long id = vrstica.path("id").asLong();
        PosnetekDogodka p = PosnetekDogodka.beri(koren.resolve("dogodki").resolve(String.valueOf(id)),
                vrstica, sezona, LocalDate.now());
        UvozZagon zagon = new UvozZagon(VirTekmovanja.STUPA, String.valueOf(id),
                UvozOblike.prirezi(p.ime(), 200), p.zgostitev(), "zgodovinski uvoz", LocalDateTime.now());
        UvozStupeStoritev.Izvedba izvedba = transakcija.execute(
                s -> uvozStupe.izvedi(p, IdentitetaStupe.Nacin.SAMODEJNO, Map.of(), znaneOsebe));
        PorociloUvozaDto dto = izvedba.porocilo().vDto();

        dto.stevci().forEach(porocilo::prestej);
        String oznaka = "stupa/" + id;
        dto.napake().forEach(u -> u.primeri().forEach(pr -> porocilo.opozori("NAPAKA " + u.vrsta(), oznaka + ": " + pr)));
        dto.opozorila().forEach(u -> u.primeri().forEach(pr -> porocilo.opozori(u.vrsta(), oznaka + ": " + pr)));
        dto.preverbe().stream().filter(x -> !x.ujemanje()).forEach(x -> porocilo.opozori(
                (x.obvezna() ? "NEUJEMANJE " : "RAZLIKA ") + x.podrocje(),
                oznaka + ": " + x.opis() + (x.podrobnosti() == null ? "" : " -> " + x.podrobnosti())));

        Map<String, Object> povzetek = new HashMap<>();
        povzetek.put("stevci", dto.stevci());
        povzetek.put("napake", dto.napake().stream().map(u -> u.vrsta() + " (" + u.stevilo() + ")").toList());
        povzetek.put("preverbe", dto.preverbe().stream()
                .map(x -> (x.ujemanje() ? "OK " : x.obvezna() ? "NAPAKA " : "RAZLIKA ") + x.opis()).toList());
        String besedilo;
        try {
            besedilo = json.writeValueAsString(povzetek);
        } catch (IOException e) {
            besedilo = null;
        }
        // zapisano je v vsakem primeru; NAPAKA pomeni, da uskladitev ni uspela in je dogodek vredno uvoziti znova
        zagon.zakljuci(izvedba.porocilo().dovoljuje() ? UvozZagon.Izid.USPEH : UvozZagon.Izid.NAPAKA, besedilo);
        zagoni.save(zagon);
    }

    /* Objavljena tekmovanja sezon zveze, ki so ze zacela: brez testnih in
       osnutkov (brez sezone) ter brez dogodkov, ki se niso bili odigrani. */
    private List<JsonNode> izberiTekmovanja(Path koren, UvozPorocilo porocilo) {
        List<JsonNode> izbrana = new ArrayList<>();
        LocalDate danes = LocalDate.now();
        for (JsonNode d : beri(koren.resolve("dogodki.json")).path("data")) {
            LocalDate zacetek = UvozOblike.datum(d.path("event_start_date").asText(null));
            if (!d.path("published").asBoolean(false)) {
                porocilo.prestej("izpusceni dogodki Stupe (neobjavljeni)");
            } else if (d.path("season_id").isNull() || d.path("season_id").asLong(0) == 0) {
                porocilo.prestej("izpusceni dogodki Stupe (brez sezone)");
            } else if (UvozStupeStoritev.jeTestni(d.path("name").asText(""))) {
                porocilo.prestej("izpusceni dogodki Stupe (testni)");
            } else if (zacetek == null || zacetek.isAfter(danes)) {
                porocilo.prestej("izpusceni dogodki Stupe (se niso odigrani)");
            } else if (!Files.isDirectory(koren.resolve("dogodki").resolve(d.path("id").asText()))) {
                porocilo.opozori("dogodek Stupe brez posnetka", d.path("id").asText() + " " + d.path("name").asText());
            } else {
                izbrana.add(d);
            }
        }
        return izbrana;
    }

    private Map<Long, String> imenaSezon(Path koren) {
        Map<Long, String> r = new HashMap<>();
        for (JsonNode s : beri(koren.resolve("sezone.json")).path("seasons")) {
            r.put(s.path("id").asLong(), s.path("season_name").asText(null));
        }
        return r;
    }

    /* Datum rojstva in spol po licenci iz prijav Stupe - samo tam, kjer je
       licenca enolicna oseba (ena letnica in en spol). */
    private Map<String, SifrantiUvoz.Dopolnilo> dopolnilaPoLicenci(Path koren, List<JsonNode> dogodki) {
        Map<String, SifrantiUvoz.Dopolnilo> r = new HashMap<>();
        Set<String> dvoumne = new HashSet<>();
        for (JsonNode d : dogodki) {
            JsonNode udelezenci = beri(koren.resolve("dogodki").resolve(d.path("id").asText()).resolve("udelezenci.json"));
            for (JsonNode u : udelezenci.path("data").path("participants")) {
                for (JsonNode pd : u.path("event_participant_details")) {
                    IdentitetaStupe.Oseba o = IdentitetaStupe.oseba(pd);
                    if (o.licenca() == null || o.rojstvo() == null || o.spol() == null) {
                        continue;
                    }
                    SifrantiUvoz.Dopolnilo nov = new SifrantiUvoz.Dopolnilo(o.rojstvo(), o.spol());
                    SifrantiUvoz.Dopolnilo prej = r.putIfAbsent(o.licenca(), nov);
                    if (prej != null && (prej.rojstvo().getYear() != nov.rojstvo().getYear() || prej.spol() != nov.spol())) {
                        dvoumne.add(o.licenca());
                    }
                }
            }
        }
        dvoumne.forEach(r::remove);
        return r;
    }

    private JsonNode beri(Path pot) {
        try {
            String vsebina = Files.readString(pot);
            if (!vsebina.isEmpty() && vsebina.charAt(0) == '﻿') {
                vsebina = vsebina.substring(1);
            }
            return json.readTree(vsebina);
        } catch (IOException e) {
            return json.createObjectNode();
        }
    }
}
