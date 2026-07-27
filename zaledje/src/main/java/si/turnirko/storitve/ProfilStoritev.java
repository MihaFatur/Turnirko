/* Profil igralca: njegove dejanske statistike prek vseh tekmovanj.

   Vir sta oba dnevnika tekem (turnirske "tekma" in ligaske posamicne
   "tekma_srecanja") ter dnevnik ratinga. Vse tekme se najprej prevedejo v
   enoten zapis Nastop (izid z vidika lastnika profila), nato se iz njega
   racunajo vse razclenitve - tako je pravilo za turnir in ligo eno samo.

   Delitev na javno in zasebno: javno je tisto, kar izhaja iz ze javnih
   rezultatov (ELO, graf, seznam tekem, izkupicek), zasebne pa so analize
   (nasprotniki, forma, tocke, konteksti), ki jih vidi samo igralec sam ali
   administrator. */
package si.turnirko.storitve;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.ProfilDto;
import si.turnirko.dto.ProfilZasebnoDto;
import si.turnirko.dto.ProfilZasebnoDto.Delez;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.izjeme.PrepovedanoIzjema;
import si.turnirko.modeli.FazaTekme;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.IgralnaRoka;
import si.turnirko.modeli.IzidTekme;
import si.turnirko.modeli.RatingStanje;
import si.turnirko.modeli.RatingZgodovina;
import si.turnirko.modeli.Srecanje;
import si.turnirko.modeli.StranEkipe;
import si.turnirko.modeli.Tekma;
import si.turnirko.modeli.TekmaSrecanja;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.IgralecRepozitorij;
import si.turnirko.repozitoriji.NizRepozitorij;
import si.turnirko.repozitoriji.RatingStanjeRepozitorij;
import si.turnirko.repozitoriji.RatingZgodovinaRepozitorij;
import si.turnirko.repozitoriji.TekmaRepozitorij;
import si.turnirko.repozitoriji.TekmaSrecanjaRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

@Service
public class ProfilStoritev {

    /* Meja, od katere naprej velja nasprotnik za mocnejsega oz. sibkejsega. */
    private static final int MEJA_PODOBNIH = 50;

    private final IgralecRepozitorij igralecRepozitorij;
    private final TekmaRepozitorij tekmaRepozitorij;
    private final TekmaSrecanjaRepozitorij tekmaSrecanjaRepozitorij;
    private final RatingZgodovinaRepozitorij zgodovinaRepozitorij;
    private final RatingStanjeRepozitorij stanjeRepozitorij;
    private final NizRepozitorij nizRepozitorij;
    private final UporabnikRepozitorij uporabnikRepozitorij;

    public ProfilStoritev(IgralecRepozitorij igralecRepozitorij,
                          TekmaRepozitorij tekmaRepozitorij,
                          TekmaSrecanjaRepozitorij tekmaSrecanjaRepozitorij,
                          RatingZgodovinaRepozitorij zgodovinaRepozitorij,
                          RatingStanjeRepozitorij stanjeRepozitorij,
                          NizRepozitorij nizRepozitorij,
                          UporabnikRepozitorij uporabnikRepozitorij) {
        this.igralecRepozitorij = igralecRepozitorij;
        this.tekmaRepozitorij = tekmaRepozitorij;
        this.tekmaSrecanjaRepozitorij = tekmaSrecanjaRepozitorij;
        this.zgodovinaRepozitorij = zgodovinaRepozitorij;
        this.stanjeRepozitorij = stanjeRepozitorij;
        this.nizRepozitorij = nizRepozitorij;
        this.uporabnikRepozitorij = uporabnikRepozitorij;
    }

