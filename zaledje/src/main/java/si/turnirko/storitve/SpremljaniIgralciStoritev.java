/* Igralci, ki jih racun spremlja, in njihov povzetek za domaco stran.

   Izbor je osebna nastavitev racuna (kot spremljane lige v DomovStoritev), ne
   zapis o tekmovanju. Povzetek bere samo to, kar je na profilu igralca ze
   javno - zasebnih analiz profila (forma, nasprotniki, napoved) ne prikaze,
   ker jih vidi samo igralec sam. Racun navijaca zato ne dobi nobenega
   osebnega podatka tujega igralca: spremljanje je bliznjica do javnega
   profila, ne dostop. */
package si.turnirko.storitve;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.DomovIgralecDto;
import si.turnirko.dto.ProfilDto;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.izjeme.PrepovedanoIzjema;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Liga;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.SpremljanIgralec;
import si.turnirko.modeli.Srecanje;
import si.turnirko.modeli.StatusSrecanja;
import si.turnirko.modeli.StatusTekmovanja;
import si.turnirko.modeli.Turnir;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.repozitoriji.IgralecRepozitorij;
import si.turnirko.repozitoriji.KaderEkipeRepozitorij;
import si.turnirko.repozitoriji.LigaRepozitorij;
import si.turnirko.repozitoriji.PrijavaRepozitorij;
import si.turnirko.repozitoriji.SpremljanIgralecRepozitorij;
import si.turnirko.repozitoriji.SrecanjeRepozitorij;
import si.turnirko.repozitoriji.TurnirRepozitorij;

@Service
public class SpremljaniIgralciStoritev {

    /* Koliko igralcev sme racun spremljati, ne da bi kaj placal. Ena stevilka za
       vse vloge. Paket Navijac in Igralec Premium mejo odstranita
       (NarocninaStoritev.imaSirokoSpremljanje), admin je brez meje. */
    public static final int NAJVEC_BREZPLACNO = 1;

    /* Toliko tekem nosi vrstica povzetka. Vec jih sklop ne prenese - kdor hoce
       vse, gre na profil. */
    private static final int ZADNJIH_TEKEM = 3;

    private final SpremljanIgralecRepozitorij repozitorij;
    private final IgralecRepozitorij igralecRepozitorij;
    private final LigaRepozitorij ligaRepozitorij;
    private final SrecanjeRepozitorij srecanjeRepozitorij;
    private final KaderEkipeRepozitorij kaderRepozitorij;
    private final PrijavaRepozitorij prijavaRepozitorij;
    private final TurnirRepozitorij turnirRepozitorij;
    private final ProfilStoritev profilStoritev;
    private final LastnistvoStoritev lastnistvo;
    private final NarocninaStoritev narocnina;
    private final DruzinaStoritev druzina;

    public SpremljaniIgralciStoritev(SpremljanIgralecRepozitorij repozitorij,
                                     IgralecRepozitorij igralecRepozitorij,
                                     LigaRepozitorij ligaRepozitorij,
                                     SrecanjeRepozitorij srecanjeRepozitorij,
                                     KaderEkipeRepozitorij kaderRepozitorij,
                                     PrijavaRepozitorij prijavaRepozitorij,
                                     TurnirRepozitorij turnirRepozitorij,
                                     ProfilStoritev profilStoritev,
                                     LastnistvoStoritev lastnistvo,
                                     NarocninaStoritev narocnina,
                                     DruzinaStoritev druzina) {
        this.repozitorij = repozitorij;
        this.igralecRepozitorij = igralecRepozitorij;
        this.ligaRepozitorij = ligaRepozitorij;
        this.srecanjeRepozitorij = srecanjeRepozitorij;
        this.kaderRepozitorij = kaderRepozitorij;
        this.prijavaRepozitorij = prijavaRepozitorij;
        this.turnirRepozitorij = turnirRepozitorij;
        this.profilStoritev = profilStoritev;
        this.lastnistvo = lastnistvo;
        this.narocnina = narocnina;
        this.druzina = druzina;
    }

    // ---------- Izbor ----------

    /* Id-ji igralcev, ki jih spremlja prijavljeni racun. */
    @Transactional(readOnly = true)
    public List<Long> idji() {
        return repozitorij.idjiZaRacun(zahtevajPrijavo().getId());
    }

