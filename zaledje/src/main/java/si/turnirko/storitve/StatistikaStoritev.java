/* Statistika prek vseh tekmovanj: globalna lestvica igralcev (po klubskem
   ELO) in pregled "1 na 1" (vsi medsebojni izidi dveh igralcev).
   Racuna se iz dnevnika tekem, zato je vedno v skladu z dejanskimi izidi.

   Vir sta DVE tabeli: turnirske tekme (tekma) in posamicne tekme ligaskih
   srecanj (tekma_srecanja). Oboje steje enakovredno - ligaska posamicna tekma
   je za igralca prav tako odigrana tekma kot turnirska, zato mora steti tudi
   v zmage/poraze in medsebojni izid, ne le v ELO. Ligaske dvojice ne stejejo
   nikamor, ker izida ni mogoce pripisati posamezniku. */
package si.turnirko.storitve;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.DvobojDto;
import si.turnirko.dto.LestvicaIgralcaDto;
import si.turnirko.dto.ZadnjaTekmaDto;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.RatingStanje;
import si.turnirko.modeli.Srecanje;
import si.turnirko.modeli.StranEkipe;
import si.turnirko.modeli.Tekma;
import si.turnirko.modeli.TekmaSrecanja;
import si.turnirko.repozitoriji.IgralecRepozitorij;
import si.turnirko.repozitoriji.RatingStanjeRepozitorij;
import si.turnirko.repozitoriji.TekmaRepozitorij;
import si.turnirko.repozitoriji.TekmaSrecanjaRepozitorij;

@Service
public class StatistikaStoritev {

    private final IgralecRepozitorij igralecRepozitorij;
    private final RatingStanjeRepozitorij ratingStanjeRepozitorij;
    private final TekmaRepozitorij tekmaRepozitorij;
    private final TekmaSrecanjaRepozitorij tekmaSrecanjaRepozitorij;
    private final SpremembeEloStoritev spremembeEloStoritev;

    public StatistikaStoritev(IgralecRepozitorij igralecRepozitorij,
                              RatingStanjeRepozitorij ratingStanjeRepozitorij,
                              TekmaRepozitorij tekmaRepozitorij,
                              TekmaSrecanjaRepozitorij tekmaSrecanjaRepozitorij,
                              SpremembeEloStoritev spremembeEloStoritev) {
        this.igralecRepozitorij = igralecRepozitorij;
        this.ratingStanjeRepozitorij = ratingStanjeRepozitorij;
        this.tekmaRepozitorij = tekmaRepozitorij;
        this.tekmaSrecanjaRepozitorij = tekmaSrecanjaRepozitorij;
        this.spremembeEloStoritev = spremembeEloStoritev;
    }

    /* Vmesni sestevek tekem enega igralca. */
    private static final class Stat {
        int odigrane, zmage, porazi;
    }

    /* Globalna lestvica aktivnih igralcev: najprej po ratingu (padajoce),
       potem po zmagah, potem abecedno. Igralci brez ratinga (se niso igrali)
       so na dnu. */
    @Transactional(readOnly = true)
    public List<LestvicaIgralcaDto> globalnaLestvica() {
        List<Igralec> igralci = igralecRepozitorij.najdiAktivne();
        Map<Long, Integer> ratingi = ratingiPoIgralcu(
                igralci.stream().map(Igralec::getId).toList());
        Map<Long, Stat> stat = statistikaTekem();

        List<LestvicaIgralcaDto> lestvica = new ArrayList<>();
        for (Igralec igralec : igralci) {
            Stat s = stat.getOrDefault(igralec.getId(), new Stat());
            lestvica.add(new LestvicaIgralcaDto(
                    igralec.getId(),
                    igralec.polnoIme(),
                    igralec.getKlub() != null ? igralec.getKlub().getIme() : null,
                    ratingi.get(igralec.getId()),
                    s.odigrane, s.zmage, s.porazi));
        }

        lestvica.sort(Comparator
                .comparing((LestvicaIgralcaDto v) -> v.rating() == null ? Integer.MIN_VALUE : v.rating())
                .reversed()
                .thenComparing(Comparator.comparingInt(LestvicaIgralcaDto::zmage).reversed())
                .thenComparing(LestvicaIgralcaDto::polnoIme));
        return lestvica;
    }

    /* Ena vrstica zgodovine dvoboja s kljucem za urejanje. Turnirska tekma nima
       svojega datuma, zato vzamemo zacetek turnirja, ligaska pa cas, ko je bilo
       srecanje odigrano; kjer datuma ni, gre vrstica na konec. */
    private record Vrstica(DvobojDto.Tekma tekma, LocalDate kdaj) {}

