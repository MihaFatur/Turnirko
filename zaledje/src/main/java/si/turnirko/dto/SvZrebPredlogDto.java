/* Predlog razporeditve igralcev po mestih v mrezi enega zreba (SV_REGIJA), brez
   zapisa v bazo. Mesta so po vrsti od vrha mreze navzdol; sosednji mesti
   (2i, 2i+1) igrata med seboj v prvem kolu. Prazno mesto (null) je prosto
   (bye): nasprotnik gre naprej brez igranja. */
package si.turnirko.dto;

import java.util.List;

public record SvZrebPredlogDto(int velikostMreze, List<SvPredlogDto.ClanDto> mesta) {}