    /* Doda igralca v izbor; ponovni klic ne naredi nicesar (idempotentno, ker je
       gumb preklop in ne stevec).

       Meja velja za NOVEGA igralca: kdor jo je dosegel, mora prejsnjega najprej
       odstraniti, drugega ne zamenjamo tiho - starsu, ki je spremljal otroka,
       bi otrok ob pomoti izginil z domace strani. */
    @Transactional
    public List<Long> spremljaj(Long idIgralec) {
        Uporabnik jaz = zahtevajPrijavo();
        Igralec igralec = igralecRepozitorij.findById(idIgralec)
                .orElseThrow(() -> new NiNajdenoIzjema("Igralec z id " + idIgralec + " ne obstaja."));
        if (jaz.getIgralec() != null && jaz.getIgralec().getId().equals(idIgralec)) {
            throw new NeveljavenVnosIzjema("Sebe ni treba spremljati: tvoj profil je pod »Moj profil«.");
        }
        if (!repozitorij.existsByIdRacunAndIdIgralec(jaz.getId(), idIgralec)) {
            if (repozitorij.countByIdRacun(jaz.getId()) >= NAJVEC_BREZPLACNO
                    && !narocnina.imaSirokoSpremljanje(jaz)) {
                throw new DomenskaIzjema(sporociloMeje(jaz));
            }
            repozitorij.save(new SpremljanIgralec(jaz.getId(), igralec.getId()));
        }
        return repozitorij.idjiZaRacun(jaz.getId());
    }

    @Transactional
    public List<Long> nehajSpremljati(Long idIgralec) {
        Uporabnik jaz = zahtevajPrijavo();
        repozitorij.deleteByIdRacunAndIdIgralec(jaz.getId(), idIgralec);
        return repozitorij.idjiZaRacun(jaz.getId());
    }

    /* Sporocilo ob dosezeni meji pove, koga racun ze spremlja: gumb na profilu
       drugega igralca sicer samo zavrne in uporabnik ne ve, kaj naj stori. */
    private String sporociloMeje(Uporabnik jaz) {
        String ime = repozitorij.idjiZaRacun(jaz.getId()).stream()
                .findFirst()
                .flatMap(igralecRepozitorij::findById)
                .map(Igralec::polnoIme)
                .orElse(null);
        String koliko = NAJVEC_BREZPLACNO == 1 ? "enega igralca" : NAJVEC_BREZPLACNO + " igralce";
        String ponudba = switch (jaz.getVloga()) {
            case NAVIJAC -> " Paket Navijač (19,99 € na leto) jih spremlja neomejeno.";
            case IGRALEC -> " Neomejeno jih spremlja Igralec Premium.";
            default -> "";
        };
        return "Brezplačen račun spremlja " + koliko
                + (ime == null ? "." : " (zdaj " + ime + "). Najprej nehaj spremljati njega.") + ponudba;
    }

    /* Izbor je last racuna, zato brez prijave ne obstaja. Veriga to pot ze
       zapre; preverba je tu druga obramba in daje razumljivo sporocilo. */
    private Uporabnik zahtevajPrijavo() {
        Uporabnik jaz = lastnistvo.trenutni();
        if (jaz == null) {
            throw new PrepovedanoIzjema("Za spremljanje igralcev je potrebna prijava.");
        }
        return jaz;
    }

    // ---------- Povzetki ----------

    /* Povzetki igralcev v druzini racuna (V59, najprej: to so najpomembnejsi) in
       spremljanih igralcev v vrstnem redu dodajanja. Igralec, ki bi med izborom in
       branjem izginil, tiho odpade (id je lahko zastarel). Druzina ne steje v mejo
       spremljanih igralcev: je svoja pravica, ki jo potrdi admin. */
    @Transactional(readOnly = true)
    public List<DomovIgralecDto> povzetki() {
        Uporabnik jaz = zahtevajPrijavo();
        Set<Long> izDruzine = druzina.igralciDruzine(jaz);
        Set<Long> idji = new java.util.LinkedHashSet<>(izDruzine);
        idji.addAll(repozitorij.idjiZaRacun(jaz.getId()));
        LocalDate danes = PotekLige.danes();
        return idji.stream()
                .filter(igralecRepozitorij::existsById)
                .map(id -> povzetek(id, danes, izDruzine.contains(id)))
                .toList();
    }

