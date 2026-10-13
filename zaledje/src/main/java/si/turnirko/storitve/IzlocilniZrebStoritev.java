/* Izlocilna mreza: racunalniski zreb, rocni vpis in predlog.

   IZLOCILNI (en sam izlocilni sistem): zreb je kar zreb dogodka. Organizator ga izvede
   racunalnisko ali pa vpise mreze rocno (ZrebStoritev).

   SKUPINE_IZLOCILNI: izlocilni del NE nastane sam, ko se skupine koncajo. Organizator
   izbere racunalniski zreb ali rocni vpis mreze, dokler se ni zacela nobena tekma
   izlocilnega dela. Zato dogodek dokler je ta odlocitev odprta, ni koncan
   (SkupineStoritev.imaIzlocilniDel). Udelezenci so zmagovalci in drugouvrsceni skupin.

   Ponovitev izbire je popravek do prve tekme izlocilnega dela, kot pri zrebu skupin.
   Skupinske tekme ostanejo nedotaknjene. */
package si.turnirko.storitve;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.IzlocilniPredlogDto;
import si.turnirko.dto.VrsticaLestviceDto;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.FazaTekme;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.SistemTekmovanja;
import si.turnirko.modeli.Skupina;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.Tekma;
import si.turnirko.repozitoriji.DogodekRepozitorij;
import si.turnirko.repozitoriji.PrijavaRepozitorij;
import si.turnirko.repozitoriji.SkupinaRepozitorij;
import si.turnirko.repozitoriji.TekmaRepozitorij;

@Service
public class IzlocilniZrebStoritev {

    private final DogodekRepozitorij dogodekRepozitorij;
    private final PrijavaRepozitorij prijavaRepozitorij;
    private final TekmaRepozitorij tekmaRepozitorij;
    private final SkupinaRepozitorij skupinaRepozitorij;
    private final RazvrstitevStoritev razvrstitevStoritev;
    private final NosilciStoritev nosilci;
    private final ZrebStoritev zrebStoritev;
    private final EkipneTekmeStoritev ekipneTekme;
    private final LastnistvoStoritev lastnistvo;

    public IzlocilniZrebStoritev(DogodekRepozitorij dogodekRepozitorij,
                                 PrijavaRepozitorij prijavaRepozitorij,
                                 TekmaRepozitorij tekmaRepozitorij,
                                 SkupinaRepozitorij skupinaRepozitorij,
                                 RazvrstitevStoritev razvrstitevStoritev,
                                 NosilciStoritev nosilci,
                                 ZrebStoritev zrebStoritev,
                                 EkipneTekmeStoritev ekipneTekme,
                                 LastnistvoStoritev lastnistvo) {
        this.dogodekRepozitorij = dogodekRepozitorij;
        this.prijavaRepozitorij = prijavaRepozitorij;
        this.tekmaRepozitorij = tekmaRepozitorij;
        this.skupinaRepozitorij = skupinaRepozitorij;
        this.razvrstitevStoritev = razvrstitevStoritev;
        this.nosilci = nosilci;
        this.zrebStoritev = zrebStoritev;
        this.ekipneTekme = ekipneTekme;
        this.lastnistvo = lastnistvo;
    }

    /* Racunalniski zreb izlocilne mreze. */
    @Transactional
    public List<Tekma> izvediZreb(Long idDogodka) {
        Dogodek dogodek = najdi(idDogodka);
        if (dogodek.getSistemTekmovanja() == SistemTekmovanja.IZLOCILNI) {
            return zrebStoritev.izvediZreb(idDogodka);
        }
        Udelezenci udelezenci = udelezenci(dogodek);
        List<Prijava> poSeedu = nosilci.vMrezoIzSkupin(udelezenci.prvi(), udelezenci.drugi());
        razveljaviIzlocilni(dogodek);
        return zgradi(dogodek, zrebStoritev.zgradiIzlocilnoMrezo(dogodek, poSeedu));
    }

    /* Predlog mreze za rocni vpis: razporeditev, kot bi jo naredil racunalniski zreb. */
    @Transactional(readOnly = true)
    public IzlocilniPredlogDto predlog(Long idDogodka) {
        Dogodek dogodek = najdi(idDogodka);
        if (dogodek.getSistemTekmovanja() == SistemTekmovanja.IZLOCILNI) {
            return zrebStoritev.predlogIzlocilni(idDogodka);
        }
        Udelezenci udelezenci = udelezenci(dogodek);
        List<Prijava> poSeedu = nosilci.vMrezoIzSkupin(udelezenci.prvi(), udelezenci.drugi());
        return new IzlocilniPredlogDto(
                udelezenci.vsi().stream().map(Prijava::getId).toList(),
                ZrebStoritev.idjiPoMestih(ZrebStoritev.mestaIzSeeda(poSeedu)));
    }

