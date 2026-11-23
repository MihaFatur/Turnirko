/* Mize turnirja in klicanje tekem (M1) ter predlog naslednje tekme (M2).
   Pravila: docs/pravila/mize.md, zasnova in odlocitve lastnika:
   docs/NACRT-BRANJE-LISTKOV.md (M1, M2).

   - Organizator nastavi stevilo miz (1-99, kolikor jih prebere okence listka),
     zacasno izklopljene mize, ali porazenec sodi in pocitek.
   - Klic: pripravljena tekma gre na prosto mizo (V_IGRI) s sodnikom ali brez. Isti
     klic tekmo premakne ali zamenja sodnika. Igralec, zaseden drugje, ni napaka:
     vmesnik ga pokaze, odloci organizator.
   - Mizo sprosti vsak prehod tekme v KONCANA (Tekma.setStatus), zato ta storitev
     za vnos izida ne ve nic.
   - Pregled sestavi posnetek turnirja, PredlogTekem pa iz njega izpelje stanje miz
     in vrstni red pripravljenih tekem.

   Vse spremembe tecejo pod zaklepom turnirja (kot vnos rezultata): dva klica na
   isto mizo ne prestaneta oba preverbe proste mize. */
package si.turnirko.storitve;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.KlicTekmeVnos;
import si.turnirko.dto.MizeTurnirjaDto;
import si.turnirko.dto.MizeTurnirjaDto.KandidatDto;
import si.turnirko.dto.MizeTurnirjaDto.MizaDto;
import si.turnirko.dto.MizeTurnirjaDto.OsebaDto;
import si.turnirko.dto.MizeTurnirjaDto.TekmaMizeDto;
import si.turnirko.dto.NastavitveMizVnos;
import si.turnirko.dto.TekmaDto;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.FazaTekme;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.SistemTekmovanja;
import si.turnirko.modeli.Skupina;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.StatusTekmovanja;
import si.turnirko.modeli.StranEkipe;
import si.turnirko.modeli.Tekma;
import si.turnirko.modeli.Turnir;
import si.turnirko.pomozno.SlovenskaAbeceda;
import si.turnirko.repozitoriji.IgralecRepozitorij;
import si.turnirko.repozitoriji.KaderEkipeRepozitorij;
import si.turnirko.repozitoriji.PostavaSrecanjaRepozitorij;
import si.turnirko.repozitoriji.PrijavaRepozitorij;
import si.turnirko.repozitoriji.SkupinaRepozitorij;
import si.turnirko.repozitoriji.SrecanjeRepozitorij;
import si.turnirko.repozitoriji.TekmaRepozitorij;
import si.turnirko.repozitoriji.TurnirRepozitorij;

@Service
public class MizeStoritev {

    /* Najvec miz: toliko jih prebere okence za mizo na listku (S1, MizaListka). */
    static final int NAJVEC_MIZ = 99;
    static final int NAJVEC_POCITKA_MIN = 30;

    private final TurnirRepozitorij turnirRepozitorij;
    private final TekmaRepozitorij tekmaRepozitorij;
    private final PostavaSrecanjaRepozitorij postavaRepozitorij;
    private final SrecanjeRepozitorij srecanjeRepozitorij;
    private final SkupinaRepozitorij skupinaRepozitorij;
    private final PrijavaRepozitorij prijavaRepozitorij;
    private final KaderEkipeRepozitorij kaderRepozitorij;
    private final IgralecRepozitorij igralecRepozitorij;
    private final LastnistvoStoritev lastnistvo;
    private final Zaklepi zaklepi;

