/* Stevci za sezname turnirjev in dogodkov.

   Zakaj posebna storitev: pas "Danes v dvorani" in vrstica dogodka nosita
   napredek ("odigranih 84 / 181") in stevilo prijavljenih. Oboje je vsota cez
   otroke zapisa, ki je vmesnik brez dodatne poizvedbe ne more sesteti - in
   racunati po eno poizvedbo na turnir bi na seznamu osmih turnirjev pomenilo
   24 poizvedb. Zato so vse tri poizvedbe skupinske (GROUP BY) in se sestejejo
   tu, v eni sami mapi. */
package si.turnirko.storitve;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.TurnirDto;
import si.turnirko.modeli.FazaTekme;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.SistemTekmovanja;
import si.turnirko.modeli.Tekma;
import si.turnirko.repozitoriji.DogodekRepozitorij;
import si.turnirko.repozitoriji.PrijavaRepozitorij;
import si.turnirko.repozitoriji.TekmaRepozitorij;
import si.turnirko.repozitoriji.TurnirRepozitorij;

@Service
public class PovzetkiStoritev {

    private final DogodekRepozitorij dogodekRepozitorij;
    private final PrijavaRepozitorij prijavaRepozitorij;
    private final TekmaRepozitorij tekmaRepozitorij;
    private final TurnirRepozitorij turnirRepozitorij;
    private final PredpomnilnikBranja predpomnilnik;

    public PovzetkiStoritev(DogodekRepozitorij dogodekRepozitorij,
                            PrijavaRepozitorij prijavaRepozitorij,
                            TekmaRepozitorij tekmaRepozitorij,
                            TurnirRepozitorij turnirRepozitorij,
                            PredpomnilnikBranja predpomnilnik) {
        this.dogodekRepozitorij = dogodekRepozitorij;
        this.prijavaRepozitorij = prijavaRepozitorij;
        this.tekmaRepozitorij = tekmaRepozitorij;
        this.turnirRepozitorij = turnirRepozitorij;
        this.predpomnilnik = predpomnilnik;
    }

    /* Stevci enega dogodka: prijavljenih, odigranih tekem, vseh tekem. */
    public record StevciDogodka(int prijav, int odigranih, int vseh) {
        public static final StevciDogodka PRAZNI = new StevciDogodka(0, 0, 0);
    }

    /* Stevci vseh turnirjev, po id-ju turnirja. Turnir brez dogodkov v mapi
       ni - klicatelj naj uporabi Stevci.PRAZNI. */
    @Transactional(readOnly = true)
    public Map<Long, TurnirDto.Stevci> zaVseTurnirje() {
        /* Seznam turnirjev je za vse gledalce isti in presteje vse tekme in
           prijave, zato pride iz predpomnilnika, dokler ga zapis v bazo ne
           razveljavi (PredpomnilnikBranja). Razlicica je dan: zreb pred
           datumom turnirja je "V pripravi" samo do tega dne. */
        return predpomnilnik.vrni("stevci-turnirjev", PotekLige.danes(),
                () -> Map.copyOf(stevci(dogodekRepozitorij.stejPoTurnirjih(),
                        tekmaRepozitorij.stejPoTurnirjih(),
                        prijavaRepozitorij.stejPoTurnirjih(Prijava.StatusPrijave.ODJAVLJEN))));
    }

