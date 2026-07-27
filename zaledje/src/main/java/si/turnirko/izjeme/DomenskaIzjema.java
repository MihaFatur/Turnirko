/* Krsitev domenskega pravila (npr. vnos rezultata na ze koncano tekmo,
   zreb ze obstaja...) - prevede se v HTTP 409. */
package si.turnirko.izjeme;

public class DomenskaIzjema extends RuntimeException {

    public DomenskaIzjema(String sporocilo) {
        super(sporocilo);
    }
}
