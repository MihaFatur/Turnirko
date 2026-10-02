/* Popravek uvozene zgodovine NA MESTU (oktober 2026).

   Revizija uvoza je nasla napake, ki jih ne popravi noben sprotni uvoz:
   pretvorba stare strani je izpustila pokal, koncnice in kvalifikacije SNTL,
   dvojice turnirjev, srecanja brez borbe in tocke nizov lig, prenesene izide
   ekipnega DP pa zapisala dvakrat; Stupa ima pri vec dogodkih napacne datume;
   dvojcka Adam sta bila povezana navzkriz; trije igralci so bili v registru
   dvakrat.

   Zakaj na mestu in ne nova izgradnja: v bazi so racuni, narocnine (Stripe),
   spremljane lige ter lastna tekmovanja (Savinja liga, Kompole), ki jih noben
   vir ne prinese znova; nanje kazejo id-ji igralcev, lig in turnirjev. Zato
   igralci, klubi, turnirji in lige obdrzijo id-je, na novo se zapise le
   vsebina pod njimi.

   Koraki (vsak je ponovljiv - drugi zagon na popravljeni bazi ne spremeni
   nicesar, razen svezega posnetka Stupe):
    1. zdruzitve: podvojeni igralci, ki jih je potrdil lastnik (30. 9. 2026),
    2. dvojcka: povezavi oseb Stupe dvojckov Adam se zamenjata,
    3. stara: vsa tekmovanja stare strani iz popravljene pretvorbe,
    4. stupa: izbrani dogodki Stupe iz svezega posnetka (popravki datumov v
       PopravkiStupe, dvojcka) - ista preslikava kot na strani /uvoz,
    5. rating: popoln preracun.

   Zaganja se nad KOPIJO baze (nikoli nad delujoco aplikacijo):

     cd zaledje
     mvnw spring-boot:run -Dspring-boot.run.profiles=popravek
       -Dspring-boot.run.arguments="--spring.datasource.url=jdbc:sqlite:<kopija.db>"

   Ob koncu se aplikacija ustavi sama. */
package si.turnirko.uvoz;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import si.turnirko.dto.PorociloUvozaDto;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Liga;
import si.turnirko.modeli.Turnir;
import si.turnirko.modeli.UvozZagon;
import si.turnirko.modeli.VirTekmovanja;
import si.turnirko.modeli.ZunanjaPovezava;
import si.turnirko.repozitoriji.DogodekRepozitorij;
import si.turnirko.repozitoriji.EkipaRepozitorij;
import si.turnirko.repozitoriji.IgralecRepozitorij;
import si.turnirko.repozitoriji.KaderEkipeRepozitorij;
import si.turnirko.repozitoriji.KlubRepozitorij;
import si.turnirko.repozitoriji.LigaRepozitorij;
import si.turnirko.repozitoriji.NizRepozitorij;
import si.turnirko.repozitoriji.NizSrecanjaRepozitorij;
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
import si.turnirko.uvoz.stara.CiscenjeStare;
import si.turnirko.uvoz.stara.StaraArhiv;
import si.turnirko.uvoz.stara.StaraLigeUvoz;
import si.turnirko.uvoz.stara.StaraTurnirjiUvoz;
import si.turnirko.uvoz.stara.StaraZbirnik;
import si.turnirko.uvoz.stupa.IdentitetaStupe;
import si.turnirko.uvoz.stupa.PosnetekDogodka;
import si.turnirko.uvoz.stupa.UvozStupeStoritev;

@Component
@Profile(PopravekZgodovineUkaz.PROFIL)
public class PopravekZgodovineUkaz implements ApplicationRunner {

    public static final String PROFIL = "popravek";

    private static final Logger dnevnik = LoggerFactory.getLogger(PopravekZgodovineUkaz.class);

    /* Igralec, ki je v registru dvakrat: izvor se zlije v cilj in izgine.
       Priimka sta varovalka - na bazi, kjer id-ji pomenijo kaj drugega (nova
       izgradnja), se zdruzitev ne izvede. */
    record Zdruzitev(long izvor, String priimekIzvora, long cilj, String priimekCilja, String razlog) {}

