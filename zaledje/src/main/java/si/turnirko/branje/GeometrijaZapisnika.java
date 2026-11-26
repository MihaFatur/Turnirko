/* Postavitev ligaskega zapisnika za bralnik (lezeci A4, 297 x 210 mm, mm od
   zgornjega levega roba). Lastnik (26. 11. 2026) je zelel dve obliki in obe:

   VRSTICE (»vrstica tekme«): vsaka tekma srecanja je ena vrsta - kvadratki
     domacega kadra, tocke nizov in kvadratki gostujocega kadra. Kdor pise
     tekmo, prekriza igralca in vpise tocke v isti vrsti. Imena kadra so glave
     stolpcev. Za vecji kader ni prostora (sirina lista).
   KADER (»tabela kadra«): zgoraj kadra obeh ekip kot vrstice s stolpcem za vsako
     tekmo, spodaj tabela tock. Prostora za imena je vec, zato je za vecji kader.

   Ta razred je EDINI vir leg: vmesnik list narise iz postavitve, ki mu jo vrne
   streznik (NatisZapisnikovStoritev), bralnik pa isce kvadratke in celice na istih
   legah. Natisnjen zapisnik ima v bazi zapisano razlicico postavitve (natis_zapisnika),
   zato se ob spremembi postavitve doda nova RAZLICICA, stara pa ostane berljiva.

   Poravnava je ista kot pri listku in vadnici (PoravnavaListka): trije polni crni
   kvadrati v vogalih in QR zgoraj desno. Ker je list velik in ni nikoli cisto
   raven (prepognjen v zepu, dvignjen rob), ima vsaka vrsta (pas) se svoji mali
   oznaki na koncih: bralnik po njiju popravi zamik vrste (OznakeVrst), nato pa
   rob vsakega kvadratka in celice poisce po crti (CeliceVadnice). */
package si.turnirko.branje;

import java.util.ArrayList;
import java.util.List;

public final class GeometrijaZapisnika {

    public static final double SIRINA = 297;
    public static final double VISINA = 210;

    /* Razlicica postavitve, ki se natisne zdaj. Ob vsaki spremembi leg spodaj jo
       povisaj in staro ohrani v izracunaj (natisnjeni listi se morajo se brati). */
    public static final int RAZLICICA = 1;

    /* Oznake v vogalih 8 x 8 mm: vecje kot na vadnici (6 mm), ker so na listu tudi
       oznake vrst (polni kvadrati). Simulacija (zamegljena fotografija) je pokazala, da
       poravnava 3-milimetrsko oznako vrste ob vogalu vzame za vogal (zamegljena
       komponenta zraste cez spodnjo mejo velikosti 0,3); pri 8 in 2,5 mm je razmerje
       ploscin ~0,1. */
    public static final double STRANICA_OZNAKE = 8;
    public static final double[] ZGORAJ_LEVO = {14, 14};
    public static final double[] SPODAJ_LEVO = {14, 196};
    public static final double[] SPODAJ_DESNO = {283, 196};

    /* QR: »TZ:<id natisa>:<skrivnost>«, verzija 1, 20 mm (~0,95 mm na modul). */
    public static final double QR_X = 262;
    public static final double QR_Y = 5;
    public static final double QR_STRANICA = 20;
    public static final int QR_MODULOV = 21;

    /* Afina napoved QR na A4 se pri nagnjenem telefonu zmoti za vec kot na listku
       (enako kot vadnica). */
    public static final Postavitev POSTAVITEV = new Postavitev(SIRINA, VISINA, ZGORAJ_LEVO, SPODAJ_LEVO,
            SPODAJ_DESNO, STRANICA_OZNAKE, QR_X, QR_Y, QR_STRANICA, QR_MODULOV, 40);

    /* Oznaka vrste: poln kvadrat 2,5 mm na vsakem koncu pasu. Ploscina je desetina
       oznake v vogalu, zato je poravnava ne zamenja za vogal (PoravnavaListka
       dovoli 0,3-2,5 pricakovane). */
    public static final double STRANICA_OZNAKE_VRSTE = 2.5;

