/* Zavihek "Zanimivosti" enega tekmovanja (turnir ali liga).

   Turnir in liga hranita tekme v dveh razlicnih tabelah (tekma /
   tekma_srecanja), zgodbe pa so iste: kdo je najvec pridobil, kdo je koga
   presenetil, kje je bilo najbolj tesno. Zato obe strani najprej prevedemo v
   isto vmesno obliko (Nastop) in vse skupne postavke racunamo samo enkrat.
   Turnirske in ligaske posebnosti (prvi naslov; srecanje na noz, gostje,
   nosilci ekip) so locene metode, ki berejo svoj vir naravnost.

   Pravila, ki jih ne razbij:

   - DVOJICE ne vstopajo v nobeno vrstico o posamezniku. Izida para ni mogoce
     pripisati posamezniku (isto pravilo kot pri ELO in ligaskih dvojicah),
     zato jih steje samo pas "V stevilkah" in svoja vrstica najuspesnejse
     dvojice. Izjema je "delaven", kjer stejemo NASTOPE (kolikokrat je igralec
     sedel za mizo) in ne izkupicka - a tudi tam so dvojice svoj stevec.

   - Prazna postavka je odsotna postavka. Uvozena zgodovina brez obracuna ELO,
     liga brez vpisanih tock po nizih in turnir v prvi uri nimajo istih
     podatkov; vrstica z ničlo bi trdila, da se nekaj ni zgodilo, ceprav
     podatka preprosto ni.

   - Poizvedbe so po TEKMOVANJU in ne po seznamu id-jev tekem: velik turnir
     ima nekaj sto tekem, sqlite pa ima omejitev stevila vezanih parametrov.
     Vseh poizvedb je pet (turnir) oziroma stiri (liga), nobena ni na tekmo. */
package si.turnirko.storitve;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.StatistikaTekmovanjaDto;
import si.turnirko.dto.StatistikaTekmovanjaDto.Delavec;
import si.turnirko.dto.StatistikaTekmovanjaDto.Dvojica;
import si.turnirko.dto.StatistikaTekmovanjaDto.Gostovanje;
import si.turnirko.dto.StatistikaTekmovanjaDto.KlubVrstica;
import si.turnirko.dto.StatistikaTekmovanjaDto.NaNoz;
import si.turnirko.dto.StatistikaTekmovanjaDto.NajdaljsaTekma;
import si.turnirko.dto.StatistikaTekmovanjaDto.NajdaljsiNiz;
import si.turnirko.dto.StatistikaTekmovanjaDto.Nosilec;
import si.turnirko.dto.StatistikaTekmovanjaDto.Obrat;
import si.turnirko.dto.StatistikaTekmovanjaDto.Oseba;
import si.turnirko.dto.StatistikaTekmovanjaDto.Presenecenje;
import si.turnirko.dto.StatistikaTekmovanjaDto.PrviNaslov;
import si.turnirko.dto.StatistikaTekmovanjaDto.Stevilke;
import si.turnirko.dto.StatistikaTekmovanjaDto.Vzpon;
import si.turnirko.dto.StatistikaTekmovanjaDto.Zid;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.modeli.Disciplina;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Klub;
import si.turnirko.modeli.Liga;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.RatingStanje;
import si.turnirko.modeli.SistemTekmovanja;
import si.turnirko.modeli.Srecanje;
import si.turnirko.modeli.StatusTekmovanja;
import si.turnirko.modeli.StranEkipe;
import si.turnirko.modeli.Tekma;
import si.turnirko.modeli.TekmaSrecanja;
import si.turnirko.modeli.TipTekmeSrecanja;
import si.turnirko.modeli.Turnir;
import si.turnirko.repozitoriji.LigaRepozitorij;
import si.turnirko.repozitoriji.NizRepozitorij;
import si.turnirko.repozitoriji.NizSrecanjaRepozitorij;
import si.turnirko.repozitoriji.PrijavaRepozitorij;
import si.turnirko.repozitoriji.RatingZgodovinaRepozitorij;
import si.turnirko.repozitoriji.TekmaRepozitorij;
import si.turnirko.repozitoriji.TekmaSrecanjaRepozitorij;
import si.turnirko.repozitoriji.TurnirRepozitorij;

@Service
public class StatistikaTekmovanjaStoritev {

    /* Pod tem stevilom odigranih tekem zavihka ni. Pri sestih tekmah je
       "najbolj delaven igralec" nakljucje in ne ugotovitev, "klub turnirja" pa
       tisti, ki je pripeljal dva bratranca. */
    static final int PRAG_TEKEM = 10;

    /* Najmanj tekem, da igralec sploh pride v vrstico "zid" - brez praga bi jo
       vzel vsak, ki je odigral eno tekmo brez izgubljenega niza. */
    private static final int PRAG_ZIDA = 3;

    /* Koliko vrstic ima lestvicka (vzponi, zid, delavci, klubi, gostje).
       Tri so toliko, kolikor jih na 390 px stoji brez drsenja. */
    private static final int VRSTIC = 3;

    private final TurnirRepozitorij turnirRepozitorij;
    private final LigaRepozitorij ligaRepozitorij;
    private final TekmaRepozitorij tekmaRepozitorij;
    private final TekmaSrecanjaRepozitorij tekmaSrecanjaRepozitorij;
    private final NizRepozitorij nizRepozitorij;
    private final NizSrecanjaRepozitorij nizSrecanjaRepozitorij;
    private final RatingZgodovinaRepozitorij ratingZgodovinaRepozitorij;
    private final PrijavaRepozitorij prijavaRepozitorij;

