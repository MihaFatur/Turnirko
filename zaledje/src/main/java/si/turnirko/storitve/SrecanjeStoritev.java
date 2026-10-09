/* Poslovna logika srecanj: dolocanje postave, generiranje posamicnih tekem po
   formatu, menjave igralcev v tekmah, ki se cakajo, vnos rezultatov, pravilo
   predcasnega konca (prvi do N zmag), obracun ratinga za posamicne tekme in
   razveljavitev zapisnika (srecanje nazaj v razpored).

   Postava dodeli igralce iz kadra na mesta (A/B/C, X/Y/Z) in oznaci par za
   dvojice; iz nje se generira urejen seznam tekem. Ko ena stran doseze prag
   zmag, se preostale tekme oznacijo kot NEODIGRANE in srecanje se konca.

   Srecanje pripada ligi ALI ekipni tekmi turnirja (V28) in tece po isti
   kodi: pravila igranja (format, nizi, prag, raven) prebere iz tekmovanja
   (Srecanje.pravila). Kar se po koncu srecanja zgodi, pa je odvisno od tega,
   kam spada:
   - ekipna tekma turnirja dobi izid srecanja in turnir tece naprej
     (napredovanje, skupine, zakljucek dogodka - TekmaStoritev),
   - tekma serije koncnice steje v serijo (KoncnicaStoritev),
   - srecanje rednega dela lige nima nadaljevanja (lestvica je izpeljanka). */
package si.turnirko.storitve;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
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
import si.turnirko.dto.MenjavaVnos;
import si.turnirko.dto.NizVnos;
import si.turnirko.dto.PostavaSrecanjaDto;
import si.turnirko.dto.PostavaVnos;
import si.turnirko.dto.SrecanjeDto;
import si.turnirko.dto.SrecanjePodrobnoDto;
import si.turnirko.dto.TekmaSrecanjaDto;
import si.turnirko.dto.VnosRezultataSrecanja;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.Ekipa;
import si.turnirko.modeli.FazaTekme;
import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.IzidTekme;
import si.turnirko.modeli.KaderEkipe;
import si.turnirko.modeli.NizSrecanja;
import si.turnirko.modeli.PostavaSrecanja;
import si.turnirko.modeli.RatingStanje;
import si.turnirko.modeli.Skupina;
import si.turnirko.modeli.Srecanje;
import si.turnirko.modeli.StatusSrecanja;
import si.turnirko.modeli.StatusTekmeSrecanja;
import si.turnirko.modeli.StranEkipe;
import si.turnirko.modeli.Tekma;
import si.turnirko.modeli.TekmaSrecanja;
import si.turnirko.modeli.TipTekmeSrecanja;
import si.turnirko.repozitoriji.KaderEkipeRepozitorij;
import si.turnirko.repozitoriji.LigaRepozitorij;
import si.turnirko.repozitoriji.NizSrecanjaRepozitorij;
import si.turnirko.repozitoriji.PostavaSrecanjaRepozitorij;
import si.turnirko.repozitoriji.RatingZgodovinaRepozitorij;
import si.turnirko.repozitoriji.SkupinaRepozitorij;
import si.turnirko.repozitoriji.SrecanjeRepozitorij;
import si.turnirko.repozitoriji.TekmaRepozitorij;
import si.turnirko.repozitoriji.TekmaSrecanjaRepozitorij;
import si.turnirko.storitve.LestvicaLigeStoritev.Bilanca;
import si.turnirko.storitve.LestvicaLigeStoritev.BilanceLige;

@Service
public class SrecanjeStoritev {

    /* Termin kola je vpisan v slovenskem casu, zato v njem zapisemo tudi
       trenutek prvega izida - sicer bi ju primerjali v dveh casovnih pasovih. */
    private static final ZoneId SLOVENIJA = ZoneId.of("Europe/Ljubljana");

    private final SrecanjeRepozitorij srecanjeRepozitorij;
    private final PostavaSrecanjaRepozitorij postavaRepozitorij;
    private final TekmaSrecanjaRepozitorij tekmaRepozitorij;
    private final NizSrecanjaRepozitorij nizRepozitorij;
    private final KaderEkipeRepozitorij kaderRepozitorij;
    private final SkupinaRepozitorij skupinaRepozitorij;
    private final TekmaRepozitorij turnirskeTekme;
    private final RatingStoritev ratingStoritev;
    private final RatingZgodovinaRepozitorij zgodovinaRepozitorij;
    private final SpremembeRatingaStoritev spremembeRatinga;
    private final LestvicaLigeStoritev lestvicaLigeStoritev;
    private final TekmaStoritev tekmaStoritev;
    private final KoncnicaStoritev koncnicaStoritev;
    private final LastnistvoStoritev lastnistvo;
    private final PreracunRatingaStoritev preracunRatinga;
    private final LigaRepozitorij ligaRepozitorij;