    /* Crta kvadratkov in celic (mm): kot okenca vadnice - tanjsa se na fotografiji
       raztrga, debelejsa bi se zlila s krizcem. */
    public static final double CRTA = 0.45;

    /* Notranja polja: vsebina med robom lista in oznakami. */
    private static final double LEVO = 20;
    private static final double DESNO = 277;

    public enum Oblika { VRSTICE, KADER }

    /* Pravokotnik v mm: levi zgornji kot in mere. */
    public record Pravokotnik(double x, double y, double sirina, double visina) {
        public double[] robovi() {
            return new double[] {x, y, x + sirina, y + visina};
        }

        public double sredinaX() {
            return x + sirina / 2;
        }

        public double sredinaY() {
            return y + visina / 2;
        }
    }

    /* Pas: vrsta kvadratkov ali celic z oznakama na koncih (sredisci v mm). */
    public record Pas(double[] levo, double[] desno) {}

    /* Kvadratek za krizec: tekma (indeks v razporedu), stran in vrstica kadra
       natisa (igralec == stevilo igralcev kadra je prazna vrstica »nov igralec«). */
    public record Kvadratek(int tekma, boolean domaci, int igralec, int pas, Pravokotnik p) {}

    /* Celica za tocke: tekma, niz (od 0) in stran. */
    public record Celica(int tekma, int niz, boolean domaci, int pas, Pravokotnik p) {}

    /* Mesto besedila, ki ga vmesnik napise (bralnik ga ne bere):
       NASLOV, EKIPE, EKIPA (stran), IME (stran, igralec), OZNAKA_TEKME (tekma),
       NIZ (niz), DVOPICJE, NOV (stran: crta za ime novega igralca), NAVODILO, KODA.
       kot: zasuk v stopinjah (-90 = navpicno, bere se od spodaj navzgor). */
    public record Besedilo(String vrsta, Boolean domaci, Integer indeks, Pravokotnik p, double kot) {}

    public record PostavitevZapisnika(Oblika oblika, int razlicica, double sirina, double visina,
                                      double stranicaOznake, List<double[]> oznake, Pravokotnik qr,
                                      double stranicaOznakeVrste, List<Pas> pasovi,
                                      List<Kvadratek> kvadratki, List<Celica> celice,
                                      List<Besedilo> besedila, double crta) {}

    /* Ena tekma razporeda, kot jo list potrebuje: oznaka (»A-X«, »dvojice«). */
    public record TekmaRazporeda(String oznaka, boolean dvojice) {}

    /* Postavitev ne gre na list (prevelik kader, preveliko nizov). */
    public static final class NeGreNaList extends RuntimeException {
        public NeGreNaList(String sporocilo) {
            super(sporocilo);
        }
    }

    // ---------- VRSTICE ----------

    /* Kvadratek 5,5 mm, korak 7 mm (1,5 mm razmika - velik krizec ne seze v soseda). */
    static final double KVADRATEK_VRSTICE = 5.5;
    static final double KORAK_VRSTICE = 7;
    /* Celica tock je visoka 12 mm; sirina sledi prostoru (najvec 15, najmanj 10 mm:
       dve stevki po ~4 mm). */
    static final double VISINA_CELICE_VRSTICE = 12;
    static final double NAJVEC_CELICA = 15;
    static final double NAJMANJ_CELICA = 10;
    /* Razmik med celicama niza (dvopicje) in med nizi: 3 mm, zato poteza ene
       celice (razrez gleda 1,5 mm okolice) ne seze v sosednjo. */
    static final double DVOPICJE = 3;
    static final double MED_NIZI = 3;
    static final double KORAK_VRSTE = 18;
    static final double OZNAKA_TEKME = 12;

    /* Fiksni del sirine vrste: oznaki vrste z razmikom, oznaka tekme, razmiki do tock. */
    private static double fiksnoVrstice() {
        return STRANICA_OZNAKE_VRSTE + 2 + OZNAKA_TEKME + 2 + 4 + 4 + 2 + STRANICA_OZNAKE_VRSTE;
    }

