/* Prijavljeni uporabnik - izpis (nikoli ne vsebuje gesla ne zgostitve).
   Pri igralcu pove tudi, ali je racun ze potrjen in s katerim igralcem je
   povezan; vmesnik po tem ve, ali sme ponuditi "Moj profil". Zastavici o
   potrditvi naslova in skrbniku vmesniku povesta, ali naj racunu ponudi
   vpis kode namesto cakanja. */
package si.turnirko.dto;

import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;

public record UporabnikDto(
        // id racuna - vmesnik ga primerja z idLastnik turnirja/lige, da pokaze
        // urejanje le lastniku (obcutljiv ni; streznik je zadnja obramba)
        Long id,
        String uporabniskoIme,
        Vloga vloga,
        StatusRacuna status,
        Long idIgralec,
        String imeIgralca,
        // klub organizatorja - po njem vmesnik pokaze urejanje klubskih tekmovanj
        Long idKlub,
        String klub,
        // ali je lastnik naslova vpisal kodo s poste
        boolean emailPotrjen,
        // racun mlajsega od 15 let se caka na skrbnikovo kodo
        boolean potrebnaKodaSkrbnika
) {

    public static UporabnikDto iz(Uporabnik uporabnik) {
        return new UporabnikDto(
                uporabnik.getId(),
                uporabnik.getUporabniskoIme(),
                uporabnik.getVloga(),
                uporabnik.getStatus(),
                uporabnik.getIgralec() != null ? uporabnik.getIgralec().getId() : null,
                uporabnik.getIgralec() != null ? uporabnik.getIgralec().polnoIme() : null,
                uporabnik.getKlub() != null ? uporabnik.getKlub().getId() : null,
                uporabnik.getKlub() != null ? uporabnik.getKlub().getIme() : null,
                uporabnik.jeEmailPotrjen(),
                uporabnik.cakaSkrbnika());
    }
}
