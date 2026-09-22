/* Omejevalnik poskusov: koliko dogodkov z istim kljucem (naslov e-poste,
   prijavno ime, naslov IP) je dovoljenih v casovnem oknu.

   To je edina resnicna obramba sestmestne kode in gesla pred ugibanjem -
   BCrypt ugibanje le upocasni, meja ga ustavi. Stanje je v pomnilniku, ker
   tece en sam streznik: ob ponovnem zagonu se stevci ponastavijo, kar je
   sprejemljivo (napadalec ne more sproziti ponovnega zagona). Vsak kljuc
   hrani case zadnjih dogodkov, zato lahko isti kljuc preverimo proti vec
   oknom hkrati (npr. 1 na minuto IN 5 na uro). */
package si.turnirko.storitve;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OmejevalnikPoskusov {

    /* Dlje od tega noben dogodek ne steje; starejsi se pobrisejo. */
    private static final Duration NAJDALJSE_OKNO = Duration.ofHours(24);

    /* Zgornja meja zapisov na kljuc - vec kot toliko poskusov v enem dnevu
       je vsekakor cez vsako mejo, hraniti jih ni treba. */
    private static final int NAJVEC_ZAPISOV = 500;

    private final Map<String, Deque<Instant>> dogodki = new ConcurrentHashMap<>();

    /* Kljuci za neuspele prijave - sestavlja jih ta razred, da filter prijave,
       poslusalec dogodkov in ponastavitev gesla govorijo o istem stevcu. */
    public static String kljucPrijave(String prijavnoIme) {
        return "prijava:" + prijavnoIme.trim().toLowerCase(java.util.Locale.ROOT);
    }

    /* Racun z enega naslova IP - nizka meja, ki ne zaklene racuna drugim. */
    public static String kljucPrijave(String prijavnoIme, String naslovIp) {
        return kljucPrijave(prijavnoIme) + "|" + (naslovIp == null ? "?" : naslovIp);
    }

    public static String kljucNaslovaPrijave(String naslovIp) {
        return "prijava-naslov:" + (naslovIp == null ? "?" : naslovIp);
    }

    /* Ali je kljuc v danem oknu ze dosegel mejo (brez belezenja). */
    public boolean jeCezMejo(String kljuc, int najvec, Duration okno) {
        Deque<Instant> vrsta = dogodki.get(kljuc);
        if (vrsta == null) {
            return false;
        }
        Instant meja = Instant.now().minus(okno);
        synchronized (vrsta) {
            int stevilo = 0;
            for (Instant t : vrsta) {
                if (!t.isBefore(meja)) {
                    stevilo++;
                }
            }
            return stevilo >= najvec;
        }
    }

    /* Zabelezi dogodek za kljuc. */
    public void zabelezi(String kljuc) {
        Deque<Instant> vrsta = dogodki.computeIfAbsent(kljuc, k -> new ArrayDeque<>());
        Instant zdaj = Instant.now();
        synchronized (vrsta) {
            odstraniStare(vrsta, zdaj);
            vrsta.addLast(zdaj);
            while (vrsta.size() > NAJVEC_ZAPISOV) {
                vrsta.pollFirst();
            }
        }
    }

    /* Ce je kljuc pod mejo, dogodek zabelezi in vrne true; sicer false in
       nicesar ne zabelezi (zavrnjena zahteva ne sme podaljsevati blokade). */
    public boolean dovoliInZabelezi(String kljuc, int najvec, Duration okno) {
        if (jeCezMejo(kljuc, najvec, okno)) {
            return false;
        }
        zabelezi(kljuc);
        return true;
    }

    /* Pozabi vse dogodke kljuca (npr. po uspesni prijavi). */
    public void pocisti(String kljuc) {
        dogodki.remove(kljuc);
    }

    /* Pozabi vse stevce prijave racuna - skupnega in z vseh naslovov IP.
       Klice se, ko je lastnik z e-posto dokazal, da je on (novo geslo). */
    public void pocistiPrijaveRacuna(String prijavnoIme) {
        String kljuc = kljucPrijave(prijavnoIme);
        dogodki.keySet().removeIf(k -> k.equals(kljuc) || k.startsWith(kljuc + "|"));
    }

    /* Pozabi vse - za teste, ki isti naslov registrirajo veckrat zapored. */
    public void pocistiVse() {
        dogodki.clear();
    }

    private static void odstraniStare(Deque<Instant> vrsta, Instant zdaj) {
        Instant meja = zdaj.minus(NAJDALJSE_OKNO);
        while (!vrsta.isEmpty() && vrsta.peekFirst().isBefore(meja)) {
            vrsta.pollFirst();
        }
    }

    /* Vsako uro odstrani kljuce brez svezih dogodkov, da zemljevid ne raste
       z vsakim naslovom, ki je kdaj poslal eno zahtevo. */
    @Scheduled(fixedDelay = 3_600_000)
    public void pospravi() {
        Instant zdaj = Instant.now();
        dogodki.entrySet().removeIf(vnos -> {
            Deque<Instant> vrsta = vnos.getValue();
            synchronized (vrsta) {
                odstraniStare(vrsta, zdaj);
                return vrsta.isEmpty();
            }
        });
    }
}
