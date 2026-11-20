/* Vadnica stevk (nacrt F3): hladen zacetek ucenja mreze s slovensko pisavo.

   Admin natisne liste (vmesnik, id lista izbere nakljucno), ljudje jih izpolnijo
   (vsak en list), admin jih fotografira. Storitev list prebere (BranjeVadnice)
   in shrani stevke z oznako vrstice, vendar NE za ucenje: zlonamerno ali
   napacno napisan list bi mrezo pokvaril (zastrupitev). Admin list pregleda,
   izloci slabe stevke in ga odobri; sele tedaj gredo stevke v nocno ucenje
   (UcenjeIzListkov), preizkusni del se deli po listu (en list = en pisec).

   Vse je samo za admina (pot zapre ze VarnostneNastavitve). Fotografija lista
   se ne hrani, le poravnan list s samimi okenci (list nima polj za ime). Nova
   fotografija istega neodobrenega lista zamenja prejsnjo; odobrenega ne. */
package si.turnirko.storitve;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import si.turnirko.branje.UcenjeStevk;
import si.turnirko.dto.OdobritevVadniceVnos;
import si.turnirko.dto.VadnicaDto;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.izjeme.PrepovedanoIzjema;
import si.turnirko.modeli.StevkaListka;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vadnica;
import si.turnirko.modeli.Vloga;
import si.turnirko.pomozno.ObdelavaSlik;
import si.turnirko.repozitoriji.StevkaListkaRepozitorij;
import si.turnirko.repozitoriji.VadnicaRepozitorij;

@Service
public class VadnicaStoritev {

    /* Daljsa stranica fotografije za branje: A4 je pri 3200 px ~11 px/mm, stevka v
       okencu 10 mm ~70 px - kot na listku pri 2000 px. Vmesnik poslje do 3200 px. */
    static final int NAJVEC_STRANICA = 3200;
    /* Seznam listov pokaze toliko zadnjih (vadnica ima nekaj deset listov). */
    static final int NAJVEC_V_SEZNAMU = 500;

    private final VadnicaRepozitorij vadnice;
    private final StevkaListkaRepozitorij stevke;
    private final LastnistvoStoritev lastnistvo;
    private final BranjeListkovStoritev branje;
    private final VirMrezeStevk vir;
    private final TransactionTemplate transakcija;

    public VadnicaStoritev(VadnicaRepozitorij vadnice, StevkaListkaRepozitorij stevke,
                           LastnistvoStoritev lastnistvo, BranjeListkovStoritev branje, VirMrezeStevk vir,
                           PlatformTransactionManager upraviteljTransakcij) {
        this.vadnice = vadnice;
        this.stevke = stevke;
        this.lastnistvo = lastnistvo;
        this.branje = branje;
        this.vir = vir;
        this.transakcija = new TransactionTemplate(upraviteljTransakcij);
    }

    private Uporabnik preveriAdmina() {
        Uporabnik jaz = lastnistvo.trenutni();
        if (jaz == null || jaz.getVloga() != Vloga.ADMIN) {
            throw new PrepovedanoIzjema("Vadnica števk je samo za administratorja.");
        }
        return jaz;
    }

    /* Fotografija izpolnjenega lista. Branje (dekodiranje, poravnava A4, 140
       okenc) tece brez transakcije pod skupno mejo hkratnih dekodiranj. */
    public VadnicaDto sprejmi(byte[] vsebina) {
        Long idUporabnika = preveriAdmina().getId();
        VirMrezeStevk.Nalozena nalozena = vir.trenutna();
        if (nalozena == null) {
            throw new DomenskaIzjema("Branje števk na tem strežniku ni nastavljeno (manjka mreža števk).");
        }
        BranjeVadnice.Prebrana prebrana;
        try {
            prebrana = branje.podDekodiranjem(idUporabnika, () -> BranjeVadnice.preberi(
                    ObdelavaSlik.pomanjsaj(ObdelavaSlik.preberi(vsebina, BranjeListkovStoritev.NAJVEC_STRANICA_VHODA,
                            BranjeListkovStoritev.NAJVEC_PIKSLOV_VHODA), NAJVEC_STRANICA),
                    nalozena.mreza()));
        } catch (BralnikListka.NapakaBranjaListka e) {
            throw new NeveljavenVnosIzjema(e.getMessage());
        }
        if (prebrana.stevke().isEmpty()) {
            throw new NeveljavenVnosIzjema("Na listu ni nobene prepoznane števke. Je list izpolnjen?");
        }
        return transakcija.execute(s -> {
            preveriAdmina();
            Vadnica obstojeca = vadnice.findById(prebrana.idLista()).orElse(null);
            if (obstojeca != null && obstojeca.isOdobrena()) {
                throw new DomenskaIzjema("List " + oznaka(obstojeca.getId())
                        + " je že odobren. Če ga želiš prebrati znova, ga najprej izbriši.");
            }
            Vadnica list = obstojeca != null ? obstojeca : new Vadnica(prebrana.idLista(), prebrana.razlicica());
            if (obstojeca != null) {
                stevke.izbrisiVadnice(list.getId());
            }
            LocalDateTime zdaj = LocalDateTime.now();
            list.nastaviBranje(prebrana.slika(), prebrana.stevke().size(), prebrana.praznih(),
                    prebrana.preskocenih(), zdaj);
            Vadnica shranjena = vadnice.save(list);
            stevke.saveAll(prebrana.stevke().stream()
                    .map(st -> StevkaListka.zVadnice(shranjena.getId(), st.okence(), UcenjeStevk.vBajte(st.vhod()),
                            st.vrednost(), st.mrezaPrav(), zdaj))
                    .toList());
            return dto(shranjena);
        });
    }

