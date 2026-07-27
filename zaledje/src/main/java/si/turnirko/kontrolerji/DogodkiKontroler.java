/* Koncne tocke za dogodke: prijave, zreb in prikaz mreze. */
package si.turnirko.kontrolerji;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import si.turnirko.dto.DogodekDto;
import si.turnirko.dto.IzborDto;
import si.turnirko.dto.MrezaDto;
import si.turnirko.dto.PrijavaDto;
import si.turnirko.dto.PrijaviIgralceVnos;
import si.turnirko.dto.SkupinaDto;
import si.turnirko.dto.TekmaDto;
import si.turnirko.dto.VrstniRedVnos;
import si.turnirko.dto.VrsticaLestviceDto;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.FazaTekme;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.SistemTekmovanja;
import si.turnirko.modeli.Skupina;
import si.turnirko.modeli.Tekma;
import si.turnirko.repozitoriji.DogodekRepozitorij;
import si.turnirko.repozitoriji.PrijavaRepozitorij;
import si.turnirko.repozitoriji.SkupinaRepozitorij;
import si.turnirko.repozitoriji.TekmaRepozitorij;
import si.turnirko.storitve.IzborStoritev;
import si.turnirko.storitve.RazvrstitevStoritev;
import si.turnirko.storitve.SpremembeEloStoritev;
import si.turnirko.storitve.SpremembeEloStoritev.ObTekmi;
import si.turnirko.storitve.TekmaStoritev;
import si.turnirko.storitve.TurnirjiStoritev;
import si.turnirko.storitve.ZrebStoritev;

@RestController
@RequestMapping("/api/v1/dogodki")
public class DogodkiKontroler {

    private final DogodekRepozitorij dogodekRepozitorij;
    private final PrijavaRepozitorij prijavaRepozitorij;
    private final TekmaRepozitorij tekmaRepozitorij;
    private final SkupinaRepozitorij skupinaRepozitorij;
    private final TurnirjiStoritev turnirjiStoritev;
    private final ZrebStoritev zrebStoritev;
    private final RazvrstitevStoritev razvrstitevStoritev;
    private final SpremembeEloStoritev spremembeEloStoritev;
    private final IzborStoritev izborStoritev;
    private final TekmaStoritev tekmaStoritev;

    public DogodkiKontroler(DogodekRepozitorij dogodekRepozitorij,
                            PrijavaRepozitorij prijavaRepozitorij,
                            TekmaRepozitorij tekmaRepozitorij,
                            SkupinaRepozitorij skupinaRepozitorij,
                            TurnirjiStoritev turnirjiStoritev,
                            ZrebStoritev zrebStoritev,
                            RazvrstitevStoritev razvrstitevStoritev,
                            SpremembeEloStoritev spremembeEloStoritev,
                            IzborStoritev izborStoritev,
                            TekmaStoritev tekmaStoritev) {
        this.dogodekRepozitorij = dogodekRepozitorij;
        this.prijavaRepozitorij = prijavaRepozitorij;
        this.tekmaRepozitorij = tekmaRepozitorij;
        this.skupinaRepozitorij = skupinaRepozitorij;
        this.turnirjiStoritev = turnirjiStoritev;
        this.zrebStoritev = zrebStoritev;
        this.razvrstitevStoritev = razvrstitevStoritev;
        this.spremembeEloStoritev = spremembeEloStoritev;
        this.izborStoritev = izborStoritev;
        this.tekmaStoritev = tekmaStoritev;
    }

