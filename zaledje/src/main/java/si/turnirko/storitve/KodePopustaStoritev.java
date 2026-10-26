/* Kode popusta ob registraciji (V53, odlocitev lastnika okt 2026): admin
   nastavi kodo in ugodnost, oseba jo vpise ob ustvarjanju racuna.

   Tri vrste ugodnosti (VrstaKodePopusta), vsaka velja na svoji poti
   registracije:
   - PREMIUM_MESECI: brezplacna registracija igralca; preizkus Premium traja
     toliko mesecev namesto enega (PreizkusPremiumStoritev.zacni);
   - ODSTOTEK: placljiva registracija (Premium ali organizator); popust na
     PRVO placilo prek Stripovega kupona, obnova je po polni ceni;
   - ORGANIZATOR_MESECI: registracija organizatorja brez placila; paket za
     toliko mesecev od potrditve organizatorja (uveljaviZaOrganizatorja).

   Pravila, ki jih ne razbij:
   - Koda se na poti, na kateri ne deluje, ZAVRNE z razlago, ne tiho prezre:
     kdor vpise kodo, pricakuje ugodnost, in ce je ne dobi, ne ve zakaj.
     Vrsta doloca pot (razlogNeuporabnosti).
   - Odgovor ne razkriva, katere kode obstajajo: neznana, ugasnjena, potekla
     in izcrpana koda dobijo isto sporocilo (NEVELJAVNA_KODA). Ugibanje je
     omejeno po naslovu IP (omeji) - promocijske kode so po naravi bolj
     ugibljive od potrditvene kode, zato je meja nizka.
   - En racun uporabi najvec eno kodo (uporaba_kode_popusta.id_uporabnik je
     UNIQUE). Mesto pri najvec uporab drzi racun (prosta registracija) ali
     cakajoca placljiva registracija (od zacetka placila do webhooka oz. 4 dni),
     zato se meja ne more prekoraciti med dvema sejama.
   - Ugodnost kode se po nastanku ne spreminja (KodaPopusta): kdor se je
     registriral, je dobil, kar je koda takrat obljubljala. */
package si.turnirko.storitve;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.KodaPopustaDto;
import si.turnirko.dto.KodaPopustaUrediVnos;
import si.turnirko.dto.KodaPopustaVnos;
import si.turnirko.dto.PreveriKodoVnos;
import si.turnirko.dto.PreverjenaKodaDto;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.izjeme.PrevecZahtevIzjema;
import si.turnirko.modeli.CiklusPlacila;
import si.turnirko.modeli.KodaPopusta;
import si.turnirko.modeli.Narocnina;
import si.turnirko.modeli.Paket;
import si.turnirko.modeli.StatusNarocnine;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.UporabaKodePopusta;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.VrstaKodePopusta;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.CakajocaRegistracijaRepozitorij;
import si.turnirko.repozitoriji.KodaPopustaRepozitorij;
import si.turnirko.repozitoriji.NarocninaRepozitorij;
import si.turnirko.repozitoriji.UporabaKodePopustaRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

@Service
public class KodePopustaStoritev {

    /* Isto sporocilo za neznano, ugasnjeno, potekleo in izcrpano kodo - drugace bi
       odgovor povedal, katere kode obstajajo. */
    public static final String NEVELJAVNA_KODA = "Koda ni veljavna ali je potekla.";

    /* Najvec preverjanj kode z enega naslova IP na uro (predogled in registracija
       skupaj). Posten obiskovalec preveri eno ali dve kodi; ugibanje zadetka pa
       s tem postane nepraktično. */
    static final int NAJVEC_PREVERJANJ_NA_URO = 30;
    private static final Duration URA = Duration.ofHours(1);

    static final int NAJVEC_MESECEV = 36;

    /* Meja odstotka: Stripe ne sprejme placila pod 0,50 EUR, najcenejsi placljivi
       paket (Premium mesecno za U21) pa stane 1,99 EUR - pri 75 % je prvo
       placilo 0,50 EUR. Kdor zeli vec, uporabi kodo z meseci brez placila. */
    static final int NAJVEC_ODSTOTEK = 75;
    static final long NAJMANJSE_PLACILO_CENTOV = 50;

