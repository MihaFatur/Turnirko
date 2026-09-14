/* Cisti izracun Turnirko ratinga - brez dostopa do baze, zato ga je enostavno
   testirati. Osnova je standardna sahovska formula (logisticna, delitelj 400),
   s stirimi izboljsavami:

   1) K PO NEGOTOVOSTI: osnovni K je 40, nanj pa se pristejejo pribitki, dokler
      o igralcu vemo premalo - manj kot 30 tekem, manj kot 10 tekem in prvih 15
      tekem po vec kot letu dni odsotnosti. Vsak igralec ima torej svoj K in nov
      ali vrnjen igralec hitreje najde svojo raven.

   2) NICVSOTNO ZAOKROZEVANJE: uporabljamo Math.rint (zaokrozi pol na sodo), ne
      Math.round (pol navzgor). Ko imata igralca isti K, gubitnik izgubi natanko
      toliko, kot zmagovalec pridobi (vsota sprememb je 0) - tudi pri izenacenem
      izidu. Math.round bi ob lihem K ob vsakem izenacenju vbrizgal +1 tocko.

   3) SET-MARGINA PO PRESENECENJU: prepricljivejsa zmaga prinese vec tock, a
      merjeno glede na PRICAKOVANO razliko. Favorit, ki gladko zmaga, je to
      pricakovano (majhen bonus); avtsajder, ki gladko zmaga, je presenetljiv
      (velik bonus). To zamenja prejsnjo surovo razliko nizov.

   4) PRI PREDAJI MARGINE NI: predana tekma ni bila odigrana do konca, zato njen
      delni izid ne pove tega, kar model po nizih predpostavlja.

   Vrednosti parametrov niso ugibane: umerjene so na 91.741 pravih tekmah iz
   razvojne baze po metodi "napovej, nato posodobi" (prequential log-loss).
   Optimum podatkov je bil K 56 / moc margine 1,5; izbrana je vmesna razlicica
   (K 40 + pribitki, moc 1,25), ker daje skoraj enako napoved z mirnejsimi
   skoki. Prejsnje vrednosti (48/32/20, moc 0,5) so napovedovale opazno slabse. */
package si.turnirko.storitve;

import org.springframework.stereotype.Service;

@Service
public class TurnirkoRatingStoritev {

    /* Rating, s katerim zacne nov igralec (ce mu admin ne postavi drugacnega). */
    public static final int ZACETNI_RATING = 1000;

    /* Meji za postavitveni (zacetni) rating, ki ga sme dolociti admin. */
    public static final int NAJNIZJI_ZACETNI = 100;
    public static final int NAJVISJI_ZACETNI = 3000;

    /* Rating nikoli ne pade pod to mejo. */
    private static final int SPODNJA_MEJA = 100;

    /* K ustaljenega igralca in pribitki za negotovost (sestevajo se). */
    public static final int K_OSNOVNI = 40;
    public static final int K_PRIBITEK_NEUSTALJEN = 8; // < PRAG_USTALJEN tekem
    public static final int K_PRIBITEK_NOVINEC = 8;    // < PRAG_PROVIZORICNI tekem
    public static final int K_PRIBITEK_VRNITEV = 8;    // prvih TEKEM_PO_VRNITVI po odsotnosti
    public static final int PRAG_PROVIZORICNI = 10;
    public static final int PRAG_USTALJEN = 30;

    /* Vrnitev: po koliko mesecih brez tekme velja igralec za odsotnega in
       koliko tekem po vrnitvi ima se povisan K. Igralec se v letu dni lahko
       precej spremeni (forma, rast mladinca, premor po poskodbi), zato mu damo
       priloznost, da se hitreje vrne na svojo raven. */
    public static final int MESECEV_ZA_VRNITEV = 12;
    public static final int TEKEM_PO_VRNITVI = 15;

    /* Skala modela po nizih za oceno pricakovane razlike. Umerjena tako, da je
       inducirana verjetnost zmage na tekmo blizu rating 400-logistiki. */
    private static final double SET_SKALA = 800.0;

    /* Moc set-margine in meji, cez kateri ne gre. Meji sta nujni: brez njiju bi
       pri velikih razlikah v ratingu mnozitelj podivjal, presenetljivo gladka
       zmaga pa bi enkratno tekmo sprevrgla v skok cez pol lestvice. */
    private static final double MARGINA_MOC = 1.25;
    private static final double MARGINA_NAJMANJ = 0.35;
    private static final double MARGINA_NAJVEC = 1.65;