    public SrecanjeStoritev(SrecanjeRepozitorij srecanjeRepozitorij,
                            PostavaSrecanjaRepozitorij postavaRepozitorij,
                            TekmaSrecanjaRepozitorij tekmaRepozitorij,
                            NizSrecanjaRepozitorij nizRepozitorij,
                            KaderEkipeRepozitorij kaderRepozitorij,
                            SkupinaRepozitorij skupinaRepozitorij,
                            TekmaRepozitorij turnirskeTekme,
                            RatingStoritev ratingStoritev,
                            RatingZgodovinaRepozitorij zgodovinaRepozitorij,
                            SpremembeRatingaStoritev spremembeRatinga,
                            LestvicaLigeStoritev lestvicaLigeStoritev,
                            TekmaStoritev tekmaStoritev,
                            KoncnicaStoritev koncnicaStoritev,
                            LastnistvoStoritev lastnistvo,
                            PreracunRatingaStoritev preracunRatinga,
                            LigaRepozitorij ligaRepozitorij) {
        this.srecanjeRepozitorij = srecanjeRepozitorij;
        this.postavaRepozitorij = postavaRepozitorij;
        this.tekmaRepozitorij = tekmaRepozitorij;
        this.nizRepozitorij = nizRepozitorij;
        this.kaderRepozitorij = kaderRepozitorij;
        this.skupinaRepozitorij = skupinaRepozitorij;
        this.turnirskeTekme = turnirskeTekme;
        this.ratingStoritev = ratingStoritev;
        this.zgodovinaRepozitorij = zgodovinaRepozitorij;
        this.spremembeRatinga = spremembeRatinga;
        this.lestvicaLigeStoritev = lestvicaLigeStoritev;
        this.tekmaStoritev = tekmaStoritev;
        this.koncnicaStoritev = koncnicaStoritev;
        this.lastnistvo = lastnistvo;
        this.preracunRatinga = preracunRatinga;
        this.ligaRepozitorij = ligaRepozitorij;
    }

    @Transactional(readOnly = true)
    public List<SrecanjeDto> zaLigo(Long idLiga) {
        return srecanjeRepozitorij.najdiZaLigo(idLiga).stream().map(SrecanjeDto::iz).toList();
    }

    @Transactional(readOnly = true)
    public SrecanjePodrobnoDto podrobno(Long idSrecanje) {
        Srecanje s = najdiPodrobno(idSrecanje);
        FormatSrecanja format = s.pravila().format();

        List<PostavaSrecanja> postava = postavaRepozitorij.najdiZaSrecanje(idSrecanje);
        List<PostavaSrecanjaDto> postave = postava.stream().map(PostavaSrecanjaDto::iz).toList();

        List<TekmaSrecanja> tekme = tekmaRepozitorij.najdiZaSrecanje(idSrecanje);
        Map<Long, Map<Long, Integer>> delte = delteRatinga(tekme.stream().map(TekmaSrecanja::getId).toList());
        Map<Long, List<NizVnos>> nizi = niziSrecanja(idSrecanje);
        List<TekmaSrecanjaDto> tekmeDto = tekme.stream()
                .map(t -> tekmaDto(t, delte, nizi.getOrDefault(t.getId(), List.of()), postava))
                .toList();

        BilanceLige bilance = s.jeTurnirsko()
                ? lestvicaLigeStoritev.bilancePosamicnihDogodka(s.getTekma().getDogodek().getId())
                : lestvicaLigeStoritev.bilancePosamicnih(s.getLiga().getId());
        return new SrecanjePodrobnoDto(
                SrecanjeDto.iz(s), kontekst(s), format,
                format.pozicijeDomaci(), format.pozicijeGost(),
                format.izbiraDvojice(), format.stVDvojici(),
                postave, tekmeDto,
                kader(s.getEkipaDomaci().getId(), bilance), kader(s.getEkipaGost().getId(), bilance));
    }

    // ---------- Postava ----------

    @Transactional
    public void nastaviPostavo(Long idSrecanje, PostavaVnos vnos) {
        lastnistvo.preveriPoSrecanju(idSrecanje);
        Srecanje s = najdiPodrobno(idSrecanje);
        FormatSrecanja format = s.pravila().format();

        if (s.jeTurnirsko() && s.getTekma().getStatus() == si.turnirko.modeli.StatusTekme.KONCANA) {
            throw new DomenskaIzjema("Ekipna tekma je ze koncana.");
        }

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
        if (s.jeTurnirsko()) {
            // ekipna tekma v mrezi se je zacela - enako kot tekma posameznikov
            tekmaStoritev.oznaciVIgri(s.getTekma().getId());
        }
    }

    // ---------- Rezultat ----------

    @Transactional
    public TekmaSrecanjaDto vnesiRezultat(Long idTekma, VnosRezultataSrecanja v) {
        lastnistvo.preveriPoTekmiSrecanja(idTekma);
        TekmaSrecanja t = tekmaRepozitorij.najdiZaObracun(idTekma)
                .orElseThrow(() -> new NiNajdenoIzjema("Tekma srecanja z id " + idTekma + " ne obstaja."));
        Srecanje s = t.getSrecanje();
        Srecanje.Pravila pravila = s.pravila();

        if (s.getStatus() == StatusSrecanja.KONCANO) {
            throw new DomenskaIzjema("Srecanje je ze koncano.");
        }
        if (t.getStatus() == StatusTekmeSrecanja.NEODIGRANA) {
            throw new DomenskaIzjema("Ta tekma se po pravilih ne igra (srecanje je bilo ze odloceno).");
        }

        Izid izid = preberiIzid(t, v);
        /* Prvi izid srecanja zabelezi, kdaj je bilo najkasneje odigrano - PRED
           obracunom, da ga obracun in poznejsi preracun preberata enako (glej
           Srecanje.casOdigranja). Slovenski cas, ker je v njem tudi termin;
           na minuto kot termin, da obracun in preracun zapiseta isti niz. */
        if (s.getOdigranOb() == null) {
            s.setOdigranOb(LocalDateTime.now(SLOVENIJA).truncatedTo(ChronoUnit.MINUTES));
        }
        List<NizVnos> vneseniNizi = zapisiIzid(t, izid);

        // rating samo za posamicne tekme, ce tekmovanje steje in izid steje
        // (w.o. in diskvalifikacija ne stejeta - enako kot pri turnirjih)
        if (t.getTip() == TipTekmeSrecanja.POSAMICNA && pravila.raven().steje()
                && stejeVElo(izid.tip())) {
            ratingStoritev.obracunajZaLigasko(t);
        }

        posodobiSrecanje(s, pravila.zmagZaSrecanje());
        if (s.getStatus() == StatusSrecanja.KONCANO && preracunajCeVnesenoZaNazaj(s)) {
            // preracun izprazni sejo (pobrisiZaTekmeSrecanja), igralca para pa
            // najdiZaObracun ne nalozi - tekmo za odgovor preberemo znova
            t = tekmaRepozitorij.najdiZaSrecanje(s.getId()).stream()
                    .filter(x -> x.getId().equals(idTekma)).findFirst().orElseThrow();
        }

        Map<Long, Map<Long, Integer>> delte = delteRatinga(List.of(t.getId()));
        return tekmaDto(t, delte, vneseniNizi, postavaRepozitorij.najdiZaSrecanje(s.getId()));
    }

