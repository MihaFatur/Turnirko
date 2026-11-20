/* Izvajalec branja listkov v ozadju.

   Branje enega listka traja nekaj sekund (klic modela). Organizator med tem ze
   fotografira naslednjega, zato zahteva za nalaganje vrne takoj, branje pa tece
   na tem izvajalcu. Dve niti zadostujeta za enega organizatorja, ki fotografira
   en listek na nekaj sekund, in za dva turnirja hkrati; vec bi samo povecalo
   hkratne klice in s tem moznost omejitve hitrosti pri ponudniku. Ko je vrsta
   polna, prebere nit, ki je fotografijo oddala (CallerRunsPolicy): zahteva
   tedaj pocaka, fotografija pa se ne izgubi. */
package si.turnirko.nastavitve;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class BranjeListkovNastavitve {

    @Bean(name = "branjeListkovIzvajalec")
    Executor branjeListkovIzvajalec() {
        ThreadPoolTaskExecutor izvajalec = new ThreadPoolTaskExecutor();
        izvajalec.setCorePoolSize(2);
        izvajalec.setMaxPoolSize(2);
        izvajalec.setQueueCapacity(200);
        izvajalec.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        izvajalec.setThreadNamePrefix("branje-listkov-");
        izvajalec.initialize();
        return izvajalec;
    }
}
