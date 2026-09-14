/* Mesta v skupinah in koncna mesta uvozenega dogodka - po istih pravilih kot
   pri dogodku, ki se je odigral v Turnirku (TekmaStoritev.zakljuciDogodek,
   SkupineStoritev). Uvoz storitev ne klice (te bi sprozile napredovanje in
   zreb), pravila pa morajo ostati ista, sicer bi uvozen in zivi dogodek z
   istimi izidi pokazala druga mesta.

   Deli ga uvoz iz Stupe (kadar vir uradnih mest nima) in uvoz stare strani. */
package si.turnirko.uvoz;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import si.turnirko.dto.VrsticaLestviceDto;
import si.turnirko.modeli.FazaTekme;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.SistemTekmovanja;
import si.turnirko.modeli.Skupina;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.Tekma;
import si.turnirko.modeli.VlogaIzvora;
import si.turnirko.storitve.RazvrstitevStoritev;
import si.turnirko.storitve.SkupineStoritev;

public final class KoncnaMestaUvoza {

    private KoncnaMestaUvoza() {}

    /* Mesto v vsaki odigrani predtekmovalni skupini. */
    public static void mestaVSkupinah(List<Skupina> skupine, List<Prijava> prijave, List<Tekma> tekme,
                                      RazvrstitevStoritev razvrstitev) {
        Map<Long, Prijava> poId = poId(prijave);
        for (Skupina sk : skupine) {
            if (sk.getStopnja() != 1) {
                continue;
            }
            List<Tekma> tekmeSkupine = tekmeSkupine(tekme, sk);
            if (tekmeSkupine.isEmpty() || tekmeSkupine.stream().anyMatch(t -> t.getStatus() != StatusTekme.KONCANA)) {
                continue;
            }
            for (VrsticaLestviceDto v : razvrstitev.lestvica(SkupineStoritev.clani(sk, prijave, tekme), tekmeSkupine)) {
                Prijava p = poId.get(v.idPrijave());
                if (p != null) {
                    p.setMestoVSkupini(v.mesto());
                }
            }
        }
    }

    /* Koncna mesta zakljucenega dogodka po pravilu sistema. */
    public static void koncnaMesta(SistemTekmovanja sistem, List<Skupina> skupine, List<Prijava> prijave,
                                   List<Tekma> tekme, RazvrstitevStoritev razvrstitev) {
        Map<Long, Prijava> poId = poId(prijave);
        switch (sistem) {
            case KROZNI -> {
                List<Prijava> udelezenci = prijave.stream()
                        .filter(p -> p.getStatus() == Prijava.StatusPrijave.PRIJAVLJEN).toList();
                for (VrsticaLestviceDto v : razvrstitev.lestvica(udelezenci, tekme)) {
                    Prijava p = poId.get(v.idPrijave());
                    if (p != null) {
                        p.setKoncnoMesto(v.mesto());
                    }
                }
            }
            case IZLOCILNI, SKUPINE_IZLOCILNI -> {
                tekme.stream()
                        .filter(t -> t.getFaza() == FazaTekme.GLAVNI && t.getPozicija() == 1)
                        .max(Comparator.comparingInt(Tekma::getKolo))
                        .ifPresent(finale -> dodeliPar(finale, 1, poId));
                tekme.stream()
                        .filter(t -> t.getFaza() == FazaTekme.TOLAZILNI
                                && t.getVlogaIzvora1() == VlogaIzvora.PORAZENEC
                                && t.getVlogaIzvora2() == VlogaIzvora.PORAZENEC)
                        .findFirst()
                        .ifPresent(tretje -> dodeliPar(tretje, 3, poId));
            }
            case SKUPINE_ZA_MESTA -> {
                for (Skupina sk : skupine) {
                    if (sk.getPrvoMesto() == null) {
                        continue;
                    }
                    for (VrsticaLestviceDto v : razvrstitev.lestvica(SkupineStoritev.clani(sk, prijave, tekme),
                            tekmeSkupine(tekme, sk))) {
                        Prijava p = poId.get(v.idPrijave());
                        if (p != null) {
                            p.setKoncnoMesto(sk.getPrvoMesto() + v.mesto() - 1);
                        }
                    }
                }
            }
            // format TOP: skupine so rangi, skupne razvrstitve ni
            case SKUPINE -> { }
        }
    }

    private static void dodeliPar(Tekma t, int boljseMesto, Map<Long, Prijava> poId) {
        if (t.getStatus() != StatusTekme.KONCANA || t.getZmagovalec() == null) {
            return;
        }
        Prijava zmagovalec = poId.get(t.getZmagovalec().getId());
        Prijava porazenec = t.porazenec() == null ? null : poId.get(t.porazenec().getId());
        if (zmagovalec != null) {
            zmagovalec.setKoncnoMesto(boljseMesto);
        }
        if (porazenec != null) {
            porazenec.setKoncnoMesto(boljseMesto + 1);
        }
    }

    private static List<Tekma> tekmeSkupine(List<Tekma> tekme, Skupina sk) {
        return tekme.stream()
                .filter(t -> t.getFaza() == FazaTekme.SKUPINA && sk.getId().equals(t.getIdSkupina()))
                .toList();
    }

    private static Map<Long, Prijava> poId(List<Prijava> prijave) {
        Map<Long, Prijava> r = new HashMap<>();
        prijave.forEach(p -> r.put(p.getId(), p));
        return r;
    }
}
