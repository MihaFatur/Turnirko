/* Placilni paket racuna. Igralec izbira med BREZPLACNO in PREMIUM (cena po
   starostnem pasu ob registraciji - glej CenikStoritev), navijac med
   BREZPLACNO in NAVIJAC (19,99 EUR na leto, V58), organizator med tremi
   obsegovnimi paketi. En racun ima vedno tocno enega izmed teh sestih -
   kateri nabor je zanj veljaven, doloca Uporabnik.vloga. */
package si.turnirko.modeli;

public enum Paket {
    BREZPLACNO,
    PREMIUM,
    NAVIJAC,
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

    /* Kratko ime za dnevnik racuna in obvestila ("Premium", "Plus"): vloga
       (igralec, organizator) je ze v besedilu ob njem. */
    public String kratekNaziv() {
        return switch (this) {
            case PREMIUM -> "Premium";
            case NAVIJAC -> "Navijač";
            case ORGANIZATOR_BASIC -> "Basic";
            case ORGANIZATOR_PLUS -> "Plus";
            case ORGANIZATOR_PRO -> "Pro";
            case BREZPLACNO -> "Brezplačno";
        };
    }

    /* Ime izdelka v Stripe (vrstica racuna, nadzorna plosca). */
    public String imeIzdelka() {
        return switch (this) {
            case PREMIUM -> "Turnirko Premium";
            case NAVIJAC -> "Turnirko Navijač";
            case ORGANIZATOR_BASIC -> "Turnirko Organizator Basic";
            case ORGANIZATOR_PLUS -> "Turnirko Organizator Plus";
            case ORGANIZATOR_PRO -> "Turnirko Organizator Pro";
            case BREZPLACNO -> throw new IllegalStateException("Brezplacen paket ne gre skozi placilo.");
        };
    }
}