    /* Brez znakov, ki se pri prepisovanju zamenjajo (0/O, 1/I). */
    private static final String ABECEDA_KODE = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int DOLZINA_NAKLJUCNE_KODE = 8;
    private static final Pattern OBLIKA_KODE = Pattern.compile("[A-Z0-9][A-Z0-9-]{2,31}");
    private static final SecureRandom NAKLJUCJE = new SecureRandom();

    /* Pot registracije, na kateri se koda uporabi: placljiva gre prek Stripe
       Checkouta, brezplacna nastane takoj. */
    enum Pot { BREZPLACNA, PLACLJIVA }

    private final KodaPopustaRepozitorij kode;
    private final UporabaKodePopustaRepozitorij uporabe;
    private final CakajocaRegistracijaRepozitorij cakajoce;
    private final NarocninaRepozitorij narocnine;
    private final UporabnikRepozitorij uporabniki;
    private final CenikStoritev cenik;
    private final OmejevalnikPoskusov omejevalnik;
    private final StripeNarocnine stripe;
    private final RacunDnevnikStoritev racunDnevnik;

    public KodePopustaStoritev(KodaPopustaRepozitorij kode,
                               UporabaKodePopustaRepozitorij uporabe,
                               CakajocaRegistracijaRepozitorij cakajoce,
                               NarocninaRepozitorij narocnine,
                               UporabnikRepozitorij uporabniki,
                               CenikStoritev cenik,
                               OmejevalnikPoskusov omejevalnik,
                               StripeNarocnine stripe,
                               RacunDnevnikStoritev racunDnevnik) {
        this.kode = kode;
        this.uporabe = uporabe;
        this.cakajoce = cakajoce;
        this.narocnine = narocnine;
        this.uporabniki = uporabniki;
        this.cenik = cenik;
        this.omejevalnik = omejevalnik;
        this.stripe = stripe;
        this.racunDnevnik = racunDnevnik;
    }

    // ---------- Administracija ----------

    @Transactional(readOnly = true)
    public List<KodaPopustaDto> seznam() {
        Map<Long, Long> poRacunih = steviloPoKodah(uporabe.steviloPoKodah());
        Map<Long, Long> cakajoceKode = steviloPoKodah(cakajoce.steviloPoKodah());
        LocalDate danes = LocalDate.now();
        return kode.findAllByOrderByIdDesc().stream()
                .map(k -> KodaPopustaDto.iz(k, poRacunih.getOrDefault(k.getId(), 0L),
                        cakajoceKode.getOrDefault(k.getId(), 0L), danes))
                .toList();
    }

    @Transactional
    public KodaPopustaDto ustvari(KodaPopustaVnos v) {
        String koda = prazno(v.koda()) ? nakljucnaKoda() : preverjenaOblika(normaliziraj(v.koda()));
        if (kode.existsByKoda(koda)) {
            throw new DomenskaIzjema("Koda " + koda + " že obstaja.");
        }
        KodaPopusta k = new KodaPopusta(koda, v.vrsta(), LocalDateTime.now());
        k.setOpis(prazno(v.opis()) ? null : v.opis().trim());
        switch (v.vrsta()) {
            case PREMIUM_MESECI -> k.setMeseci(preverjeniMeseci(v.meseci()));
            case ORGANIZATOR_MESECI -> {
                k.setMeseci(preverjeniMeseci(v.meseci()));
                if (v.paket() == null || !v.paket().jeOrganizatorski()) {
                    throw new NeveljavenVnosIzjema("Izberi organizatorski paket (Basic, Plus ali Pro).");
                }
                k.setPaket(v.paket());
            }
            case ODSTOTEK -> {
                if (v.odstotek() == null || v.odstotek() < 1 || v.odstotek() > NAJVEC_ODSTOTEK) {
                    throw new NeveljavenVnosIzjema("Popust je lahko od 1 do " + NAJVEC_ODSTOTEK
                            + " %. Za več uporabi kodo z meseci brez plačila.");
                }
                if (v.zaVlogo() != null && v.zaVlogo() != Vloga.IGRALEC && v.zaVlogo() != Vloga.ORGANIZATOR) {
                    throw new NeveljavenVnosIzjema("Koda lahko velja za igralce ali za organizatorje.");
                }
                k.setOdstotek(v.odstotek());
                k.setZaVlogo(v.zaVlogo());
            }
        }
        preveriRok(v.veljaDo(), true);
        k.setVeljaDo(v.veljaDo());
        k.setNajvecUporab(preverjenoStevilo(v.najvecUporab()));
        return dto(kode.save(k));
    }

