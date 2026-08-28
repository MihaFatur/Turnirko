/* Koledar: kaj se v danem obdobju dogaja - turnirji in kola lig v enem seznamu.

   Pogled je javen (gost je uporabnik stevilka ena) in bere samo tisto, kar ze
   obstaja: datume turnirjev in termine kol. Nove sheme ne potrebuje.

   Dve pravili, ki ju ne razbij:
   - Zrnatost ligaskega vnosa je KOLO, ne srecanje. Termin je last kola, zato
     bi vnos na srecanje isto ligo v istem dnevu izpisal petkrat.
   - Vnosov se ne razvrsca po id-ju, ampak po datumu in nato po imenu. Vmesnik
     iz tega vrstnega reda izpelje vrstni red trakov v tednu in vrstic v
     seznamu, zato mora biti med ponovnimi klici enak. */
package si.turnirko.storitve;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.KoledarVnosDto;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.modeli.Klub;
import si.turnirko.modeli.Liga;
import si.turnirko.modeli.Srecanje;
import si.turnirko.modeli.Turnir;
import si.turnirko.repozitoriji.SrecanjeRepozitorij;
import si.turnirko.repozitoriji.TurnirRepozitorij;

@Service
public class KoledarStoritev {

    /* Najdaljse obdobje enega klica. Stran koledarja lista po mesecih, domaca
       stran vprasa za en mesec - leto je torej z veliko rezerve dovolj in
       hkrati prepreci, da bi en klic potegnil vso uvozeno zgodovino. */
    private static final int NAJVEC_DNI = 366;

    private final TurnirRepozitorij turnirRepozitorij;
    private final SrecanjeRepozitorij srecanjeRepozitorij;

    public KoledarStoritev(TurnirRepozitorij turnirRepozitorij,
                           SrecanjeRepozitorij srecanjeRepozitorij) {
        this.turnirRepozitorij = turnirRepozitorij;
        this.srecanjeRepozitorij = srecanjeRepozitorij;
    }

    /* Vsi vnosi, ki se dotikajo obdobja (oba dneva vkljucno). */
    @Transactional(readOnly = true)
    public List<KoledarVnosDto> vObdobju(LocalDate od, LocalDate doKdaj) {
        if (od == null || doKdaj == null) {
            throw new NeveljavenVnosIzjema("Koledar potrebuje obdobje (od, do).");
        }
        if (doKdaj.isBefore(od)) {
            throw new NeveljavenVnosIzjema("Konec obdobja je pred zacetkom.");
        }
        if (od.plusDays(NAJVEC_DNI).isBefore(doKdaj)) {
            throw new NeveljavenVnosIzjema(
                    "Obdobje koledarja sme obsegati najvec " + NAJVEC_DNI + " dni.");
        }

        List<KoledarVnosDto> vnosi = new ArrayList<>();
        vnosi.addAll(turnirji(od, doKdaj));
        vnosi.addAll(kolaLig(od, doKdaj));
        vnosi.sort(Comparator.comparing(KoledarVnosDto::datum)
                .thenComparing(KoledarVnosDto::ime)
                .thenComparing(v -> v.kolo() == null ? 0 : v.kolo()));
        return vnosi;
    }

    // ---------- Turnirji ----------

    private List<KoledarVnosDto> turnirji(LocalDate od, LocalDate doKdaj) {
        return turnirRepozitorij.najdiVObdobju(od, doKdaj).stream()
                .map(KoledarStoritev::vnosTurnirja)
                .toList();
    }

    private static KoledarVnosDto vnosTurnirja(Turnir t) {
        LocalDate konec = t.getDatumKonca() == null ? t.getDatumZacetka() : t.getDatumKonca();
        return new KoledarVnosDto(
                KoledarVnosDto.Vrsta.TURNIR,
                t.getId(),
                t.getIme(),
                t.getDatumZacetka(),
                konec.isBefore(t.getDatumZacetka()) ? t.getDatumZacetka() : konec,
                null,
                null,
                t.getKraj() == null ? null : t.getKraj().getIme(),
                t.getDvorana(),
                null,
                imeKluba(t.getKlubLastnik()),
                t.getStatus(),
                List.of());
    }

    // ---------- Kola lig ----------

    /* Srecanja obdobja, zdruzena v vnos na (liga, kolo). Meji poizvedbe sta
       namenoma za dan sirsi (glej SrecanjeRepozitorij.najdiVObdobju); tocno
       omejitev naredimo tu po DATUMU termina. */
    private List<KoledarVnosDto> kolaLig(LocalDate od, LocalDate doKdaj) {
        LocalDateTime odCas = od.minusDays(1).atStartOfDay();
        LocalDateTime doCas = doKdaj.plusDays(2).atStartOfDay();

        Map<String, Kolo> kola = new LinkedHashMap<>();
        for (Srecanje s : srecanjeRepozitorij.najdiVObdobju(odCas, doCas)) {
            LocalDate dan = s.getPredvidenZacetek().toLocalDate();
            if (dan.isBefore(od) || dan.isAfter(doKdaj)) {
                continue;
            }
            /* Kljuc nosi tudi dan: prestavljeno srecanje kola (organizator ga
               sme premakniti posebej) sodi v koledar na svoj dan in ne na dan
               ostalih srecanj istega kola. */
            String kljuc = s.getLiga().getId() + "|" + s.getKolo() + "|" + dan;
            kola.computeIfAbsent(kljuc, k -> new Kolo(s.getLiga(), s.getKolo(), dan,
                    s.getPredvidenZacetek())).pari.add(par(s));
        }

        return kola.values().stream().map(Kolo::vDto).toList();
    }

    private static KoledarVnosDto.Par par(Srecanje s) {
        return new KoledarVnosDto.Par(
                s.getId(),
                s.getEkipaDomaci().prikazanoIme(),
                s.getEkipaGost().prikazanoIme());
    }

    /* Delovni zbir enega kola, preden postane vnos koledarja. */
    private static final class Kolo {
        private final Liga liga;
        private final int kolo;
        private final LocalDate dan;
        private final LocalDateTime zacetek;
        private final List<KoledarVnosDto.Par> pari = new ArrayList<>();

        private Kolo(Liga liga, int kolo, LocalDate dan, LocalDateTime zacetek) {
            this.liga = liga;
            this.kolo = kolo;
            this.dan = dan;
            this.zacetek = zacetek;
        }

        private KoledarVnosDto vDto() {
            return new KoledarVnosDto(
                    KoledarVnosDto.Vrsta.LIGA,
                    liga.getId(),
                    liga.getIme(),
                    dan,
                    dan,
                    zacetek,
                    kolo,
                    null,
                    null,
                    liga.getSezona(),
                    imeKluba(liga.getKlubLastnik()),
                    liga.getStatus(),
                    List.copyOf(pari));
        }
    }

    private static String imeKluba(Klub klub) {
        return klub == null ? null : klub.getIme();
    }
}
