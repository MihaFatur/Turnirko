/* Obstojec igralec, ki je podoben vpisu - za organizatorja.

   Nosi IgralecJavniDto in nic vec: organizator lahko doda igralca, datumov
   rojstva drugih pa ne vidi (glej IgralecJavniDto). Klub in starostni pas
   zadostujeta, da vpisovalec presodi, ali gre za isto osebo. Administratorju
   gre isto s polnim zapisom (PodobenIgralecDto). */
package si.turnirko.dto;

import si.turnirko.modeli.UjemanjeImena;

public record PodobenIgralecJavniDto(
        IgralecJavniDto igralec,
        UjemanjeImena ujemanje,
        // arhiviran igralec ni na seznamih za prijavo, a ima zgodovino:
        // vpis novega bi jo pustil na starem zapisu
        boolean arhiviran
) {}
