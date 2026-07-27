/* Klub - izpis. */
package si.turnirko.dto;

import si.turnirko.modeli.Klub;

public record KlubDto(Long id, String ime, String kratica) {

    public static KlubDto iz(Klub klub) {
        if (klub == null) return null;
        return new KlubDto(klub.getId(), klub.getIme(), klub.getKratica());
    }
}
