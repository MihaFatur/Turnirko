/* Liga - izpis konfiguracije in stanja. */
package si.turnirko.dto;

import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.Liga;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.StatusTekmovanja;

public record LigaDto(
        Long id,
        String ime,
        String sezona,
        SpolKategorija spolKategorija,
        FormatSrecanja formatSrecanja,
        int steviloNizov,
        Integer zmagZaSrecanje,
        boolean dvokrozno,
        int tockeZmaga,
        int tockeNeodloceno,
        int tockePoraz,
        boolean dovoljenoNeodloceno,
        boolean prepovedDvojneRegistracije,
        boolean stejeVElo,
        Long idVisjaLiga,
        String visjaLigaIme,
        int stNapreduje,
        int stIzpade,
        StatusTekmovanja status,
        int steviloEkip,
        // Lastnistvo (glej TurnirDto) - vmesnik po njiju pokaze urejanje le
        // lastniku; streznik je zadnja obramba (LastnistvoStoritev).
        Long idLastnik,
        Long idKlubLastnik,
        String klubLastnik
) {

    public static LigaDto iz(Liga l, int steviloEkip) {
        return new LigaDto(
                l.getId(), l.getIme(), l.getSezona(), l.getSpolKategorija(),
                l.getFormatSrecanja(), l.getSteviloNizov(), l.getZmagZaSrecanje(),
                l.isDvokrozno(), l.getTockeZmaga(), l.getTockeNeodloceno(), l.getTockePoraz(),
                l.isDovoljenoNeodloceno(), l.isPrepovedDvojneRegistracije(), l.isStejeVElo(),
                l.getVisjaLiga() != null ? l.getVisjaLiga().getId() : null,
                l.getVisjaLiga() != null ? l.getVisjaLiga().getIme() : null,
                l.getStNapreduje(), l.getStIzpade(), l.getStatus(), steviloEkip,
                l.getUstvaril() != null ? l.getUstvaril().getId() : null,
                l.getKlubLastnik() != null ? l.getKlubLastnik().getId() : null,
                l.getKlubLastnik() != null ? l.getKlubLastnik().getIme() : null);
    }
}