    /* Potrdil lastnik 30. 9. 2026. Cilj je zapis, ki ostane: pri Fotivcu
       tisti iz Stupe (pravi datum rojstva in tekoca licenca), pri ostalih
       uvozeni zapis z zgodovino (rocni vnos je imel izmisljen datum). */
    static final List<Zdruzitev> ZDRUZITVE = List.of(
            new Zdruzitev(267, "Fotivec", 1577, "Fotivec",
                    "Damir Fotivec: stara stran 2013-2015 in Stupa od 2025 (nova licenca)"),
            new Zdruzitev(1684, "Grenčer", 1438, "Gerenčer",
                    "Robert Grenčer (Kompole, tipkarska napaka) je Robert Gerenčer"),
            new Zdruzitev(1706, "Zore", 436, "Zore",
                    "Lojze Zore (Savinja liga) je Alojzij Zore"));

    /* Dvojcka Adam: Stupa je ob prvem uvozenem dogodku zapisala zamenjani
       licenci, uvoz pa je osebi povezal po licenci. Ime osebe pri Stupi
       (urid 17876 "Adam Tilen", 17877 "Adam Domen") je merilo. */
    record Dvojcek(String uridStupe, String idStare, String ime) {}

    /* Osebi, ki ju vir vodi BREZ datuma rojstva (v nobenem dogodku), zato ju
       uvoz ni mogel zapisati in sta njuni tekmi stali kot brez boja. Lastnik je
       1. 10. 2026 odlocil, da se zapiseta z datumom 1. 1. 1900 - isti dogovor
       kot pri Davidu Molnarju: datum ni znan, starostni pas zato ni pravi.
       Kljuc je dogodek Stupe, nato id osebe pri Stupi (user_role_id).
       Odlocitev velja samo za osebo, ki se ni povezana - drugi zagon bi sicer
       ustvaril se enega igralca (odlocitev ima prednost pred povezavo). */
    static final Map<Long, Map<Long, IdentitetaStupe.Odlocitev>> ODLOCITVE = Map.of(
            107L, Map.of(20346L, new IdentitetaStupe.Odlocitev(0, "Miha", "Košir", java.time.LocalDate.of(1900, 1, 1), null)),
            188L, Map.of(20384L, new IdentitetaStupe.Odlocitev(0, "Erazem", "Metljak", java.time.LocalDate.of(1900, 1, 1), null)));

    static final List<Dvojcek> DVOJCKA = List.of(
            new Dvojcek("17876", "4677", "Tilen"),
            new Dvojcek("17877", "4676", "Domen"));

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
    private final NizSrecanjaRepozitorij nizSrecanjaRepozitorij;
    private final ZunanjaPovezavaRepozitorij povezave;
    private final UvozZagonRepozitorij zagoni;
    private final RazvrstitevStoritev razvrstitev;
    private final UvozStupeStoritev uvozStupe;
    private final PreracunRatingaStoritev preracun;
    private final EntityManager em;
    private final TransactionTemplate transakcija;
    private final ConfigurableApplicationContext kontekst;
    private final ObjectMapper json = new ObjectMapper();

    @Value("${turnirko.popravek.stara-mapa:../../uvoz-stara-ntzs/pretvorjeno-20261001}")
    private String mapaStare;

    /* Dogodki Stupe za ponovni uvoz: popravljeni datumi (61, 138, 160-163,
       175, 177; konec dvodnevnih DP 69, 70, 217), casi in kola tekem lig 2024/25
       (169, 157, 168, 167, 174 - uradni koledar NTZS; 100 samo kola, case ima
       vir), dvojcka Adam (130, 166, 192,
       201, 218), uradna mesta 2026/27 (245, 246) in osebi brez datuma rojstva
       pri viru (107, 188 - glej ODLOCITVE). */
    @Value("${turnirko.popravek.stupa:61,138,160,161,162,163,169,157,168,167,174,100,175,177,69,70,217,130,166,192,201,218,245,246,107,188}")
    private List<Long> dogodkiStupe;

