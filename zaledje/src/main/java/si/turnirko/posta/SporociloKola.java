/* E-posta po kolu lige (ObvestilaPoKoluStoritev): lestvica po kolu, izidi kola
   in - igralcu, ki je v kolu nastopil - njegove tekme z izidom in spremembo
   ratinga.

   Vrstni red je za vse prejemnike isti (lestvica, izidi, tekme, naslednje kolo,
   Premium, odjava) in ga bere tudi besedilna razlicica. Zadeva nosi samo ime
   lige in kolo, ker je to, po cemer se sporocilo isce v nabiralniku; izidi
   prejemnikove ekipe so v telesu, poudarjeni.

   Gre kot multipart: navadno besedilo za odjemalce brez HTML in HTML, ki sledi
   DESIGN.md (zapisnik na papirju: crte namesto kartic, radij 0, brez senc,
   modra samo za dejanje in za poudarek prejemnikove ekipe in imena). Znak
   Turnirko je narisan z razpredelnico (barvni stolpci kot na ikoni), ker
   odjemalci slik pogosto ne nalozijo. Odjemalci pisav ne nalagajo, zato so
   pisave sklad z zamenjavami; slogi so v vrstici, ker jih Gmail iz <style>
   deloma pobere. Imena ekip in igralcev vpisujejo organizatorji - vse gre skozi
   ubeg.

   Povezave so stalne strani (liga, ekipa v ligi, narocnina) in odjava; zeton
   odjave ne odpre nicesar razen izklopa obvestil, zato ga ni mogoce zlorabiti
   za vstop v racun. */
package si.turnirko.posta;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.web.util.HtmlUtils;

public final class SporociloKola {

    private static final DateTimeFormatter DATUM = DateTimeFormatter.ofPattern("d. M. yyyy");

    // barve in pisave iz DESIGN.md (slog.css :root)
    private static final String PAPIR = "#fbf9f5";
    private static final String CRNILO = "#14110f";
    private static final String CRNILO_3 = "#5b534d";
    private static final String CRTA = "#ded7ce";
    private static final String MODRA = "#0071ab";
    private static final String MODRA_MEHKO = "#eaf3f9";
    private static final String ZELENA = "#4a7500";
    private static final String RJASTA = "#8c2b14";
    private static final String TELO = "Karla, 'Helvetica Neue', Arial, sans-serif";
    private static final String MONO = "'IBM Plex Mono', Consolas, 'Courier New', monospace";
    private static final String NASLOV = "'Bricolage Grotesque', 'Helvetica Neue', Arial, sans-serif";

    /* Srecanje kola; domacih == null pomeni, da izida se ni (delno kolo). */
    public record Izid(String domaci, String gost, Integer domacih, Integer gostov, boolean brezBoja,
                       boolean moje) {}

    public record Vrstica(int mesto, String ekipa, int odigrane, int tocke, boolean moja) {}

    /* Kar je skupno vsem prejemnikom: kolo, izidi, lestvica (ze izbrane
       vrstice), potek in naslednje kolo lige. */
    public record Kolo(Long idLiga, String liga, String sezona, int kolo, LocalDate datum,
                       int odigranihKol, int vsehKol, List<Izid> izidi, List<Vrstica> lestvica,
                       int ekip, Integer naslednjeKolo, LocalDate naslednjeDatum) {}

    /* Posamicna tekma ali dvojica prejemnika v kolu, z njegovega vidika: jaz je
       njegovo ime (pri dvojici skupaj s soigralcem), nasprotnik pa ime nasprotne
       strani. */
    public record Tekma(String jaz, String nasprotnik, int moji, int njegovi, boolean zmaga, Integer sprememba) {}

    /* Del, ki je samo prejemnikov: njegova ekipa, njena srecanja v kolu (liga z
       urami kola jih ima v kolu vec - V31) in njegove tekme ter rating. Prazno,
       ce v ligi ne igra. */
    public record Osebno(Long idEkipa, String ekipa, List<Izid> srecanja, List<Tekma> tekme, Integer rating,
                         Integer spremembaKola) {}

    public enum Razlog { IGRA, SPREMLJA }