    public StatistikaTekmovanjaStoritev(TurnirRepozitorij turnirRepozitorij,
                                        LigaRepozitorij ligaRepozitorij,
                                        TekmaRepozitorij tekmaRepozitorij,
                                        TekmaSrecanjaRepozitorij tekmaSrecanjaRepozitorij,
                                        NizRepozitorij nizRepozitorij,
                                        NizSrecanjaRepozitorij nizSrecanjaRepozitorij,
                                        RatingZgodovinaRepozitorij ratingZgodovinaRepozitorij,
                                        PrijavaRepozitorij prijavaRepozitorij) {
        this.turnirRepozitorij = turnirRepozitorij;
        this.ligaRepozitorij = ligaRepozitorij;
        this.tekmaRepozitorij = tekmaRepozitorij;
        this.tekmaSrecanjaRepozitorij = tekmaSrecanjaRepozitorij;
        this.nizRepozitorij = nizRepozitorij;
        this.nizSrecanjaRepozitorij = nizSrecanjaRepozitorij;
        this.ratingZgodovinaRepozitorij = ratingZgodovinaRepozitorij;
        this.prijavaRepozitorij = prijavaRepozitorij;
    }

    /* ---------- Vmesna oblika ---------- */

    /* Ena odigrana tekma, prevedena v obliko, ki je neodvisna od tega, ali
       prihaja s turnirja ali iz lige. Vse skupne postavke berejo samo to.

       Tocke nizov so ZMERAJ z vidika zmagovalca ({tockeZmagovalca,
       tockePorazenca}) - tako obrat, najdaljsi niz in sestevki ne potrebujejo
       vsak svojega obracanja strani. Seznam je prazen, kadar tock ni vpisal
       nihce; vnos je povsod neobvezen. */
    private record Nastop(
            boolean dvojice,
            Oseba zmagovalec, Oseba zmagovalec2,
            Oseba porazenec, Oseba porazenec2,
            int niziZmagovalca, int niziPorazenca,
            String kontekst,
            Long idTekme,
            List<int[]> nizi
    ) {

        boolean posamicno() {
            return !dvojice;
        }

        String izid() {
            return niziZmagovalca + " : " + niziPorazenca;
        }

        int tock() {
            int vsota = 0;
            for (int[] niz : nizi) {
                vsota += niz[0] + niz[1];
            }
            return vsota;
        }
    }

    /* Sprememba ratinga ob eni tekmi in rating pred njo. */
    private record Elo(int sprememba, int ratingPred) {}

    /* Dnevnik ELO celega tekmovanja: po tekmah in igralcih, ter rating vsakega
       igralca po njegovi zadnji tekmi tega tekmovanja. */
    private record EloTekmovanja(Map<Long, Map<Long, Elo>> poTekmah, Map<Long, Integer> koncni) {

        static final EloTekmovanja PRAZEN = new EloTekmovanja(Map.of(), Map.of());

        Elo za(Long idTekme, Long idIgralca) {
            return poTekmah.getOrDefault(idTekme, Map.of()).get(idIgralca);
        }
    }

    /* ---------- Turnir ---------- */

    @Transactional(readOnly = true)
    public StatistikaTekmovanjaDto zaTurnir(Long idTurnir) {
        Turnir turnir = turnirRepozitorij.findById(idTurnir)
                .orElseThrow(() -> new NiNajdenoIzjema("Turnir z id " + idTurnir + " ne obstaja."));

        List<Tekma> tekme = tekmaRepozitorij.najdiOdigraneZaTurnir(idTurnir);
        Map<Long, List<int[]>> nizi = niziPoTekmah(nizRepozitorij.tockeZaTurnir(idTurnir));
        Map<Long, Integer> zadnjaKola = zadnjaKola(tekmaRepozitorij.zadnjaKolaTurnirja(idTurnir));

        List<Nastop> nastopi = new ArrayList<>(tekme.size());
        Set<Long> dogodki = new HashSet<>();
        for (Tekma t : tekme) {
            nastopi.add(vNastop(t, nizi.getOrDefault(t.getId(), List.of()), zadnjaKola));
            dogodki.add(t.getDogodek().getId());
        }

        /* Dnevnik ELO beremo le, kadar turnir vanj sploh steje - sicer je
           poizvedba zagotovljeno prazna. */
        EloTekmovanja elo = turnir.isStejeVElo()
                ? eloTekmovanja(ratingZgodovinaRepozitorij
                        .spremembeZaTurnir(idTurnir, RatingStanje.SISTEM_KLUBSKI_ELO))
                : EloTekmovanja.PRAZEN;

        return sestavi(nastopi, elo, turnir.isStejeVElo(),
                turnir.getStatus() != StatusTekmovanja.ZAKLJUCEN,
                dogodki.size(), null,
                prviNaslovi(turnir), null, List.of(), List.of());
    }

    /* Turnirska tekma v vmesno obliko. Klub je posnetek OB PRIJAVI in ne
       trenutni klub igralca: vrstica pove, za koga je takrat igral. */
    private static Nastop vNastop(Tekma t, List<int[]> surovi, Map<Long, Integer> zadnjaKola) {
        boolean prviZmagal = t.getZmagovalec() != null
                && t.getZmagovalec().getId().equals(t.getPrijava1().getId());
        Prijava zmag = prviZmagal ? t.getPrijava1() : t.getPrijava2();
        Prijava por = prviZmagal ? t.getPrijava2() : t.getPrijava1();

        Dogodek d = t.getDogodek();
        String faza = opisFaze(d, t, zadnjaKola.get(d.getId()));

        return new Nastop(
                d.getDisciplina() == Disciplina.DVOJICE,
                oseba(zmag.getIgralec(), zmag.getKlubObPrijavi()),
                oseba(zmag.getIgralec2(), zmag.getKlubObPrijavi2()),
                oseba(por.getIgralec(), por.getKlubObPrijavi()),
                oseba(por.getIgralec2(), por.getKlubObPrijavi2()),
                prviZmagal ? t.getDobljeniNizi1() : t.getDobljeniNizi2(),
                prviZmagal ? t.getDobljeniNizi2() : t.getDobljeniNizi1(),
                d.getIme() + " · " + faza,
                t.getId(),
                prviZmagal ? surovi : obrnjeni(surovi));
    }

    /* ---------- Liga ---------- */

