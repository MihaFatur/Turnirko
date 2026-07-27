/* Testi kroznega in skupinskega sistema ter statistike (lestvica, dvoboj). */
package si.turnirko.storitve;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import si.turnirko.dto.DvobojDto;
import si.turnirko.dto.LestvicaIgralcaDto;
import si.turnirko.dto.VnosRezultata;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.FazaTekme;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.SistemTekmovanja;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.StatusTekmovanja;
import si.turnirko.modeli.Tekma;

class SistemiTekmovanjaTest extends IntegracijskiTest {

    @BeforeEach
    void deterministicniZreb() {
        zrebStoritev.nastaviNakljucje(new Random(42));
    }

    /* Odigra vse trenutno pripravljene tekme (zmaga prvega 3:0), dokler
       jih ne zmanjka - tako se turnir odvije do konca. */
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

    // ---------------------------------------------------------------------
    // KROZNI
    // ---------------------------------------------------------------------

    @Test
    void krozniUstvariVseTekmeVsakZVsakim() {
        Dogodek dogodek = pripraviDogodek(4, SistemTekmovanja.KROZNI);
        zrebStoritev.izvediZreb(dogodek.getId());

        List<Tekma> tekme = tekmeDogodka(dogodek.getId());
        assertEquals(6, tekme.size(), "4 igralci -> 4*3/2 = 6 tekem");
        assertTrue(tekme.stream().allMatch(t -> t.getFaza() == FazaTekme.SKUPINA));
        assertTrue(tekme.stream().allMatch(t -> t.getStatus() == StatusTekme.PRIPRAVLJENA),
                "v kroznem sistemu so vse tekme takoj pripravljene");
    }

    @Test
    void krozniPoKoncuDodeliKoncnaMesta() {
        Dogodek dogodek = pripraviDogodek(4, SistemTekmovanja.KROZNI);
        zrebStoritev.izvediZreb(dogodek.getId());
        odigrajVse(dogodek.getId());

        assertEquals(StatusTekmovanja.ZAKLJUCEN,
                dogodekRepozitorij.findById(dogodek.getId()).orElseThrow().getStatus());

        List<Integer> mesta = prijavaRepozitorij.najdiZaDogodek(dogodek.getId()).stream()
                .map(Prijava::getKoncnoMesto).toList();
        assertEquals(List.of(1, 2, 3, 4), mesta.stream().sorted().toList(),
                "vsak igralec dobi svoje koncno mesto 1..4");
    }

    // ---------------------------------------------------------------------
    // SKUPINE_IZLOCILNI
    // ---------------------------------------------------------------------

    @Test
    void skupineUstvarijoSkupinskeTekmeBrezIzlocilnega() {
        Dogodek dogodek = pripraviDogodek(8, SistemTekmovanja.SKUPINE_IZLOCILNI);
        zrebStoritev.izvediZreb(dogodek.getId());

        assertEquals(2, skupinaRepozitorij.findByDogodekIdOrderByOznakaAsc(dogodek.getId()).size(),
                "8 igralcev -> 2 skupini po 4");

        List<Tekma> tekme = tekmeDogodka(dogodek.getId());
        assertTrue(tekme.stream().allMatch(t -> t.getFaza() == FazaTekme.SKUPINA),
                "ob zrebu obstajajo samo skupinske tekme");
        assertEquals(12, tekme.size(), "2 skupini po 6 tekem");
    }

    @Test
    void skupinePoKoncuSkupinZgenerirajoIzlocilniDelInDolociZmagovalca() {
        Dogodek dogodek = pripraviDogodek(8, SistemTekmovanja.SKUPINE_IZLOCILNI);
        zrebStoritev.izvediZreb(dogodek.getId());
        odigrajVse(dogodek.getId());

        List<Tekma> tekme = tekmeDogodka(dogodek.getId());
        assertTrue(tekme.stream().anyMatch(t -> t.getFaza() == FazaTekme.GLAVNI),
                "po skupinah se zgenerira izlocilni del");

        assertEquals(StatusTekmovanja.ZAKLJUCEN,
                dogodekRepozitorij.findById(dogodek.getId()).orElseThrow().getStatus());

        List<Prijava> prijave = prijavaRepozitorij.najdiZaDogodek(dogodek.getId());
        assertEquals(1, prijave.stream().filter(p -> Integer.valueOf(1).equals(p.getKoncnoMesto())).count());
        assertEquals(1, prijave.stream().filter(p -> Integer.valueOf(2).equals(p.getKoncnoMesto())).count());
        // vsi igralci so razporejeni v skupino in imajo mesto v skupini
        assertTrue(prijave.stream().allMatch(p -> p.getIdSkupina() != null && p.getMestoVSkupini() != null));
    }

    @Test
    void premaloIgralcevZaSkupine() {
        Dogodek dogodek = pripraviDogodek(4, SistemTekmovanja.SKUPINE_IZLOCILNI);
        assertThrows(si.turnirko.izjeme.DomenskaIzjema.class,
                () -> zrebStoritev.izvediZreb(dogodek.getId()));
    }

    // ---------------------------------------------------------------------
    // Statistika
    // ---------------------------------------------------------------------

    @Test
    void lestvicaInDvobojUpostevataOdigraneTekme() {
        Dogodek dogodek = pripraviDogodek(4, SistemTekmovanja.KROZNI);
        zrebStoritev.izvediZreb(dogodek.getId());

        // zapomni si en par pred igranjem
        Tekma prva = tekmeDogodka(dogodek.getId()).get(0);
        Long igralec1 = prva.getPrijava1().getIgralec().getId();
        Long igralec2 = prva.getPrijava2().getIgralec().getId();

        odigrajVse(dogodek.getId());

        List<LestvicaIgralcaDto> lestvica = statistikaStoritev.globalnaLestvica();
        assertEquals(4, lestvica.size());
        assertTrue(lestvica.stream().anyMatch(v -> v.odigrane() > 0), "tekme se stejejo");

        DvobojDto dvoboj = statistikaStoritev.dvoboj(igralec1, igralec2);
        assertEquals(1, dvoboj.odigrane(), "igralca sta se v kroznem sistemu srecala enkrat");
        assertEquals(1, dvoboj.zmagePrvega() + dvoboj.zmageDrugega());
    }
}
