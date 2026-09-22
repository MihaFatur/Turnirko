/* Registracija racuna s potrditvijo e-poste, soglasje skrbnika, pozabljeno
   geslo in samodejna povezava racuna z igralcem.

   Nacela:
   - Naslov steje sele, ko lastnik vpise kodo, ki jo je dobil nanj. Do takrat
     je racun samo rezervacija, ki jo nova registracija z istim naslovom
     zamenja, nocno ciscenje pa po 48 urah pobrise.
   - Odgovori ne povedo, ali naslov ze obstaja (registracija, ponovno
     posiljanje, pozabljeno geslo vrnejo vedno isto). Lastnika obstojecega
     racuna obvesti posta, klicatelja ne.
   - Mlajsi od 15 let (ZVOP-2) navedejo naslov starsa oz. skrbnika, ki dobi
     svojo kodo; brez nje racun ne pride do povezave z igralcem.
   - Povezavo z zapisom v sifrantu naredi ADMIN ali SAMODEJNO: ce se ob
     potrjenem naslovu ime, priimek in datum rojstva ujemajo z natanko enim
     igralcem brez racuna. Dva zadetka ali nobeden pomenita cakanje na admina.
   - Admin se ne registrira in gesla ne ponastavlja po posti: najmocnejsi
     racun ostane izven te poti. */
package si.turnirko.storitve;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.NovoGesloVnos;
import si.turnirko.dto.PonovnoPosiljanjeVnos;
import si.turnirko.dto.PotrditevKodeVnos;
import si.turnirko.dto.PotrditevOdgovorDto;
import si.turnirko.dto.RegistracijaOdgovorDto;
import si.turnirko.dto.RegistracijaVnos;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.izjeme.PrevecZahtevIzjema;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Klub;
import si.turnirko.modeli.NamenKode;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.VirPovezave;
import si.turnirko.modeli.Vloga;
import si.turnirko.posta.PostaStoritev;
import si.turnirko.posta.SporocilaPoste;
import si.turnirko.repozitoriji.IgralecRepozitorij;
import si.turnirko.repozitoriji.KlubRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

@Service
public class RegistracijaStoritev {

    private static final Logger dnevnik = LoggerFactory.getLogger(RegistracijaStoritev.class);

    /* Pod to starostjo ZVOP-2 zahteva privolitev starsa oz. skrbnika. */
    static final int STAROST_SKRBNIKA = 15;

    /* Isto sporocilo za napacno, potekleo ali neobstojeco kodo - drugace bi
       odgovor povedal, ali racun obstaja. */
    public static final String NAPACNA_KODA = "Koda ni pravilna ali je potekla.";

    /* Omejitve posiljanja kod: na naslov najvec 1 na minuto in 5 na uro,
       na naslov IP 20 na uro (za enim naslovom je lahko cel klub). */
    static final int NA_NASLOV_NA_MINUTO = 1;
    static final int NA_NASLOV_NA_URO = 5;
    static final int NA_IP_NA_URO = 20;
    private static final Duration MINUTA = Duration.ofMinutes(1);
    private static final Duration URA = Duration.ofHours(1);

    private final UporabnikRepozitorij uporabnikRepozitorij;
    private final IgralecRepozitorij igralecRepozitorij;
    private final KlubRepozitorij klubRepozitorij;
    private final PasswordEncoder kodirnik;
    private final KodeStoritev kode;
    private final PostaStoritev posta;
    private final OmejevalnikPoskusov omejevalnik;

    public RegistracijaStoritev(UporabnikRepozitorij uporabnikRepozitorij,
                                IgralecRepozitorij igralecRepozitorij,
                                KlubRepozitorij klubRepozitorij,
                                PasswordEncoder kodirnik,
                                KodeStoritev kode,
                                PostaStoritev posta,
                                OmejevalnikPoskusov omejevalnik) {
        this.uporabnikRepozitorij = uporabnikRepozitorij;
        this.igralecRepozitorij = igralecRepozitorij;
        this.klubRepozitorij = klubRepozitorij;
        this.kodirnik = kodirnik;
        this.kode = kode;
        this.posta = posta;
        this.omejevalnik = omejevalnik;
    }

    // ---------- Registracija ----------

