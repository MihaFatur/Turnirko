/* Kaj sme racun glede na svoj placilni paket:
   - igralec s Premium vidi zasebno statistiko in sme spremljati lige
     (DostopDoProfila, DomovStoritev.spremljaj); brez Premium je kot gost,
     le prijavljen;
   - organizator sme na sezono ustvariti toliko turnirjev/lig, kolikor
     dovoljuje njegov paket (TurnirjiStoritev.ustvari, LigaStoritev.ustvari).

   Admin nima paketa in ni nikoli omejen - to preveri vsak klicatelj sam
   (isti vzorec kot LastnistvoStoritev: admin gre mimo, preden vpraša tu). */
package si.turnirko.storitve;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;

import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.PrepovedanoIzjema;
import si.turnirko.modeli.Narocnina;
import si.turnirko.modeli.Paket;
import si.turnirko.modeli.Sezona;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.LigaRepozitorij;
import si.turnirko.repozitoriji.NarocninaRepozitorij;
import si.turnirko.repozitoriji.TurnirRepozitorij;

@Service
public class NarocninaStoritev {

    /* Omejitve na sezono: [tekoce lige, ustvarjeni turnirji]. Sezona se
       ponastavi 1. julija (Sezona.zacetek) - isti rez kot starostni pas. */
    private record Omejitev(int lig, int turnirjev) {}

    private static final Omejitev BASIC = new Omejitev(1, 2);
    private static final Omejitev PLUS = new Omejitev(3, 5);
    private static final Omejitev PRO = new Omejitev(5, 10);

    private final NarocninaRepozitorij narocninaRepozitorij;
    private final TurnirRepozitorij turnirRepozitorij;
    private final LigaRepozitorij ligaRepozitorij;

    public NarocninaStoritev(NarocninaRepozitorij narocninaRepozitorij,
                             TurnirRepozitorij turnirRepozitorij,
                             LigaRepozitorij ligaRepozitorij) {
        this.narocninaRepozitorij = narocninaRepozitorij;
        this.turnirRepozitorij = turnirRepozitorij;
        this.ligaRepozitorij = ligaRepozitorij;
    }

    /* Veljavna narocnina racuna (AKTIVNA, ali PREKLICANA do konca placanega
       obdobja); prazno, ce racuna nima ali je zapadla. */
    public Optional<Narocnina> veljavna(Uporabnik u) {
        if (u == null) {
            return Optional.empty();
        }
        return narocninaRepozitorij.findByUporabnikId(u.getId())
                .filter(n -> n.jeVeljavna(LocalDateTime.now()));
    }

    /* Ali igralec sme videti zasebno statistiko in spremljati lige. */
    public boolean imaPremium(Uporabnik u) {
        if (u == null || u.getVloga() != Vloga.IGRALEC) {
            return false;
        }
        return veljavna(u).map(n -> n.getPaket() == Paket.PREMIUM).orElse(false);
    }

    /* Organizatorjev paket, ce ga (se) placuje. */
    public Optional<Paket> paketOrganizatorja(Uporabnik u) {
        if (u == null || u.getVloga() != Vloga.ORGANIZATOR) {
            return Optional.empty();
        }
        return veljavna(u).map(Narocnina::getPaket);
    }

    /* Sme organizator zdaj ustvariti se en turnir? Meji na sezono (glej
       Omejitev); admin ni organizator in sem sploh ne pride. */
    public void preveriOmejitevTurnirja(Uporabnik organizator) {
        Omejitev omejitev = omejitevAliZavrni(organizator);
        LocalDateTime odSezone = Sezona.zacetek(LocalDate.now()).atStartOfDay();
        long steviloVSezoni = turnirRepozitorij
                .countByUstvarilIdAndUstvarjenObGreaterThanEqual(organizator.getId(), odSezone);
        if (steviloVSezoni >= omejitev.turnirjev()) {
            throw new DomenskaIzjema("Dosegel si mejo " + omejitev.turnirjev()
                    + " ustvarjenih turnirjev na sezono za svoj paket. Nadgradi paket za vec.");
        }
    }

    /* Sme organizator zdaj ustvariti se eno ligo? Isto pravilo kot pri
       turnirju, samo stevec je locen (Omejitev.lig). */
    public void preveriOmejitevLige(Uporabnik organizator) {
        Omejitev omejitev = omejitevAliZavrni(organizator);
        LocalDateTime odSezone = Sezona.zacetek(LocalDate.now()).atStartOfDay();
        long steviloVSezoni = ligaRepozitorij
                .countByUstvarilIdAndUstvarjenObGreaterThanEqual(organizator.getId(), odSezone);
        if (steviloVSezoni >= omejitev.lig()) {
            throw new DomenskaIzjema("Dosegel si mejo " + omejitev.lig()
                    + " ustvarjenih lig na sezono za svoj paket. Nadgradi paket za vec.");
        }
    }

    private Omejitev omejitevAliZavrni(Uporabnik organizator) {
        Paket paket = paketOrganizatorja(organizator).orElseThrow(() -> new PrepovedanoIzjema(
                "Racun nima aktivnega organizatorskega paketa."));
        return switch (paket) {
            case ORGANIZATOR_BASIC -> BASIC;
            case ORGANIZATOR_PLUS -> PLUS;
            case ORGANIZATOR_PRO -> PRO;
            default -> throw new PrepovedanoIzjema("Racun nima aktivnega organizatorskega paketa.");
        };
    }
}
