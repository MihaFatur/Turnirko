/* Novo ime obstojece ekipe (preimenovanje, ne prijava).

   Prazno ime pri klubski ekipi pomeni "brez lastnega imena": ekipa se spet
   imenuje po klubu in zaporedni stevilki (npr. "Savinja 2"). Prosta ekipa
   drugega poimenovanja nima, zato ji ime ostane obvezno. Pravila preveri
   PravilaImenaEkipe. */
package si.turnirko.dto;

public record EkipaImeVnos(String ime) {}