    public MizeStoritev(TurnirRepozitorij turnirRepozitorij, TekmaRepozitorij tekmaRepozitorij,
                        PostavaSrecanjaRepozitorij postavaRepozitorij, SrecanjeRepozitorij srecanjeRepozitorij,
                        SkupinaRepozitorij skupinaRepozitorij, PrijavaRepozitorij prijavaRepozitorij,
                        KaderEkipeRepozitorij kaderRepozitorij, IgralecRepozitorij igralecRepozitorij,
                        LastnistvoStoritev lastnistvo, Zaklepi zaklepi) {
        this.turnirRepozitorij = turnirRepozitorij;
        this.tekmaRepozitorij = tekmaRepozitorij;
        this.postavaRepozitorij = postavaRepozitorij;
        this.srecanjeRepozitorij = srecanjeRepozitorij;
        this.skupinaRepozitorij = skupinaRepozitorij;
        this.prijavaRepozitorij = prijavaRepozitorij;
        this.kaderRepozitorij = kaderRepozitorij;
        this.igralecRepozitorij = igralecRepozitorij;
        this.lastnistvo = lastnistvo;
        this.zaklepi = zaklepi;
    }

    // ---------- pregled ----------

    @Transactional(readOnly = true)
    public MizeTurnirjaDto pregled(Long idTurnir) {
        lastnistvo.preveriTurnirPoId(idTurnir);
        Turnir turnir = najdiTurnir(idTurnir);
        LocalDateTime zdaj = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        List<Tekma> tekme = tekmaRepozitorij.zaMizeTurnirja(idTurnir, zdaj.toLocalDate().atStartOfDay());

        Posnetek posnetek = posnetek(tekme);
        PredlogTekem.Rezultat rezultat = PredlogTekem.izracunaj(posnetek.vhod(), nastavitve(turnir), zdaj);

        Map<Long, TekmaMizeDto> dto = new HashMap<>();
        Function<PredlogTekem.Tekma, TekmaMizeDto> vDto = t -> dto.computeIfAbsent(t.id(),
                id -> posnetek.dto(posnetek.entitete().get(id), t));

        List<MizaDto> mize = rezultat.mize().stream()
                .map(m -> new MizaDto(m.stevilka(), m.izklopljena(),
                        m.tekma() == null ? null : vDto.apply(m.tekma()),
                        m.prostaOd(), oseba(m.cakajociSodnik()), m.predlogi()))
                .toList();
        List<KandidatDto> kandidati = rezultat.kandidati().stream()
                .map(k -> new KandidatDto(vDto.apply(k.tekma()), k.zaseden(), k.razlogi()))
                .toList();
        boolean vTeku = tekme.stream().anyMatch(t -> t.getDogodek().getStatus() == StatusTekmovanja.V_TEKU);
        return new MizeTurnirjaDto(turnir.getId(), turnir.getIme(), turnir.getSteviloMiz(),
                List.copyOf(turnir.getIzklopljeneMize()), turnir.isPorazenecSodi(), turnir.getPocitekMin(),
                vTeku, zdaj, mize, kandidati);
    }

    /* Osebe, ki smejo soditi: igralci s prijavo na turnir (tudi soigralci para in
       kadri ekip), po abecedi priimkov. */
    @Transactional(readOnly = true)
    public List<OsebaDto> osebe(Long idTurnir) {
        lastnistvo.preveriTurnirPoId(idTurnir);
        najdiTurnir(idTurnir);
        return igralecRepozitorij.findAllById(osebeTurnirja(idTurnir)).stream()
                .sorted(Comparator.comparing(Igralec::getPriimek, SlovenskaAbeceda.RED)
                        .thenComparing(Igralec::getIme, SlovenskaAbeceda.RED))
                .map(i -> new OsebaDto(i.getId(), i.polnoIme()))
                .toList();
    }

    // ---------- nastavitve ----------

