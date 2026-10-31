/* Podpora klubu (V61, faza 3 paketa Navijac): navijac s placljivim paketom izbere
   klub, ki ga podpira; ko ima klub dovolj podpornikov, dobi njegov organizator
   popust na organizatorski paket. Izplacil ni - iz s.p. ne tece noben denar klubu;
   klub dobi razlog, da Navijaca priporoci starsem.

   Pravila, ki jih ne razbij:
   - **Steje samo racun s paketom Navijac ZDAJ.** Vrstica podpore sama ni dokaz,
     da podpornik se placuje: popust bi sicer ostal tudi po preklicu vseh. Stevilo
     se prebere ob vsakem vprasanju, ne hrani.
   - **Stopnje**: vsakih 10 podpornikov doda 10 % popusta, najvec 30 %. Meje so
     pravilo (konstante), ne shema; spremenijo se z eno vrstico.
   - **Popust velja za PRVO placilo organizatorskega paketa** (nakup ali nadgradnja
     prek Checkouta, Stripov kupon `once` kot pri kodah popusta): obnova je po polni
     ceni. Samodejen popust ob obnovi bi terjal posegu v Stripovo narocnino pred
     vsakim obdobjem, ki ga na sandboxu nismo preverili.
   - **Organizator je organizator kluba, ki ga potrdi admin** (uporabnik.id_klub): klubska
     zelja ob registraciji se ne steje.
   - Racun podpira najvec en klub; menjava je prepis. */
package si.turnirko.storitve;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.PodporaKlubaDto;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.izjeme.PrepovedanoIzjema;
import si.turnirko.modeli.Klub;
import si.turnirko.modeli.PodporaKluba;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.KlubRepozitorij;
import si.turnirko.repozitoriji.PodporaKlubaRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

@Service
public class PodporaKlubaStoritev {

    /* Vsakih toliko podpornikov klub pridobi eno stopnjo popusta. */
    public static final int PODPORNIKOV_NA_STOPNJO = 10;
    public static final int ODSTOTEK_NA_STOPNJO = 10;
    public static final int NAJVEC_ODSTOTKOV = 30;

    private final PodporaKlubaRepozitorij repozitorij;
    private final KlubRepozitorij klubRepozitorij;
    private final UporabnikRepozitorij uporabnikRepozitorij;
    private final NarocninaStoritev narocnina;
    private final LastnistvoStoritev lastnistvo;
    private final StripeNarocnine stripe;

    /* Stripov kupon po odstotku: nastane ob prvem placilu s tem popustom. Pomnilnik zadostuje -
       po ponovnem zagonu nastane nov kupon z istim imenom, ki ga Stripe sprejme. */
    private final Map<Integer, String> kuponi = new ConcurrentHashMap<>();

    public PodporaKlubaStoritev(PodporaKlubaRepozitorij repozitorij,
                                KlubRepozitorij klubRepozitorij,
                                UporabnikRepozitorij uporabnikRepozitorij,
                                NarocninaStoritev narocnina,
                                LastnistvoStoritev lastnistvo,
                                StripeNarocnine stripe) {
        this.repozitorij = repozitorij;
        this.klubRepozitorij = klubRepozitorij;
        this.uporabnikRepozitorij = uporabnikRepozitorij;
        this.narocnina = narocnina;
        this.lastnistvo = lastnistvo;
        this.stripe = stripe;
    }

    // ---------- Racun ----------

    @Transactional(readOnly = true)
    public PodporaKlubaDto moja() {
        Uporabnik jaz = zahtevajPrijavo();
        return repozitorij.findById(jaz.getId())
                .map(p -> dto(p.getIdKlub()))
                .orElse(new PodporaKlubaDto(false, narocnina.imaNavijaca(jaz), null, null, null));
    }

    @Transactional
    public PodporaKlubaDto izberi(Long idKlub) {
        Uporabnik jaz = zahtevajPrijavo();
        if (!narocnina.imaNavijaca(jaz)) {
            throw new PrepovedanoIzjema("Podpora klubu je del paketa Navijač.");
        }
        klubRepozitorij.findById(idKlub).orElseThrow(() -> new NiNajdenoIzjema("Klub ne obstaja."));
        PodporaKluba p = repozitorij.findById(jaz.getId()).orElse(null);
        if (p == null) {
            repozitorij.save(new PodporaKluba(jaz.getId(), idKlub, LocalDateTime.now()));
        } else if (!p.getIdKlub().equals(idKlub)) {
            p.setIdKlub(idKlub);
            p.setOd(LocalDateTime.now());
        }
        return dto(idKlub);
    }

