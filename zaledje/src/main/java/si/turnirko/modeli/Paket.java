/* Placilni paket racuna. Igralec izbira med BREZPLACNO in PREMIUM (cena po
   starostnem pasu ob registraciji - glej CenikStoritev), organizator med
   tremi obsegovnimi paketi. En racun ima vedno tocno enega izmed teh petih -
   kateri nabor je zanj veljaven, doloca Uporabnik.vloga. */
package si.turnirko.modeli;

public enum Paket {
    BREZPLACNO,
    PREMIUM,
    ORGANIZATOR_BASIC,
    ORGANIZATOR_PLUS,
    ORGANIZATOR_PRO
}