    /* Celotna slika dogodka: podatki, prijave, mreza in - glede na sistem -
       lestvice skupin ali skupna lestvica kroznega sistema.
       Entitete se v DTO-je pretvorijo tu (izven transakcije), poizvedbe pa
       vnaprej nalozijo vse povezave, ki jih DTO-ji in lestvica berejo. */
    @GetMapping("/{id}")
    public MrezaDto mreza(@PathVariable Long id) {
        Dogodek dogodekEntiteta = dogodekRepozitorij.najdiSTurnirjem(id)
                .orElseThrow(() -> new NiNajdenoIzjema("Dogodek z id " + id + " ne obstaja."));
        DogodekDto dogodek = DogodekDto.iz(dogodekEntiteta);

        List<Prijava> prijaveEntitete = prijavaRepozitorij.najdiZaDogodek(id);
        List<Tekma> tekmeEntitete = tekmaRepozitorij.najdiZaDogodek(id);

        boolean poJakosti = dogodekEntiteta.getSistemTekmovanja() == SistemTekmovanja.SKUPINE;
        if (poJakosti) {
            // pri formatu TOP je vrstni red del vsebine (odloca izbor in
            // skupino), zato ga izracuna streznik in ne vmesnik
            prijaveEntitete = izborStoritev.vrstniRed(prijaveEntitete);
        }
        Map<Long, Integer> ratingi = izborStoritev.ratingi(prijaveEntitete);
        List<PrijavaDto> prijave = prijaveEntitete.stream()
                .map(p -> PrijavaDto.iz(p, ratingi.get(p.getIgralec().getId())))
                .toList();

        // Sprememba ELO ("+16 / -16") in rating pred tekmo ob vsaki tekmi.
        // Za odigrane tekme oboje iz dnevnika, za neodigrane rating pred = trenutni.
        Map<Long, Map<Long, ObTekmi>> spremembe = spremembeEloStoritev.zaTekme(
                tekmeEntitete.stream().map(Tekma::getId).toList());
        Map<Long, Integer> trenutni = spremembeEloStoritev.trenutniRatingi(
                prijaveEntitete.stream().map(p -> p.getIgralec().getId()).distinct().toList());
        List<TekmaDto> tekme = tekmeEntitete.stream()
                .map(t -> TekmaDto.iz(t,
                        spremembaZaStran(spremembe, t, t.getPrijava1()),
                        spremembaZaStran(spremembe, t, t.getPrijava2()),
                        ratingPredZaStran(spremembe, trenutni, t, t.getPrijava1()),
                        ratingPredZaStran(spremembe, trenutni, t, t.getPrijava2())))
                .toList();

        List<SkupinaDto> skupine = List.of();
        List<VrsticaLestviceDto> lestvica = List.of();
        IzborDto izbor = null;

        if (dogodekEntiteta.getSistemTekmovanja().imaSkupine()) {
            List<Prijava> zaLestvice = prijaveEntitete;
            skupine = skupinaRepozitorij.findByDogodekIdOrderByOznakaAsc(id).stream()
                    .map(skupina -> lestvicaSkupine(skupina, zaLestvice, tekmeEntitete))
                    .toList();
        }
        if (poJakosti) {
            izbor = izbor(dogodekEntiteta, prijaveEntitete);
        } else if (dogodekEntiteta.getSistemTekmovanja() == SistemTekmovanja.KROZNI) {
            List<Prijava> udelezenci = prijaveEntitete.stream()
                    .filter(p -> p.getStatus() == Prijava.StatusPrijave.PRIJAVLJEN)
                    .toList();
            lestvica = razvrstitevStoritev.lestvica(udelezenci, tekmeEntitete);
        }

        return new MrezaDto(dogodek, prijave, tekme, skupine, lestvica, izbor);
    }

    /* Crta reza in predogled skupin za format TOP. Razrez racuna ZrebStoritev,
       da predogled in dejanski zreb ne moreta razsoditi razlicno. */
    private IzborDto izbor(Dogodek dogodek, List<Prijava> prijave) {
        List<Prijava> prijavljeni = prijave.stream()
                .filter(p -> p.getStatus() == Prijava.StatusPrijave.PRIJAVLJEN)
                .toList();
        int meja = dogodek.mejaIzbora();
        int igra = Math.min(meja, prijavljeni.size());
        List<Integer> velikosti = ZrebStoritev.velikostiSkupin(igra, dogodek.getVelikostSkupine());

        List<IzborDto.SkupinaPredogledDto> predogled = new ArrayList<>();
        int odMesta = 1;
        for (int i = 0; i < velikosti.size(); i++) {
            int velikost = velikosti.get(i);
            predogled.add(new IzborDto.SkupinaPredogledDto(
                    String.valueOf((char) ('A' + i)), velikost, odMesta, odMesta + velikost - 1));
            odMesta += velikost;
        }
        return new IzborDto(meja, prijavljeni.size(), igra, predogled,
                ZrebStoritev.zadrzekRazreza(velikosti));
    }

