/* Regresijski test za leno nalaganje pri sistemu SV_REGIJA: celotna slika dogodka
   (GET /dogodki/{id}) se pretvori v DTO izven transakcije, zato mora nivoje,
   skupine, zrebe in razporeditev v mrezi prebrati brez dostopa do lenih
   povezav. Razred NAMENOMA ni @Transactional (glej LenoNalaganjeTest) in po
   sebi pocisti. */
package si.turnirko.kontrolerji;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import si.turnirko.dto.DogodekVnos;
import si.turnirko.dto.MrezaDto;
import si.turnirko.dto.SvPredlogDto;
import si.turnirko.dto.TurnirVnos;
import si.turnirko.dto.VnosRezultata;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.SistemTekmovanja;
import si.turnirko.modeli.Spol;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.Tekma;
import si.turnirko.modeli.Turnir;
import si.turnirko.repozitoriji.DogodekRepozitorij;
import si.turnirko.repozitoriji.IgralecRepozitorij;
import si.turnirko.repozitoriji.NizRepozitorij;
import si.turnirko.repozitoriji.PrijavaRepozitorij;
import si.turnirko.repozitoriji.RatingStanjeRepozitorij;
import si.turnirko.repozitoriji.RatingZgodovinaRepozitorij;
import si.turnirko.repozitoriji.SkupinaRepozitorij;
import si.turnirko.repozitoriji.TekmaRepozitorij;
import si.turnirko.repozitoriji.TurnirRepozitorij;
import si.turnirko.repozitoriji.ZrebRepozitorij;
import si.turnirko.storitve.TekmaStoritev;
import si.turnirko.storitve.TurnirjiStoritev;

@SpringBootTest
@ActiveProfiles("test")
class LenoNalaganjeSvRegijaTest {

    @Autowired DogodkiKontroler dogodkiKontroler;
    @Autowired SvRegijaKontroler svRegijaKontroler;
    @Autowired TurnirjiStoritev turnirjiStoritev;
    @Autowired TekmaStoritev tekmaStoritev;

    @Autowired IgralecRepozitorij igralecRepozitorij;
    @Autowired TurnirRepozitorij turnirRepozitorij;
    @Autowired DogodekRepozitorij dogodekRepozitorij;
    @Autowired PrijavaRepozitorij prijavaRepozitorij;
    @Autowired TekmaRepozitorij tekmaRepozitorij;
    @Autowired SkupinaRepozitorij skupinaRepozitorij;
    @Autowired ZrebRepozitorij zrebRepozitorij;
    @Autowired NizRepozitorij nizRepozitorij;
    @Autowired RatingStanjeRepozitorij ratingStanjeRepozitorij;
    @Autowired RatingZgodovinaRepozitorij ratingZgodovinaRepozitorij;

    private Turnir turnir;
    private Dogodek dogodek;

    @BeforeEach
    void pripravi() {
        turnir = turnirjiStoritev.ustvari(
                new TurnirVnos("Turnir SV regije", null, null, null, null, null, null));
        dogodek = turnirjiStoritev.dodajDogodek(turnir.getId(), new DogodekVnos(
                "Clani SV", SpolKategorija.KDORKOLI, null, 5, null, null, null,
                SistemTekmovanja.SV_REGIJA, null, null));
        List<Long> igralci = IntStream.rangeClosed(1, 8)
                .mapToObj(i -> novIgralec("Igralec" + i, "Testni" + i).getId())
                .toList();
        dogodkiKontroler.prijavi(dogodek.getId(),
                new si.turnirko.dto.PrijaviIgralceVnos(igralci));
    }

