/* Zapis koledarja iCalendar (RFC 5545) za osebni koledar navijaca in igralca
   (OsebniKoledarStoritev). Samo toliko, kolikor koledar v telefonu potrebuje:
   dogodek z oznako, casom ali celim dnem, opisom in povezavo.

   Pasti formata, zaradi katerih je ta razred locen in pod testom (IcsTest):
   - vrstice se koncajo s CRLF in so dolge najvec 75 OKTETOV (ne znakov: sumniki
     imajo dva) - daljsa vrstica se prelomi s CRLF in presledkom;
   - besedilo mora ubezati poševnico, podpicje, vejico in novi red;
   - cas z uro gre v UTC (Z), da razlicice koledarjev ne ugibajo casovnega
     pasu, cel dan pa je VALUE=DATE z izkljucnim koncem. */
package si.turnirko.pomozno;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public final class Ics {

    private static final ZoneId SLOVENIJA = ZoneId.of("Europe/Ljubljana");
    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final DateTimeFormatter DAN = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String CRLF = "\r\n";
    private static final int NAJVEC_OKTETOV = 75;

    private final StringBuilder telo = new StringBuilder();

    private Ics() {}

    /* Nov koledar. `ime` vidi uporabnik v seznamu koledarjev; `osvezitevUr` pove
       koledarju, kako pogosto naj povezavo bere (Google ga vseeno bere po svoje). */
    public static Ics koledar(String ime, int osvezitevUr) {
        Ics ics = new Ics();
        ics.vrstica("BEGIN:VCALENDAR");
        ics.vrstica("VERSION:2.0");
        ics.vrstica("PRODID:-//Turnirko//Osebni koledar//SL");
        ics.vrstica("CALSCALE:GREGORIAN");
        ics.vrstica("METHOD:PUBLISH");
        ics.vrstica("X-WR-CALNAME:" + ubezi(ime));
        ics.vrstica("X-WR-TIMEZONE:Europe/Ljubljana");
        ics.vrstica("REFRESH-INTERVAL;VALUE=DURATION:PT" + osvezitevUr + "H");
        ics.vrstica("X-PUBLISHED-TTL:PT" + osvezitevUr + "H");
        return ics;
    }

    /* Dogodek z uro: zacetek in konec v slovenskem lokalnem casu. */
    public Ics dogodek(String uid, LocalDateTime zdaj, LocalDateTime zacetek, LocalDateTime konec,
                       String povzetek, String opis, String kraj, String povezava) {
        zacni(uid, zdaj);
        vrstica("DTSTART:" + utc(zacetek));
        vrstica("DTEND:" + utc(konec));
        besedilo(povzetek, opis, kraj, povezava);
        return this;
    }

    /* Dogodek za cel dan ali vec dni: `zadnjiDan` je vkljucen (v zapisu je konec
       izkljucen, zato se doda dan). */
    public Ics celDan(String uid, LocalDateTime zdaj, LocalDate prviDan, LocalDate zadnjiDan,
                      String povzetek, String opis, String kraj, String povezava) {
        zacni(uid, zdaj);
        vrstica("DTSTART;VALUE=DATE:" + prviDan.format(DAN));
        vrstica("DTEND;VALUE=DATE:" + zadnjiDan.plusDays(1).format(DAN));
        besedilo(povzetek, opis, kraj, povezava);
        return this;
    }

    public String zakljuci() {
        vrstica("END:VCALENDAR");
        return telo.toString();
    }

    private void zacni(String uid, LocalDateTime zdaj) {
        vrstica("BEGIN:VEVENT");
        vrstica("UID:" + uid);
        vrstica("DTSTAMP:" + utc(zdaj));
    }

    private void besedilo(String povzetek, String opis, String kraj, String povezava) {
        vrstica("SUMMARY:" + ubezi(povzetek));
        if (opis != null && !opis.isBlank()) {
            vrstica("DESCRIPTION:" + ubezi(opis));
        }
        if (kraj != null && !kraj.isBlank()) {
            vrstica("LOCATION:" + ubezi(kraj));
        }
        if (povezava != null && !povezava.isBlank()) {
            vrstica("URL:" + povezava);
        }
        vrstica("END:VEVENT");
    }

    /* Lokalni cas Slovenije v UTC: poletni in zimski cas se tako ne izgubita. */
    static String utc(LocalDateTime lokalni) {
        return lokalni.atZone(SLOVENIJA).withZoneSameInstant(ZoneOffset.UTC).format(UTC);
    }

    /* RFC 5545, 3.3.11: poševnica, podpicje, vejica in novi red. */
    static String ubezi(String besedilo) {
        return besedilo.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n");
    }

    /* Vrstica se prelomi pri 75 oktetih; nadaljevanje se zacne s presledkom (ki
       steje v naslednjo vrstico). Prelom nikoli ne razreze znaka UTF-8. */
    private void vrstica(String vsebina) {
        StringBuilder trenutna = new StringBuilder();
        int oktetov = 0;
        int meja = NAJVEC_OKTETOV;
        for (int i = 0; i < vsebina.length(); ) {
            int tocka = vsebina.codePointAt(i);
            String znak = new String(Character.toChars(tocka));
            int velikost = znak.getBytes(StandardCharsets.UTF_8).length;
            if (oktetov + velikost > meja) {
                telo.append(trenutna).append(CRLF);
                trenutna = new StringBuilder(" ");
                oktetov = 1;
                meja = NAJVEC_OKTETOV;
            }
            trenutna.append(znak);
            oktetov += velikost;
            i += Character.charCount(tocka);
        }
        telo.append(trenutna).append(CRLF);
    }
}
