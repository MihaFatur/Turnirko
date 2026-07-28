/* Zreb tekem za dogodek. Podprti so trije sistemi:

   - IZLOCILNI: klasicna izlocilna mreza za poljubno stevilo igralcev
     (>= 2); mreza se razsiri na najblizjo potenco 2, manjkajoca mesta so
     PROSTA MESTA (bye). Prosta mesta so enakomerno razprsena po "seed"
     vrstnem redu, tako da se dve nikoli ne srecata.

   - KROZNI (vsak z vsakim): po krozni metodi se ustvarijo vse tekme;
     razvrstitev doloca lestvica (glej RazvrstitevStoritev).

   - SKUPINE_IZLOCILNI: igralci se razdelijo v skupine (krozni del znotraj
     skupine), najboljsi napredujejo v izlocilno mrezo. Izlocilni del se
     zgenerira sele, ko so vse skupine odigrane (glej SkupineStoritev),
     zato se tu ustvarijo samo skupine in njihove tekme.

   Vse tekme izlocilne mreze dobijo EKSPLICITNE povezave na izvorni tekmi
   (idIzvorTekma1/2 + vloga ZMAGOVALEC), po katerih tece napredovanje. */
package si.turnirko.storitve;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.FazaTekme;
import si.turnirko.modeli.IzidTekme;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.RatingStanje;
import si.turnirko.modeli.SistemTekmovanja;
import si.turnirko.modeli.Skupina;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.StatusTekmovanja;
import si.turnirko.modeli.Tekma;
import si.turnirko.modeli.VlogaIzvora;
import si.turnirko.repozitoriji.DogodekRepozitorij;
import si.turnirko.repozitoriji.PrijavaRepozitorij;
import si.turnirko.repozitoriji.RatingStanjeRepozitorij;
import si.turnirko.repozitoriji.SkupinaRepozitorij;
import si.turnirko.repozitoriji.TekmaRepozitorij;

@Service
public class ZrebStoritev {

    /* Najmanjse stevilo igralcev za smiselen skupinski sistem (vsaj 2
       skupini po vsaj 3 igralce). */
    static final int NAJMANJ_ZA_SKUPINE = 6;

    /* Iz vsake skupine napredujeta dva najboljsa v izlocilni del. */
    static final int NAPREDUJE_IZ_SKUPINE = 2;

    private final DogodekRepozitorij dogodekRepozitorij;
    private final PrijavaRepozitorij prijavaRepozitorij;
    private final TekmaRepozitorij tekmaRepozitorij;
    private final SkupinaRepozitorij skupinaRepozitorij;
    private final RatingStanjeRepozitorij ratingStanjeRepozitorij;
    private final IzborStoritev izborStoritev;
    private final LastnistvoStoritev lastnistvo;

    /* Vir nakljucnosti je zamenljiv, da so testi lahko deterministicni. */
    private Random nakljucje = new SecureRandom();

    public ZrebStoritev(DogodekRepozitorij dogodekRepozitorij,
                        PrijavaRepozitorij prijavaRepozitorij,
                        TekmaRepozitorij tekmaRepozitorij,
                        SkupinaRepozitorij skupinaRepozitorij,
                        RatingStanjeRepozitorij ratingStanjeRepozitorij,
                        IzborStoritev izborStoritev,
                        LastnistvoStoritev lastnistvo) {
        this.dogodekRepozitorij = dogodekRepozitorij;
        this.prijavaRepozitorij = prijavaRepozitorij;
        this.tekmaRepozitorij = tekmaRepozitorij;
        this.skupinaRepozitorij = skupinaRepozitorij;
        this.ratingStanjeRepozitorij = ratingStanjeRepozitorij;
        this.izborStoritev = izborStoritev;
        this.lastnistvo = lastnistvo;
    }

    void nastaviNakljucje(Random nakljucje) {
        this.nakljucje = nakljucje;
    }

