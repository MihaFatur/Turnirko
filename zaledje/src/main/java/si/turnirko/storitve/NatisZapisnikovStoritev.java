/* Tisk ligaskih zapisnikov za bralnik (26. 11. 2026). Organizator natisne
   zapisnike kola (ali enega srecanja); vsak list dobi natis - zapis, kaj je na
   njem (oblika, razlicica postavitve, format, nizi, kadra po vrsticah) - in QR
   »TZ:<id natisa>:<skrivnost>«. Bralnik iz natisa izve, katera vrstica kadra je
   kateri igralec, tudi ce se je kader po tisku spremenil.

   Kode dobi samo organizator tekmovanja in admin (lastnistvo srecanja), kot kode
   sodniskih listkov: skrivnost sicer zdaj ne daje nobene pravice (zapisnike bere
   samo organizator), a poskrbi, da tuj ali poskodovan QR ne prebere napacnega
   natisa. Enak natis (iste vrednosti) se ne podvaja: ponovni tisk istega kola ne
   polni baze.

   Oblika: VRSTICE (vrstica tekme), kadar gre kader nanje, sicer KADER (tabela
   kadra) - lastnik: »nacin 2 ima ob vecjih kadrih resne probleme, takrat raje
   nacin 1«. Organizator lahko obliko izbere sam. Za zdaj samo formati z dvema
   igralcema (Savinja, Corbillon, DSI) - odlocitev lastnika. */
package si.turnirko.storitve;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.branje.GeometrijaZapisnika;
import si.turnirko.dto.NatisZapisnikaDto;
import si.turnirko.dto.NatisZapisnikovVnos;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.KaderEkipe;
import si.turnirko.modeli.NatisZapisnika;
import si.turnirko.modeli.Srecanje;
import si.turnirko.modeli.StatusSrecanja;
import si.turnirko.modeli.TipTekmeSrecanja;
import si.turnirko.repozitoriji.KaderEkipeRepozitorij;
import si.turnirko.repozitoriji.NatisZapisnikaRepozitorij;
import si.turnirko.repozitoriji.SrecanjeRepozitorij;

@Service
public class NatisZapisnikovStoritev {

    private final SrecanjeRepozitorij srecanja;
    private final KaderEkipeRepozitorij kadri;
    private final NatisZapisnikaRepozitorij natisi;
    private final LastnistvoStoritev lastnistvo;
    private final SkrivnostListka skrivnost;

    public NatisZapisnikovStoritev(SrecanjeRepozitorij srecanja, KaderEkipeRepozitorij kadri,
                                   NatisZapisnikaRepozitorij natisi, LastnistvoStoritev lastnistvo,
                                   SkrivnostListka skrivnost) {
        this.srecanja = srecanja;
        this.kadri = kadri;
        this.natisi = natisi;
        this.lastnistvo = lastnistvo;
        this.skrivnost = skrivnost;
    }

    @Transactional
    public List<NatisZapisnikaDto> natisni(NatisZapisnikovVnos vnos) {
        List<NatisZapisnikaDto> izid = new ArrayList<>();
        for (Long idSrecanje : vnos.srecanja().stream().distinct().toList()) {
            izid.add(natisni(idSrecanje, vnos.oblika()));
        }
        return izid;
    }

