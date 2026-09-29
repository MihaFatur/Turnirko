/* Stran "Narocnina": pregled narocnine prijavljenega uporabnika in njeno
   upravljanje - preklic ob koncu obdobja, obnova preklicane, preklop
   placevanja mesecno <-> letno ob NASLEDNJI obnovi (igralec) in zamenjava
   organizatorskega paketa (nadgradnja takoj, znizanje ob obnovi).

   Ta razred odloca, KAJ je dovoljeno (stanja, pravila); Stripe klice izvaja
   StripeNarocnine. Stripe ostane vir resnice: po vsakem klicu se v vrstico
   zapise stanje, ki ga vrne Stripe, webhook (PlacilaStoritev) pa isto stanje
   prebere se enkrat - obe poti se zbezita v isti vrednosti, zato zamik ali
   dvojna dostava webhooka ne pokvarita nicesar.

   Nadgradnja (Free -> Premium) je v PlacilaStoritev in gre skozi Stripe
   Checkout; tu je samo tisto, kar ze obstojeca narocnina pocne sama. */
package si.turnirko.storitve;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.NarocninaDto;
import si.turnirko.dto.SpremembaPaketaDto;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.PrepovedanoIzjema;
import si.turnirko.modeli.CiklusPlacila;
import si.turnirko.modeli.Narocnina;
import si.turnirko.modeli.Paket;
import si.turnirko.modeli.StatusNarocnine;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.NarocninaRepozitorij;

@Service
public class UpravljanjeNarocnineStoritev {

    /* Datumi obdobja gredo navzven kot koledarski dnevi po slovenskem casu, ne
       po casu streznika: konec obdobja, ki je v Sloveniji 12. 10. ob 00.30, je
       na strezniku v UTC se 11. 10. in uporabnik bi dobil dan premalo. */
    private static final ZoneId SLOVENIJA = ZoneId.of("Europe/Ljubljana");

    private final NarocninaRepozitorij narocninaRepozitorij;
    private final LastnistvoStoritev lastnistvo;
    private final CenikStoritev cenik;
    private final StripeNarocnine stripe;

    public UpravljanjeNarocnineStoritev(NarocninaRepozitorij narocninaRepozitorij,
                                        LastnistvoStoritev lastnistvo,
                                        CenikStoritev cenik,
                                        StripeNarocnine stripe) {
        this.narocninaRepozitorij = narocninaRepozitorij;
        this.lastnistvo = lastnistvo;
        this.cenik = cenik;
        this.stripe = stripe;
    }

    // ---------- Pregled ----------

    /* Racun brez vrstice v narocnina (igralec Free) ni napaka: odgovor pove,
       da narocnine ni. */
    @Transactional(readOnly = true)
    public NarocninaDto pregled() {
        Uporabnik jaz = zahtevajPrijavo();
        return narocninaRepozitorij.findByUporabnikId(jaz.getId())
                .map(this::dto)
                .orElseGet(NarocninaDto::brez);
    }

    // ---------- Preklic in obnova ----------

    /* Preklic ne ugasne dostopa: narocnina ostane AKTIVNA do konca placanega
       obdobja (Narocnina.jeVeljavna), nato preide na Free. Zabelezen preklop
       cikla odpade - po preklicu ni obnove, ki bi jo preklop spremenil. */
    @Transactional
    public NarocninaDto prekliciOKoncuObdobja() {
        Narocnina n = zahtevajStripeNarocnino();
        if (jePreklicanaInVeljavna(n)) {
            return dto(n);
        }
        if (n.getStatus() != StatusNarocnine.AKTIVNA) {
            throw new DomenskaIzjema("Narocnina ni aktivna, zato je ni mogoce preklicati.");
        }
        // narocnino z urnikom vodi urnik, zato preklop najprej umaknemo
        stripe.sprostiUrnik(n.getStripeNarocninaId());
        StripeNarocnine.Stanje stanje = stripe.nastaviPreklicOKoncu(n.getStripeNarocninaId(), true);
        StripeNarocnine.uskladi(n, stanje);
        n.setStatus(StatusNarocnine.PREKLICANA);
        n.setNaslednjiCiklus(null);
        n.setNaslednjiPaket(null);
        return dto(narocninaRepozitorij.save(n));
    }

    /* Obnova preklicane narocnine ne stane nic: obdobje je ze placano, obnova
       samo umakne preklic. Izbran cikel drugacen od tekocega se zabelezi kot
       preklop ob obnovi (uporabnik ob obnovi izbira placevanje), izbran drug
       organizatorski paket pa kot prehod ob obnovi - tudi visji: danes se
       ne zaracuna nic, obdobje ostane, kot je, novi paket pa zacne z naslednjim.
       Ko je obdobje poteklo, obnove ni: takrat je to nova narocnina prek
       Checkouta. */
    @Transactional
    public NarocninaDto obnovi(CiklusPlacila ciklus) {
        return obnovi(ciklus, null);
    }

