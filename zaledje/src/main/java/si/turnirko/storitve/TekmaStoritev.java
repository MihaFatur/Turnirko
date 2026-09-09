/* Vnos rezultata tekme - avtomat stanj z jasnimi pravili:
   - rezultat je mogoce vnesti samo na tekmi v stanju PRIPRAVLJENA ali V_IGRI
     in samo, dokler je dogodek V_TEKU,
   - vnos na ze KONCANO tekmo je zavrnjen (naknadni popravki z razveljavitvijo
     ratinga in napredovanja so predvideni kot posebna operacija v nacrtu),
   - ob koncu tekme se (enkrat!) obracuna rating, izvede napredovanje po mrezi
     in po zadnji tekmi zakljuci dogodek ter dodeli koncni mesti. */
package si.turnirko.storitve;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.NizVnos;
import si.turnirko.dto.VnosRezultata;
import si.turnirko.dto.VrsticaLestviceDto;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.FazaTekme;
import si.turnirko.modeli.IzidTekme;
import si.turnirko.modeli.Niz;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.SistemTekmovanja;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.StatusTekmovanja;
import si.turnirko.modeli.Tekma;
import si.turnirko.repozitoriji.DogodekRepozitorij;
import si.turnirko.repozitoriji.NizRepozitorij;
import si.turnirko.repozitoriji.PrijavaRepozitorij;
import si.turnirko.repozitoriji.TekmaRepozitorij;

@Service
public class TekmaStoritev {

    private final TekmaRepozitorij tekmaRepozitorij;
    private final NizRepozitorij nizRepozitorij;
    private final DogodekRepozitorij dogodekRepozitorij;
    private final PrijavaRepozitorij prijavaRepozitorij;
    private final RatingStoritev ratingStoritev;
    private final NapredovanjeStoritev napredovanjeStoritev;
    private final SkupineStoritev skupineStoritev;
    private final RazvrstitevStoritev razvrstitevStoritev;
    private final LastnistvoStoritev lastnistvo;

    public TekmaStoritev(TekmaRepozitorij tekmaRepozitorij,
                         NizRepozitorij nizRepozitorij,
                         DogodekRepozitorij dogodekRepozitorij,
                         PrijavaRepozitorij prijavaRepozitorij,
                         RatingStoritev ratingStoritev,
                         NapredovanjeStoritev napredovanjeStoritev,
                         SkupineStoritev skupineStoritev,
                         RazvrstitevStoritev razvrstitevStoritev,
                         LastnistvoStoritev lastnistvo) {
        this.tekmaRepozitorij = tekmaRepozitorij;
        this.nizRepozitorij = nizRepozitorij;
        this.dogodekRepozitorij = dogodekRepozitorij;
        this.prijavaRepozitorij = prijavaRepozitorij;
        this.ratingStoritev = ratingStoritev;
        this.napredovanjeStoritev = napredovanjeStoritev;
        this.skupineStoritev = skupineStoritev;
        this.razvrstitevStoritev = razvrstitevStoritev;
        this.lastnistvo = lastnistvo;
    }

    /* Vnese koncni rezultat tekme in sprozi vse posledice. */
    @Transactional
    public Tekma vnesiRezultat(Long idTekme, VnosRezultata vnos) {
        lastnistvo.preveriTurnirPoTekmi(idTekme);
        Tekma tekma = tekmaRepozitorij.najdiZVsem(idTekme)
                .orElseThrow(() -> new NiNajdenoIzjema("Tekma z id " + idTekme + " ne obstaja."));

        preveriStanje(tekma);

        IzidTekme izid = vnos.izidTip() == null ? IzidTekme.IGRANO : vnos.izidTip();
        switch (izid) {
            case IGRANO -> vnesiIgrano(tekma, vnos);
            case BREZ_BOJA, DISKVALIFIKACIJA -> vnesiPosebenIzid(tekma, vnos, izid);
            case PREDAJA -> vnesiPredajo(tekma, vnos);
            case PROSTO -> throw new NeveljavenVnosIzjema(
                    "Prostega prehoda ni mogoce vnesti rocno - doloci ga zreb.");
        }

        tekma.setStatus(StatusTekme.KONCANA);
        tekmaRepozitorij.save(tekma);

        // Rating se obracuna samo za dejansko igrane tekme in le, ce turnir
        // steje v ELO (organizator to izbere ob ustvarjanju; enako kot pri
        // ligi). Dvojice ne stejejo NIKOLI - izida para ni mogoce pripisati
        // posamezniku; isto pravilo velja za ligaske dvojice.
        if ((izid == IzidTekme.IGRANO || izid == IzidTekme.PREDAJA)
                && !tekma.getDogodek().jeDvojice()
                && tekma.getDogodek().getTurnir().isStejeVElo()) {
            ratingStoritev.obracunajKlubskiElo(tekma);
        }

        // izlocilni del: zmagovalec (ali porazenec) napreduje po povezavah
        napredovanjeStoritev.razsiriIzKoncane(tekma);

        // skupinski del: dodeli mesta v koncani skupini in - ko so vse
        // skupine odigrane - zgeneriraj izlocilno mrezo. Nujno se zgodi PRED
        // preverbo zakljucka dogodka, sicer bi se dogodek predcasno zakljucil.
        Dogodek dogodek = tekma.getDogodek();
        if (dogodek.getSistemTekmovanja().imaSkupine() && tekma.getFaza() == FazaTekme.SKUPINA) {
            skupineStoritev.obKoncaniSkupinski(tekma);
        }

        zakljuciDogodekCeKoncan(dogodek.getId(), dogodek.getSistemTekmovanja());

        return tekma;
    }

