/* Ob prvem zagonu ustvari zacetnega administratorja, ce v bazi se ni
   nobenega uporabnika. Uporabnisko ime in geslo se bereta iz nastavitev
   (privzeto admin/admin) - v komentar dnevnika zapisemo opozorilo, naj se
   privzeto geslo cim prej zamenja. Tako je aplikacija takoj uporabna,
   gesla pa nikoli ne vpisujemo v migracijo (bilo bi enako pri vseh). */
package si.turnirko.nastavitve;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

@Component
public class ZacetniAdmin implements ApplicationRunner {

    private static final Logger dnevnik = LoggerFactory.getLogger(ZacetniAdmin.class);

    private final UporabnikRepozitorij uporabnikRepozitorij;
    private final PasswordEncoder kodirnik;
    private final String uporabniskoIme;
    private final String privzetoGeslo;

    public ZacetniAdmin(UporabnikRepozitorij uporabnikRepozitorij,
                        PasswordEncoder kodirnik,
                        @Value("${turnirko.admin.uporabnisko-ime:admin}") String uporabniskoIme,
                        @Value("${turnirko.admin.privzeto-geslo:admin}") String privzetoGeslo) {
        this.uporabnikRepozitorij = uporabnikRepozitorij;
        this.kodirnik = kodirnik;
        this.uporabniskoIme = uporabniskoIme;
        this.privzetoGeslo = privzetoGeslo;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (uporabnikRepozitorij.count() > 0) {
            return; // vsaj en uporabnik ze obstaja - ne delamo nic
        }
        uporabnikRepozitorij.save(new Uporabnik(
                uporabniskoIme, kodirnik.encode(privzetoGeslo), Vloga.ADMIN));
        dnevnik.warn("Ustvarjen zacetni administrator '{}' s privzetim geslom - "
                + "cim prej ga zamenjaj (nastavitev turnirko.admin.privzeto-geslo).", uporabniskoIme);
    }
}
