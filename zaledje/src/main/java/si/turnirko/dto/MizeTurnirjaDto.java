/* Stran Mize (M1, M2; docs/pravila/mize.md): nastavitve miz turnirja, stanje vsake
   mize, cakajoci sodniki in pripravljene tekme v vrstnem redu predloga. Samo za
   organizatorja turnirja in admina (MizeStoritev, VarnostneNastavitve). */
package si.turnirko.dto;

import java.time.LocalDateTime;
import java.util.List;

public record MizeTurnirjaDto(
        Long idTurnir,
        String imeTurnirja,
        /* null = mize niso nastavljene (klic ni mogoc) */
        Integer steviloMiz,
        List<Integer> izklopljeneMize,
        boolean porazenecSodi,
        int pocitekMin,
        /* vsaj ena kategorija turnirja tece */
        boolean dogodkiVTeku,
        /* minute igre in pocitka vmesnik racuna od ure streznika, ne prenosnika */
        LocalDateTime casStreznika,
        List<MizaDto> mize,
        /* pripravljene tekme vseh kategorij v teku, po vrsti predloga */
        List<KandidatDto> kandidati,
        /* vrsto je uredil organizator (vsaj ena tekma vrste je v rocnem vrstnem redu) */
        boolean rocniVrstniRed
) {

    /* tekma = kar se igra na mizi (prazno pri prosti in izklopljeni); prostaOd =
       konec zadnje tekme na prosti mizi (prazno, ce se ni bila v rabi);
       cakajociSodnik = porazenec zadnje tekme, ki naj sodi naslednjo; predlogi =
       id-ji tekem (prvi je predlog za to mizo). */
    public record MizaDto(int stevilka, boolean izklopljena, TekmaMizeDto tekma,
                          LocalDateTime prostaOd, OsebaDto cakajociSodnik, List<Long> predlogi) {}

    /* Tekma z vsem, kar potrebuje stran Mize: TekmaDto (okno za vnos izida),
       kategorija in del (»Skupina B«, »polfinale«), klic in osebe (id-ji igralcev,
       da vmesnik ve, ali predlagani sodnik igra v tej tekmi). */
    public record TekmaMizeDto(TekmaDto tekma, Long idDogodka, String imeDogodka, String del,
                               boolean ekipno, Long idSrecanje, Integer miza, Integer miza2,
                               LocalDateTime poklicanaOb, OsebaDto sodnik, List<Long> osebe) {}

    /* zaseden = kdo od udelezencev zdaj igra ali sodi (tekma ni predlagana, sme pa
       se poklicati); razlogi so kratke opombe za organizatorja. */
    public record KandidatDto(TekmaMizeDto tekma, boolean zaseden, List<String> razlogi) {}

    public record OsebaDto(Long id, String ime) {}
}