    /* Obracun tece v vrstnem redu VNOSA (glej RatingStoritev), zato je
       srecanje, vneseno za nazaj, obracunano proti stevilkam, ki ze vsebujejo
       poznejse tekme istih igralcev - tipicno srecanje, ki ga je organizator
       razveljavil in vnesel znova, ali vecer, vnesen od zadnje ure proti prvi.
       Ko je srecanje vneseno do konca, zato preverimo, ali ima kdo od njegovih
       igralcev v dnevniku zapis, ki velja POZNEJE, in v tem primeru rating
       preracunamo od casa srecanja. Enkrat na srecanje in ne ob vsaki tekmi:
       preracun obnovi stanje vseh igralcev in traja nekaj sekund. Pri sprotnem
       vnosu poznejsih zapisov ni in preracun ne tece.

       Samo ligasko srecanje: ekipna tekma turnirja ima cas dneva turnirja kot
       ostale tekme turnirja, ki tega preverjanja nimajo. Vrne, ali je preracun
       tekel. */
    private boolean preracunajCeVnesenoZaNazaj(Srecanje s) {
        if (s.jeTurnirsko() || !s.pravila().raven().steje()) {
            return false;
        }
        Set<Long> igralci = new HashSet<>();
        for (TekmaSrecanja t : tekmaRepozitorij.najdiZaSrecanje(s.getId())) {
            if (t.getTip() == TipTekmeSrecanja.POSAMICNA && t.getStatus() == StatusTekmeSrecanja.KONCANA
                    && stejeVElo(t.getIzidTip())) {
                igralci.add(t.getIgralecDomaci().getId());
                igralci.add(t.getIgralecGost().getId());
            }
        }
        LocalDateTime cas = s.casOdigranja();
        if (igralci.isEmpty() || cas == null || zgodovinaRepozitorij.steviloPoznejsihZapisov(
                RatingStanje.SISTEM_TURNIRKO, igralci, cas, s.getId()) == 0) {
            return false;
        }
        preracunRatinga.preracunajPoPopravku(cas);
        return true;
    }

    /* RAZVELJAVITEV ZAPISNIKA: srecanje se vrne v stanje pred vnosom, kot da
       ga nihce ni vpisal - postava, posamicne tekme (z menjavami), tocke po
       nizih in obracun ratinga gredo, srecanje pa spet caka v razporedu.

       Zakaj in ne popravek: popravek zamenja izid, igralcev pa ne - ti so
       nastali iz postave, postave pa po prvem izidu ni mogoce spremeniti.
       Ko organizator vpise napacna igralca (ali celo napacno srecanje), je
       edina pot, da zapisnik razveljavi in ga vnese znova.

       Kdaj ne: kadar je po izidu srecanja ze teklo nekaj drugega - isto
       pravilo kot pri popravku, ki obrne zmagovalca (preveriZamenjavoZmagovalca):
       ekipna tekma turnirja (izid je postal izid tekme v mrezi), koncana tekma
       koncnice (serija je tekla) in srecanje rednega dela, kadar sta iz koncne
       lestvice ze nastali koncnica ali kvalifikacije.

       Cas prvega izida (odigranOb) gre z zapisnikom: pri ponovnem vnosu se
       zapise znova, sicer bi srecanje, razveljavljeno zato, ker je bilo
       vpisano v napacen par ekip, obdrzalo cas tujega vecera. Rating se
       preracuna od casa srecanja, ker je izid vstopal v vse poznejse tekme
       njegovih igralcev; ponovni vnos to poskrbi za nazaj sam
       (preracunajCeVnesenoZaNazaj). */
    @Transactional
    public SrecanjePodrobnoDto razveljaviZapisnik(Long idSrecanje) {
        lastnistvo.preveriPoSrecanju(idSrecanje);
        Srecanje s = najdiPodrobno(idSrecanje);
        preveriRazveljavitev(s);

        List<TekmaSrecanja> tekme = tekmaRepozitorij.najdiZaSrecanje(idSrecanje);
        LocalDateTime cas = s.casOdigranja();
        for (TekmaSrecanja t : tekme) {
            nizRepozitorij.deleteAll(nizRepozitorij.findByTekmaIdOrderByZaporednaStAsc(t.getId()));
        }
        /* Dnevnik pred tekmami (tuji kljuc), izbris pa izprazni sejo - zato
           srecanje spodaj preberemo znova. */
        int obracunov = tekme.isEmpty() ? 0 : zgodovinaRepozitorij.pobrisiZaTekmeSrecanja(
                RatingStanje.SISTEM_TURNIRKO, tekme.stream().map(TekmaSrecanja::getId).toList());
        tekmaRepozitorij.deleteBySrecanjeId(idSrecanje);
        postavaRepozitorij.deleteBySrecanjeId(idSrecanje);
        tekmaRepozitorij.flush();
        postavaRepozitorij.flush();

        Srecanje prazno = najdiPodrobno(idSrecanje);
        prazno.setDobljeneDomaci(0);
        prazno.setDobljeneGost(0);
        prazno.setOdigranOb(null);
        prazno.setStatus(StatusSrecanja.RAZPORED);
        srecanjeRepozitorij.save(prazno);

        if (obracunov > 0) {
            preracunRatinga.preracunajPoPopravku(cas);
        }
        return podrobno(idSrecanje);
    }