    @Value("${turnirko.popravek.koraki:zdruzitve,dvojcka,stara,stupa,rating}")
    private Set<String> koraki;

    @Value("${turnirko.popravek.porocilo:../../popravek-porocilo.csv}")
    private String potPorocila;

    @Value("${turnirko.popravek.ustavi:true}")
    private boolean ustaviObKoncu;

    public PopravekZgodovineUkaz(KlubRepozitorij klubRepozitorij, IgralecRepozitorij igralecRepozitorij,
                                 TurnirRepozitorij turnirRepozitorij, DogodekRepozitorij dogodekRepozitorij,
                                 SkupinaRepozitorij skupinaRepozitorij, PrijavaRepozitorij prijavaRepozitorij,
                                 TekmaRepozitorij tekmaRepozitorij, NizRepozitorij nizRepozitorij,
                                 LigaRepozitorij ligaRepozitorij, EkipaRepozitorij ekipaRepozitorij,
                                 KaderEkipeRepozitorij kaderRepozitorij, SrecanjeRepozitorij srecanjeRepozitorij,
                                 PostavaSrecanjaRepozitorij postavaRepozitorij,
                                 TekmaSrecanjaRepozitorij tekmaSrecanjaRepozitorij,
                                 NizSrecanjaRepozitorij nizSrecanjaRepozitorij, ZunanjaPovezavaRepozitorij povezave,
                                 UvozZagonRepozitorij zagoni, RazvrstitevStoritev razvrstitev,
                                 UvozStupeStoritev uvozStupe, PreracunRatingaStoritev preracun, EntityManager em,
                                 PlatformTransactionManager upravitelj, ConfigurableApplicationContext kontekst) {
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
        this.nizSrecanjaRepozitorij = nizSrecanjaRepozitorij;
        this.povezave = povezave;
        this.zagoni = zagoni;
        this.razvrstitev = razvrstitev;
        this.uvozStupe = uvozStupe;
        this.preracun = preracun;
        this.em = em;
        this.transakcija = new TransactionTemplate(upravitelj);
        this.kontekst = kontekst;
    }

    @Override
    public void run(ApplicationArguments args) {
        UvozPorocilo porocilo = new UvozPorocilo();
        try {
            izvedi(porocilo);
        } finally {
            Path datoteka = Path.of(potPorocila);
            porocilo.zapisi(datoteka);
            dnevnik.info(porocilo.povzetek());
            dnevnik.info("Podrobno porocilo: {}", datoteka.toAbsolutePath());
        }
        if (ustaviObKoncu) {
            System.exit(SpringApplication.exit(kontekst, () -> 0));
        }
    }

    private void izvedi(UvozPorocilo porocilo) {
        dnevnik.info("Popravek zgodovine, koraki {}", koraki);
        if (koraki.contains("zdruzitve")) {
            for (Zdruzitev z : ZDRUZITVE) {
                transakcija.executeWithoutResult(s -> zdruzi(z, porocilo));
            }
        }
        if (koraki.contains("dvojcka")) {
            transakcija.executeWithoutResult(s -> popraviDvojcka(porocilo));
        }
        if (koraki.contains("stara")) {
            popraviStaro(porocilo);
        }
        if (koraki.contains("stupa")) {
            for (Long id : dogodkiStupe) {
                uvoziStupo(id, porocilo);
            }
        }
        if (koraki.contains("rating")) {
            dnevnik.info("Preracun ratinga od zacetka ...");
            PreracunRatingaStoritev.Porocilo rating = preracun.preracunajOd(null);
            porocilo.prestej("obracunanih tekem (rating)", rating.obracunanihTekem());
        }
    }

    // ---------------------------------------------------------------------
    // 1. Zdruzitve igralcev
    // ---------------------------------------------------------------------