    /* Izvede zreb za dogodek in ustvari vse tekme (glede na sistem). */
    @Transactional
    public List<Tekma> izvediZreb(Long idDogodka) {
        lastnistvo.preveriTurnirPoDogodku(idDogodka);
        Dogodek dogodek = dogodekRepozitorij.najdiSTurnirjem(idDogodka)
                .orElseThrow(() -> new NiNajdenoIzjema("Dogodek z id " + idDogodka + " ne obstaja."));

        if (dogodek.getStatus() != StatusTekmovanja.PRIPRAVA) {
            throw new DomenskaIzjema("Zreb je mogoc samo, dokler je dogodek v pripravi.");
        }
        if (tekmaRepozitorij.existsByDogodekId(idDogodka)) {
            throw new DomenskaIzjema("Zreb za ta dogodek ze obstaja.");
        }

        List<Prijava> prijave = prijavaRepozitorij.najdiZaDogodekSStatusom(
                idDogodka, Prijava.StatusPrijave.PRIJAVLJEN);
        if (prijave.size() < 2) {
            throw new DomenskaIzjema("Za zreb sta potrebna vsaj 2 prijavljena igralca (trenutno: "
                    + prijave.size() + ").");
        }

        zabeleziRatingObZrebu(prijave);

        List<Tekma> vseTekme = switch (dogodek.getSistemTekmovanja()) {
            case IZLOCILNI -> zrebIzlocilni(dogodek, prijave);
            case KROZNI -> zrebKrozni(dogodek, prijave);
            case SKUPINE_IZLOCILNI -> zrebSkupine(dogodek, prijave);
            case SKUPINE -> zrebSkupinePoJakosti(dogodek, prijave);
        };

        // zreb pomeni zacetek tekmovanja
        dogodek.setStatus(StatusTekmovanja.V_TEKU);
        if (dogodek.getTurnir().getStatus() == StatusTekmovanja.PRIPRAVA) {
            dogodek.getTurnir().setStatus(StatusTekmovanja.V_TEKU);
        }
        return vseTekme;
    }

    // ---------------------------------------------------------------------
    // IZLOCILNI
    // ---------------------------------------------------------------------

    private List<Tekma> zrebIzlocilni(Dogodek dogodek, List<Prijava> prijave) {
        // nakljucni vrstni red igralcev = zreb (nosilci pridejo v kasnejsi fazi)
        List<Prijava> poSeedu = new ArrayList<>(prijave);
        Collections.shuffle(poSeedu, nakljucje);
        return zgradiIzlocilnoMrezo(dogodek, poSeedu);
    }