    private void preveriRazveljavitev(Srecanje s) {
        if (s.jeTurnirsko()) {
            throw new DomenskaIzjema("Zapisnika ekipne tekme turnirja ni mogoce razveljaviti:"
                    + " izid srecanja je postal izid tekme v mrezi oz. skupini in po njem je"
                    + " turnir ze tekel. Popravi lahko izid posamezne tekme.");
        }
        if (s.getStatus() == StatusSrecanja.RAZPORED) {
            throw new DomenskaIzjema("Srecanje se nima vpisanega zapisnika.");
        }
        if (s.jeKoncnica()) {
            if (s.getStatus() == StatusSrecanja.KONCANO) {
                throw new DomenskaIzjema("Tekma koncnice je ze koncana in po njej je serija ze"
                        + " tekla, zato njenega zapisnika ni mogoce razveljaviti. Popravi lahko"
                        + " izid posamezne tekme.");
            }
            return;
        }
        Long idLiga = s.getLiga().getId();
        if (koncnicaStoritev.jeSestavljena(idLiga)) {
            throw new DomenskaIzjema("Koncnica je ze sestavljena iz koncne lestvice rednega dela."
                    + " Najprej razveljavi koncnico, nato zapisnik.");
        }
        if (ligaRepozitorij.existsByKvalifikacijeVisjaIdOrKvalifikacijeNizjaId(idLiga, idLiga)) {
            throw new DomenskaIzjema("Iz koncne lestvice te lige so ze nastale kvalifikacije."
                    + " Najprej razveljavi kvalifikacije, nato zapisnik.");
        }
    }

    /* POPRAVEK ze shranjenega rezultata posamicne tekme srecanja.

       Rezultat se prepisuje s papirja in papir se bere z napako - tudi tako,
       da se zamenjata imeni in v zapisniku zmaga drugi. Zato sme popravek v
       srecanju spremeniti tudi ZMAGOVALCA tekme, a samo, dokler s tem ne
       razveljavi poteka tekmovanja (preveriZamenjavoZmagovalca):
         * potek srecanja ostane isti - iste tekme so odigrane in iste
           neodigrane (pri pragu zmag bi drug zmagovalec lahko pomenil, da se
           srecanje konca prej ali pozneje, tega pa papir ne more vedeti),
         * izid srecanja, po katerem je ze teklo nekaj drugega (serija
           koncnice, koncnica iz lestvice, ekipna tekma turnirja v mrezi), se
           ne spremeni.
       Liga, ki odigra vse tekme srecanja (Savinja), zato zmagovalca popravi
       vedno; lestvica lige je izpeljanka in sledi sama.

       Pri spremenjenem zmagovalcu se osvezi povzetek srecanja (dobljene
       tekme), status pa ostane - enak potek je pogoj. Nadaljevanja (serija,
       ekipna tekma) se ne sprozijo znova: izid, po katerem sta tekla, je isti.
       Preracuna se rating: izid te tekme je vstopal v vse poznejse tekme obeh
       igralcev. */
    @Transactional
    public TekmaSrecanjaDto popraviRezultat(Long idTekma, VnosRezultataSrecanja v) {
        lastnistvo.preveriPoTekmiSrecanja(idTekma);
        TekmaSrecanja t = tekmaRepozitorij.najdiZaObracun(idTekma)
                .orElseThrow(() -> new NiNajdenoIzjema("Tekma srecanja z id " + idTekma + " ne obstaja."));
        Srecanje s = t.getSrecanje();

        if (t.getStatus() != StatusTekmeSrecanja.KONCANA) {
            throw new DomenskaIzjema("Popraviti je mogoce samo koncano tekmo -"
                    + " tej rezultat se ni bil vnesen.");
        }

        Izid izid = preberiIzid(t, v);
        boolean drugZmagovalec = izid.zmagovalec() != t.getZmagovalecStran();
        // preveri se PRED zapisom - zavrnjen popravek ne sme nicesar spremeniti
        if (drugZmagovalec) {
            preveriZamenjavoZmagovalca(s, t, izid.zmagovalec());
        }

        /* Stare tocke gredo, nove se pisejo od prvega niza naprej. Izbris se
           izpere TAKOJ: Hibernate sicer vstavke izvede pred izbrisi in nov prvi
           niz bi trcil ob starega (UNIQUE (id_tekma_srecanja, zaporedna_st)). */
        nizRepozitorij.deleteAll(nizRepozitorij.findByTekmaIdOrderByZaporednaStAsc(idTekma));
        nizRepozitorij.flush();
        List<NizVnos> vneseniNizi = zapisiIzid(t, izid);

        if (drugZmagovalec) {
            /* Ista pot kot ob vnosu: pri enakem poteku le presteje dobljene
               tekme, srecanju v teku pa lahko prinese odlocitev (prag). */
            posodobiSrecanje(s, s.pravila().zmagZaSrecanje());
        }

        preracunRatinga.preracunajPoPopravku(RatingStoritev.casLigaskeTekme(t));

        Map<Long, Map<Long, Integer>> delte = delteRatinga(List.of(t.getId()));
        return tekmaDto(t, delte, vneseniNizi, postavaRepozitorij.najdiZaSrecanje(s.getId()));
    }