    @Transactional
    public void nastavi(Long idTurnir, NastavitveMizVnos vnos) {
        zaklepi.zakleni(Zaklepi.Vrsta.TURNIR, idTurnir);
        lastnistvo.preveriTurnirPoId(idTurnir);
        Turnir turnir = najdiTurnir(idTurnir);

        Integer stevilo = vnos.steviloMiz();
        if (stevilo != null && (stevilo < 1 || stevilo > NAJVEC_MIZ)) {
            throw new NeveljavenVnosIzjema("Število miz mora biti med 1 in " + NAJVEC_MIZ + ".");
        }
        if (vnos.porazenecSodi() == null || vnos.pocitekMin() == null) {
            throw new NeveljavenVnosIzjema("Nastavitve miz morajo povedati, ali poraženec sodi, in počitek.");
        }
        if (vnos.pocitekMin() < 0 || vnos.pocitekMin() > NAJVEC_POCITKA_MIN) {
            throw new NeveljavenVnosIzjema("Počitek mora biti med 0 in " + NAJVEC_POCITKA_MIN + " minut.");
        }
        Set<Integer> izklopljene = new TreeSet<>();
        if (vnos.izklopljeneMize() != null) {
            for (Integer miza : vnos.izklopljeneMize()) {
                if (miza == null || stevilo == null || miza < 1 || miza > stevilo) {
                    throw new NeveljavenVnosIzjema("Miza " + miza + " ne obstaja - izklopiš lahko samo mize od 1 do "
                            + (stevilo == null ? 0 : stevilo) + ".");
                }
                izklopljene.add(miza);
            }
        }
        // miza, na kateri se igra, ne sme izginiti: tekma bi ostala na mizi, ki je ni
        for (Tekma t : tekmaRepozitorij.naMizahTurnirja(idTurnir)) {
            for (Integer miza : mizeTekme(t)) {
                if (stevilo == null || miza > stevilo || izklopljene.contains(miza)) {
                    throw new DomenskaIzjema("Na mizi " + miza + " se igra. Najprej vpiši izid ali prekliči klic.");
                }
            }
        }
        turnir.setSteviloMiz(stevilo);
        turnir.setIzklopljeneMize(izklopljene);
        turnir.setPorazenecSodi(vnos.porazenecSodi());
        turnir.setPocitekMin(vnos.pocitekMin());
    }

    // ---------- klic ----------

