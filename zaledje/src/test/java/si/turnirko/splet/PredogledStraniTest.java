/* Vstavljanje naslova in predogleda v index.html (PredogledStrani). WhatsApp
   in Facebook berejo samo glavo HTML, zato mora streznik zamenjati natanko
   blok med oznakama - in nic drugega - ter ime iz baze ubezati. */
package si.turnirko.splet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import si.turnirko.splet.PredogledStrani.Predogled;

class PredogledStraniTest {

    private static final String HTML = """
            <head>
                <meta charset="UTF-8" />
                <!--predogled-->
                <title>Turnirko – splosno</title>
                <meta property="og:url" content="https://turnirko-nt.si/" />
                <!--/predogled-->
                <meta property="og:image" content="https://turnirko-nt.si/predogled.png" />
            </head>""";

    @Test
    void zamenjaSamoBlokMedOznakama() {
        String izid = PredogledStrani.vstavi(HTML,
                new Predogled("Liga Savinja B · Sezona 8", "Lestvica, razpored in izidi",
                        "/lige/387", "/lige/387"),
                "https://turnirko-nt.si");

        assertTrue(izid.contains("<title>Liga Savinja B · Sezona 8 – Turnirko</title>"));
        assertTrue(izid.contains("<meta property=\"og:title\" content=\"Liga Savinja B · Sezona 8\" />"));
        assertTrue(izid.contains("<meta name=\"description\" content=\"Lestvica, razpored in izidi\" />"));
        assertTrue(izid.contains("<link rel=\"canonical\" href=\"https://turnirko-nt.si/lige/387\" />"));
        assertFalse(izid.contains("splosno"), "splosni naslov je zamenjan");
        assertTrue(izid.contains("<meta charset=\"UTF-8\" />"), "pred blokom ostane");
        assertTrue(izid.contains("og:image"), "za blokom ostane");
        assertTrue(izid.contains(PredogledStrani.ZACETEK) && izid.contains(PredogledStrani.KONEC),
                "oznaki ostaneta");
    }

    /* Okno ekipe: og:url nosi ?ekipa= (to se deli), kanonicni naslov pa ne -
       za iskalnik je to ista stran lige. Koncna posevnica javnega naslova ne
       podvoji. */
    @Test
    void ogUrlZParametromKanonicniBrez() {
        String izid = PredogledStrani.vstavi(HTML,
                new Predogled("Tempo 2 · Liga Savinja B", "Kader", "/lige/387?ekipa=12", "/lige/387"),
                "https://turnirko-nt.si/");

        assertTrue(izid.contains("content=\"https://turnirko-nt.si/lige/387?ekipa=12\""));
        assertTrue(izid.contains("href=\"https://turnirko-nt.si/lige/387\""));
    }

    /* Ime ekipe ali turnirja vpise organizator - narekovaj ali oznaka ne sme
       odpreti atributa. */
    @Test
    void imeJeUbezano() {
        String izid = PredogledStrani.vstavi(HTML,
                new Predogled("\"Zmaji\" <b>", "a & b", "/lige/1", "/lige/1"),
                "https://turnirko-nt.si");

        assertTrue(izid.contains("content=\"&quot;Zmaji&quot; &lt;b&gt;\""));
        assertTrue(izid.contains("content=\"a &amp; b\""));
        assertFalse(izid.contains("<b>"));
    }

    @Test
    void brezOznakOstaneNespremenjen() {
        String brez = "<head><title>Turnirko</title></head>";

        assertEquals(brez, PredogledStrani.vstavi(brez,
                new Predogled("X", "Y", "/lige/1", "/lige/1"), "https://turnirko-nt.si"));
    }
}
