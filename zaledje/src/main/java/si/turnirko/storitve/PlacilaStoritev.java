/* Placila prek Stripe Checkout (redirect, gostovana stran - Turnirko nikoli
   ne vidi stevilke kartice) za placljive pakete: Premium igralca in vse tri
   organizatorske pakete.

   Dva zacetka, en webhook:
   - NOVA registracija (zacniRegistracijskoPlacilo): racun SE NE USTVARI tu -
     podatki obrazca gredo v Stripe kot metapodatki seje in racun (v stanju
     CAKA, s kodo za potrditev e-poste) nastane sele v webhooku, ko Stripe
     potrdi "checkout.session.completed". Neuspesen/opuscen nakup zato ne
     pusti "napol" racuna v bazi.
   - NADGRADNJA obstojecega prijavljenega racuna (zacniNadgradnjo): racun ze
     obstaja, metapodatki nosijo samo id racuna in izbran paket.

   Webhook je EDINI vir resnice o stanju narocnine (obdelajDogodek): uspeh
   checkouta jo ustvari, kasnejsi customer.subscription.* in
   invoice.payment_failed jo posodabljajo. Stripova dostava je "vsaj enkrat",
   zato je obdelava idempotentna po stripe_narocnina_id. */
package si.turnirko.storitve;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
import si.turnirko.modeli.CiklusPlacila;
import si.turnirko.modeli.Narocnina;
import si.turnirko.modeli.Paket;
import si.turnirko.modeli.StatusNarocnine;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.NarocninaRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

@Service
public class PlacilaStoritev {

    private static final Logger dnevnik = LoggerFactory.getLogger(PlacilaStoritev.class);

    // Kljuci metapodatkov Stripe seje - Stripe hrani samo nize.
    private static final String M_NAMEN = "namen";
    private static final String M_IME = "ime";
    private static final String M_PRIIMEK = "priimek";
    private static final String M_ID_KLUB = "idKlub";
    private static final String M_EMAIL = "email";
    private static final String M_GESLO_HASH = "gesloHash";
    private static final String M_ORGANIZATOR = "organizator";
    private static final String M_DATUM_ROJSTVA = "datumRojstva";
    private static final String M_EMAIL_SKRBNIKA = "emailSkrbnika";
    private static final String M_PAKET = "paket";
    private static final String M_CIKLUS = "ciklus";
    private static final String M_STAREJSI_OD_21 = "starejsiOd21";
    private static final String M_ID_UPORABNIK = "idUporabnik";

    private static final String NAMEN_REGISTRACIJA = "registracija";
    private static final String NAMEN_NADGRADNJA = "nadgradnja";

    private final String webhookSkrivnost;
    private final String vrniNaKoren;

    private final RegistracijaStoritev registracija;
    private final NarocninaRepozitorij narocninaRepozitorij;
    private final UporabnikRepozitorij uporabnikRepozitorij;
    private final CenikStoritev cenik;
    private final LastnistvoStoritev lastnistvo;
    private final PasswordEncoder kodirnik;

    public PlacilaStoritev(
            @Value("${turnirko.stripe.tajni-kljuc:}") String tajniKljuc,
            @Value("${turnirko.stripe.webhook-skrivnost:}") String webhookSkrivnost,
            @Value("${turnirko.stripe.vrni-na}") String vrniNaKoren,
            RegistracijaStoritev registracija,
            NarocninaRepozitorij narocninaRepozitorij,
            UporabnikRepozitorij uporabnikRepozitorij,
            CenikStoritev cenik,
            LastnistvoStoritev lastnistvo,
            PasswordEncoder kodirnik) {
        Stripe.apiKey = tajniKljuc;
        this.webhookSkrivnost = webhookSkrivnost;
        this.vrniNaKoren = vrniNaKoren;
        this.registracija = registracija;
        this.narocninaRepozitorij = narocninaRepozitorij;
        this.uporabnikRepozitorij = uporabnikRepozitorij;
        this.cenik = cenik;
        this.lastnistvo = lastnistvo;
        this.kodirnik = kodirnik;
    }

    // ---------- Zacetek placila: nova registracija ----------

