/* Rocni vpis skupin (skupine + izlocilni, skupine za mesta, format TOP), predlog,
   popravek do prve tekme in izlocilni del, ki ga organizator izbere sam (racunalniski
   zreb ali rocna mreza). Vsi vpisi tecejo po isti poti kot zreb - razlika je samo v tem,
   kdo doloca skupine in mesta. */
package si.turnirko.storitve;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import si.turnirko.dto.IzlocilniPredlogDto;
import si.turnirko.dto.RocniSkupineVnos;
import si.turnirko.dto.SkupinePredlogDto;
import si.turnirko.dto.VnosRezultata;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.FazaTekme;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.SistemTekmovanja;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.StatusTekmovanja;
import si.turnirko.modeli.Tekma;

class RocniZrebSkupinTest extends IntegracijskiTest {

    @Autowired private IzlocilniZrebStoritev izlocilniZrebStoritev;

    /* Clan skupine: prijava na poziciji v skupini (1 = najmocnejsi v njej). */
    private static RocniSkupineVnos.Clan clan(Prijava prijava, int pozicija) {
        return new RocniSkupineVnos.Clan(prijava.getId(), pozicija);
    }

    /* Skupine za osem prijav: A = indeksi 0, 5, 6, 7 (pozicije 1-4 v A),
       B = indeksi 1, 2, 3, 4 (pozicije 1-4 v B). Skupine niso po vrsti prijav. */
    private static List<List<RocniSkupineVnos.Clan>> osemPrijavVDveSkupini(List<Prijava> p) {
        return List.of(
                List.of(clan(p.get(0), 1), clan(p.get(5), 2), clan(p.get(6), 3), clan(p.get(7), 4)),
                List.of(clan(p.get(1), 1), clan(p.get(2), 2), clan(p.get(3), 3), clan(p.get(4), 4)));
    }

    private static Prijava prijava(List<Prijava> prijave, int indeks) {
        return prijave.get(indeks);
    }

    @Test
    void organizatorJeVpisalSkupineInNosilceZrebJihNeIzbere() {
        Dogodek dogodek = pripraviDogodek(8, SistemTekmovanja.SKUPINE_IZLOCILNI);
        List<Prijava> prijave = prijavePoVrsti(dogodek.getId());

        zrebStoritev.izvediRocniZrebSkupin(dogodek.getId(), osemPrijavVDveSkupini(prijave));

        List<si.turnirko.modeli.Skupina> skupineDogodka =
                skupinaRepozitorij.findByDogodekIdOrderByOznakaAsc(dogodek.getId());
        assertEquals(List.of("A", "B"), skupineDogodka.stream().map(s -> s.getOznaka()).toList());
        for (int indeks : List.of(0, 5, 6, 7)) {
            assertEquals(skupineDogodka.get(0).getId(), osvezi(prijava(prijave, indeks)).getIdSkupina());
        }
        for (int indeks : List.of(1, 2, 3, 4)) {
            assertEquals(skupineDogodka.get(1).getId(), osvezi(prijava(prijave, indeks)).getIdSkupina());
        }
        assertEquals(3, osvezi(prijava(prijave, 5)).getStNosilca());

        List<Tekma> tekme = tekmeDogodka(dogodek.getId());
        assertEquals(12, tekme.size(), "2 skupini po 4 -> po 6 krogov tekem");
        assertTrue(tekme.stream().allMatch(t -> t.getFaza() == FazaTekme.SKUPINA));
        assertTrue(tekme.stream().allMatch(t -> t.getIdSkupina() != null
                && t.getIdSkupina().equals(t.getPrijava1().getIdSkupina())
                && t.getIdSkupina().equals(t.getPrijava2().getIdSkupina())),
                "vsaka tekma je znotraj ene skupine");
        assertEquals(StatusTekmovanja.V_TEKU,
                dogodekRepozitorij.findById(dogodek.getId()).orElseThrow().getStatus());
    }