    /* Odstop igralca med tekmovanjem (poskodba, odhod pred koncem).
       Ze odigrane tekme obveljajo - njihovi rezultati in rating ostanejo
       nedotaknjeni -, preostale pa dobijo nasprotniki brez boja.

       Zavestno je izbran izid BREZ_BOJA in ne PREDAJA: te tekme niso bile
       nikoli odigrane, zato se rating NE sme obracunati. Nasprotnik bi sicer
       dobil ELO tocke za tekmo, ki je ni igral, kar bi popacilo lestvico.
       Na lestvici skupine tekma normalno steje kot njegova zmaga. */
    @Transactional
    public Prijava odstopiIgralca(Long idPrijave) {
        lastnistvo.preveriTurnirPoPrijavi(idPrijave);
        Prijava prijava = prijavaRepozitorij.najdiZIgralcem(idPrijave)
                .orElseThrow(() -> new NiNajdenoIzjema("Prijava z id " + idPrijave + " ne obstaja."));
        Dogodek dogodek = prijava.getDogodek();

        if (dogodek.getStatus() != StatusTekmovanja.V_TEKU) {
            throw new DomenskaIzjema("Odstop je mogoc samo, dokler tekmovanje tece."
                    + " Pred zrebom igralca odjavi.");
        }
        if (prijava.getStatus() != Prijava.StatusPrijave.PRIJAVLJEN) {
            throw new DomenskaIzjema("Igralec ne nastopa na tem dogodku (stanje: "
                    + prijava.getStatus() + ").");
        }
        prijava.setStatus(Prijava.StatusPrijave.ODSTOPIL);
        prijavaRepozitorij.save(prijava);

        List<Tekma> brezBoja = new ArrayList<>();
        for (Tekma tekma : tekmaRepozitorij.najdiZaDogodek(dogodek.getId())) {
            if (tekma.getStatus() == StatusTekme.KONCANA) {
                continue; // ze odigrana - obvelja taka, kot je
            }
            boolean prvi = jeIstaPrijava(tekma.getPrijava1(), idPrijave);
            boolean drugi = jeIstaPrijava(tekma.getPrijava2(), idPrijave);
            if (!prvi && !drugi) {
                continue;
            }
            Prijava nasprotnik = prvi ? tekma.getPrijava2() : tekma.getPrijava1();
            if (nasprotnik == null) {
                continue; // nasprotnik se ni znan (tekma visjega kola v mrezi)
            }

            int zaZmago = tekma.nizovZaZmago();
            tekma.setDobljeniNizi1(prvi ? 0 : zaZmago);
            tekma.setDobljeniNizi2(prvi ? zaZmago : 0);
            tekma.setIzidTip(IzidTekme.BREZ_BOJA);
            tekma.setZmagovalec(nasprotnik);
            tekma.setStatus(StatusTekme.KONCANA);
            brezBoja.add(tekma);
        }
        tekmaRepozitorij.saveAll(brezBoja);

        // iste posledice kot pri rocnem vnosu - le rating izostane
        for (Tekma tekma : brezBoja) {
            napredovanjeStoritev.razsiriIzKoncane(tekma);
        }
        if (dogodek.getSistemTekmovanja().imaSkupine() && !brezBoja.isEmpty()) {
            skupineStoritev.obKoncaniSkupinski(brezBoja.get(brezBoja.size() - 1));
        }
        zakljuciDogodekCeKoncan(dogodek.getId(), dogodek.getSistemTekmovanja());

        return prijava;
    }