    /* Vse, kar kaze na izvor, preusmeri na cilj; izvor izbrise. Kadar bi
       preusmeritev trcila (oba v istem dogodku, postavi, kadru), se zdruzitev
       NE izvede - to ni napaka zapisa, ampak znak, da gre morda za dve osebi. */
    private void zdruzi(Zdruzitev z, UvozPorocilo porocilo) {
        Optional<Igralec> izvor = igralecRepozitorij.findById(z.izvor());
        Optional<Igralec> cilj = igralecRepozitorij.findById(z.cilj());
        String oznaka = z.izvor() + " -> " + z.cilj() + " (" + z.razlog() + ")";
        if (izvor.isEmpty()) {
            porocilo.prestej("zdruzitev ze izvedenih");
            return;
        }
        if (cilj.isEmpty() || !z.priimekIzvora().equals(izvor.get().getPriimek())
                || !z.priimekCilja().equals(cilj.get().getPriimek())) {
            porocilo.opozori("zdruzitev izpuscena (id-ja ne ustrezata imenoma)", oznaka);
            return;
        }
        long a = z.izvor(), b = z.cilj();
        Map<String, String> trki = new LinkedHashMap<>();
        trki.put("oba prijavljena v istem dogodku",
                "SELECT count(*) FROM prijava p JOIN prijava q ON q.id_dogodek = p.id_dogodek"
                        + " WHERE ?1 IN (p.id_igralec, p.id_igralec_2) AND ?2 IN (q.id_igralec, q.id_igralec_2)");
        trki.put("oba v isti postavi srecanja",
                "SELECT count(*) FROM postava_srecanja p JOIN postava_srecanja q ON q.id_srecanje = p.id_srecanje"
                        + " WHERE p.id_igralec = ?1 AND q.id_igralec = ?2");
        trki.put("oba v isti tekmi",
                "SELECT count(*) FROM tekma_srecanja WHERE ?1 IN (id_igralec_domaci, id_igralec_domaci2,"
                        + " id_igralec_gost, id_igralec_gost2) AND ?2 IN (id_igralec_domaci, id_igralec_domaci2,"
                        + " id_igralec_gost, id_igralec_gost2)");
        trki.put("oba imata racun",
                "SELECT count(*) FROM uporabnik u JOIN uporabnik v ON 1 = 1 WHERE u.id_igralec = ?1 AND v.id_igralec = ?2");
        for (Map.Entry<String, String> t : trki.entrySet()) {
            if (stej(t.getValue(), a, b) > 0) {
                porocilo.opozori("zdruzitev izpuscena (" + t.getKey() + ")", oznaka);
                return;
            }
        }
        // isti kader: izvorova vrstica odpade (cilj je ze v kadru)
        posodobi("DELETE FROM kader_ekipe WHERE id_igralec = ?1 AND id_ekipa IN"
                + " (SELECT id_ekipa FROM kader_ekipe WHERE id_igralec = ?2)", a, b);
        posodobi("UPDATE kader_ekipe SET id_igralec = ?2 WHERE id_igralec = ?1", a, b);
        posodobi("UPDATE prijava SET id_igralec = ?2 WHERE id_igralec = ?1", a, b);
        posodobi("UPDATE prijava SET id_igralec_2 = ?2 WHERE id_igralec_2 = ?1", a, b);
        posodobi("UPDATE postava_srecanja SET id_igralec = ?2 WHERE id_igralec = ?1", a, b);
        for (String stolpec : List.of("id_igralec_domaci", "id_igralec_domaci2", "id_igralec_gost", "id_igralec_gost2")) {
            posodobi("UPDATE tekma_srecanja SET " + stolpec + " = ?2 WHERE " + stolpec + " = ?1", a, b);
        }
        posodobi("UPDATE uporabnik SET id_igralec = ?2 WHERE id_igralec = ?1", a, b);
        posodobi("UPDATE zunanja_povezava SET id_lokalni = ?2 WHERE vrsta = 'IGRALEC' AND id_lokalni = ?1", a, b);
        // rating izvora je izpeljanka napacne delitve: preracun ga sestavi znova
        // pod ciljem; rocna postavitev izvora bi se izgubila, zato se presteje
        int postavitev = stej("SELECT count(*) FROM rating_zgodovina WHERE id_igralec = ?1 AND id_tekma IS NULL"
                + " AND id_tekma_srecanja IS NULL AND (razlog IS NULL OR razlog <> 'NEAKTIVNOST')", a);
        if (postavitev > 0) {
            porocilo.opozori("zdruzitev zavrze rocno postavitev ratinga izvora", oznaka);
        }
        posodobi("DELETE FROM rating_zgodovina WHERE id_igralec = ?1", a);
        posodobi("DELETE FROM rating_stanje WHERE id_igralec = ?1", a);
        posodobi("DELETE FROM igralec WHERE id = ?1", a);
        em.clear();
        porocilo.prestej("zdruzenih igralcev");
        porocilo.opozori("zdruzena igralca", oznaka);
    }

