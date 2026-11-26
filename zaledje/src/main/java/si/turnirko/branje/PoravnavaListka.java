/* Poravnava fotografije listka: najde tri oznake v vogalih (in QR kodo, ce jo
   klicatelj da) ter listek preslika v stalne koordinate - 10 px na mm, ne glede
   na to, kako je telefon drzal organizator.

   Oznake so trije polni crni kvadrati v obliki crke L (zgoraj levo, spodaj levo,
   spodaj desno). Iz treh tock je mogoca afina preslikava (zasuk, merilo, nagib);
   cetrta tocka - iskalni vzorec QR zgoraj desno - doda se perspektivo telefona,
   ki listka ne snema natanko od zgoraj. Brez QR (prazen listek) ostane afina.

   Katera oznaka je katera, pove geometrija, ne lega na sliki: oglisce L-ja je
   tocka s pravim kotom, smer pa doloci predznak vektorskega produkta (fotografija
   ni zrcaljena). Zato listek prebere tudi narobe obrnjeno fotografijo.

   Ista poravnava velja za list vadnice stevk; mesta oznak in QR da Postavitev. */
package si.turnirko.branje;

import java.awt.image.BufferedImage;
import java.util.Comparator;
import java.util.List;

public final class PoravnavaListka {

    private static final int NAJVEC_KANDIDATOV = 24;

    private PoravnavaListka() {}

    /* Listek v stalnih koordinatah: temnost GeometrijaListka.PX_NA_MM px/mm. */
    public record PoravnanListek(Temnost slika, boolean perspektiva) {}

    /* Napaka poravnave: na fotografiji ni listka ali oznake niso vidne. */
    public static final class NapakaPoravnave extends RuntimeException {
        public NapakaPoravnave(String sporocilo) {
            super(sporocilo);
        }
    }

    /* qrTocke: sredisca iskalnih vzorcev [spodaj levo, zgoraj levo, zgoraj desno]
       ali null, ce QR kode ni. */
    public static PoravnanListek poravnaj(BufferedImage fotografija, double[][] qrTocke) {
        return poravnaj(fotografija, qrTocke, Postavitev.LISTEK);
    }

    public static PoravnanListek poravnaj(BufferedImage fotografija, double[][] qrTocke, Postavitev p) {
        PoravnavaSPreslikavo s = poravnajSPreslikavo(fotografija, qrTocke, p);
        return new PoravnanListek(s.slika(), s.perspektiva());
    }

    /* Poravnan list ter preslikava (mm lista -> px fotografije) in temnost fotografije:
       ligaski zapisnik (A4) poravnavo izboljsa se po oznakah vrst in list preslika
       znova iz fotografije (OdcitekZapisnika.izboljsaj), brez dvojnega vzorcenja. */
    public record PoravnavaSPreslikavo(Temnost slika, boolean perspektiva, Preslikava preslikava,
                                       Temnost fotografija) {}

