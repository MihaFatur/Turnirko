/* Uporabnik je prijavljen, a za to dejanje nima pravice (npr. igralec bi
   rad videl zasebno statistiko drugega igralca). Loceno od 401: poverilnice
   so veljavne, pravica pa manjka. */
package si.turnirko.izjeme;

public class PrepovedanoIzjema extends RuntimeException {

    public PrepovedanoIzjema(String sporocilo) {
        super(sporocilo);
    }
}