    @Test
    void jakostnoMestoSeIzpeljeIzPozicijVSkupini() {
        Dogodek dogodek = pripraviDogodek(8, SistemTekmovanja.SKUPINE_IZLOCILNI);
        List<Prijava> prijave = prijavePoVrsti(dogodek.getId());

        zrebStoritev.izvediRocniZrebSkupin(dogodek.getId(), osemPrijavVDveSkupini(prijave));

        // najprej pozicija v skupini (1. v A pred 1. v B, nato 2. v A pred 2. v B ...)
        assertEquals(1, osvezi(prijava(prijave, 0)).getStNosilca(), "1. v A");
        assertEquals(2, osvezi(prijava(prijave, 1)).getStNosilca(), "1. v B");
        assertEquals(7, osvezi(prijava(prijave, 7)).getStNosilca(), "4. v A");
        assertEquals(8, osvezi(prijava(prijave, 4)).getStNosilca(), "4. v B");
    }

    @Test
    void rocniVpisDelujeTudiZaSkupineZaMesta() {
        Dogodek dogodek = pripraviDogodek(8, SistemTekmovanja.SKUPINE_ZA_MESTA);
        List<Prijava> prijave = prijavePoVrsti(dogodek.getId());

        zrebStoritev.izvediRocniZrebSkupin(dogodek.getId(), osemPrijavVDveSkupini(prijave));

        assertEquals(12, tekmeDogodka(dogodek.getId()).size());
        assertEquals(StatusTekmovanja.V_TEKU,
                dogodekRepozitorij.findById(dogodek.getId()).orElseThrow().getStatus());
    }

    @Test
    void formatTopImaRezerveKiNiSoVIzboru() {
        // 10 prijav, izbor je najboljsih 8 (2 skupini po 4); dva sta rezervi
        Dogodek dogodek = pripraviSkupinskiDogodek(10, 2, 4);
        List<Prijava> prijave = prijavePoVrsti(dogodek.getId());

        zrebStoritev.izvediRocniZrebSkupin(dogodek.getId(), osemPrijavVDveSkupini(prijave));

        assertEquals(Prijava.StatusPrijave.REZERVA, osvezi(prijava(prijave, 8)).getStatus());
        assertEquals(Prijava.StatusPrijave.REZERVA, osvezi(prijava(prijave, 9)).getStatus());
        assertNull(osvezi(prijava(prijave, 8)).getIdSkupina());
        assertEquals(12, tekmeDogodka(dogodek.getId()).size());
    }

    @Test
    void predlogSkupinJeZapisanNicNeSpremeni() {
        Dogodek dogodek = pripraviDogodek(8, SistemTekmovanja.SKUPINE_IZLOCILNI);
        List<Prijava> prijave = prijavePoVrsti(dogodek.getId());

        SkupinePredlogDto predlog = zrebStoritev.predlogSkupin(dogodek.getId());

        assertEquals(2, predlog.skupine().size());
        assertEquals(8, predlog.skupine().stream().mapToInt(List::size).sum());
        assertTrue(predlog.rezerve().isEmpty());
        assertTrue(prijave.stream().allMatch(p -> osvezi(p).getIdSkupina() == null),
                "predlog ne zapise skupin v bazo");
        assertEquals(StatusTekmovanja.PRIPRAVA,
                dogodekRepozitorij.findById(dogodek.getId()).orElseThrow().getStatus());
    }

    @Test
    void vpisSeZavrneKerIgralecNiVNobeniSkupini() {
        Dogodek dogodek = pripraviDogodek(8, SistemTekmovanja.SKUPINE_IZLOCILNI);
        List<Prijava> prijave = prijavePoVrsti(dogodek.getId());
        // sedmi igralec manjka
        assertThrows(NeveljavenVnosIzjema.class, () -> zrebStoritev.izvediRocniZrebSkupin(
                dogodek.getId(), List.of(
                        List.of(clan(prijave.get(0), 1), clan(prijave.get(1), 2),
                                clan(prijave.get(2), 3), clan(prijave.get(3), 4)),
                        List.of(clan(prijave.get(4), 1), clan(prijave.get(5), 2),
                                clan(prijave.get(6), 3)))));
    }

