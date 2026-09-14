/* Skupinski del sistemov SKUPINE_IZLOCILNI, SKUPINE in SKUPINE_ZA_MESTA.

   Po vsaki koncani skupinski tekmi:
   - ce je predtekmovalna skupina odigrana do konca, se udelezencem dodeli
     mesto v skupini (za sprotni prikaz lestvice) - to velja za vse sisteme,
   - SKUPINE_IZLOCILNI: ko so odigrane VSE skupine, se iz najboljsih dveh vsake
     skupine zgenerira izlocilna mreza. Razporeditev na nosilska mesta doloca
     NosilciStoritev (glej pravila zreba tam),
   - SKUPINE_ZA_MESTA: ko so odigrane vse predtekmovalne skupine, nastanejo
     FINALNE SKUPINE ZA MESTA (PST 14. clen): prvo- in drugouvrsceni vseh
     skupin igrajo za zgornja mesta, tretje- in cetrtouvrsceni za naslednja
     ... V finalno skupino se PRENESE izid dvoboja, ki sta ga ekipi iz iste
     predtekmovalne skupine ze odigrali (tekma.id_prenesena) - ta se ne igra
     znova in nima posamicnih tekem, zato v ratingu in statistiki ne steje
     dvakrat. Koncna mesta so mesta v finalnih skupinah, zamaknjena za prvo
     mesto skupine.

   CLANSTVO: v predtekmovalni skupini ga nosi prijava (id_skupina - zreb), v
   skupini visje stopnje pa se IZPELJE iz tekem skupine. Prijava ima eno samo
   polje skupine; ce bi ga finalna skupina prepisala, bi predtekmovalna
   tabela ostala prazna. Pravilo zivi v clani(), ki ga uporablja tudi stran
   dogodka - dve kopiji bi se razsli. */