    private static boolean jeIstaPrijava(Prijava stran, Long idPrijave) {
        return stran != null && stran.getId().equals(idPrijave);
    }

    /* Preveri, ali je tekmo v trenutnem stanju sploh dovoljeno vnasati. */
    private void preveriStanje(Tekma tekma) {
        if (tekma.getDogodek().getStatus() != StatusTekmovanja.V_TEKU) {
            throw new DomenskaIzjema("Dogodek ni v teku - vnos rezultatov ni mogoc.");
        }
        switch (tekma.getStatus()) {
            case KONCANA -> throw new DomenskaIzjema(
                    "Tekma je ze koncana. Naknadni popravki rezultata se niso podprti.");
            case CAKA -> throw new DomenskaIzjema(
                    "Tekma se caka na igralce iz prejsnjih kol.");
            default -> { /* PRIPRAVLJENA ali V_IGRI - v redu */ }
        }
        if (tekma.getPrijava1() == null || tekma.getPrijava2() == null) {
            throw new DomenskaIzjema("Tekma se nima obeh igralcev.");
        }
    }

    /* Normalno odigrana tekma: preveri nize in po zelji tocke po nizih. */
    private void vnesiIgrano(Tekma tekma, VnosRezultata vnos) {
        Integer nizi1 = vnos.dobljeniNizi1();
        Integer nizi2 = vnos.dobljeniNizi2();
        if (nizi1 == null || nizi2 == null) {
            throw new NeveljavenVnosIzjema("Manjkata dobljena niza (dobljeniNizi1, dobljeniNizi2).");
        }
        if (nizi1 < 0 || nizi2 < 0) {
            throw new NeveljavenVnosIzjema("Stevilo dobljenih nizov ne sme biti negativno.");
        }
        if (nizi1.equals(nizi2)) {
            throw new NeveljavenVnosIzjema("Neodlocen izid ni mogoc.");
        }

        int zaZmago = tekma.nizovZaZmago();
        int zmagovalcevi = Math.max(nizi1, nizi2);
        int porazencevi = Math.min(nizi1, nizi2);
        if (zmagovalcevi != zaZmago) {
            throw new NeveljavenVnosIzjema("Zmagovalec mora dobiti natanko " + zaZmago
                    + " nizov (najboljsi od " + tekma.getSteviloNizov() + ").");
        }
        if (porazencevi >= zaZmago) {
            throw new NeveljavenVnosIzjema("Porazenec ima prevec dobljenih nizov.");
        }

        tekma.setDobljeniNizi1(nizi1);
        tekma.setDobljeniNizi2(nizi2);
        tekma.setIzidTip(IzidTekme.IGRANO);
        tekma.setZmagovalec(nizi1 > nizi2 ? tekma.getPrijava1() : tekma.getPrijava2());

        shraniTockeNizov(tekma, vnos, nizi1, nizi2);
    }

    /* Ce so vnesene tocke po nizih, jih preveri in shrani. */
    private void shraniTockeNizov(Tekma tekma, VnosRezultata vnos, int nizi1, int nizi2) {
        List<NizVnos> nizi = vnos.nizi();
        if (nizi == null || nizi.isEmpty()) {
            return; // tocke po nizih so za klubske turnirje neobvezne
        }
        NiziPravila.preveri(nizi, nizi1, nizi2, tekma.nizovZaZmago());

        int zaporedna = 1;
        for (NizVnos niz : nizi) {
            nizRepozitorij.save(new Niz(tekma, zaporedna, niz.tocke1(), niz.tocke2()));
            zaporedna++;
        }
    }

    /* Brez boja (w.o.) ali diskvalifikacija: zmagovalec dobi vse nize,
       rating se NE obracuna. */
    private void vnesiPosebenIzid(Tekma tekma, VnosRezultata vnos, IzidTekme izid) {
        Prijava zmagovalec = zahtevajZmagovalca(tekma, vnos);
        int zaZmago = tekma.nizovZaZmago();
        boolean prviZmagal = zmagovalec.getId().equals(tekma.getPrijava1().getId());
        tekma.setDobljeniNizi1(prviZmagal ? zaZmago : 0);
        tekma.setDobljeniNizi2(prviZmagal ? 0 : zaZmago);
        tekma.setIzidTip(izid);
        tekma.setZmagovalec(zmagovalec);
    }

