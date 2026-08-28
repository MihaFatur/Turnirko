/* Testi profila igralca: zdruzevanje turnirskih in ligaskih tekem, graf ELO,
   razclenitev po igralni roki nasprotnika ter dostop do zasebnega dela. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import si.turnirko.dto.PotrditevRacunaVnos;
import si.turnirko.dto.ProfilDto;
import si.turnirko.dto.ProfilZasebnoDto;
import si.turnirko.dto.RacunIgralcaDto;
import si.turnirko.dto.RegistracijaVnos;
import si.turnirko.dto.SpremembaGeslaVnos;
import si.turnirko.dto.VnosRezultata;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.PrepovedanoIzjema;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.IgralnaRoka;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.Tekma;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

class ProfilStoritevTest extends IntegracijskiTest {

    @Autowired private ProfilStoritev profilStoritev;
    @Autowired private RacuniStoritev racuniStoritev;
    @Autowired private UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired private PasswordEncoder kodirnik;

    @BeforeEach
    void deterministicniZreb() {
        zrebStoritev.nastaviNakljucje(new Random(42));
    }

    /* Odigra eno turnirsko tekmo in vrne jo, da testi vedo, kdo je kdo. */
    private Tekma odigrajEnoTekmo(int nizi1, int nizi2) {
        Dogodek dogodek = pripraviDogodek(4);
        zrebStoritev.izvediZreb(dogodek.getId());
        Tekma tekma = tekmeDogodka(dogodek.getId()).stream()
                .filter(t -> t.getStatus() == StatusTekme.PRIPRAVLJENA)
                .findFirst().orElseThrow();
        tekmaStoritev.vnesiRezultat(tekma.getId(), new VnosRezultata(null, nizi1, nizi2, null, null));
        return tekmeDogodka(dogodek.getId()).stream()
                .filter(t -> t.getId().equals(tekma.getId())).findFirst().orElseThrow();
    }

    @Test
    void profilPovzameOdigraneTekmeInGrafElo() {
        Tekma tekma = odigrajEnoTekmo(3, 1);
        Long idZmagovalca = tekma.getPrijava1().getIgralec().getId();
        Long idPorazenca = tekma.getPrijava2().getIgralec().getId();

        ProfilDto profil = profilStoritev.profil(idZmagovalca);
        assertEquals(1, profil.pregled().odigrane());
        assertEquals(1, profil.pregled().zmage());
        assertEquals(0, profil.pregled().porazi());
        assertEquals(100, profil.pregled().odstotekZmag());
        assertEquals(3, profil.pregled().dobljeniNizi());
        assertEquals(1, profil.pregled().prejetiNizi());
        assertEquals(1, profil.pregled().turnirskih());
        assertEquals(0, profil.pregled().ligaskih());

        assertEquals(1, profil.tekme().size());
        ProfilDto.TekmaProfila t = profil.tekme().get(0);
        assertEquals(idPorazenca, t.idNasprotnika());
        assertTrue(t.zmaga());
        assertEquals(3, t.niziZa());
        assertEquals(1, t.niziProti());

        // graf ELO: ena tocka, sprememba je pozitivna za zmagovalca
        assertEquals(1, profil.graf().size());
        assertTrue(profil.graf().get(0).sprememba() > 0);
        assertEquals(profil.glava().rating(), profil.graf().get(0).vrednost());

        // nasprotnik ima zrcalno sliko
        ProfilDto porazenec = profilStoritev.profil(idPorazenca);
        assertEquals(1, porazenec.pregled().porazi());
        assertEquals(0, porazenec.pregled().odstotekZmag());
        assertTrue(porazenec.graf().get(0).sprememba() < 0);
    }

    /* Vmesnik s tocke grafa skoci na vrstico iste tekme v seznamu spodaj. Ta
       pot stoji na tem, da tocka nosi ISTI par (idTekme, ligaska) in isti
       zapis tekmovanja kot vrstica - sicer skok cilja ne najde, gledalec pa
       ob tocki ne izve, na katerem turnirju oziroma v kateri ligi je bila
       tekma odigrana. */
    @Test
    void tockaGrafaKazeNaIstoTekmoKotVrsticaSeznama() {
        Tekma tekma = odigrajEnoTekmo(3, 1);
        ProfilDto profil = profilStoritev.profil(tekma.getPrijava1().getIgralec().getId());

        ProfilDto.TekmaProfila vrstica = profil.tekme().get(0);
        ProfilDto.TockaGrafa tocka = profil.graf().get(0);

        assertNotNull(tocka.tekmovanje());
        assertEquals(vrstica.idTekme(), tocka.idTekme());
        assertEquals(vrstica.ligaska(), tocka.ligaska());
        assertEquals(vrstica.tekmovanje(), tocka.tekmovanje());
        assertEquals(vrstica.del(), tocka.del());
        assertEquals(vrstica.nasprotnik(), tocka.nasprotnik());
    }

    @Test
    void uvrstitevIzracunaMestoInPercentil() {
        Tekma tekma = odigrajEnoTekmo(3, 0);
        ProfilDto zmagovalec = profilStoritev.profil(tekma.getPrijava1().getIgralec().getId());

        assertEquals(1, zmagovalec.uvrstitev().mesto(), "zmagovalec ima najvisji rating");
        assertEquals(2, zmagovalec.uvrstitev().skupajIgralcev(), "rating imata le igralca te tekme");
        assertEquals(100, zmagovalec.uvrstitev().percentil());
    }

    @Test
    void zasebnaStatistikaLociNasprotnikePoRoki() {
        Tekma tekma = odigrajEnoTekmo(3, 1);
        Igralec zmagovalec = tekma.getPrijava1().getIgralec();
        Igralec porazenec = tekma.getPrijava2().getIgralec();
        porazenec.setIgralnaRoka(IgralnaRoka.LEVA);
        igralecRepozitorij.save(porazenec);

        ProfilZasebnoDto z = profilStoritev.zasebno(zmagovalec.getId(), adminIme());
        assertEquals(1, z.nasprotniki().protiLevicarjem().odigrane());
        assertEquals(1, z.nasprotniki().protiLevicarjem().zmage());
        assertEquals(100, z.nasprotniki().protiLevicarjem().odstotek());
        assertEquals(0, z.nasprotniki().protiDesnicarjem().odigrane());

        // tekma je bila obracunana v ELO, zato je rating nasprotnika znan
        assertEquals(1, z.nasprotniki().tekemZZnanimRatingom());
        assertNotNull(z.nasprotniki().najpogostejsi());
        assertEquals(porazenec.getId(), z.nasprotniki().najpogostejsi().idIgralec());

        assertEquals(1, z.poTekmovanjih().turnirji().odigrane());
        assertEquals(0, z.poTekmovanjih().lige().odigrane());
        assertEquals(List.of(true), z.forma().zadnjih10());
        assertEquals(1, z.forma().najdaljsiNizZmag());
        assertEquals(4, z.niziInTocke().dobljeniNizi() + z.niziInTocke().prejetiNizi());
    }

    /* Trije razrezi, ki jih uporablja prenovljena stran profila: razsevni graf
       (rating nasprotnika ob tekmi), mesecni izkupicek proti pricakovanemu in
       tocke v tesnih nizih. */
    @Test
    void zasebnaStatistikaDaPodatkeZaGrafeProfila() {
        Dogodek dogodek = pripraviDogodek(4);
        zrebStoritev.izvediZreb(dogodek.getId());
        Tekma tekma = tekmeDogodka(dogodek.getId()).stream()
                .filter(t -> t.getStatus() == StatusTekme.PRIPRAVLJENA)
                .findFirst().orElseThrow();
        // 3:0 z enim tesnim nizom (12:10) - drugi niz gladek, tretji spet tesen
        tekmaStoritev.vnesiRezultat(tekma.getId(), new VnosRezultata(null, 3, 0, null,
                List.of(new VnosRezultata.NizVnos(11, 4),
                        new VnosRezultata.NizVnos(12, 10),
                        new VnosRezultata.NizVnos(11, 9))));
        Igralec zmagovalec = tekma.getPrijava1().getIgralec();
        Igralec porazenec = tekma.getPrijava2().getIgralec();

        ProfilZasebnoDto z = profilStoritev.zasebno(zmagovalec.getId(), adminIme());

        // razsevni graf: ena pika, rating nasprotnika PRED tekmo je zacetni
        assertEquals(1, z.razsevni().size());
        assertEquals(EloStoritev.ZACETNI_RATING, z.razsevni().get(0).ratingNasprotnika());
        assertTrue(z.razsevni().get(0).zmaga());
        assertTrue(z.razsevni().get(0).sprememba() > 0);

        // pri enakem ratingu je pricakovana zmaga natanko pol
        assertEquals(1, z.forma().poMesecih().size());
        assertEquals(1, z.forma().poMesecih().get(0).zmage());
        assertEquals(0.5, z.forma().poMesecih().get(0).pricakovaneZmage(), 0.001);

        // brez izgubljenega niza: edina tekma je bila 3:0
        assertEquals(100, z.niziInTocke().brezIzgubljenegaNiza().odstotek());

        // pod pritiskom: tesna sta 12:10 in 11:9, torej 23 od 42 tock
        assertEquals(2, z.niziInTocke().tocke().nizovPodPritiskom());
        assertEquals(Math.round(23 * 100f / 42),
                z.niziInTocke().tocke().odstotekTockPodPritiskom());

        // nasprotnik vidi zrcalno sliko: poraz in negativna sprememba
        ProfilZasebnoDto zp = profilStoritev.zasebno(porazenec.getId(), adminIme());
        assertEquals(0, zp.forma().poMesecih().get(0).zmage());
        assertTrue(zp.razsevni().get(0).sprememba() < 0);
        assertEquals(0, zp.niziInTocke().brezIzgubljenegaNiza().odstotek());
    }

    @Test
    void zasebnegaProfilaNeVidiTujIgralec() {
        Tekma tekma = odigrajEnoTekmo(3, 1);
        Igralec prvi = tekma.getPrijava1().getIgralec();
        Igralec drugi = tekma.getPrijava2().getIgralec();

        String prijavaPrvega = racunZa(prvi, "prvi@test.si");

        // svojega profila sme videti
        assertNotNull(profilStoritev.zasebno(prvi.getId(), prijavaPrvega));
        // tujega ne
        assertThrows(PrepovedanoIzjema.class,
                () -> profilStoritev.zasebno(drugi.getId(), prijavaPrvega));
    }

    @Test
    void nepotrjenRacunNeVidiZasebnegaProfila() {
        Tekma tekma = odigrajEnoTekmo(3, 1);
        Igralec prvi = tekma.getPrijava1().getIgralec();

        racuniStoritev.registriraj(new RegistracijaVnos(
                prvi.getIme(), prvi.getPriimek(), null, "caka@test.si", "geslo123", false));

        assertThrows(PrepovedanoIzjema.class,
                () -> profilStoritev.zasebno(prvi.getId(), "caka@test.si"));
    }

    @Test
    void registracijaCakaNaPotrditevInSePoveziZIgralcem() {
        Tekma tekma = odigrajEnoTekmo(3, 1);
        Igralec igralec = tekma.getPrijava1().getIgralec();

        var profil = racuniStoritev.registriraj(new RegistracijaVnos(
                igralec.getIme(), igralec.getPriimek(), null, "nov@test.si", "geslo123", false));
        assertEquals(StatusRacuna.CAKA, profil.status());
        assertEquals(Vloga.IGRALEC, profil.vloga());
        assertEquals(null, profil.idIgralec(), "nepotrjen racun se ni povezan z igralcem");

        // ista e-posta se ne more registrirati dvakrat
        assertThrows(DomenskaIzjema.class, () -> racuniStoritev.registriraj(new RegistracijaVnos(
                igralec.getIme(), igralec.getPriimek(), null, "nov@test.si", "geslo123", false)));

        // administrator vidi zahtevo in predlagane igralce (ujemanje po priimku)
        RacunIgralcaDto zahteva = racuniStoritev.racuni().stream()
                .filter(r -> r.email().equals("nov@test.si")).findFirst().orElseThrow();
        assertTrue(zahteva.predlogi().stream()
                .anyMatch(p -> p.idIgralec().equals(igralec.getId())), "igralec mora biti predlagan");

        racuniStoritev.potrdi(zahteva.id(), new PotrditevRacunaVnos(igralec.getId()));
        assertEquals(igralec.getId(), racuniStoritev.profil("nov@test.si").idIgralec());
        assertNotNull(profilStoritev.zasebno(igralec.getId(), "nov@test.si"));
    }

    @Test
    void enemuIgralcuSamoEnRacun() {
        Tekma tekma = odigrajEnoTekmo(3, 1);
        Igralec igralec = tekma.getPrijava1().getIgralec();

        racuniStoritev.registriraj(new RegistracijaVnos(
                igralec.getIme(), igralec.getPriimek(), null, "prvi@test.si", "geslo123", false));
        racuniStoritev.registriraj(new RegistracijaVnos(
                igralec.getIme(), igralec.getPriimek(), null, "drugi@test.si", "geslo123", false));

        List<RacunIgralcaDto> racuni = racuniStoritev.racuni();
        Long idPrvega = racuni.stream().filter(r -> r.email().equals("prvi@test.si"))
                .findFirst().orElseThrow().id();
        Long idDrugega = racuni.stream().filter(r -> r.email().equals("drugi@test.si"))
                .findFirst().orElseThrow().id();

        racuniStoritev.potrdi(idPrvega, new PotrditevRacunaVnos(igralec.getId()));
        assertThrows(DomenskaIzjema.class,
                () -> racuniStoritev.potrdi(idDrugega, new PotrditevRacunaVnos(igralec.getId())));
    }

    /* Zavrnjen racun sicer za vedno zasede svojo e-posto; izbris jo sprosti. */
    @Test
    void izbrisRacunaSprostiEposto() {
        racuniStoritev.registriraj(new RegistracijaVnos(
                "Ana", "Novak", null, "ana@test.si", "geslo123", false));
        Long idRacuna = racuniStoritev.racuni().stream()
                .filter(r -> r.email().equals("ana@test.si")).findFirst().orElseThrow().id();
        racuniStoritev.zavrni(idRacuna);

        // dokler racun obstaja, je e-posta zasedena
        assertThrows(DomenskaIzjema.class, () -> racuniStoritev.registriraj(new RegistracijaVnos(
                "Ana", "Novak", null, "ana@test.si", "geslo123", false)));

        racuniStoritev.zbrisi(idRacuna);
        assertTrue(uporabnikRepozitorij.findByUporabniskoIme("ana@test.si").isEmpty());
        assertNotNull(racuniStoritev.registriraj(new RegistracijaVnos(
                "Ana", "Novak", null, "ana@test.si", "geslo123", false)));
    }

    @Test
    void zamenjavaGeslaZahtevaPravilnoStaro() {
        racuniStoritev.registriraj(new RegistracijaVnos(
                "Ana", "Novak", null, "ana@test.si", "geslo123", false));

        assertThrows(NeveljavenVnosIzjema.class, () -> racuniStoritev.zamenjajGeslo(
                "ana@test.si", new SpremembaGeslaVnos("napacno", "novogeslo1")));

        racuniStoritev.zamenjajGeslo("ana@test.si", new SpremembaGeslaVnos("geslo123", "novogeslo1"));
        Uporabnik u = uporabnikRepozitorij.findByUporabniskoIme("ana@test.si").orElseThrow();
        assertTrue(kodirnik.matches("novogeslo1", u.getGesloHash()));
    }

    /* Administrator nastavi racunu geslo po svoji izbiri (brez starega gesla)
       in v bazo se shrani zgolj zgostitev - nikoli cistopis. */
    @Test
    void adminNastaviGesloRacunu() {
        racuniStoritev.registriraj(new RegistracijaVnos(
                "Ana", "Novak", null, "ana@test.si", "geslo123", false));
        Long idRacuna = racuniStoritev.racuni().stream()
                .filter(r -> r.email().equals("ana@test.si")).findFirst().orElseThrow().id();

        racuniStoritev.nastaviGeslo(idRacuna, "izbranoGeslo1");

        Uporabnik u = uporabnikRepozitorij.findByUporabniskoIme("ana@test.si").orElseThrow();
        assertTrue(kodirnik.matches("izbranoGeslo1", u.getGesloHash()));
        // v bazo gre le zgostitev, ne cistopis
        assertNotEquals("izbranoGeslo1", u.getGesloHash());
    }

    // ---------- pomozne metode ----------

    /* Registrira in potrdi racun za igralca; vrne njegovo prijavno ime. */
    private String racunZa(Igralec igralec, String email) {
        racuniStoritev.registriraj(new RegistracijaVnos(
                igralec.getIme(), igralec.getPriimek(), null, email, "geslo123", false));
        Long idRacuna = racuniStoritev.racuni().stream()
                .filter(r -> r.email().equals(email)).findFirst().orElseThrow().id();
        racuniStoritev.potrdi(idRacuna, new PotrditevRacunaVnos(igralec.getId()));
        return email;
    }

    /* Administrator za preverbo, da sme videti tuj zasebni profil. */
    private String adminIme() {
        return uporabnikRepozitorij.findAll().stream()
                .filter(u -> u.getVloga() == Vloga.ADMIN)
                .map(Uporabnik::getUporabniskoIme)
                .findFirst().orElseThrow();
    }
}
