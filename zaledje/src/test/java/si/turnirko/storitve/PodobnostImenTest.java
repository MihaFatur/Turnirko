/* Pravila ujemanja imen pri vpisu novega igralca (PodobnostImen).

   Test drzi obe strani opozorila: da ujame dvojnika, ki ga clovek ne bi
   opazil (Miha Fatur z drugim datumom, zamenjan vrstni red, tipkarska
   napaka), in da NE opozarja na razlicne osebe (Tina / Nina, Maja / Miha,
   sestra z istim priimkom) - opozorilo, ki se pokaze prepogosto, se preneha
   brati. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import si.turnirko.modeli.PrimerjavaDatuma;
import si.turnirko.modeli.Spol;
import si.turnirko.modeli.UjemanjeImena;

class PodobnostImenTest {

    private static UjemanjeImena ujemanje(String vpisIme, String vpisPriimek,
                                          String ime, String priimek) {
        return ujemanje(vpisIme, vpisPriimek, null, ime, priimek, null);
    }

    private static UjemanjeImena ujemanje(String vpisIme, String vpisPriimek, Spol vpisSpol,
                                          String ime, String priimek, Spol spol) {
        return PodobnostImen.ujemanje(PodobnostImen.Ime.iz(vpisIme, vpisPriimek), vpisSpol,
                PodobnostImen.Ime.iz(ime, priimek), spol);
    }

    @Test
    void istoImeBrezSumnikovInVelikihCrkJeIsto() {
        assertEquals(UjemanjeImena.ISTO, ujemanje("Miha", "Fatur", "Miha", "Fatur"));
        assertEquals(UjemanjeImena.ISTO, ujemanje("miha", "FATUR", "Miha", "Fatur"));
        assertEquals(UjemanjeImena.ISTO, ujemanje("Ziga", "Kovac", "Žiga", "Kovač"));
        assertEquals(UjemanjeImena.ISTO, ujemanje("  Ana ", "Novak-Kos", "Ana", "Novak Kos"));
        // »đ« razclenitev Unicode ne razstavi, zato ga jedro() zamenja samo
        assertEquals(UjemanjeImena.ISTO, ujemanje("Duro", "Duric", "Đuro", "Đurić"));
    }

    @Test
    void zamenjanaImeInPriimekStaObrnjena() {
        assertEquals(UjemanjeImena.OBRNJENO, ujemanje("Fatur", "Miha", "Miha", "Fatur"));
        assertEquals(UjemanjeImena.OBRNJENO, ujemanje("Kovač", "Žiga", "Ziga", "Kovac"));
    }

    @Test
    void enaBesedaZNapakoJePodobna() {
        // tipkarska napaka v priimku in v imenu
        assertEquals(UjemanjeImena.PODOBNO, ujemanje("Miha", "Fatar", "Miha", "Fatur"));
        assertEquals(UjemanjeImena.PODOBNO, ujemanje("Matjaz", "Horvat", "Matjaž", "Horvath"));
        assertEquals(UjemanjeImena.PODOBNO, ujemanje("Marjia", "Novak", "Marija", "Novak"));
        assertEquals(UjemanjeImena.PODOBNO, ujemanje("Katarina", "Mohoric", "Katharina", "Mohoric"));
        // dolga beseda (od 9 crk) prenese dve napaki, krajsa samo eno
        assertEquals(UjemanjeImena.PODOBNO, ujemanje("Alexander", "Novak", "Aleksander", "Novak"));
        assertNull(ujemanje("Alexandr", "Novak", "Aleksander", "Novak"));
    }

    @Test
    void krajsaOblikaImenaJePodobna() {
        assertEquals(UjemanjeImena.PODOBNO, ujemanje("Miha", "Fatur", "Mihael", "Fatur"));
        assertEquals(UjemanjeImena.PODOBNO, ujemanje("Mihael", "Fatur", "Miha", "Fatur"));
        // dve imeni ali dvojni priimek proti eni besedi
        assertEquals(UjemanjeImena.PODOBNO, ujemanje("Ana", "Novak", "Ana Marija", "Novak"));
        assertEquals(UjemanjeImena.PODOBNO, ujemanje("Ana", "Novak", "Ana", "Novak Kos"));
    }

    /* Matej in Mateja se razlikujeta za eno crko, a sta dve osebi. Spol zozi
       samo ime; isto ime ostane zadetek ne glede nanj, da napacno vpisan spol
       dvojnika ne skrije. */
    @Test
    void spolZoziImeNeZoziPa() {
        assertNull(ujemanje("Matej", "Novak", Spol.MOSKI, "Mateja", "Novak", Spol.ZENSKI));
        assertEquals(UjemanjeImena.PODOBNO,
                ujemanje("Matej", "Novak", Spol.MOSKI, "Mateja", "Novak", Spol.MOSKI));
        // brez spola (ali pri enem od obeh) ne izgubi kandidata
        assertEquals(UjemanjeImena.PODOBNO, ujemanje("Matej", "Novak", "Mateja", "Novak"));
        assertEquals(UjemanjeImena.PODOBNO,
                ujemanje("Matej", "Novak", null, "Mateja", "Novak", Spol.ZENSKI));
        // isto ime z drugim spolom je se vedno zadetek
        assertEquals(UjemanjeImena.ISTO,
                ujemanje("Miha", "Fatur", Spol.ZENSKI, "Miha", "Fatur", Spol.MOSKI));
        // krajsa oblika tudi: Mihael ni Miha, ce je ena zenska
        assertNull(ujemanje("Miha", "Fatur", Spol.ZENSKI, "Mihael", "Fatur", Spol.MOSKI));
    }

    @Test
    void razlicneOsebeNisoPodobne() {
        // sestra: isti priimek, razlicno ime (v bazi: Maja Fatur 2010)
        assertNull(ujemanje("Miha", "Fatur", "Maja", "Fatur"));
        // kratko ime ena crka razlike: dve imeni
        assertNull(ujemanje("Tina", "Novak", "Nina", "Novak"));
        assertNull(ujemanje("Ana", "Novak", "Anja", "Novak"));
        assertNull(ujemanje("Jan", "Novak", "Janja", "Novak"));
        // razlicen priimek
        assertNull(ujemanje("Miha", "Fatur", "Miha", "Kovac"));
        // ne obe besedi hkrati: dve napaki ne sestavita opozorila
        assertNull(ujemanje("Miha", "Fatar", "Mihael", "Fatur"));
        assertNull(ujemanje("Matjaz", "Horvat", "Matej", "Horvath"));
    }

    @Test
    void prazenVpisNiNikoliPodoben() {
        assertNull(ujemanje("--", "Fatur", "--", "Fatur"));
        assertNull(ujemanje("Miha", " ", "Miha", " "));
    }

    @Test
    void datumJeEnakPodobenAliDrug() {
        LocalDate obstojec = LocalDate.of(2007, 1, 9);
        assertEquals(PrimerjavaDatuma.ENAK, PodobnostImen.primerjajDatum(obstojec, obstojec));
        // en dan, en mesec ali eno leto
        assertEquals(PrimerjavaDatuma.PODOBEN,
                PodobnostImen.primerjajDatum(LocalDate.of(2007, 1, 19), obstojec));
        assertEquals(PrimerjavaDatuma.PODOBEN,
                PodobnostImen.primerjajDatum(LocalDate.of(2007, 3, 9), obstojec));
        assertEquals(PrimerjavaDatuma.PODOBEN,
                PodobnostImen.primerjajDatum(LocalDate.of(2008, 1, 9), obstojec));
        // dan in mesec zamenjana: 9. 1. proti 1. 9.
        assertEquals(PrimerjavaDatuma.PODOBEN,
                PodobnostImen.primerjajDatum(LocalDate.of(2007, 9, 1), obstojec));
        // dve sestavini sta drugi
        assertEquals(PrimerjavaDatuma.DRUG,
                PodobnostImen.primerjajDatum(LocalDate.of(1900, 1, 1), obstojec));
        assertEquals(PrimerjavaDatuma.DRUG,
                PodobnostImen.primerjajDatum(LocalDate.of(2007, 5, 20), obstojec));
    }

    @Test
    void razdaljaStejeZamenjavoSosednjihCrkZaEno() {
        assertEquals(1, PodobnostImen.razdalja("marija", "marjia"));
        assertEquals(2, PodobnostImen.razdalja("miha", "maja"));
        assertEquals(0, PodobnostImen.razdalja("", ""));
        assertEquals(4, PodobnostImen.razdalja("", "miha"));
    }
}