    /* Sirina celice pri danem kadru (kvadratkov skupaj) in stevilu nizov; NaN, ce
       tudi najozja celica ne gre. */
    static double celicaVrstic(int kvadratkov, int nizov) {
        double zaTocke = (DESNO - LEVO) - fiksnoVrstice() - KORAK_VRSTICE * kvadratkov;
        double c = (zaTocke + MED_NIZI) / nizov - DVOPICJE - MED_NIZI;
        c /= 2;
        if (c < NAJMANJ_CELICA) {
            return Double.NaN;
        }
        return Math.min(NAJVEC_CELICA, c);
    }

    /* Ali gre kader v obliko VRSTICE (vrstic kadra skupaj s prazno vrstico). */
    public static boolean greVVrstice(int domacih, int gostov, int nizov) {
        return !Double.isNaN(celicaVrstic(domacih + gostov, nizov));
    }

    private static PostavitevZapisnika vrstice(List<TekmaRazporeda> tekme, int nizov, int domacih, int gostov) {
        double c = celicaVrstic(domacih + gostov, nizov);
        if (Double.isNaN(c)) {
            throw new NeGreNaList("Kader je prevelik za zapisnik z vrsticami tekem. Izberi zapisnik s tabelo kadra.");
        }
        double tocke = nizov * (2 * c + DVOPICJE) + (nizov - 1) * MED_NIZI;
        double sirina = fiksnoVrstice() + KORAK_VRSTICE * (domacih + gostov) + tocke;
        double x0 = LEVO + ((DESNO - LEVO) - sirina) / 2;

        double xOznakaL = x0 + STRANICA_OZNAKE_VRSTE / 2;
        double xTekma = x0 + STRANICA_OZNAKE_VRSTE + 2;
        double xDomaci = xTekma + OZNAKA_TEKME + 2;
        double xTocke = xDomaci + KORAK_VRSTICE * domacih + 4;
        double xGost = xTocke + tocke + 4;
        double xOznakaD = xGost + KORAK_VRSTICE * gostov + 2 + STRANICA_OZNAKE_VRSTE / 2;

        List<Pas> pasovi = new ArrayList<>();
        List<Kvadratek> kvadratki = new ArrayList<>();
        List<Celica> celice = new ArrayList<>();
        List<Besedilo> besedila = new ArrayList<>(glava());

        // glava tabele: imeni ekip nad bloki kvadratkov, imena kadra navpicno, nizi
        besedila.add(new Besedilo("EKIPA", true, null, new Pravokotnik(xDomaci - 6, 29, KORAK_VRSTICE * domacih + 12, 7), 0));
        besedila.add(new Besedilo("EKIPA", false, null, new Pravokotnik(xGost - 6, 29, KORAK_VRSTICE * gostov + 12, 7), 0));
        for (int i = 0; i < domacih; i++) {
            besedila.add(new Besedilo("IME", true, i, new Pravokotnik(xDomaci + i * KORAK_VRSTICE, 37, KORAK_VRSTICE, 30), -90));
        }
        for (int i = 0; i < gostov; i++) {
            besedila.add(new Besedilo("IME", false, i, new Pravokotnik(xGost + i * KORAK_VRSTICE, 37, KORAK_VRSTICE, 30), -90));
        }
        double yNizi = 61;
        for (int s = 0; s < nizov; s++) {
            double xs = xTocke + s * (2 * c + DVOPICJE + MED_NIZI);
            besedila.add(new Besedilo("NIZ", null, s, new Pravokotnik(xs, yNizi, 2 * c + DVOPICJE, 6), 0));
        }

        double y = 70;
        for (int t = 0; t < tekme.size(); t++) {
            int pas = pasovi.size();
            double sredina = y + VISINA_CELICE_VRSTICE / 2;
            pasovi.add(new Pas(new double[] {xOznakaL, sredina}, new double[] {xOznakaD, sredina}));
            besedila.add(new Besedilo("OZNAKA_TEKME", null, t, new Pravokotnik(xTekma, y, OZNAKA_TEKME, VISINA_CELICE_VRSTICE), 0));
            double yk = sredina - KVADRATEK_VRSTICE / 2;
            double odmik = (KORAK_VRSTICE - KVADRATEK_VRSTICE) / 2;
            for (int i = 0; i < domacih; i++) {
                kvadratki.add(new Kvadratek(t, true, i, pas,
                        new Pravokotnik(xDomaci + i * KORAK_VRSTICE + odmik, yk, KVADRATEK_VRSTICE, KVADRATEK_VRSTICE)));
            }
            for (int i = 0; i < gostov; i++) {
                kvadratki.add(new Kvadratek(t, false, i, pas,
                        new Pravokotnik(xGost + i * KORAK_VRSTICE + odmik, yk, KVADRATEK_VRSTICE, KVADRATEK_VRSTICE)));
            }
            for (int s = 0; s < nizov; s++) {
                double xs = xTocke + s * (2 * c + DVOPICJE + MED_NIZI);
                celice.add(new Celica(t, s, true, pas, new Pravokotnik(xs, y, c, VISINA_CELICE_VRSTICE)));
                celice.add(new Celica(t, s, false, pas, new Pravokotnik(xs + c + DVOPICJE, y, c, VISINA_CELICE_VRSTICE)));
                besedila.add(new Besedilo("DVOPICJE", null, t, new Pravokotnik(xs + c, y, DVOPICJE, VISINA_CELICE_VRSTICE), 0));
            }
            y += KORAK_VRSTE;
        }
        // crti za ime novega igralca (prazna vrstica kadra) pod tabelo
        double yNov = y + 2;
        besedila.add(new Besedilo("NOV", true, domacih - 1, new Pravokotnik(LEVO + 2, yNov, 120, 7), 0));
        besedila.add(new Besedilo("NOV", false, gostov - 1, new Pravokotnik(LEVO + 135, yNov, 120, 7), 0));
        besedila.add(navodilo());
        return new PostavitevZapisnika(Oblika.VRSTICE, RAZLICICA, SIRINA, VISINA, STRANICA_OZNAKE,
                List.of(ZGORAJ_LEVO, SPODAJ_LEVO, SPODAJ_DESNO), qr(), STRANICA_OZNAKE_VRSTE, pasovi,
                kvadratki, celice, besedila, CRTA);
    }

