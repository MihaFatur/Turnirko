/* Prijavljeni uporabnik - izpis (nikoli ne vsebuje gesla ne zgostitve).
   Pri igralcu pove tudi, ali je racun ze potrjen in s katerim igralcem je
   povezan; vmesnik po tem ve, ali sme ponuditi "Moj profil". */
package si.turnirko.dto;

import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;

public record UporabnikDto(
        String uporabniskoIme,
        Vloga vloga,
        StatusRacuna status,
        Long idIgralec,
        String imeIgralca
) {

    public static UporabnikDto iz(Uporabnik uporabnik) {
        return new UporabnikDto(
                uporabnik.getUporabniskoIme(),
                uporabnik.getVloga(),
                uporabnik.getStatus(),
                uporabnik.getIgralec() != null ? uporabnik.getIgralec().getId() : null,
                uporabnik.getIgralec() != null ? uporabnik.getIgralec().polnoIme() : null);
    }
}