    @Transactional
    public KodaPopustaDto uredi(Long id, KodaPopustaUrediVnos v) {
        KodaPopusta k = najdi(id);
        k.setOpis(prazno(v.opis()) ? null : v.opis().trim());
        // pretecen rok je dovoljen: tako admin kodo zapre z datumom
        preveriRok(v.veljaDo(), false);
        k.setVeljaDo(v.veljaDo());
        k.setNajvecUporab(preverjenoStevilo(v.najvecUporab()));
        k.setAktivna(Boolean.TRUE.equals(v.aktivna()));
        return dto(kode.save(k));
    }

    /* Izbrise samo kodo, ki je nihce ni uporabil in je noben placilni poskus ne
       drzi - sicer bi racun izgubil sled, od kod ima ugodnost. Sicer jo je treba
       izklopiti. */
    @Transactional
    public void zbrisi(Long id) {
        KodaPopusta k = najdi(id);
        long zasedenih = zasedenih(k.getId());
        if (zasedenih > 0) {
            throw new DomenskaIzjema("Kodo je uporabilo ali drži " + zasedenih
                    + " registracij, zato je ni mogoče izbrisati. Izklopi jo.");
        }
        kode.delete(k);
    }

    // ---------- Predogled in preverba pri registraciji ----------

    /* Predogled za obrazec: ali koda velja in kaj prinese za izbrano vlogo in
       paket. Brez posledic (nic se ne rezervira). */
    @Transactional(readOnly = true)
    public PreverjenaKodaDto predogled(PreveriKodoVnos v, String naslovIp) {
        omeji(naslovIp);
        Optional<KodaPopusta> najdena = najdiSprejeto(v.koda(), normalizirajEmail(v.email()));
        if (najdena.isEmpty()) {
            return PreverjenaKodaDto.neveljavna(NEVELJAVNA_KODA);
        }
        KodaPopusta k = najdena.get();
        // organizator, ki vpise kodo z meseci brez placila, gre po brezplacni poti
        // (paket je izbran vnaprej in ga koda nadomesti)
        Pot pot = v.organizator()
                ? (k.getVrsta() == VrstaKodePopusta.ORGANIZATOR_MESECI ? Pot.BREZPLACNA : Pot.PLACLJIVA)
                : (v.paket() == Paket.BREZPLACNO ? Pot.BREZPLACNA : Pot.PLACLJIVA);
        return PreverjenaKodaDto.iz(k, razlogNeuporabnosti(k, v.organizator(), pot));
    }

    /* Brezplacna registracija (RegistracijaStoritev.registriraj). Prazno, ce kode
       ni; izjema z razlago, ce je vpisana, pa ne velja. */
    @Transactional(readOnly = true)
    public Optional<KodaPopusta> zaBrezplacnoRegistracijo(String vnos, boolean organizator, String naslovIp,
                                                          String email) {
        if (prazno(vnos)) {
            return Optional.empty();
        }
        return Optional.of(zahtevajUporabno(vnos, organizator, Pot.BREZPLACNA, naslovIp, email));
    }

    /* Placljiva registracija (PlacilaStoritev): poleg poti se preveri, da prvo
       placilo po popustu doseze Stripov najmanjsi znesek. */
    @Transactional(readOnly = true)
    public Optional<KodaPopusta> zaPlacljivoRegistracijo(String vnos, boolean organizator, Paket paket,
                                                         CiklusPlacila ciklus, boolean starejsiOd21,
                                                         String naslovIp, String email) {
        if (prazno(vnos)) {
            return Optional.empty();
        }
        KodaPopusta k = zahtevajUporabno(vnos, organizator, Pot.PLACLJIVA, naslovIp, email);
        if (k.getVrsta() == VrstaKodePopusta.ODSTOTEK
                && prvoPlacilo(cenik.cena(paket, ciklus, starejsiOd21), k.getOdstotek()) < NAJMANJSE_PLACILO_CENTOV) {
            throw new NeveljavenVnosIzjema("S to kodo bi bil znesek prenizek: plačila pod 0,50 € ni mogoče "
                    + "izvesti. Izberi dražji paket.");
        }
        return Optional.of(k);
    }

