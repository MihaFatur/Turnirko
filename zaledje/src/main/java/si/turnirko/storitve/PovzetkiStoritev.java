/* Stevci za sezname turnirjev in dogodkov.

   Zakaj posebna storitev: pas "Danes v dvorani" in vrstica dogodka nosita
   napredek ("odigranih 84 / 181") in stevilo prijavljenih. Oboje je vsota cez
   otroke zapisa, ki je vmesnik brez dodatne poizvedbe ne more sesteti - in
   racunati po eno poizvedbo na turnir bi na seznamu osmih turnirjev pomenilo
   24 poizvedb. Zato so vse tri poizvedbe skupinske (GROUP BY) in se sestejejo
   tu, v eni sami mapi. */
package si.turnirko.storitve;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

@Service
public class PovzetkiStoritev {

    private final DogodekRepozitorij dogodekRepozitorij;
    private final PrijavaRepozitorij prijavaRepozitorij;
    private final TekmaRepozitorij tekmaRepozitorij;

    public PovzetkiStoritev(DogodekRepozitorij dogodekRepozitorij,
                            PrijavaRepozitorij prijavaRepozitorij,
                            TekmaRepozitorij tekmaRepozitorij) {
        this.dogodekRepozitorij = dogodekRepozitorij;
        this.prijavaRepozitorij = prijavaRepozitorij;
        this.tekmaRepozitorij = tekmaRepozitorij;
    }

    /* Stevci enega dogodka: prijavljenih, odigranih tekem, vseh tekem. */
    public record StevciDogodka(int prijav, int odigranih, int vseh) {
        public static final StevciDogodka PRAZNI = new StevciDogodka(0, 0, 0);
    }

    /* Stevci vseh turnirjev, po id-ju turnirja. Turnir brez dogodkov v mapi
       ni - klicatelj naj uporabi Stevci.PRAZNI. */
    @Transactional(readOnly = true)
    public Map<Long, TurnirDto.Stevci> zaVseTurnirje() {
        Map<Long, long[]> dogodki = vVrstice(dogodekRepozitorij.stejPoTurnirjih(), 3);
        Map<Long, long[]> tekme = vVrstice(tekmaRepozitorij.stejPoTurnirjih(), 2);
        Map<Long, long[]> prijave = vVrstice(
                prijavaRepozitorij.stejPoTurnirjih(Prijava.StatusPrijave.ODJAVLJEN), 1);

        Map<Long, TurnirDto.Stevci> povzetki = new HashMap<>();
        for (Map.Entry<Long, long[]> vnos : dogodki.entrySet()) {
            long[] d = vnos.getValue();
            long[] t = tekme.getOrDefault(vnos.getKey(), new long[2]);
            long[] p = prijave.getOrDefault(vnos.getKey(), new long[1]);
            povzetki.put(vnos.getKey(), new TurnirDto.Stevci(
                    (int) d[0], (int) d[1], (int) d[2], (int) p[0], (int) t[1], (int) t[0]));
        }
        return povzetki;
    }

    /* Stevci enega turnirja (za njegovo stran). */
    @Transactional(readOnly = true)
    public TurnirDto.Stevci zaTurnir(Long idTurnir) {
        return zaVseTurnirje().getOrDefault(idTurnir, TurnirDto.Stevci.PRAZNI);
    }

    /* Besedno stanje vseh turnirjev, po id-ju turnirja: kje se turnir igra,
       kdo ga je dobil in kaj je bil zadnji izid. Vse tri poizvedbe so
       skupinske - vrstica turnirja na domaci strani bi sicer za vsak turnir
       potrebovala svojo. */
    @Transactional(readOnly = true)
    public Map<Long, TurnirDto.Potek> potekiVsehTurnirjev() {
        Map<Long, Integer> zadnjaKola = new HashMap<>();
        for (Object[] v : tekmaRepozitorij.zadnjaKolaPoDogodkih()) {
            zadnjaKola.put(((Number) v[0]).longValue(), ((Number) v[1]).intValue());
        }

        /* Turnir z vec dogodki je hkrati v vec fazah; kot "fazo turnirja"
           vzamemo tisto, ki ima najvec se neodigranih tekem - to je delo, ki
           v dvorani prav zdaj tece. */
        Map<Long, Kandidat> faze = new HashMap<>();
        for (Object[] v : tekmaRepozitorij.neodigranaKolaPoDogodkih()) {
            Long idTurnir = ((Number) v[0]).longValue();
            Long idDogodek = ((Number) v[1]).longValue();
            SistemTekmovanja sistem = (SistemTekmovanja) v[2];
            FazaTekme faza = (FazaTekme) v[3];
            int kolo = ((Number) v[4]).intValue();
            long tekem = ((Number) v[5]).longValue();

            String opis = opisFaze(sistem, faza, kolo, zadnjaKola.get(idDogodek));
            Kandidat najboljsi = faze.get(idTurnir);
            if (najboljsi == null || tekem > najboljsi.tekem()) {
                faze.put(idTurnir, new Kandidat(opis, tekem));
            }
        }

        Map<Long, String> zmagovalci = new HashMap<>();
        for (Object[] v : prijavaRepozitorij.zmagovalciPoTurnirjih()) {
            zmagovalci.putIfAbsent(((Number) v[0]).longValue(), v[1] + " " + v[2]);
        }

        Map<Long, String> zadnjiIzidi = new HashMap<>();
        for (Tekma t : tekmaRepozitorij.najdiZadnjeVsakegaTurnirja()) {
            zadnjiIzidi.put(t.getDogodek().getTurnir().getId(),
                    t.getPrijava1().getIgralec().getPriimek() + " "
                            + t.getDobljeniNizi1() + ":" + t.getDobljeniNizi2() + " "
                            + t.getPrijava2().getIgralec().getPriimek());
        }

        Map<Long, TurnirDto.Potek> poteki = new HashMap<>();
        for (Long idTurnir : vsiKljuci(faze, zmagovalci, zadnjiIzidi)) {
            Kandidat faza = faze.get(idTurnir);
            poteki.put(idTurnir, new TurnirDto.Potek(
                    faza != null ? faza.opis() : null,
                    zmagovalci.get(idTurnir),
                    zadnjiIzidi.get(idTurnir)));
        }
        return poteki;
    }

    /* Faza enega dogodka in koliko tekem jo se caka (vec = bolj "glavna"). */
    private record Kandidat(String opis, long tekem) {}

    /* Kako se faza imenuje v vrstici turnirja. Izlocilni del se imenuje po
       oddaljenosti od finala, krozni in skupinski pa po zaporedju kol -
       "1/8 finala" pri vsakem z vsakim ne pomeni nicesar. */
    private static String opisFaze(SistemTekmovanja sistem, FazaTekme faza,
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
