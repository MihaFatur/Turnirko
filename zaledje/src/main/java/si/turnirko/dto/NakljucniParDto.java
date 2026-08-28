/* Nakljucni par za semafor "1 na 1" na domaci strani. Vrneta se samo id-ja:
   sifrant igralcev ima vmesnik ze nalozen (izbirnika ob semaforju), izid pa
   itak prebere z /dvoboj. */
package si.turnirko.dto;

public record NakljucniParDto(
        Long prvi,
        Long drugi
) {}
