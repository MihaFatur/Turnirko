/* Ob prvem zagonu ustvari zacetnega administratorja, ce v bazi se ni
   nobenega uporabnika. Gesla nikoli ne vpisujemo v migracijo (bilo bi enako
   pri vseh namestitvah), zato nastane tu.

   Fiksnega privzetega gesla na strezniku NI. Zacetni admin je edini racun,
   ki sme vse, in ce bi imel privzetek, bi ga bilo mogoce pozabiti zamenjati
   - to je najpogostejsi nacin, kako se taksne aplikacije prevzame. Zato:
   brez nastavljenega gesla se aplikacija raje ne zazene, kot da bi tekla
   odprta. Napaka pove natanko, kaj nastaviti.

   Izjeme ni vec: namizna razlicica z lokalnim admin/admin je odsla skupaj s
   SQLite (prenova baze, okt 2026). */
package si.turnirko.nastavitve;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.prenos.PrenosUkaz;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

/* Ukaz "prenos" (faza 3 prenove baze) polni PRAZNO bazo: admin, ustvarjen pred
   njim, bi prenos zavrnil, po njem pa bi bil odvec (racuni pridejo iz SQLite). */
@Component
@Profile("!" + PrenosUkaz.PROFIL)
public class ZacetniAdmin implements ApplicationRunner {

    private static final Logger dnevnik = LoggerFactory.getLogger(ZacetniAdmin.class);

    /* Ime nastavitve navajamo v napakah, da je popravek ocitan. */
    private static final String NASTAVITEV_GESLA = "turnirko.admin.privzeto-geslo";

    /* Dolzina, pod katero geslo administratorja na strezniku ne pride skozi.
       Meja je namenoma nizja od tega, kar bi svetovali (naklucnih 24+
       znakov), a dovolj visoka, da ustavi "admin", "geslo123" in podobno. */
    private static final int NAJMANJSA_DOLZINA = 12;

    private final UporabnikRepozitorij uporabnikRepozitorij;
    private final PasswordEncoder kodirnik;
    private final String uporabniskoIme;
    private final String nastavljenoGeslo;

    public ZacetniAdmin(UporabnikRepozitorij uporabnikRepozitorij,
                        PasswordEncoder kodirnik,
                        @Value("${turnirko.admin.uporabnisko-ime:admin}") String uporabniskoIme,
                        @Value("${" + NASTAVITEV_GESLA + ":}") String nastavljenoGeslo) {
        this.uporabnikRepozitorij = uporabnikRepozitorij;
        this.kodirnik = kodirnik;
        this.uporabniskoIme = uporabniskoIme;
        this.nastavljenoGeslo = nastavljenoGeslo;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (uporabnikRepozitorij.count() > 0) {
            return; // vsaj en uporabnik ze obstaja - ne delamo nic
        }
        String geslo = preverjenoGeslo();
        uporabnikRepozitorij.save(new Uporabnik(
                uporabniskoIme, kodirnik.encode(geslo), Vloga.ADMIN));
        dnevnik.info("Ustvarjen zacetni administrator '{}'.", uporabniskoIme);
    }

    /* Vrne geslo, s katerim smemo ustvariti admina, ali pa ustavi zagon.
       Zavrnitev je namerno IllegalStateException iz ApplicationRunnerja:
       aplikacija se ugasne takoj po zagonu, admin z ugotovljivim geslom pa
       ne nastane nikoli. (Vrata so odprta le tistih nekaj trenutkov do
       izhoda, a v bazi takrat ni nobenega racuna.) */
    private String preverjenoGeslo() {
        String geslo = nastavljenoGeslo == null ? "" : nastavljenoGeslo.trim();

        if (geslo.isEmpty()) {
            throw new IllegalStateException(
                    "Baza je prazna, zacetnega administratorja pa ni mogoce ustvariti brez gesla. "
                    + "Nastavi " + NASTAVITEV_GESLA + " (npr. prek spremenljivke okolja "
                    + "TURNIRKO_ADMIN_PRIVZETO_GESLO). Uporabi dolgo nakljucno geslo - "
                    + "predlog: openssl rand -base64 24.");
        }
        if (geslo.length() < NAJMANJSA_DOLZINA) {
            throw new IllegalStateException(
                    "Geslo administratorja (" + NASTAVITEV_GESLA + ") je prekratko: "
                    + geslo.length() + " znakov, potrebnih je vsaj " + NAJMANJSA_DOLZINA + ". "
                    + "Ta racun sme vse, zato mu ugibanje ne sme priti blizu.");
        }
        return geslo;
    }
}
