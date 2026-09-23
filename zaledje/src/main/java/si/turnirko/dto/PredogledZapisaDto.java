/* Odgovor na predogled zapisa (glej PredogledZapisaVnos). "najden = false"
   pomeni isto kot pri samodejni povezavi - nobenega zadetka, dva zadetka ali
   zadetek, ki ze ima racun; v vseh treh primerih poveze sele administrator. */
package si.turnirko.dto;

public record PredogledZapisaDto(
        boolean najden,
        Long idIgralec,
        String ime,
        String priimek,
        String klub,
        Integer rating,
        int steviloTekem
) {
    public static final PredogledZapisaDto BREZ_ZADETKA =
            new PredogledZapisaDto(false, null, null, null, null, null, 0);

    public static PredogledZapisaDto iz(IgralecJavniDto igralec) {
        return new PredogledZapisaDto(true, igralec.id(), igralec.ime(), igralec.priimek(),
                igralec.klub() != null ? igralec.klub().ime() : null,
                igralec.rating(), igralec.steviloTekem());
    }
}
