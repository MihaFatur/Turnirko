/* E-posta po kolu lige (ObvestilaPoKoluStoritev): izidi kola, lestvica in -
   igralcu, ki je v kolu nastopil - njegove tekme z izidom in spremembo ratinga.

   Gre kot multipart: navadno besedilo za odjemalce brez HTML in HTML, ki sledi
   DESIGN.md (zapisnik na papirju: crte namesto kartic, radij 0, brez senc,
   modra samo za dejanje). Odjemalci pisav ne nalagajo, zato so pisave sklad z
   zamenjavami; slogi so v vrstici, ker jih Gmail iz <style> deloma pobere.
   Imena ekip in igralcev vpisujejo organizatorji - vse gre skozi ubeg.

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

    /* Posamicna tekma prejemnika v kolu, z njegovega vidika. */
    public record Tekma(String nasprotnik, int moji, int njegovi, boolean zmaga, boolean dvojice,
                        Integer sprememba) {}

    /* Del, ki je samo prejemnikov: njegova ekipa, njena srecanja v kolu (liga z
       urami kola jih ima v kolu vec - V31), njegove tekme, rating in naslednje
       srecanje ekipe. Prazno, ce v ligi ne igra. */
    public record Osebno(Long idEkipa, String ekipa, List<Izid> srecanja, List<Tekma> tekme, Integer rating,
                         Integer spremembaKola, String naslednjiNasprotnik, Boolean naslednjeDoma,
                         LocalDate naslednjeDatum) {}

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
        return new SporocilaPoste.Besedilo(zadeva(k, p), besedilo(k, p), html(k, p), glave);
    }

    /* Igralcu zadeva nosi izid njegove ekipe: eno srecanje kot "A 4 : 1 B",
       vec srecanj (vecer z urami) kot "A · 4 : 1, 1 : 4" z vidika ekipe. */
    static String zadeva(Kolo k, Prejemnik p) {
        String izid = izidEkipe(p.osebno());
        if (izid != null) {
            return k.liga() + ", " + k.kolo() + ". kolo: " + izid;
        }
        return k.liga() + ": izidi " + k.kolo() + ". kola";
    }

    private static String izidEkipe(Osebno o) {
        if (o == null) {
            return null;
        }
        List<Izid> odigrana = o.srecanja().stream().filter(i -> i.domacih() != null).toList();
        if (odigrana.isEmpty()) {
            return null;
        }
        if (odigrana.size() == 1) {
            Izid i = odigrana.get(0);
            return i.domaci() + " " + izid(i) + " " + i.gost();
        }
        // locilo, ker se ime ekipe lahko konca s stevilko ("Klub A 1 · 2 : 3")
        StringBuilder b = new StringBuilder(o.ekipa() == null ? "" : o.ekipa() + " · ");
        for (int n = 0; n < odigrana.size(); n++) {
            Izid i = odigrana.get(n);
            boolean doma = i.domaci().equals(o.ekipa());
            b.append(n == 0 ? "" : ", ")
                    .append(doma ? i.domacih() : i.gostov()).append(" : ").append(doma ? i.gostov() : i.domacih());
        }
        return b.toString();
    }

    // ---------- navadno besedilo ----------

    private static String besedilo(Kolo k, Prejemnik p) {
        StringBuilder b = new StringBuilder();
        b.append(k.liga()).append(k.sezona() == null ? "" : " · " + k.sezona()).append('\n');
        b.append(podnaslov(k)).append("\n\n");

        Osebno o = p.osebno();
        if (o != null && !o.tekme().isEmpty()) {
            b.append("Tvoje tekme\n");
            for (Tekma t : o.tekme()) {
                b.append("  ").append(t.zmaga() ? "zmaga " : "poraz ").append(t.moji()).append(" : ")
                        .append(t.njegovi()).append(" · ").append(t.dvojice() ? "dvojice · " : "")
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

        b.append("Izidi ").append(k.kolo()).append(". kola\n");
        for (Izid i : k.izidi()) {
            b.append("  ").append(i.domaci()).append(" – ").append(i.gost()).append("  ")
                    .append(i.domacih() == null ? "izid še ni vpisan" : izid(i))
                    .append(i.brezBoja() ? " (b. b.)" : "").append('\n');
        }
        b.append('\n');

        b.append("Lestvica\n");
        int prejsnje = 0;
        for (Vrstica v : k.lestvica()) {
            if (v.mesto() > prejsnje + 1) {
                b.append("  …\n");
            }
            b.append("  ").append(v.mesto()).append(". ").append(v.ekipa()).append(" · ")
                    .append(v.tocke()).append(' ').append(tock(v.tocke())).append('\n');
            prejsnje = v.mesto();
        }
        b.append('\n');

        String naslednje = naslednje(k, o);
        if (naslednje != null) {
            b.append(naslednje).append("\n\n");
        }
        b.append("Celotna liga: ").append(naslovLige(k, p)).append("\n\n");
        if (p.premium() != null) {
            b.append(p.premium()).append('\n').append(p.koren()).append("/narocnina\n\n");
        }
        b.append("—\n").append(razlog(k, p)).append('\n')
                .append("Odjava od obvestil po kolu: ").append(p.naslovOdjave()).append('\n');
        return b.toString();
    }

    // ---------- HTML ----------

    private static String html(Kolo k, Prejemnik p) {
        Osebno o = p.osebno();
        StringBuilder h = new StringBuilder(8_000);
        h.append("<!doctype html><html lang=\"sl\"><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
                .append("<meta name=\"color-scheme\" content=\"only light\">")
                .append("<title>").append(e(zadeva(k, p))).append("</title></head>")
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

        // glava: znamka, liga, kolo, debela crta
        vrstica(h, "<div style=\"" + oznaka() + "\">Turnirko · " + e(k.liga())
                + (k.sezona() == null ? "" : " · " + e(k.sezona())) + "</div>"
                + "<div style=\"font-family:" + NASLOV + ";font-size:30px;line-height:1.15;"
                + "font-weight:700;letter-spacing:-0.5px;margin:6px 0 4px;\">" + k.kolo() + ". kolo</div>"
                + "<div style=\"font-family:" + MONO + ";font-size:13px;color:" + CRNILO_3 + ";\">"
                + e(podnaslov(k)) + "</div>", "padding:0 0 14px;border-bottom:3px solid " + CRNILO + ";");

        if (o != null && !o.tekme().isEmpty()) {
            StringBuilder t = new StringBuilder();
            t.append(naslovSklopa("Tvoje tekme" + (o.ekipa() == null ? "" : " · " + e(o.ekipa()))));
            t.append(tabela());
            for (Tekma tk : o.tekme()) {
                t.append("<tr>")
                        .append(celica("width:64px;font-family:" + MONO + ";font-size:12px;font-weight:600;"
                                + "text-transform:uppercase;letter-spacing:0.06em;color:"
                                + (tk.zmaga() ? ZELENA : RJASTA) + ";", tk.zmaga() ? "zmaga" : "poraz"))
                        .append(celica("", (tk.dvojice() ? "<span style=\"color:" + CRNILO_3
                                + ";\">dvojice · </span>" : "") + e(tk.nasprotnik())))
                        .append(celica("text-align:right;white-space:nowrap;font-family:" + MONO
                                + ";font-size:15px;font-weight:600;", tk.moji() + " : " + tk.njegovi()))
                        .append(celica("width:56px;text-align:right;white-space:nowrap;font-family:" + MONO
                                + ";font-size:13px;color:" + barvaSpremembe(tk.sprememba()) + ";",
                                tk.sprememba() == null ? "" : predznak(tk.sprememba())))
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
            vrstica(h, t.toString(), sklop());
        }

        StringBuilder iz = new StringBuilder(naslovSklopa("Izidi " + k.kolo() + ". kola"));
        iz.append(tabela());
        for (Izid i : k.izidi()) {
            String poudarek = i.moje() ? "font-weight:700;" : "";
            iz.append("<tr").append(i.moje() ? " style=\"background:" + MODRA_MEHKO + ";\"" : "").append('>')
                    .append(celica(poudarek, e(i.domaci()) + " <span style=\"color:" + CRNILO_3
                            + ";\">–</span> " + e(i.gost())))
                    .append(celica("text-align:right;white-space:nowrap;font-family:" + MONO + ";font-size:15px;"
                            + poudarek, i.domacih() == null
                            ? "<span style=\"font-size:12px;color:" + CRNILO_3 + ";\">ni vpisan</span>"
                            : izid(i) + (i.brezBoja() ? " <span style=\"font-size:12px;color:" + CRNILO_3
                                    + ";\">b. b.</span>" : "")))
                    .append("</tr>");
        }
        iz.append("</table>");
        vrstica(h, iz.toString(), sklop());

        StringBuilder le = new StringBuilder(naslovSklopa("Lestvica"));
        le.append(tabela());
        le.append("<tr>").append(glavaStolpca("", "left")).append(glavaStolpca("Ekipa", "left"))
                .append(glavaStolpca("Tekme", "right")).append(glavaStolpca("Točke", "right")).append("</tr>");
        int prejsnje = 0;
        for (Vrstica v : k.lestvica()) {
            if (v.mesto() > prejsnje + 1) {
                le.append("<tr>").append(celica("color:" + CRNILO_3 + ";", "…")).append(celica("", ""))
                        .append(celica("", "")).append(celica("", "")).append("</tr>");
            }
            String poudarek = v.moja() ? "font-weight:700;" : "";
            le.append("<tr").append(v.moja() ? " style=\"background:" + MODRA_MEHKO + ";\"" : "").append('>')
                    .append(celica("width:32px;font-family:" + MONO + ";font-size:13px;" + poudarek, v.mesto() + "."))
                    .append(celica(poudarek, e(v.ekipa())))
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
        vrstica(h, le.toString(), sklop());

        StringBuilder dno = new StringBuilder();
        String naslednje = naslednje(k, o);
        if (naslednje != null) {
            dno.append("<p style=\"margin:0 0 16px;font-size:15px;line-height:1.5;\">")
                    .append(e(naslednje)).append("</p>");
        }
        dno.append("<a href=\"").append(e(naslovLige(k, p))).append("\" style=\"display:inline-block;")
                .append("background:").append(MODRA).append(";color:#ffffff;text-decoration:none;")
                .append("font-weight:700;font-size:15px;padding:12px 18px;border-radius:0;\">")
                .append(o != null && o.idEkipa() != null ? "Odpri svojo ekipo v ligi" : "Odpri ligo")
                .append(" →</a>");
        if (p.premium() != null) {
            dno.append("<p style=\"margin:20px 0 0;padding:12px 0 0;border-top:1px solid ").append(CRTA)
                    .append(";font-size:14px;line-height:1.5;\">").append(e(p.premium()))
                    .append(" <a href=\"").append(e(p.koren())).append("/narocnina\" style=\"color:")
                    .append(MODRA).append(";\">Naročnina</a></p>");
        }
        vrstica(h, dno.toString(), "padding:20px 0 24px;border-bottom:1px solid " + CRTA + ";");

        vrstica(h, "<p style=\"margin:0;font-size:12px;line-height:1.6;color:" + CRNILO_3 + ";\">"
                + e(razlog(k, p)) + " Obvestila izklopiš v meniju računa (Obvestila) ali tukaj: "
                + "<a href=\"" + e(p.naslovOdjave()) + "\" style=\"color:" + CRNILO_3 + ";\">odjava od "
                + "obvestil po kolu</a>.</p>", "padding:16px 0 0;");

        h.append("</table></td></tr></table></body></html>");
        return h.toString();
    }

    private static void vrstica(StringBuilder h, String vsebina, String slog) {
        h.append("<tr><td style=\"").append(slog).append("\">").append(vsebina).append("</td></tr>");
    }

    /* Sklop konca zadnja vrstica njegove tabele (ta ima crto), zato sklop
       spodnje crte nima - sicer bi bili dve. */
    private static String sklop() {
        return "padding:20px 0 0;";
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

    private static String podnaslov(Kolo k) {
        StringBuilder b = new StringBuilder();
        if (k.datum() != null) {
            b.append(k.datum().format(DATUM)).append(" · ");
        }
        b.append("odigrano ").append(k.odigranihKol()).append(" od ").append(k.vsehKol()).append(' ')
                .append(kol(k.vsehKol()));
        return b.toString();
    }

    private static String predogled(Kolo k, Osebno o) {
        String izid = izidEkipe(o);
        if (izid != null) {
            return izid + " · izidi kola in lestvica";
        }
        return k.izidi().size() + " " + srecanj(k.izidi().size()) + " · izidi kola in lestvica";
    }

    private static String naslednje(Kolo k, Osebno o) {
        if (o != null && o.naslednjiNasprotnik() != null) {
            StringBuilder b = new StringBuilder("Naslednje srečanje");
            if (o.naslednjeDatum() != null) {
                b.append(' ').append(o.naslednjeDatum().format(DATUM));
            }
            b.append(Boolean.TRUE.equals(o.naslednjeDoma()) ? ", doma: " : ", v gosteh: ")
                    .append(o.naslednjiNasprotnik()).append('.');
            return b.toString();
        }
        if (k.naslednjeKolo() != null) {
            return "Naslednje kolo: " + k.naslednjeKolo() + ". kolo"
                    + (k.naslednjeDatum() == null ? "" : ", " + k.naslednjeDatum().format(DATUM)) + ".";
        }
        return null;
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

    private static String srecanj(int n) {
        return sklon(n, "srečanje", "srečanji", "srečanja", "srečanj");
    }
}