    /* Zgodovina vseh medsebojnih tekem dveh igralcev (izidi z vidika prvega) -
       turnirskih in ligaskih posamicnih skupaj. */
    @Transactional(readOnly = true)
    public DvobojDto dvoboj(Long idPrvega, Long idDrugega) {
        if (idPrvega.equals(idDrugega)) {
            throw new NeveljavenVnosIzjema("Za dvoboj izberi dva razlicna igralca.");
        }
        Igralec prvi = najdiIgralca(idPrvega);
        Igralec drugi = najdiIgralca(idDrugega);
        Map<Long, Integer> ratingi = ratingiPoIgralcu(List.of(idPrvega, idDrugega));

        int zmagePrvega = 0;
        int zmageDrugega = 0;
        int niziPrvega = 0;
        int niziDrugega = 0;
        List<Vrstica> vrstice = new ArrayList<>();

        List<Tekma> medsebojne = tekmaRepozitorij.najdiDvoboje(idPrvega, idDrugega);
        Map<Long, Map<Long, SpremembeEloStoritev.ObTekmi>> spremembe = spremembeEloStoritev.zaTekme(
                medsebojne.stream().map(Tekma::getId).toList());

        for (Tekma t : medsebojne) {
            // izide preslikamo tako, da je "prvi" vedno izbrani prvi igralec
            boolean prviJeStran1 = t.getPrijava1().getIgralec().getId().equals(idPrvega);
            int niziPrvi = prviJeStran1 ? t.getDobljeniNizi1() : t.getDobljeniNizi2();
            int niziDrugi = prviJeStran1 ? t.getDobljeniNizi2() : t.getDobljeniNizi1();
            boolean zmagalPrvi = t.getZmagovalec() != null
                    && t.getZmagovalec().getIgralec().getId().equals(idPrvega);

            niziPrvega += niziPrvi;
            niziDrugega += niziDrugi;
            if (zmagalPrvi) {
                zmagePrvega++;
            } else {
                zmageDrugega++;
            }
            Map<Long, SpremembeEloStoritev.ObTekmi> poIgralcu = spremembe.getOrDefault(t.getId(), Map.of());
            vrstice.add(new Vrstica(new DvobojDto.Tekma(
                    t.getId(), false,
                    t.getDogodek().getTurnir().getIme(),
                    t.getDogodek().getIme(),
                    niziPrvi, niziDrugi, zmagalPrvi, t.getIzidTip(),
                    spremembaOrNull(poIgralcu.get(idPrvega)), spremembaOrNull(poIgralcu.get(idDrugega))),
                    t.getDogodek().getTurnir().getDatumZacetka()));
        }

        List<TekmaSrecanja> ligaske = tekmaSrecanjaRepozitorij.najdiDvoboje(idPrvega, idDrugega);
        Map<Long, Map<Long, Integer>> ligaskeSpremembe = spremembeEloStoritev.zaTekmeSrecanja(
                ligaske.stream().map(TekmaSrecanja::getId).toList());

        for (TekmaSrecanja t : ligaske) {
            boolean prviJeDomaci = t.getIgralecDomaci().getId().equals(idPrvega);
            int niziPrvi = prviJeDomaci ? t.getDobljeniNiziDomaci() : t.getDobljeniNiziGost();
            int niziDrugi = prviJeDomaci ? t.getDobljeniNiziGost() : t.getDobljeniNiziDomaci();
            boolean zmagalPrvi = (t.getZmagovalecStran() == StranEkipe.DOMACI) == prviJeDomaci;

            niziPrvega += niziPrvi;
            niziDrugega += niziDrugi;
            if (zmagalPrvi) {
                zmagePrvega++;
            } else {
                zmageDrugega++;
            }
            Srecanje s = t.getSrecanje();
            Map<Long, Integer> poIgralcu = ligaskeSpremembe.getOrDefault(t.getId(), Map.of());
            vrstice.add(new Vrstica(new DvobojDto.Tekma(
                    t.getId(), true,
                    s.getLiga().getIme(),
                    s.getKolo() + ". kolo · " + s.getEkipaDomaci().prikazanoIme()
                            + " – " + s.getEkipaGost().prikazanoIme(),
                    niziPrvi, niziDrugi, zmagalPrvi, t.getIzidTip(),
                    poIgralcu.get(idPrvega), poIgralcu.get(idDrugega)),
                    s.getOdigranOb() != null ? s.getOdigranOb().toLocalDate() : null));
        }

        // stabilno urejanje: najnovejse prve, vrstice brez datuma na konec
        vrstice.sort(Comparator.comparing(Vrstica::kdaj,
                Comparator.nullsLast(Comparator.reverseOrder())));
        List<DvobojDto.Tekma> tekme = vrstice.stream().map(Vrstica::tekma).toList();

        return new DvobojDto(
                igralecPovzetek(prvi, ratingi.get(idPrvega)),
                igralecPovzetek(drugi, ratingi.get(idDrugega)),
                tekme.size(), zmagePrvega, zmageDrugega, niziPrvega, niziDrugega, tekme);
    }

