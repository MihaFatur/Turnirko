/* Meritev branja listkov iz baze (nacrt F1.3, samo admin): branja izbranega
   obdobja (in turnirja) se primerjajo s potrditvami - skupaj, po turnirjih in po
   razlicicah mreze. Izracun je v MeritevBranja.

   Obdobje je po datumu nastanka branja, oba dneva vkljucno. Datum je po uri
   streznika, tako kot je zapisan ustvarjeno_ob (LocalDateTime.now()).
   Prebere najvec NAJVEC_BRANJ vrstic (brez fotografij); daljse obdobje je treba
   razdeliti, da meritev ne napolni pomnilnika streznika. */
package si.turnirko.storitve;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.MeritevBranjaDto;
import si.turnirko.dto.VnosRezultata;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.PrepovedanoIzjema;
import si.turnirko.modeli.PrepoznavaTekme;
import si.turnirko.modeli.StanjeBranja;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.BranjeListkaRepozitorij;
import si.turnirko.repozitoriji.VrsticaMeritveBranja;

@Service
public class MeritevBranjaStoritev {

    private static final Logger log = LoggerFactory.getLogger(MeritevBranjaStoritev.class);

    /* Zgornja meja branj v eni meritvi (~25 MB zapisov v pomnilniku). */
    static final int NAJVEC_BRANJ = 20_000;
    /* Privzeto obdobje: zadnje leto. */
    static final int PRIVZETO_DNI = 365;
    static final String BREZ_OZNAKE = "brez oznake";

    private final BranjeListkaRepozitorij branja;
    private final LastnistvoStoritev lastnistvo;

    public MeritevBranjaStoritev(BranjeListkaRepozitorij branja, LastnistvoStoritev lastnistvo) {
        this.branja = branja;
        this.lastnistvo = lastnistvo;
    }

    @Transactional(readOnly = true)
    public MeritevBranjaDto meritev(LocalDate od, LocalDate doDatuma, Long idTurnir) {
        preveriAdmina(lastnistvo.trenutni());
        LocalDate konec = doDatuma != null ? doDatuma : LocalDate.now();
        LocalDate zacetek = od != null ? od : konec.minusDays(PRIVZETO_DNI);
        if (zacetek.getYear() < 2020 || konec.getYear() > 2100) {
            throw new NeveljavenVnosIzjema("Obdobje mora biti med letoma 2020 in 2100.");
        }
        if (zacetek.isAfter(konec)) {
            throw new NeveljavenVnosIzjema("Začetek obdobja je za koncem.");
        }
        PageRequest stran = PageRequest.of(0, NAJVEC_BRANJ + 1);
        List<VrsticaMeritveBranja> vrstice = idTurnir == null
                ? branja.zaMeritev(zacetek.atStartOfDay(), konec.plusDays(1).atStartOfDay(), stran)
                : branja.zaMeritevTurnirja(zacetek.atStartOfDay(), konec.plusDays(1).atStartOfDay(), idTurnir, stran);
        if (vrstice.size() > NAJVEC_BRANJ) {
            throw new NeveljavenVnosIzjema("V obdobju je več kot " + NAJVEC_BRANJ
                    + " branj. Izberi krajše obdobje ali en turnir.");
        }

        MeritevBranja.Zbirnik skupaj = new MeritevBranja.Zbirnik("Skupaj", null, null);
        Map<Long, MeritevBranja.Zbirnik> poTurnirjih = new LinkedHashMap<>();
        Map<String, MeritevBranja.Zbirnik> poMrezah = new LinkedHashMap<>();
        // vir fotografije: sodnik s svojim telefonom (S2) ali organizator na pultu -
        // drug telefon, druga luc, drug kot; diagnostika primerja oba (24. 11. 2026)
        MeritevBranja.Zbirnik sodnik = new MeritevBranja.Zbirnik("Sodnik (svoj telefon)", null, null);
        MeritevBranja.Zbirnik pult = new MeritevBranja.Zbirnik("Pult (organizator)", null, null);
        for (VrsticaMeritveBranja v : vrstice) {
            MeritevBranja.Vzorec vzorec = vzorec(v);
            skupaj.dodaj(vzorec);
            (v.poslalSodnik() ? sodnik : pult).dodaj(vzorec);
            poTurnirjih.computeIfAbsent(v.idTurnir(),
                    k -> new MeritevBranja.Zbirnik(v.imeTurnirja(), v.idTurnir(), v.datumTurnirja())).dodaj(vzorec);
            // mreza je znana samo pri prebranih; neuspela branja ne gredo k nobeni
            if (v.prebrano() != null) {
                String mreza = v.model() == null ? BREZ_OZNAKE : v.model();
                poMrezah.computeIfAbsent(mreza, k -> new MeritevBranja.Zbirnik(k, null, null)).dodaj(vzorec);
            }
        }
        List<MeritevBranjaDto.Skupina> turnirji = poTurnirjih.values().stream()
                .map(MeritevBranja.Zbirnik::dto)
                .sorted((a, b) -> {
                    // najnovejsi turnir zgoraj; brez datuma na koncu
                    if (a.datum() == null || b.datum() == null) {
                        return a.datum() == null ? (b.datum() == null ? 0 : 1) : -1;
                    }
                    int c = b.datum().compareTo(a.datum());
                    return c != 0 ? c : Long.compare(b.idTurnir(), a.idTurnir());
                })
                .toList();
        return new MeritevBranjaDto(zacetek, konec, idTurnir, skupaj.dto(), turnirji,
                poMrezah.values().stream().map(MeritevBranja.Zbirnik::dto).toList(),
                List.of(sodnik.dto(), pult.dto()));
    }

    static void preveriAdmina(Uporabnik jaz) {
        if (jaz == null || jaz.getVloga() != Vloga.ADMIN) {
            throw new PrepovedanoIzjema("Meritev branja listkov je samo za administratorja.");
        }
    }

    /* Vrstica branja kot vzorec meritve. Nebrljiv zapis (ne bi smel nastati)
       se steje kot branje brez primerjave celic in se zapise v dnevnik. */
    static MeritevBranja.Vzorec vzorec(VrsticaMeritveBranja v) {
        boolean prebran = v.prebrano() != null;
        boolean potrjen = v.stanje() == StanjeBranja.POTRJENO;
        MeritevBranja.Celice celice = null;
        if (prebran && potrjen && v.potrjeno() != null) {
            try {
                celice = MeritevBranja.celice(JsonBranja.preberi(v.prebrano(), PrebranListek.class),
                        JsonBranja.preberi(v.potrjeno(), VnosRezultata.class),
                        v.prepoznava() == PrepoznavaTekme.KODA);
            } catch (IllegalStateException e) {
                log.warn("Meritev branja: zapis branja turnirja {} ni berljiv", v.idTurnir());
            }
        }
        return new MeritevBranja.Vzorec(prebran, !prebran && v.stanje() != StanjeBranja.V_VRSTI, potrjen,
                v.popravljeno(), v.pripravljen(), v.kontrola(), v.casPregledaMs(), v.nacinPotrditve(), celice);
    }
}
