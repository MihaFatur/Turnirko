/* Profilna slika v adminovem pregledu (V56): čakajoče na potrditev in nedavno
   objavljene (te admin lahko odstrani - naknadna moderacija). */
package si.turnirko.dto;

import java.time.LocalDateTime;

import si.turnirko.modeli.StanjeProfilneSlike;

public record ProfilnaSlikaPregledDto(
        Long id,
        Long idIgralec,
        String ime,
        String zgostitev,
        StanjeProfilneSlike stanje,
        LocalDateTime nalozenaOb,
        // Zakaj caka (mladoletnik, samodejna povezava); null pri objavljeni.
        String razlogCakanja,
        // Ali je slika ta trenutek javna (objavljena IN racun ima Premium).
        boolean javna
) {}
