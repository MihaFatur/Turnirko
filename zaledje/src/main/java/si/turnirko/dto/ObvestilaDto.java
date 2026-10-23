/* Nastavitev obvestil po kolu za prijavljeni racun: ali jih zeli, ali ima
   potrjen naslov (brez njega posta ne gre) in za katere lige bi jih dobil -
   nezakljucene lige, v katerih igra (IGRA), in tiste, ki jih spremlja
   (SPREMLJA). */
package si.turnirko.dto;

import java.util.List;

public record ObvestilaDto(boolean poKolu, boolean emailPotrjen, List<Liga> lige) {

    public record Liga(Long id, String ime, String sezona, String razlog) {}
}
