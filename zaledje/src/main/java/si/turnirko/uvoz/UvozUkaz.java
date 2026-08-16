/* Zagon uvoza zgodovinskih podatkov NTZS.

   Zgodovina je pri viru razdeljena na dvoje in uvozimo jo v ENEM zagonu:
    * stara stran <https://stara.ntzs.si> - sezone 2012/13 do 2023/24
      (razbrana s skripto uvoz-stara-ntzs/pretvori.mjs),
    * Stupa Events <https://ntzseventsott.stupaevents.com> - od 2024/25 naprej
      (posnetek uvoz-stupa/posnetek.ps1).
   Vira se casovno ne prekrivata: zadnje tekmovanje stare strani je junija
   2024, prvo v Stupi 14. septembra 2024.

   Uvoz je enkratno opravilo in NE sme teci ob navadnem zagonu streznika, zato
   je ves paket uvoz vezan na profil "uvoz". Zaganja se takole:

     cd zaledje
     mvnw spring-boot:run -Dspring-boot.run.profiles=uvoz

   Uvoz se izvede v prazno bazo. Ce baza ni prazna, se ustavi - zdruzevanje z
   obstojecimi podatki bi ustvarilo dvojnike igralcev in turnirjev, ker vira in
   Turnirko nimajo skupnega kljuca. Za ponoven uvoz je torej treba datoteko
   baze pobrisati (Flyway jo ob zagonu postavi znova).

   Vrstni red korakov ni poljuben:
    1. sifranti OBEH virov naenkrat - vse ostalo se sklicuje nanje, ista oseba
       pa nastopa v obeh in mora dobiti en sam profil (glej SifrantiUvoz),
    2. tekmovanja po datumu - turnirji in lige v enem zaporedju, tako kot so si
       sledili v koledarju, in ne najprej vsi turnirji in nato vse lige,
    3. ELO cisto na koncu, ker je zaporedna kolicina (glej EloUvoz). */
package si.turnirko.uvoz;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

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
import si.turnirko.uvoz.stara.StaraArhiv;
import si.turnirko.uvoz.stara.StaraLigeUvoz;
import si.turnirko.uvoz.stara.StaraTurnirjiUvoz;
import si.turnirko.uvoz.stara.StaraZbirnik;

@Component
@Profile(UvozUkaz.PROFIL)
public class UvozUkaz implements ApplicationRunner {

    public static final String PROFIL = "uvoz";

    private static final Logger dnevnik = LoggerFactory.getLogger(UvozUkaz.class);

    /* Eno tekmovanje v skupnem koledarju obeh virov. Datum je dan zacetka
       (pri ligi dan prvega kola) in po njem se tekmovanja uvozijo. */
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
    private final EloUvoz eloUvoz;

    /* Privzetki so poti iz zaledje/ - uvoz se zaganja od tam. Obe mapi sta
       ZUNAJ repozitorija (posnetka sta velika in nista koda). */
    @Value("${turnirko.uvoz.mapa:../../uvoz-stupa/surovo}")
    private String mapaStupe;

    @Value("${turnirko.uvoz.stara-mapa:../../uvoz-stara-ntzs/pretvorjeno}")
    private String mapaStare;

    @Value("${turnirko.uvoz.porocilo:../../uvoz-porocilo.csv}")
    private String potPorocila;

    /* Varovalka: uvoz v neprazno bazo bi ustvaril dvojnike. Zavestno jo je
       mogoce izklopiti, ce kdo uvaza po delih in ve, kaj dela. */
    @Value("${turnirko.uvoz.zahtevaj-prazno-bazo:true}")
    private boolean zahtevajPraznoBazo;

