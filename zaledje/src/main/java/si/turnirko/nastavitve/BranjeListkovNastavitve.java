/* Izvajalec branja listkov v ozadju.

   Branje enega listka (lastna mreza stevk) traja nekaj sto milisekund.
   Organizator med tem ze fotografira naslednjega, zato zahteva za nalaganje
   vrne takoj, branje pa tece na tem izvajalcu. Dve niti zadostujeta za vec
   turnirjev hkrati in omejita porabo procesorja ter pomnilnika na strezniku, ki
   ga deli z aplikacijo. Na istem izvajalcu tece tudi zbiranje stevk za ucenje
   (UcenjeIzListkov).

   Vrsta hrani samo naloge z id-jem branja (fotografija je v bazi), zato je
   lahko velika: 10.000 cakajocih branj je ~1,5 ure dela obeh niti, omejitev
   hitrosti (120 fotografij / 10 min na uporabnika) pa vrsto prej ustavi.
   Polna vrsta nalogo ZAVRNE (AbortPolicy), klicatelj zavrnitev zapise v
   dnevnik. CallerRunsPolicy bi nalogo izvedel na niti zahteve - ta jo odda iz
   afterCommit, kjer so viri potrjene transakcije se vezani in bi se zapisi
   branja tiho izgubili (Spring, TransactionSynchronization.afterCommit). */
package si.turnirko.nastavitve;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class BranjeListkovNastavitve {

    static final int NITI = 2;
    static final int VRSTA = 10_000;

    @Bean(name = "branjeListkovIzvajalec")
    Executor branjeListkovIzvajalec() {
        ThreadPoolTaskExecutor izvajalec = new ThreadPoolTaskExecutor();
        izvajalec.setCorePoolSize(NITI);
        izvajalec.setMaxPoolSize(NITI);
        izvajalec.setQueueCapacity(VRSTA);
        izvajalec.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        izvajalec.setThreadNamePrefix("branje-listkov-");
        izvajalec.initialize();
        return izvajalec;
    }
}
