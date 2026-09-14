/* Predlog razporeda: kdo s kom v katerem kolu, se preden je kaj zapisano.

   Vmesnik iz njega sestavi prazno mrezo za rocni vpis (koliko kol ima liga in
   koliko srecanj je v kolu) in jo na zahtevo napolni z naklucnim zrebom, ki ga
   organizator nato popravi. Racun tece skozi isto RazporedStoritev kot pravi
   zreb - pravila razporeda so tako zapisana na enem samem mestu in se ne
   morejo raziti z drugo kopijo v vmesniku.

   uraVKolu (0 = prva ura lige) pri ligi z urami srecanj pove, ob kateri uri
   kola se srecanje zacne; pri kolu kroznega sistema je 0. */
package si.turnirko.dto;

public record ParRazporedaDto(
        int kolo,
        int uraVKolu,
        Long idDomaci,
        String domaci,
        Long idGost,
        String gost
) {}
