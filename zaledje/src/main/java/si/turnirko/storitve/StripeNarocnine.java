/* Klici Stripe API-ja za UPRAVLJANJE obstojece narocnine: preklic ob koncu
   obdobja, obnova preklicane in preklop mesecno <-> letno ob naslednji obnovi.

   Zakaj je to locen razred in ne del UpravljanjeNarocnineStoritev: vsak klic
   gre skozi omrezje in pravi kljuc, testi pa ne smejo biti odvisni od nobenega
   (isto nacelo kot pri PlacilaStoritevTest, ki dogodek podpise rocno).
   Storitev odloca, KAJ je dovoljeno; ta razred ve samo, KAKO se to poslje
   Stripu, in ga test nadomesti z laznim.

   Vsak klic vrne Stanje, kakrsno Stripe zdaj ima - storitev zapise tisto in
   ne svojega ugibanja. Stripe ostane edini vir resnice o narocnini; webhook
   (PlacilaStoritev.obdelajPosodobitevNarocnine) isto stanje prebere se enkrat
   in obe poti se zbezita v isti vrednosti. */
package si.turnirko.storitve;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.stripe.exception.StripeException;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
import com.stripe.model.SubscriptionSchedule;
import com.stripe.param.SubscriptionScheduleCreateParams;
import com.stripe.param.SubscriptionScheduleUpdateParams;
import com.stripe.param.SubscriptionUpdateParams;

import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.modeli.CiklusPlacila;
import si.turnirko.modeli.Narocnina;

@Component
public class StripeNarocnine {

    private static final Logger dnevnik = LoggerFactory.getLogger(StripeNarocnine.class);

    /* Kar Stripe pove o narocnini. cena in ciklus sta polji TEKOCE cene (tiste,
       ki se zaracunava zdaj); urnikId je prazen, dokler ni dogovorjenega
       preklopa. Vsa polja razen preklicOKoncu so lahko null, ce Stripe
       postavke nima (ne bi smelo se zgoditi: checkout ustvari natanko eno). */
    public record Stanje(
            LocalDateTime obdobjeOd,
            LocalDateTime obdobjeDo,
            CiklusPlacila ciklus,
            Double cena,
            boolean preklicOKoncu,
            String urnikId) {}

    public Stanje preberi(String narocninaId) {
        return poStripu("branje narocnine", () -> stanje(Subscription.retrieve(narocninaId)));
    }

    /* cancel_at_period_end: narocnina ostane aktivna do konca placanega
       obdobja in se potem ne obnovi; z false se preklic umakne (dokler obdobje
       ni poteklo). Narocnino z urnikom vodi urnik, ne narocnina sama: Stripe
       zavrne "updating any cancelation behavior directly is not allowed"
       (preverjeno na testnem nacinu), zato storitev pred preklicem urnik
       sprosti (sprostiUrnik) - tudi tistega, ki je ostal po izvedenem
       preklopu. */
    public Stanje nastaviPreklicOKoncu(String narocninaId, boolean preklic) {
        return poStripu("preklic narocnine", () -> {
            Subscription sub = Subscription.retrieve(narocninaId);
            Subscription posodobljena = sub.update(
                    SubscriptionUpdateParams.builder().setCancelAtPeriodEnd(preklic).build());
            return stanje(posodobljena);
        });
    }

