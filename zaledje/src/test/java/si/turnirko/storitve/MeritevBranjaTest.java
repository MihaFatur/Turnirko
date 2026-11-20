/* Meritev natancnosti branja listkov (MeritevBranja) - cist izracun, brez baze.

   Pravila, ki jih test varuje:
   - celica je prav ali narobe x zanesljiva ali oznacena; oznacena je negotova
     celica in obe celici niza, ki ga predlog pokaze z opozorilom (neprebrana
     celica, neveljaven niz);
   - manjkajoca ali odvecna vrstica brez oznake je tiha napaka celice;
   - prazen Rezultat pri tockah po nizih ni napaka, brez tock po nizih pa odloca;
   - listek s kodo se primerja v natisnjeni smeri, ostali v boljsi smeri;
   - posebni izid se ne meri;
   - tiha napaka listka = pripravljen predlog z napacno celico; kontrole posebej;
   - mediana in p90 casa pregleda po metodi najblizjega ranga. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import si.turnirko.dto.MeritevBranjaDto;
import si.turnirko.dto.NizVnos;
import si.turnirko.dto.VnosRezultata;
import si.turnirko.modeli.IzidTekme;
import si.turnirko.modeli.NacinPotrditve;

class MeritevBranjaTest {

    private static final int[][] IZID = {{11, 7}, {9, 11}, {11, 5}, {11, 8}};

    @Test
    void vsePravInZanesljivo() {
        MeritevBranja.Celice c = MeritevBranja.celice(listek(IZID, 3, 1), vnos(IZID, false), true);
        // 8 celic nizov + 2 celici Rezultata
        assertEquals(new MeritevBranja.Celice(10, 0, 0, 0), c);
    }

    @Test
    void stiriRazrediCelic() {
        PrebranListek p = new PrebranListek(true, null, null, null, false, null, null, List.of(
                niz(11, 7, false, false),
                niz(9, 11, true, false),     // prav, a negotova: lazni alarm
                niz(11, 6, false, true),     // 6 namesto 5, oznacena: ujeta
                niz(11, 3, false, false)),   // 3 namesto 8, neoznacena: tiha
                3, 1, false, "NI", null, null);
        assertEquals(new MeritevBranja.Celice(7, 1, 1, 1), MeritevBranja.celice(p, vnos(IZID, false), true));
    }

    @Test
    void opozoriloNizaOznaciObeCelici() {
        PrebranListek p = new PrebranListek(true, null, null, null, false, null, null, List.of(
                niz(11, 7, false, false),
                niz(9, null, false, false),  // neprebrana celica: »niz ni v celoti prebran«
                niz(11, 10, false, false),   // neveljaven niz (5 prebran kot 10)
                niz(11, 8, false, false)),
                3, 1, false, "NI", null, null);
        // niz 2: 9 prav (oznacena), 11 narobe (oznacena); niz 3: 11 prav, 10 narobe - oba oznacena
        assertEquals(new MeritevBranja.Celice(6, 2, 2, 0), MeritevBranja.celice(p, vnos(IZID, false), true));
    }

    @Test
    void manjkajocaInOdvecnaVrsticaStaTihiNapaki() {
        int[][] tri = {{11, 7}, {9, 11}, {11, 5}};
        // bralnik ni nasel 4. vrstice
        MeritevBranja.Celice manjka = MeritevBranja.celice(listek(tri, 3, 1), vnos(IZID, false), true);
        assertEquals(new MeritevBranja.Celice(8, 0, 0, 2), manjka);
        // bralnik je v prazni 5. vrstici videl niz
        int[][] pet = {{11, 7}, {9, 11}, {11, 5}, {11, 8}, {11, 4}};
        MeritevBranja.Celice odvec = MeritevBranja.celice(listek(pet, 3, 1), vnos(IZID, false), true);
        assertEquals(new MeritevBranja.Celice(10, 0, 0, 2), odvec);
        // prazne vrstice na koncu se ne stejejo (predlog jih izpusti)
        PrebranListek prazne = new PrebranListek(true, null, null, null, false, null, null, List.of(
                niz(11, 7, false, false), niz(9, 11, false, false), niz(11, 5, false, false),
                niz(11, 8, false, false), niz(null, null, true, true)),
                3, 1, false, "NI", null, null);
        assertEquals(new MeritevBranja.Celice(10, 0, 0, 0), MeritevBranja.celice(prazne, vnos(IZID, false), true));
    }

    @Test
    void rezultatSeMeriSamoKoJeZapisanAliOdloca() {
        PrebranListek brezRezultata = listek(IZID, null, null);
        assertEquals(new MeritevBranja.Celice(8, 0, 0, 0),
                MeritevBranja.celice(brezRezultata, vnos(IZID, false), true));
        PrebranListek napacenRezultat = listek(IZID, 3, 2);
        assertEquals(new MeritevBranja.Celice(9, 0, 0, 1),
                MeritevBranja.celice(napacenRezultat, vnos(IZID, false), true));

        // turnir brez tock po nizih: odloca Rezultat, tudi neprebran
        VnosRezultata samoIzid = new VnosRezultata(null, 3, 1, null, List.of());
        PrebranListek prazen = new PrebranListek(true, null, null, null, false, null, null, List.of(),
                null, 1, false, "NI", null, null);
        // levo neprebrano (napaka, a vidna kot »?«), desno 1 prav
        assertEquals(new MeritevBranja.Celice(1, 0, 1, 0), MeritevBranja.celice(prazen, samoIzid, true));
    }

    @Test
    void smerStolpcev() {
        // sodnik je na prazen listek najprej napisal igralca 2
        int[][] obrnjen = {{7, 11}, {11, 9}, {5, 11}, {8, 11}};
        PrebranListek p = listek(obrnjen, 1, 3);
        assertEquals(new MeritevBranja.Celice(10, 0, 0, 0), MeritevBranja.celice(p, vnos(IZID, false), false));
        // listek s kodo je natisnjen v smeri tekme: obrat bi pomenil napacno branje
        assertEquals(0, MeritevBranja.celice(p, vnos(IZID, false), true).prav());
    }

    @Test
    void posebniIzidSeNeMeri() {
        VnosRezultata brezBoja = new VnosRezultata(IzidTekme.BREZ_BOJA, null, null, 1, null);
        assertNull(MeritevBranja.celice(listek(IZID, 3, 1), brezBoja, true));
        assertNull(MeritevBranja.resnica(brezBoja, false));
    }

    @Test
    void zbirnikTiheNapakeKontroleInCasi() {
        MeritevBranja.Celice prav = new MeritevBranja.Celice(10, 0, 0, 0);
        MeritevBranja.Celice napaka = new MeritevBranja.Celice(9, 0, 0, 1);
        MeritevBranja.Zbirnik z = new MeritevBranja.Zbirnik("Skupaj", null, null);
        z.dodaj(new MeritevBranja.Vzorec(true, false, true, false, true, false, 2000, NacinPotrditve.EN_DOTIK, prav));
        z.dodaj(new MeritevBranja.Vzorec(true, false, true, true, true, false, 9000, NacinPotrditve.OBRAZEC, napaka));
        z.dodaj(new MeritevBranja.Vzorec(true, false, true, true, true, true, 15000, NacinPotrditve.OBRAZEC, napaka));
        z.dodaj(new MeritevBranja.Vzorec(true, false, true, true, false, false, null, null, napaka));
        z.dodaj(new MeritevBranja.Vzorec(false, true, false, null, null, false, null, null, null));
        z.dodaj(new MeritevBranja.Vzorec(true, false, false, null, null, false, null, null, null));
        // neuspelo branje, vpisano rocno: cas steje, celic ni
        z.dodaj(new MeritevBranja.Vzorec(false, true, true, null, null, false, 30000, NacinPotrditve.OBRAZEC, null));

        MeritevBranjaDto.Skupina s = z.dto();
        assertEquals(7, s.branj());
        assertEquals(5, s.prebranih());
        assertEquals(2, s.neuspelih());
        assertEquals(4, s.potrjenih());
        assertEquals(1, s.brezPopravka());
        assertEquals(3, s.pripravljenih());
        assertEquals(4, s.izmerjenih());
        // pripravljen + napacna celica (drugi in tretji), ne cetrti (ni bil pripravljen)
        assertEquals(2, s.tihihNapak());
        assertEquals(1, s.kontrol());
        assertEquals(1, s.kontrolZNapako());
        assertEquals(new MeritevBranjaDto.Celice(37, 0, 0, 3), s.celice());
        assertEquals(new MeritevBranjaDto.Casi(4, 9000, 30000, 1, 2000, 3, 15000), s.casi());
    }

    @Test
    void percentil() {
        List<Integer> deset = new ArrayList<>();
        for (int i = 10; i >= 1; i--) {
            deset.add(i);
        }
        assertEquals(5, MeritevBranja.percentil(deset, 50));
        assertEquals(9, MeritevBranja.percentil(deset, 90));
        assertEquals(7, MeritevBranja.percentil(List.of(7), 90));
        assertNull(MeritevBranja.percentil(List.of(), 50));
    }

    // ---------- Pomozno ----------

    private static PrebranListek.Niz niz(Integer levo, Integer desno, boolean negL, boolean negD) {
        return new PrebranListek.Niz(levo, desno, negL, negD);
    }

    private static PrebranListek listek(int[][] tocke, Integer rezLevo, Integer rezDesno) {
        List<PrebranListek.Niz> nizi = new ArrayList<>();
        for (int[] t : tocke) {
            nizi.add(niz(t[0], t[1], false, false));
        }
        return new PrebranListek(true, null, null, null, false, null, null, nizi, rezLevo, rezDesno, false,
                "NI", null, null);
    }

    /* Potrjen izid v straneh tekme; obrnjeno = prijava 1 je desni stolpec listka. */
    static VnosRezultata vnos(int[][] izid, boolean obrnjeno) {
        List<NizVnos> nizi = new ArrayList<>();
        int a = 0;
        int b = 0;
        for (int[] n : izid) {
            nizi.add(obrnjeno ? new NizVnos(n[1], n[0]) : new NizVnos(n[0], n[1]));
            if (n[0] > n[1]) {
                a++;
            } else {
                b++;
            }
        }
        return new VnosRezultata(null, obrnjeno ? b : a, obrnjeno ? a : b, null, nizi);
    }
}
