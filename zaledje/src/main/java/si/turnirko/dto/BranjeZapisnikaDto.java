/* Branje ligaskega zapisnika za stran Branje zapisnikov. V seznamu brez
   podrobnosti (podrobnosti = null), v oknu pregleda z njimi.

   Predlog: za vsako tekmo kdo je igral (id igralcev, null = nov igralec, ki ga
   organizator izbere), tocke nizov in kaj je negotovo (rjasto ob izrezu). */
package si.turnirko.dto;

import java.time.LocalDateTime;
import java.util.List;

import si.turnirko.branje.GeometrijaZapisnika;
import si.turnirko.modeli.BranjeZapisnika;
import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.StatusSrecanja;

public record BranjeZapisnikaDto(Long id, BranjeZapisnika.Stanje stanje, String napaka, String opomba,
                                 boolean samodejno, boolean zapisanoSamodejno, Boolean zanesljivo,
                                 LocalDateTime ustvarjenoOb, boolean obviselo, boolean slika,
                                 Srecanje srecanje, Predlog predlog, Podrobnosti podrobnosti) {

    /* vpisano: srecanje ze ima izide (zapis ga bo prepisal). */
    public record Srecanje(Long id, Long idLiga, String tekmovanje, String opis, Integer kolo, String domaci,
                           String gost, StatusSrecanja status, int dobljeneDomaci, int dobljeneGost,
                           boolean vpisano) {}

    public record Niz(Integer domaci, Integer gost, boolean negotovoDomaci, boolean negotovoGost) {}

    public record Tekma(String oznaka, boolean dvojice, List<Long> domaci, List<Long> gost,
                        boolean negotovoDomaci, boolean negotovoGost, List<Niz> nizi, String stanje,
                        int niziDomaci, int niziGost) {}

    /* negotovih: polj (igralec ali celica), ki jih mora organizator pogledati. */
    public record Predlog(List<Tekma> tekme, boolean zanesljiv, List<String> opozorila, int negotovih) {}

    public record Igralec(Long id, String ime) {}

    /* kaderNatisaDomaci: vrstice natisnjenega kadra (za prikaz); kaderDomaci: vsi
       igralci kadra ekipe zdaj (izbira ob popravku, tudi novi). potrjeno: zapis, ki je
       sel v srecanje (zapisano branje) - popravek zacne iz njega, ne iz branja, sicer bi
       se organizatorjevi prejsnji popravki izgubili. */
    public record Podrobnosti(GeometrijaZapisnika.Oblika oblika, FormatSrecanja format, int steviloNizov,
                              Integer zmagZaSrecanje, List<Igralec> kaderNatisaDomaci,
                              List<Igralec> kaderNatisaGost, List<Igralec> kaderDomaci, List<Igralec> kaderGost,
                              CeliceZapisnikaDto celice, boolean poravnana, ZapisZapisnikaVnos potrjeno) {}
}
