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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.DvobojDto;
import si.turnirko.dto.LestvicaIgralcaDto;
import si.turnirko.dto.NakljucniParDto;
import si.turnirko.dto.ZadnjaTekmaDto;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.KategorijaIgralca;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.RatingStanje;
import si.turnirko.modeli.Srecanje;
import si.turnirko.modeli.StranEkipe;
import si.turnirko.modeli.Tekma;
import si.turnirko.modeli.TekmaSrecanja;
import si.turnirko.repozitoriji.IgralecRepozitorij;
import si.turnirko.repozitoriji.KaderEkipeRepozitorij;
import si.turnirko.repozitoriji.RatingStanjeRepozitorij;
import si.turnirko.repozitoriji.RatingZgodovinaRepozitorij;
import si.turnirko.repozitoriji.TekmaRepozitorij;
import si.turnirko.repozitoriji.TekmaSrecanjaRepozitorij;

@Service
public class StatistikaStoritev {

    /* Koliko tock ima crta gibanja ELO ob vrstici lestvice. Sedem je toliko,
       kolikor jih je pri sirini 140 px se mogoce lociti. */
    private static final int TOCK_CRTE = 7;

    /* Obdobje, ki ga crta pokriva, in obdobje, cez katero se meri premik
       mesta. Premik cez mesec je dovolj dolg, da ni sum posamezne tekme, in
       dovolj kratek, da se se bere kot "kaj se je zgodilo zadnjic". */
    private static final int MESECEV_CRTE = 12;
    private static final int DNI_PREMIKA = 30;

    /* Koliko igralcev poskusi zreb nakljucnega para, preden odneha. Drugega
       poskusa potrebuje samo v redkem primeru, ko so vsi nasprotniki
       izzrebanega igralca arhivirani. */
    private static final int POSKUSOV_ZREBA = 10;

    private final IgralecRepozitorij igralecRepozitorij;
    private final RatingStanjeRepozitorij ratingStanjeRepozitorij;
    private final RatingZgodovinaRepozitorij ratingZgodovinaRepozitorij;
    private final KaderEkipeRepozitorij kaderEkipeRepozitorij;
    private final TekmaRepozitorij tekmaRepozitorij;
    private final TekmaSrecanjaRepozitorij tekmaSrecanjaRepozitorij;
    private final SpremembeEloStoritev spremembeEloStoritev;

    /* Vir nakljucja je zamenljiv, da je zreb para v testu ponovljiv -
       isto kot pri zrebu tekmovanja (ZrebStoritev.nastaviNakljucje). */
    private Random nakljucje = new Random();

    public StatistikaStoritev(IgralecRepozitorij igralecRepozitorij,
                              RatingStanjeRepozitorij ratingStanjeRepozitorij,
                              RatingZgodovinaRepozitorij ratingZgodovinaRepozitorij,
                              KaderEkipeRepozitorij kaderEkipeRepozitorij,
                              TekmaRepozitorij tekmaRepozitorij,
                              TekmaSrecanjaRepozitorij tekmaSrecanjaRepozitorij,
                              SpremembeEloStoritev spremembeEloStoritev) {
        this.igralecRepozitorij = igralecRepozitorij;
        this.ratingStanjeRepozitorij = ratingStanjeRepozitorij;
        this.ratingZgodovinaRepozitorij = ratingZgodovinaRepozitorij;
        this.kaderEkipeRepozitorij = kaderEkipeRepozitorij;
        this.tekmaRepozitorij = tekmaRepozitorij;
        this.tekmaSrecanjaRepozitorij = tekmaSrecanjaRepozitorij;
        this.spremembeEloStoritev = spremembeEloStoritev;
    }

