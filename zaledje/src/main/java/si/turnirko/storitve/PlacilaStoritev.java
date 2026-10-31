/* Placila prek Stripe Checkout (redirect, gostovana stran - Turnirko nikoli
   ne vidi stevilke kartice) za placljive pakete: Premium igralca in vse tri
   organizatorske pakete.

   Dva zacetka, en webhook:
   - NOVA registracija (zacniRegistracijskoPlacilo): racun SE NE USTVARI tu -
     podatki obrazca pocakajo v nasi bazi (CakajocaRegistracija), v Stripe gre
     samo nakljucen zeton, in racun (v stanju CAKA, s kodo za potrditev
     e-poste) nastane sele v webhooku, ko Stripe potrdi
     "checkout.session.completed". Neuspesen/opuscen nakup zato ne pusti
     "napol" racuna v bazi, nocno ciscenje pa pobrise njegovo cakajoco vrstico.
     Gesla, datuma rojstva in naslova skrbnika v Stripu ni: njegove dogodke
     bere tudi Moj Racun (davcno potrjeni racuni) in kdor koli upravlja
     Stripov racun.
   - NADGRADNJA obstojecega prijavljenega racuna (zacniNadgradnjo): racun ze
     obstaja, metapodatki nosijo samo id racuna in izbran paket.

   Webhook je EDINI vir resnice o stanju narocnine (obdelajDogodek): uspeh
   checkouta jo ustvari, kasnejsi customer.subscription.* in
   invoice.payment_failed jo posodabljajo. Stripova dostava je "vsaj enkrat",
   zato je obdelava idempotentna po stripe_narocnina_id. */
package si.turnirko.storitve;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stripe.Stripe;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;

import si.turnirko.dto.NadgradnjaVnos;
import si.turnirko.dto.PlacanaRegistracijaVnos;
import si.turnirko.dto.PlacilnaSejaDto;
import si.turnirko.dto.RegistracijaVnos;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.PrepovedanoIzjema;
import si.turnirko.modeli.CakajocaRegistracija;
import si.turnirko.modeli.CiklusPlacila;
import si.turnirko.modeli.KodaPopusta;
import si.turnirko.modeli.Narocnina;
import si.turnirko.modeli.Paket;
import si.turnirko.modeli.StatusNarocnine;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.CakajocaRegistracijaRepozitorij;
import si.turnirko.repozitoriji.NarocninaRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

@Service
public class PlacilaStoritev {

    private static final Logger dnevnik = LoggerFactory.getLogger(PlacilaStoritev.class);

    // Kljuci metapodatkov Stripe seje - Stripe hrani samo nize. Osebnih
    // podatkov med njimi ni (glej uvod); e-posta gre kot customer_email,
    // ker jo Stripe in Moj Racun potrebujeta za racun.
    static final String M_NAMEN = "namen";
    static final String M_ZETON = "registracija";
    private static final String M_PAKET = "paket";
    private static final String M_CIKLUS = "ciklus";
    private static final String M_STAREJSI_OD_21 = "starejsiOd21";
    private static final String M_ID_UPORABNIK = "idUporabnik";

    static final String NAMEN_REGISTRACIJA = "registracija";
    private static final String NAMEN_NADGRADNJA = "nadgradnja";

    private static final SecureRandom NAKLJUCJE = new SecureRandom();

    private final String webhookSkrivnost;
    private final String vrniNaKoren;

    private final RegistracijaStoritev registracija;
    private final CakajocaRegistracijaRepozitorij cakajoce;
    private final NarocninaRepozitorij narocninaRepozitorij;
    private final UporabnikRepozitorij uporabnikRepozitorij;
    private final CenikStoritev cenik;
    private final LastnistvoStoritev lastnistvo;
    private final PasswordEncoder kodirnik;
    private final StripeNarocnine stripe;
    private final RacunDnevnikStoritev racunDnevnik;
    private final KodePopustaStoritev kodePopusta;
    private final PodporaKlubaStoritev podporaKluba;

    public PlacilaStoritev(
            @Value("${turnirko.stripe.tajni-kljuc:}") String tajniKljuc,
            @Value("${turnirko.stripe.webhook-skrivnost:}") String webhookSkrivnost,
            @Value("${turnirko.stripe.vrni-na}") String vrniNaKoren,
            RegistracijaStoritev registracija,
            CakajocaRegistracijaRepozitorij cakajoce,
            NarocninaRepozitorij narocninaRepozitorij,
            UporabnikRepozitorij uporabnikRepozitorij,
            CenikStoritev cenik,
            LastnistvoStoritev lastnistvo,
            PasswordEncoder kodirnik,
            StripeNarocnine stripe,
            RacunDnevnikStoritev racunDnevnik,
            KodePopustaStoritev kodePopusta,
            PodporaKlubaStoritev podporaKluba) {
        Stripe.apiKey = tajniKljuc;
        this.webhookSkrivnost = webhookSkrivnost;
        this.vrniNaKoren = vrniNaKoren;
        this.registracija = registracija;
        this.cakajoce = cakajoce;
        this.narocninaRepozitorij = narocninaRepozitorij;
        this.uporabnikRepozitorij = uporabnikRepozitorij;
        this.cenik = cenik;
        this.lastnistvo = lastnistvo;
        this.kodirnik = kodirnik;
        this.stripe = stripe;
        this.racunDnevnik = racunDnevnik;
        this.kodePopusta = kodePopusta;
        this.podporaKluba = podporaKluba;
    }

