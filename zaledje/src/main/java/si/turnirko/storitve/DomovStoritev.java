/* Podatki, ki jih potrebuje domaca stran in jih ni v obstojecih pogledih.

   Dvoje: izbor lig, ki jih uporabnik spremlja (osebna nastavitev racuna), in
   povzetek vsake take lige (kolo od kol, vrh lestvice, naslednje kolo).
   Gost izbora nima - njegov brskalnik posilja id-je lig, ki si jih je zapomnil
   sam; kadar jih ni, pokazemo lige, ki so v teku. */
package si.turnirko.storitve;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.DomovLigaDto;
import si.turnirko.dto.LestvicaEkipeDto;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.izjeme.PrepovedanoIzjema;
import si.turnirko.modeli.Liga;
import si.turnirko.modeli.Srecanje;
import si.turnirko.modeli.SpremljanaLiga;
import si.turnirko.modeli.StatusSrecanja;
import si.turnirko.modeli.StatusTekmovanja;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.LigaRepozitorij;
import si.turnirko.repozitoriji.SpremljanaLigaRepozitorij;
import si.turnirko.repozitoriji.SrecanjeRepozitorij;

@Service
public class DomovStoritev {

    /* Koliko lig pokazemo tistemu, ki svojega izbora nima. Meja je ista kot
       za adminov izbor (LigaStoritev.LIG_NA_DOMACI): kar se privzeto pokaze,
       mora biti enako dolgo kot to, kar admin postavi na njegovo mesto -
       drugace se domaca stran ob prvi oznaceni ligi skrci. */
    private static final int PRIVZETO_LIG = LigaStoritev.LIG_NA_DOMACI;

    /* Vrh mini razpredelnice: prve tri ekipe. Vec jih vrstica lige ne prenese. */
    private static final int EKIP_NA_VRHU = 3;

    private final LigaRepozitorij ligaRepozitorij;
    private final SrecanjeRepozitorij srecanjeRepozitorij;
    private final SpremljanaLigaRepozitorij spremljanaLigaRepozitorij;
    private final LestvicaLigeStoritev lestvicaLigeStoritev;
    private final LastnistvoStoritev lastnistvo;
    private final NarocninaStoritev narocnina;

    public DomovStoritev(LigaRepozitorij ligaRepozitorij,
                         SrecanjeRepozitorij srecanjeRepozitorij,
                         SpremljanaLigaRepozitorij spremljanaLigaRepozitorij,
                         LestvicaLigeStoritev lestvicaLigeStoritev,
                         LastnistvoStoritev lastnistvo,
                         NarocninaStoritev narocnina) {
        this.ligaRepozitorij = ligaRepozitorij;
        this.srecanjeRepozitorij = srecanjeRepozitorij;
        this.spremljanaLigaRepozitorij = spremljanaLigaRepozitorij;
        this.lestvicaLigeStoritev = lestvicaLigeStoritev;
        this.lastnistvo = lastnistvo;
        this.narocnina = narocnina;
    }

    // ---------- Izbor spremljanih lig ----------

    /* Lige, ki jih spremlja prijavljeni uporabnik. */
    @Transactional(readOnly = true)
    public List<Long> mojeLige() {
        return spremljanaLigaRepozitorij.idjiZaRacun(zahtevajPrijavo().getId());
    }

    /* Doda ligo v izbor; ponovni klic ne naredi nicesar (idempotentno, ker je
       kvadratek preklop in ne stevec). Dodajanje je Premium funkcija - brez
       nje racun ostane na kar mu je admin postavil oz. kar je gost ze videl
       (glej NarocninaStoritev); ODJAVA spremljanja (nehajSpremljati) in BRANJE
       (mojeLige) ostaneta odprta vsakemu prijavljenemu. */
    @Transactional
    public List<Long> spremljaj(Long idLiga) {
        Uporabnik jaz = zahtevajPrijavo();
        if (jaz.getVloga() != Vloga.ADMIN && !narocnina.imaPremium(jaz)) {
            throw new PrepovedanoIzjema("Spremljanje lig je Premium funkcija.");
        }
        if (!ligaRepozitorij.existsById(idLiga)) {
            throw new NiNajdenoIzjema("Liga z id " + idLiga + " ne obstaja.");
        }
        if (!spremljanaLigaRepozitorij.existsByIdRacunAndIdLiga(jaz.getId(), idLiga)) {
            spremljanaLigaRepozitorij.save(new SpremljanaLiga(jaz.getId(), idLiga));
        }
        return spremljanaLigaRepozitorij.idjiZaRacun(jaz.getId());
    }

