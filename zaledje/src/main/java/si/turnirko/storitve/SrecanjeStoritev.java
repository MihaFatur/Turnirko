/* Poslovna logika srecanj: dolocanje postave, generiranje posamicnih tekem po
   formatu, vnos rezultatov, pravilo predcasnega konca (prvi do N zmag) in
   obracun ELO za posamicne tekme.

   Postava dodeli igralce iz kadra na mesta (A/B/C, X/Y/Z) in oznaci par za
   dvojice; iz nje se generira urejen seznam tekem. Ko ena stran doseze prag
   zmag, se preostale tekme oznacijo kot NEODIGRANE in srecanje se konca. */
package si.turnirko.storitve;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.KaderIgralecDto;
import si.turnirko.dto.PostavaSrecanjaDto;
import si.turnirko.dto.PostavaVnos;
import si.turnirko.dto.SrecanjeDto;
import si.turnirko.dto.SrecanjePodrobnoDto;
import si.turnirko.dto.TekmaSrecanjaDto;
import si.turnirko.dto.VnosRezultataSrecanja;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.modeli.Ekipa;
import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.IzidTekme;
import si.turnirko.modeli.KaderEkipe;
import si.turnirko.modeli.Liga;
import si.turnirko.modeli.PostavaSrecanja;
import si.turnirko.modeli.RatingStanje;
import si.turnirko.modeli.Srecanje;
import si.turnirko.modeli.StatusSrecanja;
import si.turnirko.modeli.StatusTekmeSrecanja;
import si.turnirko.modeli.StranEkipe;
import si.turnirko.modeli.TekmaSrecanja;
import si.turnirko.modeli.TipTekmeSrecanja;
import si.turnirko.repozitoriji.KaderEkipeRepozitorij;
import si.turnirko.repozitoriji.PostavaSrecanjaRepozitorij;
import si.turnirko.repozitoriji.RatingZgodovinaRepozitorij;
import si.turnirko.repozitoriji.SrecanjeRepozitorij;
import si.turnirko.repozitoriji.TekmaSrecanjaRepozitorij;

@Service
public class SrecanjeStoritev {

    private final SrecanjeRepozitorij srecanjeRepozitorij;
    private final PostavaSrecanjaRepozitorij postavaRepozitorij;
    private final TekmaSrecanjaRepozitorij tekmaRepozitorij;
    private final KaderEkipeRepozitorij kaderRepozitorij;
    private final RatingStoritev ratingStoritev;
    private final RatingZgodovinaRepozitorij zgodovinaRepozitorij;
    private final SpremembeEloStoritev spremembeEloStoritev;
    private final LastnistvoStoritev lastnistvo;

    public SrecanjeStoritev(SrecanjeRepozitorij srecanjeRepozitorij,
                            PostavaSrecanjaRepozitorij postavaRepozitorij,
                            TekmaSrecanjaRepozitorij tekmaRepozitorij,
                            KaderEkipeRepozitorij kaderRepozitorij,
                            RatingStoritev ratingStoritev,
                            RatingZgodovinaRepozitorij zgodovinaRepozitorij,
                            SpremembeEloStoritev spremembeEloStoritev,
                            LastnistvoStoritev lastnistvo) {
        this.srecanjeRepozitorij = srecanjeRepozitorij;
        this.postavaRepozitorij = postavaRepozitorij;
        this.tekmaRepozitorij = tekmaRepozitorij;
        this.kaderRepozitorij = kaderRepozitorij;
        this.ratingStoritev = ratingStoritev;
        this.zgodovinaRepozitorij = zgodovinaRepozitorij;
        this.spremembeEloStoritev = spremembeEloStoritev;
        this.lastnistvo = lastnistvo;
    }

    @Transactional(readOnly = true)
    public List<SrecanjeDto> zaLigo(Long idLiga) {
        return srecanjeRepozitorij.najdiZaLigo(idLiga).stream().map(SrecanjeDto::iz).toList();
    }

