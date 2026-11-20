/* Poravnava fotografije listka: najde tri oznake v vogalih (in QR kodo, ce jo
   klicatelj da) ter listek preslika v stalne koordinate - 10 px na mm, ne glede
   na to, kako je telefon drzal organizator.

   Oznake so trije polni crni kvadrati v obliki crke L (zgoraj levo, spodaj levo,
   spodaj desno). Iz treh tock je mogoca afina preslikava (zasuk, merilo, nagib);
   cetrta tocka - iskalni vzorec QR zgoraj desno - doda se perspektivo telefona,
   ki listka ne snema natanko od zgoraj. Brez QR (prazen listek) ostane afina.

   Katera oznaka je katera, pove geometrija, ne lega na sliki: oglisce L-ja je
   tocka s pravim kotom, smer pa doloci predznak vektorskega produkta (fotografija
   ni zrcaljena). Zato listek prebere tudi narobe obrnjeno fotografijo. */
package si.turnirko.branje;

import java.awt.image.BufferedImage;
import java.util.Comparator;
import java.util.List;

public final class PoravnavaListka {

    /* Razdalji med oznakami v mm (oglisce spodaj levo). */
    private static final double DOLZINA_NAVPIK = GeometrijaListka.SPODAJ_LEVO[1] - GeometrijaListka.ZGORAJ_LEVO[1];
    private static final double DOLZINA_VODORAVNO = GeometrijaListka.SPODAJ_DESNO[0] - GeometrijaListka.SPODAJ_LEVO[0];
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

        Temnost.Komponenta[] oznake = izberiOznake(kandidati, qrTocke);
        if (oznake == null) {
            throw new NapakaPoravnave("Na fotografiji ni mogoče najti oznak v vogalih listka. "
                    + "Fotografiraj cel listek od zgoraj, da se vidijo trije črni kvadrati.");
        }
        double[][] slika = {
                {oznake[0].cx(), oznake[0].cy()},
                {oznake[1].cx(), oznake[1].cy()},
                {oznake[2].cx(), oznake[2].cy()}};
        double[][] listek = {GeometrijaListka.ZGORAJ_LEVO, GeometrijaListka.SPODAJ_LEVO, GeometrijaListka.SPODAJ_DESNO};

        Preslikava afina = Preslikava.afina(listek, slika);
        Preslikava preslikava = afina;
        boolean perspektiva = false;
        if (qrTocke != null && afina != null) {
            double merilo = merilo(slika);
            double[] napoved = afina.uporabi(GeometrijaListka.QR_ZGORAJ_DESNO[0], GeometrijaListka.QR_ZGORAJ_DESNO[1]);
            // QR je ista koda samo, ce lezi priblizno tam, kjer jo listek predvideva (sicer je
            // tuja). Afina napoved se pri nagnjenem telefonu zmoti za nekaj mm, zato meja 15 mm.
            if (razdalja(napoved, qrTocke[2]) < 15 * merilo) {
                Preslikava h = Preslikava.homografija(
                        new double[][] {GeometrijaListka.ZGORAJ_LEVO, GeometrijaListka.SPODAJ_LEVO,
                                GeometrijaListka.SPODAJ_DESNO, GeometrijaListka.QR_ZGORAJ_DESNO},
                        new double[][] {slika[0], slika[1], slika[2], qrTocke[2]});
                if (h != null && soglasa(h, qrTocke, merilo)) {
                    preslikava = h;
                    perspektiva = true;
                }
            }
        }
        if (preslikava == null) {
            throw new NapakaPoravnave("Listka na fotografiji ni mogoče poravnati. Fotografiraj ga znova.");
        }
        return new PoravnanListek(prerisi(d, preslikava), perspektiva);
    }

    /* Trojica oznak (zgoraj levo, spodaj levo, spodaj desno), ki najbolje ustreza
       obliki L listka; null, ce je ni. */
    static Temnost.Komponenta[] izberiOznake(List<Temnost.Komponenta> kandidati, double[][] qrTocke) {
        Temnost.Komponenta[] najboljse = null;
        double najmanjsaNapaka = Double.MAX_VALUE;
        double pricakovanoRazmerje = DOLZINA_NAVPIK / DOLZINA_VODORAVNO;
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
                    // velikost oznake mora ustrezati razdalji med njimi (4 mm proti 85 mm)
                    double pxNaMm = (lu / DOLZINA_NAVPIK + lv / DOLZINA_VODORAVNO) / 2;
                    double pricakovanaPloscina = Math.pow(GeometrijaListka.STRANICA_OZNAKE * pxNaMm, 2);
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
                        Preslikava p = Preslikava.afina(
                                new double[][] {GeometrijaListka.ZGORAJ_LEVO, GeometrijaListka.SPODAJ_LEVO,
                                        GeometrijaListka.SPODAJ_DESNO},
                                new double[][] {{zl.cx(), zl.cy()}, {sl.cx(), sl.cy()}, {sd.cx(), sd.cy()}});
                        if (p != null) {
                            double[] q = p.uporabi(GeometrijaListka.QR_ZGORAJ_DESNO[0], GeometrijaListka.QR_ZGORAJ_DESNO[1]);
                            napaka += razdalja(q, qrTocke[2]) / pxNaMm / 10;
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

    /* Homografija velja, ce napove tudi ostala iskalna vzorca QR (sicer je bila
       tocka zgoraj desno prebrana narobe). */
    private static boolean soglasa(Preslikava h, double[][] qrTocke, double merilo) {
        double[] spodajLevo = h.uporabi(GeometrijaListka.QR_SPODAJ_LEVO[0], GeometrijaListka.QR_SPODAJ_LEVO[1]);
        double[] zgorajLevo = h.uporabi(GeometrijaListka.QR_ZGORAJ_LEVO[0], GeometrijaListka.QR_ZGORAJ_LEVO[1]);
        return razdalja(spodajLevo, qrTocke[0]) < 3 * merilo && razdalja(zgorajLevo, qrTocke[1]) < 3 * merilo;
    }

    /* px na mm iz razdalj med oznakami. */
    private static double merilo(double[][] oznake) {
        return (razdalja(oznake[0], oznake[1]) / DOLZINA_NAVPIK + razdalja(oznake[1], oznake[2]) / DOLZINA_VODORAVNO) / 2;
    }

    private static double razdalja(double[] a, double[] b) {
        return Math.hypot(a[0] - b[0], a[1] - b[1]);
    }

    private static Temnost prerisi(Temnost vir, Preslikava listekVSliko) {
        int r = GeometrijaListka.PX_NA_MM;
        Temnost cilj = new Temnost((int) Math.round(GeometrijaListka.SIRINA * r),
                (int) Math.round(GeometrijaListka.VISINA * r));
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