    @Transactional
    public List<Long> nehajSpremljati(Long idLiga) {
        Uporabnik jaz = zahtevajPrijavo();
        spremljanaLigaRepozitorij.deleteByIdRacunAndIdLiga(jaz.getId(), idLiga);
        return spremljanaLigaRepozitorij.idjiZaRacun(jaz.getId());
    }

    /* Izbor je last racuna, zato brez prijave ne obstaja. Veriga to pot ze
       zapre; preverba je tu druga obramba in daje razumljivo sporocilo. */
    private Uporabnik zahtevajPrijavo() {
        Uporabnik jaz = lastnistvo.trenutni();
        if (jaz == null) {
            throw new PrepovedanoIzjema("Za spremljanje lig je potrebna prijava.");
        }
        return jaz;
    }

    // ---------- Povzetki lig ----------

    /* Povzetki lig za sklop "Lige" na domaci strani.

       Vrstni red odlocanja je vrstni red namernosti:
         1. "idji" - izbor prijavljenega racuna. Kdor si je sklop sestavil sam,
            ga vidi takega, kot ga je sestavil (dolzine ne omejujemo: to je
            njegova odlocitev in ne izlozba).
         2. lige, v katerih igra igralec s Premium (kader ekipe, ne zakljucene)
            - njegovo dejstvo, ne zvezina izlozba: kdor v ligi igra, jo pricakuje
            na vhodni strani, prej kot ligo, ki jo je zveza izbrala za vse. Brez
            Premium in za gosta te stopnje ni.
         3. lige, ki jih je admin postavil na domaco stran (najvec dve) - to je
            privzeti pogled gosta in vsakega, ki svojega izbora nima.
         4. "ogledane" - lige, ki si jih je gost nazadnje ogledal (spomin
            njegovega brskalnika). Sele TU, ker ogled ni izbira: adminova
            uredniska odlocitev ne sme odpasti zato, ker je gost pred tednom
            odprl neko ligo.
         5. lige v teku - nova namestitev, kjer se ni odlocil nihce. */
    @Transactional(readOnly = true)
    public List<DomovLigaDto> povzetkiLig(List<Long> idji, List<Long> ogledane) {
        if (idji != null && !idji.isEmpty()) {
            return povzetki(poIzboru(idji), false);
        }
        List<Liga> igralceve = ligeIgralcevihEkip();
        if (!igralceve.isEmpty()) {
            return povzetki(igralceve, true);
        }
        List<Liga> lige;
        List<Liga> izpostavljene = ligaRepozitorij.najdiNaDomaci();
        if (!izpostavljene.isEmpty()) {
            lige = izpostavljene.stream().limit(PRIVZETO_LIG).toList();
        } else if (ogledane != null && !ogledane.isEmpty()) {
            lige = poIzboru(ogledane).stream().limit(PRIVZETO_LIG).toList();
        } else {
            lige = ligaRepozitorij.najdiVse().stream()
                    .filter(l -> l.getStatus() == StatusTekmovanja.V_TEKU)
                    .limit(PRIVZETO_LIG)
                    .toList();
        }
        return povzetki(lige, false);
    }

    private List<DomovLigaDto> povzetki(List<Liga> lige, boolean izEkipe) {
        return lige.stream().map(l -> povzetek(l, izEkipe)).toList();
    }

    /* Lige, v katerih igra prijavljeni igralec s Premium. Samo s paketom, ker
       je to del iste funkcije kot spremljanje lig (glej spremljaj): brez njega
       racun ostane pri tem, kar mu je postavil admin. Lige v teku so pred
       tistimi v pripravi - tam se igra zdaj. Stevila ne omejujemo: to so lige,
       v katerih res nastopa, in ne izlozba. */
    private List<Liga> ligeIgralcevihEkip() {
        Uporabnik jaz = lastnistvo.trenutni();
        if (jaz == null || !jaz.jePotrjenIgralec() || !narocnina.imaPremium(jaz)) {
            return List.of();
        }
        // razvrscanje je stabilno: znotraj stanja ostane vrstni red vpisa (id)
        return ligaRepozitorij.najdiNezakljuceneZaIgralca(jaz.getIgralec().getId()).stream()
                .sorted(Comparator.comparing(l -> l.getStatus() != StatusTekmovanja.V_TEKU))
                .toList();
    }

    /* Vrstni red sledi izboru, neobstojece lige tiho odpadejo. */
    private List<Liga> poIzboru(List<Long> idji) {
        List<Liga> lige = new ArrayList<>();
        for (Long id : idji) {
            ligaRepozitorij.findById(id).ifPresent(lige::add);
        }
        return lige;
    }

