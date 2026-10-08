/* Podatki, ki jih potrebuje domaca stran in jih ni v obstojecih pogledih.

   Dvoje: izbor lig, ki jih uporabnik spremlja (osebna nastavitev racuna), in
   povzetek vsake take lige (kolo od kol, vrh lestvice, naslednje kolo).
   Gost izbora nima - njegov brskalnik posilja id-je lig, ki si jih je zapomnil
   sam; kadar jih ni, pokazemo lige, ki so v teku. */
package si.turnirko.storitve;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.DomovLigaDto;
import si.turnirko.dto.LestvicaEkipeDto;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.izjeme.PrepovedanoIzjema;
import si.turnirko.modeli.Liga;
import si.turnirko.modeli.SpremljanaLiga;
import si.turnirko.modeli.StatusTekmovanja;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.KaderEkipeRepozitorij;
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

    /* Mini razpredelnica: tri ekipe (vrh, igralcu s Premium pa njegova ekipa s
       sosedama). Vec jih vrstica lige ne prenese. */
    private static final int EKIP_NA_VRHU = 3;

    private final LigaRepozitorij ligaRepozitorij;
    private final SrecanjeRepozitorij srecanjeRepozitorij;
    private final SpremljanaLigaRepozitorij spremljanaLigaRepozitorij;
    private final LestvicaLigeStoritev lestvicaLigeStoritev;
    private final LastnistvoStoritev lastnistvo;
    private final NarocninaStoritev narocnina;
    private final KaderEkipeRepozitorij kaderRepozitorij;

    public DomovStoritev(LigaRepozitorij ligaRepozitorij,
                         SrecanjeRepozitorij srecanjeRepozitorij,
                         SpremljanaLigaRepozitorij spremljanaLigaRepozitorij,
                         LestvicaLigeStoritev lestvicaLigeStoritev,
                         LastnistvoStoritev lastnistvo,
                         NarocninaStoritev narocnina,
                         KaderEkipeRepozitorij kaderRepozitorij) {
        this.ligaRepozitorij = ligaRepozitorij;
        this.srecanjeRepozitorij = srecanjeRepozitorij;
        this.spremljanaLigaRepozitorij = spremljanaLigaRepozitorij;
        this.lestvicaLigeStoritev = lestvicaLigeStoritev;
        this.lastnistvo = lastnistvo;
        this.narocnina = narocnina;
        this.kaderRepozitorij = kaderRepozitorij;
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
        Long mojIgralec = premiumIgralec();
        return lige.stream().map(l -> povzetek(l, izEkipe, mojIgralec)).toList();
    }

    /* Lige, v katerih igra prijavljeni igralec s Premium. Samo s paketom, ker
       je to del iste funkcije kot spremljanje lig (glej spremljaj): brez njega
       racun ostane pri tem, kar mu je postavil admin. Lige v teku so pred
       tistimi v pripravi - tam se igra zdaj. Stevila ne omejujemo: to so lige,
       v katerih res nastopa, in ne izlozba. */
    private List<Liga> ligeIgralcevihEkip() {
        Long jaz = premiumIgralec();
        if (jaz == null) {
            return List.of();
        }
        // razvrscanje je stabilno: znotraj stanja ostane vrstni red vpisa (id)
        return ligaRepozitorij.najdiNezakljuceneZaIgralca(jaz).stream()
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

    private DomovLigaDto povzetek(Liga liga, boolean izEkipe, Long mojIgralec) {
        /* Kolo od kol in naslednje kolo po ISTEM pravilu kot stran lige in
           seznam lig (PotekLige). Koncnica gre zraven: v stevcu ne steje, po
           rednem delu pa je njena tekma "naslednje". */
        List<PotekLige.Termin> termini = srecanjeRepozitorij.najdiZaLigo(liga.getId()).stream()
                .map(s -> new PotekLige.Termin(s.getKolo(), s.getStatus(), s.getPredvidenZacetek(),
                        s.getSerija() != null))
                .toList();
        PotekLige.Potek potek = PotekLige.izracunaj(termini, PotekLige.danes());

        /* Igralcu s Premium, ki v ligi igra, razpredelnica pokaze NJEGOVO ekipo
           in sosedi namesto vrha - ne glede na to, zakaj je liga na domaci
           strani (lasten izbor, kader ali adminova izlozba). Vrh lestvice ga
           v ligi, kjer je deveti, ne zanima; kdo je tik nad in pod njim, pa. */
        List<LestvicaEkipeDto> lestvica = lestvicaLigeStoritev.lestvica(liga.getId());
        Set<Long> moje = mojIgralec == null
                ? Set.of()
                : new HashSet<>(kaderRepozitorij.ekipeIgralcaVLigi(liga.getId(), mojIgralec));
        int mojaVrstica = -1;
        for (int i = 0; i < lestvica.size(); i++) {
            if (moje.contains(lestvica.get(i).idEkipa())) {
                mojaVrstica = i;
                break;
            }
        }
        List<LestvicaEkipeDto> izbrane = mojaVrstica < 0
                ? lestvica.subList(0, Math.min(EKIP_NA_VRHU, lestvica.size()))
                : okno(lestvica, mojaVrstica, EKIP_NA_VRHU);
        List<DomovLigaDto.Vrh> vrh = izbrane.stream()
                .map(v -> new DomovLigaDto.Vrh(v.mesto(), v.ekipa(), v.odigrane(), v.tocke(),
                        moje.contains(v.idEkipa())))
                .toList();

        PotekLige.Naslednje naslednje = potek.naslednje();
        return new DomovLigaDto(
                liga.getId(), liga.getIme(), liga.getSezona(), liga.getStatus(),
                potek.odigranih(), potek.vseh(), vrh, lestvica.size(),
                naslednje == null ? null : new DomovLigaDto.Naslednje(naslednje.kolo(), naslednje.datum()),
                izEkipe);
    }

    /* Okno dane velikosti okoli vrstice i: ena gor in ena dol, pri prvi pa dve
       dol in pri zadnji dve gor - okno je vedno polno, ce je vrstic dovolj.
       Vmesnik ob prvi oz. zadnji vrstici sam pove, da je to vrh oz. dno. */
    static <T> List<T> okno(List<T> vrstice, int i, int velikost) {
        if (vrstice.size() <= velikost) {
            return vrstice;
        }
        int zacetek = Math.max(0, Math.min(i - velikost / 2, vrstice.size() - velikost));
        return vrstice.subList(zacetek, zacetek + velikost);
    }

    /* Id igralca prijavljenega racuna, ce je potrjen igralec s Premium; sicer
       null. Domaca stran mu prilagodi lige (ekipa namesto vrha). */
    private Long premiumIgralec() {
        Uporabnik jaz = lastnistvo.trenutni();
        if (jaz == null || !jaz.jePotrjenIgralec() || !narocnina.imaPremium(jaz)) {
            return null;
        }
        return jaz.getIgralec().getId();
    }
}
