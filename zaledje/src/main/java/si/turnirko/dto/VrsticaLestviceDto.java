/* Ena vrstica lestvice (krozni sistem ali skupina): povzetek uspeha
   enega udelezenca. Razlika nizov (niziZa - niziProti) je glavno merilo
   ob enakem stevilu zmag. */
package si.turnirko.dto;

public record VrsticaLestviceDto(
        Long idPrijave,
        Long idIgralca,
        String polnoIme,
        String klub,
        int odigrane,
        int zmage,
        int porazi,
        int niziZa,
        int niziProti,
        Integer mesto
) {}
