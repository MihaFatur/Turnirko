/* Obvestilo po kolu lige po e-posti (V50, odlocitev lastnika 23. 10. 2026).

   Zakaj: vecina gledalcev pride pogledat izide svoje lige in odide - racun jim
   ni dal razloga za vrnitev. Po vsakem odigranem kolu zato racun dobi izide
   kola, lestvico in (ce je v kolu igral) svoje tekme s spremembo ratinga, s
   povezavo nazaj v ligo. Brezplacnemu igralcu sporocilo pove, kaj bi mu
   pokazal Premium.

   Kdaj je kolo zrelo (zrelo):
   - vsa srecanja kola (redni del, brez srecanj, ki cakajo na termin) so
     koncana ALI je koncanih vsaj pol in je od dneva kola minilo DNI_ZA_DELNO
     dni - v ligi brez vpisanega izida enega srecanja sicer ne bi slo nikoli;
   - zadnji izid kola je vpisan vsaj MIRUJE pred zdaj (organizator med vnosom
     vecera popravlja) in ne pred vec kot SVEZE (staro kolo ni vec novica -
     to izloci tudi uvozeno zgodovino brez casa vnosa);
   - kolo se ni obdelano (obvestilo_kola) in nobeno novejse kolo lige se ni
     bilo obdelano - kolo, ki se dopolni za novejsim, se preskoci.
   Vec zrelih kol iste lige naenkrat (po izpadu streznika) da eno sporocilo
   za najnovejse; starejsa se zabelezijo kot preskocena.

   Komu: racunom z igralcem v kadru katere koli ekipe lige (IGRA) in racunom,
   ki ligo spremljajo (SPREMLJA) - vsakemu enkrat. Samo aktiven racun s
   potrjenim naslovom (in skrbnikom), ki obvestil ni izklopil. Vrstice o
   Premium ne dobi otrok (racun z naslovom skrbnika) - isto pravilo kot
   obvestilo o koncu preizkusa. */
package si.turnirko.storitve;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.LestvicaEkipeDto;
import si.turnirko.dto.ObvestilaDto;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.izjeme.PrepovedanoIzjema;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Liga;
import si.turnirko.modeli.Narocnina;
import si.turnirko.modeli.ObvestiloKola;
import si.turnirko.modeli.Paket;
import si.turnirko.modeli.RatingStanje;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.StatusSrecanja;
import si.turnirko.modeli.StatusTekmeSrecanja;
import si.turnirko.modeli.StatusTekmovanja;
import si.turnirko.modeli.Srecanje;
import si.turnirko.modeli.TekmaSrecanja;
import si.turnirko.modeli.TipTekmeSrecanja;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.posta.PostaStoritev;
import si.turnirko.posta.SporociloKola;
import si.turnirko.repozitoriji.KaderEkipeRepozitorij;
import si.turnirko.repozitoriji.LigaRepozitorij;
import si.turnirko.repozitoriji.ObvestiloKolaRepozitorij;
import si.turnirko.repozitoriji.RatingStanjeRepozitorij;
import si.turnirko.repozitoriji.RatingZgodovinaRepozitorij;
import si.turnirko.repozitoriji.SpremljanaLigaRepozitorij;
import si.turnirko.repozitoriji.SrecanjeRepozitorij;
import si.turnirko.repozitoriji.TekmaSrecanjaRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

@Service
public class ObvestilaPoKoluStoritev {

    private static final Logger dnevnik = LoggerFactory.getLogger(ObvestilaPoKoluStoritev.class);

    /* Cas izida in termin srecanja sta zapisana po slovenskem casu. */
    private static final ZoneId SLOVENIJA = ZoneId.of("Europe/Ljubljana");

    static final Duration MIRUJE = Duration.ofHours(2);
    static final Duration SVEZE = Duration.ofDays(7);
    static final int DNI_ZA_DELNO = 4;

