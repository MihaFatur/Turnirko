/* Ponovni preracun Turnirko ratinga od danega datuma naprej.

   Zakaj sploh: rating je izpeljana kolicina - edini pravi vir resnice so izidi
   tekem. Doslej je bil dnevnik enosmeren: ce je sodnik vnesel napacen rezultat
   in ga popravil, je rating ostal napacen za vedno (varovalka existsByTekmaId
   je drugi obracun iste tekme zavrnila). Prav tako ni bilo mogoce spremeniti
   nobenega parametra formule, ne da bi stare in nove vrednosti pomesali.

   Ta storitev je zato temelj vsega nadaljnjega: pobrise obracune od datuma,
   obnovi stanje na tisti dan iz dnevnika in vse skupaj odigra znova v pravem
   casovnem zaporedju. Nemska andro-Rangliste isto stvar izvaja nacrtno (celoten
   preracun od leta 2005) in prav zato si sme popravljati logiko za nazaj.

   Kaj se NE pobrise: zapisi, ki jih je naredil clovek. Postavitev
   (POSTAVITEV) je IZHODISCE igralca in ostane kot zacetna vrednost, zunanja
   uvrstitev (ZUNANJA_UVRSTITEV) pa je popravek na dolocen dan in se zato
   odigra znova NA SVOJEM MESTU v casovni vrsti - med tekmami, ki so bile pred
   njo, in tistimi, ki so ji sledile. Njena zapisana vrednost je nedotakljiva,
   sprememba do prejsnje vrednosti pa se preracuna, ker je odvisna od vsega,
   kar se je zgodilo prej.

   Transakcije: ciscenje, obnova stanja in vsak paket tekem tecejo v svoji
   transakciji (TransactionTemplate). Pri 90 tisoc tekmah bi ena sama transakcija
   pomenila ogromno sejo in dolg zaklep baze. */
package si.turnirko.storitve;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import si.turnirko.modeli.Disciplina;
import si.turnirko.modeli.FazaTekme;
import si.turnirko.modeli.IzidTekme;
import si.turnirko.modeli.RatingStanje;
import si.turnirko.modeli.RatingZgodovina;
import si.turnirko.modeli.TipTekmeSrecanja;
import si.turnirko.repozitoriji.IgralecRepozitorij;
import si.turnirko.repozitoriji.RatingStanjeRepozitorij;
import si.turnirko.repozitoriji.RatingZgodovinaRepozitorij;
import si.turnirko.repozitoriji.TekmaRepozitorij;
import si.turnirko.repozitoriji.TekmaSrecanjaRepozitorij;

@Service
public class PreracunRatingaStoritev {

    /* Koliko tekem obracunamo v eni transakciji. Dovolj veliko, da sqlite ne
       placuje potrditve za vsako tekmo, dovolj majhno, da seja ne zraste. */
    private static final int VELIKOST_PAKETA = 500;

    /* Koliko id-jev gre v en IN stavek. sqlite ima omejeno stevilo vezanih
       parametrov (privzeto 999), zato brisemo po kosih. */
    private static final int VELIKOST_KOSA = 400;

    /* Izidi, ki stejejo v rating: tekma je morala biti dejansko odigrana.
       Brez boja in diskvalifikacija ne stejeta (isto kot pri rednem vnosu). */
    private static final List<IzidTekme> ODIGRANE = List.of(IzidTekme.IGRANO, IzidTekme.PREDAJA);

    private final TekmaRepozitorij tekmaRepozitorij;
    private final TekmaSrecanjaRepozitorij tekmaSrecanjaRepozitorij;
    private final RatingStanjeRepozitorij stanjeRepozitorij;
    private final RatingZgodovinaRepozitorij zgodovinaRepozitorij;
    private final IgralecRepozitorij igralecRepozitorij;
    private final RatingStoritev ratingStoritev;
    private final NeaktivnostStoritev neaktivnostStoritev;
    private final TransactionTemplate transakcija;

    public PreracunRatingaStoritev(TekmaRepozitorij tekmaRepozitorij,
                                   TekmaSrecanjaRepozitorij tekmaSrecanjaRepozitorij,
                                   RatingStanjeRepozitorij stanjeRepozitorij,
                                   RatingZgodovinaRepozitorij zgodovinaRepozitorij,
                                   IgralecRepozitorij igralecRepozitorij,
                                   RatingStoritev ratingStoritev,
                                   NeaktivnostStoritev neaktivnostStoritev,
                                   PlatformTransactionManager upravitelj) {
        this.tekmaRepozitorij = tekmaRepozitorij;
        this.tekmaSrecanjaRepozitorij = tekmaSrecanjaRepozitorij;
        this.stanjeRepozitorij = stanjeRepozitorij;
        this.zgodovinaRepozitorij = zgodovinaRepozitorij;
        this.igralecRepozitorij = igralecRepozitorij;
        this.ratingStoritev = ratingStoritev;
        this.neaktivnostStoritev = neaktivnostStoritev;
        this.transakcija = new TransactionTemplate(upravitelj);
    }

