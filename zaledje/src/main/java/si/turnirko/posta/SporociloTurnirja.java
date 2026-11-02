/* E-posta po turnirju (ObvestilaPoTurnirjuStoritev, V58, paket Navijac in
   Igralec Premium): kako je zakljucen turnir minil igralcem, ki jih prejemnik
   spremlja - po kategoriji mesto, izkupicek in sprememba ratinga, pod tem
   najnovejse tekme.

   Isti okvir in slog kot e-posta po kolu (SporociloKola, DESIGN.md: crte
   namesto kartic, radij 0, modra samo za dejanje): zadeva nosi samo ime
   turnirja, ker se po njem sporocilo isce v nabiralniku. Gre kot multipart z
   glavama List-Unsubscribe (odjava z enim klikom, RFC 8058); odjava je ista kot
   pri obvestilu po kolu - en izklop za obe vrsti e-poste. Imena turnirjev,
   kategorij in igralcev vpisujejo organizatorji in uvoz - vse gre skozi ubeg. */
package si.turnirko.posta;

import static si.turnirko.posta.PostniOkvir.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SporociloTurnirja {

    private static final DateTimeFormatter DATUM = DateTimeFormatter.ofPattern("d. M. yyyy");

    /* Ena tekma igralca, z njegovega vidika; sprememba je lahko prazna (tekma, ki
       ratinga ni premaknila). */
    public record Tekma(String nasprotnik, int moji, int njegovi, boolean zmaga, Integer sprememba) {}

    /* Nastop igralca v eni kategoriji turnirja: mesto je lahko prazno (turnir se
       je zakljucil brez uvrstitve); mestoDo je zadnje mesto, ki ga deli. */
    public record Kategorija(String ime, Integer mesto, Integer mestoDo, Integer udelezencev,
                             int zmage, int porazi, Integer sprememba) {}

    /* Igralec, ki ga prejemnik spremlja in je na turnirju nastopil. */
    public record Igralec(Long id, String ime, String klub, List<Kategorija> kategorije, List<Tekma> tekme) {}

    public record Turnir(Long id, String ime, LocalDate datum, String kraj) {}

    public record Prejemnik(String naslovOdjave, String naslovOdjaveApi, String koren) {}

    private SporociloTurnirja() {}

    public static SporocilaPoste.Besedilo sestavi(Turnir t, List<Igralec> igralci, Prejemnik p) {
        Map<String, String> glave = new LinkedHashMap<>();
        glave.put("List-Unsubscribe", "<" + p.naslovOdjaveApi() + ">");
        glave.put("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
        return new SporocilaPoste.Besedilo(zadeva(t), besedilo(t, igralci, p), html(t, igralci, p), glave);
    }

    static String zadeva(Turnir t) {
        return t.ime() + ": izidi";
    }

    // ---------- navadno besedilo ----------

    private static String besedilo(Turnir t, List<Igralec> igralci, Prejemnik p) {
        StringBuilder b = new StringBuilder();
        b.append("TURNIRKO · obvestilo po turnirju\n\n");
        b.append(t.ime()).append('\n').append(podnaslov(t)).append("\n\n");
        for (Igralec i : igralci) {
            b.append(i.ime()).append(i.klub() == null ? "" : " · " + i.klub()).append('\n');
            for (Kategorija k : i.kategorije()) {
                b.append("  ").append(k.ime()).append(": ").append(mesto(k)).append(" · ")
                        .append(k.zmage()).append('–').append(k.porazi());
                if (k.sprememba() != null) {
                    b.append(" · rating ").append(predznak(k.sprememba()));
                }
                b.append('\n');
            }
            for (Tekma tk : i.tekme()) {
                b.append("    ").append(tk.zmaga() ? "zmaga " : "poraz ").append(tk.moji()).append(" : ")
                        .append(tk.njegovi()).append(" proti ").append(tk.nasprotnik());
                if (tk.sprememba() != null) {
                    b.append(" (").append(predznak(tk.sprememba())).append(')');
                }
                b.append('\n');
            }
            b.append("  Profil: ").append(p.koren()).append("/igralci/").append(i.id()).append("/profil\n\n");
        }
        b.append("Turnir: ").append(p.koren()).append("/turnirji/").append(t.id()).append("\n\n");
        b.append("Obvestilo dobivaš, ker spremljaš ").append(imena(igralci)).append(".\n");
        b.append("Odjava od obvestil: ").append(p.naslovOdjave()).append('\n');
        return b.toString();
    }

    // ---------- HTML ----------

    private static String html(Turnir t, List<Igralec> igralci, Prejemnik p) {
        StringBuilder h = zacetek(zadeva(t), null, predogled(t, igralci));
        vrstica(h, glava("obvestilo po turnirju"), "padding:0 0 26px;");
        vrstica(h, naslov(t), "padding:0 0 6px;");
        for (Igralec i : igralci) {
            vrstica(h, igralec(i, p), sklop());
        }
        vrstica(h, gumb(t, p), "padding:28px 0 0;");
        vrstica(h, dno(igralci, p), "padding:32px 0 0;");
        return konec(h);
    }

    private static String naslov(Turnir t) {
        return "<div style=\"font-family:" + NASLOV + ";font-size:30px;line-height:1.15;font-weight:700;"
                + "letter-spacing:-0.5px;margin:0 0 8px;\">" + e(zadeva(t)) + "</div>"
                + "<div style=\"font-family:" + MONO + ";font-size:13px;color:" + CRNILO_3 + ";\">"
                + e(podnaslov(t)) + "</div>";
    }

    /* Igralec: ime (povezava na profil), kategorije z mestom in izkupickom, pod
       njimi tekme. Ena tabela na igralca, da se vrstice ne pomesajo med njimi. */
    private static String igralec(Igralec i, Prejemnik p) {
        StringBuilder s = new StringBuilder(naslovSklopa(
                "<a href=\"" + e(p.koren()) + "/igralci/" + i.id() + "/profil\" style=\"color:" + MODRA
                        + ";text-decoration:none;\">" + e(i.ime()) + "</a>"
                        + (i.klub() == null ? "" : " <span style=\"color:" + CRNILO_3 + ";\">· "
                                + e(i.klub()) + "</span>")));
        s.append(tabela());
        for (Kategorija k : i.kategorije()) {
            s.append("<tr>")
                    .append(celica("", "<strong>" + e(k.ime()) + "</strong>"))
                    .append(celica("white-space:nowrap;font-family:" + MONO + ";font-size:13px;", e(mesto(k))))
                    .append(celica("text-align:right;white-space:nowrap;font-family:" + MONO
                            + ";font-size:15px;font-weight:600;", k.zmage() + "–" + k.porazi()
                            + (k.sprememba() == null ? "" : " <span style=\"font-size:13px;font-weight:400;color:"
                                    + barva(k.sprememba()) + ";\">" + predznak(k.sprememba()) + "</span>")))
                    .append("</tr>");
        }
        for (Tekma tk : i.tekme()) {
            s.append("<tr>")
                    .append(celica("white-space:nowrap;font-family:" + MONO + ";font-size:12px;font-weight:600;"
                            + "text-transform:uppercase;letter-spacing:0.06em;color:"
                            + (tk.zmaga() ? ZELENA : RJASTA) + ";", tk.zmaga() ? "zmaga" : "poraz"))
                    .append(celica("font-size:14px;", "<span style=\"color:" + CRNILO_3 + ";\">proti</span> "
                            + e(tk.nasprotnik())))
                    .append(celica("text-align:right;white-space:nowrap;font-family:" + MONO
                            + ";font-size:14px;", tk.moji() + " : " + tk.njegovi()
                            + (tk.sprememba() == null ? "" : " <span style=\"color:" + barva(tk.sprememba())
                                    + ";\">" + predznak(tk.sprememba()) + "</span>")))
                    .append("</tr>");
        }
        s.append("</table>");
        return s.toString();
    }

    private static String gumb(Turnir t, Prejemnik p) {
        return "<a href=\"" + e(p.koren()) + "/turnirji/" + t.id() + "\" style=\"display:inline-block;"
                + "background:" + MODRA + ";color:#ffffff;text-decoration:none;font-weight:700;font-size:15px;"
                + "padding:12px 18px;border-radius:0;\">Odpri turnir →</a>";
    }

    private static String dno(List<Igralec> igralci, Prejemnik p) {
        return "<p style=\"margin:0;font-size:13px;line-height:1.6;color:" + CRNILO_3 + ";\">"
                + "Obvestilo dobivaš, ker spremljaš " + e(imena(igralci)) + ".<br>Odjava od obvestil: "
                + "<a href=\"" + e(p.naslovOdjave()) + "\" style=\"color:" + CRNILO_3 + ";\">"
                + e(p.naslovOdjave()) + "</a></p>" + pasek();
    }

    // ---------- skupni deli ----------

    private static String predogled(Turnir t, List<Igralec> igralci) {
        return "Izidi turnirja " + t.ime() + ": " + imena(igralci);
    }

    private static String podnaslov(Turnir t) {
        StringBuilder b = new StringBuilder();
        if (t.datum() != null) {
            b.append(t.datum().format(DATUM));
        }
        if (t.kraj() != null && !t.kraj().isBlank()) {
            b.append(b.length() > 0 ? " · " : "").append(t.kraj());
        }
        return b.toString();
    }

    /* "3. mesto od 19", "3.–4. mesto od 19", brez uvrstitve "brez uvrstitve". */
    static String mesto(Kategorija k) {
        if (k.mesto() == null) {
            return "brez uvrstitve";
        }
        String razpon = k.mestoDo() != null && k.mestoDo() > k.mesto()
                ? k.mesto() + ".–" + k.mestoDo() + "." : k.mesto() + ".";
        return razpon + " mesto" + (k.udelezencev() == null ? "" : " od " + k.udelezencev());
    }

    private static String imena(List<Igralec> igralci) {
        return String.join(", ", igralci.stream().map(Igralec::ime).toList());
    }

    private static String predznak(int n) {
        return n > 0 ? "+" + n : n < 0 ? "−" + Math.abs(n) : "±0";
    }

    private static String barva(Integer sprememba) {
        if (sprememba == null || sprememba == 0) return CRNILO_3;
        return sprememba > 0 ? ZELENA : RJASTA;
    }
}
