/* Obracun ratinga po koncani tekmi (turnirski ali ligaski).
   Varovalka pred dvojnim obracunom: ce v dnevniku (rating_zgodovina)
   za tekmo ze obstaja zapis, se obracun preskoci. Vsaka sprememba
   ratinga ima torej svoj zapis v dnevniku - stanje je vedno sledljivo.

   Klicalec (TekmaStoritev / SrecanjeStoritev) odloca, KDAJ se rating obracuna
   (npr. brez boja in diskvalifikacija ne stejeta, dvojice v ligi ne stejejo);
   ta storitev le izracuna in zapise spremembo. */
package si.turnirko.storitve;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.RatingStanje;
import si.turnirko.modeli.RatingZgodovina;
import si.turnirko.modeli.StranEkipe;
import si.turnirko.modeli.Tekma;
import si.turnirko.modeli.TekmaSrecanja;
import si.turnirko.modeli.TipTekmeSrecanja;
import si.turnirko.repozitoriji.RatingStanjeRepozitorij;
import si.turnirko.repozitoriji.RatingZgodovinaRepozitorij;

@Service
public class RatingStoritev {

    private final RatingStanjeRepozitorij stanjeRepozitorij;
    private final RatingZgodovinaRepozitorij zgodovinaRepozitorij;
    private final EloStoritev eloStoritev;

    public RatingStoritev(RatingStanjeRepozitorij stanjeRepozitorij,
                          RatingZgodovinaRepozitorij zgodovinaRepozitorij,
                          EloStoritev eloStoritev) {
        this.stanjeRepozitorij = stanjeRepozitorij;
        this.zgodovinaRepozitorij = zgodovinaRepozitorij;
        this.eloStoritev = eloStoritev;
    }

    /* Obracuna klubski ELO za koncano turnirsko tekmo (samo enkrat na tekmo). */
    @Transactional
    public void obracunajKlubskiElo(Tekma tekma) {
        if (zgodovinaRepozitorij.existsByTekmaId(tekma.getId())) {
            return; // tekma je ze obracunana
        }
        if (tekma.getZmagovalec() == null || tekma.getPrijava1() == null || tekma.getPrijava2() == null) {
            return;
        }

        Igralec igralec1 = tekma.getPrijava1().getIgralec();
        Igralec igralec2 = tekma.getPrijava2().getIgralec();
        boolean zmagalPrvi = tekma.getZmagovalec().getId().equals(tekma.getPrijava1().getId());

        EloStoritev.IzracunElo izracun = posodobiStanja(igralec1, igralec2, zmagalPrvi,
                tekma.getDobljeniNizi1(), tekma.getDobljeniNizi2(), tekma.getSteviloNizov());

        zgodovinaRepozitorij.save(new RatingZgodovina(
                igralec1, RatingStanje.SISTEM_KLUBSKI_ELO, tekma,
                izracun.sprememba1(), izracun.ratingPo1()));
        zgodovinaRepozitorij.save(new RatingZgodovina(
                igralec2, RatingStanje.SISTEM_KLUBSKI_ELO, tekma,
                izracun.sprememba2(), izracun.ratingPo2()));
    }

    /* Obracuna klubski ELO za posamicno tekmo ligaskega srecanja.
       Dvojice ne stejejo (dva igralca na strani nimata enega ratinga). */
    @Transactional
    public void obracunajZaLigasko(TekmaSrecanja tekma) {
        if (tekma.getTip() != TipTekmeSrecanja.POSAMICNA) {
            return; // dvojice ne stejejo v ELO
        }
        if (tekma.getZmagovalecStran() == null
                || tekma.getIgralecDomaci() == null || tekma.getIgralecGost() == null) {
            return;
        }
        if (zgodovinaRepozitorij.existsByTekmaSrecanjaId(tekma.getId())) {
            return; // ze obracunana
        }

        Igralec domaci = tekma.getIgralecDomaci();
        Igralec gost = tekma.getIgralecGost();
        boolean zmagalDomaci = tekma.getZmagovalecStran() == StranEkipe.DOMACI;

        EloStoritev.IzracunElo izracun = posodobiStanja(domaci, gost, zmagalDomaci,
                tekma.getDobljeniNiziDomaci(), tekma.getDobljeniNiziGost(), tekma.getSteviloNizov());

        zgodovinaRepozitorij.save(new RatingZgodovina(
                domaci, RatingStanje.SISTEM_KLUBSKI_ELO, tekma,
                izracun.sprememba1(), izracun.ratingPo1()));
        zgodovinaRepozitorij.save(new RatingZgodovina(
                gost, RatingStanje.SISTEM_KLUBSKI_ELO, tekma,
                izracun.sprememba2(), izracun.ratingPo2()));
    }

