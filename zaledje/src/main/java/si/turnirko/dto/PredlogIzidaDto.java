/* Predlog izida, sestavljen iz prebranega listka (PredlogIzidaListka).

   Strani sta ze strani TEKME (1 = prijava1), ne stolpca listka: vmesnik jih
   napolni v isti obrazec kot rocni vnos (VnosRezultataOkno) in ob potrditvi
   poslje navaden VnosRezultata. Opozorila so razlogi, zakaj predlog ni
   »pripravljen« za potrditev z enim dotikom - organizator mora tedaj pogledati
   fotografijo. Negotove celice (popravljeno, precrtano, tezko berljivo) vmesnik
   obarva. */
package si.turnirko.dto;

import java.util.List;

import si.turnirko.modeli.IzidTekme;

public record PredlogIzidaDto(
        IzidTekme izidTip,
        Integer dobljeniNizi1,
        Integer dobljeniNizi2,
        Integer zmagovalecStran,
        List<NizPredloga> nizi,
        List<String> opozorila,
        boolean pripravljen
) {

    /* Tocke niza; null = celice ni bilo mogoce prebrati. */
    public record NizPredloga(Integer tocke1, Integer tocke2,
                              boolean negotovo1, boolean negotovo2, boolean neveljaven) {}
}
