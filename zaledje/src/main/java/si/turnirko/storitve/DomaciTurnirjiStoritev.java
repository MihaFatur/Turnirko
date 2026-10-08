/* Sklop "Turnirji" na domaci strani igralca s Premium: zbere, kar pravilo
   izbora potrebuje (IzborTurnirjev), in vrne izbrane turnirje z razlogom.

   Samo za potrjenega igralca s Premium. Vsem drugim (gost, igralec brez
   paketa, organizator) vrne prazen seznam - vmesnik takrat pokaze najnovejse
   turnirje kot doslej. Pot je GET in javna kot ostale poti domace strani,
   racun pa se prebere iz glave Authorization (isto kot /domov/lige).

   Podatki: turnirji z ravnijo in stanjem, razpisi dogodkov (spol, starostna
   kategorija), turnirji, na katere je igralec prijavljen ali je v njih
   nastopil (tudi v ekipi), turnirji njegovega kluba in koliko je odigral na
   uradnih tekmovanjih. Vse so skupinske poizvedbe, nobena ni na turnir.

   RAVEN ZA IZBOR TURNIRJEV NI ZASTAVICA LESTVICE REKREATIVCEV. Ta steje tudi
   klubska tekmovanja, zato je igralec Savinja lige B po treh tekmah
   "tekmovalec" - uradni turnir NTZS mu zato se ni primeren. Merilo je, ali je
   odigral vsaj PRAG_TEKEM tekem na uradnih tekmovanjih (NTZS); kdor ni,
   dobi rekreativne in nato klubske turnirje. */
package si.turnirko.storitve;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.DomovTurnirDto;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.RatingStanje;
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.StarostniPas;
import si.turnirko.modeli.Turnir;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.repozitoriji.DogodekRepozitorij;
import si.turnirko.repozitoriji.KaderEkipeRepozitorij;
import si.turnirko.repozitoriji.PrijavaRepozitorij;
import si.turnirko.repozitoriji.RatingZgodovinaRepozitorij;
import si.turnirko.repozitoriji.TurnirRepozitorij;

@Service
public class DomaciTurnirjiStoritev {

    /* Leto, s katerim je zapisan neznan datum rojstva. */
    private static final int NEZNANO_LETO = 1900;

    /* Starost, s katero se meri igralec z neznanim datumom (glej
       primerniRazpisi): odrasel, na meji veteranov. */
    private static final int ODRASEL = 40;

    private final TurnirRepozitorij turnirRepozitorij;
    private final DogodekRepozitorij dogodekRepozitorij;
    private final PrijavaRepozitorij prijavaRepozitorij;
    private final KaderEkipeRepozitorij kaderRepozitorij;
    private final RatingZgodovinaRepozitorij zgodovinaRepozitorij;
    private final LastnistvoStoritev lastnistvo;
    private final NarocninaStoritev narocnina;

    public DomaciTurnirjiStoritev(TurnirRepozitorij turnirRepozitorij,
                                  DogodekRepozitorij dogodekRepozitorij,
                                  PrijavaRepozitorij prijavaRepozitorij,
                                  KaderEkipeRepozitorij kaderRepozitorij,
                                  RatingZgodovinaRepozitorij zgodovinaRepozitorij,
                                  LastnistvoStoritev lastnistvo,
                                  NarocninaStoritev narocnina) {
        this.turnirRepozitorij = turnirRepozitorij;
        this.dogodekRepozitorij = dogodekRepozitorij;
        this.prijavaRepozitorij = prijavaRepozitorij;
        this.kaderRepozitorij = kaderRepozitorij;
        this.zgodovinaRepozitorij = zgodovinaRepozitorij;
        this.lastnistvo = lastnistvo;
        this.narocnina = narocnina;
    }

