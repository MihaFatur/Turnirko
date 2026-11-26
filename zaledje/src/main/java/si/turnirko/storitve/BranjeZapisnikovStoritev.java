/* Branje ligaskih zapisnikov s fotografije (V79, 26. 11. 2026).

   Organizator lige zapisnike kola natisne iz Turnirka (NatisZapisnikovStoritev),
   po kolu jih dobi nazaj in jih fotografira s telefonom ali skenira v PDF (vmesnik
   strani PDF pretvori v slike). Potek enega branja:
   1. sprejmi: fotografija se shrani, branje gre v vrsto (kot listki: dostop in
      meja v kratki transakciji, dekodiranje brez nje pod skupno mejo hkratnih
      dekodiranj, kljuc posiljanja proti dvojnikom ob slabem signalu);
   2. obdelaj (v ozadju): QR pove natis, bralnik (LokalniBralnikZapisnika) prebere
      krizce in tocke, PredlogZapisnika sestavi predlog in oceni zanesljivost;
   3. zanesljivo prebran zapisnik se ZAPISE SAM (lastnik: »samodejno, ce je
      zanesljivo«), ce je organizator ob nalaganju to dovolil in srecanje se ni
      vpisano; ostale organizator pregleda in potrdi (potrdi).
   Zapis gre vedno skozi ZapisZapisnikaStoritev - isto pot kot rocni vnos.

   Samodejni zapis tece v niti izvajalca, brez zahteve. Da veljajo iste preverbe
   lastnistva kot ob rocnem vnosu, tece z identiteto organizatorja, ki je
   fotografijo nalozil (kotNalagalec): branje dobi natanko njegove pravice, nic
   vec. Tudi natis, do katerega nalagalec nima dostopa, se zavrne.

   Dostop: organizator in admin; dokler je turnirko.branje-listkov.samo-admin
   vklopljen, samo admin (isto stikalo kot branje listkov). Branje vidi samo, kdor
   ga je nalozil, in admin. Fotografija ostane 30 dni. */
package si.turnirko.storitve;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import si.turnirko.branje.CeliceListka;
import si.turnirko.branje.GeometrijaZapisnika;
import si.turnirko.branje.MrezaStevk;
import si.turnirko.branje.RazrezCelice;
import si.turnirko.branje.Temnost;
import si.turnirko.branje.UcenjeStevk;
import si.turnirko.dto.BranjeZapisnikaDto;
import si.turnirko.dto.CeliceZapisnikaDto;
import si.turnirko.dto.NizVnos;
import si.turnirko.dto.StanjeBranjaListkovDto;
import si.turnirko.dto.ZapisZapisnikaVnos;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.izjeme.PrepovedanoIzjema;
import si.turnirko.izjeme.PrevecZahtevIzjema;
import si.turnirko.modeli.BranjeZapisnika;
import si.turnirko.modeli.BranjeZapisnika.Stanje;
import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.KaderEkipe;
import si.turnirko.modeli.NatisZapisnika;
import si.turnirko.modeli.SlikaBranjaZapisnika;
import si.turnirko.modeli.Srecanje;
import si.turnirko.modeli.StatusTekmeSrecanja;
import si.turnirko.modeli.StevkaListka;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.pomozno.ObdelavaSlik;
import si.turnirko.repozitoriji.BranjeZapisnikaRepozitorij;
import si.turnirko.repozitoriji.KaderEkipeRepozitorij;
import si.turnirko.repozitoriji.NatisZapisnikaRepozitorij;
import si.turnirko.repozitoriji.SlikaBranjaZapisnikaRepozitorij;
import si.turnirko.repozitoriji.StevkaListkaRepozitorij;
import si.turnirko.repozitoriji.TekmaSrecanjaRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

@Service
public class BranjeZapisnikovStoritev {

    private static final Logger log = LoggerFactory.getLogger(BranjeZapisnikovStoritev.class);

    /* Daljsa stranica shranjene fotografije: list A4 pri 3200 px ima ~11 px/mm,
       toliko kot poravnan list (10 px/mm). Vmesnik pomanjsa na isto mero. */
    static final int NAJVEC_STRANICA = 3200;
    /* Ena fotografija na 5 s: hitreje, kot je mogoce liste prinasati pod telefon;
       PDF celega kola (do ~30 strani) je pod mejo. */
    static final int NAJVEC_V_OKNU = 120;
    static final Duration OKNO = Duration.ofMinutes(10);
    static final int DNI_HRAMBE = 30;
    /* Seznam na strani branja: branja zadnjih toliko dni. */
    static final int DNI_SEZNAMA = 30;
    static final Duration OBVISELO_PO = Duration.ofMinutes(2);
    static final Duration KLJUC_POSILJANJA_VELJA = Duration.ofHours(24);
    static final String NADOMESCENO = "Nadomeščeno z novejšo fotografijo istega zapisnika.";
    static final String VPISANO_DRUGJE = "Srečanje je bilo vpisano z drugo fotografijo.";

