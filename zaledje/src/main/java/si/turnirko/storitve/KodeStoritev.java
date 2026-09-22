/* Potrditvene kode: ustvarjanje in preverjanje.

   Koda ima sest stevk iz SecureRandom in je shranjena samo kot BCrypt
   zgostitev (isti kodirnik kot za gesla) - iz baze je ni mogoce prebrati.
   Sest stevk je milijon moznosti, kar samo po sebi ni nic; kodo varujeta
   veljavnost (NamenKode) in stevec poskusov: po NAJVEC_POSKUSOV napacnih
   vnosih koda propade. Nova koda za isti namen vedno odnese prejsnjo, zato
   je ziva najvec ena. */
package si.turnirko.storitve;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.modeli.NamenKode;
import si.turnirko.modeli.PotrditvenaKoda;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.repozitoriji.PotrditvenaKodaRepozitorij;

@Service
public class KodeStoritev {

    static final int NAJVEC_POSKUSOV = 5;
    private static final int DOLZINA = 6;
    private static final SecureRandom NAKLJUCJE = new SecureRandom();

    private final PotrditvenaKodaRepozitorij repozitorij;
    private final PasswordEncoder kodirnik;

    /* Zgostitev, proti kateri primerjamo vnos, kadar zive kode NI - da klic
       traja enako dolgo in iz casa odgovora ni mogoce sklepati, ali racun
       oz. koda obstaja. */
    private final String nadomestnaZgostitev;

    public KodeStoritev(PotrditvenaKodaRepozitorij repozitorij, PasswordEncoder kodirnik) {
        this.repozitorij = repozitorij;
        this.kodirnik = kodirnik;
        this.nadomestnaZgostitev = kodirnik.encode("000000");
    }

    /* Ustvari novo kodo za racun in namen (prejsnje za isti namen pobrise) in
       vrne cistopis - edino mesto, kjer koda obstaja necifrirana; klicatelj jo
       poslje po posti in pozabi. */
    @Transactional
    public String ustvari(Uporabnik racun, NamenKode namen) {
        repozitorij.deleteAll(repozitorij.findByRacunIdAndNamen(racun.getId(), namen));
        String koda = String.format("%0" + DOLZINA + "d", NAKLJUCJE.nextInt(1_000_000));
        LocalDateTime zdaj = LocalDateTime.now();
        repozitorij.save(new PotrditvenaKoda(
                racun, namen, kodirnik.encode(koda), zdaj, zdaj.plus(namen.veljavnost())));
        return koda;
    }

    /* Preveri vnos proti zivi kodi racuna. Pravilna koda se s tem porabi;
       napacna steje poskus in po zadnjem dovoljenem kodo pobrise. */
    @Transactional
    public boolean preveri(Uporabnik racun, NamenKode namen, String vnos) {
        LocalDateTime zdaj = LocalDateTime.now();
        Optional<PotrditvenaKoda> ziva = repozitorij.findByRacunIdAndNamen(racun.getId(), namen)
                .stream().filter(k -> k.jeZiva(zdaj)).findFirst();
        if (ziva.isEmpty()) {
            preveriNeznanega(vnos);
            return false;
        }
        PotrditvenaKoda koda = ziva.get();
        koda.setPoskusi(koda.getPoskusi() + 1);
        if (kodirnik.matches(ocisti(vnos), koda.getKodaHash())) {
            koda.setPorabljenOb(zdaj);
            repozitorij.save(koda);
            return true;
        }
        if (koda.getPoskusi() >= NAJVEC_POSKUSOV) {
            repozitorij.delete(koda);
        } else {
            repozitorij.save(koda);
        }
        return false;
    }

    /* Pobrise vse kode racuna - pred izbrisom racuna. Baza bi jih odnesla
       sama (ON DELETE CASCADE), a enota dela mora zanje vedeti, sicer ob
       izpisu naleti na kodo, ki kaze na izbrisan racun. */
    @Transactional
    public void pobrisiVse(Uporabnik racun) {
        repozitorij.deleteAll(repozitorij.findByRacunId(racun.getId()));
        repozitorij.flush();
    }

    /* Enak strosek kot prava primerjava, brez ucinka - za primere, ko racuna
       ni ali koda zanj ne pride v postev. */
    public void preveriNeznanega(String vnos) {
        kodirnik.matches(ocisti(vnos), nadomestnaZgostitev);
    }

    /* "123 456" -> "123456": ljudje kodo prepisejo s presledki. */
    static String ocisti(String vnos) {
        return vnos == null ? "" : vnos.replaceAll("\\s", "");
    }
}
