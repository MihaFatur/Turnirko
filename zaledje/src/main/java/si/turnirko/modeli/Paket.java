/* Placilni paket racuna. Igralec izbira med BREZPLACNO in PREMIUM (cena po
   starostnem pasu ob registraciji - glej CenikStoritev), organizator med
   tremi obsegovnimi paketi. En racun ima vedno tocno enega izmed teh petih -
   kateri nabor je zanj veljaven, doloca Uporabnik.vloga. */
package si.turnirko.modeli;

public enum Paket {
    BREZPLACNO,
    PREMIUM,
    // organizatorski paketi so razvrsceni od najmanjsega do najvecjega (jeVisji)
    ORGANIZATOR_BASIC,
    ORGANIZATOR_PLUS,
    ORGANIZATOR_PRO;

    public boolean jeOrganizatorski() {
        return this == ORGANIZATOR_BASIC || this == ORGANIZATOR_PLUS || this == ORGANIZATOR_PRO;
    }

    /* Ali ima ta paket vecji obseg od drugega; pomembno samo med organizatorskimi
       paketi (nadgradnja velja takoj, znizanje ob obnovi). */
    public boolean jeVisji(Paket drug) {
        return ordinal() > drug.ordinal();
    }

    /* Ime izdelka v Stripe (vrstica racuna, nadzorna plosca). */
    public String imeIzdelka() {
        return switch (this) {
            case PREMIUM -> "Turnirko Premium";
            case ORGANIZATOR_BASIC -> "Turnirko Organizator Basic";
            case ORGANIZATOR_PLUS -> "Turnirko Organizator Plus";
            case ORGANIZATOR_PRO -> "Turnirko Organizator Pro";
            case BREZPLACNO -> throw new IllegalStateException("Brezplacen paket ne gre skozi placilo.");
        };
    }
}