    // ---------------------------------------------------------------------
    // 2. Dvojcka Adam
    // ---------------------------------------------------------------------

    private void popraviDvojcka(UvozPorocilo porocilo) {
        for (Dvojcek d : DVOJCKA) {
            Optional<ZunanjaPovezava> stara = povezave.findByVirAndVrstaAndZunanjiId(
                    VirTekmovanja.STARA_NTZS, ZunanjaPovezava.Vrsta.IGRALEC, d.idStare());
            Optional<Igralec> igralec = stara.flatMap(p -> igralecRepozitorij.findById(p.getIdLokalni()));
            if (igralec.isEmpty() || !d.ime().equals(igralec.get().getIme())) {
                porocilo.opozori("dvojcka: igralec stare strani ni, kot je pricakovan", d.toString());
                return;
            }
            long id = igralec.get().getId();
            int spremenjenih = posodobi("UPDATE zunanja_povezava SET id_lokalni = ?2 WHERE vir = 'STUPA'"
                    + " AND vrsta = 'IGRALEC' AND zunanji_id = ?1 AND id_lokalni <> ?2", d.uridStupe(), id);
            porocilo.prestej(spremenjenih > 0 ? "preusmerjenih povezav dvojckov" : "povezav dvojckov ze pravilnih");
        }
        em.clear();
    }

    // ---------------------------------------------------------------------
    // 3. Stara stran na mestu
    // ---------------------------------------------------------------------

