/* Vadnica stevk (nacrt F3, VadnicaStoritev). Pot je samo za admina ze v
   VarnostneNastavitve, storitev preveri se enkrat. */
package si.turnirko.kontrolerji;

import java.io.IOException;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;

import si.turnirko.dto.OdobritevVadniceVnos;
import si.turnirko.dto.VadnicaDto;
import si.turnirko.storitve.VadnicaStoritev;

@RestController
public class VadnicaKontroler {

    private final VadnicaStoritev storitev;

    public VadnicaKontroler(VadnicaStoritev storitev) {
        this.storitev = storitev;
    }

    @GetMapping("/api/v1/vadnice")
    public VadnicaDto.Seznam seznam() {
        return storitev.seznam();
    }

    @PostMapping(path = "/api/v1/vadnice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public VadnicaDto nalozi(@RequestParam("slika") MultipartFile slika) throws IOException {
        return storitev.sprejmi(slika.getBytes());
    }

    @GetMapping("/api/v1/vadnice/{id}")
    public VadnicaDto.Podrobno podrobno(@PathVariable Long id) {
        return storitev.podrobno(id);
    }

    /* Rokopis: ne gre v skupni predpomnilnik. */
    @GetMapping("/api/v1/vadnice/{id}/slika")
    public ResponseEntity<byte[]> slika(@PathVariable Long id) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(storitev.slika(id));
    }

    @PostMapping("/api/v1/vadnice/{id}/odobri")
    public VadnicaDto odobri(@PathVariable Long id, @Valid @RequestBody OdobritevVadniceVnos vnos) {
        return storitev.odobri(id, vnos);
    }

    @DeleteMapping("/api/v1/vadnice/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void izbrisi(@PathVariable Long id) {
        storitev.izbrisi(id);
    }
}
