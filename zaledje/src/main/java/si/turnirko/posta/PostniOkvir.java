/* Skupni okvir HTML sporocil Turnirka (obvestilo po kolu, obvestilo adminu):
   barve in pisave iz DESIGN.md, glava z znakom Turnirko, barvni pasek, ovoj
   dokumenta in gradniki crt (tabela, celica, oznaka). Vsa sporocila tako
   izgledajo kot isti zapisnik na papirju - razlikuje jih le vsebina.

   Znak je narisan z razpredelnico (barvni stolpci kot na ikoni), ker
   odjemalci slik pogosto ne nalozijo. Odjemalci pisav ne nalagajo, zato so
   pisave sklad z zamenjavami; slogi so v vrstici, ker jih Gmail iz <style>
   deloma pobere. Vse, kar vpise clovek (imena, ekipe, klubi), gre skozi e(). */
package si.turnirko.posta;

import org.springframework.web.util.HtmlUtils;

final class PostniOkvir {

    // barve in pisave iz DESIGN.md (slog.css :root)
    static final String PAPIR = "#fbf9f5";
    static final String CRNILO = "#14110f";
    static final String CRNILO_3 = "#5b534d";
    static final String CRTA = "#ded7ce";
    static final String MODRA = "#0071ab";
    static final String MODRA_MEHKO = "#eaf3f9";
    static final String ZELENA = "#4a7500";
    static final String RJASTA = "#8c2b14";
    static final String TELO = "Karla, 'Helvetica Neue', Arial, sans-serif";
    static final String MONO = "'IBM Plex Mono', Consolas, 'Courier New', monospace";
    static final String NASLOV = "'Bricolage Grotesque', 'Helvetica Neue', Arial, sans-serif";

    private PostniOkvir() {}

    /* Ovoj dokumenta do prve vrstice vsebine: glava HTML, predogled v seznamu
       sporocil (Gmail, Apple Mail - ne izrise se) in zunanja razpredelnica
       sirine 600 px. slogi gre v <style>; pravila, ki jih odjemalec brez
       njega ne uveljavi, so namenjena le izboljsavi na sirokem zaslonu. */
    static StringBuilder zacetek(String naslov, String slogi, String predogled) {
        StringBuilder h = new StringBuilder(14_000);
        h.append("<!doctype html><html lang=\"sl\"><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
                .append("<meta name=\"color-scheme\" content=\"only light\">")
                .append("<title>").append(e(naslov)).append("</title>");
        if (slogi != null && !slogi.isEmpty()) {
            h.append("<style>").append(slogi).append("</style>");
        }
        h.append("</head>")
                .append("<body style=\"margin:0;padding:0;background:").append(PAPIR).append(";color:")
                .append(CRNILO).append(";font-family:").append(TELO).append(";\">");
        h.append("<div style=\"display:none;max-height:0;overflow:hidden;opacity:0;\">")
                .append(e(predogled)).append("</div>");
        h.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" ")
                .append("style=\"background:").append(PAPIR).append(";\"><tr><td align=\"center\" ")
                .append("style=\"padding:24px 16px;\">")
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" ")
                .append("style=\"max-width:600px;\">");
        return h;
    }

    static String konec(StringBuilder h) {
        h.append("</table></td></tr></table></body></html>");
        return h.toString();
    }

    static void vrstica(StringBuilder h, String vsebina, String slog) {
        h.append("<tr><td style=\"").append(slog).append("\">").append(vsebina).append("</td></tr>");
    }

    /* Glava: znak Turnirko (barvni stolpci kot na ikoni), ime in oznaka vrste
       sporocila; pod njo stiri barvne crte, ki so isti razpored kot stolpci
       znaka. */
    static String glava(String oznakaDesno) {
        return "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr>"
                + "<td valign=\"middle\" style=\"width:31px;padding:0 14px 0 0;\">" + znak() + "</td>"
                + "<td valign=\"middle\" style=\"font-family:" + NASLOV + ";font-size:22px;font-weight:700;"
                + "letter-spacing:-0.3px;line-height:1;\">Turnirko</td>"
                + "<td valign=\"middle\" align=\"right\" style=\"" + oznaka() + "\">" + e(oznakaDesno) + "</td>"
                + "</tr></table>" + pasek();
    }

    private static String znak() {
        return "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"border-collapse:collapse;\"><tr>"
                + palica(CRNILO) + razmik() + palica(CRNILO) + razmik() + palica(MODRA) + razmik() + palica(ZELENA)
                + "</tr></table>";
    }

    private static String palica(String barva) {
        return "<td width=\"4\" height=\"26\" style=\"width:4px;height:26px;background:" + barva
                + ";font-size:0;line-height:0;\">&nbsp;</td>";
    }

    private static String razmik() {
        return "<td width=\"5\" style=\"width:5px;font-size:0;line-height:0;\">&nbsp;</td>";
    }

    static String pasek() {
        return "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"margin-top:14px;border-collapse:collapse;\"><tr>"
                + odsek(CRNILO) + odsek(CRNILO) + odsek(MODRA) + odsek(ZELENA) + "</tr></table>";
    }

    private static String odsek(String barva) {
        return "<td height=\"4\" style=\"height:4px;background:" + barva + ";font-size:0;line-height:0;\">&nbsp;</td>";
    }

    /* Sklop konca zadnja vrstica njegove tabele (ta ima crto), zato sklop
       spodnje crte nima - sicer bi bili dve. */
    static String sklop() {
        return "padding:28px 0 0;";
    }

    static String oznaka() {
        return "font-family:" + MONO + ";font-size:12px;text-transform:uppercase;letter-spacing:0.08em;"
                + "color:" + CRNILO_3 + ";";
    }

    static String naslovSklopa(String besedilo) {
        return "<div style=\"" + oznaka() + "margin-bottom:8px;\">" + besedilo + "</div>";
    }

    static String tabela() {
        return "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"border-collapse:collapse;font-size:15px;line-height:1.4;\">";
    }

    static String celica(String slog, String vsebina) {
        return "<td style=\"padding:8px 6px 8px 0;border-bottom:1px solid " + CRTA + ";vertical-align:top;"
                + slog + "\">" + vsebina + "</td>";
    }

    static String glavaStolpca(String besedilo, String poravnava) {
        return "<td style=\"padding:0 6px 6px 0;border-bottom:1px solid " + CRNILO + ";text-align:" + poravnava
                + ";font-family:" + MONO + ";font-size:11px;text-transform:uppercase;letter-spacing:0.08em;"
                + "color:" + CRNILO_3 + ";\">" + besedilo + "</td>";
    }

    /* Poudarek prejemnikove ekipe ali igralca: modro in krepko, enako v obeh
       razlicicah sporocila (v besedilu z »). */
    static String poudari(String besedilo) {
        return "<strong style=\"color:" + MODRA + ";\">" + e(besedilo) + "</strong>";
    }

    static String e(String besedilo) {
        return besedilo == null ? "" : HtmlUtils.htmlEscape(besedilo, "UTF-8");
    }
}