    /* Rocna mreza: organizator vpise, kdo stoji na katerem mestu (null = prosto mesto). */
    @Transactional
    public List<Tekma> rocniZreb(Long idDogodka, List<Long> idjiPoMestih) {
        Dogodek dogodek = najdi(idDogodka);
        if (dogodek.getSistemTekmovanja() == SistemTekmovanja.IZLOCILNI) {
            return zrebStoritev.rocniZrebIzlocilni(idDogodka, idjiPoMestih);
        }
        Udelezenci udelezenci = udelezenci(dogodek);
        var mesta = ZrebStoritev.mestaZaRocniVpis(udelezenci.vsi(), idjiPoMestih);
        razveljaviIzlocilni(dogodek);
        return zgradi(dogodek, zrebStoritev.zgradiIzlocilnoMrezoIzMest(dogodek, mesta));
    }

    private Dogodek najdi(Long idDogodka) {
        lastnistvo.preveriTurnirPoDogodku(idDogodka);
        Dogodek dogodek = dogodekRepozitorij.najdiSTurnirjem(idDogodka)
                .orElseThrow(() -> new NiNajdenoIzjema("Dogodek z id " + idDogodka + " ne obstaja."));
        SistemTekmovanja sistem = dogodek.getSistemTekmovanja();
        if (sistem != SistemTekmovanja.IZLOCILNI && sistem != SistemTekmovanja.SKUPINE_IZLOCILNI) {
            throw new DomenskaIzjema("Izločilna mreža ni na voljo za sistem " + sistem + ".");
        }
        return dogodek;
    }

    /* Ekipna tekma dobi srecanje sele, ko ima obe ekipi; mreza ima prve prehode prazne,
       zato se srecanja zagotovijo po zgraditvi. */
    private List<Tekma> zgradi(Dogodek dogodek, List<Tekma> tekme) {
        if (dogodek.jeEkipno()) {
            ekipneTekme.zagotoviSrecanja(dogodek.getId());
        }
        return tekme;
    }

    /* Popravek izlocilnega dela: stara mreza se zbrise, dokler se nobena njena tekma ni
       zacela. Skupinske tekme ostanejo. */
    private void razveljaviIzlocilni(Dogodek dogodek) {
        List<Tekma> izlocilne = tekmaRepozitorij.najdiZaDogodek(dogodek.getId()).stream()
                .filter(t -> t.getFaza() == FazaTekme.GLAVNI || t.getFaza() == FazaTekme.TOLAZILNI)
                .toList();
        if (izlocilne.isEmpty()) {
            return;
        }
        if (izlocilne.stream().anyMatch(ZrebStoritev::zacet)) {
            throw new DomenskaIzjema("Izločilnega dela ni mogoče ponoviti: nekatere tekme so že začete"
                    + " ali odigrane.");
        }
        if (dogodek.jeEkipno()) {
            for (Tekma tekma : izlocilne) {
                ekipneTekme.odstraniNezacetoSrecanje(tekma.getId());
            }
        }
        zrebStoritev.izbrisiTekme(izlocilne);
    }

    /* Udelezenci izlocilnega dela: zmagovalec in drugouvrsceni vsake skupine, po skupinah
       (A je najmocnejsa). Izlocilni del se da zrebati sele, ko so vse skupinske tekme
       odigrane. */
    private Udelezenci udelezenci(Dogodek dogodek) {
        Long idDogodka = dogodek.getId();
        List<Prijava> prijave = prijavaRepozitorij.najdiZaDogodek(idDogodka);
        List<Tekma> tekme = tekmaRepozitorij.najdiZaDogodek(idDogodka);
        List<Tekma> skupinske = tekme.stream()
                .filter(t -> t.getFaza() == FazaTekme.SKUPINA)
                .toList();
        if (skupinske.isEmpty() || !skupinske.stream().allMatch(t -> t.getStatus() == StatusTekme.KONCANA)) {
            throw new DomenskaIzjema("Izločilni del je mogoče žrebati, ko so odigrane vse skupine.");
        }

        List<Prijava> prvi = new ArrayList<>();
        List<Prijava> drugi = new ArrayList<>();
        for (Skupina skupina : skupinaRepozitorij.findByDogodekIdOrderByOznakaAsc(idDogodka)) {
            if (skupina.getStopnja() != 1) {
                continue;
            }
            List<Tekma> tekmeSkupine = skupinske.stream()
                    .filter(t -> skupina.getId().equals(t.getIdSkupina()))
                    .toList();
            List<VrsticaLestviceDto> lestvica = razvrstitevStoritev.lestvica(
                    SkupineStoritev.clani(skupina, prijave, tekme), tekmeSkupine);
            prvi.add(poId(prijave, lestvica.get(0).idPrijave()));
            drugi.add(poId(prijave, lestvica.get(1).idPrijave()));
        }
        return new Udelezenci(prvi, drugi);
    }

    private static Prijava poId(List<Prijava> prijave, Long id) {
        return prijave.stream().filter(p -> p.getId().equals(id)).findFirst().orElseThrow();
    }

    private record Udelezenci(List<Prijava> prvi, List<Prijava> drugi) {

        List<Prijava> vsi() {
            List<Prijava> vsi = new ArrayList<>(prvi);
            vsi.addAll(drugi);
            return vsi;
        }
    }
}