    private final BranjeZapisnikaRepozitorij branja;
    private final SlikaBranjaZapisnikaRepozitorij slike;
    private final NatisZapisnikaRepozitorij natisi;
    private final KaderEkipeRepozitorij kadri;
    private final TekmaSrecanjaRepozitorij tekmeSrecanj;
    private final UporabnikRepozitorij uporabniki;
    private final StevkaListkaRepozitorij stevke;
    private final LastnistvoStoritev lastnistvo;
    private final BranjeListkovStoritev branjeListkov;
    private final BralnikZapisnika bralnik;
    private final SkrivnostListka skrivnost;
    private final ZapisZapisnikaStoritev zapis;
    private final VirMrezeStevk vir;
    private final Executor izvajalec;
    private final TransactionTemplate transakcija;
    private boolean samoAdmin;
    /* Zakljucek branja, samodejni zapis, potrditev, zavrzenje in ponovno branje po
       eno naenkrat (kot pri listkih): dvojniki se vidijo, potrditev in samodejni zapis
       istega srecanja se ne prehitita. */
    private final Object zakljucevanje = new Object();

    public BranjeZapisnikovStoritev(BranjeZapisnikaRepozitorij branja, SlikaBranjaZapisnikaRepozitorij slike,
                                    NatisZapisnikaRepozitorij natisi, KaderEkipeRepozitorij kadri,
                                    TekmaSrecanjaRepozitorij tekmeSrecanj, UporabnikRepozitorij uporabniki,
                                    StevkaListkaRepozitorij stevke, LastnistvoStoritev lastnistvo,
                                    BranjeListkovStoritev branjeListkov, BralnikZapisnika bralnik,
                                    SkrivnostListka skrivnost, ZapisZapisnikaStoritev zapis, VirMrezeStevk vir,
                                    @Qualifier("branjeListkovIzvajalec") Executor izvajalec,
                                    PlatformTransactionManager upraviteljTransakcij,
                                    @Value("${turnirko.branje-listkov.samo-admin:true}") boolean samoAdmin) {
        this.branja = branja;
        this.slike = slike;
        this.natisi = natisi;
        this.kadri = kadri;
        this.tekmeSrecanj = tekmeSrecanj;
        this.uporabniki = uporabniki;
        this.stevke = stevke;
        this.lastnistvo = lastnistvo;
        this.branjeListkov = branjeListkov;
        this.bralnik = bralnik;
        this.skrivnost = skrivnost;
        this.zapis = zapis;
        this.vir = vir;
        this.izvajalec = izvajalec;
        this.transakcija = new TransactionTemplate(upraviteljTransakcij);
        this.samoAdmin = samoAdmin;
    }

    /* Samo za teste. */
    void nastaviSamoAdmin(boolean samoAdmin) {
        this.samoAdmin = samoAdmin;
    }

    // ---------- Dostop ----------

    public StanjeBranjaListkovDto stanje() {
        Uporabnik jaz = lastnistvo.trenutni();
        if (jaz == null) {
            return new StanjeBranjaListkovDto(false, "Za branje zapisnikov se prijavi.");
        }
        if (jaz.getVloga() != Vloga.ADMIN && jaz.getVloga() != Vloga.ORGANIZATOR) {
            return new StanjeBranjaListkovDto(false, "Branje zapisnikov je namenjeno organizatorjem lig.");
        }
        if (!bralnik.jeNastavljen()) {
            return new StanjeBranjaListkovDto(false, "Branje zapisnikov na tem strežniku ni nastavljeno.");
        }
        if (samoAdmin && jaz.getVloga() != Vloga.ADMIN) {
            return new StanjeBranjaListkovDto(false, "Branje zapisnikov je v preizkusu in še ni na voljo.");
        }
        return new StanjeBranjaListkovDto(true, null);
    }

    private Uporabnik preveriDostop() {
        StanjeBranjaListkovDto stanje = stanje();
        if (!stanje.omogoceno()) {
            throw new PrepovedanoIzjema(stanje.razlog());
        }
        return lastnistvo.trenutni();
    }

    /* Branje vidi in ureja samo, kdor ga je nalozil, in admin. */
    private BranjeZapisnika najdiSvoje(Long id) {
        Uporabnik jaz = preveriDostop();
        BranjeZapisnika b = branja.najdiPodrobno(id)
                .orElseThrow(() -> new NiNajdenoIzjema("Branje zapisnika z id " + id + " ne obstaja."));
        if (jaz.getVloga() != Vloga.ADMIN && (b.getNalozil() == null || !b.getNalozil().getId().equals(jaz.getId()))) {
            throw new NiNajdenoIzjema("Branje zapisnika z id " + id + " ne obstaja.");
        }
        return b;
    }

    // ---------- Sprejem ----------

    private record Prejeto(Long idUporabnika, BranjeZapisnikaDto poslano) {}