    /* Stevci iz vrstic treh skupinskih poizvedb. Seznam turnirjev poda vrstice
       vseh turnirjev, stran turnirja samo svoje (zaTurnir) - sestevanje je
       eno, da si vrstica v seznamu in stran turnirja ne moreta nasprotovati. */
    private Map<Long, TurnirDto.Stevci> stevci(List<Object[]> vrsticeDogodkov,
                                               List<Object[]> vrsticeTekem,
                                               List<Object[]> vrsticePrijav) {
        Map<Long, long[]> dogodki = vVrstice(vrsticeDogodkov, 3);
        Map<Long, long[]> tekme = vVrstice(vrsticeTekem, 2);
        Map<Long, long[]> prijave = vVrstice(vrsticePrijav, 1);
        Set<Long> zrebaniPrihodnji = prihodnjiZrebi();

        Map<Long, TurnirDto.Stevci> povzetki = new HashMap<>();
        for (Map.Entry<Long, long[]> vnos : dogodki.entrySet()) {
            long[] d = vnos.getValue();
            long[] t = tekme.getOrDefault(vnos.getKey(), new long[2]);
            long[] p = prijave.getOrDefault(vnos.getKey(), new long[1]);
            // zreb pred datumom turnirja je se "V pripravi" (StatusTekmovanja.zaPrikaz)
            boolean pripravljen = zrebaniPrihodnji.contains(vnos.getKey());
            long vTeku = pripravljen ? 0 : d[1];
            long vPripravi = pripravljen ? d[1] + d[2] : d[2];
            povzetki.put(vnos.getKey(), new TurnirDto.Stevci(
                    (int) d[0], (int) vTeku, (int) vPripravi, (int) p[0], (int) t[1], (int) t[0]));
        }
        return povzetki;
    }

    /* Turnirji z zrebom, ki se se ne zacnejo: gledalcu so "V pripravi", zato njihovih
       faz ne smemo izpisati kot da tekmujejo. */
    private Set<Long> prihodnjiZrebi() {
        return new HashSet<>(turnirRepozitorij.idjiZrebanihPrihodnjih(PotekLige.danes()));
    }

    /* Stevci enega turnirja (za njegovo stran). Poizvedbe so iste kot za
       seznam, le omejene na ta turnir: stran enega turnirja je prej sestela
       dogodke, tekme in prijave VSEH turnirjev (nov. 2026, izmerjeno na
       kopiji produkcije: GET /turnirji/{id} 224 ms za 574 znakov odgovora). */
    @Transactional(readOnly = true)
    public TurnirDto.Stevci zaTurnir(Long idTurnir) {
        return stevci(dogodekRepozitorij.stejZaTurnir(idTurnir),
                tekmaRepozitorij.stejZaTurnir(idTurnir),
                prijavaRepozitorij.stejZaTurnir(idTurnir, Prijava.StatusPrijave.ODJAVLJEN))
                .getOrDefault(idTurnir, TurnirDto.Stevci.PRAZNI);
    }

    /* Besedno stanje vseh turnirjev, po id-ju turnirja: kje se turnir igra,
       kdo ga je dobil in kaj je bil zadnji izid. Vse tri poizvedbe so
       skupinske - vrstica turnirja na domaci strani bi sicer za vsak turnir
       potrebovala svojo. */
    @Transactional(readOnly = true)
    public Map<Long, TurnirDto.Potek> potekiVsehTurnirjev() {
        // iz predpomnilnika, kot stevci (zaVseTurnirje)
        return predpomnilnik.vrni("poteki-turnirjev", PotekLige.danes(),
                () -> Map.copyOf(poteki(tekmaRepozitorij.zadnjaKolaPoDogodkih(),
                        tekmaRepozitorij.neodigranaKolaPoDogodkih(),
                        prijavaRepozitorij.zmagovalciPoTurnirjih(),
                        tekmaRepozitorij.najdiZadnjeVsakegaTurnirja())));
    }

    /* Besedno stanje enega turnirja (za njegovo stran) - iste poizvedbe,
       omejene na ta turnir, in isto sestavljanje kot za seznam. */
    @Transactional(readOnly = true)
    public TurnirDto.Potek potekTurnirja(Long idTurnir) {
        return poteki(tekmaRepozitorij.zadnjaKolaTurnirja(idTurnir),
                tekmaRepozitorij.neodigranaKolaTurnirja(idTurnir),
                prijavaRepozitorij.imenaZmagovalcevTurnirja(idTurnir),
                tekmaRepozitorij.najdiZadnjoTurnirja(idTurnir))
                .getOrDefault(idTurnir, TurnirDto.Potek.PRAZEN);
    }