    // ---------- KADER ----------

    /* Kvadratek 5,5 mm v stolpcih po 8 mm; vrstica kadra 6,5-8 mm visoka (manjsa
       pri vecjem kadru, kvadratek pod 7 mm vrstice 5 mm). */
    static final double KORAK_STOLPCA_KADRA = 8;
    static final double NAJVEC_VRSTICA_KADRA = 8;
    static final double NAJMANJ_VRSTICA_KADRA = 6;
    static final double VISINA_CELICE_KADRA = 10;
    static final double KORAK_VRSTE_TOCK = 13.5;
    static final double NAJVEC_CELICA_KADRA = 16;
    static final double MED_NIZI_KADRA = 5;

    /* Prostor za vrstice kadra (mm, navpicno) pri danem stevilu tekem. */
    private static double prostorKadra(int tekem) {
        double tabelaTock = 7 + tekem * KORAK_VRSTE_TOCK;
        double konecTock = 188;
        double zacetekTock = konecTock - tabelaTock;
        return zacetekTock - 6 - 41;
    }

    /* Ali gre kader (vrstic na stran, s prazno vrstico) v obliko KADER. */
    public static boolean greVKader(int domacih, int gostov, int tekem) {
        return Math.max(domacih, gostov) * NAJMANJ_VRSTICA_KADRA <= prostorKadra(tekem);
    }