    @Transactional(readOnly = true)
    public StatistikaTekmovanjaDto zaLigo(Long idLiga) {
        Liga liga = ligaRepozitorij.findById(idLiga)
                .orElseThrow(() -> new NiNajdenoIzjema("Liga z id " + idLiga + " ne obstaja."));

        List<TekmaSrecanja> tekme = tekmaSrecanjaRepozitorij.najdiOdigraneZaLigo(idLiga);
        Map<Long, List<int[]>> nizi = niziPoTekmah(nizSrecanjaRepozitorij.tockeZaLigo(idLiga));

        List<Nastop> nastopi = new ArrayList<>(tekme.size());
        Set<Long> ekipe = new HashSet<>();
        for (TekmaSrecanja t : tekme) {
            Nastop n = vNastop(t, nizi.getOrDefault(t.getId(), List.of()));
            /* Tekma brez postavljenih igralcev nosi izid srecanja, o igralcih
               pa ne pove nicesar - v statistiko posameznika ne sodi. */
            if (n != null) {
                nastopi.add(n);
            }
            ekipe.add(t.getSrecanje().getEkipaDomaci().getId());
            ekipe.add(t.getSrecanje().getEkipaGost().getId());
        }

        EloTekmovanja elo = liga.isStejeVElo()
                ? eloTekmovanja(ratingZgodovinaRepozitorij
                        .spremembeZaLigo(idLiga, RatingStanje.SISTEM_KLUBSKI_ELO))
                : EloTekmovanja.PRAZEN;

        return sestavi(nastopi, elo, liga.isStejeVElo(),
                liga.getStatus() != StatusTekmovanja.ZAKLJUCEN,
                null, ekipe.size(),
                List.of(), naNoz(liga, tekme), gostje(tekme), nosilci(tekme));
    }

    /* Ligaska tekma v vmesno obliko; null, kadar postava ni bila vpisana.
       Klub je tu trenutni klub igralca - posnetka ob nastopu liga ne hrani
       (ekipa ni nujno klub, klub pa je last igralca). */
    private static Nastop vNastop(TekmaSrecanja t, List<int[]> surovi) {
        boolean domaciZmagali = t.getZmagovalecStran() == StranEkipe.DOMACI;
        Igralec z1 = domaciZmagali ? t.getIgralecDomaci() : t.getIgralecGost();
        Igralec z2 = domaciZmagali ? t.getIgralecDomaci2() : t.getIgralecGost2();
        Igralec p1 = domaciZmagali ? t.getIgralecGost() : t.getIgralecDomaci();
        Igralec p2 = domaciZmagali ? t.getIgralecGost2() : t.getIgralecDomaci2();
        if (z1 == null || p1 == null) {
            return null;
        }
        boolean dvojice = t.getTip() == TipTekmeSrecanja.DVOJICE;
        if (dvojice && (z2 == null || p2 == null)) {
            return null;
        }

        Srecanje s = t.getSrecanje();
        return new Nastop(
                dvojice,
                oseba(z1, z1.getKlub()), oseba(z2, z2 == null ? null : z2.getKlub()),
                oseba(p1, p1.getKlub()), oseba(p2, p2 == null ? null : p2.getKlub()),
                domaciZmagali ? t.getDobljeniNiziDomaci() : t.getDobljeniNiziGost(),
                domaciZmagali ? t.getDobljeniNiziGost() : t.getDobljeniNiziDomaci(),
                s.getKolo() + ". kolo · " + s.getEkipaDomaci().prikazanoIme()
                        + " : " + s.getEkipaGost().prikazanoIme(),
                t.getId(),
                domaciZmagali ? surovi : obrnjeni(surovi));
    }

    /* ---------- Skupno sestavljanje ---------- */

    private StatistikaTekmovanjaDto sestavi(List<Nastop> nastopi, EloTekmovanja elo,
                                            boolean stejeVElo, boolean vTeku,
                                            Integer dogodkov, Integer ekip,
                                            List<PrviNaslov> prviNaslovi, NaNoz naNoz,
                                            List<Gostovanje> gostje, List<Nosilec> nosilci) {
        if (nastopi.size() < PRAG_TEKEM) {
            return new StatistikaTekmovanjaDto(false, vTeku, stejeVElo, null,
                    List.of(), null, List.of(), List.of(), List.of(), null, null, null,
                    List.of(), null, List.of(), List.of(), null);
        }
        return new StatistikaTekmovanjaDto(
                true, vTeku, stejeVElo,
                stevilke(nastopi, dogodkov, ekip),
                vzponi(nastopi, elo),
                presenecenje(nastopi, elo),
                zid(nastopi),
                delavci(nastopi),
                klubi(nastopi),
                obrat(nastopi),
                najdaljsiNiz(nastopi),
                najdaljsaTekma(nastopi),
                prviNaslovi,
                naNoz,
                gostje,
                nosilci,
                dvojica(nastopi));
    }

    /* ---------- 6. V stevilkah ---------- */

    private static Stevilke stevilke(List<Nastop> nastopi, Integer dogodkov, Integer ekip) {
        Set<Long> igralci = new HashSet<>();
        Set<String> klubi = new HashSet<>();
        int nizov = 0;
        int tock = 0;
        int dvojic = 0;
        for (Nastop n : nastopi) {
            for (Oseba o : udelezenci(n)) {
                igralci.add(o.idIgralec());
                if (o.klub() != null) {
                    klubi.add(o.klub());
                }
            }
            nizov += n.niziZmagovalca() + n.niziPorazenca();
            tock += n.tock();
            if (n.dvojice()) {
                dvojic++;
            }
        }
        /* Nic tock ne pomeni "odigrali so nic tock", ampak "tock ni vpisal
           nihce" - takrat stevilke ni. */
        return new Stevilke(igralci.size(), klubi.size(), nastopi.size(), nizov,
                tock == 0 ? null : tock, dogodkov, ekip, dvojic);
    }

    /* ---------- 1. Najvec pridobljenega ELO ---------- */

