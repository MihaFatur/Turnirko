/* Izbran cikel placevanja pri preklopu (PUT /api/v1/narocnina/preklop); cikel
   je obvezen (storitev to preveri). Obnova preklicane narocnine ima svoj vnos,
   NarocninaObnovaVnos, ker ob obnovi organizator izbira tudi paket. */
package si.turnirko.dto;

import si.turnirko.modeli.CiklusPlacila;

public record NarocninaCiklusVnos(CiklusPlacila ciklus) {}
