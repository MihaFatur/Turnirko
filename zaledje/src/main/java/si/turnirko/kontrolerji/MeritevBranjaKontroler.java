/* Meritev branja listkov, zlati nabor (nacrt F1) in ucenje mreze stevk (F6), samo
   admin. Pot je zaprta za ostale vloge ze v VarnostneNastavitve, storitev preveri
   se enkrat. */
package si.turnirko.kontrolerji;

import java.io.IOException;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;

import si.turnirko.dto.MeritevBranjaDto;
import si.turnirko.dto.MrezeStevkDto;
import si.turnirko.dto.NapakeMrezeDto;
import si.turnirko.dto.StanjeNaboraDto;
import si.turnirko.storitve.MeritevBranjaStoritev;
import si.turnirko.storitve.MeritevNabora;
import si.turnirko.storitve.NapakeMreze;
import si.turnirko.storitve.UcenjeMreze;
import si.turnirko.storitve.ZlatiNabor;

@RestController
public class MeritevBranjaKontroler {

    private final MeritevBranjaStoritev meritev;
    private final MeritevNabora meritevNabora;
    private final ZlatiNabor nabor;
    private final UcenjeMreze ucenje;
    private final NapakeMreze napakeMreze;

    public MeritevBranjaKontroler(MeritevBranjaStoritev meritev, MeritevNabora meritevNabora, ZlatiNabor nabor,
                                  UcenjeMreze ucenje, NapakeMreze napakeMreze) {
        this.meritev = meritev;
        this.meritevNabora = meritevNabora;
        this.nabor = nabor;
        this.ucenje = ucenje;
        this.napakeMreze = napakeMreze;
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

    /* Mreza v uporabi, tekoce ucenje in zgodovina razlicic (F6.4). */
    @GetMapping("/api/v1/branje-listkov/mreze")
    public MrezeStevkDto mreze() {
        return ucenje.stanje();
    }

    /* »Uci zdaj«: ucenje v ozadju; 409, ce ze tece, je v premoru ali je stevk premalo. */
    @PostMapping("/api/v1/branje-listkov/mreze/uci")
    public MrezeStevkDto uciZdaj() {
        return ucenje.uciZdaj();
    }

    /* »Vrni prejsnjo«: umakne razlicico v uporabi. */
    @PostMapping("/api/v1/branje-listkov/mreze/vrni")
    public MrezeStevkDto vrniPrejsnjo() {
        return ucenje.vrniPrejsnjo();
    }

    /* Napake mreze po stevkah in sumljive oznake (F6.6); racuna se ob ogledu. */
    @GetMapping("/api/v1/branje-listkov/mreze/napake")
    public NapakeMrezeDto napake() {
        return napakeMreze.pregled();
    }

    /* Izloci stevko iz ucenja (napacna oznaka ali narobe izrezano okence). */
    @DeleteMapping("/api/v1/branje-listkov/mreze/stevke/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void izlociStevko(@PathVariable Long id) {
        napakeMreze.izloci(id);
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
