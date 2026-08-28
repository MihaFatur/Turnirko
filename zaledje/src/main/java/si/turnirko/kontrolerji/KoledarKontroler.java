/* Koncna tocka koledarja: kaj se dogaja v danem obdobju.

   Javna kot ostali GET-i - koledar je prvi odgovor na vprasanje "kdaj je
   naslednji turnir" in ga mora videti tudi neprijavljen gledalec. */
package si.turnirko.kontrolerji;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import si.turnirko.dto.KoledarVnosDto;
import si.turnirko.storitve.KoledarStoritev;

@RestController
@RequestMapping("/api/v1/koledar")
public class KoledarKontroler {

    private final KoledarStoritev koledarStoritev;

    public KoledarKontroler(KoledarStoritev koledarStoritev) {
        this.koledarStoritev = koledarStoritev;
    }

    /* Obdobje je vkljucno na obeh straneh (od <= dan <= do). */
    @GetMapping
    public List<KoledarVnosDto> vObdobju(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate od,
            @RequestParam("do") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate doKdaj) {
        return koledarStoritev.vObdobju(od, doKdaj);
    }
}
