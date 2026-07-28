/* Administratorjeva potrditev organizatorja: (neobvezni) klub, ki mu ga
   dodeli. Brez kluba organizator upravlja samo svoja tekmovanja. */
package si.turnirko.dto;

public record PotrditevOrganizatorjaVnos(
        /* Neobvezen - organizator je lahko brez kluba. */
        Long idKlub
) {}
