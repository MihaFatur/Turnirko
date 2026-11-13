/* Pravila predpomnilnika javnih agregatov (PredpomnilnikBranja), brez baze.

   Predpomnilnik sme pohitriti branje, ne sme pa nikoli vrniti nicesar, cesar
   izracun v tistem trenutku ne bi vrnil. Vsak test drzi eno od mej, na katerih
   bi zastarel odgovor lahko usel: zapis med izracunom, transakcija, ki pise,
   razveljavljena transakcija, potek veljavnosti, druga razlicica. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class PredpomnilnikBranjaTest {

    private final PredpomnilnikBranja predpomnilnik = new PredpomnilnikBranja(Duration.ofMinutes(5));
    private final AtomicInteger izracunov = new AtomicInteger();

    @AfterEach
    void pocistiTransakcijo() {
        TransactionSynchronizationManager.clear();
        TransactionSynchronizationManager.setActualTransactionActive(false);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
    }

    private int vrni() {
        return predpomnilnik.vrni("kljuc", null, izracunov::incrementAndGet);
    }

    @Test
    void drugiKlicNeRacunaZnova() {
        assertEquals(1, vrni());
        assertEquals(1, vrni());
        assertEquals(1, izracunov.get());
    }

    @Test
    void razveljavitevSproziNovIzracun() {
        vrni();
        predpomnilnik.razveljavi();
        assertEquals(2, vrni());
        assertEquals(2, vrni());
    }

    @Test
    void kljuciSoLoceni() {
        assertEquals("a", predpomnilnik.vrni("prvi", null, () -> "a"));
        assertEquals("b", predpomnilnik.vrni("drugi", null, () -> "b"));
        assertEquals("a", predpomnilnik.vrni("prvi", null, () -> "c"));
    }

    /* Vrednost, odvisna od dneva: ob novem dnevu se izracuna znova in stara
       ne ostane (vnos se zamenja). */
    @Test
    void drugaRazlicicaSproziNovIzracun() {
        assertEquals(1, (int) predpomnilnik.vrni("kljuc", "torek", izracunov::incrementAndGet));
        assertEquals(1, (int) predpomnilnik.vrni("kljuc", "torek", izracunov::incrementAndGet));
        assertEquals(2, (int) predpomnilnik.vrni("kljuc", "sreda", izracunov::incrementAndGet));
        assertEquals(3, (int) predpomnilnik.vrni("kljuc", "torek", izracunov::incrementAndGet));
    }

    @Test
    void poIztekuVeljavnostiRacunaZnova() {
        AtomicLong ura = new AtomicLong();
        predpomnilnik.nastaviUro(ura::get);

        assertEquals(1, vrni());
        ura.set(Duration.ofMinutes(5).toNanos() - 1);
        assertEquals(1, vrni());
        ura.set(Duration.ofMinutes(5).toNanos());
        assertEquals(2, vrni());
    }

    @Test
    void veljavnostNicGaIzklopi() {
        PredpomnilnikBranja izklopljen = new PredpomnilnikBranja(Duration.ZERO);
        assertEquals(1, (int) izklopljen.vrni("kljuc", null, izracunov::incrementAndGet));
        assertEquals(2, (int) izklopljen.vrni("kljuc", null, izracunov::incrementAndGet));
    }

    /* Izracun, ki ga je zapis prehitel, je morda bral staro stanje: klicatelj
       ga dobi (tako je bilo tudi brez predpomnilnika), shrani pa se ne. */
    @Test
    void izracunMedKaterimJeBilZapisSeNeShrani() {
        int prvi = predpomnilnik.vrni("kljuc", null, () -> {
            predpomnilnik.razveljavi();
            return izracunov.incrementAndGet();
        });
        assertEquals(1, prvi);
        assertEquals(2, vrni());
        assertEquals(2, vrni());
    }

    /* Transakcija, ki sme pisati, vidi svoje nepotrjene zapise: predpomnilnika
       ne bere (shranjena vrednost njenih zapisov nima) in ne polni (po
       razveljavitvi bi v njem ostalo, cesar v bazi ni). */
    @Test
    void vTransakcijiKiPiseSeNeBereInNePolni() {
        vrni();

        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
        assertEquals(2, vrni());
        assertEquals(3, vrni());

        TransactionSynchronizationManager.setActualTransactionActive(false);
        assertEquals(1, vrni(), "vrednost izpred transakcije je ostala nedotaknjena");
    }

    @Test
    void vBralniTransakcijiSeUporablja() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        assertEquals(1, vrni());
        assertEquals(1, vrni());
    }

    /* Zapis razveljavi sele ob potrditvi transakcije - prej ga drugi ne vidijo
       - in enkrat, ne glede na stevilo stavkov. */
    @Test
    void zapisRazveljaviObPotrditviTransakcije() {
        vrni();
        TransactionSynchronizationManager.initSynchronization();

        predpomnilnik.obZapisu();
        predpomnilnik.obZapisu();
        List<TransactionSynchronization> prijavljene = TransactionSynchronizationManager.getSynchronizations();
        assertEquals(1, prijavljene.size());
        assertEquals(1, vrni(), "pred potrditvijo se velja");

        prijavljene.get(0).afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        assertEquals(2, vrni());
    }

    @Test
    void razveljavljenaTransakcijaPredpomnilnikaNeRazveljavi() {
        vrni();
        TransactionSynchronizationManager.initSynchronization();
        predpomnilnik.obZapisu();

        TransactionSynchronizationManager.getSynchronizations().get(0)
                .afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertEquals(1, vrni());
    }

    /* Izid potrditve ni znan (prekinjena povezava): varneje je racunati znova. */
    @Test
    void neznanIzidPotrditveRazveljavi() {
        vrni();
        TransactionSynchronizationManager.initSynchronization();
        predpomnilnik.obZapisu();

        TransactionSynchronizationManager.getSynchronizations().get(0)
                .afterCompletion(TransactionSynchronization.STATUS_UNKNOWN);
        assertEquals(2, vrni());
    }

    @Test
    void zapisBrezTransakcijeRazveljaviTakoj() {
        vrni();
        predpomnilnik.obZapisu();
        assertEquals(2, vrni());
    }

    /* Po vnosu izida vsi gledalci hkrati zahtevajo isto lestvico: racuna en
       zahtevek, ostali pocakajo nanj. */
    @Test
    void hkratneZahteveRacunajoEnkrat() throws Exception {
        int niti = 8;
        CountDownLatch zacetek = new CountDownLatch(1);
        ExecutorService izvajalec = Executors.newFixedThreadPool(niti);
        try {
            List<Future<Integer>> odgovori = new java.util.ArrayList<>();
            for (int i = 0; i < niti; i++) {
                odgovori.add(izvajalec.submit(() -> {
                    zacetek.await();
                    return predpomnilnik.vrni("kljuc", null, () -> {
                        try {
                            Thread.sleep(150);
                        } catch (InterruptedException prekinitev) {
                            Thread.currentThread().interrupt();
                        }
                        return izracunov.incrementAndGet();
                    });
                }));
            }
            zacetek.countDown();
            for (Future<Integer> odgovor : odgovori) {
                assertEquals(1, odgovor.get(5, TimeUnit.SECONDS));
            }
            assertEquals(1, izracunov.get());
        } finally {
            izvajalec.shutdownNow();
        }
    }

    @Test
    void napakaVIzracunuSeNeShrani() {
        try {
            predpomnilnik.vrni("kljuc", null, () -> {
                throw new IllegalStateException("baza ni dosegljiva");
            });
        } catch (IllegalStateException pricakovana) {
            assertTrue(pricakovana.getMessage().contains("baza"));
        }
        assertEquals(1, vrni(), "naslednji klic racuna znova in uspe");
    }
}
