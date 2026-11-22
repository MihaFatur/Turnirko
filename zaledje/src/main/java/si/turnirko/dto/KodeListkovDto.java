/* Kode QR za tisk listkov dogodka (S1) in stopnja sodniske potrditve turnirja (S2).
   Ko sodniki slikajo listke (sodnikiListki ni null), vmesnik na hrbtno stran
   vsakega lista natisne kodo do strani za sodnika (/s). Samo organizator
   turnirja ali admin (ListkiStoritev.kode). */
package si.turnirko.dto;

import java.util.Map;

import si.turnirko.modeli.SodniskaPotrditev;

public record KodeListkovDto(
        SodniskaPotrditev sodnikiListki,
        // id tekme -> »TK:<id>:<skrivnost>«
        Map<Long, String> kode
) {}
