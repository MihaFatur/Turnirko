/* Belezi izid prijav za omejevalnik: neuspela prijava steje proti racunu z
   naslova, proti racunu skupaj in proti naslovu IP; uspesna stevca racuna
   pobrise. Spring Security objavi dogodek ob vsaki Basic prijavi (veriga je
   brez stanja, prijava je vsaka zahteva), zato so stevci vedno sveza slika.
   Neobstojece ime se skriva za BadCredentials - napadalec iz odgovora ne
   izve, ali racun obstaja. Uspesna prijava se belezi tudi v dnevnik prijav
   racuna (RacunDnevnikStoritev), a na koncu in brez posledic za zahtevo. */
package si.turnirko.nastavitve;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

import si.turnirko.storitve.OmejevalnikPoskusov;
import si.turnirko.storitve.RacunDnevnikStoritev;

@Component
public class PrijavaDogodki {

    private final OmejevalnikPoskusov omejevalnik;
    private final RacunDnevnikStoritev racunDnevnik;

    public PrijavaDogodki(OmejevalnikPoskusov omejevalnik, RacunDnevnikStoritev racunDnevnik) {
        this.omejevalnik = omejevalnik;
        this.racunDnevnik = racunDnevnik;
    }

    @EventListener
    public void neuspelaPrijava(AuthenticationFailureBadCredentialsEvent dogodek) {
        Authentication prijava = dogodek.getAuthentication();
        String naslov = naslov(prijava);
        if (prijava.getName() != null) {
            omejevalnik.zabelezi(OmejevalnikPoskusov.kljucPrijave(prijava.getName()));
            omejevalnik.zabelezi(OmejevalnikPoskusov.kljucPrijave(prijava.getName(), naslov));
        }
        omejevalnik.zabelezi(OmejevalnikPoskusov.kljucNaslovaPrijave(naslov));
    }

    @EventListener
    public void uspesnaPrijava(AuthenticationSuccessEvent dogodek) {
        Authentication prijava = dogodek.getAuthentication();
        if (prijava.getName() != null) {
            omejevalnik.pocisti(OmejevalnikPoskusov.kljucPrijave(prijava.getName()));
            omejevalnik.pocisti(OmejevalnikPoskusov.kljucPrijave(prijava.getName(), naslov(prijava)));
            // stran Dostopi: zadnja prijava in prijave v 30 dneh (glej RacunDnevnikStoritev)
            racunDnevnik.zabeleziPrijavo(prijava.getName());
        }
    }

    private static String naslov(Authentication prijava) {
        return prijava.getDetails() instanceof WebAuthenticationDetails podrobnosti
                ? podrobnosti.getRemoteAddress()
                : null;
    }
}
