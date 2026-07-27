/* Izracun lestvice za krozni sistem in za skupine.

   Merila razvrstitve (po vrsti):
     1. vec zmag,
     2. boljsa razlika nizov (dobljeni - izgubljeni),
     3. medsebojna tekma (zanesljivo razresi izenacenje dveh; pri treh in
        vec izenacenih je le pripomocek),
     4. vec dobljenih nizov,
     5. abecedno po imenu (da je vrstni red vedno enolicen).

   Poenostavljeno glede na polni sistem ITTF (mini-liga med izenacenimi),
   a determinisiticno in za klubske turnirje povsem zadostno. */
package si.turnirko.storitve;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import si.turnirko.dto.VrsticaLestviceDto;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.Tekma;

@Service
public class RazvrstitevStoritev {

    /* Vmesni sestevek uspeha enega udelezenca. */
    private static final class Vmesni {
        final Prijava prijava;
        int odigrane, zmage, porazi, niziZa, niziProti;
        Vmesni(Prijava prijava) { this.prijava = prijava; }
    }

    /* Urejena lestvica danih udelezencev iz njihovih KONCANIH medsebojnih
       tekem. Uposteva samo tekme, kjer sta oba udelezenca s tega seznama. */
    public List<VrsticaLestviceDto> lestvica(List<Prijava> udelezenci, List<Tekma> tekme) {
        Map<Long, Vmesni> po = new LinkedHashMap<>();
        for (Prijava p : udelezenci) {
            po.put(p.getId(), new Vmesni(p));
        }

        // kdo je koga premagal - za razresitev izenacenja z medsebojno tekmo
        Map<Long, Set<Long>> premagal = new HashMap<>();

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
            Vmesni zmag = prviZmagal ? v1 : v2;
            Vmesni por = prviZmagal ? v2 : v1;
            zmag.zmage++;
            por.porazi++;
            premagal.computeIfAbsent(zmag.prijava.getId(), k -> new HashSet<>())
                    .add(por.prijava.getId());
        }

        List<Vmesni> urejeni = new ArrayList<>(po.values());
        urejeni.sort((a, b) -> {
            if (a.zmage != b.zmage) return b.zmage - a.zmage;
            int razA = a.niziZa - a.niziProti;
            int razB = b.niziZa - b.niziProti;
            if (razA != razB) return razB - razA;
            boolean aPremagalB = premagal.getOrDefault(a.prijava.getId(), Set.of())
                    .contains(b.prijava.getId());
            boolean bPremagalA = premagal.getOrDefault(b.prijava.getId(), Set.of())
                    .contains(a.prijava.getId());
            if (aPremagalB && !bPremagalA) return -1;
            if (bPremagalA && !aPremagalB) return 1;
            if (a.niziZa != b.niziZa) return b.niziZa - a.niziZa;
            return a.prijava.getIgralec().polnoIme().compareTo(b.prijava.getIgralec().polnoIme());
        });

        List<VrsticaLestviceDto> rezultat = new ArrayList<>();
        int mesto = 1;
        for (Vmesni v : urejeni) {
            rezultat.add(new VrsticaLestviceDto(
                    v.prijava.getId(),
                    v.prijava.getIgralec().getId(),
                    v.prijava.getIgralec().polnoIme(),
                    v.prijava.getKlubObPrijavi() != null ? v.prijava.getKlubObPrijavi().getIme() : null,
                    v.odigrane, v.zmage, v.porazi, v.niziZa, v.niziProti, mesto++));
        }
        return rezultat;
    }
}
