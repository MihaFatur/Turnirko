/* Kaj se je zgodilo po vpisu pravilne kode - vmesnik po tem izbere naslednji
   korak: prijava (racun je povezan ali je potrjen navijac), skrbnikova koda
   ali cakanje na admina. */
package si.turnirko.dto;

import java.time.LocalDate;

public record PotrditevOdgovorDto(
        /* Racun je bil samodejno povezan z igralcem in je pripravljen. */
        boolean povezan,
        /* Se caka na kodo, ki jo je dobil skrbnik. */
        boolean potrebnaKodaSkrbnika,
        /* Organizatorja vedno potrdi administrator (vloga in klub). */
        boolean organizator,
        /* Zadnji dan preizkusa Premium, ki se je zacel s povezavo (koledarski
           dan po slovenskem casu); null, ce preizkusa ni (racun ni povezan,
           je organizator ali je narocnino ze imel). */
        LocalDate preizkusDo,
        /* Racun navijaca je potrjen in pripravljen (admina ne caka). */
        boolean navijac
) {}
