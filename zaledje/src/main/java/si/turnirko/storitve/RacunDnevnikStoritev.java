/* Dnevnik dogodkov in prijave racunov za administratorjevo stran Dostopi.

   Dnevnik (racun_dogodek): besedilo se zapise ob dogodku (registracija,
   potrditev naslova, povezava, potrditev, placilo, geslo, izklop/vklop) in se
   nikoli ne racuna nazaj - ime igralca ali paket se pozneje spremenita, zapis
   o preteklosti ne. Casi so po slovenskem casu, kot ostali zapisi, ki jih
   bere clovek (srecanja, naročnine).

   Prijava (racun_prijava): veriga je brez stanja (HTTP Basic, vsaka zahteva
   je overjena), zato »prijave« ni mogoce prebrati iz seje. Prijava je PRVA
   overjena zahteva racuna po vsaj 30 minutah mirovanja: to je enako, kot bi
   clovek rekel »danes sem se vpisal«, in se ne razlikuje med osvezitvijo
   strani in novo sejo. Zahteve se sproti belezijo samo v pomnilniku
   (zadnji cas po racunu), v bazo gre ena vrstica na prijavo - SQLite prenese
   enega pisca in zapis ob vsaki zahtevi bi bil breme brez koristi.
   Po ponovnem zagonu strezniku je prva zahteva vsakega racuna nova prijava;
   odstopanje za eno vrstico je cena, da stanja ni treba hraniti. */
package si.turnirko.storitve;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.RacunIgralcaDto.Dogodek;
import si.turnirko.dto.RacunIgralcaDto.Prijave;
import si.turnirko.modeli.CasKotBesedilo;
import si.turnirko.modeli.CiklusPlacila;
import si.turnirko.modeli.Paket;
import si.turnirko.modeli.RacunDogodek;
import si.turnirko.modeli.RacunPrijava;
import si.turnirko.repozitoriji.RacunDogodekRepozitorij;
import si.turnirko.repozitoriji.RacunPrijavaRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

@Service
public class RacunDnevnikStoritev {

    private static final ZoneId SLOVENIJA = ZoneId.of("Europe/Ljubljana");

    /* Koliko dogodkov nosi karton racuna (najnovejsi prvi). */
    public static final int NAJVEC_DOGODKOV = 8;

    /* Mirovanje, po katerem je naslednja zahteva nova prijava. */
    static final Duration MIROVANJE = Duration.ofMinutes(30);

    /* Okno »prijave v 30 dneh«. */
    public static final int OKNO_DNI = 30;

    private static final CasKotBesedilo BESEDILO_V_CAS = new CasKotBesedilo();

    private final RacunDogodekRepozitorij dogodki;
    private final RacunPrijavaRepozitorij prijave;
    private final UporabnikRepozitorij uporabniki;

    /* Zadnja overjena zahteva po racunu (ime v malih crkah). */
    private final Map<String, LocalDateTime> zadnjaZahteva = new ConcurrentHashMap<>();

    public RacunDnevnikStoritev(RacunDogodekRepozitorij dogodki,
                                RacunPrijavaRepozitorij prijave,
                                UporabnikRepozitorij uporabniki) {
        this.dogodki = dogodki;
        this.prijave = prijave;
        this.uporabniki = uporabniki;
    }

    static LocalDateTime zdaj() {
        return LocalDateTime.now(SLOVENIJA).truncatedTo(ChronoUnit.SECONDS);
    }

    // ---------- Pisanje ----------

    /* Zapise dogodek v isti transakciji kot sprememba, ki ga povzroci: ce se
       sprememba razveljavi, ne ostane dnevnik o dogodku, ki se ni zgodil. */
    @Transactional
    public void zabelezi(Long idRacun, String tekst) {
        dogodki.save(new RacunDogodek(idRacun, zdaj(), tekst));
    }

    /* Klic ob vsaki overjeni zahtevi (PrijavaDogodki). Napaka pri zapisu ne
       sme nikoli ustaviti zahteve, zato je tu ne propagiramo. */
    public void zabeleziPrijavo(String uporabniskoIme) {
        zabeleziPrijavo(uporabniskoIme, zdaj());
    }

    void zabeleziPrijavo(String uporabniskoIme, LocalDateTime cas) {
        if (uporabniskoIme == null) {
            return;
        }
        LocalDateTime prejsnja = zadnjaZahteva.put(uporabniskoIme.toLowerCase(), cas);
        boolean nova = prejsnja == null || Duration.between(prejsnja, cas).compareTo(MIROVANJE) >= 0;
        if (!nova) {
            return;
        }
        try {
            vpisiPrijavo(uporabniskoIme, cas);
        } catch (RuntimeException e) {
            // belezenje je dodatek: prijava je veljavna tudi, ce vrstice ni mogoce zapisati
        }
    }

    void vpisiPrijavo(String uporabniskoIme, LocalDateTime cas) {
        uporabniki.findByUporabniskoImeIgnoreCase(uporabniskoIme)
                .ifPresent(u -> prijave.save(new RacunPrijava(u.getId(), cas)));
    }

    /* »Placilo: Plus, letno« - ime paketa brez oznake vloge, kot ga kaze stran. */
    public static String opisPlacila(Paket paket, CiklusPlacila ciklus) {
        String ime = paket.kratekNaziv();
        String cikel = ciklus == null ? "" : ciklus == CiklusPlacila.LETNO ? ", letno" : ", mesečno";
        return "Plačilo: " + ime + cikel;
    }

    // ---------- Branje ----------

    /* Zadnja prijava in stevilo prijav v oknu za VSE racune; racun brez prijave
       v mapi ni. */
    @Transactional(readOnly = true)
    public Map<Long, Prijave> prijaveVsehRacunov() {
        Map<Long, Prijave> vse = new HashMap<>();
        for (Object[] v : prijave.povzetekZaVse(odOkna())) {
            vse.put(((Number) v[0]).longValue(), povzetek(v));
        }
        return vse;
    }

    @Transactional(readOnly = true)
    public Optional<Prijave> prijave(Long idRacun) {
        return prijave.povzetekZaRacun(idRacun, odOkna()).stream().findFirst()
                .map(RacunDnevnikStoritev::povzetek);
    }

    @Transactional(readOnly = true)
    public Map<Long, List<Dogodek>> dnevnikVsehRacunov() {
        Map<Long, List<Dogodek>> vse = new HashMap<>();
        for (Object[] v : dogodki.najnovejsiZaVse(NAJVEC_DOGODKOV)) {
            vse.computeIfAbsent(((Number) v[0]).longValue(), k -> new ArrayList<>()).add(dogodek(v));
        }
        return vse;
    }

    @Transactional(readOnly = true)
    public List<Dogodek> dnevnik(Long idRacun) {
        return dogodki.najnovejsiZaRacun(idRacun, NAJVEC_DOGODKOV).stream()
                .map(RacunDnevnikStoritev::dogodek).toList();
    }

    private static String odOkna() {
        return vNiz(zdaj().minusDays(OKNO_DNI));
    }

    private static String vNiz(LocalDateTime cas) {
        return BESEDILO_V_CAS.convertToDatabaseColumn(cas);
    }

    private static Prijave povzetek(Object[] v) {
        return new Prijave(BESEDILO_V_CAS.convertToEntityAttribute((String) v[1]),
                v[2] == null ? 0 : ((Number) v[2]).intValue());
    }

    private static Dogodek dogodek(Object[] v) {
        return new Dogodek(BESEDILO_V_CAS.convertToEntityAttribute((String) v[1]), (String) v[2]);
    }
}
