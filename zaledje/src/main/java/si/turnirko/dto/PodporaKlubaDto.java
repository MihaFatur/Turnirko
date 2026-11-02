/* Podpora klubu (V61): navijacev pogled (kateri klub podpira in koliko podpornikov ima
   ta klub) in organizatorjev (popust njegovega kluba). Stevilo podpornikov je zbirno:
   imen podpornikov ne dobi nihce, tudi organizator kluba ne. */
package si.turnirko.dto;

public record PodporaKlubaDto(
        // racun podpira klub (navijacev pogled)
        boolean izbran,
        // racun s paketom Navijac sme izbrati klub
        boolean smemIzbrati,
        Long idKlub,
        String klub,
        Popust popust
) {

    /* Stevilo podpornikov z veljavnim paketom, popust, ki ga klub zdaj ima, in naslednja
       stopnja (null pri najvecjem popustu). */
    public record Popust(int podpornikov, int odstotek, Integer naslednjaMeja, Integer naslednjiOdstotek) {}
}
