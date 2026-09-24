/* Narocnina prijavljenega uporabnika za stran "Narocnina" (GET /api/v1/narocnina).

   Zakaj ne polja na UporabnikDto: ta gre ob vsaki prijavi in osvezitvi in ga
   bere vsak del vmesnika; datumi obdobja so podatek ene strani. Kdor
   narocnine nima (igralec Free), dobi odgovor z aktivna = false, ne 404 -
   "brez narocnine" je enakovredno stanje in ne napaka.

   Datumi so koledarski dnevi po slovenskem casu (LocalDate): vmesnik dneve do
   obnove steje po dnevih in ura obdobja ni podatek, ki bi ga prikazal. */
package si.turnirko.dto;

import java.time.LocalDate;

import si.turnirko.modeli.CiklusPlacila;
import si.turnirko.modeli.Paket;
import si.turnirko.modeli.StatusNarocnine;

public record NarocninaDto(
        // paket iz zapisa; BREZPLACNO, kadar racun zapisa o narocnini nima
        Paket paket,
        // ali narocnina zdaj daje pravice (Narocnina.jeVeljavna); preklicana
        // ostane true do konca placanega obdobja
        boolean aktivna,
        // preklicana ob koncu obdobja, a se placano obdobje se ni izteklo
        boolean preklicana,
        StatusNarocnine status,
        // cikel, ki ga Stripe zaracunava ZDAJ
        CiklusPlacila ciklus,
        // cena tekocega cikla, kot jo Stripe zaracunava (cena ob sklenitvi -
        // starejse narocnine imajo lahko drugo od danasnjega cenika)
        Double cena,
        // cenovni pas ob sklenitvi (starejsi od 21 let); po njem vmesnik
        // izbere ceno DRUGEGA cikla za predogled preklopa
        Boolean starejsiOd21,
        LocalDate narocenOd,
        LocalDate obdobjeOd,
        LocalDate obdobjeDo,
        // zabelezen preklop, ki zacne veljati ob obnovi; null = preklopa ni
        CiklusPlacila naslednjiCiklus
) {

    /* Racun brez veljavne narocnine (igralec Free ali potekla narocnina). */
    public static NarocninaDto brez() {
        return new NarocninaDto(Paket.BREZPLACNO, false, false, null, null, null, null,
                null, null, null, null);
    }
}
