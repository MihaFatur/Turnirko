/* Kako se vpisan datum rojstva primerja z datumom obstojecega igralca.
   Pozna ga samo administratorjev izpis (PodobenIgralecDto): organizator
   datumov rojstva ne vidi in mu primerjava ne sme biti obvestilo, ki bi mu
   jih izdalo s poskusanjem. */
package si.turnirko.modeli;

public enum PrimerjavaDatuma {

    /* Isti dan, mesec in leto: skoraj gotovo ista oseba. */
    ENAK,

    /* Razlikuje se natanko ena sestavina (dan, mesec ali leto) ali pa sta
       dan in mesec zamenjana (12. 3. proti 3. 12.) - videti kot tipkarska
       napaka, ne kot druga oseba. */
    PODOBEN,

    DRUG
}
