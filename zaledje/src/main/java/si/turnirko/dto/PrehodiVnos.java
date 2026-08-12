/* Vnos prehodov (mesta lige v piramidi): kam se iz nje napreduje, katere lige
   so pod njo in koliko ekip se ob koncu sezone premakne.

   Povezava je v bazi ena sama (nizja liga kaze na visjo prek id_visja_liga),
   tu pa jo je mogoce zapisati z obeh strani: idVisjaLiga jo nastavi navzgor,
   idNizjeLige pa navzdol - drugace bi bilo treba za vsako nizjo ligo posebej
   odpirati njen obrazec (in ce ta ni vec v pripravi, sploh ne bi slo).

   idNizjeLige = null pomeni "ne dotikaj se nizjih lig", prazen seznam pomeni
   "nobena liga ni pod to". */
package si.turnirko.dto;

import java.util.List;

public record PrehodiVnos(
        Long idVisjaLiga,
        List<Long> idNizjeLige,
        Integer stNapreduje,
        Integer stIzpade
) {}
