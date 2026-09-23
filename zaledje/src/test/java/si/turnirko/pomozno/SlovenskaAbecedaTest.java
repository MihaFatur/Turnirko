/* Slovenski abecedni vrstni red (a b c č d e f g h i j k l m n o p r s š t u v z ž).
   Testi varujejo tisto, kar privzeta primerjava zgresi: Č, Š, Ž so pravi crki
   sredi abecede in ne za Z, velikost crk ne odloca, presledek pred priimkom
   ne zamenja vrstnega reda priimkov. Zadnji test zahteva isto zaporedje, kot
   ga za isti seznam da brskalnik (Intl.Collator("sl")) - vmesnik seznam po
   prejemu se enkrat uredi in ga zaledje ne sme premesati. */
package si.turnirko.pomozno;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class SlovenskaAbecedaTest {

    private static final List<String> ABECEDA = List.of(
            "a", "b", "c", "č", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m",
            "n", "o", "p", "r", "s", "š", "t", "u", "v", "z", "ž");

    private static List<String> urejeno(String... besede) {
        List<String> seznam = new ArrayList<>(Arrays.asList(besede));
        seznam.sort(SlovenskaAbeceda.RED);
        return seznam;
    }

    /* "cž" je se vedno beseda na c, zato pred vsako na č; "čž" pred vsako na d. */
    @Test
    void vsakaCrkaJeZaPrejsnjoInPredNaslednjo() {
        for (int i = 0; i + 1 < ABECEDA.size(); i++) {
            String crka = ABECEDA.get(i);
            String naslednja = ABECEDA.get(i + 1);
            assertTrue(SlovenskaAbeceda.primerjaj(crka + "ž", naslednja) < 0,
                    crka + "ž mora pred " + naslednja);
            assertTrue(SlovenskaAbeceda.primerjaj(crka, naslednja) < 0,
                    crka + " mora pred " + naslednja);
        }
    }

    @Test
    void cSZSoPredSvojimiKljukicami() {
        assertEquals(List.of("Cerar", "Čeh", "Dolinar", "Sever", "Šuštar", "Zupan", "Žagar"),
                urejeno("Žagar", "Zupan", "Šuštar", "Sever", "Čeh", "Cerar", "Dolinar"));
    }

    /* Znotraj priimka odloca vsaka crka po svoji legi, ne le prva. */
    @Test
    void kljukicaOdlocaTudiSrediBesede() {
        assertEquals(List.of("Kos", "Koš", "Kot", "Kraš", "Krž", "Kržič"),
                urejeno("Kržič", "Krž", "Kraš", "Koš", "Kot", "Kos"));
    }

    /* Priimek pred imenom (Igralec.abecedno()): kratek priimek s presledkom pred
       imenom mora pred daljsim priimkom, ki se z njim zacne. JDK-jev Collator
       presledek prezre in bi tu zamenjal vrstni red. */
    @Test
    void presledekJeManjsiOdCrke() {
        assertEquals(List.of("Kos Marko", "Kos-Novak Ana", "Kosem Ana", "Kosmač Ana"),
                urejeno("Kosmač Ana", "Kosem Ana", "Kos-Novak Ana", "Kos Marko"));
    }

    @Test
    void velikostCrkeNiPrimarna() {
        assertEquals(List.of("abc", "ABD", "Abe"), urejeno("Abe", "ABD", "abc"));
        assertEquals(List.of("novak", "Novak"), urejeno("Novak", "novak"),
                "ob enakih crkah gre mala pred veliko");
        assertEquals(List.of("žagar", "Žagar", "Žnidar"), urejeno("Žnidar", "Žagar", "žagar"));
    }

    /* Ć in Đ sta v slovenski abecedi nima, a sta pogosta v priimkih igralcev iz
       drugih delov nekdanje domovine: ostaneta ob svoji osnovi (kot v brskalniku). */
    @Test
    void cInDStaObSvojiOsnovi() {
        assertEquals(List.of("Cerar", "Čeh", "Ćosić", "Davis", "Dolinar", "Đukić", "Ema"),
                urejeno("Ema", "Đukić", "Dolinar", "Davis", "Ćosić", "Čeh", "Cerar"));
    }

    @Test
    void nullJePredVsem() {
        assertEquals(Arrays.asList(null, "Cerar", "Čeh"), urejeno("Čeh", null, "Cerar"));
    }

    /* Izhod Intl.Collator("sl") v brskalniku (Chrome/Node) za isti seznam. */
    @Test
    void ujemaSeZBrskalnikom() {
        assertEquals(List.of("Cerar", "čeh", "Čeh", "Ćosić", "Davis", "Dolinar", "Đukić",
                        "novak", "Novak", "Pirc", "Quinn", "Sever", "Šuštar", "Vidmar",
                        "Wilson", "Xu", "Yang", "Zupan", "Žagar"),
                urejeno("Žagar", "Zupan", "Šuštar", "Sever", "Čeh", "Cerar", "Ćosić",
                        "Dolinar", "Đukić", "Davis", "čeh", "Novak", "novak", "Wilson",
                        "Vidmar", "Quinn", "Pirc", "Xu", "Yang"));
    }
}