    @Test
    void igralecVDvehSkupinahSeZavrne() {
        Dogodek dogodek = pripraviDogodek(8, SistemTekmovanja.SKUPINE_IZLOCILNI);
        List<Prijava> prijave = prijavePoVrsti(dogodek.getId());
        assertThrows(NeveljavenVnosIzjema.class, () -> zrebStoritev.izvediRocniZrebSkupin(
                dogodek.getId(), List.of(
                        List.of(clan(prijave.get(0), 1), clan(prijave.get(1), 2),
                                clan(prijave.get(2), 3), clan(prijave.get(3), 4)),
                        List.of(clan(prijave.get(3), 1), clan(prijave.get(4), 2),
                                clan(prijave.get(5), 3), clan(prijave.get(6), 4),
                                clan(prijave.get(7), 5)))));
    }

    @Test
    void skupinaZEnimIgralcemSeZavrne() {
        Dogodek dogodek = pripraviDogodek(8, SistemTekmovanja.SKUPINE_IZLOCILNI);
        List<Prijava> prijave = prijavePoVrsti(dogodek.getId());
        assertThrows(NeveljavenVnosIzjema.class, () -> zrebStoritev.izvediRocniZrebSkupin(
                dogodek.getId(), List.of(
                        List.of(clan(prijave.get(0), 1), clan(prijave.get(1), 2),
                                clan(prijave.get(2), 3), clan(prijave.get(3), 4),
                                clan(prijave.get(4), 5), clan(prijave.get(5), 6),
                                clan(prijave.get(6), 7)),
                        List.of(clan(prijave.get(7), 1)))));
    }

    @Test
    void enaSkupinaNiSkupinskiSistem() {
        Dogodek dogodek = pripraviDogodek(8, SistemTekmovanja.SKUPINE_IZLOCILNI);
        List<Prijava> prijave = prijavePoVrsti(dogodek.getId());
        assertThrows(NeveljavenVnosIzjema.class, () -> zrebStoritev.izvediRocniZrebSkupin(
                dogodek.getId(), List.of(
                        prijave.stream().map(p -> clan(p, prijave.indexOf(p) + 1)).toList())));
    }

    @Test
    void pozicijeVSkupiniMorajoBitiEnolicneInDoVelikosti() {
        Dogodek dogodek = pripraviDogodek(8, SistemTekmovanja.SKUPINE_IZLOCILNI);
        List<Prijava> prijave = prijavePoVrsti(dogodek.getId());
        // dvakrat pozicija 1 v isti skupini - dvojnik
        assertThrows(NeveljavenVnosIzjema.class, () -> zrebStoritev.izvediRocniZrebSkupin(
                dogodek.getId(), List.of(
                        List.of(clan(prijave.get(0), 1), clan(prijave.get(1), 1),
                                clan(prijave.get(2), 3), clan(prijave.get(3), 4)),
                        List.of(clan(prijave.get(4), 1), clan(prijave.get(5), 2),
                                clan(prijave.get(6), 3), clan(prijave.get(7), 4)))));
        // pozicija 5 v skupini s štirimi igralci ne obstaja
        assertThrows(NeveljavenVnosIzjema.class, () -> zrebStoritev.izvediRocniZrebSkupin(
                dogodek.getId(), List.of(
                        List.of(clan(prijave.get(0), 1), clan(prijave.get(1), 2),
                                clan(prijave.get(2), 3), clan(prijave.get(3), 4)),
                        List.of(clan(prijave.get(4), 1), clan(prijave.get(5), 2),
                                clan(prijave.get(6), 3), clan(prijave.get(7), 5)))));
    }

    @Test
    void rocniVpisJeSamoZaSkupinskeSisteme() {
        Dogodek dogodek = pripraviDogodek(8, SistemTekmovanja.IZLOCILNI);
        List<Prijava> prijave = prijavePoVrsti(dogodek.getId());
        assertThrows(DomenskaIzjema.class, () -> zrebStoritev.izvediRocniZrebSkupin(
                dogodek.getId(), osemPrijavVDveSkupini(prijave)));
    }

