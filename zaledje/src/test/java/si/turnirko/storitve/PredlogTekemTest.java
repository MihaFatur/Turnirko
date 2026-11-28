/* Zasedenost miz in predlog naslednje tekme (M1, M2; PredlogTekem).

   Domenska pravila, ki jih ta test varuje (docs/pravila/mize.md):
   - miza je zasedena, dokler se na njej igra (tudi druga miza ekipnega srecanja),
     izklopljena miza ne dobi predlogov;
   - kdor igra ali sodi tekmo v igri, ni predlagan - po OSEBI (isti igralec v
     dvojicah ali drugem dogodku); tekma ostane na seznamu z razlogom;
   - vrstni red: strogo po kolih (lastnik 25. 11. 2026; izlocilni del steje kot
     naslednja kola za skupinami), v kolu pocitek, vrstni red kategorij (skupne
     mize), skupina, stalen vrstni red;
   - porazenec zadnje tekme na mizi sodi naslednjo: samo po tekmi posameznikov,
     odigrani ali predani, isti dan, ce od tedaj ni igral ali sodil; tekme s
     takim igralcem gredo za ostale;
   - vsaka prosta miza dobi svoj prvi predlog, brez skupne osebe z drugimi;
     najprej mize, kjer porazenec caka, da sodi, nato ostale po stevilki. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import si.turnirko.modeli.FazaTekme;
import si.turnirko.modeli.IzidTekme;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.storitve.PredlogTekem.Kandidat;
import si.turnirko.storitve.PredlogTekem.Miza;
import si.turnirko.storitve.PredlogTekem.Nastavitve;
import si.turnirko.storitve.PredlogTekem.Oseba;
import si.turnirko.storitve.PredlogTekem.Rezultat;
import si.turnirko.storitve.PredlogTekem.Stran;
import si.turnirko.storitve.PredlogTekem.Tekma;
import si.turnirko.storitve.PredlogTekem.Vrsta;

class PredlogTekemTest {

    private static final LocalDateTime ZDAJ = LocalDateTime.of(2026, 12, 5, 12, 0);

    // ---------- zasedenost miz ----------

    @Test
    void mizaJeZasedenaDoklerSeNaNjejIgra() {
        Tekma igra = t(1).igralca(1, 2).vIgri(2).v();
        Rezultat r = izracunaj(nast(3), igra, t(2).igralca(3, 4).v());

        assertNull(miza(r, 1).tekma());
        assertEquals(1L, miza(r, 2).tekma().id());
        assertNull(miza(r, 3).tekma());
        assertTrue(miza(r, 2).predlogi().isEmpty());
    }

    @Test
    void ekipnoSrecanjeZasedeObeMizi() {
        Tekma srecanje = t(1).vrsta(Vrsta.EKIPNO).igralca(1, 2).vIgri(1).miza2(2).v();
        Rezultat r = izracunaj(nast(3), srecanje);
        assertEquals(1L, miza(r, 1).tekma().id());
        assertEquals(1L, miza(r, 2).tekma().id());
        assertNull(miza(r, 3).tekma());
    }

    @Test
    void izklopljenaMizaNimaPredlogov() {
        Rezultat r = izracunaj(new Nastavitve(2, Set.of(1), false, 3), t(1).igralca(1, 2).v());
        assertTrue(miza(r, 1).izklopljena());
        assertTrue(miza(r, 1).predlogi().isEmpty());
        assertEquals(List.of(1L), miza(r, 2).predlogi());
    }

    // ---------- kdo je zaseden ----------

    @Test
    void igralecNaMiziNiPredlagan() {
        Tekma igra = t(1).igralca(1, 2).vIgri(1).v();
        Tekma zNjim = t(2).igralca(1, 3).v();
        Tekma prosta = t(3).igralca(4, 5).v();
        Rezultat r = izracunaj(nast(2), igra, zNjim, prosta);

        Kandidat k = kandidat(r, 2);
        assertTrue(k.zaseden());
        assertTrue(k.razlogi().contains("Igralec1 igra na mizi 1"), k.razlogi().toString());
        // zaseden je na koncu in ni predlog nobene mize
        assertEquals(List.of(3L, 2L), idji(r));
        assertEquals(List.of(3L), miza(r, 2).predlogi());
    }

    @Test
    void sodnikTekmeVIgriJeZaseden() {
        Tekma igra = t(1).igralca(1, 2).vIgri(1).sodnik(7).v();
        Tekma sSodnikom = t(2).igralca(7, 3).v();
        Rezultat r = izracunaj(nast(2), igra, sSodnikom);
        Kandidat k = kandidat(r, 2);
        assertTrue(k.zaseden());
        assertTrue(k.razlogi().contains("Igralec7 sodi na mizi 1"), k.razlogi().toString());
    }

    /* Isti igralec igra posamicno in v dvojicah: zaseden je kot oseba. */
    @Test
    void osebaVDvojicahJeZasedenaTudiPriPosamicni() {
        Tekma posamicna = t(1).igralca(1, 2).vIgri(1).v();
        Tekma dvojice = t(2).dogodek(2).vrsta(Vrsta.DVOJICE).faza(FazaTekme.GLAVNI)
                .strani(par(21, 1, 3), par(22, 4, 5)).v();
        Rezultat r = izracunaj(nast(2), posamicna, dvojice);
        assertTrue(kandidat(r, 2).zaseden());
    }

    /* Ekipna tekma, ki ze tece (postava), a nima mize, je kandidat - sebe ne zaseda. */
    @Test
    void ekipnaVIgriBrezMizeJeKandidat() {
        Tekma ekipna = t(1).vrsta(Vrsta.EKIPNO).igralca(1, 2).status(StatusTekme.V_IGRI).v();
        Rezultat r = izracunaj(nast(1), ekipna);
        assertFalse(kandidat(r, 1).zaseden());
        assertEquals(List.of(1L), miza(r, 1).predlogi());
    }

    @Test
    void tekmaKategorijeVPripravInCakajocaNistaKandidata() {
        Rezultat r = izracunaj(nast(1), t(1).igralca(1, 2).dogodekVTeku(false).v(),
                t(2).igralca(3, 4).status(StatusTekme.CAKA).v());
        assertTrue(r.kandidati().isEmpty());
    }

    // ---------- vrstni red ----------

    /* Strogo po kolih (lastnik 25. 11. 2026): zaostala tekma 1. kola skupine A
       (igralec 1 je bil zaseden drugje in je pravkar koncal) gre pred 2. kolo skupine
       B, ceprav so tam vsi spocit - pocitek odloca samo v istem kolu. */
    @Test
    void zaostalaTekmaNizjegaKolaPredPocitkom() {
        Tekma drugje = t(1).dogodek(2).skupina(50).igralca(1, 9).koncana(ZDAJ.minusMinutes(1)).v();
        Tekma zaostala = t(2).skupina(10, "A").kolo(1).igralca(1, 2).v();
        Tekma a3 = t(3).skupina(10, "A").kolo(3).igralca(3, 4).v();
        Tekma b2 = t(4).skupina(20, "B").kolo(2).igralca(5, 6).v();
        Tekma b3 = t(5).skupina(20, "B").kolo(3).igralca(7, 8).v();
        Rezultat r = izracunaj(nast(1), drugje, zaostala, a3, b2, b3);

        assertEquals(List.of(2L, 4L, 3L, 5L), idji(r));
        assertTrue(kandidat(r, 2).razlogi().contains("1. kolo"), kandidat(r, 2).razlogi().toString());
        assertTrue(kandidat(r, 2).razlogi().contains("Igralec1: zadnja tekma pred 1 min"),
                kandidat(r, 2).razlogi().toString());
    }

    /* Lastnik 23. 11. 2026 (turnir s skupinami D, G, J po 3 igralce): edina tekma
       1. kola skupine po 3 pride za 1. kolom skupine C, ne na konec. V istem kolu
       gredo skupine po vrsti A, B, C, D (ne po id-ju tekem). */
    @Test
    void skupinaPoTriVKoluMedSkupinamiPoStiri() {
        List<Tekma> tekme = new ArrayList<>();
        tekme.addAll(skupinaPoTri(1, 40, "D", 70));
        tekme.addAll(skupinaPoStiri(11, 10, "A", 10));
        tekme.addAll(skupinaPoStiri(21, 20, "B", 20));
        tekme.addAll(skupinaPoStiri(31, 30, "C", 30));
        Rezultat r = PredlogTekem.izracunaj(tekme, nast(1), ZDAJ);

        assertEquals(List.of(11L, 12L, 21L, 22L, 31L, 32L, 1L,
                        13L, 14L, 23L, 24L, 33L, 34L, 2L,
                        15L, 16L, 25L, 26L, 35L, 36L, 3L), idji(r));
        assertTrue(kandidat(r, 1).razlogi().contains("1. kolo"), kandidat(r, 1).razlogi().toString());
    }

    /* Lastnik 25. 11. 2026 (preklicuje »vecja skupina prej« s 23. 11.): strogo po
       kolih, zato skupina po 5 v vsakem kolu igra skupaj s skupino po 4 in konca
       zadnja (4. in 5. kolo igra sama). */
    @Test
    void skupinaPoPetIgraPoKolihInKoncaZadnja() {
        List<Tekma> tekme = new ArrayList<>();
        for (int kolo = 1; kolo <= 5; kolo++) {
            tekme.add(t(50 + kolo).skupina(50, "E").kolo(kolo).igralca(50 + 2 * kolo, 51 + 2 * kolo).v());
        }
        for (int kolo = 1; kolo <= 3; kolo++) {
            tekme.add(t(10 + kolo).skupina(10, "A").kolo(kolo).igralca(10 + 2 * kolo, 11 + 2 * kolo).v());
        }
        Rezultat r = PredlogTekem.izracunaj(tekme, nast(1), ZDAJ);
        assertEquals(List.of(11L, 51L, 12L, 52L, 13L, 53L, 54L, 55L), idji(r));
    }

    /* Lastnik 23. 11. 2026: kategorije se izmenjujejo po kolih. Kategorija z vec
       skupinami (daljsi izlocilni del) ne gre vsa naprej: najprej 1. kolo obeh. */
    @Test
    void kategorijeIzmenicnoPoKolih() {
        Tekma e1a1 = t(1).dogodek(1).skupina(10, "A").kolo(1).igralca(1, 2).v();
        Tekma e1a2 = t(2).dogodek(1).skupina(10, "A").kolo(2).igralca(1, 3).v();
        Tekma e2a1 = t(3).dogodek(2).skupina(20, "A").kolo(1).igralca(5, 6).v();
        Tekma e2a2 = t(4).dogodek(2).skupina(20, "A").kolo(2).igralca(5, 7).v();
        Tekma e2b1 = t(5).dogodek(2).skupina(30, "B").kolo(1).igralca(8, 9).v();
        Tekma e2b2 = t(6).dogodek(2).skupina(30, "B").kolo(2).igralca(8, 11).v();
        Rezultat r = izracunaj(nast(1), e2b2, e2a2, e1a2, e2b1, e2a1, e1a1);
        assertEquals(List.of(1L, 3L, 5L, 2L, 4L, 6L), idji(r));
    }

    @Test
    void priEnakemZaostankuSpocitiPrej() {
        Tekma koncana = t(1).igralca(1, 9).koncana(ZDAJ.minusMinutes(1)).v();
        Tekma utrujen = t(2).igralca(1, 2).v();
        Tekma spocit = t(3).skupina(20).igralca(3, 4).v();
        Rezultat r = izracunaj(nast(1), koncana, utrujen, spocit);
        assertEquals(List.of(3L, 2L), idji(r));

        // pocitek 0 min: utrujenosti ni, premor je samo podatek
        Rezultat brez = izracunaj(new Nastavitve(1, Set.of(), false, 0), koncana, utrujen, spocit);
        assertTrue(kandidat(brez, 2).razlogi().contains("premor 1 min"), kandidat(brez, 2).razlogi().toString());
    }

    @Test
    void priEnakemPocitkuNizjeKoloPrej() {
        Tekma kolo2 = t(1).igralca(1, 2).kolo(2).v();
        Tekma kolo1 = t(2).skupina(20).igralca(3, 4).kolo(1).v();
        assertEquals(List.of(2L, 1L), idji(izracunaj(nast(1), kolo2, kolo1)));
    }

    /* Merilo »najdlje caka« je odpadlo (25. 11. 2026): v istem kolu gredo tekme po
       skupinah kot na listkih, ne po tem, kdo dlje caka. */
    @Test
    void vKoluPoSkupinahNeNajdljeCaka() {
        Tekma prej1 = t(1).igralca(1, 9).koncana(ZDAJ.minusMinutes(10)).v();
        Tekma prej2 = t(2).skupina(20).igralca(3, 8).koncana(ZDAJ.minusMinutes(25)).v();
        Tekma cakaDeset = t(3).igralca(1, 2).v();
        Tekma cakaPetindvajset = t(4).skupina(20).igralca(3, 4).v();
        Tekma seNiIgral = t(5).skupina(30).igralca(5, 6).v();
        Rezultat r = izracunaj(nast(1), prej1, prej2, cakaDeset, cakaPetindvajset, seNiIgral);
        assertEquals(List.of(3L, 4L, 5L), idji(r));
        assertTrue(kandidat(r, 5).razlogi().contains("prva tekma"));
        assertTrue(kandidat(r, 4).razlogi().contains("premor 25 min"), kandidat(r, 4).razlogi().toString());
    }

    /* Izlocilni del steje kot naslednja kola za zadnjim kolom skupin svoje kategorije
       (lastnik 25. 11. 2026): pri 3 kolih skupin je cetrtfinale »4. kolo«. Gre za 3. kolom
       in skupaj s 4. kolom skupine po 5 druge kategorije, pred njenim 5. kolom. */
    @Test
    void izlocilniDelStejeKotNaslednjaKola() {
        List<Tekma> tekme = new ArrayList<>();
        for (int kolo = 1; kolo <= 3; kolo++) {
            tekme.add(t(20 + kolo).dogodek(2).skupina(20, "1").kolo(kolo).igralca(40 + 2 * kolo, 41 + 2 * kolo)
                    .koncana(ZDAJ.minusMinutes(90)).v());
        }
        Tekma cetrtfinale = t(1).dogodek(2).faza(FazaTekme.GLAVNI).skupina(null).kolo(1).igralca(1, 2).v();
        Tekma polfinale = t(2).dogodek(2).faza(FazaTekme.GLAVNI).skupina(null).kolo(2).brezIgralcev()
                .status(StatusTekme.CAKA).v();
        tekme.add(cetrtfinale);
        tekme.add(polfinale);
        Tekma kolo3 = t(13).skupina(10, "1").kolo(3).igralca(5, 6).v();
        Tekma kolo4 = t(14).skupina(10, "1").kolo(4).igralca(7, 8).v();
        Tekma kolo5 = t(15).skupina(10, "1").kolo(5).igralca(9, 11).v();
        tekme.addAll(List.of(kolo5, kolo4, kolo3));

        Rezultat r = PredlogTekem.izracunaj(tekme, nast(1), ZDAJ);

        assertEquals(List.of(13L, 14L, 1L, 15L), idji(r));
        assertTrue(kandidat(r, 1).razlogi().contains("šteje kot 4. kolo"), kandidat(r, 1).razlogi().toString());
    }

    /* Skupne mize (V78): v kolu gredo kategorije v vrstnem redu, ki ga je nastavil
       organizator (U15m, U15z, U21m, U21z), nato naslednje kolo v istem redu. */
    @Test
    void vKoluPoVrstnemReduKategorij() {
        List<Tekma> tekme = new ArrayList<>();
        long[] dogodki = {21, 11, 22, 12};
        for (int i = 0; i < dogodki.length; i++) {
            long d = dogodki[i];
            tekme.add(t(d * 10 + 1).dogodek(d).skupina(d, "1").kolo(1).igralca(d * 10 + 1, d * 10 + 2).v());
            tekme.add(t(d * 10 + 2).dogodek(d).skupina(d, "1").kolo(2).igralca(d * 10 + 3, d * 10 + 4).v());
        }
        Nastavitve n = new Nastavitve(1, Set.of(), false, 3, List.of(), List.of(11L, 12L, 21L, 22L));

        Rezultat r = PredlogTekem.izracunaj(tekme, n, ZDAJ);

        assertEquals(List.of(111L, 121L, 211L, 221L, 112L, 122L, 212L, 222L), idji(r));
        // brez reda kategorij po id-ju dogodka
        assertEquals(List.of(111L, 121L, 211L, 221L, 112L, 122L, 212L, 222L),
                idji(PredlogTekem.izracunaj(tekme, nast(1), ZDAJ)));
        Nastavitve obrnjeno = new Nastavitve(1, Set.of(), false, 3, List.of(), List.of(22L, 21L));
        assertEquals(List.of(221L, 211L, 111L, 121L, 222L, 212L, 112L, 122L),
                idji(PredlogTekem.izracunaj(tekme, obrnjeno, ZDAJ)));
    }

    /* Vrstni red kol (V80, lastnik 28. 11. 2026): kategorija 1 igra kola 3, 1, 2,
       kategorija 2 naravno. »Strogo po kolih« velja po mestu v vrstnem redu: v prvem
       valu gre 3. kolo prve in 1. kolo druge kategorije ... Izlocilni del prve
       kategorije ostane za vsemi njenimi koli skupin. */
    @Test
    void vrstniRedKolVKategoriji() {
        List<Tekma> tekme = new ArrayList<>();
        for (long d = 1; d <= 2; d++) {
            for (int kolo = 1; kolo <= 3; kolo++) {
                long id = d * 10 + kolo;
                tekme.add(t(id).dogodek(d).skupina(d, "1").kolo(kolo).igralca(id * 10 + 1, id * 10 + 2).v());
            }
        }
        tekme.add(t(19).dogodek(1).faza(FazaTekme.GLAVNI).kolo(1).igralca(191, 192).v());
        Nastavitve n = new Nastavitve(1, Set.of(), false, 3, List.of(), List.of(1L, 2L),
                java.util.Map.of(1L, List.of(3, 1, 2)));

        Rezultat r = PredlogTekem.izracunaj(tekme, n, ZDAJ);

        assertEquals(List.of(13L, 21L, 11L, 22L, 12L, 23L, 19L), idji(r));
        // razlog pove pravo kolo skupine, ne mesta v vrstnem redu
        assertTrue(kandidat(r, 13).razlogi().contains("3. kolo"), kandidat(r, 13).razlogi().toString());
        assertTrue(kandidat(r, 19).razlogi().contains("šteje kot 4. kolo"), kandidat(r, 19).razlogi().toString());
        // brez vrstnega reda kol naravno
        assertEquals(List.of(11L, 21L, 12L, 22L, 13L, 23L, 19L),
                idji(PredlogTekem.izracunaj(tekme, new Nastavitve(1, Set.of(), false, 3, List.of(),
                        List.of(1L, 2L)), ZDAJ)));
    }

    /* Nastavljena kola najprej, manjkajoca po vrsti za njimi, neobstojeca odpadejo
       (zreb je bil ponovljen z manjsimi skupinami). */
    @Test
    void redKolDopolniManjkajocaInOdstraniNeobstojeca() {
        assertEquals(List.of(1, 2, 3), PredlogTekem.redKol(null, 3));
        assertEquals(List.of(1, 2, 3), PredlogTekem.redKol(List.of(), 3));
        assertEquals(List.of(3, 1, 2), PredlogTekem.redKol(List.of(3, 1, 2), 3));
        assertEquals(List.of(2, 1, 3), PredlogTekem.redKol(List.of(2), 3));
        assertEquals(List.of(1, 2, 3), PredlogTekem.redKol(List.of(5, 4), 3));
        assertEquals(List.of(3, 1, 2, 4, 5), PredlogTekem.redKol(List.of(3, 1, 2), 5));
        assertEquals(List.of(), PredlogTekem.redKol(List.of(1), 0));
    }

    /* Pocitek odloca pred vrstnim redom kategorij, a samo v istem kolu: utrujen
       igralec prve kategorije pocaka za spocitimi drugih kategorij istega kola. */
    @Test
    void vKoluPocitekPredRedomKategorij() {
        Tekma koncana = t(1).dogodek(1).kolo(1).igralca(1, 9).koncana(ZDAJ.minusMinutes(1)).v();
        Tekma utrujen = t(2).dogodek(1).kolo(2).igralca(1, 2).v();
        Tekma spocit = t(3).dogodek(2).skupina(20).kolo(2).igralca(3, 4).v();
        Tekma naslednjeKolo = t(4).dogodek(2).skupina(20).kolo(3).igralca(5, 6).v();
        Nastavitve n = new Nastavitve(1, Set.of(), false, 3, List.of(), List.of(1L, 2L));
        assertEquals(List.of(3L, 2L, 4L), idji(PredlogTekem.izracunaj(
                List.of(koncana, utrujen, spocit, naslednjeKolo), n, ZDAJ)));
    }

    // ---------- porazenec sodi ----------

    @Test
    void porazenecZadnjeTekmeSodiNaSvojiMizi() {
        Tekma zadnja = t(1).igralca(1, 2).naMizi(2).zmagal(1).koncana(ZDAJ.minusMinutes(2)).v();
        Tekma sPorazencem = t(2).igralca(2, 3).v();
        Tekma brez = t(3).skupina(20).igralca(4, 5).v();
        Rezultat r = izracunaj(sodi(3), zadnja, sPorazencem, brez);

        assertEquals(new Oseba(2, "Igralec2"), miza(r, 2).cakajociSodnik());
        assertNull(miza(r, 1).cakajociSodnik());
        // tekma s porazencem, ki naj sodi, gre za ostale
        assertEquals(List.of(3L, 2L), idji(r));
        assertTrue(kandidat(r, 2).razlogi().contains("Igralec2 naj sodi na mizi 2"));
        assertEquals(ZDAJ.minusMinutes(2), miza(r, 2).prostaOd());
    }

    @Test
    void brezNastavitvePorazenecNeSodi() {
        Tekma zadnja = t(1).igralca(1, 2).naMizi(1).zmagal(1).koncana(ZDAJ.minusMinutes(2)).v();
        assertNull(miza(izracunaj(nast(1), zadnja), 1).cakajociSodnik());
    }

    @Test
    void poDvojicahNihceNeSodi() {
        Tekma dvojice = t(1).vrsta(Vrsta.DVOJICE).faza(FazaTekme.GLAVNI).strani(par(21, 1, 2), par(22, 3, 4))
                .naMizi(1).zmagal(21).koncana(ZDAJ.minusMinutes(2)).v();
        assertNull(miza(izracunaj(sodi(1), dvojice), 1).cakajociSodnik());
    }

    @Test
    void poBrezBojaNihceNeSodi() {
        Tekma brezBoja = t(1).igralca(1, 2).naMizi(1).zmagal(1).izid(IzidTekme.BREZ_BOJA)
                .koncana(ZDAJ.minusMinutes(2)).v();
        assertNull(miza(izracunaj(sodi(1), brezBoja), 1).cakajociSodnik());
    }

    @Test
    void poPredajiPorazenecSodi() {
        Tekma predaja = t(1).igralca(1, 2).naMizi(1).zmagal(2).izid(IzidTekme.PREDAJA)
                .koncana(ZDAJ.minusMinutes(2)).v();
        assertEquals(1L, miza(izracunaj(sodi(1), predaja), 1).cakajociSodnik().id());
    }

    @Test
    void vcerajsnjiPorazenecNeSodi() {
        Tekma vceraj = t(1).igralca(1, 2).naMizi(1).zmagal(1).koncana(ZDAJ.minusDays(1)).v();
        assertNull(miza(izracunaj(sodi(1), vceraj), 1).cakajociSodnik());
    }

    /* Porazenec, ki je od tedaj ze igral ali sodil, ne caka vec na sojenje. */
    @Test
    void porazenecKiJeZeBilPoklicanNeSodi() {
        Tekma zadnja = t(1).igralca(1, 2).naMizi(1).zmagal(1).koncana(ZDAJ.minusMinutes(20)).v();
        Tekma potem = t(2).igralca(2, 3).naMizi(2).zmagal(3).poklicana(ZDAJ.minusMinutes(15))
                .koncana(ZDAJ.minusMinutes(5)).v();
        Rezultat r = izracunaj(sodi(2), zadnja, potem);
        assertNull(miza(r, 1).cakajociSodnik());
        // na mizi 2 je izgubil znova - tam sodi
        assertEquals(2L, miza(r, 2).cakajociSodnik().id());
    }

    @Test
    void porazenecKiIgraNeSodi() {
        Tekma zadnja = t(1).igralca(1, 2).naMizi(1).zmagal(1).koncana(ZDAJ.minusMinutes(5)).v();
        Tekma igra = t(2).igralca(2, 3).vIgri(2).poklicana(ZDAJ.minusMinutes(1)).v();
        assertNull(miza(izracunaj(sodi(2), zadnja, igra), 1).cakajociSodnik());
    }

    // ---------- razporeditev predlogov po mizah ----------

    @Test
    void prosteMizeDobijoRazlicnePrvePredlogeBrezSkupneOsebe() {
        Tekma a = t(1).igralca(1, 2).v();
        Tekma b = t(2).igralca(1, 3).kolo(2).v();
        Tekma c = t(3).skupina(20).igralca(4, 5).kolo(3).v();
        Rezultat r = izracunaj(nast(2), a, b, c);
        assertEquals(List.of(1L, 2L, 3L), idji(r));
        // miza 2 ne dobi tekme 2: igralec 1 bi igral na dveh mizah
        assertEquals(List.of(1L, 2L), miza(r, 1).predlogi());
        assertEquals(List.of(3L, 2L), miza(r, 2).predlogi());
    }

    /* Brez sodnika, ki caka, se dvorana polni po stevilki miz. */
    @Test
    void mizeBrezCakajocegaSodnikaPoStevilki() {
        Tekma na2 = t(1).igralca(8, 9).naMizi(2).zmagal(8).koncana(ZDAJ.minusMinutes(30)).v();
        Tekma edina = t(2).igralca(1, 2).v();
        Rezultat r = izracunaj(nast(3), na2, edina);
        assertEquals(List.of(2L), miza(r, 1).predlogi());
        assertTrue(miza(r, 2).predlogi().isEmpty());
        assertTrue(miza(r, 3).predlogi().isEmpty());
    }

    /* Miza, kjer porazenec caka, da sodi, dobi predlog pred nerabljenimi mizami:
       sodnik je ze ob njej. */
    @Test
    void mizaSCakajocimSodnikomPrva() {
        Tekma na3 = t(1).igralca(8, 9).naMizi(3).zmagal(8).koncana(ZDAJ.minusMinutes(1)).v();
        Tekma edina = t(2).skupina(20).igralca(1, 2).v();
        Rezultat r = izracunaj(sodi(3), na3, edina);
        assertEquals(9L, miza(r, 3).cakajociSodnik().id());
        assertEquals(List.of(2L), miza(r, 3).predlogi());
        assertTrue(miza(r, 1).predlogi().isEmpty());
    }

    /* Porazenec, ki bo sodil predlog ene mize, ne igra v predlogu druge: »Pokliči
       predloge« bi ga sicer poklical na dve mizi hkrati. */
    @Test
    void sodnikPredlogaNiVPredloguDrugeMize() {
        Tekma na1 = t(1).igralca(1, 2).naMizi(1).zmagal(1).koncana(ZDAJ.minusMinutes(1)).v();
        Tekma na2 = t(2).igralca(3, 4).naMizi(2).zmagal(3).koncana(ZDAJ.minusMinutes(2)).v();
        // 1-3 brez cakajocih sodnikov, 4-2 s porazencema obeh miz
        Tekma zmagovalca = t(3).igralca(1, 3).kolo(2).v();
        Tekma porazenca = t(4).igralca(4, 2).kolo(2).v();
        Rezultat r = izracunaj(sodi(2), na1, na2, zmagovalca, porazenca);
        assertEquals(2L, miza(r, 1).cakajociSodnik().id());
        assertEquals(List.of(3L, 4L), miza(r, 2).predlogi());
        // miza 2 (prej sproscena) dobi tekmo zmagovalcev in sodi 4; miza 1 ne dobi
        // tekme, v kateri igra 4 - druge pa ni
        assertTrue(miza(r, 1).predlogi().isEmpty());
    }

    @Test
    void najvecTriPredlogiNaMizo() {
        List<Tekma> tekme = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            tekme.add(t(i).skupina(10L + i).igralca(2L * i, 2L * i + 1).v());
        }
        Rezultat r = PredlogTekem.izracunaj(tekme, nast(1), ZDAJ);
        assertEquals(3, miza(r, 1).predlogi().size());
    }

    // ---------- rocni vrstni red ----------

    /* Organizator je vrsto uredil: njegove tekme gredo naprej v njegovem redu, tudi pred
       zaostalim kolom; tekma, ki je na seznamu ni (pripravila se je pozneje), za njimi.
       Mize dobijo predloge po tem redu. */
    @Test
    void rocniVrstniRedVeljaPredSamodejnim() {
        Tekma prvoKolo = t(1).kolo(1).igralca(1, 2).v();
        Tekma drugoKolo = t(2).kolo(2).igralca(3, 4).v();
        Tekma nova = t(3).kolo(1).igralca(5, 6).v();
        Nastavitve n = new Nastavitve(1, Set.of(), false, 3, List.of(2L, 1L, 99L));

        Rezultat r = izracunaj(n, prvoKolo, drugoKolo, nova);

        assertEquals(List.of(2L, 1L, 3L), idji(r));
        assertEquals(2L, miza(r, 1).predlogi().get(0));
        // brez rocnega reda: samodejno
        assertEquals(List.of(1L, 3L, 2L), idji(izracunaj(nast(1), prvoKolo, drugoKolo, nova)));
    }

    /* Zasedena tekma ostane na mestu, ki ji ga je dal organizator, a mize ne dobi. */
    @Test
    void rocniVrstniRedNePredlagaZasedenih() {
        Tekma igra = t(1).igralca(1, 2).vIgri(1).v();
        Tekma zNjim = t(2).igralca(1, 3).v();
        Tekma prosta = t(3).igralca(4, 5).v();
        Nastavitve n = new Nastavitve(2, Set.of(), false, 3, List.of(2L, 3L));

        Rezultat r = izracunaj(n, igra, zNjim, prosta);

        assertEquals(List.of(2L, 3L), idji(r));
        assertTrue(kandidat(r, 2).zaseden());
        assertEquals(List.of(3L), miza(r, 2).predlogi());
    }

    @Test
    void sklanjanjeKol() {
        assertEquals("kolo", PredlogTekem.kol(1));
        assertEquals("kola", PredlogTekem.kol(2));
        assertEquals("kola", PredlogTekem.kol(3));
        assertEquals("kola", PredlogTekem.kol(4));
        assertEquals("kol", PredlogTekem.kol(5));
        assertEquals("kol", PredlogTekem.kol(12));
        assertEquals("kolo", PredlogTekem.kol(101));
    }

    @Test
    void skupinePoVrstiKotNaListkih() {
        assertTrue(PredlogTekem.primerjajSkupini("A", "B") < 0);
        assertTrue(PredlogTekem.primerjajSkupini("Z", "ZA") < 0);
        assertTrue(PredlogTekem.primerjajSkupini("ZZ", "ZZA") < 0);
        assertTrue(PredlogTekem.primerjajSkupini("9", "10") < 0);
        assertTrue(PredlogTekem.primerjajSkupini("J", null) < 0);
        assertEquals(0, PredlogTekem.primerjajSkupini(null, null));
    }

    // ---------- pomozno ----------

    /* Skupina po 4 (krozno, 3 kola po 2 tekmi): id-ji prviId .. prviId+5. */
    private static List<Tekma> skupinaPoStiri(long prviId, long idSkupine, String oznaka, long prviIgralec) {
        long a = prviIgralec, b = prviIgralec + 1, c = prviIgralec + 2, d = prviIgralec + 3;
        long[][] pari = {{a, d}, {b, c}, {a, c}, {d, b}, {a, b}, {c, d}};
        List<Tekma> tekme = new ArrayList<>();
        for (int i = 0; i < pari.length; i++) {
            tekme.add(t(prviId + i).skupina(idSkupine, oznaka).kolo(i / 2 + 1)
                    .igralca(pari[i][0], pari[i][1]).v());
        }
        return tekme;
    }

    /* Skupina po 3 (krozno s prostim: 3 kola po 1 tekmo): id-ji prviId .. prviId+2. */
    private static List<Tekma> skupinaPoTri(long prviId, long idSkupine, String oznaka, long prviIgralec) {
        long a = prviIgralec, b = prviIgralec + 1, c = prviIgralec + 2;
        long[][] pari = {{b, c}, {a, c}, {a, b}};
        List<Tekma> tekme = new ArrayList<>();
        for (int i = 0; i < pari.length; i++) {
            tekme.add(t(prviId + i).skupina(idSkupine, oznaka).kolo(i + 1)
                    .igralca(pari[i][0], pari[i][1]).v());
        }
        return tekme;
    }

    private static Nastavitve nast(int mize) {
        return new Nastavitve(mize, Set.of(), false, 3);
    }

    private static Nastavitve sodi(int mize) {
        return new Nastavitve(mize, Set.of(), true, 3);
    }

    private static Rezultat izracunaj(Nastavitve n, Tekma... tekme) {
        return PredlogTekem.izracunaj(Arrays.asList(tekme), n, ZDAJ);
    }

    private static Miza miza(Rezultat r, int st) {
        return r.mize().stream().filter(m -> m.stevilka() == st).findFirst().orElseThrow();
    }

    private static Kandidat kandidat(Rezultat r, long id) {
        return r.kandidati().stream().filter(k -> k.tekma().id() == id).findFirst().orElseThrow();
    }

    private static List<Long> idji(Rezultat r) {
        return r.kandidati().stream().map(k -> k.tekma().id()).toList();
    }

    /* Posameznik: prijava 100 + id osebe, ime »Igralec<id>«. */
    private static Stran posameznik(long idOsebe) {
        return new Stran(100 + idOsebe, "Igralec" + idOsebe, List.of(new Oseba(idOsebe, "Igralec" + idOsebe)));
    }

    private static Stran par(long idPrijave, long a, long b) {
        return new Stran(idPrijave, "Igralec" + a + " / Igralec" + b,
                List.of(new Oseba(a, "Igralec" + a), new Oseba(b, "Igralec" + b)));
    }

    private static Gradnik t(long id) {
        return new Gradnik(id);
    }

    /* Tekma za test: privzeto pripravljena tekma posameznikov skupine 10 dogodka 1. */
    private static final class Gradnik {
        long id;
        long idDogodka = 1;
        boolean vTeku = true;
        Vrsta vrsta = Vrsta.POSAMICNO;
        FazaTekme faza = FazaTekme.SKUPINA;
        Long idSkupina = 10L;
        int kolo = 1;
        StatusTekme status = StatusTekme.PRIPRAVLJENA;
        IzidTekme izid;
        Stran s1;
        Stran s2;
        Long zmagovalec;
        Integer miza;
        Integer miza2;
        LocalDateTime poklicana;
        LocalDateTime koncana;
        Oseba sodnik;
        String oznaka;

        Gradnik(long id) {
            this.id = id;
        }

        Gradnik dogodek(long d) { idDogodka = d; return this; }
        Gradnik dogodekVTeku(boolean v) { vTeku = v; return this; }
        Gradnik vrsta(Vrsta v) { vrsta = v; return this; }
        Gradnik faza(FazaTekme f) { faza = f; return this; }
        Gradnik skupina(Integer s) { idSkupina = s == null ? null : (long) s; return this; }
        Gradnik skupina(long s) { idSkupina = s; return this; }
        Gradnik skupina(long s, String o) { idSkupina = s; oznaka = o; return this; }
        Gradnik kolo(int k) { kolo = k; return this; }
        Gradnik status(StatusTekme s) { status = s; return this; }
        Gradnik izid(IzidTekme i) { izid = i; return this; }
        Gradnik igralca(long a, long b) { s1 = posameznik(a); s2 = posameznik(b); return this; }
        Gradnik strani(Stran a, Stran b) { s1 = a; s2 = b; return this; }
        Gradnik brezIgralcev() { s1 = null; s2 = null; return this; }
        Gradnik miza2(int m) { miza2 = m; return this; }
        Gradnik sodnik(long o) { sodnik = new Oseba(o, "Igralec" + o); return this; }
        Gradnik poklicana(LocalDateTime c) { poklicana = c; return this; }

        Gradnik vIgri(int m) {
            status = StatusTekme.V_IGRI;
            miza = m;
            if (poklicana == null) poklicana = ZDAJ.minusMinutes(10);
            return this;
        }

        Gradnik naMizi(int m) { miza = m; return this; }

        /* zmagal: id osebe (posameznik) ali prijave (par) */
        Gradnik zmagal(long kdo) {
            zmagovalec = kdo > 20 && kdo < 100 ? kdo : 100 + kdo;
            return this;
        }

        Gradnik koncana(LocalDateTime c) {
            status = StatusTekme.KONCANA;
            koncana = c;
            if (izid == null) izid = IzidTekme.IGRANO;
            if (poklicana == null && miza != null) poklicana = c.minusMinutes(15);
            return this;
        }

        Tekma v() {
            return new Tekma(id, idDogodka, vTeku, vrsta, faza, idSkupina, null, kolo, (int) id,
                    status, izid, false, s1, s2, zmagovalec, miza, miza2, poklicana, koncana, sodnik,
                    oznaka);
        }
    }
}
