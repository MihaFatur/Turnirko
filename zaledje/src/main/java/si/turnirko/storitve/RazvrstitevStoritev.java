/* Izracun lestvice za krozni sistem in za skupine (tudi format TOP).

   Merila razvrstitve (po vrsti):
     1. vec zmag,
     2. manj porazov (loci tistega, ki je odigral manj tekem, od kroga),
     3. KROG - kdor je izenacen po zmagah IN porazih, se razvrsti SAMO po
        tekmah, ki so jih clani kroga odigrali med seboj (PST 20. clen:
        "odloca rezultat medsebojnih dvobojev"):
          a) vec zmag nad clani kroga,
          b) ce je zmag enako, boljsa razlika nizov iz teh tekem,
          c) ce je enaka tudi ta, boljsa razlika tock (le tam, kjer so tocke
             po nizih vnesene),
        po vsakem uspesnem razrezu se postopek PONOVI znotraj vsakega
        nastalega (manjsega) kroga - dvojico tako vedno razsodi njuna
        medsebojna tekma, ker je razlika nizov v njej nujno na eno stran;
     4. sele ce medsebojne tekme ne razsodijo nicesar (npr. sredi skupine, ko
        se se niso igrali): boljsa razlika nizov v celi skupini, vec dobljenih
        nizov, jakostno mesto z zreba (Prijava.stNosilca), abecedno po imenu
        (da je vrstni red vedno enolicen).

   Primer kroga (skupina A, B, C, D; A dobi vse tri tekme, ostali po eno):
     B-C 3:0, C-D 3:1, D-B 3:0  ->  zmage v krogu povsod 1, razlika nizov pa
     B 0, C -1, D +1  ->  vrstni red A, D, B, C.
   Skupna razlika nizov tu NE odloca - steje samo, kar se je zgodilo med
   clani kroga. Zato je lahko drugouvrsceni po SKUPNI razliki nizov slabsi od
   tretjeuvrscenega; to je pravilno in ne napaka izpisa. Prav zato vrstica
   lestvice nosi se `krog`: kdor je bil v krogu, ki ga je razsodil medsebojni
   izkupicek, dobi njegovo zaporedno stevilko, da prikaz to lahko pojasni.

   Jakostno mesto je predzadnje merilo in ne okras: dokler skupina ni zaceta,
   so vsi izenaceni na 0 in lestvica je hkrati IZPIS SKUPINE. Po abecedi bi
   nosilec skupine pristal sredi seznama, pa je po pravilih zreba prvi
   zapisani. */
package si.turnirko.storitve;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.stereotype.Service;

import si.turnirko.dto.VrsticaLestviceDto;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.Tekma;
import si.turnirko.repozitoriji.NizRepozitorij;

@Service
public class RazvrstitevStoritev {

    /* Merila znotraj kroga, po vrsti. */
    private static final int MERILO_ZMAGE = 0;
    private static final int MERILO_NIZI = 1;
    private static final int MERILO_TOCKE = 2;

    private final NizRepozitorij nizRepozitorij;

    public RazvrstitevStoritev(NizRepozitorij nizRepozitorij) {
        this.nizRepozitorij = nizRepozitorij;
    }

    /* Vmesni sestevek uspeha enega udelezenca v CELI skupini. */
    private static final class Vmesni {
        final Prijava prijava;
        int odigrane, zmage, porazi, niziZa, niziProti;
        Vmesni(Prijava prijava) { this.prijava = prijava; }
        Long id() { return prijava.getId(); }
    }

    /* Ena upostevana medsebojna tekma, zozena na to, kar rabi razvrstitev.
       prvi/nizi1 pripadata prijavi1 tekme, drugi/nizi2 pa prijavi2.
       Zmagovalec je zapisan posebej: pri PREDAJI ga iz nizov ni mogoce
       izpeljati, ker ostane delni rezultat (zmagovalec ima lahko manj nizov). */
    private record Dvoboj(Long idTekme, Long prvi, Long drugi,
                          int nizi1, int nizi2, Long idZmagovalca) {}

    /* Zadnja merila - v igro pridejo sele, ko medsebojne tekme ne razsodijo. */
    private static final Comparator<Vmesni> ZADNJA_MERILA = (a, b) -> {
        int razA = a.niziZa - a.niziProti;
        int razB = b.niziZa - b.niziProti;
        if (razA != razB) return razB - razA;
        if (a.niziZa != b.niziZa) return b.niziZa - a.niziZa;
        Integer nosilecA = a.prijava.getStNosilca();
        Integer nosilecB = b.prijava.getStNosilca();
        if (nosilecA != null && nosilecB != null && !nosilecA.equals(nosilecB)) {
            return nosilecA - nosilecB;
        }
        return a.prijava.getIgralec().abecedno().compareTo(b.prijava.getIgralec().abecedno());
    };

