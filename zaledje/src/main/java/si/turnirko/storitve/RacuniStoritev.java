/* Racuni igralcev: registracija, potrjevanje s strani administratorja in
   zamenjava gesla.

   Registracija je prosta, zato racun nastane v stanju CAKA in sam po sebi ne
   daje nobene pravice - dokler ga administrator ne poveze z zapisom igralca,
   uporabnik ne vidi nicesar zasebnega. Povezava je enolicna v obe smeri:
   en igralec ima najvec en racun. */
package si.turnirko.storitve;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.PotrditevRacunaVnos;
import si.turnirko.dto.RacunIgralcaDto;
import si.turnirko.dto.RegistracijaVnos;
import si.turnirko.dto.SpremembaGeslaVnos;
import si.turnirko.dto.UporabnikDto;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Klub;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.IgralecRepozitorij;
import si.turnirko.repozitoriji.KlubRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

@Service
public class RacuniStoritev {

    private final UporabnikRepozitorij uporabnikRepozitorij;
    private final IgralecRepozitorij igralecRepozitorij;
    private final KlubRepozitorij klubRepozitorij;
    private final PasswordEncoder kodirnik;

    public RacuniStoritev(UporabnikRepozitorij uporabnikRepozitorij,
                          IgralecRepozitorij igralecRepozitorij,
                          KlubRepozitorij klubRepozitorij,
                          PasswordEncoder kodirnik) {
        this.uporabnikRepozitorij = uporabnikRepozitorij;
        this.igralecRepozitorij = igralecRepozitorij;
        this.klubRepozitorij = klubRepozitorij;
        this.kodirnik = kodirnik;
    }

    // ---------- Registracija in prijava ----------

    /* Registracija osebe (igralca ali organizatorja). E-posta je hkrati
       prijavno ime, zato mora biti enolicna. Racun nastane v stanju CAKA in
       sam po sebi ne daje pravice; potrdi ga administrator. Vrne profil novega
       (se nepotrjenega) racuna. */
    @Transactional
    public UporabnikDto registriraj(RegistracijaVnos v) {
        String email = v.email().trim().toLowerCase(Locale.ROOT);
        if (uporabnikRepozitorij.existsByUporabniskoImeIgnoreCase(email)) {
            throw new DomenskaIzjema("Racun z e-posto " + email + " ze obstaja.");
        }
        Vloga vloga = Boolean.TRUE.equals(v.organizator()) ? Vloga.ORGANIZATOR : Vloga.IGRALEC;
        Uporabnik u = new Uporabnik(email, kodirnik.encode(v.geslo()), vloga);
        u.setStatus(StatusRacuna.CAKA);
        u.setPrijavljenoIme(v.ime().trim());
        u.setPrijavljeniPriimek(v.priimek().trim());
        if (v.idKlub() != null) {
            Klub klub = klubRepozitorij.findById(v.idKlub())
                    .orElseThrow(() -> new NiNajdenoIzjema("Klub z id " + v.idKlub() + " ne obstaja."));
            u.setKlubZelja(klub);
        }
        return UporabnikDto.iz(uporabnikRepozitorij.save(u));
    }

    /* Profil trenutno prijavljenega uporabnika. */
    @Transactional(readOnly = true)
    public UporabnikDto profil(String prijavnoIme) {
        return UporabnikDto.iz(najdiPoPrijavnemImenu(prijavnoIme));
    }

    /* Zamenjava lastnega gesla; staro geslo se mora ujemati. */
    @Transactional
    public void zamenjajGeslo(String prijavnoIme, SpremembaGeslaVnos v) {
        Uporabnik u = najdiPoPrijavnemImenu(prijavnoIme);
        if (!kodirnik.matches(v.staro(), u.getGesloHash())) {
            throw new NeveljavenVnosIzjema("Staro geslo ni pravilno.");
        }
        if (kodirnik.matches(v.novo(), u.getGesloHash())) {
            throw new NeveljavenVnosIzjema("Novo geslo mora biti drugacno od starega.");
        }
        u.setGesloHash(kodirnik.encode(v.novo()));
        uporabnikRepozitorij.save(u);
    }

    // ---------- Administracija racunov ----------