    /* premium: vrstica o Premium (ponudba ali stanje preizkusa) ali null. */
    public record Prejemnik(Razlog razlog, Osebno osebno, String premium, String naslovOdjave,
                            String naslovOdjaveApi, String koren) {}

    private SporociloKola() {}

    public static SporocilaPoste.Besedilo sestavi(Kolo k, Prejemnik p) {
        Map<String, String> glave = new LinkedHashMap<>();
        // odjava z enim klikom (RFC 8058): odjemalec poslje POST na ta naslov
        glave.put("List-Unsubscribe", "<" + p.naslovOdjaveApi() + ">");
        glave.put("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
        return new SporocilaPoste.Besedilo(zadeva(k), besedilo(k, p), html(k, p), glave);
    }

    /* Zadeva je za vse enaka: ime lige in kolo ("Liga Savinja A: 1. kolo"). */
    static String zadeva(Kolo k) {
        return k.liga() + ": " + k.kolo() + ". kolo";
    }

    // ---------- navadno besedilo ----------

    private static String besedilo(Kolo k, Prejemnik p) {
        Osebno o = p.osebno();
        String moja = o == null ? null : o.ekipa();
        StringBuilder b = new StringBuilder();
        b.append("TURNIRKO · obvestilo po kolu\n\n");
        b.append(zadeva(k)).append('\n');
        b.append(podnaslov(k)).append('\n');
        if (moja != null) {
            b.append("» tvoja ekipa\n");
        }
        b.append('\n');

        b.append("Lestvica po tem kolu\n");
        int prejsnje = 0;
        for (Vrstica v : k.lestvica()) {
            if (v.mesto() > prejsnje + 1) {
                b.append("   …\n");
            }
            b.append(v.moja() ? "» " : "  ").append(v.mesto()).append(". ").append(v.ekipa()).append(" · ")
                    .append(v.tocke()).append(' ').append(tock(v.tocke())).append('\n');
            prejsnje = v.mesto();
        }
        if (prejsnje < k.ekip()) {
            b.append("  Ekip v ligi: ").append(k.ekip()).append('\n');
        }
        b.append('\n');

        b.append("Izidi ").append(k.kolo()).append(". kola\n");
        for (Izid i : k.izidi()) {
            b.append(i.moje() ? "» " : "  ").append(i.domaci()).append(" – ").append(i.gost()).append("  ")
                    .append(i.domacih() == null ? "izid še ni vpisan" : izid(i))
                    .append(i.brezBoja() ? " (b. b.)" : "").append('\n');
        }
        b.append('\n');

        if (o != null && !o.tekme().isEmpty()) {
            b.append("Tvoje tekme").append(o.ekipa() == null ? "" : " · " + o.ekipa()).append('\n');
            for (Tekma t : o.tekme()) {
                b.append("  ").append(t.zmaga() ? "zmaga " : "poraz ").append(t.moji()).append(" : ")
                        .append(t.njegovi()).append(" · ").append(t.jaz()).append(" proti ")
                        .append(t.nasprotnik());
                if (t.sprememba() != null) {
                    b.append(" (").append(predznak(t.sprememba())).append(')');
                }
                b.append('\n');
            }
            if (o.rating() != null) {
                // rating je danasnji (zapis stanja), sprememba pa samo iz tega kola
                b.append("  Tvoj rating: ").append(o.rating());
                if (o.spremembaKola() != null) {
                    b.append(" (").append(predznak(o.spremembaKola())).append(" v tem kolu)");
                }
                b.append('\n');
            }
            b.append('\n');
        }

        String kolo = naslednjeKolo(k);
        if (kolo != null) {
            b.append(kolo).append('\n');
        }
        b.append("Celotna liga: ").append(naslovLige(k, p)).append("\n\n");
        if (p.premium() != null) {
            b.append(p.premium()).append('\n').append(p.koren()).append("/narocnina\n\n");
        }
        b.append(razlog(k, p)).append('\n')
                .append("Odjava od obvestil po kolu: ").append(p.naslovOdjave()).append('\n');
        return b.toString();
    }

    // ---------- HTML ----------

    private static String html(Kolo k, Prejemnik p) {
        Osebno o = p.osebno();
        String moja = o == null ? null : o.ekipa();
        StringBuilder h = new StringBuilder(14_000);
        h.append("<!doctype html><html lang=\"sl\"><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
                .append("<meta name=\"color-scheme\" content=\"only light\">")
                .append("<title>").append(e(zadeva(k))).append("</title>")
                // tekma v eni vrstici na sirokem zaslonu; na telefonu se ime sme prelomiti (sicer
                // bi vsebina zasla cez rob) - pravilo je v <style>, zato ga odjemalec brez njega ne uveljavi
                .append("<style>@media (min-width:521px){.tekma-ime{white-space:nowrap;}}</style>")
                .append("</head>")
                .append("<body style=\"margin:0;padding:0;background:").append(PAPIR).append(";color:")
                .append(CRNILO).append(";font-family:").append(TELO).append(";\">");
        // predogled v seznamu sporocil (Gmail, Apple Mail) - ne izrise se
        h.append("<div style=\"display:none;max-height:0;overflow:hidden;opacity:0;\">")
                .append(e(predogled(k, o))).append("</div>");
        h.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" ")
                .append("style=\"background:").append(PAPIR).append(";\"><tr><td align=\"center\" ")
                .append("style=\"padding:24px 16px;\">")
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" ")
                .append("style=\"max-width:600px;\">");

        vrstica(h, glava(), "padding:0 0 26px;");
        vrstica(h, naslov(k), "padding:0 0 6px;");
        vrstica(h, lestvica(k), sklop());
        vrstica(h, izidi(k, moja), sklop());
        if (o != null && !o.tekme().isEmpty()) {
            vrstica(h, tekme(o), sklop());
        }
        vrstica(h, naslednje(k, o, p), sklop());
        if (p.premium() != null) {
            vrstica(h, premium(p), "padding:28px 0 0;");
        }
        vrstica(h, dno(k, p), "padding:32px 0 0;");

        h.append("</table></td></tr></table></body></html>");
        return h.toString();
    }

