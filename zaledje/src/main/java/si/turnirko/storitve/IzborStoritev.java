/* Izbor igralcev in jakostni vrstni red pri sistemu SKUPINE (format TOP).

   Na TOP turnirju se prijavi vec igralcev, kot jih lahko igra: izmed njih se
   izbere najboljsih N (N = stevilo skupin x velikost skupine), ostali ostanejo
   rezerve. Vrstni red odloca vse - po njem tece izbor IN zaporedna
   razporeditev v skupine - zato je edino merilo, ki ga administrator ureja.

   Predlog vrstnega reda naredi sistem po klubskem ELO, administrator pa ga
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
import si.turnirko.repozitoriji.PrijavaRepozitorij;
import si.turnirko.repozitoriji.RatingStanjeRepozitorij;

@Service
public class IzborStoritev {

    private final DogodekRepozitorij dogodekRepozitorij;
    private final PrijavaRepozitorij prijavaRepozitorij;
    private final RatingStanjeRepozitorij ratingStanjeRepozitorij;
    private final LastnistvoStoritev lastnistvo;

    public IzborStoritev(DogodekRepozitorij dogodekRepozitorij,
                         PrijavaRepozitorij prijavaRepozitorij,
                         RatingStanjeRepozitorij ratingStanjeRepozitorij,
                         LastnistvoStoritev lastnistvo) {
        this.dogodekRepozitorij = dogodekRepozitorij;
        this.prijavaRepozitorij = prijavaRepozitorij;
        this.ratingStanjeRepozitorij = ratingStanjeRepozitorij;
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

    /* Ista razvrstitev nad ze nalozenim seznamom (da zreb ne poizveduje znova). */
    public List<Prijava> vrstniRed(List<Prijava> prijave) {
        Map<Long, Integer> ratingi = ratingi(prijave);

        List<Prijava> urejene = new ArrayList<>(prijave);
        urejene.sort(Comparator
                // 1. kdor ima shranjeno mesto, je pred vsemi, ki ga nimajo
                .comparing((Prijava p) -> p.getStNosilca() == null)
                .thenComparing(p -> p.getStNosilca() == null ? 0 : p.getStNosilca())
                // 2. med neuvrscenimi: najprej tisti brez ratinga (glej uvod)
                .thenComparing(p -> ratingi.get(p.getIgralec().getId()) != null)
                // 3. nato po ratingu padajoce
                .thenComparing(Comparator.comparingInt(
                        (Prijava p) -> ratingi.getOrDefault(p.getIgralec().getId(), 0)).reversed())
                // 4. abecedno, da je vrstni red vedno enolicen in ponovljiv
                .thenComparing(p -> p.getIgralec().polnoIme()));
        return urejene;
    }

    /* Trenutni klubski ELO prijavljenih igralcev (manjka = igralec se nima
       nobene obracunane tekme). */
    public Map<Long, Integer> ratingi(List<Prijava> prijave) {
        List<Long> idjiIgralcev = prijave.stream().map(p -> p.getIgralec().getId()).toList();
        if (idjiIgralcev.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> po = new HashMap<>();
        for (Object[] vrstica : ratingStanjeRepozitorij.ratingiIgralcev(
                idjiIgralcev, RatingStanje.SISTEM_KLUBSKI_ELO)) {
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
