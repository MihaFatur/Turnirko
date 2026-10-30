/* Koncne tocke za profilno sliko igralca (V56). Tanek adapter - pravila so v
   ProfilneSlikeStoritev.

   - /moj-profil/slika: prijavljeni igralec (branje, nalaganje, izbris svoje);
   - /igralci/slike?idji=: javne slike danih igralcev (stopnicke, 1 na 1);
   - /slike/profil/{zgostitev}: bajti - javno; predpomnjenje en dan in ne za
     vedno kot pri logotipu, da izbrisana slika iz tujih predpomnilnikov izgine;
   - /profilne-slike: adminov pregled (cakajoce in nedavne), objava, odstranitev. */
package si.turnirko.kontrolerji;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartFile;

import si.turnirko.dto.MojaProfilnaSlikaDto;
import si.turnirko.dto.ProfilnaSlikaPregledDto;
import si.turnirko.dto.SlikaIgralcaDto;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.modeli.ProfilnaSlikaVsebina;
import si.turnirko.storitve.ProfilneSlikeStoritev;

@RestController
public class ProfilneSlikeKontroler {

    private static final CacheControl EN_DAN = CacheControl.maxAge(Duration.ofDays(1)).cachePublic();

    private final ProfilneSlikeStoritev storitev;

    public ProfilneSlikeKontroler(ProfilneSlikeStoritev storitev) {
        this.storitev = storitev;
    }

    @GetMapping("/api/v1/moj-profil/slika")
    public MojaProfilnaSlikaDto moja() {
        return storitev.moja();
    }

    @PostMapping(path = "/api/v1/moj-profil/slika", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MojaProfilnaSlikaDto nalozi(@RequestParam("datoteka") MultipartFile datoteka) throws IOException {
        return storitev.nalozi(datoteka.getBytes());
    }

    @DeleteMapping("/api/v1/moj-profil/slika")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void izbrisi() {
        storitev.izbrisiMojo();
    }

    @GetMapping("/api/v1/igralci/slike")
    public List<SlikaIgralcaDto> vidne(@RequestParam("idji") List<Long> idji) {
        return storitev.vidne(idji);
    }

    @GetMapping("/api/v1/slike/profil/{zgostitev:[0-9a-f]{64}}")
    public ResponseEntity<byte[]> slika(@PathVariable String zgostitev,
                                        @RequestParam(defaultValue = "VELIKA") String velikost,
                                        WebRequest zahteva) {
        String v = velikost.toUpperCase(Locale.ROOT);
        if (!v.equals(ProfilnaSlikaVsebina.VELIKA) && !v.equals(ProfilnaSlikaVsebina.MALA)) {
            throw new NeveljavenVnosIzjema("Velikost je VELIKA ali MALA.");
        }
        String oznaka = "\"" + zgostitev + "-" + v.toLowerCase(Locale.ROOT) + "\"";
        if (zahteva.checkNotModified(oznaka)) {
            return null;
        }
        return storitev.vsebina(zgostitev, v)
                .map(bajti -> ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        .cacheControl(EN_DAN)
                        .eTag(oznaka)
                        .header("Content-Security-Policy", "default-src 'none'; sandbox")
                        .body(bajti))
                .orElseThrow(() -> new NiNajdenoIzjema("Slike ni."));
    }

    @GetMapping("/api/v1/profilne-slike")
    public List<ProfilnaSlikaPregledDto> pregled() {
        return storitev.pregled();
    }

    @PostMapping("/api/v1/profilne-slike/{id}/objavi")
    public ProfilnaSlikaPregledDto objavi(@PathVariable Long id) {
        return storitev.objavi(id);
    }

    @DeleteMapping("/api/v1/profilne-slike/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void odstrani(@PathVariable Long id) {
        storitev.odstrani(id);
    }
}