    // ---------- Zacetek placila: nova registracija ----------

    /* Preveri vnos (isti validatorji kot brezplacna registracija - da ne
       zaracuna vnosa, ki bi ga tako ali tako zavrnili) in vrne naslov Stripe
       Checkouta. Racun se ustvari sele v webhooku. */
    public PlacilnaSejaDto zacniRegistracijskoPlacilo(PlacanaRegistracijaVnos vnos, String naslovIp) {
        PripravljenaRegistracija pripravljena = pripraviRegistracijo(vnos, naslovIp);
        try {
            return new PlacilnaSejaDto(ustvariSejo(pripravljena.seja()));
        } catch (DomenskaIzjema e) {
            // seje ni, placila torej ne bo - vrstica nima cesa cakati
            cakajoce.deleteById(pripravljena.cakajoca().getId());
            throw e;
        }
    }

    /* Cakajoca vrstica in parametri seje, preden gre karkoli v Stripe - locen
       korak, da test prebere, kaj bi Stripe dobil (toMap()). */
    record PripravljenaRegistracija(CakajocaRegistracija cakajoca, SessionCreateParams seja) {}

    PripravljenaRegistracija pripraviRegistracijo(PlacanaRegistracijaVnos vnos) {
        return pripraviRegistracijo(vnos, null);
    }

    PripravljenaRegistracija pripraviRegistracijo(PlacanaRegistracijaVnos vnos, String naslovIp) {
        if (vnos.paket() == Paket.BREZPLACNO) {
            throw new NeveljavenVnosIzjema("Brezplacna registracija ne gre prek placila.");
        }
        RegistracijaVnos racun = vnos.racun();
        RegistracijaStoritev.PreverjenVnos preverjen = registracija.preveriPolja(racun);
        String email = RegistracijaStoritev.normalizirajEmail(racun.email());
        if (!registracija.jeEmailProstZaRegistracijo(email)) {
            throw new NeveljavenVnosIzjema("Ta e-posta je ze v uporabi.");
        }

        boolean organizator = Boolean.TRUE.equals(racun.organizator());
        boolean navijac = racun.jeNavijac();
        boolean paketJePremium = vnos.paket() == Paket.PREMIUM;
        // navijac placuje samo svoj paket (V58), nihce drug ga ne more kupiti
        if (navijac != (vnos.paket() == Paket.NAVIJAC)) {
            throw new NeveljavenVnosIzjema(navijac
                    ? "Navijač lahko izbere samo paket Navijač."
                    : "Paket Navijač je za račun navijača.");
        }
        if (organizator && paketJePremium) {
            throw new NeveljavenVnosIzjema("Organizator ne more izbrati paketa Premium.");
        }
        if (!organizator && !navijac && !paketJePremium) {
            throw new NeveljavenVnosIzjema("Igralec lahko izbere samo paket Premium.");
        }

        boolean starejsiOd21 = paketJePremium && cenik.stariEnaindvajset(preverjen.datumRojstva());
        double cena = cenik.cena(vnos.paket(), vnos.ciklus(), starejsiOd21);

        /* Koda popusta (V53): preverba in kupon nastaneta PRED vrstico, ki kodo drzi -
           ce Stripe kupona ne ustvari, ne ostane nic. Cakajoca vrstica rezervira mesto
           pri najvec uporab do placila; kodo po placilu zapise webhook. V Stripe gre
           samo kupon (njegovo ime je koda, ki jo kupec pozna), metapodatki ostanejo
           enaki. */
        KodaPopusta koda = kodePopusta.zaPlacljivoRegistracijo(racun.kodaPopusta(),
                KodePopustaStoritev.vlogaRegistracije(organizator, navijac),
                vnos.paket(), vnos.ciklus(), starejsiOd21, naslovIp, email).orElse(null);
        String kupon = koda == null ? null : kodePopusta.stripeKupon(koda);

        CakajocaRegistracija nova = new CakajocaRegistracija(
                novZeton(), racun.ime().trim(), racun.priimek().trim(), racun.idKlub(), email,
                kodirnik.encode(racun.geslo()), organizator, preverjen.datumRojstva(),
                preverjen.emailSkrbnika(), LocalDateTime.now());
        if (koda != null) {
            nova.setIdKoda(koda.getId());
        }
        nova.setNavijac(navijac);
        CakajocaRegistracija cakajoca = cakajoce.save(nova);

        SessionCreateParams.Builder seja = osnovaSeje(vnos.paket(), vnos.ciklus())
                .setCustomerEmail(email)
                .setSuccessUrl(vrniNaKoren + "/registracija/zakljucena?stanje=uspeh")
                .setCancelUrl(vrniNaKoren + "/registracija/zakljucena?stanje=preklic")
                .addLineItem(vrsticaSeje(vnos.paket(), vnos.ciklus(), cena))
                .putMetadata(M_NAMEN, NAMEN_REGISTRACIJA)
                .putMetadata(M_ZETON, cakajoca.getZeton())
                .putMetadata(M_PAKET, vnos.paket().name())
                .putMetadata(M_CIKLUS, vnos.ciklus() == null ? "" : vnos.ciklus().name())
                .putMetadata(M_STAREJSI_OD_21, String.valueOf(starejsiOd21));
        if (kupon != null) {
            seja.addDiscount(SessionCreateParams.Discount.builder().setCoupon(kupon).build());
        }
        return new PripravljenaRegistracija(cakajoca, seja.build());
    }

