/* Testi starostnega sidra in uvrstitve novinca skozi celo pot: od igralceve
   letnice rojstva do vrednosti v rating_stanje.

   Tu se meri tisto, zaradi cesar je 4. korak nastal: dva novinca v U11, ki sta
   igrala med sabo, sta se doslej ustalila pri isti stevilki kot dva novinca v
   U19, ker so vsi zaceli pri 1000. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import si.turnirko.dto.VnosRezultata;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.RatingStanje;
import si.turnirko.modeli.Spol;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.Tekma;
import si.turnirko.repozitoriji.PrijavaRepozitorij;

class StarostnoSidroTest extends IntegracijskiTest {

    @Autowired private PrijavaRepozitorij prijaveRepo;
    @Autowired private ZrebStoritev zreb;

    /* Letnica, pri kateri je igralec v tekoci sezoni star toliko let
       (11. clen PST - glej StarostniPas.letaVSezoni). */
    private static int letnicaZaStarost(int leta) {
        LocalDate danes = LocalDate.now();
        int letoSezone = danes.getMonthValue() >= 7 ? danes.getYear() : danes.getYear() - 1;
        return letoSezone - leta;
    }

    /* Vsem prijavljenim dogodka nastavi letnico rojstva. */
    private void nastaviStarost(Dogodek dogodek, int leta) {
        List<Prijava> prijave = prijavePoVrsti(dogodek.getId());
        for (Prijava p : prijave) {
            p.getIgralec().setDatumRojstva(LocalDate.of(letnicaZaStarost(leta), 1, 1));
            igralecRepozitorij.save(p.getIgralec());
        }
    }

    private int rating(Igralec igralec) {
        return ratingStanjeRepozitorij
                .findByIgralecIdAndSistem(igralec.getId(), RatingStanje.SISTEM_TURNIRKO)
                .orElseThrow().getVrednost();
    }

    private Tekma prvaPripravljena(Long idDogodka) {
        return tekmeDogodka(idDogodka).stream()
                .filter(t -> t.getStatus() == StatusTekme.PRIPRAVLJENA)
                .findFirst().orElseThrow();
    }

    /* Sidro mora z leti rasti - to je cela poanta tabele. */
    @Test
    void sidroRasteSStarostjo() {
        Igralec mlajsi = noviIgralec("Mlad", "Novinec");
        mlajsi.setDatumRojstva(LocalDate.of(letnicaZaStarost(11), 1, 1));
        Igralec starejsi = noviIgralec("Star", "Novinec");
        starejsi.setDatumRojstva(LocalDate.of(letnicaZaStarost(18), 1, 1));

        int sidroMlajsi = sidroStoritev.zacetniRating(mlajsi, LocalDate.now());
        int sidroStarejsi = sidroStoritev.zacetniRating(starejsi, LocalDate.now());

        assertTrue(sidroMlajsi < sidroStarejsi,
                "18-letnik mora imeti visje izhodisce kot 11-letnik (" + sidroMlajsi
                        + " vs " + sidroStarejsi + ")");
        assertNotEquals(TurnirkoRatingStoritev.ZACETNI_RATING, sidroMlajsi,
                "novinec ne sme vec zaceti pri enotnih 1000");
    }

    /* Zenska in moska tabela sta loceni (skali sta neprimerljivi - med spoloma
       v 91.741 uvozenih tekmah ni niti ene skupne). */
    @Test
    void sidroLociSpol() {
        Igralec fant = noviIgralec("Fant", "Trinajst", Spol.MOSKI);
        fant.setDatumRojstva(LocalDate.of(letnicaZaStarost(13), 1, 1));
        Igralec dekle = noviIgralec("Dekle", "Trinajst", Spol.ZENSKI);
        dekle.setDatumRojstva(LocalDate.of(letnicaZaStarost(13), 1, 1));

        assertNotEquals(sidroStoritev.zacetniRating(fant, LocalDate.now()),
                sidroStoritev.zacetniRating(dekle, LocalDate.now()));
    }

    /* Prav ta primer je uporabnik prijavil: dva novinca v U11 in dva v U19,
       vsak par med sabo - po novem U11 ne more prehiteti U19. */
    @Test
    void novincaVU11OstanetaPodNovincemaVU19() {
        Dogodek mladi = pripraviDogodek(4);
        nastaviStarost(mladi, 11);
        zreb.izvediZreb(mladi.getId());
        Tekma tekmaMladih = prvaPripravljena(mladi.getId());
        Igralec zmagovalecMladih = tekmaMladih.getPrijava1().getIgralec();
        tekmaStoritev.vnesiRezultat(tekmaMladih.getId(), new VnosRezultata(null, 3, 0, null, null));

        Dogodek starejsi = pripraviDogodek(4);
        nastaviStarost(starejsi, 19);
        zreb.izvediZreb(starejsi.getId());
        Tekma tekmaStarejsih = prvaPripravljena(starejsi.getId());
        Igralec zmagovalecStarejsih = tekmaStarejsih.getPrijava1().getIgralec();
        tekmaStoritev.vnesiRezultat(tekmaStarejsih.getId(),
                new VnosRezultata(null, 3, 0, null, null));

        assertTrue(rating(zmagovalecMladih) < rating(zmagovalecStarejsih),
                "zmagovalec med 11-letniki ne sme imeti vec tock kot zmagovalec med 19-letniki ("
                        + rating(zmagovalecMladih) + " vs " + rating(zmagovalecStarejsih) + ")");
    }

    /* Prva tekma se sesteje po korakih (pri eni tekmi je ugibanje vecje od
       podatka), od druge naprej pa se rating prvega dne izracuna znova iz vseh
       izidov - in to premakne mocneje kot dva koraka. */
    @Test
    void odDrugeTekmePrvegaDnevaVeljaUvrstitev() {
        Dogodek dogodek = pripraviDogodek(4);
        zreb.izvediZreb(dogodek.getId());

        Tekma polfinale = prvaPripravljena(dogodek.getId());
        Igralec zmagovalec = polfinale.getPrijava1().getIgralec();
        int sidro = sidroZa(zmagovalec);
        tekmaStoritev.vnesiRezultat(polfinale.getId(), new VnosRezultata(null, 3, 0, null, null));
        assertEquals(sidro + 41, rating(zmagovalec), "prva tekma: obicajni korak");

        // drugi polfinale, da se sestavi finale
        Tekma drugiPolfinale = prvaPripravljena(dogodek.getId());
        tekmaStoritev.vnesiRezultat(drugiPolfinale.getId(),
                new VnosRezultata(null, 3, 0, null, null));

        Tekma finale = prvaPripravljena(dogodek.getId());
        boolean zmagovalecJePrvi = finale.getPrijava1().getIgralec().getId()
                .equals(zmagovalec.getId());
        tekmaStoritev.vnesiRezultat(finale.getId(), zmagovalecJePrvi
                ? new VnosRezultata(null, 3, 0, null, null)
                : new VnosRezultata(null, 0, 3, null, null));

        /* Dve zmagi proti sidru in sidru+41, ob dveh navideznih izenacenih
           tekmah proti sidru. Pri sidru 1263 je to 1465 - precej vec, kot bi
           dala dva koraka (1263 + 41 + 38). */
        int pricakovano = UvrstitevNovinca.izracunaj(
                List.of(new UvrstitevNovinca.Izid(sidro, true),
                        new UvrstitevNovinca.Izid(sidro + 41, true)),
                sidro);
        assertEquals(pricakovano, rating(zmagovalec));
        assertTrue(rating(zmagovalec) > sidro + 100,
                "uvrstitev mora dvomljivega novinca premakniti odlocneje kot dva koraka");
    }

    /* Kdor ima POSTAVLJEN rating, ni novinec: uvrstitev ga ne sme preracunati.
       Sicer bi Jorgicu, postavljenemu po svetovni lestvici, prvi turnir pri nas
       stevilko podrl na raven nasprotnikov, ki jih je premagal. */
    @Test
    void postavljenegaIgralcaUvrstitevNePovozi() {
        Dogodek dogodek = pripraviDogodek(4);
        zreb.izvediZreb(dogodek.getId());

        Tekma polfinale = prvaPripravljena(dogodek.getId());
        Igralec postavljeni = polfinale.getPrijava1().getIgralec();
        RatingStanje stanje = new RatingStanje(postavljeni, RatingStanje.SISTEM_TURNIRKO, 2400);
        stanje.setPostavljen(true);
        ratingStanjeRepozitorij.save(stanje);

        tekmaStoritev.vnesiRezultat(polfinale.getId(), new VnosRezultata(null, 3, 0, null, null));
        Tekma drugiPolfinale = prvaPripravljena(dogodek.getId());
        tekmaStoritev.vnesiRezultat(drugiPolfinale.getId(),
                new VnosRezultata(null, 3, 0, null, null));
        Tekma finale = prvaPripravljena(dogodek.getId());
        boolean prvi = finale.getPrijava1().getIgralec().getId().equals(postavljeni.getId());
        tekmaStoritev.vnesiRezultat(finale.getId(), prvi
                ? new VnosRezultata(null, 3, 0, null, null)
                : new VnosRezultata(null, 0, 3, null, null));

        assertTrue(rating(postavljeni) > 2350,
                "postavljeni igralec sme po dveh zmagah proti sibkejsim le malo zdrsniti,"
                        + " ne pa se uvrstiti na njihovo raven (" + rating(postavljeni) + ")");
    }
}
