/* Zreb nakljucnega para za semafor "1 na 1" na domaci strani.

   Pravilo, ki ga test varuje: izzrebana igralca sta ze igrala drug proti
   drugemu. Izid 0 : 0 gledalcu ne pove nicesar, zato par brez medsebojne
   tekme ni veljaven predlog - ne glede na to, koliko tekem ima vsak zase. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import si.turnirko.dto.NakljucniParDto;
import si.turnirko.dto.VnosRezultata;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.SistemTekmovanja;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.Tekma;

class NakljucniParTest extends IntegracijskiTest {

    /* Koliko zrebov preveri vsak test. Dovolj, da se par brez medsebojne
       tekme pokaze, ce bi bil sploh mogoc. */
    private static final int ZREBOV = 25;

    @BeforeEach
    void deterministicnoNakljucje() {
        zrebStoritev.nastaviNakljucje(new Random(42));
        statistikaStoritev.nastaviNakljucje(new Random(7));
    }

    /* Krozni sistem s stirimi igralci: vsak je igral z vsakim, zato je vsak
       par veljaven. Prvi in najpomembnejsi pogoj je, da zreb sploh vrne par,
       ki ima medsebojno zgodovino. */
    @Test
    void izzrebanaIgralcaStaZeIgralaDrugProtiDrugemu() {
        odigranKrozniDogodek(4);

        for (int i = 0; i < ZREBOV; i++) {
            NakljucniParDto par = statistikaStoritev.nakljucniPar();
            assertNotEquals(par.prvi(), par.drugi(), "par sta dva razlicna igralca");
            assertTrue(statistikaStoritev.dvoboj(par.prvi(), par.drugi()).odigrane() >= 1,
                    "izzrebana igralca morata imeti vsaj eno medsebojno tekmo");
        }
    }

    /* Igralec, ki tekem se nima, v zreb ne sme - tudi kadar je v sifrantu.
       Enako velja za igralca, ki so ga vsi ostali ze igrali: on veljaven je. */
    @Test
    void igralecBrezTekemVZrebNePride() {
        odigranKrozniDogodek(4);
        Igralec brezTekem = noviIgralec("Nova", "Prijava");

        for (int i = 0; i < ZREBOV; i++) {
            NakljucniParDto par = statistikaStoritev.nakljucniPar();
            assertNotEquals(brezTekem.getId(), par.prvi(), "igralec brez tekem ni kandidat");
            assertNotEquals(brezTekem.getId(), par.drugi(), "igralec brez tekem ni nasprotnik");
        }
    }

    /* Dva turnirja brez skupnega igralca: zreb ne sme sestaviti para cez mejo
       turnirja, ceprav imata oba igralca tekme. To je bistvo popravka -
       prejsnji zreb je vlekel dva poljubna igralca iz sifranta. */
    @Test
    void parNeNastaneCezDveLoceniDruzbiIgralcev() {
        Dogodek prvi = odigranKrozniDogodek(4);
        Dogodek drugi = odigranKrozniDogodek(4);

        List<Long> izPrvega = idjiIgralcev(prvi);
        List<Long> izDrugega = idjiIgralcev(drugi);

        /* Preverjamo samo zrebe, ki so zadeli enega od nasih dveh turnirjev -
           testna baza v target/ lahko nosi tudi ostanke drugih testov. */
        for (int i = 0; i < ZREBOV; i++) {
            NakljucniParDto par = statistikaStoritev.nakljucniPar();
            if (izPrvega.contains(par.prvi())) {
                assertTrue(izPrvega.contains(par.drugi()),
                        "nasprotnik je iz istega turnirja, sicer se nista srecala");
            }
            if (izDrugega.contains(par.prvi())) {
                assertTrue(izDrugega.contains(par.drugi()),
                        "nasprotnik je iz istega turnirja, sicer se nista srecala");
            }
        }
    }

    /* Brez odigranih tekem zreb ne sme pociti: semafor je takrat prazen, a
       pripomocek na domaci strani ostane uporaben. */
    @Test
    void brezMedsebojnihTekemZrebVrneKarDvaIgralca() {
        Igralec prvi = noviIgralec("Ana", "Brez");
        Igralec drugi = noviIgralec("Bor", "Tekem");

        NakljucniParDto par = statistikaStoritev.nakljucniPar();
        assertNotEquals(par.prvi(), par.drugi());
        assertEquals(0, statistikaStoritev.dvoboj(prvi.getId(), drugi.getId()).odigrane(),
                "ta test ima smisel le, dokler v bazi ni odigranih tekem");
    }

    /* Krozni dogodek, odigran do konca: vsak igralec ima tekmo z vsakim. */
    private Dogodek odigranKrozniDogodek(int steviloIgralcev) {
        Dogodek dogodek = pripraviDogodek(steviloIgralcev, SistemTekmovanja.KROZNI);
        zrebStoritev.izvediZreb(dogodek.getId());
        boolean seIgra = true;
        while (seIgra) {
            List<Tekma> pripravljene = tekmeDogodka(dogodek.getId()).stream()
                    .filter(t -> t.getStatus() == StatusTekme.PRIPRAVLJENA).toList();
            seIgra = !pripravljene.isEmpty();
            for (Tekma tekma : pripravljene) {
                tekmaStoritev.vnesiRezultat(tekma.getId(), new VnosRezultata(null, 3, 0, null, null));
            }
        }
        return dogodek;
    }

    private List<Long> idjiIgralcev(Dogodek dogodek) {
        return prijavaRepozitorij.najdiZaDogodek(dogodek.getId()).stream()
                .map(p -> p.getIgralec().getId())
                .toList();
    }
}