    /* Postavitveni (zacetni) rating: admin igralcu doloci vstopni klubski ELO,
       da mocnemu novincu ni treba ~15 tekem plezati z 1000. Dovoljeno LE dokler
       igralec nima nobene odigrane ratinske tekme - kasneje rating dolocajo
       samo rezultati. Zabelezi se v dnevnik kot zapis brez tekme. */
    @Transactional
    public void nastaviZacetniRating(Igralec igralec, int vrednost) {
        if (vrednost < EloStoritev.NAJNIZJI_ZACETNI || vrednost > EloStoritev.NAJVISJI_ZACETNI) {
            throw new NeveljavenVnosIzjema("Zacetni rating mora biti med "
                    + EloStoritev.NAJNIZJI_ZACETNI + " in " + EloStoritev.NAJVISJI_ZACETNI + ".");
        }
        RatingStanje stanje = stanjeRepozitorij
                .findByIgralecIdAndSistem(igralec.getId(), RatingStanje.SISTEM_KLUBSKI_ELO)
                .orElse(null);
        if (stanje != null && stanje.getStTekem() > 0) {
            throw new DomenskaIzjema(
                    "Zacetni rating je mogoce postaviti le igralcu, ki se ni odigral nobene tekme.");
        }
        // pri prvi postavitvi ni spremembe (igralec ni imel prejsnjega ratinga);
        // pri ponovni postavitvi (pred prvo tekmo) zabelezimo razliko od prejsnje
        int staro = (stanje != null) ? stanje.getVrednost() : vrednost;
        if (stanje == null) {
            stanje = new RatingStanje(igralec, RatingStanje.SISTEM_KLUBSKI_ELO, vrednost);
        } else {
            stanje.setVrednost(vrednost);
        }
        stanjeRepozitorij.save(stanje);
        zgodovinaRepozitorij.save(new RatingZgodovina(
                igralec, RatingStanje.SISTEM_KLUBSKI_ELO, vrednost - staro, vrednost));
    }

    /* Izracuna in shrani novi stanji obeh igralcev; vrne izracun za dnevnik. */
    private EloStoritev.IzracunElo posodobiStanja(Igralec prvi, Igralec drugi, boolean zmagalPrvi,
                                                  int nizi1, int nizi2, int steviloNizov) {
        RatingStanje stanje1 = najdiAliUstvari(prvi);
        RatingStanje stanje2 = najdiAliUstvari(drugi);

        EloStoritev.IzracunElo izracun = eloStoritev.izracunaj(
                stanje1.getVrednost(), stanje2.getVrednost(),
                stanje1.getStTekem(), stanje2.getStTekem(),
                zmagalPrvi, nizi1, nizi2, steviloNizov);

        stanje1.setVrednost(izracun.ratingPo1());
        stanje1.povecajStTekem();
        stanje2.setVrednost(izracun.ratingPo2());
        stanje2.povecajStTekem();
        stanjeRepozitorij.save(stanje1);
        stanjeRepozitorij.save(stanje2);
        return izracun;
    }

    /* Vrne obstojece stanje ratinga ali ustvari novo z zacetno vrednostjo. */
    private RatingStanje najdiAliUstvari(Igralec igralec) {
        return stanjeRepozitorij
                .findByIgralecIdAndSistem(igralec.getId(), RatingStanje.SISTEM_KLUBSKI_ELO)
                .orElseGet(() -> new RatingStanje(
                        igralec, RatingStanje.SISTEM_KLUBSKI_ELO, EloStoritev.ZACETNI_RATING));
    }
}