    /* Zgradi izlocilno mrezo iz seznama igralcev v "seed" vrstnem redu
       (poSeedu.get(0) = prvi nosilec). Metodo uporablja tudi skupinski
       sistem za izlocilni del iz kvalificiranih igralcev. */
    public List<Tekma> zgradiIzlocilnoMrezo(Dogodek dogodek, List<Prijava> poSeedu) {
        int steviloIgralcev = poSeedu.size();
        int velikostMreze = najblizjaPotencaDve(steviloIgralcev);

        // razporeditev po "seed" polozajih: polozaji manjkajocih stevilk
        // (od steviloIgralcev naprej) ostanejo prazni = prosta mesta
        int[] vrstniRedPolozajev = seedVrstniRed(velikostMreze);
        Prijava[] mesta = new Prijava[velikostMreze];
        for (int i = 0; i < velikostMreze; i++) {
            int zaporednaStevilka = vrstniRedPolozajev[i]; // 1..velikostMreze
            if (zaporednaStevilka <= steviloIgralcev) {
                mesta[i] = poSeedu.get(zaporednaStevilka - 1);
            }
        }

        // 1. kolo: pari sosednjih mest; kjer je eno mesto prazno, je prost prehod
        List<Tekma> prvoKolo = new ArrayList<>();
        for (int i = 0; i < velikostMreze / 2; i++) {
            Prijava prvi = mesta[2 * i];
            Prijava drugi = mesta[2 * i + 1];

            Tekma tekma = novaTekma(dogodek, FazaTekme.GLAVNI, 1, i + 1);
            tekma.setPrijava1(prvi);
            tekma.setPrijava2(drugi);

            if (prvi != null && drugi != null) {
                tekma.setStatus(StatusTekme.PRIPRAVLJENA);
            } else {
                // prosto mesto: prisotni igralec napreduje brez igranja
                tekma.setStatus(StatusTekme.KONCANA);
                tekma.setIzidTip(IzidTekme.PROSTO);
                tekma.setZmagovalec(prvi != null ? prvi : drugi);
            }
            prvoKolo.add(tekma);
        }
        tekmaRepozitorij.saveAll(prvoKolo); // shranimo, da tekme dobijo id-je

        // visja kola: prazne tekme z eksplicitnimi povezavami na izvorni tekmi
        List<Tekma> vseTekme = new ArrayList<>(prvoKolo);
        List<Tekma> prejsnjeKolo = prvoKolo;
        int steviloKol = (int) (Math.log(velikostMreze) / Math.log(2));

        for (int kolo = 2; kolo <= steviloKol; kolo++) {
            List<Tekma> trenutnoKolo = new ArrayList<>();
            for (int pozicija = 1; pozicija <= prejsnjeKolo.size() / 2; pozicija++) {
                Tekma izvor1 = prejsnjeKolo.get(2 * (pozicija - 1));
                Tekma izvor2 = prejsnjeKolo.get(2 * (pozicija - 1) + 1);

                Tekma tekma = novaTekma(dogodek, FazaTekme.GLAVNI, kolo, pozicija);
                tekma.setIdIzvorTekma1(izvor1.getId());
                tekma.setVlogaIzvora1(VlogaIzvora.ZMAGOVALEC);
                tekma.setIdIzvorTekma2(izvor2.getId());
                tekma.setVlogaIzvora2(VlogaIzvora.ZMAGOVALEC);
                trenutnoKolo.add(tekma);
            }
            tekmaRepozitorij.saveAll(trenutnoKolo);
            vseTekme.addAll(trenutnoKolo);
            prejsnjeKolo = trenutnoKolo;
        }

        prenesiProstePrehode(vseTekme);
        tekmaRepozitorij.saveAll(vseTekme);
        return vseTekme;
    }

    // ---------------------------------------------------------------------
    // KROZNI (vsak z vsakim)
    // ---------------------------------------------------------------------

    private List<Tekma> zrebKrozni(Dogodek dogodek, List<Prijava> prijave) {
        List<Tekma> tekme = kroznaMetoda(dogodek, prijave, null);
        tekmaRepozitorij.saveAll(tekme);
        return tekme;
    }

    /* Krozni sistem z zrebom: vrstni red igralcev doloci nakljucje. */
    private List<Tekma> kroznaMetoda(Dogodek dogodek, List<Prijava> igralci, Long idSkupina) {
        List<Prijava> premesani = new ArrayList<>(igralci);
        Collections.shuffle(premesani, nakljucje);
        return kroznePare(dogodek, premesani, idSkupina);
    }

    /* Krozna ("berger") metoda nad ZE UREJENIM seznamom: v vsakem kolu se prvi
       igralec drzi na mestu, ostali pa se zavrtijo. Pri lihem stevilu doda
       navideznega igralca (prosto) - tisti, ki je z njim v paru, pociva.
       idSkupina je null za cist krozni sistem oz. id skupine v skupinskem.

       Ker vrstni red vstopa nedotaknjen, pri jakostno urejenem seznamu ta
       metoda sama od sebe da zeleni razpored: prvi nosilec v prvem kolu igra
       z najsibkejsim, dvoboj prvih dveh nosilcev pa pade v zadnje kolo. */
    private List<Tekma> kroznePare(Dogodek dogodek, List<Prijava> igralci, Long idSkupina) {
        List<Prijava> krog = new ArrayList<>(igralci);
        boolean liho = krog.size() % 2 != 0;
        if (liho) {
            krog.add(null); // navidezni igralec (prosto)
        }
        int velikost = krog.size();
        int steviloKol = velikost - 1;
        int tekemNaKolo = velikost / 2;

        List<Tekma> tekme = new ArrayList<>();
        for (int kolo = 1; kolo <= steviloKol; kolo++) {
            for (int i = 0; i < tekemNaKolo; i++) {
                Prijava prvi = krog.get(i);
                Prijava drugi = krog.get(velikost - 1 - i);
                if (prvi == null || drugi == null) {
                    continue; // igralec v tem kolu pociva - tekme ni
                }
                Tekma tekma = novaTekma(dogodek, FazaTekme.SKUPINA, kolo, i + 1);
                tekma.setIdSkupina(idSkupina);
                tekma.setPrijava1(prvi);
                tekma.setPrijava2(drugi);
                tekma.setStatus(StatusTekme.PRIPRAVLJENA);
                tekme.add(tekma);
            }
            // zavrti: prvi ostane, zadnji gre na drugo mesto
            krog.add(1, krog.remove(velikost - 1));
        }
        return tekme;
    }