package si.turnirko.storitve;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    /* Koliko najboljsih vsake predtekmovalne skupine gre v isto finalno
       skupino (PST: prvi in drugi za 1.-4. mesto, tretji in cetrti za 5.-8.). */
    static final int PAS_FINALNE_SKUPINE = 2;

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

        // sprotno dodeljevanje mest v vsaki ze zakljuceni predtekmovalni skupini
        for (Skupina skupina : skupine) {
            if (skupina.getStopnja() != 1) {
                continue;
            }
            List<Prijava> clani = clani(skupina, prijave, tekme);
            List<Tekma> tekmeSkupine = tekmeSkupine(tekme, skupina.getId());
            if (jeKoncana(tekmeSkupine)) {
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

        SistemTekmovanja sistem = koncana.getDogodek().getSistemTekmovanja();
        List<Tekma> predtekmovanje = tekme.stream()
                .filter(t -> t.getFaza() == FazaTekme.SKUPINA && stopnja(skupine, t) == 1)
                .toList();
        boolean predtekmovanjeKoncano = jeKoncana(predtekmovanje);

        // Format TOP (sistem SKUPINE) se konca po zadnjem kolu skupin -
        // izlocilnega dela ni in skupne razvrstitve cez skupine ne racunamo,
        // ker so skupine RANGI (A je mocnejsa od B), ne enakovredne skupine.
        if (sistem == SistemTekmovanja.SKUPINE_IZLOCILNI) {
            boolean izlocilniZeObstaja = tekme.stream().anyMatch(t -> t.getFaza() == FazaTekme.GLAVNI);
            if (predtekmovanjeKoncano && !izlocilniZeObstaja) {
                zgenerirajIzlocilniDel(koncana.getDogodek(), skupine, prijave, tekme);
            }
        } else if (sistem == SistemTekmovanja.SKUPINE_ZA_MESTA) {
            boolean finalneZeObstajajo = skupine.stream().anyMatch(s -> s.getStopnja() > 1);
            if (predtekmovanjeKoncano && !finalneZeObstajajo) {
                zgenerirajFinalneSkupine(koncana.getDogodek(), skupine, prijave, tekme);
            }
        }
    }

    /* Ali ima dogodek ze finalne skupine za mesta (stopnja 2). */
    @Transactional(readOnly = true)
    public boolean imaFinalneSkupine(Long idDogodka) {
        return skupinaRepozitorij.findByDogodekIdOrderByOznakaAsc(idDogodka).stream()
                .anyMatch(s -> s.getStopnja() > 1);
    }

    /* Koncna mesta iz finalnih skupin: mesto v skupini, zamaknjeno za prvo
       mesto, ki ga skupina odloca. Kdor je ostal sam v svojem pasu, je mesto
       dobil ze ob nastanku finalnih skupin. */
    @Transactional
    public void dodeliMestaIzFinalnihSkupin(Long idDogodka) {
        List<Skupina> skupine = skupinaRepozitorij.findByDogodekIdOrderByOznakaAsc(idDogodka);
        List<Prijava> prijave = prijavaRepozitorij.najdiZaDogodek(idDogodka);
        List<Tekma> tekme = tekmaRepozitorij.najdiZaDogodek(idDogodka);
        Map<Long, Prijava> poId = new HashMap<>();
        for (Prijava p : prijave) {
            poId.put(p.getId(), p);
        }
        for (Skupina skupina : skupine) {
            if (skupina.getPrvoMesto() == null) {
                continue;
            }
            List<VrsticaLestviceDto> lestvica = razvrstitevStoritev.lestvica(
                    clani(skupina, prijave, tekme), tekmeSkupine(tekme, skupina.getId()));
            for (VrsticaLestviceDto vrstica : lestvica) {
                Prijava p = poId.get(vrstica.idPrijave());
                if (p != null) {
                    p.setKoncnoMesto(skupina.getPrvoMesto() + vrstica.mesto() - 1);
                }
            }
        }
        prijavaRepozitorij.saveAll(prijave);
    }

    /* Clani skupine: v predtekmovalni skupini prijave z id_skupina (zreb), v
       vsaki skupini pa se udelezenci njenih tekem - clanstvo v skupini visje
       stopnje nosijo samo tekme (glej uvod). */
    public static List<Prijava> clani(Skupina skupina, List<Prijava> prijave, List<Tekma> tekme) {
        Set<Long> idji = new HashSet<>();
        for (Tekma t : tekme) {
            if (t.getFaza() != FazaTekme.SKUPINA || !skupina.getId().equals(t.getIdSkupina())) {
                continue;
            }
            if (t.getPrijava1() != null) {
                idji.add(t.getPrijava1().getId());
            }
            if (t.getPrijava2() != null) {
                idji.add(t.getPrijava2().getId());
            }
        }
        return prijave.stream()
                .filter(p -> skupina.getId().equals(p.getIdSkupina()) || idji.contains(p.getId()))
                .toList();
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
            if (skupina.getStopnja() != 1) {
                continue;
            }
            List<VrsticaLestviceDto> lestvica = razvrstitevStoritev.lestvica(
                    clani(skupina, prijave, tekme),
                    tekmeSkupine(tekme, skupina.getId()));
            zmagovalci.add(poId.get(lestvica.get(0).idPrijave()));
            drugi.add(poId.get(lestvica.get(1).idPrijave()));
        }

        zrebStoritev.zgradiIzlocilnoMrezo(dogodek, nosilci.vMrezoIzSkupin(zmagovalci, drugi));
    }

    /* Finalne skupine za mesta. Pas b (0, 1, ...) zdruzi mesta b*2+1 in b*2+2
       vseh predtekmovalnih skupin; skupina pasu odloca mesta od prvega
       prostega naprej. Razpored v skupini je krozna metoda nad clani, urejenimi
       po (mesto, skupina) - dvoboji ekip iz iste predtekmovalne skupine pri
       tem ne nastanejo znova, ampak se prenesejo. Pas z eno samo ekipo
       skupine nima: ekipa je svoje mesto ze dosegla. */
    private void zgenerirajFinalneSkupine(Dogodek dogodek, List<Skupina> skupine,
                                          List<Prijava> prijave, List<Tekma> tekme) {
        List<Skupina> predtekmovalne = skupine.stream().filter(s -> s.getStopnja() == 1).toList();
        Map<Skupina, List<Prijava>> uvrstitve = new LinkedHashMap<>();
        Map<Long, Long> predtekmovalnaPrijave = new HashMap<>();
        Map<Long, Prijava> poId = new HashMap<>();
        for (Prijava p : prijave) {
            poId.put(p.getId(), p);
        }
        int najvec = 0;
        for (Skupina skupina : predtekmovalne) {
            List<Prijava> poVrsti = new ArrayList<>();
            for (VrsticaLestviceDto v : razvrstitevStoritev.lestvica(
                    clani(skupina, prijave, tekme), tekmeSkupine(tekme, skupina.getId()))) {
                Prijava p = poId.get(v.idPrijave());
                poVrsti.add(p);
                predtekmovalnaPrijave.put(p.getId(), skupina.getId());
            }
            uvrstitve.put(skupina, poVrsti);
            najvec = Math.max(najvec, poVrsti.size());
        }

        int pozicija = tekme.stream()
                .filter(t -> t.getFaza() == FazaTekme.SKUPINA)
                .mapToInt(Tekma::getPozicija).max().orElse(0) + 1;
        int prvoMesto = 1;
        List<Tekma> nove = new ArrayList<>();
        for (int pas = 0; pas * PAS_FINALNE_SKUPINE < najvec; pas++) {
            List<Prijava> clani = new ArrayList<>();
            for (int rang = pas * PAS_FINALNE_SKUPINE; rang < (pas + 1) * PAS_FINALNE_SKUPINE; rang++) {
                for (List<Prijava> poVrsti : uvrstitve.values()) {
                    if (rang < poVrsti.size()) {
                        clani.add(poVrsti.get(rang));
                    }
                }
            }
            if (clani.size() == 1) {
                clani.get(0).setKoncnoMesto(prvoMesto);
                prvoMesto++;
                continue;
            }
            int zadnjeMesto = prvoMesto + clani.size() - 1;
            Skupina finalna = new Skupina(dogodek, "M" + prvoMesto);
            finalna.setStopnja(2);
            finalna.setIme(prvoMesto + ".–" + zadnjeMesto + ". mesto");
            finalna.setPrvoMesto(prvoMesto);
            finalna = skupinaRepozitorij.save(finalna);

            for (Tekma par : zrebStoritev.kroznePareFinalneSkupine(dogodek, clani, finalna.getId())) {
                par.setPozicija(pozicija++);
                Long skupina1 = predtekmovalnaPrijave.get(par.getPrijava1().getId());
                Long skupina2 = predtekmovalnaPrijave.get(par.getPrijava2().getId());
                if (skupina1 != null && skupina1.equals(skupina2)) {
                    prenesiIzid(par, tekme);
                }
                nove.add(par);
            }
            prvoMesto = zadnjeMesto + 1;
        }
        tekmaRepozitorij.saveAll(nove);
        prijavaRepozitorij.saveAll(prijave);
    }

    /* Tekma finalne skupine prevzame izid dvoboja iz predtekmovanja: isti
       zmagovalec, dobljene tekme (nizi) na pravi strani in kazalec na prvotno
       tekmo. Ne igra se, zato je takoj koncana. */
    private static void prenesiIzid(Tekma nova, List<Tekma> tekme) {
        Long a = nova.getPrijava1().getId();
        Long b = nova.getPrijava2().getId();
        Tekma prvotna = tekme.stream()
                .filter(t -> t.getFaza() == FazaTekme.SKUPINA && !t.jePrenesena()
                        && t.getPrijava1() != null && t.getPrijava2() != null
                        && ((t.getPrijava1().getId().equals(a) && t.getPrijava2().getId().equals(b))
                            || (t.getPrijava1().getId().equals(b) && t.getPrijava2().getId().equals(a))))
                .max(Comparator.comparingLong(Tekma::getId))
                .orElse(null);
        if (prvotna == null || prvotna.getStatus() != StatusTekme.KONCANA) {
            return; // ekipi se v predtekmovanju nista srecali - tekma se odigra
        }
        boolean enakaSmer = prvotna.getPrijava1().getId().equals(a);
        nova.setDobljeniNizi1(enakaSmer ? prvotna.getDobljeniNizi1() : prvotna.getDobljeniNizi2());
        nova.setDobljeniNizi2(enakaSmer ? prvotna.getDobljeniNizi2() : prvotna.getDobljeniNizi1());
        nova.setZmagovalec(prvotna.getZmagovalec());
        nova.setIzidTip(prvotna.getIzidTip());
        nova.setStatus(StatusTekme.KONCANA);
        nova.setIdPrenesena(prvotna.getId());
    }

    private static int stopnja(List<Skupina> skupine, Tekma t) {
        return skupine.stream()
                .filter(s -> s.getId().equals(t.getIdSkupina()))
                .map(Skupina::getStopnja)
                .findFirst().orElse(1);
    }

    private static boolean jeKoncana(List<Tekma> tekme) {
        return !tekme.isEmpty() && tekme.stream().allMatch(t -> t.getStatus() == StatusTekme.KONCANA);
    }

    private static List<Tekma> tekmeSkupine(List<Tekma> tekme, Long idSkupine) {
        return tekme.stream()
                .filter(t -> t.getFaza() == FazaTekme.SKUPINA && idSkupine.equals(t.getIdSkupina()))
                .toList();
    }
}