    /* Ena odigrana posamicna tekma, prevedena v pogled lastnika profila.
       "kdaj" je najboljsi razpolozljivi cas: trenutek obracuna ratinga, sicer
       datum tekmovanja - po njem so tekme urejene in iz njega tecejo nizi
       zmag in forma. */
    private record Nastop(
            Long idTekme, boolean ligaska, LocalDateTime kdaj, LocalDate datum,
            String tekmovanje, String del,
            Igralec nasprotnik,
            int niziZa, int niziProti, boolean zmaga, IzidTekme izidTip,
            Integer spremembaElo, Integer mojRatingPred, Integer ratingNasprotnikaPred,
            Boolean doma, String pozicija, FazaTekme faza, boolean jazPrvi) {}

    // ---------- Javni del ----------

    @Transactional(readOnly = true)
    public ProfilDto profil(Long idIgralec) {
        Igralec igralec = najdiIgralca(idIgralec);
        List<Nastop> nastopi = nastopi(idIgralec);
        Integer rating = stanjeRepozitorij
                .findByIgralecIdAndSistem(idIgralec, RatingStanje.SISTEM_KLUBSKI_ELO)
                .map(RatingStanje::getVrednost).orElse(null);

        int zmage = (int) nastopi.stream().filter(Nastop::zmaga).count();
        int porazi = nastopi.size() - zmage;
        int dobljeniNizi = nastopi.stream().mapToInt(Nastop::niziZa).sum();
        int prejetiNizi = nastopi.stream().mapToInt(Nastop::niziProti).sum();
        int ligaskih = (int) nastopi.stream().filter(Nastop::ligaska).count();

        return new ProfilDto(
                new ProfilDto.Glava(igralec.getId(), igralec.polnoIme(),
                        igralec.getKlub() != null ? igralec.getKlub().getIme() : null,
                        igralec.getIgralnaRoka(), rating),
                new ProfilDto.Pregled(nastopi.size(), zmage, porazi, odstotek(zmage, nastopi.size()),
                        dobljeniNizi, prejetiNizi, nastopi.size() - ligaskih, ligaskih),
                uvrstitev(igralec, rating),
                graf(idIgralec, nastopi),
                nastopi.stream().map(ProfilStoritev::vTekmoProfila).toList());
    }

    // ---------- Zasebni del ----------

    /* Zasebne analize; dostop ima samo igralec sam ali administrator.
       "prijavnoIme" je ime prijavljenega uporabnika iz varnostnega konteksta. */
    @Transactional(readOnly = true)
    public ProfilZasebnoDto zasebno(Long idIgralec, String prijavnoIme) {
        preveriLastnistvo(idIgralec, prijavnoIme);
        najdiIgralca(idIgralec);
        List<Nastop> nastopi = nastopi(idIgralec);
        return new ProfilZasebnoDto(
                nasprotniki(nastopi),
                niziInTocke(nastopi),
                forma(idIgralec, nastopi),
                poTekmovanjih(idIgralec, nastopi));
    }

    /* Administrator sme vse; igralec samo svoj profil. */
    private void preveriLastnistvo(Long idIgralec, String prijavnoIme) {
        Uporabnik u = uporabnikRepozitorij.najdiZVsem(prijavnoIme)
                .orElseThrow(() -> new PrepovedanoIzjema("Prijavljeni uporabnik ne obstaja."));
        if (u.getVloga() == Vloga.ADMIN) {
            return;
        }
        if (!u.jePotrjenIgralec()) {
            throw new PrepovedanoIzjema(
                    "Racun se ni potrjen, zato zasebna statistika ni na voljo.");
        }
        if (!u.getIgralec().getId().equals(idIgralec)) {
            throw new PrepovedanoIzjema("Zasebno statistiko lahko vidi samo igralec sam.");
        }
    }

    // ---------- Zbiranje nastopov ----------