    /* samodejno: ali sme se zanesljivo prebran zapisnik zapisati sam (izbira na strani). */
    public BranjeZapisnikaDto sprejmi(byte[] vsebina, String kljuc, boolean samodejno) {
        String kljucPosiljanja = BranjeListkovStoritev.preveriKljuc(kljuc);
        Prejeto prejeto = transakcija.execute(s -> {
            Uporabnik jaz = preveriDostop();
            BranjeZapisnikaDto poslano = zePoslano(jaz.getId(), kljucPosiljanja);
            if (poslano != null) {
                return new Prejeto(jaz.getId(), poslano);
            }
            if (branja.countByNalozilIdAndUstvarjenoObAfter(jaz.getId(), LocalDateTime.now().minus(OKNO))
                    >= NAJVEC_V_OKNU) {
                throw new PrevecZahtevIzjema("Preveč fotografij v kratkem času. Počakaj nekaj minut.");
            }
            return new Prejeto(jaz.getId(), null);
        });
        if (prejeto.poslano() != null) {
            return prejeto.poslano();
        }
        ObdelavaSlik.Zapis zapisSlike = branjeListkov.podDekodiranjem(prejeto.idUporabnika(),
                () -> ObdelavaSlik.zapisiFotografijo(ObdelavaSlik.pomanjsaj(ObdelavaSlik.preberi(vsebina,
                        BranjeListkovStoritev.NAJVEC_STRANICA_VHODA, BranjeListkovStoritev.NAJVEC_PIKSLOV_VHODA),
                        NAJVEC_STRANICA)));
        try {
            return transakcija.execute(s -> {
                Uporabnik jaz = preveriDostop();
                BranjeZapisnikaDto poslano = zePoslano(jaz.getId(), kljucPosiljanja);
                if (poslano != null) {
                    return poslano;
                }
                BranjeZapisnika novo = new BranjeZapisnika(jaz, samodejno, LocalDateTime.now());
                novo.setKljucPosiljanja(kljucPosiljanja);
                BranjeZapisnika b = branja.saveAndFlush(novo);
                slike.save(new SlikaBranjaZapisnika(b.getId(), zapisSlike.vsebina(), zapisSlike.sirina(),
                        zapisSlike.visina()));
                vVrsto(b.getId());
                return dto(b, false);
            });
        } catch (DataIntegrityViolationException e) {
            if (kljucPosiljanja == null) {
                throw e;
            }
            BranjeZapisnikaDto poslano = transakcija.execute(s -> zePoslano(prejeto.idUporabnika(), kljucPosiljanja));
            if (poslano == null) {
                throw e;
            }
            return poslano;
        }
    }

    private BranjeZapisnikaDto zePoslano(Long idUporabnika, String kljuc) {
        if (kljuc == null) {
            return null;
        }
        return branja.poKljucuPosiljanja(idUporabnika, kljuc)
                .flatMap(b -> branja.najdiPodrobno(b.getId()))
                .map(b -> dto(b, false))
                .orElse(null);
    }