    @Transactional(readOnly = true)
    public List<RacunIgralcaDto> racuni() {
        List<RacunIgralcaDto> seznam = new ArrayList<>();
        for (Uporabnik u : uporabnikRepozitorij.najdiRacuneOseb()) {
            // predlogi za povezavo veljajo le pri cakajocem racunu IGRALCA
            // (organizatorja ne povezujemo z zapisom v sifrantu igralcev)
            boolean predlagaj = u.getVloga() == Vloga.IGRALEC && u.getStatus() == StatusRacuna.CAKA;
            seznam.add(RacunIgralcaDto.iz(u, predlagaj ? predlogi(u) : List.of()));
        }
        return seznam;
    }

    @Transactional(readOnly = true)
    public long steviloCakajocih() {
        // igralci IN organizatorji, ki cakajo na potrditev
        return uporabnikRepozitorij.countByStatus(StatusRacuna.CAKA);
    }

    /* Potrditev igralca: racun se poveze z igralcem in s tem dobi dostop do
       svoje statistike. Igralec, ki racun ze ima, se ne sme povezati se enkrat. */
    @Transactional
    public RacunIgralcaDto potrdi(Long idRacuna, PotrditevRacunaVnos v) {
        Uporabnik u = najdiRacun(idRacuna);
        if (u.getVloga() != Vloga.IGRALEC) {
            throw new DomenskaIzjema("Ta racun ni igralski - uporabi potrditev organizatorja.");
        }
        Igralec igralec = igralecRepozitorij.najdiZVsem(v.idIgralec())
                .orElseThrow(() -> new NiNajdenoIzjema("Igralec z id " + v.idIgralec() + " ne obstaja."));
        if (igralec.isArhiviran()) {
            throw new DomenskaIzjema("Arhiviranemu igralcu ni mogoce dodeliti dostopa.");
        }
        Uporabnik obstojeci = najdiRacunIgralca(igralec.getId());
        if (obstojeci != null && !obstojeci.getId().equals(u.getId())) {
            throw new DomenskaIzjema("Igralec " + igralec.polnoIme() + " ima dostop ze dodeljen.");
        }
        u.setIgralec(igralec);
        u.setStatus(StatusRacuna.POTRJEN);
        u.setAktiven(true);
        return RacunIgralcaDto.iz(uporabnikRepozitorij.save(u), List.of());
    }

    /* Potrditev organizatorja: racun dobi vlogo organizatorja in (neobvezno)
       pripadnost klubu, po katerem se doloci klubsko soupravljanje tekmovanj.
       Za razliko od igralca se ne poveze z zapisom v sifrantu igralcev. */
    @Transactional
    public RacunIgralcaDto potrdiOrganizatorja(Long idRacuna, Long idKlub) {
        Uporabnik u = najdiRacun(idRacuna);
        if (u.getVloga() != Vloga.ORGANIZATOR) {
            throw new DomenskaIzjema("Ta racun ni organizatorski - uporabi potrditev igralca.");
        }
        if (idKlub != null) {
            Klub klub = klubRepozitorij.findById(idKlub)
                    .orElseThrow(() -> new NiNajdenoIzjema("Klub z id " + idKlub + " ne obstaja."));
            u.setKlub(klub);
        } else {
            u.setKlub(null); // organizator brez kluba upravlja samo svoja tekmovanja
        }
        u.setStatus(StatusRacuna.POTRJEN);
        u.setAktiven(true);
        return RacunIgralcaDto.iz(uporabnikRepozitorij.save(u), List.of());
    }

    /* Zavrnitev: racun ostane (da se ista e-posta ne registrira znova),
       a nima ne povezave ne dostopa. */
    @Transactional
    public RacunIgralcaDto zavrni(Long idRacuna) {
        Uporabnik u = najdiRacun(idRacuna);
        u.setStatus(StatusRacuna.ZAVRNJEN);
        u.setIgralec(null);
        u.setAktiven(false);
        return RacunIgralcaDto.iz(uporabnikRepozitorij.save(u), List.of());
    }

    /* Vklop/izklop dostopa ze potrjenemu racunu. */
    @Transactional
    public RacunIgralcaDto nastaviAktiven(Long idRacuna, boolean aktiven) {
        Uporabnik u = najdiRacun(idRacuna);
        u.setAktiven(aktiven);
        return RacunIgralcaDto.iz(uporabnikRepozitorij.save(u), List.of());
    }