    /* Zavihek pozna samo vzpon. Vrstice "najvec izgubil" nima namenoma: v
       klubu, kjer se vsi poznajo, je razglasitev najvecjega padca dneva edina
       postavka, ki bi komu skodila. */
    private static List<Vzpon> vzponi(List<Nastop> nastopi, EloTekmovanja elo) {
        Map<Long, Oseba> osebe = new HashMap<>();
        Map<Long, int[]> sestevki = new HashMap<>();   // {sprememba, odigranih}
        for (Nastop n : nastopi) {
            if (n.posamicno()) {
                for (Oseba o : List.of(n.zmagovalec(), n.porazenec())) {
                    Elo e = elo.za(n.idTekme(), o.idIgralec());
                    if (e == null) {
                        continue;
                    }
                    osebe.putIfAbsent(o.idIgralec(), o);
                    int[] s = sestevki.computeIfAbsent(o.idIgralec(), k -> new int[2]);
                    s[0] += e.sprememba();
                    s[1]++;
                }
            }
        }

        List<Vzpon> vrstice = new ArrayList<>();
        for (Map.Entry<Long, int[]> vnos : sestevki.entrySet()) {
            if (vnos.getValue()[0] <= 0) {
                continue;
            }
            Integer koncni = elo.koncni().get(vnos.getKey());
            if (koncni == null) {
                continue;
            }
            vrstice.add(new Vzpon(osebe.get(vnos.getKey()), vnos.getValue()[0],
                    vnos.getValue()[1], koncni));
        }
        vrstice.sort(Comparator.comparingInt((Vzpon v) -> v.pridobil()).reversed()
                .thenComparing(v -> v.oseba().polnoIme()));
        return prvih(vrstice);
    }

    /* ---------- 2. Presenecenje ---------- */

    private static Presenecenje presenecenje(List<Nastop> nastopi, EloTekmovanja elo) {
        Presenecenje najvecje = null;
        for (Nastop n : nastopi) {
            if (!n.posamicno()) {
                continue;
            }
            Elo zmag = elo.za(n.idTekme(), n.zmagovalec().idIgralec());
            Elo por = elo.za(n.idTekme(), n.porazenec().idIgralec());
            if (zmag == null || por == null) {
                continue;
            }
            int razlika = por.ratingPred() - zmag.ratingPred();
            if (razlika <= 0 || (najvecje != null && razlika <= najvecje.razlika())) {
                continue;
            }
            najvecje = new Presenecenje(n.zmagovalec(), zmag.ratingPred(),
                    n.porazenec(), por.ratingPred(), razlika, n.izid(), n.kontekst());
        }
        return najvecje;
    }

    /* ---------- 3. Zid ---------- */

    /* Merilo je DELEZ dobljenih nizov, ne njihova razlika: igralec s petimi
       tekmami in enim izgubljenim nizom je naredil vec kot tisti s tremi in
       nobenim, razlika pa bi ju obrnila. Deleza primerjamo navzkrizno (brez
       decimalk), isto kot lestvica lige - zaokrozen odstotek bi izenacil
       vrstici, ki nista izenaceni. */
    private static List<Zid> zid(List<Nastop> nastopi) {
        Map<Long, Oseba> osebe = new HashMap<>();
        Map<Long, int[]> sestevki = new HashMap<>();   // {dobljeni, prejeti, odigrane}
        for (Nastop n : nastopi) {
            if (!n.posamicno()) {
                continue;
            }
            dodaj(osebe, sestevki, n.zmagovalec(), n.niziZmagovalca(), n.niziPorazenca());
            dodaj(osebe, sestevki, n.porazenec(), n.niziPorazenca(), n.niziZmagovalca());
        }

        List<Zid> vrstice = new ArrayList<>();
        for (Map.Entry<Long, int[]> vnos : sestevki.entrySet()) {
            int[] s = vnos.getValue();
            if (s[2] < PRAG_ZIDA || s[0] + s[1] == 0) {
                continue;
            }
            vrstice.add(new Zid(osebe.get(vnos.getKey()), s[0], s[1], s[2]));
        }
        vrstice.sort((a, b) -> {
            long levo = (long) a.dobljeni() * (b.dobljeni() + b.prejeti());
            long desno = (long) b.dobljeni() * (a.dobljeni() + a.prejeti());
            if (levo != desno) {
                return Long.compare(desno, levo);
            }
            if (a.odigrane() != b.odigrane()) {
                return Integer.compare(b.odigrane(), a.odigrane());
            }
            return a.oseba().polnoIme().compareTo(b.oseba().polnoIme());
        });
        return prvih(vrstice);
    }

    private static void dodaj(Map<Long, Oseba> osebe, Map<Long, int[]> sestevki,
                              Oseba o, int dobljeni, int prejeti) {
        osebe.putIfAbsent(o.idIgralec(), o);
        int[] s = sestevki.computeIfAbsent(o.idIgralec(), k -> new int[3]);
        s[0] += dobljeni;
        s[1] += prejeti;
        s[2]++;
    }

    /* ---------- 4. Najbolj delaven ---------- */

    /* Steje NASTOPE (kolikokrat je igralec sedel za mizo), ne izkupicka, zato
       smemo pripisati tudi dvojice - a v svoj stevec, ker zmage para v
       "zmage" ne smejo. */
    private static List<Delavec> delavci(List<Nastop> nastopi) {
        Map<Long, Oseba> osebe = new HashMap<>();
        Map<Long, int[]> sestevki = new HashMap<>();   // {odigrane, zmage, dvojic}
        for (Nastop n : nastopi) {
            for (Oseba o : udelezenci(n)) {
                osebe.putIfAbsent(o.idIgralec(), o);
                int[] s = sestevki.computeIfAbsent(o.idIgralec(), k -> new int[3]);
                if (n.dvojice()) {
                    s[2]++;
                } else {
                    s[0]++;
                    if (o.idIgralec().equals(n.zmagovalec().idIgralec())) {
                        s[1]++;
                    }
                }
            }
        }

        List<Delavec> vrstice = new ArrayList<>();
        for (Map.Entry<Long, int[]> vnos : sestevki.entrySet()) {
            int[] s = vnos.getValue();
            vrstice.add(new Delavec(osebe.get(vnos.getKey()), s[0], s[1], s[2]));
        }
        vrstice.sort(Comparator
                .comparingInt((Delavec d) -> d.odigrane() + d.dvojic()).reversed()
                .thenComparing(Comparator.comparingInt(Delavec::zmage).reversed())
                .thenComparing(d -> d.oseba().polnoIme()));
        return prvih(vrstice);
    }

    /* ---------- 5. Klub tekmovanja ---------- */

