/* Zasedenost miz in predlog naslednje tekme (M1, M2; PredlogTekem).

   Domenska pravila, ki jih ta test varuje (docs/pravila/mize.md):
   - miza je zasedena, dokler se na njej igra (tudi druga miza ekipnega srecanja),
     izklopljena miza ne dobi predlogov;
   - kdor igra ali sodi tekmo v igri, ni predlagan - po OSEBI (isti igralec v
     dvojicah ali drugem dogodku); tekma ostane na seznamu z razlogom;
   - vrstni red: zaostanek (lastnik: zaostala skupina pred pocitkom), pocitek,
     nizje kolo, najdlje caka, stalen vrstni red;
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

    /* Lastnik 23. 11. 2026: zaostala skupina ima prednost pred pocitkom. */
    @Test
    void zaostalaSkupinaPredPocitkom() {
        // skupina 10: igralec 1 ima se 3 tekme, a je pravkar koncal
        Tekma koncana = t(1).igralca(1, 9).koncana(ZDAJ.minusMinutes(1)).v();
        Tekma a = t(2).igralca(1, 2).kolo(2).v();
        Tekma a2 = t(3).igralca(1, 3).kolo(3).v();
        Tekma a3 = t(4).igralca(1, 4).kolo(4).v();
        // skupina 20: vsak ima samo se to tekmo, vsi spocit
        Tekma b = t(5).skupina(20).igralca(5, 6).v();
        Rezultat r = izracunaj(nast(1), koncana, a, a2, a3, b);

        assertEquals(2L, idji(r).get(0));
        assertTrue(kandidat(r, 2).razlogi().contains("Igralec1 ima v kategoriji še 3 tekme"),
                kandidat(r, 2).razlogi().toString());
        assertTrue(kandidat(r, 2).razlogi().contains("Igralec1: zadnja tekma pred 1 min"),
                kandidat(r, 2).razlogi().toString());
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

    @Test
    void najdljeCakaPrej() {
        Tekma prej1 = t(1).igralca(1, 9).koncana(ZDAJ.minusMinutes(10)).v();
        Tekma prej2 = t(2).skupina(20).igralca(3, 8).koncana(ZDAJ.minusMinutes(25)).v();
        Tekma cakaDeset = t(3).igralca(1, 2).v();
        Tekma cakaPetindvajset = t(4).skupina(20).igralca(3, 4).v();
        Tekma seNiIgral = t(5).skupina(30).igralca(5, 6).v();
        Rezultat r = izracunaj(nast(1), prej1, prej2, cakaDeset, cakaPetindvajset, seNiIgral);
        assertEquals(List.of(5L, 4L, 3L), idji(r));
        assertTrue(kandidat(r, 5).razlogi().contains("prva tekma"));
        assertTrue(kandidat(r, 4).razlogi().contains("premor 25 min"), kandidat(r, 4).razlogi().toString());
    }

    /* Izlocilni del: kola do konca veje. Cetrtfinale (3 kola do konca) pred skupino,
       v kateri imata oba se 2 tekmi. */
    @Test
    void zaostanekIzlocilnegaDelaSoKolaDoKonca() {
        Tekma cetrtfinale = t(1).dogodek(2).faza(FazaTekme.GLAVNI).skupina(null).kolo(1).igralca(1, 2).v();
        Tekma polfinale = t(2).dogodek(2).faza(FazaTekme.GLAVNI).skupina(null).kolo(2).brezIgralcev()
                .status(StatusTekme.CAKA).v();
        Tekma finale = t(3).dogodek(2).faza(FazaTekme.GLAVNI).skupina(null).kolo(3).brezIgralcev()
                .status(StatusTekme.CAKA).v();
        Tekma skupina1 = t(4).igralca(5, 6).v();
        Tekma skupina2 = t(5).igralca(5, 6).kolo(2).v();
        Rezultat r = izracunaj(nast(1), cetrtfinale, polfinale, finale, skupina1, skupina2);
        assertEquals(1L, idji(r).get(0));
        assertTrue(kandidat(r, 1).razlogi().contains("Igralec1 ima v kategoriji še 3 tekme"));
    }

    /* Skupina s sledecim izlocilnim delom: ocena kol po skupinah steje v zaostanek. */
    @Test
    void izlocilniDelPoSkupiniStejeVZaostanek() {
        Tekma sIzlocilnim = t(1).igralca(1, 2).nadaljnjih(3).v();
        Tekma brez = t(2).dogodek(2).skupina(20).igralca(3, 4).v();
        Tekma brez2 = t(3).dogodek(2).skupina(20).igralca(3, 4).kolo(2).v();
        Rezultat r = izracunaj(nast(1), brez, brez2, sIzlocilnim);
        assertEquals(1L, idji(r).get(0));
        assertTrue(kandidat(r, 1).razlogi().contains("Igralec1 ima v kategoriji še 4 tekme"));
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

    @Test
    void sklanjanjeTekem() {
        assertEquals("tekmo", PredlogTekem.tekem(1));
        assertEquals("tekmi", PredlogTekem.tekem(2));
        assertEquals("tekme", PredlogTekem.tekem(3));
        assertEquals("tekme", PredlogTekem.tekem(4));
        assertEquals("tekem", PredlogTekem.tekem(5));
        assertEquals("tekem", PredlogTekem.tekem(11));
        assertEquals("tekmo", PredlogTekem.tekem(101));
    }

    // ---------- pomozno ----------

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
        int nadaljnjih;

        Gradnik(long id) {
            this.id = id;
        }

        Gradnik dogodek(long d) { idDogodka = d; return this; }
        Gradnik dogodekVTeku(boolean v) { vTeku = v; return this; }
        Gradnik vrsta(Vrsta v) { vrsta = v; return this; }
        Gradnik faza(FazaTekme f) { faza = f; return this; }
        Gradnik skupina(Integer s) { idSkupina = s == null ? null : (long) s; return this; }
        Gradnik skupina(long s) { idSkupina = s; return this; }
        Gradnik kolo(int k) { kolo = k; return this; }
        Gradnik status(StatusTekme s) { status = s; return this; }
        Gradnik izid(IzidTekme i) { izid = i; return this; }
        Gradnik igralca(long a, long b) { s1 = posameznik(a); s2 = posameznik(b); return this; }
        Gradnik strani(Stran a, Stran b) { s1 = a; s2 = b; return this; }
        Gradnik brezIgralcev() { s1 = null; s2 = null; return this; }
        Gradnik miza2(int m) { miza2 = m; return this; }
        Gradnik sodnik(long o) { sodnik = new Oseba(o, "Igralec" + o); return this; }
        Gradnik poklicana(LocalDateTime c) { poklicana = c; return this; }
        Gradnik nadaljnjih(int n) { nadaljnjih = n; return this; }

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
                    nadaljnjih);
        }
    }
}