    /* Izid srecanja iz koncanih tekem - 1 domaci, -1 gost, 0 izenaceno; tekmi
       `popravljena` se pri tem steje zmagovalec `novi` (null = kot je zapisano). */
    private static int izidSrecanja(List<TekmaSrecanja> tekme, TekmaSrecanja popravljena,
                                    StranEkipe novi) {
        int razlika = 0;
        for (TekmaSrecanja t : tekme) {
            if (t.getStatus() != StatusTekmeSrecanja.KONCANA) {
                continue;
            }
            StranEkipe zmagovalec = popravljena != null && t.getId().equals(popravljena.getId())
                    ? novi : t.getZmagovalecStran();
            if (zmagovalec == StranEkipe.DOMACI) {
                razlika++;
            } else if (zmagovalec == StranEkipe.GOST) {
                razlika--;
            }
        }
        return Integer.signum(razlika);
    }

    /* Ali sme popravek tekmi dati drugega zmagovalca - glej popraviRezultat. */
    private void preveriZamenjavoZmagovalca(Srecanje s, TekmaSrecanja popravljena, StranEkipe novi) {
        if (s.jeTurnirsko()) {
            throw new DomenskaIzjema("Zmagovalca tekme v ekipni tekmi turnirja ni mogoce"
                    + " spremeniti: izid srecanja je postal izid tekme v mrezi oz. skupini in"
                    + " po njem je turnir ze tekel. Popravi lahko izid v nizih, tocke po nizih"
                    + " in nacin zakljucka.");
        }

        List<TekmaSrecanja> tekme = tekmaRepozitorij.najdiZaSrecanje(s.getId());
        preveriEnakPotek(tekme, popravljena, novi, s.pravila().zmagZaSrecanje());

        if (s.getStatus() != StatusSrecanja.KONCANO) {
            return; // srecanje se tece - izid srecanja se ni nikamor vstopil
        }
        if (izidSrecanja(tekme, popravljena, novi) == izidSrecanja(tekme, null, null)) {
            return;
        }
        if (s.jeKoncnica()) {
            throw new DomenskaIzjema("Popravek bi spremenil zmagovalca tekme koncnice, po njem"
                    + " pa je serija ze tekla. Popravi lahko tekmo, ki izida srecanja ne obrne.");
        }
        if (koncnicaStoritev.jeSestavljena(s.getLiga().getId())) {
            throw new DomenskaIzjema("Popravek bi spremenil izid srecanja rednega dela, koncnica"
                    + " pa je ze sestavljena iz koncne lestvice. Najprej razveljavi koncnico.");
        }
    }

    /* Po pravilu praga (prvi do N zmag) se srecanje konca s tekmo, v kateri ena
       stran doseze prag; poznejse tekme se ne igrajo. Popravljen zapisnik mora
       biti z njim skladen - sicer bi popravek trdil, da so bile odigrane tekme,
       ki jih po pravilih ni bilo, ali da manjkajo tekme, ki so se morale
       odigrati (te so neodigrane in jih ni mogoce vpisati). Tekme, ki se
       CAKAJO, so v redu v obeh primerih: srecanju v teku lahko popravek
       prinese odlocitev (posodobiSrecanje), kot bi jo prinesel vnos. Brez
       praga se odigrajo vse tekme in potek je vedno isti. */
    private static void preveriEnakPotek(List<TekmaSrecanja> tekme, TekmaSrecanja popravljena,
                                         StranEkipe novi, Integer prag) {
        if (prag == null) {
            return;
        }
        int domaci = 0;
        int gost = 0;
        Integer odlocitev = null; // zaporedje tekme, v kateri je bil dosezen prag
        for (TekmaSrecanja t : tekme) {
            if (t.getStatus() != StatusTekmeSrecanja.KONCANA) {
                continue;
            }
            StranEkipe zmagovalec = t.getId().equals(popravljena.getId()) ? novi : t.getZmagovalecStran();
            if (zmagovalec == StranEkipe.DOMACI) {
                domaci++;
            } else {
                gost++;
            }
            if (odlocitev == null && (domaci >= prag || gost >= prag)) {
                odlocitev = t.getZaporedje();
            }
        }
        for (TekmaSrecanja t : tekme) {
            boolean odigrana = t.getStatus() == StatusTekmeSrecanja.KONCANA;
            if (odlocitev != null && odigrana && t.getZaporedje() > odlocitev) {
                throw new DomenskaIzjema("Popravek bi spremenil potek srecanja: s tem izidom bi bilo"
                        + " srecanje odloceno ze po " + odlocitev + ". tekmi, v zapisniku pa so"
                        + " odigrane tudi poznejse. Preveri zapisnik - zmagovalca tu ni mogoce obrniti.");
            }
            if (odlocitev == null && t.getStatus() == StatusTekmeSrecanja.NEODIGRANA) {
                throw new DomenskaIzjema("Popravek bi spremenil potek srecanja: s tem izidom srecanje"
                        + " ne bi bilo odloceno, preostale tekme pa so v zapisniku neodigrane."
                        + " Preveri zapisnik - zmagovalca tu ni mogoce obrniti.");
            }
        }
    }

    /* Preverjen izid vnosa, se preden se karkoli zapise - popravek iz njega
       prebere zmagovalca, preden se odloci, ali sme nadaljevati. */
    private record Izid(IzidTekme tip, int niziDomaci, int niziGost, StranEkipe zmagovalec,
                        List<NizVnos> nizi) {}

