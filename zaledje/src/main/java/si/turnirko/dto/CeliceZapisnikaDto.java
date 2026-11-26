/* Lega kvadratkov in celic na poravnanem ligaskem zapisniku (px poravnane slike,
   10 px/mm): vmesnik ob vsakem polju pregleda pokaze izrez fotografije. Samo
   stevilke, brez imen. Shranjeno kot JSON v branje_zapisnika.celice. */
package si.turnirko.dto;

import java.util.List;

public record CeliceZapisnikaDto(int sirina, int visina, List<Kvadratek> kvadratki, List<Celica> celice) {

    public record Kvadratek(int tekma, boolean domaci, int igralec, int x0, int y0, int x1, int y1) {}

    public record Celica(int tekma, int niz, boolean domaci, int x0, int y0, int x1, int y1) {}
}
