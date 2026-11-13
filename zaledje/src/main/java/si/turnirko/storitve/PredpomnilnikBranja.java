/* Predpomnilnik javnih agregatov (nov. 2026, najdba C1 pregleda kakovosti).

   Lestvica, seznam turnirjev, uvrstitev na profilu in zreb nakljucnega para so
   izpeljanke cez VSO zgodovino (~94 tisoc tekem), odpira pa jih gledalec brez
   prijave na telefonu - ob vsakem obisku znova. Podatki pod njimi se
   spremenijo redko (vnos izida, preracun, nov igralec), zato se rezultat hrani
   v pomnilniku, dokler ga zapis v bazo ne razveljavi.

   Pravila, ki jih ne razbij:

   - RAZVELJAVI GA VSAK ZAPIS V BAZO, ne seznam "pomembnih" mest. Vsak stavek,
     ki ga Hibernate poslje bazi in ni branje (entiteta, JPQL, izvorni SQL),
     ob potrditvi svoje transakcije poveca ROD (PredpomnilnikNastavitve);
     vnos velja samo v rodu, v katerem je nastal. Seznam mest, ki agregate
     spremenijo (obracun, preracun, popravek izida, uvoz, zdruzitev igralcev,
     urejanje turnirja ...), bi bil dolg in bi ga naslednja sprememba kode
     tiho pustila nepopolnega. Cena je, da tudi nepomemben zapis (koda za
     potrditev naslova) sprozi nov izracun - zapisov je malo, branj veliko.

   - ROD SE POVECA PO POTRDITVI, vnos pa se shrani samo, ce se rod med
     izracunom ni spremenil. Izracun, ki ga je zapis prehitel, zato nikoli ne
     obvelja kot svez; kdor je izid vpisal, ga v odgovoru na naslednjo zahtevo
     ze vidi.

   - V TRANSAKCIJI, KI SME PISATI, SE NE UPORABLJA (ne bere in ne polni):
     taka transakcija vidi svoje se nepotrjene zapise, ki bi po razveljavitvi
     ostali v predpomnilniku, shranjena vrednost pa njenih zapisov ne bi
     vsebovala. Tam se izracuna neposredno, kot pred uvedbo. Zato so testi z
     @Transactional (transakcija testa pise in se razveljavi) nedotaknjeni.

   - VELJAVNOST (turnirko.predpomnilnik.veljavnost, privzeto 5 minut) je
     varovalka in ne mehanizem: pokrije zapis mimo Hibernata (popravek v bazi
     na roko, uvoz v drugem procesu) in to, da del agregatov bere uro
     (18 mesecev do skritja z lestvice, premik mesta v 30 dneh). PT0S ga
     izklopi brez spremembe kode.

   - VREDNOST JE SKUPNA VSEM ZAHTEVKOM: klicatelj je ne spreminja. Kar je
     odvisno od racuna, sem ne sodi.

   - RAZLICICA loci vrednosti, ki so odvisne se od cesa drugega kot od baze
     (dan: status "V pripravi" do datuma turnirja, starostni pas po sezoni).
     Ob drugi razlicici se vnos zamenja, ne kopici.

   Ob zgresku racuna en sam zahtevek, ostali istega kljuca ga pocakajo
   (najvec NAJDLJE_CAKA, nato racunajo sami): po vnosu izida na turnirju bi
   sicer vsi gledalci hkrati sprozili isti izracun in izpraznili bazen
   povezav. */
package si.turnirko.storitve;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class PredpomnilnikBranja {

    /* Koliko najdlje zahtevek caka na izracun drugega, preden racuna sam. Meja
       je zato, da cakanje nikoli ne postane zastoj: cakajoci lahko drzi
       povezavo z bazo, ki jo racunajoci se potrebuje. */
    private static final long NAJDLJE_CAKA_MS = 2_000;

    private record Vnos(Object vrednost, Object razlicica, long rod, long nastalOb) {}

    private static final class Reza {
        final ReentrantLock zaklep = new ReentrantLock();
        volatile Vnos vnos;
    }

    private final ConcurrentHashMap<String, Reza> reze = new ConcurrentHashMap<>();
    private final AtomicLong rod = new AtomicLong();
    private final long veljavnost;
    private LongSupplier ura = System::nanoTime;

    /* Ista sinhronizacija za vse transakcije: Spring jih hrani v mnozici, zato
       jo vec zapisov iste transakcije prijavi samo enkrat, ugnezdena
       transakcija (REQUIRES_NEW) pa ima svojo mnozico in razveljavi ob svoji
       potrditvi. Razveljavljena transakcija ni spremenila nicesar. */
    private final TransactionSynchronization poPotrditvi = new TransactionSynchronization() {
        @Override
        public void afterCompletion(int status) {
            if (status != STATUS_ROLLED_BACK) {
                razveljavi();
            }
        }
    };

    public PredpomnilnikBranja(
            @Value("${turnirko.predpomnilnik.veljavnost:PT5M}") Duration veljavnost) {
        this.veljavnost = veljavnost.toNanos();
    }

    /* Vrednost kljuca: shranjena, ce se velja, sicer izracunana (in shranjena,
       kadar se sme). Razlicica je lahko null. */
    @SuppressWarnings("unchecked")
    public <T> T vrni(String kljuc, Object razlicica, Supplier<T> izracun) {
        if (veljavnost <= 0 || vPisociTransakciji()) {
            return izracun.get();
        }
        Reza reza = reze.computeIfAbsent(kljuc, k -> new Reza());
        Vnos vnos = reza.vnos;
        if (velja(vnos, razlicica)) {
            return (T) vnos.vrednost();
        }
        boolean zaklenjeno = false;
        try {
            zaklenjeno = reza.zaklep.tryLock(NAJDLJE_CAKA_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException prekinitev) {
            Thread.currentThread().interrupt();
        }
        try {
            // medtem ga je morda izracunal tisti, na katerega smo cakali
            vnos = reza.vnos;
            if (velja(vnos, razlicica)) {
                return (T) vnos.vrednost();
            }
            long rodPrej = rod.get();
            T vrednost = izracun.get();
            if (rod.get() == rodPrej) {
                reza.vnos = new Vnos(vrednost, razlicica, rodPrej, ura.getAsLong());
            }
            return vrednost;
        } finally {
            if (zaklenjeno) {
                reza.zaklep.unlock();
            }
        }
    }

    private boolean velja(Vnos vnos, Object razlicica) {
        return vnos != null
                && vnos.rod() == rod.get()
                && Objects.equals(vnos.razlicica(), razlicica)
                && ura.getAsLong() - vnos.nastalOb() < veljavnost;
    }

    /* Transakcija, ki sme pisati, vidi svoje nepotrjene zapise (glej glavo). */
    private static boolean vPisociTransakciji() {
        return TransactionSynchronizationManager.isActualTransactionActive()
                && !TransactionSynchronizationManager.isCurrentTransactionReadOnly();
    }

    /* Vse shranjeno od tega trenutka ne velja vec. */
    public void razveljavi() {
        rod.incrementAndGet();
    }

    /* Hibernate bo bazi poslal stavek, ki pise: razveljavi ob potrditvi
       transakcije (prej drugi zapisa se ne vidijo). Zapis brez Springove
       transakcije se potrdi sam, zato razveljavi takoj. */
    public void obZapisu() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(poPotrditvi);
        } else {
            razveljavi();
        }
    }

    /* Ura je zamenljiva, da test veljavnosti ne caka pet minut. */
    void nastaviUro(LongSupplier ura) {
        this.ura = ura;
    }
}
