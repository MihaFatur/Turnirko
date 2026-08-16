/* Koncne tocke za lige: konfiguracija, ekipe, kader, razpored, lestvica.
   Tanek adapter - vsa logika je v LigaStoritev / SrecanjeStoritev.
   Gost sme samo GET; mutacije zahtevajo administratorja (VarnostneNastavitve). */
package si.turnirko.kontrolerji;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import si.turnirko.dto.EkipaDto;
import si.turnirko.dto.EkipaVnos;
import si.turnirko.dto.KaderIgralecDto;
import si.turnirko.dto.KaderVnos;
import si.turnirko.dto.LestvicaDvojiceDto;
import si.turnirko.dto.LestvicaEkipeDto;
import si.turnirko.dto.LestvicaIgralcaLigeDto;
import si.turnirko.dto.LigaDto;
import si.turnirko.dto.LigaVnos;
import si.turnirko.dto.PrehodiVnos;
import si.turnirko.dto.SrecanjeDto;
import si.turnirko.dto.TerminiVnos;
import si.turnirko.storitve.LigaStoritev;
import si.turnirko.storitve.SrecanjeStoritev;

@RestController
@RequestMapping("/api/v1/lige")
public class LigeKontroler {

    private final LigaStoritev ligaStoritev;
    private final SrecanjeStoritev srecanjeStoritev;

    public LigeKontroler(LigaStoritev ligaStoritev, SrecanjeStoritev srecanjeStoritev) {
        this.ligaStoritev = ligaStoritev;
        this.srecanjeStoritev = srecanjeStoritev;
    }

    // ---------- Liga ----------

    @GetMapping
    public List<LigaDto> seznam() {
        return ligaStoritev.vse();
    }

    @GetMapping("/{id}")
    public LigaDto najdi(@PathVariable Long id) {
        return ligaStoritev.najdi(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LigaDto ustvari(@Valid @RequestBody LigaVnos vnos) {
        return ligaStoritev.ustvari(vnos);
    }

    @PutMapping("/{id}")
    public LigaDto uredi(@PathVariable Long id, @Valid @RequestBody LigaVnos vnos) {
        return ligaStoritev.uredi(id, vnos);
    }

    /* Prehodi (mesto v piramidi) so loceni od pravil: pravila se po generiranju
       razporeda zaklenejo, povezave med ligami pa ostanejo popravljive. */
    @PutMapping("/{id}/prehodi")
    public LigaDto nastaviPrehode(@PathVariable Long id, @Valid @RequestBody PrehodiVnos vnos) {
        return ligaStoritev.nastaviPrehode(id, vnos);
    }

    /* Termini kol - iz istega razloga loceni od pravil: kolo se prestavi tudi
       sredi sezone. Odgovor so vsa srecanja lige, ker se je spremenil razpored. */
    @PutMapping("/{id}/termini")
    public List<SrecanjeDto> nastaviTermine(@PathVariable Long id, @Valid @RequestBody TerminiVnos vnos) {
        return ligaStoritev.nastaviTermine(id, vnos);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void zbrisi(@PathVariable Long id) {
        ligaStoritev.zbrisi(id);
    }

    // ---------- Ekipe ----------

    @GetMapping("/{id}/ekipe")
    public List<EkipaDto> ekipe(@PathVariable Long id) {
        return ligaStoritev.ekipe(id);
    }

    @PostMapping("/{id}/ekipe")
    @ResponseStatus(HttpStatus.CREATED)
    public EkipaDto dodajEkipo(@PathVariable Long id, @Valid @RequestBody EkipaVnos vnos) {
        return ligaStoritev.dodajEkipo(id, vnos);
    }

    @DeleteMapping("/ekipe/{idEkipa}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void odstraniEkipo(@PathVariable Long idEkipa) {
        ligaStoritev.odstraniEkipo(idEkipa);
    }

    // ---------- Kader ----------

    @GetMapping("/ekipe/{idEkipa}/kader")
    public List<KaderIgralecDto> kader(@PathVariable Long idEkipa) {
        return ligaStoritev.kader(idEkipa);
    }

    @PostMapping("/ekipe/{idEkipa}/kader")
    @ResponseStatus(HttpStatus.CREATED)
    public KaderIgralecDto dodajVKader(@PathVariable Long idEkipa, @Valid @RequestBody KaderVnos vnos) {
        return ligaStoritev.dodajVKader(idEkipa, vnos);
    }

    @DeleteMapping("/kader/{idKader}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void odstraniIzKadra(@PathVariable Long idKader) {
        ligaStoritev.odstraniIzKadra(idKader);
    }

    // ---------- Razpored, lestvica, srecanja ----------

    @PostMapping("/{id}/razpored")
    public List<SrecanjeDto> generirajRazpored(@PathVariable Long id) {
        ligaStoritev.generirajRazpored(id);
        return srecanjeStoritev.zaLigo(id);
    }

    @GetMapping("/{id}/srecanja")
    public List<SrecanjeDto> srecanja(@PathVariable Long id) {
        return srecanjeStoritev.zaLigo(id);
    }

    @GetMapping("/{id}/lestvica")
    public List<LestvicaEkipeDto> lestvica(@PathVariable Long id) {
        return ligaStoritev.lestvica(id);
    }

    /* Lestvici posameznikov in dvojic te lige. Loceni koncni tocki in ne del
       /lestvica: stran ju nalozi sele, ko gledalec sklop odpre. */
    @GetMapping("/{id}/lestvica-igralcev")
    public List<LestvicaIgralcaLigeDto> lestvicaIgralcev(@PathVariable Long id) {
        return ligaStoritev.lestvicaIgralcev(id);
    }

    @GetMapping("/{id}/lestvica-dvojic")
    public List<LestvicaDvojiceDto> lestvicaDvojic(@PathVariable Long id) {
        return ligaStoritev.lestvicaDvojic(id);
    }
}
