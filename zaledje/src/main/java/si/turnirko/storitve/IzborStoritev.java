/* Izbor igralcev in jakostni vrstni red pri sistemu SKUPINE (format TOP).

   Na TOP turnirju se prijavi vec igralcev, kot jih lahko igra: izmed njih se
   izbere najboljsih N (N = stevilo skupin x velikost skupine), ostali ostanejo
   rezerve. Vrstni red odloca vse - po njem tece izbor IN zaporedna
   razporeditev v skupine - zato je edino merilo, ki ga administrator ureja.

   Predlog vrstnega reda naredi sistem po Turnirko ratingu, administrator pa ga
   lahko pred zrebom popravi. Igralci BREZ ratinga gredo na VRH predloga:
   sistem o njih ne ve nicesar, zato jih mora clovek zavestno uvrstiti - ce bi
   jih tiho postavil na dno, bi mocan novinec pristal med rezervami, ne da bi
   kdo opazil.

   Vrstni red se hrani v Prijava.stNosilca (1 = najmocnejsi). Dokler ga
   administrator ne shrani, je stNosilca prazen in velja sprotni predlog. */
package si.turnirko.storitve;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.RatingStanje;
import si.turnirko.modeli.StatusTekmovanja;
import si.turnirko.repozitoriji.DogodekRepozitorij;
import si.turnirko.repozitoriji.KaderEkipeRepozitorij;
import si.turnirko.repozitoriji.PrijavaRepozitorij;
import si.turnirko.repozitoriji.RatingStanjeRepozitorij;

@Service
public class IzborStoritev {

    private final DogodekRepozitorij dogodekRepozitorij;
    private final PrijavaRepozitorij prijavaRepozitorij;
    private final RatingStanjeRepozitorij ratingStanjeRepozitorij;
    private final KaderEkipeRepozitorij kaderRepozitorij;
    private final LastnistvoStoritev lastnistvo;

    public IzborStoritev(DogodekRepozitorij dogodekRepozitorij,
                         PrijavaRepozitorij prijavaRepozitorij,
                         RatingStanjeRepozitorij ratingStanjeRepozitorij,
                         KaderEkipeRepozitorij kaderRepozitorij,
                         LastnistvoStoritev lastnistvo) {
        this.dogodekRepozitorij = dogodekRepozitorij;
        this.prijavaRepozitorij = prijavaRepozitorij;
        this.ratingStanjeRepozitorij = ratingStanjeRepozitorij;
        this.kaderRepozitorij = kaderRepozitorij;
        this.lastnistvo = lastnistvo;
    }

    /* Prijave dogodka v veljavnem jakostnem vrstnem redu.

       Ce je vrstni red ze shranjen (stNosilca), velja ta; sicer velja predlog
       po ratingu. Ker se lahko po shranjevanju kdo se prijavi ali odjavi, sta
       oba vira zdruzena: kdor ima stNosilca, gre po njem, novinci pa se
       razvrstijo za njimi po istem pravilu kot v predlogu. */
    @Transactional(readOnly = true)
    public List<Prijava> vrstniRed(Long idDogodka) {
        return vrstniRed(prijavaRepozitorij.najdiZaDogodekSStatusom(
                idDogodka, Prijava.StatusPrijave.PRIJAVLJEN));
    }

    /* Ista razvrstitev nad ze nalozenim seznamom (da zreb ne poizveduje znova).

       Ekipa ratinga nima, ima pa ga njen kader: predlog jo zato uvrsti po
       povprecju NAJBOLJSIH igralcev kadra - toliko, kolikor jih format
       postavi za mizo (ekipa s tremi mocnimi in desetimi rezervami ni sibkejsa
       od ekipe s tremi mocnimi). Ekipa brez igralca z ratingom gre na vrh,
       iz istega razloga kot igralec brez ratinga. */
    public List<Prijava> vrstniRed(List<Prijava> prijave) {
        Map<Long, Integer> ratingi = ratingi(prijave);
        Map<Long, Integer> ekipni = ratingiEkip(prijave);

        List<Prijava> urejene = new ArrayList<>(prijave);
        urejene.sort(Comparator
                // 1. kdor ima shranjeno mesto, je pred vsemi, ki ga nimajo
                .comparing((Prijava p) -> p.getStNosilca() == null)
                .thenComparing(p -> p.getStNosilca() == null ? 0 : p.getStNosilca())
                // 2. med neuvrscenimi: najprej tisti brez ratinga (glej uvod)
                .thenComparing(p -> jakost(p, ratingi, ekipni) != null)
                // 3. nato po ratingu padajoce
                .thenComparing(Comparator.comparingInt(
                        (Prijava p) -> {
                            Integer r = jakost(p, ratingi, ekipni);
                            return r == null ? 0 : r;
                        }).reversed())
                // 4. abecedno, da je vrstni red vedno enolicen in ponovljiv
                .thenComparing(IzborStoritev::abecedno));
        return urejene;
    }

    private static Integer jakost(Prijava p, Map<Long, Integer> ratingi, Map<Long, Integer> ekipni) {
        return p.jeEkipa() ? ekipni.get(p.getEkipa().getId()) : ratingi.get(p.getIgralec().getId());
    }

    private static String abecedno(Prijava p) {
        return p.jeEkipa() ? p.getEkipa().prikazanoIme() : p.getIgralec().abecedno();
    }