    private NatisZapisnikaDto natisni(Long idSrecanje, GeometrijaZapisnika.Oblika zelena) {
        lastnistvo.preveriPoSrecanju(idSrecanje);
        Srecanje s = srecanja.najdiPodrobno(idSrecanje)
                .orElseThrow(() -> new NiNajdenoIzjema("Srecanje z id " + idSrecanje + " ne obstaja."));
        Srecanje.Pravila pravila = s.pravila();
        FormatSrecanja format = pravila.format();
        if (format.getStIgralcev() != 2) {
            throw new DomenskaIzjema("Zapisnik za bralnik je za zdaj samo za srečanja z dvema igralcema na ekipo"
                    + " (Savinja, Corbillon, DŠI).");
        }
        if (s.getStatus() == StatusSrecanja.KONCANO) {
            throw new DomenskaIzjema("Srečanje " + s.getEkipaDomaci().prikazanoIme() + " : "
                    + s.getEkipaGost().prikazanoIme() + " je že končano.");
        }
        List<KaderEkipe> kaderD = kadri.najdiZaEkipo(s.getEkipaDomaci().getId());
        List<KaderEkipe> kaderG = kadri.najdiZaEkipo(s.getEkipaGost().getId());
        int nizov = pravila.steviloNizov();
        List<GeometrijaZapisnika.TekmaRazporeda> tekme = tekme(format);
        GeometrijaZapisnika.Oblika oblika = zelena != null ? zelena
                : GeometrijaZapisnika.samodejna(kaderD.size() + 1, kaderG.size() + 1, nizov, tekme.size());
        if (oblika == null) {
            throw new DomenskaIzjema("Kader ekipe je prevelik za zapisnik za bralnik.");
        }
        GeometrijaZapisnika.PostavitevZapisnika postavitev;
        try {
            postavitev = GeometrijaZapisnika.izracunaj(GeometrijaZapisnika.RAZLICICA, oblika, tekme, nizov,
                    kaderD.size() + 1, kaderG.size() + 1);
        } catch (GeometrijaZapisnika.NeGreNaList e) {
            throw new DomenskaIzjema(e.getMessage());
        }
        String jsonD = JsonBranja.zapisi(kaderD.stream().map(k -> k.getIgralec().getId()).toList());
        String jsonG = JsonBranja.zapisi(kaderG.stream().map(k -> k.getIgralec().getId()).toList());
        NatisZapisnika natis = natisi.zaSrecanje(idSrecanje).stream()
                .filter(n -> n.getOblika() == oblika && n.getRazlicica() == GeometrijaZapisnika.RAZLICICA
                        && n.getFormat() == format && n.getSteviloNizov() == nizov
                        && n.getKaderDomaci().equals(jsonD) && n.getKaderGost().equals(jsonG))
                .findFirst()
                .orElseGet(() -> natisi.save(new NatisZapisnika(s, oblika, GeometrijaZapisnika.RAZLICICA, format,
                        nizov, jsonD, jsonG, lastnistvo.trenutni(), LocalDateTime.now())));

        String koda = KodaListka.vsebinaQrZapisnika(natis.getId(), skrivnost.zaZapisnik(natis.getId()));
        boolean liga = s.getLiga() != null;
        String tekmovanje = liga ? s.getLiga().getIme() : s.getTekma().getDogodek().getTurnir().getIme();
        String sezona = liga ? s.getLiga().getSezona() : s.getTekma().getDogodek().getIme();
        String opis = !liga ? null : s.getSerija() != null ? "končnica" : s.getKolo() + ". kolo";
        return new NatisZapisnikaDto(idSrecanje, natis.getId(), koda, oznaka(natis.getId()), oblika, postavitev, tekme,
                igralci(kaderD), igralci(kaderG), s.getEkipaDomaci().prikazanoIme(), s.getEkipaGost().prikazanoIme(),
                tekmovanje, sezona, opis, s.getPredvidenZacetek(), liga ? s.getLiga().getId() : null);
    }

    /* Natisnjena oznaka ob QR (za cloveka): »Z-<id natisa>«. */
    static String oznaka(long idNatisa) {
        return "Z-" + idNatisa;
    }

    static List<GeometrijaZapisnika.TekmaRazporeda> tekme(FormatSrecanja format) {
        return format.razpored().stream()
                .map(m -> new GeometrijaZapisnika.TekmaRazporeda(m.oznaka(), m.tip() == TipTekmeSrecanja.DVOJICE))
                .toList();
    }

    private static List<NatisZapisnikaDto.Igralec> igralci(List<KaderEkipe> kader) {
        return kader.stream()
                .map(k -> new NatisZapisnikaDto.Igralec(k.getIgralec().getId(), k.getIgralec().getIme(),
                        k.getIgralec().getPriimek()))
                .toList();
    }

    /* Kader natisa (id igralcev po vrsticah) iz JSON. */
    static List<Long> kader(String json) {
        return Arrays.asList(JsonBranja.preberi(json, Long[].class));
    }

    /* Postavitev natisnjenega lista (bralnik). */
    static GeometrijaZapisnika.PostavitevZapisnika postavitev(NatisZapisnika n) {
        return GeometrijaZapisnika.izracunaj(n.getRazlicica(), n.getOblika(), tekme(n.getFormat()),
                n.getSteviloNizov(), kader(n.getKaderDomaci()).size() + 1, kader(n.getKaderGost()).size() + 1);
    }
}
