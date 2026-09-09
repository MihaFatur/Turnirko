/* Skupinski del sistemov SKUPINE_IZLOCILNI in SKUPINE.

   Po vsaki koncani skupinski tekmi:
   - ce je skupina odigrana do konca, se udelezencem dodeli mesto v skupini
     (za sprotni prikaz lestvice) - to velja za oba sistema,
   - SAMO pri SKUPINE_IZLOCILNI: ko so odigrane VSE skupine, se iz najboljsih
     dveh vsake skupine zgenerira izlocilna mreza. Razporeditev na nosilska
     mesta doloca NosilciStoritev (glej pravila zreba tam). */
package si.turnirko.storitve;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.VrsticaLestviceDto;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.FazaTekme;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.SistemTekmovanja;
import si.turnirko.modeli.Skupina;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.Tekma;
import si.turnirko.repozitoriji.PrijavaRepozitorij;
import si.turnirko.repozitoriji.SkupinaRepozitorij;
import si.turnirko.repozitoriji.TekmaRepozitorij;

@Service
public class SkupineStoritev {

    private final SkupinaRepozitorij skupinaRepozitorij;
    private final PrijavaRepozitorij prijavaRepozitorij;
    private final TekmaRepozitorij tekmaRepozitorij;
    private final RazvrstitevStoritev razvrstitevStoritev;
    private final ZrebStoritev zrebStoritev;
    private final NosilciStoritev nosilci;

    public SkupineStoritev(SkupinaRepozitorij skupinaRepozitorij,
                           PrijavaRepozitorij prijavaRepozitorij,
                           TekmaRepozitorij tekmaRepozitorij,
                           RazvrstitevStoritev razvrstitevStoritev,
                           ZrebStoritev zrebStoritev,
                           NosilciStoritev nosilci) {
        this.skupinaRepozitorij = skupinaRepozitorij;
        this.prijavaRepozitorij = prijavaRepozitorij;
        this.tekmaRepozitorij = tekmaRepozitorij;
        this.razvrstitevStoritev = razvrstitevStoritev;
        this.zrebStoritev = zrebStoritev;
        this.nosilci = nosilci;
    }

    @Transactional
    public void obKoncaniSkupinski(Tekma koncana) {
        Long idDogodka = koncana.getDogodek().getId();
        List<Skupina> skupine = skupinaRepozitorij.findByDogodekIdOrderByOznakaAsc(idDogodka);
        List<Prijava> prijave = prijavaRepozitorij.najdiZaDogodek(idDogodka);
        List<Tekma> tekme = tekmaRepozitorij.najdiZaDogodek(idDogodka);

        // sprotno dodeljevanje mest v vsaki ze zakljuceni skupini
        for (Skupina skupina : skupine) {
            List<Prijava> clani = claniSkupine(prijave, skupina.getId());
            List<Tekma> tekmeSkupine = tekmeSkupine(tekme, skupina.getId());
            boolean skupinaKoncana = !tekmeSkupine.isEmpty() && tekmeSkupine.stream()
                    .allMatch(t -> t.getStatus() == StatusTekme.KONCANA);
            if (skupinaKoncana) {
                List<VrsticaLestviceDto> lestvica = razvrstitevStoritev.lestvica(clani, tekmeSkupine);
                Map<Long, Integer> mestoPoPrijavi = new HashMap<>();
                for (VrsticaLestviceDto vrstica : lestvica) {
                    mestoPoPrijavi.put(vrstica.idPrijave(), vrstica.mesto());
                }
                for (Prijava clan : clani) {
                    clan.setMestoVSkupini(mestoPoPrijavi.get(clan.getId()));
                }
            }
        }

        // Format TOP (sistem SKUPINE) se konca po zadnjem kolu skupin -
        // izlocilnega dela ni in skupne razvrstitve cez skupine ne racunamo,
        // ker so skupine RANGI (A je mocnejsa od B), ne enakovredne skupine.
        if (koncana.getDogodek().getSistemTekmovanja() != SistemTekmovanja.SKUPINE_IZLOCILNI) {
            return;
        }

        boolean vseSkupineKoncane = tekme.stream()
                .filter(t -> t.getFaza() == FazaTekme.SKUPINA)
                .allMatch(t -> t.getStatus() == StatusTekme.KONCANA);
        boolean izlocilniZeObstaja = tekme.stream()
                .anyMatch(t -> t.getFaza() == FazaTekme.GLAVNI);

        if (vseSkupineKoncane && !izlocilniZeObstaja) {
            zgenerirajIzlocilniDel(koncana.getDogodek(), skupine, prijave, tekme);
        }
    }

    /* Iz najboljsih dveh vsake skupine sestavi seznam nosilcev in zgradi
       izlocilno mrezo. Zmagovalec skupine A je 1. nosilec, zmagovalec B 2. in
       tako naprej - skupine so namrec nastale iz jakostnih pasov, zato je
       njihov vrstni red hkrati vrstni red nosilcev. Kam nosilci padejo in kam
       se zrebajo drugouvrsceni, doloca NosilciStoritev. */
    private void zgenerirajIzlocilniDel(Dogodek dogodek, List<Skupina> skupine,
                                        List<Prijava> prijave, List<Tekma> tekme) {
        List<Prijava> zmagovalci = new ArrayList<>();
        List<Prijava> drugi = new ArrayList<>();

        Map<Long, Prijava> poId = new HashMap<>();
        for (Prijava p : prijave) {
            poId.put(p.getId(), p);
        }

        for (Skupina skupina : skupine) {
            List<VrsticaLestviceDto> lestvica = razvrstitevStoritev.lestvica(
                    claniSkupine(prijave, skupina.getId()),
                    tekmeSkupine(tekme, skupina.getId()));
            zmagovalci.add(poId.get(lestvica.get(0).idPrijave()));
            drugi.add(poId.get(lestvica.get(1).idPrijave()));
        }

        zrebStoritev.zgradiIzlocilnoMrezo(dogodek, nosilci.vMrezoIzSkupin(zmagovalci, drugi));
    }

    private List<Prijava> claniSkupine(List<Prijava> prijave, Long idSkupine) {
        return prijave.stream()
                .filter(p -> idSkupine.equals(p.getIdSkupina()))
                .toList();
    }

    private List<Tekma> tekmeSkupine(List<Tekma> tekme, Long idSkupine) {
        return tekme.stream()
                .filter(t -> t.getFaza() == FazaTekme.SKUPINA && idSkupine.equals(t.getIdSkupina()))
                .toList();
    }
}
