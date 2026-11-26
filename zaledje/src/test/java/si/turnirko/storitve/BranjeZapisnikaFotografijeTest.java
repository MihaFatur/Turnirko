/* Ligaski zapisnik od tiska do zapisa s PRAVIM bralnikom: natis iz baze (koda
   QR s skrivnostjo, kader po vrsticah), list narisan po postavitvi natisa,
   izpolnjen (krizci, stevke MNIST) in »fotografiran«, nato sprejem, branje,
   predlog, potrditev in stevke za ucenje.

   Varuje: postavitev, ki jo vrne natis, se po fotografiji prebere (QR, poravnava,
   kvadratki, celice) za obe obliki; predlog pozna prave igralce (id iz kadra
   natisa) in izide; potrjen zapisnik se zapise in da stevke za ucenje (tretji vir
   stevka_listka, V79). */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import si.turnirko.branje.GeometrijaZapisnika;
import si.turnirko.dto.BranjeZapisnikaDto;
import si.turnirko.dto.EkipaVnos;
import si.turnirko.dto.KaderVnos;
import si.turnirko.dto.LigaVnos;
import si.turnirko.dto.NatisZapisnikaDto;
import si.turnirko.dto.NatisZapisnikovVnos;
import si.turnirko.dto.SrecanjeDto;
import si.turnirko.dto.ZapisZapisnikaVnos;
import si.turnirko.modeli.BranjeZapisnika;
import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Klub;
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.StatusSrecanja;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.BranjeZapisnikaRepozitorij;
import si.turnirko.repozitoriji.LigaRepozitorij;
import si.turnirko.repozitoriji.StevkaListkaRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

class BranjeZapisnikaFotografijeTest extends IntegracijskiTest {

    @Autowired BranjeZapisnikovStoritev storitev;
    @Autowired NatisZapisnikovStoritev natisi;
    @Autowired LigaStoritev ligaStoritev;
    @Autowired SrecanjeStoritev srecanjeStoritev;
    @Autowired RatingStoritev ratingStoritev;
    @Autowired BranjeZapisnikaRepozitorij branja;
    @Autowired UporabnikRepozitorij uporabniki;
    @Autowired LigaRepozitorij ligaRepozitorij;
    @Autowired StevkaListkaRepozitorij stevke;

    @BeforeAll
    static void mreza() throws IOException {
        SimulacijaZapisnikaTest.pripravi();
    }

    @BeforeEach
    void pripravi() {
        storitev.nastaviSamoAdmin(false);
    }

    @AfterEach
    void pocisti() {
        storitev.nastaviSamoAdmin(true);
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @EnumSource(GeometrijaZapisnika.Oblika.class)
    void zapisnikSeOdTiskaDoZapisaPrebere(GeometrijaZapisnika.Oblika oblika) throws IOException {
        Uporabnik org = uporabniki.save(racun("org-foto-" + oblika + "@test"));
        String oznaka = oblika.name().substring(0, 3);
        LigaVnos v = new LigaVnos("Liga foto " + oznaka, "2026/27", SpolKategorija.MOSKI, FormatSrecanja.SAVINJA, 5,
                null, false, 2, 1, 0, true, false, RavenTekmovanja.URADNO, false, null, null, null, null, null, null);
        Long liga = ligaStoritev.ustvari(v).id();
        var l = ligaRepozitorij.findById(liga).orElseThrow();
        l.setUstvaril(org);
        ligaRepozitorij.save(l);
        List<List<Igralec>> kadri = new ArrayList<>();
        List<Long> ekipe = new ArrayList<>();
        for (String ime : List.of("Foto A", "Foto B")) {
            Klub klub = klubRepozitorij.save(new Klub(ime + " " + oznaka, null));
            var ekipa = ligaStoritev.dodajEkipo(liga, new EkipaVnos(klub.getId(), null, null));
            ekipe.add(ekipa.id());
            List<Igralec> kader = new ArrayList<>();
            for (int i = 1; i <= 3; i++) {
                Igralec ig = noviIgralec("Ig" + i, ime.replace(' ', 'x') + i + oznaka);
                ratingStoritev.nastaviZacetniRating(ig, 1500);
                ligaStoritev.dodajVKader(ekipa.id(), new KaderVnos(ig.getId(), i));
                kader.add(ig);
            }
            kadri.add(kader);
        }
        ligaStoritev.generirajRazpored(liga);
        SrecanjeDto s = srecanjeStoritev.zaLigo(liga).getFirst();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                org.getUporabniskoIme(), null, List.of(new SimpleGrantedAuthority("ROLE_ORGANIZATOR"))));

