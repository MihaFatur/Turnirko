/* Sodnik slika listek s svojim telefonom (S2, docs/NACRT-BRANJE-LISTKOV.md).

   Sodnik ni prijavljen. Pravico mu da posest listka: QR koda nosi skrivnost
   tekme (SkrivnostListka, S1), ki jo zna izracunati samo streznik. Kdor ima
   listek v roki, sme poslati izid samo te tekme in samo, dokler tekma nima
   izida - enako kot na papirju lahko izid zapise samo, kdor ima listek.

   Potek:
   1. sprejmi: omejitev hitrosti po naslovu IP, dekodiranje pod skupno mejo
      (BranjeListkovStoritev.podDekodiranjem), QR in skrivnost - PREDEN branje
      sploh nastane (branje stevk je drago, tuj ali star listek ga ne sprozi).
      Turnir mora imeti vklopljeno stikalo »Sodniki slikajo listke«, tekma mora
      cakati na izid. Branje nastane brez uporabnika (poslal_sodnik) in z
      enkratnim zetonom potrditve; v bazi je samo SHA-256 zetona.
   2. Branje tece v ozadju kot pri organizatorju (isti bralnik in isti zakljucek).
      Telefon sprasuje stanje z zetonom v telesu zahteve.
   3. potrdi: sodnik izid potrdi ali popravi. Pri stopnji VELJA_TAKOJ gre izid
      skozi TekmaStoritev.vnesiRezultatSodnika (iste preverbe in posledice kot
      rocni vnos, brez preverbe lastnistva); pri PREDPREGLED se samo zapise in
      pocaka organizatorja, ki ga potrdi z enim dotikom.

   Skrivnost dokazuje le, da je nekdo VIDEL listek (QR je na mizi, lahko ga kdo
   fotografira), ne pa, da je izid na njem pravi. Zato velja izid takoj samo, ce
   ga podpira fotografija: bralnik je sam prebral celo tekmo, sodnik pa je
   spremenil kvecjemu negotove ali neprebrane stevilke (smeTakoj). Fotografija
   praznega listka s poljubnim izidom ali sprememba zanesljivo prebrane stevilke
   gre v predpregled k organizatorju - tudi pri stopnji VELJA_TAKOJ.

   Sodnik vpise samo odigrano tekmo s tockami po nizih; predajo, brez boja in
   diskvalifikacijo vpise organizator na pultu (listek z opombo gre tja).

   Odgovori na napake ne razkrijejo, ali tuja tekma ali branje obstaja: brez
   prave skrivnosti ali zetona je odgovor vedno isti. */
package si.turnirko.storitve;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import si.turnirko.dto.NizVnos;
import si.turnirko.dto.PredlogIzidaDto;
import si.turnirko.dto.SodnikPoslanoDto;
import si.turnirko.dto.SodniskaPotrditevVnos;
import si.turnirko.dto.SodniskoBranjeDto;
import si.turnirko.dto.VnosRezultata;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.izjeme.PrevecZahtevIzjema;
import si.turnirko.izjeme.ZasedenoIzjema;
import si.turnirko.modeli.BranjeListka;
import si.turnirko.modeli.FazaTekme;
import si.turnirko.modeli.IzidTekme;
import si.turnirko.modeli.NacinPotrditve;
import si.turnirko.modeli.PrepoznavaTekme;
import si.turnirko.modeli.SlikaBranjaListka;
import si.turnirko.modeli.SodniskaPotrditev;
import si.turnirko.modeli.StanjeBranja;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.StatusTekmovanja;
import si.turnirko.modeli.Tekma;
import si.turnirko.modeli.Turnir;
import si.turnirko.pomozno.ObdelavaSlik;
import si.turnirko.repozitoriji.BranjeListkaRepozitorij;
import si.turnirko.repozitoriji.SkupinaRepozitorij;
import si.turnirko.repozitoriji.SlikaBranjaListkaRepozitorij;
import si.turnirko.repozitoriji.TekmaRepozitorij;

@Service
public class SodniskaBranjaStoritev {

