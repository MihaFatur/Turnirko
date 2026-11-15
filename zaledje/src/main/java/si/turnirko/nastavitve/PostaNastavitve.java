/* Asinhrono posiljanje e-poste na dveh locenih izvajalcih.

   postaIzvajalec: posamicna sporocila, ki jih sprozi zahteva (potrditvena
   koda, pozabljeno geslo, obvestilo adminu). Zahteva se konca takoj, SMTP tece
   v ozadju. Vrsta je omejena, da tisoc sprozenih posiljanj ne poje pomnilnika;
   ko je polna, sporocilo poslje nit, ki ga je oddala (CallerRunsPolicy) -
   zahteva takrat pocaka na streznik poste, sporocilo pa se NE izgubi. Prej je
   polna vrsta posiljanje zavrnila z izjemo in koda za prijavo je izpadla.

   obvestilaIzvajalec: mnozicna obvestila po kolu in po turnirju
   (DostavaObvestilStoritev). Ena nit, ker gre ena dostava (kolo, turnir) za
   drugo; v vrsti so cele dostave, ne posamezna sporocila, zato jih je malo.
   Locen od postaIzvajalec, da nekaj sto obvestil ne stoji pred kodo za
   prijavo. */
package si.turnirko.nastavitve;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

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
        izvajalec.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        izvajalec.setThreadNamePrefix("posta-");
        izvajalec.initialize();
        return izvajalec;
    }

    @Bean(name = "obvestilaIzvajalec")
    Executor obvestilaIzvajalec() {
        ThreadPoolTaskExecutor izvajalec = new ThreadPoolTaskExecutor();
        izvajalec.setCorePoolSize(1);
        izvajalec.setMaxPoolSize(1);
        izvajalec.setThreadNamePrefix("obvestila-");
        izvajalec.initialize();
        return izvajalec;
    }
}
