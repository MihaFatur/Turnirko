/* Vnos ekipe v ligo.

   Dve poti: KLUBSKA ekipa (idKlub izpolnjen) - zaporedna jo loci od drugih
   ekip istega kluba (Savinja 1, 2), ime pa je neobvezno; in PROSTA ekipa
   (idKlub prazen) - zasedba brez zapisa v registru klubov, ki nastopa samo v
   tej ligi in se poimenuje z imenom, zato je to takrat obvezno.

   Obveznosti ne more nositi bean validacija (odvisni sta druga od druge),
   zato jo preveri LigaStoritev.dodajEkipo. */
package si.turnirko.dto;

public record EkipaVnos(
        // prazen = prosta ekipa (brez kluba)
        Long idKlub,
        // ce null, se zaporedna doloci samodejno (naslednja prosta za klub);
        // pri prosti ekipi je vedno 1
        Integer zaporedna,
        // pri klubski ekipi neobvezno lastno ime, pri prosti obvezno
        String ime
) {}