    /* Fotografij z enega naslova IP v oknu. Za istim naslovom je lahko cela
       dvorana (Wi-Fi) ali mobilno omrezje (skupni naslov operaterja), zato meja ni
       nizka; ugibanja 40-bitne skrivnosti pa tudi ta meja ne pusti blizu. */
    static final int NAJVEC_Z_NASLOVA = 120;
    /* Vse sodniske fotografije skupaj (zascita streznika, ne pravilo turnirja). */
    static final int NAJVEC_SKUPAJ = 1000;
    static final Duration OKNO = Duration.ofMinutes(10);
    /* Neuspelih poskusov z zetonom z enega naslova (napacen, potekel, smeti): zeton
       je 256-biten, meja varuje streznik pred poplavo zahtev, ne ugibanja. */
    static final int NAJVEC_NAPAK_ZETONA = 300;
    /* Tocke v nizu: vec kot toliko ni namiznoteniski niz (zascita pred smetmi). */
    static final int NAJVEC_TOCK = 99;
    /* Fotografij ene tekme: ponovljen posnetek je obicajen, deset jih ni. */
    static final int NAJVEC_NA_TEKMO = 10;
    /* Zeton potrditve velja toliko od posiljanja (sodnik potrdi takoj po tekmi). */
    static final Duration ZETON_VELJA = Duration.ofMinutes(30);

    /* Enotno sporocilo, ko listek ni za sodnisko pot: ni kode, stara koda brez
       skrivnosti, napacna skrivnost, ni take tekme. */
    static final String NI_KODE = "Na fotografiji ni kode listka, ki bi jo bilo mogoče poslati s telefonom. "
            + "Fotografiraj cel listek od zgoraj ali ga oddaj na pultu.";
    static final String NI_BRANJA = "Branja ni ali je poteklo. Fotografiraj listek znova ali ga oddaj na pultu.";
    static final String IZKLOPLJENO = "Na tem turnirju izidov s telefona ni mogoče pošiljati. Listek oddaj na pultu.";

    private final BranjeListkovStoritev branjeListkov;
    private final BranjeListkaRepozitorij branjaRepozitorij;
    private final SlikaBranjaListkaRepozitorij slikeRepozitorij;
    private final TekmaRepozitorij tekmaRepozitorij;
    private final SkupinaRepozitorij skupinaRepozitorij;
    private final TekmaStoritev tekmaStoritev;
    private final MizeStoritev mize;
    private final SkrivnostListka skrivnost;
    private final BralnikListka bralnik;
    private final UcenjeIzListkov ucenje;
    private final OmejevalnikPoskusov omejevalnik;
    private final TransactionTemplate transakcija;
    private final SecureRandom nakljucje = new SecureRandom();

    public SodniskaBranjaStoritev(BranjeListkovStoritev branjeListkov, BranjeListkaRepozitorij branjaRepozitorij,
                                  SlikaBranjaListkaRepozitorij slikeRepozitorij, TekmaRepozitorij tekmaRepozitorij,
                                  SkupinaRepozitorij skupinaRepozitorij, TekmaStoritev tekmaStoritev,
                                  SkrivnostListka skrivnost, BralnikListka bralnik, UcenjeIzListkov ucenje,
                                  OmejevalnikPoskusov omejevalnik, PlatformTransactionManager transakcije,
                                  MizeStoritev mize) {
        this.mize = mize;
        this.branjeListkov = branjeListkov;
        this.branjaRepozitorij = branjaRepozitorij;
        this.slikeRepozitorij = slikeRepozitorij;
        this.tekmaRepozitorij = tekmaRepozitorij;
        this.skupinaRepozitorij = skupinaRepozitorij;
        this.tekmaStoritev = tekmaStoritev;
        this.skrivnost = skrivnost;
        this.bralnik = bralnik;
        this.ucenje = ucenje;
        this.omejevalnik = omejevalnik;
        this.transakcija = new TransactionTemplate(transakcije);
    }

    // ---------- Sprejem ----------