    /* Klic tekme na mizo ali sprememba klica (premik na drugo mizo, drug sodnik). */
    @Transactional
    public void poklici(Long idTekme, KlicTekmeVnos vnos) {
        zaklepi.zakleniTurnirPoTekmi(idTekme);
        lastnistvo.preveriTurnirPoTekmi(idTekme);
        Tekma tekma = najdiTekmo(idTekme);
        Dogodek dogodek = tekma.getDogodek();
        Turnir turnir = dogodek.getTurnir();

        if (turnir.getSteviloMiz() == null) {
            throw new DomenskaIzjema("Turnir nima nastavljenih miz - najprej vpiši število miz.");
        }
        if (dogodek.getStatus() != StatusTekmovanja.V_TEKU) {
            throw new DomenskaIzjema("Kategorija ni v teku - tekme ni mogoče poklicati.");
        }
        boolean zePoklicana = tekma.getStatus() == StatusTekme.V_IGRI && tekma.getMiza() != null;
        boolean ekipnaBrezMize = tekma.getStatus() == StatusTekme.V_IGRI && tekma.getMiza() == null
                && dogodek.jeEkipno();
        if (!zePoklicana && !ekipnaBrezMize) {
            switch (tekma.getStatus()) {
                case KONCANA -> throw new DomenskaIzjema("Tekma je že končana.");
                case CAKA -> throw new DomenskaIzjema("Tekma še čaka na igralce iz prejšnjih kol.");
                case V_IGRI -> throw new DomenskaIzjema("Tekma se že igra.");
                case PRIPRAVLJENA -> { /* v redu */ }
            }
        }
        if (tekma.getPrijava1() == null || tekma.getPrijava2() == null) {
            throw new DomenskaIzjema("Tekma še nima obeh udeležencev.");
        }

        int miza = vnos.miza();
        Integer miza2 = vnos.miza2();
        preveriMizo(turnir, miza);
        if (miza2 != null) {
            if (!dogodek.jeEkipno()) {
                throw new NeveljavenVnosIzjema("Drugo mizo dobi samo ekipno srečanje.");
            }
            if (miza2 == miza) {
                throw new NeveljavenVnosIzjema("Druga miza mora biti druga od prve.");
            }
            preveriMizo(turnir, miza2);
        }
        for (Tekma druga : tekmaRepozitorij.naMizahTurnirja(turnir.getId())) {
            if (druga.getId().equals(tekma.getId())) {
                continue;
            }
            if (druga.naMizi(miza)) {
                throw new DomenskaIzjema("Na mizi " + miza + " se že igra.");
            }
            if (miza2 != null && druga.naMizi(miza2)) {
                throw new DomenskaIzjema("Na mizi " + miza2 + " se že igra.");
            }
        }
        Igralec sodnik = vnos.idSodnika() == null ? null : sodnik(turnir, tekma, vnos.idSodnika());
        tekma.poklici(miza, miza2, sodnik, LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS));
        tekmaRepozitorij.save(tekma);
    }

    /* Preklic klica: tekma se vrne med pripravljene. Ekipna tekma, katere srecanje ze
       ima postavo, se igra naprej (postava jo je zacela) in izgubi samo mizo. */
    @Transactional
    public void preklici(Long idTekme) {
        zaklepi.zakleniTurnirPoTekmi(idTekme);
        lastnistvo.preveriTurnirPoTekmi(idTekme);
        Tekma tekma = najdiTekmo(idTekme);
        if (tekma.getStatus() != StatusTekme.V_IGRI || tekma.getMiza() == null) {
            throw new DomenskaIzjema("Tekma ni poklicana na mizo.");
        }
        boolean srecanjeTece = tekma.getDogodek().jeEkipno()
                && srecanjeRepozitorij.najdiZaTekmo(idTekme)
                        .map(s -> postavaRepozitorij.countBySrecanjeId(s.getId()) > 0).orElse(false);
        if (srecanjeTece) {
            tekma.odstraniMizo();
        } else {
            tekma.setStatus(StatusTekme.PRIPRAVLJENA);
        }
        tekmaRepozitorij.save(tekma);
    }

    /* Miza z listka (M1, 3.7): koncana tekma, ki ni bila poklicana, dobi mizo, ki jo
       je potrdil sodnik (ali jo je bralnik zanesljivo prebral) - tako tudi
       »porazenec sodi« ve, kje se je igralo. Samo veljavna miza, na kateri ne igra
       druga tekma: listek z napacno mizo ne sme prestaviti sodnika druge mize.
       Klice se v transakciji potrditve, za vpisom izida. */
    void zapisiMizoZListka(Long idTekme, Integer miza) {
        if (miza == null) {
            return;
        }
        Tekma tekma = tekmaRepozitorij.najdiZVsem(idTekme).orElse(null);
        if (tekma == null || tekma.getMiza() != null || tekma.getStatus() != StatusTekme.KONCANA) {
            return;
        }
        Turnir turnir = tekma.getDogodek().getTurnir();
        if (turnir.getSteviloMiz() == null || miza < 1 || miza > turnir.getSteviloMiz()
                || turnir.getIzklopljeneMize().contains(miza)) {
            return;
        }
        if (tekmaRepozitorij.naMizahTurnirja(turnir.getId()).stream().anyMatch(t -> t.naMizi(miza))) {
            return;
        }
        tekma.setMiza(miza);
        tekmaRepozitorij.save(tekma);
    }

    // ---------- pomozno: preverbe ----------

    private static void preveriMizo(Turnir turnir, int miza) {
        if (miza < 1 || miza > turnir.getSteviloMiz()) {
            throw new NeveljavenVnosIzjema("Miza " + miza + " ne obstaja - turnir ima mize od 1 do "
                    + turnir.getSteviloMiz() + ".");
        }
        if (turnir.getIzklopljeneMize().contains(miza)) {
            throw new DomenskaIzjema("Miza " + miza + " je izklopljena.");
        }
    }

    /* Sodnik je oseba s prijavo na turnir, ki v tej tekmi ne igra. Ali je zaseden
       drugje, odloci organizator (vmesnik ga opozori). */
    private Igralec sodnik(Turnir turnir, Tekma tekma, Long idSodnika) {
        if (!osebeTurnirja(turnir.getId()).contains(idSodnika)) {
            throw new NeveljavenVnosIzjema("Sodnik mora biti igralec, prijavljen na ta turnir.");
        }
        for (Prijava p : List.of(tekma.getPrijava1(), tekma.getPrijava2())) {
            boolean igra = (p.getIgralec() != null && p.getIgralec().getId().equals(idSodnika))
                    || (p.getIgralec2() != null && p.getIgralec2().getId().equals(idSodnika))
                    || (p.getEkipa() != null && kaderRepozitorij.existsByEkipaIdAndIgralecId(
                            p.getEkipa().getId(), idSodnika));
            if (igra) {
                throw new NeveljavenVnosIzjema("Igralec ne more soditi tekme, v kateri igra.");
            }
        }
        return igralecRepozitorij.findById(idSodnika)
                .orElseThrow(() -> new NiNajdenoIzjema("Igralec z id " + idSodnika + " ne obstaja."));
    }

    private Set<Long> osebeTurnirja(Long idTurnir) {
        List<Long> turnir = List.of(idTurnir);
        Set<Long> osebe = new HashSet<>();
        prijavaRepozitorij.igralciTurnirjev(turnir, Prijava.StatusPrijave.ODJAVLJEN)
                .forEach(v -> osebe.add(((Number) v[1]).longValue()));
        prijavaRepozitorij.soigralciTurnirjev(turnir, Prijava.StatusPrijave.ODJAVLJEN)
                .forEach(v -> osebe.add(((Number) v[1]).longValue()));
        kaderRepozitorij.igralciEkipnihTurnirjev(turnir)
                .forEach(v -> osebe.add(((Number) v[1]).longValue()));
        return osebe;
    }

    private static List<Integer> mizeTekme(Tekma t) {
        List<Integer> mize = new ArrayList<>(2);
        if (t.getMiza() != null) mize.add(t.getMiza());
        if (t.getMiza2() != null) mize.add(t.getMiza2());
        return mize;
    }

    private Turnir najdiTurnir(Long idTurnir) {
        return turnirRepozitorij.findById(idTurnir)
                .orElseThrow(() -> new NiNajdenoIzjema("Turnir z id " + idTurnir + " ne obstaja."));
    }

    private Tekma najdiTekmo(Long idTekme) {
        return tekmaRepozitorij.najdiZVsem(idTekme)
                .orElseThrow(() -> new NiNajdenoIzjema("Tekma z id " + idTekme + " ne obstaja."));
    }

    private static PredlogTekem.Nastavitve nastavitve(Turnir turnir) {
        return new PredlogTekem.Nastavitve(turnir.getSteviloMiz() == null ? 0 : turnir.getSteviloMiz(),
                turnir.getIzklopljeneMize(), turnir.isPorazenecSodi(), turnir.getPocitekMin());
    }

    private static OsebaDto oseba(PredlogTekem.Oseba o) {
        return o == null ? null : new OsebaDto(o.id(), o.ime());
    }

    // ---------- posnetek turnirja ----------

    /* Posnetek: vhod za PredlogTekem in vse, kar potrebuje DTO (entitete, oznake
       skupin, srecanja ekipnih tekem). */
    private record Posnetek(List<PredlogTekem.Tekma> vhod, Map<Long, Tekma> entitete,
                            Map<Long, String> skupine, Map<Long, Long> srecanja,
                            Map<String, Integer> zadnjaKola) {

        TekmaMizeDto dto(Tekma t, PredlogTekem.Tekma v) {
            Set<Long> osebe = new TreeSet<>();
            for (PredlogTekem.Stran s : List.of(v.stran1(), v.stran2())) {
                if (s != null) s.osebe().forEach(o -> osebe.add(o.id()));
            }
            return new TekmaMizeDto(TekmaDto.iz(t), t.getDogodek().getId(), t.getDogodek().getIme(),
                    del(t), t.getDogodek().jeEkipno(), srecanja.get(t.getId()), t.getMiza(), t.getMiza2(),
                    t.getPoklicanaOb(), oseba(v.sodnik()), List.copyOf(osebe));
        }

        /* »Skupina B«, »polfinale«, »za 5.-8. mesto«, »tolažilni del«. */
        String del(Tekma t) {
            if (t.getFaza() == FazaTekme.SKUPINA) {
                String oznaka = t.getIdSkupina() == null ? null : skupine.get(t.getIdSkupina());
                return oznaka == null ? "skupina" : "Skupina " + oznaka;
            }
            if (t.getRazponOd() != null && t.getRazponDo() != null) {
                return t.getRazponOd() == 1 && t.getRazponDo() == 2 ? "finale"
                        : "za " + t.getRazponOd() + ".–" + t.getRazponDo() + ". mesto";
            }
            if (t.getFaza() == FazaTekme.TOLAZILNI) {
                return "tolažilni del";
            }
            Integer zadnje = zadnjaKola.get(kljucVeje(t));
            int doKonca = zadnje == null ? 0 : zadnje - t.getKolo() + 1;
            return switch (doKonca) {
                case 1 -> "finale";
                case 2 -> "polfinale";
                case 3 -> "četrtfinale";
                case 4 -> "osmina finala";
                case 5 -> "šestnajstina finala";
                default -> t.getKolo() + ". kolo";
            };
        }
    }

    private static String kljucVeje(Tekma t) {
        return t.getDogodek().getId() + "/" + t.getFaza() + "/" + t.getIdZreb();
    }

    private Posnetek posnetek(List<Tekma> tekme) {
        Map<Long, Tekma> entitete = tekme.stream()
                .collect(Collectors.toMap(Tekma::getId, Function.identity(), (a, b) -> a));

        List<Long> ekipne = tekme.stream().filter(t -> t.getDogodek().jeEkipno()).map(Tekma::getId).toList();
        Map<Long, Long> srecanja = new HashMap<>();
        if (!ekipne.isEmpty()) {
            srecanjeRepozitorij.srecanjaTekem(ekipne)
                    .forEach(v -> srecanja.put(((Number) v[0]).longValue(), ((Number) v[1]).longValue()));
        }
        // postave samo za ekipne tekme v igri: kdo zdaj igra
        List<Long> ekipneVIgri = tekme.stream()
                .filter(t -> t.getDogodek().jeEkipno() && t.getStatus() == StatusTekme.V_IGRI)
                .map(Tekma::getId).toList();
        Map<Long, Map<StranEkipe, List<PredlogTekem.Oseba>>> postave = new HashMap<>();
        if (!ekipneVIgri.isEmpty()) {
            for (Object[] v : postavaRepozitorij.osebePostavZaTekme(ekipneVIgri)) {
                List<PredlogTekem.Oseba> stran = postave
                        .computeIfAbsent(((Number) v[0]).longValue(), k -> new HashMap<>())
                        .computeIfAbsent((StranEkipe) v[1], k -> new ArrayList<>());
                long id = ((Number) v[2]).longValue();
                if (stran.stream().noneMatch(o -> o.id() == id)) {
                    stran.add(new PredlogTekem.Oseba(id, v[3] + " " + v[4]));
                }
            }
        }

        Set<Long> idjiSkupin = tekme.stream().map(Tekma::getIdSkupina).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> skupine = idjiSkupin.isEmpty() ? Map.of()
                : skupinaRepozitorij.findAllById(idjiSkupin).stream()
                        .collect(Collectors.toMap(Skupina::getId, Skupina::getOznaka));

        Map<String, Integer> zadnjaKola = new HashMap<>();
        tekme.forEach(t -> zadnjaKola.merge(kljucVeje(t), t.getKolo(), Math::max));

        Map<Long, Integer> nadaljnjih = new HashMap<>();
        List<PredlogTekem.Tekma> vhod = new ArrayList<>();
        for (Tekma t : tekme) {
            Dogodek d = t.getDogodek();
            int nad = t.getFaza() == FazaTekme.SKUPINA
                    ? nadaljnjih.computeIfAbsent(d.getId(), id -> poSkupinah(d, tekme)) : 0;
            Map<StranEkipe, List<PredlogTekem.Oseba>> postava = postave.getOrDefault(t.getId(), Map.of());
            vhod.add(new PredlogTekem.Tekma(t.getId(), d.getId(), d.getStatus() == StatusTekmovanja.V_TEKU,
                    vrsta(d), t.getFaza(), t.getIdSkupina(), t.getIdZreb(), t.getKolo(), t.getPozicija(),
                    t.getStatus(), t.getIzidTip(), t.jePrenesena(),
                    stran(t.getPrijava1(), postava.getOrDefault(StranEkipe.DOMACI, List.of())),
                    stran(t.getPrijava2(), postava.getOrDefault(StranEkipe.GOST, List.of())),
                    t.getZmagovalec() == null ? null : t.getZmagovalec().getId(),
                    t.getMiza(), t.getMiza2(), t.getPoklicanaOb(), t.getKoncanaOb(),
                    t.getSodnik() == null ? null
                            : new PredlogTekem.Oseba(t.getSodnik().getId(), t.getSodnik().polnoIme()),
                    nad));
        }
        return new Posnetek(vhod, entitete, skupine, srecanja, zadnjaKola);
    }

    private static PredlogTekem.Vrsta vrsta(Dogodek d) {
        if (d.jeEkipno()) return PredlogTekem.Vrsta.EKIPNO;
        if (d.jeDvojice()) return PredlogTekem.Vrsta.DVOJICE;
        return PredlogTekem.Vrsta.POSAMICNO;
    }

    /* Stran: posameznik in par nosita osebe v prijavi, ekipa v postavi srecanja. */
    private static PredlogTekem.Stran stran(Prijava p, List<PredlogTekem.Oseba> postava) {
        if (p == null) {
            return null;
        }
        List<PredlogTekem.Oseba> osebe = new ArrayList<>();
        if (p.jeEkipa()) {
            osebe.addAll(postava);
        } else {
            osebe.add(new PredlogTekem.Oseba(p.getIgralec().getId(), p.getIgralec().polnoIme()));
            if (p.getIgralec2() != null) {
                osebe.add(new PredlogTekem.Oseba(p.getIgralec2().getId(), p.getIgralec2().polnoIme()));
            }
        }
        return new PredlogTekem.Stran(p.getId(), p.prikazanoIme(), osebe);
    }

    /* Ocena tekem, ki udelezenca skupine cakajo po skupinskem delu (zaostanek, M2).
       Izlocilni del iz prvih dveh vsake skupine ima log2(2 x skupin) kol; SV regija
       igra zreb za vsa mesta iz skupin nivoja; finalna skupina za mesta ima 2 x
       skupin igralcev, eden od dvobojev se prenese. Ocena, ne napoved: sluzi samo
       temu, da kategorija z daljso potjo naprej pride na vrsto prej. */
    static int poSkupinah(Dogodek d, Collection<Tekma> tekme) {
        int skupin = (int) tekme.stream()
                .filter(t -> t.getDogodek().getId().equals(d.getId()) && t.getFaza() == FazaTekme.SKUPINA)
                .map(Tekma::getIdSkupina).filter(Objects::nonNull).distinct().count();
        SistemTekmovanja sistem = d.getSistemTekmovanja();
        return switch (sistem) {
            case SKUPINE_IZLOCILNI -> kol(2 * Math.max(1, skupin));
            case SV_REGIJA -> kol(2 * (d.getSteviloSkupin() == null ? 4 : Math.max(1, d.getSteviloSkupin())));
            case SKUPINE_ZA_MESTA -> Math.max(0, 2 * skupin - 2);
            default -> 0;
        };
    }

    /* Stevilo kol izlocilne mreze za n udelezencev (zaokrozeno navzgor na potenco 2). */
    static int kol(int n) {
        int kol = 0;
        for (int velikost = 1; velikost < n; velikost *= 2) {
            kol++;
        }
        return kol;
    }
}