        NatisZapisnikaDto n = natisi.natisni(new NatisZapisnikovVnos(List.of(s.id()), oblika)).getFirst();
        Random r = new Random(oblika.ordinal() + 5);
        SimulacijaZapisnikaTest.Resnica res = SimulacijaZapisnikaTest.nakljucnoSrecanje(r, FormatSrecanja.SAVINJA,
                3, 3, null);
        BufferedImage list = SimulacijaZapisnikaTest.izpolni(n.postavitev(), res, r, n.koda());
        byte[] foto = SimuliraneFotografijeTest.fotografija(list, r, SimuliraneFotografijeTest.OBICAJNO, 3200, 2400,
                2300, 300);

        BranjeZapisnikaDto sprejeto = storitev.sprejmi(foto, null, false);
        storitev.obdelaj(sprejeto.id());
        BranjeZapisnikaDto prebrano = storitev.podrobno(sprejeto.id());
        assertEquals(BranjeZapisnika.Stanje.PREBRANO, prebrano.stanje(), prebrano.napaka());
        assertNotNull(prebrano.podrobnosti().celice());
        assertTrue(prebrano.podrobnosti().poravnana());

        // predlog: igralci po id iz kadra natisa, tocke po resnici
        for (int t = 0; t < 5; t++) {
            BranjeZapisnikaDto.Tekma pt = prebrano.predlog().tekme().get(t);
            assertEquals(ids(res.domaci().get(t), n.kaderDomaci()), pt.domaci().stream().sorted().toList(), "tekma " + t);
            assertEquals(ids(res.gost().get(t), n.kaderGost()), pt.gost().stream().sorted().toList(), "tekma " + t);
        }

        ZapisZapisnikaVnos vnos = resnica(res, n);
        BranjeZapisnikaDto zapisano = storitev.potrdi(sprejeto.id(), vnos);
        assertEquals(BranjeZapisnika.Stanje.ZAPISANO, zapisano.stanje());
        assertEquals(StatusSrecanja.KONCANO, srecanjeStoritev.podrobno(s.id()).srecanje().status());
        assertTrue(storitev.zabeleziStevke(sprejeto.id()) > 20, "stevke za ucenje");
        assertTrue(stevke.existsByIdBranjeZapisnika(sprejeto.id()));
    }

    private static Uporabnik racun(String ime) {
        Uporabnik u = new Uporabnik(ime, "{bcrypt}x", Vloga.ORGANIZATOR);
        u.setStatus(StatusRacuna.POTRJEN);
        return u;
    }

    private static List<Long> ids(List<Integer> vrstice, List<NatisZapisnikaDto.Igralec> kader) {
        return vrstice.stream().map(v -> kader.get(v).id()).sorted().toList();
    }

    /* Zapis, kot ga organizator potrdi (resnica). */
    private static ZapisZapisnikaVnos resnica(SimulacijaZapisnikaTest.Resnica res, NatisZapisnikaDto n) {
        List<ZapisZapisnikaVnos.Tekma> tekme = new ArrayList<>();
        for (int t = 0; t < res.nizi().size(); t++) {
            List<si.turnirko.dto.NizVnos> nizi = new ArrayList<>();
            for (int[] x : res.nizi().get(t)) {
                nizi.add(new si.turnirko.dto.NizVnos(x[0], x[1]));
            }
            tekme.add(new ZapisZapisnikaVnos.Tekma(ids(res.domaci().get(t), n.kaderDomaci()),
                    ids(res.gost().get(t), n.kaderGost()), nizi, null, null));
        }
        return new ZapisZapisnikaVnos(tekme, false, null);
    }
}