    private Map<Long, TurnirDto.Potek> poteki(List<Object[]> vrsticeZadnjihKol,
                                              List<Object[]> vrsticeNeodigranih,
                                              List<Object[]> vrsticeZmagovalcev,
                                              List<Tekma> zadnjeTekme) {
        Map<Long, Integer> zadnjaKola = new HashMap<>();
        for (Object[] v : vrsticeZadnjihKol) {
            zadnjaKola.put(((Number) v[0]).longValue(), ((Number) v[1]).intValue());
        }

        /* Turnir z vec dogodki je hkrati v vec fazah; kot "fazo turnirja"
           vzamemo tisto, ki ima najvec se neodigranih tekem - to je delo, ki
           v dvorani prav zdaj tece. Ob enakem stevilu odloci nizji id dogodka
           in nato faza: vrstni red vrstic skupinske poizvedbe ni dolocen,
           seznam turnirjev in stran turnirja pa morata izbrati isto. */
        Map<Long, Kandidat> faze = new HashMap<>();
        for (Object[] v : vrsticeNeodigranih) {
            Long idTurnir = ((Number) v[0]).longValue();
            Long idDogodek = ((Number) v[1]).longValue();
            SistemTekmovanja sistem = (SistemTekmovanja) v[2];
            FazaTekme faza = (FazaTekme) v[3];
            int kolo = ((Number) v[4]).intValue();
            long tekem = ((Number) v[5]).longValue();

            Kandidat kandidat = new Kandidat(
                    opisFaze(sistem, faza, kolo, zadnjaKola.get(idDogodek)), tekem, idDogodek, faza);
            Kandidat najboljsi = faze.get(idTurnir);
            if (najboljsi == null || kandidat.predKandidatom(najboljsi)) {
                faze.put(idTurnir, kandidat);
            }
        }

        // Zmagovalec dogodka dvojic je PAR - izpisemo obe imeni
        // ("Ana Novak / Eva Zajc"), sicer bi polovica zmagovalcev izginila.
        Map<Long, String> zmagovalci = new HashMap<>();
        for (Object[] v : vrsticeZmagovalcev) {
            // ekipni dogodek: zmagovalec je ekipa (ime in priimek sta prazna)
            String ime = v[1] == null ? String.valueOf(v[5]) : v[1] + " " + v[2];
            if (v[3] != null) {
                ime += " / " + v[3] + " " + v[4];
            }
            zmagovalci.putIfAbsent(((Number) v[0]).longValue(), ime);
        }

        Map<Long, String> zadnjiIzidi = new HashMap<>();
        for (Tekma t : zadnjeTekme) {
            zadnjiIzidi.put(t.getDogodek().getTurnir().getId(),
                    t.getPrijava1().prikazaniPriimek() + " "
                            + t.getDobljeniNizi1() + ":" + t.getDobljeniNizi2() + " "
                            + t.getPrijava2().prikazaniPriimek());
        }

        Set<Long> zrebaniPrihodnji = prihodnjiZrebi();
        Map<Long, TurnirDto.Potek> poteki = new HashMap<>();
        for (Long idTurnir : vsiKljuci(faze, zmagovalci, zadnjiIzidi)) {
            Kandidat faza = faze.get(idTurnir);
            poteki.put(idTurnir, new TurnirDto.Potek(
                    faza != null && !zrebaniPrihodnji.contains(idTurnir) ? faza.opis() : null,
                    zmagovalci.get(idTurnir),
                    zadnjiIzidi.get(idTurnir)));
        }
        return poteki;
    }

    /* Faza enega dogodka in koliko tekem jo se caka (vec = bolj "glavna"). */
    private record Kandidat(String opis, long tekem, long idDogodek, FazaTekme faza) {

        boolean predKandidatom(Kandidat drugi) {
            if (tekem != drugi.tekem) {
                return tekem > drugi.tekem;
            }
            if (idDogodek != drugi.idDogodek) {
                return idDogodek < drugi.idDogodek;
            }
            return faza.compareTo(drugi.faza) < 0;
        }
    }

