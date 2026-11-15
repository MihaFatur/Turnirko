/* E-posta administratorju, ko je Stripe placilo potrdil, v aplikaciji pa iz
   njega ni nastalo nic (PlacilaStoritev, najdba E1 pregleda kakovosti): racun
   placljive registracije ni nastal ali pa je placana nadgradnja racuna, ki ga
   ni vec. Brez tega sporocila je bila edina sled vrstica v dnevniku Dockerja.

   Nosi tisto, s cimer admin placilo najde v Stripu (seja, narocnina, stranka)
   in razlog. Naslov placnika je v sporocilu, ker mu mora admin odgovoriti; v
   dnevnik aplikacije ne gre. Vrednosti pridejo iz Stripovega dogodka in iz
   obrazca registracije, zato gre vse skozi ubeg in v eno vrstico (kot
   SporociloAdminu). Sporocilo nima odjave: gre enemu naslovu, ki ga doloca
   nastavitev streznika (turnirko.posta.admin). */
package si.turnirko.posta;

import static si.turnirko.posta.PostniOkvir.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class SporociloOPlacilu {

    /* razlog je ze besedilo za admina. Vse razen seje je neobvezno: pri seji
       brez cakajoce registracije poznamo samo to, kar je v dogodku. idCakajoce
       je vrstica cakajoca_registracija, ki ostane v bazi (ali null, ce je ni);
       podrobnost je tehnicna opomba (vrsta napake). */
    public record Placilo(String razlog, String idSeje, String idNarocnine, String idStranke, String paket,
                          String emailPlacnika, Long idCakajoce, String podrobnost) {}

    private SporociloOPlacilu() {}

    public static SporocilaPoste.Besedilo sestavi(Placilo p) {
        return new SporocilaPoste.Besedilo(zadeva(p), besedilo(p), html(p), Map.of());
    }

    /* Seja je v zadevi, ker se po njej sporocilo isce v nabiralniku in placilo v Stripu. */
    static String zadeva(Placilo p) {
        return "Turnirko: plačilo brez računa · " + vVrsto(p.idSeje());
    }

    private record Podatek(String oznaka, String vrednost) {}

    private static List<Podatek> podatki(Placilo p) {
        List<Podatek> v = new ArrayList<>();
        v.add(new Podatek("Stripova seja", vVrsto(p.idSeje())));
        dodaj(v, "Naročnina", p.idNarocnine());
        dodaj(v, "Stranka", p.idStranke());
        dodaj(v, "Paket", p.paket());
        dodaj(v, "E-pošta plačnika", p.emailPlacnika());
        if (p.idCakajoce() != null) {
            v.add(new Podatek("Čakajoča registracija", "št. " + p.idCakajoce() + " (ostane v bazi, dokler je ne odstraniš)"));
        }
        dodaj(v, "Podrobnost", p.podrobnost());
        return v;
    }

    private static void dodaj(List<Podatek> v, String oznaka, String vrednost) {
        String cista = vVrsto(vrednost);
        if (!cista.isEmpty()) {
            v.add(new Podatek(oznaka, cista));
        }
    }

    // ---------- navadno besedilo ----------

    private static String besedilo(Placilo p) {
        StringBuilder b = new StringBuilder();
        b.append("TURNIRKO · obvestilo za administratorja\n\n");
        b.append(naslov()).append('\n');
        b.append(vVrsto(p.razlog())).append("\n\n");
        for (Podatek d : podatki(p)) {
            b.append(d.oznaka()).append(": ").append(d.vrednost()).append('\n');
        }
        b.append('\n').append(poziv()).append("\n\n");
        b.append(dno()).append('\n');
        return b.toString();
    }

    // ---------- HTML ----------

    private static String html(Placilo p) {
        StringBuilder h = zacetek(zadeva(p), "", naslov() + " · " + vVrsto(p.idSeje()));
        vrstica(h, glava("obvestilo za admina"), "padding:0 0 26px;");
        vrstica(h, "<div style=\"font-family:" + NASLOV + ";font-size:30px;line-height:1.15;font-weight:700;"
                + "letter-spacing:-0.5px;margin:0 0 8px;color:" + RJASTA + ";\">" + e(naslov()) + "</div>"
                + "<p style=\"margin:0;font-size:16px;line-height:1.5;\">" + e(vVrsto(p.razlog())) + "</p>",
                "padding:0 0 6px;");
        StringBuilder t = new StringBuilder(naslovSklopa("Plačilo v Stripu")).append(tabela());
        for (Podatek d : podatki(p)) {
            t.append("<tr>")
                    .append(celica("width:150px;padding-top:11px;font-family:" + MONO + ";font-size:11px;"
                            + "text-transform:uppercase;letter-spacing:0.08em;color:" + CRNILO_3 + ";", e(d.oznaka())))
                    .append(celica("word-break:break-word;overflow-wrap:anywhere;font-family:" + MONO
                            + ";font-size:13px;", e(d.vrednost())))
                    .append("</tr>");
        }
        vrstica(h, t.append("</table>").toString(), sklop());
        vrstica(h, "<p style=\"margin:0;font-size:16px;line-height:1.5;\"><strong>" + e(poziv()) + "</strong></p>",
                sklop());
        vrstica(h, "<p style=\"margin:0;font-size:13px;line-height:1.6;color:" + CRNILO_3 + ";\">" + e(dno())
                + "</p>" + pasek(), "padding:32px 0 0;");
        return konec(h);
    }

    // ---------- skupni deli ----------

    private static String naslov() {
        return "Plačilo je uspelo, v Turnirku pa iz njega ni nastalo nič";
    }

    private static String poziv() {
        return "V Stripu poišči sejo (iskanje po oznaki cs_...), nato plačilo vrni in naročnino prekliči ali pa"
                + " račun uredi ročno in plačniku odgovori.";
    }

    private static String dno() {
        return "To sporočilo pošlje Turnirko samodejno, ko Stripe potrdi plačilo, ki ga aplikacija ne more"
                + " pripisati nobenemu računu.";
    }

    /* Vrednost v eno vrstico: krmilni znaki (tudi nova vrstica) so presledki. */
    private static String vVrsto(String vnos) {
        return vnos == null ? "" : vnos.replaceAll("\\p{Cntrl}", " ").trim();
    }
}
