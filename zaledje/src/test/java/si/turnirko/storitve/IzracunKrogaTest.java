/* Izracun kroga, ki ga gledalec odpre pod lestvico: nositi mora natanko tiste
   stevilke, po katerih je bil vrstni red dolocen, in samo tiste, ki so odlocale
   (kjer odlocijo nizi, tock ni). Vrstni red sam drzi KrogVSkupiniTest. */
package si.turnirko.storitve;

import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import si.turnirko.dto.KrogDto;
import si.turnirko.dto.NizVnos;
import si.turnirko.dto.VnosRezultata;
import si.turnirko.dto.VrsticaLestviceDto;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.IzidTekme;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.Tekma;

class IzracunKrogaTest extends IntegracijskiTest {

    /* Dve dvojici v isti skupini: dva kroga, vsak z mesti in z eno tekmo. */
    @Test
    void krogDvehPokazeSamoNjunoTekmo() {
        Dogodek dogodek = pripraviSkupinskiDogodek(4, 1, 4);
        zrebStoritev.izvediZreb(dogodek.getId());
        List<Prijava> clani = clani(dogodek);
        Prijava a = clani.get(0);
        Prijava b = clani.get(1);
        Prijava c = clani.get(2);
        Prijava d = clani.get(3);

        odigraj(dogodek, a, b, 3, 2);
        odigraj(dogodek, a, c, 3, 0);
        odigraj(dogodek, d, a, 3, 0);
        odigraj(dogodek, b, c, 3, 0);
        odigraj(dogodek, b, d, 3, 0);
        odigraj(dogodek, c, d, 3, 0);

        List<KrogDto> krogi = krogi(dogodek);
        assertEquals(2, krogi.size());

        KrogDto prvi = krogi.get(0);
        assertEquals(1, prvi.odMesta());
        assertEquals(2, prvi.doMesta());
        assertEquals(KrogDto.Merilo.ZMAGE, prvi.odlocilo());
        assertEquals(List.of(a.getId(), b.getId()), idji(prvi));
        assertEquals(1, prvi.tekme().size());
        assertEquals(3, zmagovalcevihNizov(prvi.tekme().get(0)));
        assertEquals(2, porazencevihNizov(prvi.tekme().get(0)));

        assertEquals(3, krogi.get(1).odMesta());
        assertEquals(4, krogi.get(1).doMesta());
        assertTrue(prvi.vseTekmeOdigrane());
    }

    /* Primer iz pravil: B, C, D v ciklu. Enake zmage niso celica, ampak stavek,
       nizi pa so samo iz tekem med njimi; tock ni, ker jih ni bilo treba. */
    @Test
    void krogTrehPoNizihNosiSamoNize() {
        Dogodek dogodek = pripraviSkupinskiDogodek(4, 1, 4);
        zrebStoritev.izvediZreb(dogodek.getId());
        List<Prijava> clani = clani(dogodek);
        Prijava a = clani.get(0);
        Prijava b = clani.get(1);
        Prijava c = clani.get(2);
        Prijava d = clani.get(3);

        odigraj(dogodek, a, b, 3, 2);
        odigraj(dogodek, a, c, 3, 1);
        odigraj(dogodek, a, d, 3, 0);
        odigraj(dogodek, b, c, 3, 0);
        odigraj(dogodek, c, d, 3, 1);
        odigraj(dogodek, d, b, 3, 0);

        List<KrogDto> krogi = krogi(dogodek);
        assertEquals(1, krogi.size());
        KrogDto krog = krogi.get(0);

        assertEquals(2, krog.odMesta());
        assertEquals(4, krog.doMesta());
        assertEquals(KrogDto.Merilo.NIZI, krog.odlocilo());
        assertEquals(1, krog.enakeZmage());
        assertEquals(List.of(d.getId(), b.getId(), c.getId()), idji(krog));
        assertEquals(List.of(new KrogDto.Izkupicek(4, 3), new KrogDto.Izkupicek(3, 3), new KrogDto.Izkupicek(3, 4)),
                krog.clani().stream().map(KrogDto.Clan::nizi).toList());
        assertEquals(List.of(2, 3, 4), krog.clani().stream().map(KrogDto.Clan::mesto).toList());
        assertTrue(krog.clani().stream().allMatch(clan -> clan.zmage() == null), "zmage ne locijo");
        assertTrue(krog.clani().stream().allMatch(clan -> clan.tocke() == null), "tock ni bilo treba");
        assertEquals(3, krog.tekme().size(), "samo tekme med clani, brez tekem z A");
        assertTrue(krog.opombe().isEmpty());
    }