    /* 192 nakljucnih bitov: zeton sam ne odpre nicesar, a ga ne sme uganiti
       nihce, ki bi z njim podtaknil svoje placilo tuji registraciji. */
    private static String novZeton() {
        byte[] bajti = new byte[24];
        NAKLJUCJE.nextBytes(bajti);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bajti);
    }

    // ---------- Zacetek placila: nadgradnja obstojecega racuna ----------

    /* Druga Stripe narocnina ob zivi prvi pomeni dvojno zaracunavanje: stara
       tece naprej, nova pa v bazi prepise vrstico in stara ostane brez lastnika,
       ki bi jo videl ali preklical. Najnevarnejsa je ZAPADLA: stran Narocnina
       takrat pokaze stanje brez paketa, torej ponudbo nakupa, Stripe pa staro
       narocnino se ponavlja in jo lahko uspesno zaracuna. Potekla preklicana
       narocnina je konec in je nov nakup pravilen ("nova narocnina prek
       Checkouta"); narocnina brez Stripe placila (brezplacno leto Pro) tudi. */
    private static void zavrniCeJeNarocninaZiva(Narocnina n) {
        if (n.getStripeNarocninaId() == null) {
            return;
        }
        if (n.getStatus() == StatusNarocnine.ZAPADLA) {
            throw new DomenskaIzjema("Zadnje placilo narocnine ni uspelo. Posodobi kartico prek gumba "
                    + "\"Posodobi kartico v Stripe\" - ko se placilo izvede, se paket vklopi sam.");
        }
        if (n.jeVeljavna(LocalDateTime.now())) {
            throw new DomenskaIzjema("Narocnino ze imas. Spremenis jo lahko na strani Narocnina.");
        }
    }

    public PlacilnaSejaDto zacniNadgradnjo(NadgradnjaVnos vnos) {
        return new PlacilnaSejaDto(ustvariSejo(pripraviNadgradnjo(zahtevajPrijavo(), vnos)));
    }

    /* Parametri Stripe seje nadgradnje, preden gre karkoli v Stripe - locen korak, da test
       prebere, kaj bi Stripe dobil (toMap()), kot pri registraciji. */
    SessionCreateParams pripraviNadgradnjo(Uporabnik jaz, NadgradnjaVnos vnos) {
        if (vnos.paket() == Paket.BREZPLACNO) {
            throw new NeveljavenVnosIzjema("Za prehod na brezplacno ne potrebujes placila.");
        }
        boolean paketJePremium = vnos.paket() == Paket.PREMIUM;
        boolean paketJeNavijac = vnos.paket() == Paket.NAVIJAC;
        if (paketJePremium && jaz.getVloga() != Vloga.IGRALEC) {
            throw new NeveljavenVnosIzjema("Premium je paket za igralca.");
        }
        if (paketJeNavijac && jaz.getVloga() != Vloga.NAVIJAC) {
            throw new NeveljavenVnosIzjema("Paket Navijač je za račun navijača.");
        }
        if (!paketJePremium && !paketJeNavijac && jaz.getVloga() != Vloga.ORGANIZATOR) {
            throw new NeveljavenVnosIzjema("Ta paket je za organizatorja.");
        }

        narocninaRepozitorij.findByUporabnikId(jaz.getId()).ifPresent(PlacilaStoritev::zavrniCeJeNarocninaZiva);

        // Starost za ceno je VEDNO iz vpisanega datuma ob registraciji - isto
        // pravilo kot pri novi registraciji, ne iz kasneje povezanega igralca.
        boolean starejsiOd21 = paketJePremium
                && cenik.stariEnaindvajset(jaz.getPrijavljeniDatumRojstva());
        double cena = cenik.cena(vnos.paket(), vnos.ciklus(), starejsiOd21);

        SessionCreateParams.Builder seja = osnovaSeje(vnos.paket(), vnos.ciklus())
                .setCustomerEmail(jaz.getUporabniskoIme())
                .setSuccessUrl(vrniNaKoren + "/narocnina?stanje=uspeh")
                .setCancelUrl(vrniNaKoren + "/narocnina?stanje=preklic")
                .addLineItem(vrsticaSeje(vnos.paket(), vnos.ciklus(), cena))
                .putMetadata(M_NAMEN, NAMEN_NADGRADNJA)
                .putMetadata(M_ID_UPORABNIK, String.valueOf(jaz.getId()))
                .putMetadata(M_PAKET, vnos.paket().name())
                .putMetadata(M_CIKLUS, vnos.ciklus() == null ? "" : vnos.ciklus().name())
                .putMetadata(M_STAREJSI_OD_21, String.valueOf(starejsiOd21));
        /* Popust kluba (V61): organizator kluba, ki ima dovolj podpornikov s paketom Navijac, dobi
           odstotek na prvo placilo (Stripov kupon once, kot koda popusta). Obnova je po polni ceni. */
        int popustKluba = vnos.paket().jeOrganizatorski() ? podporaKluba.popustZaOrganizatorja(jaz) : 0;
        if (popustKluba > 0) {
            seja.addDiscount(SessionCreateParams.Discount.builder()
                    .setCoupon(podporaKluba.stripeKupon(popustKluba)).build());
        }
        return seja.build();
    }

    // ---------- Stripe Billing Portal (upravljanje/preklic) ----------

    public PlacilnaSejaDto zacniPortal() {
        Uporabnik jaz = zahtevajPrijavo();
        Narocnina n = narocninaRepozitorij.findByUporabnikId(jaz.getId())
                .orElseThrow(() -> new NeveljavenVnosIzjema("Racun nima narocnine."));
        if (n.getStripeCustomerId() == null) {
            throw new NeveljavenVnosIzjema(
                    "Ta narocnina nima Stripe placila za upravljanje (npr. brezplacno leto Pro).");
        }
        com.stripe.param.billingportal.SessionCreateParams params =
                com.stripe.param.billingportal.SessionCreateParams.builder()
                        .setCustomer(n.getStripeCustomerId())
                        .setReturnUrl(vrniNaKoren + "/narocnina")
                        .build();
        try {
            com.stripe.model.billingportal.Session seja =
                    com.stripe.model.billingportal.Session.create(params);
            return new PlacilnaSejaDto(seja.getUrl());
        } catch (StripeException e) {
            dnevnik.error("Stripe portala ni bilo mogoce odpreti: {}", e.getMessage());
            throw new DomenskaIzjema("Portala trenutno ni mogoce odpreti. Poskusi kasneje.");
        }
    }

    // ---------- Webhook ----------

    @Transactional
    public void obdelajDogodek(String telo, String podpis) {
        Event event;
        try {
            event = Webhook.constructEvent(telo, podpis, webhookSkrivnost);
        } catch (SignatureVerificationException e) {
            throw new NeveljavenVnosIzjema("Neveljaven podpis Stripe webhooka.");
        }

        switch (event.getType()) {
            case "checkout.session.completed" -> obdelajZakljucenoSejo((Session) objekt(event));
            case "customer.subscription.updated" -> obdelajPosodobitevNarocnine((Subscription) objekt(event));
            case "customer.subscription.deleted" -> obdelajIzbrisNarocnine((Subscription) objekt(event));
            case "invoice.payment_failed" -> obdelajNeuspeloPlacilo((Invoice) objekt(event));
            default -> dnevnik.debug("Neobravnavan Stripe dogodek: {}", event.getType());
        }
    }

    private void obdelajZakljucenoSejo(Session session) {
        Map<String, String> meta = session.getMetadata();
        String namen = meta.get(M_NAMEN);
        if (NAMEN_NADGRADNJA.equals(namen)) {
            obdelajNadgradnjo(session, meta);
            return;
        }
        if (!NAMEN_REGISTRACIJA.equals(namen)) {
            return; // dogodek ni nas (npr. drug produkt v istem Stripe racunu)
        }
        if (narocninaRepozitorij.findByStripeNarocninaId(session.getSubscription()).isPresent()) {
            return; // Stripe dostavi "vsaj enkrat" - ze obdelano
        }

        Optional<CakajocaRegistracija> najdena = Optional.ofNullable(prazenVNull(meta.get(M_ZETON)))
                .flatMap(cakajoce::findByZeton);
        if (najdena.isEmpty()) {
            dnevnik.warn("Placilo seje {} ({}) je uspelo, a racun ni nastal (cakajoce registracije "
                    + "ni vec - potekla ali seja izpred V48) - potrebna rocna obravnava.",
                    session.getId(), session.getCustomerEmail());
            return;
        }
        CakajocaRegistracija c = najdena.get();
        RegistracijaVnos racun = new RegistracijaVnos(
                c.getIme(), c.getPriimek(), c.getIdKlub(), c.getEmail(), "placano-preko-stripe",
                c.isOrganizator(), c.getDatumRojstva(), c.getEmailSkrbnika(), null, c.isNavijac());

        Optional<Uporabnik> nastal = registracija.registrirajPoPlacilu(racun, c.getGesloHash());
        // podatki so zdaj v racunu ali pa racuna ne bo - kopija ne ostane
        cakajoce.delete(c);
        if (nastal.isEmpty()) {
            dnevnik.warn("Placilo za {} je uspelo, a racun ni nastal (naslov je medtem "
                    + "postal neuporaben) - potrebna rocna obravnava.", c.getEmail());
            return;
        }
        if (c.getIdKoda() != null) {
            kodePopusta.zabeleziUporaboPoPlacilu(nastal.get(), c.getIdKoda());
        }
        ustvariNarocnino(nastal.get(), session.getCustomer(), session.getSubscription(),
                Paket.valueOf(meta.get(M_PAKET)),
                prazenVNull(meta.get(M_CIKLUS)) == null ? null : CiklusPlacila.valueOf(meta.get(M_CIKLUS)),
                Boolean.parseBoolean(meta.get(M_STAREJSI_OD_21)));
    }

    private void obdelajNadgradnjo(Session session, Map<String, String> meta) {
        if (narocninaRepozitorij.findByStripeNarocninaId(session.getSubscription()).isPresent()) {
            return;
        }
        Long idUporabnik = Long.valueOf(meta.get(M_ID_UPORABNIK));
        Uporabnik u = uporabnikRepozitorij.findById(idUporabnik).orElse(null);
        if (u == null) {
            dnevnik.warn("Nadgradnja za racun {}, ki ne obstaja vec - placilo ostane brez ucinka v aplikaciji.",
                    idUporabnik);
            return;
        }
        Paket paket = Paket.valueOf(meta.get(M_PAKET));
        CiklusPlacila ciklus = prazenVNull(meta.get(M_CIKLUS)) == null
                ? null : CiklusPlacila.valueOf(meta.get(M_CIKLUS));
        boolean starejsiOd21 = Boolean.parseBoolean(meta.get(M_STAREJSI_OD_21));

        Narocnina n = narocninaRepozitorij.findByUporabnikId(idUporabnik)
                .orElseGet(() -> new Narocnina(u, paket));
        if (n.getStripeNarocninaId() != null && !n.getStripeNarocninaId().equals(session.getSubscription())
                && (n.getStatus() == StatusNarocnine.ZAPADLA || n.jeVeljavna(LocalDateTime.now()))) {
            // zavora v zacniNadgradnjo to prepreci; ostane tek dveh hkrati odprtih Checkoutov
            dnevnik.warn("Racun {} je ob zivi narocnini {} placal se {} - stara tece naprej v Stripu in jo je "
                    + "treba preklicati rocno (ali vrniti novo placilo).",
                    idUporabnik, n.getStripeNarocninaId(), session.getSubscription());
        }
        n.setPaket(paket);
        n.setCiklus(ciklus);
        n.setCenaObSklenitvi(cenik.cena(paket, ciklus, starejsiOd21));
        n.setStarejsiOd21(paket == Paket.PREMIUM ? starejsiOd21 : null);
        n.setStripeCustomerId(session.getCustomer());
        n.setStripeNarocninaId(session.getSubscription());
        n.setStatus(StatusNarocnine.AKTIVNA);
        n.setZacetekOb(LocalDateTime.now());
        // nova narocnina nima dogovorjenega preklopa ne znizanja (stara, potekla, ju je imela lahko)
        n.setNaslednjiCiklus(null);
        n.setNaslednjiPaket(null);
        zapisiObdobje(n, session.getSubscription());
        narocninaRepozitorij.save(n);
        racunDnevnik.zabelezi(u.getId(), RacunDnevnikStoritev.opisPlacila(paket, ciklus));
    }

    private void ustvariNarocnino(Uporabnik uporabnik, String customerId, String subscriptionId,
                                  Paket paket, CiklusPlacila ciklus, boolean starejsiOd21) {
        Narocnina n = new Narocnina(uporabnik, paket);
        n.setCiklus(ciklus);
        n.setCenaObSklenitvi(cenik.cena(paket, ciklus, starejsiOd21));
        n.setStarejsiOd21(paket == Paket.PREMIUM ? starejsiOd21 : null);
        n.setStripeCustomerId(customerId);
        n.setStripeNarocninaId(subscriptionId);
        n.setStatus(StatusNarocnine.AKTIVNA);
        n.setZacetekOb(LocalDateTime.now());
        zapisiObdobje(n, subscriptionId);
        narocninaRepozitorij.save(n);
        racunDnevnik.zabelezi(uporabnik.getId(), RacunDnevnikStoritev.opisPlacila(paket, ciklus));
    }

    /* Preklic ob koncu obdobja pusti narocnino AKTIVNO do konca placanega
       casa (Narocnina.jeVeljavna); dejanski konec pride pozneje kot
       customer.subscription.deleted. Reaktivacija (cancel_at_period_end
       nazaj na false) obratno vrne status na AKTIVNA.

       Isti dogodek pride tudi ob vsaki obnovi in ob izvedenem preklopu cikla
       (Stripe urnik zamenja postavko), zato iz njega beremo se obdobje, cikel
       in ceno: preklop je z njim izveden in zabelezena namera odpade. Namera
       odpade tudi, ko urnika ni vec (uporabnik ga je umaknil) - Stripe tedaj
       prispe brez polja schedule. Vrstni red ni pomemben: storitev za
       upravljanje narocnine zapise isto stanje, ki ga vrne Stripe. */
    private void obdelajPosodobitevNarocnine(Subscription sub) {
        if ("canceled".equals(sub.getStatus())) {
            // Stripe je narocnino koncal (npr. po zadnjem neuspelem poskusu): enako kot izbris
            obdelajIzbrisNarocnine(sub);
            return;
        }
        narocninaRepozitorij.findByStripeNarocninaId(sub.getId()).ifPresent(n -> {
            n.setStatus(statusIzStripa(sub));
            LocalDateTime staroObdobjeDo = n.getTrenutnoObdobjeDo();
            StripeNarocnine.Stanje stanje = StripeNarocnine.stanje(sub);
            StripeNarocnine.uskladi(n, stanje);
            boolean preklopIzveden = stanje.urnikId() != null && n.getNaslednjiCiklus() != null
                    && n.getNaslednjiCiklus() == n.getCiklus();
            if (stanje.urnikId() == null || preklopIzveden) {
                n.setNaslednjiCiklus(null);
            }
            boolean prehodIzveden = jeZnizanjeIzvedeno(n, staroObdobjeDo, stanje);
            if (prehodIzveden) {
                n.setPaket(n.getNaslednjiPaket());
                n.setNaslednjiPaket(null);
            } else if (stanje.urnikId() == null) {
                n.setNaslednjiPaket(null);
            }
            narocninaRepozitorij.save(n);
            if (preklopIzveden || prehodIzveden) {
                sprostiUrnikPoPreklopu(n);
            }
        });
    }

    /* Lokalno stanje se izpelje iz STRIPOVEGA statusa narocnine, ne samo iz
       preklica ob koncu obdobja: past_due in unpaid pomenita neplacano obnovo
       (ZAPADLA - dostop se ugasne, Stripe pa placilo ponavlja), active po
       uspesnem ponovnem poskusu vse vrne. Ob neuspeli obnovi Stripe poslje dva
       dogodka (invoice.payment_failed in ta) in vrstni red ni zagotovljen:
       prej je posodobitev, ki je prisla zadnja, narocnino vrnila na AKTIVNA in
       neplacnik je obdrzal dostop. Brez statusa (starejsa oblika dogodka) velja
       stara presoja po preklicu. */
    static StatusNarocnine statusIzStripa(Subscription sub) {
        String status = sub.getStatus();
        if ("past_due".equals(status) || "unpaid".equals(status)) {
            return StatusNarocnine.ZAPADLA;
        }
        return Boolean.TRUE.equals(sub.getCancelAtPeriodEnd())
                ? StatusNarocnine.PREKLICANA : StatusNarocnine.AKTIVNA;
    }

    /* Zabelezeno znizanje paketa je izvedeno, ko se je zacelo NOVO obdobje: urnik
       ob obnovi zamenja postavko, zato Stripe isti dogodek poslje z novim
       obdobjem (zacetek = konec starega). Paketa iz Stripa ne beremo - postavka
       nosi samo ceno, ta pa ni enolicen kljuc, ce se cenik kdaj spremeni -,
       zato ga izda prehod meje obdobja. Navadna obnova brez zabelezenega
       znizanja paketa ne spremeni. Toleranca nekaj minut: zapis v bazi hrani
       cas na minuto. */
    private static boolean jeZnizanjeIzvedeno(Narocnina n, LocalDateTime staroObdobjeDo,
                                              StripeNarocnine.Stanje stanje) {
        return n.getNaslednjiPaket() != null && staroObdobjeDo != null && stanje.obdobjeOd() != null
                && !stanje.obdobjeOd().isBefore(staroObdobjeDo.minusMinutes(5));
    }

    /* Urnik po izvedenem preklopu ostane prikljucen se cel novi cikel (Stripe:
       faza z iterations 1). Prikljucen urnik pa vodi narocnino, zato bi v Stripe
       portalu ne bilo mogoce niti preklicati - sprostimo ga takoj, ko je preklop
       izveden. Samo takrat: ob nastanku urnika (isti dogodek) bi sprostitev
       odnesla ravnokar zabelezen preklop. Neuspeh je brez posledic za dostop:
       naslednji preklop ali preklic urnik vseeno sprosti sam. */
    private void sprostiUrnikPoPreklopu(Narocnina n) {
        try {
            stripe.sprostiUrnik(n.getStripeNarocninaId());
        } catch (DomenskaIzjema e) {
            dnevnik.warn("Urnika narocnine {} po izvedenem preklopu ni bilo mogoce sprostiti: {}",
                    n.getStripeNarocninaId(), e.getMessage());
        }
    }

    private void obdelajIzbrisNarocnine(Subscription sub) {
        narocninaRepozitorij.findByStripeNarocninaId(sub.getId()).ifPresent(n -> {
            n.setStatus(StatusNarocnine.PREKLICANA);
            n.setTrenutnoObdobjeDo(LocalDateTime.now());
            narocninaRepozitorij.save(n);
        });
    }

    /* Dostop ugasne samo neuspela OBNOVA. Doplacilo ob nadgradnji organizatorja
       (subscription_update) Stripe ob zavrnjeni kartici zavrne skupaj z
       nadgradnjo: narocnina tece naprej po starem in ostane placana, zato je
       ne smemo oznaciti kot zapadlo. */
    private void obdelajNeuspeloPlacilo(Invoice invoice) {
        if (!"subscription_cycle".equals(invoice.getBillingReason())) {
            dnevnik.info("Neuspelo placilo racuna {} ({}) ni obnova - narocnina se ne spremeni.",
                    invoice.getId(), invoice.getBillingReason());
            return;
        }
        String subscriptionId = subscriptionIzRacuna(invoice);
        if (subscriptionId == null) {
            return;
        }
        narocninaRepozitorij.findByStripeNarocninaId(subscriptionId).ifPresent(n -> {
            n.setStatus(StatusNarocnine.ZAPADLA);
            narocninaRepozitorij.save(n);
        });
    }

    // ---------- Pomozno ----------

    private Uporabnik zahtevajPrijavo() {
        Uporabnik jaz = lastnistvo.trenutni();
        if (jaz == null) {
            throw new PrepovedanoIzjema("Za to dejanje je potrebna prijava.");
        }
        return jaz;
    }

    /* Skupni del obeh zacetkov placila (nova registracija, nadgradnja): kar kupec
       vidi in mora potrditi na Stripovi strani. Cisto sestavljanje parametrov
       brez klica Stripa, zato ga test prebere prek toMap().

       - Samo kartica (Apple Pay in Google Pay sta del nje): kartica je po
         ZDavPR placilo z gotovino, zato vsako placilo dobi davcno potrjen
         racun. Drugi nacini (npr. SEPA) bi terjali posebno presojo.
       - Soglasje s pogoji je obvezno; povezava na /pogoji je v besedilu, URL
         pogojev mora biti tudi v Stripovi nadzorni plosci (Public details),
         sicer Stripe seje ne ustvari - velja za testni in zivi nacin.
       - Organizator je pogosto klub ali podjetje: racun brez naslova in davcne
         stevilke kupca knjigovodstvu kluba ne koristi. Igralec tega ne
         potrebuje (en obrazec manj pri nakupu za nekaj evrov). */
    SessionCreateParams.Builder osnovaSeje(Paket paket, CiklusPlacila ciklus) {
        SessionCreateParams.Builder seja = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                .setLocale(SessionCreateParams.Locale.SL)
                .setConsentCollection(SessionCreateParams.ConsentCollection.builder()
                        .setTermsOfService(SessionCreateParams.ConsentCollection.TermsOfService.REQUIRED)
                        .build())
                .setCustomText(SessionCreateParams.CustomText.builder()
                        .setSubmit(SessionCreateParams.CustomText.Submit.builder()
                                .setMessage(besediloPredPotrditvijo(ciklus))
                                .build())
                        .setTermsOfServiceAcceptance(SessionCreateParams.CustomText.TermsOfServiceAcceptance.builder()
                                .setMessage(besediloSoglasja())
                                .build())
                        .build());
        if (paket.jeOrganizatorski()) {
            seja.setBillingAddressCollection(SessionCreateParams.BillingAddressCollection.REQUIRED)
                    .setTaxIdCollection(SessionCreateParams.TaxIdCollection.builder().setEnabled(true).build());
        }
        return seja;
    }

    /* Nad gumbom za potrditev. Besedilo ne omenja napisa na gumbu: ta pride
       od Stripa in se lahko spremeni. Kar pise, mora drzati tudi za aplikacijo
       (obnova, preklic, odstop - glej strani Pogoji in Narocnina). */
    private static String besediloPredPotrditvijo(CiklusPlacila ciklus) {
        String obnova = ciklus == CiklusPlacila.LETNO ? "vsako leto" : "vsak mesec";
        return "Z gumbom za potrditev spodaj sklenete naročilo z obveznostjo plačila. "
                + "Naročnina se obnavlja " + obnova + ", dokler je ne prekličete (kadar koli na strani Naročnina). "
                + "Račun prejmete po e-pošti.";
    }

    /* Ob kljukici. Storitev se zacne takoj, zato kupec izrecno zahteva zacetek v
       odstopnem roku in ve, da ob odstopu placa sorazmerni del - isto kot
       odstavek o odstopu na strani Pogoji. Pravica do odstopa je potrosnikova
       (organizator je pogosto klub ali podjetje), zato besedilo pravi "kot
       potrosnik" in te pravice ne obljublja vsem. */
    private String besediloSoglasja() {
        return "Strinjam se s [pogoji uporabe](" + vrniNaKoren + "/pogoji) in zahtevam, da se storitev začne takoj. "
                + "Kot potrošnik vem, da ob odstopu v 14 dneh plačam sorazmerni del za že porabljeno obdobje.";
    }

    private SessionCreateParams.LineItem vrsticaSeje(Paket paket, CiklusPlacila ciklus, double cena) {
        long centi = Math.round(cena * 100);
        SessionCreateParams.LineItem.PriceData.Recurring.Interval interval =
                ciklus == CiklusPlacila.LETNO
                        ? SessionCreateParams.LineItem.PriceData.Recurring.Interval.YEAR
                        : SessionCreateParams.LineItem.PriceData.Recurring.Interval.MONTH;
        return SessionCreateParams.LineItem.builder()
                .setQuantity(1L)
                .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                        .setCurrency("eur")
                        .setUnitAmount(centi)
                        .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                .setName(paket.imeIzdelka())
                                .build())
                        .setRecurring(SessionCreateParams.LineItem.PriceData.Recurring.builder()
                                .setInterval(interval)
                                .build())
                        .build())
                .build();
    }

    private String ustvariSejo(SessionCreateParams params) {
        try {
            return Session.create(params).getUrl();
        } catch (StripeException e) {
            dnevnik.error("Stripe checkout seje ni bilo mogoce ustvariti: {}", e.getMessage());
            throw new DomenskaIzjema("Placila trenutno ni mogoce zaceti. Poskusi kasneje.");
        }
    }

    /* Invoice nima vec neposrednega getSubscription() (flexible billing) -
       sled je zdaj pod parent.subscriptionDetails, in sicer samo pri racunih,
       ki sploh izhajajo iz narocnine. */
    private static String subscriptionIzRacuna(Invoice invoice) {
        if (invoice.getParent() == null || invoice.getParent().getSubscriptionDetails() == null) {
            return null;
        }
        return invoice.getParent().getSubscriptionDetails().getSubscription();
    }

    /* Zacetek in konec tekocega placanega obdobja - potrebna za
       "Narocnina.jeVeljavna" po preklicu in za stran "Narocnina". Ce Stripe
       ne odgovori, obdobje ostane prazno; prvi webhook customer.subscription.
       updated ga zapise. (Stripe je s "flexible billing mode" obdobje preselil
       s Subscription na postavko - beri StripeNarocnine.) */
    private void zapisiObdobje(Narocnina n, String subscriptionId) {
        try {
            StripeNarocnine.uskladi(n, StripeNarocnine.stanje(Subscription.retrieve(subscriptionId)));
        } catch (StripeException e) {
            dnevnik.warn("Obdobja narocnine {} ni bilo mogoce prebrati: {}", subscriptionId, e.getMessage());
        }
    }

    /* getObject() primerja event.apiVersion z razlicico, vgrajeno v SDK, in
       ob odsotnem/razlicnem apiVersion celo vrze NullPointerException namesto
       da bi vrnil prazen Optional (znana tezava tega SDK-ja). deserializeUnsafe()
       je Stripova uradno priporocena pot MIMO te preverbe in jo zato
       uporabimo neposredno - api_version dogodka za nas ni pomemben, ker
       beremo samo nekaj poljih (id, metadata), ne celotne oblike objekta. */
    private static StripeObject objekt(Event event) {
        try {
            return event.getDataObjectDeserializer().deserializeUnsafe();
        } catch (EventDataObjectDeserializationException e) {
            throw new NeveljavenVnosIzjema("Stripe dogodka ni bilo mogoce razbrati.");
        }
    }

    private static String prazenVNull(String vrednost) {
        return vrednost == null || vrednost.isBlank() ? null : vrednost;
    }
}