    private DomovLigaDto povzetek(Liga liga, boolean izEkipe) {
        List<Srecanje> srecanja = srecanjeRepozitorij.najdiZaLigo(liga.getId());
        LocalDate danes = LocalDate.now();

        Srecanje naslednje = null;
        for (Srecanje s : srecanja) {
            if (s.getStatus() != StatusSrecanja.KONCANO && jeNaslednje(s, naslednje, danes)) {
                naslednje = s;
            }
        }
        Kola kola = presteji(srecanja, danes);
        int odigranihKol = kola.odigranih();
        int vsehKol = kola.vseh();

        List<DomovLigaDto.Vrh> vrh = new ArrayList<>();
        for (LestvicaEkipeDto v : lestvicaLigeStoritev.lestvica(liga.getId())) {
            if (vrh.size() == EKIP_NA_VRHU) {
                break;
            }
            vrh.add(new DomovLigaDto.Vrh(v.mesto(), v.ekipa(), v.odigrane(), v.tocke()));
        }

        return new DomovLigaDto(
                liga.getId(), liga.getIme(), liga.getSezona(), liga.getStatus(),
                odigranihKol, vsehKol, vrh,
                naslednje == null ? null
                        : new DomovLigaDto.Naslednje(naslednje.getKolo(), datum(naslednje)),
                izEkipe);
    }

    /* Kola rednega dela lige: koliko jih je in koliko je odigranih. */
    record Kola(int odigranih, int vseh) {}

    /* Kolo je ODIGRANO, ko je njegov datum ze mimo (danasnji dan se ne steje:
       kolo je se "naslednje", glej jeNaslednje). Datum kola je najzgodnejsi
       predvideni zacetek njegovih srecanj - isti kot v razporedu lige.

       Zakaj ne po koncanih srecanjih: ekipe se neuradno dogovorijo za menjavo
       terminov in odigrajo srecanje, ki spada v pozno kolo, ze zdaj. Ce bi
       kolo steli za odigrano ob prvem koncanem srecanju, bi po taki menjavi v
       vsakem kolu domaca stran trdila, da je odigrano vse - v resnici je le
       eno. Kolo brez datuma (organizator termina ni vpisal) datuma nima, zato
       je odigrano, ko je koncano vsako njegovo srecanje (kot v razporedu lige).

       Koncnica ni kolo rednega dela (njena "kola" so krogi serij) in ne steje. */
    static Kola presteji(List<Srecanje> srecanja, LocalDate danes) {
        Map<Integer, List<Srecanje>> poKolih = new TreeMap<>();
        for (Srecanje s : srecanja) {
            if (s.getSerija() == null) {
                poKolih.computeIfAbsent(s.getKolo(), k -> new ArrayList<>()).add(s);
            }
        }
        int odigranih = 0;
        for (List<Srecanje> kolo : poKolih.values()) {
            LocalDate datumKola = kolo.stream()
                    .map(DomovStoritev::datum)
                    .filter(Objects::nonNull)
                    .min(Comparator.naturalOrder())
                    .orElse(null);
            boolean odigrano = datumKola != null
                    ? datumKola.isBefore(danes)
                    : kolo.stream().allMatch(s -> s.getStatus() == StatusSrecanja.KONCANO);
            if (odigrano) {
                odigranih++;
            }
        }
        return new Kola(odigranih, poKolih.size());
    }

    /* "Naslednje kolo" je prvo nekoncano srecanje, ki ga termin se ni prehitel.
       Srecanje, ki je ostalo neodigrano (igralca sta se dogovorila za drug
       dan, izida ni nihce vpisal), bi sicer s starim datumom ostalo
       "naslednje" cele tedne, domaca stran pa obljublja, kdaj se igra NASLEDNJIC.
       Kolo zaradi tega ni odigrano - to se vidi v razporedu lige in v stevcu
       kol, ne v tej vrstici. Danasnji dan se steje, srecanje brez termina
       ni ze mimo (ne vemo, da je), zato ostane kandidat.

       Srecanja pridejo urejena po kolu in zacetku, zato prvi kandidat ostane
       na mestu - razen ce za njim pride srecanje z datumom, ki je prej
       (prestavljeno srecanje starejsega kola je lahko pozneje od naslednjega). */
    private static boolean jeNaslednje(Srecanje s, Srecanje trenutno, LocalDate danes) {
        LocalDate datum = datum(s);
        if (datum != null && datum.isBefore(danes)) {
            return false;
        }
        if (trenutno == null) {
            return true;
        }
        LocalDate trenutniDatum = datum(trenutno);
        return datum != null && trenutniDatum != null && datum.isBefore(trenutniDatum);
    }

    private static LocalDate datum(Srecanje srecanje) {
        return srecanje.getPredvidenZacetek() == null
                ? null
                : srecanje.getPredvidenZacetek().toLocalDate();
    }
}
