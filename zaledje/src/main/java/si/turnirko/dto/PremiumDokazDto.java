/* Socialni dokaz v oglasu Igralec Premium (GET /api/v1/premium/dokaz):
   koliko igralcev ze placuje Premium. Stevilo 0 pomeni "vrstice ni" - vmesnik
   je takrat sploh ne izrise (stevilo, ki bi ga pokazali, je pod mejo ali ga ni).

   izKluba pove, KATERO stevilo je: igralcev iz kluba prijavljenega igralca (true)
   ali vseh igralcev s Premium (false, za gosta in za igralca brez kluba). */
package si.turnirko.dto;

public record PremiumDokazDto(int stevilo, boolean izKluba) {

    public static final PremiumDokazDto PRAZEN = new PremiumDokazDto(0, false);
}