    /* Glava: znak Turnirko (barvni stolpci kot na ikoni), ime in oznaka; pod njo
       stiri barvne crte, ki so isti razpored kot stolpci znaka. */
    private static String glava() {
        return "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr>"
                + "<td valign=\"middle\" style=\"width:31px;padding:0 14px 0 0;\">" + znak() + "</td>"
                + "<td valign=\"middle\" style=\"font-family:" + NASLOV + ";font-size:22px;font-weight:700;"
                + "letter-spacing:-0.3px;line-height:1;\">Turnirko</td>"
                + "<td valign=\"middle\" align=\"right\" style=\"" + oznaka() + "\">obvestilo po kolu</td>"
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

    private static String pasek() {
        return "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"margin-top:14px;border-collapse:collapse;\"><tr>"
                + odsek(CRNILO) + odsek(CRNILO) + odsek(MODRA) + odsek(ZELENA) + "</tr></table>";
    }

    private static String odsek(String barva) {
        return "<td height=\"4\" style=\"height:4px;background:" + barva + ";font-size:0;line-height:0;\">&nbsp;</td>";
    }

    private static String naslov(Kolo k) {
        return "<div style=\"font-family:" + NASLOV + ";font-size:30px;line-height:1.15;font-weight:700;"
                + "letter-spacing:-0.5px;margin:0 0 8px;\">" + e(zadeva(k)) + "</div>"
                + "<div style=\"font-family:" + MONO + ";font-size:13px;color:" + CRNILO_3 + ";\">"
                + e(podnaslov(k)) + "</div>";
    }

    /* Cela lestvica (ali vrh in okolica) po kolu; prejemnikova ekipa je poudarjena
       v vrstici. */
    private static String lestvica(Kolo k) {
        StringBuilder le = new StringBuilder(naslovSklopa("Lestvica po tem kolu"));
        le.append(tabela());
        le.append("<tr>").append(glavaStolpca("", "left")).append(glavaStolpca("Ekipa", "left"))
                .append(glavaStolpca("Tekme", "right")).append(glavaStolpca("Točke", "right")).append("</tr>");
        int prejsnje = 0;
        for (Vrstica v : k.lestvica()) {
            if (v.mesto() > prejsnje + 1) {
                le.append("<tr>").append(celica("color:" + CRNILO_3 + ";", "…")).append(celica("", ""))
                        .append(celica("", "")).append(celica("", "")).append("</tr>");
            }
            String slogVrstice = v.moja() ? " style=\"background:" + MODRA_MEHKO + ";\"" : "";
            le.append("<tr").append(slogVrstice).append('>')
                    .append(celica("width:32px;font-family:" + MONO + ";font-size:13px;"
                            + (v.moja() ? "font-weight:700;color:" + MODRA + ";" : ""), v.mesto() + "."))
                    .append(celica("", v.moja() ? poudari(v.ekipa()) : e(v.ekipa())))
                    .append(celica("text-align:right;font-family:" + MONO + ";font-size:13px;color:" + CRNILO_3
                            + ";", String.valueOf(v.odigrane())))
                    .append(celica("width:56px;text-align:right;font-family:" + MONO + ";font-size:15px;"
                            + "font-weight:600;", String.valueOf(v.tocke())))
                    .append("</tr>");
            prejsnje = v.mesto();
        }
        le.append("</table>");
        if (prejsnje < k.ekip()) {
            le.append("<div style=\"margin-top:8px;font-family:").append(MONO).append(";font-size:12px;color:")
                    .append(CRNILO_3).append(";\">Ekip v ligi: ").append(k.ekip()).append("</div>");
        }
        return le.toString();
    }

    /* Izidi kola; vrstica prejemnikove ekipe je v svetlo modrem ozadju, njeno
       ime pa poudarjeno. */
    private static String izidi(Kolo k, String moja) {
        StringBuilder iz = new StringBuilder(naslovSklopa("Izidi " + k.kolo() + ". kola"));
        iz.append(tabela());
        for (Izid i : k.izidi()) {
            String poudarek = i.moje() ? "font-weight:700;" : "";
            iz.append("<tr").append(i.moje() ? " style=\"background:" + MODRA_MEHKO + ";\"" : "").append('>')
                    .append(celica("", ime(i.domaci(), moja) + " <span style=\"color:" + CRNILO_3
                            + ";\">–</span> " + ime(i.gost(), moja)))
                    .append(celica("text-align:right;white-space:nowrap;font-family:" + MONO + ";font-size:15px;"
                            + poudarek, i.domacih() == null
                            ? "<span style=\"font-size:12px;color:" + CRNILO_3 + ";\">ni vpisan</span>"
                            : izid(i) + (i.brezBoja() ? " <span style=\"font-size:12px;color:" + CRNILO_3
                                    + ";\">b. b.</span>" : "")))
                    .append("</tr>");
        }
        iz.append("</table>");
        return iz.toString();
    }

    /* Tekme prejemnika v kolu: ime (ali par) poudarjeno, ker je to njegova
       vrstica; pri dvojici poleg tega ime pari. */
    private static String tekme(Osebno o) {
        StringBuilder t = new StringBuilder(naslovSklopa("Tvoje tekme"
                + (o.ekipa() == null ? "" : " · " + poudari(o.ekipa()))));
        t.append(tabela());
        for (Tekma tk : o.tekme()) {
            // vrstica tekme je ena sama vrsta (ime nosi razred tekma-ime, glej glavo); izid in
            // sprememba ratinga sta v isti celici, da imena ostanejo sirina
            t.append("<tr>")
                    .append(celica("white-space:nowrap;font-family:" + MONO + ";font-size:12px;font-weight:600;"
                            + "text-transform:uppercase;letter-spacing:0.06em;color:"
                            + (tk.zmaga() ? ZELENA : RJASTA) + ";", tk.zmaga() ? "zmaga" : "poraz"))
                    .append("<td class=\"tekma-ime\" style=\"padding:8px 10px 8px 0;border-bottom:1px solid " + CRTA
                            + ";vertical-align:top;font-size:14px;\">" + poudari(tk.jaz())
                            + " <span style=\"color:" + CRNILO_3 + ";\">proti</span> " + e(tk.nasprotnik()) + "</td>")
                    .append(celica("text-align:right;white-space:nowrap;font-family:" + MONO
                            + ";font-size:15px;font-weight:600;", tk.moji() + " : " + tk.njegovi()
                            + (tk.sprememba() == null ? "" : " <span style=\"font-size:13px;font-weight:400;color:"
                                    + barvaSpremembe(tk.sprememba()) + ";\">" + predznak(tk.sprememba())
                                    + "</span>")))
                    .append("</tr>");
        }
        t.append("</table>");
        if (o.rating() != null) {
            t.append("<div style=\"padding:12px 0;border-bottom:1px solid ").append(CRTA)
                    .append(";font-family:").append(MONO)
                    .append(";font-size:13px;\">Tvoj rating <strong style=\"font-size:15px;\">")
                    .append(o.rating()).append("</strong>");
            if (o.spremembaKola() != null) {
                t.append(" <span style=\"color:").append(barvaSpremembe(o.spremembaKola())).append(";\">")
                        .append(predznak(o.spremembaKola())).append("</span> v tem kolu");
            }
            t.append("</div>");
        }
        return t.toString();
    }

    /* Naslednje kolo lige, prejemnikova naslednja tekma (ce igra) in gumb nazaj
       v ligo. */
    private static String naslednje(Kolo k, Osebno o, Prejemnik p) {
        StringBuilder b = new StringBuilder();
        String kolo = naslednjeKolo(k);
        if (kolo != null) {
            b.append("<p style=\"margin:0;font-size:16px;line-height:1.5;\"><strong>").append(e(kolo))
                    .append("</strong></p>");
        }
        b.append("<a href=\"").append(e(naslovLige(k, p))).append("\" style=\"display:inline-block;")
                .append("background:").append(MODRA).append(";color:#ffffff;text-decoration:none;")
                .append("font-weight:700;font-size:15px;padding:12px 18px;border-radius:0;margin-top:18px;\">")
                .append(o != null && o.idEkipa() != null ? "Odpri svojo ekipo v ligi" : "Odpri ligo")
                .append(" →</a>");
        return b.toString();
    }

    /* Vrstica o Premium: samo pri igralcu brez paketa (ali v preizkusu). */
    private static String premium(Prejemnik p) {
        return "<p style=\"margin:0;padding:16px 0 0;border-top:1px solid " + CRTA
                + ";font-size:14px;line-height:1.5;\">" + e(p.premium()) + "<br><a href=\"" + e(p.koren())
                + "/narocnina\" style=\"color:" + MODRA + ";\">" + e(p.koren()) + "/narocnina</a></p>";
    }

    private static String dno(Kolo k, Prejemnik p) {
        return "<p style=\"margin:0;font-size:13px;line-height:1.6;color:" + CRNILO_3 + ";\">" + e(razlog(k, p))
                + "<br>Odjava od obvestil po kolu: <a href=\"" + e(p.naslovOdjave()) + "\" style=\"color:"
                + CRNILO_3 + ";\">" + e(p.naslovOdjave()) + "</a></p>" + pasek();
    }

    private static void vrstica(StringBuilder h, String vsebina, String slog) {
        h.append("<tr><td style=\"").append(slog).append("\">").append(vsebina).append("</td></tr>");
    }

    /* Sklop konca zadnja vrstica njegove tabele (ta ima crto), zato sklop
       spodnje crte nima - sicer bi bili dve. */
    private static String sklop() {
        return "padding:28px 0 0;";
    }

    private static String oznaka() {
        return "font-family:" + MONO + ";font-size:12px;text-transform:uppercase;letter-spacing:0.08em;"
                + "color:" + CRNILO_3 + ";";
    }

    private static String naslovSklopa(String besedilo) {
        return "<div style=\"" + oznaka() + "margin-bottom:8px;\">" + besedilo + "</div>";
    }

    private static String tabela() {
        return "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"border-collapse:collapse;font-size:15px;line-height:1.4;\">";
    }

    private static String celica(String slog, String vsebina) {
        return "<td style=\"padding:8px 6px 8px 0;border-bottom:1px solid " + CRTA + ";vertical-align:top;"
                + slog + "\">" + vsebina + "</td>";
    }

    private static String glavaStolpca(String besedilo, String poravnava) {
        return "<td style=\"padding:0 6px 6px 0;border-bottom:1px solid " + CRNILO + ";text-align:" + poravnava
                + ";font-family:" + MONO + ";font-size:11px;text-transform:uppercase;letter-spacing:0.08em;"
                + "color:" + CRNILO_3 + ";\">" + besedilo + "</td>";
    }

    private static String barvaSpremembe(Integer sprememba) {
        if (sprememba == null || sprememba == 0) return CRNILO_3;
        return sprememba > 0 ? ZELENA : RJASTA;
    }

    // ---------- skupni deli ----------

    /* Poudarek prejemnikove ekipe ali igralca: modro in krepko, enako v obeh
       razlicicah sporocila (v besedilu z »). */
    private static String poudari(String besedilo) {
        return "<strong style=\"color:" + MODRA + ";\">" + e(besedilo) + "</strong>";
    }

    private static String ime(String ime, String moja) {
        return ime != null && ime.equals(moja) ? poudari(ime) : e(ime);
    }

    private static String podnaslov(Kolo k) {
        StringBuilder b = new StringBuilder();
        if (k.sezona() != null) {
            b.append(k.sezona()).append(" · ");
        }
        if (k.datum() != null) {
            b.append(k.datum().format(DATUM)).append(" · ");
        }
        b.append("odigrano ").append(k.odigranihKol()).append(" od ").append(k.vsehKol()).append(' ')
                .append(kol(k.vsehKol()));
        return b.toString();
    }

    private static String predogled(Kolo k, Osebno o) {
        return "Izidi in lestvica" + (o != null && !o.tekme().isEmpty() ? ", tvoje tekme" : "")
                + " · " + k.kolo() + ". kolo";
    }

    private static String naslednjeKolo(Kolo k) {
        if (k.naslednjeKolo() == null) {
            return null;
        }
        return "Naslednje kolo: " + k.naslednjeKolo() + ". kolo"
                + (k.naslednjeDatum() == null ? "" : ", " + k.naslednjeDatum().format(DATUM)) + ".";
    }

    private static String naslovLige(Kolo k, Prejemnik p) {
        String naslov = p.koren() + "/lige/" + k.idLiga();
        if (p.osebno() != null && p.osebno().idEkipa() != null) {
            naslov += "?ekipa=" + p.osebno().idEkipa();
        }
        return naslov;
    }

    private static String razlog(Kolo k, Prejemnik p) {
        return p.razlog() == Razlog.IGRA
                ? "To obvestilo dobiš, ker igraš v " + sklon(k.liga(), "ligi") + "."
                : "To obvestilo dobiš, ker spremljaš " + sklon(k.liga(), "ligo") + ".";
    }

    private static final Pattern BESEDA_LIGA = Pattern.compile("(^|[^\\p{L}])([Ll])iga(?![\\p{L}])");

    /* Ime lige se ne sklanja, sklanja se samo beseda "liga" v njem (isto
       pravilo kot ligaVTozilniku v vmesniku): "Liga Savinja D" -> "Ligo
       Savinja D". Ime brez nje ("1. SNTL") dobi besedo spredaj. */
    static String sklon(String ime, String oblika) {
        Matcher m = BESEDA_LIGA.matcher(ime);
        if (!m.find()) {
            return oblika + " " + ime;
        }
        return m.replaceAll(r -> r.group(1) + r.group(2) + oblika.substring(1));
    }

    private static String izid(Izid i) {
        return i.domacih() + " : " + i.gostov();
    }

    private static String predznak(int n) {
        return n > 0 ? "+" + n : n < 0 ? "−" + (-n) : "±0";
    }

    private static String e(String besedilo) {
        return besedilo == null ? "" : HtmlUtils.htmlEscape(besedilo, "UTF-8");
    }

    /* Sklanjanje po stevilu: posebna oblika samo za ostanek 1-4 pri 100
       (isto pravilo kot sklon v vmesniku). */
    private static String sklon(int n, String ena, String dve, String tri, String vec) {
        int o = n % 100;
        return switch (o) {
            case 1 -> ena;
            case 2 -> dve;
            case 3, 4 -> tri;
            default -> vec;
        };
    }

    private static String kol(int n) {
        return sklon(n, "kola", "kol", "kol", "kol");
    }

    private static String tock(int n) {
        return sklon(n, "točka", "točki", "točke", "točk");
    }
}