    @Transactional(readOnly = true)
    public SrecanjePodrobnoDto podrobno(Long idSrecanje) {
        Srecanje s = srecanjeRepozitorij.najdiPodrobno(idSrecanje)
                .orElseThrow(() -> new NiNajdenoIzjema("Srecanje z id " + idSrecanje + " ne obstaja."));
        Liga liga = s.getLiga();
        FormatSrecanja format = liga.getFormatSrecanja();

        List<PostavaSrecanjaDto> postave = postavaRepozitorij.najdiZaSrecanje(idSrecanje)
                .stream().map(PostavaSrecanjaDto::iz).toList();

        List<TekmaSrecanja> tekme = tekmaRepozitorij.najdiZaSrecanje(idSrecanje);
        Map<Long, Map<Long, Integer>> delte = eloDelte(tekme.stream().map(TekmaSrecanja::getId).toList());
        List<TekmaSrecanjaDto> tekmeDto = tekme.stream()
                .map(t -> TekmaSrecanjaDto.iz(t,
                        eloZa(delte, t.getId(), t.getIgralecDomaci()),
                        eloZa(delte, t.getId(), t.getIgralecGost())))
                .toList();

        return new SrecanjePodrobnoDto(
                SrecanjeDto.iz(s), format,
                format.pozicijeDomaci(), format.pozicijeGost(),
                format.izbiraDvojice(), format.stVDvojici(),
                postave, tekmeDto,
                kader(s.getEkipaDomaci().getId()), kader(s.getEkipaGost().getId()));
    }

    // ---------- Postava ----------

    @Transactional
    public void nastaviPostavo(Long idSrecanje, PostavaVnos vnos) {
        lastnistvo.preveriLigaPoSrecanju(idSrecanje);
        Srecanje s = srecanjeRepozitorij.najdiPodrobno(idSrecanje)
                .orElseThrow(() -> new NiNajdenoIzjema("Srecanje z id " + idSrecanje + " ne obstaja."));
        FormatSrecanja format = s.getLiga().getFormatSrecanja();

        // ce je ze vnesen kaksen rezultat, postave ne spreminjamo (obracun ratinga
        // bi bilo treba razveljaviti) - najprej je treba rezultate pociscati
        for (TekmaSrecanja obstojeca : tekmaRepozitorij.najdiZaSrecanje(idSrecanje)) {
            if (obstojeca.getStatus() == StatusTekmeSrecanja.KONCANA) {
                throw new DomenskaIzjema("Postave ni mogoce spremeniti, ko so ze vneseni rezultati.");
            }
        }

        Map<String, PostavaVnos.MestoVnos> domaci = new HashMap<>();
        Map<String, PostavaVnos.MestoVnos> gost = new HashMap<>();
        for (PostavaVnos.MestoVnos m : vnos.mesta()) {
            Map<String, PostavaVnos.MestoVnos> cilj = m.stran() == StranEkipe.DOMACI ? domaci : gost;
            if (cilj.put(m.pozicija(), m) != null) {
                throw new NeveljavenVnosIzjema("Mesto " + m.pozicija() + " je navedeno vec kot enkrat.");
            }
        }

        Set<Long> kaderDomaci = kaderIgralci(s.getEkipaDomaci());
        Set<Long> kaderGost = kaderIgralci(s.getEkipaGost());
        preveriStran(format, StranEkipe.DOMACI, domaci, format.pozicijeDomaci(), kaderDomaci);
        preveriStran(format, StranEkipe.GOST, gost, format.pozicijeGost(), kaderGost);

        // igralci iz kadrov (za entitete)
        Map<Long, Igralec> igralci = new HashMap<>();
        for (KaderEkipe k : kaderRepozitorij.najdiZaEkipo(s.getEkipaDomaci().getId())) {
            igralci.put(k.getIgralec().getId(), k.getIgralec());
        }
        for (KaderEkipe k : kaderRepozitorij.najdiZaEkipo(s.getEkipaGost().getId())) {
            igralci.put(k.getIgralec().getId(), k.getIgralec());
        }

        // odstrani staro postavo in tekme, nato generiraj na novo
        tekmaRepozitorij.deleteBySrecanjeId(idSrecanje);
        postavaRepozitorij.deleteBySrecanjeId(idSrecanje);
        tekmaRepozitorij.flush();
        postavaRepozitorij.flush();

        shraniPostavo(s, StranEkipe.DOMACI, domaci, igralci);
        shraniPostavo(s, StranEkipe.GOST, gost, igralci);
        generirajTekme(s, format, domaci, gost, igralci);

        s.setDobljeneDomaci(0);
        s.setDobljeneGost(0);
        s.setOdigranOb(null);
        s.setStatus(StatusSrecanja.POTEKA);
        srecanjeRepozitorij.save(s);
    }