    /* Ustvari racun v stanju CAKA in poslje kodo za potrditev naslova (pri
       mlajsih od 15 let se kodo skrbniku). Odgovor je enak, ce naslov ze
       pripada potrjenemu racunu - takrat nov racun ne nastane, lastnik pa
       dobi obvestilo. */
    @Transactional
    public RegistracijaOdgovorDto registriraj(RegistracijaVnos v, String naslovIp) {
        String email = normalizirajEmail(v.email());
        boolean organizator = Boolean.TRUE.equals(v.organizator());

        LocalDate datumRojstva = null;
        String emailSkrbnika = null;
        if (!organizator) {
            datumRojstva = preverjenDatumRojstva(v.datumRojstva());
            if (potrebujeSkrbnika(datumRojstva)) {
                emailSkrbnika = preverjenEmailSkrbnika(v.emailSkrbnika(), email);
            }
        }
        preveriOmejitevPosiljanja(email, naslovIp);

        /* Zgostitev gesla se izracuna VEDNO, tudi ce racun ne bo nastal - BCrypt
           je najdrazji korak in brez njega bi bil odgovor za zaseden naslov
           merljivo hitrejsi, torej bi cas razkril, kaj odgovor skriva. */
        String gesloHash = kodirnik.encode(v.geslo());

        Optional<Uporabnik> obstojeci = uporabnikRepozitorij.findByUporabniskoImeIgnoreCase(email);
        if (obstojeci.isPresent()) {
            Uporabnik o = obstojeci.get();
            /* Potrjen naslov, administrator ali racun, ki ga je admin zavrnil
               (zavrnitev je odlocitev, ki je nova registracija ne sme obiti),
               ostane zaseden. */
            if (o.jeEmailPotrjen() || o.getVloga() == Vloga.ADMIN
                    || o.getStatus() == StatusRacuna.ZAVRNJEN) {
                posta.poslji(email, SporocilaPoste.obstojeciRacun());
                // enak strosek kot zgostitev kode, ki bi sicer nastala
                kode.preveriNeznanega("000000");
                return new RegistracijaOdgovorDto(email, emailSkrbnika != null);
            }
            /* Nepotrjen ostanek prejsnjega poskusa: lastnistva naslova ni
               dokazal nihce, zato ga nova registracija zamenja. */
            kode.pobrisiVse(o);
            uporabnikRepozitorij.delete(o);
            uporabnikRepozitorij.flush();
        }

        Uporabnik u = new Uporabnik(email, gesloHash,
                organizator ? Vloga.ORGANIZATOR : Vloga.IGRALEC);
        u.setStatus(StatusRacuna.CAKA);
        u.setPrijavljenoIme(v.ime().trim());
        u.setPrijavljeniPriimek(v.priimek().trim());
        u.setPrijavljeniDatumRojstva(datumRojstva);
        u.setEmailSkrbnika(emailSkrbnika);
        if (v.idKlub() != null) {
            Klub klub = klubRepozitorij.findById(v.idKlub())
                    .orElseThrow(() -> new NiNajdenoIzjema("Klub z id " + v.idKlub() + " ne obstaja."));
            u.setKlubZelja(klub);
        }
        u = uporabnikRepozitorij.save(u);

        posljiKodo(u, NamenKode.EPOSTA);
        if (emailSkrbnika != null) {
            posljiKodo(u, NamenKode.SKRBNIK);
        }
        return new RegistracijaOdgovorDto(email, emailSkrbnika != null);
    }

    /* Vpis kode s potrjenega naslova. Ob uspehu se racun poskusi samodejno
       povezati z igralcem. */
    @Transactional
    public PotrditevOdgovorDto potrdiEposto(PotrditevKodeVnos v) {
        Uporabnik u = najdi(v.email());
        boolean pravilna;
        if (u == null || u.jeEmailPotrjen()) {
            kode.preveriNeznanega(v.koda());
            pravilna = false;
        } else {
            pravilna = kode.preveri(u, NamenKode.EPOSTA, v.koda());
        }
        if (!pravilna) {
            throw new NeveljavenVnosIzjema(NAPACNA_KODA);
        }
        u.setEmailPotrjenOb(LocalDateTime.now());
        boolean povezan = poskusiSamodejnoPovezavo(u);
        uporabnikRepozitorij.save(u);
        return odgovor(u, povezan);
    }