    /* Steje se nastop igralca, ne tekma: v dvoboju dveh igralcev istega kluba
       klub dobi dva nastopa in eno zmago. Dvojice odpadejo - zmaga para ni
       zmaga posameznika in bi klub z eno mocno dvojico prehitel klub s
       polnim avtobusom. */
    private static List<KlubVrstica> klubi(List<Nastop> nastopi) {
        Map<String, int[]> sestevki = new HashMap<>();       // {zmage, odigrane}
        Map<String, Set<Long>> igralci = new HashMap<>();
        for (Nastop n : nastopi) {
            if (!n.posamicno()) {
                continue;
            }
            dodajKlub(sestevki, igralci, n.zmagovalec(), true);
            dodajKlub(sestevki, igralci, n.porazenec(), false);
        }

        List<KlubVrstica> vrstice = new ArrayList<>();
        for (Map.Entry<String, int[]> vnos : sestevki.entrySet()) {
            vrstice.add(new KlubVrstica(vnos.getKey(), vnos.getValue()[0], vnos.getValue()[1],
                    igralci.get(vnos.getKey()).size()));
        }
        vrstice.sort(Comparator.comparingInt((KlubVrstica k) -> k.zmage()).reversed()
                .thenComparing(KlubVrstica::odigrane)
                .thenComparing(KlubVrstica::ime));
        return prvih(vrstice);
    }

    private static void dodajKlub(Map<String, int[]> sestevki, Map<String, Set<Long>> igralci,
                                  Oseba o, boolean zmaga) {
        if (o.klub() == null) {
            return;
        }
        int[] s = sestevki.computeIfAbsent(o.klub(), k -> new int[2]);
        if (zmaga) {
            s[0]++;
        }
        s[1]++;
        igralci.computeIfAbsent(o.klub(), k -> new HashSet<>()).add(o.idIgralec());
    }

    /* ---------- 7. Obrat ---------- */

    /* Obrat je zmaga po izgubljenih PRVIH DVEH nizih. Merilo je zavestno
       preprosto (in tako, kot o tem ljudje govorijo); pri dveh dobljenih nizih
       na tri je nemogoc, zato vrstice pri kratkih tekmah preprosto ni.
       Izmed vseh obratov pokazemo najbolj borbenega - tistega z najvec
       odigranimi tockami. */
    private static Obrat obrat(List<Nastop> nastopi) {
        Nastop najboljsi = null;
        int koliko = 0;
        for (Nastop n : nastopi) {
            if (!n.posamicno() || n.nizi().size() < 3) {
                continue;
            }
            int[] prvi = n.nizi().get(0);
            int[] drugi = n.nizi().get(1);
            if (prvi[0] >= prvi[1] || drugi[0] >= drugi[1]) {
                continue;
            }
            koliko++;
            if (najboljsi == null || n.tock() > najboljsi.tock()) {
                najboljsi = n;
            }
        }
        if (najboljsi == null) {
            return null;
        }
        return new Obrat(najboljsi.zmagovalec(), najboljsi.porazenec(), najboljsi.izid(),
                zapisNizov(najboljsi.nizi()), najboljsi.kontekst(), koliko);
    }

    /* ---------- 8. Najdaljsi niz in najdaljsa tekma ---------- */

    private static NajdaljsiNiz najdaljsiNiz(List<Nastop> nastopi) {
        NajdaljsiNiz najdaljsi = null;
        int najvec = 0;
        for (Nastop n : nastopi) {
            if (!n.posamicno()) {
                continue;
            }
            for (int i = 0; i < n.nizi().size(); i++) {
                int[] niz = n.nizi().get(i);
                int skupaj = niz[0] + niz[1];
                if (skupaj <= najvec) {
                    continue;
                }
                najvec = skupaj;
                /* "Prvi" je tisti, ki je NIZ dobil - ta ni nujno zmagovalec
                   tekme, in ravno to vrstico naredi zanimivo. */
                boolean zmagovalecTekmeDobilNiz = niz[0] > niz[1];
                najdaljsi = new NajdaljsiNiz(
                        zmagovalecTekmeDobilNiz ? n.zmagovalec() : n.porazenec(),
                        zmagovalecTekmeDobilNiz ? n.porazenec() : n.zmagovalec(),
                        Math.max(niz[0], niz[1]), Math.min(niz[0], niz[1]),
                        i + 1, n.kontekst());
            }
        }
        return najdaljsi;
    }

    private static NajdaljsaTekma najdaljsaTekma(List<Nastop> nastopi) {
        Nastop najdaljsa = null;
        for (Nastop n : nastopi) {
            if (!n.posamicno() || n.nizi().isEmpty()) {
                continue;
            }
            if (najdaljsa == null || n.tock() > najdaljsa.tock()) {
                najdaljsa = n;
            }
        }
        if (najdaljsa == null) {
            return null;
        }
        return new NajdaljsaTekma(najdaljsa.zmagovalec(), najdaljsa.porazenec(),
                najdaljsa.izid(), najdaljsa.tock(), najdaljsa.nizi().size(),
                najdaljsa.kontekst());
    }

    /* ---------- 9. Prvi naslov (samo turnir) ---------- */