    /* Preklop cikla ob NASLEDNJI obnovi = Stripe subscription schedule z dvema
       fazama: tekoca (nespremenjena do konca obdobja) in nova z drugim
       intervalom in ceno. Takoj se ne zgodi nic - ne zaracuna se ne razlika ne
       povracilo (proration none); novo obdobje se zacne ob poteku starega.
       Preverjeno na Stripovem testnem nacinu s testno uro: po koncu obdobja je
       racun natanko ena postavka po novi ceni, brez obracuna razlike.

       Nova cena je price_data (kot pri checkoutu), zato gre za nov Price na
       ISTEM izdelku: v Stripe nadzorni plosci ostane ena vrstica izdelka
       "Turnirko Premium". Zadnja faza traja en cikel (iterations 1) in se
       potem sprosti (end_behavior release) - narocnina od takrat naprej tece
       normalno z novim ciklom.

       Urnik po izvedenem preklopu OSTANE prikljucen (faza z novim ciklom traja
       se cel cikel), zato vsak nov preklop najprej sprosti obstojecega in
       naredi svez urnik iz narocnine. Obstojecega ne posodabljamo: njegova
       prva faza je lahko ze pretekla, preteklih faz pa Stripe ne pusti
       spreminjati. */
    public Stanje zabeleziPreklop(String narocninaId, CiklusPlacila novCiklus, double novaCena) {
        return poStripu("preklop cikla", () -> {
            Subscription sub = Subscription.retrieve(narocninaId);
            SubscriptionItem postavka = prvaPostavka(sub);
            if (postavka == null || postavka.getPrice() == null) {
                throw new DomenskaIzjema("Narocnine ni mogoce preklopiti: Stripe ne vrne postavke.");
            }

            if (sub.getSchedule() != null) {
                SubscriptionSchedule.retrieve(sub.getSchedule()).release();
            }
            SubscriptionSchedule urnik = SubscriptionSchedule.create(
                    SubscriptionScheduleCreateParams.builder().setFromSubscription(narocninaId).build());
            try {
                SubscriptionSchedule.Phase tekoca = urnik.getPhases().get(0);
                urnik.update(SubscriptionScheduleUpdateParams.builder()
                        .setEndBehavior(SubscriptionScheduleUpdateParams.EndBehavior.RELEASE)
                        .addPhase(SubscriptionScheduleUpdateParams.Phase.builder()
                                .setStartDate(tekoca.getStartDate())
                                .setEndDate(tekoca.getEndDate())
                                .setProrationBehavior(
                                        SubscriptionScheduleUpdateParams.Phase.ProrationBehavior.NONE)
                                .addItem(SubscriptionScheduleUpdateParams.Phase.Item.builder()
                                        .setPrice(postavka.getPrice().getId())
                                        .setQuantity(1L)
                                        .build())
                                .build())
                        .addPhase(SubscriptionScheduleUpdateParams.Phase.builder()
                                .setIterations(1L)
                                .setProrationBehavior(
                                        SubscriptionScheduleUpdateParams.Phase.ProrationBehavior.NONE)
                                .addItem(SubscriptionScheduleUpdateParams.Phase.Item.builder()
                                        .setQuantity(1L)
                                        .setPriceData(SubscriptionScheduleUpdateParams.Phase.Item.PriceData
                                                .builder()
                                                .setCurrency("eur")
                                                .setProduct(postavka.getPrice().getProduct())
                                                .setUnitAmount(Math.round(novaCena * 100))
                                                .setRecurring(SubscriptionScheduleUpdateParams.Phase.Item
                                                        .PriceData.Recurring.builder()
                                                        .setInterval(interval(novCiklus))
                                                        .build())
                                                .build())
                                        .build())
                                .build())
                        .build());
            } catch (StripeException | RuntimeException e) {
                // urnik z eno fazo bi visel na narocnini brez pomena; sprostitev je
                // brez posledic (narocnina tece naprej, kot je tekla)
                sprostiTiho(urnik);
                throw e;
            }
            return stanje(Subscription.retrieve(narocninaId));
        });
    }

    /* Umik zabelezenega preklopa: urnik se SPROSTI (release), narocnina pa
       tece naprej z ceno in ciklom, ki ju ima zdaj. Nic se ne preklice. */
    public Stanje sprostiUrnik(String narocninaId) {
        return poStripu("umik preklopa", () -> {
            Subscription sub = Subscription.retrieve(narocninaId);
            if (sub.getSchedule() != null) {
                SubscriptionSchedule.retrieve(sub.getSchedule()).release();
                sub = Subscription.retrieve(narocninaId);
            }
            return stanje(sub);
        });
    }

    // ---------- Branje Stripovega objekta (deli ga tudi webhook) ----------

