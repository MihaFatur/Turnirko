/* Mize turnirja in klicanje tekem (M1, M2; docs/pravila/mize.md). Vse poti so za
   organizatorja turnirja in admina, tudi GET (VarnostneNastavitve); lastnistvo
   preveri MizeStoritev. */
package si.turnirko.kontrolerji;

import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import si.turnirko.dto.KlicTekmeVnos;
import si.turnirko.dto.MizeTurnirjaDto;
import si.turnirko.dto.NastavitveMizVnos;
import si.turnirko.dto.PovezavaMizVnos;
import si.turnirko.dto.RedKategorijVnos;
import si.turnirko.dto.RedKolVnos;
import si.turnirko.dto.VrstniRedTekemVnos;
import si.turnirko.storitve.MizeStoritev;

@RestController
public class MizeKontroler {

    private final MizeStoritev storitev;

    public MizeKontroler(MizeStoritev storitev) {
        this.storitev = storitev;
    }

    /* Stanje dvorane se spreminja vsako minuto: brez predpomnjenja. */
    @GetMapping("/api/v1/turnirji/{id}/mize")
    public ResponseEntity<MizeTurnirjaDto> pregled(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate())
                .body(storitev.pregled(id));
    }

    @GetMapping("/api/v1/turnirji/{id}/mize/osebe")
    public ResponseEntity<List<MizeTurnirjaDto.OsebaDto>> osebe(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate())
                .body(storitev.osebe(id));
    }

    @PutMapping("/api/v1/turnirji/{id}/mize")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void nastavi(@PathVariable Long id, @Valid @RequestBody NastavitveMizVnos vnos) {
        storitev.nastavi(id, vnos);
    }

    /* Rocni vrstni red vrste »Na vrsti«; prazen seznam vrne samodejni red. */
    @PutMapping("/api/v1/turnirji/{id}/mize/vrstni-red")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void vrstniRed(@PathVariable Long id, @Valid @RequestBody VrstniRedTekemVnos vnos) {
        storitev.nastaviVrstniRed(id, vnos);
    }

    /* Vrstni red kategorij v kolu (V78); prazen seznam vrne privzeti red. */
    @PutMapping("/api/v1/turnirji/{id}/mize/red-kategorij")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void redKategorij(@PathVariable Long id, @Valid @RequestBody RedKategorijVnos vnos) {
        storitev.nastaviRedKategorij(id, vnos);
    }

    /* Vrstni red kol skupin ene kategorije (V80); prazen seznam vrne 1, 2, 3 ... */
    @PutMapping("/api/v1/turnirji/{id}/mize/red-kol")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void redKol(@PathVariable Long id, @Valid @RequestBody RedKolVnos vnos) {
        storitev.nastaviRedKol(id, vnos);
    }

    /* Skupne mize (V78): turnirji, s katerimi sme organizator ta turnir povezati. */
    @GetMapping("/api/v1/turnirji/{id}/mize/za-povezavo")
    public ResponseEntity<List<MizeTurnirjaDto.TurnirMizDto>> zaPovezavo(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate())
                .body(storitev.zaPovezavo(id));
    }

    /* Ta turnir deli mize z izbranim (in z vsemi, s katerimi jih ze deli izbrani). */
    @PutMapping("/api/v1/turnirji/{id}/mize/skupne")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void povezi(@PathVariable Long id, @Valid @RequestBody PovezavaMizVnos vnos) {
        storitev.povezi(id, vnos);
    }

    /* Ta turnir zapusti skupne mize in dobi kopijo njihovih nastavitev. */
    @DeleteMapping("/api/v1/turnirji/{id}/mize/skupne")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void razdruzi(@PathVariable Long id) {
        storitev.razdruzi(id);
    }

    @PutMapping("/api/v1/tekme/{id}/klic")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void poklici(@PathVariable Long id, @Valid @RequestBody KlicTekmeVnos vnos) {
        storitev.poklici(id, vnos);
    }

    @DeleteMapping("/api/v1/tekme/{id}/klic")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void preklici(@PathVariable Long id) {
        storitev.preklici(id);
    }
}
