/* Branje sodniskih listkov s fotografije (V68): organizator fotografira listek,
   streznik predlaga izid, organizator ga potrdi.

   Potek enega branja:
   1. sprejmi: fotografija (JPEG brez metapodatkov) se shrani, branje dobi stanje
      V_VRSTI in zahteva se takoj konca - organizator ze fotografira naslednjega.
   2. obdelaj (v ozadju, BranjeListkovNastavitve): QR koda doloci tekmo, bralnik
      (LokalniBralnikListka, lastna mreza stevk) prepise tocke; brez berljive kode
      izbere tekmo organizator. Stanje PREBRANO ali NAPAKA.
   3. potrdi: izid, kot ga je organizator potrdil ali popravil, gre skozi
      TekmaStoritev.vnesiRezultat - isto pot in iste preverbe kot rocni vnos.
      Branje samo nikoli ne zapise izida.

   Branje (QR koda, poravnava, mreza stevk; nekaj sto milisekund) tece BREZ odprte
   transakcije, zato ima obdelaj dve kratki transakciji okrog njega.

   Dostop: lastnik turnirja ali admin (LastnistvoStoritev); dokler je
   turnirko.branje-listkov.samo-admin vklopljen, samo admin - preizkus pred
   meritvijo natancnosti na pravih listkih.

   Merilo natancnosti: ob potrditvi se zapise, ali je organizator predlog
   spremenil (popravljeno). Seznam vrne, koliko potrjenih je bilo brez popravka. */
package si.turnirko.storitve;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import si.turnirko.dto.BranjaTurnirjaDto;
import si.turnirko.dto.BranjeListkaDto;
import si.turnirko.dto.BranjeListkaDto.KandidatListka;
import si.turnirko.dto.BranjeListkaDto.TekmaListka;
import si.turnirko.dto.NizVnos;
import si.turnirko.dto.PotrditevBranjaVnos;
import si.turnirko.dto.PredlogIzidaDto;
import si.turnirko.dto.StanjeBranjaListkovDto;
import si.turnirko.dto.TekmaDto;
import si.turnirko.dto.VnosRezultata;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.izjeme.PrepovedanoIzjema;
import si.turnirko.izjeme.PrevecZahtevIzjema;
import si.turnirko.modeli.BranjeListka;
import si.turnirko.modeli.IzidTekme;
import si.turnirko.modeli.PrepoznavaTekme;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.Skupina;
import si.turnirko.modeli.SlikaBranjaListka;
import si.turnirko.modeli.StanjeBranja;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.Tekma;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.pomozno.ObdelavaSlik;
import si.turnirko.repozitoriji.BranjeListkaRepozitorij;
import si.turnirko.repozitoriji.SkupinaRepozitorij;
import si.turnirko.repozitoriji.SlikaBranjaListkaRepozitorij;
import si.turnirko.repozitoriji.TekmaRepozitorij;
import si.turnirko.repozitoriji.TurnirRepozitorij;
import si.turnirko.storitve.UjemanjeTekmeListka.Kandidat;
import si.turnirko.storitve.UjemanjeTekmeListka.Oseba;

@Service
public class BranjeListkovStoritev {

    private static final Logger log = LoggerFactory.getLogger(BranjeListkovStoritev.class);

    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /* Daljsa stranica shranjene fotografije. Stevke na listku (105 x 99 mm, cez
       ves posnetek) so pri 2000 px visoke ~70 px, QR koda ima ~8 px na modul;
       vec samo podrazi klic modela. */
    static final int NAJVEC_STRANICA = 2000;
    /* Vsako branje stane: ena fotografija na 5 s je hitrejse, kot organizator
       sploh prinasa listke, zato meja ustavi le zanko ali zlorabo. */
    static final int NAJVEC_V_OKNU = 120;
    static final Duration OKNO = Duration.ofMinutes(10);
    /* Fotografija ostane toliko dni po turnirju (odlocitev lastnika, 19. 11. 2026). */
    static final int DNI_HRAMBE = 30;
    /* Zakljucenih branj (potrjenih, zavrzenih) seznam pokaze toliko zadnjih. */
    static final int ZADNJIH_ZAKLJUCENIH = 30;
    /* Listek brez kode: toliko tekem, ki cakajo na izid, je ponujenih za izbiro. */
    static final int NAJVEC_IZBIRE = 200;