    /* Vpis kode, ki jo je dobil skrbnik (otrok jo prepise). */
    @Transactional
    public PotrditevOdgovorDto potrdiSkrbnika(PotrditevKodeVnos v) {
        Uporabnik u = najdi(v.email());
        boolean pravilna;
        if (u == null || !u.cakaSkrbnika()) {
            kode.preveriNeznanega(v.koda());
            pravilna = false;
        } else {
            pravilna = kode.preveri(u, NamenKode.SKRBNIK, v.koda());
        }
        if (!pravilna) {
            throw new NeveljavenVnosIzjema(NAPACNA_KODA);
        }
        u.setSkrbnikPotrjenOb(LocalDateTime.now());
        boolean povezan = poskusiSamodejnoPovezavo(u);
        uporabnikRepozitorij.save(u);
        return odgovor(u, povezan);
    }

    /* Nova koda za naslov ali skrbnika. Brez odgovora o obstoju racuna. */
    @Transactional
    public void ponovnoPoslji(PonovnoPosiljanjeVnos v, String naslovIp) {
        if (v.namen() == NamenKode.GESLO) {
            throw new NeveljavenVnosIzjema("Za geslo uporabi »Pozabljeno geslo«.");
        }
        String email = normalizirajEmail(v.email());
        preveriOmejitevPosiljanja(email, naslovIp);
        Uporabnik u = najdi(email);
        if (u == null || u.getVloga() == Vloga.ADMIN) {
            return;
        }
        if (v.namen() == NamenKode.EPOSTA && !u.jeEmailPotrjen()) {
            posljiKodo(u, NamenKode.EPOSTA);
        } else if (v.namen() == NamenKode.SKRBNIK && u.cakaSkrbnika()) {
            posljiKodo(u, NamenKode.SKRBNIK);
        }
    }

    // ---------- Pozabljeno geslo ----------

    /* Koda za novo geslo gre samo na POTRJEN naslov aktivnega racuna osebe;
       administratorjevega gesla ta pot ne ponastavlja. */
    @Transactional
    public void pozabljenoGeslo(String vnosEmail, String naslovIp) {
        String email = normalizirajEmail(vnosEmail);
        preveriOmejitevPosiljanja(email, naslovIp);
        Uporabnik u = najdi(email);
        if (u == null || u.getVloga() == Vloga.ADMIN || !u.jeEmailPotrjen() || !u.isAktiven()) {
            return;
        }
        posljiKodo(u, NamenKode.GESLO);
    }

    @Transactional
    public void novoGeslo(NovoGesloVnos v) {
        Uporabnik u = najdi(v.email());
        boolean pravilna;
        if (u == null || u.getVloga() == Vloga.ADMIN) {
            kode.preveriNeznanega(v.koda());
            pravilna = false;
        } else {
            pravilna = kode.preveri(u, NamenKode.GESLO, v.koda());
        }
        if (!pravilna) {
            throw new NeveljavenVnosIzjema(NAPACNA_KODA);
        }
        u.setGesloHash(kodirnik.encode(v.geslo()));
        uporabnikRepozitorij.save(u);
        /* Kdor je dokazal lastnistvo naslova, ni napadalec: morebitna blokada
           prijave zaradi prejsnjega ugibanja odpade. */
        omejevalnik.pocistiPrijaveRacuna(u.getUporabniskoIme());
    }

    // ---------- Samodejna povezava ----------

    /* Racun igralca se povezze z zapisom v sifrantu, ce so pogoji izpolnjeni
       (potrjen naslov, soglasje skrbnika, ce je potrebno) in ce se ime,
       priimek in datum rojstva ujemajo z NATANKO ENIM igralcem brez racuna.
       Vrne true, ce je povezava nastala. */
    boolean poskusiSamodejnoPovezavo(Uporabnik u) {
        if (u.getVloga() != Vloga.IGRALEC || u.getIgralec() != null
                || u.getStatus() != StatusRacuna.CAKA
                || !u.jeEmailPotrjen() || u.cakaSkrbnika()
                || u.getPrijavljeniDatumRojstva() == null) {
            return false;
        }
        String ime = RacuniStoritev.normaliziraj(u.getPrijavljenoIme());
        String priimek = RacuniStoritev.normaliziraj(u.getPrijavljeniPriimek());
        List<Igralec> kandidati = igralecRepozitorij
                .najdiPoDatumihRojstva(List.of(u.getPrijavljeniDatumRojstva())).stream()
                .filter(i -> !i.isArhiviran())
                .filter(i -> RacuniStoritev.normaliziraj(i.getIme()).equals(ime)
                        && RacuniStoritev.normaliziraj(i.getPriimek()).equals(priimek))
                .toList();
        if (kandidati.size() != 1) {
            return false;
        }
        Igralec igralec = kandidati.get(0);
        if (uporabnikRepozitorij.existsByIgralecId(igralec.getId())) {
            return false;
        }
        u.setIgralec(igralec);
        u.setStatus(StatusRacuna.POTRJEN);
        u.setAktiven(true);
        u.setVirPovezave(VirPovezave.SAMODEJNO);
        u.setPovezanOb(LocalDateTime.now());
        dnevnik.info("Racun {} samodejno povezan z igralcem {}.", u.getId(), igralec.getId());
        return true;
    }

