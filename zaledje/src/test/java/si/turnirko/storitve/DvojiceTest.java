/* Dvojice: par kot tekmovalna enota, sestavljanje parov in izlocilna mreza.

   Nosilna domenska pravila, ki jih ta test varuje:
   - par je ENA prijava (drugi igralec je v istem zapisu), zato tece cez mrezo
     ista koda kot pri posamicnem tekmovanju,
   - igralci se prijavijo posamicno, pare pa pred zrebom sestavi organizator;
     dokler kdo ostane brez soigralca, zreba ni,
   - isti igralec ne sme nastopati v dveh parih,
   - kategorija MESANO zahteva strogo mesan par, KDORKOLI ne omejuje nicesar,
   - tekme dvojic se NE obracunajo v klubski ELO. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import si.turnirko.dto.DogodekVnos;
import si.turnirko.dto.VnosRezultata;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.modeli.Disciplina;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.IzidTekme;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.SistemTekmovanja;
import si.turnirko.modeli.Spol;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.Tekma;
import si.turnirko.modeli.Turnir;

class DvojiceTest extends IntegracijskiTest {

    // ---------------------------------------------------------------
    // Sestavljanje parov
    // ---------------------------------------------------------------

    /* Povezava dveh prijav pusti ENO vrstico z obema igralcema - druga
       izgine, sicer bi njen igralec veljal za se enega tekmovalca. */
    @Test
    void povezavaVParZdruziDveVrsticiVEno() {
        Dogodek dogodek = pripraviDogodekDvojic(2);
        List<Prijava> prijave = prijavePoVrsti(dogodek.getId());

        Prijava par = turnirjiStoritev.poveziVPar(dogodek.getId(),
                prijave.get(0).getId(), prijave.get(1).getId());

        assertTrue(par.jePar());
        assertEquals(prijave.get(0).getIgralec().getId(), par.getIgralec().getId());
        assertEquals(prijave.get(1).getIgralec().getId(), par.getIgralec2().getId());
        assertEquals(1, prijavaRepozitorij.najdiZaDogodek(dogodek.getId()).size());
    }

    /* Razdruzitev vrne soigralcu njegovo lastno prijavo. */
    @Test
    void razdruzitevVrneDveSamostojniPrijavi() {
        Dogodek dogodek = pripraviDogodekDvojic(2);
        List<Prijava> prijave = prijavePoVrsti(dogodek.getId());
        Long idDrugega = prijave.get(1).getIgralec().getId();
        Prijava par = turnirjiStoritev.poveziVPar(dogodek.getId(),
                prijave.get(0).getId(), prijave.get(1).getId());

        List<Prijava> razdruzeni = turnirjiStoritev.razdruziPar(par.getId());

        assertEquals(2, razdruzeni.size());
        assertFalse(razdruzeni.get(0).jePar());
        assertEquals(idDrugega, razdruzeni.get(1).getIgralec().getId());
        assertEquals(2, prijavaRepozitorij.najdiZaDogodek(dogodek.getId()).size());
    }

    /* Soigralec para nima svoje vrstice, zato ga UNIQUE (dogodek, igralec) ne
       ustavi - da ne bi nastopal dvakrat, ga zavrne storitev. */
    @Test
    void soigralcaNiMogocePrijavitiSeEnkrat() {
        Dogodek dogodek = pripraviDogodekDvojic(2);
        List<Prijava> prijave = prijavePoVrsti(dogodek.getId());
        Long idSoigralca = prijave.get(1).getIgralec().getId();
        turnirjiStoritev.poveziVPar(dogodek.getId(),
                prijave.get(0).getId(), prijave.get(1).getId());

        assertThrows(DomenskaIzjema.class, () ->
                turnirjiStoritev.prijaviIgralce(dogodek.getId(), List.of(idSoigralca)));
    }

    /* Ze sestavljenega para ni mogoce vkljuciti v drug par. */
    @Test
    void igralecVParuNeMoreVDrugPar() {
        Dogodek dogodek = pripraviDogodekDvojic(3);
        List<Prijava> prijave = prijavePoVrsti(dogodek.getId());
        Prijava par = turnirjiStoritev.poveziVPar(dogodek.getId(),
                prijave.get(0).getId(), prijave.get(1).getId());

        assertThrows(DomenskaIzjema.class, () ->
                turnirjiStoritev.poveziVPar(dogodek.getId(),
                        par.getId(), prijave.get(2).getId()));
    }

    /* Odjava para bi odjavila dva igralca hkrati - najprej ga je treba
       razdruziti. */
    @Test
    void odjavaParaJeZavrnjena() {
        Dogodek dogodek = pripraviDogodekDvojic(2);
        List<Prijava> prijave = prijavePoVrsti(dogodek.getId());
        Prijava par = turnirjiStoritev.poveziVPar(dogodek.getId(),
                prijave.get(0).getId(), prijave.get(1).getId());

        assertThrows(DomenskaIzjema.class, () -> turnirjiStoritev.odjavi(par.getId()));
    }

    // ---------------------------------------------------------------
    // Spolne kategorije
    // ---------------------------------------------------------------

    /* MESANO je pravilo o SESTAVI para: dva moska nista mesan par. */
    @Test
    void mesanaKategorijaZahtevaMoskegaInZensko() {
        Dogodek dogodek = pripraviDogodekDvojic(0);
        dogodek.setSpolKategorija(SpolKategorija.MESANO);
        dogodekRepozitorij.save(dogodek);

        Igralec moski1 = noviIgralec("Ana", "Moski1", Spol.MOSKI);
        Igralec moski2 = noviIgralec("Bor", "Moski2", Spol.MOSKI);
        Igralec zenska = noviIgralec("Cvet", "Zenska", Spol.ZENSKI);
        List<Prijava> prijave = turnirjiStoritev.prijaviIgralce(dogodek.getId(),
                List.of(moski1.getId(), moski2.getId(), zenska.getId()));

        assertThrows(DomenskaIzjema.class, () ->
                turnirjiStoritev.poveziVPar(dogodek.getId(),
                        prijave.get(0).getId(), prijave.get(1).getId()));

        Prijava par = turnirjiStoritev.poveziVPar(dogodek.getId(),
                prijave.get(0).getId(), prijave.get(2).getId());
        assertTrue(par.jePar());
    }

    /* KDORKOLI ne omejuje nicesar - par dveh moskih je veljaven. */
    @Test
    void kategorijaKdorkoliDovoliPoljubenPar() {
        Dogodek dogodek = pripraviDogodekDvojic(0);
        Igralec moski = noviIgralec("Ana", "Moski", Spol.MOSKI);
        Igralec zenska = noviIgralec("Bea", "Zenska", Spol.ZENSKI);
        List<Prijava> prijave = turnirjiStoritev.prijaviIgralce(dogodek.getId(),
                List.of(moski.getId(), zenska.getId()));

        Prijava par = turnirjiStoritev.poveziVPar(dogodek.getId(),
                prijave.get(0).getId(), prijave.get(1).getId());

        assertTrue(par.jePar());
    }

    /* "Strogo mesano" pri posamicnem tekmovanju ne pomeni nicesar - tam je
       odprta kategorija KDORKOLI. */
    @Test
    void mesanaKategorijaPriPosamicnemNiMogoca() {
        Turnir turnir = turnirRepozitorij.save(novTurnir());
        assertThrows(NeveljavenVnosIzjema.class, () ->
                turnirjiStoritev.dodajDogodek(turnir.getId(), new DogodekVnos(
                        "Clani", SpolKategorija.MESANO, null, 5, null, null,
                        Disciplina.POSAMICNO, SistemTekmovanja.IZLOCILNI, null, null)));
    }

    /* Dvojice igrajo izkljucno izlocilno mrezo. */
    @Test
    void dvojiceZahtevajoIzlocilniSistem() {
        Turnir turnir = turnirRepozitorij.save(novTurnir());
        assertThrows(NeveljavenVnosIzjema.class, () ->
                turnirjiStoritev.dodajDogodek(turnir.getId(), new DogodekVnos(
                        "Clani dvojice", SpolKategorija.KDORKOLI, null, 5, null, null,
                        Disciplina.DVOJICE, SistemTekmovanja.KROZNI, null, null)));
    }

    // ---------------------------------------------------------------
    // Zreb in mreza
    // ---------------------------------------------------------------

    /* Igralca brez soigralca zreb ne sme tiho izpustiti - v mrezi bi ga
       organizator zaman iskal. */
    @Test
    void zrebZavrnePrijavoBrezSoigralca() {
        Dogodek dogodek = pripraviDogodekDvojic(5);
        List<Prijava> prijave = prijavePoVrsti(dogodek.getId());
        turnirjiStoritev.poveziVPar(dogodek.getId(),
                prijave.get(0).getId(), prijave.get(1).getId());
        turnirjiStoritev.poveziVPar(dogodek.getId(),
                prijave.get(2).getId(), prijave.get(3).getId());

        assertThrows(DomenskaIzjema.class, () -> zrebStoritev.izvediZreb(dogodek.getId()));
    }

    /* Zreb osmih igralcev da mrezo STIRIH parov: 2 tekmi prvega kola,
       skupaj 3 tekme - torej isto kot pri stirih posameznikih. */
    @Test
    void zrebDvojicNaredIMrezoParov() {
        Dogodek dogodek = pripraviDogodekDvojic(8);
        poveziVsePare(dogodek.getId());

        List<Tekma> tekme = zrebStoritev.izvediZreb(dogodek.getId());

        assertEquals(3, tekme.size());
        assertEquals(2, tekme.stream().filter(t -> t.getKolo() == 1).count());
        // v mrezi so pari, ne posamezniki
        Tekma prva = tekme.stream().filter(t -> t.getKolo() == 1).findFirst().orElseThrow();
        assertTrue(prva.getPrijava1().jePar());
        assertTrue(prva.getPrijava2().jePar());
    }

    /* Zreb zabelezi posnetek ratinga OBEMA clanoma para. */
    @Test
    void zrebZabeleziRatingObemaClanomaPara() {
        Dogodek dogodek = pripraviDogodekDvojic(4);
        nastaviRatinge(dogodek, 1500, 1400, 1300, 1200);
        poveziVsePare(dogodek.getId());

        zrebStoritev.izvediZreb(dogodek.getId());

        Prijava par = prijavePoVrsti(dogodek.getId()).get(0);
        assertNotNull(par.getRatingObZrebu());
        assertNotNull(par.getRatingObZrebu2());
    }

    /* Prosti prehod deluje enako kot pri posamicnem: trije pari, en dobi bye. */
    @Test
    void trijeParDobijoProstiPrehod() {
        Dogodek dogodek = pripraviDogodekDvojic(6);
        poveziVsePare(dogodek.getId());

        List<Tekma> tekme = zrebStoritev.izvediZreb(dogodek.getId());

        long prosti = tekme.stream().filter(t -> t.getIzidTip() == IzidTekme.PROSTO).count();
        assertEquals(1, prosti);
    }

    // ---------------------------------------------------------------
    // Rezultati in ELO
    // ---------------------------------------------------------------

    /* Izida para ni mogoce pripisati posamezniku, zato se rating NE obracuna -
       isto pravilo kot pri ligaskih dvojicah. */
    @Test
    void tekmaDvojicNeStejeVElo() {
        Dogodek dogodek = pripraviDogodekDvojic(4);
        nastaviRatinge(dogodek, 1500, 1400, 1300, 1200);
        poveziVsePare(dogodek.getId());
        zrebStoritev.izvediZreb(dogodek.getId());

        Tekma prva = tekmeDogodka(dogodek.getId()).stream()
                .filter(t -> t.getStatus() == StatusTekme.PRIPRAVLJENA)
                .findFirst().orElseThrow();
        tekmaStoritev.vnesiRezultat(prva.getId(), new VnosRezultata(null, 3, 1, null, null));

        assertTrue(ratingZgodovinaRepozitorij.findAll().stream()
                .noneMatch(z -> z.getTekma() != null && z.getTekma().getId().equals(prva.getId())));
    }

    /* Ker rating ne tece, tekma dvojic tudi ne sme priti med posamicne tekme
       igralca (profil, "1 na 1", lestvica). */
    @Test
    void tekmaDvojicNiMedPosamicnimiTekmamiIgralca() {
        Dogodek dogodek = pripraviDogodekDvojic(4);
        poveziVsePare(dogodek.getId());
        zrebStoritev.izvediZreb(dogodek.getId());
        Tekma prva = tekmeDogodka(dogodek.getId()).stream()
                .filter(t -> t.getStatus() == StatusTekme.PRIPRAVLJENA)
                .findFirst().orElseThrow();
        tekmaStoritev.vnesiRezultat(prva.getId(), new VnosRezultata(null, 3, 0, null, null));

        Long idIgralca = prva.getPrijava1().getIgralec().getId();
        assertTrue(tekmaRepozitorij.najdiZaIgralca(idIgralca).isEmpty());
        // v svojem seznamu pa mora biti - tudi za soigralca, ki je v drugem stolpcu
        assertEquals(1, tekmaRepozitorij.najdiDvojiceZaIgralca(idIgralca).size());
        assertEquals(1, tekmaRepozitorij.najdiDvojiceZaIgralca(
                prva.getPrijava1().getIgralec2().getId()).size());
    }

    /* Ob koncu dogodka dobi koncno mesto PAR (ena vrstica), ne posameznik. */
    @Test
    void zmagovalecDogodkaJePar() {
        Dogodek dogodek = pripraviDogodekDvojic(4);
        poveziVsePare(dogodek.getId());
        zrebStoritev.izvediZreb(dogodek.getId());

        for (Tekma tekma : tekmeDogodka(dogodek.getId())) {
            Tekma sveza = tekmaRepozitorij.findById(tekma.getId()).orElseThrow();
            if (sveza.getStatus() == StatusTekme.PRIPRAVLJENA) {
                tekmaStoritev.vnesiRezultat(sveza.getId(), new VnosRezultata(null, 3, 0, null, null));
            }
        }

        Prijava zmagovalec = prijavaRepozitorij.najdiZaDogodek(dogodek.getId()).stream()
                .filter(p -> Integer.valueOf(1).equals(p.getKoncnoMesto()))
                .findFirst().orElseThrow();
        assertTrue(zmagovalec.jePar());
        // drugo mesto dobi porazenec finala - tudi ta je par, ne posameznik
        assertEquals(1, prijavaRepozitorij.najdiZaDogodek(dogodek.getId()).stream()
                .filter(p -> Integer.valueOf(2).equals(p.getKoncnoMesto()))
                .filter(Prijava::jePar)
                .count());
    }

    private Turnir novTurnir() {
        Turnir turnir = new Turnir();
        turnir.setIme("Turnir za preverbo vnosa");
        return turnir;
    }
}
