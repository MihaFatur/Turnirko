/* Zivljenjski cikel turnirjev in dogodkov ter prijave igralcev.
   Kljucno nacelo: statuse vedno doloca streznik; odjemalec le sprozi
   prehode (zacni, zakljuci), pravila pa se preverijo tukaj. */
package si.turnirko.storitve;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.DogodekVnos;
import si.turnirko.dto.TurnirVnos;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.SistemTekmovanja;
import si.turnirko.modeli.Spol;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.StatusTekmovanja;
import si.turnirko.modeli.Turnir;
import si.turnirko.repozitoriji.DogodekRepozitorij;
import si.turnirko.repozitoriji.IgralecRepozitorij;
import si.turnirko.repozitoriji.KrajRepozitorij;
import si.turnirko.repozitoriji.PrijavaRepozitorij;
import si.turnirko.repozitoriji.TurnirRepozitorij;

@Service
public class TurnirjiStoritev {

    private final TurnirRepozitorij turnirRepozitorij;
    private final DogodekRepozitorij dogodekRepozitorij;
    private final KrajRepozitorij krajRepozitorij;
    private final IgralecRepozitorij igralecRepozitorij;
    private final PrijavaRepozitorij prijavaRepozitorij;
    private final LastnistvoStoritev lastnistvo;

    public TurnirjiStoritev(TurnirRepozitorij turnirRepozitorij,
                            DogodekRepozitorij dogodekRepozitorij,
                            KrajRepozitorij krajRepozitorij,
                            IgralecRepozitorij igralecRepozitorij,
                            PrijavaRepozitorij prijavaRepozitorij,
                            LastnistvoStoritev lastnistvo) {
        this.turnirRepozitorij = turnirRepozitorij;
        this.dogodekRepozitorij = dogodekRepozitorij;
        this.krajRepozitorij = krajRepozitorij;
        this.igralecRepozitorij = igralecRepozitorij;
        this.prijavaRepozitorij = prijavaRepozitorij;
        this.lastnistvo = lastnistvo;
    }

    @Transactional
    public Turnir ustvari(TurnirVnos vnos) {
        Turnir turnir = new Turnir();
        turnir.setIme(vnos.ime().trim());
        if (vnos.postnaSt() != null) {
            turnir.setKraj(krajRepozitorij.findById(vnos.postnaSt())
                    .orElseThrow(() -> new NeveljavenVnosIzjema(
                            "Kraj s postno stevilko " + vnos.postnaSt() + " ne obstaja.")));
        }
        turnir.setDvorana(vnos.dvorana());
        turnir.setDatumZacetka(vnos.datumZacetka());
        turnir.setDatumKonca(vnos.datumKonca());
        turnir.setOpombe(vnos.opombe());
        // zabelezi lastnika (organizator oz. admin, ki ga ustvarja)
        lastnistvo.oznaciLastnika(turnir);
        // status vedno doloci streznik (PRIPRAVA je privzeti)
        return turnirRepozitorij.save(turnir);
    }

    @Transactional
    public Dogodek dodajDogodek(Long idTurnirja, DogodekVnos vnos) {
        lastnistvo.preveriTurnirPoId(idTurnirja);
        Turnir turnir = najdiTurnir(idTurnirja);
        if (turnir.getStatus() == StatusTekmovanja.ZAKLJUCEN) {
            throw new DomenskaIzjema("Na zakljucen turnir ni mogoce dodajati dogodkov.");
        }
        int steviloNizov = vnos.privzetoSteviloNizov();
        if (steviloNizov != 3 && steviloNizov != 5 && steviloNizov != 7) {
            throw new NeveljavenVnosIzjema("Stevilo nizov mora biti 3, 5 ali 7.");
        }

        Dogodek dogodek = new Dogodek();
        dogodek.setTurnir(turnir);
        dogodek.setIme(vnos.ime().trim());
        dogodek.setSpolKategorija(vnos.spolKategorija());
        dogodek.setStarostnaKategorija(vnos.starostnaKategorija());
        dogodek.setPrivzetoSteviloNizov(steviloNizov);
        dogodek.setPrijavnina(vnos.prijavnina());
        dogodek.setRokPrijave(vnos.rokPrijave());
        // sistem doloci organizator; null pomeni privzeto (IZLOCILNI)
        if (vnos.sistemTekmovanja() != null) {
            dogodek.setSistemTekmovanja(vnos.sistemTekmovanja());
        }
        nastaviSkupinskeNastavitve(dogodek, vnos);
        return dogodekRepozitorij.save(dogodek);
    }

    /* Format TOP potrebuje stevilo skupin in velikost skupine - njun zmnozek
       pove, koliko najboljsih prijavljenih sploh igra. Pri drugih sistemih
       nastavitvi namenoma ostaneta prazni, da ne zavajata. */
    private void nastaviSkupinskeNastavitve(Dogodek dogodek, DogodekVnos vnos) {
        if (dogodek.getSistemTekmovanja() != SistemTekmovanja.SKUPINE) {
            return;
        }
        Integer steviloSkupin = vnos.steviloSkupin();
        Integer velikostSkupine = vnos.velikostSkupine();
        if (steviloSkupin == null || velikostSkupine == null) {
            throw new NeveljavenVnosIzjema(
                    "Pri skupinskem sistemu sta stevilo skupin in velikost skupine obvezna.");
        }
        // zgornja meja skupin je posledica oznak A..Z
        if (steviloSkupin < 1 || steviloSkupin > 26) {
            throw new NeveljavenVnosIzjema("Stevilo skupin mora biti med 1 in 26.");
        }
        if (velikostSkupine < 2 || velikostSkupine > 24) {
            throw new NeveljavenVnosIzjema("Velikost skupine mora biti med 2 in 24.");
        }
        dogodek.setSteviloSkupin(steviloSkupin);
        dogodek.setVelikostSkupine(velikostSkupine);
    }