    public VadnicaDto.Seznam seznam() {
        return transakcija.execute(s -> {
            preveriAdmina();
            return new VadnicaDto.Seznam(vadnice.seznam(PageRequest.of(0, NAJVEC_V_SEZNAMU)),
                    stevke.countByIdVadnicaIsNotNullAndOdobrenoTrue());
        });
    }

    public VadnicaDto.Podrobno podrobno(Long id) {
        return transakcija.execute(s -> {
            preveriAdmina();
            Vadnica list = najdi(id);
            Base64.Encoder base64 = Base64.getEncoder();
            List<VadnicaDto.Stevka> zapis = stevke.findByIdVadnicaOrderByCelicaAscIdAsc(id).stream()
                    .map(st -> new VadnicaDto.Stevka(st.getId(), st.getCelica(), st.getVrednost(),
                            base64.encodeToString(st.getSlika()), st.isMrezaPrav()))
                    .toList();
            return new VadnicaDto.Podrobno(dto(list), zapis);
        });
    }

    /* Poravnan list s samimi okenci (PNG) za pregled. */
    public byte[] slika(Long id) {
        return transakcija.execute(s -> {
            preveriAdmina();
            return najdi(id).getSlika();
        });
    }

    /* Odobritev: izlocene stevke se izbrisejo, ostale gredo v ucenje. Izlociti se
       da samo stevke tega lista (poizvedba je omejena na list). */
    public VadnicaDto odobri(Long id, OdobritevVadniceVnos vnos) {
        List<Long> izlocene = vnos == null || vnos.izlocene() == null ? List.of() : vnos.izlocene();
        return transakcija.execute(s -> {
            preveriAdmina();
            if (najdi(id).isOdobrena()) {
                throw new DomenskaIzjema("List " + oznaka(id) + " je že odobren.");
            }
            if (!izlocene.isEmpty()) {
                stevke.izlociVadnice(id, izlocene);
            }
            long ostalih = stevke.countByIdVadnica(id);
            if (ostalih == 0) {
                throw new DomenskaIzjema("Izločene so vse števke lista. Če list ni uporaben, ga izbriši.");
            }
            LocalDateTime zdaj = LocalDateTime.now();
            if (vadnice.odobri(id, (int) ostalih, zdaj) == 0) {
                throw new DomenskaIzjema("List " + oznaka(id) + " je že odobren.");
            }
            stevke.odobriVadnico(id, zdaj);
            return dto(najdi(id));
        });
    }

    /* Izbris lista in njegovih stevk (tudi odobrenega: stevke ne gredo vec v ucenje,
       ze naucena mreza pa ostane). */
    public void izbrisi(Long id) {
        transakcija.executeWithoutResult(s -> {
            preveriAdmina();
            najdi(id);
            stevke.izbrisiVadnice(id);
            vadnice.deleteById(id);
        });
    }

    private Vadnica najdi(Long id) {
        return vadnice.findById(id).orElseThrow(() -> new NiNajdenoIzjema("List vadnice ne obstaja."));
    }

    /* Oznaka lista, kot je natisnjena pod QR kodo (vadnica.ts v vmesniku). */
    static String oznaka(long id) {
        return "V-" + id;
    }

    private static VadnicaDto dto(Vadnica v) {
        return new VadnicaDto(v.getId(), v.getStevk(), v.getPraznih(), v.getPreskocenih(), v.isOdobrena(),
                v.getUstvarjenoOb(), v.getOdobrenoOb());
    }
}
