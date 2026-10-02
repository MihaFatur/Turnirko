/* Kvalifikacije med visjo in nizjo ligo, kakrsne bi nastale zdaj: kdo jih
   igra (po lestvicah obeh lig), krizni pari in kaj nastanku se stoji na
   poti. Javno kot vsak GET - izpeljano je iz javnih lestvic.

   Pred koncem rednega dela so ekipe TRENUTNE (koncano = false); ustvariti se
   kvalifikacije dajo sele iz koncnih lestvic. */
package si.turnirko.dto;

import java.util.List;

public record KvalifikacijePredlogDto(
        Long idVisja,
        String visja,
        Long idNizja,
        String nizja,
        // koliko ekip po prehodih da visja liga (tik nad izpadom) in koliko
        // nizja (tik pod napredovanjem)
        int izVisje,
        int izNizje,
        // ali je redni del obeh lig odigran - lestvici sta koncni
        boolean koncano,
        List<Udelezenec> ekipeVisje,
        List<Udelezenec> ekipeNizje,
        // krizni pari; prazno, kadar lige ne dajo enako ekip (takrat je
        // mogoca le mala liga)
        List<Par> pari,
        // zakaj kvalifikacij zdaj ni mogoce ustvariti (prazno = mogoce)
        List<String> ovire,
        // ze ustvarjena liga kvalifikacij (null = se je ni)
        Long idKvalifikacije,
        String predlaganoIme
) {

    public record Udelezenec(Long idEkipa, String ekipa, int mesto) {}

    public record Par(Udelezenec visja, Udelezenec nizja) {}
}
