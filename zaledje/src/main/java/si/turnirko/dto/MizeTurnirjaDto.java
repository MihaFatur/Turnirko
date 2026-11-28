/* Stran Mize (M1, M2; docs/pravila/mize.md): nastavitve miz turnirja, stanje vsake
   mize, cakajoci sodniki in pripravljene tekme v vrstnem redu predloga. Samo za
   organizatorja turnirja in admina (MizeStoritev, VarnostneNastavitve).

   Skupne mize (V78): ce si turnir deli mize z drugimi turnirji (ista dvorana), so
   nastavitve, mize in vrsta skupne za vse - stran Mize kateregakoli od njih kaze isto. */
package si.turnirko.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import si.turnirko.modeli.StatusTekmovanja;

public record MizeTurnirjaDto(
        Long idTurnir,
        String imeTurnirja,
        /* null = mize niso nastavljene (klic ni mogoc) */
        Integer steviloMiz,
        List<Integer> izklopljeneMize,
        boolean porazenecSodi,
        int pocitekMin,
        /* vsaj ena kategorija turnirjev s skupnimi mizami tece */
        boolean dogodkiVTeku,
        /* minute igre in pocitka vmesnik racuna od ure streznika, ne prenosnika */
        LocalDateTime casStreznika,
        List<MizaDto> mize,
        /* pripravljene tekme vseh kategorij v teku, po vrsti predloga */
        List<KandidatDto> kandidati,
        /* vrsto je uredil organizator (vsaj ena tekma vrste je v rocnem vrstnem redu) */
        boolean rocniVrstniRed,
        SkupneMizeDto skupne
) {

    /* tekma = kar se igra na mizi (prazno pri prosti in izklopljeni); prostaOd =
       konec zadnje tekme na prosti mizi (prazno, ce se ni bila v rabi);
       cakajociSodnik = porazenec zadnje tekme, ki naj sodi naslednjo; predlogi =
       id-ji tekem (prvi je predlog za to mizo). */
    public record MizaDto(int stevilka, boolean izklopljena, TekmaMizeDto tekma,
                          LocalDateTime prostaOd, OsebaDto cakajociSodnik, List<Long> predlogi) {}

    /* Tekma z vsem, kar potrebuje stran Mize: TekmaDto (okno za vnos izida),
       kategorija in del (»Skupina B«, »polfinale«), klic in osebe (id-ji igralcev,
       da vmesnik ve, ali predlagani sodnik igra v tej tekmi). Turnir loci enako
       imenovane kategorije turnirjev s skupnimi mizami. */
    public record TekmaMizeDto(TekmaDto tekma, Long idDogodka, String imeDogodka, String del,
                               boolean ekipno, Long idSrecanje, Integer miza, Integer miza2,
                               LocalDateTime poklicanaOb, OsebaDto sodnik, List<Long> osebe,
                               Long idTurnir, String imeTurnirja) {}

    /* zaseden = kdo od udelezencev zdaj igra ali sodi (tekma ni predlagana, sme pa
       se poklicati); razlogi so kratke opombe za organizatorja. */
    public record KandidatDto(TekmaMizeDto tekma, boolean zaseden, List<String> razlogi) {}

    public record OsebaDto(Long id, String ime) {}

    /* Turnirji, ki si delijo mize (glavni prvi; en sam = ni povezave), in kategorije
       vseh v vrstnem redu kola, ki ga uporablja predlog. redNastavljen = organizator
       je kategorije razvrstil sam (sicer privzeti red po turnirju in nastanku). */
    public record SkupneMizeDto(List<TurnirMizDto> turnirji, List<KategorijaMizDto> kategorije,
                                boolean redNastavljen) {}

    public record TurnirMizDto(Long id, String ime, LocalDate datumZacetka) {}

    /* kola = kola skupin v vrstnem redu igranja (V80; prazno, dokler kategorija nima
       zreba skupin ali ni v teku); redKolNastavljen = organizator je kola razvrstil sam
       (sicer 1, 2, 3 ...). */
    public record KategorijaMizDto(Long idDogodka, String ime, Long idTurnir, String imeTurnirja,
                                   StatusTekmovanja status, boolean ekipno, List<Integer> kola,
                                   boolean redKolNastavljen) {}
}