    @Transactional
    public NarocninaDto obnovi(CiklusPlacila ciklus, Paket paket) {
        Narocnina n = zahtevajStripeNarocnino();
        if (n.getStatus() == StatusNarocnine.PREKLICANA) {
            if (!jePreklicanaInVeljavna(n)) {
                throw new DomenskaIzjema("Narocnina je ze potekla. Nadgradi znova.");
            }
            StripeNarocnine.Stanje stanje = stripe.nastaviPreklicOKoncu(n.getStripeNarocninaId(), false);
            StripeNarocnine.uskladi(n, stanje);
            n.setStatus(StatusNarocnine.AKTIVNA);
        } else if (n.getStatus() != StatusNarocnine.AKTIVNA) {
            throw new DomenskaIzjema("Narocnine ni mogoce obnoviti.");
        }
        if (ciklus != null && ciklus != n.getCiklus()) {
            zabeleziPreklop(n, ciklus);
        }
        if (paket != null && paket != n.getPaket()) {
            zabeleziPrehod(n, paket);
        }
        return dto(narocninaRepozitorij.save(n));
    }

    // ---------- Zamenjava organizatorskega paketa ----------

    /* Nadgradnja (visji paket) velja TAKOJ: Stripe zaracuna sorazmerno
       doplacilo za preostanek obdobja. Znizanje (nizji paket) velja sele ob
       obnovi in se danes ne zaracuna nic - obdobje je ze placano po visji
       ceni, pravice ostanejo do konca. Isti paket kot tekoci pomeni umik
       zabelezenega znizanja (premislil se je, ne "zamenjava na isto"). */
    @Transactional
    public SpremembaPaketaDto zamenjajPaket(Paket paket) {
        if (paket == null || !paket.jeOrganizatorski()) {
            throw new NeveljavenVnosIzjema("Izberi organizatorski paket: Basic, Plus ali Pro.");
        }
        Narocnina n = zahtevajOrganizatorskoNarocnino();
        if (n.getStatus() != StatusNarocnine.AKTIVNA) {
            throw new DomenskaIzjema("Paket je mogoce zamenjati samo pri aktivni narocnini. "
                    + "Preklicano narocnino najprej obnovi.");
        }
        if (paket == n.getPaket()) {
            return new SpremembaPaketaDto(razveljaviPrehod(), null);
        }
        if (paket.jeVisji(n.getPaket())) {
            double cena = cenik.cena(paket, CiklusPlacila.LETNO, null);
            StripeNarocnine.Nadgradnja nadgradnja =
                    stripe.nadgradiPaket(n.getStripeNarocninaId(), paket, cena);
            StripeNarocnine.uskladi(n, nadgradnja.stanje());
            n.setPaket(paket);
            // nadgradnja preglasi zabelezeno znizanje (Stripe je urnik sprostil)
            n.setNaslednjiPaket(null);
            return new SpremembaPaketaDto(dto(narocninaRepozitorij.save(n)), nadgradnja.doplacilo());
        }
        zabeleziPrehod(n, paket);
        return new SpremembaPaketaDto(dto(narocninaRepozitorij.save(n)), null);
    }

    /* Umik zabelezenega znizanja paketa: narocnina tece naprej z ze
       zacetim paketom in ceno. */
    @Transactional
    public NarocninaDto razveljaviPrehod() {
        Narocnina n = zahtevajOrganizatorskoNarocnino();
        StripeNarocnine.Stanje stanje = stripe.sprostiUrnik(n.getStripeNarocninaId());
        StripeNarocnine.uskladi(n, stanje);
        n.setNaslednjiPaket(null);
        return dto(narocninaRepozitorij.save(n));
    }

    private void zabeleziPrehod(Narocnina n, Paket paket) {
        if (!n.getPaket().jeOrganizatorski() || !paket.jeOrganizatorski()) {
            throw new NeveljavenVnosIzjema("Paket je mogoce zamenjati samo pri organizatorskem paketu.");
        }
        if (n.getStatus() != StatusNarocnine.AKTIVNA) {
            throw new DomenskaIzjema("Paket je mogoce zamenjati samo pri aktivni narocnini. "
                    + "Preklicano narocnino najprej obnovi.");
        }
        if (paket == n.getNaslednjiPaket()) {
            return; // ze zabelezeno
        }
        double cena = cenik.cena(paket, CiklusPlacila.LETNO, null);
        StripeNarocnine.Stanje stanje = stripe.zabeleziPrehod(n.getStripeNarocninaId(), paket, cena);
        StripeNarocnine.uskladi(n, stanje);
        n.setNaslednjiPaket(paket);
    }

    // ---------- Preklop cikla ----------

    /* Preklop na drug cikel zacne veljati ob NASLEDNJI obnovi: mesecno obdobje
       se izteka in se nadaljuje letno (ali obratno). Takoj se ne zaracuna nic.
       Isti cikel kot tekoci pomeni umik zabelezenega preklopa - uporabnik je
       premislil, ne "preklop na isto". */
    @Transactional
    public NarocninaDto preklopi(CiklusPlacila ciklus) {
        if (ciklus == null) {
            throw new NeveljavenVnosIzjema("Izberi mesecno ali letno placevanje.");
        }
        Narocnina n = zahtevajStripeNarocnino();
        if (ciklus == n.getCiklus()) {
            return razveljaviPreklop();
        }
        zabeleziPreklop(n, ciklus);
        return dto(narocninaRepozitorij.save(n));
    }