    void nastaviNakljucje(Random nakljucje) {
        this.nakljucje = nakljucje;
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
        Map<Long, List<Long>> lige = ligePoIgralcih();

        LocalDateTime zdaj = LocalDateTime.now();
        Poteki poteki = potekiEla(zdaj);
        /* Premik je razlika mest, zato potrebuje CELO lestvico izpred meseca -
           ne le ratingov igralcev, ki so danes na njej. */
        Map<Long, Integer> mestaPrej = mestaPoRatingu(poteki.predMesecem());

        LocalDate danes = zdaj.toLocalDate();
        List<LestvicaIgralcaDto> lestvica = new ArrayList<>();
        for (Igralec igralec : igralci) {
            Stat s = stat.getOrDefault(igralec.getId(), new Stat());
            Integer rating = ratingi.get(igralec.getId());
            /* Delta ratinga je razlika proti istemu stanju, iz katerega se
               racuna premik mesta - oba stolpca torej merita isto obdobje. */
            Integer ratingPrej = poteki.predMesecem().get(igralec.getId());
            Integer sprememba = (rating == null || ratingPrej == null) ? null : rating - ratingPrej;
            lestvica.add(new LestvicaIgralcaDto(
                    igralec.getId(),
                    igralec.getIme(),
                    igralec.getPriimek(),
                    igralec.polnoIme(),
                    igralec.getKlub() != null ? igralec.getKlub().getIme() : null,
                    igralec.getKlub() != null ? igralec.getKlub().getId() : null,
                    rating,
                    s.odigrane, s.zmage, s.porazi,
                    null,
                    sprememba,
                    igralec.getSpol(),
                    KategorijaIgralca.izpelji(igralec.getSpol(), igralec.getDatumRojstva(), danes),
                    poteki.crte().getOrDefault(igralec.getId(), List.of()),
                    lige.getOrDefault(igralec.getId(), List.of())));
        }

        lestvica.sort(primerjavaLestvice());

        /* Premik dopisemo sele zdaj, ko je znano danasnje mesto. */
        List<LestvicaIgralcaDto> zPremikom = new ArrayList<>(lestvica.size());
        for (int i = 0; i < lestvica.size(); i++) {
            LestvicaIgralcaDto v = lestvica.get(i);
            Integer prej = mestaPrej.get(v.idIgralca());
            Integer premik = (prej == null || v.rating() == null) ? null : prej - (i + 1);
            zPremikom.add(new LestvicaIgralcaDto(
                    v.idIgralca(), v.ime(), v.priimek(), v.polnoIme(), v.klub(), v.idKluba(),
                    v.rating(), v.odigrane(), v.zmage(), v.porazi(),
                    premik, v.spremembaRatinga(), v.spol(), v.kategorija(),
                    v.eloZgodovina(), v.idjiLig()));
        }
        return zPremikom;
    }

    /* Najprej po ratingu (padajoce), potem po zmagah, potem abecedno.
       Igralci brez ratinga (se niso igrali) so na dnu. */
    private static Comparator<LestvicaIgralcaDto> primerjavaLestvice() {
        return Comparator
                .comparing((LestvicaIgralcaDto v) -> v.rating() == null ? Integer.MIN_VALUE : v.rating())
                .reversed()
                .thenComparing(Comparator.comparingInt(LestvicaIgralcaDto::zmage).reversed())
                .thenComparing(LestvicaIgralcaDto::polnoIme);
    }

    /* Crte gibanja ELO in ratingi izpred meseca - oboje iz istega dnevnika,
       zato v enem prehodu. */
    private record Poteki(Map<Long, List<Integer>> crte, Map<Long, Integer> predMesecem) {}

