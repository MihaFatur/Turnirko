/* Razvojni nacin brez SMTP (turnirko.posta.nacin=dnevnik, privzeto): celotno
   sporocilo - tudi koda - gre v dnevnik aplikacije. Na strezniku je to
   uporabno samo zacasno (dokler poste se ni), zato ob zagonu glasno opozori:
   registracija brez prave poste za nikogar zunaj dnevnika ne deluje. */
package si.turnirko.posta;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "turnirko.posta.nacin", havingValue = "dnevnik", matchIfMissing = true)
public class DnevniskiPosiljatelj implements PosiljateljPoste {

    private static final Logger dnevnik = LoggerFactory.getLogger(DnevniskiPosiljatelj.class);

    public DnevniskiPosiljatelj() {
        dnevnik.warn("Posta ni nastavljena (turnirko.posta.nacin=dnevnik): potrditvene kode "
                + "se izpisujejo v dnevnik in ne posiljajo. Za splet nastavi nacin smtp.");
    }

    @Override
    public void poslji(String prejemnik, String zadeva, String besedilo) {
        dnevnik.info("E-posta (ni poslana) za {} / Zadeva: {}\n{}", prejemnik, zadeva, besedilo);
    }
}
