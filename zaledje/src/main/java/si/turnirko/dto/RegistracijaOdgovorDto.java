/* Odgovor na registracijo. Namenoma ne pove, ali je racun nastal ali je
   naslov ze zaseden - oboje je izpeljano samo iz VNOSA (naslov, ali je
   potreben skrbnik), zato je odgovor v obeh primerih enak. */
package si.turnirko.dto;

public record RegistracijaOdgovorDto(
        String email,
        /* Ob registraciji mlajsega od 15 let je sla druga koda skrbniku. */
        boolean potrebnaKodaSkrbnika
) {}