    @Test
    void poRocnemVpisuSeIzlocilniDelNeZgradiSam() {
        Dogodek dogodek = pripraviDogodek(8, SistemTekmovanja.SKUPINE_IZLOCILNI);
        List<Prijava> prijave = prijavePoVrsti(dogodek.getId());
        zrebStoritev.izvediRocniZrebSkupin(dogodek.getId(), osemPrijavVDveSkupini(prijave));

        odigrajVse(dogodek.getId());

        assertTrue(tekmeDogodka(dogodek.getId()).stream().noneMatch(t -> t.getFaza() == FazaTekme.GLAVNI),
                "izlocilni del nastane sele, ko ga organizator izbere");
        assertEquals(StatusTekmovanja.V_TEKU,
                dogodekRepozitorij.findById(dogodek.getId()).orElseThrow().getStatus(),
                "dogodek ne sme biti koncan, dokler izlocilni del ni zrebom izbran");
    }

    @Test
    void racunalniskiZrebIzlocilnegaDelaPoSkupinah() {
        Dogodek dogodek = pripraviDogodek(8, SistemTekmovanja.SKUPINE_IZLOCILNI);
        List<Prijava> prijave = prijavePoVrsti(dogodek.getId());
        zrebStoritev.izvediRocniZrebSkupin(dogodek.getId(), osemPrijavVDveSkupini(prijave));
        odigrajVse(dogodek.getId());

        izlocilniZrebStoritev.izvediZreb(dogodek.getId());

        assertEquals(3, tekmeDogodka(dogodek.getId()).stream()
                .filter(t -> t.getFaza() == FazaTekme.GLAVNI).count(),
                "4 udelezenci (zmagovalca in drugouvrsceni 2 skupin) -> 2 + 1 tekma");
        odigrajVse(dogodek.getId());
        assertEquals(StatusTekmovanja.ZAKLJUCEN,
                dogodekRepozitorij.findById(dogodek.getId()).orElseThrow().getStatus());
    }

    @Test
    void rocniIzlocilniDelPoVrstiUdelezencev() {
        Dogodek dogodek = pripraviDogodek(8, SistemTekmovanja.SKUPINE_IZLOCILNI);
        List<Prijava> prijave = prijavePoVrsti(dogodek.getId());
        zrebStoritev.izvediRocniZrebSkupin(dogodek.getId(), osemPrijavVDveSkupini(prijave));
        odigrajVse(dogodek.getId());

        IzlocilniPredlogDto predlog = izlocilniZrebStoritev.predlog(dogodek.getId());
        assertEquals(4, predlog.udelezenci().size());
        assertEquals(4, predlog.idjiPoMestih().size());

        // organizator obrne vrstni red: prvi par v prvem kolu je obratni od predloga
        List<Long> obratno = List.of(predlog.udelezenci().get(3), predlog.udelezenci().get(2),
                predlog.udelezenci().get(1), predlog.udelezenci().get(0));
        izlocilniZrebStoritev.rocniZreb(dogodek.getId(), obratno);

        Tekma prvi = glavni(dogodek.getId(), 1, 1);
        assertEquals(obratno.get(0), prvi.getPrijava1().getId());
        assertEquals(obratno.get(1), prvi.getPrijava2().getId());
        odigrajVse(dogodek.getId());
        assertEquals(StatusTekmovanja.ZAKLJUCEN,
                dogodekRepozitorij.findById(dogodek.getId()).orElseThrow().getStatus());
    }

    @Test
    void izlocilniDelSePopraviDoPrveTekme() {
        Dogodek dogodek = pripraviDogodek(8, SistemTekmovanja.SKUPINE_IZLOCILNI);
        List<Prijava> prijave = prijavePoVrsti(dogodek.getId());
        zrebStoritev.izvediRocniZrebSkupin(dogodek.getId(), osemPrijavVDveSkupini(prijave));
        odigrajVse(dogodek.getId());

        izlocilniZrebStoritev.izvediZreb(dogodek.getId());
        // popravek pred prvo tekmo izlocilnega dela: rocna mreza zamenja racunalniski zreb
        List<Long> predlog = izlocilniZrebStoritev.predlog(dogodek.getId()).udelezenci();
        izlocilniZrebStoritev.rocniZreb(dogodek.getId(), List.of(
                predlog.get(0), predlog.get(1), predlog.get(2), predlog.get(3)));

        assertEquals(3, tekmeDogodka(dogodek.getId()).stream()
                .filter(t -> t.getFaza() == FazaTekme.GLAVNI).count());

        // ko se odigra prva tekma izlocilnega dela, popravek ni vec mogoc
        Tekma prvaIzlocilna = glavni(dogodek.getId(), 1, 1);
        tekmaStoritev.vnesiRezultat(prvaIzlocilna.getId(), new VnosRezultata(null, 3, 0, null, null));
        assertThrows(DomenskaIzjema.class, () -> izlocilniZrebStoritev.izvediZreb(dogodek.getId()));
        assertThrows(DomenskaIzjema.class, () -> izlocilniZrebStoritev.rocniZreb(dogodek.getId(), predlog));
    }

