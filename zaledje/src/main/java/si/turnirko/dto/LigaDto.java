/* Liga - izpis konfiguracije in stanja. */
package si.turnirko.dto;

import java.time.LocalDateTime;

import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.Liga;
import si.turnirko.modeli.PredlogaLige;
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
        // Zreb po parih: ekipe imajo jakostni vrstni red (EkipaDto.stNosilca)
        // in razpored jih zveze v pare - zgornja polovica s spodnjo.
        boolean enakomernaRazvrstitev,
        PredlogaLige predlogaListka,
        // Seme terminov (glej Liga): iz njiju se ob zrebu izracunajo datumi
        // kol. Obrazec lige ju prikaze nazaj, ko se pravila urejajo.
        LocalDateTime zacetekPrvegaKola,
        Integer razmikDni,
        Long idVisjaLiga,
        String visjaLigaIme,
        int stNapreduje,
        int stIzpade,
        StatusTekmovanja status,
        int steviloEkip,
        // Napredek lige: koliko kol ima razpored in koliko jih je odigranih
        // (kolo je odigrano, ko je koncano vsako njegovo srecanje). Vrstica
        // lige na telefonu iz tega izpise "7. od 18 kol" in palico; brez
        // razporeda sta oba nic.
        int odigranihKol,
        int steviloKol,
        // Ali liga stoji v sklopu "Lige" na domaci strani (najvec dve, izbere
        // admin). Javno polje, ker po njem vmesnik oznaci ligo v izboru in ve,
        // kaj naj domaca stran pokaze gostu.
        boolean naDomaci,
        // Lastnistvo (glej TurnirDto) - vmesnik po njiju pokaze urejanje le
        // lastniku; streznik je zadnja obramba (LastnistvoStoritev).
        Long idLastnik,
        Long idKlubLastnik,
        String klubLastnik
) {

    /* Liga brez razporeda (nova, urejena, prehodi) - kol se ni. */
    public static LigaDto iz(Liga l, int steviloEkip) {
        return iz(l, steviloEkip, 0, 0);
    }

    public static LigaDto iz(Liga l, int steviloEkip, int odigranihKol, int steviloKol) {
        return new LigaDto(
                l.getId(), l.getIme(), l.getSezona(), l.getSpolKategorija(),
                l.getFormatSrecanja(), l.getSteviloNizov(), l.getZmagZaSrecanje(),
                l.isDvokrozno(), l.getTockeZmaga(), l.getTockeNeodloceno(), l.getTockePoraz(),
                l.isDovoljenoNeodloceno(), l.isPrepovedDvojneRegistracije(), l.isStejeVElo(),
                l.isEnakomernaRazvrstitev(), l.getPredlogaListka(),
                l.getZacetekPrvegaKola(), l.getRazmikDni(),
                l.getVisjaLiga() != null ? l.getVisjaLiga().getId() : null,
                l.getVisjaLiga() != null ? l.getVisjaLiga().getIme() : null,
                l.getStNapreduje(), l.getStIzpade(), l.getStatus(), steviloEkip,
                odigranihKol, steviloKol, l.isNaDomaci(),
                l.getUstvaril() != null ? l.getUstvaril().getId() : null,
                l.getKlubLastnik() != null ? l.getKlubLastnik().getId() : null,
                l.getKlubLastnik() != null ? l.getKlubLastnik().getIme() : null);
    }
}
