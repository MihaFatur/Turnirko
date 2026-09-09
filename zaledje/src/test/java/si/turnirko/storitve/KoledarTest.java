/* Koledar zdruzi dve razlicni stvari v en seznam po dnevih, zato ga varujejo
   testi treh pravil, ki jih je mogoce nehote razbiti:
     - ligaski vnos je KOLO in ne posamezno srecanje,
     - turnir cez vec dni nosi obe meji (koledar oznaci vse vmesne dneve),
     - obdobje je vkljucno na obeh straneh in omejeno po dolzini. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import si.turnirko.dto.EkipaVnos;
import si.turnirko.dto.KaderVnos;
import si.turnirko.dto.KoledarVnosDto;
import si.turnirko.dto.LigaVnos;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Klub;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.Turnir;
import si.turnirko.repozitoriji.KlubRepozitorij;

class KoledarTest extends IntegracijskiTest {

    @Autowired private KoledarStoritev koledarStoritev;
    @Autowired private LigaStoritev ligaStoritev;
    @Autowired private KlubRepozitorij klubRepozitorij;

    private static final LocalDate PRVO_KOLO = LocalDate.of(2026, 10, 4);

    /* Stiri ekipe pomenijo dve srecanji v kolu. V koledarju je to EN vnos z
       dvema paroma - sicer bi se ime lige v istem dnevu ponovilo dvakrat. */
    @Test
    void koloLigeJeEnVnosZVsemiPari() {
        pripraviLigo(4);

        List<KoledarVnosDto> vnosi = koledarStoritev.vObdobju(PRVO_KOLO, PRVO_KOLO);

        assertEquals(1, vnosi.size(), "eno kolo -> en vnos");
        KoledarVnosDto vnos = vnosi.get(0);
        assertEquals(KoledarVnosDto.Vrsta.LIGA, vnos.vrsta());
        assertEquals(1, vnos.kolo());
        assertEquals(2, vnos.srecanja().size(), "4 ekipe -> 2 para v kolu");
        assertEquals(PRVO_KOLO, vnos.datum());
        assertEquals(PRVO_KOLO, vnos.datumKonca(), "kolo je vedno en dan");
        assertNotNull(vnos.zacetek(), "termin kola nosi tudi uro");
        assertEquals(18, vnos.zacetek().getHour(), "ura semena se ohrani");
    }

    /* Naslednje kolo je cez teden dni in v koledarju prvega dneva ne sme biti. */
    @Test
    void obdobjeZajameSamoSvojeDneve() {
        pripraviLigo(4);

        assertTrue(koledarStoritev.vObdobju(PRVO_KOLO.plusDays(1), PRVO_KOLO.plusDays(6)).isEmpty(),
                "med kolama koledar nima nicesar");
        assertEquals(1, koledarStoritev.vObdobju(PRVO_KOLO.plusDays(7), PRVO_KOLO.plusDays(7)).size(),
                "drugo kolo je teden dni kasneje");
    }

    /* Turnir cez vec dni: vmesnik po obeh datumih oznaci ves razpon, zato
       morata biti oba v vnosu - tudi ce obdobje zajame le en njegov dan. */
    @Test
    void turnirCezVecDniNosiObeMeji() {
        noviTurnir("Pokal Savinje", LocalDate.of(2026, 3, 14), LocalDate.of(2026, 3, 16));

        List<KoledarVnosDto> vnosi = koledarStoritev.vObdobju(
                LocalDate.of(2026, 3, 15), LocalDate.of(2026, 3, 15));

        assertEquals(1, vnosi.size(), "turnir sega v obdobje s srednjim dnem");
        assertEquals(LocalDate.of(2026, 3, 14), vnosi.get(0).datum());
        assertEquals(LocalDate.of(2026, 3, 16), vnosi.get(0).datumKonca());
        assertTrue(vnosi.get(0).srecanja().isEmpty(), "turnir parov nima");
    }

    /* Enodnevni turnir datuma konca nima; koledar mu ga izpelje iz zacetka,
       sicer bi iz vsakega obdobja izpadel. */
    @Test
    void enodnevniTurnirDobiKonecIzZacetka() {
        noviTurnir("Odprto prvenstvo", LocalDate.of(2026, 5, 9), null);

        List<KoledarVnosDto> vnosi = koledarStoritev.vObdobju(
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));

        assertEquals(1, vnosi.size());
        assertEquals(vnosi.get(0).datum(), vnosi.get(0).datumKonca());
    }

    /* Uvozena zgodovina ima turnirje brez datuma. Koledar je pogled po dnevih,
       zato tak vnos nima kam - ostane samo v seznamu turnirjev. */
    @Test
    void turnirBrezDatumaNiVKoledarju() {
        noviTurnir("Brez datuma", null, null);

        assertTrue(koledarStoritev.vObdobju(
                LocalDate.of(2000, 1, 1), LocalDate.of(2000, 12, 31)).isEmpty());
    }

    /* En klic ne sme potegniti vse uvozene zgodovine. */
    @Test
    void predolgoObdobjeSeZavrne() {
        LocalDate od = LocalDate.of(2020, 1, 1);
        assertThrows(NeveljavenVnosIzjema.class,
                () -> koledarStoritev.vObdobju(od, od.plusYears(5)));
        assertThrows(NeveljavenVnosIzjema.class,
                () -> koledarStoritev.vObdobju(od, od.minusDays(1)));
    }

    /* Vrstni red je datum in nato ime: iz njega vmesnik izpelje barve
       tekmovanj, zato mora biti stalen. */
    @Test
    void vnosiSoUrejeniPoDatumuInImenu() {
        noviTurnir("Zadnji po abecedi", LocalDate.of(2026, 3, 15), null);
        noviTurnir("Aljazev memorial", LocalDate.of(2026, 3, 15), null);
        noviTurnir("Prvi po datumu", LocalDate.of(2026, 3, 14), null);

        List<String> imena = koledarStoritev.vObdobju(
                        LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)).stream()
                .map(KoledarVnosDto::ime)
                .toList();

        assertEquals(List.of("Prvi po datumu", "Aljazev memorial", "Zadnji po abecedi"), imena);
    }

    // ---------- priprava ----------

    private Turnir noviTurnir(String ime, LocalDate zacetek, LocalDate konec) {
        Turnir turnir = new Turnir();
        turnir.setIme(ime);
        turnir.setDatumZacetka(zacetek);
        turnir.setDatumKonca(konec);
        return turnirRepozitorij.save(turnir);
    }

    /* Liga s semenom terminov: prvo kolo 4. 10. 2026 ob 18.00, naslednja na
       sedem dni. Ekipe dobijo kader, ker ga razpored zahteva. */
    private Long pripraviLigo(int steviloEkip) {
        LigaVnos vnos = new LigaVnos("Test liga", "2026/27", SpolKategorija.MOSKI,
                FormatSrecanja.SNTL, 5, null, false, 2, 1, 0, true, false, true, false, null,
                PRVO_KOLO.atTime(18, 0), 7);
        Long idLige = ligaStoritev.ustvari(vnos).id();

        for (int i = 1; i <= steviloEkip; i++) {
            Klub klub = klubRepozitorij.save(new Klub("Klub K" + i, null));
            var ekipa = ligaStoritev.dodajEkipo(idLige, new EkipaVnos(klub.getId(), null, null));
            for (int j = 1; j <= 3; j++) {
                Igralec igralec = noviIgralec("Ime" + i + j, "Priimek" + i + j);
                ligaStoritev.dodajVKader(ekipa.id(), new KaderVnos(igralec.getId(), j));
            }
        }
        ligaStoritev.generirajRazpored(idLige);
        return idLige;
    }
}