    private Poteki potekiEla(LocalDateTime zdaj) {
        LocalDateTime zacetekOkna = zdaj.minusMonths(MESECEV_CRTE);
        LocalDateTime mejnikPremika = zdaj.minusDays(DNI_PREMIKA);

        /* Izhodisce: kdo je ob zacetku okna ze imel rating. Brez tega bi crta
           igralca, ki v zadnjem letu ni igral, ostala prazna. */
        Map<Long, Integer> izhodisca = vMapoVrednosti(
                ratingZgodovinaRepozitorij.stanjeOb(RatingStanje.SISTEM_KLUBSKI_ELO, zacetekOkna));

        Map<Long, List<Object[]>> poIgralcu = new HashMap<>();
        for (Object[] v : ratingZgodovinaRepozitorij.potekOd(RatingStanje.SISTEM_KLUBSKI_ELO, zacetekOkna)) {
            poIgralcu.computeIfAbsent(((Number) v[0]).longValue(), k -> new ArrayList<>()).add(v);
        }

        Map<Long, List<Integer>> crte = new HashMap<>();
        Map<Long, Integer> predMesecem = new HashMap<>(izhodisca);

        Set<Long> vsi = new HashSet<>(izhodisca.keySet());
        vsi.addAll(poIgralcu.keySet());

        for (Long idIgralca : vsi) {
            List<Object[]> zapisi = poIgralcu.getOrDefault(idIgralca, List.of());
            boolean imaIzhodisce = izhodisca.containsKey(idIgralca);

            /* Vrednosti v oknu: izhodisce (ce ga je) in vsaka zabelezena
               sprememba. Ena sama vrednost ni gibanje, ampak ravna crta, ki
               obljublja zgodovino, ki je ni. */
            List<Integer> vrednosti = new ArrayList<>();
            if (imaIzhodisce) {
                vrednosti.add(izhodisca.get(idIgralca));
            }
            for (Object[] zapis : zapisi) {
                vrednosti.add(((Number) zapis[2]).intValue());
            }

            /* Crta tece po TEKMAH in ne po koledarju. Razlog je v podatkih:
               ustvarjen_ob v dnevniku ratinga je cas VNOSA, ne cas tekme -
               klub cesto vnese celo kolo naenkrat. Casovno vzorcenje bi zato
               celo sezono stisnilo v zadnjo tocko in vsem narisalo ravno crto.
               Okno "zadnjih 12 mesecev" ostane: doloca, katere tekme so notri. */
            List<Integer> crta = new ArrayList<>(TOCK_CRTE);
            if (vrednosti.size() >= 2) {
                if (vrednosti.size() <= TOCK_CRTE) {
                    crta.addAll(vrednosti);
                } else {
                    for (int i = 0; i < TOCK_CRTE; i++) {
                        int kje = (int) Math.round(
                                (double) (vrednosti.size() - 1) * i / (TOCK_CRTE - 1));
                        crta.add(vrednosti.get(kje));
                    }
                }
            }

            /* Rating izpred meseca beremo iz dnevnika neposredno - vzorcenje
               crte je pregrobo (tocke so vsak drugi mesec). */
            Integer prej = izhodisca.get(idIgralca);
            for (Object[] zapis : zapisi) {
                if (((LocalDateTime) zapis[1]).isAfter(mejnikPremika)) {
                    break;
                }
                prej = ((Number) zapis[2]).intValue();
            }
            if (prej != null) {
                predMesecem.put(idIgralca, prej);
            }
            if (!crta.isEmpty()) {
                crte.put(idIgralca, crta);
            }
        }
        return new Poteki(crte, predMesecem);
    }

    /* Iz ratingov izpred meseca zgradi lestvico in vrne mesto vsakega igralca. */
    private static Map<Long, Integer> mestaPoRatingu(Map<Long, Integer> ratingi) {
        List<Map.Entry<Long, Integer>> urejeni = new ArrayList<>(ratingi.entrySet());
        urejeni.sort(Map.Entry.<Long, Integer>comparingByValue().reversed()
                .thenComparing(Map.Entry.comparingByKey()));
        Map<Long, Integer> mesta = new HashMap<>();
        for (int i = 0; i < urejeni.size(); i++) {
            mesta.put(urejeni.get(i).getKey(), i + 1);
        }
        return mesta;
    }

    private static Map<Long, Integer> vMapoVrednosti(List<Object[]> vrstice) {
        Map<Long, Integer> mapa = new HashMap<>();
        for (Object[] v : vrstice) {
            mapa.put(((Number) v[0]).longValue(), ((Number) v[1]).intValue());
        }
        return mapa;
    }

