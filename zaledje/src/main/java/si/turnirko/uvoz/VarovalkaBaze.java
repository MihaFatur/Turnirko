/* Varovalka pred zagonom rusilnega ukaza nad napacno bazo (najdba E3 pregleda
   kakovosti, nov 2026).

   Ukaz, ki brise in znova pise podatke (PopravekZgodovineUkaz), se zazene z
   istim programom in istimi nastavitvami povezave kot aplikacija: dovolj je
   profil vec ob zagonu na strezniku in tece nad zivo bazo. Zato mora tisti, ki
   ga zazene, izrecno zapisati, NAD KATERO bazo ga zaganja - naslov, kot ga
   vidi povezava (streznik:vrata/baza) - in zapis se mora ujemati z dejansko
   povezavo. Samo ime baze ni dovolj: kopija na domacem racunalniku in
   produkcijska baza se obe imenujeta "turnirko", loci ju streznik.

   V profilu "splet" (streznik) se ukaz ne zazene niti s potrditvijo: tece samo
   lokalno nad kopijo (docs/OBJAVA.md, "Ukazi nad bazo"). */
package si.turnirko.uvoz;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;

import javax.sql.DataSource;

public final class VarovalkaBaze {

    /* Profil, v katerem tece aplikacija na strezniku (application-splet.properties). */
    static final String PROFIL_STREZNIKA = "splet";

    private VarovalkaBaze() {}

    /* Vrze IllegalStateException z navodilom, ce zagon ni potrjen za bazo, na
       katero je ukaz dejansko povezan. `nastavitev` je ime lastnosti, s katero
       se zagon potrdi (za sporocilo). */
    public static void zahtevajPotrditev(String ukaz, String nastavitev, String potrjenaBaza,
                                         String dejanskaBaza, boolean profilStreznika) {
        if (profilStreznika) {
            throw new IllegalStateException(ukaz + " se NE zazene: aktiven je profil \"" + PROFIL_STREZNIKA
                    + "\" (streznik), povezava kaze na bazo " + dejanskaBaza + ". Ukaz brise in znova pise"
                    + " podatke in tece samo na domacem racunalniku nad KOPIJO baze - postopek je v"
                    + " docs/OBJAVA.md, razdelek \"Ukazi nad bazo\".");
        }
        String potrjena = potrjenaBaza == null ? "" : potrjenaBaza.trim();
        if (potrjena.isEmpty()) {
            throw new IllegalStateException(ukaz + " se NE zazene: brise in znova pise podatke, zagon pa ni"
                    + " potrjen. Povezan je na bazo " + dejanskaBaza + ". Ce je to res KOPIJA baze (nikoli baza"
                    + " delujoce aplikacije), dodaj ob zagonu --" + nastavitev + "=" + dejanskaBaza + " .");
        }
        if (!potrjena.toLowerCase(Locale.ROOT).equals(dejanskaBaza.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException(ukaz + " se NE zazene: potrjen je za bazo " + potrjena
                    + ", povezan pa na bazo " + dejanskaBaza + ". Preveri --spring.datasource.url in --"
                    + nastavitev + " (obe morata kazati na isto KOPIJO baze).");
        }
    }

    /* Naslov baze, kot ga vidi povezava: "streznik:vrata/baza". */
    public static String naslovBaze(DataSource vir) {
        try (Connection povezava = vir.getConnection()) {
            return izNaslovaJdbc(povezava.getMetaData().getURL());
        } catch (SQLException napaka) {
            throw new IllegalStateException("Naslova baze ni bilo mogoce prebrati iz povezave.", napaka);
        }
    }

    /* "jdbc:postgresql://localhost:5432/turnirko?x=y" -> "localhost:5432/turnirko".
       Naslov, ki ga ne znamo razbrati, ostane cel: potrditev se mora potem
       ujemati z njim dobesedno. */
    static String izNaslovaJdbc(String naslov) {
        if (naslov == null) {
            return "";
        }
        String brezParametrov = naslov.contains("?") ? naslov.substring(0, naslov.indexOf('?')) : naslov;
        int zacetek = brezParametrov.indexOf("://");
        return zacetek < 0 ? brezParametrov : brezParametrov.substring(zacetek + 3);
    }
}
