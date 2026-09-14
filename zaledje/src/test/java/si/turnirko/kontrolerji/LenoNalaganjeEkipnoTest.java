/* Regresija lenega nalaganja za ekipni turnir (V28).

   Ekipna prijava nosi ekipo namesto igralca, ekipna tekma pa svoje srecanje -
   obe povezavi bere DTO mreze, zreba in seznama dogodkov, ki nastane v
   kontrolerju IZVEN transakcije. Razred zato namenoma ni @Transactional (glej
   LenoNalaganjeTest) in za seboj pocisti sam, po vrstnem redu tujih kljucev. */
package si.turnirko.kontrolerji;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import si.turnirko.dto.DogodekDto;
import si.turnirko.dto.DogodekVnos;
import si.turnirko.dto.EkipaDto;
import si.turnirko.dto.EkipaVnos;
import si.turnirko.dto.KaderVnos;
import si.turnirko.dto.MrezaDto;
import si.turnirko.dto.PostavaVnos;
import si.turnirko.dto.SrecanjePodrobnoDto;
import si.turnirko.dto.TekmaDto;
import si.turnirko.dto.TekmaSrecanjaDto;
import si.turnirko.dto.VnosRezultataSrecanja;
import si.turnirko.modeli.Disciplina;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Klub;
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.modeli.SistemTekmovanja;
import si.turnirko.modeli.Spol;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.StatusSrecanja;
import si.turnirko.modeli.StranEkipe;
import si.turnirko.modeli.Turnir;
import si.turnirko.repozitoriji.IgralecRepozitorij;
import si.turnirko.repozitoriji.KlubRepozitorij;
import si.turnirko.repozitoriji.TurnirRepozitorij;
import si.turnirko.storitve.TurnirjiStoritev;

@SpringBootTest
@ActiveProfiles("test")
class LenoNalaganjeEkipnoTest {

    @Autowired DogodkiKontroler dogodkiKontroler;
    @Autowired TurnirjiKontroler turnirjiKontroler;
    @Autowired SrecanjaKontroler srecanjaKontroler;
    @Autowired LigeKontroler ligeKontroler;
    @Autowired StatistikaKontroler statistikaKontroler;
    @Autowired ProfiliKontroler profiliKontroler;
    @Autowired KoledarKontroler koledarKontroler;
    @Autowired DomovKontroler domovKontroler;
    @Autowired TurnirjiStoritev turnirjiStoritev;
    @Autowired TurnirRepozitorij turnirRepozitorij;
    @Autowired KlubRepozitorij klubRepozitorij;
    @Autowired IgralecRepozitorij igralecRepozitorij;
    @Autowired JdbcTemplate jdbc;

    private Turnir turnir;
    private Dogodek dogodek;

    @BeforeEach
    void pripravi() {
        Turnir t = new Turnir();
        t.setIme("Ekipni turnir brez seje");
        t.setDatumZacetka(LocalDate.of(2026, 9, 12));
        t.setRaven(RavenTekmovanja.URADNO);
        turnir = turnirRepozitorij.save(t);
        dogodek = turnirjiStoritev.dodajDogodek(turnir.getId(), new DogodekVnos("U15 ekipno",
                SpolKategorija.MOSKI, "U15", 5, null, null, Disciplina.EKIPNO, SistemTekmovanja.IZLOCILNI,
                null, null, FormatSrecanja.POKAL_NTZS, null, null));

        // ekipe in kadri prek kontrolerja - tudi ta pot vraca DTO
        for (int i = 1; i <= 4; i++) {
            Klub klub = klubRepozitorij.save(new Klub("Klub " + i, "K" + i));
            EkipaDto ekipa = dogodkiKontroler.dodajEkipo(dogodek.getId(), new EkipaVnos(klub.getId(), 1, null));
            for (int j = 1; j <= 3; j++) {
                Igralec ig = new Igralec();
                ig.setIme("Ig" + i + j);
                ig.setPriimek("Ekipni");
                ig.setSpol(Spol.MOSKI);
                ig.setDatumRojstva(LocalDate.of(2012, 1, 1));
                ig.setKlub(klub);
                dogodkiKontroler.dodajVKader(ekipa.id(), new KaderVnos(igralecRepozitorij.save(ig).getId(), j));
            }
        }
    }

    @AfterEach
    void pocisti() {
        // tekma se sklicuje sama nase (izvor, prenesena), zato najprej povezave
        for (String sql : List.of(
                "DELETE FROM niz_srecanja", "DELETE FROM rating_zgodovina", "DELETE FROM rating_stanje",
                "DELETE FROM postava_srecanja", "DELETE FROM tekma_srecanja", "DELETE FROM srecanje",
                "DELETE FROM niz",
                "UPDATE tekma SET id_izvor_tekma_1 = NULL, id_izvor_tekma_2 = NULL, id_prenesena = NULL",
                "DELETE FROM tekma", "DELETE FROM prijava", "DELETE FROM kader_ekipe", "DELETE FROM ekipa",
                "DELETE FROM skupina", "DELETE FROM dogodek", "DELETE FROM turnir", "DELETE FROM igralec",
                "DELETE FROM klub")) {
            jdbc.update(sql);
        }
    }