    /* Zmagovalec dogodka, ki pred tem turnirjem se ni osvojil nobenega
       1. mesta. Merilo je datum ZACETKA turnirja in strogo "prej":

       - vrstica ne sme izginiti za nazaj. Ce bi bilo merilo "vsi njegovi
         naslovi so s tega turnirja", bi lanski turnir svoj "prvi naslov"
         izgubil v trenutku, ko isti clovek zmaga se enkrat - zapis o
         preteklosti pa se ne spreminja.
       - turnir brez datuma (in naslov brez datuma) ne moreta biti "prej".
         Primerjave, ki je ni mogoce narediti, ne ugibamo: v najslabsem
         primeru vrstica ostane, kar je manjsa napaka od tihega izbrisa. */
    private List<PrviNaslov> prviNaslovi(Turnir turnir) {
        List<Prijava> zmagovalci = prijavaRepozitorij.zmagovalciTurnirja(turnir.getId());
        if (zmagovalci.isEmpty()) {
            return List.of();
        }

        List<Long> idji = new ArrayList<>();
        for (Prijava p : zmagovalci) {
            idji.add(p.getIgralec().getId());
            if (p.getIgralec2() != null) {
                idji.add(p.getIgralec2().getId());
            }
        }

        LocalDate zacetek = turnir.getDatumZacetka();
        Set<Long> zeSNaslovom = new HashSet<>();
        for (Object[] vrstica : prijavaRepozitorij.naslovi(idji)) {
            Long idTurnirja = ((Number) vrstica[2]).longValue();
            LocalDate kdaj = (LocalDate) vrstica[3];
            boolean prej = zacetek != null && kdaj != null && kdaj.isBefore(zacetek);
            if (idTurnirja.equals(turnir.getId()) || !prej) {
                continue;
            }
            for (int i = 0; i < 2; i++) {
                if (vrstica[i] != null) {
                    zeSNaslovom.add(((Number) vrstica[i]).longValue());
                }
            }
        }

        List<PrviNaslov> vrstice = new ArrayList<>();
        Set<Long> ze = new HashSet<>();
        for (Prijava p : zmagovalci) {
            for (Igralec i : new Igralec[] {p.getIgralec(), p.getIgralec2()}) {
                /* Kdor na istem turnirju zmaga v dveh kategorijah, ima en
                   prvi naslov in ne dveh - vrstni red dogodkov znotraj
                   turnirja ni podatek, ki bi ga bilo mogoce razsoditi. */
                if (i == null || !ze.add(i.getId()) || zeSNaslovom.contains(i.getId())) {
                    continue;
                }
                vrstice.add(new PrviNaslov(oseba(i, i.getKlub()), p.getDogodek().getIme()));
            }
        }
        return List.copyOf(vrstice);
    }

    /* ---------- 10. Srecanje na noz (samo liga) ---------- */

    /* Najtesnejse srecanje, ki ga je odlocila ZADNJA odigrana tekma:
       zmagovalna ekipa jo je dobila in brez nje zmage se ne bi imela.

       Merilo "brez nje se ne bi imela" je odvisno od pravil lige in ne od
       razlike v izidu: kjer liga ima prag zmag (SNTL se konca pri sestih),
       se srecanje ustavi v trenutku odlocitve, zato zadnja odigrana tekma
       zmago VEDNO prinese - tam loci sele tesnost izida. Kjer praga ni in se
       odigrajo vse tekme, pa zadnja tekma lahko pade tudi, ko je vse ze
       odloceno; take izpustimo.

       Neodloceni izid sem ne sodi: tam zadnja tekma ni odlocila, ampak
       izenacila. Ob enaki tesnosti pokazemo najnovejse srecanje - gledalec
       zavihek odpre zaradi tega, kar se je zgodilo zadnjic. */
    private static NaNoz naNoz(Liga liga, List<TekmaSrecanja> tekme) {
        Map<Long, List<TekmaSrecanja>> poSrecanjih = new LinkedHashMap<>();
        for (TekmaSrecanja t : tekme) {
            poSrecanjih.computeIfAbsent(t.getSrecanje().getId(), k -> new ArrayList<>()).add(t);
        }

        TekmaSrecanja najtesnejsaOdlocitev = null;
        int domacihNajtesnejse = 0;
        int gostovNajtesnejse = 0;
        int koliko = 0;
        for (List<TekmaSrecanja> vSrecanju : poSrecanjih.values()) {
            TekmaSrecanja zadnja = vSrecanju.get(vSrecanju.size() - 1);
            int domaci = 0;
            int gost = 0;
            for (TekmaSrecanja t : vSrecanju) {
                if (t.getZmagovalecStran() == StranEkipe.DOMACI) {
                    domaci++;
                } else {
                    gost++;
                }
            }
            if (domaci == gost) {
                continue;
            }
            boolean domaciZmagali = domaci > gost;
            if ((zadnja.getZmagovalecStran() == StranEkipe.DOMACI) != domaciZmagali) {
                continue;
            }
            int zmageZmagovalca = Math.max(domaci, gost);
            int zmagePorazenca = Math.min(domaci, gost);
            if (jeBiloOdlocenoZePrej(liga, zmageZmagovalca, zmagePorazenca)) {
                continue;
            }

            int razlika = zmageZmagovalca - zmagePorazenca;
            if (najtesnejsaOdlocitev != null) {
                int dosedanja = Math.abs(domacihNajtesnejse - gostovNajtesnejse);
                /* Stevec teje samo srecanja z ISTO (najmanjso) razliko - pri
                   ligi s pragom bi "koliko srecanj je odlocila zadnja tekma"
                   pomenilo vsa in ne bi povedalo nicesar. */
                if (razlika > dosedanja) {
                    continue;
                }
                if (razlika < dosedanja) {
                    koliko = 0;
                } else if (zadnja.getSrecanje().getKolo()
                        <= najtesnejsaOdlocitev.getSrecanje().getKolo()) {
                    koliko++;
                    continue;
                }
            }
            koliko++;
            najtesnejsaOdlocitev = zadnja;
            domacihNajtesnejse = domaci;
            gostovNajtesnejse = gost;
        }
        if (najtesnejsaOdlocitev == null) {
            return null;
        }

        Srecanje s = najtesnejsaOdlocitev.getSrecanje();
        Igralec odlocil = domacihNajtesnejse > gostovNajtesnejse
                ? najtesnejsaOdlocitev.getIgralecDomaci()
                : najtesnejsaOdlocitev.getIgralecGost();
        return new NaNoz(s.getId(), s.getKolo(),
                s.getEkipaDomaci().prikazanoIme(), s.getEkipaGost().prikazanoIme(),
                domacihNajtesnejse, gostovNajtesnejse,
                /* Pri dvojicah zadnje tekme ne pripisemo enemu cloveku -
                   vrstica takrat ostane brez imena. */
                najtesnejsaOdlocitev.getTip() == TipTekmeSrecanja.DVOJICE || odlocil == null
                        ? null : oseba(odlocil, odlocil.getKlub()),
                koliko);
    }

