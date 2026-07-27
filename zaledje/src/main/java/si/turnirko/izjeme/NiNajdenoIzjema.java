/* Zahtevani zapis ne obstaja - prevede se v HTTP 404. */
package si.turnirko.izjeme;

public class NiNajdenoIzjema extends RuntimeException {

    public NiNajdenoIzjema(String sporocilo) {
        super(sporocilo);
    }
}
