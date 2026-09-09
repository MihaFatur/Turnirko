/* Igralec za javne poglede - tisto, kar sme videti kdorkoli.

   Namenoma NE nosi osebnih podatkov (datum rojstva, e-posta, telefon,
   naslov, kraj, licenca): ta razred je zascita, ki je ni mogoce pozabiti.
   Dokler javne koncne tocke vracajo ta tip, osebni podatek fizicno ne more
   uiti - tudi ce kdo kasneje doda polje v entiteto. Poln izpis (IgralecDto)
   vracata samo koncni tocki /podrobno, ki ju veriga omejuje na ADMIN.

   Vsebina se ujema z javnim profilom igralca (ProfilStoritev): ime, klub,
   igralna roka in rating so del rezultatov tekmovanja, osebni podatki ne. */
package si.turnirko.dto;

import java.time.LocalDate;

import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.IgralnaRoka;
import si.turnirko.modeli.Spol;
import si.turnirko.modeli.StarostniPas;

public record IgralecJavniDto(
        Long id,
        String ime,
        String priimek,
        /* Spol ostane javen, ker doloca kategorijo tekmovanja. */
        Spol spol,
        IgralnaRoka igralnaRoka,
        /* Tekmovalni starostni pas (U11 ... veterani), IZPELJAN iz letnice -
           ne datum rojstva. Groba skupina, po kateri se prijavlja na turnir
           in ki je ob nastopu tako ali tako javna (glej StarostniPas); brez
           nje organizator med tisoc igralci mladincev ne loci. Null, kadar
           igralec letnice nima. */
        StarostniPas starostniPas,
        KlubDto klub,
        Integer rating, // trenutni klubski ELO; null, ce igralec se ni igral
        // stevilo ze odigranih ratinskih tekem; nizko stevilo -> rating je
        // se provizoricen (isti pomen kot v IgralecDto)
        int steviloTekem
) {

    /* Dan, na katerega se izpelje starostni pas, je parameter in ne
       LocalDate.now() v tem razredu: pas tece po SEZONI (rez 1. julija), zato
       mora biti v testu mogoce dolociti, kdaj "danes" je. */
    public static IgralecJavniDto iz(Igralec igralec, Integer rating, int steviloTekem,
                                     LocalDate danes) {
        return new IgralecJavniDto(
                igralec.getId(),
                igralec.getIme(),
                igralec.getPriimek(),
                igralec.getSpol(),
                igralec.getIgralnaRoka(),
                StarostniPas.izpelji(igralec.getDatumRojstva(), danes),
                KlubDto.iz(igralec.getKlub()),
                rating,
                steviloTekem
        );
    }
}
