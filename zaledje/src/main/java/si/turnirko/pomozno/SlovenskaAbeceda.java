/* Slovenski abecedni vrstni red: a b c č d e f g h i j k l m n o p r s š t u v z ž.

   Vsako mesto, ki besedilo (imena, priimke, klube, ekipe) razvršča po abecedi,
   mora uporabiti to primerjavo. Privzeti primerjavi ne zadostita:
   String.compareTo in SQLite ORDER BY primerjata kodne točke, zato Č, Š in Ž
   pristanejo za Z, velike črke pa pred vsemi malimi. JDK-jev Collator poleg
   tega ignorira presledke, zato bi »Kosem Ana« stal pred »Kos Marko« (pri
   priimku pred imenom to zamenja vrstni red priimkov).

   Osnova je ICU (CLDR, jezik "sl"): isti standard kot Intl.Collator v brskalniku,
   zato vmesnik, ki seznam po prejemu še enkrat uredi (localeCompare(..., 'sl')),
   ne premeša tistega, kar je uredilo zaledje. Črke, ki jih slovenska abeceda
   nima (ć, đ, q, w, x, y, tuje diakritike), so razvrščene ob svoji osnovi. */
package si.turnirko.pomozno;

import java.util.Comparator;
import java.util.Locale;

import com.ibm.icu.text.Collator;

public final class SlovenskaAbeceda {

    private static final Collator PRIMERJALNIK = ustvari();

    /* Slovenski abecedni vrstni red; null je pred vsem (kot pri SQL ORDER BY).
       Med besedami, ki se razlikujejo le po velikosti črk, gre mala pred veliko
       ("novak" pred "Novak"). Uporaba: Comparator.comparing(Klub::getIme,
       SlovenskaAbeceda.RED). */
    public static final Comparator<String> RED =
            Comparator.nullsFirst((a, b) -> PRIMERJALNIK.compare(a, b));

    private SlovenskaAbeceda() {}

    public static int primerjaj(String a, String b) {
        return RED.compare(a, b);
    }

    private static Collator ustvari() {
        Collator c = Collator.getInstance(Locale.forLanguageTag("sl"));
        // zamrznjen primerjalnik je varen za hkratno uporabo iz vec niti
        return c.freeze();
    }
}
