/* Zapis ligaskega zapisnika v srecanje iz branja (samodejno ali po potrditvi
   organizatorja). Po ISTI poti kot rocni vnos: postava (SrecanjeStoritev.
   nastaviPostavo), menjave (zamenjajIgralce) in izidi (vnesiRezultat) - z vsemi
   preverbami, lastnistvom, obracunom ratinga in preracunom za nazaj. Vse v ENI
   transakciji: ce se karkoli zavrne (igralec ni v kadru, izid ni veljaven), se ne
   zapise nic in srecanje ostane, kot je bilo.

   Postava se ne bere, ampak izpelje (lige igrajo z menjavami, pravila v
   docs/pravila/lige.md »Menjava je last ENE tekme«): mesto A je igralec prve
   odigrane posamicne tekme z mestom A (pri SAVINJA A-X), B prve z B ... Kdor tekmo
   igra namesto igralca svojega mesta, je menjava te tekme. Pri formatih, kjer v
   dvojicah igrata oba igralca postave (izbiraDvojice = false), sta oba v paru;
   drug par v dvojicah je menjava dvojic. */
package si.turnirko.storitve;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.MenjavaVnos;
import si.turnirko.dto.NizVnos;
import si.turnirko.dto.PostavaVnos;
import si.turnirko.dto.SrecanjePodrobnoDto;
import si.turnirko.dto.VnosRezultataSrecanja;
import si.turnirko.dto.ZapisZapisnikaVnos;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.IzidTekme;
import si.turnirko.modeli.Srecanje;
import si.turnirko.modeli.StatusSrecanja;
import si.turnirko.modeli.StatusTekmeSrecanja;
import si.turnirko.modeli.StranEkipe;
import si.turnirko.modeli.TekmaSrecanja;
import si.turnirko.modeli.TipTekmeSrecanja;
import si.turnirko.repozitoriji.SrecanjeRepozitorij;
import si.turnirko.repozitoriji.TekmaSrecanjaRepozitorij;

@Service
public class ZapisZapisnikaStoritev {

    private final SrecanjeStoritev srecanja;
    private final SrecanjeRepozitorij srecanjeRepozitorij;
    private final TekmaSrecanjaRepozitorij tekme;
    private final Zaklepi zaklepi;
    private final LastnistvoStoritev lastnistvo;

    public ZapisZapisnikaStoritev(SrecanjeStoritev srecanja, SrecanjeRepozitorij srecanjeRepozitorij,
                                  TekmaSrecanjaRepozitorij tekme, Zaklepi zaklepi, LastnistvoStoritev lastnistvo) {
        this.srecanja = srecanja;
        this.srecanjeRepozitorij = srecanjeRepozitorij;
        this.tekme = tekme;
        this.zaklepi = zaklepi;
        this.lastnistvo = lastnistvo;
    }

    /* Ali je v srecanju ze kaj vpisano (zapisnik bi bilo treba prepisati). */
    @Transactional(readOnly = true)
    public boolean jeVpisano(Long idSrecanje) {
        Srecanje s = srecanjeRepozitorij.findById(idSrecanje)
                .orElseThrow(() -> new NiNajdenoIzjema("Srecanje z id " + idSrecanje + " ne obstaja."));
        return jeVpisano(s);
    }

    private boolean jeVpisano(Srecanje s) {
        if (s.getStatus() == StatusSrecanja.KONCANO) {
            return true;
        }
        return tekme.najdiZaSrecanje(s.getId()).stream()
                .anyMatch(t -> t.getStatus() == StatusTekmeSrecanja.KONCANA);
    }