    /* Fotografija s strani za sodnika. kljuc: kljuc posiljanja z vrste na telefonu
       (glava Idempotency-Key) - ponovljeno posiljanje vrne isto branje z novim
       zetonom (starega streznik ne pozna, ima samo njegov SHA-256). */
    public SodnikPoslanoDto sprejmi(byte[] vsebina, String kljuc, String naslovIp) {
        String kljucPosiljanja = BranjeListkovStoritev.preveriKljuc(kljuc);
        String naslov = kljucNaslova(naslovIp);
        // meja steje tudi zavrnjene poskuse: prav ti so ugibanje skrivnosti
        if (!omejevalnik.dovoliInZabelezi("sodnik-naslov:" + naslov, NAJVEC_Z_NASLOVA, OKNO)
                || omejevalnik.jeCezMejo("sodnik-vsi", NAJVEC_SKUPAJ, OKNO)) {
            throw new PrevecZahtevIzjema("Preveč fotografij v kratkem času. Počakaj nekaj minut ali listek oddaj na pultu.");
        }
        if (!bralnik.jeNastavljen()) {
            throw new ZasedenoIzjema("Branje listkov trenutno ni na voljo. Listek oddaj na pultu.", 60);
        }

        // dekodiranje pod skupno mejo z organizatorji; pas po naslovu (negativen kljuc,
        // da se ne meša z id-ji uporabnikov - pas je le porazdelitev, ne pravica)
        Fotografija foto = branjeListkov.podDekodiranjem((long) -1 - Math.floorMod(naslov.hashCode(), 1 << 20), () -> {
            BufferedImage slika = ObdelavaSlik.pomanjsaj(ObdelavaSlik.preberi(vsebina,
                    BranjeListkovStoritev.NAJVEC_STRANICA_VHODA, BranjeListkovStoritev.NAJVEC_PIKSLOV_VHODA),
                    BranjeListkovStoritev.NAJVEC_STRANICA);
            List<KodaListka.NajdenaKoda> kode = KodaListka.najdi(slika);
            KodaListka.NajdenaKoda koda = kode.size() == 1 ? kode.getFirst() : null;
            return new Fotografija(ObdelavaSlik.zapisiFotografijo(slika), koda);
        });
        if (foto.koda() == null || !skrivnost.velja(foto.koda().idTekme(), foto.koda().skrivnost())) {
            throw new NeveljavenVnosIzjema(NI_KODE);
        }
        // skupna meja steje samo listke s pravo skrivnostjo: smeti z vec naslovov je ne
        // morejo izcrpati in zapreti poti vsem sodnikom
        omejevalnik.zabelezi("sodnik-vsi");
        long idTekme = foto.koda().idTekme();

        String zeton = novZeton();
        try {
            return transakcija.execute(s -> {
                if (kljucPosiljanja != null) {
                    BranjeListka poslano = branjaRepozitorij.sodniskoPoKljucu(idTekme, kljucPosiljanja).orElse(null);
                    if (poslano != null) {
                        return novZetonBranja(poslano, zeton);
                    }
                }
                Tekma tekma = tekmaRepozitorij.najdiZVsem(idTekme)
                        .orElseThrow(() -> new NeveljavenVnosIzjema(NI_KODE));
                Turnir turnir = tekma.getDogodek().getTurnir();
                preveriTekmo(tekma, turnir);
                if (branjaRepozitorij.countByTekmaIdAndPoslalSodnikTrue(idTekme) >= NAJVEC_NA_TEKMO) {
                    throw new PrevecZahtevIzjema("Za to tekmo je poslanih že preveč fotografij. Listek oddaj na pultu.");
                }
                BranjeListka novo = new BranjeListka(turnir, null, LocalDateTime.now());
                novo.setPoslalSodnik(true);
                novo.setTekma(tekma);
                novo.setPrepoznava(PrepoznavaTekme.KODA);
                novo.setKljucPosiljanja(kljucPosiljanja);
                novo.setZetonSodnika(zgostitev(zeton));
                novo.setZetonVeljaDo(LocalDateTime.now().plus(ZETON_VELJA));
                BranjeListka branje = branjaRepozitorij.saveAndFlush(novo);
                ObdelavaSlik.Zapis zapis = foto.zapis();
                slikeRepozitorij.save(new SlikaBranjaListka(branje.getId(), zapis.vsebina(), zapis.sirina(),
                        zapis.visina()));
                branjeListkov.vVrsto(branje.getId());
                return new SodnikPoslanoDto(branje.getId(), zeton);
            });
        } catch (DataIntegrityViolationException e) {
            // dve zahtevi z istim kljucem hkrati: edinstveni indeks je zavrnil drugo
            if (kljucPosiljanja == null) {
                throw e;
            }
            String drugi = novZeton();
            SodnikPoslanoDto poslano = transakcija.execute(s -> branjaRepozitorij
                    .sodniskoPoKljucu(idTekme, kljucPosiljanja).map(b -> novZetonBranja(b, drugi)).orElse(null));
            if (poslano == null) {
                throw e;
            }
            return poslano;
        }
    }

