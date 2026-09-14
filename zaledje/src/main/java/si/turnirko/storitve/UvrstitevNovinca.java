/* Uvrstitev novinca po prvem dnevu igranja - regularizirana fiksna tocka.

   Zakaj to in ne obicajno sestevanje po tekmah: novinec, ki na prvem turnirju
   odigra pet tekem, se z obicajnim korakom (K 56) proti svoji pravi ravni sele
   priblizuje. Mocan novinec bi po prvem turnirju kotiral prenizko, sibak
   previsoko - in oba bi naslednjih nekaj mesecev kvarila obracun vsem, ki jih
   srecata.

   Namesto tega poiscemo stevilko, ki njegove izide NAJBOLJE RAZLOZI: tisti
   rating r, pri katerem je vsota pricakovanih izidov proti njegovim
   nasprotnikom enaka vsoti dejanskih. To je najverjetnejsa ocena (MLE) modela,
   na katerem stoji cel sistem.

   Sama fiksna tocka je pri dveh ali treh tekmah negotova - kdor dobi vse, bi
   "najbolje razlozil" izide pri neskoncnem ratingu. Zato je REGULARIZIRANA:
   poleg dejanskih tekem stejemo se NAVIDEZNE_TEKME izenacenih tekem proti
   starostnemu sidru. Novinec je tako potegnjen proti pricakovani vrednosti za
   svojo starost, dokler ni dovolj dokazov, da je drugacen. Isto pocne Ratings
   Central s svojim "priorjem".

   Nize namenoma NE upostevamo: za razlago igralcu ("premagal si 1150, izgubil
   z 1320, to te postavi na ...") steje, koga je premagal, in prav to je tudi
   podatek, ki ga je najlaze preveriti. Nizi svoje delo opravijo pozneje, ko se
   rating sestevlja po tekmah. */
package si.turnirko.storitve;

import java.util.List;

public final class UvrstitevNovinca {

    /* Koliko navideznih izenacenih tekem proti sidru dodamo dejanskim izidom.
       Dve sta dovolj, da ena sama zmaga ne odnese novinca v nebo, in premalo,
       da bi zadusili pet pravih tekem. */
    public static final int NAVIDEZNE_TEKME = 2;

    /* Najmanj toliko tekem mora novinec odigrati, da ga sploh uvrstimo po
       izidih; pri eni tekmi je ugibanje vecje od podatka. */
    public static final int NAJMANJ_TEKEM = 2;

    private static final int NAJNIZJI = 100;
    private static final int NAJVISJI = 3000;

    /* Ena odigrana tekma: rating nasprotnika PRED tekmo in ali jo je novinec
       dobil. */
    public record Izid(int ratingNasprotnika, boolean zmaga) {}

    private UvrstitevNovinca() {}

    /* Vrne rating, ki dane izide najbolje razlozi, potegnjen proti sidru.
       Prazen seznam vrne sidro. */
    public static int izracunaj(List<Izid> izidi, int sidro) {
        if (izidi.isEmpty()) {
            return sidro;
        }
        double dejansko = 0.0;
        for (Izid i : izidi) {
            if (i.zmaga()) dejansko += 1.0;
        }
        // navidezne tekme prinesejo pol tocke vsaka (izenaceno)
        double cilj = dejansko + NAVIDEZNE_TEKME * 0.5;

        /* Pricakovana vsota je v ratingu narascajoca, zato bisekcija. Meji sta
           isti kot pri postavitvenem ratingu - cez njiju rating ne gre. */
        double spodaj = NAJNIZJI, zgoraj = NAJVISJI;
        if (pricakovano(spodaj, izidi, sidro) >= cilj) return NAJNIZJI;
        if (pricakovano(zgoraj, izidi, sidro) <= cilj) return NAJVISJI;
        for (int korak = 0; korak < 40; korak++) {
            double sredina = (spodaj + zgoraj) / 2.0;
            if (pricakovano(sredina, izidi, sidro) < cilj) {
                spodaj = sredina;
            } else {
                zgoraj = sredina;
            }
        }
        return (int) Math.rint((spodaj + zgoraj) / 2.0);
    }

    /* Vsota pricakovanih izidov pri ratingu r - dejanske tekme in navidezne
       proti sidru. */
    private static double pricakovano(double r, List<Izid> izidi, int sidro) {
        double vsota = NAVIDEZNE_TEKME * verjetnost(r, sidro);
        for (Izid i : izidi) {
            vsota += verjetnost(r, i.ratingNasprotnika());
        }
        return vsota;
    }

    /* Ista logisticna krivulja kot v TurnirkoRatingStoritev (delitelj 400). */
    private static double verjetnost(double r, double nasprotnik) {
        return 1.0 / (1.0 + Math.pow(10.0, (nasprotnik - r) / 400.0));
    }
}