    @Test
    void izlocilniDelPredSkupinamiNiMogoc() {
        Dogodek dogodek = pripraviDogodek(8, SistemTekmovanja.SKUPINE_IZLOCILNI);
        List<Prijava> prijave = prijavePoVrsti(dogodek.getId());
        zrebStoritev.izvediRocniZrebSkupin(dogodek.getId(), osemPrijavVDveSkupini(prijave));

        assertThrows(DomenskaIzjema.class, () -> izlocilniZrebStoritev.izvediZreb(dogodek.getId()));
    }

    @Test
    void skupinePoRocnemVpisuSePopraviDoPrveTekme() {
        Dogodek dogodek = pripraviDogodek(8, SistemTekmovanja.SKUPINE_IZLOCILNI);
        List<Prijava> prijave = prijavePoVrsti(dogodek.getId());
        zrebStoritev.izvediRocniZrebSkupin(dogodek.getId(), osemPrijavVDveSkupini(prijave));

        // popravek: nove skupine (tri namesto dveh) nadomestijo prejsnje
        zrebStoritev.izvediRocniZrebSkupin(dogodek.getId(), List.of(
                List.of(clan(prijave.get(0), 1), clan(prijave.get(1), 2), clan(prijave.get(2), 3)),
                List.of(clan(prijave.get(3), 1), clan(prijave.get(4), 2), clan(prijave.get(5), 3)),
                List.of(clan(prijave.get(6), 1), clan(prijave.get(7), 2))));

        assertEquals(3, skupinaRepozitorij.findByDogodekIdOrderByOznakaAsc(dogodek.getId()).size());
        assertEquals(StatusTekmovanja.V_TEKU,
                dogodekRepozitorij.findById(dogodek.getId()).orElseThrow().getStatus());

        // ko se igra, popravek ni vec mogoc
        Tekma prva = tekmeDogodka(dogodek.getId()).stream()
                .filter(t -> t.getFaza() == FazaTekme.SKUPINA).findFirst().orElseThrow();
        tekmaStoritev.vnesiRezultat(prva.getId(), new VnosRezultata(null, 3, 0, null, null));
        assertThrows(DomenskaIzjema.class, () -> zrebStoritev.izvediRocniZrebSkupin(
                dogodek.getId(), osemPrijavVDveSkupini(prijave)));
    }

    @Test
    void racunalniskiZrebSePonovi() {
        Dogodek dogodek = pripraviDogodek(8, SistemTekmovanja.SKUPINE_IZLOCILNI);
        zrebStoritev.izvediZreb(dogodek.getId());
        int prvi = tekmeDogodka(dogodek.getId()).size();

        zrebStoritev.izvediZreb(dogodek.getId());

        assertEquals(prvi, tekmeDogodka(dogodek.getId()).size(),
                "ponovni zreb pred prvo tekmo nadomesti prvotnega, ne doda drugega");
    }

    @Test
    void rocniIzlocilniZrebZaIzlocilnoMrezo() {
        Dogodek dogodek = pripraviDogodek(4, SistemTekmovanja.IZLOCILNI);
        List<Prijava> prijave = prijavePoVrsti(dogodek.getId());

        izlocilniZrebStoritev.rocniZreb(dogodek.getId(), List.of(
                prijave.get(3).getId(), prijave.get(0).getId(),
                prijave.get(2).getId(), prijave.get(1).getId()));

        Tekma prvi = glavni(dogodek.getId(), 1, 1);
        assertEquals(prijave.get(3).getId(), prvi.getPrijava1().getId());
        assertEquals(prijave.get(0).getId(), prvi.getPrijava2().getId());
        assertEquals(StatusTekmovanja.V_TEKU,
                dogodekRepozitorij.findById(dogodek.getId()).orElseThrow().getStatus());
    }

