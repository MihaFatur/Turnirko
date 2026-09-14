/* Prijava na dogodek - izpis.

   Pri dvojicah je prijava PAR: polja z dvojko nosijo drugega igralca in so
   pri posamicnem dogodku prazna. Imeni ostajata loceni (in se ne zlepita v
   en niz), da ju vmesnik lahko izpise v dveh vrsticah kartice mreze.

   Pri ekipnem dogodku (V28) je prijava EKIPA: igralca ni (idIgralca prazen),
   polnoIme je ime ekipe, klub pa klub ekipe - mreza in skupine jo zato
   izpisejo brez posebne veje. idEkipa vodi do kadra. */
package si.turnirko.dto;

import si.turnirko.modeli.Prijava;

public record PrijavaDto(
        Long id,
        Long idIgralca,
        String polnoIme,
        String klub,
        /* Drugi igralec para; null pri posamicni prijavi in pri prijavljenem
           igralcu dvojic, ki soigralca se nima. */
        Long idIgralca2,
        String polnoIme2,
        String klub2,
        Prijava.StatusPrijave status,
        Integer stNosilca,
        Integer ratingObZrebu,
        Integer ratingObZrebu2,
        Integer koncnoMesto,
        /* Trenutni Turnirko rating - podlaga za jakostni vrstni red v pripravi;
           null pomeni, da igralec se nima nobene obracunane tekme. Pri ekipi je
           to predlagana jakost po ratingu kadra. */
        Integer rating,
        Integer rating2,
        Long idSkupina,
        Integer mestoVSkupini,
        Long idEkipa
) {

    public static PrijavaDto iz(Prijava prijava) {
        return iz(prijava, null, null);
    }

    public static PrijavaDto iz(Prijava prijava, Integer rating, Integer rating2) {
        boolean ekipa = prijava.jeEkipa();
        return new PrijavaDto(
                prijava.getId(),
                ekipa ? null : prijava.getIgralec().getId(),
                prijava.jeEkipa() ? prijava.getEkipa().prikazanoIme() : prijava.getIgralec().polnoIme(),
                prijava.getKlubObPrijavi() != null ? prijava.getKlubObPrijavi().getIme() : null,
                prijava.jePar() ? prijava.getIgralec2().getId() : null,
                prijava.jePar() ? prijava.getIgralec2().polnoIme() : null,
                prijava.getKlubObPrijavi2() != null ? prijava.getKlubObPrijavi2().getIme() : null,
                prijava.getStatus(),
                prijava.getStNosilca(),
                prijava.getRatingObZrebu(),
                prijava.getRatingObZrebu2(),
                prijava.getKoncnoMesto(),
                rating,
                rating2,
                prijava.getIdSkupina(),
                prijava.getMestoVSkupini(),
                ekipa ? prijava.getEkipa().getId() : null
        );
    }
}
