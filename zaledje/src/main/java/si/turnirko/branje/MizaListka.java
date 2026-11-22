/* Okence za stevilko mize na poravnanem listku (S1, od 22. 11. 2026).

   Okence stoji na stalnem mestu v glavi (GeometrijaListka.MIZA). Robovi se
   poiscejo kot pri okencih vadnice (CeliceVadnice): poravnava ni popolna,
   prepognjen listek pa okence malo premakne.

   Listek, natisnjen pred S1, okenca nima - na istem mestu je crta za rocni
   vpis (»Miza st. ___«) in morda z roko napisana stevilka. Ce bi bralnik tam
   vseeno bral, bi crto in besedilo prebral kot stevko. Zato velja okence samo,
   ce so vsaj trije njegovi robovi najdeni kot crta (crta za vpis je en sam rob);
   sicer mize ni.

   Za mizo ni pravil, ki bi napacno branje popravila (pri tockah jih ima
   RazlagaListka), zato je okence »zanesljivo« samo, ce:
   - so najdeni vsi stirje robovi (pri treh velja za manjkajocega pricakovana
     lega in premaknjena crta lahko ostane v izrezu),
   - ima najdeno okence mere natisnjenega (senca pregiba ob robu je tudi »crta«),
   - cez notranjost ne gre ravna crta od roba do roba. Simulacija (pregib cez
     levi del okenca): senca pregiba je navpicna crta cez vso visino in mreza jo
     prebere kot »1« - prazno okence je bilo tiho prebrano kot miza 1.
   Nezanesljivo okence se se vedno prebere, miza pa je negotova (organizator ali
   sodnik jo pogleda). */
package si.turnirko.branje;

public final class MizaListka {

    /* Najmanj najdenih robov okenca (od stirih): prst ali pregib lahko zakrije enega. */
    static final int NAJMANJ_ROBOV = 3;
    /* Najvecje odstopanje mer najdenega okenca od natisnjenega (px pri 10 px/mm). */
    static final int NAJVEC_ODSTOPANJE = 6;
    /* Crta cez okence: delez visine (sirine) notranjosti, ki ga pokrije crnilo v
       pasu treh stolpcev (vrstic). Rocna enica je nagnjena in ne seze od roba do
       roba, zato v tako ozkem pasu ne doseze tega deleza. */
    static final double POKRITOST_CRTE = 0.92;

    private MizaListka() {}

    /* Notranjost okenca za razrez (kot CeliceVadnice) in ali je okence zanesljivo
       (sicer je branje mize negotovo). */
    public record Okence(CeliceListka.Celica celica, boolean zanesljivo) {}

    /* Okence mize ali null, ce ga listek nima. */
    public static Okence najdi(Temnost listek) {
        boolean[] crnilo = CeliceVadnice.crnilo(listek);
        CeliceVadnice.Okence o = CeliceVadnice.okence(listek, crnilo, GeometrijaListka.MIZA);
        if (o.najdenihRobov() < NAJMANJ_ROBOV) {
            return null;
        }
        boolean zanesljivo = o.najdenihRobov() == 4 && praveMere(o.celica())
                && !crtaCezNotranjost(crnilo, listek.sirina, listek.visina, o.celica());
        return new Okence(o.celica(), zanesljivo);
    }

    /* Notranjost okenca (z odmikom razreza, kot jo vrne CeliceVadnice) ima mere
       natisnjenega okenca: zunanja mera manj dve crti (~5 px) plus dvakrat odmik. */
    static boolean praveMere(CeliceListka.Celica c) {
        int r = GeometrijaListka.PX_NA_MM;
        int o = RazrezCelice.ODMIK - 1;
        double sirina = (GeometrijaListka.MIZA[2] - GeometrijaListka.MIZA[0]) * r - 2 * 5 + 2 * o;
        double visina = (GeometrijaListka.MIZA[3] - GeometrijaListka.MIZA[1]) * r - 2 * 5 + 2 * o;
        return Math.abs(c.sirina() - sirina) <= NAJVEC_ODSTOPANJE && Math.abs(c.visina() - visina) <= NAJVEC_ODSTOPANJE;
    }

    /* Ali gre cez notranjost (kot jo bere razrez: celica brez odmika) navpicna ali
       vodoravna crta od roba do roba - pregib ali ostanek roba, ne stevka. */
    static boolean crtaCezNotranjost(boolean[] crnilo, int w, int h, CeliceListka.Celica c) {
        int x0 = Math.max(1, c.x0() + RazrezCelice.ODMIK);
        int x1 = Math.min(w - 2, c.x1() - RazrezCelice.ODMIK);
        int y0 = Math.max(1, c.y0() + RazrezCelice.ODMIK);
        int y1 = Math.min(h - 2, c.y1() - RazrezCelice.ODMIK);
        if (x1 <= x0 || y1 <= y0) {
            return false;
        }
        for (int x = x0; x <= x1; x++) {
            int zadetkov = 0;
            for (int y = y0; y <= y1; y++) {
                if (crnilo[y * w + x - 1] || crnilo[y * w + x] || crnilo[y * w + x + 1]) {
                    zadetkov++;
                }
            }
            if (zadetkov >= POKRITOST_CRTE * (y1 - y0 + 1)) {
                return true;
            }
        }
        for (int y = y0; y <= y1; y++) {
            int zadetkov = 0;
            for (int x = x0; x <= x1; x++) {
                if (crnilo[(y - 1) * w + x] || crnilo[y * w + x] || crnilo[(y + 1) * w + x]) {
                    zadetkov++;
                }
            }
            if (zadetkov >= POKRITOST_CRTE * (x1 - x0 + 1)) {
                return true;
            }
        }
        return false;
    }
}
