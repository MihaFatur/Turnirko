/* Streznik zahteve trenutno ne more obdelati, ker je zaseden (npr. hkrati
   dekodira najvec dovoljenih fotografij listkov) - prevede se v HTTP 503 z
   glavo Retry-After. Ni napaka odjemalca: ista zahteva cez nekaj sekund uspe,
   zato jo vrsta brez signala (vmesnik, postaja) sme ponoviti. */
package si.turnirko.izjeme;

public class ZasedenoIzjema extends RuntimeException {

    /* Cez koliko sekund naj odjemalec poskusi znova. */
    private final int cezSekund;

    public ZasedenoIzjema(String sporocilo, int cezSekund) {
        super(sporocilo);
        this.cezSekund = cezSekund;
    }

    public int getCezSekund() {
        return cezSekund;
    }
}
