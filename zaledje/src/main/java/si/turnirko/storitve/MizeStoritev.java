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

   Skupne mize (V78, lastnik 25. 11. 2026): vec turnirjev istega organizatorja v isti
   dvorani si deli mize. Organizator jih poveze na strani Mize; od tedaj so mize,
   vrsta, klici, sodniki in nastavitve ena celota. Skupino vodi GLAVNI turnir
   (turnir.id_skupne_mize clanov kaze nanj): nosi nastavitve miz, rocni vrstni red
   tekem in vrstni red kategorij. Turnir brez povezave je skupina enega in vse
   deluje kot prej.

   Zaklepi: klic tece pod zaklepom turnirja tekme (kot vnos rezultata) in pod
   zaklepom skupnih miz (Zaklepi.Vrsta.MIZE, kljuc glavni turnir) - dva klica na isto
   mizo iz dveh turnirjev ne prestaneta oba preverbe proste mize. Sprememba
   nastavitev zaklene se turnir glavnega (njegova entiteta ima verzijo), povezava
   pa vse turnirje obeh skupin po vrsti id-jev; vedno najprej turnirji, nato mize. */
package si.turnirko.storitve;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
import si.turnirko.dto.MizeTurnirjaDto.KategorijaMizDto;
import si.turnirko.dto.MizeTurnirjaDto.MizaDto;
import si.turnirko.dto.MizeTurnirjaDto.OsebaDto;
import si.turnirko.dto.MizeTurnirjaDto.SkupneMizeDto;
import si.turnirko.dto.MizeTurnirjaDto.TekmaMizeDto;
import si.turnirko.dto.MizeTurnirjaDto.TurnirMizDto;
import si.turnirko.dto.NastavitveMizVnos;
import si.turnirko.dto.PovezavaMizVnos;
import si.turnirko.dto.RedKategorijVnos;
import si.turnirko.dto.RedKolVnos;
import si.turnirko.dto.TekmaDto;
import si.turnirko.dto.VrstniRedTekemVnos;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.FazaTekme;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.Skupina;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.StatusTekmovanja;
import si.turnirko.modeli.StranEkipe;
import si.turnirko.modeli.Tekma;
import si.turnirko.modeli.Turnir;
import si.turnirko.pomozno.SlovenskaAbeceda;
import si.turnirko.repozitoriji.DogodekRepozitorij;
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

    /* Povezava turnirjev se je spremenila, medtem ko je zahteva cakala na zaklep. */
    static final String SKUPNE_SPREMENJENE =
            "Skupne mize turnirja so se ravno spremenile. Osveži stran in poskusi znova.";

    private final TurnirRepozitorij turnirRepozitorij;
    private final DogodekRepozitorij dogodekRepozitorij;
    private final TekmaRepozitorij tekmaRepozitorij;
    private final PostavaSrecanjaRepozitorij postavaRepozitorij;
    private final SrecanjeRepozitorij srecanjeRepozitorij;
    private final SkupinaRepozitorij skupinaRepozitorij;
    private final PrijavaRepozitorij prijavaRepozitorij;
    private final KaderEkipeRepozitorij kaderRepozitorij;
    private final IgralecRepozitorij igralecRepozitorij;
    private final LastnistvoStoritev lastnistvo;
    private final Zaklepi zaklepi;

    public MizeStoritev(TurnirRepozitorij turnirRepozitorij, DogodekRepozitorij dogodekRepozitorij,
                        TekmaRepozitorij tekmaRepozitorij,
                        PostavaSrecanjaRepozitorij postavaRepozitorij, SrecanjeRepozitorij srecanjeRepozitorij,
                        SkupinaRepozitorij skupinaRepozitorij, PrijavaRepozitorij prijavaRepozitorij,
                        KaderEkipeRepozitorij kaderRepozitorij, IgralecRepozitorij igralecRepozitorij,
                        LastnistvoStoritev lastnistvo, Zaklepi zaklepi) {
        this.turnirRepozitorij = turnirRepozitorij;
        this.dogodekRepozitorij = dogodekRepozitorij;
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
        SkupneMize skupne = skupne(turnir.idGlavnegaZaMize());
        Turnir glavni = skupne.glavni();
        LocalDateTime zdaj = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        List<Tekma> tekme = tekmaRepozitorij.zaMizeTurnirjev(skupne.idji(), zdaj.toLocalDate().atStartOfDay());
        List<Dogodek> dogodki = redDogodkov(skupne);

        Posnetek posnetek = posnetek(tekme);
        Map<Long, Integer> zadnjeKoloSkupin = new HashMap<>();
        for (Tekma t : tekme) {
            if (t.getFaza() == FazaTekme.SKUPINA) {
                zadnjeKoloSkupin.merge(t.getDogodek().getId(), t.getKolo(), Math::max);
            }
        }
        PredlogTekem.Rezultat rezultat = PredlogTekem.izracunaj(posnetek.vhod(), nastavitve(glavni, dogodki), zdaj);

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
        Set<Long> rocni = Set.copyOf(glavni.getVrstniRedTekem());
        boolean rocniRed = rezultat.kandidati().stream().anyMatch(k -> rocni.contains(k.tekma().id()));
        return new MizeTurnirjaDto(turnir.getId(), turnir.getIme(), glavni.getSteviloMiz(),
                List.copyOf(glavni.getIzklopljeneMize()), glavni.isPorazenecSodi(), glavni.getPocitekMin(),
                vTeku, zdaj, mize, kandidati, rocniRed, skupneDto(skupne, dogodki, zadnjeKoloSkupin));
    }

    /* Rocni vrstni red vrste »Na vrsti« (V77): organizator je tekmo povlekel na drugo
       mesto, vmesnik poslje vrsto, kot jo je videl. Shranijo se samo tekme turnirjev s
       skupnimi mizami (dogodki v teku in tekme, koncane danes - isti posnetek kot
       pregled), da tuj id ne pride v zapis. Prazen seznam vrne samodejni vrstni red. */
    @Transactional
    public void nastaviVrstniRed(Long idTurnir, VrstniRedTekemVnos vnos) {
        SkupneMize skupne = zakleniNastavitve(idTurnir);
        List<Long> zeleni = vnos.idjiTekem() == null ? List.of() : vnos.idjiTekem();
        // List.of(...).contains(null) vrze izjemo, zato pregled po elementih
        if (zeleni.stream().anyMatch(Objects::isNull)) {
            throw new NeveljavenVnosIzjema("Vrstni red tekem ne sme vsebovati praznih vnosov.");
        }
        Set<Long> tekmeSkupine = tekmaRepozitorij
                .zaMizeTurnirjev(skupne.idji(), LocalDateTime.now().toLocalDate().atStartOfDay()).stream()
                .map(Tekma::getId).collect(Collectors.toSet());
        skupne.glavni().setVrstniRedTekem(zeleni.stream().filter(tekmeSkupine::contains).distinct().toList());
    }

    /* Osebe, ki smejo soditi: igralci s prijavo na turnirje s skupnimi mizami (tudi
       soigralci para in kadri ekip), po abecedi priimkov. Porazenec sodi tudi tekmo
       drugega turnirja na isti mizi (lastnik 25. 11. 2026). */
    @Transactional(readOnly = true)
    public List<OsebaDto> osebe(Long idTurnir) {
        lastnistvo.preveriTurnirPoId(idTurnir);
        SkupneMize skupne = skupne(najdiTurnir(idTurnir).idGlavnegaZaMize());
        return igralecRepozitorij.findAllById(osebeTurnirjev(skupne.idji())).stream()
                .sorted(Comparator.comparing(Igralec::getPriimek, SlovenskaAbeceda.RED)
                        .thenComparing(Igralec::getIme, SlovenskaAbeceda.RED))
                .map(i -> new OsebaDto(i.getId(), i.polnoIme()))
                .toList();
    }

    // ---------- nastavitve ----------

    /* Nastavitve miz veljajo za vse turnirje s skupnimi mizami (zapise jih glavni). */
    @Transactional
    public void nastavi(Long idTurnir, NastavitveMizVnos vnos) {
        SkupneMize skupne = zakleniNastavitve(idTurnir);
        Turnir glavni = skupne.glavni();

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
        for (Tekma t : tekmaRepozitorij.naMizahTurnirjev(skupne.idji())) {
            for (Integer miza : mizeTekme(t)) {
                if (stevilo == null || miza > stevilo || izklopljene.contains(miza)) {
                    throw new DomenskaIzjema("Na mizi " + miza + " se igra. Najprej vpiši izid ali prekliči klic.");
                }
            }
        }
        glavni.setSteviloMiz(stevilo);
        glavni.setIzklopljeneMize(izklopljene);
        glavni.setPorazenecSodi(vnos.porazenecSodi());
        glavni.setPocitekMin(vnos.pocitekMin());
    }

    /* Vrstni red kategorij v kolu (V78): predlog gre strogo po kolih, v kolu po tem redu
       kategorij (lastnik 25. 11. 2026). Samo kategorije turnirjev s skupnimi mizami;
       prazen seznam vrne privzeti red. */
    @Transactional
    public void nastaviRedKategorij(Long idTurnir, RedKategorijVnos vnos) {
        SkupneMize skupne = zakleniNastavitve(idTurnir);
        Set<Long> dogodkiSkupine = dogodekRepozitorij.zaTurnirje(skupne.idji()).stream()
                .map(Dogodek::getId).collect(Collectors.toSet());
        List<Long> red = new ArrayList<>(new LinkedHashSet<>(vnos.idjiDogodkov()));
        for (Long id : red) {
            if (!dogodkiSkupine.contains(id)) {
                throw new NeveljavenVnosIzjema("Kategorija z id " + id + " ni v turnirjih s temi mizami.");
            }
        }
        skupne.glavni().setVrstniRedDogodkov(red);
    }

    /* Vrstni red kol skupin kategorije (V80, lastnik 28. 11. 2026): predlog gre po kolih,
       kola kategorije pa v tem vrstnem redu (npr. 3., 1., 2. kolo). Je lastnost kategorije,
       zato ga pise zaklep njenega turnirja (kot zreb in vnos izidov), ne skupnih miz, in
       ostane ob povezavi in razdruzitvi. Samo kategorije turnirjev s skupnimi mizami,
       samo kola skupin od 1 do zadnjega; prazen seznam vrne 1, 2, 3 ... */
    @Transactional
    public void nastaviRedKol(Long idTurnir, RedKolVnos vnos) {
        Long idTurnirKategorije = dogodekRepozitorij.najdiSTurnirjem(vnos.idDogodka())
                .map(d -> d.getTurnir().getId())
                .orElseThrow(() -> new NiNajdenoIzjema("Kategorija z id " + vnos.idDogodka() + " ne obstaja."));
        zaklepi.zakleni(Zaklepi.Vrsta.TURNIR, idTurnirKategorije);
        lastnistvo.preveriTurnirPoId(idTurnir);
        lastnistvo.preveriTurnirPoId(idTurnirKategorije);
        if (!glavniId(idTurnir).equals(glavniId(idTurnirKategorije))) {
            throw new NeveljavenVnosIzjema("Kategorija z id " + vnos.idDogodka() + " ni v turnirjih s temi mizami.");
        }
        Dogodek dogodek = dogodekRepozitorij.findById(vnos.idDogodka())
                .orElseThrow(() -> new NiNajdenoIzjema("Kategorija z id " + vnos.idDogodka() + " ne obstaja."));
        List<Integer> kola = vnos.kola() == null ? List.of() : vnos.kola();
        // List.of(...).contains(null) vrze izjemo, zato pregled po elementih
        if (kola.stream().anyMatch(Objects::isNull)) {
            throw new NeveljavenVnosIzjema("Vrstni red kol ne sme vsebovati praznih vnosov.");
        }
        List<Integer> red = new ArrayList<>(new LinkedHashSet<>(kola));
        if (!red.isEmpty()) {
            Integer zadnje = tekmaRepozitorij.zadnjeKoloSkupin(dogodek.getId());
            if (zadnje == null) {
                throw new DomenskaIzjema("Kategorija " + dogodek.getIme() + " nima kol skupin - vrstni red kol "
                        + "nastaviš po žrebu skupin.");
            }
            for (Integer kolo : red) {
                if (kolo < 1 || kolo > zadnje) {
                    throw new NeveljavenVnosIzjema("Kategorija " + dogodek.getIme() + " ima kola skupin od 1 do "
                            + zadnje + " - " + kolo + ". kola ni.");
                }
            }
        }
        dogodek.setVrstniRedKol(red);
    }

    // ---------- skupne mize: povezava turnirjev (V78) ----------

    /* Turnirji, s katerimi sme organizator ta turnir povezati: njegovi turnirji, ki se
       dotikajo istih dni (lastnik 25. 11. 2026: vedno isti racun, isti dan) in se nimajo
       skupnih miz s tem. */
    @Transactional(readOnly = true)
    public List<TurnirMizDto> zaPovezavo(Long idTurnir) {
        lastnistvo.preveriTurnirPoId(idTurnir);
        Turnir turnir = najdiTurnir(idTurnir);
        if (turnir.getDatumZacetka() == null) {
            return List.of();
        }
        Set<Long> skupine = Set.copyOf(skupne(turnir.idGlavnegaZaMize()).idji());
        LocalDate konec = turnir.getDatumKonca() != null ? turnir.getDatumKonca() : turnir.getDatumZacetka();
        return turnirRepozitorij.najdiVObdobju(turnir.getDatumZacetka(), konec).stream()
                .filter(t -> !skupine.contains(t.getId()))
                .filter(t -> !t.jeUvozen() && lastnistvo.smeUrejatiTurnir(t))
                .map(MizeStoritev::turnirMiz)
                .toList();
    }

    /* Ta turnir (z vsemi, s katerimi ze deli mize) se pridruzi skupnim mizam izbranega.
       Nastavitve miz ostanejo izbranega, razen ce jih ta se nima. Mize, na katerih se
       ze igra, se ne smejo prekrivati (miza 3 v obeh turnirjih). */
    @Transactional
    public void povezi(Long idTurnir, PovezavaMizVnos vnos) {
        Long idDrugega = vnos.idTurnirja();
        if (idDrugega.equals(idTurnir)) {
            throw new NeveljavenVnosIzjema("Turnir ne more deliti miz sam s sabo.");
        }
        Long g1 = glavniId(idTurnir);
        Long g2 = glavniId(idDrugega);
        zakleniSkupine(List.of(g1, g2));
        if (!g1.equals(glavniId(idTurnir)) || !g2.equals(glavniId(idDrugega))) {
            throw new DomenskaIzjema(SKUPNE_SPREMENJENE);
        }
        if (g1.equals(g2)) {
            throw new DomenskaIzjema("Turnirja že imata skupne mize.");
        }
        SkupneMize pridruzena = skupne(g1);
        SkupneMize cilj = skupne(g2);
        // vsi turnirji obeh skupin morajo biti organizatorjevi (preveriTurnir zavrne tudi uvozene)
        pridruzena.turnirji().forEach(lastnistvo::preveriTurnir);
        cilj.turnirji().forEach(lastnistvo::preveriTurnir);
        if (!istiDnevi(najdiTurnir(idTurnir), najdiTurnir(idDrugega))) {
            throw new DomenskaIzjema("Mize lahko delita samo turnirja na isti dan.");
        }

        Turnir glavni = cilj.glavni();
        Turnir prejsnji = pridruzena.glavni();
        if (glavni.getSteviloMiz() == null && prejsnji.getSteviloMiz() != null) {
            prepisiNastavitve(prejsnji, glavni);
        }
        preveriMizeVIgri(tekmaRepozitorij.naMizahTurnirjev(pridruzena.idji()),
                tekmaRepozitorij.naMizahTurnirjev(cilj.idji()), glavni);

        // kategorije pridruzenih gredo za ze razvrscenimi (privzeti red jih doda sam)
        if (!glavni.getVrstniRedDogodkov().isEmpty() && !prejsnji.getVrstniRedDogodkov().isEmpty()) {
            List<Long> red = new ArrayList<>(glavni.getVrstniRedDogodkov());
            red.addAll(prejsnji.getVrstniRedDogodkov());
            glavni.setVrstniRedDogodkov(red);
        }
        for (Turnir t : pridruzena.turnirji()) {
            t.setIdSkupneMize(glavni.getId());
            t.setVrstniRedTekem(null);
            t.setVrstniRedDogodkov(null);
        }
    }

    /* Turnir zapusti skupne mize: dobi kopijo nastavitev skupine (tekme, ki jih igra,
       ostanejo na svojih mizah). Ce odide glavni, skupino prevzame naslednji. */
    @Transactional
    public void razdruzi(Long idTurnir) {
        Long g = glavniId(idTurnir);
        zakleniSkupine(List.of(g));
        if (!g.equals(glavniId(idTurnir))) {
            throw new DomenskaIzjema(SKUPNE_SPREMENJENE);
        }
        lastnistvo.preveriTurnirPoId(idTurnir);
        SkupneMize skupne = skupne(g);
        if (!skupne.povezane()) {
            throw new DomenskaIzjema("Turnir nima skupnih miz z drugim turnirjem.");
        }
        Turnir glavni = skupne.glavni();
        if (glavni.getId().equals(idTurnir)) {
            Turnir novi = skupne.turnirji().get(1);
            prepisiNastavitve(glavni, novi);
            novi.setVrstniRedTekem(glavni.getVrstniRedTekem());
            novi.setVrstniRedDogodkov(glavni.getVrstniRedDogodkov());
            novi.setIdSkupneMize(null);
            for (Turnir t : skupne.turnirji().subList(2, skupne.turnirji().size())) {
                t.setIdSkupneMize(novi.getId());
            }
        } else {
            Turnir odhaja = najdiTurnir(idTurnir);
            prepisiNastavitve(glavni, odhaja);
            odhaja.setVrstniRedTekem(glavni.getVrstniRedTekem());
            odhaja.setVrstniRedDogodkov(glavni.getVrstniRedDogodkov());
            odhaja.setIdSkupneMize(null);
        }
    }

    // ---------- klic ----------

    /* Klic tekme na mizo ali sprememba klica (premik na drugo mizo, drug sodnik). */
    @Transactional
    public void poklici(Long idTekme, KlicTekmeVnos vnos) {
        zaklepi.zakleniTurnirPoTekmi(idTekme);
        Long idGlavni = zaklepi.zakleniMizePoTekmi(idTekme);
        lastnistvo.preveriTurnirPoTekmi(idTekme);
        Tekma tekma = najdiTekmo(idTekme);
        if (idGlavni == null) {
            throw new DomenskaIzjema(SKUPNE_SPREMENJENE);
        }
        SkupneMize skupne = skupne(idGlavni);
        Turnir glavni = skupne.glavni();
        Dogodek dogodek = tekma.getDogodek();

        if (glavni.getSteviloMiz() == null) {
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
        preveriMizo(glavni, miza);
        if (miza2 != null) {
            if (!dogodek.jeEkipno()) {
                throw new NeveljavenVnosIzjema("Drugo mizo dobi samo ekipno srečanje.");
            }
            if (miza2 == miza) {
                throw new NeveljavenVnosIzjema("Druga miza mora biti druga od prve.");
            }
            preveriMizo(glavni, miza2);
        }
        for (Tekma druga : tekmaRepozitorij.naMizahTurnirjev(skupne.idji())) {
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
        Igralec sodnik = vnos.idSodnika() == null ? null : sodnik(skupne, tekma, vnos.idSodnika());
        tekma.poklici(miza, miza2, sodnik, LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS));
        tekmaRepozitorij.save(tekma);
    }

    /* Preklic klica: tekma se vrne med pripravljene. Ekipna tekma, katere srecanje ze
       ima postavo, se igra naprej (postava jo je zacela) in izgubi samo mizo. Miza se
       samo sprosti, zato zaklep skupnih miz ni potreben. */
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
       druga tekma (tudi drugega turnirja s skupnimi mizami): listek z napacno mizo ne
       sme prestaviti sodnika druge mize. Klice se v transakciji potrditve, za vpisom
       izida (zaklep turnirja tekme je ze vzet). */
    void zapisiMizoZListka(Long idTekme, Integer miza) {
        if (miza == null) {
            return;
        }
        Long idGlavni = zaklepi.zakleniMizePoTekmi(idTekme);
        if (idGlavni == null) {
            return; // povezava se je ravno spremenila: miza z listka ni nujna
        }
        Tekma tekma = tekmaRepozitorij.najdiZVsem(idTekme).orElse(null);
        if (tekma == null || tekma.getMiza() != null || tekma.getStatus() != StatusTekme.KONCANA) {
            return;
        }
        SkupneMize skupne = skupne(idGlavni);
        Turnir glavni = skupne.glavni();
        if (glavni.getSteviloMiz() == null || miza < 1 || miza > glavni.getSteviloMiz()
                || glavni.getIzklopljeneMize().contains(miza)) {
            return;
        }
        if (tekmaRepozitorij.naMizahTurnirjev(skupne.idji()).stream().anyMatch(t -> t.naMizi(miza))) {
            return;
        }
        tekma.setMiza(miza);
        tekmaRepozitorij.save(tekma);
    }

    // ---------- pomozno: skupne mize ----------

    /* Turnirji s skupnimi mizami: glavni (nosi nastavitve miz, vrstni red tekem in
       kategorij) in clani. Turnir brez povezave je skupina enega. */
    private record SkupneMize(Turnir glavni, List<Turnir> turnirji) {

        List<Long> idji() {
            return turnirji.stream().map(Turnir::getId).toList();
        }

        boolean povezane() {
            return turnirji.size() > 1;
        }
    }

    private SkupneMize skupne(Long idGlavni) {
        List<Turnir> turnirji = turnirRepozitorij.skupneMize(idGlavni);
        if (turnirji.isEmpty() || !turnirji.getFirst().getId().equals(idGlavni)) {
            throw new NiNajdenoIzjema("Turnir z id " + idGlavni + " ne obstaja.");
        }
        return new SkupneMize(turnirji.getFirst(), turnirji);
    }

    private Long glavniId(Long idTurnir) {
        return turnirRepozitorij.idGlavnegaZaMize(idTurnir)
                .orElseThrow(() -> new NiNajdenoIzjema("Turnir z id " + idTurnir + " ne obstaja."));
    }

    /* Sprememba nastavitev skupnih miz: najprej turnir glavnega (entiteta z verzijo, ki
       jo pise tudi drugo urejanje turnirja), nato mize skupine. */
    private SkupneMize zakleniNastavitve(Long idTurnir) {
        Long idGlavni = glavniId(idTurnir);
        zaklepi.zakleni(Zaklepi.Vrsta.TURNIR, idGlavni);
        Long poZaklepu = zaklepi.zakleniMizePoTurnirju(idTurnir);
        lastnistvo.preveriTurnirPoId(idTurnir);
        if (!idGlavni.equals(poZaklepu)) {
            throw new DomenskaIzjema(SKUPNE_SPREMENJENE);
        }
        return skupne(idGlavni);
    }

    /* Povezava spremeni turnirje obeh skupin: zaklene vse po vrsti id-jev (dve povezavi
       hkrati se ne cakata navzkriz), nato mize obeh skupin. */
    private void zakleniSkupine(List<Long> glavni) {
        List<Long> sorted = turnirRepozitorij.idjiSkupnihMiz(glavni).stream().sorted().toList();
        for (Long id : sorted) {
            zaklepi.zakleni(Zaklepi.Vrsta.TURNIR, id);
        }
        for (Long id : glavni.stream().distinct().sorted().toList()) {
            zaklepi.zakleni(Zaklepi.Vrsta.MIZE, id);
        }
    }

    private static void prepisiNastavitve(Turnir iz, Turnir v) {
        v.setSteviloMiz(iz.getSteviloMiz());
        v.setIzklopljeneMize(iz.getIzklopljeneMize());
        v.setPorazenecSodi(iz.isPorazenecSodi());
        v.setPocitekMin(iz.getPocitekMin());
    }

    /* Pred povezavo: tekme v igri obeh skupin morajo ostati na veljavnih, razlicnih mizah. */
    private static void preveriMizeVIgri(List<Tekma> pridruzeni, List<Tekma> cilj, Turnir glavni) {
        Set<Integer> zasedene = new HashSet<>();
        cilj.forEach(t -> zasedene.addAll(mizeTekme(t)));
        for (Tekma t : pridruzeni) {
            for (Integer miza : mizeTekme(t)) {
                if (zasedene.contains(miza)) {
                    throw new DomenskaIzjema("Na mizi " + miza + " se igra v obeh turnirjih. Pred povezavo "
                            + "vpiši izid ali prekliči enega od klicev.");
                }
                if (glavni.getSteviloMiz() == null || miza > glavni.getSteviloMiz()
                        || glavni.getIzklopljeneMize().contains(miza)) {
                    throw new DomenskaIzjema("Na mizi " + miza + " se igra, skupne mize pa je nimajo. Pred "
                            + "povezavo vpiši izid ali prekliči klic.");
                }
            }
        }
    }

    /* Turnirja se dotikata istih dni (enodnevni ima samo zacetek). */
    private static boolean istiDnevi(Turnir a, Turnir b) {
        if (a.getDatumZacetka() == null || b.getDatumZacetka() == null) {
            return false;
        }
        LocalDate konecA = a.getDatumKonca() != null ? a.getDatumKonca() : a.getDatumZacetka();
        LocalDate konecB = b.getDatumKonca() != null ? b.getDatumKonca() : b.getDatumZacetka();
        return !a.getDatumZacetka().isAfter(konecB) && !b.getDatumZacetka().isAfter(konecA);
    }

    /* Kategorije skupine v vrstnem redu kola (V78): kot jih je razvrstil organizator,
       ostale za njimi po turnirju (glavni prvi) in nastanku kategorije. */
    private List<Dogodek> redDogodkov(SkupneMize skupne) {
        Map<Long, Integer> turnir = new HashMap<>();
        for (Turnir t : skupne.turnirji()) {
            turnir.putIfAbsent(t.getId(), turnir.size());
        }
        Map<Long, Integer> mesto = new HashMap<>();
        for (Long id : skupne.glavni().getVrstniRedDogodkov()) {
            mesto.putIfAbsent(id, mesto.size());
        }
        return dogodekRepozitorij.zaTurnirje(skupne.idji()).stream()
                .sorted(Comparator.comparing((Dogodek d) -> mesto.getOrDefault(d.getId(), Integer.MAX_VALUE))
                        .thenComparing(d -> turnir.getOrDefault(d.getTurnir().getId(), Integer.MAX_VALUE))
                        .thenComparing(Dogodek::getId))
                .toList();
    }

    /* Kola skupin kategorije v vrstnem redu igranja: samo pri kategoriji v teku (posnetek
       ima takrat vse njene tekme, zato je zadnje kolo skupin pravo). */
    private static SkupneMizeDto skupneDto(SkupneMize skupne, List<Dogodek> dogodki,
                                           Map<Long, Integer> zadnjeKoloSkupin) {
        return new SkupneMizeDto(
                skupne.turnirji().stream().map(MizeStoritev::turnirMiz).toList(),
                dogodki.stream().map(d -> {
                    Integer zadnje = d.getStatus() == StatusTekmovanja.V_TEKU ? zadnjeKoloSkupin.get(d.getId()) : null;
                    List<Integer> kola = zadnje == null ? List.of() : PredlogTekem.redKol(d.getVrstniRedKol(), zadnje);
                    return new KategorijaMizDto(d.getId(), d.getIme(), d.getTurnir().getId(),
                            d.getTurnir().getIme(), d.getStatus(), d.jeEkipno(), kola,
                            !d.getVrstniRedKol().isEmpty());
                }).toList(),
                !skupne.glavni().getVrstniRedDogodkov().isEmpty());
    }

    private static TurnirMizDto turnirMiz(Turnir t) {
        return new TurnirMizDto(t.getId(), t.getIme(), t.getDatumZacetka());
    }

    // ---------- pomozno: preverbe ----------

    private static void preveriMizo(Turnir glavni, int miza) {
        if (miza < 1 || miza > glavni.getSteviloMiz()) {
            throw new NeveljavenVnosIzjema("Miza " + miza + " ne obstaja - turnir ima mize od 1 do "
                    + glavni.getSteviloMiz() + ".");
        }
        if (glavni.getIzklopljeneMize().contains(miza)) {
            throw new DomenskaIzjema("Miza " + miza + " je izklopljena.");
        }
    }

    /* Sodnik je oseba s prijavo na enega od turnirjev s skupnimi mizami, ki v tej
       tekmi ne igra. Ali je zaseden drugje, odloci organizator (vmesnik ga opozori). */
    private Igralec sodnik(SkupneMize skupne, Tekma tekma, Long idSodnika) {
        if (!osebeTurnirjev(skupne.idji()).contains(idSodnika)) {
            throw new NeveljavenVnosIzjema(skupne.povezane()
                    ? "Sodnik mora biti igralec, prijavljen na enega od turnirjev s temi mizami."
                    : "Sodnik mora biti igralec, prijavljen na ta turnir.");
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

    private Set<Long> osebeTurnirjev(List<Long> turnirji) {
        Set<Long> osebe = new HashSet<>();
        prijavaRepozitorij.igralciTurnirjev(turnirji, Prijava.StatusPrijave.ODJAVLJEN)
                .forEach(v -> osebe.add(((Number) v[1]).longValue()));
        prijavaRepozitorij.soigralciTurnirjev(turnirji, Prijava.StatusPrijave.ODJAVLJEN)
                .forEach(v -> osebe.add(((Number) v[1]).longValue()));
        kaderRepozitorij.igralciEkipnihTurnirjev(turnirji)
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

    private static PredlogTekem.Nastavitve nastavitve(Turnir glavni, List<Dogodek> redDogodkov) {
        Map<Long, List<Integer>> redKol = new HashMap<>();
        for (Dogodek d : redDogodkov) {
            if (!d.getVrstniRedKol().isEmpty()) {
                redKol.put(d.getId(), d.getVrstniRedKol());
            }
        }
        return new PredlogTekem.Nastavitve(glavni.getSteviloMiz() == null ? 0 : glavni.getSteviloMiz(),
                glavni.getIzklopljeneMize(), glavni.isPorazenecSodi(), glavni.getPocitekMin(),
                glavni.getVrstniRedTekem(), redDogodkov.stream().map(Dogodek::getId).toList(), redKol);
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
            Turnir turnir = t.getDogodek().getTurnir();
            return new TekmaMizeDto(TekmaDto.iz(t), t.getDogodek().getId(), t.getDogodek().getIme(),
                    del(t), t.getDogodek().jeEkipno(), srecanja.get(t.getId()), t.getMiza(), t.getMiza2(),
                    t.getPoklicanaOb(), oseba(v.sodnik()), List.copyOf(osebe), turnir.getId(), turnir.getIme());
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

        List<PredlogTekem.Tekma> vhod = new ArrayList<>();
        for (Tekma t : tekme) {
            Dogodek d = t.getDogodek();
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
                    t.getIdSkupina() == null ? null : skupine.get(t.getIdSkupina())));
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

}