    /* Ali je bilo srecanje odloceno ze PRED zadnjo odigrano tekmo. Liga s
       pragom zmag se v trenutku odlocitve ustavi, zato tam odgovor ni nikoli
       pritrdilen; liga, ki odigra vse tekme, pa lahko konca 7 : 3 in zadnja
       tekma je bila samo se ena tekma. */
    private static boolean jeBiloOdlocenoZePrej(Liga liga, int zmagovalec, int porazenec) {
        Integer prag = liga.getZmagZaSrecanje();
        return prag != null ? zmagovalec - 1 >= prag : zmagovalec - 1 > porazenec;
    }

    /* ---------- 11. Najboljsi gost (samo liga) ---------- */

    /* Izkupicek V GOSTEH po ekipah. Ekipna lestvica tega ne pove - tam se
       doma in v gosteh sestejeta v eno vrstico. */
    private static List<Gostovanje> gostje(List<TekmaSrecanja> tekme) {
        Map<Long, Srecanje> srecanja = new LinkedHashMap<>();
        for (TekmaSrecanja t : tekme) {
            srecanja.putIfAbsent(t.getSrecanje().getId(), t.getSrecanje());
        }

        Map<String, int[]> sestevki = new HashMap<>();   // {zmage, srecanj}
        for (Srecanje s : srecanja.values()) {
            int[] v = sestevki.computeIfAbsent(s.getEkipaGost().prikazanoIme(), k -> new int[2]);
            if (s.getDobljeneGost() > s.getDobljeneDomaci()) {
                v[0]++;
            }
            v[1]++;
        }

        List<Gostovanje> vrstice = new ArrayList<>();
        for (Map.Entry<String, int[]> vnos : sestevki.entrySet()) {
            int[] v = vnos.getValue();
            if (v[0] == 0) {
                continue;
            }
            vrstice.add(new Gostovanje(vnos.getKey(), v[0], v[1],
                    Math.round(v[0] * 100f / v[1])));
        }
        vrstice.sort(Comparator.comparingInt((Gostovanje g) -> g.zmage()).reversed()
                .thenComparing(Comparator.comparingInt(Gostovanje::odstotek).reversed())
                .thenComparing(Gostovanje::ekipa));
        return prvih(vrstice);
    }

    /* ---------- 12. Nosilec ekipe (samo liga) ---------- */

    /* Za vsako ekipo igralec z najvec zmagami ZANJO. Bilanca je bilanca pri
       tisti ekipi in ne v celi ligi - liga brez prepovedi dvojne registracije
       sme istega igralca voditi v dveh kadrih (isto pravilo kot pri kadru pod
       vrstico lestvice). Dvojice odpadejo: zmage para ni mogoce pripisati. */
    private static List<Nosilec> nosilci(List<TekmaSrecanja> tekme) {
        Map<KljucKadra, int[]> bilance = new HashMap<>();    // {zmage, porazi}
        Map<Long, Igralec> igralci = new HashMap<>();

        for (TekmaSrecanja t : tekme) {
            if (t.getTip() != TipTekmeSrecanja.POSAMICNA) {
                continue;
            }
            Igralec domaci = t.getIgralecDomaci();
            Igralec gost = t.getIgralecGost();
            if (domaci == null || gost == null) {
                continue;
            }
            boolean domaciZmagal = t.getZmagovalecStran() == StranEkipe.DOMACI;
            Srecanje s = t.getSrecanje();
            igralci.putIfAbsent(domaci.getId(), domaci);
            igralci.putIfAbsent(gost.getId(), gost);
            bilance.computeIfAbsent(
                    new KljucKadra(s.getEkipaDomaci().prikazanoIme(), domaci.getId()),
                    k -> new int[2])[domaciZmagal ? 0 : 1]++;
            bilance.computeIfAbsent(
                    new KljucKadra(s.getEkipaGost().prikazanoIme(), gost.getId()),
                    k -> new int[2])[domaciZmagal ? 1 : 0]++;
        }

        Map<String, Nosilec> najboljsi = new HashMap<>();
        for (Map.Entry<KljucKadra, int[]> vnos : bilance.entrySet()) {
            String ekipa = vnos.getKey().ekipa();
            Igralec i = igralci.get(vnos.getKey().idIgralec());
            Nosilec kandidat = new Nosilec(ekipa, oseba(i, i.getKlub()),
                    vnos.getValue()[0], vnos.getValue()[1]);
            Nosilec dosedanji = najboljsi.get(ekipa);
            if (dosedanji == null || boljsiNosilec(kandidat, dosedanji)) {
                najboljsi.put(ekipa, kandidat);
            }
        }

        List<Nosilec> vrstice = new ArrayList<>(najboljsi.values());
        vrstice.sort(Comparator.comparingInt((Nosilec n) -> n.zmage()).reversed()
                .thenComparing(Nosilec::ekipa));
        return List.copyOf(vrstice);
    }

    /* Bilanca je bilanca PRI TISTI EKIPI: liga brez prepovedi dvojne
       registracije sme istega igralca voditi v dveh kadrih. */
    private record KljucKadra(String ekipa, Long idIgralec) {}

    private static boolean boljsiNosilec(Nosilec kandidat, Nosilec dosedanji) {
        if (kandidat.zmage() != dosedanji.zmage()) {
            return kandidat.zmage() > dosedanji.zmage();
        }
        if (kandidat.porazi() != dosedanji.porazi()) {
            return kandidat.porazi() < dosedanji.porazi();
        }
        return kandidat.oseba().polnoIme().compareTo(dosedanji.oseba().polnoIme()) < 0;
    }

    /* ---------- Najuspesnejsa dvojica ---------- */