    /* Preveri vnos (isti validatorji kot brezplacna registracija - da ne
       zaracuna vnosa, ki bi ga tako ali tako zavrnili) in vrne naslov Stripe
       Checkouta. Racun se ustvari sele v webhooku. */
    public PlacilnaSejaDto zacniRegistracijskoPlacilo(PlacanaRegistracijaVnos vnos) {
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
        boolean paketJePremium = vnos.paket() == Paket.PREMIUM;
        if (organizator && paketJePremium) {
            throw new NeveljavenVnosIzjema("Organizator ne more izbrati paketa Premium.");
        }
        if (!organizator && !paketJePremium) {
            throw new NeveljavenVnosIzjema("Igralec lahko izbere samo paket Premium.");
        }

        boolean starejsiOd21 = paketJePremium && cenik.stariEnaindvajset(preverjen.datumRojstva());
        double cena = cenik.cena(vnos.paket(), vnos.ciklus(), starejsiOd21);
        String gesloHash = kodirnik.encode(racun.geslo());

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setCustomerEmail(email)
                .setSuccessUrl(vrniNaKoren + "/registracija/zakljucena?stanje=uspeh")
                .setCancelUrl(vrniNaKoren + "/registracija/zakljucena?stanje=preklic")
                .addLineItem(vrsticaSeje(vnos.paket(), vnos.ciklus(), cena))
                .putMetadata(M_NAMEN, NAMEN_REGISTRACIJA)
                .putMetadata(M_IME, racun.ime().trim())
                .putMetadata(M_PRIIMEK, racun.priimek().trim())
                .putMetadata(M_ID_KLUB, racun.idKlub() == null ? "" : String.valueOf(racun.idKlub()))
                .putMetadata(M_EMAIL, email)
                .putMetadata(M_GESLO_HASH, gesloHash)
                .putMetadata(M_ORGANIZATOR, String.valueOf(organizator))
                .putMetadata(M_DATUM_ROJSTVA,
                        preverjen.datumRojstva() == null ? "" : preverjen.datumRojstva().toString())
                .putMetadata(M_EMAIL_SKRBNIKA,
                        preverjen.emailSkrbnika() == null ? "" : preverjen.emailSkrbnika())
                .putMetadata(M_PAKET, vnos.paket().name())
                .putMetadata(M_CIKLUS, vnos.ciklus() == null ? "" : vnos.ciklus().name())
                .putMetadata(M_STAREJSI_OD_21, String.valueOf(starejsiOd21))
                .build();
        return new PlacilnaSejaDto(ustvariSejo(params));
    }

    // ---------- Zacetek placila: nadgradnja obstojecega racuna ----------

