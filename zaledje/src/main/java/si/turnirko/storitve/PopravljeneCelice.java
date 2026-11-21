/* Celice listka, katerih vrednost je organizator izrecno dolocil (F6.2) - v
   straneh TEKME (1 = prijava1), ne v stolpcih listka.

   Ob potrditvi: celica, v kateri se potrjena vrednost razlikuje od predloga
   bralnika (tudi celica, ki je bralnik ni prebral). Ob popravku izida (F6.1):
   celica, v kateri se nov izid razlikuje od potrjenega. Na takih celicah se je
   mreza zmotila (ali je ni znala prebrati), zato so najdragocenejsi ucni primer
   in pri ucenju stejejo dvojno (StevkaListka.popravljena). */
package si.turnirko.storitve;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import si.turnirko.dto.NizVnos;
import si.turnirko.dto.PredlogIzidaDto;
import si.turnirko.dto.VnosRezultata;
import si.turnirko.modeli.IzidTekme;

public record PopravljeneCelice(Set<Integer> nizi1, Set<Integer> nizi2, boolean rezultat1, boolean rezultat2) {

    public static final PopravljeneCelice NIC = new PopravljeneCelice(Set.of(), Set.of(), false, false);

    public int stevilo() {
        return nizi1.size() + nizi2.size() + (rezultat1 ? 1 : 0) + (rezultat2 ? 1 : 0);
    }

    /* Ali je popravljena celica niza (0 ..) na strani tekme (1 ali 2). */
    public boolean niz(int niz, int stran) {
        return (stran == 1 ? nizi1 : nizi2).contains(niz);
    }

    public boolean rezultat(int stran) {
        return stran == 1 ? rezultat1 : rezultat2;
    }

    /* Ob potrditvi. Brez predloga (bralnik ni dal nicesar) ni mogoce vedeti, kaj je
       organizator popravil: nobena celica ni mocna. */
    public static PopravljeneCelice izPredloga(PredlogIzidaDto predlog, VnosRezultata izid) {
        if (predlog == null || izid == null) {
            return NIC;
        }
        Set<Integer> n1 = new HashSet<>();
        Set<Integer> n2 = new HashSet<>();
        List<NizVnos> nizi = izid.nizi() == null ? List.of() : izid.nizi();
        for (int i = 0; i < nizi.size(); i++) {
            PredlogIzidaDto.NizPredloga p = i < predlog.nizi().size() ? predlog.nizi().get(i) : null;
            if (p == null || !Objects.equals(p.tocke1(), nizi.get(i).tocke1())) {
                n1.add(i);
            }
            if (p == null || !Objects.equals(p.tocke2(), nizi.get(i).tocke2())) {
                n2.add(i);
            }
        }
        return new PopravljeneCelice(Set.copyOf(n1), Set.copyOf(n2),
                !Objects.equals(predlog.dobljeniNizi1(), izid.dobljeniNizi1()),
                !Objects.equals(predlog.dobljeniNizi2(), izid.dobljeniNizi2()));
    }

    /* Ob popravku izida: celice, ki jih je popravek spremenil. null, ce izidov ni
       mogoce primerjati po celicah (posebni izid ali drugo stevilo nizov) - takrat
       listek ne ustreza vec nobeni celici zanesljivo. */
    public static PopravljeneCelice izPopravka(VnosRezultata star, VnosRezultata nov) {
        if (star == null || nov == null || !jeIgrano(star) || !jeIgrano(nov)) {
            return null;
        }
        List<NizVnos> a = star.nizi() == null ? List.of() : star.nizi();
        List<NizVnos> b = nov.nizi() == null ? List.of() : nov.nizi();
        if (a.size() != b.size()) {
            return null;
        }
        Set<Integer> n1 = new HashSet<>();
        Set<Integer> n2 = new HashSet<>();
        for (int i = 0; i < a.size(); i++) {
            if (!Objects.equals(a.get(i).tocke1(), b.get(i).tocke1())) {
                n1.add(i);
            }
            if (!Objects.equals(a.get(i).tocke2(), b.get(i).tocke2())) {
                n2.add(i);
            }
        }
        return new PopravljeneCelice(Set.copyOf(n1), Set.copyOf(n2),
                !Objects.equals(star.dobljeniNizi1(), nov.dobljeniNizi1()),
                !Objects.equals(star.dobljeniNizi2(), nov.dobljeniNizi2()));
    }

    private static boolean jeIgrano(VnosRezultata v) {
        return v.izidTip() == null || v.izidTip() == IzidTekme.IGRANO;
    }
}