    public static PoravnavaSPreslikavo poravnajSPreslikavo(BufferedImage fotografija, double[][] qrTocke, Postavitev p) {
        Temnost d = Temnost.iz(fotografija);
        int okno = Math.max(15, Math.max(d.sirina, d.visina) / 20);
        boolean[] maska = d.crnilo(okno, 0.12f, 0.2f);
        double najmanjsaStranica = Math.min(d.sirina, d.visina);
        double najvecPloscina = Math.pow(najmanjsaStranica / 8, 2);

        List<Temnost.Komponenta> kandidati = Temnost.komponente(maska, d.sirina, d.visina, false).stream()
                .filter(k -> k.ploscina >= 80 && k.ploscina <= najvecPloscina)
                .filter(Temnost.Komponenta::jePolnKvadrat)
                .sorted(Comparator.comparingInt((Temnost.Komponenta k) -> k.ploscina).reversed())
                .limit(NAJVEC_KANDIDATOV)
                .toList();

        Temnost.Komponenta[] oznake = izberiOznake(kandidati, qrTocke, p);
        if (oznake == null) {
            // ena oznaka je zakrita (prst cez vogal) ali odrezana: QR + preostali dve (F2.6)
            Preslikava rezervna = qrTocke == null ? null : rezervna(kandidati, qrTocke, p);
            if (rezervna == null) {
                throw new NapakaPoravnave("Na fotografiji ni mogoče najti oznak v vogalih listka. "
                        + "Fotografiraj cel listek od zgoraj, da se vidijo trije črni kvadrati.");
            }
            return new PoravnavaSPreslikavo(prerisi(d, rezervna, p), true, rezervna, d);
        }
        double[][] slika = {
                {oznake[0].cx(), oznake[0].cy()},
                {oznake[1].cx(), oznake[1].cy()},
                {oznake[2].cx(), oznake[2].cy()}};
        double[][] listek = {p.zgorajLevo(), p.spodajLevo(), p.spodajDesno()};

        Preslikava afina = Preslikava.afina(listek, slika);
        Preslikava preslikava = afina;
        boolean perspektiva = false;
        if (qrTocke != null && afina != null) {
            double merilo = merilo(slika, p);
            double[] qrZd = p.qrZgorajDesno();
            double[] napoved = afina.uporabi(qrZd[0], qrZd[1]);
            // QR je ista koda samo, ce lezi priblizno tam, kjer jo list predvideva (sicer je
            // tuja). Afina napoved se pri nagnjenem telefonu zmoti za nekaj mm (Postavitev).
            if (razdalja(napoved, qrTocke[2]) < p.najvecOdmikQr() * merilo) {
                Preslikava h = Preslikava.homografija(
                        new double[][] {p.zgorajLevo(), p.spodajLevo(), p.spodajDesno(), qrZd},
                        new double[][] {slika[0], slika[1], slika[2], qrTocke[2]});
                if (h != null && soglasa(h, qrTocke, merilo, p)) {
                    preslikava = h;
                    perspektiva = true;
                }
            }
        }
        if (preslikava == null) {
            throw new NapakaPoravnave("Listka na fotografiji ni mogoče poravnati. Fotografiraj ga znova.");
        }
        return new PoravnavaSPreslikavo(prerisi(d, preslikava, p), perspektiva, preslikava, d);
    }