    private record Fotografija(ObdelavaSlik.Zapis zapis, KodaListka.NajdenaKoda koda) {}

    /* Pravila, pod katerimi sme sodnik poslati izid tekme. */
    private static void preveriTekmo(Tekma tekma, Turnir turnir) {
        if (turnir.getSodnikiListki() == null || turnir.jeUvozen()) {
            throw new DomenskaIzjema(IZKLOPLJENO);
        }
        if (tekma.getDogodek().getStatus() != StatusTekmovanja.V_TEKU) {
            throw new DomenskaIzjema("Dogodek ni v teku. Listek oddaj na pultu.");
        }
        if (tekma.getDogodek().jeEkipno()) {
            throw new DomenskaIzjema("Izid ekipne tekme nastane iz zapisnika srečanja. Zapisnik oddaj na pultu.");
        }
        if (tekma.getStatus() == StatusTekme.KONCANA) {
            throw new DomenskaIzjema("Izid te tekme je že vpisan. Listek oddaj na pultu.");
        }
        if (tekma.getStatus() == StatusTekme.CAKA) {
            throw new DomenskaIzjema("Tekma še nima obeh igralcev. Listek oddaj na pultu.");
        }
    }

    private SodnikPoslanoDto novZetonBranja(BranjeListka branje, String zeton) {
        if (!branje.getStanje().jeOdprto() || branje.getSodnikPotrdilOb() != null) {
            throw new DomenskaIzjema("To fotografijo je Turnirko že obdelal. Za nov poskus fotografiraj listek znova.");
        }
        branje.setZetonSodnika(zgostitev(zeton));
        branje.setZetonVeljaDo(LocalDateTime.now().plus(ZETON_VELJA));
        return new SodnikPoslanoDto(branje.getId(), zeton);
    }

    // ---------- Stanje ----------

    public SodniskoBranjeDto stanje(Long idBranja, String zeton, String naslovIp) {
        String naslov = kljucNaslova(naslovIp);
        return transakcija.execute(s -> dto(zZetonom(idBranja, zeton, naslov)));
    }

    // ---------- Potrditev ----------

    public SodniskoBranjeDto potrdi(Long idBranja, SodniskaPotrditevVnos vnos, String naslovIp) {
        String naslov = kljucNaslova(naslovIp);
        // zeton se preveri PRED zaklepom zakljucevanja: poplava smeti ne sme cakati na
        // istem zaklepu kot potrditve organizatorjev
        transakcija.executeWithoutResult(s -> zZetonom(idBranja, vnos.zeton(), naslov));
        return branjeListkov.podZaklepom(() -> potrdiPodZaklepom(idBranja, vnos, naslov));
    }

