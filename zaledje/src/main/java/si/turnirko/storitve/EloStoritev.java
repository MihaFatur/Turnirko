/* Cisti izracun ELO ratinga - brez dostopa do baze, zato ga je enostavno testirati.
   Osnova je standardna sahovska formula (logisticna, delitelj 400), z zdaj tremi
   izboljsavami:

   1) DINAMICNI (provizoricni) K: nov igralec ima velik K in hitro najde svojo
      raven, ustaljen pa majhnega in se malo giblje. K je odvisen od stevila ze
      odigranih tekem igralca - vsak igralec ima torej svoj K.

   2) NICVSOTNO ZAOKROZEVANJE: uporabljamo Math.rint (zaokrozi pol na sodo), ne
      Math.round (pol navzgor). Ko imata igralca isti K, gubitnik izgubi natanko
      toliko, kot zmagovalec pridobi (vsota sprememb je 0) - tudi pri izenacenem
      izidu. Math.round bi ob lihem K ob vsakem izenacenju vbrizgal +1 tocko.

   3) SET-MARGINA PO PRESENECENJU: prepricljivejsa zmaga prinese vec tock, a
      merjeno glede na PRICAKOVANO razliko. Favorit, ki gladko zmaga, je to
      pricakovano (majhen bonus); avtsajder, ki gladko zmaga, je presenetljiv
      (velik bonus). To zamenja prejsnjo surovo razliko nizov. */
package si.turnirko.storitve;

import org.springframework.stereotype.Service;

@Service
public class EloStoritev {

    /* Rating, s katerim zacne nov igralec (ce mu admin ne postavi drugacnega). */
    public static final int ZACETNI_RATING = 1000;

    /* Meji za postavitveni (zacetni) rating, ki ga sme dolociti admin. */
    public static final int NAJNIZJI_ZACETNI = 100;
    public static final int NAJVISJI_ZACETNI = 3000;

    /* Rating nikoli ne pade pod to mejo. */
    private static final int SPODNJA_MEJA = 100;

    /* Dinamicni K glede na stevilo ze odigranih tekem igralca. */
    public static final int K_PROVIZORICNI = 48; // < PRAG_PROVIZORICNI tekem
    public static final int K_USTALJEVANJE = 32; // < PRAG_USTALJEN tekem
    public static final int K_USTALJEN     = 20; // sicer
    public static final int PRAG_PROVIZORICNI = 10;
    public static final int PRAG_USTALJEN     = 30;

    /* Skala modela po nizih za oceno pricakovane razlike. Umerjena tako, da je
       inducirana verjetnost zmage na tekmo blizu ELO 400-logistiki. */
    private static final double SET_SKALA = 800.0;

    /* Rezultat izracuna za obe strani tekme. */
    public record IzracunElo(
            int ratingPrej1, int ratingPrej2,
            int ratingPo1, int ratingPo2,
            int sprememba1, int sprememba2,
            double pricakovanaVerjetnost1,
            double uporabljeniK
    ) {}

    /* Izracuna nova ratinga po tekmi.
       stTekem1/stTekem2 sta stevili ze odigranih ratinskih tekem posameznega
       igralca (dolocata njegov K). zmagalPrvi pove zmagovalca eksplicitno (ne iz
       nizov), ker je tekma lahko koncana tudi s predajo pri izenacenih nizih. */
    public IzracunElo izracunaj(int rating1, int rating2, int stTekem1, int stTekem2,
                                boolean zmagalPrvi, int nizi1, int nizi2, int steviloNizov) {
        if (nizi1 < 0 || nizi2 < 0) {
            throw new IllegalArgumentException("Stevilo nizov ne sme biti negativno.");
        }
        if (steviloNizov <= 0 || steviloNizov % 2 == 0) {
            throw new IllegalArgumentException("Stevilo nizov mora biti liho (npr. 3, 5 ali 7).");
        }

        double izid1 = zmagalPrvi ? 1.0 : 0.0;
        double pricakovano1 = pricakovanaVerjetnost(rating1, rating2);
        double margina = marginaMnozitelj(rating1, rating2, zmagalPrvi, nizi1, nizi2, steviloNizov);

        double k1 = kFaktor(stTekem1) * margina;
        double k2 = kFaktor(stTekem2) * margina;

        // Math.rint = zaokrozi pol na sodo -> pri enakem K je vsota sprememb 0.
        int sprememba1 = (int) Math.rint(k1 * (izid1 - pricakovano1));
        int sprememba2 = (int) Math.rint(k2 * ((1.0 - izid1) - (1.0 - pricakovano1)));

        int novi1 = Math.max(SPODNJA_MEJA, rating1 + sprememba1);
        int novi2 = Math.max(SPODNJA_MEJA, rating2 + sprememba2);

        return new IzracunElo(
                rating1, rating2,
                novi1, novi2,
                novi1 - rating1, novi2 - rating2,
                pricakovano1, k1
        );
    }

    /* K faktor igralca glede na stevilo ze odigranih ratinskih tekem. */
    public int kFaktor(int stTekem) {
        if (stTekem < PRAG_PROVIZORICNI) return K_PROVIZORICNI;
        if (stTekem < PRAG_USTALJEN) return K_USTALJEVANJE;
        return K_USTALJEN;
    }

    /* Pricakovana verjetnost zmage prvega igralca po ELO formuli. */
    private double pricakovanaVerjetnost(int rating1, int rating2) {
        return 1.0 / (1.0 + Math.pow(10.0, (rating2 - rating1) / 400.0));
    }

    /* Mnozitelj K glede na to, kako PRESENETLJIVA je razlika v nizih:
       primerja dejansko dobljene nize porazenca s pricakovanimi (iz razlike
       ratingov). Manj nizov od pricakovanih -> bolj prepricljivo -> vec tock.
       Enak za oba igralca (presenetljiv izid nosi vec informacije o obeh). */
    private double marginaMnozitelj(int rating1, int rating2, boolean zmagalPrvi,
                                    int nizi1, int nizi2, int steviloNizov) {
        int zaZmago = steviloNizov / 2 + 1;
        int nizovPorazenca = Math.min(nizi1, nizi2);
        if (nizovPorazenca > zaZmago - 1) nizovPorazenca = zaZmago - 1; // varovalka (npr. predaja)

        int rZmag = zmagalPrvi ? rating1 : rating2;
        int rPor  = zmagalPrvi ? rating2 : rating1;
        double pricakovaniNizi = pricakovaniNiziPorazenca(rZmag, rPor, steviloNizov);

        double presenecenje = (pricakovaniNizi - nizovPorazenca) / zaZmago;
        double mnozitelj = 1.0 + 0.5 * presenecenje;
        return Math.max(0.6, Math.min(1.5, mnozitelj));
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
