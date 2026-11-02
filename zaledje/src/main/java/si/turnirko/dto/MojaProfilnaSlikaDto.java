/* Profilna slika prijavljenega igralca (V56), kot jo vidi on sam: tudi slika, ki
   čaka na admina, in razlog, zakaj je ne more naložiti ali zakaj čaka. */
package si.turnirko.dto;

import si.turnirko.modeli.StanjeProfilneSlike;

public record MojaProfilnaSlikaDto(
        // Naslov slike; null, ce je ni.
        String zgostitev,
        StanjeProfilneSlike stanje,
        // Ali sme naloziti (povezan racun z veljavnim Premium).
        boolean smeNalagati,
        // Zakaj ne sme oz. zakaj slika caka - stavek za izpis; null, ce ni kaj povedati.
        String pojasnilo
) {}