    /* Nizi so izenaceni: izracun pokaze nize IN tocke, ker prav enaki nizi
       pojasnijo, zakaj stejejo tocke. */
    @Test
    void izenaceniNiziPokazejoSeTocke() {
        Dogodek dogodek = pripraviSkupinskiDogodek(4, 1, 4);
        zrebStoritev.izvediZreb(dogodek.getId());
        List<Prijava> clani = clani(dogodek);
        Prijava a = clani.get(0);
        Prijava b = clani.get(1);
        Prijava c = clani.get(2);
        Prijava d = clani.get(3);

        odigraj(dogodek, a, b, 3, 0);
        odigraj(dogodek, a, c, 3, 0);
        odigraj(dogodek, a, d, 3, 0);
        odigraj(dogodek, b, c, 3, 0, nizi(11, 0, 11, 0, 11, 0));
        odigraj(dogodek, c, d, 3, 0, nizi(11, 9, 11, 9, 11, 9));
        odigraj(dogodek, d, b, 3, 0, nizi(11, 5, 11, 5, 11, 5));

        KrogDto krog = krogi(dogodek).get(0);

        assertEquals(KrogDto.Merilo.TOCKE, krog.odlocilo());
        assertEquals(List.of(b.getId(), d.getId(), c.getId()), idji(krog));
        assertTrue(krog.clani().stream().allMatch(clan -> clan.nizi().equals(new KrogDto.Izkupicek(3, 3))));
        assertEquals(List.of(new KrogDto.Izkupicek(48, 33), new KrogDto.Izkupicek(60, 48), new KrogDto.Izkupicek(33, 60)),
                krog.clani().stream().map(KrogDto.Clan::tocke).toList());
        assertFalse(krog.tockeManjkajo());
    }

    /* Krog treh, ki ni cikel: odlocijo ze zmage in nizov se ne prikaze. */
    @Test
    void kjerOdlocijoZmageNizovNi() {
        Dogodek dogodek = pripraviSkupinskiDogodek(6, 1, 6);
        zrebStoritev.izvediZreb(dogodek.getId());
        List<Prijava> clani = clani(dogodek);
        Prijava a = clani.get(0);
        Prijava b = clani.get(1);
        Prijava c = clani.get(2);
        Prijava d = clani.get(3);
        Prijava e = clani.get(4);
        Prijava f = clani.get(5);

        odigraj(dogodek, a, b, 3, 0);
        odigraj(dogodek, a, c, 3, 0);
        odigraj(dogodek, a, d, 3, 0);
        odigraj(dogodek, a, e, 3, 0);
        odigraj(dogodek, a, f, 3, 0);
        odigraj(dogodek, b, c, 3, 2);
        odigraj(dogodek, b, d, 3, 2);
        odigraj(dogodek, c, d, 3, 0);
        odigraj(dogodek, e, b, 3, 0);
        odigraj(dogodek, f, b, 3, 0);
        odigraj(dogodek, c, e, 3, 0);
        odigraj(dogodek, f, c, 3, 0);
        odigraj(dogodek, d, e, 3, 0);
        odigraj(dogodek, d, f, 3, 0);
        odigraj(dogodek, f, e, 3, 0);

        KrogDto krog = krogi(dogodek).stream()
                .filter(k -> k.clani().size() == 3)
                .findFirst().orElseThrow();

        assertEquals(KrogDto.Merilo.ZMAGE, krog.odlocilo());
        assertNull(krog.enakeZmage());
        assertEquals(List.of(b.getId(), c.getId(), d.getId()), idji(krog));
        assertEquals(List.of(2, 1, 0), krog.clani().stream().map(KrogDto.Clan::zmage).toList());
        assertTrue(krog.clani().stream().allMatch(clan -> clan.nizi() == null));
    }

