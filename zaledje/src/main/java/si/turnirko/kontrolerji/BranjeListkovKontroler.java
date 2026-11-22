/* Branje sodniskih listkov s fotografije (BranjeListkovStoritev). Vse poti so
   za organizatorja turnirja ali admina; preverba je v storitvi. */
package si.turnirko.kontrolerji;

import java.io.IOException;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;

import si.turnirko.dto.BranjaTurnirjaDto;
import si.turnirko.dto.BranjeListkaDto;
import si.turnirko.dto.PotrditevBranjaVnos;
import si.turnirko.dto.StanjeBranjaListkovDto;
import si.turnirko.storitve.BranjeListkovStoritev;

@RestController
public class BranjeListkovKontroler {

    private final BranjeListkovStoritev storitev;

    public BranjeListkovKontroler(BranjeListkovStoritev storitev) {
        this.storitev = storitev;
    }

    @GetMapping("/api/v1/branje-listkov/stanje")
    public StanjeBranjaListkovDto stanje() {
        return storitev.stanje();
    }

    @GetMapping("/api/v1/turnirji/{id}/branja-listkov")
    public BranjaTurnirjaDto seznam(@PathVariable Long id) {
        return storitev.seznam(id);
    }

    @PostMapping(path = "/api/v1/turnirji/{id}/branja-listkov", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public BranjeListkaDto nalozi(@PathVariable Long id, @RequestParam("slika") MultipartFile slika,
                                  @RequestHeader(name = "Idempotency-Key", required = false) String kljuc)
            throws IOException {
        // kljuc: vrsta na telefonu (F5.2) - ponovljeno posiljanje ne ustvari dvojnika
        return storitev.sprejmi(id, slika.getBytes(), kljuc);
    }

    /* Fotografija je osebni podatek (imena, rokopis): ne gre v skupni predpomnilnik. */
    @GetMapping("/api/v1/branja-listkov/{id}/slika")
    public ResponseEntity<byte[]> slika(@PathVariable Long id) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.noStore())
                .body(storitev.slika(id));
    }

    /* Poravnan listek (F4.1) za izrez celic ob poljih pregleda: isti dostop in glave kot fotografija. */
    @GetMapping("/api/v1/branja-listkov/{id}/poravnana")
    public ResponseEntity<byte[]> poravnana(@PathVariable Long id) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(storitev.poravnana(id));
    }

    /* Fotografija listka tekme (F4.4) za organizatorja turnirja in admina. */
    @GetMapping("/api/v1/tekme/{id}/listek")
    public ResponseEntity<byte[]> listekTekme(@PathVariable Long id) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(storitev.listekTekme(id));
    }

    @PostMapping("/api/v1/branja-listkov/{id}/potrdi")
    public BranjeListkaDto potrdi(@PathVariable Long id, @Valid @RequestBody PotrditevBranjaVnos vnos) {
        return storitev.potrdi(id, vnos);
    }

    @PostMapping("/api/v1/branja-listkov/{id}/zavrzi")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void zavrzi(@PathVariable Long id) {
        storitev.zavrzi(id);
    }

    @PostMapping("/api/v1/branja-listkov/{id}/znova")
    public BranjeListkaDto znova(@PathVariable Long id) {
        return storitev.znova(id);
    }
}
