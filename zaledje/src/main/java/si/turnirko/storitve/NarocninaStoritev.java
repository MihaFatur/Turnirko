/* Kaj sme racun glede na svoj placilni paket:
   - igralec s Premium vidi zasebno statistiko (DostopDoProfila) in domaco
     stran po meri (DomovStoritev, DomaciTurnirjiStoritev); spremljanje lig in
     obvestila po kolu ima od 23. 10. 2026 vsak racun;
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
import si.turnirko.modeli.DruzinaPovezava;
import si.turnirko.modeli.Narocnina;
import si.turnirko.modeli.Paket;
import si.turnirko.modeli.Sezona;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.DruzinaPovezavaRepozitorij;
import si.turnirko.repozitoriji.LigaRepozitorij;
import si.turnirko.repozitoriji.NarocninaRepozitorij;
import si.turnirko.repozitoriji.TurnirRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

@Service
public class NarocninaStoritev {

    /* Omejitve na sezono: [tekoce lige, ustvarjeni turnirji]. Sezona se
       ponastavi 1. julija (Sezona.zacetek) - isti rez kot starostni pas.
       Javna, ker jo bere tudi organizatorski pregled: stran mora pokazati
       natanko tisto mejo in tisto stevilo, ki ju strezniku ob ustvarjanju
       preveri ta razred - ce bi ju stela sama, bi se sceasoma razsla. */
    public record Omejitev(int lig, int turnirjev) {}

    private static final Omejitev BASIC = new Omejitev(1, 2);
    private static final Omejitev PLUS = new Omejitev(3, 5);
    private static final Omejitev PRO = new Omejitev(5, 10);

    private final NarocninaRepozitorij narocninaRepozitorij;
    private final TurnirRepozitorij turnirRepozitorij;
    private final LigaRepozitorij ligaRepozitorij;
    private final DruzinaPovezavaRepozitorij druzinaRepozitorij;
    private final UporabnikRepozitorij uporabnikRepozitorij;

    public NarocninaStoritev(NarocninaRepozitorij narocninaRepozitorij,
                             TurnirRepozitorij turnirRepozitorij,
                             LigaRepozitorij ligaRepozitorij,
                             DruzinaPovezavaRepozitorij druzinaRepozitorij,
                             UporabnikRepozitorij uporabnikRepozitorij) {
        this.narocninaRepozitorij = narocninaRepozitorij;
        this.turnirRepozitorij = turnirRepozitorij;
        this.ligaRepozitorij = ligaRepozitorij;
        this.druzinaRepozitorij = druzinaRepozitorij;
        this.uporabnikRepozitorij = uporabnikRepozitorij;
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

    /* Ali igralec sme videti zasebno statistiko in domaco stran po meri: z lastnim
       paketom ali (V59) prek druzine. */
    public boolean imaPremium(Uporabnik u) {
        if (u == null || u.getVloga() != Vloga.IGRALEC) {
            return false;
        }
        return veljavna(u).map(n -> n.getPaket() == Paket.PREMIUM).orElse(false)
                || imaDruzinskiPremium(u);
    }

    /* Premium prek druzine (V59): igralec s potrjenim racunom, ki ga ima v druzini
       navijac s paketom Navijac. Potrjena povezava, ki veljaOb, in placnik s paketom;
       sicer paket ni nikomur placan, zato tudi Premium ne. Vrstice ne ustvarja - igralec
       ne dobi lastne narocnine, Premium samo velja. */
    public boolean imaDruzinskiPremium(Uporabnik u) {
        if (u == null || u.getVloga() != Vloga.IGRALEC || !u.jePotrjenIgralec()) {
            return false;
        }
        LocalDateTime zdaj = LocalDateTime.now();
        return druzinaRepozitorij.findByIdIgralecAndStatus(u.getIgralec().getId(), DruzinaPovezava.Status.POTRJENA)
                .stream()
                .filter(p -> p.veljaOb(zdaj))
                .map(p -> uporabnikRepozitorij.findById(p.getIdRacun()).orElse(null))
                .anyMatch(placnik -> placnik != null && imaNavijaca(placnik));
    }

    /* Ali ima navijac placljiv paket Navijac (V58): aktiven, preklican do konca
       placanega obdobja ali preizkus s kodo NAVIJAC_MESECI. */
    public boolean imaNavijaca(Uporabnik u) {
        if (u == null || u.getVloga() != Vloga.NAVIJAC) {
            return false;
        }
        return veljavna(u).map(n -> n.getPaket() == Paket.NAVIJAC).orElse(false);
    }

    /* Ugodnosti, ki jih imata paket Navijac in Igralec Premium skupaj: neomejeno
       spremljanih igralcev, e-posta po turnirju, osebni koledar .ics in napoved
       dvoboja za kateri koli par. Brezplacen racun jih nima; admin je vedno brez
       omejitev (kot povsod, kjer paket kaj omejuje). */
    public boolean imaSirokoSpremljanje(Uporabnik u) {
        if (u != null && u.getVloga() == Vloga.ADMIN) {
            return true;
        }
        return imaNavijaca(u) || imaPremium(u);
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
        long steviloVSezoni = steviloTurnirjevVSezoni(organizator.getId(), LocalDate.now());
        if (steviloVSezoni >= omejitev.turnirjev()) {
            throw new DomenskaIzjema("Dosegel si mejo " + omejitev.turnirjev()
                    + " ustvarjenih turnirjev na sezono za svoj paket. Nadgradi paket za vec.");
        }
    }

    /* Sme organizator zdaj ustvariti se eno ligo? Isto pravilo kot pri
       turnirju, samo stevec je locen (Omejitev.lig). */
    public void preveriOmejitevLige(Uporabnik organizator) {
        Omejitev omejitev = omejitevAliZavrni(organizator);
        long steviloVSezoni = steviloLigVSezoni(organizator.getId(), LocalDate.now());
        if (steviloVSezoni >= omejitev.lig()) {
            throw new DomenskaIzjema("Dosegel si mejo " + omejitev.lig()
                    + " ustvarjenih lig na sezono za svoj paket. Nadgradi paket za vec.");
        }
    }

    /* Koliko turnirjev oz. lig je racun ustvaril v sezoni, ki tece na dani dan.
       Ena metoda za preverbo ob ustvarjanju in za pregled organizatorja. */
    public long steviloTurnirjevVSezoni(Long idUporabnik, LocalDate danes) {
        return turnirRepozitorij.countByUstvarilIdAndUstvarjenObGreaterThanEqual(
                idUporabnik, Sezona.zacetek(danes).atStartOfDay());
    }

    public long steviloLigVSezoni(Long idUporabnik, LocalDate danes) {
        return ligaRepozitorij.countByUstvarilIdAndUstvarjenObGreaterThanEqualAndKvalifikacijeVisjaIsNull(
                idUporabnik, Sezona.zacetek(danes).atStartOfDay());
    }

    /* Meje organizatorskega paketa; prazno za vsak drug paket. */
    public Optional<Omejitev> omejitev(Paket paket) {
        return switch (paket) {
            case ORGANIZATOR_BASIC -> Optional.of(BASIC);
            case ORGANIZATOR_PLUS -> Optional.of(PLUS);
            case ORGANIZATOR_PRO -> Optional.of(PRO);
            default -> Optional.empty();
        };
    }

    private Omejitev omejitevAliZavrni(Uporabnik organizator) {
        Paket paket = paketOrganizatorja(organizator).orElseThrow(() -> new PrepovedanoIzjema(
                "Racun nima aktivnega organizatorskega paketa."));
        return omejitev(paket).orElseThrow(() -> new PrepovedanoIzjema(
                "Racun nima aktivnega organizatorskega paketa."));
    }
}