    private void popraviStaro(UvozPorocilo porocilo) {
        if (!Files.isDirectory(Path.of(mapaStare))) {
            porocilo.opozori("NAPAKA ni pretvorbe stare strani", mapaStare);
            return;
        }
        StaraArhiv stara = new StaraArhiv(Path.of(mapaStare));
        ZbirnikSifrantov zbirnik = new ZbirnikSifrantov();
        StaraZbirnik.dodaj(stara, zbirnik, porocilo);
        SifrantiUvoz sifranti = new SifrantiUvoz(klubRepozitorij, igralecRepozitorij, povezave, porocilo,
                licenca -> Optional.empty());
        sifranti.naloziObstojece(zbirnik);
        dnevnik.info("Sifranti stare strani nalozeni: {} igralcev, {} klubov.",
                sifranti.igralci().size(), sifranti.klubi().size());

        StaraTurnirjiUvoz turnirji = new StaraTurnirjiUvoz(turnirRepozitorij, dogodekRepozitorij, skupinaRepozitorij,
                prijavaRepozitorij, tekmaRepozitorij, nizRepozitorij, povezave, razvrstitev, sifranti, porocilo);
        StaraLigeUvoz lige = new StaraLigeUvoz(ligaRepozitorij, ekipaRepozitorij, kaderRepozitorij,
                srecanjeRepozitorij, postavaRepozitorij, tekmaSrecanjaRepozitorij, nizSrecanjaRepozitorij, povezave,
                sifranti, porocilo);

        Set<String> obdelani = new java.util.HashSet<>();
        for (String sezona : stara.sezone()) {
            for (JsonNode t : stara.turnirji(sezona)) {
                String id = t.path("id").asText();
                obdelani.add("TURNIR:" + id);
                Optional<Long> obstojeci = lokalni(ZunanjaPovezava.Vrsta.TURNIR, id);
                zapisi("stara/turnir " + id, porocilo, () -> transakcija.executeWithoutResult(s -> {
                    if (obstojeci.isEmpty()) {
                        turnirji.uvozi(t, null);
                        porocilo.prestej("novih turnirjev stare strani");
                        return;
                    }
                    em.flush();
                    CiscenjeStare.turnir(em, obstojeci.get());
                    em.clear();
                    Turnir turnir = turnirRepozitorij.findById(obstojeci.get()).orElseThrow();
                    turnirji.uvozi(t, turnir);
                }));
            }
            for (JsonNode l : stara.lige(sezona)) {
                String id = l.path("id").asText();
                obdelani.add("LIGA:" + id);
                Optional<Long> obstojeca = lokalni(ZunanjaPovezava.Vrsta.LIGA, id);
                zapisi("stara/liga " + id, porocilo, () -> transakcija.executeWithoutResult(s -> {
                    if (obstojeca.isEmpty()) {
                        if (lige.uvozi(l, null) != null) {
                            porocilo.prestej("novih lig stare strani");
                        }
                        return;
                    }
                    em.flush();
                    CiscenjeStare.liga(em, obstojeca.get());
                    em.clear();
                    Liga liga = ligaRepozitorij.findById(obstojeca.get()).orElseThrow();
                    if (lige.uvozi(l, liga) == null) {
                        porocilo.opozori("liga stare strani brez kol (vsebina pobrisana, liga ostala)", id);
                    }
                }));
            }
            dnevnik.info("Stara stran, sezona {}: zapisano.", sezona);
        }
        // tekmovanje v bazi, ki ga popravljena pretvorba ne pozna vec, ostane, kot je
        for (ZunanjaPovezava.Vrsta vrsta : List.of(ZunanjaPovezava.Vrsta.TURNIR, ZunanjaPovezava.Vrsta.LIGA)) {
            for (ZunanjaPovezava p : povezave.findByVirAndVrsta(VirTekmovanja.STARA_NTZS, vrsta)) {
                if (!obdelani.contains(vrsta + ":" + p.getZunanjiId())) {
                    porocilo.opozori("tekmovanje stare strani ni v pretvorbi (ostalo nespremenjeno)",
                            vrsta + " " + p.getZunanjiId() + " -> " + p.getIdLokalni());
                }
            }
        }
    }

    // ---------------------------------------------------------------------
    // 4. Stupa iz svezega posnetka
    // ---------------------------------------------------------------------