    private SodniskoBranjeDto potrdiPodZaklepom(Long idBranja, SodniskaPotrditevVnos vnos, String naslov) {
        BranjeListka branje = zZetonom(idBranja, vnos.zeton(), naslov);
        if (branje.getStanje() != StanjeBranja.PREBRANO) {
            throw new DomenskaIzjema(switch (branje.getStanje()) {
                case V_VRSTI -> "Listek se še bere. Počakaj nekaj sekund.";
                case NAPAKA -> "Listka ni bilo mogoče prebrati. Fotografiraj ga znova ali ga oddaj na pultu.";
                default -> "To branje je že zaključeno.";
            });
        }
        if (branje.getSodnikPotrdilOb() != null) {
            throw new DomenskaIzjema("Izid si že potrdil. Čaka na organizatorja.");
        }
        Tekma tekma = tekmaBranja(branje);
        Turnir turnir = tekma.getDogodek().getTurnir();
        preveriTekmo(tekma, turnir);
        VnosRezultata izid = samoIgrano(vnos.izid());

        PrebranListek listek = branje.getPrebrano() == null ? null : BranjeListkovStoritev.izJson(branje.getPrebrano());
        PredlogIzidaDto predlog = listek == null ? null
                : PredlogIzidaListka.sestavi(listek, tekma.getSteviloNizov(), false);
        boolean spremenil = predlog == null || !BranjeListkovStoritev.jeEnako(predlog, izid);
        // ali je bil predlog za en dotik - pred vpisom izida (meritev, kot pri organizatorju)
        Boolean ponujen = listek == null ? null : branjeListkov.predlog(branje, listek, tekma, false).pripravljen();
        LocalDateTime zdaj = LocalDateTime.now();

        if (turnir.getSodnikiListki() == SodniskaPotrditev.VELJA_TAKOJ && smeTakoj(predlog, izid, tekma.nizovZaZmago())) {
            Long idTekme = tekma.getId();
            tekmaStoritev.vnesiRezultatSodnika(idTekme, izid);
            // tekma, ki ni bila poklicana, dobi sodnikovo mizo (M1, 3.7)
            mize.zapisiMizoZListka(idTekme, vnos.miza());
            // napredovanje v mrezi s skupinskimi posodobitvami izprazni sejo (kot pri organizatorju)
            branje = branjaRepozitorij.najdiZTurnirjem(idBranja).orElseThrow();
            branje.setStanje(StanjeBranja.POTRJENO);
            branje.setZakljucenoOb(zdaj);
            branje.setPotrjeno(JsonBranja.zapisi(izid));
            branje.setPopravljeno(spremenil);
            branje.setPripravljen(ponujen);
            branje.setNacinPotrditve(NacinPotrditve.SODNIK);
            branjeListkov.zapriOstalaBranjaTekme(idTekme, idBranja);
            // sodnikove potrditve so ucni primeri z navadno tezo; mocne oznake (F6.2) so
            // samo organizatorjevi popravki. Uci se samo iz izida, ki ga podpira
            // fotografija (smeTakoj); ostalo se uci sele ob organizatorjevi potrditvi
            int steviloNizov = izid.steviloNizov() != null ? izid.steviloNizov() : tekma.getSteviloNizov();
            ucenje.poPotrditvi(idBranja, izid, Boolean.FALSE, steviloNizov, branje.getModel(), PopravljeneCelice.NIC);
        } else {
            // predpregled (ali VELJA_TAKOJ, ki ga fotografija ne podpira): izid se
            // preveri, zapise pa sele organizatorjeva potrditev
            TekmaStoritev.preveriIgrano(tekma, izid);
        }
        branje.setSodnikovIzid(JsonBranja.zapisi(izid));
        branje.setSodnikSpremenil(spremenil);
        branje.setSodnikPotrdilOb(zdaj);
        branje.setMizaSodnika(vnos.miza());
        // zeton je enkraten: po potrditvi branja s telefona ni mogoce vec brati
        branje.setZetonSodnika(null);
        branje.setZetonVeljaDo(null);
        return dto(branje);
    }