    /* Trojica oznak (zgoraj levo, spodaj levo, spodaj desno), ki najbolje ustreza
       obliki L lista; null, ce je ni. */
    static Temnost.Komponenta[] izberiOznake(List<Temnost.Komponenta> kandidati, double[][] qrTocke, Postavitev p) {
        Temnost.Komponenta[] najboljse = null;
        double najmanjsaNapaka = Double.MAX_VALUE;
        double dolzinaNavpik = dolzinaNavpik(p);
        double dolzinaVodoravno = dolzinaVodoravno(p);
        double pricakovanoRazmerje = dolzinaNavpik / dolzinaVodoravno;
        double[] qrZd = p.qrZgorajDesno();
        int n = kandidati.size();
        for (int b = 0; b < n; b++) {
            Temnost.Komponenta sl = kandidati.get(b);
            for (int a = 0; a < n; a++) {
                if (a == b) {
                    continue;
                }
                Temnost.Komponenta zl = kandidati.get(a);
                double ux = zl.cx() - sl.cx();
                double uy = zl.cy() - sl.cy();
                double lu = Math.hypot(ux, uy);
                for (int c = 0; c < n; c++) {
                    if (c == a || c == b) {
                        continue;
                    }
                    Temnost.Komponenta sd = kandidati.get(c);
                    double vx = sd.cx() - sl.cx();
                    double vy = sd.cy() - sl.cy();
                    double lv = Math.hypot(vx, vy);
                    if (lu < 1 || lv < 1) {
                        continue;
                    }
                    double kos = (ux * vx + uy * vy) / (lu * lv);
                    double vektorski = ux * vy - uy * vx;
                    double razmerje = (lu / lv) / pricakovanoRazmerje;
                    if (Math.abs(kos) > 0.3 || vektorski <= 0 || Math.abs(razmerje - 1) > 0.2) {
                        continue;
                    }
                    // velikost oznake mora ustrezati razdalji med njimi (na listku 4 mm proti 85 mm)
                    double pxNaMm = (lu / dolzinaNavpik + lv / dolzinaVodoravno) / 2;
                    double pricakovanaPloscina = Math.pow(p.stranicaOznake() * pxNaMm, 2);
                    double napaka = Math.abs(kos) + Math.abs(razmerje - 1);
                    boolean ustrezne = true;
                    for (Temnost.Komponenta k : new Temnost.Komponenta[] {zl, sl, sd}) {
                        double r = k.ploscina / pricakovanaPloscina;
                        if (r < 0.3 || r > 2.5) {
                            ustrezne = false;
                            break;
                        }
                        napaka += 0.2 * Math.abs(Math.log(r));
                    }
                    if (!ustrezne) {
                        continue;
                    }
                    if (qrTocke != null) {
                        Preslikava afina = Preslikava.afina(
                                new double[][] {p.zgorajLevo(), p.spodajLevo(), p.spodajDesno()},
                                new double[][] {{zl.cx(), zl.cy()}, {sl.cx(), sl.cy()}, {sd.cx(), sd.cy()}});
                        if (afina != null) {
                            double[] q = afina.uporabi(qrZd[0], qrZd[1]);
                            double odmik = razdalja(q, qrTocke[2]) / pxNaMm;
                            // trojica, ki ne ustreza QR, je napacna (npr. prst cez
                            // oznako, tretji »kvadrat« je nekaj drugega): rezervna poravnava
                            if (odmik > p.najvecOdmikQr()) {
                                continue;
                            }
                            napaka += odmik / 10;
                        }
                    }
                    if (napaka < najmanjsaNapaka) {
                        najmanjsaNapaka = napaka;
                        najboljse = new Temnost.Komponenta[] {zl, sl, sd};
                    }
                }
            }
        }
        return najboljse;
    }

    /* Rezervna poravnava, ko trojice oznak ni: trije iskalni vzorci QR so tocke na
       znanih mestih. Afina preslikava iz njih napove, kje so oznake (QR je majhen,
       zato je napoved na drugem koncu lista netocna za nekaj mm - isce se v
       sirokem krogu); ob napovedi se poiscejo oznake. Z vsaj dvema oznakama je tock
       pet, dve od njih dalec od QR, in homografija po najmanjsih kvadratih se mora
       z vsemi ujemati na NAJVEC_ODSTOPANJE mm (sicer je bila izbrana napacna
       »oznaka«). null, ce poravnava ni zanesljiva. */
    static Preslikava rezervna(List<Temnost.Komponenta> kandidati, double[][] qrTocke, Postavitev p) {
        double[][] qrMm = {p.qrSpodajLevo(), p.qrZgorajLevo(), p.qrZgorajDesno()};
        Preslikava izQr = Preslikava.afina(qrMm, qrTocke);
        if (izQr == null) {
            return null;
        }
        double pxNaMm = (razdalja(qrTocke[0], qrTocke[1]) + razdalja(qrTocke[1], qrTocke[2]))
                / (razdalja(qrMm[0], qrMm[1]) + razdalja(qrMm[1], qrMm[2]));
        double pricakovanaPloscina = Math.pow(p.stranicaOznake() * pxNaMm, 2);
        List<double[]> izMm = new java.util.ArrayList<>(List.of(qrMm));
        List<double[]> vSliko = new java.util.ArrayList<>(List.of(qrTocke));
        List<Temnost.Komponenta> uporabljene = new java.util.ArrayList<>();
        for (double[] mm : new double[][] {p.zgorajLevo(), p.spodajLevo(), p.spodajDesno()}) {
            double[] napoved = izQr.uporabi(mm[0], mm[1]);
            Temnost.Komponenta najblizja = null;
            double najmanj = ISKANJE_OZNAKE * razdalja(mm, p.qrZgorajLevo()) * pxNaMm;
            for (Temnost.Komponenta k : kandidati) {
                double r = k.ploscina / pricakovanaPloscina;
                double dd = Math.hypot(k.cx() - napoved[0], k.cy() - napoved[1]);
                if (r >= 0.3 && r <= 2.5 && dd < najmanj && !uporabljene.contains(k)) {
                    najmanj = dd;
                    najblizja = k;
                }
            }
            if (najblizja != null) {
                uporabljene.add(najblizja);
                izMm.add(mm);
                vSliko.add(new double[] {najblizja.cx(), najblizja.cy()});
            }
        }
        if (uporabljene.size() < 2) {
            return null;
        }
        double[][] iz = izMm.toArray(new double[0][]);
        double[][] v = vSliko.toArray(new double[0][]);
        Preslikava h = Preslikava.homografijaNajmanjsihKvadratov(iz, v);
        if (h == null) {
            return null;
        }
        for (int i = 0; i < iz.length; i++) {
            if (razdalja(h.uporabi(iz[i][0], iz[i][1]), v[i]) > NAJVEC_ODSTOPANJE * pxNaMm) {
                return null;
            }
        }
        return h;
    }