    /* Ista pot kot uvoz na strani /uvoz (strogi nacin, potrdi se samo
       uvoz, ki ga uskladitev dovoli), le da se rating preracuna enkrat na
       koncu in ne po vsakem dogodku. */
    private void uvoziStupo(long id, UvozPorocilo porocilo) {
        String oznaka = "stupa/" + id;
        PosnetekDogodka p;
        try {
            p = uvozStupe.posnemiSvez(id);
        } catch (RuntimeException e) {
            porocilo.opozori("NAPAKA posnetek Stupe", oznaka + ": " + e.getMessage());
            return;
        }
        UvozZagon zagon = new UvozZagon(VirTekmovanja.STUPA, String.valueOf(id), UvozOblike.prirezi(p.ime(), 200),
                p.zgostitev(), "popravek zgodovine", LocalDateTime.now());
        UvozStupeStoritev.Izvedba izvedba;
        try {
            izvedba = transakcija.execute(s -> {
                UvozStupeStoritev.Izvedba x = uvozStupe.izvedi(p, IdentitetaStupe.Nacin.STROGO, odlocitve(id));
                if (!x.porocilo().dovoljuje()) {
                    s.setRollbackOnly();
                }
                return x;
            });
        } catch (RuntimeException e) {
            dnevnik.error("Dogodek Stupe {} se ni uvozil", id, e);
            porocilo.opozori("NAPAKA uvoz Stupe", oznaka + ": " + e);
            zagon.zakljuci(UvozZagon.Izid.NAPAKA, null);
            zagoni.save(zagon);
            return;
        }
        PorociloUvozaDto dto = izvedba.porocilo().vDto();
        boolean uspeh = izvedba.porocilo().dovoljuje();
        porocilo.prestej(uspeh ? "dogodkov Stupe uvozenih znova" : "dogodkov Stupe ZAVRNJENIH (ostali, kot so bili)");
        dto.napake().forEach(u -> u.primeri().forEach(pr -> porocilo.opozori("NAPAKA " + u.vrsta(), oznaka + ": " + pr)));
        dto.odlocitve().forEach(o -> porocilo.opozori("odlocitev o osebi (dogodek zavrnjen)", oznaka + ": " + o));
        dto.preverbe().stream().filter(x -> !x.ujemanje()).forEach(x -> porocilo.opozori(
                (x.obvezna() ? "NEUJEMANJE " : "RAZLIKA ") + x.podrocje(),
                oznaka + ": " + x.opis() + (x.podrobnosti() == null ? "" : " -> " + x.podrobnosti())));

        Map<String, Object> povzetek = new LinkedHashMap<>();
        povzetek.put("stevci", dto.stevci());
        povzetek.put("napake", dto.napake().stream().map(u -> u.vrsta() + " (" + u.stevilo() + ")").toList());
        povzetek.put("preverbe", dto.preverbe().stream()
                .map(PorociloUvozaDto.Preverba::vDnevnik).toList());
        String besedilo;
        try {
            besedilo = json.writeValueAsString(povzetek);
        } catch (IOException e) {
            besedilo = null;
        }
        zagon.zakljuci(uspeh ? UvozZagon.Izid.USPEH : UvozZagon.Izid.ZAVRNJENO, besedilo);
        zagoni.save(zagon);
        dnevnik.info("Stupa {} ({}): {}", id, p.ime(), uspeh ? "uvozeno znova" : "ZAVRNJENO");
    }

    // ---------------------------------------------------------------------
    // Pomozno
    // ---------------------------------------------------------------------

    /* Odlocitve za dogodek, brez oseb, ki so ze povezane z igralcem. */
    private Map<Long, IdentitetaStupe.Odlocitev> odlocitve(long idDogodka) {
        Map<Long, IdentitetaStupe.Odlocitev> r = new LinkedHashMap<>();
        ODLOCITVE.getOrDefault(idDogodka, Map.of()).forEach((idOsebe, o) -> {
            if (povezave.findByVirAndVrstaAndZunanjiId(VirTekmovanja.STUPA, ZunanjaPovezava.Vrsta.IGRALEC,
                    String.valueOf(idOsebe)).isEmpty()) {
                r.put(idOsebe, o);
            }
        });
        return r;
    }

    /* En pokvarjen zapis ne sme ustaviti popravka: tekmovanje, ki se ne
       zapise, ostane v transakciji razveljavljeno - torej tako, kot je bilo. */
    private void zapisi(String opis, UvozPorocilo porocilo, Runnable delo) {
        try {
            delo.run();
        } catch (RuntimeException e) {
            porocilo.opozori("NAPAKA tekmovanje ni popravljeno (ostalo, kot je bilo)", opis + ": " + e);
            dnevnik.error("Tekmovanje {} ni popravljeno", opis, e);
        }
    }

    private Optional<Long> lokalni(ZunanjaPovezava.Vrsta vrsta, String zunanji) {
        return povezave.findByVirAndVrstaAndZunanjiId(VirTekmovanja.STARA_NTZS, vrsta, zunanji)
                .map(ZunanjaPovezava::getIdLokalni);
    }

    private int stej(String sql, Object... parametri) {
        return ((Number) poizvedba(sql, parametri).getSingleResult()).intValue();
    }

    private int posodobi(String sql, Object... parametri) {
        return poizvedba(sql, parametri).executeUpdate();
    }

    private jakarta.persistence.Query poizvedba(String sql, Object... parametri) {
        jakarta.persistence.Query q = em.createNativeQuery(sql);
        for (int i = 0; i < parametri.length; i++) {
            q.setParameter(i + 1, parametri[i]);
        }
        return q;
    }
}