    /* Kako se faza imenuje v vrstici turnirja. Izlocilni del se imenuje po
       oddaljenosti od finala, krozni in skupinski pa po zaporedju kol -
       "1/8 finala" pri vsakem z vsakim ne pomeni nicesar.

       Vidna je v paketu, ker isto poimenovanje potrebuje zavihek statistike
       turnirja (kontekst vrstice "Clani - polfinale"); podvojena bi se s to
       tiho razsla. */
    static String opisFaze(SistemTekmovanja sistem, FazaTekme faza,
                           int kolo, Integer zadnjeKolo) {
        if (faza == FazaTekme.SKUPINA) {
            return "skupine";
        }
        if (faza == FazaTekme.TOLAZILNI) {
            return "tolažilni del";
        }
        boolean izlocilni = sistem == SistemTekmovanja.IZLOCILNI
                || sistem == SistemTekmovanja.SKUPINE_IZLOCILNI;
        if (!izlocilni || zadnjeKolo == null) {
            return kolo + ". kolo";
        }
        return imeKola(kolo, zadnjeKolo);
    }

    /* Kolo z enim parom je finale, z dvema polfinale ...; zgodnja kola se
       imenujejo po delezu ("1/16 finala"). Isto poimenovanje ima vmesnik v
       pomozno/oblikovanje.ts - tu je zato, ker vrstica turnirja kol nima. */
    private static String imeKola(int kolo, int zadnjeKolo) {
        int tekemVKolu = 1 << Math.max(zadnjeKolo - kolo, 0);
        return switch (tekemVKolu) {
            case 1 -> "finale";
            case 2 -> "polfinale";
            case 4 -> "četrtfinale";
            case 8 -> "osmina finala";
            default -> "1/" + tekemVKolu + " finala";
        };
    }

    @SafeVarargs
    private static java.util.Set<Long> vsiKljuci(Map<Long, ?>... mape) {
        java.util.Set<Long> vsi = new java.util.HashSet<>();
        for (Map<Long, ?> mapa : mape) {
            vsi.addAll(mapa.keySet());
        }
        return vsi;
    }

    /* Stevci dogodkov enega turnirja, po id-ju dogodka. */
    @Transactional(readOnly = true)
    public Map<Long, StevciDogodka> zaDogodkeTurnirja(Long idTurnir) {
        Map<Long, long[]> tekme = vVrstice(tekmaRepozitorij.stejPoDogodkihTurnirja(idTurnir), 2);
        Map<Long, long[]> prijave = vVrstice(
                prijavaRepozitorij.stejPoDogodkihTurnirja(idTurnir, Prijava.StatusPrijave.ODJAVLJEN), 1);

        Map<Long, StevciDogodka> povzetki = new HashMap<>();
        for (Long idDogodka : union(tekme, prijave)) {
            long[] t = tekme.getOrDefault(idDogodka, new long[2]);
            long[] p = prijave.getOrDefault(idDogodka, new long[1]);
            povzetki.put(idDogodka, new StevciDogodka((int) p[0], (int) t[1], (int) t[0]));
        }
        return povzetki;
    }

    /* [id, stevilo, stevilo ...] -> mapa id -> stevila. SUM vrne null, kadar
       skupina nima nobene ustrezne vrstice, zato je manjkajoca vrednost 0. */
    private static Map<Long, long[]> vVrstice(List<Object[]> vrstice, int koliko) {
        Map<Long, long[]> mapa = new HashMap<>();
        for (Object[] vrstica : vrstice) {
            long[] stevila = new long[koliko];
            for (int i = 0; i < koliko; i++) {
                Object vrednost = vrstica[i + 1];
                stevila[i] = vrednost == null ? 0L : ((Number) vrednost).longValue();
            }
            mapa.put(((Number) vrstica[0]).longValue(), stevila);
        }
        return mapa;
    }

    private static java.util.Set<Long> union(Map<Long, long[]> prva, Map<Long, long[]> druga) {
        java.util.Set<Long> vsi = new java.util.HashSet<>(prva.keySet());
        vsi.addAll(druga.keySet());
        return vsi;
    }
}