    /* Ali izid velja takoj (stopnja VELJA_TAKOJ): bralnik je sam prebral ODLOCENO
       tekmo (zmagovalec ima zaZmago nizov - prazen listek da 0:0 ali nic), sodnik
       ne zamenja zmagovalca in spremeni kvecjemu celice, ki jih bralnik ni
       zanesljivo prebral (negotove, neprebrane, neveljaven niz). Sicer pocaka
       organizatorja - glej glavo razreda. */
    static boolean smeTakoj(PredlogIzidaDto predlog, VnosRezultata izid, int zaZmago) {
        if (predlog == null || predlog.izidTip() != IzidTekme.IGRANO
                || predlog.dobljeniNizi1() == null || predlog.dobljeniNizi2() == null
                || Math.max(predlog.dobljeniNizi1(), predlog.dobljeniNizi2()) != zaZmago
                || izid.dobljeniNizi1() == null || izid.dobljeniNizi2() == null
                || (predlog.dobljeniNizi1() > predlog.dobljeniNizi2()) != (izid.dobljeniNizi1() > izid.dobljeniNizi2())) {
            return false;
        }
        List<NizVnos> nizi = izid.nizi();
        int vrstic = Math.max(nizi.size(), predlog.nizi().size());
        for (int i = 0; i < vrstic; i++) {
            PredlogIzidaDto.NizPredloga p = i < predlog.nizi().size() ? predlog.nizi().get(i) : null;
            NizVnos s = i < nizi.size() ? nizi.get(i) : null;
            Integer p1 = p == null ? null : p.tocke1();
            Integer p2 = p == null ? null : p.tocke2();
            Integer s1 = s == null ? null : s.tocke1();
            Integer s2 = s == null ? null : s.tocke2();
            boolean negotovo1 = p == null || p1 == null || p.negotovo1() || p.neveljaven();
            boolean negotovo2 = p == null || p2 == null || p.negotovo2() || p.neveljaven();
            if ((!Objects.equals(p1, s1) && !negotovo1) || (!Objects.equals(p2, s2) && !negotovo2)) {
                return false;
            }
        }
        return true;
    }

    /* Sodnik vpise samo odigrano tekmo s tockami; posebne izide vpise organizator. */
    private static VnosRezultata samoIgrano(VnosRezultata izid) {
        if (izid.izidTip() != null && izid.izidTip() != IzidTekme.IGRANO) {
            throw new NeveljavenVnosIzjema("Predajo, brez boja ali diskvalifikacijo vpiše organizator. Listek oddaj na pultu.");
        }
        List<NizVnos> nizi = izid.nizi();
        if (nizi == null || nizi.isEmpty()) {
            throw new NeveljavenVnosIzjema("Vpiši točke vseh odigranih nizov.");
        }
        for (NizVnos n : nizi) {
            if (n.tocke1() < 0 || n.tocke2() < 0 || n.tocke1() > NAJVEC_TOCK || n.tocke2() > NAJVEC_TOCK) {
                throw new NeveljavenVnosIzjema("Točke niza morajo biti med 0 in " + NAJVEC_TOCK + ".");
            }
        }
        return new VnosRezultata(IzidTekme.IGRANO, izid.dobljeniNizi1(), izid.dobljeniNizi2(), null, nizi, null);
    }

    // ---------- Pomozno ----------

    /* Sodnisko branje z veljavnim zetonom; sicer vedno isti odgovor (ne pove, ali
       branje obstaja, je pretece ali je zeton napacen). */
    private BranjeListka zZetonom(Long idBranja, String zeton, String naslov) {
        String kljucNapak = "sodnik-zeton:" + naslov;
        if (omejevalnik.jeCezMejo(kljucNapak, NAJVEC_NAPAK_ZETONA, OKNO)) {
            throw new PrevecZahtevIzjema("Preveč neuspelih poskusov. Počakaj nekaj minut ali listek oddaj na pultu.");
        }
        BranjeListka branje = idBranja == null ? null : branjaRepozitorij.najdiZTurnirjem(idBranja).orElse(null);
        byte[] shranjen = branje == null ? null : branje.getZetonSodnika();
        boolean velja = shranjen != null && zeton != null
                && MessageDigest.isEqual(shranjen, zgostitev(zeton))
                && branje.isPoslalSodnik()
                && branje.getZetonVeljaDo() != null && branje.getZetonVeljaDo().isAfter(LocalDateTime.now());
        if (!velja) {
            omejevalnik.zabelezi(kljucNapak);
            throw new NiNajdenoIzjema(NI_BRANJA);
        }
        return branje;
    }