    /* Prvo placilo po popustu v centih. Popust se zaokrozi, ostanek je placilo - tako
       kot pri Stripovem odstotnem kuponu. */
    public static long prvoPlacilo(double cena, int odstotek) {
        long centi = Math.round(cena * 100);
        return centi - Math.round(centi * odstotek / 100.0);
    }

    private KodaPopusta zahtevajUporabno(String vnos, boolean organizator, Pot pot, String naslovIp,
                                         String email) {
        omeji(naslovIp);
        KodaPopusta k = najdiSprejeto(vnos, normalizirajEmail(email))
                .orElseThrow(() -> new NeveljavenVnosIzjema(NEVELJAVNA_KODA));
        String razlog = razlogNeuporabnosti(k, organizator, pot);
        if (razlog != null) {
            throw new NeveljavenVnosIzjema(razlog);
        }
        return k;
    }

    /* Zakaj koda na tej poti ne velja; null, ce velja. Besedilo vidi oseba. */
    private static String razlogNeuporabnosti(KodaPopusta k, boolean organizator, Pot pot) {
        return switch (k.getVrsta()) {
            case PREMIUM_MESECI -> organizator
                    ? "Ta koda je za registracijo igralca."
                    : pot == Pot.PLACLJIVA
                            ? "Ta koda je za brezplačno registracijo: izberi brezplačen paket."
                            : null;
            case ORGANIZATOR_MESECI -> !organizator
                    ? "Ta koda je za registracijo organizatorja."
                    : pot == Pot.PLACLJIVA
                            ? "Ta koda da paket brez plačila in se ne združuje s plačilom."
                            : null;
            case ODSTOTEK -> pot == Pot.BREZPLACNA
                    ? (organizator
                            ? "Ta koda zniža ceno paketa: izberi paket s plačilom, da jo uporabiš."
                            : "Ta koda zniža ceno Premium: izberi Premium, da jo uporabiš.")
                    : k.getZaVlogo() == null || k.getZaVlogo() == (organizator ? Vloga.ORGANIZATOR : Vloga.IGRALEC)
                            ? null
                            : k.getZaVlogo() == Vloga.ORGANIZATOR
                                    ? "Ta koda je za organizatorje."
                                    : "Ta koda je za igralce.";
        };
    }

    // ---------- Uporaba in ugodnosti ----------

    /* Racun je kodo uporabil: rezervira mesto in pusti sled v dnevniku racuna. */
    @Transactional
    public void zabeleziUporabo(Uporabnik u, KodaPopusta koda) {
        uporabe.save(new UporabaKodePopusta(koda, u.getId(), LocalDateTime.now()));
        racunDnevnik.zabelezi(u.getId(), "Koda popusta " + koda.getKoda() + ": " + opisUgodnosti(koda));
    }

    /* Isto po placilu (webhook): kodo drzi cakajoca registracija, ki je vedela le
       njen id. Kode, ki je ni vec (ni mogoce - drzi jo cakajoca vrstica), ne
       zabelezi. */
    @Transactional
    public void zabeleziUporaboPoPlacilu(Uporabnik u, Long idKoda) {
        kode.findById(idKoda).ifPresent(k -> zabeleziUporabo(u, k));
    }

    /* Koda, ki jo je racun uporabil ob registraciji (ce jo je). */
    @Transactional(readOnly = true)
    public Optional<KodaPopusta> kodaRacuna(Uporabnik u) {
        if (u == null || u.getId() == null) {
            return Optional.empty();
        }
        return uporabe.najdiZaUporabnika(u.getId()).map(UporabaKodePopusta::getKoda);
    }

