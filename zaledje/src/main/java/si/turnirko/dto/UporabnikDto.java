/* Prijavljeni uporabnik - izpis (nikoli ne vsebuje gesla ne zgostitve).
   Pri igralcu pove tudi, ali je racun ze potrjen in s katerim igralcem je
   povezan; vmesnik po tem ve, ali sme ponuditi "Moj profil". Zastavici o
   potrditvi naslova in skrbniku vmesniku povesta, ali naj racunu ponudi
   vpis kode namesto cakanja. Paket pove, kateri placilni paket ima racun
   (null = brezplacno oz. admin) in ali je zdaj veljaven - glej Narocnina. */
package si.turnirko.dto;

import java.time.LocalDateTime;

import si.turnirko.modeli.CiklusPlacila;
import si.turnirko.modeli.Narocnina;
import si.turnirko.modeli.Paket;
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
        // Ime osebe za oznako v glavi: ime povezanega igralca, sicer ime in
        // priimek, kot ju je oseba navedla ob registraciji (organizator); null
        // pri adminu. Prej je organizator v glavi videl svojo e-posto.
        String polnoIme,
        // klub organizatorja - po njem vmesnik pokaze urejanje klubskih tekmovanj
        Long idKlub,
        String klub,
        // ali je lastnik naslova vpisal kodo s poste
        boolean emailPotrjen,
        // racun mlajsega od 15 let se caka na skrbnikovo kodo
        boolean potrebnaKodaSkrbnika,
        // null = BREZPLACNO oz. admin (nima vrstice v narocnina)
        Paket paket,
        // ali paket zdaj daje pravice (Narocnina.jeVeljavna) - preklican
        // paket ostane true do konca ze placanega obdobja
        boolean paketAktiven,
        CiklusPlacila ciklus,
        // izpeljanka iz datuma rojstva (kot starostniPas) - NE datum sam, ki
        // je oseben in gre navzven samo prek /podrobno. Pove ceno Premium
        // (CenikStoritev) brez razkritja letnice; null pri organizatorju
        // oz. ce datum rojstva (se) ni znan.
        Boolean starejsiOd21
) {

    public static UporabnikDto iz(Uporabnik uporabnik, Narocnina narocnina, Boolean starejsiOd21) {
        return new UporabnikDto(
                uporabnik.getId(),
                uporabnik.getUporabniskoIme(),
                uporabnik.getVloga(),
                uporabnik.getStatus(),
                uporabnik.getIgralec() != null ? uporabnik.getIgralec().getId() : null,
                uporabnik.getIgralec() != null ? uporabnik.getIgralec().polnoIme() : null,
                polnoIme(uporabnik),
                uporabnik.getKlub() != null ? uporabnik.getKlub().getId() : null,
                uporabnik.getKlub() != null ? uporabnik.getKlub().getIme() : null,
                uporabnik.jeEmailPotrjen(),
                uporabnik.cakaSkrbnika(),
                narocnina != null ? narocnina.getPaket() : null,
                narocnina != null && narocnina.jeVeljavna(LocalDateTime.now()),
                narocnina != null ? narocnina.getCiklus() : null,
                starejsiOd21);
    }

    private static String polnoIme(Uporabnik uporabnik) {
        if (uporabnik.getIgralec() != null) {
            return uporabnik.getIgralec().polnoIme();
        }
        String ime = uporabnik.getPrijavljenoIme() == null ? "" : uporabnik.getPrijavljenoIme().trim();
        String priimek = uporabnik.getPrijavljeniPriimek() == null
                ? "" : uporabnik.getPrijavljeniPriimek().trim();
        String polno = (ime + " " + priimek).trim();
        return polno.isEmpty() ? null : polno;
    }
}