    private Map<Long, List<Long>> ligePoIgralcih() {
        Map<Long, List<Long>> lige = new HashMap<>();
        for (Object[] v : kaderEkipeRepozitorij.ligePoIgralcih()) {
            lige.computeIfAbsent(((Number) v[0]).longValue(), k -> new ArrayList<>())
                    .add(((Number) v[1]).longValue());
        }
        return lige;
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
            LocalDate kdaj = t.getDogodek().getTurnir().getDatumZacetka();
            vrstice.add(new Vrstica(new DvobojDto.Tekma(
                    t.getId(), false,
                    t.getDogodek().getTurnir().getIme(),
                    t.getDogodek().getIme(),
                    kdaj,
                    niziPrvi, niziDrugi, zmagalPrvi, t.getIzidTip(),
                    spremembaOrNull(poIgralcu.get(idPrvega)), spremembaOrNull(poIgralcu.get(idDrugega))),
                    kdaj));
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
            LocalDate kdaj = s.getOdigranOb() != null ? s.getOdigranOb().toLocalDate() : null;
            vrstice.add(new Vrstica(new DvobojDto.Tekma(
                    t.getId(), true,
                    s.getLiga().getIme(),
                    s.getKolo() + ". kolo · " + s.getEkipaDomaci().prikazanoIme()
                            + " – " + s.getEkipaGost().prikazanoIme(),
                    kdaj,
                    niziPrvi, niziDrugi, zmagalPrvi, t.getIzidTip(),
                    poIgralcu.get(idPrvega), poIgralcu.get(idDrugega)),
                    kdaj));
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

    /* Nakljucni par za semafor "1 na 1" na domaci strani.

       Merilo je, da sta se igralca ZE srecala: izid 0 : 0 o njiju ne pove
       nicesar, pripomocek za raziskovanje pa mora vsakic postreci z zgodbo.
       Zato se najprej izzreba igralec z vsaj eno odigrano tekmo, zatem pa
       nasprotnik IZMED tistih, s katerimi je ta ze igral.

       Zreb tece po IGRALCIH in ne po tekmah: enakomerno izbrana tekma bi
       vlekla iste najbolj dejavne igralce, ker teh je v seznamu tekem
       najvec. Steje oboje - turnirske in posamicne ligaske tekme -, tako
       kot medsebojni izid sam.

       Kadar medsebojnih tekem se ni (nova namestitev), zreb vrne kar dva
       aktivna igralca: prazen semafor je manj skodljiv od napake. */
    @Transactional(readOnly = true)
    public NakljucniParDto nakljucniPar() {
        Set<Long> zTekmo = new LinkedHashSet<>(tekmaRepozitorij.idjiZOdigranoTekmo());
        zTekmo.addAll(tekmaSrecanjaRepozitorij.idjiZOdigranoTekmo());

        List<Long> kandidati = new ArrayList<>(zTekmo);
        Collections.shuffle(kandidati, nakljucje);

        for (int i = 0; i < kandidati.size() && i < POSKUSOV_ZREBA; i++) {
            Long id = kandidati.get(i);
            Set<Long> nasprotniki = new LinkedHashSet<>(tekmaRepozitorij.nasprotniki(id));
            nasprotniki.addAll(tekmaSrecanjaRepozitorij.nasprotniki(id));
            if (!nasprotniki.isEmpty()) {
                List<Long> izbira = new ArrayList<>(nasprotniki);
                return new NakljucniParDto(id, izbira.get(nakljucje.nextInt(izbira.size())));
            }
        }
        return parBrezMedsebojnih();
    }

    /* Zasilni izhod zreba: dva razlicna aktivna igralca, ki morda nista nikoli
       igrala. Doleti novo namestitev in bazo, kjer so vsi nasprotniki
       izzrebanih igralcev arhivirani. */
    private NakljucniParDto parBrezMedsebojnih() {
        List<Igralec> aktivni = igralecRepozitorij.najdiAktivne();
        if (aktivni.size() < 2) {
            throw new NiNajdenoIzjema("Za nakljucni par sta potrebna vsaj dva igralca.");
        }
        int prvi = nakljucje.nextInt(aktivni.size());
        // drugi se zreba iz seznama brez prvega, da zreb ne vrne istega igralca dvakrat
        int drugi = nakljucje.nextInt(aktivni.size() - 1);
        if (drugi >= prvi) {
            drugi++;
        }
        return new NakljucniParDto(aktivni.get(prvi).getId(), aktivni.get(drugi).getId());
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
                igralec.getIme(),
                igralec.getPriimek(),
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