    /* Vse o enem igralcu, kar vpliva na velikost njegovega K. Zapis (namesto
       treh locenih parametrov) zato, ker se sicer ob klicu prevec zaporednih
       stevil in zastavic pomesa. */
    public record StanjeIgralca(int rating, int stTekem, boolean poVrnitvi) {
        public static StanjeIgralca ustaljeno(int rating, int stTekem) {
            return new StanjeIgralca(rating, stTekem, false);
        }
    }

    /* Rezultat izracuna za obe strani tekme, skupaj s SESTAVINAMI, iz katerih
       je sprememba nastala: sprememba = K x margina x teza x (izid -
       pricakovano). Sestavine gredo v dnevnik, da zna vrstica pozneje razloziti
       svojo stevilko - poznejsi izracun bi dal danasnje parametre za staro
       tekmo in bi lahko protislovil zapisani spremembi.

       K in pricakovano sta last IGRALCA (vsak ima svoj K; pricakovani izid
       drugega je 1 minus prvega), margina in teza pa last TEKME. */
    public record Izracun(
            int ratingPrej1, int ratingPrej2,
            int ratingPo1, int ratingPo2,
            int sprememba1, int sprememba2,
            double pricakovanaVerjetnost1,
            int k1, int k2,
            double margina, double teza
    ) {}

    /* Izracuna nova ratinga po odigrani tekmi.
       stTekem1/stTekem2 sta stevili ze odigranih ratinskih tekem posameznega
       igralca (dolocata njegov K). zmagalPrvi pove zmagovalca eksplicitno (ne iz
       nizov), ker je tekma lahko koncana tudi s predajo pri izenacenih nizih. */
    public Izracun izracunaj(int rating1, int rating2, int stTekem1, int stTekem2,
                                boolean zmagalPrvi, int nizi1, int nizi2, int steviloNizov) {
        return izracunaj(rating1, rating2, stTekem1, stTekem2, zmagalPrvi,
                nizi1, nizi2, steviloNizov, false);
    }

    /* Razlicica, ki ve tudi, ali je bila tekma PREDANA.

       Predana tekma ni bila odigrana do konca, zato njen delni izid ne pove
       tega, kar model po nizih predpostavlja: kdor preda pri vodstvu 2:0, je
       zmagovalcu pustil nic dobljenih nizov, kar bi margina brala kot gladko
       razbitje in zmagovalcu pripisala najvisji mozni bonus. Pri predaji zato
       margine ne uporabimo (mnozitelj 1) - steje samo, kdo je zmagal. */
    public Izracun izracunaj(int rating1, int rating2, int stTekem1, int stTekem2,
                                boolean zmagalPrvi, int nizi1, int nizi2, int steviloNizov,
                                boolean predaja) {
        return izracunaj(StanjeIgralca.ustaljeno(rating1, stTekem1),
                StanjeIgralca.ustaljeno(rating2, stTekem2),
                zmagalPrvi, nizi1, nizi2, steviloNizov, predaja);
    }

    public Izracun izracunaj(StanjeIgralca prvi, StanjeIgralca drugi, boolean zmagalPrvi,
                                int nizi1, int nizi2, int steviloNizov, boolean predaja) {
        return izracunaj(prvi, drugi, zmagalPrvi, nizi1, nizi2, steviloNizov, predaja, 1.0);
    }

    /* Polni izracun: poleg ratinga in stevila tekem pozna se zastavico vrnitve
       za vsakega igralca posebej (K je last igralca, ne tekme) in TEZO
       tekmovanja (RavenTekmovanja.getTeza). Teza je last tekme, zato je enaka
       za oba - vsota sprememb pri enakem K je se vedno 0. */
    public Izracun izracunaj(StanjeIgralca prvi, StanjeIgralca drugi, boolean zmagalPrvi,
                                int nizi1, int nizi2, int steviloNizov, boolean predaja,
                                double teza) {
        if (nizi1 < 0 || nizi2 < 0) {
            throw new IllegalArgumentException("Stevilo nizov ne sme biti negativno.");
        }
        if (steviloNizov <= 0 || steviloNizov % 2 == 0) {
            throw new IllegalArgumentException("Stevilo nizov mora biti liho (npr. 3, 5 ali 7).");
        }

        int rating1 = prvi.rating();
        int rating2 = drugi.rating();

        double izid1 = zmagalPrvi ? 1.0 : 0.0;
        double pricakovano1 = pricakovanaVerjetnost(rating1, rating2);
        double margina = predaja
                ? 1.0
                : marginaMnozitelj(rating1, rating2, zmagalPrvi, nizi1, nizi2, steviloNizov);

        int kFaktor1 = kFaktor(prvi.stTekem(), prvi.poVrnitvi());
        int kFaktor2 = kFaktor(drugi.stTekem(), drugi.poVrnitvi());
        double k1 = kFaktor1 * margina * teza;
        double k2 = kFaktor2 * margina * teza;

        // Math.rint = zaokrozi pol na sodo -> pri enakem K je vsota sprememb 0.
        int sprememba1 = (int) Math.rint(k1 * (izid1 - pricakovano1));
        int sprememba2 = (int) Math.rint(k2 * ((1.0 - izid1) - (1.0 - pricakovano1)));

        int novi1 = Math.max(SPODNJA_MEJA, rating1 + sprememba1);
        int novi2 = Math.max(SPODNJA_MEJA, rating2 + sprememba2);

        return new Izracun(
                rating1, rating2,
                novi1, novi2,
                novi1 - rating1, novi2 - rating2,
                pricakovano1,
                kFaktor1, kFaktor2,
                margina, teza
        );
    }