    private static Izid preberiIzid(TekmaSrecanja t, VnosRezultataSrecanja v) {
        int zaZmago = t.nizovZaZmago();
        IzidTekme izid = v.izidTip() != null ? v.izidTip() : IzidTekme.IGRANO;

        if (izid == IzidTekme.PROSTO) {
            throw new NeveljavenVnosIzjema("Izid PROSTO v srecanju ni mogoc.");
        }
        if (izid != IzidTekme.IGRANO) {
            if (v.zmagovalecStran() == null) {
                throw new NeveljavenVnosIzjema("Za posebni izid je obvezna zmagovalna stran.");
            }
            StranEkipe zmagovalec = v.zmagovalecStran();
            // Tocke nizov obstajajo samo pri dejansko odigrani tekmi: pri w.o.,
            // diskvalifikaciji in predaji dobi zmagovalec nize pripisane in
            // posameznih izidov ni (enako kot pri turnirjih).
            return new Izid(izid,
                    zmagovalec == StranEkipe.DOMACI ? zaZmago : 0,
                    zmagovalec == StranEkipe.GOST ? zaZmago : 0,
                    zmagovalec, List.of());
        }

        if (v.dobljeniNiziDomaci() == null || v.dobljeniNiziGost() == null) {
            throw new NeveljavenVnosIzjema("Manjkata dobljena niza.");
        }
        int niziDomaci = v.dobljeniNiziDomaci();
        int niziGost = v.dobljeniNiziGost();
        if (niziDomaci < 0 || niziGost < 0) {
            throw new NeveljavenVnosIzjema("Stevilo dobljenih nizov ne sme biti negativno.");
        }
        if (niziDomaci == niziGost) {
            throw new NeveljavenVnosIzjema("Neodlocen izid posamicne tekme ni mozen.");
        }
        if (Math.max(niziDomaci, niziGost) != zaZmago) {
            throw new NeveljavenVnosIzjema("Zmagovalec mora dobiti natanko " + zaZmago + " nizov.");
        }
        if (Math.min(niziDomaci, niziGost) > zaZmago - 1) {
            throw new NeveljavenVnosIzjema("Porazenec ima prevec dobljenih nizov.");
        }
        // tocke po nizih so neobvezne - preverimo jih pred vsako spremembo
        // stanja, po istih pravilih kot pri turnirski tekmi
        List<NizVnos> nizi = v.nizi() != null ? v.nizi() : List.<NizVnos>of();
        if (!nizi.isEmpty()) {
            NiziPravila.preveri(nizi, niziDomaci, niziGost, zaZmago);
        }
        return new Izid(izid, niziDomaci, niziGost,
                niziDomaci > niziGost ? StranEkipe.DOMACI : StranEkipe.GOST, nizi);
    }

    /* Zapise preverjen izid v tekmo in njene nize; vrne vpisane nize za DTO. */
    private List<NizVnos> zapisiIzid(TekmaSrecanja t, Izid izid) {
        t.setDobljeniNiziDomaci(izid.niziDomaci());
        t.setDobljeniNiziGost(izid.niziGost());
        t.setZmagovalecStran(izid.zmagovalec());
        t.setIzidTip(izid.tip());
        t.setStatus(StatusTekmeSrecanja.KONCANA);
        tekmaRepozitorij.save(t);

        int zaporedna = 1;
        for (NizVnos niz : izid.nizi()) {
            nizRepozitorij.save(new NizSrecanja(t, zaporedna, niz.tocke1(), niz.tocke2()));
            zaporedna++;
        }
        return izid.nizi();
    }

    // ---------- Menjava ----------

    /* Menjava: eno se neodigrano tekmo srecanja igra drug igralec iz kadra.
       Postava ostane ZACETNA postava - iz nje so nastale tekme -, kdo tekmo res
       igra, pa stoji v tekmi sami. Zato menjava tekmi ne spremeni ne mesta v
       zaporedju ne oznake: "A-Y" ostane "A-Y", le igralec je drug (zapisnik to
       oznaci, glej menjava()). Rating, lestvica igralcev in bilanca kadra ze
       zdaj berejo igralca iz tekme, zato stejejo zamenjanega brez posebne veje.

       Menjava velja za ENO tekmo in ne "od tu naprej". V ligi se igralci med
       srecanjem menjajo prosto: po dvojicah AB/XY ter A-X in B-Y lahko sledita
       C-Y in A-Z - A se je umaknil C-ju in se vrnil na mesto B. Pravilo
       "zamenjani ostane zunaj" bi tak vpis onemogocilo, organizator pa tako
       prepise zapisnik s papirja tekmo za tekmo.

       Samo tekma, ki CAKA: odigrana ima ze obracunan rating, neodigrana pa se
       po pravilih ne igra vec. Igralec mora biti v kadru svoje ekipe - kdor na
       srecanje pride na novo, gre najprej v kader (ta se sme dopolnjevati tudi
       med sezono). Koliko tekem odigra en igralec, strezniku ni mar: to je
       pravilo tekmovanja, ki ga liga v aplikaciji ne pozna. */
    @Transactional
    public SrecanjePodrobnoDto zamenjajIgralce(Long idTekma, MenjavaVnos v) {
        lastnistvo.preveriPoTekmiSrecanja(idTekma);
        TekmaSrecanja t = tekmaRepozitorij.najdiZaObracun(idTekma)
                .orElseThrow(() -> new NiNajdenoIzjema("Tekma srecanja z id " + idTekma + " ne obstaja."));
        Srecanje s = t.getSrecanje();

        if (s.getStatus() == StatusSrecanja.KONCANO) {
            throw new DomenskaIzjema("Srecanje je ze koncano.");
        }
        if (t.getStatus() == StatusTekmeSrecanja.KONCANA) {
            throw new DomenskaIzjema("Tekma je ze odigrana - igralcev ni vec mogoce zamenjati.");
        }
        if (t.getStatus() == StatusTekmeSrecanja.NEODIGRANA) {
            throw new DomenskaIzjema("Ta tekma se po pravilih ne igra (srecanje je bilo ze odloceno).");
        }

        boolean dvojice = t.getTip() == TipTekmeSrecanja.DVOJICE;
        List<Long> domaci = igralciStrani(v.idDomaci(), v.idDomaci2(), dvojice, "domacih");
        List<Long> gost = igralciStrani(v.idGost(), v.idGost2(), dvojice, "gostov");
        if (domaci.stream().anyMatch(gost::contains)) {
            throw new NeveljavenVnosIzjema("Isti igralec ne more igrati za obe ekipi.");
        }

        Map<Long, Igralec> kaderDomaci = kaderPoId(s.getEkipaDomaci());
        Map<Long, Igralec> kaderGost = kaderPoId(s.getEkipaGost());
        for (Long id : domaci) {
            if (!kaderDomaci.containsKey(id)) {
                throw new DomenskaIzjema("Igralec ni v kadru domacih.");
            }
        }
        for (Long id : gost) {
            if (!kaderGost.containsKey(id)) {
                throw new DomenskaIzjema("Igralec ni v kadru gostov.");
            }
        }

        t.setIgralecDomaci(kaderDomaci.get(domaci.get(0)));
        t.setIgralecGost(kaderGost.get(gost.get(0)));
        t.setIgralecDomaci2(dvojice ? kaderDomaci.get(domaci.get(1)) : null);
        t.setIgralecGost2(dvojice ? kaderGost.get(gost.get(1)) : null);
        tekmaRepozitorij.save(t);
        return podrobno(s.getId());
    }