    /* Krog stirih: A ima 3 zmage med njimi, ostali trije po 1 in jih razsodijo
       nizi SAMO iz tekem med njimi tremi - tekme z A ne stejejo vec. */
    @Test
    void krogStirihSeRazbijeInManjsiKrogStejeSvojeNize() {
        Dogodek dogodek = pripraviSkupinskiDogodek(6, 1, 6);
        zrebStoritev.izvediZreb(dogodek.getId());
        List<Prijava> clani = clani(dogodek);
        Prijava a = clani.get(0);
        Prijava b = clani.get(1);
        Prijava c = clani.get(2);
        Prijava d = clani.get(3);
        Prijava e = clani.get(4);
        Prijava f = clani.get(5);

        odigraj(dogodek, a, b, 3, 0);
        odigraj(dogodek, a, c, 3, 0);
        odigraj(dogodek, a, d, 3, 0);
        odigraj(dogodek, b, c, 3, 0);
        odigraj(dogodek, c, d, 3, 1);
        odigraj(dogodek, d, b, 3, 0);
        odigraj(dogodek, e, a, 3, 0);
        odigraj(dogodek, f, a, 3, 0);
        odigraj(dogodek, b, e, 3, 0);
        odigraj(dogodek, b, f, 3, 0);
        odigraj(dogodek, c, e, 3, 0);
        odigraj(dogodek, c, f, 3, 0);
        odigraj(dogodek, d, e, 3, 0);
        odigraj(dogodek, d, f, 3, 0);
        odigraj(dogodek, e, f, 3, 0);

        List<KrogDto> krogi = krogi(dogodek);
        assertEquals(1, krogi.size());
        KrogDto krog = krogi.get(0);

        assertEquals(1, krog.odMesta());
        assertEquals(4, krog.doMesta());
        assertEquals(KrogDto.Merilo.ZMAGE, krog.odlocilo());
        assertEquals(List.of(a.getId(), d.getId(), b.getId(), c.getId()), idji(krog));
        assertEquals(List.of(3, 1, 1, 1), krog.clani().stream().map(KrogDto.Clan::zmage).toList());

        assertNull(krog.clani().get(0).nizi(), "A je razsodila ze prva vrstica");
        assertEquals(new KrogDto.Izkupicek(4, 3), krog.clani().get(1).nizi());
        assertEquals(new KrogDto.Izkupicek(3, 3), krog.clani().get(2).nizi());
        assertEquals(new KrogDto.Izkupicek(3, 4), krog.clani().get(3).nizi());
        assertEquals(6, krog.tekme().size(), "vse tekme med stirimi clani");
    }

    /* Krog petih, kjer ima vsak po 2 zmagi; nizi locijo vse razen dveh, tista
       dva pa razsodi njuna tekma (ta se izpise kot izid in ne kot zmage 1 : 0). */
    @Test
    void krogPetihDvojicoPoNizihRazsodiNjunaTekma() {
        Dogodek dogodek = pripraviSkupinskiDogodek(5, 1, 5);
        zrebStoritev.izvediZreb(dogodek.getId());
        List<Prijava> clani = clani(dogodek);
        Prijava a = clani.get(0);
        Prijava b = clani.get(1);
        Prijava c = clani.get(2);
        Prijava d = clani.get(3);
        Prijava e = clani.get(4);

        odigraj(dogodek, a, b, 3, 0);
        odigraj(dogodek, a, c, 3, 0);
        odigraj(dogodek, d, a, 3, 2);
        odigraj(dogodek, e, a, 3, 2);
        odigraj(dogodek, b, c, 3, 2);
        odigraj(dogodek, b, d, 3, 1);
        odigraj(dogodek, c, d, 3, 1);
        odigraj(dogodek, c, e, 3, 2);
        odigraj(dogodek, d, e, 3, 0);
        odigraj(dogodek, e, b, 3, 2);

        KrogDto krog = krogi(dogodek).get(0);

        assertEquals(KrogDto.Merilo.NIZI, krog.odlocilo());
        assertEquals(2, krog.enakeZmage());
        assertEquals(List.of(a.getId(), d.getId(), b.getId(), c.getId(), e.getId()), idji(krog));
        assertEquals(List.of(new KrogDto.Izkupicek(10, 6), new KrogDto.Izkupicek(8, 8),
                        new KrogDto.Izkupicek(8, 9), new KrogDto.Izkupicek(8, 9), new KrogDto.Izkupicek(8, 10)),
                krog.clani().stream().map(KrogDto.Clan::nizi).toList());

        assertTrue(krog.clani().get(0).medNjimi().isEmpty());
        assertEquals(List.of(new KrogDto.Korak(KrogDto.Merilo.TEKMA, 3, 2, null)), krog.clani().get(2).medNjimi());
        assertEquals(List.of(new KrogDto.Korak(KrogDto.Merilo.TEKMA, 2, 3, null)), krog.clani().get(3).medNjimi());
    }