    /* Vse odigrane posamicne tekme igralca iz obeh virov, najnovejse prve. */
    private List<Nastop> nastopi(Long idIgralec) {
        List<Tekma> turnirske = tekmaRepozitorij.najdiZaIgralca(idIgralec);
        List<TekmaSrecanja> ligaske = tekmaSrecanjaRepozitorij.najdiPosamicneZaIgralca(idIgralec);

        Map<Long, Map<Long, Integer>> ratingPredT =
                ratingiPredZa(turnirske.stream().map(Tekma::getId).toList(), false);
        Map<Long, Map<Long, Integer>> ratingPredL =
                ratingiPredZa(ligaske.stream().map(TekmaSrecanja::getId).toList(), true);
        Map<Long, LocalDateTime> casT = new HashMap<>();
        Map<Long, LocalDateTime> casL = new HashMap<>();
        Map<Long, Integer> spremembaT = new HashMap<>();
        Map<Long, Integer> spremembaL = new HashMap<>();
        for (RatingZgodovina z : zgodovinaRepozitorij.najdiZaIgralca(idIgralec,
                RatingStanje.SISTEM_KLUBSKI_ELO)) {
            if (z.getTekma() != null) {
                casT.put(z.getTekma().getId(), z.getUstvarjenOb());
                spremembaT.put(z.getTekma().getId(), z.getSprememba());
            } else if (z.getTekmaSrecanja() != null) {
                casL.put(z.getTekmaSrecanja().getId(), z.getUstvarjenOb());
                spremembaL.put(z.getTekmaSrecanja().getId(), z.getSprememba());
            }
        }

        List<Nastop> nastopi = new ArrayList<>();
        for (Tekma t : turnirske) {
            boolean jazPrvi = t.getPrijava1().getIgralec().getId().equals(idIgralec);
            Igralec nasprotnik = (jazPrvi ? t.getPrijava2() : t.getPrijava1()).getIgralec();
            LocalDateTime kdaj = casT.getOrDefault(t.getId(),
                    casIz(t.getDogodek().getTurnir().getDatumZacetka()));
            Map<Long, Integer> ratingi = ratingPredT.getOrDefault(t.getId(), Map.of());
            nastopi.add(new Nastop(
                    t.getId(), false,
                    kdaj,
                    datumIz(t.getDogodek().getTurnir().getDatumZacetka(), kdaj),
                    t.getDogodek().getTurnir().getIme(), t.getDogodek().getIme(),
                    nasprotnik,
                    jazPrvi ? t.getDobljeniNizi1() : t.getDobljeniNizi2(),
                    jazPrvi ? t.getDobljeniNizi2() : t.getDobljeniNizi1(),
                    t.getZmagovalec() != null
                            && t.getZmagovalec().getIgralec().getId().equals(idIgralec),
                    t.getIzidTip(),
                    spremembaT.get(t.getId()),
                    ratingi.get(idIgralec), ratingi.get(nasprotnik.getId()),
                    null, null, t.getFaza(), jazPrvi));
        }
        for (TekmaSrecanja t : ligaske) {
            boolean jazDomaci = t.getIgralecDomaci().getId().equals(idIgralec);
            Igralec nasprotnik = jazDomaci ? t.getIgralecGost() : t.getIgralecDomaci();
            Srecanje s = t.getSrecanje();
            LocalDateTime kdaj = casL.getOrDefault(t.getId(), s.getOdigranOb());
            Map<Long, Integer> ratingi = ratingPredL.getOrDefault(t.getId(), Map.of());
            nastopi.add(new Nastop(
                    t.getId(), true,
                    kdaj,
                    datumIz(s.getOdigranOb() != null ? s.getOdigranOb().toLocalDate() : null, kdaj),
                    s.getLiga().getIme(),
                    s.getKolo() + ". kolo · " + s.getEkipaDomaci().prikazanoIme()
                            + " – " + s.getEkipaGost().prikazanoIme(),
                    nasprotnik,
                    jazDomaci ? t.getDobljeniNiziDomaci() : t.getDobljeniNiziGost(),
                    jazDomaci ? t.getDobljeniNiziGost() : t.getDobljeniNiziDomaci(),
                    (t.getZmagovalecStran() == StranEkipe.DOMACI) == jazDomaci,
                    t.getIzidTip(),
                    spremembaL.get(t.getId()),
                    ratingi.get(idIgralec), ratingi.get(nasprotnik.getId()),
                    jazDomaci, mojaPozicija(t.getOznaka(), jazDomaci), null, jazDomaci));
        }

        // najnovejse prve; tekme brez znanega casa na konec
        nastopi.sort(Comparator.comparing(Nastop::kdaj,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return nastopi;
    }

    private static LocalDateTime casIz(LocalDate datum) {
        return datum != null ? datum.atStartOfDay() : null;
    }

    /* Datum tekmovanja, sicer dan obracuna ratinga - da vrstica ni brez datuma,
       ko srecanje se ni zakljuceno oziroma turnir nima vpisanih datumov. */
    private static LocalDate datumIz(LocalDate datumTekmovanja, LocalDateTime kdaj) {
        if (datumTekmovanja != null) {
            return datumTekmovanja;
        }
        return kdaj != null ? kdaj.toLocalDate() : null;
    }

    /* Iz oznake tekme ("A-X") vrne pozicijo lastnika profila. */
    private static String mojaPozicija(String oznaka, boolean domaci) {
        if (oznaka == null || !oznaka.contains("-")) {
            return null;
        }
        String[] deli = oznaka.split("-", 2);
        return domaci ? deli[0] : deli[1];
    }

    /* Prazen seznam id-jev ne sme v poizvedbo "IN (...)", zato ga prestrezemo. */
    private Map<Long, Map<Long, Integer>> ratingiPredZa(List<Long> idjiTekem, boolean ligaske) {
        if (idjiTekem.isEmpty()) {
            return Map.of();
        }
        return ratingiPred(ligaske
                ? zgodovinaRepozitorij.ratingiPredLigaskimi(idjiTekem, RatingStanje.SISTEM_KLUBSKI_ELO)
                : zgodovinaRepozitorij.ratingiPredTurnirskimi(idjiTekem, RatingStanje.SISTEM_KLUBSKI_ELO));
    }

    private static Map<Long, Map<Long, Integer>> ratingiPred(List<Object[]> vrstice) {
        Map<Long, Map<Long, Integer>> po = new HashMap<>();
        for (Object[] v : vrstice) {
            po.computeIfAbsent(((Number) v[0]).longValue(), k -> new HashMap<>())
                    .put(((Number) v[1]).longValue(), ((Number) v[2]).intValue());
        }
        return po;
    }

    // ---------- Javni izracuni ----------

    private ProfilDto.Uvrstitev uvrstitev(Igralec igralec, Integer rating) {
        List<Object[]> vsi = stanjeRepozitorij.vsiRatingi(RatingStanje.SISTEM_KLUBSKI_ELO);
        int skupaj = vsi.size();
        if (rating == null || skupaj == 0) {
            return new ProfilDto.Uvrstitev(null, skupaj, null, null);
        }
        // mesto = koliko igralcev ima strogo visji rating, plus ena
        int boljsih = (int) vsi.stream().filter(v -> ((Number) v[2]).intValue() > rating).count();
        int mesto = boljsih + 1;
        int percentil = skupaj <= 1 ? 100 : Math.round((skupaj - mesto) * 100f / (skupaj - 1));

        Integer klubskoPovprecje = null;
        if (igralec.getKlub() != null) {
            Long idKluba = igralec.getKlub().getId();
            List<Integer> klubski = vsi.stream()
                    .filter(v -> v[1] != null && ((Number) v[1]).longValue() == idKluba)
                    .map(v -> ((Number) v[2]).intValue())
                    .toList();
            if (!klubski.isEmpty()) {
                klubskoPovprecje = (int) Math.round(
                        klubski.stream().mapToInt(Integer::intValue).average().orElse(0));
            }
        }
        return new ProfilDto.Uvrstitev(mesto, skupaj, percentil, klubskoPovprecje);
    }

    /* Graf napredka: dnevnik ratinga od najstarejsega, obogaten z imenom
       nasprotnika, da je ob tocki jasno, katera tekma jo je povzrocila. */
    private List<ProfilDto.TockaGrafa> graf(Long idIgralec, List<Nastop> nastopi) {
        Map<String, Nastop> poKljucu = new HashMap<>();
        for (Nastop n : nastopi) {
            poKljucu.put(kljuc(n.idTekme(), n.ligaska()), n);
        }
        List<ProfilDto.TockaGrafa> tocke = new ArrayList<>();
        for (RatingZgodovina z : zgodovinaRepozitorij.najdiZaIgralca(idIgralec,
                RatingStanje.SISTEM_KLUBSKI_ELO)) {
            boolean ligaska = z.getTekmaSrecanja() != null;
            Long idTekme = ligaska ? z.getTekmaSrecanja().getId()
                    : (z.getTekma() != null ? z.getTekma().getId() : null);
            Nastop n = idTekme != null ? poKljucu.get(kljuc(idTekme, ligaska)) : null;
            tocke.add(new ProfilDto.TockaGrafa(
                    z.getUstvarjenOb(), z.getNovaVrednost(), z.getSprememba(),
                    idTekme, ligaska,
                    n != null ? n.nasprotnik().polnoIme() : null));
        }
        return tocke;
    }

    private static String kljuc(Long idTekme, boolean ligaska) {
        return (ligaska ? "L" : "T") + idTekme;
    }

    private static ProfilDto.TekmaProfila vTekmoProfila(Nastop n) {
        return new ProfilDto.TekmaProfila(
                n.idTekme(), n.ligaska(), n.datum(), n.tekmovanje(), n.del(),
                n.nasprotnik().getId(), n.nasprotnik().polnoIme(),
                n.nasprotnik().getKlub() != null ? n.nasprotnik().getKlub().getIme() : null,
                n.niziZa(), n.niziProti(), n.zmaga(), n.izidTip(), n.spremembaElo());
    }

    // ---------- Zasebni izracuni ----------

    private ProfilZasebnoDto.Nasprotniki nasprotniki(List<Nastop> nastopi) {
        int[] desna = new int[2];
        int[] leva = new int[2];
        int[] neznana = new int[2];
        int[] mocnejsi = new int[2];
        int[] podobni = new int[2];
        int[] sibkejsi = new int[2];
        int zZnanimRatingom = 0;

        Map<Long, int[]> poNasprotniku = new HashMap<>();
        Map<Long, Igralec> nasprotnikiPoId = new HashMap<>();
        Map<String, int[]> poKlubih = new LinkedHashMap<>();
        Nastop najboljsaZmaga = null;

        for (Nastop n : nastopi) {
            int i = n.zmaga() ? 0 : 1;
            IgralnaRoka roka = n.nasprotnik().getIgralnaRoka();
            if (roka == IgralnaRoka.DESNA) {
                desna[i]++;
            } else if (roka == IgralnaRoka.LEVA) {
                leva[i]++;
            } else {
                neznana[i]++;
            }

            if (n.mojRatingPred() != null && n.ratingNasprotnikaPred() != null) {
                zZnanimRatingom++;
                int razlika = n.ratingNasprotnikaPred() - n.mojRatingPred();
                if (razlika > MEJA_PODOBNIH) {
                    mocnejsi[i]++;
                } else if (razlika < -MEJA_PODOBNIH) {
                    sibkejsi[i]++;
                } else {
                    podobni[i]++;
                }
                if (n.zmaga() && (najboljsaZmaga == null
                        || n.ratingNasprotnikaPred() > najboljsaZmaga.ratingNasprotnikaPred())) {
                    najboljsaZmaga = n;
                }
            }

            nasprotnikiPoId.put(n.nasprotnik().getId(), n.nasprotnik());
            poNasprotniku.computeIfAbsent(n.nasprotnik().getId(), k -> new int[2])[i]++;
            String klub = n.nasprotnik().getKlub() != null
                    ? n.nasprotnik().getKlub().getIme() : "brez kluba";
            poKlubih.computeIfAbsent(klub, k -> new int[2])[i]++;
        }

        ProfilZasebnoDto.Nasprotnik zmaga = null;
        if (najboljsaZmaga != null) {
            int[] izid = poNasprotniku.get(najboljsaZmaga.nasprotnik().getId());
            zmaga = new ProfilZasebnoDto.Nasprotnik(
                    najboljsaZmaga.nasprotnik().getId(),
                    najboljsaZmaga.nasprotnik().polnoIme(),
                    klubIme(najboljsaZmaga.nasprotnik()),
                    najboljsaZmaga.ratingNasprotnikaPred(), izid[0], izid[1]);
        }

        return new ProfilZasebnoDto.Nasprotniki(
                Delez.iz("proti desničarjem", desna[0], desna[1]),
                Delez.iz("proti levičarjem", leva[0], leva[1]),
                Delez.iz("roka ni znana", neznana[0], neznana[1]),
                Delez.iz("proti močnejšim", mocnejsi[0], mocnejsi[1]),
                Delez.iz("proti enakovrednim", podobni[0], podobni[1]),
                Delez.iz("proti šibkejšim", sibkejsi[0], sibkejsi[1]),
                zZnanimRatingom,
                zmaga,
                // nemesis: najvec porazov; najpogostejsi: najvec medsebojnih tekem
                izbrani(poNasprotniku, nasprotnikiPoId, izid -> izid[1]),
                izbrani(poNasprotniku, nasprotnikiPoId, izid -> izid[0] + izid[1]),
                poKlubih.entrySet().stream()
                        .map(e -> Delez.iz(e.getKey(), e.getValue()[0], e.getValue()[1]))
                        .sorted(Comparator.comparingInt(Delez::odigrane).reversed())
                        .toList());
    }

    /* Nasprotnik z najvisjo vrednostjo merila (npr. najvec porazov); ce je
       merilo pri vseh nic, ni kaj izpostaviti. Izid je [zmage, porazi]. */
    private static ProfilZasebnoDto.Nasprotnik izbrani(Map<Long, int[]> poNasprotniku,
                                                       Map<Long, Igralec> igralci,
                                                       ToIntFunction<int[]> merilo) {
        return poNasprotniku.entrySet().stream()
                .filter(e -> merilo.applyAsInt(e.getValue()) > 0)
                .max(Comparator.comparingInt(e -> merilo.applyAsInt(e.getValue())))
                .map(e -> {
                    Igralec i = igralci.get(e.getKey());
                    return new ProfilZasebnoDto.Nasprotnik(i.getId(), i.polnoIme(), klubIme(i),
                            null, e.getValue()[0], e.getValue()[1]);
                })
                .orElse(null);
    }

    private static String klubIme(Igralec i) {
        return i.getKlub() != null ? i.getKlub().getIme() : null;
    }

    private ProfilZasebnoDto.NiziInTocke niziInTocke(List<Nastop> nastopi) {
        int dobljeni = nastopi.stream().mapToInt(Nastop::niziZa).sum();
        int prejeti = nastopi.stream().mapToInt(Nastop::niziProti).sum();

        Map<String, int[]> razmerja = new LinkedHashMap<>();
        int[] odlocilni = new int[2];
        for (Nastop n : nastopi) {
            razmerja.computeIfAbsent(n.niziZa() + ":" + n.niziProti(), k -> new int[2])
                    [n.zmaga() ? 0 : 1]++;
            // odlocilni niz: porazenec je zaostal za natanko en niz
            if (Math.abs(n.niziZa() - n.niziProti()) == 1) {
                odlocilni[n.zmaga() ? 0 : 1]++;
            }
        }

        return new ProfilZasebnoDto.NiziInTocke(dobljeni, prejeti,
                razmerja.entrySet().stream()
                        .flatMap(e -> java.util.stream.Stream.of(
                                e.getValue()[0] > 0
                                        ? new ProfilZasebnoDto.Razmerje(e.getKey(), e.getValue()[0], true) : null,
                                e.getValue()[1] > 0
                                        ? new ProfilZasebnoDto.Razmerje(e.getKey(), e.getValue()[1], false) : null))
                        .filter(java.util.Objects::nonNull)
                        .sorted(Comparator.comparingInt(ProfilZasebnoDto.Razmerje::stevilo).reversed())
                        .toList(),
                Delez.iz("odločilni niz", odlocilni[0], odlocilni[1]),
                tocke(nastopi));
    }

    /* Tocke po nizih obstajajo samo pri turnirskih tekmah. */
    private ProfilZasebnoDto.Tocke tocke(List<Nastop> nastopi) {
        Map<Long, Boolean> turnirske = new HashMap<>();
        for (Nastop n : nastopi) {
            if (!n.ligaska()) {
                turnirske.put(n.idTekme(), n.jazPrvi());
            }
        }
        if (turnirske.isEmpty()) {
            return new ProfilZasebnoDto.Tocke(0, 0, 0, 0, 0, 0);
        }
        int za = 0;
        int proti = 0;
        int nizov = 0;
        int najvec = 0;
        java.util.Set<Long> stekmami = new java.util.HashSet<>();
        for (Object[] v : nizRepozitorij.tockeZaTekme(turnirske.keySet())) {
            Long idTekme = ((Number) v[0]).longValue();
            Boolean jazPrvi = turnirske.get(idTekme);
            if (jazPrvi == null) {
                continue;
            }
            int moje = ((Number) (jazPrvi ? v[1] : v[2])).intValue();
            int njegove = ((Number) (jazPrvi ? v[2] : v[1])).intValue();
            za += moje;
            proti += njegove;
            nizov++;
            najvec = Math.max(najvec, moje);
            stekmami.add(idTekme);
        }
        int skupaj = za + proti;
        return new ProfilZasebnoDto.Tocke(stekmami.size(), za, proti,
                skupaj == 0 ? 0 : Math.round(za * 100f / skupaj),
                nizov == 0 ? 0 : Math.round(za * 10.0 / nizov) / 10.0,
                najvec);
    }

    private ProfilZasebnoDto.Forma forma(Long idIgralec, List<Nastop> nastopi) {
        List<Boolean> zadnjih10 = nastopi.stream().limit(10).map(Nastop::zmaga).toList();

        int trenutni = 0;
        boolean nizZmag = !zadnjih10.isEmpty() && zadnjih10.get(0);
        for (Nastop n : nastopi) {
            if (n.zmaga() != nizZmag) {
                break;
            }
            trenutni++;
        }

        // najdaljsi niz racunamo v casovnem zaporedju (od najstarejsega)
        int najdaljseZmage = 0;
        int najdaljsiPorazi = 0;
        int tekociZ = 0;
        int tekociP = 0;
        for (int i = nastopi.size() - 1; i >= 0; i--) {
            if (nastopi.get(i).zmaga()) {
                tekociZ++;
                tekociP = 0;
            } else {
                tekociP++;
                tekociZ = 0;
            }
            najdaljseZmage = Math.max(najdaljseZmage, tekociZ);
            najdaljsiPorazi = Math.max(najdaljsiPorazi, tekociP);
        }

        List<RatingZgodovina> dnevnik = zgodovinaRepozitorij.najdiZaIgralca(idIgralec,
                RatingStanje.SISTEM_KLUBSKI_ELO);
        LocalDateTime meja = LocalDateTime.now().minusDays(30);
        Integer sprememba30 = dnevnik.isEmpty() ? null : dnevnik.stream()
                .filter(z -> z.getUstvarjenOb() != null && z.getUstvarjenOb().isAfter(meja))
                .mapToInt(RatingZgodovina::getSprememba).sum();
        RatingZgodovina najvisji = dnevnik.stream()
                .max(Comparator.comparingInt(RatingZgodovina::getNovaVrednost)).orElse(null);

        return new ProfilZasebnoDto.Forma(zadnjih10, trenutni, nizZmag,
                najdaljseZmage, najdaljsiPorazi, sprememba30,
                najvisji != null ? najvisji.getNovaVrednost() : null,
                najvisji != null && najvisji.getUstvarjenOb() != null
                        ? najvisji.getUstvarjenOb().toLocalDate() : null);
    }

    private ProfilZasebnoDto.PoTekmovanjih poTekmovanjih(Long idIgralec, List<Nastop> nastopi) {
        int[] turnir = new int[2];
        int[] liga = new int[2];
        int[] doma = new int[2];
        int[] gosti = new int[2];
        Map<String, int[]> poPoziciji = new LinkedHashMap<>();
        Map<FazaTekme, int[]> poFazi = new LinkedHashMap<>();

        for (Nastop n : nastopi) {
            int i = n.zmaga() ? 0 : 1;
            if (n.ligaska()) {
                liga[i]++;
                if (Boolean.TRUE.equals(n.doma())) {
                    doma[i]++;
                } else if (Boolean.FALSE.equals(n.doma())) {
                    gosti[i]++;
                }
                if (n.pozicija() != null) {
                    poPoziciji.computeIfAbsent(n.pozicija(), k -> new int[2])[i]++;
                }
            } else {
                turnir[i]++;
                if (n.faza() != null) {
                    poFazi.computeIfAbsent(n.faza(), k -> new int[2])[i]++;
                }
            }
        }

        int[] dvojice = new int[2];
        for (TekmaSrecanja t : tekmaSrecanjaRepozitorij.najdiDvojiceZaIgralca(idIgralec)) {
            boolean jazDomaci = jeNaStrani(t, idIgralec, true);
            boolean zmaga = (t.getZmagovalecStran() == StranEkipe.DOMACI) == jazDomaci;
            dvojice[zmaga ? 0 : 1]++;
        }

        return new ProfilZasebnoDto.PoTekmovanjih(
                Delez.iz("turnirji", turnir[0], turnir[1]),
                Delez.iz("lige", liga[0], liga[1]),
                Delez.iz("doma", doma[0], doma[1]),
                Delez.iz("v gosteh", gosti[0], gosti[1]),
                poPoziciji.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(e -> Delez.iz("pozicija " + e.getKey(), e.getValue()[0], e.getValue()[1]))
                        .toList(),
                poFazi.entrySet().stream()
                        .map(e -> Delez.iz(oznakaFaze(e.getKey()), e.getValue()[0], e.getValue()[1]))
                        .toList(),
                Delez.iz("dvojice", dvojice[0], dvojice[1]));
    }

    private static boolean jeNaStrani(TekmaSrecanja t, Long idIgralec, boolean domaca) {
        Igralec prvi = domaca ? t.getIgralecDomaci() : t.getIgralecGost();
        Igralec drugi = domaca ? t.getIgralecDomaci2() : t.getIgralecGost2();
        return (prvi != null && prvi.getId().equals(idIgralec))
                || (drugi != null && drugi.getId().equals(idIgralec));
    }

    private static String oznakaFaze(FazaTekme faza) {
        return switch (faza) {
            case SKUPINA -> "skupine";
            case GLAVNI -> "izločilni del";
            case TOLAZILNI -> "tolažilni del";
        };
    }

    private static int odstotek(int del, int celota) {
        return celota == 0 ? 0 : Math.round(del * 100f / celota);
    }

    private Igralec najdiIgralca(Long id) {
        return igralecRepozitorij.najdiZVsem(id)
                .orElseThrow(() -> new NiNajdenoIzjema("Igralec z id " + id + " ne obstaja."));
    }
}