    /* Lestvica v sporocilu: cela do te dolzine, sicer vrh in okolica ekipe. */
    private static final int CELA_LESTVICA_DO = 12;
    private static final int VRH_LESTVICE = 8;

    private static final DateTimeFormatter DATUM = DateTimeFormatter.ofPattern("d. M. yyyy");
    private static final SecureRandom NAKLJUCJE = new SecureRandom();

    private final SrecanjeRepozitorij srecanjeRepozitorij;
    private final LigaRepozitorij ligaRepozitorij;
    private final ObvestiloKolaRepozitorij obvestiloKolaRepozitorij;
    private final UporabnikRepozitorij uporabnikRepozitorij;
    private final SpremljanaLigaRepozitorij spremljanaLigaRepozitorij;
    private final TekmaSrecanjaRepozitorij tekmaSrecanjaRepozitorij;
    private final RatingZgodovinaRepozitorij ratingZgodovinaRepozitorij;
    private final RatingStanjeRepozitorij ratingStanjeRepozitorij;
    private final KaderEkipeRepozitorij kaderRepozitorij;
    private final LestvicaLigeStoritev lestvicaLigeStoritev;
    private final NarocninaStoritev narocnina;
    private final LastnistvoStoritev lastnistvo;
    private final PostaStoritev posta;
    private final String koren;

    public ObvestilaPoKoluStoritev(SrecanjeRepozitorij srecanjeRepozitorij,
                                   LigaRepozitorij ligaRepozitorij,
                                   ObvestiloKolaRepozitorij obvestiloKolaRepozitorij,
                                   UporabnikRepozitorij uporabnikRepozitorij,
                                   SpremljanaLigaRepozitorij spremljanaLigaRepozitorij,
                                   TekmaSrecanjaRepozitorij tekmaSrecanjaRepozitorij,
                                   RatingZgodovinaRepozitorij ratingZgodovinaRepozitorij,
                                   RatingStanjeRepozitorij ratingStanjeRepozitorij,
                                   KaderEkipeRepozitorij kaderRepozitorij,
                                   LestvicaLigeStoritev lestvicaLigeStoritev,
                                   NarocninaStoritev narocnina,
                                   LastnistvoStoritev lastnistvo,
                                   PostaStoritev posta,
                                   @Value("${turnirko.stripe.vrni-na}") String koren) {
        this.srecanjeRepozitorij = srecanjeRepozitorij;
        this.ligaRepozitorij = ligaRepozitorij;
        this.obvestiloKolaRepozitorij = obvestiloKolaRepozitorij;
        this.uporabnikRepozitorij = uporabnikRepozitorij;
        this.spremljanaLigaRepozitorij = spremljanaLigaRepozitorij;
        this.tekmaSrecanjaRepozitorij = tekmaSrecanjaRepozitorij;
        this.ratingZgodovinaRepozitorij = ratingZgodovinaRepozitorij;
        this.ratingStanjeRepozitorij = ratingStanjeRepozitorij;
        this.kaderRepozitorij = kaderRepozitorij;
        this.lestvicaLigeStoritev = lestvicaLigeStoritev;
        this.narocnina = narocnina;
        this.lastnistvo = lastnistvo;
        this.posta = posta;
        this.koren = koren;
    }

    // ---------- Posiljanje ----------

    /* Vsako uro med 8.20 in 20.20 po slovenskem casu: obvestilo naj pride
       podnevi; kolo, vpisano pozno zvecer, gre zjutraj. Urnik je nastavljiv
       (turnirko.obvestila.urnik) samo zato, da ga razvoj lahko pozene takoj.
       Transakcija mora biti TU: klic poslji() iz istega razreda gre mimo
       Springovega posrednika, zato bi brez nje zeton odjave ostal nezapisan. */
    @Transactional
    @Scheduled(cron = "${turnirko.obvestila.urnik:0 20 8-20 * * *}", zone = "Europe/Ljubljana")
    public void urnoPreverjanje() {
        poslji(LocalDateTime.now(SLOVENIJA));
    }