    /* Igralci ene strani tekme: pri posamicni en, pri dvojicah dva razlicna. */
    private static List<Long> igralciStrani(Long prvi, Long drugi, boolean dvojice, String opis) {
        if (dvojice) {
            if (prvi == null || drugi == null) {
                throw new NeveljavenVnosIzjema("Za dvojice " + opis + " sta potrebna dva igralca.");
            }
            if (prvi.equals(drugi)) {
                throw new NeveljavenVnosIzjema("Par " + opis + " sestavljata dva razlicna igralca.");
            }
            return List.of(prvi, drugi);
        }
        if (prvi == null) {
            throw new NeveljavenVnosIzjema("Manjka igralec " + opis + ".");
        }
        if (drugi != null) {
            throw new NeveljavenVnosIzjema("Posamicno tekmo igra en igralec na strani.");
        }
        return List.of(prvi);
    }

    private Map<Long, Igralec> kaderPoId(Ekipa ekipa) {
        Map<Long, Igralec> poId = new HashMap<>();
        for (KaderEkipe k : kaderRepozitorij.najdiZaEkipo(ekipa.getId())) {
            poId.put(k.getIgralec().getId(), k.getIgralec());
        }
        return poId;
    }

    /* Menjava na strani tekme: igra kdo drug kot tisti, ki je v zacetni postavi
       na mestu, iz katerega je tekma nastala. Mesto se prebere iz OZNAKE
       ("A-Y"; pri dvojicah par, oznacen v postavi) in ne iz zaporedja formata,
       ker imajo uvozena srecanja svoje zaporedje. Kadar mesta ni mogoce najti
       (uvoz brez postave, oznaka "?-X", par brez natanko dveh oznacenih), se
       menjava ne ugiba - raje nic kot napacna oznaka. */
    private static Menjava menjava(TekmaSrecanja t, List<PostavaSrecanja> postava) {
        if (t.getTip() == TipTekmeSrecanja.DVOJICE) {
            return new Menjava(
                    menjavaPara(postava, StranEkipe.DOMACI, t.getIgralecDomaci(), t.getIgralecDomaci2()),
                    menjavaPara(postava, StranEkipe.GOST, t.getIgralecGost(), t.getIgralecGost2()));
        }
        String[] mesti = t.getOznaka().split("-");
        if (mesti.length != 2) {
            return new Menjava(false, false);
        }
        return new Menjava(
                menjavaMesta(postava, StranEkipe.DOMACI, mesti[0], t.getIgralecDomaci()),
                menjavaMesta(postava, StranEkipe.GOST, mesti[1], t.getIgralecGost()));
    }

    private record Menjava(boolean domaci, boolean gost) {}

    private static boolean menjavaMesta(List<PostavaSrecanja> postava, StranEkipe stran,
                                        String mesto, Igralec igralec) {
        if (igralec == null) {
            return false;
        }
        return postava.stream()
                .filter(p -> p.getStran() == stran && p.getPozicija().equals(mesto))
                .findFirst()
                .map(p -> !p.getIgralec().getId().equals(igralec.getId()))
                .orElse(false);
    }

    private static boolean menjavaPara(List<PostavaSrecanja> postava, StranEkipe stran,
                                       Igralec prvi, Igralec drugi) {
        if (prvi == null || drugi == null) {
            return false;
        }
        Set<Long> par = new HashSet<>();
        for (PostavaSrecanja p : postava) {
            if (p.getStran() == stran && p.isVDvojici()) {
                par.add(p.getIgralec().getId());
            }
        }
        return par.size() == 2 && !par.equals(Set.of(prvi.getId(), drugi.getId()));
    }

    private TekmaSrecanjaDto tekmaDto(TekmaSrecanja t, Map<Long, Map<Long, Integer>> delte,
                                      List<NizVnos> nizi, List<PostavaSrecanja> postava) {
        Menjava menjava = menjava(t, postava);
        return TekmaSrecanjaDto.iz(t,
                ratingZa(delte, t.getId(), t.getIgralecDomaci()),
                ratingZa(delte, t.getId(), t.getIgralecGost()),
                nizi, menjava.domaci(), menjava.gost());
    }