    /* Tekma branja; izgine lahko le z novim zrebom (tekme se zbrisejo, branje ostane
       brez tekme) - tedaj listek ne velja vec. */
    private Tekma tekmaBranja(BranjeListka branje) {
        if (branje.getTekma() == null) {
            throw new DomenskaIzjema("Te tekme ni več (nov žreb?). Listek oddaj na pultu.");
        }
        return tekmaRepozitorij.najdiZVsem(branje.getTekma().getId())
                .orElseThrow(() -> new DomenskaIzjema("Te tekme ni več (nov žreb?). Listek oddaj na pultu."));
    }

    /* Kljuc naslova za omejitve: IPv6 po omrezju /64 (en odjemalec ima obicajno celo
       omrezje in bi sicer imel neomejeno »naslovov«), IPv4 cel naslov. Ne shrani se. */
    static String kljucNaslova(String naslovIp) {
        if (naslovIp == null || naslovIp.isBlank()) {
            return "?";
        }
        if (naslovIp.indexOf(':') < 0) {
            return naslovIp;
        }
        try {
            byte[] b = java.net.InetAddress.getByName(naslovIp).getAddress();
            if (b.length == 16) {
                return java.util.HexFormat.of().formatHex(b, 0, 8) + "::/64";
            }
            return naslovIp;
        } catch (java.net.UnknownHostException e) {
            return naslovIp;
        }
    }

    private SodniskoBranjeDto dto(BranjeListka branje) {
        Tekma tekma = tekmaBranja(branje);
        PrebranListek listek = branje.getPrebrano() == null ? null : BranjeListkovStoritev.izJson(branje.getPrebrano());
        PredlogIzidaDto predlog = listek == null ? null : branjeListkov.predlog(branje, listek, tekma, false);
        String del = tekma.getIdSkupina() != null
                ? skupinaRepozitorij.findById(tekma.getIdSkupina()).map(sk -> "Skupina " + sk.getOznaka()).orElse(null)
                : tekma.getFaza() == FazaTekme.TOLAZILNI ? "Tolažilni del" : "Izločilni del";
        // napaka zakljucenega branja (nadomesceno, potrjeno drugje) je sodniku v pomoc;
        // opombe bralnika pri prebranem listku so v predlogu
        String napaka = switch (branje.getStanje()) {
            case PREBRANO, V_VRSTI -> null;
            // zakljuceno brez razloga: organizator je izid vpisal ali fotografijo zavrgel na pultu
            case POTRJENO -> "Izid te tekme je že vpisan. Listek oddaj na pultu.";
            case ZAVRZENO -> branje.getNapaka() != null ? branje.getNapaka()
                    : "Organizator je to fotografijo zavrgel. Listek oddaj na pultu.";
            case NAPAKA -> branje.getNapaka();
        };
        return new SodniskoBranjeDto(branje.getId(), branje.getStanje(), napaka,
                tekma.getDogodek().getIme(), del,
                tekma.getPrijava1() == null ? null : tekma.getPrijava1().prikazanoIme(),
                tekma.getPrijava2() == null ? null : tekma.getPrijava2().prikazanoIme(),
                tekma.getSteviloNizov(), predlog,
                listek == null ? null : listek.miza(), listek != null && listek.negotovaMiza(),
                tekma.getMiza(), tekma.getStatus() == StatusTekme.KONCANA,
                tekma.getDogodek().getTurnir().getSodnikiListki(), branje.getSodnikPotrdilOb() != null);
    }

    /* 32 nakljucnih bajtov v base64url: telefon ga dobi enkrat, v bazi je SHA-256. */
    private String novZeton() {
        byte[] b = new byte[32];
        nakljucje.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    static byte[] zgostitev(String zeton) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(Objects.requireNonNull(zeton)
                    .getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 ni na voljo.", e);
        }
    }
}