    /* K faktor igralca glede na stevilo ze odigranih ratinskih tekem. */
    public int kFaktor(int stTekem) {
        return kFaktor(stTekem, false);
    }

    /* K faktor igralca: osnova plus pribitki za vse, cesar o njem se ne vemo.
       Pribitki se sestevajo, zato ima novinec (56) vec kot komaj neustaljen
       igralec (48), vrnjeni novinec pa najvec (64). */
    public int kFaktor(int stTekem, boolean poVrnitvi) {
        int k = K_OSNOVNI;
        if (stTekem < PRAG_USTALJEN) k += K_PRIBITEK_NEUSTALJEN;
        if (stTekem < PRAG_PROVIZORICNI) k += K_PRIBITEK_NOVINEC;
        if (poVrnitvi) k += K_PRIBITEK_VRNITEV;
        return k;
    }

    /* Pricakovana verjetnost zmage prvega igralca po rating formuli. */
    private double pricakovanaVerjetnost(int rating1, int rating2) {
        return 1.0 / (1.0 + Math.pow(10.0, (rating2 - rating1) / 400.0));
    }

    /* Mnozitelj K glede na to, kako PRESENETLJIVA je razlika v nizih:
       primerja dejansko dobljene nize porazenca s pricakovanimi (iz razlike
       ratingov). Manj nizov od pricakovanih -> bolj prepricljivo -> vec tock.
       Enak za oba igralca (presenetljiv izid nosi vec informacije o obeh).

       Nize porazenca vzamemo po ZMAGOVALCU, ne kot manjsega od obeh stevil:
       pri koncani tekmi je to isto, pri predaji pa ne (tam poraz ni nujno
       slabsi izid po nizih) - glej izracunaj(..., predaja). */
    private double marginaMnozitelj(int rating1, int rating2, boolean zmagalPrvi,
                                    int nizi1, int nizi2, int steviloNizov) {
        int zaZmago = steviloNizov / 2 + 1;
        int nizovPorazenca = zmagalPrvi ? nizi2 : nizi1;
        if (nizovPorazenca > zaZmago - 1) nizovPorazenca = zaZmago - 1; // varovalka

        int rZmag = zmagalPrvi ? rating1 : rating2;
        int rPor  = zmagalPrvi ? rating2 : rating1;
        double pricakovaniNizi = pricakovaniNiziPorazenca(rZmag, rPor, steviloNizov);

        double presenecenje = (pricakovaniNizi - nizovPorazenca) / zaZmago;
        double mnozitelj = 1.0 + MARGINA_MOC * presenecenje;
        return Math.max(MARGINA_NAJMANJ, Math.min(MARGINA_NAJVEC, mnozitelj));
    }

    /* Pricakovano stevilo nizov porazenca (0..zaZmago-1) glede na razliko
       ratingov: model po nizih (skala SET_SKALA), pogojen na to, da zmagovalec
       tekmo dobi. */
    private double pricakovaniNiziPorazenca(int rZmag, int rPor, int steviloNizov) {
        int zaZmago = steviloNizov / 2 + 1;
        double s = 1.0 / (1.0 + Math.pow(10.0, -(rZmag - rPor) / SET_SKALA)); // verj. niza za zmagovalca
        double vsota = 0.0, prispevek = 0.0;
        for (int l = 0; l < zaZmago; l++) {
            double w = binom(l + zaZmago - 1, l) * Math.pow(s, zaZmago) * Math.pow(1 - s, l);
            vsota += w;
            prispevek += l * w;
        }
        return vsota > 0 ? prispevek / vsota : 0.0;
    }

    private static double binom(int n, int k) {
        if (k < 0 || k > n) return 0.0;
        double r = 1.0;
        for (int i = 0; i < k; i++) r = r * (n - i) / (i + 1);
        return r;
    }
}