    /* Vse je enako in tock ni: oznaka in izracun morata vseeno biti, sicer je
       vrstni red najmanj pojasnjen prav tam, kjer je najmanj razviden. */
    @Test
    void povsemIzenaceniDobijoOpomboOZadnjemMerilu() {
        Dogodek dogodek = pripraviSkupinskiDogodek(3, 1, 3);
        zrebStoritev.izvediZreb(dogodek.getId());
        List<Prijava> clani = clani(dogodek);
        Prijava a = clani.get(0);
        Prijava b = clani.get(1);
        Prijava c = clani.get(2);

        odigraj(dogodek, a, b, 3, 0);
        odigraj(dogodek, b, c, 3, 0);
        odigraj(dogodek, c, a, 3, 0);

        KrogDto krog = krogi(dogodek).get(0);

        assertNull(krog.odlocilo());
        assertEquals(1, krog.enakeZmage());
        assertTrue(krog.tockeManjkajo());
        assertTrue(krog.clani().stream().allMatch(clan -> clan.nizi().equals(new KrogDto.Izkupicek(3, 3))));
        assertTrue(krog.clani().stream().allMatch(clan -> clan.tocke() == null), "ni vpisanih tock");
        assertEquals(1, krog.opombe().size());
        KrogDto.Opomba opomba = krog.opombe().get(0);
        assertFalse(opomba.brezTekem());
        assertEquals(1, opomba.odlocitve().size());
        assertEquals(KrogDto.ZadnjeMerilo.JAKOSTNO_MESTO, opomba.odlocitve().get(0).merilo());
        assertEquals(List.of(1, 2, 3), opomba.odlocitve().get(0).vrednosti().stream()
                .map(KrogDto.Vrednost::vrednost).toList());

        assertEquals(List.of(1, 1, 1), razvrstitevStoritev.lestvica(clani(dogodek), tekmeDogodka(dogodek.getId()))
                .stream().map(VrsticaLestviceDto::krog).toList(), "tudi brez razreza je to krog");
    }

    /* Tekma brez vpisanih tock pri merilu tock odpade; to mora izracun povedati. */
    @Test
    void delnoVpisaneTockeSoOznacene() {
        Dogodek dogodek = pripraviSkupinskiDogodek(3, 1, 3);
        zrebStoritev.izvediZreb(dogodek.getId());
        List<Prijava> clani = clani(dogodek);
        Prijava a = clani.get(0);
        Prijava b = clani.get(1);
        Prijava c = clani.get(2);

        odigraj(dogodek, a, b, 3, 0, nizi(11, 5, 11, 5, 11, 5));
        odigraj(dogodek, b, c, 3, 0);
        odigraj(dogodek, c, a, 3, 0);

        KrogDto krog = krogi(dogodek).get(0);

        assertEquals(KrogDto.Merilo.TOCKE, krog.odlocilo());
        assertTrue(krog.tockeManjkajo());
        assertEquals(List.of(a.getId(), b.getId()), krog.clani().stream()
                .filter(clan -> clan.tocke().za() + clan.tocke().proti() > 0)
                .map(KrogDto.Clan::idPrijave).toList());
    }

    /* Sredi skupine: izenaceni, ki se se niso srecali, kroga nimajo; ko se
       srecajo, je krog viden in izracun pove, da se lahko spremeni. */
    @Test
    void srediIgranjaJeKrogSeleZMedsebojnimiTekmami() {
        Dogodek dogodek = pripraviSkupinskiDogodek(4, 1, 4);
        zrebStoritev.izvediZreb(dogodek.getId());
        List<Prijava> clani = clani(dogodek);
        Prijava a = clani.get(0);
        Prijava b = clani.get(1);
        Prijava c = clani.get(2);
        Prijava d = clani.get(3);

        assertTrue(krogi(dogodek).isEmpty(), "pred prvo tekmo kroga ni");

        odigraj(dogodek, a, d, 3, 0);
        odigraj(dogodek, b, c, 3, 0);
        assertTrue(krogi(dogodek).isEmpty(), "izenacena se se nista srecala");

        odigraj(dogodek, a, b, 3, 0);
        odigraj(dogodek, c, d, 3, 0);
        odigraj(dogodek, d, b, 3, 0);
        KrogDto krog = krogi(dogodek).get(0);
        assertEquals(List.of(d.getId(), b.getId()), idji(krog), "B in D sta izenacena, D je premagal B");
        assertFalse(krog.vseTekmeOdigrane(), "A-C se ni odigrana");

        odigraj(dogodek, a, c, 3, 0);
        krog = krogi(dogodek).get(0);
        assertEquals(3, krog.clani().size(), "po zadnji tekmi je v krogu se C");
        assertTrue(krog.vseTekmeOdigrane());
    }

