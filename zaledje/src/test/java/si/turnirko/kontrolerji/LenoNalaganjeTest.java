/* Regresijski testi za leno nalaganje (LazyInitializationException).

   Kontrolerji pretvarjajo entitete v DTO-je IZVEN transakcije, zato mora
   vsaka poizvedba, katere rezultat gre v DTO, vnaprej naloziti povezane
   entitete (kraj, igralca, klub). Ta razred NAMENOMA ni @Transactional:
   napaka se pokaze sele, ko se seja baze konca pred pretvorbo v DTO -
   natanko tako, kot se zgodi na pravem strezniku. Zato testi tudi sami
   pocistijo za seboj (ni samodejne razveljavitve transakcije). */
package si.turnirko.kontrolerji;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import si.turnirko.dto.DogodekVnos;
import si.turnirko.dto.MrezaDto;
import si.turnirko.dto.PrijavaDto;
import si.turnirko.dto.PrijaviIgralceVnos;
import si.turnirko.dto.TekmaDto;
import si.turnirko.dto.TurnirDto;
import si.turnirko.dto.TurnirVnos;
import si.turnirko.dto.VnosRezultata;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.IzidTekme;
import si.turnirko.modeli.Klub;
import si.turnirko.modeli.Kraj;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.Spol;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.Tekma;
import si.turnirko.modeli.Turnir;
import si.turnirko.repozitoriji.DogodekRepozitorij;
import si.turnirko.repozitoriji.IgralecRepozitorij;
import si.turnirko.repozitoriji.KlubRepozitorij;
import si.turnirko.repozitoriji.KrajRepozitorij;
import si.turnirko.repozitoriji.NizRepozitorij;
import si.turnirko.repozitoriji.PrijavaRepozitorij;
import si.turnirko.repozitoriji.RatingStanjeRepozitorij;
import si.turnirko.repozitoriji.RatingZgodovinaRepozitorij;
import si.turnirko.repozitoriji.TekmaRepozitorij;
import si.turnirko.repozitoriji.TurnirRepozitorij;
import si.turnirko.storitve.TurnirjiStoritev;

@SpringBootTest
@ActiveProfiles("test")
class LenoNalaganjeTest {

    @Autowired TurnirjiKontroler turnirjiKontroler;
    @Autowired DogodkiKontroler dogodkiKontroler;
    @Autowired TekmeKontroler tekmeKontroler;
    @Autowired TurnirjiStoritev turnirjiStoritev;

    @Autowired KrajRepozitorij krajRepozitorij;
    @Autowired KlubRepozitorij klubRepozitorij;
    @Autowired IgralecRepozitorij igralecRepozitorij;
    @Autowired TurnirRepozitorij turnirRepozitorij;
    @Autowired DogodekRepozitorij dogodekRepozitorij;
    @Autowired PrijavaRepozitorij prijavaRepozitorij;
    @Autowired TekmaRepozitorij tekmaRepozitorij;
    @Autowired NizRepozitorij nizRepozitorij;
    @Autowired RatingStanjeRepozitorij ratingStanjeRepozitorij;
    @Autowired RatingZgodovinaRepozitorij ratingZgodovinaRepozitorij;

    private Klub klub;
    private Turnir turnir;
    private Dogodek dogodek;
    private List<PrijavaDto> prijave;

    @BeforeEach
    void pripravi() {
        krajRepozitorij.save(new Kraj(9999, "Testni kraj"));
        klub = klubRepozitorij.save(new Klub("Testni klub", "TST"));

        turnir = turnirjiStoritev.ustvari(
                new TurnirVnos("Turnir z lenim krajem", 9999, null, null, null, null));
        dogodek = turnirjiStoritev.dodajDogodek(turnir.getId(),
                new DogodekVnos("Clani", SpolKategorija.MESANO, null, 5, null, null, null, null, null));

        Igralec prvi = novIgralec("Ana", "Prva");
        Igralec drugi = novIgralec("Bor", "Drugi");
        Igralec tretji = novIgralec("Cene", "Tretji");
        // prijava prek kontrolerja - tudi ta pot pretvarja v DTO izven transakcije
        prijave = dogodkiKontroler.prijavi(dogodek.getId(),
                new PrijaviIgralceVnos(List.of(prvi.getId(), drugi.getId(), tretji.getId())));
    }

