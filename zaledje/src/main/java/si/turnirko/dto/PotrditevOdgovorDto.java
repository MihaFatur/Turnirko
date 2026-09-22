/* Kaj se je zgodilo po vpisu pravilne kode - vmesnik po tem izbere naslednji
   korak: prijava (racun je povezan), skrbnikova koda ali cakanje na admina. */
package si.turnirko.dto;

public record PotrditevOdgovorDto(
        /* Racun je bil samodejno povezan z igralcem in je pripravljen. */
        boolean povezan,
        /* Se caka na kodo, ki jo je dobil skrbnik. */
        boolean potrebnaKodaSkrbnika,
        /* Organizatorja vedno potrdi administrator (vloga in klub). */
        boolean organizator
) {}
