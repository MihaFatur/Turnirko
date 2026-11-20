/* Meritev natancnosti branja listkov (nacrt F1): za vsako celico potrjenega
   listka ugotovi, ali jo je bralnik prebral prav in ali jo je oznacil za
   pregled, ter iz tega izpelje merila (docs/NACRT-BRANJE-LISTKOV.md, razdelek 1).

   Cist izracun brez baze: isti razred meri branja v bazi (MeritevBranjaStoritev)
   in zlati nabor (MeritevNabora), zato sta stevili primerljivi.

   Kdaj je celica »oznacena za pregled«, sledi temu, kar organizator vidi v
   predlogu (PredlogIzidaListka): negotova celica, niz z neprebrano celico ali
   neveljaven niz (opozorilo pri nizu). Prazne vrstice na koncu predlog izpusti,
   zato se ne stejejo (manjkajoc niz pa je napaka). Opozorila celega listka (potek tekme, Rezultat se ne
   ujema) niso oznaka celice - njih zajame merilo »tihe napake«: pripravljen
   predlog (en dotik) z napacno celico. */
package si.turnirko.storitve;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import si.turnirko.dto.MeritevBranjaDto;
import si.turnirko.dto.NizVnos;
import si.turnirko.dto.VnosRezultata;
import si.turnirko.modeli.IzidTekme;
import si.turnirko.modeli.NacinPotrditve;

public final class MeritevBranja {

    private MeritevBranja() {}

    /* Potrjen izid v STOLPCIH LISTKA (levo, desno), ne v straneh tekme.
       nizi je prazen, ce tocke po nizih niso bile vpisane (samo izid). */
    public record Resnica(List<int[]> nizi, Integer rezultatLevo, Integer rezultatDesno) {}

    /* Stevci celic enega ali vec listkov. */
    public record Celice(int pravZanesljive, int pravNegotove, int narobeNegotove, int narobeZanesljive) {
        static final Celice NIC = new Celice(0, 0, 0, 0);

        public int prav() {
            return pravZanesljive + pravNegotove;
        }

        public int narobe() {
            return narobeNegotove + narobeZanesljive;
        }

        Celice plus(Celice o) {
            return new Celice(pravZanesljive + o.pravZanesljive, pravNegotove + o.pravNegotove,
                    narobeNegotove + o.narobeNegotove, narobeZanesljive + o.narobeZanesljive);
        }

        MeritevBranjaDto.Celice dto() {
            return new MeritevBranjaDto.Celice(pravZanesljive, pravNegotove, narobeNegotove, narobeZanesljive);
        }
    }

    /* Potrjen izid v stolpcih listka; null pri posebnem izidu (brez boja,
       predaja, diskvalifikacija), ki ga bralnik ne bere. */
    public static Resnica resnica(VnosRezultata izid, boolean obrnjeno) {
        if (izid == null || (izid.izidTip() != null && izid.izidTip() != IzidTekme.IGRANO)) {
            return null;
        }
        List<int[]> nizi = new ArrayList<>();
        for (NizVnos n : izid.nizi() == null ? List.<NizVnos>of() : izid.nizi()) {
            if (n == null || n.tocke1() == null || n.tocke2() == null) {
                return null;
            }
            nizi.add(obrnjeno ? new int[] {n.tocke2(), n.tocke1()} : new int[] {n.tocke1(), n.tocke2()});
        }
        return new Resnica(nizi, obrnjeno ? izid.dobljeniNizi2() : izid.dobljeniNizi1(),
                obrnjeno ? izid.dobljeniNizi1() : izid.dobljeniNizi2());
    }

    /* Celice listka v smeri, v kateri se potrjeni izid primerja s prebranim.
       Listek s kodo je natisnjen z igralcem 1 v levem stolpcu. Pri ostalih (prazen
       listek, tekma izbrana rocno) je lahko sodnik imeni napisal obratno; tedaj
       velja smer z vec pravilnimi celicami - bralnik je bral prav, organizator je
       le zamenjal strani. Enako dobri smeri: levo = igralec 1. */
    public static Celice celice(PrebranListek prebrano, VnosRezultata izid, boolean poKodi) {
        Resnica naravnost = resnica(izid, false);
        if (prebrano == null || naravnost == null) {
            return null;
        }
        Celice c = primerjaj(prebrano, naravnost);
        if (poKodi) {
            return c;
        }
        Celice obrat = primerjaj(prebrano, resnica(izid, true));
        return obrat.prav() > c.prav() ? obrat : c;
    }

