/* Zavrne prijavo (HTTP Basic), ce je v zadnjem casu nabrala prevec
   NEUSPELIH prijav - se preden se geslo sploh preveri.

   Meje so tri, ker ena sama ne zadosca:
   - racun z istega naslova IP (nizka meja): ustavi ugibanje gesla iz enega
     racunalnika, a ne zaklene racuna za vse ostale;
   - racun z vseh naslovov skupaj (visoka meja): ustavi porazdeljeno ugibanje;
   - naslov IP za vse racune: ustavi preizkusanje veliko racunov z enega mesta.
   Blokada samo po racunu bi bila vrata za nagajanje: kdorkoli bi desetkrat
   vpisal napacno geslo in tuj racun zaklenil. Kdor je blokiran, si geslo
   nastavi znova s kodo po posti, kar stevec racuna pobrise.

   Neuspehe beleze dogodki Spring Securityja (PrijavaDogodki); uspesna prijava
   stevca racuna pobrise. Odgovor je 429 v obliki problem-detail. Filter ni
   bean (registrira ga VarnostneNastavitve), da ga vsebnik ne bi se enkrat
   pripel na vse zahteve zunaj varnostne verige. */
package si.turnirko.nastavitve;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import si.turnirko.storitve.OmejevalnikPoskusov;

public class OmejitevPrijavFilter extends OncePerRequestFilter {

    static final int NAJVEC_NA_RACUN_Z_NASLOVA = 10;
    static final int NAJVEC_NA_RACUN = 100;
    static final int NAJVEC_NA_NASLOV = 50;
    static final Duration OKNO = Duration.ofMinutes(15);

    private final OmejevalnikPoskusov omejevalnik;

    public OmejitevPrijavFilter(OmejevalnikPoskusov omejevalnik) {
        this.omejevalnik = omejevalnik;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest zahteva, HttpServletResponse odgovor,
                                    FilterChain veriga) throws ServletException, IOException {
        String glava = zahteva.getHeader("Authorization");
        if (glava != null && glava.regionMatches(true, 0, "Basic ", 0, 6)) {
            String ime = uporabniskoIme(glava);
            String naslov = zahteva.getRemoteAddr();
            boolean blokiran = omejevalnik.jeCezMejo(
                    OmejevalnikPoskusov.kljucNaslovaPrijave(naslov), NAJVEC_NA_NASLOV, OKNO)
                    || (ime != null && (
                            omejevalnik.jeCezMejo(OmejevalnikPoskusov.kljucPrijave(ime, naslov),
                                    NAJVEC_NA_RACUN_Z_NASLOVA, OKNO)
                            || omejevalnik.jeCezMejo(OmejevalnikPoskusov.kljucPrijave(ime),
                                    NAJVEC_NA_RACUN, OKNO)));
            if (blokiran) {
                odgovor.setStatus(429);
                odgovor.setContentType("application/json;charset=UTF-8");
                odgovor.getWriter().write(
                        "{\"title\":\"Prevec poskusov prijave\","
                        + "\"detail\":\"Preveč neuspelih prijav. Počakaj 15 minut ali si nastavi "
                        + "novo geslo (Pozabljeno geslo).\"}");
                return;
            }
        }
        veriga.doFilter(zahteva, odgovor);
    }

    /* Prijavno ime iz glave "Basic base64(ime:geslo)"; null, ce glava ni berljiva
       (tako zahtevo zavrne sam Basic filter). */
    static String uporabniskoIme(String glava) {
        try {
            String par = new String(Base64.getDecoder().decode(glava.substring(6).trim()),
                    StandardCharsets.UTF_8);
            int dvopicje = par.indexOf(':');
            return dvopicje < 0 ? null : par.substring(0, dvopicje);
        } catch (IllegalArgumentException napaka) {
            return null;
        }
    }
}