    private static PostavitevZapisnika kader(List<TekmaRazporeda> tekme, int nizov, int domacih, int gostov) {
        int vrstic = Math.max(domacih, gostov);
        double prostor = prostorKadra(tekme.size());
        double korak = Math.min(NAJVEC_VRSTICA_KADRA, prostor / vrstic);
        if (korak < NAJMANJ_VRSTICA_KADRA) {
            throw new NeGreNaList("Kader ekipe je prevelik za zapisnik za bralnik (največ "
                    + (int) Math.floor(prostor / NAJMANJ_VRSTICA_KADRA - 1) + " igralcev na ekipo).");
        }
        double kvadratek = korak < 7 ? 5 : 5.5;

        List<Pas> pasovi = new ArrayList<>();
        List<Kvadratek> kvadratki = new ArrayList<>();
        List<Celica> celice = new ArrayList<>();
        List<Besedilo> besedila = new ArrayList<>(glava());

        // dve tabeli kadra drug ob drugem: oznaka, ime, stolpci tekem, oznaka
        double sirinaTabele = ((DESNO - LEVO) - 5) / 2;
        double stolpci = tekme.size() * KORAK_STOLPCA_KADRA;
        for (int stran = 0; stran < 2; stran++) {
            boolean domaci = stran == 0;
            int n = domaci ? domacih : gostov;
            double x0 = LEVO + stran * (sirinaTabele + 5);
            double xIme = x0 + STRANICA_OZNAKE_VRSTE + 2;
            double xStolpci = x0 + sirinaTabele - STRANICA_OZNAKE_VRSTE - 2 - stolpci;
            double sirinaImena = xStolpci - 2 - xIme;
            besedila.add(new Besedilo("EKIPA", domaci, null, new Pravokotnik(xIme, 28, sirinaTabele - 10, 7), 0));
            for (int t = 0; t < tekme.size(); t++) {
                besedila.add(new Besedilo("OZNAKA_TEKME", domaci, t,
                        new Pravokotnik(xStolpci + t * KORAK_STOLPCA_KADRA, 36, KORAK_STOLPCA_KADRA, 5), 0));
            }
            double y = 42;
            for (int i = 0; i < n; i++) {
                int pas = pasovi.size();
                double sredina = y + korak / 2;
                pasovi.add(new Pas(new double[] {x0 + STRANICA_OZNAKE_VRSTE / 2, sredina},
                        new double[] {x0 + sirinaTabele - STRANICA_OZNAKE_VRSTE / 2, sredina}));
                besedila.add(new Besedilo(i == n - 1 ? "NOV" : "IME", domaci, i,
                        new Pravokotnik(xIme, y, sirinaImena, korak), 0));
                double odmik = (KORAK_STOLPCA_KADRA - kvadratek) / 2;
                for (int t = 0; t < tekme.size(); t++) {
                    kvadratki.add(new Kvadratek(t, domaci, i, pas, new Pravokotnik(
                            xStolpci + t * KORAK_STOLPCA_KADRA + odmik, sredina - kvadratek / 2, kvadratek, kvadratek)));
                }
                y += korak;
            }
        }

        // tabela tock: vrstica na tekmo, stolpec na niz (domaci : gost)
        double c = Math.min(NAJVEC_CELICA_KADRA,
                (((DESNO - LEVO) - 26 + MED_NIZI_KADRA) / nizov - DVOPICJE - MED_NIZI_KADRA) / 2);
        if (c < NAJMANJ_CELICA) {
            throw new NeGreNaList("Preveč nizov za zapisnik za bralnik.");
        }
        double tocke = nizov * (2 * c + DVOPICJE) + (nizov - 1) * MED_NIZI_KADRA;
        double sirina = STRANICA_OZNAKE_VRSTE + 2 + 14 + 3 + tocke + 2 + STRANICA_OZNAKE_VRSTE;
        double x0 = LEVO + ((DESNO - LEVO) - sirina) / 2;
        double xTekma = x0 + STRANICA_OZNAKE_VRSTE + 2;
        double xTocke = xTekma + 14 + 3;
        double y = 188 - tekme.size() * KORAK_VRSTE_TOCK;
        for (int s = 0; s < nizov; s++) {
            double xs = xTocke + s * (2 * c + DVOPICJE + MED_NIZI_KADRA);
            besedila.add(new Besedilo("NIZ", null, s, new Pravokotnik(xs, y - 7, 2 * c + DVOPICJE, 6), 0));
        }
        for (int t = 0; t < tekme.size(); t++) {
            int pas = pasovi.size();
            double sredina = y + VISINA_CELICE_KADRA / 2;
            pasovi.add(new Pas(new double[] {x0 + STRANICA_OZNAKE_VRSTE / 2, sredina},
                    new double[] {x0 + sirina - STRANICA_OZNAKE_VRSTE / 2, sredina}));
            besedila.add(new Besedilo("OZNAKA_TEKME", null, t, new Pravokotnik(xTekma, y, 14, VISINA_CELICE_KADRA), 0));
            for (int s = 0; s < nizov; s++) {
                double xs = xTocke + s * (2 * c + DVOPICJE + MED_NIZI_KADRA);
                celice.add(new Celica(t, s, true, pas, new Pravokotnik(xs, y, c, VISINA_CELICE_KADRA)));
                celice.add(new Celica(t, s, false, pas, new Pravokotnik(xs + c + DVOPICJE, y, c, VISINA_CELICE_KADRA)));
                besedila.add(new Besedilo("DVOPICJE", null, t, new Pravokotnik(xs + c, y, DVOPICJE, VISINA_CELICE_KADRA), 0));
            }
            y += KORAK_VRSTE_TOCK;
        }
        besedila.add(navodilo());
        return new PostavitevZapisnika(Oblika.KADER, RAZLICICA, SIRINA, VISINA, STRANICA_OZNAKE,
                List.of(ZGORAJ_LEVO, SPODAJ_LEVO, SPODAJ_DESNO), qr(), STRANICA_OZNAKE_VRSTE, pasovi,
                kvadratki, celice, besedila, CRTA);
    }