    // ---------------------------------------------------------------------
    // SKUPINE_IZLOCILNI (skupinski del; izlocilni del zgenerira SkupineStoritev)
    // ---------------------------------------------------------------------

    private List<Tekma> zrebSkupine(Dogodek dogodek, List<Prijava> prijave) {
        if (prijave.size() < NAJMANJ_ZA_SKUPINE) {
            throw new DomenskaIzjema("Za sistem skupine+izlocilni je potrebnih vsaj "
                    + NAJMANJ_ZA_SKUPINE + " prijavljenih igralcev (trenutno: " + prijave.size() + ").");
        }

        List<Prijava> premesane = new ArrayList<>(prijave);
        Collections.shuffle(premesane, nakljucje);

        int stSkupin = izberiSteviloSkupin(premesane.size());

        // ustvari skupine (A, B, C ...) in jih shrani, da dobijo id-je
        List<Skupina> skupine = new ArrayList<>();
        for (int i = 0; i < stSkupin; i++) {
            skupine.add(new Skupina(dogodek, String.valueOf((char) ('A' + i))));
        }
        skupinaRepozitorij.saveAll(skupine);

        // razdeli igralce po skupinah v krogu (enakomerne velikosti)
        List<List<Prijava>> poSkupinah = new ArrayList<>();
        for (int i = 0; i < stSkupin; i++) {
            poSkupinah.add(new ArrayList<>());
        }
        for (int i = 0; i < premesane.size(); i++) {
            Prijava prijava = premesane.get(i);
            prijava.setIdSkupina(skupine.get(i % stSkupin).getId());
            poSkupinah.get(i % stSkupin).add(prijava);
        }

        // krozne tekme znotraj vsake skupine; pozicija je enolicna cez ves
        // dogodek (UNIQUE dogodek+faza+kolo+pozicija), zato jo stejemo globalno
        List<Tekma> vse = new ArrayList<>();
        int globalnaPozicija = 1;
        for (int i = 0; i < stSkupin; i++) {
            List<Tekma> tekmeSkupine = kroznaMetoda(dogodek, poSkupinah.get(i), skupine.get(i).getId());
            for (Tekma tekma : tekmeSkupine) {
                tekma.setPozicija(globalnaPozicija++);
                vse.add(tekma);
            }
        }
        tekmaRepozitorij.saveAll(vse);
        return vse;
    }

    // ---------------------------------------------------------------------
    // SKUPINE (format TOP): izbor najboljsih N in zaporedna razporeditev
    // ---------------------------------------------------------------------