    private final BranjeListkaRepozitorij branjaRepozitorij;
    private final SlikaBranjaListkaRepozitorij slikeRepozitorij;
    private final TurnirRepozitorij turnirRepozitorij;
    private final TekmaRepozitorij tekmaRepozitorij;
    private final SkupinaRepozitorij skupinaRepozitorij;
    private final LastnistvoStoritev lastnistvo;
    private final TekmaStoritev tekmaStoritev;
    private final BralnikListka bralnik;
    private final UcenjeIzListkov ucenje;
    private final Executor izvajalec;
    private final TransactionTemplate transakcija;
    private boolean samoAdmin;

    public BranjeListkovStoritev(BranjeListkaRepozitorij branjaRepozitorij,
                                 SlikaBranjaListkaRepozitorij slikeRepozitorij,
                                 TurnirRepozitorij turnirRepozitorij,
                                 TekmaRepozitorij tekmaRepozitorij,
                                 SkupinaRepozitorij skupinaRepozitorij,
                                 LastnistvoStoritev lastnistvo,
                                 TekmaStoritev tekmaStoritev,
                                 BralnikListka bralnik,
                                 UcenjeIzListkov ucenje,
                                 @Qualifier("branjeListkovIzvajalec") Executor izvajalec,
                                 PlatformTransactionManager upraviteljTransakcij,
                                 @Value("${turnirko.branje-listkov.samo-admin:true}") boolean samoAdmin) {
        this.branjaRepozitorij = branjaRepozitorij;
        this.slikeRepozitorij = slikeRepozitorij;
        this.turnirRepozitorij = turnirRepozitorij;
        this.tekmaRepozitorij = tekmaRepozitorij;
        this.skupinaRepozitorij = skupinaRepozitorij;
        this.lastnistvo = lastnistvo;
        this.tekmaStoritev = tekmaStoritev;
        this.bralnik = bralnik;
        this.ucenje = ucenje;
        this.izvajalec = izvajalec;
        this.transakcija = new TransactionTemplate(upraviteljTransakcij);
        this.samoAdmin = samoAdmin;
    }

    /* Samo za teste: preizkus organizatorja brez lastnega konteksta Springa. */
    void nastaviSamoAdmin(boolean samoAdmin) {
        this.samoAdmin = samoAdmin;
    }

    // ---------- Dostop ----------

    public StanjeBranjaListkovDto stanje() {
        Uporabnik jaz = lastnistvo.trenutni();
        if (jaz == null) {
            return new StanjeBranjaListkovDto(false, "Za branje listkov se prijavi.");
        }
        if (jaz.getVloga() != Vloga.ADMIN && jaz.getVloga() != Vloga.ORGANIZATOR) {
            return new StanjeBranjaListkovDto(false, "Branje listkov je namenjeno organizatorjem.");
        }
        if (!bralnik.jeNastavljen()) {
            return new StanjeBranjaListkovDto(false, "Branje listkov na tem strežniku ni nastavljeno.");
        }
        if (samoAdmin && jaz.getVloga() != Vloga.ADMIN) {
            return new StanjeBranjaListkovDto(false, "Branje listkov je v preizkusu in še ni na voljo.");
        }
        return new StanjeBranjaListkovDto(true, null);
    }

    private Uporabnik preveriDostop(Long idTurnir) {
        StanjeBranjaListkovDto stanje = stanje();
        if (!stanje.omogoceno()) {
            throw new PrepovedanoIzjema(stanje.razlog());
        }
        lastnistvo.preveriTurnirPoId(idTurnir);
        return lastnistvo.trenutni();
    }

    // ---------- Sprejem fotografije ----------