    @Transactional
    public SrecanjePodrobnoDto zapisi(Long idSrecanje, ZapisZapisnikaVnos vnos) {
        zaklepi.zakleniTekmovanjePoSrecanju(idSrecanje);
        lastnistvo.preveriPoSrecanju(idSrecanje);
        Srecanje s = srecanjeRepozitorij.najdiPodrobno(idSrecanje)
                .orElseThrow(() -> new NiNajdenoIzjema("Srecanje z id " + idSrecanje + " ne obstaja."));
        if (s.isBrezBoja()) {
            throw new DomenskaIzjema("Srečanje je registrirano brez boja.");
        }
        if (jeVpisano(s)) {
            if (!vnos.prepisi()) {
                throw new DomenskaIzjema("Zapisnik tega srečanja je že vpisan. Če je prebrani pravi, ga prepiši.");
            }
            srecanja.razveljaviZapisnik(idSrecanje);
        }
        FormatSrecanja format = s.pravila().format();
        List<FormatSrecanja.MestoTekme> razpored = format.razpored();
        if (vnos.tekme().size() != razpored.size()) {
            throw new NeveljavenVnosIzjema("Zapisnik mora imeti " + razpored.size() + " tekem.");
        }

        srecanja.nastaviPostavo(idSrecanje, postava(format, razpored, vnos.tekme()));
        List<TekmaSrecanja> ustvarjene = tekme.najdiZaSrecanje(idSrecanje);
        // menjave pred izidi: menjava je mogoca samo pri tekmi, ki se caka
        for (int i = 0; i < razpored.size(); i++) {
            ZapisZapisnikaVnos.Tekma t = vnos.tekme().get(i);
            if (!t.imaIzid()) {
                continue;
            }
            TekmaSrecanja tekma = tekmaPoZaporedju(ustvarjene, i + 1);
            boolean dvojice = razpored.get(i).tip() == TipTekmeSrecanja.DVOJICE;
            List<Long> d = igralci(t.domaci(), dvojice, razpored.get(i).oznaka(), "domačih");
            List<Long> g = igralci(t.gost(), dvojice, razpored.get(i).oznaka(), "gostov");
            if (!enaki(tekma, d, g, dvojice)) {
                srecanja.zamenjajIgralce(tekma.getId(), new MenjavaVnos(d.get(0), dvojice ? d.get(1) : null,
                        g.get(0), dvojice ? g.get(1) : null));
            }
        }
        for (int i = 0; i < razpored.size(); i++) {
            ZapisZapisnikaVnos.Tekma t = vnos.tekme().get(i);
            if (!t.imaIzid()) {
                continue;
            }
            TekmaSrecanja tekma = tekmaPoZaporedju(tekme.najdiZaSrecanje(idSrecanje), i + 1);
            if (tekma.getStatus() == StatusTekmeSrecanja.NEODIGRANA) {
                throw new DomenskaIzjema("Tekma " + razpored.get(i).oznaka() + " ima vpisan izid, srečanje pa je bilo"
                        + " že odločeno pred njo. Preveri zapisnik.");
            }
            srecanja.vnesiRezultat(tekma.getId(), izid(t));
        }
        return srecanja.podrobno(idSrecanje);
    }

    /* Postava iz prvih nastopov mest (glej uvod). */
    static PostavaVnos postava(FormatSrecanja format, List<FormatSrecanja.MestoTekme> razpored,
                               List<ZapisZapisnikaVnos.Tekma> vnos) {
        Map<String, Long> domaci = new HashMap<>();
        Map<String, Long> gost = new HashMap<>();
        for (int i = 0; i < razpored.size(); i++) {
            FormatSrecanja.MestoTekme m = razpored.get(i);
            ZapisZapisnikaVnos.Tekma t = vnos.get(i);
            if (m.tip() != TipTekmeSrecanja.POSAMICNA || !t.imaIzid()) {
                continue;
            }
            if (t.domaci() != null && t.domaci().size() == 1) {
                domaci.putIfAbsent(m.domaci(), t.domaci().getFirst());
            }
            if (t.gost() != null && t.gost().size() == 1) {
                gost.putIfAbsent(m.gost(), t.gost().getFirst());
            }
        }
        List<PostavaVnos.MestoVnos> mesta = new ArrayList<>();
        mesta.addAll(mesta(format, StranEkipe.DOMACI, format.pozicijeDomaci(), domaci, razpored, vnos));
        mesta.addAll(mesta(format, StranEkipe.GOST, format.pozicijeGost(), gost, razpored, vnos));
        return new PostavaVnos(mesta);
    }