    /* Osvezi povzetek srecanja in uveljavi pravilo predcasnega konca. Ko se
       srecanje konca, sprozi nadaljevanje v tekmovanju, ki mu pripada. */
    private void posodobiSrecanje(Srecanje s, Integer prag) {
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
        } else {
            s.setStatus(StatusSrecanja.POTEKA);
        }
        srecanjeRepozitorij.save(s);

        if (odloceno && s.jeTurnirsko()) {
            tekmaStoritev.zakljuciEkipnoTekmo(s.getTekma().getId(), zmageDomaci, zmageGost);
        } else if (odloceno && s.jeKoncnica()) {
            koncnicaStoritev.obKoncanemSrecanju(s.getId());
        }
    }

    // ---------- Pomozno ----------

    private Srecanje najdiPodrobno(Long idSrecanje) {
        return srecanjeRepozitorij.najdiPodrobno(idSrecanje)
                .orElseThrow(() -> new NiNajdenoIzjema("Srecanje z id " + idSrecanje + " ne obstaja."));
    }

    /* Kje srecanje stoji - za naslov zapisnika. */
    private SrecanjePodrobnoDto.Kontekst kontekst(Srecanje s) {
        if (s.jeTurnirsko()) {
            Tekma tekma = s.getTekma();
            Dogodek dogodek = tekma.getDogodek();
            return new SrecanjePodrobnoDto.Kontekst(dogodek.getTurnir().getIme(), null,
                    dogodek.getIme(), opisEkipneTekme(tekma), dogodek.getTurnir().getVir());
        }
        String opis = s.jeKoncnica() ? KoncnicaStoritev.opisTekme(s) : s.getKolo() + ". kolo";
        return new SrecanjePodrobnoDto.Kontekst(s.getLiga().getIme(), s.getLiga().getSezona(),
                null, opis, s.getLiga().getVir());
    }

    /* Mesto ekipne tekme v turnirju: skupina z imenom oz. faza mreze. */
    private String opisEkipneTekme(Tekma tekma) {
        if (tekma.getFaza() == FazaTekme.SKUPINA && tekma.getIdSkupina() != null) {
            return skupinaRepozitorij.findById(tekma.getIdSkupina())
                    .map(sk -> (sk.getIme() != null ? sk.getIme() : "skupina " + sk.getOznaka())
                            + " · " + tekma.getKolo() + ". kolo")
                    .orElse("skupine");
        }
        if (tekma.getFaza() == FazaTekme.TOLAZILNI && tekma.getDogodek().isTekmaZaTretjeMesto()) {
            return "za 3. mesto";
        }
        Integer zadnje = turnirskeTekme.zadnjaKolaPoDogodkih().stream()
                .filter(r -> ((Number) r[0]).longValue() == tekma.getDogodek().getId())
                .map(r -> ((Number) r[1]).intValue())
                .findFirst().orElse(null);
        return PovzetkiStoritev.opisFaze(tekma.getDogodek().getSistemTekmovanja(), tekma.getFaza(),
                tekma.getKolo(), zadnje);
    }

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
        int steviloNizov = s.pravila().steviloNizov();
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

    /* Kader za izbiro postave: vrstni red je organizatorjev (mesta A/B/C se
       delijo po njem), zato se tu NE razvrsca po izkupicku kot na strani lige
       (glej LigaStoritev.kader). Bilanca je bilanca pri TEJ ekipi. */
    private List<KaderIgralecDto> kader(Long idEkipa, BilanceLige bilance) {
        List<KaderEkipe> kader = kaderRepozitorij.najdiZaEkipo(idEkipa);
        Map<Long, Integer> ratingi = spremembeRatinga.trenutniRatingi(
                kader.stream().map(k -> k.getIgralec().getId()).toList());
        return kader.stream()
                .map(k -> {
                    Long idIgralec = k.getIgralec().getId();
                    Bilanca b = bilance.za(idEkipa, idIgralec);
                    return KaderIgralecDto.iz(k, ratingi.get(idIgralec), b.zmage(), b.porazi());
                })
                .toList();
    }

    /* Tocke po nizih vseh tekem srecanja: id tekme -> nizi po vrsti. Ena
       poizvedba za cel zapisnik; tekma brez vpisanih tock v mapi ni. */
    private Map<Long, List<NizVnos>> niziSrecanja(Long idSrecanje) {
        Map<Long, List<NizVnos>> po = new HashMap<>();
        for (Object[] v : nizRepozitorij.tockeZaSrecanje(idSrecanje)) {
            po.computeIfAbsent(((Number) v[0]).longValue(), k -> new ArrayList<>())
                    .add(new NizVnos(((Number) v[2]).intValue(), ((Number) v[3]).intValue()));
        }
        return po;
    }

    private Map<Long, Map<Long, Integer>> delteRatinga(Collection<Long> idjiTekem) {
        Map<Long, Map<Long, Integer>> m = new HashMap<>();
        if (idjiTekem.isEmpty()) {
            return m;
        }
        for (Object[] r : zgodovinaRepozitorij.spremembeZaTekmeSrecanja(idjiTekem, RatingStanje.SISTEM_TURNIRKO)) {
            Long idTekme = ((Number) r[0]).longValue();
            Long idIgralca = ((Number) r[1]).longValue();
            int sprememba = ((Number) r[2]).intValue();
            m.computeIfAbsent(idTekme, k -> new HashMap<>()).put(idIgralca, sprememba);
        }
        return m;
    }

    private Integer ratingZa(Map<Long, Map<Long, Integer>> delte, Long idTekme, Igralec igralec) {
        if (igralec == null) {
            return null;
        }
        return delte.getOrDefault(idTekme, Map.of()).get(igralec.getId());
    }

    private static boolean stejeVElo(IzidTekme izid) {
        return izid == IzidTekme.IGRANO || izid == IzidTekme.PREDAJA;
    }
}
