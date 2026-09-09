/* Podatki, ki jih potrebuje domaca stran in jih ni v obstojecih pogledih.

   Dvoje: izbor lig, ki jih uporabnik spremlja (osebna nastavitev racuna), in
   povzetek vsake take lige (kolo od kol, vrh lestvice, naslednje kolo).
   Gost izbora nima - njegov brskalnik posilja id-je lig, ki si jih je zapomnil
   sam; kadar jih ni, pokazemo lige, ki so v teku. */
package si.turnirko.storitve;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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

    public DomovStoritev(LigaRepozitorij ligaRepozitorij,
                         SrecanjeRepozitorij srecanjeRepozitorij,
                         SpremljanaLigaRepozitorij spremljanaLigaRepozitorij,
                         LestvicaLigeStoritev lestvicaLigeStoritev,
                         LastnistvoStoritev lastnistvo) {
        this.ligaRepozitorij = ligaRepozitorij;
        this.srecanjeRepozitorij = srecanjeRepozitorij;
        this.spremljanaLigaRepozitorij = spremljanaLigaRepozitorij;
        this.lestvicaLigeStoritev = lestvicaLigeStoritev;
        this.lastnistvo = lastnistvo;
    }

    // ---------- Izbor spremljanih lig ----------

    /* Lige, ki jih spremlja prijavljeni uporabnik. */
    @Transactional(readOnly = true)
    public List<Long> mojeLige() {
        return spremljanaLigaRepozitorij.idjiZaRacun(zahtevajPrijavo().getId());
    }

    /* Doda ligo v izbor; ponovni klic ne naredi nicesar (idempotentno, ker je
       kvadratek preklop in ne stevec). */
    @Transactional
    public List<Long> spremljaj(Long idLiga) {
        Uporabnik jaz = zahtevajPrijavo();
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
         2. lige, ki jih je admin postavil na domaco stran (najvec dve) - to je
            privzeti pogled gosta in vsakega, ki svojega izbora nima.
         3. "ogledane" - lige, ki si jih je gost nazadnje ogledal (spomin
            njegovega brskalnika). Sele TU, ker ogled ni izbira: adminova
            uredniska odlocitev ne sme odpasti zato, ker je gost pred tednom
            odprl neko ligo.
         4. lige v teku - nova namestitev, kjer se ni odlocil nihce. */
    @Transactional(readOnly = true)
    public List<DomovLigaDto> povzetkiLig(List<Long> idji, List<Long> ogledane) {
        List<Liga> lige;
        if (idji != null && !idji.isEmpty()) {
            lige = poIzboru(idji);
        } else {
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
        }
        return lige.stream().map(this::povzetek).toList();
    }

    /* Vrstni red sledi izboru, neobstojece lige tiho odpadejo. */
    private List<Liga> poIzboru(List<Long> idji) {
        List<Liga> lige = new ArrayList<>();
        for (Long id : idji) {
            ligaRepozitorij.findById(id).ifPresent(lige::add);
        }
        return lige;
    }

    private DomovLigaDto povzetek(Liga liga) {
        List<Srecanje> srecanja = srecanjeRepozitorij.najdiZaLigo(liga.getId());

        int vsehKol = 0;
        int odigranihKol = 0;
        Srecanje naslednje = null;
        for (Srecanje s : srecanja) {
            vsehKol = Math.max(vsehKol, s.getKolo());
            if (s.getStatus() == StatusSrecanja.KONCANO) {
                odigranihKol = Math.max(odigranihKol, s.getKolo());
            } else if (naslednje == null) {
                // srecanja pridejo urejena po kolu in id-ju, zato je prvo
                // nekoncano tudi prvo na vrsti
                naslednje = s;
            }
        }

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
                naslednje == null ? null : new DomovLigaDto.Naslednje(
                        naslednje.getKolo(),
                        datum(naslednje),
                        naslednje.getEkipaDomaci().prikazanoIme(),
                        naslednje.getEkipaGost().prikazanoIme()));
    }

    private static LocalDate datum(Srecanje srecanje) {
        return srecanje.getPredvidenZacetek() == null
                ? null
                : srecanje.getPredvidenZacetek().toLocalDate();
    }
}
