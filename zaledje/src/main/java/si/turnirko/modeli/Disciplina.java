/* Disciplina dogodka: en igralec na strani, par ali ekipa.

   Nobena disciplina ni nov SISTEM tekmovanja - razlikuje se samo tekmovalna
   enota, ki jo nosi prijava:
   - POSAMICNO: prijava nosi igralca,
   - DVOJICE:   prijava nosi dva igralca (V14); igrajo samo izlocilno mrezo,
   - EKIPNO:    prijava nosi EKIPO (V28); izid ekipne tekme so dobljene
                posamicne tekme, kako je bilo igrano, pa zapise srecanje. */
package si.turnirko.modeli;

public enum Disciplina {
    POSAMICNO,
    DVOJICE,
    EKIPNO;

    public boolean jeDvojice() {
        return this == DVOJICE;
    }

    public boolean jeEkipno() {
        return this == EKIPNO;
    }
}