    private static List<PostavaVnos.MestoVnos> mesta(FormatSrecanja format, StranEkipe stran, List<String> pozicije,
                                                     Map<String, Long> izbrani, List<FormatSrecanja.MestoTekme> razpored,
                                                     List<ZapisZapisnikaVnos.Tekma> vnos) {
        String opis = stran == StranEkipe.DOMACI ? "domačih" : "gostov";
        for (String p : pozicije) {
            if (!izbrani.containsKey(p)) {
                throw new NeveljavenVnosIzjema("Mesta " + p + " (" + opis + ") ni mogoče določiti: igralec na tem"
                        + " mestu ni odigral nobene tekme z izidom.");
            }
        }
        // par za dvojice: pri formatih brez izbire igrata vsa mesta; sicer par iz
        // prvih dvojic, ce sta oba igralca na mestih postave
        java.util.Set<Long> par = new java.util.HashSet<>();
        if (format.imaDvojice() && format.izbiraDvojice()) {
            for (int i = 0; i < razpored.size(); i++) {
                if (razpored.get(i).tip() == TipTekmeSrecanja.DVOJICE && vnos.get(i).imaIzid()) {
                    List<Long> igralci = stran == StranEkipe.DOMACI ? vnos.get(i).domaci() : vnos.get(i).gost();
                    if (igralci != null && izbrani.values().containsAll(igralci)) {
                        par.addAll(igralci);
                    }
                    break;
                }
            }
        }
        List<PostavaVnos.MestoVnos> mesta = new ArrayList<>();
        List<String> brezPara = new ArrayList<>();
        for (String p : pozicije) {
            Long id = izbrani.get(p);
            boolean vDvojici = format.imaDvojice() && (!format.izbiraDvojice() || par.contains(id));
            mesta.add(new PostavaVnos.MestoVnos(stran, p, id, vDvojici));
            if (!vDvojici) {
                brezPara.add(p);
            }
        }
        // izbira para brez dvojic z izidom ali s parom zunaj postave: prva mesta
        // (dvojice so tedaj menjava), da postava ostane veljavna
        if (format.imaDvojice() && format.izbiraDvojice() && par.size() != format.stVDvojici()) {
            mesta.clear();
            for (int i = 0; i < pozicije.size(); i++) {
                String p = pozicije.get(i);
                mesta.add(new PostavaVnos.MestoVnos(stran, p, izbrani.get(p), i < format.stVDvojici()));
            }
        }
        return mesta;
    }

    private static TekmaSrecanja tekmaPoZaporedju(List<TekmaSrecanja> tekme, int zaporedje) {
        return tekme.stream().filter(t -> t.getZaporedje() == zaporedje).findFirst()
                .orElseThrow(() -> new IllegalStateException("Tekme " + zaporedje + " ni."));
    }

    private static List<Long> igralci(List<Long> idji, boolean dvojice, String oznaka, String opis) {
        int k = dvojice ? 2 : 1;
        if (idji == null || idji.size() != k || idji.stream().anyMatch(Objects::isNull)) {
            throw new NeveljavenVnosIzjema("Pri tekmi " + oznaka + " izberi " + (dvojice ? "oba igralca " : "igralca ")
                    + opis + ".");
        }
        return idji;
    }

    private static boolean enaki(TekmaSrecanja t, List<Long> d, List<Long> g, boolean dvojice) {
        if (!dvojice) {
            return t.getIgralecDomaci().getId().equals(d.getFirst()) && t.getIgralecGost().getId().equals(g.getFirst());
        }
        return java.util.Set.of(t.getIgralecDomaci().getId(), t.getIgralecDomaci2().getId()).equals(java.util.Set.copyOf(d))
                && java.util.Set.of(t.getIgralecGost().getId(), t.getIgralecGost2().getId()).equals(java.util.Set.copyOf(g));
    }

    /* Izid tekme iz zapisa: pri igrani tekmi dobljeni nizi iz tock. */
    static VnosRezultataSrecanja izid(ZapisZapisnikaVnos.Tekma t) {
        if (t.izidTip() != null && t.izidTip() != IzidTekme.IGRANO) {
            return new VnosRezultataSrecanja(t.izidTip(), null, null, t.zmagovalecStran(), List.of());
        }
        List<NizVnos> nizi = t.nizi() == null ? List.of() : t.nizi();
        int d = 0;
        int g = 0;
        for (NizVnos n : nizi) {
            if (n.tocke1() == null || n.tocke2() == null) {
                throw new NeveljavenVnosIzjema("Niz brez točk.");
            }
            if (n.tocke1() > n.tocke2()) {
                d++;
            } else {
                g++;
            }
        }
        return new VnosRezultataSrecanja(IzidTekme.IGRANO, d, g, null, nizi);
    }
}