    @AfterEach
    void pocisti() {
        // vrstni red sledi tujim kljucem (najprej odvisni zapisi):
        // niz in rating_zgodovina se sklicujeta na tekmo, tekme visjih kol
        // na nizja (id_izvor_tekma) - te brisemo od finala proti prvemu kolu
        nizRepozitorij.deleteAll();
        ratingZgodovinaRepozitorij.deleteAll();
        ratingStanjeRepozitorij.deleteAll();
        tekmaRepozitorij.findAll().stream()
                .sorted(Comparator.comparingInt(Tekma::getKolo).reversed())
                .forEach(tekmaRepozitorij::delete);
        prijavaRepozitorij.deleteAll();
        dogodekRepozitorij.deleteAll();
        turnirRepozitorij.deleteAll();
        igralecRepozitorij.deleteAll();
        klubRepozitorij.deleteAll();
        krajRepozitorij.deleteAll();
    }

    @Test
    void seznamTurnirjevVrneTudiKraj() {
        List<TurnirDto> seznam = turnirjiKontroler.seznam();
        assertTrue(seznam.stream().anyMatch(t ->
                t.kraj() != null && "Testni kraj".equals(t.kraj().ime())));
    }

    @Test
    void posamezenTurnirVrneTudiKraj() {
        TurnirDto dto = turnirjiKontroler.najdi(turnir.getId());
        assertEquals("Testni kraj", dto.kraj().ime());
    }

    @Test
    void odjavaVrneIgralcaInKlub() {
        PrijavaDto odjavljena = dogodkiKontroler.odjavi(prijave.get(2).id());
        assertEquals(Prijava.StatusPrijave.ODJAVLJEN, odjavljena.status());
        assertEquals("Tretji Cene", odjavljena.polnoIme());
        assertEquals("Testni klub", odjavljena.klub());
    }

    @Test
    void zrebVrneTekmeZImeniInKlubi() {
        List<TekmaDto> tekme = dogodkiKontroler.zreb(dogodek.getId());
        assertFalse(tekme.isEmpty());
        assertTrue(tekme.stream()
                .flatMap(t -> Stream.of(t.udelezenec1(), t.udelezenec2()))
                .filter(Objects::nonNull)
                .allMatch(u -> "Testni klub".equals(u.klub())));
    }

    @Test
    void mrezaPoZrebuVrneTekmeSKlubi() {
        dogodkiKontroler.zreb(dogodek.getId());
        MrezaDto mreza = dogodkiKontroler.mreza(dogodek.getId());
        assertFalse(mreza.tekme().isEmpty());
        assertTrue(mreza.tekme().stream()
                .flatMap(t -> Stream.of(t.udelezenec1(), t.udelezenec2()))
                .filter(Objects::nonNull)
                .allMatch(u -> "Testni klub".equals(u.klub())));
    }

    @Test
    void vnosRezultataVrneTekmoSKlubi() {
        TekmaDto pripravljena = dogodkiKontroler.zreb(dogodek.getId()).stream()
                .filter(t -> t.status() == StatusTekme.PRIPRAVLJENA)
                .findFirst().orElseThrow();
        TekmaDto koncana = tekmeKontroler.vnesiRezultat(pripravljena.id(),
                new VnosRezultata(IzidTekme.IGRANO, 3, 0, null, null));
        assertEquals(StatusTekme.KONCANA, koncana.status());
        assertEquals("Testni klub", koncana.udelezenec1().klub());
        assertEquals("Testni klub", koncana.udelezenec2().klub());
    }

    private Igralec novIgralec(String ime, String priimek) {
        Igralec igralec = new Igralec();
        igralec.setIme(ime);
        igralec.setPriimek(priimek);
        igralec.setSpol(Spol.MOSKI);
        igralec.setDatumRojstva(LocalDate.of(2000, 1, 1));
        igralec.setKlub(klub);
        return igralecRepozitorij.save(igralec);
    }
}