    // ---------- Skupno ----------

    private static List<Besedilo> glava() {
        return List.of(
                new Besedilo("NASLOV", null, null, new Pravokotnik(LEVO + 2, 5, 236, 9), 0),
                new Besedilo("EKIPE", null, null, new Pravokotnik(LEVO + 2, 14, 236, 11), 0),
                new Besedilo("KODA", null, null, new Pravokotnik(QR_X, QR_Y + QR_STRANICA + 0.5, QR_STRANICA, 3.5), 0));
    }

    private static Besedilo navodilo() {
        return new Besedilo("NAVODILO", null, null, new Pravokotnik(LEVO + 2, 191, (DESNO - LEVO) - 4, 10), 0);
    }

    private static Pravokotnik qr() {
        return new Pravokotnik(QR_X, QR_Y, QR_STRANICA, QR_STRANICA);
    }

    /* Postavitev zapisnika. domacih/gostov = vrstic kadra, VKLJUCNO s prazno vrstico
       za novega igralca (zadnja). */
    public static PostavitevZapisnika izracunaj(int razlicica, Oblika oblika, List<TekmaRazporeda> tekme, int nizov,
                                                int domacih, int gostov) {
        if (razlicica != RAZLICICA) {
            throw new IllegalArgumentException("Neznana razlicica postavitve zapisnika: " + razlicica);
        }
        if (tekme.isEmpty() || nizov < 1 || domacih < 1 || gostov < 1) {
            throw new IllegalArgumentException("Zapisnik potrebuje tekme, nize in kader.");
        }
        return oblika == Oblika.VRSTICE ? vrstice(tekme, nizov, domacih, gostov) : kader(tekme, nizov, domacih, gostov);
    }

    /* Samodejna izbira oblike: vrstice tekem, kadar gre kader nanje (lastnik:
       »nacin 2 ima ob vecjih kadrih resne probleme, takrat raje nacin 1«); sicer
       tabela kadra; null, ce ne gre nobena. */
    public static Oblika samodejna(int domacih, int gostov, int nizov, int tekem) {
        if (greVVrstice(domacih, gostov, nizov)) {
            return Oblika.VRSTICE;
        }
        if (greVKader(domacih, gostov, tekem)) {
            return Oblika.KADER;
        }
        return null;
    }

    private GeometrijaZapisnika() {}
}
