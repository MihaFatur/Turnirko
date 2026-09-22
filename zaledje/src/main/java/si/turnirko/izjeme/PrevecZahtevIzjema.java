/* Isti naslov ali isti odjemalec je v kratkem casu poslal prevec zahtev
   (ponovno posiljanje kode, neuspele prijave) - prevede se v HTTP 429.
   Meja ni kazen, ampak edino, kar sestmestno kodo in geslo res varuje pred
   ugibanjem. */
package si.turnirko.izjeme;

public class PrevecZahtevIzjema extends RuntimeException {

    public PrevecZahtevIzjema(String sporocilo) {
        super(sporocilo);
    }
}
