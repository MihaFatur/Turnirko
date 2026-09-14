/* Poslovna logika lig: konfiguracija lige, ekipe, kader in generiranje
   razporeda po kroznem sistemu. Prehodi stanj:
   PRIPRAVA (ureja se konfiguracija, ekipe, kader) -> V_TEKU (razpored
   generiran, srecanja tecejo) -> ZAKLJUCEN.

   Spremembe strukture (ekipe, kader, format) so mozne le v PRIPRAVI, da se
   ze generiran razpored ne razveljavi. */
package si.turnirko.storitve;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.EkipaDto;
import si.turnirko.dto.EkipaVnos;
import si.turnirko.dto.KaderIgralecDto;
import si.turnirko.dto.KaderVnos;
import si.turnirko.dto.LestvicaDvojiceDto;
import si.turnirko.dto.LestvicaEkipeDto;
import si.turnirko.dto.LestvicaIgralcaLigeDto;
import si.turnirko.dto.LigaDto;
import si.turnirko.dto.LigaVnos;
import si.turnirko.dto.ParRazporedaDto;
import si.turnirko.dto.PrehodiVnos;
import si.turnirko.dto.RocniRazporedVnos;
import si.turnirko.dto.SrecanjeDto;
import si.turnirko.dto.TerminiVnos;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.modeli.Ekipa;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.KaderEkipe;
import si.turnirko.modeli.Klub;
import si.turnirko.modeli.Liga;
import si.turnirko.modeli.PredlogaLige;
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.modeli.Srecanje;
import si.turnirko.modeli.StatusSrecanja;
import si.turnirko.modeli.StatusTekmovanja;
import si.turnirko.repozitoriji.EkipaRepozitorij;
import si.turnirko.repozitoriji.IgralecRepozitorij;
import si.turnirko.repozitoriji.KaderEkipeRepozitorij;
import si.turnirko.repozitoriji.KlubRepozitorij;
import si.turnirko.repozitoriji.LigaRepozitorij;
import si.turnirko.repozitoriji.SrecanjeRepozitorij;
import si.turnirko.storitve.LestvicaLigeStoritev.Bilanca;
import si.turnirko.storitve.LestvicaLigeStoritev.BilanceLige;

@Service
public class LigaStoritev {

    /* Koliko lig sme hkrati stati na domaci strani. Sklop je povzetek in ne
       seznam: tretja liga potisne lestvico pod rob zaslona telefona. Meja je
       tu in ne v shemi, ker je stvar predstavitve (glej V17). */
    public static final int LIG_NA_DOMACI = 2;

    private final LigaRepozitorij ligaRepozitorij;
    private final EkipaRepozitorij ekipaRepozitorij;
    private final KaderEkipeRepozitorij kaderRepozitorij;
    private final SrecanjeRepozitorij srecanjeRepozitorij;
    private final KlubRepozitorij klubRepozitorij;
    private final IgralecRepozitorij igralecRepozitorij;
    private final RazporedStoritev razporedStoritev;
    private final LestvicaLigeStoritev lestvicaLigeStoritev;
    private final SpremembeRatingaStoritev spremembeRatinga;
    private final LastnistvoStoritev lastnistvo;

    public LigaStoritev(LigaRepozitorij ligaRepozitorij,
                        EkipaRepozitorij ekipaRepozitorij,
                        KaderEkipeRepozitorij kaderRepozitorij,
                        SrecanjeRepozitorij srecanjeRepozitorij,
                        KlubRepozitorij klubRepozitorij,
                        IgralecRepozitorij igralecRepozitorij,
                        RazporedStoritev razporedStoritev,
                        LestvicaLigeStoritev lestvicaLigeStoritev,
                        SpremembeRatingaStoritev spremembeRatinga,
                        LastnistvoStoritev lastnistvo) {
        this.ligaRepozitorij = ligaRepozitorij;
        this.ekipaRepozitorij = ekipaRepozitorij;
        this.kaderRepozitorij = kaderRepozitorij;
        this.srecanjeRepozitorij = srecanjeRepozitorij;
        this.klubRepozitorij = klubRepozitorij;
        this.igralecRepozitorij = igralecRepozitorij;
        this.razporedStoritev = razporedStoritev;
        this.lestvicaLigeStoritev = lestvicaLigeStoritev;
        this.spremembeRatinga = spremembeRatinga;
        this.lastnistvo = lastnistvo;
    }

    // ---------- Liga ----------