    private DomovIgralecDto povzetek(Long idIgralec, LocalDate danes, boolean izDruzine) {
        /* Rating, mesto in tekme so isti izracun kot na profilu: povzetek in
           stran profila ne smeta trditi dvoje. Profil je tudi za igralca s sto
           tekmami poceni (en obhod nastopov), spremljamo pa najvec
           NAJVEC_BREZPLACNO igralcev. */
        ProfilDto profil = profilStoritev.profil(idIgralec);
        List<ProfilDto.TekmaProfila> zadnje = profil.tekme().stream().limit(ZADNJIH_TEKEM).toList();
        return new DomovIgralecDto(
                idIgralec,
                profil.glava().polnoIme(),
                profil.glava().klub(),
                profil.glava().rating(),
                profil.uvrstitev(),
                zadnje,
                naslednjeSrecanje(idIgralec, danes),
                prihajajociTurnir(idIgralec, danes),
                izDruzine);
    }

    /* Naslednje srecanje ekipe, v kateri igralec nastopa, v ligah, ki se niso
       zakljucene. Pravilo je isto kot "naslednje kolo" lige (PotekLige): ne
       koncano, ne caka na termin in termin ni ze mimo - srecanje z nevpisanim
       izidom in starim datumom ni "naslednje", ker vrstica obljublja, kdaj se
       igra NASLEDNJIC. Med ligami velja najzgodnejsi zacetek; srecanje brez
       zacetka gre za tistimi, ki ga imajo. */
    private DomovIgralecDto.NaslednjeSrecanje naslednjeSrecanje(Long idIgralec, LocalDate danes) {
        DomovIgralecDto.NaslednjeSrecanje najboljse = null;
        for (Liga liga : ligaRepozitorij.najdiNezakljuceneZaIgralca(idIgralec)) {
            Set<Long> ekipe = new HashSet<>(kaderRepozitorij.ekipeIgralcaVLigi(liga.getId(), idIgralec));
            for (Srecanje s : srecanjeRepozitorij.najdiZaLigo(liga.getId())) {
                boolean doma = ekipe.contains(s.getEkipaDomaci().getId());
                if (!doma && !ekipe.contains(s.getEkipaGost().getId())) {
                    continue;
                }
                if (s.getStatus() == StatusSrecanja.KONCANO || s.isCakaNaTermin()) {
                    continue;
                }
                LocalDateTime zacetek = s.getPredvidenZacetek();
                if (zacetek != null && zacetek.toLocalDate().isBefore(danes)) {
                    continue;
                }
                if (najboljse != null && !jeZgodnejse(zacetek, s.getKolo(), najboljse)) {
                    continue;
                }
                najboljse = new DomovIgralecDto.NaslednjeSrecanje(
                        s.getId(), liga.getId(), liga.getIme(), s.getKolo(), zacetek,
                        (doma ? s.getEkipaDomaci() : s.getEkipaGost()).prikazanoIme(),
                        (doma ? s.getEkipaGost() : s.getEkipaDomaci()).prikazanoIme(),
                        doma);
            }
        }
        return najboljse;
    }

    private static boolean jeZgodnejse(LocalDateTime zacetek, int kolo,
                                       DomovIgralecDto.NaslednjeSrecanje drugo) {
        if (zacetek == null) {
            return false;
        }
        if (drugo.zacetek() == null) {
            return true;
        }
        int primerjava = zacetek.compareTo(drugo.zacetek());
        return primerjava < 0 || (primerjava == 0 && kolo < drugo.kolo());
    }

    /* Najbolj zgoden turnir z danasnjim ali poznejsim datumom, na katerega je
       igralec prijavljen (posamicno, v paru ali v kadru ekipe) in ni zakljucen.
       Turnir brez datuma ni "prihajajoc": o njem ne vemo, kdaj bo. */
    private DomovIgralecDto.PrihajajociTurnir prihajajociTurnir(Long idIgralec, LocalDate danes) {
        Set<Long> idji = new HashSet<>(
                prijavaRepozitorij.turnirjiIgralca(idIgralec, Prijava.StatusPrijave.ODJAVLJEN));
        idji.addAll(kaderRepozitorij.ekipniTurnirjiIgralca(idIgralec));
        return turnirRepozitorij.findAllById(idji).stream()
                .filter(t -> t.getDatumZacetka() != null && !t.getDatumZacetka().isBefore(danes))
                .filter(t -> t.getStatus() != StatusTekmovanja.ZAKLJUCEN)
                .min(Comparator.comparing(Turnir::getDatumZacetka).thenComparing(Turnir::getId))
                .map(t -> new DomovIgralecDto.PrihajajociTurnir(
                        t.getId(), t.getIme(), t.getDatumZacetka(),
                        StatusTekmovanja.zaPrikaz(t.getStatus(), t.getDatumZacetka(), danes)))
                .orElse(null);
    }
}