    /* Organizatorski paket brez placila (ORGANIZATOR_MESECI): zacne se, ko admin
       organizatorja potrdi - prej organizator se ne sme vodit tekmovanj, mesec pa
       ne sme teci med cakanjem (isto nacelo kot preizkus Premium). Enkrat na
       racun: kdor vrstico v narocnina ze ima, je ne dobi. Vrne novo narocnino. */
    @Transactional
    public Optional<Narocnina> uveljaviZaOrganizatorja(Uporabnik u, LocalDateTime zdaj) {
        if (u == null || u.getId() == null || u.getVloga() != Vloga.ORGANIZATOR) {
            return Optional.empty();
        }
        Optional<KodaPopusta> koda = kodaRacuna(u).filter(k -> k.getVrsta() == VrstaKodePopusta.ORGANIZATOR_MESECI);
        if (koda.isEmpty() || narocnine.findByUporabnikId(u.getId()).isPresent()) {
            return Optional.empty();
        }
        KodaPopusta k = koda.get();
        Narocnina n = new Narocnina(u, k.getPaket());
        n.setStatus(StatusNarocnine.PREIZKUS);
        n.setCiklus(CiklusPlacila.LETNO);
        n.setCenaObSklenitvi(0.0);
        n.setZacetekOb(zdaj);
        n.setObdobjeOd(zdaj);
        n.setTrenutnoObdobjeDo(zdaj.plusMonths(k.getMeseci()));
        narocnine.save(n);
        racunDnevnik.zabelezi(u.getId(), "Organizator " + k.getPaket().kratekNaziv() + " brez plačila do "
                + oblikuj(UpravljanjeNarocnineStoritev.dan(n.getTrenutnoObdobjeDo())) + " (koda " + k.getKoda() + ")");
        return Optional.of(n);
    }

    /* Stripov kupon kode (ODSTOTEK); nastane ob prvem placilu in se uporabi znova.
       Brez @Transactional: klic Stripa ne sme drzati transakcije (en sam pisalec v
       SQLite). */
    public String stripeKupon(KodaPopusta k) {
        if (k.getVrsta() != VrstaKodePopusta.ODSTOTEK) {
            throw new IllegalStateException("Kupon ima samo koda z odstotkom.");
        }
        if (k.getStripeKuponId() != null) {
            return k.getStripeKuponId();
        }
        String id = stripe.ustvariKupon(k.getKoda(), k.getOdstotek());
        k.setStripeKuponId(id);
        kode.save(k);
        return id;
    }

    /* Ugodnost v besedilu za dnevnik racuna. */
    static String opisUgodnosti(KodaPopusta k) {
        return switch (k.getVrsta()) {
            case PREMIUM_MESECI -> "Premium brez plačila: " + mesecev(k.getMeseci());
            case ORGANIZATOR_MESECI -> k.getPaket().kratekNaziv() + " brez plačila: " + mesecev(k.getMeseci());
            case ODSTOTEK -> "−" + k.getOdstotek() + " % na prvo plačilo";
        };
    }

    /* "1 mesec", "2 meseca", "3 meseci", "6 mesecev" (dvojina in mnozina). */
    static String mesecev(int n) {
        return n + " " + switch (n) {
            case 1 -> "mesec";
            case 2 -> "meseca";
            case 3, 4 -> "meseci";
            default -> "mesecev";
        };
    }

    // ---------- Pomozno ----------

    /* Velike crke in brez presledkov: oseba kodo prepise s telefona, s presledkom
       na koncu ali z drugo vrsto vezaja. */
    public static String normaliziraj(String vnos) {
        if (vnos == null) {
            return "";
        }
        return vnos.replaceAll("[\\u2010-\\u2015\\u2212]", "-")
                .replaceAll("\\s+", "")
                .toUpperCase(Locale.ROOT);
    }

    /* `email` je naslov, s katerim se oseba ravnokar registrira: njen lastni
       nepotrjen ostanek (racun, ki ga nova registracija zamenja, ali opuscen
       placilni poskus) ne sme zasedati mesta, ki ga potrebuje isti poskus - sicer
       bi kdor registracije ni dokoncal in jo ponovi, pri kodi z omejenim stevilom
       uporab ostal pred zaprtimi vrati do nocnega ciscenja. */
    private Optional<KodaPopusta> najdiSprejeto(String vnos, String email) {
        String koda = normaliziraj(vnos);
        if (koda.isEmpty()) {
            return Optional.empty();
        }
        return kode.findByKoda(koda).filter(k -> jeSprejeta(k, email));
    }

    /* Aktivna, ne potekla in se ima prosto mesto. */
    private boolean jeSprejeta(KodaPopusta k, String email) {
        if (!k.isAktivna() || k.jePotekla(LocalDate.now())) {
            return false;
        }
        return k.getNajvecUporab() == null
                || zasedenih(k.getId()) - zasedenihZ(k.getId(), email) < k.getNajvecUporab();
    }

