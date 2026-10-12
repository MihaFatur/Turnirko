/* Izid zdruzitve igralcev: obdrzani igralec po zdruzitvi in ali je bilo
   zaradi prenesenih tekem treba rating preracunati. */
package si.turnirko.dto;

public record ZdruzitevIgralcevDto(
        IgralecDto igralec,
        /* Odvecni je imel ratinske tekme, zato je rating obdrzanega sestavljen
           znova od dneva njegove prve tekme (PreracunRatingaStoritev). */
        boolean preracunan
) {}