    /* Izbris racuna. Potreben je, ker zavrnjen racun sicer za vedno zasede
       svojo e-posto - brisanje je edini nacin, da se ista oseba lahko
       registrira znova (ali da se odstrani lazna oz. testna registracija).
       Zapisi igralca in njegovih tekem se s tem ne dotaknejo. */
    @Transactional
    public void zbrisi(Long idRacuna) {
        uporabnikRepozitorij.delete(najdiRacun(idRacuna));
    }

    /* Ponastavitev gesla: administrator ga ne vidi, zato mu vrnemo novo
       nakljucno geslo, ki ga izroci igralcu. */
    @Transactional
    public String ponastaviGeslo(Long idRacuna) {
        Uporabnik u = najdiRacun(idRacuna);
        String novo = nakljucnoGeslo();
        u.setGesloHash(kodirnik.encode(novo));
        uporabnikRepozitorij.save(u);
        return novo;
    }

    /* Administrator nastavi racunu geslo, ki si ga izbere sam (za razliko od
       ponastaviGeslo, ki ga generira). Shranjena je le zgostitev; ker gesla
       kasneje ni mogoce prebrati, je nastavljanje edini nacin, da ga admin
       pozna in izroci igralcu. Staro geslo ni potrebno - administrator
       upravlja tuje racune, ne svojega. */
    @Transactional
    public void nastaviGeslo(Long idRacuna, String geslo) {
        Uporabnik u = najdiRacun(idRacuna);
        u.setGesloHash(kodirnik.encode(geslo));
        uporabnikRepozitorij.save(u);
    }

    // ---------- Pomozno ----------

    /* Kandidati za povezavo: ujemanje po priimku in imenu, sicer po priimku.
       Namen je prihraniti iskanje - administrator lahko izbere kogarkoli. */
    private List<RacunIgralcaDto.PredlogIgralcaDto> predlogi(Uporabnik u) {
        String ime = normaliziraj(u.getPrijavljenoIme());
        String priimek = normaliziraj(u.getPrijavljeniPriimek());
        if (priimek.isEmpty()) {
            return List.of();
        }
        List<Igralec> ujemajoci = new ArrayList<>(igralecRepozitorij.najdiAktivne().stream()
                .filter(i -> normaliziraj(i.getPriimek()).equals(priimek))
                .toList());
        // ujemanje priimka je pogoj; kdor se ujema tudi po imenu, gre na vrh
        ujemajoci.sort(java.util.Comparator
                .comparing((Igralec i) -> !normaliziraj(i.getIme()).equals(ime))
                .thenComparing(Igralec::abecedno));

        return ujemajoci.stream()
                .map(i -> new RacunIgralcaDto.PredlogIgralcaDto(
                        i.getId(), i.polnoIme(),
                        i.getKlub() != null ? i.getKlub().getIme() : null,
                        uporabnikRepozitorij.existsByIgralecId(i.getId())))
                .toList();
    }

    /* Primerjava brez sumnikov in velikih crk, da "Zagar" najde "Žagar". */
    private static String normaliziraj(String v) {
        if (v == null) {
            return "";
        }
        return java.text.Normalizer.normalize(v.trim().toLowerCase(Locale.ROOT),
                        java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }

    private static String nakljucnoGeslo() {
        String znaki = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        java.security.SecureRandom nakljucje = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append(znaki.charAt(nakljucje.nextInt(znaki.length())));
        }
        return sb.toString();
    }

    private Uporabnik najdiPoPrijavnemImenu(String prijavnoIme) {
        return uporabnikRepozitorij.najdiZVsem(prijavnoIme)
                .orElseThrow(() -> new NiNajdenoIzjema("Uporabnik ne obstaja."));
    }

    /* Racun osebe (igralca ali organizatorja); adminovega racuna te poti ne
       urejajo (admin se ne registrira in ne potrjuje). */
    private Uporabnik najdiRacun(Long id) {
        Uporabnik u = uporabnikRepozitorij.najdiZVsemPoId(id)
                .orElseThrow(() -> new NiNajdenoIzjema("Racun z id " + id + " ne obstaja."));
        if (u.getVloga() == Vloga.ADMIN) {
            throw new DomenskaIzjema("Administratorskega racuna ta pot ne ureja.");
        }
        return u;
    }

    private Uporabnik najdiRacunIgralca(Long idIgralec) {
        return uporabnikRepozitorij.najdiRacuneOseb().stream()
                .filter(r -> r.getIgralec() != null && r.getIgralec().getId().equals(idIgralec))
                .findFirst().orElse(null);
    }
}
