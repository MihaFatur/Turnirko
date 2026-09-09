/* Disciplina dogodka: en igralec na strani ali par.

   Dvojice niso nov SISTEM tekmovanja - igrajo isto izlocilno mrezo kot
   posamicno tekmovanje (glej CHECK v migraciji V14). Razlikuje se le
   tekmovalna enota: pri dvojicah prijava nosi dva igralca. */
package si.turnirko.modeli;

public enum Disciplina {
    POSAMICNO,
    DVOJICE;

    public boolean jeDvojice() {
        return this == DVOJICE;
    }
}