    /* Format TOP: igra najboljsih N (N = stevilo skupin x velikost skupine),
       razdeljenih ZAPOREDNO po jakosti - skupina A dobi prvih 8, B naslednjih
       8 in tako naprej. Skupine torej niso uravnotezene, ampak so rangi.
       Kdor ne pride v izbor, postane rezerva in ne igra.

       Izlocilnega dela ni: vsaka skupina ima svojo lestvico in tekmovanje se
       konca po zadnjem kolu. */
    private List<Tekma> zrebSkupinePoJakosti(Dogodek dogodek, List<Prijava> prijave) {
        if (dogodek.getSteviloSkupin() == null || dogodek.getVelikostSkupine() == null) {
            throw new DomenskaIzjema(
                    "Dogodek nima nastavljenega stevila skupin in velikosti skupine.");
        }

        // isti vrstni red, kot ga administrator vidi in ureja v pripravi
        List<Prijava> poJakosti = izborStoritev.vrstniRed(prijave);
        int meja = dogodek.getSteviloSkupin() * dogodek.getVelikostSkupine();
        int igra = Math.min(meja, poJakosti.size());

        List<Integer> velikosti = velikostiSkupin(igra, dogodek.getVelikostSkupine());
        String zadrzek = zadrzekRazreza(velikosti);
        if (zadrzek != null) {
            throw new DomenskaIzjema(zadrzek);
        }

        // Jakostno mesto zabelezimo VSEM - tudi ce ga administrator ni rocno
        // urejal in je obveljal predlog po ratingu. Brez tega po turnirju ne
        // bi bilo vec razvidno, po kaksnem vrstnem redu so nastale skupine in
        // kdo je bil prvi pod crto reza (rating se medtem spreminja).
        int mesto = 1;
        for (Prijava prijava : poJakosti) {
            prijava.setStNosilca(mesto);
            // kdor ni v izboru, ne igra
            if (mesto > igra) {
                prijava.setStatus(Prijava.StatusPrijave.REZERVA);
            }
            mesto++;
        }
        prijavaRepozitorij.saveAll(poJakosti);

        List<Skupina> skupine = new ArrayList<>();
        for (int i = 0; i < velikosti.size(); i++) {
            skupine.add(new Skupina(dogodek, String.valueOf((char) ('A' + i))));
        }
        skupinaRepozitorij.saveAll(skupine);

        // pozicija je enolicna cez ves dogodek (UNIQUE dogodek+faza+kolo+
        // pozicija), zato jo stejemo globalno
        List<Tekma> vse = new ArrayList<>();
        int globalnaPozicija = 1;
        int odMesta = 0;
        for (int i = 0; i < velikosti.size(); i++) {
            List<Prijava> clani = new ArrayList<>(poJakosti.subList(odMesta, odMesta + velikosti.get(i)));
            odMesta += velikosti.get(i);

            Skupina skupina = skupine.get(i);
            for (Prijava clan : clani) {
                clan.setIdSkupina(skupina.getId());
            }
            // clani so ze urejeni po jakosti, zato brez mesanja
            for (Tekma tekma : kroznePare(dogodek, clani, skupina.getId())) {
                tekma.setPozicija(globalnaPozicija++);
                vse.add(tekma);
            }
        }
        tekmaRepozitorij.saveAll(vse);
        return vse;
    }

    /* Velikosti skupin po vrsti: polne skupine najprej, ostanek v zadnjo.
       Pri 20 igralcih in velikosti 8 vrne [8, 8, 4] - najmocnejse skupine so
       vedno polne, manjka le na dnu. */
    public static List<Integer> velikostiSkupin(int steviloIgralcev, int velikostSkupine) {
        List<Integer> velikosti = new ArrayList<>();
        int preostanek = steviloIgralcev;
        while (preostanek >= velikostSkupine) {
            velikosti.add(velikostSkupine);
            preostanek -= velikostSkupine;
        }
        if (preostanek > 0) {
            velikosti.add(preostanek);
        }
        return velikosti;
    }

    /* Zakaj danega razreza ni mogoce odigrati (null = razrez je v redu).
       Isto pravilo uporabljata zreb (zavrne) in predogled v pripravi (opozori),
       da administrator vidi tezavo, se preden klikne zreb. */
    public static String zadrzekRazreza(List<Integer> velikosti) {
        if (velikosti.isEmpty()) {
            return "Za skupinski del sta potrebna vsaj dva igralca.";
        }
        int zadnja = velikosti.get(velikosti.size() - 1);
        if (zadnja < 2) {
            return "Zadnja skupina bi imela enega samega igralca, kar ni tekmovanje."
                    + " Prijavi ali odjavi enega igralca oz. spremeni velikost skupine.";
        }
        return null;
    }

