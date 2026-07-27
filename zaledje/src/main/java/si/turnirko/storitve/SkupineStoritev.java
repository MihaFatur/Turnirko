/* Skupinski del sistemov SKUPINE_IZLOCILNI in SKUPINE.

   Po vsaki koncani skupinski tekmi:
   - ce je skupina odigrana do konca, se udelezencem dodeli mesto v skupini
     (za sprotni prikaz lestvice) - to velja za oba sistema,
   - SAMO pri SKUPINE_IZLOCILNI: ko so odigrane VSE skupine, se iz najboljsih dveh vsake skupine
     zgenerira izlocilna mreza. Nosilci so razporejeni navzkrizno, tako da
     se zmagovalec skupine in drugouvrsceni iz iste skupine ne srecata v
     prvem kolu (zmagovalec ob lihem stevilu dobi prosto mesto). */
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

    public SkupineStoritev(SkupinaRepozitorij skupinaRepozitorij,
                           PrijavaRepozitorij prijavaRepozitorij,
                           TekmaRepozitorij tekmaRepozitorij,
                           RazvrstitevStoritev razvrstitevStoritev,
                           ZrebStoritev zrebStoritev) {
        this.skupinaRepozitorij = skupinaRepozitorij;
        this.prijavaRepozitorij = prijavaRepozitorij;
        this.tekmaRepozitorij = tekmaRepozitorij;
        this.razvrstitevStoritev = razvrstitevStoritev;
        this.zrebStoritev = zrebStoritev;
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
       izlocilno mrezo. Zmagovalci skupin so prvi nosilci (dobijo lazje
       polozaje / prosta mesta), drugouvrsceni pa so razporejeni navzkrizno,
       da se ne srecajo s svojim zmagovalcem skupine v prvem kolu. */
    private void zgenerirajIzlocilniDel(Dogodek dogodek, List<Skupina> skupine,
                                        List<Prijava> prijave, List<Tekma> tekme) {
        int g = skupine.size();
        Prijava[] zmagovalci = new Prijava[g];
        Prijava[] drugi = new Prijava[g];

        Map<Long, Prijava> poId = new HashMap<>();
        for (Prijava p : prijave) {
            poId.put(p.getId(), p);
        }

        for (int i = 0; i < g; i++) {
            Skupina skupina = skupine.get(i);
            List<VrsticaLestviceDto> lestvica = razvrstitevStoritev.lestvica(
                    claniSkupine(prijave, skupina.getId()),
                    tekmeSkupine(tekme, skupina.getId()));
            zmagovalci[i] = poId.get(lestvica.get(0).idPrijave());
            drugi[i] = poId.get(lestvica.get(1).idPrijave());
        }

        // seed 1..g = zmagovalci skupin; seed g+1..2g = drugouvrsceni navzkrizno
        Prijava[] poSeedu = new Prijava[2 * g];
        for (int i = 0; i < g; i++) {
            poSeedu[i] = zmagovalci[i]; // seedi 1..g
        }
        for (int i = 1; i <= g; i++) {
            // nosilec-zmagovalec seeda i igra proti drugouvrscenemu druge skupine
            poSeedu[(2 * g + 1 - i) - 1] = drugi[i % g];
        }

        zrebStoritev.zgradiIzlocilnoMrezo(dogodek, new ArrayList<>(List.of(poSeedu)));
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