    @Test
    void rocnaIzlocilnaMrezaSProstimiMesti() {
        // 6 igralcev v mrezi 8 mest: dva prosta prehoda (bye), ki nista sosednja
        Dogodek dogodek = pripraviDogodek(6, SistemTekmovanja.IZLOCILNI);
        List<Prijava> p = prijavePoVrsti(dogodek.getId());

        izlocilniZrebStoritev.rocniZreb(dogodek.getId(), java.util.Arrays.asList(
                p.get(0).getId(), null, p.get(1).getId(), p.get(2).getId(),
                p.get(3).getId(), null, p.get(4).getId(), p.get(5).getId()));

        Tekma prvi = glavni(dogodek.getId(), 1, 1);
        assertEquals(StatusTekme.KONCANA, prvi.getStatus(), "prosti prehod je koncan ze ob nastanku");
        // zmagovalec prostega prehoda napreduje v drugo kolo brez igranja
        assertEquals(p.get(0).getId(), glavni(dogodek.getId(), 2, 1).getPrijava1().getId());
        assertEquals(p.get(3).getId(), glavni(dogodek.getId(), 2, 2).getPrijava1().getId());
    }

    @Test
    void dveSosednjiProstaMestaSeZavrneta() {
        Dogodek dogodek = pripraviDogodek(5, SistemTekmovanja.IZLOCILNI);
        List<Prijava> p = prijavePoVrsti(dogodek.getId());
        // 5 igralcev v 8 mestih: prvi par je prazen - nihce ne bi igral
        assertThrows(NeveljavenVnosIzjema.class, () -> izlocilniZrebStoritev.rocniZreb(
                dogodek.getId(), java.util.Arrays.asList(
                        null, null, p.get(0).getId(), p.get(1).getId(),
                        p.get(2).getId(), p.get(3).getId(), p.get(4).getId(), null)));
    }

    @Test
    void predlogIzlocilneMrezeNicNeZapise() {
        Dogodek dogodek = pripraviDogodek(6, SistemTekmovanja.IZLOCILNI);
        List<Prijava> prijave = prijavePoVrsti(dogodek.getId());

        IzlocilniPredlogDto predlog = izlocilniZrebStoritev.predlog(dogodek.getId());

        assertEquals(6, predlog.udelezenci().size());
        assertEquals(8, predlog.idjiPoMestih().size(), "mreza ima 8 mest, 2 sta prosta");
        assertEquals(2, predlog.idjiPoMestih().stream().filter(java.util.Objects::isNull).count());
        assertTrue(prijave.stream().allMatch(p -> osvezi(p).getIdSkupina() == null));
        assertTrue(tekmeDogodka(dogodek.getId()).isEmpty());
    }

    /* Prijava iz baze (po spremembah, ki jih je naredil zreb v isti transakciji). */
    private Prijava osvezi(Prijava prijava) {
        return prijavaRepozitorij.findById(prijava.getId()).orElseThrow();
    }

    /* Tekma glavnega dela na danem kolu in pozicija. */
    private Tekma glavni(Long idDogodka, int kolo, int pozicija) {
        return tekmeDogodka(idDogodka).stream()
                .filter(t -> t.getFaza() == FazaTekme.GLAVNI && t.getKolo() == kolo
                        && t.getPozicija() == pozicija)
                .findFirst().orElseThrow();
    }

    private void odigrajVse(Long idDogodka) {
        boolean seIgra = true;
        while (seIgra) {
            List<Tekma> pripravljene = tekmeDogodka(idDogodka).stream()
                    .filter(t -> t.getStatus() == StatusTekme.PRIPRAVLJENA).toList();
            seIgra = !pripravljene.isEmpty();
            for (Tekma tekma : pripravljene) {
                tekmaStoritev.vnesiRezultat(tekma.getId(), new VnosRezultata(null, 3, 0, null, null));
            }
        }
    }
}
