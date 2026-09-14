/* Ena tekma v casovni vrsti obracuna Turnirko ratinga.

   Zakaj sploh: rating je ZAPOREDNA kolicina - izid vsake tekme je odvisen od
   stanja obeh igralcev v tistem trenutku. Kdor tekme obracuna po tekmovanjih
   (najprej cel turnir, potem cela liga), jih obracuna v napacnem vrstnem redu:
   igralec bi dobil majske tekme pred oktobrskimi in vse vrednosti bi bile
   napacne. Zato uvoz IN ponovni preracun uporabljata isto pravilo razvrscanja,
   zapisano tukaj na enem mestu.

   "cas" je datum in - kadar ga vir pove - ura. Uro imajo ligaska srecanja
   (zapisnik jo zabelezi), turnirske tekme pa ne: vir pove samo dan tekmovanja.
   Turnirska tekma zato dobi polnoc, kar jo na isti dan postavi PRED ligaska
   srecanja - turnirji se zacnejo zjutraj, ligaska srecanja pa so praviloma
   dopoldne ali popoldne. Igralec istega dne tako ali tako ne igra obojega.

   "zaporedje" uredi tekme, ki imajo isti cas - torej znotraj enega tekmovanja.
   Sestavljeno je iz faze (skupinski del pred izlocilnim; brez tega bi prvo kolo
   finalnega dela padlo pred drugo kolo skupin), kola in mesta. Pri ligah je to
   kolo in zaporedje tekme v srecanju. */
package si.turnirko.storitve;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public record VrstaRatinskeTekme(boolean ligaska, long id, LocalDateTime cas, long zaporedje) {

    /* Tekme, ki jim datuma ne poznamo, gredo na zacetek - tam najmanj skodijo:
       igralec je takrat se brez zgodovine in K faktor je tako ali tako najvisji. */
    public static final LocalDateTime BREZ_DATUMA = LocalDate.of(1900, 1, 1).atStartOfDay();

    public static VrstaRatinskeTekme turnirska(long id, LocalDate datum, boolean izlocilna,
                                               int kolo, int pozicija) {
        long faza = izlocilna ? 1 : 0;
        return new VrstaRatinskeTekme(false, id, datum == null ? null : datum.atStartOfDay(),
                faza * 1_000_000_000L + (long) kolo * 1_000_000 + pozicija);
    }

    public static VrstaRatinskeTekme ligaska(long id, LocalDateTime cas, int kolo,
                                             int zaporedjeVSrecanju) {
        return new VrstaRatinskeTekme(true, id, cas,
                (long) kolo * 1_000_000 + zaporedjeVSrecanju);
    }

    /* Posamicna tekma srecanja EKIPNE TEKME TURNIRJA (V28). Id je id tekme
       srecanja (obracun je ligaski), cas pa dan turnirja kot pri vseh
       turnirskih tekmah. Zaporedje nosi stopnjo: predtekmovalne skupine,
       nato visje skupinske stopnje in sele nato mreza - brez tega bi prvo kolo
       finalne skupine padlo pred drugo kolo predtekmovanja. */
    public static VrstaRatinskeTekme ekipnaTurnirska(long id, LocalDate datum, boolean skupinska,
                                                     Integer stopnjaSkupine, int kolo, int pozicija,
                                                     int zaporedjeVSrecanju) {
        long faza = skupinska ? Math.max(0, (stopnjaSkupine == null ? 1 : stopnjaSkupine) - 1) : 9;
        return new VrstaRatinskeTekme(true, id, datum == null ? null : datum.atStartOfDay(),
                faza * 1_000_000_000_000L + (long) kolo * 1_000_000_000L
                        + (long) pozicija * 1_000 + zaporedjeVSrecanju);
    }

    /* Cas, po katerem se razvrsca in ki se zapise v dnevnik. */
    public LocalDateTime casZaObracun() {
        return cas == null ? BREZ_DATUMA : cas;
    }

    public static final Comparator<VrstaRatinskeTekme> VRSTNI_RED = Comparator
            .comparing(VrstaRatinskeTekme::casZaObracun)
            .thenComparingLong(VrstaRatinskeTekme::zaporedje)
            .thenComparingLong(VrstaRatinskeTekme::id);

    public static List<VrstaRatinskeTekme> uredi(List<VrstaRatinskeTekme> vrsta) {
        List<VrstaRatinskeTekme> urejene = new ArrayList<>(vrsta);
        urejene.sort(VRSTNI_RED);
        return urejene;
    }
}
