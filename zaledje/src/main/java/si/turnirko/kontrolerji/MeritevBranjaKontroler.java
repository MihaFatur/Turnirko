/* Meritev branja listkov in zlati nabor (nacrt F1, samo admin). Pot je zaprta
   za ostale vloge ze v VarnostneNastavitve, storitev preveri se enkrat. */
package si.turnirko.kontrolerji;

import java.io.IOException;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;

import si.turnirko.dto.MeritevBranjaDto;
import si.turnirko.dto.StanjeNaboraDto;
import si.turnirko.storitve.MeritevBranjaStoritev;
import si.turnirko.storitve.MeritevNabora;
import si.turnirko.storitve.ZlatiNabor;

@RestController
public class MeritevBranjaKontroler {

    private final MeritevBranjaStoritev meritev;
    private final MeritevNabora meritevNabora;
    private final ZlatiNabor nabor;

    public MeritevBranjaKontroler(MeritevBranjaStoritev meritev, MeritevNabora meritevNabora, ZlatiNabor nabor) {
        this.meritev = meritev;
        this.meritevNabora = meritevNabora;
        this.nabor = nabor;
    }

    @GetMapping("/api/v1/branje-listkov/meritev")
    public MeritevBranjaDto meritev(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate od,
            @RequestParam(name = "do", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate doDatuma,
            @RequestParam(required = false) Long idTurnir) {
        return meritev.meritev(od, doDatuma, idTurnir);
    }

    @GetMapping("/api/v1/branje-listkov/nabor")
    public StanjeNaboraDto nabor() {
        return meritevNabora.stanje();
    }

    @PostMapping("/api/v1/branje-listkov/nabor/meritev")
    public StanjeNaboraDto izmeriNabor() {
        return meritevNabora.zazeni();
    }

    /* Zip zlatega nabora za lokalne meritve. Pravica se preveri PRED zacetkom
       pretoka (stanje), saj se po prvem bajtu napake ne da vec sporociti. Pise
       se neposredno v odgovor (ne asinhrono), da pocasen prenos ne naleti na
       casovno mejo asinhrone zahteve. */
    @GetMapping("/api/v1/branje-listkov/nabor/zip")
    public void zipNabora(HttpServletResponse odgovor) throws IOException {
        meritevNabora.stanje();
        odgovor.setContentType("application/zip");
        odgovor.setHeader(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().cachePrivate().getHeaderValue());
        odgovor.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename("zlati-nabor.zip").build().toString());
        nabor.izvozi(odgovor.getOutputStream());
    }
}
