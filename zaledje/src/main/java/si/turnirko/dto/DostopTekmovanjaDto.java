/* Kdo od organizatorjev ima dostop do turnirja ali lige in ali mu tekmovanje
   steje v mejo paketa (okno »Organizatorji«, samo admin). Nosi e-poste
   organizatorjev, zato ga javna pot nikoli ne vrne.

   `organizatorji` so tisti, ki jih zajame dostop prek lastnistva ali
   dodelitve (lastnik prvi); organizatorji istega kluba imajo dostop
   samodejno in jih seznam ne našteva - nosi ga samo ime kluba. `kandidati`
   so aktivni organizatorji, ki jih je se mogoce dodati. */
package si.turnirko.dto;

import java.util.List;

import si.turnirko.modeli.Paket;

public record DostopTekmovanjaDto(
        // klub, čigar organizatorji imajo dostop samodejno; null = tekmovanje ni klubsko
        String klubLastnik,
        List<Organizator> organizatorji,
        List<Kandidat> kandidati
) {

    /* LASTNIK je ustvaril tekmovanje (dostopa mu ni mogoce odvzeti, spremeni se
       lahko samo stetje v mejo); DODELJEN je dostop, ki ga je odobril admin. */
    public enum Vir { LASTNIK, DODELJEN }

    public record Organizator(
            Long idRacun,
            String ime,
            String eposta,
            String klub,
            Vir vir,
            // ali tekmovanje steje v mejo organizatorjevega paketa
            boolean steje,
            Poraba poraba
    ) {}

    /* Poraba meje za VRSTO tega tekmovanja (turnir ali liga) v tekoci sezoni;
       meja je null, kadar organizator nima organizatorskega paketa. */
    public record Poraba(Paket paket, int uporabljeno, Integer meja) {}

    public record Kandidat(Long idRacun, String ime, String eposta, String klub) {}
}