    // ---------- Pomozno ----------

    static boolean potrebujeSkrbnika(LocalDate datumRojstva) {
        return Period.between(datumRojstva, LocalDate.now()).getYears() < STAROST_SKRBNIKA;
    }

    static String normalizirajEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private Uporabnik najdi(String email) {
        return uporabnikRepozitorij.findByUporabniskoImeIgnoreCase(normalizirajEmail(email)).orElse(null);
    }

    private static PotrditevOdgovorDto odgovor(Uporabnik u, boolean povezan) {
        return new PotrditevOdgovorDto(povezan, u.cakaSkrbnika(), u.getVloga() == Vloga.ORGANIZATOR);
    }

    private void posljiKodo(Uporabnik u, NamenKode namen) {
        String koda = kode.ustvari(u, namen);
        switch (namen) {
            case EPOSTA -> posta.poslji(u.getUporabniskoIme(), SporocilaPoste.potrditevEposte(koda));
            case SKRBNIK -> posta.poslji(u.getEmailSkrbnika(), SporocilaPoste.soglasjeSkrbnika(
                    u.getPrijavljenoIme() + " " + u.getPrijavljeniPriimek(), koda));
            case GESLO -> posta.poslji(u.getUporabniskoIme(), SporocilaPoste.pozabljenoGeslo(koda));
        }
    }

    private void preveriOmejitevPosiljanja(String email, String naslovIp) {
        String kljucNaslova = "koda:" + email;
        String kljucIp = "koda-ip:" + (naslovIp == null ? "?" : naslovIp);
        if (omejevalnik.jeCezMejo(kljucNaslova, NA_NASLOV_NA_MINUTO, MINUTA)) {
            throw new PrevecZahtevIzjema("Kodo smo pravkar poslali. Počakaj minuto, preden zahtevaš novo.");
        }
        if (omejevalnik.jeCezMejo(kljucNaslova, NA_NASLOV_NA_URO, URA)
                || omejevalnik.jeCezMejo(kljucIp, NA_IP_NA_URO, URA)) {
            throw new PrevecZahtevIzjema("Preveč zahtev za kodo. Poskusi čez eno uro.");
        }
        omejevalnik.zabelezi(kljucNaslova);
        omejevalnik.zabelezi(kljucIp);
    }

    private static LocalDate preverjenDatumRojstva(LocalDate datum) {
        if (datum == null) {
            throw new NeveljavenVnosIzjema("Datum rojstva je obvezen.");
        }
        LocalDate danes = LocalDate.now();
        if (!datum.isBefore(danes) || datum.isBefore(danes.minusYears(110))) {
            throw new NeveljavenVnosIzjema("Datum rojstva ni veljaven.");
        }
        return datum;
    }

    private static String preverjenEmailSkrbnika(String vnos, String emailOtroka) {
        if (vnos == null || vnos.isBlank()) {
            throw new NeveljavenVnosIzjema(
                    "Za mlajše od 15 let je obvezna e-pošta starša oz. skrbnika.");
        }
        String email = normalizirajEmail(vnos);
        if (!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw new NeveljavenVnosIzjema("E-pošta skrbnika ni veljavna.");
        }
        if (email.equals(emailOtroka)) {
            throw new NeveljavenVnosIzjema("E-pošta skrbnika mora biti drugačna od e-pošte otroka.");
        }
        return email;
    }
}
