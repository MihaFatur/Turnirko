/* Ligaski zapisniki za bralnik: tisk (NatisZapisnikovStoritev) in branje s
   fotografije (BranjeZapisnikovStoritev). Vse poti so za organizatorja in admina
   (VarnostneNastavitve, tudi GET); lastnistvo preveri storitev. */
package si.turnirko.kontrolerji;

import java.io.IOException;
import java.util.List;

import jakarta.validation.Valid;

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

import si.turnirko.dto.BranjeZapisnikaDto;
import si.turnirko.dto.NatisZapisnikaDto;
import si.turnirko.dto.NatisZapisnikovVnos;
import si.turnirko.dto.StanjeBranjaListkovDto;
import si.turnirko.dto.ZapisZapisnikaVnos;
import si.turnirko.storitve.BranjeZapisnikovStoritev;
import si.turnirko.storitve.NatisZapisnikovStoritev;

@RestController
public class BranjeZapisnikovKontroler {

    private final NatisZapisnikovStoritev natis;
    private final BranjeZapisnikovStoritev branje;

    public BranjeZapisnikovKontroler(NatisZapisnikovStoritev natis, BranjeZapisnikovStoritev branje) {
        this.natis = natis;
        this.branje = branje;
    }

    /* Zapisniki za tisk (eden ali vsa srecanja kola); kode QR nosijo skrivnost. */
    @PostMapping("/api/v1/zapisniki/natis")
    public ResponseEntity<List<NatisZapisnikaDto>> natisni(@Valid @RequestBody NatisZapisnikovVnos vnos) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(natis.natisni(vnos));
    }

    @GetMapping("/api/v1/zapisniki/branje/stanje")
    public StanjeBranjaListkovDto stanje() {
        return branje.stanje();
    }

    @GetMapping("/api/v1/branja-zapisnikov")
    public List<BranjeZapisnikaDto> seznam() {
        return branje.seznam();
    }

    /* samodejno: ali sme se zanesljivo prebran zapisnik zapisati sam. */
    @PostMapping(path = "/api/v1/branja-zapisnikov", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public BranjeZapisnikaDto nalozi(@RequestParam("slika") MultipartFile slika,
                                     @RequestParam(name = "samodejno", defaultValue = "true") boolean samodejno,
                                     @RequestHeader(name = "Idempotency-Key", required = false) String kljuc)
            throws IOException {
        return branje.sprejmi(slika.getBytes(), kljuc, samodejno);
    }

    @GetMapping("/api/v1/branja-zapisnikov/{id}")
    public BranjeZapisnikaDto podrobno(@PathVariable Long id) {
        return branje.podrobno(id);
    }

    /* Fotografija nosi imena in rokopis: ne gre v skupni predpomnilnik. */
    @GetMapping("/api/v1/branja-zapisnikov/{id}/slika")
    public ResponseEntity<byte[]> slika(@PathVariable Long id) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(branje.slika(id));
    }

    @GetMapping("/api/v1/branja-zapisnikov/{id}/poravnana")
    public ResponseEntity<byte[]> poravnana(@PathVariable Long id) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(branje.poravnana(id));
    }

    @PostMapping("/api/v1/branja-zapisnikov/{id}/potrdi")
    public BranjeZapisnikaDto potrdi(@PathVariable Long id, @Valid @RequestBody ZapisZapisnikaVnos vnos) {
        return branje.potrdi(id, vnos);
    }

    @PostMapping("/api/v1/branja-zapisnikov/{id}/zavrzi")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void zavrzi(@PathVariable Long id) {
        branje.zavrzi(id);
    }

    @PostMapping("/api/v1/branja-zapisnikov/{id}/znova")
    public BranjeZapisnikaDto znova(@PathVariable Long id) {
        return branje.znova(id);
    }
}
