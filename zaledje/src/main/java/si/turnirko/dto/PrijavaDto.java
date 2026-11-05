/* Prijava na dogodek - izpis.

   Pri dvojicah je prijava PAR: polja z dvojko nosijo drugega igralca in so
   pri posamicnem dogodku prazna. Imeni ostajata loceni (in se ne zlepita v
   en niz), da ju vmesnik lahko izpise v dveh vrsticah kartice mreze.

   Pri ekipnem dogodku (V28) je prijava EKIPA: igralca ni (idIgralca prazen),
   polnoIme je ime ekipe, klub pa klub ekipe - mreza in skupine jo zato
   izpisejo brez posebne veje. idEkipa vodi do kadra. */
package si.turnirko.dto;

import java.util.List;

import si.turnirko.modeli.Prijava;

public record PrijavaDto(
        Long id,
        Long idIgralca,
        String polnoIme,
        /* Ime in priimek (prvega) igralca LOCENO: koncna razvrstitev ju izpise v
           dveh vrsticah, priimek pa se iz polnoIme ne da zanesljivo odrezati
           (»Ana Marija Novak«, »Novak Kovac«). Pri ekipi sta null - ime ekipe je
           samo polnoIme. */
        String ime,
        String priimek,
        String klub,
        /* Drugi igralec para; null pri posamicni prijavi in pri prijavljenem
           igralcu dvojic, ki soigralca se nima. */
        Long idIgralca2,
        String polnoIme2,
        /* Ime in priimek drugega igralca LOCENO, iz istega razloga kot pri
           prvem: pogled Udelezenci par izpise v dveh vrsticah, ime v lazji teji,
           priimek v debeli. Null kot pri polnoIme2. */
        String ime2,
        String priimek2,
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
        Long idEkipa,
        /* Priimki igralcev v kadru ekipe, najmocnejsi prvi (kot jih steje
           predlagana jakost). Prazen seznam pri posamicni prijavi in paru;
           pogled Udelezenci ekipe izpise »Kader: Novak · Kos · Petek« brez
           posebne poizvedbe za vsako ekipo. */
        List<String> kader
) {

    public static PrijavaDto iz(Prijava prijava) {
        return iz(prijava, null, null, List.of());
    }

    public static PrijavaDto iz(Prijava prijava, Integer rating, Integer rating2) {
        return iz(prijava, rating, rating2, List.of());
    }

    public static PrijavaDto iz(Prijava prijava, Integer rating, Integer rating2, List<String> kader) {
        boolean ekipa = prijava.jeEkipa();
        return new PrijavaDto(
                prijava.getId(),
                ekipa ? null : prijava.getIgralec().getId(),
                prijava.jeEkipa() ? prijava.getEkipa().prikazanoIme() : prijava.getIgralec().polnoIme(),
                ekipa ? null : prijava.getIgralec().getIme(),
                ekipa ? null : prijava.getIgralec().getPriimek(),
                prijava.getKlubObPrijavi() != null ? prijava.getKlubObPrijavi().getIme() : null,
                prijava.jePar() ? prijava.getIgralec2().getId() : null,
                prijava.jePar() ? prijava.getIgralec2().polnoIme() : null,
                prijava.jePar() ? prijava.getIgralec2().getIme() : null,
                prijava.jePar() ? prijava.getIgralec2().getPriimek() : null,
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
                ekipa ? prijava.getEkipa().getId() : null,
                kader
        );
    }
}