    @Transactional(readOnly = true)
    public List<DomovTurnirDto> zaPrijavljenega() {
        Uporabnik jaz = lastnistvo.trenutni();
        if (jaz == null || !jaz.jePotrjenIgralec() || !narocnina.imaPremium(jaz)) {
            return List.of();
        }
        Igralec igralec = jaz.getIgralec();
        Long id = igralec.getId();
        LocalDate danes = PotekLige.danes();

        Set<Long> moji = new HashSet<>(prijavaRepozitorij.turnirjiIgralca(id, Prijava.StatusPrijave.ODJAVLJEN));
        moji.addAll(kaderRepozitorij.ekipniTurnirjiIgralca(id));
        Set<Long> kolegi = igralec.getKlub() == null
                ? Set.of()
                : new HashSet<>(prijavaRepozitorij.turnirjiKluba(
                        igralec.getKlub().getId(), id, Prijava.StatusPrijave.ODJAVLJEN));
        boolean rekreativnaRaven = uradnihTekem(id) < RekreativecStoritev.PRAG_TEKEM;

        List<Turnir> turnirji = turnirRepozitorij.findAll();
        Map<Long, LocalDate> datumi = new HashMap<>();
        turnirji.forEach(t -> datumi.put(t.getId(), t.getDatumZacetka()));
        Map<Long, Boolean> primerni = primerniRazpisi(igralec, datumi, danes);

        List<IzborTurnirjev.Kandidat> kandidati = turnirji.stream()
                .map(t -> new IzborTurnirjev.Kandidat(t.getId(), t.getDatumZacetka(), t.getStatus(),
                        t.getRaven(), primerni.getOrDefault(t.getId(), true),
                        kolegi.contains(t.getId()), moji.contains(t.getId())))
                .toList();
        return IzborTurnirjev.izberi(kandidati, rekreativnaRaven, danes).stream()
                .map(i -> new DomovTurnirDto(i.id(), i.razlog()))
                .toList();
    }

    /* Koliko obracunanih tekem je igralec odigral na uradnih tekmovanjih. Ista
       poizvedba kot zastavica rekreativca (pokrivni indeks dnevnika), le z
       ravnijo URADNO. */
    private int uradnihTekem(Long idIgralec) {
        for (Object[] r : zgodovinaRepozitorij.tekmovalnihTekem(
                RatingStanje.SISTEM_TURNIRKO, List.of(RavenTekmovanja.URADNO))) {
            if (((Number) r[0]).longValue() == idIgralec) {
                return ((Number) r[1]).intValue();
            }
        }
        return 0;
    }

    /* Za vsak turnir z dogodki: ali vsaj eden dopusca igralcev spol in
       starost. Starost se meri na dan turnirja (pravilo PST), turnir brez
       datuma pa na danes. Turnirja brez dogodkov v mapi ni - razpisa se ne
       poznamo, zato velja za primernega. */
    private Map<Long, Boolean> primerniRazpisi(Igralec igralec, Map<Long, LocalDate> datumi, LocalDate danes) {
        Map<Long, Boolean> primerni = new HashMap<>();
        /* 1. 1. 1900 je zapis "datum ni znan" in ne starost 126 let. Tak zapis
           imajo skoraj izkljucno odrasli rekreativci (v Savinja ligah 86 od 154
           clanov kadra, oktober 2026) - mladinci imajo datum iz registracije
           NTZS. Zato velja za odraslega: mladinski razpis ga ne dopusca,
           veteranskega pa ne zapre (ODRASEL je meja veteranov). */
        LocalDate rojstvo = igralec.getDatumRojstva();
        boolean neznano = rojstvo != null && rojstvo.getYear() <= NEZNANO_LETO;
        for (Object[] r : dogodekRepozitorij.razpisiDogodkov()) {
            Long idTurnir = ((Number) r[0]).longValue();
            LocalDate dan = datumi.getOrDefault(idTurnir, null);
            Integer starost = neznano
                    ? ODRASEL
                    : StarostniPas.letaVSezoni(rojstvo, dan != null ? dan : danes);
            boolean dopusca = IzborTurnirjev.razpisDopusca((SpolKategorija) r[1], (String) r[2],
                    igralec.getSpol(), starost);
            primerni.merge(idTurnir, dopusca, Boolean::logicalOr);
        }
        return primerni;
    }
}