    /* Predaja med igro: delni rezultat ostane, rating SE obracuna. */
    private void vnesiPredajo(Tekma tekma, VnosRezultata vnos) {
        Prijava zmagovalec = zahtevajZmagovalca(tekma, vnos);
        int nizi1 = vnos.dobljeniNizi1() == null ? 0 : vnos.dobljeniNizi1();
        int nizi2 = vnos.dobljeniNizi2() == null ? 0 : vnos.dobljeniNizi2();
        int zaZmago = tekma.nizovZaZmago();
        if (nizi1 < 0 || nizi2 < 0 || nizi1 >= zaZmago || nizi2 >= zaZmago) {
            throw new NeveljavenVnosIzjema(
                    "Pri predaji mora biti delni rezultat pred koncem tekme (manj kot "
                            + zaZmago + " dobljenih nizov).");
        }
        tekma.setDobljeniNizi1(nizi1);
        tekma.setDobljeniNizi2(nizi2);
        tekma.setIzidTip(IzidTekme.PREDAJA);
        tekma.setZmagovalec(zmagovalec);
    }

    private Prijava zahtevajZmagovalca(Tekma tekma, VnosRezultata vnos) {
        Integer stran = vnos.zmagovalecStran();
        if (stran == null || (stran != 1 && stran != 2)) {
            throw new NeveljavenVnosIzjema(
                    "Za ta izid je treba navesti zmagovalca (zmagovalecStran: 1 ali 2).");
        }
        return stran == 1 ? tekma.getPrijava1() : tekma.getPrijava2();
    }

    /* Ko so vse tekme dogodka koncane: dogodek zakljuci in dodeli koncna
       mesta. Pri izlocilnem in skupinskem sistemu iz finala izlocilne mreze,
       pri kroznem iz koncne lestvice.
       Dogodek se nalozi sveze iz baze, ker so atomarne posodobitve
       napredovanja lahko medtem ocistile sejo (stara referenca bi bila
       odklopljena in sprememba statusa bi se tiho izgubila). */
    private void zakljuciDogodekCeKoncan(Long idDogodka, SistemTekmovanja sistem) {
        long nedokoncanih = tekmaRepozitorij.countByDogodekIdAndStatusNot(
                idDogodka, StatusTekme.KONCANA);
        if (nedokoncanih > 0) {
            return;
        }

        Dogodek dogodek = dogodekRepozitorij.findById(idDogodka).orElseThrow();
        dogodek.setStatus(StatusTekmovanja.ZAKLJUCEN);

        switch (sistem) {
            case KROZNI -> dodeliMestaIzLestvice(idDogodka);
            // Format TOP: skupine so rangi (A je mocnejsa od B), zato skupne
            // razvrstitve 1..N cez skupine ni - vsaka skupina obdrzi svojo
            // lestvico (mesto v skupini dodeli SkupineStoritev).
            case SKUPINE -> { }
            case IZLOCILNI, SKUPINE_IZLOCILNI -> dodeliMestaIzFinala(idDogodka);
        }
    }

    /* Izlocilni in skupinski sistem: 1. in 2. mesto iz finala glavne mreze. */
    private void dodeliMestaIzFinala(Long idDogodka) {
        List<Tekma> tekme = tekmaRepozitorij.najdiZaDogodek(idDogodka);
        tekme.stream()
                .filter(t -> t.getFaza() == FazaTekme.GLAVNI && t.getPozicija() == 1)
                .max(Comparator.comparingInt(Tekma::getKolo))
                .ifPresent(finale -> {
                    if (finale.getZmagovalec() != null) {
                        finale.getZmagovalec().setKoncnoMesto(1);
                        Prijava porazenec = finale.porazenec();
                        if (porazenec != null) {
                            porazenec.setKoncnoMesto(2);
                        }
                    }
                });
    }

    /* Krozni sistem: koncna mesta so kar mesta na skupni lestvici. */
    private void dodeliMestaIzLestvice(Long idDogodka) {
        List<Prijava> igralci = prijavaRepozitorij.najdiZaDogodek(idDogodka).stream()
                .filter(p -> p.getStatus() == Prijava.StatusPrijave.PRIJAVLJEN)
                .toList();
        List<Tekma> tekme = tekmaRepozitorij.najdiZaDogodek(idDogodka);
        List<VrsticaLestviceDto> lestvica = razvrstitevStoritev.lestvica(igralci, tekme);

        Map<Long, Prijava> poId = new HashMap<>();
        for (Prijava p : igralci) {
            poId.put(p.getId(), p);
        }
        for (VrsticaLestviceDto vrstica : lestvica) {
            Prijava p = poId.get(vrstica.idPrijave());
            if (p != null) {
                p.setKoncnoMesto(vrstica.mesto());
            }
        }
    }
}
