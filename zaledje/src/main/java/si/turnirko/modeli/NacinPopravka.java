/* Kaj naj popravek izida turnirske tekme naredi s tistim, kar je po njej ze
   teklo. Odloci organizator, ker aplikacija tega ne more vedeti: ali so naslednje
   tekme res igrali pravi igralci, je znano samo v dvorani. */
package si.turnirko.modeli;

public enum NacinPopravka {

    /* Mreza oz. zreb: izidi odvisnih tekem gredo, organizator jih vnese znova. */
    IZBRISI_IZIDE,

    /* Mreza oz. zreb: igralca si zamenjata mesti v vseh odvisnih tekmah, izidi
       ostanejo na svoji strani tekme. Pravilno, kadar je tekme igral pravi igralec
       in je bilo napacno samo ime v aplikaciji. */
    ZAMENJAJ_IGRALCA,

    /* Skupina: kar je nastalo iz njenega vrstnega reda (zrebi, izlocilni del,
       finalne skupine), gre - tudi z vnesenimi izidi. */
    PODRI_NASLEDNJI_DEL,

    /* Skupina: popravi se samo tabela skupine in rating, naslednji del ostane,
       kakrsen je, ceprav se s tabelo potem ne ujema. */
    OHRANI_NASLEDNJI_DEL
}