    /* Umik zabelezenega preklopa: narocnina tece naprej z ze zacetim ciklom. */
    @Transactional
    public NarocninaDto razveljaviPreklop() {
        Narocnina n = zahtevajStripeNarocnino();
        StripeNarocnine.Stanje stanje = stripe.sprostiUrnik(n.getStripeNarocninaId());
        StripeNarocnine.uskladi(n, stanje);
        n.setNaslednjiCiklus(null);
        n.setNaslednjiPaket(null);
        return dto(narocninaRepozitorij.save(n));
    }

    private void zabeleziPreklop(Narocnina n, CiklusPlacila ciklus) {
        if (n.getPaket() != Paket.PREMIUM) {
            throw new NeveljavenVnosIzjema("Preklop placevanja je mogoc samo pri paketu Premium.");
        }
        if (n.getStatus() != StatusNarocnine.AKTIVNA) {
            throw new DomenskaIzjema("Preklop je mogoc samo pri aktivni narocnini. "
                    + "Preklicano narocnino najprej obnovi.");
        }
        if (ciklus == n.getNaslednjiCiklus()) {
            return; // ze zabelezeno
        }
        // Cena po pasu, s katerim je bila narocnina sklenjena (ne po danasnji
        // starosti): kdor je Premium sklenil kot U21, ostane v tem pasu, dokler
        // narocnina traja - isto pravilo kot cena ob nadgradnji (CenikStoritev).
        double cena = cenik.cena(Paket.PREMIUM, ciklus, n.getStarejsiOd21());
        StripeNarocnine.Stanje stanje = stripe.zabeleziPreklop(n.getStripeNarocninaId(), ciklus, cena);
        StripeNarocnine.uskladi(n, stanje);
        n.setNaslednjiCiklus(ciklus);
    }

    // ---------- Pomozno ----------

    private NarocninaDto dto(Narocnina n) {
        boolean aktivna = n.jeVeljavna(LocalDateTime.now());
        return new NarocninaDto(
                n.getPaket(),
                aktivna,
                aktivna && n.getStatus() == StatusNarocnine.PREKLICANA,
                n.getStatus(),
                n.getCiklus(),
                n.getCenaObSklenitvi(),
                n.getStarejsiOd21(),
                dan(n.getZacetekOb()),
                dan(n.obdobjeOd()),
                dan(n.getTrenutnoObdobjeDo()),
                n.getNaslednjiCiklus(),
                n.getNaslednjiPaket(),
                n.getStripeNarocninaId() != null);
    }

    /* Cas iz baze je v casovnem pasu streznika (LocalDateTime brez pasu). */
    private static LocalDate dan(LocalDateTime cas) {
        if (cas == null) {
            return null;
        }
        return cas.atZone(ZoneId.systemDefault()).withZoneSameInstant(SLOVENIJA).toLocalDate();
    }

    private static boolean jePreklicanaInVeljavna(Narocnina n) {
        return n.getStatus() == StatusNarocnine.PREKLICANA && n.jeVeljavna(LocalDateTime.now());
    }

    private Uporabnik zahtevajPrijavo() {
        Uporabnik jaz = lastnistvo.trenutni();
        if (jaz == null) {
            throw new PrepovedanoIzjema("Za to dejanje je potrebna prijava.");
        }
        return jaz;
    }

    /* Narocnina, ki jo je mogoce upravljati: brez Stripe narocnine (npr. leto
       Pro, ki ga je ob migraciji dobil obstojeci organizator) Stripe nima kaj
       spremeniti. */
    private Narocnina zahtevajStripeNarocnino() {
        Uporabnik jaz = zahtevajPrijavo();
        Narocnina n = narocninaRepozitorij.findByUporabnikId(jaz.getId())
                .orElseThrow(() -> new NeveljavenVnosIzjema("Racun nima narocnine."));
        if (n.getStripeNarocninaId() == null) {
            throw new NeveljavenVnosIzjema(
                    "Ta narocnina nima Stripe placila (npr. brezplacno leto Pro), zato je tu ni mogoce spreminjati.");
        }
        return n;
    }

    /* Narocnina organizatorja, ki jo je mogoce upravljati: samo organizator
       (Premium igralca ima svoj potek) in samo z organizatorskim paketom. */
    private Narocnina zahtevajOrganizatorskoNarocnino() {
        Uporabnik jaz = zahtevajPrijavo();
        if (jaz.getVloga() != Vloga.ORGANIZATOR) {
            throw new NeveljavenVnosIzjema("Paket organizatorja lahko zamenja samo organizator.");
        }
        Narocnina n = zahtevajStripeNarocnino();
        if (!n.getPaket().jeOrganizatorski()) {
            throw new NeveljavenVnosIzjema("Racun nima organizatorskega paketa.");
        }
        return n;
    }
}