    /* Polmer iskanja oznake ob afini napovedi iz QR: delez razdalje od QR (napoved
       iz majhne kode se z razdaljo in perspektivo telefona slabsa). */
    private static final double ISKANJE_OZNAKE = 0.25;
    /* Najvecje odstopanje tocke od rezervne homografije (mm). */
    private static final double NAJVEC_ODSTOPANJE = 1.0;

    /* Homografija velja, ce napove tudi ostala iskalna vzorca QR (sicer je bila
       tocka zgoraj desno prebrana narobe). */
    private static boolean soglasa(Preslikava h, double[][] qrTocke, double merilo, Postavitev p) {
        double[] sl = p.qrSpodajLevo();
        double[] zl = p.qrZgorajLevo();
        double[] spodajLevo = h.uporabi(sl[0], sl[1]);
        double[] zgorajLevo = h.uporabi(zl[0], zl[1]);
        return razdalja(spodajLevo, qrTocke[0]) < 3 * merilo && razdalja(zgorajLevo, qrTocke[1]) < 3 * merilo;
    }

    /* Razdalji med oznakami v mm (oglisce spodaj levo). */
    private static double dolzinaNavpik(Postavitev p) {
        return p.spodajLevo()[1] - p.zgorajLevo()[1];
    }

    private static double dolzinaVodoravno(Postavitev p) {
        return p.spodajDesno()[0] - p.spodajLevo()[0];
    }

    /* px na mm iz razdalj med oznakami. */
    private static double merilo(double[][] oznake, Postavitev p) {
        return (razdalja(oznake[0], oznake[1]) / dolzinaNavpik(p)
                + razdalja(oznake[1], oznake[2]) / dolzinaVodoravno(p)) / 2;
    }

    private static double razdalja(double[] a, double[] b) {
        return Math.hypot(a[0] - b[0], a[1] - b[1]);
    }

    public static Temnost prerisi(Temnost vir, Preslikava listekVSliko, Postavitev postavitev) {
        int r = GeometrijaListka.PX_NA_MM;
        Temnost cilj = new Temnost((int) Math.round(postavitev.sirina() * r),
                (int) Math.round(postavitev.visina() * r));
        for (int y = 0; y < cilj.visina; y++) {
            for (int x = 0; x < cilj.sirina; x++) {
                double[] p = listekVSliko.uporabi((x + 0.5) / r, (y + 0.5) / r);
                // tezisca oznak so v istem sistemu (sredisce tocke x je pri celem x)
                cilj.t[y * cilj.sirina + x] = vir.vzorec(p[0], p[1]);
            }
        }
        return cilj;
    }
}
