/* Namen potrditvene kode, ki jo racun dobi po e-posti.
   EPOSTA  - potrditev lastnega naslova ob registraciji (velja 10 minut)
   SKRBNIK - soglasje starsa oz. skrbnika za racun mlajsega od 15 let (24 ur,
             ker stars poste ne bere sproti)
   GESLO   - pozabljeno geslo (10 minut) */
package si.turnirko.modeli;

import java.time.Duration;

public enum NamenKode {
    EPOSTA(Duration.ofMinutes(10)),
    SKRBNIK(Duration.ofHours(24)),
    GESLO(Duration.ofMinutes(10));

    private final Duration veljavnost;

    NamenKode(Duration veljavnost) {
        this.veljavnost = veljavnost;
    }

    public Duration veljavnost() {
        return veljavnost;
    }
}