    /* Kaj je preracun naredil. */
    public record Porocilo(LocalDateTime od, int obracunanihTekem, int prizadetihIgralcev) {}

    /* En korak casovne vrste: tekma ALI zunanja uvrstitev. Oba premakneta
       rating in oba imata cas, zato morata biti v ENI vrsti - zunanja
       uvrstitev, odigrana na koncu, bi pomenila, da so vse poznejse tekme
       stekle iz napacne stevilke, odigrana na zacetku pa, da tiste pred njo
       niso vplivale na nic. */
    private record Korak(VrstaRatinskeTekme tekma, Long idUvrstitve, LocalDateTime cas) {
        static Korak zaTekmo(VrstaRatinskeTekme v) {
            return new Korak(v, null, v.casZaObracun());
        }
        boolean jeUvrstitev() {
            return idUvrstitve != null;
        }
    }

    /* Preracuna rating od datuma naprej (vkljucno). null pomeni "vse od zacetka". */
    public Porocilo preracunajOd(LocalDate od) {
        LocalDateTime meja = (od == null) ? VrstaRatinskeTekme.BREZ_DATUMA : od.atStartOfDay();

        List<VrstaRatinskeTekme> zaPreracun = new ArrayList<>();
        for (VrstaRatinskeTekme v : vsaVrsta()) {
            if (!v.casZaObracun().isBefore(meja)) {
                zaPreracun.add(v);
            }
        }

        /* Zunanje uvrstitve se razdelijo na dvoje: tiste pred mejo obnovijo
           stanje skupaj z ostalim dnevnikom, tiste od meje naprej pa se
           odigrajo znova na svojem mestu med tekmami. */
        List<Object[]> vseUvrstitve =
                zgodovinaRepozitorij.zunanjeUvrstitve(RatingStanje.SISTEM_TURNIRKO);
        List<Object[]> uvrstitvePredMejo = new ArrayList<>();
        List<Korak> koraki = new ArrayList<>();
        for (Object[] u : vseUvrstitve) {
            LocalDateTime velja = (LocalDateTime) u[2];
            if (velja.isBefore(meja)) {
                uvrstitvePredMejo.add(u);
            } else {
                koraki.add(new Korak(null, ((Number) u[0]).longValue(), velja));
            }
        }

        transakcija.executeWithoutResult(stanje -> {
            pobrisiObracune(zaPreracun);
            /* Odbitki za neaktivnost so IZPELJANKA iz zaporedja tekem (za
               razliko od postavitev, ki so odlocitev cloveka) - zavrzemo jih
               in izracunamo znova. */
            zgodovinaRepozitorij.pobrisiOdbitke(RatingStanje.SISTEM_TURNIRKO, meja);
        });
        int igralcev = transakcija.execute(
                stanje -> obnoviStanjaIzDnevnika(meja, uvrstitvePredMejo));

        /* Tekme in zunanje uvrstitve v eni casovni vrsti. Razvrscanje je
           STABILNO, zato tekme med sabo obdrzijo vrstni red, ki ga je dolocila
           VrstaRatinskeTekme (datum, ura, zaporedje v tekmovanju). */
        for (VrstaRatinskeTekme v : zaPreracun) {
            koraki.add(Korak.zaTekmo(v));
        }
        koraki.sort(Comparator.comparing(Korak::cas));

        for (int od0 = 0; od0 < koraki.size(); od0 += VELIKOST_PAKETA) {
            List<Korak> paket =
                    koraki.subList(od0, Math.min(od0 + VELIKOST_PAKETA, koraki.size()));
            transakcija.executeWithoutResult(stanje -> odigrajPaket(paket));
        }

        /* Odbitki med tekmami se uveljavijo sproti (ob vrnitvi igralca), zadnji
           premor - tisti, ki se traja - pa nima tekme, ki bi ga sprozila. Zato
           na koncu se enkrat uveljavimo vse, kar je zapadlo do danes. */
        transakcija.executeWithoutResult(
                stanje -> neaktivnostStoritev.uveljaviVse(LocalDateTime.now()));

        return new Porocilo(meja, zaPreracun.size(), igralcev);
    }