    /* Obdela vse zrele kole; vrne stevilo poslanih sporocil. */
    @Transactional
    public int poslji(LocalDateTime zdaj) {
        int poslanih = 0;
        for (Long idLiga : srecanjeRepozitorij.ligeZVnosomOd(zdaj.minus(SVEZE))) {
            poslanih += obdelajLigo(idLiga, zdaj);
        }
        if (poslanih > 0) {
            dnevnik.info("Obvestila po kolu: {} sporocil.", poslanih);
        }
        return poslanih;
    }

    private int obdelajLigo(Long idLiga, LocalDateTime zdaj) {
        LocalDateTime ob = zdaj.withNano(0);
        Liga liga = ligaRepozitorij.findById(idLiga).orElse(null);
        if (liga == null) {
            return 0;
        }
        List<Srecanje> redna = srecanjeRepozitorij.najdiRednaZaLigo(idLiga);
        Map<Integer, List<Srecanje>> poKolih = redna.stream()
                .filter(s -> !s.isCakaNaTermin())
                .collect(Collectors.groupingBy(Srecanje::getKolo, TreeMap::new, Collectors.toList()));
        Set<Integer> obdelana = new HashSet<>(obvestiloKolaRepozitorij.obdelanaKola(idLiga));
        int zadnjeObdelano = obdelana.stream().mapToInt(Integer::intValue).max().orElse(0);

        List<Integer> zrela = new ArrayList<>();
        for (var kolo : poKolih.entrySet()) {
            if (!obdelana.contains(kolo.getKey()) && zrelo(kolo.getValue(), zdaj)) {
                zrela.add(kolo.getKey());
            }
        }
        if (zrela.isEmpty()) {
            return 0;
        }
        int najnovejse = zrela.get(zrela.size() - 1);
        for (int kolo : zrela) {
            if (kolo != najnovejse || kolo < zadnjeObdelano) {
                obvestiloKolaRepozitorij.save(new ObvestiloKola(idLiga, kolo, ob, 0, true));
            }
        }
        if (najnovejse < zadnjeObdelano) {
            return 0;
        }
        int poslanih = posljiKolo(liga, najnovejse, poKolih.get(najnovejse), redna, zdaj);
        obvestiloKolaRepozitorij.save(new ObvestiloKola(idLiga, najnovejse, ob, poslanih, false));
        return poslanih;
    }

