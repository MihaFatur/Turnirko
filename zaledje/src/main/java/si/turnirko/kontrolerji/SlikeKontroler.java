/* Koncne tocke za slike in logotip tekmovanja (V54). Tanek adapter - logika je
   v SlikeStoritev, obdelava slike v ObdelavaSlik.

   Slika se postreze po zgostitvi (javno, kot vsi GET-i): ista zgostitev je
   vedno ista vsebina, zato jo brskalnik in vmesni strezniki hranijo za vedno
   (immutable) - telefon v dvorani logotip lige prenese enkrat. Nova slika je nov
   naslov, zamenjava logotipa torej ne potrebuje praznjenja predpomnilnika.

   Logotip lige in turnirja je tu in ne v LigeKontroler/TurnirjiKontroler, ker je
   ista operacija nad isto storitvijo; poti pa sledijo tekmovanju, da jih
   varnostna veriga pokrije s pravilom za lige in turnirje (organizator). */
package si.turnirko.kontrolerji;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

import jakarta.validation.Valid;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartFile;

import si.turnirko.dto.LogotipVnos;
import si.turnirko.dto.SlikaDto;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.modeli.VelikostSlike;
import si.turnirko.storitve.SlikeStoritev;

@RestController
public class SlikeKontroler {

    /* Leto je najdaljse, kar brskalniki spostujejo; immutable pove, da ob
       osvezitvi strani ni treba niti vprasati, ali se je spremenila. */
    private static final CacheControl ZA_VEDNO =
            CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable();

    private final SlikeStoritev slikeStoritev;

    public SlikeKontroler(SlikeStoritev slikeStoritev) {
        this.slikeStoritev = slikeStoritev;
    }

    /* Knjiznica logotipov prijavljenega (admin, organizator). Ni javna, ceprav
       je GET - pravilo stoji v VarnostneNastavitve pred "GET je javen". */
    @GetMapping("/api/v1/slike")
    public List<SlikaDto> knjiznica() {
        return slikeStoritev.knjiznica();
    }

    @PostMapping(path = "/api/v1/slike", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public SlikaDto nalozi(@RequestParam("datoteka") MultipartFile datoteka) throws IOException {
        return slikeStoritev.nalozi(datoteka.getBytes());
    }

    @DeleteMapping("/api/v1/slike/{id:\\d+}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void izbrisi(@PathVariable Long id) {
        slikeStoritev.izbrisi(id);
    }

    /* Bajti razlicice. Vrsto je dolocil streznik ob zapisu (nikoli brskalnik);
       "sandbox" in prazen CSP zapreta pot, po kateri bi odprta slika kdaj
       izvedla karkoli - tudi ce bi v bazo kdaj prislo kaj drugega kot PNG/JPEG. */
    @GetMapping("/api/v1/slike/{zgostitev:[0-9a-f]{64}}")
    public ResponseEntity<byte[]> slika(@PathVariable String zgostitev,
                                        @RequestParam(defaultValue = "ZASLON") VelikostSlike velikost,
                                        WebRequest zahteva) {
        String oznaka = "\"" + zgostitev + "-" + velikost.name().toLowerCase(Locale.ROOT) + "\"";
        if (zahteva.checkNotModified(oznaka)) {
            return null;
        }
        return slikeStoritev.vsebina(zgostitev, velikost)
                .map(r -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(r.getVrsta()))
                        .cacheControl(ZA_VEDNO)
                        .eTag(oznaka)
                        .header("Content-Security-Policy", "default-src 'none'; sandbox")
                        .body(r.getVsebina()))
                .orElseThrow(() -> new NiNajdenoIzjema("Slike ni."));
    }

    // ---------- Logotip tekmovanja ----------

    @PutMapping("/api/v1/lige/{id}/logotip")
    public SlikaDto logotipLige(@PathVariable Long id, @Valid @RequestBody LogotipVnos vnos) {
        return slikeStoritev.nastaviLogotipLige(id, vnos.idSlika());
    }

    @DeleteMapping("/api/v1/lige/{id}/logotip")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void odstraniLogotipLige(@PathVariable Long id) {
        slikeStoritev.odstraniLogotipLige(id);
    }

    @PutMapping("/api/v1/turnirji/{id}/logotip")
    public SlikaDto logotipTurnirja(@PathVariable Long id, @Valid @RequestBody LogotipVnos vnos) {
        return slikeStoritev.nastaviLogotipTurnirja(id, vnos.idSlika());
    }

    @DeleteMapping("/api/v1/turnirji/{id}/logotip")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void odstraniLogotipTurnirja(@PathVariable Long id) {
        slikeStoritev.odstraniLogotipTurnirja(id);
    }
}
