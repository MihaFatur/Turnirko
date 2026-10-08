/* Preizkus "prvega dne" novinca na javni strani z razlago ratinga.

   Prvi dan se novincu rating ne sesteva po tekmah, ampak se po vsaki tekmi
   (od druge naprej) izracuna znova iz vseh izidov tega dne (UvrstitevNovinca).
   Zato skace za stotine tock - in prav tega gledalec brez preizkusa ne
   razume: poraz v tretji tekmi lahko vzame 113 tock, ker ga postavi v drugo
   luc tudi zmage pred njim. Vsak korak nosi rating PO tekmi in razliko do
   prejsnjega, kot ju kaze zapisnik srecanja. */
package si.turnirko.dto;

import java.util.List;

public record PrviDanNovincaDto(
        int izhodisce,
        List<Korak> koraki
) {

    /* Ena tekma prvega dne. uvrstitev = rating je izracunan znova iz vseh
       izidov dneva (od druge tekme naprej); sicer je navaden korak. */
    public record Korak(
            int ratingNasprotnika,
            boolean zmaga,
            int rating,
            int sprememba,
            boolean uvrstitev
    ) {}
}