    /* Predana tekma: zmagovalec ima lahko manj nizov, zato izid gre zraven. */
    @Test
    void predanaTekmaKrogaNosiIzid() {
        Dogodek dogodek = pripraviSkupinskiDogodek(4, 1, 4);
        zrebStoritev.izvediZreb(dogodek.getId());
        List<Prijava> clani = clani(dogodek);
        Prijava a = clani.get(0);
        Prijava b = clani.get(1);

        Tekma tekma = tekmeDogodka(dogodek.getId()).stream()
                .filter(t -> jeUdelezenec(t, a.getId()) && jeUdelezenec(t, b.getId()))
                .findFirst().orElseThrow();
        boolean aJePrvi = tekma.getPrijava1().getId().equals(a.getId());
        tekmaStoritev.vnesiRezultat(tekma.getId(), new VnosRezultata(
                IzidTekme.PREDAJA, aJePrvi ? 1 : 2, aJePrvi ? 2 : 1, aJePrvi ? 1 : 2, null));

        // A, B in C so izenaceni sele, ko krog zaprejo se dve tekmi
        Prijava c = clani.get(2);
        odigraj(dogodek, c, a, 3, 0);
        odigraj(dogodek, b, c, 3, 0);

        KrogDto krog = krogi(dogodek).get(0);
        assertEquals(1, krog.tekme().stream().filter(t -> t.izid() == IzidTekme.PREDAJA).count());
        assertEquals(2, krog.tekme().stream().filter(t -> t.izid() == null).count(),
                "navadna tekma oznake ne potrebuje");
    }

    // ------------------------------------------------------------------

    private List<KrogDto> krogi(Dogodek dogodek) {
        return razvrstitevStoritev.razvrsti(clani(dogodek), tekmeDogodka(dogodek.getId())).krogi();
    }

    private static List<Long> idji(KrogDto krog) {
        return krog.clani().stream().map(KrogDto.Clan::idPrijave).toList();
    }

    private static int zmagovalcevihNizov(KrogDto.TekmaKroga tekma) {
        return tekma.prviZmagal() ? tekma.nizi1() : tekma.nizi2();
    }

    private static int porazencevihNizov(KrogDto.TekmaKroga tekma) {
        return tekma.prviZmagal() ? tekma.nizi2() : tekma.nizi1();
    }

    /* Clani edine skupine po jakostnem mestu z zreba. */
    private List<Prijava> clani(Dogodek dogodek) {
        return prijavaRepozitorij.najdiZaDogodek(dogodek.getId()).stream()
                .filter(p -> p.getIdSkupina() != null)
                .sorted(Comparator.comparing(Prijava::getStNosilca))
                .toList();
    }

    private void odigraj(Dogodek dogodek, Prijava zmagovalec, Prijava porazenec,
                         int niziZmagovalca, int niziPorazenca) {
        odigraj(dogodek, zmagovalec, porazenec, niziZmagovalca, niziPorazenca, null);
    }

    /* Vnese rezultat ne glede na to, na kateri strani tekme kdo stoji.
       Nizi so podani z zmagovalcevega zornega kota. */
    private void odigraj(Dogodek dogodek, Prijava zmagovalec, Prijava porazenec,
                         int niziZmagovalca, int niziPorazenca, List<NizVnos> nizi) {
        Tekma tekma = tekmeDogodka(dogodek.getId()).stream()
                .filter(t -> jeUdelezenec(t, zmagovalec.getId())
                        && jeUdelezenec(t, porazenec.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("tekme med izbranima igralcema ni"));

        boolean zmagovalecJePrvi = tekma.getPrijava1().getId().equals(zmagovalec.getId());
        List<NizVnos> zaVnos = nizi == null || zmagovalecJePrvi ? nizi
                : nizi.stream()
                        .map(n -> new NizVnos(n.tocke2(), n.tocke1()))
                        .toList();
        tekmaStoritev.vnesiRezultat(tekma.getId(), new VnosRezultata(null,
                zmagovalecJePrvi ? niziZmagovalca : niziPorazenca,
                zmagovalecJePrvi ? niziPorazenca : niziZmagovalca,
                null, zaVnos));
    }

    /* Tocke nizov po parih, z zmagovalcevega zornega kota: 11, 0, 11, 0 ... */
    private static List<NizVnos> nizi(int... tocke) {
        return java.util.stream.IntStream.range(0, tocke.length / 2)
                .mapToObj(i -> new NizVnos(tocke[2 * i], tocke[2 * i + 1]))
                .toList();
    }

    private static boolean jeUdelezenec(Tekma tekma, Long idPrijave) {
        return (tekma.getPrijava1() != null && tekma.getPrijava1().getId().equals(idPrijave))
                || (tekma.getPrijava2() != null && tekma.getPrijava2().getId().equals(idPrijave));
    }
}