    public PlacilnaSejaDto zacniNadgradnjo(NadgradnjaVnos vnos) {
        Uporabnik jaz = zahtevajPrijavo();
        if (vnos.paket() == Paket.BREZPLACNO) {
            throw new NeveljavenVnosIzjema("Za prehod na brezplacno ne potrebujes placila.");
        }
        boolean paketJePremium = vnos.paket() == Paket.PREMIUM;
        if (paketJePremium && jaz.getVloga() != Vloga.IGRALEC) {
            throw new NeveljavenVnosIzjema("Premium je paket za igralca.");
        }
        if (!paketJePremium && jaz.getVloga() != Vloga.ORGANIZATOR) {
            throw new NeveljavenVnosIzjema("Ta paket je za organizatorja.");
        }

        // Starost za ceno je VEDNO iz vpisanega datuma ob registraciji - isto
        // pravilo kot pri novi registraciji, ne iz kasneje povezanega igralca.
        boolean starejsiOd21 = paketJePremium
                && cenik.stariEnaindvajset(jaz.getPrijavljeniDatumRojstva());
        double cena = cenik.cena(vnos.paket(), vnos.ciklus(), starejsiOd21);

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setCustomerEmail(jaz.getUporabniskoIme())
                .setSuccessUrl(vrniNaKoren + "/narocnina?stanje=uspeh")
                .setCancelUrl(vrniNaKoren + "/narocnina?stanje=preklic")
                .addLineItem(vrsticaSeje(vnos.paket(), vnos.ciklus(), cena))
                .putMetadata(M_NAMEN, NAMEN_NADGRADNJA)
                .putMetadata(M_ID_UPORABNIK, String.valueOf(jaz.getId()))
                .putMetadata(M_PAKET, vnos.paket().name())
                .putMetadata(M_CIKLUS, vnos.ciklus() == null ? "" : vnos.ciklus().name())
                .putMetadata(M_STAREJSI_OD_21, String.valueOf(starejsiOd21))
                .build();
        return new PlacilnaSejaDto(ustvariSejo(params));
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

        RegistracijaVnos racun = new RegistracijaVnos(
                meta.get(M_IME), meta.get(M_PRIIMEK),
                prazenVNull(meta.get(M_ID_KLUB)) == null ? null : Long.valueOf(meta.get(M_ID_KLUB)),
                meta.get(M_EMAIL), "placano-preko-stripe",
                Boolean.parseBoolean(meta.get(M_ORGANIZATOR)),
                prazenVNull(meta.get(M_DATUM_ROJSTVA)) == null ? null : LocalDate.parse(meta.get(M_DATUM_ROJSTVA)),
                prazenVNull(meta.get(M_EMAIL_SKRBNIKA)));

        Optional<Uporabnik> nastal = registracija.registrirajPoPlacilu(racun, meta.get(M_GESLO_HASH));
        if (nastal.isEmpty()) {
            dnevnik.warn("Placilo za {} je uspelo, a racun ni nastal (naslov je medtem "
                    + "postal neuporaben) - potrebna rocna obravnava.", meta.get(M_EMAIL));
            return;
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
        n.setPaket(paket);
        n.setCiklus(ciklus);
        n.setCenaObSklenitvi(cenik.cena(paket, ciklus, starejsiOd21));
        n.setStarejsiOd21(paket == Paket.PREMIUM ? starejsiOd21 : null);
        n.setStripeCustomerId(session.getCustomer());
        n.setStripeNarocninaId(session.getSubscription());
        n.setStatus(StatusNarocnine.AKTIVNA);
        n.setZacetekOb(LocalDateTime.now());
        n.setTrenutnoObdobjeDo(obdobjeDo(session.getSubscription()));
        narocninaRepozitorij.save(n);
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
        n.setTrenutnoObdobjeDo(obdobjeDo(subscriptionId));
        narocninaRepozitorij.save(n);
    }

    /* Preklic ob koncu obdobja pusti narocnino AKTIVNO do konca placanega
       casa (Narocnina.jeVeljavna); dejanski konec pride pozneje kot
       customer.subscription.deleted. Reaktivacija (cancel_at_period_end
       nazaj na false) obratno vrne status na AKTIVNA. */
    private void obdelajPosodobitevNarocnine(Subscription sub) {
        narocninaRepozitorij.findByStripeNarocninaId(sub.getId()).ifPresent(n -> {
            boolean preklicOKoncu = Boolean.TRUE.equals(sub.getCancelAtPeriodEnd());
            n.setStatus(preklicOKoncu ? StatusNarocnine.PREKLICANA : StatusNarocnine.AKTIVNA);
            n.setTrenutnoObdobjeDo(obdobjeDoIzSeje(sub));
            narocninaRepozitorij.save(n);
        });
    }

    private void obdelajIzbrisNarocnine(Subscription sub) {
        narocninaRepozitorij.findByStripeNarocninaId(sub.getId()).ifPresent(n -> {
            n.setStatus(StatusNarocnine.PREKLICANA);
            n.setTrenutnoObdobjeDo(LocalDateTime.now());
            narocninaRepozitorij.save(n);
        });
    }

    private void obdelajNeuspeloPlacilo(Invoice invoice) {
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
                                .setName(imePaketa(paket))
                                .build())
                        .setRecurring(SessionCreateParams.LineItem.PriceData.Recurring.builder()
                                .setInterval(interval)
                                .build())
                        .build())
                .build();
    }

    private static String imePaketa(Paket paket) {
        return switch (paket) {
            case PREMIUM -> "Turnirko Premium";
            case ORGANIZATOR_BASIC -> "Turnirko Organizator Basic";
            case ORGANIZATOR_PLUS -> "Turnirko Organizator Plus";
            case ORGANIZATOR_PRO -> "Turnirko Organizator Pro";
            case BREZPLACNO -> throw new IllegalStateException("Brezplacen paket ne gre skozi placilo.");
        };
    }

    private String ustvariSejo(SessionCreateParams params) {
        try {
            return Session.create(params).getUrl();
        } catch (StripeException e) {
            dnevnik.error("Stripe checkout seje ni bilo mogoce ustvariti: {}", e.getMessage());
            throw new DomenskaIzjema("Placila trenutno ni mogoce zaceti. Poskusi kasneje.");
        }
    }

    /* Konec tekocega placanega obdobja - potreben za "Narocnina.jeVeljavna"
       po preklicu. Stripe je s "flexible billing mode" current_period_end
       preselil s Subscription na posamezne postavke (SubscriptionItem);
       Checkout seja tu vedno ustvari natanko eno postavko (vrsticaSeje). */
    private static LocalDateTime obdobjeDoIzSeje(Subscription sub) {
        if (sub.getItems() == null || sub.getItems().getData().isEmpty()) {
            return null;
        }
        Long konec = sub.getItems().getData().get(0).getCurrentPeriodEnd();
        return konec == null ? null
                : LocalDateTime.ofInstant(Instant.ofEpochSecond(konec), ZoneId.systemDefault());
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

    private LocalDateTime obdobjeDo(String subscriptionId) {
        try {
            return obdobjeDoIzSeje(Subscription.retrieve(subscriptionId));
        } catch (StripeException e) {
            dnevnik.warn("Obdobja narocnine {} ni bilo mogoce prebrati: {}", subscriptionId, e.getMessage());
            return null;
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
