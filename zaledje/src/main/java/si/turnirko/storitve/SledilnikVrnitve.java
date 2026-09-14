/* Prepoznavanje vrnitve po daljsi odsotnosti.

   Turnirko rating da igralcu, ki se po vec kot letu dni vrne za mizo, prvih
   petnajst tekem povisan K: v letu dni se lahko precej spremeni (mladinec
   zraste, clovek po poskodbi izgubi formo), zato njegova stara stevilka o njem
   pove manj kot obicajno.

   Pravilo zivi tu in ne na dveh mestih, ker ga potrebujeta dva: redni obracun
   tekme (iz shranjenega stanja igralca) in ponovni preracun, ki stanje obnovi
   tako, da vse case igralcevih tekem po vrsti spusti skozi ta sledilnik. Dve
   kopiji istega pravila bi se scasoma razsli in isti izidi bi dali dva
   razlicna ratinga. */
package si.turnirko.storitve;

import java.time.LocalDateTime;

public final class SledilnikVrnitve {

    private LocalDateTime zadnja;
    private int preostanek;

    public SledilnikVrnitve(LocalDateTime zadnja, int preostanek) {
        this.zadnja = zadnja;
        this.preostanek = preostanek;
    }

    public SledilnikVrnitve() {
        this(null, 0);
    }

    /* Sprejme cas naslednje igralceve tekme in pove, ali ta tekma se steje med
       tekme po vrnitvi (in jo pri tem porabi).

       Tekma brez znanega datuma (BREZ_DATUMA) vrnitve niti ne sprozi niti ne
       premakne zadnjega termina: o njej ne vemo, kdaj je bila, zato iz nje ne
       smemo sklepati na presledek - sicer bi vsaka uvozena tekma brez datuma
       naslednjo pravo tekmo oznacila za "vrnitev po stotih letih". */
    public boolean obracunaj(LocalDateTime cas) {
        boolean znanCas = cas != null && !cas.equals(VrstaRatinskeTekme.BREZ_DATUMA);
        boolean znanaZadnja = zadnja != null && !zadnja.equals(VrstaRatinskeTekme.BREZ_DATUMA);

        if (znanCas && znanaZadnja
                && zadnja.plusMonths(TurnirkoRatingStoritev.MESECEV_ZA_VRNITEV).isBefore(cas)) {
            preostanek = TurnirkoRatingStoritev.TEKEM_PO_VRNITVI;
        }

        boolean poVrnitvi = preostanek > 0;
        if (poVrnitvi) {
            preostanek--;
        }
        // zadnji termin sme samo naprej: rezultat se lahko vnese tudi za nazaj
        if (znanCas && (zadnja == null || cas.isAfter(zadnja))) {
            zadnja = cas;
        }
        return poVrnitvi;
    }

    public LocalDateTime zadnja() { return zadnja; }

    public int preostanek() { return preostanek; }
}
