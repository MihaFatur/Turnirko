/* Obracun Turnirko ratinga po koncani tekmi (turnirski ali ligaski).
   Varovalka pred dvojnim obracunom: ce v dnevniku (rating_zgodovina)
   za tekmo ze obstaja zapis, se obracun preskoci. Vsaka sprememba
   ratinga ima torej svoj zapis v dnevniku - stanje je vedno sledljivo.

   Klicalec (TekmaStoritev / SrecanjeStoritev) odloca, KDAJ se rating obracuna
   (npr. brez boja in diskvalifikacija ne stejeta, dvojice v ligi ne stejejo);
   ta storitev le izracuna in zapise spremembo. */
package si.turnirko.storitve;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.IzidTekme;
import si.turnirko.modeli.RatingStanje;
import si.turnirko.modeli.RatingZgodovina;
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.modeli.Srecanje;
import si.turnirko.modeli.StranEkipe;
import si.turnirko.modeli.Tekma;
import si.turnirko.modeli.TekmaSrecanja;
import si.turnirko.modeli.TipTekmeSrecanja;
import si.turnirko.repozitoriji.RatingStanjeRepozitorij;
import si.turnirko.repozitoriji.RatingZgodovinaRepozitorij;

@Service
public class RatingStoritev {

    /* Kaj je obracun tekme naredil obema stranema. Poleg izracuna nosi se,
       ali je bila stran UVRSCENA (novinec v obdobju uvrstitve): taki strani se
       rating ni sestel po korakih, ampak izracunal znova iz vseh izidov prvega
       dne, zato sestavine spremembe (K, margina, teza, pricakovano) zanjo ne
       veljajo in gredo v dnevnik prazne. */
    private record Obracun(TurnirkoRatingStoritev.Izracun izracun,
                           boolean uvrscen1, boolean uvrscen2) {}

    private final RatingStanjeRepozitorij stanjeRepozitorij;
    private final RatingZgodovinaRepozitorij zgodovinaRepozitorij;
    private final TurnirkoRatingStoritev turnirkoRating;
    private final SidroStoritev sidroStoritev;
    private final NeaktivnostStoritev neaktivnostStoritev;

    public RatingStoritev(RatingStanjeRepozitorij stanjeRepozitorij,
                          RatingZgodovinaRepozitorij zgodovinaRepozitorij,
                          TurnirkoRatingStoritev turnirkoRating,
                          SidroStoritev sidroStoritev,
                          NeaktivnostStoritev neaktivnostStoritev) {
        this.stanjeRepozitorij = stanjeRepozitorij;
        this.zgodovinaRepozitorij = zgodovinaRepozitorij;
        this.turnirkoRating = turnirkoRating;
        this.sidroStoritev = sidroStoritev;
        this.neaktivnostStoritev = neaktivnostStoritev;
    }

    /* Obracuna Turnirko rating za koncano turnirsko tekmo (samo enkrat na tekmo).
       Dvojice ne stejejo - izida para ni mogoce pripisati posamezniku (isto
       pravilo kot pri ligaskih dvojicah spodaj). */
    @Transactional
    public void obracunajZaTurnirsko(Tekma tekma) {
        if (tekma.getDogodek().jeDvojice()) {
            return;
        }
        // ekipna tekma nima igralcev (steje njeno srecanje), prenesen izid pa
        // je dvoboj, ki je bil obracunan ze v predtekmovanju
        if (tekma.getDogodek().jeEkipno() || tekma.jePrenesena()) {
            return;
        }
        RavenTekmovanja raven = tekma.getDogodek().getTurnir().getRaven();
        if (!raven.steje()) {
            return; // prijateljski turnir: tekme se ne obracunajo
        }
        if (zgodovinaRepozitorij.existsByTekmaId(tekma.getId())) {
            return; // tekma je ze obracunana
        }
        if (tekma.getZmagovalec() == null || tekma.getPrijava1() == null || tekma.getPrijava2() == null) {
            return;
        }

        Igralec igralec1 = tekma.getPrijava1().getIgralec();
        Igralec igralec2 = tekma.getPrijava2().getIgralec();
        boolean zmagalPrvi = tekma.getZmagovalec().getId().equals(tekma.getPrijava1().getId());
        LocalDateTime veljaOb = casTurnirskeTekme(tekma);

        Obracun obracun = posodobiStanja(igralec1, igralec2, zmagalPrvi,
                tekma.getDobljeniNizi1(), tekma.getDobljeniNizi2(), tekma.getSteviloNizov(),
                IzidTekme.samoZmagovalec(tekma.getIzidTip(), tekma.getDobljeniNizi1(), tekma.getDobljeniNizi2()),
                veljaOb, raven.getTeza());
        TurnirkoRatingStoritev.Izracun izracun = obracun.izracun();

        zgodovinaRepozitorij.save(razcleni(new RatingZgodovina(
                igralec1, RatingStanje.SISTEM_TURNIRKO, tekma,
                izracun.sprememba1(), izracun.ratingPo1(), veljaOb, zmagalPrvi),
                izracun, true, obracun.uvrscen1()));
        zgodovinaRepozitorij.save(razcleni(new RatingZgodovina(
                igralec2, RatingStanje.SISTEM_TURNIRKO, tekma,
                izracun.sprememba2(), izracun.ratingPo2(), veljaOb, !zmagalPrvi),
                izracun, false, obracun.uvrscen2()));
    }