    static boolean zrelo(List<Srecanje> kolo, LocalDateTime zdaj) {
        LocalDateTime zadnjiVnos = kolo.stream()
                .map(Srecanje::getOdigranOb).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);
        if (zadnjiVnos == null || zadnjiVnos.isAfter(zdaj.minus(MIRUJE))
                || zadnjiVnos.isBefore(zdaj.minus(SVEZE))) {
            return false;
        }
        long koncanih = kolo.stream().filter(s -> s.getStatus() == StatusSrecanja.KONCANO).count();
        if (koncanih == kolo.size()) {
            return true;
        }
        LocalDate dan = dan(kolo);
        return dan != null && koncanih * 2 >= kolo.size()
                && !zdaj.toLocalDate().isBefore(dan.plusDays(DNI_ZA_DELNO));
    }

    /* Dan kola: srecanja, ki v kolo sodijo po zrebu (prestavljeno ima lahko
       svoj dan); ce so vsa prestavljena, katerokoli. */
    private static LocalDate dan(List<Srecanje> kolo) {
        return kolo.stream()
                .sorted(Comparator.comparing(Srecanje::jePremaknjenoVDrugoKolo))
                .map(Srecanje::getPredvidenZacetek).filter(Objects::nonNull)
                .map(LocalDateTime::toLocalDate)
                .findFirst().orElse(null);
    }

    private int posljiKolo(Liga liga, int kolo, List<Srecanje> srecanja, List<Srecanje> redna,
                           LocalDateTime zdaj) {
        Map<Long, Uporabnik> igralci = new LinkedHashMap<>();
        for (Uporabnik u : uporabnikRepozitorij.najdiIgralceLige(liga.getId())) {
            if (primeren(u) && u.getStatus() == StatusRacuna.POTRJEN) {
                igralci.put(u.getId(), u);
            }
        }
        Map<Long, Uporabnik> spremljevalci = new LinkedHashMap<>();
        for (Uporabnik u : uporabnikRepozitorij.najdiSpremljevalceLige(liga.getId())) {
            if (primeren(u) && !igralci.containsKey(u.getId())) {
                spremljevalci.put(u.getId(), u);
            }
        }
        if (igralci.isEmpty() && spremljevalci.isEmpty()) {
            return 0;
        }

        Skupno skupno = skupno(liga, kolo, srecanja, redna);
        int poslanih = 0;
        for (Uporabnik u : igralci.values()) {
            poslji(u, SporociloKola.Razlog.IGRA, skupno, srecanja, kolo, zdaj);
            poslanih++;
        }
        for (Uporabnik u : spremljevalci.values()) {
            poslji(u, SporociloKola.Razlog.SPREMLJA, skupno, srecanja, kolo, zdaj);
            poslanih++;
        }
        return poslanih;
    }

    private static boolean primeren(Uporabnik u) {
        return u.isAktiven() && u.isObvestilaPoKolu() && u.jeEmailPotrjen() && !u.cakaSkrbnika()
                && u.getStatus() != StatusRacuna.ZAVRNJEN
                && u.getUporabniskoIme() != null && u.getUporabniskoIme().contains("@");
    }

    /* Kar je skupno vsem prejemnikom kola. */
    private record Skupno(Liga liga, List<LestvicaEkipeDto> lestvica, LocalDate dan, PotekLige.Potek potek) {}

    private Skupno skupno(Liga liga, int kolo, List<Srecanje> srecanja, List<Srecanje> redna) {
        List<PotekLige.Termin> termini = redna.stream()
                .map(s -> new PotekLige.Termin(s.getKolo(), s.getStatus(), s.getPredvidenZacetek(),
                        false, s.jePremaknjenoVDrugoKolo(), s.isCakaNaTermin()))
                .toList();
        return new Skupno(liga, lestvicaLigeStoritev.lestvica(liga.getId()), dan(srecanja),
                PotekLige.izracunaj(termini, PotekLige.danes()));
    }

    private void poslji(Uporabnik u, SporociloKola.Razlog razlog, Skupno s, List<Srecanje> srecanja,
                        int kolo, LocalDateTime zdaj) {
        if (u.getZetonOdjave() == null) {
            u.setZetonOdjave(novZeton());
        }
        Set<Long> mojeEkipe = u.getIgralec() == null
                ? Set.of()
                : new HashSet<>(kaderRepozitorij.ekipeIgralcaVLigi(s.liga().getId(), u.getIgralec().getId()));

        List<SporociloKola.Izid> izidi = srecanja.stream().map(sr -> izid(sr, mojeEkipe)).toList();
        PotekLige.Naslednje naslednje = s.potek().naslednje();
        SporociloKola.Kolo k = new SporociloKola.Kolo(
                s.liga().getId(), s.liga().getIme(), s.liga().getSezona(), kolo, s.dan(),
                s.potek().odigranih(), s.potek().vseh(), izidi, vrsticeLestvice(s.lestvica(), mojeEkipe),
                s.lestvica().size(),
                naslednje == null ? null : naslednje.kolo(), naslednje == null ? null : naslednje.datum());

        SporociloKola.Osebno osebno = mojeEkipe.isEmpty()
                ? null
                : osebno(u.getIgralec(), mojeEkipe, srecanja, s.lestvica());
        String odjava = koren + "/odjava-obvestil?z=" + u.getZetonOdjave();
        String odjavaApi = koren + "/api/v1/obvestila/odjava?z=" + u.getZetonOdjave();
        SporociloKola.Prejemnik p = new SporociloKola.Prejemnik(
                razlog, osebno, vrsticaPremium(u, zdaj), odjava, odjavaApi, koren);
        posta.poslji(u.getUporabniskoIme(), SporociloKola.sestavi(k, p));
    }

    private static SporociloKola.Izid izid(Srecanje s, Set<Long> mojeEkipe) {
        boolean koncano = s.getStatus() == StatusSrecanja.KONCANO;
        return new SporociloKola.Izid(
                s.getEkipaDomaci().prikazanoIme(), s.getEkipaGost().prikazanoIme(),
                koncano ? s.getDobljeneDomaci() : null, koncano ? s.getDobljeneGost() : null,
                s.isBrezBoja(),
                mojeEkipe.contains(s.getEkipaDomaci().getId()) || mojeEkipe.contains(s.getEkipaGost().getId()));
    }

    /* Cela lestvica do CELA_LESTVICA_DO ekip; daljsa vrh in moja ekipa s
       sosedama (enako okno kot na domaci strani). */
    static List<SporociloKola.Vrstica> vrsticeLestvice(List<LestvicaEkipeDto> lestvica, Set<Long> mojeEkipe) {
        List<LestvicaEkipeDto> izbrane;
        if (lestvica.size() <= CELA_LESTVICA_DO) {
            izbrane = lestvica;
        } else {
            izbrane = new ArrayList<>(lestvica.subList(0, VRH_LESTVICE));
            for (int i = 0; i < lestvica.size(); i++) {
                if (mojeEkipe.contains(lestvica.get(i).idEkipa())) {
                    for (LestvicaEkipeDto v : DomovStoritev.okno(lestvica, i, 3)) {
                        if (!izbrane.contains(v)) {
                            izbrane.add(v);
                        }
                    }
                    break;
                }
            }
        }
        return izbrane.stream()
                .map(v -> new SporociloKola.Vrstica(v.mesto(), v.ekipa(), v.odigrane(), v.tocke(),
                        mojeEkipe.contains(v.idEkipa())))
                .toList();
    }

    private SporociloKola.Osebno osebno(Igralec jaz, Set<Long> mojeEkipe, List<Srecanje> srecanja,
                                        List<LestvicaEkipeDto> lestvica) {
        // srecanja ekipe v kolu - pri ligi z urami kola (V31) jih je vec
        List<Srecanje> moja = srecanja.stream()
                .filter(s -> mojeEkipe.contains(s.getEkipaDomaci().getId())
                        || mojeEkipe.contains(s.getEkipaGost().getId()))
                .toList();
        Srecanje prvo = moja.isEmpty() ? null : moja.get(0);
        Long idEkipa = prvo == null
                ? mojeEkipe.iterator().next()
                : (mojeEkipe.contains(prvo.getEkipaDomaci().getId())
                        ? prvo.getEkipaDomaci().getId() : prvo.getEkipaGost().getId());
        String imeEkipe = prvo != null
                ? (prvo.getEkipaDomaci().getId().equals(idEkipa) ? prvo.getEkipaDomaci() : prvo.getEkipaGost())
                        .prikazanoIme()
                : lestvica.stream().filter(v -> v.idEkipa().equals(idEkipa))
                        .map(LestvicaEkipeDto::ekipa).findFirst().orElse(null);

        List<SporociloKola.Tekma> tekme = new ArrayList<>();
        Integer spremembaKola = null;
        for (Srecanje s : moja) {
            List<TekmaSrecanja> vse = tekmaSrecanjaRepozitorij.najdiZaSrecanje(s.getId());
            Map<Long, Integer> spremembe = spremembeIgralca(vse, jaz.getId());
            for (TekmaSrecanja t : vse) {
                SporociloKola.Tekma tekma = tekma(t, jaz, spremembe.get(t.getId()));
                if (tekma != null) {
                    tekme.add(tekma);
                }
            }
            for (int sprememba : spremembe.values()) {
                spremembaKola = (spremembaKola == null ? 0 : spremembaKola) + sprememba;
            }
        }
        Integer rating = tekme.isEmpty() ? null
                : ratingStanjeRepozitorij.findByIgralecIdAndSistem(jaz.getId(), RatingStanje.SISTEM_TURNIRKO)
                        .map(RatingStanje::getVrednost).orElse(null);

        return new SporociloKola.Osebno(idEkipa, imeEkipe, moja.stream().map(s -> izid(s, mojeEkipe)).toList(),
                tekme, rating, spremembaKola);
    }

    /* Spremembe ratinga igralca po tekmah srecanja (samo posamicne tekme jih
       imajo). */
    private Map<Long, Integer> spremembeIgralca(List<TekmaSrecanja> tekme, Long idIgralec) {
        List<Long> idji = tekme.stream()
                .filter(t -> t.getTip() == TipTekmeSrecanja.POSAMICNA && t.getStatus() == StatusTekmeSrecanja.KONCANA)
                .map(TekmaSrecanja::getId).toList();
        Map<Long, Integer> spremembe = new HashMap<>();
        if (idji.isEmpty()) {
            return spremembe;
        }
        for (Object[] v : ratingZgodovinaRepozitorij.spremembeZaTekmeSrecanja(idji, RatingStanje.SISTEM_TURNIRKO)) {
            if (idIgralec.equals(v[1])) {
                spremembe.merge((Long) v[0], ((Number) v[2]).intValue(), Integer::sum);
            }
        }
        return spremembe;
    }

    /* Tekma z vidika igralca; null, ce je ni igral ali ni odigrana. Pri dvojici
       je njegov soigralec zapisan skupaj z njim (z istega para). */
    private static SporociloKola.Tekma tekma(TekmaSrecanja t, Igralec jaz, Integer sprememba) {
        if (t.getStatus() != StatusTekmeSrecanja.KONCANA || t.getZmagovalecStran() == null) {
            return null;
        }
        Long idJaz = jaz.getId();
        boolean doma = idJaz.equals(id(t.getIgralecDomaci())) || idJaz.equals(id(t.getIgralecDomaci2()));
        boolean gost = idJaz.equals(id(t.getIgralecGost())) || idJaz.equals(id(t.getIgralecGost2()));
        if (!doma && !gost) {
            return null;
        }
        boolean dvojice = t.getTip() == TipTekmeSrecanja.DVOJICE;
        Igralec soigralec = null;
        if (dvojice) {
            soigralec = doma
                    ? soigralecIz(t.getIgralecDomaci(), t.getIgralecDomaci2(), idJaz)
                    : soigralecIz(t.getIgralecGost(), t.getIgralecGost2(), idJaz);
        }
        String moj = dvojice ? imena(jaz, soigralec) : jaz.polnoIme();
        String nasprotnik = doma
                ? imena(t.getIgralecGost(), dvojice ? t.getIgralecGost2() : null)
                : imena(t.getIgralecDomaci(), dvojice ? t.getIgralecDomaci2() : null);
        int moji = doma ? t.getDobljeniNiziDomaci() : t.getDobljeniNiziGost();
        int njegovi = doma ? t.getDobljeniNiziGost() : t.getDobljeniNiziDomaci();
        boolean zmaga = (t.getZmagovalecStran() == si.turnirko.modeli.StranEkipe.DOMACI) == doma;
        return new SporociloKola.Tekma(moj, nasprotnik, moji, njegovi, zmaga, sprememba);
    }

    /* Soigralec v paru: drugi igralec, ce je prvi jaz, sicer prvi. */
    private static Igralec soigralecIz(Igralec prvi, Igralec drugi, Long idJaz) {
        return idJaz.equals(id(prvi)) ? drugi : prvi;
    }

    private static Long id(Igralec i) {
        return i == null ? null : i.getId();
    }

    private static String imena(Igralec prvi, Igralec drugi) {
        String ime = prvi == null ? "?" : prvi.polnoIme();
        return drugi == null ? ime : ime + " / " + drugi.polnoIme();
    }

    /* Vrstica o Premium: igralcu brez paketa ponudba (brez cene - ta je na
       strani naročnine), v preizkusu datum konca. Otrok (naslov skrbnika),
       placnik in racun brez igralca je ne dobijo. */
    private String vrsticaPremium(Uporabnik u, LocalDateTime zdaj) {
        if (u.getVloga() != Vloga.IGRALEC || !u.jePotrjenIgralec() || u.getEmailSkrbnika() != null) {
            return null;
        }
        Optional<Narocnina> veljavna = narocnina.veljavna(u);
        if (veljavna.isPresent() && veljavna.get().getPaket() == Paket.PREMIUM) {
            Narocnina n = veljavna.get();
            if (!n.jePreizkus() || n.getTrenutnoObdobjeDo() == null) {
                return null;
            }
            return "Premium imaš brez plačila do "
                    + UpravljanjeNarocnineStoritev.dan(n.getTrenutnoObdobjeDo()).format(DATUM)
                    + ": na profilu vidiš svojo formo, nasprotnike in napoved naslednje tekme.";
        }
        return "S Premium na profilu vidiš še svojo formo, nasprotnike, napoved naslednje tekme ter nize in točke";
    }

    private static String novZeton() {
        byte[] bajti = new byte[24];
        NAKLJUCJE.nextBytes(bajti);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bajti);
    }

    // ---------- Nastavitev racuna ----------

    /* Ali racun zeli obvestila in za katere lige bi jih dobil (nezakljucene:
       v katerih igra in ki jih spremlja). */
    @Transactional(readOnly = true)
    public ObvestilaDto nastavitve() {
        Uporabnik jaz = zahtevajPrijavo();
        Map<Long, ObvestilaDto.Liga> lige = new LinkedHashMap<>();
        if (jaz.jePotrjenIgralec()) {
            for (Liga l : ligaRepozitorij.najdiNezakljuceneZaIgralca(jaz.getIgralec().getId())) {
                lige.put(l.getId(), new ObvestilaDto.Liga(l.getId(), l.getIme(), l.getSezona(), "IGRA"));
            }
        }
        for (Long id : spremljanaLigaRepozitorij.idjiZaRacun(jaz.getId())) {
            if (lige.containsKey(id)) {
                continue;
            }
            ligaRepozitorij.findById(id)
                    .filter(l -> l.getStatus() != StatusTekmovanja.ZAKLJUCEN)
                    .ifPresent(l -> lige.put(l.getId(),
                            new ObvestilaDto.Liga(l.getId(), l.getIme(), l.getSezona(), "SPREMLJA")));
        }
        return new ObvestilaDto(jaz.isObvestilaPoKolu(), jaz.jeEmailPotrjen(), List.copyOf(lige.values()));
    }

    @Transactional
    public ObvestilaDto nastavi(boolean poKolu) {
        zahtevajPrijavo().setObvestilaPoKolu(poKolu);
        return nastavitve();
    }

    /* Odjava iz e-poste brez prijave. Zeton ostane (ponovna odjava iz starega
       sporocila je ista operacija). */
    @Transactional
    public void odjava(String zeton) {
        if (zeton == null || zeton.isBlank()) {
            throw new NiNajdenoIzjema("Povezava za odjavo ni veljavna.");
        }
        Uporabnik u = uporabnikRepozitorij.findByZetonOdjave(zeton.trim())
                .orElseThrow(() -> new NiNajdenoIzjema("Povezava za odjavo ni veljavna."));
        u.setObvestilaPoKolu(false);
    }

    private Uporabnik zahtevajPrijavo() {
        Uporabnik jaz = lastnistvo.trenutni();
        if (jaz == null) {
            throw new PrepovedanoIzjema("Za nastavitve obvestil je potrebna prijava.");
        }
        return jaz;
    }
}