    private long zasedenih(Long idKoda) {
        return uporabe.countByKodaId(idKoda) + cakajoce.countByIdKoda(idKoda);
    }

    /* Mesta, ki jih kodi drzi isti naslov in jih nova registracija zamenja. */
    private long zasedenihZ(Long idKoda, String email) {
        if (email == null || email.isEmpty()) {
            return 0;
        }
        long cakajocih = cakajoce.countByIdKodaAndEmail(idKoda, email);
        boolean zamenjan = uporabniki.findByUporabniskoImeIgnoreCase(email)
                .filter(o -> !o.jeEmailPotrjen() && o.getVloga() != Vloga.ADMIN
                        && o.getStatus() != StatusRacuna.ZAVRNJEN)
                .filter(o -> uporabe.existsByIdUporabnikAndKodaId(o.getId(), idKoda))
                .isPresent();
        return cakajocih + (zamenjan ? 1 : 0);
    }

    private static String normalizirajEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private void omeji(String naslovIp) {
        String kljuc = "koda-popusta-ip:" + (naslovIp == null ? "?" : naslovIp);
        if (omejevalnik.jeCezMejo(kljuc, NAJVEC_PREVERJANJ_NA_URO, URA)) {
            throw new PrevecZahtevIzjema("Preveč poskusov. Poskusi čez eno uro.");
        }
        omejevalnik.zabelezi(kljuc);
    }

    private KodaPopusta najdi(Long id) {
        return kode.findById(id).orElseThrow(() -> new NiNajdenoIzjema("Koda z id " + id + " ne obstaja."));
    }

    private KodaPopustaDto dto(KodaPopusta k) {
        return KodaPopustaDto.iz(k, uporabe.countByKodaId(k.getId()), cakajoce.countByIdKoda(k.getId()),
                LocalDate.now());
    }

    private static Map<Long, Long> steviloPoKodah(List<Object[]> vrstice) {
        Map<Long, Long> stevila = new HashMap<>();
        for (Object[] vrstica : vrstice) {
            stevila.put((Long) vrstica[0], (Long) vrstica[1]);
        }
        return stevila;
    }

    private String nakljucnaKoda() {
        for (int poskus = 0; poskus < 20; poskus++) {
            StringBuilder koda = new StringBuilder();
            for (int i = 0; i < DOLZINA_NAKLJUCNE_KODE; i++) {
                koda.append(ABECEDA_KODE.charAt(NAKLJUCJE.nextInt(ABECEDA_KODE.length())));
            }
            if (!kode.existsByKoda(koda.toString())) {
                return koda.toString();
            }
        }
        throw new DomenskaIzjema("Nove kode ni bilo mogoče ustvariti. Poskusi znova.");
    }

    private static String preverjenaOblika(String koda) {
        if (!OBLIKA_KODE.matcher(koda).matches()) {
            throw new NeveljavenVnosIzjema("Koda ima lahko 3 do 32 znakov: črke brez šumnikov, številke in vezaj.");
        }
        return koda;
    }

    private static int preverjeniMeseci(Integer meseci) {
        if (meseci == null || meseci < 1 || meseci > NAJVEC_MESECEV) {
            throw new NeveljavenVnosIzjema("Število mesecev je od 1 do " + NAJVEC_MESECEV + ".");
        }
        return meseci;
    }

    private static Integer preverjenoStevilo(Integer najvecUporab) {
        if (najvecUporab != null && najvecUporab < 1) {
            throw new NeveljavenVnosIzjema("Največ uporab je vsaj 1 (prazno = neomejeno).");
        }
        return najvecUporab;
    }

    /* Nova koda ne sme biti ze potekla; pri popravku je pretekel rok dovoljen. */
    private static void preveriRok(LocalDate veljaDo, boolean nova) {
        if (nova && veljaDo != null && veljaDo.isBefore(LocalDate.now())) {
            throw new NeveljavenVnosIzjema("Rok kode je že potekel.");
        }
    }

    private static boolean prazno(String besedilo) {
        return besedilo == null || besedilo.isBlank();
    }

    private static String oblikuj(LocalDate d) {
        return d.getDayOfMonth() + ". " + d.getMonthValue() + ". " + d.getYear();
    }
}