    /* Od Stripovega API-ja "basil" (ta SDK ga uporablja) je obdobje na postavki
       (SubscriptionItem) in ne vec na narocnini; checkout tu vedno ustvari
       natanko eno postavko. */
    static SubscriptionItem prvaPostavka(Subscription sub) {
        if (sub.getItems() == null || sub.getItems().getData().isEmpty()) {
            return null;
        }
        return sub.getItems().getData().get(0);
    }

    static Stanje stanje(Subscription sub) {
        SubscriptionItem postavka = prvaPostavka(sub);
        return new Stanje(
                postavka == null ? null : cas(postavka.getCurrentPeriodStart()),
                postavka == null ? null : cas(postavka.getCurrentPeriodEnd()),
                ciklus(postavka),
                cena(postavka),
                Boolean.TRUE.equals(sub.getCancelAtPeriodEnd()),
                sub.getSchedule());
    }

    /* Stripovo stanje se prepise v vrstico - edino mesto, kjer se to zgodi, da
       preklic, obnova, preklop in webhook zapisujejo ista polja. Kar Stripe ne
       poslje (null), lokalne vrednosti ne izbrise. */
    static void uskladi(Narocnina n, Stanje s) {
        if (s.obdobjeOd() != null) {
            n.setObdobjeOd(s.obdobjeOd());
        }
        if (s.obdobjeDo() != null) {
            n.setTrenutnoObdobjeDo(s.obdobjeDo());
        }
        if (s.ciklus() != null) {
            n.setCiklus(s.ciklus());
        }
        if (s.cena() != null) {
            n.setCenaObSklenitvi(s.cena());
        }
    }

    /* Iz intervala cene; null, ce ga Stripe ne poslje. Interval "month" s
       stevcem ni mogoc (vrsticaSeje ga nikoli ne nastavi). */
    static CiklusPlacila ciklus(SubscriptionItem postavka) {
        if (postavka == null || postavka.getPrice() == null || postavka.getPrice().getRecurring() == null) {
            return null;
        }
        return switch (String.valueOf(postavka.getPrice().getRecurring().getInterval())) {
            case "year" -> CiklusPlacila.LETNO;
            case "month" -> CiklusPlacila.MESECNO;
            default -> null;
        };
    }

    static Double cena(SubscriptionItem postavka) {
        if (postavka == null || postavka.getPrice() == null || postavka.getPrice().getUnitAmount() == null) {
            return null;
        }
        return postavka.getPrice().getUnitAmount() / 100.0;
    }

    private static LocalDateTime cas(Long sekunde) {
        return sekunde == null ? null
                : LocalDateTime.ofInstant(Instant.ofEpochSecond(sekunde), ZoneId.systemDefault());
    }

    private static SubscriptionScheduleUpdateParams.Phase.Item.PriceData.Recurring.Interval interval(
            CiklusPlacila ciklus) {
        return ciklus == CiklusPlacila.LETNO
                ? SubscriptionScheduleUpdateParams.Phase.Item.PriceData.Recurring.Interval.YEAR
                : SubscriptionScheduleUpdateParams.Phase.Item.PriceData.Recurring.Interval.MONTH;
    }

    // ---------- Pomozno ----------

    @FunctionalInterface
    private interface StripeKlic<T> {
        T izvedi() throws StripeException;
    }

    /* StripeException gre uporabniku kot razumljivo sporocilo, podrobnost pa v
       dnevnik (isto kot ustvariSejo v PlacilaStoritev). */
    private static <T> T poStripu(String opis, StripeKlic<T> klic) {
        try {
            return klic.izvedi();
        } catch (StripeException e) {
            dnevnik.error("Stripe ({}) ni uspel: {}", opis, e.getMessage());
            throw new DomenskaIzjema("Narocnine trenutno ni mogoce spremeniti. Poskusi kasneje.");
        }
    }

    private static void sprostiTiho(SubscriptionSchedule urnik) {
        try {
            urnik.release();
        } catch (StripeException e) {
            dnevnik.warn("Urnika {} ni bilo mogoce sprostiti po neuspelem preklopu: {}",
                    urnik.getId(), e.getMessage());
        }
    }
}
