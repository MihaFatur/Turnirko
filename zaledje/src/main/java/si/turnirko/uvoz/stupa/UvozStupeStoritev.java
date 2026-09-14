/* Sinhronizacija s Stupo: admin uvozi odigran turnir ali ligo NTZS rocno,
   hitro in z dokazom, da je zapisano isto kot pri viru.

   Potek:
   1. PREDOGLED - posnetek dogodka (mapa JSON), preslikava in uskladitev v
      transakciji, ki se RAZVELJAVI. Admin vidi stevila, opozorila, nove
      igralce, odlocitve o istovetnosti in preverbe uskladitve.
   2. UVOZ - ista preslikava nad ISTIM posnetkom (oznaka iz predogleda) z
      odlocitvami admina. Ce so preverbe v redu, se transakcija potrdi in
      rating se preracuna od dneva tekmovanja; sicer se razveljavi (ZAVRNJENO).
   Vsak uvoz se zapise v dnevnik (uvoz_zagon) z zgostitvijo posnetka.

   Ponovni uvoz istega dogodka je varen: tekmovanje, dogodki, ekipe lige in
   srecanja lige obdrzijo id, vsebina pod njimi se zapise znova iz vira. */
package si.turnirko.uvoz.stupa;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import si.turnirko.dto.IzidUvozaDto;
import si.turnirko.dto.PorociloUvozaDto;
import si.turnirko.dto.PredogledUvozaDto;
import si.turnirko.dto.SezonaUvozaDto;
import si.turnirko.dto.UvozDogodekDto;
import si.turnirko.dto.UvozZagonDto;
import si.turnirko.dto.UvozZahtevaDto;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.Spol;
import si.turnirko.modeli.UvozZagon;
import si.turnirko.modeli.VirTekmovanja;
import si.turnirko.modeli.ZunanjaPovezava;
import si.turnirko.repozitoriji.UvozZagonRepozitorij;
import si.turnirko.storitve.LestvicaLigeStoritev;
import si.turnirko.storitve.PreracunRatingaStoritev;
import si.turnirko.storitve.RazvrstitevStoritev;
import si.turnirko.uvoz.UvozOblike;

@Service
public class UvozStupeStoritev {

    private static final Logger dnevnik = LoggerFactory.getLogger(UvozStupeStoritev.class);
    private static final DateTimeFormatter OBLIKA_POSNETKA = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final Pattern OZNAKA_POSNETKA = Pattern.compile("^(\\d{1,9})-(\\d{8})-(\\d{6})$");
    /* Testni dogodki zveze ("Samo - TEST ...", "S15 Test") v zgodovino ne sodijo. */
    private static final Pattern TESTNI = Pattern.compile("(?i)^\\s*samo\\b.*|.*\\btest\\b.*");

    /* Kar preslikava in uskladitev vrneta iz ene transakcije. */
    public record Izvedba(PorociloUvoza porocilo, SledUvoza sled, LocalDate preracunOd) {}

    private final StupaOdjemalec odjemalec;
    private final RepozitorijiUvoza repo;
    private final RazvrstitevStoritev razvrstitev;
    private final LestvicaLigeStoritev lestvice;
    private final PreracunRatingaStoritev preracun;
    private final UvozZagonRepozitorij zagoni;
    private final TransactionTemplate transakcija;
    private final TransactionTemplate dnevnikTransakcija;
    private final ObjectMapper json = new ObjectMapper();

    @Value("${turnirko.uvoz.posnetki:./podatki/stupa-posnetki}")
    private String mapaPosnetkov;

