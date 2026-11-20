/* Eno branje listka za stran »Branje listkov« (samo organizator turnirja in admin).

   tekma je prepoznana (ali izbrana) tekma s predlogom izida v njenih straneh;
   kadar tekme ni bilo mogoce dolociti, so v kandidati tekme, ki se ujemajo s
   prebranimi imeni, vsaka s svojim predlogom (strani so lahko zamenjane).
   prebranoLevo/Desno sta imeni, kot ju je prebral bralnik - organizator po njiju
   vidi, zakaj tekma ni bila najdena. */
package si.turnirko.dto;

import java.time.LocalDateTime;
import java.util.List;

import si.turnirko.modeli.PrepoznavaTekme;
import si.turnirko.modeli.StanjeBranja;

public record BranjeListkaDto(
        Long id,
        StanjeBranja stanje,
        LocalDateTime ustvarjenoOb,
        LocalDateTime prebranoOb,
        boolean imaSliko,
        PrepoznavaTekme prepoznava,
        TekmaListka tekma,
        PredlogIzidaDto predlog,
        List<KandidatListka> kandidati,
        String prebranoLevo,
        String prebranoDesno,
        String napaka,
        Boolean popravljeno,
        // V_VRSTI dlje kot nekaj minut (npr. zavrnjeno ob polni vrsti): sme se prebrati znova
        boolean obviselo,
        // nakljucna kontrola (V69): predlog ni za en dotik, organizator ga pregleda v celoti
        boolean kontrola
) {

    /* Tekma z imenom dogodka in oznako skupine (za izpis »U15 · Skupina B · 2. kolo»). */
    public record TekmaListka(TekmaDto tekma, Long idDogodka, String imeDogodka, String oznakaSkupine) {}

    /* Tekma, ki bi lahko bila na listku, s predlogom v njenih straneh. */
    public record KandidatListka(TekmaListka tekma, boolean obrnjeno, PredlogIzidaDto predlog) {}
}