    // ---------- Rezultat ----------

    @Transactional
    public TekmaSrecanjaDto vnesiRezultat(Long idTekma, VnosRezultataSrecanja v) {
        lastnistvo.preveriLigaPoTekmiSrecanja(idTekma);
        TekmaSrecanja t = tekmaRepozitorij.najdiZaObracun(idTekma)
                .orElseThrow(() -> new NiNajdenoIzjema("Tekma srecanja z id " + idTekma + " ne obstaja."));
        Srecanje s = t.getSrecanje();
        Liga liga = s.getLiga();

        if (s.getStatus() == StatusSrecanja.KONCANO) {
            throw new DomenskaIzjema("Srecanje je ze koncano.");
        }
        if (t.getStatus() == StatusTekmeSrecanja.NEODIGRANA) {
            throw new DomenskaIzjema("Ta tekma se po pravilih ne igra (srecanje je bilo ze odloceno).");
        }

        int zaZmago = t.nizovZaZmago();
        IzidTekme izid = v.izidTip() != null ? v.izidTip() : IzidTekme.IGRANO;
        int niziDomaci;
        int niziGost;
        StranEkipe zmagovalec;

        if (izid == IzidTekme.IGRANO) {
            if (v.dobljeniNiziDomaci() == null || v.dobljeniNiziGost() == null) {
                throw new NeveljavenVnosIzjema("Manjkata dobljena niza.");
            }
            niziDomaci = v.dobljeniNiziDomaci();
            niziGost = v.dobljeniNiziGost();
            if (niziDomaci < 0 || niziGost < 0) {
                throw new NeveljavenVnosIzjema("Stevilo dobljenih nizov ne sme biti negativno.");
            }
            if (niziDomaci == niziGost) {
                throw new NeveljavenVnosIzjema("Neodlocen izid posamicne tekme ni mozen.");
            }
            int zmagNizi = Math.max(niziDomaci, niziGost);
            int porazNizi = Math.min(niziDomaci, niziGost);
            if (zmagNizi != zaZmago) {
                throw new NeveljavenVnosIzjema("Zmagovalec mora dobiti natanko " + zaZmago + " nizov.");
            }
            if (porazNizi > zaZmago - 1) {
                throw new NeveljavenVnosIzjema("Porazenec ima prevec dobljenih nizov.");
            }
            zmagovalec = niziDomaci > niziGost ? StranEkipe.DOMACI : StranEkipe.GOST;
        } else if (izid == IzidTekme.PROSTO) {
            throw new NeveljavenVnosIzjema("Izid PROSTO v ligi ni mogoc.");
        } else {
            if (v.zmagovalecStran() == null) {
                throw new NeveljavenVnosIzjema("Za posebni izid je obvezna zmagovalna stran.");
            }
            zmagovalec = v.zmagovalecStran();
            niziDomaci = zmagovalec == StranEkipe.DOMACI ? zaZmago : 0;
            niziGost = zmagovalec == StranEkipe.GOST ? zaZmago : 0;
        }

        t.setDobljeniNiziDomaci(niziDomaci);
        t.setDobljeniNiziGost(niziGost);
        t.setZmagovalecStran(zmagovalec);
        t.setIzidTip(izid);
        t.setStatus(StatusTekmeSrecanja.KONCANA);
        tekmaRepozitorij.save(t);

        // ELO samo za posamicne tekme, ce liga steje in izid steje (w.o. in
        // diskvalifikacija ne stejeta - enako kot pri turnirjih)
        if (t.getTip() == TipTekmeSrecanja.POSAMICNA && liga.isStejeVElo() && stejeVElo(izid)) {
            ratingStoritev.obracunajZaLigasko(t);
        }

        posodobiSrecanje(s, liga);

        Map<Long, Map<Long, Integer>> delte = eloDelte(List.of(t.getId()));
        return TekmaSrecanjaDto.iz(t,
                eloZa(delte, t.getId(), t.getIgralecDomaci()),
                eloZa(delte, t.getId(), t.getIgralecGost()));
    }

