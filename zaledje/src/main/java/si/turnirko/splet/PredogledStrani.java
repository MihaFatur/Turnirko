/* Naslov in predogled za deljenje, vstavljen v index.html na strezniku.

   Vmesnik je enostranska aplikacija: vsaka pot dobi isti index.html, zato je
   imela vsaka stran isti <title> in isti predogled. Povezava na ligo v
   skupini ekipe na WhatsAppu je bila videti kot domaca stran - WhatsApp,
   Facebook in Viber JavaScripta ne izvajajo, berejo samo glavo HTML. Zato jo
   za strani lig, turnirjev, srecanj in profilov sestavi streznik
   (StraniKontroler), naslov v zavihku pa nato vzdrzuje vmesnik
   (useNaslovStrani - isti zapis "<naslov> – Turnirko").

   index.html ima blok med oznakama <!--predogled--> in <!--/predogled-->;
   ta razred ga zamenja. Vse ostalo (slika, ime strani, skripti) ostane, kot
   ga je zgradil Vite. Ce oznak ni (drugacen index.html), ostane HTML
   nespremenjen - stran deluje, le predogled je splosen. */
package si.turnirko.splet;

import org.springframework.web.util.HtmlUtils;

public final class PredogledStrani {

    public static final String ZACETEK = "<!--predogled-->";
    public static final String KONEC = "<!--/predogled-->";
    /* Pripona naslova v zavihku; og:title je brez nje (ime strani nosi
       og:site_name). Isti zapis kot useNaslovStrani v vmesniku. */
    public static final String PRIPONA = " – Turnirko";

    /* Splosna slika predogleda (1200 x 630, skripte/ikone). */
    static final String SPLOSNA_SLIKA = "/predogled.png";

    /* Kaj o strani pove predogled. "pot" je pot z ?parametri, kakrsno je
       gledalec delil (og:url), "kanonicnaPot" pa pot brez njih (iskalnik naj
       okno ekipe steje kot isto stran lige). "logotip" je zgostitev logotipa
       tekmovanja (V54) - predogled lige z logotipom pokaze njega na papirni
       podlagi (razlicica DELJENJE, ista mera kot splosna slika); null = splosna. */
    public record Predogled(String naslov, String opis, String pot, String kanonicnaPot, String logotip) {

        public Predogled(String naslov, String opis, String pot, String kanonicnaPot) {
            this(naslov, opis, pot, kanonicnaPot, null);
        }

        public Predogled zLogotipom(String zgostitev) {
            return new Predogled(naslov, opis, pot, kanonicnaPot, zgostitev);
        }
    }

    private PredogledStrani() {}

    public static String vstavi(String html, Predogled predogled, String javniNaslov) {
        int zacetek = html.indexOf(ZACETEK);
        int konec = html.indexOf(KONEC);
        if (zacetek < 0 || konec < zacetek) {
            return html;
        }
        String osnova = javniNaslov.endsWith("/")
                ? javniNaslov.substring(0, javniNaslov.length() - 1)
                : javniNaslov;
        String naslov = ubezi(predogled.naslov());
        String opis = ubezi(predogled.opis());
        // zgostitev je samo sestnajstiska (pot slike jo zahteva), a ubezimo vseeno
        String slika = predogled.logotip() != null
                ? osnova + "/api/v1/slike/" + predogled.logotip() + "?velikost=DELJENJE"
                : osnova + SPLOSNA_SLIKA;
        String opisSlike = predogled.logotip() != null
                ? naslov
                : ubezi("Turnirko – namizni tenis: turnirji, lige in lestvica");
        String blok = ZACETEK + "\n"
                + "    <title>" + naslov + ubezi(PRIPONA) + "</title>\n"
                + "    <meta name=\"description\" content=\"" + opis + "\" />\n"
                + "    <link rel=\"canonical\" href=\"" + ubezi(osnova + predogled.kanonicnaPot()) + "\" />\n"
                + "    <meta property=\"og:title\" content=\"" + naslov + "\" />\n"
                + "    <meta property=\"og:description\" content=\"" + opis + "\" />\n"
                + "    <meta property=\"og:url\" content=\"" + ubezi(osnova + predogled.pot()) + "\" />\n"
                + "    <meta property=\"og:image\" content=\"" + ubezi(slika) + "\" />\n"
                + "    <meta property=\"og:image:width\" content=\"1200\" />\n"
                + "    <meta property=\"og:image:height\" content=\"630\" />\n"
                + "    <meta property=\"og:image:alt\" content=\"" + opisSlike + "\" />\n"
                + "    ";
        return html.substring(0, zacetek) + blok + html.substring(konec);
    }

    /* Imena prihajajo od organizatorjev in iz uvoza - narekovaj ali < v
       imenu ekipe ne sme odpreti atributa ali oznake. */
    private static String ubezi(String besedilo) {
        return HtmlUtils.htmlEscape(besedilo == null ? "" : besedilo, "UTF-8");
    }
}