    @AfterEach
    void pocisti() {
        nizRepozitorij.deleteAll();
        ratingZgodovinaRepozitorij.deleteAll();
        ratingStanjeRepozitorij.deleteAll();
        // povezave med tekmami zrebov najprej pretrgamo, sicer tuji kljuc ustavi brisanje
        List<Tekma> tekme = tekmaRepozitorij.findAll();
        tekme.forEach(t -> {
            t.setIdIzvorTekma1(null);
            t.setIdIzvorTekma2(null);
            t.setVlogaIzvora1(null);
            t.setVlogaIzvora2(null);
        });
        tekmaRepozitorij.saveAllAndFlush(tekme);
        tekmaRepozitorij.deleteAll();
        List<si.turnirko.modeli.Prijava> prijave = prijavaRepozitorij.findAll();
        prijave.forEach(p -> p.setIdSkupina(null));
        prijavaRepozitorij.saveAllAndFlush(prijave);
        zrebRepozitorij.deleteAll();
        skupinaRepozitorij.deleteAll();
        prijavaRepozitorij.deleteAll();
        dogodekRepozitorij.deleteAll();
        turnirRepozitorij.deleteAll();
        igralecRepozitorij.deleteAll();
    }

    @Test
    void predZrebomSlikaDogodkaNosiPredogledNivojev() {
        MrezaDto slika = dogodkiKontroler.mreza(dogodek.getId());

        assertNotNull(slika.svRegija());
        assertFalse(slika.svRegija().skupineZrebane());
        assertEquals(1, slika.svRegija().nivoji().size());
        assertEquals(8, slika.svRegija().nivoji().get(0).velikost());
        assertEquals(List.of(4, 4), slika.svRegija().nivoji().get(0).velikostiSkupin());
        assertNull(slika.svRegija().zadrzek());
        // jakostni vrstni red nosi predogled nivojev (oznaka N1)
        assertEquals("N1", slika.izbor().skupine().get(0).oznaka());
    }

    @Test
    void predlogSkupinInZrebPoSlikiBrezLenihNapak() {
        SvPredlogDto predlog = svRegijaKontroler.predlog(dogodek.getId());
        assertEquals(1, predlog.nivoji().size());
        assertEquals(2, predlog.nivoji().get(0).skupine().size());

        dogodkiKontroler.zreb(dogodek.getId());
        MrezaDto slika = dogodkiKontroler.mreza(dogodek.getId());
        assertTrue(slika.svRegija().skupineZrebane());
        assertTrue(slika.svRegija().skupineUredljive());
        assertEquals(2, slika.skupine().size());
        assertEquals(1, slika.skupine().get(0).nivo());

        // odigraj skupine: nastaneta glavni in tolazilni zreb z razporeditvijo mest
        List<Tekma> skupinske = tekmaRepozitorij.najdiZaDogodek(dogodek.getId());
        for (Tekma t : skupinske) {
            if (t.getStatus() == StatusTekme.PRIPRAVLJENA) {
                tekmaStoritev.vnesiRezultat(t.getId(), new VnosRezultata(null, 3, 0, null, null));
            }
        }
        MrezaDto po = dogodkiKontroler.mreza(dogodek.getId());
        var zrebi = po.svRegija().nivoji().get(0).zrebi();
        assertEquals(2, zrebi.size());
        for (var zreb : zrebi) {
            assertTrue(zreb.zgrajen());
            assertEquals(4, zreb.stUdelezencev());
            assertEquals(4, zreb.razpored().size(), "razpored mest po mrezi");
            assertTrue(zreb.razpored().stream().allMatch(java.util.Objects::nonNull));
        }
        assertFalse(po.svRegija().skupineUredljive(), "po prvi odigrani tekmi skupin ni vec mogoce urejati");
        assertTrue(po.tekme().stream().anyMatch(t -> t.idZreb() != null && t.razponOd() != null));
    }

    private Igralec novIgralec(String ime, String priimek) {
        Igralec igralec = new Igralec();
        igralec.setIme(ime);
        igralec.setPriimek(priimek);
        igralec.setSpol(Spol.MOSKI);
        igralec.setDatumRojstva(java.time.LocalDate.of(1995, 1, 1));
        return igralecRepozitorij.save(igralec);
    }
}
