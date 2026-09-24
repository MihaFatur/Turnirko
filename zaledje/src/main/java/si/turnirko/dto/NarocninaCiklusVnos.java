/* Izbran cikel placevanja pri preklopu (PUT /api/v1/narocnina/preklop) in
   obnovi preklicane narocnine (POST /api/v1/narocnina/obnova). Pri preklopu je
   cikel obvezen (storitev to preveri), pri obnovi ne: brez njega se obnova
   nadaljuje z ze zacetim ciklom. */
package si.turnirko.dto;

import si.turnirko.modeli.CiklusPlacila;

public record NarocninaCiklusVnos(CiklusPlacila ciklus) {}