    /* Par je NEUREJEN: ista igralca sta ista dvojica ne glede na to, kdo je
       zapisan prvi (isto pravilo kot v lestvici dvojic lige). */
    private static Dvojica dvojica(List<Nastop> nastopi) {
        record Par(Long prvi, Long drugi) {

            static Par iz(Oseba a, Oseba b) {
                return a.idIgralec() <= b.idIgralec()
                        ? new Par(a.idIgralec(), b.idIgralec())
                        : new Par(b.idIgralec(), a.idIgralec());
            }
        }

        Map<Par, int[]> bilance = new HashMap<>();      // {zmage, porazi}
        Map<Par, List<Oseba>> imena = new HashMap<>();
        for (Nastop n : nastopi) {
            if (!n.dvojice() || n.zmagovalec2() == null || n.porazenec2() == null) {
                continue;
            }
            Par zmag = Par.iz(n.zmagovalec(), n.zmagovalec2());
            Par por = Par.iz(n.porazenec(), n.porazenec2());
            imena.putIfAbsent(zmag, List.of(n.zmagovalec(), n.zmagovalec2()));
            imena.putIfAbsent(por, List.of(n.porazenec(), n.porazenec2()));
            bilance.computeIfAbsent(zmag, k -> new int[2])[0]++;
            bilance.computeIfAbsent(por, k -> new int[2])[1]++;
        }

        Par najboljsi = null;
        for (Map.Entry<Par, int[]> vnos : bilance.entrySet()) {
            if (vnos.getValue()[0] == 0) {
                continue;
            }
            if (najboljsi == null || boljsiPar(vnos.getValue(), bilance.get(najboljsi))) {
                najboljsi = vnos.getKey();
            }
        }
        if (najboljsi == null) {
            return null;
        }
        List<Oseba> par = imena.get(najboljsi);
        int[] b = bilance.get(najboljsi);
        return new Dvojica(par.get(0), par.get(1), b[0], b[1]);
    }

    private static boolean boljsiPar(int[] kandidat, int[] dosedanji) {
        if (kandidat[0] != dosedanji[0]) {
            return kandidat[0] > dosedanji[0];
        }
        return kandidat[1] < dosedanji[1];
    }

    /* ---------- Pomozno ---------- */

    /* Ime faze v kontekstu vrstice. Poimenovanje si deli s PovzetkiStoritev -
       z eno izjemo: pri KROZNEM sistemu skupin NI, faza SKUPINA je tam samo
       oznaka kroznega dela, zato bi beseda "skupine" gledalca poslala iskat
       skupinsko lestvico, ki je ni. */
    private static String opisFaze(Dogodek d, Tekma t, Integer zadnjeKolo) {
        if (d.getSistemTekmovanja() == SistemTekmovanja.KROZNI) {
            return t.getKolo() + ". kolo";
        }
        return PovzetkiStoritev.opisFaze(d.getSistemTekmovanja(), t.getFaza(),
                t.getKolo(), zadnjeKolo);
    }

    private static Oseba oseba(Igralec igralec, Klub klub) {
        if (igralec == null) {
            return null;
        }
        return new Oseba(igralec.getId(), igralec.polnoIme(), klub == null ? null : klub.getIme());
    }

    /* Vsi ljudje na tekmi - pri dvojicah stirje, sicer dva. */
    private static List<Oseba> udelezenci(Nastop n) {
        List<Oseba> vsi = new ArrayList<>(4);
        vsi.add(n.zmagovalec());
        if (n.zmagovalec2() != null) {
            vsi.add(n.zmagovalec2());
        }
        vsi.add(n.porazenec());
        if (n.porazenec2() != null) {
            vsi.add(n.porazenec2());
        }
        return vsi;
    }

    /* Vrstice [idTekme, zaporedna, tocke1, tocke2] v seznam nizov po tekmah.
       Poizvedba je urejena po tekmi in zaporedju, zato je vrstni red v
       seznamu vrstni red igranja - brez njega obrata ni mogoce prepoznati. */
    private static Map<Long, List<int[]>> niziPoTekmah(List<Object[]> vrstice) {
        Map<Long, List<int[]>> nizi = new HashMap<>();
        for (Object[] v : vrstice) {
            Long idTekme = ((Number) v[0]).longValue();
            nizi.computeIfAbsent(idTekme, k -> new ArrayList<>())
                    .add(new int[] {((Number) v[2]).intValue(), ((Number) v[3]).intValue()});
        }
        return nizi;
    }

    /* Isti nizi z vidika druge strani. */
    private static List<int[]> obrnjeni(List<int[]> nizi) {
        List<int[]> obrnjeni = new ArrayList<>(nizi.size());
        for (int[] niz : nizi) {
            obrnjeni.add(new int[] {niz[1], niz[0]});
        }
        return obrnjeni;
    }

    /* "11 : 9, 9 : 11, 11 : 7" z vidika zmagovalca tekme. */
    private static String zapisNizov(List<int[]> nizi) {
        StringBuilder zapis = new StringBuilder();
        for (int[] niz : nizi) {
            if (zapis.length() > 0) {
                zapis.append(", ");
            }
            zapis.append(niz[0]).append(':').append(niz[1]);
        }
        return zapis.toString();
    }

    private static Map<Long, Integer> zadnjaKola(List<Object[]> vrstice) {
        Map<Long, Integer> kola = new HashMap<>();
        for (Object[] v : vrstice) {
            kola.put(((Number) v[0]).longValue(), ((Number) v[1]).intValue());
        }
        return kola;
    }

    /* Vrstice [idTekme, idIgralca, sprememba, novaVrednost] v dnevnik ELO.
       Poizvedba je urejena po casu obracuna, zato je zadnja vrednost igralca
       hkrati njegov rating ob koncu tekmovanja. */
    private static EloTekmovanja eloTekmovanja(List<Object[]> vrstice) {
        Map<Long, Map<Long, Elo>> poTekmah = new HashMap<>();
        Map<Long, Integer> koncni = new HashMap<>();
        for (Object[] v : vrstice) {
            Long idTekme = ((Number) v[0]).longValue();
            Long idIgralca = ((Number) v[1]).longValue();
            int sprememba = ((Number) v[2]).intValue();
            int novaVrednost = ((Number) v[3]).intValue();
            poTekmah.computeIfAbsent(idTekme, k -> new HashMap<>())
                    .put(idIgralca, new Elo(sprememba, novaVrednost - sprememba));
            koncni.put(idIgralca, novaVrednost);
        }
        return new EloTekmovanja(poTekmah, koncni);
    }

    private static <T> List<T> prvih(List<T> vrstice) {
        return vrstice.size() <= VRSTIC ? List.copyOf(vrstice) : List.copyOf(vrstice.subList(0, VRSTIC));
    }
}