    /* Obracuna Turnirko rating za posamicno tekmo ligaskega srecanja.
       Dvojice ne stejejo (dva igralca na strani nimata enega ratinga). */
    @Transactional
    public void obracunajZaLigasko(TekmaSrecanja tekma) {
        if (tekma.getTip() != TipTekmeSrecanja.POSAMICNA) {
            return; // dvojice ne stejejo v rating
        }
        // pri ligi raven lige, pri ekipni tekmi turnirja raven turnirja
        RavenTekmovanja raven = tekma.getSrecanje().pravila().raven();
        if (!raven.steje()) {
            return; // tekmovanje, ki ne steje v rating
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
        LocalDateTime veljaOb = casLigaskeTekme(tekma);

        Obracun obracun = posodobiStanja(domaci, gost, zmagalDomaci,
                tekma.getDobljeniNiziDomaci(), tekma.getDobljeniNiziGost(), tekma.getSteviloNizov(),
                IzidTekme.samoZmagovalec(tekma.getIzidTip(), tekma.getDobljeniNiziDomaci(), tekma.getDobljeniNiziGost()),
                veljaOb, raven.getTeza());
        TurnirkoRatingStoritev.Izracun izracun = obracun.izracun();

        zgodovinaRepozitorij.save(razcleni(new RatingZgodovina(
                domaci, RatingStanje.SISTEM_TURNIRKO, tekma,
                izracun.sprememba1(), izracun.ratingPo1(), veljaOb, zmagalDomaci),
                izracun, true, obracun.uvrscen1()));
        zgodovinaRepozitorij.save(razcleni(new RatingZgodovina(
                gost, RatingStanje.SISTEM_TURNIRKO, tekma,
                izracun.sprememba2(), izracun.ratingPo2(), veljaOb, !zmagalDomaci),
                izracun, false, obracun.uvrscen2()));
    }

    /* Zapisu doda sestavine spremembe - razen ce je bila stran uvrscena, kjer
       sprememba ni nastala po obrazcu K x margina x teza in bi zapisane
       sestavine lagale. */
    private static RatingZgodovina razcleni(RatingZgodovina zapis,
                                            TurnirkoRatingStoritev.Izracun izracun,
                                            boolean prvaStran, boolean uvrscen) {
        if (uvrscen) {
            return zapis;
        }
        return zapis.zRazclenitvijo(
                prvaStran ? izracun.k1() : izracun.k2(),
                izracun.margina(),
                izracun.teza(),
                prvaStran ? izracun.pricakovanaVerjetnost1()
                        : 1.0 - izracun.pricakovanaVerjetnost1());
    }

    /* Kdaj turnirska tekma VELJA: vir pove samo dan tekmovanja, zato polnoc -
       isto pravilo kot pri razvrscanju (glej VrstaRatinskeTekme). */
    public static LocalDateTime casTurnirskeTekme(Tekma tekma) {
        LocalDate datum = tekma.getDogodek().getTurnir().getDatumZacetka();
        return datum == null ? VrstaRatinskeTekme.BREZ_DATUMA : datum.atStartOfDay();
    }

    /* Kdaj tekma srecanja VELJA: pri ligi cas odigranega srecanja, sicer
       predvideni termin. Ekipna tekma turnirja velja na dan turnirja ob
       polnoci - isto kot vse turnirske tekme (vir pove samo dan) in isto,
       kot jo razvrsti preracun (VrstaRatinskeTekme). */
    public static LocalDateTime casLigaskeTekme(TekmaSrecanja tekma) {
        Srecanje srecanje = tekma.getSrecanje();
        if (srecanje.jeTurnirsko()) {
            LocalDate datum = srecanje.datumTurnirja();
            return datum == null ? VrstaRatinskeTekme.BREZ_DATUMA : datum.atStartOfDay();
        }
        if (srecanje.getOdigranOb() != null) {
            return srecanje.getOdigranOb();
        }
        if (srecanje.getPredvidenZacetek() != null) {
            return srecanje.getPredvidenZacetek();
        }
        return VrstaRatinskeTekme.BREZ_DATUMA;
    }

    /* Postavitveni (zacetni) rating: admin igralcu doloci vstopni rating,
       da mocnemu novincu ni treba ~15 tekem plezati z 1000. Dovoljeno LE dokler
       igralec nima nobene odigrane ratinske tekme - kasneje rating dolocajo
       samo rezultati. Zabelezi se v dnevnik kot zapis brez tekme. */
    @Transactional
    public void nastaviZacetniRating(Igralec igralec, int vrednost) {
        if (vrednost < TurnirkoRatingStoritev.NAJNIZJI_ZACETNI || vrednost > TurnirkoRatingStoritev.NAJVISJI_ZACETNI) {
            throw new NeveljavenVnosIzjema("Zacetni rating mora biti med "
                    + TurnirkoRatingStoritev.NAJNIZJI_ZACETNI + " in " + TurnirkoRatingStoritev.NAJVISJI_ZACETNI + ".");
        }
        RatingStanje stanje = stanjeRepozitorij
                .findByIgralecIdAndSistem(igralec.getId(), RatingStanje.SISTEM_TURNIRKO)
                .orElse(null);
        if (stanje != null && stanje.getStTekem() > 0) {
            throw new DomenskaIzjema(
                    "Zacetni rating je mogoce postaviti le igralcu, ki se ni odigral nobene tekme.");
        }
        // pri prvi postavitvi ni spremembe (igralec ni imel prejsnjega ratinga);
        // pri ponovni postavitvi (pred prvo tekmo) zabelezimo razliko od prejsnje
        int staro = (stanje != null) ? stanje.getVrednost() : vrednost;
        if (stanje == null) {
            stanje = new RatingStanje(igralec, RatingStanje.SISTEM_TURNIRKO, vrednost);
        } else {
            stanje.setVrednost(vrednost);
        }
        stanje.setPostavljen(true);
        stanjeRepozitorij.save(stanje);
        zgodovinaRepozitorij.save(new RatingZgodovina(
                igralec, RatingStanje.SISTEM_TURNIRKO, vrednost - staro, vrednost));
    }

    /* ZUNANJA UVRSTITEV: rating, prepisan z zunanje lestvice (ITTF, NTZS,
       druga zveza).

       Zakaj obstaja poleg postavitve. Igralec, ki pri nas odigra dve tekmi na
       leto, ker sicer igra po svetu, dobi stevilko, ki o njem ne pove nicesar:
       lestvica ga postavi ocitno prenizko, po 18 mesecih pa ga sploh ne kaze
       vec. Njegova moc pri tem ni neznana - zapisana je drugje. Postavitev
       tega ne resi, ker je dovoljena le pred prvo tekmo; prav v tem se ta
       poseg od nje locuje.

       Trije pogoji, brez katerih je to samovolja in ne podatek:
         * VIR in POJASNILO sta obvezna in JAVNA (vidna na profilu),
         * zapis gre v dnevnik kot vsaka druga sprememba,
         * stevilka mora ostati v istih mejah kot vsak drug rating.

       Premor se poplaca PRED uvrstitvijo (isto kot pred tekmo): odbitki, ki so
       do tedaj zapadli, so se res zgodili in morajo ostati v zgodovini. Sele
       nato stevilka skoci na zunanjo - in od tistega trenutka je spet sveza
       (glej RatingStanje.svezOb). */
    @Transactional
    public void zunanjaUvrstitev(Igralec igralec, int vrednost, String vir, String pojasnilo) {
        if (vrednost < TurnirkoRatingStoritev.NAJNIZJI_ZACETNI
                || vrednost > TurnirkoRatingStoritev.NAJVISJI_ZACETNI) {
            throw new NeveljavenVnosIzjema("Rating mora biti med "
                    + TurnirkoRatingStoritev.NAJNIZJI_ZACETNI + " in "
                    + TurnirkoRatingStoritev.NAJVISJI_ZACETNI + ".");
        }
        if (vir == null || vir.isBlank()) {
            throw new NeveljavenVnosIzjema("Zunanja uvrstitev mora navesti vir (npr. ITTF, NTZS).");
        }
        if (pojasnilo == null || pojasnilo.isBlank()) {
            throw new NeveljavenVnosIzjema("Zunanja uvrstitev mora navesti pojasnilo.");
        }

        LocalDateTime zdaj = LocalDateTime.now();
        RatingStanje stanje = stanjeRepozitorij
                .findByIgralecIdAndSistem(igralec.getId(), RatingStanje.SISTEM_TURNIRKO)
                .orElse(null);
        if (stanje == null) {
            stanje = new RatingStanje(igralec, RatingStanje.SISTEM_TURNIRKO, vrednost);
        } else {
            neaktivnostStoritev.uveljavi(stanje, zdaj);
        }
        int staro = stanje.getVrednost();

        stanje.setVrednost(vrednost);
        stanje.setPostavljen(true);
        stanje.setZunanjaUvrstitevOb(zdaj);
        stanjeRepozitorij.save(stanje);

        zgodovinaRepozitorij.save(RatingZgodovina.zunanjaUvrstitev(
                igralec, RatingStanje.SISTEM_TURNIRKO, vrednost - staro, vrednost,
                zdaj, vir.trim(), pojasnilo.trim()));
    }

    /* Izracuna in shrani novi stanji obeh igralcev; vrne izracun za dnevnik.
       veljaOb je cas tekme - iz njega se prepozna vrnitev po odsotnosti, ki
       igralcu za nekaj tekem povisa K. teza je teza tekmovanja.

       Teza NE vpliva na stevec tekem: rekreativna tekma je ena odigrana tekma,
       le rating premakne za polovico (glej RavenTekmovanja). */
    private Obracun posodobiStanja(Igralec prvi, Igralec drugi, boolean zmagalPrvi,
                                   int nizi1, int nizi2, int steviloNizov,
                                   boolean predaja, LocalDateTime veljaOb,
                                   double teza) {
        RatingStanje stanje1 = najdiAliUstvari(prvi, veljaOb);
        RatingStanje stanje2 = najdiAliUstvari(drugi, veljaOb);

        /* Premor se poplaca PRED tekmo: odbitek je zapadel takrat, ko je
           zapadel, in nasprotnik mora igrati proti ze popravljeni stevilki.
           Zapisi odbitka nosijo datum zapadlosti, zato v dnevniku pristanejo
           pred to tekmo, ne za njo. */
        neaktivnostStoritev.uveljavi(stanje1, veljaOb);
        neaktivnostStoritev.uveljavi(stanje2, veljaOb);

        boolean vrnitev1 = zabeleziTekmo(stanje1, veljaOb);
        boolean vrnitev2 = zabeleziTekmo(stanje2, veljaOb);

        int ratingPrej1 = stanje1.getVrednost();
        int ratingPrej2 = stanje2.getVrednost();

        TurnirkoRatingStoritev.Izracun izracun = turnirkoRating.izracunaj(
                new TurnirkoRatingStoritev.StanjeIgralca(ratingPrej1, stanje1.getStTekem(), vrnitev1),
                new TurnirkoRatingStoritev.StanjeIgralca(ratingPrej2, stanje2.getStTekem(), vrnitev2),
                zmagalPrvi, nizi1, nizi2, steviloNizov, predaja, teza);

        /* Kdor je se v obdobju uvrstitve, ne sesteva korakov: njegova stevilka
           se izracuna znova iz vseh izidov prvega dne. Nasprotnikova sprememba
           ostane taksna, kot jo je prislužil proti ratingu PRED tekmo. */
        int novi1 = uvrstiAliObdrzi(stanje1, prvi, veljaOb, ratingPrej2, zmagalPrvi,
                izracun.ratingPo1());
        int novi2 = uvrstiAliObdrzi(stanje2, drugi, veljaOb, ratingPrej1, !zmagalPrvi,
                izracun.ratingPo2());
        boolean uvrscen1 = novi1 != izracun.ratingPo1();
        boolean uvrscen2 = novi2 != izracun.ratingPo2();

        stanje1.setVrednost(novi1);
        stanje1.povecajStTekem();
        stanje2.setVrednost(novi2);
        stanje2.povecajStTekem();
        stanjeRepozitorij.save(stanje1);
        stanjeRepozitorij.save(stanje2);

        return new Obracun(new TurnirkoRatingStoritev.Izracun(
                ratingPrej1, ratingPrej2, novi1, novi2,
                novi1 - ratingPrej1, novi2 - ratingPrej2,
                izracun.pricakovanaVerjetnost1(),
                izracun.k1(), izracun.k2(),
                izracun.margina(), izracun.teza()),
                uvrscen1, uvrscen2);
    }

    /* Vrne rating igralca po tej tekmi: v obdobju uvrstitve regularizirano
       fiksno tocko iz vseh izidov prvega dne, sicer obicajni korak.

       Obdobje uvrstitve je PRVI DAN igranja. Pri turnirju je to natanko en
       turnir (vse njegove tekme dobijo datum turnirja), pri ligi eno srecanje.
       Prve tekme se ne uvrsca - pri eni tekmi je ugibanje vecje od podatka. */
    private int uvrstiAliObdrzi(RatingStanje stanje, Igralec igralec, LocalDateTime veljaOb,
                                int ratingNasprotnika, boolean zmaga, int privzeto) {
        if (stanje.isPostavljen()) {
            return privzeto; // rating je postavil clovek - ne ugibamo cez njega
        }
        LocalDateTime prva = stanje.getPrvaTekmaOb();
        if (prva == null || !prva.toLocalDate().equals(veljaOb.toLocalDate())) {
            return privzeto; // prvi dan je mimo - rating tece po tekmah
        }

        LocalDateTime zacetekDneva = veljaOb.toLocalDate().atStartOfDay();
        List<UvrstitevNovinca.Izid> izidi = new ArrayList<>();
        Long idIgralca = igralec.getId();
        LocalDateTime doKdaj = zacetekDneva.plusDays(1);
        dodaj(izidi, zgodovinaRepozitorij.izidiVOknuTurnirski(
                RatingStanje.SISTEM_TURNIRKO, idIgralca, zacetekDneva, doKdaj));
        dodaj(izidi, zgodovinaRepozitorij.izidiVOknuLigaski(
                RatingStanje.SISTEM_TURNIRKO, idIgralca, zacetekDneva, doKdaj));
        izidi.add(new UvrstitevNovinca.Izid(ratingNasprotnika, zmaga)); // ta tekma se ni v dnevniku

        if (izidi.size() < UvrstitevNovinca.NAJMANJ_TEKEM) {
            return privzeto;
        }
        return UvrstitevNovinca.izracunaj(izidi,
                sidroStoritev.zacetniRating(igralec, veljaOb.toLocalDate()));
    }

    private static void dodaj(List<UvrstitevNovinca.Izid> izidi, List<Object[]> vrstice) {
        for (Object[] v : vrstice) {
            izidi.add(new UvrstitevNovinca.Izid(
                    ((Number) v[0]).intValue(), ((Number) v[1]).intValue() == 1));
        }
    }

    /* Zabelezi tekmo v sledilnik vrnitve in pove, ali ta tekma se steje med
       tekme po vrnitvi. Stanje (zadnji termin, preostanek) se posodobi takoj,
       da ga vidi tudi naslednja tekma istega igralca. */
    private boolean zabeleziTekmo(RatingStanje stanje, LocalDateTime veljaOb) {
        SledilnikVrnitve sledilnik =
                new SledilnikVrnitve(stanje.getZadnjaTekmaOb(), stanje.getPreostanekVrnitve());
        boolean poVrnitvi = sledilnik.obracunaj(veljaOb);
        stanje.setZadnjaTekmaOb(sledilnik.zadnja());
        stanje.setPreostanekVrnitve(sledilnik.preostanek());
        if (stanje.getPrvaTekmaOb() == null) {
            stanje.setPrvaTekmaOb(veljaOb);
        }
        return poVrnitvi;
    }

    /* Vrne obstojece stanje ratinga ali ustvari novo. Izhodisce novinca je
       STAROSTNO SIDRO ob dnevu tekme in ne enotnih 1000: sicer se dva novinca
       v U11, ki igrata med sabo, ustalita pri isti stevilki kot dva novinca v
       U19, kar ocitno ni res. */
    private RatingStanje najdiAliUstvari(Igralec igralec, LocalDateTime veljaOb) {
        return stanjeRepozitorij
                .findByIgralecIdAndSistem(igralec.getId(), RatingStanje.SISTEM_TURNIRKO)
                .orElseGet(() -> new RatingStanje(igralec, RatingStanje.SISTEM_TURNIRKO,
                        sidroStoritev.zacetniRating(igralec, veljaOb.toLocalDate())));
    }
}
