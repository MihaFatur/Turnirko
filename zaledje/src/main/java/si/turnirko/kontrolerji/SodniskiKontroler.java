/* Sodnik slika listek s svojim telefonom brez prijave (S2, SodniskaBranjaStoritev).

   Edine poti, kjer gost sme pisati (izjema od »gost sme samo GET«, PRODUCT.md):
   pravico da skrivnost v QR kodi listka, nato enkraten zeton branja. Zeton gre
   vedno v telesu zahteve, nikoli v naslovu (naslovi gredo v dnevnike). */
package si.turnirko.kontrolerji;

import java.io.IOException;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import si.turnirko.dto.SodnikPoslanoDto;
import si.turnirko.dto.SodniskaPotrditevVnos;
import si.turnirko.dto.SodniskiZetonVnos;
import si.turnirko.dto.SodniskoBranjeDto;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.storitve.SodniskaBranjaStoritev;

@RestController
public class SodniskiKontroler {

    /* Najvecja fotografija s strani za sodnika (telefon jo pomanjsa na 2000 px). */
    static final long NAJVEC_BAJTOV = 4L * 1024 * 1024;

    private final SodniskaBranjaStoritev storitev;

    public SodniskiKontroler(SodniskaBranjaStoritev storitev) {
        this.storitev = storitev;
    }

    /* Naslov IP za omejitev hitrosti: za Caddyjem ga pove X-Forwarded-For
       (forward-headers-strategy v profilu splet). Ne shrani se nikamor. */
    @PostMapping(path = "/api/v1/sodnik/listki", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SodnikPoslanoDto> poslji(@RequestParam("slika") MultipartFile slika,
                                                   @RequestHeader(name = "Idempotency-Key", required = false) String kljuc,
                                                   HttpServletRequest zahteva) throws IOException {
        // telefon poslje ~0,5 MB (2000 px); vecje datoteke brez prijave ne kopiramo v pomnilnik
        if (slika.getSize() > NAJVEC_BAJTOV) {
            throw new NeveljavenVnosIzjema("Fotografija je prevelika. Fotografiraj listek znova.");
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(storitev.sprejmi(slika.getBytes(), kljuc, zahteva.getRemoteAddr()));
    }

    @PostMapping("/api/v1/sodnik/branja/{id}/stanje")
    public ResponseEntity<SodniskoBranjeDto> stanje(@PathVariable Long id, @Valid @RequestBody SodniskiZetonVnos vnos,
                                                    HttpServletRequest zahteva) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(storitev.stanje(id, vnos.zeton(), zahteva.getRemoteAddr()));
    }

    @PostMapping("/api/v1/sodnik/branja/{id}/potrdi")
    public ResponseEntity<SodniskoBranjeDto> potrdi(@PathVariable Long id, @Valid @RequestBody SodniskaPotrditevVnos vnos,
                                                    HttpServletRequest zahteva) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(storitev.potrdi(id, vnos, zahteva.getRemoteAddr()));
    }
}