    @Test
    void zrebInMrezaVrneteEkipe() {
        List<TekmaDto> zreb = dogodkiKontroler.zreb(dogodek.getId());
        assertFalse(zreb.isEmpty());

        MrezaDto mreza = dogodkiKontroler.mreza(dogodek.getId());
        assertEquals(4, mreza.prijave().size());
        assertTrue(mreza.prijave().stream().allMatch(p -> p.polnoIme() != null && p.polnoIme().startsWith("Klub")));
        List<TekmaDto> polfinala = mreza.tekme().stream().filter(t -> t.kolo() == 1).toList();
        assertEquals(2, polfinala.size());
        assertTrue(polfinala.stream().allMatch(t -> t.idSrecanje() != null), "ekipna tekma vodi na zapisnik");
        assertTrue(polfinala.stream()
                .flatMap(t -> Stream.of(t.udelezenec1(), t.udelezenec2()))
                .filter(Objects::nonNull)
                .allMatch(u -> u.polnoIme().startsWith("Klub")));
    }

    @Test
    void seznamDogodkovTurnirjaInEkipe() {
        List<DogodekDto> dogodki = turnirjiKontroler.dogodki(turnir.getId());
        assertEquals(1, dogodki.size());
        assertEquals(Disciplina.EKIPNO, dogodki.get(0).disciplina());
        assertEquals(FormatSrecanja.POKAL_NTZS, dogodki.get(0).formatSrecanja());

        List<EkipaDto> ekipe = dogodkiKontroler.ekipe(dogodek.getId());
        assertEquals(4, ekipe.size());
        assertEquals(3, dogodkiKontroler.kader(ekipe.get(0).id()).size());
    }

    /* Turnirsko srecanje zivi v isti tabeli kot ligasko, a nima lige. Javni
       pogledi, ki srecanja in kadre stejejo po ligah (seznam lig, lestvica,
       koledar, domaca stran), ga morajo izpustiti, profil in dvoboj pa zajeti. */
    @Test
    void javniPoglediPrenesejoOdigranoTurnirskoSrecanje() {
        dogodkiKontroler.zreb(dogodek.getId());
        Long idSrecanje = dogodkiKontroler.mreza(dogodek.getId()).tekme().stream()
                .map(TekmaDto::idSrecanje).filter(Objects::nonNull).findFirst().orElseThrow();
        SrecanjePodrobnoDto p = srecanjaKontroler.podrobno(idSrecanje);
        List<PostavaVnos.MestoVnos> mesta = new ArrayList<>();
        for (int i = 0; i < p.pozicijeDomaci().size(); i++) {
            mesta.add(new PostavaVnos.MestoVnos(StranEkipe.DOMACI, p.pozicijeDomaci().get(i),
                    p.kaderDomaci().get(i).idIgralec(), false));
        }
        for (int i = 0; i < p.pozicijeGost().size(); i++) {
            mesta.add(new PostavaVnos.MestoVnos(StranEkipe.GOST, p.pozicijeGost().get(i),
                    p.kaderGost().get(i).idIgralec(), false));
        }
        srecanjaKontroler.nastaviPostavo(idSrecanje, new PostavaVnos(mesta));
        for (TekmaSrecanjaDto t : srecanjaKontroler.podrobno(idSrecanje).tekme()) {
            if (srecanjaKontroler.podrobno(idSrecanje).srecanje().status() == StatusSrecanja.KONCANO) {
                break;
            }
            srecanjaKontroler.vnesiRezultat(t.id(), new VnosRezultataSrecanja(null, 3, 1, null, null));
        }
        assertEquals(StatusSrecanja.KONCANO, srecanjaKontroler.podrobno(idSrecanje).srecanje().status());

        // POKAL_NTZS se konca 3 : 0 po A-Y, B-X, C-Z: dvoboj A proti Y je odigran
        Long domaci = p.kaderDomaci().get(0).idIgralec();
        Long gost = p.kaderGost().get(1).idIgralec();
        assertTrue(ligeKontroler.seznam().isEmpty());
        assertFalse(statistikaKontroler.lestvica().isEmpty());
        assertEquals(1, statistikaKontroler.dvoboj(domaci, gost).tekme().size());
        assertFalse(profiliKontroler.profil(domaci).tekme().isEmpty());
        assertEquals(1, koledarKontroler.vObdobju(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)).size());
        assertTrue(domovKontroler.lige(null, null).isEmpty());
        assertNotNull(turnirjiKontroler.statistika(turnir.getId()));
        assertNotNull(turnirjiKontroler.najdi(turnir.getId()));
    }

    @Test
    void zapisnikTurnirskegaSrecanja() {
        dogodkiKontroler.zreb(dogodek.getId());
        Long idSrecanje = dogodkiKontroler.mreza(dogodek.getId()).tekme().stream()
                .map(TekmaDto::idSrecanje).filter(Objects::nonNull).findFirst().orElseThrow();

        SrecanjePodrobnoDto zapisnik = srecanjaKontroler.podrobno(idSrecanje);
        assertNotNull(zapisnik.kontekst());
        assertEquals("Ekipni turnir brez seje", zapisnik.kontekst().tekmovanje());
        assertEquals(3, zapisnik.kaderDomaci().size());
        assertEquals(3, zapisnik.kaderGost().size());
    }
}