    @Transactional
    public PodporaKlubaDto umakni() {
        Uporabnik jaz = zahtevajPrijavo();
        repozitorij.deleteById(jaz.getId());
        return new PodporaKlubaDto(false, narocnina.imaNavijaca(jaz), null, null, null);
    }

    private PodporaKlubaDto dto(Long idKlub) {
        Klub klub = klubRepozitorij.findById(idKlub).orElse(null);
        return new PodporaKlubaDto(true, true, klub == null ? null : klub.getId(),
                klub == null ? null : klub.getIme(), popust(idKlub));
    }

    // ---------- Popust ----------

    /* Kaj klub ZDAJ ima: stevilo podpornikov z veljavnim paketom, odstotek in naslednja stopnja. */
    @Transactional(readOnly = true)
    public PodporaKlubaDto.Popust popust(Long idKlub) {
        int podpornikov = 0;
        for (PodporaKluba p : repozitorij.findByIdKlub(idKlub)) {
            Uporabnik u = uporabnikRepozitorij.findById(p.getIdRacun()).orElse(null);
            if (u != null && u.isAktiven() && narocnina.imaNavijaca(u)) {
                podpornikov++;
            }
        }
        return popustZa(podpornikov);
    }

    static PodporaKlubaDto.Popust popustZa(int podpornikov) {
        int stopenj = podpornikov / PODPORNIKOV_NA_STOPNJO;
        int odstotek = Math.min(NAJVEC_ODSTOTKOV, stopenj * ODSTOTEK_NA_STOPNJO);
        Integer naslednjaMeja = odstotek >= NAJVEC_ODSTOTKOV ? null : (stopenj + 1) * PODPORNIKOV_NA_STOPNJO;
        Integer naslednjiOdstotek = naslednjaMeja == null ? null
                : Math.min(NAJVEC_ODSTOTKOV, (stopenj + 1) * ODSTOTEK_NA_STOPNJO);
        return new PodporaKlubaDto.Popust(podpornikov, odstotek, naslednjaMeja, naslednjiOdstotek);
    }

    /* Popust za organizatorja kluba (potrjen klub racuna); 0, ce organizator kluba nima. */
    @Transactional(readOnly = true)
    public int popustZaOrganizatorja(Uporabnik organizator) {
        if (organizator == null || organizator.getVloga() != Vloga.ORGANIZATOR || organizator.getKlub() == null) {
            return 0;
        }
        return popust(organizator.getKlub().getId()).odstotek();
    }

    /* Popust kluba prijavljenega organizatorja za stran Narocnina (z navedbo kluba). */
    @Transactional(readOnly = true)
    public PodporaKlubaDto zaOrganizatorja() {
        Uporabnik jaz = zahtevajPrijavo();
        if (jaz.getVloga() != Vloga.ORGANIZATOR || jaz.getKlub() == null) {
            return new PodporaKlubaDto(false, false, null, null, null);
        }
        Klub klub = jaz.getKlub();
        return new PodporaKlubaDto(false, false, klub.getId(), klub.getIme(), popust(klub.getId()));
    }

    /* Stripov kupon za odstotek (ustvari se ob prvi uporabi). Brez @Transactional: klic Stripa ne
       sme drzati transakcije (en sam pisalec v SQLite). */
    public String stripeKupon(int odstotek) {
        return kuponi.computeIfAbsent(odstotek,
                o -> stripe.ustvariKupon("Podpora kluba " + o + " %", o));
    }

    private Uporabnik zahtevajPrijavo() {
        Uporabnik jaz = lastnistvo.trenutni();
        if (jaz == null) {
            throw new PrepovedanoIzjema("Za podporo klubu je potrebna prijava.");
        }
        return jaz;
    }

    /* Za teste: zbrisi shranjene kupone. */
    void pozabiKupone() {
        kuponi.clear();
    }

}