    /* Stevilo skupin tako, da ima vsaka priblizno 4 (in vsaj 3) igralce. */
    static int izberiSteviloSkupin(int steviloIgralcev) {
        int stSkupin = Math.max(2, (int) Math.round(steviloIgralcev / 4.0));
        while (stSkupin > 2 && steviloIgralcev / stSkupin < 3) {
            stSkupin--;
        }
        return stSkupin;
    }

    // ---------------------------------------------------------------------
    // Skupno
    // ---------------------------------------------------------------------

    /* Igralce ob prostih prehodih takoj vpise v tekme 2. kola.
       Tu se ne uporablja NapredovanjeStoritev, ker so vse tekme sele
       nastale v tej transakciji in socasnost ni mozna - neposredni
       vpis v objekte je enostavnejsi in pregleden. */
    private void prenesiProstePrehode(List<Tekma> vseTekme) {
        for (Tekma tekma : vseTekme) {
            if (tekma.getIzidTip() != IzidTekme.PROSTO) {
                continue;
            }
            for (Tekma kandidat : vseTekme) {
                if (tekma.getId().equals(kandidat.getIdIzvorTekma1())) {
                    kandidat.setPrijava1(tekma.getZmagovalec());
                }
                if (tekma.getId().equals(kandidat.getIdIzvorTekma2())) {
                    kandidat.setPrijava2(tekma.getZmagovalec());
                }
                if (kandidat.getStatus() == StatusTekme.CAKA
                        && kandidat.getPrijava1() != null && kandidat.getPrijava2() != null) {
                    kandidat.setStatus(StatusTekme.PRIPRAVLJENA);
                }
            }
        }
    }

    /* V prijave zabelezi trenutni rating igralcev - posnetek za sledljivost. */
    private void zabeleziRatingObZrebu(List<Prijava> prijave) {
        List<Long> idjiIgralcev = prijave.stream().map(p -> p.getIgralec().getId()).toList();
        List<RatingStanje> stanja = ratingStanjeRepozitorij
                .findByIgralecIdInAndSistem(idjiIgralcev, RatingStanje.SISTEM_KLUBSKI_ELO);
        for (Prijava prijava : prijave) {
            stanja.stream()
                    .filter(s -> s.getIgralec().getId().equals(prijava.getIgralec().getId()))
                    .findFirst()
                    .ifPresent(s -> prijava.setRatingObZrebu(s.getVrednost()));
        }
    }

    private Tekma novaTekma(Dogodek dogodek, FazaTekme faza, int kolo, int pozicija) {
        Tekma tekma = new Tekma();
        tekma.setDogodek(dogodek);
        tekma.setFaza(faza);
        tekma.setKolo(kolo);
        tekma.setPozicija(pozicija);
        tekma.setSteviloNizov(dogodek.getPrivzetoSteviloNizov());
        tekma.setStatus(StatusTekme.CAKA);
        return tekma;
    }

    /* Najmanjsa potenca stevila 2, ki je >= n. */
    static int najblizjaPotencaDve(int n) {
        int potenca = 1;
        while (potenca < n) {
            potenca *= 2;
        }
        return potenca;
    }

    /* Standardni "seed" vrstni red polozajev v mrezi.
       Za velikost 8 vrne [1, 8, 4, 5, 2, 7, 3, 6], kar pomeni pare
       1-8, 4-5, 2-7 in 3-6. Ker prosta mesta zasedejo najvisje stevilke,
       so enakomerno razprsena po mrezi in se nikoli ne srecata med sabo. */
    static int[] seedVrstniRed(int velikost) {
        int[] vrstniRed = {1};
        while (vrstniRed.length < velikost) {
            int dvojnaVelikost = vrstniRed.length * 2;
            int[] novi = new int[dvojnaVelikost];
            for (int i = 0; i < vrstniRed.length; i++) {
                novi[2 * i] = vrstniRed[i];
                novi[2 * i + 1] = dvojnaVelikost + 1 - vrstniRed[i];
            }
            vrstniRed = novi;
        }
        return vrstniRed;
    }
}
