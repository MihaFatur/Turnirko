/* Vnos ni veljaven (npr. neodlocen izid) - prevede se v HTTP 400. */
package si.turnirko.izjeme;

public class NeveljavenVnosIzjema extends RuntimeException {

    public NeveljavenVnosIzjema(String sporocilo) {
        super(sporocilo);
    }
}
