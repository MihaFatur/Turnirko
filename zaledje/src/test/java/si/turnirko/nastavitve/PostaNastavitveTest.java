/* Izvajalec posamicne poste (kode za prijavo in registracijo) ne sme zavreci
   sporocila, ko je vrsta polna (najdba E2). Prej je bila vrsta 200 mest brez
   pravila zavrnitve: 203. oddano sporocilo je vrglo izjemo in koda je izpadla. */
package si.turnirko.nastavitve;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class PostaNastavitveTest {

    @Test
    void polnaVrstaPosteNeZavrneSporocila() {
        ThreadPoolTaskExecutor izvajalec = (ThreadPoolTaskExecutor) new PostaNastavitve().postaIzvajalec();
        AtomicInteger opravljenih = new AtomicInteger();
        int oddanih = 500; // vec kot vrsta (200) in obe niti skupaj

        try {
            for (int i = 0; i < oddanih; i++) {
                izvajalec.execute(() -> {
                    try {
                        Thread.sleep(1); // pocasnejse od oddajanja, da se vrsta res napolni
                    } catch (InterruptedException prekinitev) {
                        Thread.currentThread().interrupt();
                    }
                    opravljenih.incrementAndGet();
                });
            }
        } finally {
            izvajalec.setWaitForTasksToCompleteOnShutdown(true);
            izvajalec.setAwaitTerminationSeconds(60);
            izvajalec.shutdown();
        }

        assertEquals(oddanih, opravljenih.get(), "vsako oddano sporocilo je bilo poslano");
    }
}
