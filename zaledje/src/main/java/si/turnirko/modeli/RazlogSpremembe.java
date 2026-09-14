/* Zakaj je zapis v dnevniku ratinga nastal, kadar ni posledica tekme.

   Zapis brez tekme je bil doslej nujno postavitveni; od V22 je lahko tudi
   odbitek za neaktivnost, od V25 pa zunanja uvrstitev. Razlikovati jih je
   treba, ker se ob ponovnem preracunu obnasajo razlicno: kar je dolocil
   clovek, se OHRANI (ni posledica rezultatov), odbitek pa se POBRISE in
   izracuna znova (je izpeljanka iz zaporedja tekem). */
package si.turnirko.modeli;

public enum RazlogSpremembe {
    /* Vstopna vrednost novinca, ki jo doloci administrator. Je IZHODISCE
       igralca: v casovni vrsti stoji pred njegovo prvo tekmo, ne glede na to,
       kdaj je bila vpisana. */
    POSTAVITEV,
    /* Odbitek, ki je zapadel zaradi dolgega premora. */
    NEAKTIVNOST,
    /* Stevilka, prepisana z zunanje lestvice (ITTF, NTZS, druga zveza) za
       redkega gosta, ki pri nas odigra premalo tekem. Za razliko od
       POSTAVITVE je dovoljena tudi igralcu s tekmami, zahteva vir in
       pojasnilo, v casovni vrsti pa velja ob SVOJEM casu - je popravek na
       dolocen dan in ne izhodisce. */
    ZUNANJA_UVRSTITEV;

    /* Ali je vrednost dolocil clovek (in ne izid tekme). Taki zapisi ponovni
       preracun prezivijo, izpeljanke (NEAKTIVNOST) pa ne. */
    public boolean odCloveka() {
        return this == POSTAVITEV || this == ZUNANJA_UVRSTITEV;
    }
}