    public UvozStupeStoritev(StupaOdjemalec odjemalec, RepozitorijiUvoza repo, RazvrstitevStoritev razvrstitev,
                             LestvicaLigeStoritev lestvice, PreracunRatingaStoritev preracun,
                             UvozZagonRepozitorij zagoni, PlatformTransactionManager upravitelj) {
        this.odjemalec = odjemalec;
        this.repo = repo;
        this.razvrstitev = razvrstitev;
        this.lestvice = lestvice;
        this.preracun = preracun;
        this.zagoni = zagoni;
        this.transakcija = new TransactionTemplate(upravitelj);
        this.dnevnikTransakcija = new TransactionTemplate(upravitelj);
        this.dnevnikTransakcija.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // ---------------------------------------------------------------------
    // Seznam
    // ---------------------------------------------------------------------

    /* Objavljeni dogodki sezone (privzeto tekoce) s stanjem uvoza. */
    public List<UvozDogodekDto> dogodki(Long idSezone) {
        List<JsonNode> sezone;
        List<JsonNode> dogodki;
        try {
            sezone = odjemalec.sezone();
            dogodki = odjemalec.dogodki();
        } catch (IOException e) {
            throw new DomenskaIzjema("Stupa ni dosegljiva: " + e.getMessage());
        }
        long sezona = idSezone != null ? idSezone : tekocaSezona(sezone);
        Map<Long, String> imenaSezon = new HashMap<>();
        sezone.forEach(s -> imenaSezon.put(s.path("id").asLong(), s.path("season_name").asText(null)));

        Map<String, UvozZagon> zadnji = new HashMap<>();
        for (UvozZagon z : zagoni.findByVirOrderByIdDesc(VirTekmovanja.STUPA)) {
            zadnji.putIfAbsent(z.getZunanjiId(), z);
        }
        List<UvozDogodekDto> r = new ArrayList<>();
        for (JsonNode d : dogodki) {
            if (!d.path("published").asBoolean(false) || d.path("season_id").asLong(0) != sezona
                    || jeTestni(d.path("name").asText(""))) {
                continue;
            }
            long id = d.path("id").asLong();
            boolean liga = "L".equals(d.path("event_type").asText());
            Long lokalni = PovezaveStupe.lokalni(repo.povezave(),
                    liga ? ZunanjaPovezava.Vrsta.LIGA : ZunanjaPovezava.Vrsta.TURNIR, id).orElse(null);
            UvozZagon z = zadnji.get(String.valueOf(id));
            r.add(new UvozDogodekDto(id, UvozOblike.ocisti(d.path("name").asText(null)), liga ? "LIGA" : "TURNIR",
                    UvozOblike.datum(d.path("event_start_date").asText(null)),
                    UvozOblike.datum(d.path("event_end_date").asText(null)),
                    imenaSezon.get(sezona), liga ? null : lokalni, liga ? lokalni : null,
                    z == null ? null : UvozZagonDto.iz(z)));
        }
        r.sort(Comparator.comparing(UvozDogodekDto::zacetek, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(UvozDogodekDto::ime));
        return r;
    }

    /* Sezone zveze, najnovejsa prva. */
    public List<SezonaUvozaDto> sezone() {
        List<JsonNode> sezone;
        try {
            sezone = odjemalec.sezone();
        } catch (IOException e) {
            throw new DomenskaIzjema("Stupa ni dosegljiva: " + e.getMessage());
        }
        long tekoca = tekocaSezona(sezone);
        return sezone.stream()
                .sorted(Comparator.comparing((JsonNode s) -> s.path("start_date").asText("")).reversed())
                .map(s -> new SezonaUvozaDto(s.path("id").asLong(), UvozOblike.ocisti(s.path("season_name").asText(null)),
                        s.path("id").asLong() == tekoca))
                .toList();
    }

    /* Testni dogodek zveze ("Samo - TEST ...", "S15 Test") - v zgodovino ne sodi. */
    public static boolean jeTestni(String ime) {
        return ime != null && TESTNI.matcher(ime).matches();
    }

    public List<UvozZagonDto> zagoni() {
        return zagoni.findByVirOrderByIdDesc(VirTekmovanja.STUPA).stream().limit(200).map(UvozZagonDto::iz).toList();
    }

    // ---------------------------------------------------------------------
    // Predogled in uvoz
    // ---------------------------------------------------------------------

    public PredogledUvozaDto predogled(long idDogodka, UvozZahtevaDto zahteva) {
        String oznaka = idDogodka + "-" + LocalDateTime.now().format(OBLIKA_POSNETKA);
        Path mapa = posnemi(idDogodka, oznaka);
        PosnetekDogodka p = PosnetekDogodka.beri(mapa, null, null, datumPosnetka(oznaka));
        Map<Long, IdentitetaStupe.Odlocitev> odlocitve = odlocitve(zahteva);

        Izvedba izvedba;
        try {
            izvedba = transakcija.execute(stanje -> {
                stanje.setRollbackOnly();
                return izvedi(p, IdentitetaStupe.Nacin.STROGO, odlocitve);
            });
        } catch (RuntimeException e) {
            dnevnik.error("Predogled uvoza dogodka {} ni uspel", idDogodka, e);
            PorociloUvoza r = new PorociloUvoza();
            r.napaka("nepricakovana napaka pri preslikavi", e.getClass().getSimpleName() + ": " + e.getMessage());
            return new PredogledUvozaDto(idDogodka, p.ime(), oznaka, p.zgostitev(), false, false, null, null, r.vDto());
        }
        boolean enak = zadnjiUspesen(idDogodka).map(z -> z.getZgostitev().equals(p.zgostitev())).orElse(false);
        return new PredogledUvozaDto(idDogodka, p.ime(), oznaka, p.zgostitev(), enak,
                izvedba.porocilo().dovoljuje(),
                p.jeLiga() ? null : obstojeci(ZunanjaPovezava.Vrsta.TURNIR, idDogodka),
                p.jeLiga() ? obstojeci(ZunanjaPovezava.Vrsta.LIGA, idDogodka) : null,
                izvedba.porocilo().vDto());
    }

    public IzidUvozaDto uvozi(long idDogodka, UvozZahtevaDto zahteva) {
        if (zahteva == null || zahteva.posnetek() == null) {
            throw new NeveljavenVnosIzjema("Uvoz potrebuje posnetek iz predogleda.");
        }
        var m = OZNAKA_POSNETKA.matcher(zahteva.posnetek());
        if (!m.matches() || Long.parseLong(m.group(1)) != idDogodka) {
            throw new NeveljavenVnosIzjema("Oznaka posnetka ni veljavna za ta dogodek.");
        }
        Path mapa = Path.of(mapaPosnetkov).resolve(zahteva.posnetek());
        if (!Files.isDirectory(mapa)) {
            throw new NiNajdenoIzjema("Posnetka " + zahteva.posnetek() + " ni vec - naredi predogled znova.");
        }
        PosnetekDogodka p = PosnetekDogodka.beri(mapa, null, null, datumPosnetka(zahteva.posnetek()));

        UvozZagon zagon = dnevnikTransakcija.execute(s -> zagoni.save(new UvozZagon(VirTekmovanja.STUPA,
                String.valueOf(idDogodka), UvozOblike.prirezi(p.ime(), 200), p.zgostitev(), uporabnik(),
                LocalDateTime.now())));

        if (!zahteva.vsili() && zadnjiUspesen(idDogodka).map(z -> z.getZgostitev().equals(p.zgostitev())).orElse(false)) {
            zakljuci(zagon, UvozZagon.Izid.BREZ_SPREMEMB, null, null);
            return new IzidUvozaDto(zagon.getId(), UvozZagon.Izid.BREZ_SPREMEMB,
                    p.jeLiga() ? null : obstojeci(ZunanjaPovezava.Vrsta.TURNIR, idDogodka),
                    p.jeLiga() ? obstojeci(ZunanjaPovezava.Vrsta.LIGA, idDogodka) : null, null, null, null);
        }

        Map<Long, IdentitetaStupe.Odlocitev> odlocitve = odlocitve(zahteva);
        Izvedba izvedba;
        try {
            izvedba = transakcija.execute(stanje -> {
                Izvedba x = izvedi(p, IdentitetaStupe.Nacin.STROGO, odlocitve);
                if (!x.porocilo().dovoljuje()) {
                    stanje.setRollbackOnly();
                }
                return x;
            });
        } catch (RuntimeException e) {
            dnevnik.error("Uvoz dogodka {} ni uspel", idDogodka, e);
            String napaka = e.getClass().getSimpleName() + ": " + e.getMessage();
            zakljuci(zagon, UvozZagon.Izid.NAPAKA, null, napaka);
            return new IzidUvozaDto(zagon.getId(), UvozZagon.Izid.NAPAKA, null, null, null, napaka, null);
        }

        PorociloUvozaDto porocilo = izvedba.porocilo().vDto();
        if (!izvedba.porocilo().dovoljuje()) {
            zakljuci(zagon, UvozZagon.Izid.ZAVRNJENO, porocilo, null);
            return new IzidUvozaDto(zagon.getId(), UvozZagon.Izid.ZAVRNJENO, null, null, null, null, porocilo);
        }

        Integer preracunanih = null;
        String napaka = null;
        try {
            preracunanih = preracun.preracunajOd(izvedba.preracunOd()).obracunanihTekem();
        } catch (RuntimeException e) {
            // podatki so zapisani; rating je mogoce preracunati znova (POST /rating/preracun)
            dnevnik.error("Preracun ratinga po uvozu dogodka {} ni uspel", idDogodka, e);
            napaka = "Uvoz je zapisan, preracun ratinga pa ni uspel: " + e.getMessage();
        }
        zakljuci(zagon, UvozZagon.Izid.USPEH, porocilo, napaka);
        return new IzidUvozaDto(zagon.getId(), UvozZagon.Izid.USPEH, izvedba.sled().idTurnir, izvedba.sled().idLiga,
                preracunanih, napaka, porocilo);
    }

    // ---------------------------------------------------------------------
    // Preslikava v eni transakciji (jo uporablja tudi zgodovinski uvoz)
    // ---------------------------------------------------------------------

    /* Zapise dogodek iz posnetka in ga uskladi z virom - v TEKOCI transakciji.
       Rating se ne preracuna (to naredi klicatelj po potrditvi). */
    public Izvedba izvedi(PosnetekDogodka p, IdentitetaStupe.Nacin nacin, Map<Long, IdentitetaStupe.Odlocitev> odlocitve) {
        return izvedi(p, nacin, odlocitve, Map.of());
    }

    /* znaneOsebe: podatki o osebah iz vseh posnetkov (zgodovinski uvoz, glej
       ZnaneOsebeStupe) - dopolnijo osebo, ki jih v tem dogodku nima. */
    public Izvedba izvedi(PosnetekDogodka p, IdentitetaStupe.Nacin nacin, Map<Long, IdentitetaStupe.Odlocitev> odlocitve,
                          Map<Long, IdentitetaStupe.Oseba> znaneOsebe) {
        PorociloUvoza r = new PorociloUvoza();
        SledUvoza sled = new SledUvoza();
        if (p.osirotelihTekem() > 0) {
            r.prestej("tekem razveljavljenih zrebov pri viru (izpuscene)", p.osirotelihTekem());
        }
        LocalDate od = p.zacetek();
        repo.em().flush();
        if (p.jeLiga()) {
            Long idLiga = PovezaveStupe.lokalni(repo.povezave(), ZunanjaPovezava.Vrsta.LIGA, p.id()).orElse(null);
            if (idLiga != null) {
                LocalDateTime prej = repo.lige().findById(idLiga).map(l -> l.getZacetekPrvegaKola()).orElse(null);
                od = najzgodnejsi(od, prej == null ? null : prej.toLocalDate());
                CiscenjeUvoza.vsebinaLige(repo.em(), idLiga);
                r.prestej("ponovnih uvozov lige");
            }
        } else {
            Long idTurnir = PovezaveStupe.lokalni(repo.povezave(), ZunanjaPovezava.Vrsta.TURNIR, p.id()).orElse(null);
            if (idTurnir != null) {
                od = najzgodnejsi(od, repo.turnirji().findById(idTurnir).map(t -> t.getDatumZacetka()).orElse(null));
                for (Dogodek d : repo.dogodki().findByTurnirIdOrderByIdAsc(idTurnir)) {
                    CiscenjeUvoza.vsebinaDogodka(repo.em(), d.getId());
                }
                r.prestej("ponovnih uvozov turnirja");
            }
        }
        repo.em().clear();

        IdentitetaStupe identiteta = new IdentitetaStupe(nacin, odlocitve, repo.igralci(), repo.klubi(),
                repo.prijave(), repo.kadri(), repo.povezave(), r);
        identiteta.upostevajZnaneOsebe(znaneOsebe);
        SrecanjaStupe srecanja = new SrecanjaStupe(identiteta, repo.postave(), repo.tekmeSrecanj(),
                repo.niziSrecanj(), r);
        UskladitevStupe uskladitev = new UskladitevStupe(repo, razvrstitev, lestvice, r);
        if (p.jeLiga()) {
            new PreslikavaLigeStupe(repo, identiteta, srecanja, lestvice, r, sled).uvozi(p);
            if (!r.imaNapake()) {
                uskladitev.liga(p, sled);
            }
        } else {
            new PreslikavaTurnirjaStupe(repo, identiteta, srecanja, razvrstitev, r, sled).uvozi(p);
            if (!r.imaNapake()) {
                uskladitev.turnir(p, sled);
            }
        }
        return new Izvedba(r, sled, od);
    }

    // ---------------------------------------------------------------------
    // Pomozno
    // ---------------------------------------------------------------------

    private Path posnemi(long idDogodka, String oznaka) {
        try {
            JsonNode vrstica = odjemalec.dogodki().stream()
                    .filter(d -> d.path("id").asLong() == idDogodka)
                    .findFirst()
                    .orElseThrow(() -> new NiNajdenoIzjema("Dogodka " + idDogodka + " pri Stupi ni."));
            long idSezone = vrstica.path("season_id").asLong(0);
            JsonNode sezona = odjemalec.sezone().stream()
                    .filter(s -> s.path("id").asLong() == idSezone)
                    .findFirst().orElse(null);
            Path mapa = Path.of(mapaPosnetkov).resolve(oznaka);
            odjemalec.posnemiDogodek(vrstica, sezona, mapa);
            return mapa;
        } catch (IOException e) {
            throw new DomenskaIzjema("Posnetka dogodka " + idDogodka + " ni bilo mogoce narediti: " + e.getMessage());
        }
    }

    private static LocalDate datumPosnetka(String oznaka) {
        var m = OZNAKA_POSNETKA.matcher(oznaka);
        if (!m.matches()) {
            return LocalDate.now();
        }
        return LocalDate.parse(m.group(2), DateTimeFormatter.BASIC_ISO_DATE);
    }

    private java.util.Optional<UvozZagon> zadnjiUspesen(long idDogodka) {
        return zagoni.findByVirAndZunanjiIdOrderByIdDesc(VirTekmovanja.STUPA, String.valueOf(idDogodka)).stream()
                .filter(z -> z.getIzid() == UvozZagon.Izid.USPEH)
                .findFirst();
    }

    private Long obstojeci(ZunanjaPovezava.Vrsta vrsta, long idDogodka) {
        return PovezaveStupe.lokalni(repo.povezave(), vrsta, idDogodka).orElse(null);
    }

    private void zakljuci(UvozZagon zagon, UvozZagon.Izid izid, PorociloUvozaDto porocilo, String napaka) {
        Map<String, Object> povzetek = new LinkedHashMap<>();
        if (porocilo != null) {
            povzetek.put("stevci", porocilo.stevci());
            povzetek.put("napake", porocilo.napake().stream().map(u -> u.vrsta() + " (" + u.stevilo() + ")").toList());
            povzetek.put("opozorila", porocilo.opozorila().stream().map(u -> u.vrsta() + " (" + u.stevilo() + ")").toList());
            povzetek.put("odlocitve", porocilo.odlocitve().size());
            povzetek.put("noviIgralci", porocilo.noviIgralci().size());
            povzetek.put("preverbe", porocilo.preverbe().stream()
                    .map(x -> (x.ujemanje() ? "OK " : x.obvezna() ? "NAPAKA " : "RAZLIKA ") + x.opis()).toList());
        }
        if (napaka != null) {
            povzetek.put("napaka", napaka);
        }
        String besedilo;
        try {
            besedilo = json.writeValueAsString(povzetek);
        } catch (IOException e) {
            besedilo = null;
        }
        final String koncno = besedilo;
        dnevnikTransakcija.executeWithoutResult(s -> {
            UvozZagon z = zagoni.findById(zagon.getId()).orElseThrow();
            z.zakljuci(izid, koncno);
            zagoni.save(z);
        });
    }

    private static Map<Long, IdentitetaStupe.Odlocitev> odlocitve(UvozZahtevaDto zahteva) {
        Map<Long, IdentitetaStupe.Odlocitev> r = new HashMap<>();
        if (zahteva != null && zahteva.odlocitve() != null) {
            for (UvozZahtevaDto.Odlocitev o : zahteva.odlocitve()) {
                Spol spol = null;
                if (o.spol() != null && !o.spol().isBlank()) {
                    try {
                        spol = Spol.valueOf(o.spol().trim().toUpperCase());
                    } catch (IllegalArgumentException e) {
                        throw new NeveljavenVnosIzjema("Spol mora biti MOSKI ali ZENSKI (oseba " + o.idOsebe() + ").");
                    }
                }
                if (o.datumRojstva() != null && (o.datumRojstva().getYear() < 1900 || o.datumRojstva().isAfter(LocalDate.now()))) {
                    throw new NeveljavenVnosIzjema("Datum rojstva osebe " + o.idOsebe() + " ni veljaven.");
                }
                r.put(o.idOsebe(), new IdentitetaStupe.Odlocitev(o.idIgralec() == null ? 0L : o.idIgralec(),
                        o.ime(), o.priimek(), o.datumRojstva(), spol));
            }
        }
        return r;
    }

    private static long tekocaSezona(List<JsonNode> sezone) {
        return sezone.stream()
                .filter(s -> "ongoing".equalsIgnoreCase(s.path("status").asText()))
                .map(s -> s.path("id").asLong())
                .findFirst()
                .orElseGet(() -> sezone.stream()
                        .max(Comparator.comparing(s -> s.path("start_date").asText("")))
                        .map(s -> s.path("id").asLong()).orElse(0L));
    }

    private static LocalDate najzgodnejsi(LocalDate a, LocalDate b) {
        if (a == null) {
            return b;
        }
        return b != null && b.isBefore(a) ? b : a;
    }

    private static String uporabnik() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a == null ? null : a.getName();
    }
}
