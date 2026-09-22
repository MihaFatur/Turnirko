/* Pravo posiljanje prek SMTP (turnirko.posta.nacin=smtp).

   Tece asinhrono: zahteva, ki je sprozila posiljanje (registracija), ne sme
   cakati na tuj streznik - v dvorani je povezava pocasna, streznik poste pa
   vcasih odgovori sele po sekundah. Neuspeh se poskusi se dvakrat z odmikom,
   nato se zapise v dnevnik; naslov prejemnika je v dnevniku zakrit, ker je
   osebni podatek. */
package si.turnirko.posta;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "turnirko.posta.nacin", havingValue = "smtp")
public class SmtpPosiljatelj implements PosiljateljPoste {

    private static final Logger dnevnik = LoggerFactory.getLogger(SmtpPosiljatelj.class);

    /* Odmiki med poskusi (ms): kratek za prehodno napako, daljsi za zasedenost. */
    private static final long[] ODMIKI = {2_000, 10_000};

    private final JavaMailSender posiljatelj;
    private final String od;
    private final String odgovor;

    /* JavaMailSender pride prek ObjectProvider: Spring Boot ga ustvari sele, ko
       je spring.mail.host nastavljen, in brez tega bi zagon padel z nejasno
       napako o manjkajocem beanu namesto s spodnjim navodilom. */
    public SmtpPosiljatelj(ObjectProvider<JavaMailSender> posiljatelj,
                           @Value("${turnirko.posta.od}") String od,
                           @Value("${turnirko.posta.odgovor:}") String odgovor,
                           @Value("${spring.mail.host:}") String streznik) {
        JavaMailSender izvedba = posiljatelj.getIfAvailable();
        if (streznik == null || streznik.isBlank() || izvedba == null) {
            throw new IllegalStateException(
                    "turnirko.posta.nacin=smtp, a spring.mail.host ni nastavljen. Nastavi "
                    + "TURNIRKO_SMTP_STREZNIK (in uporabnika ter geslo) ali preklopi na nacin dnevnik.");
        }
        this.posiljatelj = izvedba;
        this.od = od;
        this.odgovor = odgovor == null ? "" : odgovor.trim();
    }

    @Async("postaIzvajalec")
    @Override
    public void poslji(String prejemnik, String zadeva, String besedilo) {
        SimpleMailMessage sporocilo = new SimpleMailMessage();
        sporocilo.setFrom(od);
        /* Posiljatelj je nabiralnik, ki ga nihce ne bere (ne-odgovarjaj@), ker
           pot avtomatske poste ni ista kot pot cloveske. Odgovor na kodo pa je
           ravno takrat, ko uporabnik potrebuje pomoc - zato ga preusmerimo na
           pravi predal, namesto da bi padel v prazno. */
        if (!odgovor.isBlank()) {
            sporocilo.setReplyTo(odgovor);
        }
        sporocilo.setTo(prejemnik);
        sporocilo.setSubject(zadeva);
        sporocilo.setText(besedilo);

        for (int poskus = 0; ; poskus++) {
            try {
                posiljatelj.send(sporocilo);
                return;
            } catch (MailException napaka) {
                if (poskus >= ODMIKI.length) {
                    dnevnik.warn("E-poste \"{}\" za {} ni bilo mogoce poslati: {}",
                            zadeva, zakrij(prejemnik), napaka.getMessage());
                    return;
                }
                try {
                    Thread.sleep(ODMIKI[poskus]);
                } catch (InterruptedException prekinitev) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    /* "ana.novak@primer.si" -> "an***@primer.si": dovolj, da admin ve, komu
       posta ni sla, premalo, da bi dnevnik postal seznam naslovov. */
    static String zakrij(String naslov) {
        int afna = naslov.indexOf('@');
        if (afna <= 0) {
            return "***";
        }
        String pred = naslov.substring(0, Math.min(2, afna));
        return pred + "***" + naslov.substring(afna);
    }
}