    @Transactional
    public Turnir zakljuci(Long idTurnirja) {
        lastnistvo.preveriTurnirPoId(idTurnirja);
        Turnir turnir = najdiTurnir(idTurnirja);
        if (turnir.getStatus() != StatusTekmovanja.V_TEKU) {
            throw new DomenskaIzjema("Zakljuciti je mogoce samo turnir, ki je v teku.");
        }
        boolean vsiZakljuceni = dogodekRepozitorij.findByTurnirIdOrderByImeAsc(idTurnirja).stream()
                .allMatch(dogodek -> dogodek.getStatus() == StatusTekmovanja.ZAKLJUCEN);
        if (!vsiZakljuceni) {
            throw new DomenskaIzjema("Vsi dogodki turnirja se niso zakljuceni.");
        }
        turnir.setStatus(StatusTekmovanja.ZAKLJUCEN);
        return turnir;
    }

    /* Prijavi igralce na dogodek - vse ali nic (transakcija). */
    @Transactional
    public List<Prijava> prijaviIgralce(Long idDogodka, List<Long> idjiIgralcev) {
        lastnistvo.preveriTurnirPoDogodku(idDogodka);
        Dogodek dogodek = dogodekRepozitorij.najdiSTurnirjem(idDogodka)
                .orElseThrow(() -> new NiNajdenoIzjema("Dogodek z id " + idDogodka + " ne obstaja."));
        if (dogodek.getStatus() != StatusTekmovanja.PRIPRAVA) {
            throw new DomenskaIzjema("Prijave so mozne samo, dokler je dogodek v pripravi.");
        }

        List<Prijava> nove = new ArrayList<>();
        for (Long idIgralca : idjiIgralcev) {
            Igralec igralec = igralecRepozitorij.najdiZVsem(idIgralca)
                    .orElseThrow(() -> new NiNajdenoIzjema("Igralec z id " + idIgralca + " ne obstaja."));
            if (igralec.isArhiviran()) {
                throw new DomenskaIzjema("Igralec " + igralec.polnoIme() + " je arhiviran.");
            }
            preveriSpolZaKategorijo(igralec, dogodek.getSpolKategorija());
            Prijava obstojeca = prijavaRepozitorij.najdiZaDogodekInIgralca(idDogodka, idIgralca)
                    .orElse(null);
            if (obstojeca != null) {
                // Odjava je le mehak izbris (status ODJAVLJEN); ponovna prijava
                // zato obstojeci zapis vrne v igro, ne ustvari novega (UNIQUE
                // dogodek+igralec). Klub posnamemo znova - med odjavo in vrnitvijo
                // je igralec lahko prestopil. Drugi statusi so dejanske prijave.
                if (obstojeca.getStatus() != Prijava.StatusPrijave.ODJAVLJEN) {
                    throw new DomenskaIzjema("Igralec " + igralec.polnoIme() + " je ze prijavljen.");
                }
                obstojeca.setStatus(Prijava.StatusPrijave.PRIJAVLJEN);
                obstojeca.setKlubObPrijavi(igralec.getKlub());
                nove.add(obstojeca);
            } else {
                nove.add(prijavaRepozitorij.save(new Prijava(dogodek, igralec)));
            }
        }
        return nove;
    }

    /* Odjava je mozna samo pred zrebom; po zrebu bi pomenila predajo tekem. */
    @Transactional
    public Prijava odjavi(Long idPrijave) {
        lastnistvo.preveriTurnirPoPrijavi(idPrijave);
        // najdiZIgralcem: kontroler po koncu transakcije bere igralca in klub
        Prijava prijava = prijavaRepozitorij.najdiZIgralcem(idPrijave)
                .orElseThrow(() -> new NiNajdenoIzjema("Prijava z id " + idPrijave + " ne obstaja."));
        if (prijava.getDogodek().getStatus() != StatusTekmovanja.PRIPRAVA) {
            throw new DomenskaIzjema(
                    "Po zrebu odjava ni vec mozna - uporabi izid BREZ_BOJA na tekmah.");
        }
        prijava.setStatus(Prijava.StatusPrijave.ODJAVLJEN);
        return prijava;
    }

    /* Na dogodek za moske smejo samo moski, za zenske samo zenske. */
    private void preveriSpolZaKategorijo(Igralec igralec, SpolKategorija kategorija) {
        boolean ustreza = switch (kategorija) {
            case MOSKI -> igralec.getSpol() == Spol.MOSKI;
            case ZENSKE -> igralec.getSpol() == Spol.ZENSKI;
            case MESANO -> true;
        };
        if (!ustreza) {
            throw new DomenskaIzjema("Igralec " + igralec.polnoIme()
                    + " po spolu ne ustreza kategoriji " + kategorija + ".");
        }
    }

    private Turnir najdiTurnir(Long id) {
        // s krajem, ker gre entiteta po koncu transakcije naravnost v DTO
        return turnirRepozitorij.najdiSKrajem(id)
                .orElseThrow(() -> new NiNajdenoIzjema("Turnir z id " + id + " ne obstaja."));
    }
}