    /* Vse tekme, ki stejejo v rating - turnirske in ligaske skupaj, urejene v
       eno casovno vrsto. Merila so ista kot pri rednem vnosu rezultata. */
    public List<VrstaRatinskeTekme> vsaVrsta() {
        List<VrstaRatinskeTekme> vrsta = new ArrayList<>();
        for (Object[] r : tekmaRepozitorij.ratinskeTekme(Disciplina.POSAMICNO, ODIGRANE)) {
            vrsta.add(VrstaRatinskeTekme.turnirska(
                    ((Number) r[0]).longValue(),
                    (LocalDate) r[1],
                    r[2] != FazaTekme.SKUPINA,
                    ((Number) r[3]).intValue(),
                    ((Number) r[4]).intValue()));
        }
        for (Object[] r : tekmaSrecanjaRepozitorij.ratinskeTekme(TipTekmeSrecanja.POSAMICNA, ODIGRANE)) {
            LocalDateTime cas = (r[1] != null) ? (LocalDateTime) r[1] : (LocalDateTime) r[2];
            vrsta.add(VrstaRatinskeTekme.ligaska(
                    ((Number) r[0]).longValue(),
                    cas,
                    ((Number) r[3]).intValue(),
                    ((Number) r[4]).intValue()));
        }
        // posamicne tekme ekipnih tekem turnirjev: dan turnirja, zaporedje po stopnji
        for (Object[] r : tekmaSrecanjaRepozitorij.ratinskeTekmeTurnirskihSrecanj(
                TipTekmeSrecanja.POSAMICNA, ODIGRANE)) {
            vrsta.add(VrstaRatinskeTekme.ekipnaTurnirska(
                    ((Number) r[0]).longValue(),
                    (LocalDate) r[1],
                    r[2] == FazaTekme.SKUPINA,
                    r[3] == null ? null : ((Number) r[3]).intValue(),
                    ((Number) r[4]).intValue(),
                    ((Number) r[5]).intValue(),
                    ((Number) r[6]).intValue()));
        }
        return VrstaRatinskeTekme.uredi(vrsta);
    }

    /* Odigra en paket korakov v ze urejenem zaporedju. */
    private void odigrajPaket(List<Korak> paket) {
        for (Korak k : paket) {
            if (k.jeUvrstitev()) {
                uveljaviUvrstitev(k.idUvrstitve());
            } else if (k.tekma().ligaska()) {
                tekmaSrecanjaRepozitorij.findById(k.tekma().id())
                        .ifPresent(ratingStoritev::obracunajZaLigasko);
            } else {
                tekmaRepozitorij.findById(k.tekma().id())
                        .ifPresent(ratingStoritev::obracunajZaTurnirsko);
            }
        }
    }

    /* Znova odigra eno zunanjo uvrstitev: stevilko postavi na zapisano
       vrednost in POPRAVI zabelezeno spremembo.

       Zapisana vrednost je nedotakljiva - to je tisto, kar je clovek prebral z
       zunanje lestvice. Sprememba do prejsnje vrednosti pa je odvisna od vsega,
       kar se je zgodilo prej, in prav to preracun odigra znova; brez popravka
       bi graf napredka trdil "+240" tam, kjer je razlika zdaj +190.

       Zapadli odbitki se poplacajo PRED uvrstitvijo (isto kot pred tekmo):
       zgodili so se in v zgodovini morajo ostati. Sele nato stevilka skoci na
       zunanjo in je od tistega trenutka spet sveza. */
    private void uveljaviUvrstitev(Long idZapisa) {
        RatingZgodovina zapis = zgodovinaRepozitorij.findById(idZapisa).orElse(null);
        if (zapis == null) {
            return;
        }
        RatingStanje stanje = stanjeRepozitorij
                .findByIgralecIdAndSistem(zapis.getIgralec().getId(), RatingStanje.SISTEM_TURNIRKO)
                .orElse(null);
        if (stanje == null) {
            stanje = new RatingStanje(zapis.getIgralec(), RatingStanje.SISTEM_TURNIRKO,
                    zapis.getNovaVrednost());
        } else {
            neaktivnostStoritev.uveljavi(stanje, zapis.getVeljaOb());
        }
        int staro = stanje.getVrednost();

        stanje.setVrednost(zapis.getNovaVrednost());
        stanje.setPostavljen(true);
        stanje.setZunanjaUvrstitevOb(zapis.getVeljaOb());
        stanjeRepozitorij.save(stanje);

        zapis.popraviSpremembo(zapis.getNovaVrednost() - staro);
        zgodovinaRepozitorij.save(zapis);
    }