    /* Zadnje odigrane tekme cez vse dogodke (najnovejse prve) s spremembo
       klubskega ELO obeh igralcev - za "Zadnji rezultati" na domaci strani. */
    @Transactional(readOnly = true)
    public List<ZadnjaTekmaDto> zadnjeTekme(int koliko) {
        List<Tekma> tekme = tekmaRepozitorij.najdiZadnje(PageRequest.of(0, koliko));
        Map<Long, Map<Long, SpremembeEloStoritev.ObTekmi>> spremembe = spremembeEloStoritev.zaTekme(
                tekme.stream().map(Tekma::getId).toList());

        List<ZadnjaTekmaDto> rezultat = new ArrayList<>();
        for (Tekma t : tekme) {
            boolean zmagalPrvi = t.getZmagovalec() != null
                    && t.getZmagovalec().getId().equals(t.getPrijava1().getId());
            Map<Long, SpremembeEloStoritev.ObTekmi> poIgralcu = spremembe.getOrDefault(t.getId(), Map.of());
            rezultat.add(new ZadnjaTekmaDto(
                    t.getId(),
                    t.getDogodek().getTurnir().getIme(),
                    t.getDogodek().getIme(),
                    t.getPrijava1().getIgralec().polnoIme(),
                    klubIme(t.getPrijava1()),
                    t.getPrijava2().getIgralec().polnoIme(),
                    klubIme(t.getPrijava2()),
                    t.getDobljeniNizi1(), t.getDobljeniNizi2(),
                    zmagalPrvi, t.getIzidTip(),
                    spremembaOrNull(poIgralcu.get(t.getPrijava1().getIgralec().getId())),
                    spremembaOrNull(poIgralcu.get(t.getPrijava2().getIgralec().getId()))));
        }
        return rezultat;
    }

    private static String klubIme(Prijava prijava) {
        return prijava.getKlubObPrijavi() != null ? prijava.getKlubObPrijavi().getIme() : null;
    }

    private static Integer spremembaOrNull(SpremembeEloStoritev.ObTekmi ob) {
        return ob == null ? null : ob.sprememba();
    }

    private DvobojDto.Igralec igralecPovzetek(Igralec igralec, Integer rating) {
        return new DvobojDto.Igralec(
                igralec.getId(),
                igralec.polnoIme(),
                igralec.getKlub() != null ? igralec.getKlub().getIme() : null,
                rating);
    }

    /* Sestej odigrane, zmage in poraze za vsakega igralca cez vsa tekmovanja -
       turnirske tekme in posamicne tekme ligaskih srecanj. */
    private Map<Long, Stat> statistikaTekem() {
        Map<Long, Stat> stat = new HashMap<>();
        for (Tekma t : tekmaRepozitorij.najdiVseOdigrane()) {
            if (t.getPrijava1() == null || t.getPrijava2() == null || t.getZmagovalec() == null) {
                continue;
            }
            pripisi(stat,
                    t.getPrijava1().getIgralec().getId(),
                    t.getPrijava2().getIgralec().getId(),
                    t.getZmagovalec().getId().equals(t.getPrijava1().getId()));
        }
        for (TekmaSrecanja t : tekmaSrecanjaRepozitorij.najdiVseOdigranePosamicne()) {
            pripisi(stat,
                    t.getIgralecDomaci().getId(),
                    t.getIgralecGost().getId(),
                    t.getZmagovalecStran() == StranEkipe.DOMACI);
        }
        return stat;
    }

    /* Ena odigrana tekma: obema igralcema pristej nastop, zmagovalcu zmago. */
    private static void pripisi(Map<Long, Stat> stat, Long idPrvega, Long idDrugega, boolean zmagalPrvi) {
        Stat s1 = stat.computeIfAbsent(idPrvega, k -> new Stat());
        Stat s2 = stat.computeIfAbsent(idDrugega, k -> new Stat());
        s1.odigrane++;
        s2.odigrane++;
        if (zmagalPrvi) {
            s1.zmage++;
            s2.porazi++;
        } else {
            s2.zmage++;
            s1.porazi++;
        }
    }

    private Map<Long, Integer> ratingiPoIgralcu(List<Long> idjiIgralcev) {
        Map<Long, Integer> ratingi = new HashMap<>();
        for (RatingStanje stanje : ratingStanjeRepozitorij
                .findByIgralecIdInAndSistem(idjiIgralcev, RatingStanje.SISTEM_KLUBSKI_ELO)) {
            ratingi.put(stanje.getIgralec().getId(), stanje.getVrednost());
        }
        return ratingi;
    }

    private Igralec najdiIgralca(Long id) {
        return igralecRepozitorij.najdiZVsem(id)
                .orElseThrow(() -> new NiNajdenoIzjema("Igralec z id " + id + " ne obstaja."));
    }
}
