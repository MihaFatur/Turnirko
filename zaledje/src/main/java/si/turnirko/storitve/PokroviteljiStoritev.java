/* Pokrovitelji lige in turnirja (V56): logotipi v traku na dnu strani in v nogi
   tiska (žreb mreže, Turnirkov zapisnik).

   Kot logotip tekmovanja je to PRIKAZ, ne pravilo: ureja ga lastnik (ali
   klubski kolega) in admin, v vsakem stanju in tudi pri uvoženem tekmovanju.
   Logotip pokrovitelja je slika iz iste knjižnice (SlikeStoritev) in za tujo
   sliko velja isto pravilo - samo, če je že javna.

   Seznam se shrani v celoti (kot jakostni vrstni red): organizator ureja trak,
   ne posameznih vrstic, in vmesnih stanj ni. */
package si.turnirko.storitve;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.PokroviteljDto;
import si.turnirko.dto.PokroviteljiVnos;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.modeli.Liga;
import si.turnirko.modeli.Pokrovitelj;
import si.turnirko.modeli.Turnir;
import si.turnirko.repozitoriji.LigaRepozitorij;
import si.turnirko.repozitoriji.PokroviteljRepozitorij;
import si.turnirko.repozitoriji.TurnirRepozitorij;

@Service
public class PokroviteljiStoritev {

    /* Več jih trak ne nosi, ne da bi bil vsak logotip droben. */
    static final int NAJVEC_POKROVITELJEV = 8;
    static final int NAJDALJSE_IME = 80;
    static final int NAJDALJSA_POVEZAVA = 300;
    /* Samo spletna stran - javascript: ali data: v href bi bil skript na naši strani. */
    private static final Pattern POVEZAVA = Pattern.compile("^https?://[^\\s<>\"]+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHEMA = Pattern.compile("^[a-z][a-z0-9+.-]*:", Pattern.CASE_INSENSITIVE);

    private final PokroviteljRepozitorij pokroviteljRepozitorij;
    private final LigaRepozitorij ligaRepozitorij;
    private final TurnirRepozitorij turnirRepozitorij;
    private final LastnistvoStoritev lastnistvoStoritev;
    private final SlikeStoritev slikeStoritev;

    public PokroviteljiStoritev(PokroviteljRepozitorij pokroviteljRepozitorij,
                                LigaRepozitorij ligaRepozitorij,
                                TurnirRepozitorij turnirRepozitorij,
                                LastnistvoStoritev lastnistvoStoritev,
                                SlikeStoritev slikeStoritev) {
        this.pokroviteljRepozitorij = pokroviteljRepozitorij;
        this.ligaRepozitorij = ligaRepozitorij;
        this.turnirRepozitorij = turnirRepozitorij;
        this.lastnistvoStoritev = lastnistvoStoritev;
        this.slikeStoritev = slikeStoritev;
    }

    @Transactional(readOnly = true)
    public List<PokroviteljDto> zaLigo(Long idLiga) {
        return pokroviteljRepozitorij.findByLigaIdOrderByVrstniRed(idLiga).stream()
                .map(PokroviteljDto::iz).toList();
    }

    @Transactional(readOnly = true)
    public List<PokroviteljDto> zaTurnir(Long idTurnir) {
        return pokroviteljRepozitorij.findByTurnirIdOrderByVrstniRed(idTurnir).stream()
                .map(PokroviteljDto::iz).toList();
    }

    @Transactional
    public List<PokroviteljDto> shraniZaLigo(Long idLiga, List<PokroviteljiVnos.Pokrovitelj> vnos) {
        lastnistvoStoritev.preveriLigaZaPrikazPoId(idLiga);
        Liga liga = ligaRepozitorij.findById(idLiga)
                .orElseThrow(() -> new NiNajdenoIzjema("Liga z id " + idLiga + " ne obstaja."));
        List<Pokrovitelj> novi = sestavi(vnos, liga, null);
        pokroviteljRepozitorij.deleteAll(pokroviteljRepozitorij.findByLigaIdOrderByVrstniRed(idLiga));
        pokroviteljRepozitorij.flush();
        return pokroviteljRepozitorij.saveAll(novi).stream().map(PokroviteljDto::iz).toList();
    }

    @Transactional
    public List<PokroviteljDto> shraniZaTurnir(Long idTurnir, List<PokroviteljiVnos.Pokrovitelj> vnos) {
        lastnistvoStoritev.preveriTurnirZaPrikazPoId(idTurnir);
        Turnir turnir = turnirRepozitorij.findById(idTurnir)
                .orElseThrow(() -> new NiNajdenoIzjema("Turnir z id " + idTurnir + " ne obstaja."));
        List<Pokrovitelj> novi = sestavi(vnos, null, turnir);
        pokroviteljRepozitorij.deleteAll(pokroviteljRepozitorij.findByTurnirIdOrderByVrstniRed(idTurnir));
        pokroviteljRepozitorij.flush();
        return pokroviteljRepozitorij.saveAll(novi).stream().map(PokroviteljDto::iz).toList();
    }

    /* Celoten seznam se preveri PRED izbrisom starega: napaka v petem
       pokrovitelju ne sme pustiti traku praznega. */
    private List<Pokrovitelj> sestavi(List<PokroviteljiVnos.Pokrovitelj> vnos, Liga liga, Turnir turnir) {
        List<PokroviteljiVnos.Pokrovitelj> seznam = vnos == null ? List.of() : vnos;
        if (seznam.size() > NAJVEC_POKROVITELJEV) {
            throw new NeveljavenVnosIzjema("Največ " + NAJVEC_POKROVITELJEV + " pokroviteljev.");
        }
        if (seznam.stream().filter(PokroviteljiVnos.Pokrovitelj::glavni).count() > 1) {
            throw new NeveljavenVnosIzjema("Glavni pokrovitelj je lahko samo eden.");
        }
        List<Pokrovitelj> novi = new ArrayList<>();
        for (int i = 0; i < seznam.size(); i++) {
            PokroviteljiVnos.Pokrovitelj p = seznam.get(i);
            String ime = p.ime() == null ? "" : p.ime().trim();
            if (ime.isEmpty() || ime.length() > NAJDALJSE_IME) {
                throw new NeveljavenVnosIzjema("Ime pokrovitelja mora imeti od 1 do " + NAJDALJSE_IME + " znakov.");
            }
            novi.add(new Pokrovitelj(liga, turnir, slikeStoritev.zaUporabo(p.idSlika()), ime,
                    povezava(p.povezava()), p.glavni(), i + 1));
        }
        return novi;
    }

    /* Prazno = brez povezave. Brez sheme (»petrol.si«) dobi https://, ker jo
       organizatorji tako vpisujejo; drugačna shema (javascript:, mailto:) se
       zavrne - predpona bi iz nje naredila nesmiseln naslov. */
    static String povezava(String vnos) {
        if (vnos == null || vnos.isBlank()) {
            return null;
        }
        String p = vnos.trim();
        if (!SHEMA.matcher(p).find()) {
            p = "https://" + p;
        }
        if (p.length() > NAJDALJSA_POVEZAVA || !POVEZAVA.matcher(p).matches()) {
            throw new NeveljavenVnosIzjema("Povezava mora biti spletni naslov (https://…).");
        }
        return p;
    }
}