    /* Sprememba ELO za enega udelezenca tekme (null, ce tekma ni obracunana
       ali je stran prazna). */
    private static Integer spremembaZaStran(Map<Long, Map<Long, ObTekmi>> spremembe, Tekma tekma, Prijava stran) {
        ObTekmi ob = obTekmi(spremembe, tekma, stran);
        return ob == null ? null : ob.sprememba();
    }

    /* Rating igralca pred tekmo: pri odigrani tekmi zgodovinski (iz dnevnika),
       pri neodigrani pa trenutni rating; null, ce igralca ali ratinga ni. */
    private static Integer ratingPredZaStran(Map<Long, Map<Long, ObTekmi>> spremembe,
                                             Map<Long, Integer> trenutni, Tekma tekma, Prijava stran) {
        if (stran == null) return null;
        ObTekmi ob = obTekmi(spremembe, tekma, stran);
        if (ob != null) return ob.ratingPred();
        return trenutni.get(stran.getIgralec().getId());
    }

    private static ObTekmi obTekmi(Map<Long, Map<Long, ObTekmi>> spremembe, Tekma tekma, Prijava stran) {
        if (stran == null) return null;
        Map<Long, ObTekmi> poIgralcu = spremembe.get(tekma.getId());
        return poIgralcu == null ? null : poIgralcu.get(stran.getIgralec().getId());
    }

    private SkupinaDto lestvicaSkupine(Skupina skupina, List<Prijava> prijave, List<Tekma> tekme) {
        List<Prijava> clani = prijave.stream()
                .filter(p -> skupina.getId().equals(p.getIdSkupina()))
                .toList();
        List<Tekma> tekmeSkupine = tekme.stream()
                .filter(t -> t.getFaza() == FazaTekme.SKUPINA
                        && skupina.getId().equals(t.getIdSkupina()))
                .toList();
        return new SkupinaDto(skupina.getId(), skupina.getOznaka(),
                razvrstitevStoritev.lestvica(clani, tekmeSkupine));
    }

    @PostMapping("/{id}/prijave")
    @ResponseStatus(HttpStatus.CREATED)
    public List<PrijavaDto> prijavi(@PathVariable Long id, @Valid @RequestBody PrijaviIgralceVnos vnos) {
        return turnirjiStoritev.prijaviIgralce(id, vnos.idjiIgralcev())
                .stream().map(PrijavaDto::iz).toList();
    }

    @PostMapping("/prijave/{idPrijave}/odjava")
    public PrijavaDto odjavi(@PathVariable Long idPrijave) {
        return PrijavaDto.iz(turnirjiStoritev.odjavi(idPrijave));
    }

    /* Shrani rocno urejen jakostni vrstni red (format TOP). */
    @PutMapping("/{id}/vrstni-red")
    public List<PrijavaDto> vrstniRed(@PathVariable Long id, @Valid @RequestBody VrstniRedVnos vnos) {
        return izborStoritev.shraniVrstniRed(id, vnos.idjiPrijav())
                .stream().map(PrijavaDto::iz).toList();
    }

    /* Odstop igralca med tekmovanjem: odigrane tekme obveljajo,
       preostale dobijo nasprotniki brez boja. */
    @PostMapping("/prijave/{idPrijave}/odstop")
    public PrijavaDto odstop(@PathVariable Long idPrijave) {
        return PrijavaDto.iz(tekmaStoritev.odstopiIgralca(idPrijave));
    }

    /* Izvede zreb in vrne ustvarjene tekme. */
    @PostMapping("/{id}/zreb")
    @ResponseStatus(HttpStatus.CREATED)
    public List<TekmaDto> zreb(@PathVariable Long id) {
        return zrebStoritev.izvediZreb(id).stream().map(TekmaDto::iz).toList();
    }
}