    /* Pobrise dnevniske zapise danih tekem, po kosih zaradi omejitve parametrov. */
    private void pobrisiObracune(List<VrstaRatinskeTekme> tekme) {
        List<Long> turnirske = new ArrayList<>();
        List<Long> ligaske = new ArrayList<>();
        for (VrstaRatinskeTekme v : tekme) {
            (v.ligaska() ? ligaske : turnirske).add(v.id());
        }
        poKosih(turnirske, kos -> zgodovinaRepozitorij.pobrisiZaTekme(RatingStanje.SISTEM_TURNIRKO, kos));
        poKosih(ligaske, kos -> zgodovinaRepozitorij.pobrisiZaTekmeSrecanja(RatingStanje.SISTEM_TURNIRKO, kos));
    }

    private void poKosih(List<Long> idji, java.util.function.Consumer<List<Long>> kaj) {
        for (int i = 0; i < idji.size(); i += VELIKOST_KOSA) {
            kaj.accept(idji.subList(i, Math.min(i + VELIKOST_KOSA, idji.size())));
        }
    }

    /* Postavi rating_stanje znova iz tega, kar je v dnevniku ostalo: vrednost je
       zadnji zapis igralca, stevec tekem pa stevilo njegovih zapisov, vezanih na
       tekmo. Igralci brez zapisov stanja nimajo - prvi obracun jim ga ustvari.

       Zadnjega termina in preostanka vrnitve iz zadnje vrstice ni mogoce
       prebrati - sta posledica CELOTNEGA zaporedja, zato case igralcevih tekem
       po vrsti spustimo skozi isti SledilnikVrnitve, ki ga uporablja redni
       obracun. En sam prehod da oboje: stevec tekem in sledilnik. */
    private int obnoviStanjaIzDnevnika(LocalDateTime meja, List<Object[]> uvrstitvePredMejo) {
        String sistem = RatingStanje.SISTEM_TURNIRKO;
        stanjeRepozitorij.deleteAll(stanjeRepozitorij.findBySistem(sistem));
        stanjeRepozitorij.flush();

        /* Zunanje uvrstitve PRED mejo se ne odigrajo znova, zato mora stanje
           njihovo posledico nositi: igralec je postavljen in njegova stevilka
           je bila takrat sveza. */
        Map<Long, LocalDateTime> uvrscenOb = new HashMap<>();
        for (Object[] u : uvrstitvePredMejo) {
            uvrscenOb.put(((Number) u[1]).longValue(), (LocalDateTime) u[2]);
        }

        Map<Long, Integer> stevila = new HashMap<>();
        Map<Long, SledilnikVrnitve> sledilniki = new HashMap<>();
        Map<Long, LocalDateTime> prveTekme = new HashMap<>();
        Set<Long> postavljeni = new HashSet<>(zgodovinaRepozitorij.igralciSPostavitvijo(sistem));
        for (Object[] r : zgodovinaRepozitorij.casiObracunanihTekem(sistem)) {
            Long idIgralca = ((Number) r[0]).longValue();
            LocalDateTime cas = (LocalDateTime) r[1];
            stevila.merge(idIgralca, 1, Integer::sum);
            sledilniki.computeIfAbsent(idIgralca, kljuc -> new SledilnikVrnitve()).obracunaj(cas);
            // poizvedba je urejena po casu, zato je prva vrstica igralca njegova prva tekma
            prveTekme.putIfAbsent(idIgralca, cas);
        }

        List<RatingStanje> nova = new ArrayList<>();
        for (Object[] r : zgodovinaRepozitorij.zadnjeVrednosti(sistem, meja)) {
            Long idIgralca = ((Number) r[0]).longValue();
            RatingStanje stanje = new RatingStanje(
                    igralecRepozitorij.getReferenceById(idIgralca), sistem, ((Number) r[1]).intValue());
            stanje.setStTekem(stevila.getOrDefault(idIgralca, 0));
            SledilnikVrnitve sledilnik = sledilniki.get(idIgralca);
            if (sledilnik != null) {
                stanje.setZadnjaTekmaOb(sledilnik.zadnja());
                stanje.setPreostanekVrnitve(sledilnik.preostanek());
            }
            stanje.setPrvaTekmaOb(prveTekme.get(idIgralca));
            LocalDateTime zunanja = uvrscenOb.get(idIgralca);
            stanje.setZunanjaUvrstitevOb(zunanja);
            stanje.setPostavljen(postavljeni.contains(idIgralca) || zunanja != null);
            nova.add(stanje);
        }
        stanjeRepozitorij.saveAll(nova);
        return nova.size();
    }
}