    public UvozUkaz(KlubRepozitorij klubRepozitorij, IgralecRepozitorij igralecRepozitorij,
                    TurnirRepozitorij turnirRepozitorij, DogodekRepozitorij dogodekRepozitorij,
                    SkupinaRepozitorij skupinaRepozitorij, PrijavaRepozitorij prijavaRepozitorij,
                    TekmaRepozitorij tekmaRepozitorij, NizRepozitorij nizRepozitorij,
                    LigaRepozitorij ligaRepozitorij, EkipaRepozitorij ekipaRepozitorij,
                    KaderEkipeRepozitorij kaderRepozitorij, SrecanjeRepozitorij srecanjeRepozitorij,
                    PostavaSrecanjaRepozitorij postavaRepozitorij,
                    TekmaSrecanjaRepozitorij tekmaSrecanjaRepozitorij, EloUvoz eloUvoz) {
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
        this.eloUvoz = eloUvoz;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (zahtevajPraznoBazo && (igralecRepozitorij.count() > 0 || turnirRepozitorij.count() > 0)) {
            dnevnik.error("Baza ni prazna ({} igralcev, {} turnirjev). Uvoz zahteva prazno bazo - "
                            + "pobrisi podatki/turnirko.db in zazeni znova.",
                    igralecRepozitorij.count(), turnirRepozitorij.count());
            return;
        }

        StaraArhiv stara = Files.isDirectory(Path.of(mapaStare)) ? new StaraArhiv(Path.of(mapaStare)) : null;
        StupaArhiv stupa = Files.isDirectory(Path.of(mapaStupe)) ? new StupaArhiv(Path.of(mapaStupe)) : null;
        if (stara == null) {
            dnevnik.warn("Mape {} ni - stare strani NTZS ne uvazam.", mapaStare);
        }
        if (stupa == null) {
            dnevnik.warn("Mape {} ni - Stupa Events ne uvazam.", mapaStupe);
        }
        if (stara == null && stupa == null) {
            dnevnik.error("Ni nobenega vira. Uvoz odpade.");
            return;
        }

        UvozPorocilo porocilo = new UvozPorocilo();

        // 1. sifranti obeh virov naenkrat
        ZbirnikSifrantov zbirnik = new ZbirnikSifrantov();
        if (stara != null) {
            StaraZbirnik.dodaj(stara, zbirnik, porocilo);
        }
        List<JsonNode> tekmovanjaStupe = (stupa == null) ? List.of() : izberiTekmovanja(stupa, porocilo);
        for (JsonNode d : tekmovanjaStupe) {
            zbirnik.dodajDogodek(stupa, d, porocilo);
        }
        SifrantiUvoz sifranti = new SifrantiUvoz(klubRepozitorij, igralecRepozitorij, porocilo);
        sifranti.uvozi(zbirnik);
        dnevnik.info("Sifranti: {} klubov, {} igralcev.",
                porocilo.stevec("klubov"), porocilo.stevec("igralcev"));

        // 2. tekmovanja obeh virov v enem koledarju, po datumu
        TurnirjiUvoz turnirjiStupe = new TurnirjiUvoz(turnirRepozitorij, dogodekRepozitorij,
                skupinaRepozitorij, prijavaRepozitorij, tekmaRepozitorij, nizRepozitorij,
                sifranti, porocilo);
        LigeUvoz ligeStupe = new LigeUvoz(ligaRepozitorij, ekipaRepozitorij, kaderRepozitorij,
                srecanjeRepozitorij, postavaRepozitorij, tekmaSrecanjaRepozitorij,
                sifranti, porocilo);
        StaraTurnirjiUvoz turnirjiStare = new StaraTurnirjiUvoz(turnirRepozitorij, dogodekRepozitorij,
                skupinaRepozitorij, prijavaRepozitorij, tekmaRepozitorij, nizRepozitorij,
                sifranti, porocilo);
        StaraLigeUvoz ligeStare = new StaraLigeUvoz(ligaRepozitorij, ekipaRepozitorij, kaderRepozitorij,
                srecanjeRepozitorij, postavaRepozitorij, tekmaSrecanjaRepozitorij,
                sifranti, porocilo);

        List<Tekmovanje> koledar = new ArrayList<>();
        if (stara != null) {
            zberiStaro(stara, turnirjiStare, ligeStare, koledar);
        }
        if (stupa != null) {
            zberiStupo(stupa, tekmovanjaStupe, turnirjiStupe, ligeStupe, koledar);
        }
        koledar.sort(Comparator
                .comparing(Tekmovanje::zacetek, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(Tekmovanje::opis));
        dnevnik.info("Za uvoz izbranih {} tekmovanj.", koledar.size());

        // En pokvarjen zapis pri viru ne sme podreti celotnega uvoza - tekmovanje
        // preskocimo, zabelezimo in nadaljujemo; kaj je odpadlo, pove porocilo.
        for (Tekmovanje t : koledar) {
            try {
                t.uvozi().run();
                dnevnik.info("Uvozeno {} - {}", t.zacetek(), t.opis());
            } catch (RuntimeException e) {
                porocilo.opozori("tekmovanje se ni uvozilo", t.opis() + ": " + e);
                dnevnik.error("Tekmovanje {} se ni uvozilo: {}", t.opis(), e.toString());
            }
        }

        // 3. ELO cisto na koncu, v casovnem zaporedju vseh tekem skupaj
        List<EloUvoz.VrstaTekme> vrsta = new ArrayList<>(turnirjiStupe.uvozeneTekme());
        vrsta.addAll(ligeStupe.uvozeneTekme());
        vrsta.addAll(turnirjiStare.uvozeneTekme());
        vrsta.addAll(ligeStare.uvozeneTekme());
        List<EloUvoz.VrstaTekme> urejene = eloUvoz.uredi(vrsta);
        for (int od = 0; od < urejene.size(); od += eloUvoz.velikostPaketa()) {
            int doKam = Math.min(od + eloUvoz.velikostPaketa(), urejene.size());
            eloUvoz.obracunajPaket(urejene.subList(od, doKam), porocilo);
        }

        Path porociloDatoteka = Path.of(potPorocila);
        porocilo.zapisi(porociloDatoteka);
        dnevnik.info(porocilo.povzetek());
        dnevnik.info("Podrobno porocilo: {}", porociloDatoteka.toAbsolutePath());
    }

    private void zberiStaro(StaraArhiv arhiv, StaraTurnirjiUvoz turnirji, StaraLigeUvoz lige,
                            List<Tekmovanje> koledar) {
        for (String sezona : arhiv.sezone()) {
            for (JsonNode t : arhiv.turnirji(sezona)) {
                koledar.add(new Tekmovanje(UvozOblike.datum(t.path("datumOd").asText(null)),
                        "stara/turnir " + t.path("id").asText() + " " + t.path("ime").asText(),
                        () -> turnirji.uvozi(t)));
            }
            for (JsonNode l : arhiv.lige(sezona)) {
                koledar.add(new Tekmovanje(UvozOblike.datum(StaraArhiv.zacetekLige(l)),
                        "stara/liga " + l.path("id").asText(),
                        () -> lige.uvozi(l)));
            }
        }
    }

    private void zberiStupo(StupaArhiv arhiv, List<JsonNode> tekmovanja, TurnirjiUvoz turnirji,
                            LigeUvoz lige, List<Tekmovanje> koledar) {
        Map<Long, String> imenaSezon = new HashMap<>();
        for (JsonNode s : arhiv.sezone()) {
            imenaSezon.put(s.path("id").asLong(), s.path("season_name").asText(null));
        }
        for (JsonNode d : tekmovanja) {
            LocalDate zacetek = UvozOblike.datum(d.path("event_start_date").asText(null));
            String opis = "stupa/" + d.path("id").asLong() + " " + d.path("name").asText();
            if ("L".equals(d.path("event_type").asText())) {
                String sezona = imenaSezon.get(d.path("season_id").asLong());
                koledar.add(new Tekmovanje(zacetek, opis, () -> lige.uvozi(arhiv, d, sezona)));
            } else {
                koledar.add(new Tekmovanje(zacetek, opis, () -> turnirji.uvozi(arhiv, d)));
            }
        }
    }

    /* Izbor tekmovanj Stupe: samo objavljena in taka, ki pripadajo sezoni.
       Neobjavljeni dogodki v podatkih NTZS so testni ("Samo - TEST ...") in v
       zgodovino ne sodijo; dogodek brez sezone je osnutek. */
    private List<JsonNode> izberiTekmovanja(StupaArhiv arhiv, UvozPorocilo porocilo) {
        List<JsonNode> izbrana = new ArrayList<>();
        for (JsonNode d : arhiv.dogodki()) {
            if (!d.path("published").asBoolean(false)) {
                porocilo.prestej("izpusceni dogodki (neobjavljeni)");
                continue;
            }
            if (d.path("season_id").isNull() || d.path("season_id").asLong(0) == 0) {
                porocilo.prestej("izpusceni dogodki (brez sezone)");
                continue;
            }
            izbrana.add(d);
        }
        izbrana.sort(Comparator.comparing(d -> d.path("event_start_date").asText("")));
        return izbrana;
    }
}