    void vVrsto(Long idBranja) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    oddaj(idBranja);
                }
            });
        } else {
            oddaj(idBranja);
        }
    }

    private void oddaj(Long idBranja) {
        try {
            izvajalec.execute(() -> obdelaj(idBranja));
        } catch (RejectedExecutionException e) {
            log.error("Branje zapisnika {} ni slo v vrsto: vrsta branja je polna", idBranja);
        }
    }

    // ---------- Branje (v ozadju) ----------

    private record Vhod(Long idNalozil, byte[] slika) {}

    /* Prebere zapisnik in zapise predlog; zanesljivega po potrebi tudi vpise. Ne
       vrze nicesar: napaka konca v stanju NAPAKA z razlago. */
    public void obdelaj(Long idBranja) {
        Vhod vhod = transakcija.execute(s -> {
            BranjeZapisnika b = branja.findById(idBranja).orElse(null);
            if (b == null || b.getStanje() != Stanje.V_VRSTI) {
                return null;
            }
            SlikaBranjaZapisnika slika = slike.findById(idBranja).orElse(null);
            if (slika == null || b.getNalozil() == null) {
                oznaciNapako(b, slika == null ? "Fotografije ni več." : "Uporabnika, ki je naložil fotografijo, ni več.");
                return null;
            }
            return new Vhod(b.getNalozil().getId(), slika.getVsebina());
        });
        if (vhod == null) {
            return;
        }
        boolean zapisi;
        try {
            zapisi = kotNalagalec(vhod.idNalozil(), () -> preberiInZakljuci(idBranja, vhod));
        } catch (RuntimeException e) {
            log.error("Branje zapisnika {} ni uspelo", idBranja, e);
            zapisiNapako(idBranja, "Branje ni uspelo. Poskusi znova.");
            return;
        }
        if (zapisi) {
            kotNalagalec(vhod.idNalozil(), () -> {
                samodejniZapis(idBranja);
                return null;
            });
        }
    }

    /* Branje in zapis predloga; vrne, ali naj se zapisnik zapise sam. */
    private boolean preberiInZakljuci(Long idBranja, Vhod vhod) {
        LokalniBralnikZapisnika.Branje prepis;
        try {
            prepis = bralnik.preberi(vhod.slika(), koda -> transakcija.execute(s -> natisZaBranje(koda)));
        } catch (LokalniBralnikZapisnika.NapakaBranjaZapisnika e) {
            zapisiNapako(idBranja, e.getMessage());
            return false;
        }
        synchronized (zakljucevanje) {
            Boolean zapisi = transakcija.execute(s -> {
                BranjeZapisnika b = branja.findById(idBranja).orElse(null);
                if (b == null || b.getStanje() != Stanje.V_VRSTI) {
                    return false;
                }
                NatisZapisnika natis = natisi.najdiPodrobno(prepis.zapisnik().idNatisa()).orElseThrow();
                b.setNatis(natis);
                b.setSrecanje(natis.getSrecanje());
                b.setPrebrano(JsonBranja.zapisi(prepis.zapisnik()));
                b.setCelice(JsonBranja.zapisi(prepis.celice()));
                slike.findById(idBranja).ifPresent(sl -> sl.setPoravnana(prepis.poravnana()));
                b.setModel(prepis.model());
                b.setStanje(Stanje.PREBRANO);
                b.setPrebranoOb(LocalDateTime.now());
                b.setNapaka(null);
                b.setOpomba(null);
                PredlogZapisnika.Predlog predlog = predlog(natis, prepis.zapisnik());
                boolean vpisano = zapis.jeVpisano(natis.getSrecanje().getId());
                b.setZanesljivo(predlog.zanesljiv());
                if (vpisano) {
                    b.setOpomba("Zapisnik tega srečanja je že vpisan. Če je ta pravi, ga prepiši.");
                }
                zapriDvojnike(b);
                return b.isSamodejno() && predlog.zanesljiv() && !vpisano;
            });
            return Boolean.TRUE.equals(zapisi);
        }
    }

    /* Natis po kodi s fotografije: obstoj, skrivnost in dostop nalagalca (tece z
       njegovo identiteto). */
    private LokalniBralnikZapisnika.NatisZaBranje natisZaBranje(KodaListka.KodaZapisnika koda) {
        NatisZapisnika n = natisi.najdiPodrobno(koda.idNatisa()).orElse(null);
        if (n == null || !skrivnost.veljaZapisnik(koda.idNatisa(), koda.skrivnost())) {
            throw new LokalniBralnikZapisnika.NapakaBranjaZapisnika("Koda zapisnika ni veljavna (zapisnik je bil "
                    + "morda natisnjen na drugem strežniku ali pred obnovo baze).");
        }
        try {
            lastnistvo.preveriPoSrecanju(n.getSrecanje().getId());
        } catch (PrepovedanoIzjema | DomenskaIzjema | NiNajdenoIzjema e) {
            throw new LokalniBralnikZapisnika.NapakaBranjaZapisnika("Zapisnik je za srečanje tekmovanja, ki ga ne "
                    + "urejaš: " + n.getSrecanje().getEkipaDomaci().prikazanoIme() + " : "
                    + n.getSrecanje().getEkipaGost().prikazanoIme() + ".");
        }
        return new LokalniBralnikZapisnika.NatisZaBranje(n.getId(), NatisZapisnikovStoritev.postavitev(n),
                n.getSteviloNizov());
    }

    /* Dvojniki: med odprtimi branji istega srecanja ostane to (najnovejse prebrano). */
    private void zapriDvojnike(BranjeZapisnika b) {
        for (BranjeZapisnika d : branja.odprtaZaSrecanje(b.getSrecanje().getId(), b.getId())) {
            if (d.getStanje() == Stanje.V_VRSTI) {
                continue;
            }
            d.setStanje(Stanje.ZAVRZENO);
            d.setNapaka(NADOMESCENO);
            d.setZakljucenoOb(LocalDateTime.now());
        }
    }

    private void samodejniZapis(Long idBranja) {
        synchronized (zakljucevanje) {
            record Naloga(Long idSrecanje, ZapisZapisnikaVnos vnos) {}
            Naloga naloga = transakcija.execute(s -> {
                BranjeZapisnika b = branja.najdiPodrobno(idBranja).orElse(null);
                if (b == null || b.getStanje() != Stanje.PREBRANO || !Boolean.TRUE.equals(b.getZanesljivo())
                        || b.getSrecanje() == null) {
                    return null;
                }
                return new Naloga(b.getSrecanje().getId(), vnosIzPredloga(b));
            });
            if (naloga == null) {
                return;
            }
            try {
                transakcija.executeWithoutResult(s -> {
                    zapis.zapisi(naloga.idSrecanje(), naloga.vnos());
                    BranjeZapisnika b = branja.findById(idBranja).orElseThrow();
                    zakljuci(b, naloga.vnos(), false, true);
                });
            } catch (RuntimeException e) {
                // zanesljivo prebrano, a zapis zavrnjen (npr. igralec ni vec v kadru): organizator
                log.info("Samodejni zapis zapisnika {} ni uspel: {}", idBranja, e.getMessage());
                transakcija.executeWithoutResult(s -> branja.findById(idBranja).ifPresent(b -> {
                    b.setOpomba("Samodejni zapis ni uspel: " + e.getMessage());
                    b.setZanesljivo(false);
                }));
            }
        }
    }

    /* Branje je zapisano v srecanje: sled in zaprtje ostalih branj istega srecanja. */
    private void zakljuci(BranjeZapisnika b, ZapisZapisnikaVnos vnos, boolean popravljeno, boolean samodejno) {
        b.setStanje(Stanje.ZAPISANO);
        b.setPotrjeno(JsonBranja.zapisi(vnos));
        b.setPopravljeno(popravljeno);
        b.setZapisanoSamodejno(samodejno);
        b.setZakljucenoOb(LocalDateTime.now());
        b.setOpomba(null);
        for (BranjeZapisnika d : branja.odprtaZaSrecanje(b.getSrecanje().getId(), b.getId())) {
            d.setStanje(Stanje.ZAVRZENO);
            d.setNapaka(VPISANO_DRUGJE);
            d.setZakljucenoOb(LocalDateTime.now());
        }
    }

    private void zapisiNapako(Long idBranja, String sporocilo) {
        synchronized (zakljucevanje) {
            transakcija.executeWithoutResult(s -> branja.findById(idBranja).ifPresent(b -> {
                if (b.getStanje() == Stanje.V_VRSTI) {
                    oznaciNapako(b, sporocilo);
                }
            }));
        }
    }

    private static void oznaciNapako(BranjeZapisnika b, String sporocilo) {
        b.setStanje(Stanje.NAPAKA);
        b.setNapaka(sporocilo);
        b.setPrebranoOb(LocalDateTime.now());
    }

    /* Delo z identiteto uporabnika, ki je fotografijo nalozil: preverbe lastnistva
       (LastnistvoStoritev) veljajo kot ob njegovem rocnem vnosu. Samo za niti
       izvajalca - nit zahteve ima svojo prijavo. */
    private <T> T kotNalagalec(Long idUporabnika, Supplier<T> delo) {
        Uporabnik u = transakcija.execute(s -> uporabniki.findById(idUporabnika).orElse(null));
        if (u == null) {
            throw new IllegalStateException("Uporabnika ni.");
        }
        SecurityContext prej = SecurityContextHolder.getContext();
        SecurityContext kontekst = SecurityContextHolder.createEmptyContext();
        kontekst.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(u.getUporabniskoIme(), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + u.getVloga().name()))));
        SecurityContextHolder.setContext(kontekst);
        try {
            return delo.get();
        } finally {
            SecurityContextHolder.setContext(prej);
        }
    }

    // ---------- Predlog ----------

    static PredlogZapisnika.Predlog predlog(NatisZapisnika n, PrebranZapisnik prebrano) {
        Srecanje s = n.getSrecanje();
        return PredlogZapisnika.izracunaj(prebrano, n.getFormat(), n.getSteviloNizov(), s.pravila().zmagZaSrecanje(),
                NatisZapisnikovStoritev.kader(n.getKaderDomaci()).size(),
                NatisZapisnikovStoritev.kader(n.getKaderGost()).size());
    }

    /* Zapis iz predloga (samodejni zapis): igralci po kadru natisa, tocke odigranih tekem. */
    static ZapisZapisnikaVnos vnosIzPredloga(BranjeZapisnika b) {
        NatisZapisnika n = b.getNatis();
        PredlogZapisnika.Predlog p = predlog(n, JsonBranja.preberi(b.getPrebrano(), PrebranZapisnik.class));
        List<Long> kaderD = NatisZapisnikovStoritev.kader(n.getKaderDomaci());
        List<Long> kaderG = NatisZapisnikovStoritev.kader(n.getKaderGost());
        List<ZapisZapisnikaVnos.Tekma> tekme = new ArrayList<>();
        for (PredlogZapisnika.Tekma t : p.tekme()) {
            if (t.stanje() != PredlogZapisnika.StanjeTekme.ODIGRANA) {
                tekme.add(new ZapisZapisnikaVnos.Tekma(List.of(), List.of(), List.of(), null, null));
                continue;
            }
            List<NizVnos> nizi = new ArrayList<>();
            for (PrebranListek.Niz niz : t.nizi()) {
                if (niz.levo() != null && niz.desno() != null) {
                    nizi.add(new NizVnos(niz.levo(), niz.desno()));
                }
            }
            tekme.add(new ZapisZapisnikaVnos.Tekma(ids(t.domaci(), kaderD), ids(t.gost(), kaderG), nizi, null, null));
        }
        return new ZapisZapisnikaVnos(tekme, false, null);
    }

    private static List<Long> ids(List<Integer> vrstice, List<Long> kader) {
        List<Long> ids = new ArrayList<>();
        for (Integer v : vrstice) {
            ids.add(v < kader.size() ? kader.get(v) : null);
        }
        return ids;
    }

    // ---------- Seznam, podrobno ----------

    public List<BranjeZapisnikaDto> seznam() {
        return transakcija.execute(s -> {
            Uporabnik jaz = preveriDostop();
            return branja.zaUporabnika(jaz.getId(), LocalDateTime.now().minusDays(DNI_SEZNAMA)).stream()
                    .map(b -> dto(b, false))
                    .toList();
        });
    }

    public BranjeZapisnikaDto podrobno(Long id) {
        return transakcija.execute(s -> dto(najdiSvoje(id), true));
    }

    private BranjeZapisnikaDto dto(BranjeZapisnika b, boolean podrobno) {
        Srecanje s = b.getSrecanje();
        BranjeZapisnikaDto.Srecanje srecanje = null;
        if (s != null) {
            boolean liga = s.getLiga() != null;
            boolean vpisano = s.getStatus() == si.turnirko.modeli.StatusSrecanja.KONCANO
                    || tekmeSrecanj.najdiZaSrecanje(s.getId()).stream()
                    .anyMatch(t -> t.getStatus() == StatusTekmeSrecanja.KONCANA);
            srecanje = new BranjeZapisnikaDto.Srecanje(s.getId(), liga ? s.getLiga().getId() : null,
                    liga ? s.getLiga().getIme() : s.getTekma().getDogodek().getTurnir().getIme(),
                    liga ? (s.getSerija() != null ? "končnica" : s.getKolo() + ". kolo") : s.getTekma().getDogodek().getIme(),
                    liga ? s.getKolo() : null, s.getEkipaDomaci().prikazanoIme(), s.getEkipaGost().prikazanoIme(),
                    s.getStatus(), s.getDobljeneDomaci(), s.getDobljeneGost(), vpisano);
        }
        BranjeZapisnikaDto.Predlog predlog = null;
        NatisZapisnika n = b.getNatis();
        if (n != null && b.getPrebrano() != null) {
            predlog = predlogDto(n, JsonBranja.preberi(b.getPrebrano(), PrebranZapisnik.class));
        }
        BranjeZapisnikaDto.Podrobnosti podrobnosti = null;
        if (podrobno && n != null && s != null) {
            Map<Long, String> imena = new HashMap<>();
            List<BranjeZapisnikaDto.Igralec> kaderD = kaderEkipe(s.getEkipaDomaci().getId(), imena);
            List<BranjeZapisnikaDto.Igralec> kaderG = kaderEkipe(s.getEkipaGost().getId(), imena);
            boolean poravnana = b.getSlikaIzbrisanaOb() == null
                    && slike.findById(b.getId()).map(sl -> sl.getPoravnana() != null).orElse(false);
            podrobnosti = new BranjeZapisnikaDto.Podrobnosti(n.getOblika(), n.getFormat(), n.getSteviloNizov(),
                    s.pravila().zmagZaSrecanje(), igralciNatisa(NatisZapisnikovStoritev.kader(n.getKaderDomaci()), imena),
                    igralciNatisa(NatisZapisnikovStoritev.kader(n.getKaderGost()), imena), kaderD, kaderG,
                    b.getCelice() == null || !poravnana ? null : JsonBranja.preberi(b.getCelice(), CeliceZapisnikaDto.class),
                    poravnana, b.getPotrjeno() == null ? null : JsonBranja.preberi(b.getPotrjeno(), ZapisZapisnikaVnos.class));
        }
        boolean obviselo = b.getStanje() == Stanje.V_VRSTI
                && b.getUstvarjenoOb().isBefore(LocalDateTime.now().minus(OBVISELO_PO));
        return new BranjeZapisnikaDto(b.getId(), b.getStanje(), b.getNapaka(), b.getOpomba(), b.isSamodejno(),
                b.isZapisanoSamodejno(), b.getZanesljivo(), b.getUstvarjenoOb(), obviselo,
                b.getSlikaIzbrisanaOb() == null, srecanje, predlog, podrobnosti);
    }

    private static BranjeZapisnikaDto.Predlog predlogDto(NatisZapisnika n, PrebranZapisnik prebrano) {
        PredlogZapisnika.Predlog p = predlog(n, prebrano);
        List<Long> kaderD = NatisZapisnikovStoritev.kader(n.getKaderDomaci());
        List<Long> kaderG = NatisZapisnikovStoritev.kader(n.getKaderGost());
        List<BranjeZapisnikaDto.Tekma> tekme = new ArrayList<>();
        int negotovih = 0;
        for (PredlogZapisnika.Tekma t : p.tekme()) {
            List<BranjeZapisnikaDto.Niz> nizi = new ArrayList<>();
            for (PrebranListek.Niz niz : t.nizi()) {
                nizi.add(new BranjeZapisnikaDto.Niz(niz.levo(), niz.desno(), niz.negotovoLevo(), niz.negotovoDesno()));
                if (t.stanje() != PredlogZapisnika.StanjeTekme.NEODIGRANA) {
                    negotovih += (niz.negotovoLevo() ? 1 : 0) + (niz.negotovoDesno() ? 1 : 0);
                }
            }
            negotovih += (t.negotovoDomaci() ? 1 : 0) + (t.negotovoGost() ? 1 : 0);
            tekme.add(new BranjeZapisnikaDto.Tekma(t.oznaka(), t.dvojice(), ids(t.domaci(), kaderD),
                    ids(t.gost(), kaderG), t.negotovoDomaci(), t.negotovoGost(), nizi, t.stanje().name(),
                    t.niziDomaci(), t.niziGost()));
        }
        return new BranjeZapisnikaDto.Predlog(tekme, p.zanesljiv(), p.opozorila(), negotovih);
    }

    private List<BranjeZapisnikaDto.Igralec> kaderEkipe(Long idEkipa, Map<Long, String> imena) {
        List<BranjeZapisnikaDto.Igralec> kader = new ArrayList<>();
        for (KaderEkipe k : kadri.najdiZaEkipo(idEkipa)) {
            String ime = k.getIgralec().getIme() + " " + k.getIgralec().getPriimek();
            imena.put(k.getIgralec().getId(), ime);
            kader.add(new BranjeZapisnikaDto.Igralec(k.getIgralec().getId(), ime));
        }
        return kader;
    }

    /* Vrstice natisnjenega kadra z imeni; igralec, ki ga v kadru ni vec, ostane z id. */
    private List<BranjeZapisnikaDto.Igralec> igralciNatisa(List<Long> kader, Map<Long, String> imena) {
        List<BranjeZapisnikaDto.Igralec> igralci = new ArrayList<>();
        for (Long id : kader) {
            igralci.add(new BranjeZapisnikaDto.Igralec(id, imena.getOrDefault(id, "(ni več v kadru)")));
        }
        return igralci;
    }

    // ---------- Potrditev, zavrzenje, ponovno branje ----------

    public BranjeZapisnikaDto potrdi(Long id, ZapisZapisnikaVnos vnos) {
        synchronized (zakljucevanje) {
            transakcija.executeWithoutResult(s -> {
                BranjeZapisnika b = najdiSvoje(id);
                if (b.getSrecanje() == null || (b.getStanje() != Stanje.PREBRANO && b.getStanje() != Stanje.ZAPISANO)) {
                    throw new DomenskaIzjema("Tega branja ni mogoče zapisati (stanje " + b.getStanje() + ").");
                }
                if (b.getStanje() == Stanje.ZAPISANO && !vnos.prepisi()) {
                    throw new DomenskaIzjema("Zapisnik je že zapisan. Popravek ga mora prepisati.");
                }
                boolean popravljeno = !enakPredlogu(b, vnos);
                zapis.zapisi(b.getSrecanje().getId(), vnos);
                // zapis (preracun ratinga ob prepisu ali vnosu za nazaj) lahko izprazni sejo:
                // branje se prebere znova, sicer bi se sprememba stanja izgubila
                BranjeZapisnika zdaj = branja.najdiPodrobno(id).orElseThrow();
                zakljuci(zdaj, vnos, popravljeno, false);
                poPotrditvi(zdaj.getId());
            });
        }
        return podrobno(id);
    }

    /* Ali organizator ni nic spremenil (merilo natancnosti, kot pri listkih). */
    private static boolean enakPredlogu(BranjeZapisnika b, ZapisZapisnikaVnos vnos) {
        if (b.getNatis() == null || b.getPrebrano() == null) {
            return false;
        }
        ZapisZapisnikaVnos predlog = vnosIzPredloga(b);
        for (int i = 0; i < predlog.tekme().size() && i < vnos.tekme().size(); i++) {
            ZapisZapisnikaVnos.Tekma a = predlog.tekme().get(i);
            ZapisZapisnikaVnos.Tekma c = vnos.tekme().get(i);
            if (a.imaIzid() != c.imaIzid() || (a.imaIzid() && (!Objects.equals(sorted(a.domaci()), sorted(c.domaci()))
                    || !Objects.equals(sorted(a.gost()), sorted(c.gost())) || !Objects.equals(a.nizi(), c.nizi())
                    || c.izidTip() != null))) {
                return false;
            }
        }
        return true;
    }

    private static List<Long> sorted(List<Long> l) {
        return l == null ? List.of() : l.stream().filter(Objects::nonNull).sorted().toList();
    }

    public void zavrzi(Long id) {
        synchronized (zakljucevanje) {
            transakcija.executeWithoutResult(s -> {
                BranjeZapisnika b = najdiSvoje(id);
                if (!b.getStanje().jeOdprto()) {
                    throw new DomenskaIzjema("Branje je že zaključeno.");
                }
                b.setStanje(Stanje.ZAVRZENO);
                b.setZakljucenoOb(LocalDateTime.now());
            });
        }
    }

    public BranjeZapisnikaDto znova(Long id) {
        synchronized (zakljucevanje) {
            transakcija.executeWithoutResult(s -> {
                BranjeZapisnika b = najdiSvoje(id);
                boolean obviselo = b.getStanje() == Stanje.V_VRSTI
                        && b.getUstvarjenoOb().isBefore(LocalDateTime.now().minus(OBVISELO_PO));
                if (b.getStanje() != Stanje.NAPAKA && b.getStanje() != Stanje.PREBRANO && !obviselo) {
                    throw new DomenskaIzjema("Branje ni mogoče ponoviti v tem stanju.");
                }
                if (b.getSlikaIzbrisanaOb() != null || slike.findById(id).isEmpty()) {
                    throw new DomenskaIzjema("Fotografije ni več - zapisnik fotografiraj znova.");
                }
                b.setStanje(Stanje.V_VRSTI);
                b.setNapaka(null);
                b.setOpomba(null);
                b.setZanesljivo(null);
                vVrsto(b.getId());
            });
        }
        return podrobno(id);
    }

    public byte[] slika(Long id) {
        return transakcija.execute(s -> {
            najdiSvoje(id);
            return slike.findById(id).map(SlikaBranjaZapisnika::getVsebina)
                    .orElseThrow(() -> new NiNajdenoIzjema("Fotografije ni več."));
        });
    }

    public byte[] poravnana(Long id) {
        return transakcija.execute(s -> {
            najdiSvoje(id);
            byte[] p = slike.findById(id).map(SlikaBranjaZapisnika::getPoravnana).orElse(null);
            if (p == null) {
                throw new NiNajdenoIzjema("Poravnanega zapisnika ni.");
            }
            return p;
        });
    }

    // ---------- Ucenje iz potrjenih zapisnikov ----------

    /* Stevke potrjenega zapisnika gredo v ucenje mreze (isto kot listki, F6): samo
       zapisnik, ki ga je pregledal organizator - samodejni zapis bi mrezo ucil na
       njenem lastnem branju. Celica, ki jo je organizator popravil, je mocna oznaka.
       Po potrditvi transakcije, v izvajalcu branja (ne zadrzi organizatorja). */
    private void poPotrditvi(Long idBranja) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        izvajalec.execute(() -> {
                            try {
                                zabeleziStevke(idBranja);
                            } catch (RuntimeException e) {
                                log.warn("Stevke zapisnika {} niso bile zapisane: {}", idBranja, e.getMessage());
                            }
                        });
                    } catch (RejectedExecutionException e) {
                        log.warn("Stevke zapisnika {} niso sle v vrsto", idBranja);
                    }
                }
            });
        }
    }

    /* Vrne stevilo zapisanih stevk. */
    int zabeleziStevke(Long idBranja) {
        VirMrezeStevk.Nalozena nalozena = vir.trenutna();
        if (nalozena == null) {
            return 0;
        }
        MrezaStevk mreza = nalozena.mreza();
        Integer zapisanih = transakcija.execute(s -> {
            BranjeZapisnika b = branja.findById(idBranja).orElse(null);
            SlikaBranjaZapisnika slika = slike.findById(idBranja).orElse(null);
            if (b == null || slika == null || slika.getPoravnana() == null || b.getCelice() == null
                    || b.getPotrjeno() == null || b.getPrebrano() == null || stevke.existsByIdBranjeZapisnika(idBranja)) {
                return 0;
            }
            Temnost list = temnost(slika.getPoravnana());
            if (list == null) {
                return 0;
            }
            CeliceZapisnikaDto celice = JsonBranja.preberi(b.getCelice(), CeliceZapisnikaDto.class);
            ZapisZapisnikaVnos potrjeno = JsonBranja.preberi(b.getPotrjeno(), ZapisZapisnikaVnos.class);
            PrebranZapisnik prebrano = JsonBranja.preberi(b.getPrebrano(), PrebranZapisnik.class);
            LocalDateTime zdaj = LocalDateTime.now();
            List<StevkaListka> nove = new ArrayList<>();
            for (CeliceZapisnikaDto.Celica c : celice.celice()) {
                if (c.tekma() >= potrjeno.tekme().size()) {
                    continue;
                }
                ZapisZapisnikaVnos.Tekma t = potrjeno.tekme().get(c.tekma());
                if (t.nizi() == null || c.niz() >= t.nizi().size() || t.izidTip() != null) {
                    continue;
                }
                int vrednost = c.domaci() ? t.nizi().get(c.niz()).tocke1() : t.nizi().get(c.niz()).tocke2();
                RazrezCelice.Vsebina v = RazrezCelice.razrezi(list, new CeliceListka.Celica(c.x0(), c.y0(), c.x1(), c.y1()));
                if (v.popravljena()) {
                    continue;
                }
                List<float[]> vhodi = UcenjeIzListkov.stevkeCelice(v, vrednost, mreza);
                if (vhodi == null) {
                    continue;
                }
                Integer prebranaVrednost = null;
                if (c.tekma() < prebrano.tekme().size() && c.niz() < prebrano.tekme().get(c.tekma()).nizi().size()) {
                    PrebranListek.Niz n = prebrano.tekme().get(c.tekma()).nizi().get(c.niz());
                    prebranaVrednost = c.domaci() ? n.levo() : n.desno();
                }
                boolean mocna = !Objects.equals(prebranaVrednost, vrednost);
                String zapis = String.valueOf(vrednost);
                int celica = 10 * c.tekma() + 2 * c.niz() + (c.domaci() ? 0 : 1);
                for (int i = 0; i < vhodi.size(); i++) {
                    int prava = zapis.charAt(i) - '0';
                    nove.add(StevkaListka.zZapisnika(idBranja, celica, UcenjeStevk.vBajte(vhodi.get(i)), prava,
                            UcenjeIzListkov.mrezaPrav(mreza, vhodi.get(i), prava), zdaj).popravljena(mocna));
                }
            }
            stevke.saveAll(nove);
            return nove.size();
        });
        return zapisanih == null ? 0 : zapisanih;
    }

    /* Temnost poravnanega lista iz JPEG v sivinah (kot ga je zapisal bralnik). */
    private static Temnost temnost(byte[] jpeg) {
        try {
            BufferedImage slika = ImageIO.read(new ByteArrayInputStream(jpeg));
            return slika == null ? null : Temnost.iz(slika);
        } catch (IOException e) {
            return null;
        }
    }

    // ---------- Vzdrzevanje (nocno opravilo, zagon) ----------

    public int izbrisiStareSlike() {
        return transakcija.execute(s -> {
            List<Long> idji = branja.zaIzbrisSlike(LocalDateTime.now().minusDays(DNI_HRAMBE));
            if (idji.isEmpty()) {
                return 0;
            }
            slike.izbrisi(idji);
            LocalDateTime zdaj = LocalDateTime.now();
            branja.findAllById(idji).forEach(b -> b.setSlikaIzbrisanaOb(zdaj));
            return idji.size();
        });
    }

    public int pobrisiStareKljucePosiljanja() {
        return transakcija.execute(s -> branja.pobrisiKljuce(LocalDateTime.now().minus(KLJUC_POSILJANJA_VELJA)));
    }

    /* Ob zagonu: branja, ki so ostala v vrsti (izvajalec je v pomnilniku), gredo v napako. */
    public int prekiniOstalaVVrsti() {
        return transakcija.execute(s -> {
            List<Long> idji = branja.vVrsti();
            branja.findAllById(idji).forEach(b -> oznaciNapako(b,
                    "Branje je prekinil ponovni zagon strežnika. Klikni »Preberi znova«."));
            return idji.size();
        });
    }
}