    /* Osvezi povzetek srecanja in uveljavi pravilo predcasnega konca. */
    private void posodobiSrecanje(Srecanje s, Liga liga) {
        List<TekmaSrecanja> tekme = tekmaRepozitorij.najdiZaSrecanje(s.getId());
        int zmageDomaci = 0;
        int zmageGost = 0;
        int cakajoce = 0;
        for (TekmaSrecanja t : tekme) {
            if (t.getStatus() == StatusTekmeSrecanja.KONCANA && t.getZmagovalecStran() != null) {
                if (t.getZmagovalecStran() == StranEkipe.DOMACI) {
                    zmageDomaci++;
                } else {
                    zmageGost++;
                }
            } else if (t.getStatus() == StatusTekmeSrecanja.CAKA) {
                cakajoce++;
            }
        }
        s.setDobljeneDomaci(zmageDomaci);
        s.setDobljeneGost(zmageGost);

        Integer prag = liga.getZmagZaSrecanje();
        boolean odloceno;
        if (prag != null && (zmageDomaci >= prag || zmageGost >= prag)) {
            for (TekmaSrecanja t : tekme) {
                if (t.getStatus() == StatusTekmeSrecanja.CAKA) {
                    t.setStatus(StatusTekmeSrecanja.NEODIGRANA);
                    tekmaRepozitorij.save(t);
                }
            }
            odloceno = true;
        } else {
            odloceno = cakajoce == 0;
        }

        if (odloceno) {
            s.setStatus(StatusSrecanja.KONCANO);
            if (s.getOdigranOb() == null) {
                s.setOdigranOb(LocalDateTime.now());
            }
        } else {
            s.setStatus(StatusSrecanja.POTEKA);
        }
        srecanjeRepozitorij.save(s);
    }

    // ---------- Pomozno ----------

    private void preveriStran(FormatSrecanja format, StranEkipe stran,
                              Map<String, PostavaVnos.MestoVnos> postava,
                              List<String> pozicije, Set<Long> kader) {
        String opis = stran == StranEkipe.DOMACI ? "domacih" : "gostov";
        if (!postava.keySet().equals(new HashSet<>(pozicije))) {
            throw new NeveljavenVnosIzjema("Postava " + opis + " mora zasesti mesta " + pozicije + ".");
        }
        Set<Long> igralci = new HashSet<>();
        int vDvojici = 0;
        for (PostavaVnos.MestoVnos m : postava.values()) {
            if (!igralci.add(m.idIgralec())) {
                throw new NeveljavenVnosIzjema("En igralec ne more zasesti dveh mest (" + opis + ").");
            }
            if (!kader.contains(m.idIgralec())) {
                throw new DomenskaIzjema("Igralec ni v kadru " + opis + ".");
            }
            if (m.vDvojici()) {
                vDvojici++;
            }
        }
        if (format.imaDvojice() && vDvojici != format.stVDvojici()) {
            throw new NeveljavenVnosIzjema("Za dvojice " + opis + " je treba oznaciti natanko "
                    + format.stVDvojici() + " igralca.");
        }
        if (!format.imaDvojice() && vDvojici != 0) {
            throw new NeveljavenVnosIzjema("Ta format nima dvojic.");
        }
    }

    private void shraniPostavo(Srecanje s, StranEkipe stran,
                               Map<String, PostavaVnos.MestoVnos> postava, Map<Long, Igralec> igralci) {
        for (PostavaVnos.MestoVnos m : postava.values()) {
            postavaRepozitorij.save(new PostavaSrecanja(
                    s, stran, m.pozicija(), igralci.get(m.idIgralec()), m.vDvojici()));
        }
    }

