/* E-posta administratorju, ko racun po potrditvi naslova caka na njegovo
   odlocitev (RegistracijaStoritev, ObvestiloAdminuStoritev): igralec, ki se ni
   povezal sam z igralcem v sifrantu, in organizator, ki ga potrdi vedno admin.

   Pove tri stvari, zaradi katerih admin odpre Dostope: kdo je (ime, naslov,
   zeleni klub), kateri paket je izbral (placan paket ceka najdlje - zato je
   v zadevi in na vrhu) in zakaj racun caka. Osebnih podatkov, ki jih admin v
   Dostopih tako ali tako vidi (datum rojstva, naslov skrbnika), v posto ne
   damo.

   Gre kot multipart (besedilo + HTML) v istem slogu kot obvestilo po kolu
   (PostniOkvir, DESIGN.md). Ime in naslov vpise obiskovalec strani, zato gre
   vse skozi ubeg, v zadevi in besedilu pa so krmilni znaki zamenjani s
   presledkom (drugace bi ime z novo vrstico lahko podtaknilo glavo ali
   lazno vrstico). Sporocilo nima odjave: gre enemu naslovu, ki ga doloca
   nastavitev streznika. */
package si.turnirko.posta;

import static si.turnirko.posta.PostniOkvir.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public final class SporociloAdminu {

    private static final DateTimeFormatter DATUM = DateTimeFormatter.ofPattern("d. M. yyyy");

    /* organizator: vloga racuna (drugace igralec). paket: kratko ime placanega
       paketa ("Premium", "Plus") ali null, ce racun nima placanega paketa;
       cikel ("letno", "mesecno") in cena ob sklenitvi sta lahko null. klub je
       neobvezen, razlog pa je ze besedilo za admina. */
    public record Zahteva(boolean organizator, String ime, String email, String klub, String paket, String cikel,
                          Double cena, boolean skrbnikPotrdil, String razlog, LocalDate registriran,
                          String naslovDostopov) {

        boolean placan() {
            return paket != null;
        }

        /* "Igralec Premium", "Organizator Plus". */
        String paketVTabeli() {
            return (organizator ? "Organizator " : "Igralec ") + paket;
        }
    }

    private SporociloAdminu() {}

    public static SporocilaPoste.Besedilo sestavi(Zahteva z) {
        return new SporocilaPoste.Besedilo(zadeva(z), besedilo(z), html(z), Map.of());
    }

    /* Ime je v zadevi, ker se po njem sporocilo isce v nabiralniku; placan
       paket je v njej, ker placilo ne sme cakati kot navadna zahteva. */
    static String zadeva(Zahteva z) {
        return "Turnirko: " + (z.organizator() ? "organizator" : "račun") + " čaka na potrditev · "
                + vVrsto(z.ime()) + (z.placan() ? " · " + z.paket() : "");
    }

    // ---------- navadno besedilo ----------

    private static String besedilo(Zahteva z) {
        StringBuilder b = new StringBuilder();
        b.append("TURNIRKO · obvestilo za administratorja\n\n");
        b.append(naslov(z)).append('\n');
        if (z.placan()) {
            b.append(placanoVrstica(z)).append('\n');
        }
        b.append('\n');
        b.append(z.organizator() ? "Organizator:  " : "Igralec:      ").append(vVrsto(z.ime())).append('\n');
        b.append("E-pošta:      ").append(vVrsto(z.email())).append(" (potrjena)\n");
        b.append("Želeni klub:  ").append(z.klub() == null ? "ni izbran" : vVrsto(z.klub())).append('\n');
        b.append("Paket:        ").append(paket(z)).append('\n');
        if (z.skrbnikPotrdil()) {
            b.append("Skrbnik:      soglasje potrjeno (mlajši od 15 let)\n");
        }
        b.append("Registracija: ").append(z.registriran().format(DATUM)).append("\n\n");
        b.append(vprasanje(z)).append('\n').append(z.razlog()).append("\n\n");
        b.append(poziv(z)).append('\n');
        b.append("Odpri Dostope: ").append(z.naslovDostopov()).append("\n\n");
        b.append(dno()).append('\n');
        return b.toString();
    }

    // ---------- HTML ----------

    private static String html(Zahteva z) {
        StringBuilder h = zacetek(zadeva(z), "", predogled(z));
        vrstica(h, glava("obvestilo za admina"), "padding:0 0 26px;");
        vrstica(h, naslovHtml(z), "padding:0 0 6px;");
        vrstica(h, podatki(z), sklop());
        vrstica(h, razlog(z), sklop());
        vrstica(h, naprej(z), sklop());
        vrstica(h, "<p style=\"margin:0;font-size:13px;line-height:1.6;color:" + CRNILO_3 + ";\">" + e(dno())
                + "</p>" + pasek(), "padding:32px 0 0;");
        return konec(h);
    }

    private static String naslovHtml(Zahteva z) {
        StringBuilder n = new StringBuilder("<div style=\"font-family:" + NASLOV + ";font-size:30px;"
                + "line-height:1.15;font-weight:700;letter-spacing:-0.5px;margin:0 0 8px;\">" + e(naslov(z))
                + "</div>"
                + "<div style=\"font-family:" + MONO + ";font-size:13px;color:" + CRNILO_3 + ";\">"
                + e(vVrsto(z.ime())) + " · " + z.registriran().format(DATUM) + "</div>");
        if (z.placan()) {
            n.append("<p style=\"margin:16px 0 0;font-size:16px;line-height:1.5;\"><strong>")
                    .append(e(placanoNaslov(z))).append("</strong>")
                    .append(e(placanoRep(z))).append("</p>");
        }
        return n.toString();
    }

    /* Kdo je in kaj je izbral; ime poudarjeno kot prejemnik v obvestilu po kolu,
       oznake v mono kot glave stolpcev. */
    private static String podatki(Zahteva z) {
        StringBuilder t = new StringBuilder(naslovSklopa("Kdo se je prijavil")).append(tabela());
        podatek(t, z.organizator() ? "Organizator" : "Igralec", poudari(vVrsto(z.ime())));
        podatek(t, "E-pošta", e(vVrsto(z.email())) + " " + pripis("potrjena", CRNILO_3));
        podatek(t, "Želeni klub", z.klub() == null ? pripis("ni izbran", CRNILO_3) : e(vVrsto(z.klub())));
        podatek(t, "Paket", z.placan()
                ? "<strong>" + e(z.paketVTabeli()) + "</strong> " + pripis(sPaketa(z), CRNILO_3)
                        + " " + pripis("plačano", ZELENA)
                : "Brezplačno");
        if (z.skrbnikPotrdil()) {
            podatek(t, "Skrbnik", "soglasje potrjeno " + pripis("mlajši od 15 let", CRNILO_3));
        }
        podatek(t, "Registracija", z.registriran().format(DATUM));
        return t.append("</table>").toString();
    }

    private static void podatek(StringBuilder t, String oznaka, String vrednost) {
        t.append("<tr>")
                .append(celica("width:112px;padding-top:11px;font-family:" + MONO + ";font-size:11px;"
                        + "text-transform:uppercase;letter-spacing:0.08em;color:" + CRNILO_3 + ";", e(oznaka)))
                .append(celica("word-break:break-word;overflow-wrap:anywhere;", vrednost))
                .append("</tr>");
    }

    private static String pripis(String besedilo, String barva) {
        return "<span style=\"font-family:" + MONO + ";font-size:12px;color:" + barva + ";\">" + e(besedilo)
                + "</span>";
    }

    private static String razlog(Zahteva z) {
        return naslovSklopa(e(vprasanje(z)))
                + "<p style=\"margin:0;font-size:15px;line-height:1.5;\">" + e(z.razlog()) + "</p>";
    }

    private static String naprej(Zahteva z) {
        return "<p style=\"margin:0;font-size:16px;line-height:1.5;\"><strong>" + e(poziv(z)) + "</strong></p>"
                + "<a href=\"" + e(z.naslovDostopov()) + "\" style=\"display:inline-block;background:" + MODRA
                + ";color:#ffffff;text-decoration:none;font-weight:700;font-size:15px;padding:12px 18px;"
                + "border-radius:0;margin-top:18px;\">Odpri Dostope →</a>";
    }

    // ---------- skupni deli ----------

    private static String naslov(Zahteva z) {
        return z.organizator() ? "Organizator čaka na potrditev" : "Račun čaka na potrditev";
    }

    /* Naslov sklopa z razlogom: igralca admin povezuje z zapisom v sifrantu,
       zato ga zanima, zakaj tega ni storilo ujemanje; organizatorja potrdi
       vedno, zato razloga za iskanje ni. */
    private static String vprasanje(Zahteva z) {
        return z.organizator() ? "Zakaj čaka" : "Zakaj ni samodejne povezave";
    }

    private static String poziv(Zahteva z) {
        return z.organizator()
                ? "Potrdi organizatorja (in po potrebi izberi klub) ali ga zavrni."
                : "Poveži račun z igralcem v šifrantu ali ga zavrni.";
    }

    /* "Že plačan Premium" / "Že plačan paket Plus" + rep stavka. */
    private static String placanoNaslov(Zahteva z) {
        return "Že plačan " + (z.organizator() ? "paket " : "") + z.paket();
    }

    private static String placanoRep(Zahteva z) {
        return " - račun je smiselno " + (z.organizator() ? "potrditi" : "povezati") + " čim prej.";
    }

    private static String placanoVrstica(Zahteva z) {
        return placanoNaslov(z) + placanoRep(z);
    }

    private static String dno() {
        return "To sporočilo pošlje Turnirko samodejno, enkrat za vsak račun, ko ta po potrditvi e-pošte"
                + " čaka na tvojo odločitev.";
    }

    private static String predogled(Zahteva z) {
        return vVrsto(z.ime()) + " · " + (z.placan() ? "plačan " + z.paketVTabeli() : "brezplačen račun")
                + (z.organizator() ? " · čaka na potrditev organizatorja" : " · čaka na povezavo z igralcem");
    }

    /* "letno · 9,99 €" - cikel in cena ob sklenitvi. */
    private static String sPaketa(Zahteva z) {
        String cikel = z.cikel() == null ? "" : z.cikel();
        String cena = z.cena() == null ? "" : SporocilaPoste.cena(z.cena());
        return cikel.isEmpty() ? cena : cena.isEmpty() ? cikel : cikel + " · " + cena;
    }

    private static String paket(Zahteva z) {
        return z.placan() ? z.paketVTabeli() + " · " + sPaketa(z) + " · plačano" : "Brezplačno";
    }

    /* Vnos obiskovalca v eno vrstico: krmilni znaki (tudi nova vrstica) so
       presledki. */
    private static String vVrsto(String vnos) {
        return vnos == null ? "" : vnos.replaceAll("\\p{Cntrl}", " ").trim();
    }
}