    /* Predlagana jakost ekip: povprecje najboljsih ratingov kadra (toliko
       igralcev, kolikor jih postavi format dogodka). Ekipa brez igralca z
       ratingom v zemljevidu ni. */
    public Map<Long, Integer> ratingiEkip(List<Prijava> prijave) {
        List<Prijava> ekipne = prijave.stream().filter(Prijava::jeEkipa).toList();
        if (ekipne.isEmpty()) {
            return Map.of();
        }
        List<Long> idjiEkip = ekipne.stream().map(p -> p.getEkipa().getId()).toList();
        Map<Long, List<Long>> igralciPoEkipah = new HashMap<>();
        for (Object[] r : kaderRepozitorij.igralciEkip(idjiEkip)) {
            igralciPoEkipah.computeIfAbsent(((Number) r[0]).longValue(), k -> new ArrayList<>())
                    .add(((Number) r[1]).longValue());
        }
        List<Long> vsiIgralci = igralciPoEkipah.values().stream().flatMap(List::stream).distinct().toList();
        Map<Long, Integer> poIgralcu = new HashMap<>();
        if (!vsiIgralci.isEmpty()) {
            for (Object[] vrstica : ratingStanjeRepozitorij.ratingiIgralcev(
                    vsiIgralci, RatingStanje.SISTEM_TURNIRKO)) {
                poIgralcu.put((Long) vrstica[0], (Integer) vrstica[1]);
            }
        }
        /* Format se prebere znova po id-ju: mreza klice to metodo izven
           transakcije, dogodek prijave pa je leni posrednik (id ga ne sprozi). */
        Map<Long, Integer> zaMizoPoDogodku = new HashMap<>();
        for (Dogodek d : dogodekRepozitorij.findAllById(
                ekipne.stream().map(p -> p.getDogodek().getId()).distinct().toList())) {
            zaMizoPoDogodku.put(d.getId(), d.getFormatSrecanja() == null ? 3 : d.getFormatSrecanja().getStIgralcev());
        }
        Map<Long, Integer> po = new HashMap<>();
        for (Prijava p : ekipne) {
            Long idEkipa = p.getEkipa().getId();
            int zaMizo = zaMizoPoDogodku.getOrDefault(p.getDogodek().getId(), 3);
            List<Integer> najboljsi = igralciPoEkipah.getOrDefault(idEkipa, List.of()).stream()
                    .map(poIgralcu::get)
                    .filter(java.util.Objects::nonNull)
                    .sorted(Comparator.reverseOrder())
                    .limit(zaMizo)
                    .toList();
            if (!najboljsi.isEmpty()) {
                po.put(idEkipa, (int) Math.round(najboljsi.stream().mapToInt(Integer::intValue).average().orElse(0)));
            }
        }
        return po;
    }

    /* Trenutni Turnirko rating prijavljenih igralcev (manjka = igralec se nima
       nobene obracunane tekme). Pri dvojicah zajame OBA clana para - vmesnik
       ju v vrstici prijave izpise oba. */
    public Map<Long, Integer> ratingi(List<Prijava> prijave) {
        List<Long> idjiIgralcev = prijave.stream()
                .flatMap(p -> p.igralci().stream())
                .map(si.turnirko.modeli.Igralec::getId)
                .distinct()
                .toList();
        if (idjiIgralcev.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> po = new HashMap<>();
        for (Object[] vrstica : ratingStanjeRepozitorij.ratingiIgralcev(
                idjiIgralcev, RatingStanje.SISTEM_TURNIRKO)) {
            po.put((Long) vrstica[0], (Integer) vrstica[1]);
        }
        return po;
    }

    /* Shrani rocno urejen jakostni vrstni red. Pricakuje VSE prijavljene
       dogodka natanko enkrat - delni seznam bi tiho pustil koga brez mesta,
       zato ga zavrnemo. */
    @Transactional
    public List<Prijava> shraniVrstniRed(Long idDogodka, List<Long> idjiPrijavPoVrsti) {
        lastnistvo.preveriTurnirPoDogodku(idDogodka);
        Dogodek dogodek = najdiDogodek(idDogodka);
        if (dogodek.getStatus() != StatusTekmovanja.PRIPRAVA) {
            throw new DomenskaIzjema(
                    "Jakostni vrstni red je mogoce urejati samo, dokler je dogodek v pripravi.");
        }

        List<Prijava> prijavljeni = prijavaRepozitorij.najdiZaDogodekSStatusom(
                idDogodka, Prijava.StatusPrijave.PRIJAVLJEN);
        Map<Long, Prijava> poId = new HashMap<>();
        for (Prijava p : prijavljeni) {
            poId.put(p.getId(), p);
        }

        Set<Long> videne = new HashSet<>();
        List<Prijava> urejene = new ArrayList<>();
        for (Long idPrijave : idjiPrijavPoVrsti) {
            Prijava prijava = poId.get(idPrijave);
            if (prijava == null) {
                throw new NeveljavenVnosIzjema(
                        "Prijava z id " + idPrijave + " ni prijavljena na ta dogodek.");
            }
            if (!videne.add(idPrijave)) {
                throw new NeveljavenVnosIzjema(
                        "Prijava z id " + idPrijave + " se v vrstnem redu pojavi veckrat.");
            }
            urejene.add(prijava);
        }
        if (videne.size() != prijavljeni.size()) {
            throw new NeveljavenVnosIzjema("Vrstni red mora vsebovati vse prijavljene igralce ("
                    + prijavljeni.size() + "), prejetih pa je " + videne.size() + ".");
        }

        int mesto = 1;
        for (Prijava prijava : urejene) {
            prijava.setStNosilca(mesto++);
        }
        prijavaRepozitorij.saveAll(urejene);
        return urejene;
    }

    private Dogodek najdiDogodek(Long id) {
        return dogodekRepozitorij.najdiSTurnirjem(id)
                .orElseThrow(() -> new NiNajdenoIzjema("Dogodek z id " + id + " ne obstaja."));
    }
}