    private void generirajTekme(Srecanje s, FormatSrecanja format,
                                Map<String, PostavaVnos.MestoVnos> domaci,
                                Map<String, PostavaVnos.MestoVnos> gost,
                                Map<Long, Igralec> igralci) {
        int steviloNizov = s.getLiga().getSteviloNizov();
        List<Igralec> dvojicaDomaci = paroviZaDvojice(format.pozicijeDomaci(), domaci, igralci);
        List<Igralec> dvojicaGost = paroviZaDvojice(format.pozicijeGost(), gost, igralci);

        int zaporedje = 1;
        for (FormatSrecanja.MestoTekme mesto : format.razpored()) {
            TekmaSrecanja t = new TekmaSrecanja(s, zaporedje++, mesto.tip(), mesto.oznaka(), steviloNizov);
            if (mesto.tip() == TipTekmeSrecanja.POSAMICNA) {
                t.setIgralecDomaci(igralci.get(domaci.get(mesto.domaci()).idIgralec()));
                t.setIgralecGost(igralci.get(gost.get(mesto.gost()).idIgralec()));
            } else {
                t.setIgralecDomaci(dvojicaDomaci.get(0));
                t.setIgralecDomaci2(dvojicaDomaci.get(1));
                t.setIgralecGost(dvojicaGost.get(0));
                t.setIgralecGost2(dvojicaGost.get(1));
            }
            tekmaRepozitorij.save(t);
        }
    }

    /* Igralca para za dvojice v vrstnem redu mest (A pred B ...). */
    private List<Igralec> paroviZaDvojice(List<String> pozicije,
                                          Map<String, PostavaVnos.MestoVnos> postava, Map<Long, Igralec> igralci) {
        List<Igralec> par = new ArrayList<>();
        for (String p : pozicije) {
            PostavaVnos.MestoVnos m = postava.get(p);
            if (m != null && m.vDvojici()) {
                par.add(igralci.get(m.idIgralec()));
            }
        }
        return par;
    }

    private Set<Long> kaderIgralci(Ekipa ekipa) {
        Set<Long> ids = new HashSet<>();
        for (KaderEkipe k : kaderRepozitorij.najdiZaEkipo(ekipa.getId())) {
            ids.add(k.getIgralec().getId());
        }
        return ids;
    }

    private List<KaderIgralecDto> kader(Long idEkipa) {
        List<KaderEkipe> kader = kaderRepozitorij.najdiZaEkipo(idEkipa);
        Map<Long, Integer> ratingi = spremembeEloStoritev.trenutniRatingi(
                kader.stream().map(k -> k.getIgralec().getId()).toList());
        return kader.stream()
                .map(k -> KaderIgralecDto.iz(k, ratingi.get(k.getIgralec().getId())))
                .toList();
    }

    private Map<Long, Map<Long, Integer>> eloDelte(Collection<Long> idjiTekem) {
        Map<Long, Map<Long, Integer>> m = new HashMap<>();
        if (idjiTekem.isEmpty()) {
            return m;
        }
        for (Object[] r : zgodovinaRepozitorij.spremembeZaTekmeSrecanja(idjiTekem, RatingStanje.SISTEM_KLUBSKI_ELO)) {
            Long idTekme = ((Number) r[0]).longValue();
            Long idIgralca = ((Number) r[1]).longValue();
            int sprememba = ((Number) r[2]).intValue();
            m.computeIfAbsent(idTekme, k -> new HashMap<>()).put(idIgralca, sprememba);
        }
        return m;
    }

    private Integer eloZa(Map<Long, Map<Long, Integer>> delte, Long idTekme, Igralec igralec) {
        if (igralec == null) {
            return null;
        }
        return delte.getOrDefault(idTekme, Map.of()).get(igralec.getId());
    }

    private static boolean stejeVElo(IzidTekme izid) {
        return izid == IzidTekme.IGRANO || izid == IzidTekme.PREDAJA;
    }
}