    /* Urejena lestvica danih udelezencev iz njihovih KONCANIH medsebojnih
       tekem. Uposteva samo tekme, kjer sta oba udelezenca s tega seznama. */
    public List<VrsticaLestviceDto> lestvica(List<Prijava> udelezenci, List<Tekma> tekme) {
        Map<Long, Vmesni> po = new LinkedHashMap<>();
        for (Prijava p : udelezenci) {
            po.put(p.getId(), new Vmesni(p));
        }

        List<Dvoboj> dvoboji = new ArrayList<>();

        for (Tekma t : tekme) {
            if (t.getStatus() != StatusTekme.KONCANA || t.getZmagovalec() == null) continue;
            if (t.getPrijava1() == null || t.getPrijava2() == null) continue;
            Vmesni v1 = po.get(t.getPrijava1().getId());
            Vmesni v2 = po.get(t.getPrijava2().getId());
            if (v1 == null || v2 == null) continue; // tekma ni znotraj te lestvice

            v1.odigrane++;
            v2.odigrane++;
            v1.niziZa += t.getDobljeniNizi1();
            v1.niziProti += t.getDobljeniNizi2();
            v2.niziZa += t.getDobljeniNizi2();
            v2.niziProti += t.getDobljeniNizi1();

            boolean prviZmagal = t.getZmagovalec().getId().equals(t.getPrijava1().getId());
            (prviZmagal ? v1 : v2).zmage++;
            (prviZmagal ? v2 : v1).porazi++;

            dvoboji.add(new Dvoboj(t.getId(), v1.id(), v2.id(),
                    t.getDobljeniNizi1(), t.getDobljeniNizi2(),
                    prviZmagal ? v1.id() : v2.id()));
        }

        // 1. korak: zmage, nato porazi. Kdor je izenacen v obojem, je "v krogu"
        // in ga naprej razvrsti le se medsebojni izkupicek.
        List<Vmesni> poIzkupicku = new ArrayList<>(po.values());
        poIzkupicku.sort(Comparator.comparingInt((Vmesni v) -> -v.zmage)
                .thenComparingInt(v -> v.porazi));

        Kontekst kontekst = new Kontekst(dvoboji);
        List<Vmesni> urejeni = new ArrayList<>(poIzkupicku.size());
        Map<Long, Integer> krogi = new HashMap<>();
        int zaporednaKroga = 0;
        int zacetek = 0;
        while (zacetek < poIzkupicku.size()) {
            int konec = zacetek + 1;
            while (konec < poIzkupicku.size()
                    && vIstemKrogu(poIzkupicku.get(zacetek), poIzkupicku.get(konec))) {
                konec++;
            }
            // razsodil[0] pove, ali je katero od medsebojnih meril krog res
            // razbilo - samo tak krog je vreden oznake na izpisu
            boolean[] razsodil = new boolean[1];
            List<Vmesni> krog = razvrstiKrog(
                    new ArrayList<>(poIzkupicku.subList(zacetek, konec)),
                    kontekst, MERILO_ZMAGE, razsodil);
            if (razsodil[0]) {
                zaporednaKroga++;
                for (Vmesni v : krog) {
                    krogi.put(v.id(), zaporednaKroga);
                }
            }
            urejeni.addAll(krog);
            zacetek = konec;
        }

        List<VrsticaLestviceDto> rezultat = new ArrayList<>();
        int mesto = 1;
        for (Vmesni v : urejeni) {
            rezultat.add(new VrsticaLestviceDto(
                    v.prijava.getId(),
                    v.prijava.getIgralec().getId(),
                    v.prijava.getIgralec().polnoIme(),
                    v.prijava.getKlubObPrijavi() != null ? v.prijava.getKlubObPrijavi().getIme() : null,
                    v.odigrane, v.zmage, v.porazi, v.niziZa, v.niziProti, mesto++,
                    krogi.get(v.id())));
        }
        return rezultat;
    }

    /* Krog: enako zmag IN enako porazov. */
    private static boolean vIstemKrogu(Vmesni a, Vmesni b) {
        return a.zmage == b.zmage && a.porazi == b.porazi;
    }

