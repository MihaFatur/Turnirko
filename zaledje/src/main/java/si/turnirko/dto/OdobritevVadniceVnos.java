/* Odobritev lista vadnice: stevke, ki jih admin izloci (napacno napisane ali
   narobe razrezane), ostale gredo v ucenje. List ima najvec 180 stevk. */
package si.turnirko.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OdobritevVadniceVnos(@Size(max = 400) List<@NotNull Long> izlocene) {}
