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
        boolean kontrola,
        // lega celic na poravnanem listku za izrez ob poljih (F4.1); null brez slike
        CeliceListkaDto celice,
        // levi stolpec listka je udelezenec 2 prepoznane tekme (listek brez kode)
        boolean obrnjeno,
        // stevilka mize iz okenca listka (S1); null, ce je okence prazno ali ga ni
        Integer prebranaMiza,
        // zapis v okencu mize je tezko berljiv
        boolean negotovaMiza,
        // S2: fotografijo je poslal sodnik s svojim telefonom
        boolean poslalSodnik,
        // sodnik je izid potrdil (pri predpregledu je predlog sodnikov izid)
        boolean sodnikPotrdil,
        // sodnik je spremenil prebrano - organizator naj pogleda fotografijo
        Boolean sodnikSpremenil,
        // miza, kot jo je potrdil sodnik
        Integer mizaSodnika
) {

    /* Tekma z imenom dogodka in oznako skupine (za izpis »U15 · Skupina B · 2. kolo»);
       turnir loci enako imenovane kategorije turnirjev s skupnimi mizami (V78). */
    public record TekmaListka(TekmaDto tekma, Long idDogodka, String imeDogodka, String oznakaSkupine,
                              Long idTurnir, String imeTurnirja) {}

    /* Tekma, ki bi lahko bila na listku, s predlogom v njenih straneh. */
    public record KandidatListka(TekmaListka tekma, boolean obrnjeno, PredlogIzidaDto predlog) {}
}
