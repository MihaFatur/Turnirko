/* Porocilo predogleda oz. uvoza dogodka iz Stupe (samo admin - nosi datume
   rojstva, zato gre le po poteh /api/v1/uvoz/**, ki jih varnostna veriga
   omeji na ADMIN).

   napake       - uvoz ni mogoc (nic se ne zapise)
   odlocitve    - istovetnost osebe, ki je strojno ni mogoce varno dolociti;
                  dokler katera ni razresena, je uvoz zavrnjen
   opozorila    - uvoz je mogoc, a nekaj pri viru ni tako, kot bi moralo biti
                  (izpuscene neodigrane tekme, neveljavne tocke niza ...)
   preverbe     - uskladitev zapisanega z virom: stevila, zmagovalci, uradna
                  mesta skupin in lestvice */
package si.turnirko.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record PorociloUvozaDto(
        Map<String, Integer> stevci,
        List<Ugotovitev> napake,
        List<Ugotovitev> opozorila,
        List<Odlocitev> odlocitve,
        List<NovIgralec> noviIgralci,
        List<Preverba> preverbe
) {

    /* Ena vrsta ugotovitve s stevilom pojavitev in nekaj primeri. */
    public record Ugotovitev(String vrsta, int stevilo, List<String> primeri) {}

    /* Oseba iz vira, za katero se mora odlociti admin: povezi z enim od
       kandidatov ali ustvari novega igralca.
       predlogIme/predlogPriimek - razdelitev imena za novega igralca (null, ce
                                   je ime iz ene besede)
       manjka                     - kaj mora admin za novega igralca vpisati, ker
                                   vir tega nima: IME, ROJSTVO, SPOL */
    public record Odlocitev(long idOsebe, String ime, LocalDate rojstvo, String spol, String licenca,
                            String klub, String razlog, List<Kandidat> kandidati,
                            String predlogIme, String predlogPriimek, List<String> manjka) {}

    public record Kandidat(long idIgralec, String polnoIme, LocalDate rojstvo, String licenca, String klub) {}

    /* Igralec, ki ga bo uvoz ustvaril. "zanesljivo" pove, ali je razdelitev
       imena na ime in priimek zanesljiva (sicer jo je vredno pregledati). */
    public record NovIgralec(long idOsebe, String polnoIme, String ime, String priimek, boolean zanesljivo,
                             LocalDate rojstvo, String licenca, String klub) {}

    /* Ena preverba uskladitve z virom. "obvezna" pove, ali neujemanje ustavi
       uvoz: stevila tekem, zmagovalci in mreza morajo biti natanko taki kot
       pri viru, uradno mesto v skupini pa se lahko razlikuje po pravilu
       razvrscanja (Turnirko izenacene loci po razliki, glej RazvrstitevStoritev)
       - to admin vidi, uvoza pa ne ustavi. */
    public record Preverba(String podrocje, String opis, boolean ujemanje, boolean obvezna, String podrobnosti) {}
}
