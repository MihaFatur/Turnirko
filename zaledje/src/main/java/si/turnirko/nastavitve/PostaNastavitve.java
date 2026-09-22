/* Asinhrono posiljanje e-poste: zahteva, ki jo sprozi (registracija,
   pozabljeno geslo), se konca takoj, SMTP pa tece v ozadju na majhnem
   lastnem izvajalcu. Vrsta je omejena - ce bi kdo sprozil tisoce posiljanj,
   zahteve odpadejo, namesto da bi pojedle pomnilnik. */
package si.turnirko.nastavitve;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class PostaNastavitve {

    @Bean(name = "postaIzvajalec")
    Executor postaIzvajalec() {
        ThreadPoolTaskExecutor izvajalec = new ThreadPoolTaskExecutor();
        izvajalec.setCorePoolSize(1);
        izvajalec.setMaxPoolSize(2);
        izvajalec.setQueueCapacity(200);
        izvajalec.setThreadNamePrefix("posta-");
        izvajalec.initialize();
        return izvajalec;
    }
}
