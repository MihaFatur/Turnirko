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
        // null = privzeto (tekme stejejo v ELO)
        turnir.setStejeVElo(vnos.stejeVElo() == null || vnos.stejeVElo());
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
        // disciplina in sistem dolocita organizator; null pomeni privzeto
        // (POSAMICNO oz. IZLOCILNI)
        if (vnos.disciplina() != null) {
            dogodek.setDisciplina(vnos.disciplina());
        }
        if (vnos.sistemTekmovanja() != null) {
            dogodek.setSistemTekmovanja(vnos.sistemTekmovanja());
        }
        preveriDisciplino(dogodek);
        nastaviSkupinskeNastavitve(dogodek, vnos);
        return dogodekRepozitorij.save(dogodek);
    }

    /* Dvojice igrajo izkljucno izlocilno mrezo, "strogo mesano" pa je lastnost
       para in pri posamicnem dogodku ne pomeni nicesar - tam je odprta
       kategorija KDORKOLI. Isti dve pravili varuje CHECK v migraciji V14;
       tu sta zato, da organizator dobi razumljivo sporocilo namesto napake
       podatkovne baze. */
    private void preveriDisciplino(Dogodek dogodek) {
        if (dogodek.jeDvojice()
                && dogodek.getSistemTekmovanja() != SistemTekmovanja.IZLOCILNI) {
            throw new NeveljavenVnosIzjema(
                    "Dvojice se igrajo samo po izlocilnem sistemu (takojsnje izpadanje).");
        }
        if (dogodek.getSpolKategorija() == SpolKategorija.MESANO && !dogodek.jeDvojice()) {
            throw new NeveljavenVnosIzjema("Kategorija MESANO pomeni strogo mesan par in je"
                    + " mogoca samo pri dvojicah. Za odprt dogodek izberi KDORKOLI.");
        }
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
        boolean vsiZakljuceni = dogodekRepozitorij.findByTurnirIdOrderByIdAsc(idTurnirja).stream()
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
            // Pri dvojicah je igralec lahko ze soigralec tujega para. Takrat
            // ga UNIQUE (dogodek, igralec) ne ustavi - njegova lastna vrstica
            // je ob povezavi para izginila -, zato ga poiscemo se v drugem
            // slotu; sicer bi na dogodku nastopal dvakrat.
            prijavaRepozitorij.najdiPoSoigralcu(idDogodka, idIgralca).ifPresent(par -> {
                throw new DomenskaIzjema("Igralec " + igralec.polnoIme()
                        + " je ze prijavljen v paru " + par.prikazanoIme() + ".");
            });
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
        // Odjava sestavljenega para bi tiho odjavila DVA igralca, od katerih
        // eden morda le isce novega soigralca. Razdruzitev je zato loceno
        // dejanje, ki ga mora organizator opraviti zavestno.
        if (prijava.jePar()) {
            throw new DomenskaIzjema("Prijava je par (" + prijava.prikazanoIme()
                    + "). Najprej ga razdruzi, nato odjavi posameznika.");
        }
        prijava.setStatus(Prijava.StatusPrijave.ODJAVLJEN);
        return prijava;
    }

    // ---------------------------------------------------------------------
    // Dvojice: sestavljanje in razdruzevanje parov
    // ---------------------------------------------------------------------

    /* Poveze dve prijavi istega dogodka v par: prva vrstica dobi soigralca,
       druga izgine. Vrstici sta pred zrebom brez sledi (nanju se ne sklicuje
       nobena tekma), zato je izbris pravi izbris in ne mehak - ostanek bi
       pomenil "prijavljen igralec", kar drugi igralec para ni.

       Vrstni red igralcev v paru je vrstni red prijave: kdor je prijavo
       oddal prej, je v paru prvi. */
    @Transactional
    public Prijava poveziVPar(Long idDogodka, Long idPrijave1, Long idPrijave2) {
        lastnistvo.preveriTurnirPoDogodku(idDogodka);
        Dogodek dogodek = dogodekRepozitorij.najdiSTurnirjem(idDogodka)
                .orElseThrow(() -> new NiNajdenoIzjema("Dogodek z id " + idDogodka + " ne obstaja."));
        if (!dogodek.jeDvojice()) {
            throw new DomenskaIzjema("Pare je mogoce sestavljati samo na dogodku dvojic.");
        }
        if (dogodek.getStatus() != StatusTekmovanja.PRIPRAVA) {
            throw new DomenskaIzjema("Pare je mogoce sestavljati samo, dokler je dogodek v pripravi.");
        }
        if (idPrijave1.equals(idPrijave2)) {
            throw new NeveljavenVnosIzjema("Par sestavljata dva razlicna igralca.");
        }

        Prijava prva = najdiZaPar(idDogodka, idPrijave1);
        Prijava druga = najdiZaPar(idDogodka, idPrijave2);
        preveriMesanPar(dogodek, prva.getIgralec(), druga.getIgralec());

        // prijavo z nizjim id-jem obdrzimo kot nosilca para (prej oddana
        // prijava), da je vrstni red imen ponovljiv
        Prijava nosilec = prva.getId() < druga.getId() ? prva : druga;
        Prijava vkljucena = nosilec == prva ? druga : prva;

        nosilec.nastaviSoigralca(vkljucena.getIgralec());
        // par je placan, ko sta placala oba - prijavnina je vezana na igralca
        nosilec.setPlacano(nosilec.isPlacano() && vkljucena.isPlacano());
        prijavaRepozitorij.delete(vkljucena);
        // izbris mora v bazo pred naslednjim branjem, sicer delni edinstveni
        // indeks (dogodek, igralec_2) trci ob se obstojeco vrstico
        prijavaRepozitorij.flush();
        return nosilec;
    }

    /* Razdruzi par nazaj v dve samostojni prijavi. Soigralec dobi novo
       vrstico - njegova stara je ob povezavi izginila. */
    @Transactional
    public List<Prijava> razdruziPar(Long idPrijave) {
        lastnistvo.preveriTurnirPoPrijavi(idPrijave);
        Prijava par = prijavaRepozitorij.najdiZIgralcem(idPrijave)
                .orElseThrow(() -> new NiNajdenoIzjema("Prijava z id " + idPrijave + " ne obstaja."));
        if (par.getDogodek().getStatus() != StatusTekmovanja.PRIPRAVA) {
            throw new DomenskaIzjema("Par je mogoce razdruziti samo, dokler je dogodek v pripravi.");
        }
        if (!par.jePar()) {
            throw new DomenskaIzjema("Ta prijava ni par.");
        }

        Igralec soigralec = par.getIgralec2();
        par.nastaviSoigralca(null);
        par.setStNosilca(null);
        // novi vrstici damo isti status kot paru: razdruzitev ne sme
        // odjavljenega igralca vrniti med prijavljene
        Prijava samostojna = new Prijava(par.getDogodek(), soigralec);
        samostojna.setStatus(par.getStatus());
        samostojna.setPlacano(par.isPlacano());
        prijavaRepozitorij.save(samostojna);
        return List.of(par, samostojna);
    }

    /* Prijava, ki jo je mogoce povezati v par: pravi dogodek, prijavljena
       in ne ze v paru. */
    private Prijava najdiZaPar(Long idDogodka, Long idPrijave) {
        Prijava prijava = prijavaRepozitorij.najdiZIgralcem(idPrijave)
                .orElseThrow(() -> new NiNajdenoIzjema("Prijava z id " + idPrijave + " ne obstaja."));
        if (!prijava.getDogodek().getId().equals(idDogodka)) {
            throw new NeveljavenVnosIzjema("Prijava " + idPrijave + " ni s tega dogodka.");
        }
        if (prijava.getStatus() != Prijava.StatusPrijave.PRIJAVLJEN) {
            throw new DomenskaIzjema("Igralec " + prijava.prikazanoIme()
                    + " ni prijavljen (stanje: " + prijava.getStatus() + ").");
        }
        if (prijava.jePar()) {
            throw new DomenskaIzjema(prijava.prikazanoIme() + " je ze par.");
        }
        return prijava;
    }

    /* Pri kategoriji MESANO mora par sestavljati en moski in ena zenska;
       druge kategorije so na sestavo para brezbrizne (spol posameznika je
       preveril ze vpis prijave). */
    private void preveriMesanPar(Dogodek dogodek, Igralec prvi, Igralec drugi) {
        if (dogodek.getSpolKategorija() != SpolKategorija.MESANO) {
            return;
        }
        if (prvi.getSpol() == drugi.getSpol()) {
            throw new DomenskaIzjema("Kategorija je mesane dvojice:"
                    + " par mora sestavljati en moski in ena zenska.");
        }
    }

    /* Na dogodek za moske smejo samo moski, za zenske samo zenske.
       MESANO je pravilo o SESTAVI PARA, ne o posamezniku - preveri ga
       preveriMesanPar ob povezavi -, KDORKOLI pa ne omejuje nicesar. */
    private void preveriSpolZaKategorijo(Igralec igralec, SpolKategorija kategorija) {
        boolean ustreza = switch (kategorija) {
            case MOSKI -> igralec.getSpol() == Spol.MOSKI;
            case ZENSKE -> igralec.getSpol() == Spol.ZENSKI;
            case MESANO, KDORKOLI -> true;
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