    @Transactional(readOnly = true)
    public List<LigaDto> vse() {
        Map<Long, Kola> kola = kolaPoLigah();
        return ligaRepozitorij.najdiVse().stream()
                .map(l -> {
                    Kola k = kola.getOrDefault(l.getId(), Kola.PRAZNA);
                    return LigaDto.iz(l, (int) ekipaRepozitorij.countByLigaId(l.getId()),
                            k.odigranih(), k.vseh());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public LigaDto najdi(Long id) {
        Liga l = ligaRepozitorij.najdiZVisjo(id)
                .orElseThrow(() -> new NiNajdenoIzjema("Liga z id " + id + " ne obstaja."));
        Kola k = presteji(srecanjeRepozitorij.stanjeKol(id), 1);
        return LigaDto.iz(l, (int) ekipaRepozitorij.countByLigaId(id), k.odigranih(), k.vseh());
    }

    /* Koliko kol ima liga in koliko jih je odigranih. */
    private record Kola(int odigranih, int vseh) {
        static final Kola PRAZNA = new Kola(0, 0);
    }

    private Map<Long, Kola> kolaPoLigah() {
        Map<Long, List<Object[]>> poLigah = new HashMap<>();
        for (Object[] r : srecanjeRepozitorij.stanjeKolPoLigah()) {
            poLigah.computeIfAbsent(((Number) r[0]).longValue(), k -> new ArrayList<>()).add(r);
        }
        Map<Long, Kola> rezultat = new HashMap<>();
        poLigah.forEach((idLiga, vrstice) -> rezultat.put(idLiga, presteji(vrstice, 2)));
        return rezultat;
    }

    /* Vrstice so [.., srecanj, koncanih]; "odmik" pove, kje se v vrstici
       zacneta stevili (skupinska poizvedba cez vse lige ima spredaj se id). */
    private static Kola presteji(List<Object[]> vrstice, int odmik) {
        int odigranih = 0;
        for (Object[] r : vrstice) {
            long srecanj = ((Number) r[odmik]).longValue();
            long koncanih = ((Number) r[odmik + 1]).longValue();
            if (srecanj > 0 && srecanj == koncanih) {
                odigranih++;
            }
        }
        return new Kola(odigranih, vrstice.size());
    }

    @Transactional
    public LigaDto ustvari(LigaVnos v) {
        Liga liga = new Liga();
        uporabiVnos(liga, v);
        // zabelezi lastnika (organizator oz. admin, ki jo ustvarja)
        lastnistvo.oznaciLastnika(liga);
        liga = ligaRepozitorij.save(liga);
        return LigaDto.iz(liga, 0);
    }

    @Transactional
    public LigaDto uredi(Long id, LigaVnos v) {
        lastnistvo.preveriLigaPoId(id);
        Liga liga = ligaRepozitorij.findById(id)
                .orElseThrow(() -> new NiNajdenoIzjema("Liga z id " + id + " ne obstaja."));
        if (liga.getStatus() != StatusTekmovanja.PRIPRAVA) {
            throw new DomenskaIzjema("Ligo je mogoce urejati samo, dokler je v pripravi.");
        }
        uporabiVnos(liga, v);
        liga = ligaRepozitorij.save(liga);
        return LigaDto.iz(liga, (int) ekipaRepozitorij.countByLigaId(id));
    }

    /* Liga na domaci strani: uredniska odlocitev, katero tekmovanje je izlozba
       zveze.

       Kot prehodi in termini kol NI del "uredi": pravila se ob generiranju
       razporeda zaklenejo, ligo na domaci strani pa je treba zamenjati prav
       takrat, ko tece. Iz istega razloga tu ni preverbe lastnistva -
       organizator sme svojo ligo, domaca stran pa ni njegova; koncno tocko
       varnostna veriga omeji na ADMIN.

       Meja LIG_NA_DOMACI se preveri tu in ne v shemi: pogoj cez vec vrstic bi
       v SQLite terjal prozilec, sporocilo pa mora povedati, katera liga je na
       poti (drugace admin ugiba, kaj mora odkljukati). */
    @Transactional
    public LigaDto nastaviNaDomaci(Long id, boolean naDomaci) {
        Liga liga = ligaRepozitorij.findById(id)
                .orElseThrow(() -> new NiNajdenoIzjema("Liga z id " + id + " ne obstaja."));

        if (naDomaci && !liga.isNaDomaci()) {
            List<Liga> ze = ligaRepozitorij.najdiNaDomaci();
            if (ze.size() >= LIG_NA_DOMACI) {
                throw new DomenskaIzjema("Na domaci strani sta lahko najvec "
                        + LIG_NA_DOMACI + " ligi. Najprej odstrani eno od teh: "
                        + ze.stream().map(Liga::getIme).collect(Collectors.joining(", ")) + ".");
            }
        }

        liga.setNaDomaci(naDomaci);
        liga = ligaRepozitorij.save(liga);
        /* Zastavica se preklaplja sredi sezone, zato kol ne smemo zanemariti -
           odgovor je isti pogled kot pri najdi() (enako kot nastaviPrehode). */
        Kola k = presteji(srecanjeRepozitorij.stanjeKol(id), 1);
        return LigaDto.iz(liga, (int) ekipaRepozitorij.countByLigaId(id), k.odigranih(), k.vseh());
    }

    /* Mesto lige v piramidi (visja liga, nizje lige, koliko napreduje/izpade).

       Namenoma NI del "uredi": pravila lige so po generiranju razporeda
       zaklenjena, ker bi popravek razveljavil odigrano, povezave med ligami pa
       na razpored ne vplivajo - so opis sezone in jih je treba smeti popraviti
       tudi sredi tekmovanja (npr. ko nastane nova nizja liga).

       Nizje lige nosijo povezavo v SVOJEM stolpcu, zato jih tu spreminjamo -
       in zato mora imeti urejevalec pravico tudi nad njimi.

       Uvozena liga je sicer samo za branje, piramido pa sme dobiti: vir
       povezav med ligami ne pozna, zato jih uvoz ne more prenesti in jih ne
       bo povozil (preveriLigaZaOpis). */
    @Transactional
    public LigaDto nastaviPrehode(Long id, PrehodiVnos v) {
        lastnistvo.preveriLigaZaOpis(id);
        Liga liga = ligaRepozitorij.findById(id)
                .orElseThrow(() -> new NiNajdenoIzjema("Liga z id " + id + " ne obstaja."));

        liga.setStNapreduje(v.stNapreduje() != null ? Math.max(0, v.stNapreduje()) : 0);
        liga.setStIzpade(v.stIzpade() != null ? Math.max(0, v.stIzpade()) : 0);

        if (v.idVisjaLiga() != null) {
            if (v.idVisjaLiga().equals(id)) {
                throw new NeveljavenVnosIzjema("Liga ne more biti sama sebi nadrejena.");
            }
            liga.setVisjaLiga(ligaRepozitorij.findById(v.idVisjaLiga()).orElseThrow(
                    () -> new NiNajdenoIzjema("Visja liga z id " + v.idVisjaLiga() + " ne obstaja.")));
        } else {
            liga.setVisjaLiga(null);
        }

        List<Liga> spremenjene = new ArrayList<>();
        spremenjene.add(liga);

        // null = odjemalec nizjih lig ne ureja; prazen seznam = nobene ni
        if (v.idNizjeLige() != null) {
            Set<Long> zelene = new LinkedHashSet<>(v.idNizjeLige());
            if (zelene.contains(id)) {
                throw new NeveljavenVnosIzjema("Liga ne more biti sama sebi podrejena.");
            }
            for (Liga obstojeca : ligaRepozitorij.najdiNizje(id)) {
                // kar je bilo spodaj in v novem izboru ni vec, se odveze
                if (!zelene.remove(obstojeca.getId())) {
                    lastnistvo.preveriLigaZaOpis(obstojeca.getId());
                    obstojeca.setVisjaLiga(null);
                    spremenjene.add(obstojeca);
                }
            }
            for (Long idNizja : zelene) {
                Liga nizja = ligaRepozitorij.findById(idNizja)
                        .orElseThrow(() -> new NiNajdenoIzjema("Liga z id " + idNizja + " ne obstaja."));
                lastnistvo.preveriLigaZaOpis(idNizja);
                nizja.setVisjaLiga(liga);
                spremenjene.add(nizja);
            }
        }

        for (Liga s : spremenjene) {
            preveriBrezKroga(s);
        }
        ligaRepozitorij.saveAll(spremenjene);
        /* Prehode je mogoce urejati tudi sredi sezone, zato tu kol ne smemo
           zanemariti - odgovor je isti pogled kot pri najdi(). */
        Kola k = presteji(srecanjeRepozitorij.stanjeKol(id), 1);
        return LigaDto.iz(liga, (int) ekipaRepozitorij.countByLigaId(id), k.odigranih(), k.vseh());
    }

    /* Rocni termini kol: termin kola dobijo vsa njegova srecanja, liga z urami
       srecanj pa lahko vsakemu srecanju popravi se svojega (obvelja za
       terminom kola). Kot prehodi in za razliko od pravil to NI zaklenjeno s
       stanjem lige - kolo se prestavi tudi sredi sezone.

       Seme (zacetek prvega kola, razmik, ure) ostane, kakrsno je bilo: opisuje
       nacrt ob nastanku lige in je privzetek za polnjenje v vmesniku, ne pa
       zapis o tem, kdaj se kolo dejansko igra. */
    @Transactional
    public List<SrecanjeDto> nastaviTermine(Long id, TerminiVnos v) {
        lastnistvo.preveriLigaPoId(id);
        if (!ligaRepozitorij.existsById(id)) {
            throw new NiNajdenoIzjema("Liga z id " + id + " ne obstaja.");
        }
        Map<Integer, LocalDateTime> poKolih = new HashMap<>();
        Set<Integer> nasteta = new HashSet<>();
        for (TerminiVnos.TerminKola t : v.kola()) {
            if (!nasteta.add(t.kolo())) {
                throw new NeveljavenVnosIzjema("Kolo " + t.kolo() + " je v seznamu dvakrat.");
            }
            // null je veljaven vnos (kolo termin izgubi), zato loceno od "kolo ni nasteto"
            poKolih.put(t.kolo(), t.zacetek());
        }
        Map<Long, LocalDateTime> poSrecanjih = new HashMap<>();
        for (TerminiVnos.TerminSrecanja t : v.srecanja() != null ? v.srecanja() : List.<TerminiVnos.TerminSrecanja>of()) {
            if (poSrecanjih.containsKey(t.id())) {
                throw new NeveljavenVnosIzjema("Srecanje z id " + t.id() + " je v seznamu dvakrat.");
            }
            poSrecanjih.put(t.id(), t.zacetek());
        }

        // termin kola je last rednega dela; tekme koncnice imajo vsaka svoj
        // termin (KoncnicaStoritev.nastaviTermin)
        List<Srecanje> srecanja = srecanjeRepozitorij.najdiRednaZaLigo(id);
        if (srecanja.isEmpty()) {
            throw new DomenskaIzjema("Termine je mogoce vpisati sele, ko je razpored generiran.");
        }
        Set<Integer> obstojeca = srecanja.stream().map(Srecanje::getKolo).collect(Collectors.toSet());
        for (Integer kolo : nasteta) {
            if (!obstojeca.contains(kolo)) {
                throw new NeveljavenVnosIzjema("Liga nima " + kolo + ". kola.");
            }
        }
        Set<Long> obstojecaSrecanja = srecanja.stream().map(Srecanje::getId).collect(Collectors.toSet());
        for (Long idSrecanje : poSrecanjih.keySet()) {
            if (!obstojecaSrecanja.contains(idSrecanje)) {
                throw new NeveljavenVnosIzjema("Srecanje z id " + idSrecanje + " ni srecanje rednega dela te lige.");
            }
        }

        Set<Long> spremenjena = new HashSet<>();
        for (Srecanje s : srecanja) {
            LocalDateTime prej = s.getPredvidenZacetek();
            if (poKolih.containsKey(s.getKolo())) {
                s.setPredvidenZacetek(poKolih.get(s.getKolo()));
            }
            if (poSrecanjih.containsKey(s.getId())) {
                s.setPredvidenZacetek(poSrecanjih.get(s.getId()));
            }
            if (!Objects.equals(prej, s.getPredvidenZacetek())) {
                spremenjena.add(s.getId());
            }
        }
        preveriHkratnaSrecanja(srecanja, spremenjena);
        srecanjeRepozitorij.saveAll(srecanja);
        return srecanja.stream().map(SrecanjeDto::iz).toList();
    }

    /* Ekipa ne more igrati dveh srecanj hkrati. Pri kolu kroznega sistema se to
       ne more zgoditi (ekipa v kolu igra enkrat), pri ligi z urami pa en sam
       zamenjan cas postavi obe srecanja ekipe na 18.30.

       Steje le dolocena ura (00:00 = ura ni dolocena) in samo trk, v katerem je
       vsaj eno srecanje spremenil ta vnos - star zapis ne sme ustaviti popravka
       kola, ki z njim nima nic. */
    private static void preveriHkratnaSrecanja(List<Srecanje> srecanja, Set<Long> spremenjena) {
        Map<String, Srecanje> zasedeno = new HashMap<>();
        for (Srecanje s : srecanja) {
            LocalDateTime cas = s.getPredvidenZacetek();
            if (cas == null || cas.toLocalTime().equals(LocalTime.MIDNIGHT)) {
                continue;
            }
            for (Ekipa e : List.of(s.getEkipaDomaci(), s.getEkipaGost())) {
                Srecanje drugo = zasedeno.putIfAbsent(e.getId() + "|" + cas, s);
                if (drugo != null && (spremenjena.contains(s.getId()) || spremenjena.contains(drugo.getId()))) {
                    String kola = drugo.getKolo() == s.getKolo()
                            ? s.getKolo() + ". kolo"
                            : drugo.getKolo() + ". in " + s.getKolo() + ". kolo";
                    throw new NeveljavenVnosIzjema("Ekipa " + e.prikazanoIme() + " bi " + cas.getDayOfMonth()
                            + ". " + cas.getMonthValue() + ". ob " + oblikujUro(cas.toLocalTime())
                            + " igrala dve srecanji hkrati (" + kola + ").");
                }
            }
        }
    }

    private static String oblikujUro(LocalTime ura) {
        return ura.getHour() + "." + String.format("%02d", ura.getMinute());
    }

    @Transactional
    public void zbrisi(Long id) {
        lastnistvo.preveriLigaPoId(id);
        Liga liga = ligaRepozitorij.findById(id)
                .orElseThrow(() -> new NiNajdenoIzjema("Liga z id " + id + " ne obstaja."));
        if (srecanjeRepozitorij.existsByLigaId(id)) {
            throw new DomenskaIzjema("Lige z generiranim razporedom ni mogoce izbrisati.");
        }
        for (Ekipa e : ekipaRepozitorij.najdiZaLigo(id)) {
            kaderRepozitorij.deleteAll(kaderRepozitorij.najdiZaEkipo(e.getId()));
        }
        ekipaRepozitorij.deleteAll(ekipaRepozitorij.najdiZaLigo(id));
        ligaRepozitorij.delete(liga);
    }

    // ---------- Ekipe ----------

    /* Vrstni red seznama je odvisen od tega, kaj seznam JE. Pri navadni ligi je
       to šifrant prijavljenih in se bere po abecedi; pri ligi z enakomerno
       razvrstitvijo pa jakostna lestvica, iz katere zreb sestavi pare - tam bi
       abecedni izpis skril prav tisto, kar organizator ureja. */
    @Transactional(readOnly = true)
    public List<EkipaDto> ekipe(Long idLiga) {
        Map<Long, Integer> velikostKadra = new HashMap<>();
        for (Object[] r : kaderRepozitorij.steviloPoEkipah(idLiga)) {
            velikostKadra.put(((Number) r[0]).longValue(), ((Number) r[1]).intValue());
        }
        return ekipeVPravemRedu(idLiga).stream()
                .map(e -> EkipaDto.iz(e, velikostKadra.getOrDefault(e.getId(), 0)))
                .toList();
    }

    private List<Ekipa> ekipeVPravemRedu(Long idLiga) {
        boolean poJakosti = ligaRepozitorij.findById(idLiga)
                .map(Liga::isEnakomernaRazvrstitev)
                .orElse(false);
        return poJakosti
                ? ekipaRepozitorij.najdiZaLigoPoJakosti(idLiga)
                : ekipaRepozitorij.najdiZaLigo(idLiga);
    }

    /* Jakostni vrstni red ekip lige. Pricakuje VSE ekipe natanko enkrat - delni
       seznam bi tiho pustil koga brez mesta, po mestih pa tece razdelitev na
       pare in celoten zreb (isto pravilo kot IzborStoritev.shraniVrstniRed).

       Vezano na PRIPRAVO: po zrebu bi bila sprememba mest brez ucinka, ker je
       razpored ze zapisan, in bi trdila nekaj, kar ni res. */
    @Transactional
    public List<EkipaDto> shraniVrstniRedEkip(Long idLiga, List<Long> idjiEkipPoVrsti) {
        lastnistvo.preveriLigaPoId(idLiga);
        Liga liga = ligaRepozitorij.findById(idLiga)
                .orElseThrow(() -> new NiNajdenoIzjema("Liga z id " + idLiga + " ne obstaja."));
        preveriVPripravi(liga);

        List<Ekipa> ekipe = ekipaRepozitorij.najdiZaLigoPoJakosti(idLiga);
        Map<Long, Ekipa> poId = new HashMap<>();
        for (Ekipa e : ekipe) {
            poId.put(e.getId(), e);
        }

        Set<Long> videne = new LinkedHashSet<>();
        List<Ekipa> urejene = new ArrayList<>();
        for (Long idEkipa : idjiEkipPoVrsti) {
            Ekipa ekipa = poId.get(idEkipa);
            if (ekipa == null) {
                throw new NeveljavenVnosIzjema(
                        "Ekipa z id " + idEkipa + " ni prijavljena v to ligo.");
            }
            if (!videne.add(idEkipa)) {
                throw new NeveljavenVnosIzjema(
                        "Ekipa z id " + idEkipa + " se v vrstnem redu pojavi veckrat.");
            }
            urejene.add(ekipa);
        }
        if (videne.size() != ekipe.size()) {
            throw new NeveljavenVnosIzjema("Vrstni red mora vsebovati vse ekipe lige ("
                    + ekipe.size() + "), prejetih pa je " + videne.size() + ".");
        }

        prestevilci(urejene);
        Map<Long, Integer> velikostKadra = new HashMap<>();
        for (Object[] r : kaderRepozitorij.steviloPoEkipah(idLiga)) {
            velikostKadra.put(((Number) r[0]).longValue(), ((Number) r[1]).intValue());
        }
        return urejene.stream()
                .map(e -> EkipaDto.iz(e, velikostKadra.getOrDefault(e.getId(), 0)))
                .toList();
    }

    /* Mesta zapise od 1 naprej. Enolicnost mest v ligi stoji na tem, da se
       vedno prestevilci CEL seznam - zato je shema (za razliko od zaporedne
       stevilke ekipe) ne vsiljuje z indeksom. Kliceta jo tudi dodajanje in
       odstranjevanje ekipe, da so mesta v ligi vedno strnjen niz 1..N. */
    private void prestevilci(List<Ekipa> poVrsti) {
        int mesto = 1;
        for (Ekipa e : poVrsti) {
            e.setStNosilca(mesto++);
        }
        ekipaRepozitorij.saveAll(poVrsti);
    }

    /* Ekipo je mogoce prijaviti na dva nacina:

       KLUBSKA (idKlub izpolnjen) - nastop kluba iz registra; zaporedna jo loci
       od drugih ekip istega kluba, ime je neobvezno.

       PROSTA (idKlub prazen) - zasedba, ki kluba nima in ga ne bo dobila
       (rekreacijske, medpodjetniske lige). Zivi samo v tej ligi, zato registra
       klubov ne zasuje z enkratnimi zapisi; poimenuje jo lastno ime, ki je
       zato obvezno. Kader ostane skupen register igralcev - prosta je ekipa,
       ne igralci. */
    @Transactional
    public EkipaDto dodajEkipo(Long idLiga, EkipaVnos v) {
        lastnistvo.preveriLigaPoId(idLiga);
        Liga liga = ligaRepozitorij.findById(idLiga)
                .orElseThrow(() -> new NiNajdenoIzjema("Liga z id " + idLiga + " ne obstaja."));
        preveriVPripravi(liga);

        String ime = v.ime() != null && !v.ime().isBlank() ? v.ime().trim() : null;
        List<Ekipa> obstojece = ekipaRepozitorij.najdiZaLigo(idLiga);

        Klub klub = null;
        int zaporedna = 1;
        if (v.idKlub() != null) {
            klub = klubRepozitorij.findById(v.idKlub())
                    .orElseThrow(() -> new NiNajdenoIzjema("Klub z id " + v.idKlub() + " ne obstaja."));
            zaporedna = v.zaporedna() != null ? v.zaporedna() : naslednjaZaporedna(obstojece, klub.getId());
            if (zaporedna < 1) {
                throw new NeveljavenVnosIzjema("Zaporedna stevilka ekipe mora biti vsaj 1.");
            }
            if (ekipaRepozitorij.existsByLigaIdAndKlubIdAndZaporedna(idLiga, klub.getId(), zaporedna)) {
                throw new DomenskaIzjema("Ekipa " + klub.getIme() + " " + zaporedna + " v tej ligi ze obstaja.");
            }
        } else if (ime == null) {
            throw new NeveljavenVnosIzjema("Ekipa brez kluba mora imeti ime.");
        }

        /* V razporedu, na lestvici in v zapisniku se ekipi locita samo po
           prikazanem imenu - dve enaki bi bili gledalcu nelocljivi. Zato se
           preveri prikazano ime in ne le (klub, zaporedna): odslej ima lastno
           ime lahko tudi klubska ekipa in trk je mogoc cez oba tipa. */
        String prikazano = ime != null ? ime : klub.getIme() + " " + zaporedna;
        if (obstojece.stream().anyMatch(e -> e.prikazanoIme().equalsIgnoreCase(prikazano))) {
            throw new DomenskaIzjema("Ekipa \"" + prikazano + "\" v tej ligi ze obstaja.");
        }

        Ekipa ekipa = ekipaRepozitorij.save(new Ekipa(liga, klub, zaporedna, ime));
        /* Nova ekipa gre na dno jakostne lestvice: kam sodi, ve samo
           organizator, in dokler tega ne pove, je edino posteno mesto zadnje.
           Prestevilcimo cel seznam in ne vpisemo le naslednje stevilke: lige od
           prej mest nimajo (stolpec je iz V16) in prvi vpis jih tako uredi po
           vrstnem redu prijave, namesto da bi nova ekipa z mestom 1 pristala
           pred njimi. Mesta dobi tudi liga brez enakomerne razvrstitve - tam
           nicesar ne pomenijo, zato pa je lestvica ze pripravljena, ce
           organizator oznako prizge. */
        ekipaRepozitorij.flush();
        prestevilci(ekipaRepozitorij.najdiZaLigoPoJakosti(idLiga));
        // za DTO potrebujemo klub (nalozen); ponovno preberi z join fetch
        return EkipaDto.iz(ekipaRepozitorij.najdiZKlubomInLigo(ekipa.getId()).orElseThrow(), 0);
    }

    @Transactional
    public void odstraniEkipo(Long idEkipa) {
        lastnistvo.preveriPoEkipi(idEkipa);
        Ekipa ekipa = ekipaRepozitorij.najdiZKlubomInLigo(idEkipa)
                .orElseThrow(() -> new NiNajdenoIzjema("Ekipa z id " + idEkipa + " ne obstaja."));
        preveriVPripravi(ekipa.getLiga());
        Long idLiga = ekipa.getLiga().getId();
        kaderRepozitorij.deleteAll(kaderRepozitorij.najdiZaEkipo(idEkipa));
        ekipaRepozitorij.delete(ekipa);
        /* Odhod ekipe s sredine lestvice pusti vrzel; mesta so za organizatorja
           stevilke, ki jih bere ob imenih, zato jih strnemo nazaj v 1..N. */
        ekipaRepozitorij.flush();
        prestevilci(ekipaRepozitorij.najdiZaLigoPoJakosti(idLiga));
    }

    // ---------- Kader ----------

    /* Kader ekipe z ratingom in bilanco posamicnih tekem, ki jih je igralec
       odigral ZA TO ekipo v tej ligi. Bilanca je del izpisa kadra pod vrstico
       lestvice, zato jo prilozimo ze tu - vmesnik z eno poizvedbo dobi vse, kar
       razsirjena vrstica pokaze.

       Vrstni red je izkupicek in ne organizatorjev seznam: kader se odpre pod
       vrstico lestvice, kjer je vprasanje "kdo ekipo nosi", zato gredo zmage v
       tej ligi na vrh. Ob enakih zmagah odloca manj porazov (pri enakih zmagah
       je to isto kot boljsa uspesnost), nato organizatorjev vrstni red in
       abeceda - v pripravi, ko so bilance se 0 : 0, je izpis zato tak kot prej.
       Postava srecanja tega vrstnega reda NE deli (glej SrecanjeStoritev.kader):
       tam mesta A/B/C dolocajo vrstni red kadra in ne izkupicek. */
    @Transactional(readOnly = true)
    public List<KaderIgralecDto> kader(Long idEkipa) {
        Ekipa ekipa = ekipaRepozitorij.najdiZKlubomInLigo(idEkipa)
                .orElseThrow(() -> new NiNajdenoIzjema("Ekipa z id " + idEkipa + " ne obstaja."));
        List<KaderEkipe> kader = kaderRepozitorij.najdiZaEkipo(idEkipa);
        Map<Long, Integer> ratingi = spremembeRatinga.trenutniRatingi(
                kader.stream().map(k -> k.getIgralec().getId()).toList());
        BilanceLige bilance = lestvicaLigeStoritev.bilancePosamicnih(ekipa.getLiga().getId());
        /* Seznam iz repozitorija je ze urejen po vrstnem redu in abecedi, zato
           stabilno razvrscanje po izkupicku ta dva kljuca ohrani kot zadnji. */
        return kader.stream()
                .map(k -> {
                    Long idIgralec = k.getIgralec().getId();
                    Bilanca b = bilance.za(idEkipa, idIgralec);
                    return KaderIgralecDto.iz(k, ratingi.get(idIgralec), b.zmage(), b.porazi());
                })
                .sorted(Comparator.comparingInt(KaderIgralecDto::zmage).reversed()
                        .thenComparingInt(KaderIgralecDto::porazi))
                .toList();
    }

    @Transactional
    public KaderIgralecDto dodajVKader(Long idEkipa, KaderVnos v) {
        lastnistvo.preveriPoEkipi(idEkipa);
        Ekipa ekipa = ekipaRepozitorij.najdiZKlubomInLigo(idEkipa)
                .orElseThrow(() -> new NiNajdenoIzjema("Ekipa z id " + idEkipa + " ne obstaja."));
        Igralec igralec = igralecRepozitorij.najdiZVsem(v.idIgralec())
                .orElseThrow(() -> new NiNajdenoIzjema("Igralec z id " + v.idIgralec() + " ne obstaja."));

        if (kaderRepozitorij.existsByEkipaIdAndIgralecId(idEkipa, igralec.getId())) {
            throw new DomenskaIzjema("Igralec " + igralec.polnoIme() + " je ze v kadru te ekipe.");
        }
        if (ekipa.getLiga().isPrepovedDvojneRegistracije()
                && kaderRepozitorij.steviloVLigi(ekipa.getLiga().getId(), igralec.getId()) > 0) {
            throw new DomenskaIzjema("Igralec " + igralec.polnoIme()
                    + " je v tej ligi ze registriran za drugo ekipo (dvojna registracija ni dovoljena).");
        }
        KaderEkipe vnos = kaderRepozitorij.save(new KaderEkipe(ekipa, igralec, v.vrstniRed()));
        Integer rating = spremembeRatinga.trenutniRatingi(List.of(igralec.getId())).get(igralec.getId());
        /* Kader se ureja samo v pripravi, ko liga se ni odigrala nicesar - bilanca
           novega clana je zato nujno 0 : 0 in je ni treba sestevati. */
        return KaderIgralecDto.iz(vnos, rating, 0, 0);
    }

    @Transactional
    public void odstraniIzKadra(Long idKader) {
        lastnistvo.preveriPoKadru(idKader);
        KaderEkipe k = kaderRepozitorij.findById(idKader)
                .orElseThrow(() -> new NiNajdenoIzjema("Vnos kadra z id " + idKader + " ne obstaja."));
        kaderRepozitorij.delete(k);
    }

    // ---------- Razpored ----------

    /* Razpored nastane na dva izkljucujoca se nacina: sestavi ga zreb
       (generirajRazpored) ali pa ga VPISE organizator (rocniRazpored). Oba
       zapiseta isto vrsto srecanj, zato lestvica, termini, zapisniki in rating
       tecejo nespremenjeno - loci ju samo zastavica liga.rocniZreb, ki gre na
       javno stran lige (glej migracijo V26). */

    @Transactional
    public void generirajRazpored(Long idLiga) {
        Liga liga = ligaPredZrebom(idLiga);
        List<Ekipa> ekipe = ekipeZaZreb(liga);

        List<NovoSrecanje> srecanja = new ArrayList<>();
        for (RazporedStoritev.ParNaMestu par : razporedLige(liga, ekipe.size())) {
            srecanja.add(new NovoSrecanje(
                    new Srecanje(liga, par.kolo(), ekipe.get(par.domaci()), ekipe.get(par.gost())),
                    par.mesto()));
        }
        zapisiRazpored(liga, srecanja, false);
    }

    /* Pari po pravilih lige: krozni sistem (navaden ali po parih) in, kadar
       liga igra ob urah, razdelitev po vecerih. Isti racun za zreb in predlog. */
    private List<RazporedStoritev.ParNaMestu> razporedLige(Liga liga, int stEkip) {
        return razporedStoritev.razpored(stEkip, liga.isDvokrozno(), liga.isEnakomernaRazvrstitev(),
                liga.getUreSrecanj());
    }

    /* Rocno vpisan razpored: pare je dolocil clovek in ne zreb.

       Liga, ki se je doslej vodila na roke, pride v aplikacijo z razporedom, ki
       je ze razposlan igralcem - naklucni zreb bi ga zavrgel. Vpis zato tece na
       isto mesto kot zreb (liga v pripravi, se brez srecanj) in se konca enako:
       srecanja dobijo termine iz semena, liga gre v V_TEKU.

       Preverimo tisto, kar bi razpored pokvarilo, ne pa tudi, ali je "pravilen"
       krozni sistem. Ekipa sama proti sebi in vrzel med koli sta napaka v
       vsakem primeru (kola lige tecejo od 1 naprej), prav tako ekipa, ki bi
       igrala dve srecanji hkrati: v kolu kroznega sistema to pomeni dve
       srecanji v kolu, v ligi z urami dve srecanji ob isti uri. Ali kak par
       igra dvakrat ali nikoli, pa je stvar tekmovanja in ne sheme: rocno
       vodene lige imajo tudi nepopolne razporede, zato vmesnik na to samo
       opozori, strezniku pa je to veljaven vnos. */
    @Transactional
    public List<SrecanjeDto> rocniRazpored(Long idLiga, RocniRazporedVnos v) {
        Liga liga = ligaPredZrebom(idLiga);
        Map<Long, Ekipa> poId = new HashMap<>();
        for (Ekipa e : ekipeZaZreb(liga)) {
            poId.put(e.getId(), e);
        }
        List<LocalTime> ure = liga.getUreSrecanj();

        // kljuc "kolo" pri kolu kroznega sistema, "kolo|ura" pri ligi z urami
        Map<String, Set<Long>> zasedene = new HashMap<>();
        Map<Integer, Set<Integer>> mestaVKolu = new HashMap<>();
        List<NovoSrecanje> srecanja = new ArrayList<>();
        for (RocniRazporedVnos.ParVnos p : v.srecanja()) {
            if (p.kolo() < 1) {
                throw new NeveljavenVnosIzjema(
                        "Stevilka kola mora biti vsaj 1, prejeta pa je " + p.kolo() + ".");
            }
            Ekipa domaci = ekipaLige(poId, p.idDomaci());
            Ekipa gost = ekipaLige(poId, p.idGost());
            if (domaci.getId().equals(gost.getId())) {
                throw new NeveljavenVnosIzjema(p.kolo() + ". kolo: ekipa "
                        + domaci.prikazanoIme() + " ne more igrati sama s sabo.");
            }

            Set<Integer> mesta = mestaVKolu.computeIfAbsent(p.kolo(), k -> new HashSet<>());
            int mesto = p.mesto() != null ? p.mesto() : mesta.size();
            String kljuc;
            String opis;
            if (ure == null) {
                /* Kolo kroznega sistema je en igralni dan, zato ekipa v njem
                   odigra eno srecanje. Dve bi razdrli lestvico in termin kola. */
                kljuc = String.valueOf(p.kolo());
                opis = " ima dve srecanji, kolo pa je en igralni dan.";
            } else {
                if (mesto < 0 || mesto >= ure.size()) {
                    throw new NeveljavenVnosIzjema(p.kolo() + ". kolo: liga igra " + ure.size()
                            + " srecanj na kolo, srecanje na " + (mesto + 1) + ". mestu nima ure.");
                }
                if (!mesta.add(mesto)) {
                    throw new NeveljavenVnosIzjema(p.kolo() + ". kolo: na " + (mesto + 1)
                            + ". mestu sta vpisani dve srecanji.");
                }
                /* Ekipa sme v kolu igrati veckrat, a ne ob isti uri (dve mizi).
                   Nedolocena ura (00:00) ne trci z nicimer. */
                LocalTime ura = ure.get(mesto);
                kljuc = ura.equals(LocalTime.MIDNIGHT) ? null : p.kolo() + "|" + ura;
                opis = " ima ob " + oblikujUro(ura) + " dve srecanji hkrati.";
            }
            if (kljuc != null) {
                Set<Long> vKljucu = zasedene.computeIfAbsent(kljuc, k -> new HashSet<>());
                for (Ekipa e : List.of(domaci, gost)) {
                    if (!vKljucu.add(e.getId())) {
                        throw new NeveljavenVnosIzjema(p.kolo() + ". kolo: ekipa " + e.prikazanoIme() + opis);
                    }
                }
            }
            if (ure == null) {
                mesta.add(mesto);
            }
            srecanja.add(new NovoSrecanje(new Srecanje(liga, p.kolo(), domaci, gost), mesto));
        }

        /* Kola morajo teci od 1 naprej brez vrzeli: prazno kolo med polnimi je
           bodisi napaka pri prepisu bodisi kolo brez srecanj, ki bi ga razpored
           izpisal kot prazno stran. */
        int najvisje = mestaVKolu.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        for (int kolo = 1; kolo <= najvisje; kolo++) {
            if (!mestaVKolu.containsKey(kolo)) {
                throw new NeveljavenVnosIzjema("Razpored nima " + kolo
                        + ". kola; kola morajo teci od 1 naprej brez vrzeli.");
            }
        }

        zapisiRazpored(liga, srecanja, true);
        /* Preberemo nazaj: DTO potrebuje id-je zapisanih srecanj, poizvedba pa
           ekipe s klubom nalozi vnaprej. */
        return srecanjeRepozitorij.najdiZaLigo(idLiga).stream().map(SrecanjeDto::iz).toList();
    }

    /* Predlog razporeda, kakrsnega bi sestavil zreb - brez zapisa v bazo.

       Vmesnik iz njega dobi OBLIKO lige (koliko kol in koliko srecanj je v
       kolu) za prazno mrezo rocnega vpisa in jo na zahtevo napolni s
       predlaganimi pari, ki jih organizator nato popravi. Racun je isti kot pri
       pravem zrebu, zato pravila razporeda ostanejo na enem mestu in se kopija
       v vmesniku ne more raziti z njimi. */
    @Transactional(readOnly = true)
    public List<ParRazporedaDto> predlogRazporeda(Long idLiga) {
        Liga liga = ligaRepozitorij.findById(idLiga)
                .orElseThrow(() -> new NiNajdenoIzjema("Liga z id " + idLiga + " ne obstaja."));
        List<Ekipa> ekipe = ekipeZaZreb(liga);
        return razporedLige(liga, ekipe.size())
                .stream()
                .map(par -> {
                    Ekipa domaci = ekipe.get(par.domaci());
                    Ekipa gost = ekipe.get(par.gost());
                    return new ParRazporedaDto(par.kolo(), par.mesto(), domaci.getId(),
                            domaci.prikazanoIme(), gost.getId(), gost.prikazanoIme());
                })
                .toList();
    }

    /* Razveljavitev razporeda: srecanja se zbrisejo, liga se vrne v pripravo.

       Rocni vpis je prepis s papirja in v sedemdesetih srecanjih se zatipka -
       popravek mora biti mogoc, ne da bi organizator brisal celo ligo z ekipami
       in kadrom vred. Isto velja za zreb, ki je stekel prezgodaj, zato je
       pravilo eno za oba nacina in ne izjema rocnega vpisa.

       Meja je prvo srecanje, ki se je zacelo: srecanje zunaj stanja RAZPORED
       ima doloceno postavo in generirane tekme (SrecanjeStoritev.nastaviPostavo),
       z njimi pa lahko vpisane rezultate in obracunan rating. Tega izbris
       razporeda ne sme tiho odnesti. */
    @Transactional
    public void razveljaviRazpored(Long idLiga) {
        lastnistvo.preveriLigaPoId(idLiga);
        Liga liga = ligaRepozitorij.findById(idLiga)
                .orElseThrow(() -> new NiNajdenoIzjema("Liga z id " + idLiga + " ne obstaja."));
        List<Srecanje> srecanja = srecanjeRepozitorij.najdiZaLigo(idLiga);
        if (srecanja.isEmpty()) {
            throw new DomenskaIzjema("Ta liga razporeda nima.");
        }
        for (Srecanje s : srecanja) {
            if (s.getStatus() != StatusSrecanja.RAZPORED) {
                throw new DomenskaIzjema("Razporeda ni mogoce razveljaviti: srecanje "
                        + s.getEkipaDomaci().prikazanoIme() + " - " + s.getEkipaGost().prikazanoIme()
                        + " (" + s.getKolo() + ". kolo) se je ze zacelo. Izbris bi odnesel"
                        + " vpisane postave in rezultate.");
            }
        }
        srecanjeRepozitorij.deleteAll(srecanja);
        liga.setRocniZreb(false);
        liga.setStatus(StatusTekmovanja.PRIPRAVA);
        ligaRepozitorij.save(liga);
    }

    /* Liga, pripravljena na dolocitev razporeda: v pripravi in se brez
       srecanj. Pogoj je skupen obema nacinoma - razpored nastane enkrat. */
    private Liga ligaPredZrebom(Long idLiga) {
        lastnistvo.preveriLigaPoId(idLiga);
        Liga liga = ligaRepozitorij.findById(idLiga)
                .orElseThrow(() -> new NiNajdenoIzjema("Liga z id " + idLiga + " ne obstaja."));
        if (liga.getStatus() != StatusTekmovanja.PRIPRAVA) {
            throw new DomenskaIzjema("Razpored je mogoce dolociti samo, ko je liga v pripravi.");
        }
        if (srecanjeRepozitorij.existsByLigaId(idLiga)) {
            throw new DomenskaIzjema("Razpored za to ligo ze obstaja; najprej ga razveljavi.");
        }
        return liga;
    }

    /* Vrstni red ekip je vhod v zreb, zato mora biti dolocen. Pri enakomerni
       razvrstitvi je to jakostna lestvica (indeks 0 = najmocnejsa, po njej se
       sestavijo pari), sicer pa zgolj stabilen vrstni red vpisa - da je
       razpored ponovljiv in ni odvisen od tega, kako baza vrne vrstice. */
    private List<Ekipa> ekipeZaZreb(Liga liga) {
        List<Ekipa> ekipe = new ArrayList<>(liga.isEnakomernaRazvrstitev()
                ? ekipaRepozitorij.najdiZaLigoPoJakosti(liga.getId())
                : ekipaRepozitorij.najdiZaLigo(liga.getId()));
        if (!liga.isEnakomernaRazvrstitev()) {
            ekipe.sort(Comparator.comparing(Ekipa::getId));
        }
        if (ekipe.size() < 2) {
            throw new DomenskaIzjema("Za razpored sta potrebni vsaj dve ekipi.");
        }
        return ekipe;
    }

    /* Vpisana ekipa mora biti prijavljena prav v to ligo - tuja ekipa v
       razporedu bi bila srecanje, ki ga ni mogoce odigrati. */
    private static Ekipa ekipaLige(Map<Long, Ekipa> poId, Long idEkipa) {
        Ekipa ekipa = poId.get(idEkipa);
        if (ekipa == null) {
            throw new NeveljavenVnosIzjema("Ekipa z id " + idEkipa + " ni prijavljena v to ligo.");
        }
        return ekipa;
    }

    /* Srecanje, ki se sele zapisuje, z mestom v kolu - po njem dobi uro. */
    private record NovoSrecanje(Srecanje srecanje, int mesto) {}

    /* Zapis razporeda je sklepni korak obeh nacinov. Sele tu je znano, koliko
       kol liga ima, zato se datumi iz semena (zacetek prvega kola + razmik)
       izracunajo zdaj in ne ob vpisu lige. */
    private void zapisiRazpored(Liga liga, List<NovoSrecanje> nova, boolean rocno) {
        List<Srecanje> srecanja = new ArrayList<>();
        for (NovoSrecanje n : nova) {
            Srecanje s = n.srecanje();
            s.setPredvidenZacetek(terminSrecanja(liga, s.getKolo(), n.mesto()));
            srecanja.add(s);
        }
        srecanjeRepozitorij.saveAll(srecanja);
        liga.setRocniZreb(rocno);
        liga.setStatus(StatusTekmovanja.V_TEKU);
        ligaRepozitorij.save(liga);
    }

    @Transactional(readOnly = true)
    public List<LestvicaEkipeDto> lestvica(Long idLiga) {
        return lestvicaLigeStoritev.lestvica(idLiga);
    }

    /* Lestvici posameznikov in dvojic te lige - obe stejeta samo tekme te lige
       (glej LestvicaLigeStoritev). */
    @Transactional(readOnly = true)
    public List<LestvicaIgralcaLigeDto> lestvicaIgralcev(Long idLiga) {
        return lestvicaLigeStoritev.lestvicaIgralcev(idLiga);
    }

    @Transactional(readOnly = true)
    public List<LestvicaDvojiceDto> lestvicaDvojic(Long idLiga) {
        return lestvicaLigeStoritev.lestvicaDvojic(idLiga);
    }

    // ---------- Pomozno ----------

    private void uporabiVnos(Liga liga, LigaVnos v) {
        if (v.steviloNizov() == null || (v.steviloNizov() != 3 && v.steviloNizov() != 5 && v.steviloNizov() != 7)) {
            throw new NeveljavenVnosIzjema("Stevilo nizov mora biti 3, 5 ali 7.");
        }
        if (v.zmagZaSrecanje() != null) {
            if (v.zmagZaSrecanje() < 1) {
                throw new NeveljavenVnosIzjema("Prag zmag za srecanje mora biti vsaj 1.");
            }
            if (v.zmagZaSrecanje() > v.formatSrecanja().stTekem()) {
                throw new NeveljavenVnosIzjema("Prag zmag za srecanje ne sme presegati stevila tekem ("
                        + v.formatSrecanja().stTekem() + ").");
            }
        }
        if (v.razmikDni() != null && (v.razmikDni() < 1 || v.razmikDni() > 365)) {
            throw new NeveljavenVnosIzjema("Razmik med koli mora biti med 1 in 365 dnevi.");
        }
        if (v.formatSrecanja().samoZaTurnir()) {
            throw new NeveljavenVnosIzjema("Format " + v.formatSrecanja()
                    + " je namenjen ekipnim dogodkom turnirja, ne ligi.");
        }
        preveriKoncnico(v);
        preveriUreSrecanj(v.ureSrecanj());
        liga.setIme(v.ime().trim());
        liga.setSezona(v.sezona() != null && !v.sezona().isBlank() ? v.sezona().trim() : null);
        liga.setSpolKategorija(v.spolKategorija());
        liga.setFormatSrecanja(v.formatSrecanja());
        liga.setSteviloNizov(v.steviloNizov());
        liga.setZmagZaSrecanje(v.zmagZaSrecanje());
        liga.setDvokrozno(v.dvokrozno() == null || v.dvokrozno());
        liga.setTockeZmaga(v.tockeZmaga() != null ? v.tockeZmaga() : 2);
        liga.setTockeNeodloceno(v.tockeNeodloceno() != null ? v.tockeNeodloceno() : 1);
        liga.setTockePoraz(v.tockePoraz() != null ? v.tockePoraz() : 0);
        liga.setDovoljenoNeodloceno(v.dovoljenoNeodloceno() == null || v.dovoljenoNeodloceno());
        liga.setPrepovedDvojneRegistracije(Boolean.TRUE.equals(v.prepovedDvojneRegistracije()));
        liga.setRaven(v.raven() == null ? RavenTekmovanja.KLUBSKO : v.raven());
        liga.setEnakomernaRazvrstitev(Boolean.TRUE.equals(v.enakomernaRazvrstitev()));
        liga.setPredlogaListka(v.predlogaListka() != null ? v.predlogaListka() : PredlogaLige.SNTL_23);
        liga.setUreSrecanj(v.ureSrecanj());
        /* Ura v semenu je ura prvega srecanja kola. Pri ligi z urami je to prva
           ura seznama - poravnamo ju, da seme in ure ne povesta dveh razlicnih
           stvari (seme bere tudi koledar kadra in obrazec lige). */
        liga.setZacetekPrvegaKola(v.zacetekPrvegaKola() != null && liga.getUreSrecanj() != null
                ? v.zacetekPrvegaKola().toLocalDate().atTime(liga.getUreSrecanj().get(0))
                : v.zacetekPrvegaKola());
        /* Razmik brez datuma prvega kola ne pomeni nicesar, datum brez razmika
           pa je najpogostejsi primer (tedenska liga) - zato se privzame teden
           in ne zavrne vnos. */
        liga.setRazmikDni(v.zacetekPrvegaKola() == null
                ? null
                : (v.razmikDni() != null ? v.razmikDni() : PRIVZET_RAZMIK_DNI));
        liga.setKoncnicaEkip(v.koncnicaEkip());
        liga.setKoncnicaZmag(v.koncnicaEkip() == null ? null
                : (v.koncnicaZmag() != null ? v.koncnicaZmag() : PRIVZETE_ZMAGE_V_SERIJI));
    }

    /* Privzeta serija koncnice: na dve zmagi (tako igra 1. SNTL). */
    public static final int PRIVZETE_ZMAGE_V_SERIJI = 2;

    /* Koncnica je pravilo tekmovanja, zato se preveri ob vnosu lige in ne sele
       ob koncu rednega dela - organizator mora vedeti, kaj je liga obljubila.
       Serija potrebuje zmagovalca vsake tekme: srecanje, ki se lahko konca
       neodloceno, bi serijo pustilo brez odlocitve. */
    private static void preveriKoncnico(LigaVnos v) {
        if (v.koncnicaEkip() == null) {
            if (v.koncnicaZmag() != null) {
                throw new NeveljavenVnosIzjema("Stevilo zmag za serijo brez koncnice ne pomeni nicesar.");
            }
            return;
        }
        if (v.koncnicaEkip() != 2 && v.koncnicaEkip() != 4 && v.koncnicaEkip() != 8) {
            throw new NeveljavenVnosIzjema("V koncnici igrajo 2, 4 ali 8 ekip.");
        }
        if (v.koncnicaZmag() != null && (v.koncnicaZmag() < 1 || v.koncnicaZmag() > 4)) {
            throw new NeveljavenVnosIzjema("Serija koncnice traja do 1, 2, 3 ali 4 zmag.");
        }
        boolean sodoTekem = v.formatSrecanja().stTekem() % 2 == 0;
        if (v.zmagZaSrecanje() == null && sodoTekem) {
            throw new NeveljavenVnosIzjema("Liga s koncnico potrebuje srecanje brez neodlocenega izida:"
                    + " nastavi prag zmag za srecanje.");
        }
    }

    /* Privzeti razmik med koli: liga se praviloma igra tedensko. */
    public static final int PRIVZET_RAZMIK_DNI = 7;

    /* Predviden zacetek srecanja po semenu lige: dan kola iz prvega kola in
       razmika, ura iz ure mesta v kolu (liga z urami) oz. iz semena (kolo
       kroznega sistema). Brez semena termina ni. */
    private static LocalDateTime terminSrecanja(Liga liga, int kolo, int mesto) {
        if (liga.getZacetekPrvegaKola() == null) {
            return null;
        }
        int razmik = liga.getRazmikDni() != null ? liga.getRazmikDni() : PRIVZET_RAZMIK_DNI;
        LocalDateTime termin = liga.getZacetekPrvegaKola().plusDays((long) (kolo - 1) * razmik);
        List<LocalTime> ure = liga.getUreSrecanj();
        return ure != null && mesto < ure.size() ? termin.toLocalDate().atTime(ure.get(mesto)) : termin;
    }

    /* Najvec srecanj v kolu lige z urami. Varovalka pred pomoto in ne pravilo:
       vecer z vec kot desetimi srecanji na eni mizi ne obstaja, z vec mizami
       pa je to navadno kolo kroznega sistema. */
    public static final int NAJVEC_SRECANJ_V_KOLU = 10;

    /* Ure srecanj morajo teci naprej: i-to srecanje kola se zacne ob i-ti uri,
       razpored jih izpise po uri, zato bi padajoc seznam pomenil, da prvo
       srecanje kola stoji na dnu. Enaki uri sta dovoljeni (dve mizi hkrati),
       nedolocena ura (00:00) se ne primerja. */
    private static void preveriUreSrecanj(List<LocalTime> ure) {
        if (ure == null) {
            return;
        }
        if (ure.isEmpty() || ure.size() > NAJVEC_SRECANJ_V_KOLU) {
            throw new NeveljavenVnosIzjema("V kolu je lahko od 1 do " + NAJVEC_SRECANJ_V_KOLU + " srecanj.");
        }
        LocalTime prejsnja = null;
        for (LocalTime ura : ure) {
            if (ura == null) {
                throw new NeveljavenVnosIzjema("Vsako srecanje kola potrebuje uro (00:00 = ura ni dolocena).");
            }
            if (ura.equals(LocalTime.MIDNIGHT)) {
                continue;
            }
            if (prejsnja != null && ura.isBefore(prejsnja)) {
                throw new NeveljavenVnosIzjema("Ure srecanj v kolu morajo teci naprej: "
                        + oblikujUro(ura) + " je pred " + oblikujUro(prejsnja) + ".");
            }
            prejsnja = ura;
        }
    }

    /* Povezave "visja liga" morajo tvoriti drevo. Ce se pri hoji navzgor
       vrnemo na ze videno ligo, je nastal krog - baza vidi le najkrajsega
       (liga sama sebi), daljse mora ujeti koda, sicer se izris piramide
       zavrti v neskoncnost. */
    private void preveriBrezKroga(Liga zacetek) {
        Set<Long> videne = new HashSet<>();
        for (Liga t = zacetek; t != null; t = t.getVisjaLiga()) {
            if (!videne.add(t.getId())) {
                throw new NeveljavenVnosIzjema(
                        "Tako bi se lige povezale v krog - preveri, katera je visja in katera nizja.");
            }
        }
    }

    private void preveriVPripravi(Liga liga) {
        if (liga.getStatus() != StatusTekmovanja.PRIPRAVA) {
            throw new DomenskaIzjema("Ekipe in kader je mogoce spreminjati samo, dokler je liga v pripravi.");
        }
    }

    /* Prve proste zaporedne stevilke kluba med ze prijavljenimi ekipami lige.
       Proste ekipe se ne stejejo - te kluba nimajo. */
    private static int naslednjaZaporedna(List<Ekipa> obstojece, Long idKlub) {
        int najvecja = obstojece.stream()
                .filter(e -> !e.jeProsta() && e.getKlub().getId().equals(idKlub))
                .mapToInt(Ekipa::getZaporedna)
                .max().orElse(0);
        return najvecja + 1;
    }
}