    @Transactional
    public BranjeListkaDto sprejmi(Long idTurnir, byte[] vsebina) {
        Uporabnik jaz = preveriDostop(idTurnir);
        LocalDateTime zdaj = LocalDateTime.now();
        if (branjaRepozitorij.countByNalozilIdAndUstvarjenoObAfter(jaz.getId(), zdaj.minus(OKNO)) >= NAJVEC_V_OKNU) {
            throw new PrevecZahtevIzjema("Preveč fotografij v kratkem času. Počakaj nekaj minut.");
        }
        BufferedImage slika = ObdelavaSlik.pomanjsaj(ObdelavaSlik.preberi(vsebina), NAJVEC_STRANICA);
        ObdelavaSlik.Zapis zapis = ObdelavaSlik.zapisiFotografijo(slika);

        BranjeListka branje = branjaRepozitorij.save(
                new BranjeListka(turnirRepozitorij.getReferenceById(idTurnir), jaz, zdaj));
        slikeRepozitorij.save(new SlikaBranjaListka(branje.getId(), zapis.vsebina(), zapis.sirina(), zapis.visina()));
        vVrsto(branje.getId());
        return dto(branje, Map.of(), Map.of(), List.of());
    }

    /* Branje gre na izvajalca sele po potrditvi transakcije - prej ga nit v
       ozadju v bazi se ne bi videla. */
    private void vVrsto(Long idBranja) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    izvajalec.execute(() -> obdelaj(idBranja));
                }
            });
        } else {
            izvajalec.execute(() -> obdelaj(idBranja));
        }
    }

    // ---------- Branje (v ozadju) ----------

    private record Vhod(Long idTurnir, byte[] slika) {}

    /* Prebere kodo in tocke ter zapise predlog. Ne vrze nicesar: vsaka napaka
       konca v stanju NAPAKA z razlago za organizatorja. */
    public void obdelaj(Long idBranja) {
        Vhod vhod = transakcija.execute(s -> {
            BranjeListka branje = branjaRepozitorij.findById(idBranja).orElse(null);
            if (branje == null || branje.getStanje() != StanjeBranja.V_VRSTI) {
                return null;
            }
            SlikaBranjaListka slika = slikeRepozitorij.findById(idBranja).orElse(null);
            if (slika == null) {
                oznaciNapako(branje, "Fotografije ni več.", List.of());
                return null;
            }
            return new Vhod(branje.getTurnir().getId(), slika.getVsebina());
        });
        if (vhod == null) {
            return;
        }

        List<Long> kode = preberiKode(vhod.slika());
        BralnikListka.Branje prepis;
        try {
            prepis = bralnik.preberi(vhod.slika());
        } catch (BralnikListka.NapakaBranjaListka e) {
            zapisiNapako(idBranja, e.getMessage(), kode);
            return;
        } catch (RuntimeException e) {
            log.error("Branje listka {} ni uspelo", idBranja, e);
            zapisiNapako(idBranja, "Branje ni uspelo. Poskusi znova.", kode);
            return;
        }

        transakcija.executeWithoutResult(s -> {
            BranjeListka branje = branjaRepozitorij.findById(idBranja).orElse(null);
            if (branje == null || branje.getStanje() != StanjeBranja.V_VRSTI) {
                return;
            }
            prepoznajTekmo(branje, vhod.idTurnir(), kode, prepis.listek());
            branje.setPrebrano(vJson(prepis.listek()));
            branje.setModel(prepis.model());
            branje.setStanje(StanjeBranja.PREBRANO);
            branje.setPrebranoOb(LocalDateTime.now());
        });
    }

    private static List<Long> preberiKode(byte[] jpeg) {
        try {
            BufferedImage slika = ImageIO.read(new ByteArrayInputStream(jpeg));
            return slika == null ? List.of() : KodaListka.preberiQr(slika);
        } catch (IOException | RuntimeException e) {
            return List.of();
        }
    }

    /* Tekma po QR kodi, sicer po natisnjeni stevilki (ki jo je prebral model in
       se zato preveri z imeni), sicer po imenih. */
    private void prepoznajTekmo(BranjeListka branje, Long idTurnir, List<Long> kode, PrebranListek listek) {
        if (kode.size() > 1) {
            branje.setNapaka("Na fotografiji je več listkov. Fotografiraj vsakega posebej.");
            return;
        }
        if (kode.size() == 1) {
            Tekma tekma = tekmaTurnirja(kode.getFirst(), idTurnir);
            if (tekma != null) {
                branje.setTekma(tekma);
                branje.setPrepoznava(PrepoznavaTekme.KODA);
                return;
            }
            branje.setNapaka("Koda na listku ne pripada tekmi tega turnirja.");
        }
        Long izNatisnjene = KodaListka.izNatisnjene(listek.koda()).orElse(null);
        if (izNatisnjene != null) {
            Tekma tekma = tekmaTurnirja(izNatisnjene, idTurnir);
            // prebrana stevka je lahko napacna (T-48213 / T-48218): veljajo samo, ce se ujemajo imena
            if (tekma != null && (nimaImen(listek) || ujemanjeImen(listek, tekma) >= UjemanjeTekmeListka.PRAG_IZBIRE)) {
                branje.setTekma(tekma);
                branje.setPrepoznava(PrepoznavaTekme.KODA);
                return;
            }
        }
        UjemanjeTekmeListka.Izid izid = UjemanjeTekmeListka.najdi(listek.imeLevo(), listek.imeDesno(),
                listek.kategorija(), kandidati(tekmaRepozitorij.cakajoceNaListekZaTurnir(idTurnir)));
        if (izid.izbrana() != null) {
            branje.setTekma(tekmaRepozitorij.getReferenceById(izid.izbrana().idTekme()));
            branje.setPrepoznava(PrepoznavaTekme.IMENA);
        }
    }

    private Tekma tekmaTurnirja(Long idTekme, Long idTurnir) {
        return tekmaRepozitorij.najdiZVsem(idTekme)
                .filter(t -> t.getDogodek().getTurnir().getId().equals(idTurnir))
                .orElse(null);
    }

    private void zapisiNapako(Long idBranja, String sporocilo, List<Long> kode) {
        transakcija.executeWithoutResult(s -> branjaRepozitorij.findById(idBranja)
                .filter(b -> b.getStanje() == StanjeBranja.V_VRSTI)
                .ifPresent(b -> oznaciNapako(b, sporocilo, kode)));
    }

    /* Ob napaki bralnika se tekma po kodi vseeno zapise: organizator lahko izid
       vpise rocno ob fotografiji, ne da bi tekmo iskal. */
    private void oznaciNapako(BranjeListka branje, String sporocilo, List<Long> kode) {
        branje.setStanje(StanjeBranja.NAPAKA);
        branje.setNapaka(sporocilo);
        branje.setPrebranoOb(LocalDateTime.now());
        if (kode.size() == 1) {
            Tekma tekma = tekmaTurnirja(kode.getFirst(), branje.getTurnir().getId());
            if (tekma != null) {
                branje.setTekma(tekma);
                branje.setPrepoznava(PrepoznavaTekme.KODA);
            }
        }
    }

    // ---------- Seznam ----------

    @Transactional(readOnly = true)
    public BranjaTurnirjaDto seznam(Long idTurnir) {
        preveriDostop(idTurnir);
        List<BranjeListka> vsa = branjaRepozitorij.najdiZaTurnir(idTurnir);
        List<BranjeListka> prikaz = new ArrayList<>();
        int zakljucenih = 0;
        int potrjenih = 0;
        int brezPopravka = 0;
        for (BranjeListka b : vsa) {
            if (b.getStanje() == StanjeBranja.POTRJENO) {
                potrjenih++;
                if (Boolean.FALSE.equals(b.getPopravljeno())) {
                    brezPopravka++;
                }
            }
            if (b.getStanje().jeOdprto() || zakljucenih++ < ZADNJIH_ZAKLJUCENIH) {
                prikaz.add(b);
            }
        }

        boolean rabimKandidate = prikaz.stream()
                .anyMatch(b -> b.getStanje() == StanjeBranja.PREBRANO && b.getTekma() == null);
        List<Tekma> cakajoce = rabimKandidate ? tekmaRepozitorij.cakajoceNaListekZaTurnir(idTurnir) : List.of();
        Set<Long> idji = prikaz.stream().map(BranjeListka::getTekma).filter(Objects::nonNull)
                .map(Tekma::getId).collect(Collectors.toSet());
        Map<Long, Tekma> tekme = new HashMap<>();
        cakajoce.forEach(t -> tekme.put(t.getId(), t));
        idji.removeAll(tekme.keySet());
        if (!idji.isEmpty()) {
            tekmaRepozitorij.najdiZVsemPoIdjih(idji).forEach(t -> tekme.put(t.getId(), t));
        }
        Map<Long, String> skupine = oznakeSkupin(tekme.values());

        List<BranjeListkaDto> branja = prikaz.stream().map(b -> dto(b, tekme, skupine, cakajoce)).toList();
        return new BranjaTurnirjaDto(branja, potrjenih, brezPopravka);
    }

    private Map<Long, String> oznakeSkupin(Collection<Tekma> tekme) {
        Set<Long> idji = tekme.stream().map(Tekma::getIdSkupina).filter(Objects::nonNull).collect(Collectors.toSet());
        if (idji.isEmpty()) {
            return Map.of();
        }
        return skupinaRepozitorij.findAllById(idji).stream()
                .collect(Collectors.toMap(Skupina::getId, Skupina::getOznaka));
    }

    private BranjeListkaDto dto(BranjeListka b, Map<Long, Tekma> tekme, Map<Long, String> skupine,
                                List<Tekma> cakajoce) {
        PrebranListek listek = b.getPrebrano() == null ? null : izJson(b.getPrebrano());
        TekmaListka tekma = null;
        PredlogIzidaDto predlog = null;
        List<KandidatListka> kandidati = List.of();

        Tekma t = b.getTekma() == null ? null : tekme.get(b.getTekma().getId());
        if (t != null) {
            tekma = tekmaListka(t, skupine);
            if (listek != null) {
                predlog = predlog(b, listek, t);
            }
        } else if (b.getStanje() == StanjeBranja.PREBRANO && listek != null && nimaImen(listek)) {
            // listek brez kode (prazen listek): bralnik imen ne bere, tekmo izbere organizator
            kandidati = cakajoce.stream()
                    .sorted(Comparator.comparing((Tekma k) -> k.getDogodek().getIme())
                            .thenComparing(Tekma::getId))
                    .limit(NAJVEC_IZBIRE)
                    .map(k -> new KandidatListka(tekmaListka(k, skupine), false,
                            PredlogIzidaListka.sestavi(listek, k.getSteviloNizov(), false)))
                    .toList();
        } else if (b.getStanje() == StanjeBranja.PREBRANO && listek != null) {
            UjemanjeTekmeListka.Izid izid = UjemanjeTekmeListka.najdi(listek.imeLevo(), listek.imeDesno(),
                    listek.kategorija(), kandidati(cakajoce));
            Map<Long, Tekma> poId = cakajoce.stream().collect(Collectors.toMap(Tekma::getId, Function.identity()));
            kandidati = izid.kandidati().stream()
                    .map(z -> {
                        Tekma k = poId.get(z.idTekme());
                        return new KandidatListka(tekmaListka(k, skupine), z.obrnjeno(),
                                PredlogIzidaListka.sestavi(listek, k.getSteviloNizov(), z.obrnjeno()));
                    })
                    .toList();
        }
        return new BranjeListkaDto(b.getId(), b.getStanje(), b.getUstvarjenoOb(), b.getPrebranoOb(),
                b.getSlikaIzbrisanaOb() == null, b.getPrepoznava(), tekma, predlog, kandidati,
                listek == null ? null : listek.imeLevo(), listek == null ? null : listek.imeDesno(),
                b.getNapaka(), b.getPopravljeno());
    }

    /* Predlog v straneh tekme z opozorili, ki jih pozna samo streznik: izid je ze
       vpisan, imena na listku niso imena tekme, na posnetku je vec listkov. */
    private PredlogIzidaDto predlog(BranjeListka b, PrebranListek listek, Tekma t) {
        boolean poKodi = b.getPrepoznava() == PrepoznavaTekme.KODA;
        boolean obrnjeno = !poKodi && jeObrnjeno(listek, t);
        PredlogIzidaDto osnova = PredlogIzidaListka.sestavi(listek, t.getSteviloNizov(), obrnjeno);
        List<String> dodatna = new ArrayList<>();
        if (b.getStanje().jeOdprto() && t.getStatus() == StatusTekme.KONCANA) {
            dodatna.add("Ta tekma ima izid že vpisan. Listek je morda fotografiran dvakrat.");
        } else if (b.getStanje().jeOdprto() && t.getStatus() == StatusTekme.CAKA) {
            dodatna.add("Tekma še čaka na igralca iz prejšnjega kola.");
        }
        if (poKodi && !nimaImen(listek) && ujemanjeImen(listek, t) < UjemanjeTekmeListka.PRAG_IZBIRE) {
            dodatna.add("Imeni na listku (" + Objects.toString(listek.imeLevo(), "?") + " – "
                    + Objects.toString(listek.imeDesno(), "?") + ") se ne ujemata s tekmo.");
        }
        if (b.getStanje() == StanjeBranja.PREBRANO && b.getNapaka() != null) {
            dodatna.add(b.getNapaka());
        }
        if (dodatna.isEmpty()) {
            return osnova;
        }
        List<String> opozorila = new ArrayList<>(dodatna);
        opozorila.addAll(osnova.opozorila());
        return new PredlogIzidaDto(osnova.izidTip(), osnova.dobljeniNizi1(), osnova.dobljeniNizi2(),
                osnova.zmagovalecStran(), osnova.nizi(), opozorila, false);
    }

    private TekmaListka tekmaListka(Tekma t, Map<Long, String> skupine) {
        return new TekmaListka(TekmaDto.iz(t), t.getDogodek().getId(), t.getDogodek().getIme(),
                t.getIdSkupina() == null ? null : skupine.get(t.getIdSkupina()));
    }

    // ---------- Potrditev, zavrnitev, ponovno branje ----------

    @Transactional
    public BranjeListkaDto potrdi(Long idBranja, PotrditevBranjaVnos vnos) {
        BranjeListka branje = najdi(idBranja);
        preveriDostop(branje.getTurnir().getId());
        if (branje.getStanje() != StanjeBranja.PREBRANO && branje.getStanje() != StanjeBranja.NAPAKA) {
            throw new DomenskaIzjema(branje.getStanje() == StanjeBranja.V_VRSTI
                    ? "Listek se še bere. Počakaj nekaj sekund."
                    : "To branje je že zaključeno.");
        }
        Long idTekme = vnos.idTekme() != null ? vnos.idTekme()
                : branje.getTekma() != null ? branje.getTekma().getId() : null;
        if (idTekme == null) {
            throw new NeveljavenVnosIzjema("Izberi tekmo, ki je na listku.");
        }
        Tekma tekma = tekmaTurnirja(idTekme, branje.getTurnir().getId());
        if (tekma == null) {
            throw new NeveljavenVnosIzjema("Tekma ni s tega turnirja.");
        }

        boolean izbranaRocno = branje.getTekma() == null || !branje.getTekma().getId().equals(idTekme);
        PrepoznavaTekme prepoznava = izbranaRocno ? PrepoznavaTekme.ROCNO : branje.getPrepoznava();
        PrebranListek listek = branje.getPrebrano() == null ? null : izJson(branje.getPrebrano());
        PredlogIzidaDto predlog = listek == null ? null
                : PredlogIzidaListka.sestavi(listek, tekma.getSteviloNizov(),
                        prepoznava != PrepoznavaTekme.KODA && jeObrnjeno(listek, tekma));

        tekmaStoritev.vnesiRezultat(idTekme, vnos.izid());

        // napredovanje v mrezi s skupinskimi posodobitvami izprazni sejo: branje
        // od prej je odklopljeno in sprememba nanj bi se tiho izgubila
        branje = najdi(idBranja);
        branje.setTekma(tekmaRepozitorij.getReferenceById(idTekme));
        branje.setPrepoznava(prepoznava);
        branje.setStanje(StanjeBranja.POTRJENO);
        branje.setZakljucenoOb(LocalDateTime.now());
        branje.setPotrjeno(vJson(vnos.izid()));
        branje.setPopravljeno(predlog == null || !jeEnako(predlog, vnos.izid()));
        // bralnik se uci iz potrjenega izida; smer stolpcev je znana samo pri listku s kodo
        ucenje.poPotrditvi(idBranja, vnos.izid(), prepoznava == PrepoznavaTekme.KODA ? Boolean.FALSE : null);
        Tekma koncana = tekmaRepozitorij.najdiZVsem(idTekme).orElse(tekma);
        return dto(branje, Map.of(idTekme, koncana), oznakeSkupin(List.of(koncana)), List.of());
    }

    @Transactional
    public void zavrzi(Long idBranja) {
        BranjeListka branje = najdi(idBranja);
        preveriDostop(branje.getTurnir().getId());
        if (!branje.getStanje().jeOdprto()) {
            throw new DomenskaIzjema("To branje je že zaključeno.");
        }
        branje.setStanje(StanjeBranja.ZAVRZENO);
        branje.setZakljucenoOb(LocalDateTime.now());
    }

    /* Isto fotografijo prebere se enkrat (napaka povezave, ali slab prepis). */
    @Transactional
    public BranjeListkaDto znova(Long idBranja) {
        BranjeListka branje = najdi(idBranja);
        preveriDostop(branje.getTurnir().getId());
        if (branje.getStanje() != StanjeBranja.NAPAKA && branje.getStanje() != StanjeBranja.PREBRANO) {
            throw new DomenskaIzjema("To branje se ne da ponoviti.");
        }
        if (branje.getSlikaIzbrisanaOb() != null) {
            throw new DomenskaIzjema("Fotografija je bila izbrisana.");
        }
        branje.setStanje(StanjeBranja.V_VRSTI);
        branje.setNapaka(null);
        branje.setPrebrano(null);
        branje.setTekma(null);
        branje.setPrepoznava(null);
        vVrsto(branje.getId());
        return dto(branje, Map.of(), Map.of(), List.of());
    }

    @Transactional(readOnly = true)
    public byte[] slika(Long idBranja) {
        BranjeListka branje = najdi(idBranja);
        preveriDostop(branje.getTurnir().getId());
        return slikeRepozitorij.findById(idBranja)
                .map(SlikaBranjaListka::getVsebina)
                .orElseThrow(() -> new NiNajdenoIzjema("Fotografija listka je bila izbrisana."));
    }

    private BranjeListka najdi(Long idBranja) {
        return branjaRepozitorij.najdiZTurnirjem(idBranja)
                .orElseThrow(() -> new NiNajdenoIzjema("Branje listka z id " + idBranja + " ne obstaja."));
    }

    // ---------- Opravila (OpravilaBranjaListkov) ----------

    /* Fotografije, starejse od 30 dni po turnirju; vrstica branja ostane. */
    @Transactional
    public int izbrisiStareSlike(LocalDate danes) {
        LocalDateTime zdaj = LocalDateTime.now();
        List<Long> idji = branjaRepozitorij.zaIzbrisSlike(danes.minusDays(DNI_HRAMBE), zdaj.minusDays(DNI_HRAMBE));
        if (idji.isEmpty()) {
            return 0;
        }
        slikeRepozitorij.izbrisi(idji);
        return branjaRepozitorij.oznaciIzbrisaneSlike(idji, zdaj);
    }

    /* Ob zagonu: branja, ki so ostala v vrsti, ko se je streznik ustavil, nima
       nihce vec v rokah. Organizator jih vidi kot napako z gumbom za ponovitev. */
    @Transactional
    public int prekiniOstalaVVrsti() {
        List<BranjeListka> ostala = branjaRepozitorij.findByStanje(StanjeBranja.V_VRSTI);
        ostala.forEach(b -> {
            b.setStanje(StanjeBranja.NAPAKA);
            b.setNapaka("Branje se je prekinilo (ponovni zagon strežnika). Poskusi znova.");
        });
        return ostala.size();
    }

    // ---------- Pomozno ----------

    private static boolean nimaImen(PrebranListek listek) {
        return (listek.imeLevo() == null || listek.imeLevo().isBlank())
                && (listek.imeDesno() == null || listek.imeDesno().isBlank());
    }

    /* Ujemanje prebranih imen s tekmo v obeh smereh: levi stolpec = prijava 1
       (naravnost) ali levi stolpec = prijava 2 (obrat). */
    private record Smeri(double naravnost, double obrat) {}

    private static Smeri smeri(PrebranListek listek, Tekma t) {
        List<Oseba> s1 = osebe(t.getPrijava1());
        List<Oseba> s2 = osebe(t.getPrijava2());
        return new Smeri(
                Math.min(UjemanjeTekmeListka.ocena(listek.imeLevo(), s1),
                        UjemanjeTekmeListka.ocena(listek.imeDesno(), s2)),
                Math.min(UjemanjeTekmeListka.ocena(listek.imeLevo(), s2),
                        UjemanjeTekmeListka.ocena(listek.imeDesno(), s1)));
    }

    private static double ujemanjeImen(PrebranListek listek, Tekma t) {
        Smeri s = smeri(listek, t);
        return Math.max(s.naravnost(), s.obrat());
    }

    private static boolean jeObrnjeno(PrebranListek listek, Tekma t) {
        Smeri s = smeri(listek, t);
        return s.obrat() > s.naravnost();
    }

    private static List<Kandidat> kandidati(List<Tekma> tekme) {
        return tekme.stream()
                .map(t -> new Kandidat(t.getId(), osebe(t.getPrijava1()), osebe(t.getPrijava2()),
                        t.getDogodek().getIme()))
                .toList();
    }

    private static List<Oseba> osebe(Prijava p) {
        if (p == null || p.jeEkipa()) {
            return List.of();
        }
        List<Oseba> osebe = new ArrayList<>();
        osebe.add(new Oseba(p.getIgralec().getIme(), p.getIgralec().getPriimek()));
        if (p.jePar()) {
            osebe.add(new Oseba(p.getIgralec2().getIme(), p.getIgralec2().getPriimek()));
        }
        return osebe;
    }

    /* Ali je organizator potrdil natanko predlog (merilo natancnosti bralnika). */
    static boolean jeEnako(PredlogIzidaDto predlog, VnosRezultata vnos) {
        IzidTekme izid = vnos.izidTip() == null ? IzidTekme.IGRANO : vnos.izidTip();
        if (izid != predlog.izidTip()) {
            return false;
        }
        if (izid == IzidTekme.BREZ_BOJA || izid == IzidTekme.DISKVALIFIKACIJA) {
            return Objects.equals(vnos.zmagovalecStran(), predlog.zmagovalecStran());
        }
        if (izid == IzidTekme.PREDAJA && !Objects.equals(vnos.zmagovalecStran(), predlog.zmagovalecStran())) {
            return false;
        }
        if (!Objects.equals(vnos.dobljeniNizi1(), predlog.dobljeniNizi1())
                || !Objects.equals(vnos.dobljeniNizi2(), predlog.dobljeniNizi2())) {
            return false;
        }
        List<NizVnos> nizi = vnos.nizi() == null ? List.of() : vnos.nizi();
        if (nizi.size() != predlog.nizi().size()) {
            return false;
        }
        for (int i = 0; i < nizi.size(); i++) {
            if (!Objects.equals(nizi.get(i).tocke1(), predlog.nizi().get(i).tocke1())
                    || !Objects.equals(nizi.get(i).tocke2(), predlog.nizi().get(i).tocke2())) {
                return false;
            }
        }
        return true;
    }

    private static String vJson(Object vrednost) {
        try {
            return JSON.writeValueAsString(vrednost);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static PrebranListek izJson(String json) {
        try {
            return JSON.readValue(json, PrebranListek.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Zapis branja ni berljiv.", e);
        }
    }
}
