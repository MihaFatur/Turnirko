/* Kako pogosti so izidi nizov v resnicnih tekmah (F2.1): koliko tock ima
   porazenec pri 11 in kako dolgo traja podaljsek.

   Mreza stevk med 11:1 in 11:7 ali med 13:11 in 18:16 vcasih ne loci; zgodovina
   pove, da je 11:7 dvanajstkrat pogostejsi od 11:1 in 18:16 skoraj trideset-
   krat redkejsi od 13:11. RazlagaListka zato vsak veljaven niz utezi s
   (P(niz) / P_povprecni_11)^beta: beta 0 = brez zgodovine (vsi nizi enako
   verjetni), beta 1 = polna zgodovina. Privzeta beta 0,5 je polovicna, ker je
   zgodovina povprecje vseh igralcev, listek pa je ena tekma (in ker je napacna
   samozavestna izbira slabsa od negotove celice).

   Stetje se zgladi z vgrajeno obliko (PSEVDO_STEVILO navideznih nizov), da
   redek, a mogoc izid (11:0, 25:23) nikoli ne dobi verjetnosti 0. */
package si.turnirko.branje;

public final class PogostostNizov {

    /* Najdaljsi podaljsek, ki ga RazlagaListka se pozna (40:38). */
    public static final int NAJVEC_PODALJSEK = 40;
    public static final double PRIVZETA_BETA = 0.5;

    /* Navidezni nizi vgrajene oblike pri glajenju. */
    private static final double PSEVDO_STEVILO = 100;

    /* Vgrajena porazdelitev: niz v kopiji produkcijske baze 20. 11. 2026
       (200.287 nizov, 54.555 tekem). Porazenceve tocke pri 11 (0-9) in stevilo
       nizov s podaljskom od 12:10 do 28:26. Velja, dokler baza nima dovolj
       lastnih nizov (PogostostNizovStoritev). */
    private static final long[] VGRAJENO_11 = {1178, 4215, 8360, 13140, 18454, 22739, 25644, 27478, 27103, 27081};
    private static final long[] VGRAJENO_PODALJSEK = {12468, 6295, 3063, 1566, 720, 371, 211, 105, 51, 25, 6, 7, 2, 2, 0, 1, 1};

    public static final PogostostNizov VGRAJENA = new PogostostNizov(VGRAJENO_11, VGRAJENO_PODALJSEK);

    /* p11[a] = P(11:a), podaljsek[k] = P(k:k-2) za k 12..NAJVEC_PODALJSEK (ne glede na stran). */
    private final double[] p11 = new double[10];
    private final double[] podaljsek = new double[NAJVEC_PODALJSEK + 1];
    private final long nizov;

    /* stevilo11[a] = nizi 11:a; stevilaPodaljskov[i] = nizi (12+i):(10+i). */
    public PogostostNizov(long[] stevilo11, long[] stevilaPodaljskov) {
        double[] oblika11 = new double[10];
        double[] oblikaPodaljska = new double[NAJVEC_PODALJSEK + 1];
        obliko(oblika11, oblikaPodaljska);
        long n = 0;
        for (long s : stevilo11) {
            n += s;
        }
        for (long s : stevilaPodaljskov) {
            n += s;
        }
        this.nizov = n;
        double imenovalec = n + PSEVDO_STEVILO;
        for (int a = 0; a < 10; a++) {
            long s = a < stevilo11.length ? stevilo11[a] : 0;
            p11[a] = (s + PSEVDO_STEVILO * oblika11[a]) / imenovalec;
        }
        for (int k = 12; k <= NAJVEC_PODALJSEK; k++) {
            long s = k - 12 < stevilaPodaljskov.length ? stevilaPodaljskov[k - 12] : 0;
            podaljsek[k] = (s + PSEVDO_STEVILO * oblikaPodaljska[k]) / imenovalec;
        }
    }

    /* Vgrajena oblika za glajenje: 11:a enakomerno (7/8 vseh nizov), podaljsek
       (1/8) se z vsakim parom tock razpolovi. */
    private static void obliko(double[] oblika11, double[] oblikaPodaljska) {
        for (int a = 0; a < 10; a++) {
            oblika11[a] = 0.875 / 10;
        }
        double vsota = 0;
        for (int k = 12; k <= NAJVEC_PODALJSEK; k++) {
            vsota += Math.pow(0.5, k - 12);
        }
        for (int k = 12; k <= NAJVEC_PODALJSEK; k++) {
            oblikaPodaljska[k] = 0.125 * Math.pow(0.5, k - 12) / vsota;
        }
    }

    public long nizov() {
        return nizov;
    }

    /* Verjetnost veljavnega niza (zmagovalec : porazenec); 0 za neveljaven niz. */
    public double verjetnost(int zmagovalec, int porazenec) {
        if (zmagovalec == 11 && porazenec >= 0 && porazenec <= 9) {
            return p11[porazenec];
        }
        if (zmagovalec >= 12 && zmagovalec <= NAJVEC_PODALJSEK && porazenec == zmagovalec - 2) {
            return podaljsek[zmagovalec];
        }
        return 0;
    }

    /* Utez niza za RazlagaListka: (P(niz) / povprecni P(11:a))^beta. Merilo je
       izbrano tako, da so utezi nizov 11:a okoli 1 kot pred zgodovino, zato
       ostane razmerje do prazne vrstice (nepopolna tekma) enako. */
    public double utez(int zmagovalec, int porazenec, double beta) {
        if (beta == 0) {
            return 1;
        }
        double povprecje = 0;
        for (double p : p11) {
            povprecje += p / 10;
        }
        return Math.pow(verjetnost(zmagovalec, porazenec) / povprecje, beta);
    }
}
