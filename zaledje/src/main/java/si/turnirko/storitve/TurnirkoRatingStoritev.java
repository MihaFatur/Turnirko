/* Cisti izracun Turnirko ratinga - brez dostopa do baze, zato ga je enostavno
   testirati. Osnova je standardna sahovska formula (logisticna, delitelj 400),
   s tremi izboljsavami:

   1) K PO NEGOTOVOSTI: osnovni K je 48, nanj pa se pristejejo pribitki, dokler
      o igralcu vemo premalo - manj kot 30 tekem, manj kot 10 tekem in prvih 15
      tekem po vec kot letu dni odsotnosti. Vsak igralec ima torej svoj K in nov
      ali vrnjen igralec hitreje najde svojo raven.

   2) NICVSOTNO ZAOKROZEVANJE: uporabljamo Math.rint (zaokrozi pol na sodo), ne
      Math.round (pol navzgor). Ko imata igralca isti K, gubitnik izgubi natanko
      toliko, kot zmagovalec pridobi (vsota sprememb je 0) - tudi pri izenacenem
      izidu. Math.round bi ob lihem K ob vsakem izenacenju vbrizgal +1 tocko.

   3) TEZA TEKMOVANJA: uradna tekma premakne rating bolj kot klubska ali
      rekreativna (RavenTekmovanja).

   ZMAGA JE ZMAGA. Izid v nizih na spremembo ne vpliva: 3 : 0 in 3 : 2 prineseta
   isto, prav tako 0 : 3 in 2 : 3 vzameta isto (odlocitev lastnika, 30. 9. 2026).
   Prej je K mnozila "set-margina po presenecenju" (gladka zmaga avtsajderja je
   prinesla do 65 % vec). Brez nje je rating enak ne glede na to, kako natancno
   je bil izid prepisan s papirja, predaja, tekma z zapisanim samo zmagovalcem
   in odigrana tekma pa se obracunajo po istem pravilu. Kdor bi margino vracal,
   naj izmeri, koliko napoved res izboljsa - ne ugiba.

   Vrednosti K niso ugibane: umerjene so na pravih tekmah iz zgodovine NTZS po
   metodi "napovej, nato posodobi" (prequential log-loss). Brez margine je bil
   K 40 premajhen (oktober 2026, tekme od 2024, oba igralca z vsaj 20 tekmami):
   K x1,0 0,3951, x1,2 0,3930, x1,4 0,3922, x1,6 0,3922. Lastnik je izbral
   x1,2 - napove bolje, skoki pa ostanejo blizu tistih z margino (povprecno
   13 namesto 11,8 tocke pri ustaljenem igralcu, najvec 57 namesto 74). */
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
    public static final int SPODNJA_MEJA = 100;

    /* K ustaljenega igralca in pribitki za negotovost (sestevajo se). */
    public static final int K_OSNOVNI = 48;
    public static final int K_PRIBITEK_NEUSTALJEN = 10; // < PRAG_USTALJEN tekem
    public static final int K_PRIBITEK_NOVINEC = 10;    // < PRAG_PROVIZORICNI tekem
    public static final int K_PRIBITEK_VRNITEV = 10;    // prvih TEKEM_PO_VRNITVI po odsotnosti
    public static final int PRAG_PROVIZORICNI = 10;
    public static final int PRAG_USTALJEN = 30;

    /* Vrnitev: po koliko mesecih brez tekme velja igralec za odsotnega in
       koliko tekem po vrnitvi ima se povisan K. Igralec se v letu dni lahko
       precej spremeni (forma, rast mladinca, premor po poskodbi), zato mu damo
       priloznost, da se hitreje vrne na svojo raven. */
    public static final int MESECEV_ZA_VRNITEV = 12;
    public static final int TEKEM_PO_VRNITVI = 15;

    /* Vse o enem igralcu, kar vpliva na velikost njegovega K. Zapis (namesto
       treh locenih parametrov) zato, ker se sicer ob klicu prevec zaporednih
       stevil in zastavic pomesa. */
    public record StanjeIgralca(int rating, int stTekem, boolean poVrnitvi) {
        public static StanjeIgralca ustaljeno(int rating, int stTekem) {
            return new StanjeIgralca(rating, stTekem, false);
        }
    }

    /* Rezultat izracuna za obe strani tekme, skupaj s SESTAVINAMI, iz katerih
       je sprememba nastala: sprememba = K x teza x (izid - pricakovano).
       Sestavine gredo v dnevnik, da zna vrstica pozneje razloziti svojo
       stevilko - poznejsi izracun bi dal danasnje parametre za staro tekmo in
       bi lahko protislovil zapisani spremembi.

       K in pricakovano sta last IGRALCA (vsak ima svoj K; pricakovani izid
       drugega je 1 minus prvega), teza pa last TEKME. */
    public record Izracun(
            int ratingPrej1, int ratingPrej2,
            int ratingPo1, int ratingPo2,
            int sprememba1, int sprememba2,
            double pricakovanaVerjetnost1,
            int k1, int k2,
            double teza
    ) {}

    /* Izracuna nova ratinga po odigrani tekmi s tezo 1 (uradno tekmovanje).
       stTekem1/stTekem2 sta stevili ze odigranih ratinskih tekem posameznega
       igralca (dolocata njegov K). */
    public Izracun izracunaj(int rating1, int rating2, int stTekem1, int stTekem2,
                                boolean zmagalPrvi) {
        return izracunaj(StanjeIgralca.ustaljeno(rating1, stTekem1),
                StanjeIgralca.ustaljeno(rating2, stTekem2), zmagalPrvi, 1.0);
    }

    /* Polni izracun: poleg ratinga in stevila tekem pozna se zastavico vrnitve
       za vsakega igralca posebej (K je last igralca, ne tekme) in TEZO
       tekmovanja (RavenTekmovanja.getTeza). Teza je last tekme, zato je enaka
       za oba - vsota sprememb pri enakem K je se vedno 0.

       Zmagovalec je podan izrecno in se ne bere iz nizov: tekma se lahko konca
       tudi s predajo pri izenacenih nizih ali z zapisanim samo zmagovalcem. */
    public Izracun izracunaj(StanjeIgralca prvi, StanjeIgralca drugi, boolean zmagalPrvi,
                                double teza) {
        int rating1 = prvi.rating();
        int rating2 = drugi.rating();

        double izid1 = zmagalPrvi ? 1.0 : 0.0;
        double pricakovano1 = pricakovanaVerjetnost(rating1, rating2);

        int kFaktor1 = kFaktor(prvi.stTekem(), prvi.poVrnitvi());
        int kFaktor2 = kFaktor(drugi.stTekem(), drugi.poVrnitvi());
        double k1 = kFaktor1 * teza;
        double k2 = kFaktor2 * teza;

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
                teza
        );
    }

    /* K faktor igralca glede na stevilo ze odigranih ratinskih tekem. */
    public int kFaktor(int stTekem) {
        return kFaktor(stTekem, false);
    }

    /* K faktor igralca: osnova plus pribitki za vse, cesar o njem se ne vemo.
       Pribitki se sestevajo, zato ima novinec (68) vec kot komaj neustaljen
       igralec (58), vrnjeni novinec pa najvec (78). */
    public int kFaktor(int stTekem, boolean poVrnitvi) {
        int k = K_OSNOVNI;
        if (stTekem < PRAG_USTALJEN) k += K_PRIBITEK_NEUSTALJEN;
        if (stTekem < PRAG_PROVIZORICNI) k += K_PRIBITEK_NOVINEC;
        if (poVrnitvi) k += K_PRIBITEK_VRNITEV;
        return k;
    }

    /* Pricakovana verjetnost zmage prvega igralca po rating formuli. */
    public double pricakovanaVerjetnost(int rating1, int rating2) {
        return 1.0 / (1.0 + Math.pow(10.0, (rating2 - rating1) / 400.0));
    }
}