    /* Razvrsti clane enega kroga po merilih iz njihovih MEDSEBOJNIH tekem.
       Ko merilo krog razbije, se za vsak nastali manjsi krog postopek zacne
       znova pri prvem merilu (v manjsem krogu so vrednosti druge!). Ko merilo
       ne razbije nicesar, gre na naslednje merilo, na koncu na ZADNJA_MERILA.
       Rekurzija se vedno konca: razbiti kos je strogo manjsi od kroga.
       razsodil[0] se postavi, ce je katero od meril kaj razsodilo. */
    private List<Vmesni> razvrstiKrog(List<Vmesni> krog, Kontekst kontekst, int merilo,
                                      boolean[] razsodil) {
        if (krog.size() <= 1) return krog;
        if (merilo > MERILO_TOCKE) {
            krog.sort(ZADNJA_MERILA);
            return krog;
        }

        Map<Long, Integer> vrednosti = vrednostiVKrogu(krog, kontekst, merilo);

        // kosi po enaki vrednosti, od najboljse navzdol
        TreeMap<Integer, List<Vmesni>> kosi = new TreeMap<>(Comparator.reverseOrder());
        for (Vmesni v : krog) {
            kosi.computeIfAbsent(vrednosti.get(v.id()), k -> new ArrayList<>()).add(v);
        }
        if (kosi.size() == 1) {
            return razvrstiKrog(krog, kontekst, merilo + 1, razsodil); // merilo ni razsodilo
        }

        razsodil[0] = true;
        List<Vmesni> urejeni = new ArrayList<>(krog.size());
        for (List<Vmesni> kos : kosi.values()) {
            urejeni.addAll(razvrstiKrog(kos, kontekst, MERILO_ZMAGE, razsodil));
        }
        return urejeni;
    }

    /* Vrednost merila za vsakega clana kroga, izracunana SAMO iz tekem med
       clani kroga (vec je bolje): stevilo zmag, razlika nizov ali razlika
       tock. Tekma brez vnesenih tock po nizih pri merilu tock odpade -
       upostevajo se tiste, ki jih imajo. */
    private Map<Long, Integer> vrednostiVKrogu(List<Vmesni> krog, Kontekst kontekst, int merilo) {
        Set<Long> vKrogu = new HashSet<>();
        Map<Long, Integer> vrednost = new HashMap<>();
        for (Vmesni v : krog) {
            vKrogu.add(v.id());
            vrednost.put(v.id(), 0);
        }
        for (Dvoboj d : kontekst.dvoboji) {
            if (!vKrogu.contains(d.prvi()) || !vKrogu.contains(d.drugi())) continue;
            if (merilo == MERILO_ZMAGE) {
                vrednost.merge(d.idZmagovalca(), 1, Integer::sum);
                continue;
            }
            int za1;
            int za2;
            if (merilo == MERILO_TOCKE) {
                int[] tocke = kontekst.tocke().get(d.idTekme());
                if (tocke == null) continue; // tocke po nizih niso vnesene
                za1 = tocke[0];
                za2 = tocke[1];
            } else {
                za1 = d.nizi1();
                za2 = d.nizi2();
            }
            vrednost.merge(d.prvi(), za1 - za2, Integer::sum);
            vrednost.merge(d.drugi(), za2 - za1, Integer::sum);
        }
        return vrednost;
    }

    /* Podatki ene lestvice. Tocke po nizih se naloze sele, ce jih razvrstitev
       res potrebuje - razlika nizov v krogu skoraj vedno ze razsodi, zato
       vecina lestvic te poizvedbe sploh ne sprozi. */
    private final class Kontekst {
        final List<Dvoboj> dvoboji;
        private Map<Long, int[]> tockePoTekmi;

        Kontekst(List<Dvoboj> dvoboji) { this.dvoboji = dvoboji; }

        /* id tekme -> [tocke prvega, tocke drugega], sesteto cez vse nize. */
        Map<Long, int[]> tocke() {
            if (tockePoTekmi == null) {
                tockePoTekmi = new HashMap<>();
                List<Long> idji = dvoboji.stream().map(Dvoboj::idTekme).toList();
                if (!idji.isEmpty()) {
                    for (Object[] vrstica : nizRepozitorij.tockeZaTekme(idji)) {
                        int[] sestevek = tockePoTekmi.computeIfAbsent(
                                ((Number) vrstica[0]).longValue(), k -> new int[2]);
                        sestevek[0] += ((Number) vrstica[1]).intValue();
                        sestevek[1] += ((Number) vrstica[2]).intValue();
                    }
                }
            }
            return tockePoTekmi;
        }
    }
}
