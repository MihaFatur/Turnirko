/* Meritev branja listkov (F1, samo admin): kako dobro bralnik bere in koliko
   dela ima organizator. Racuna MeritevBranja; pomen stevil je opisan pri poljih
   in v docs/pravila/branje-listkov.md (razdelek »Meritev«).

   skupaj je celotno obdobje (in turnir, ce je izbran), poTurnirjih in poMrezah
   isto po turnirjih (najnovejsi zgoraj) in po razlicicah mreze, ki je brala. */
package si.turnirko.dto;

import java.time.LocalDate;
import java.util.List;

public record MeritevBranjaDto(
        LocalDate od,
        LocalDate doDatuma,
        Long idTurnir,
        Skupina skupaj,
        List<Skupina> poTurnirjih,
        List<Skupina> poMrezah
) {

    public record Skupina(
            /* ime turnirja, oznaka mreze ali »Skupaj« */
            String oznaka,
            /* samo pri skupini turnirja */
            Long idTurnir,
            LocalDate datum,
            /* vse fotografije (branja) */
            int branj,
            /* bralnik je dal predlog */
            int prebranih,
            /* branje ni uspelo: fotografijo je bilo treba ponoviti ali izid vpisati rocno */
            int neuspelih,
            /* potrjena branja s predlogom */
            int potrjenih,
            /* potrjena natanko, kot je predlagal bralnik */
            int brezPopravka,
            /* predlog je bil ponujen za potrditev z enim dotikom */
            int pripravljenih,
            /* potrjena branja, pri katerih je bilo mogoce primerjati celice */
            int izmerjenih,
            /* pripravljen predlog z vsaj eno napacno celico (z enim dotikom bi
               napacen izid sel naprej); pri potrditvah brez kontrole jih vidimo
               samo, ce jih je organizator vseeno popravil */
            int tihihNapak,
            /* nakljucne kontrole: pregledane v celoti, zato nepristranska ocena */
            int kontrol,
            int kontrolZNapako,
            Celice celice,
            Casi casi
    ) {}

    /* Celice potrjenih listkov: prav ali narobe prebrana x zanesljiva ali
       oznacena za pregled (negotova, neveljaven ali neprebran niz). */
    public record Celice(int pravZanesljive, int pravNegotove, int narobeNegotove, int narobeZanesljive) {}

    /* Cas pregleda (ms, meri vmesnik) po potrditvah, ki ga imajo zapisanega -
       tudi pri neuspelem branju, vpisanem rocno. */
    public record Casi(int izmerjenih, Integer mediana, Integer p90,
                       int enDotik, Integer medianaEnDotik,
                       int obrazec, Integer medianaObrazec) {}
}
