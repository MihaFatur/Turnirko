/* Koncne tocke za statistiko: globalna lestvica igralcev in pregled "1 na 1".
   Oboje je javno (bralni dostop tudi za goste). */
package si.turnirko.kontrolerji;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import si.turnirko.dto.DvobojDto;
import si.turnirko.dto.LestvicaIgralcaDto;
import si.turnirko.dto.NakljucniParDto;
import si.turnirko.dto.ZadnjaTekmaDto;
import si.turnirko.storitve.StatistikaStoritev;

@RestController
@RequestMapping("/api/v1")
public class StatistikaKontroler {

    private final StatistikaStoritev statistikaStoritev;

    public StatistikaKontroler(StatistikaStoritev statistikaStoritev) {
        this.statistikaStoritev = statistikaStoritev;
    }

    /* Globalna lestvica igralcev po klubskem ELO. */
    @GetMapping("/lestvica")
    public List<LestvicaIgralcaDto> lestvica() {
        return statistikaStoritev.globalnaLestvica();
    }

    /* Zgodovina medsebojnih tekem dveh igralcev (izidi z vidika prvega). */
    @GetMapping("/dvoboj")
    public DvobojDto dvoboj(@RequestParam Long prvi, @RequestParam Long drugi) {
        return statistikaStoritev.dvoboj(prvi, drugi);
    }

    /* Nakljucni par za semafor "1 na 1": igralca, ki sta ze igrala drug proti
       drugemu (dokler je v bazi vsaj ena odigrana tekma). */
    @GetMapping("/dvoboj/nakljucni")
    public NakljucniParDto nakljucniPar() {
        return statistikaStoritev.nakljucniPar();
    }

    /* Zadnje odigrane tekme cez vse dogodke - za "Zadnji rezultati" na
       domaci strani. Privzeto 8, najvec 20. */
    @GetMapping("/zadnje-tekme")
    public List<ZadnjaTekmaDto> zadnjeTekme(@RequestParam(defaultValue = "8") int koliko) {
        return statistikaStoritev.zadnjeTekme(Math.min(Math.max(koliko, 1), 20));
    }
}