    /* Primerjava celic enega listka. */
    public static Celice primerjaj(PrebranListek prebrano, Resnica resnica) {
        int[] c = new int[4];
        List<PrebranListek.Niz> vrstice = prebrano.niziAliPrazno();
        boolean brezNizov = true;
        if (!resnica.nizi().isEmpty()) {
            int zadnjaPolna = -1;
            for (int i = 0; i < vrstice.size(); i++) {
                PrebranListek.Niz v = vrstice.get(i);
                if (v != null && (v.levo() != null || v.desno() != null)) {
                    zadnjaPolna = i;
                }
            }
            // prazne vrstice za zadnjim nizom (listek za 5 nizov, tekma 3:0) niso celice
            // s pomenom: stete bi napihnile »prav« - odvecen niz pa se steje
            int n = Math.max(zadnjaPolna + 1, resnica.nizi().size());
            for (int i = 0; i < n; i++) {
                PrebranListek.Niz v = i < vrstice.size() ? vrstice.get(i) : null;
                Integer levo = v == null ? null : v.levo();
                Integer desno = v == null ? null : v.desno();
                // vrstica, ki je v predlogu: opozorilo niza oznaci obe celici
                boolean vidna = i <= zadnjaPolna;
                boolean opozoriloNiza = vidna && (levo == null || desno == null
                        || !PredlogIzidaListka.veljavenNiz(levo, desno));
                int[] prava = i < resnica.nizi().size() ? resnica.nizi().get(i) : null;
                stej(c, levo, prava == null ? null : prava[0], opozoriloNiza || (vidna && v.negotovoLevo()));
                stej(c, desno, prava == null ? null : prava[1], opozoriloNiza || (vidna && v.negotovoDesno()));
            }
            brezNizov = false;
        }
        // Rezultat je pri tockah po nizih le preverba (opozorilo ob neujemanju);
        // prazen Rezultat zato ni napaka - sodnik ga pogosto ne izpolni
        boolean rezultatOdloca = brezNizov && vrstice.stream()
                .allMatch(v -> v == null || (v.levo() == null && v.desno() == null));
        if (prebrano.rezultatLevo() != null || rezultatOdloca) {
            stej(c, prebrano.rezultatLevo(), resnica.rezultatLevo(),
                    prebrano.negotovRezultat() || prebrano.rezultatLevo() == null);
        }
        if (prebrano.rezultatDesno() != null || rezultatOdloca) {
            stej(c, prebrano.rezultatDesno(), resnica.rezultatDesno(),
                    prebrano.negotovRezultat() || prebrano.rezultatDesno() == null);
        }
        return new Celice(c[0], c[1], c[2], c[3]);
    }

    private static void stej(int[] c, Integer prebrano, Integer prava, boolean oznacena) {
        boolean prav = Objects.equals(prebrano, prava);
        c[prav ? (oznacena ? 1 : 0) : (oznacena ? 2 : 3)]++;
    }

    // ---------- Zbiranje ----------

    /* En listek za zbirnik. celice je null, ce celic ni bilo mogoce primerjati
       (ni potrjen, ni predloga, posebni izid). */
    public record Vzorec(boolean prebran, boolean neuspel, boolean potrjen, Boolean popravljeno,
                         Boolean pripravljen, boolean kontrola, Integer casMs, NacinPotrditve nacin,
                         Celice celice) {}

    /* Sesteje listke ene skupine (obdobje, turnir, mreza). */
    public static final class Zbirnik {
        private final String oznaka;
        private final Long idTurnir;
        private final LocalDate datum;
        private int branj;
        private int prebranih;
        private int neuspelih;
        private int potrjenih;
        private int brezPopravka;
        private int pripravljenih;
        private int izmerjenih;
        private int tihihNapak;
        private int kontrol;
        private int kontrolZNapako;
        private Celice celice = Celice.NIC;
        private final List<Integer> casi = new ArrayList<>();
        private final List<Integer> casiEnDotik = new ArrayList<>();
        private final List<Integer> casiObrazec = new ArrayList<>();

        public Zbirnik(String oznaka, Long idTurnir, LocalDate datum) {
            this.oznaka = oznaka;
            this.idTurnir = idTurnir;
            this.datum = datum;
        }

        public void dodaj(Vzorec v) {
            branj++;
            if (v.prebran()) {
                prebranih++;
            }
            if (v.neuspel()) {
                neuspelih++;
            }
            if (!v.potrjen()) {
                return;
            }
            // cas steje tudi pri neuspelem branju, vpisanem rocno: tudi to je organizatorjevo delo
            if (v.casMs() != null) {
                casi.add(v.casMs());
                if (v.nacin() == NacinPotrditve.EN_DOTIK) {
                    casiEnDotik.add(v.casMs());
                } else if (v.nacin() == NacinPotrditve.OBRAZEC) {
                    casiObrazec.add(v.casMs());
                }
            }
            if (!v.prebran()) {
                return;
            }
            potrjenih++;
            if (Boolean.FALSE.equals(v.popravljeno())) {
                brezPopravka++;
            }
            boolean pripravljen = Boolean.TRUE.equals(v.pripravljen());
            if (pripravljen) {
                pripravljenih++;
            }
            if (v.celice() != null) {
                izmerjenih++;
                celice = celice.plus(v.celice());
                boolean napaka = v.celice().narobe() > 0;
                if (pripravljen && napaka) {
                    tihihNapak++;
                }
                if (v.kontrola()) {
                    kontrol++;
                    if (napaka) {
                        kontrolZNapako++;
                    }
                }
            }
        }

        public MeritevBranjaDto.Skupina dto() {
            return new MeritevBranjaDto.Skupina(oznaka, idTurnir, datum, branj, prebranih, neuspelih,
                    potrjenih, brezPopravka, pripravljenih, izmerjenih, tihihNapak, kontrol, kontrolZNapako,
                    celice.dto(),
                    new MeritevBranjaDto.Casi(casi.size(), percentil(casi, 50), percentil(casi, 90),
                            casiEnDotik.size(), percentil(casiEnDotik, 50),
                            casiObrazec.size(), percentil(casiObrazec, 50)));
        }
    }

    /* Percentil po metodi najblizjega ranga (vrednost, ki jo ima res neki listek);
       null brez podatkov. */
    static Integer percentil(List<Integer> vrednosti, int p) {
        if (vrednosti.isEmpty()) {
            return null;
        }
        List<Integer> urejene = new ArrayList<>(vrednosti);
        Collections.sort(urejene);
        int rang = (int) Math.ceil(p / 100.0 * urejene.size());
        return urejene.get(Math.max(0, rang - 1));
    }
}
