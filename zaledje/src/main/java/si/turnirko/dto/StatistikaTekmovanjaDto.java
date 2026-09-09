/* Zavihek "Zanimivosti" enega tekmovanja - turnirja ali lige.

   Namen je gledalec (uporabnik st. 1), ne organizator: zavihek pove, kaj je
   bilo na tem tekmovanju vredno videti. Zato so postavke pretezno pozitivne
   in nobena ne razglasa najslabsega - najvecji padec ELO je zavestno
   izpuscen.

   Vsaka postavka je lahko prazna (null oz. prazen seznam) in se takrat NE
   izrise. To ni pomanjkljivost, ampak pravilo: uvozena zgodovina brez
   ratingov, liga brez vpisanih tock po nizih in turnir v prvi uri nimajo
   istih podatkov, izpis "ni podatka" pa je slabsi od odsotnosti vrstice.

   Kaj je turnirsko in kaj ligasko:
     - skupno: stevilke, vzponi, presenecenje, zid, delavci, klubi, obrat,
       najdaljsi niz, najdaljsa tekma, dvojica;
     - samo turnir: prvi naslovi (1. mesto v karieri);
     - samo liga: srecanje na noz, najboljsi gostje, nosilci ekip.

   DVOJICE: v vrstice o posamezniku (vzponi, presenecenje, zid, obrat,
   najdaljsi niz/tekma, klubi) ne vstopajo - izida para ni mogoce pripisati
   posamezniku, isto pravilo kot pri ELO. Stejejo samo v "V stevilkah" in v
   svojo vrstico. */
package si.turnirko.dto;

import java.util.List;

public record StatistikaTekmovanjaDto(
        /* Pod pragom odigranih tekem zavihka sploh ni: pri sestih tekmah je
           "najbolj delaven igralec" nakljucje in ne ugotovitev. */
        boolean dovoljPodatkov,
        /* Tekmovanje se traja - stevilke se bodo se premaknile. */
        boolean vTeku,
        /* Ali tekmovanje steje v klubski ELO. Kadar ne, celoten ELO sklop
           (vzponi, presenecenje) odpade in se ne izrise kot nic. */
        boolean stejeVElo,
        Stevilke stevilke,
        List<Vzpon> vzponi,
        Presenecenje presenecenje,
        List<Zid> zid,
        List<Delavec> delavci,
        List<KlubVrstica> klubi,
        Obrat obrat,
        NajdaljsiNiz najdaljsiNiz,
        NajdaljsaTekma najdaljsaTekma,
        List<PrviNaslov> prviNaslovi,
        NaNoz naNoz,
        List<Gostovanje> gostje,
        List<Nosilec> nosilci,
        Dvojica dvojica
) {

    /* Igralec, kot ga izpise vrstica. Klub je posnetek ob nastopu (pri
       turnirju klub ob prijavi), ne trenutni klub - vrstica pove, za koga je
       igralec takrat igral. Osebnih podatkov tu ni in ne sme biti. */
    public record Oseba(Long idIgralec, String polnoIme, String klub) {}

    /* Pas kazalnikov na vrhu zavihka. "Tock" je prazen, kadar tock po nizih
       ni vpisal nihce (vnos je povsod neobvezen); "dogodkov" nosi turnir,
       "ekip" liga. */
    public record Stevilke(
            int igralcev,
            int klubov,
            int tekem,
            int nizov,
            Integer tock,
            Integer dogodkov,
            Integer ekip,
            int tekemDvojic
    ) {}

    /* Koliko ELO je igralec na tem tekmovanju pridobil. Samo pozitivni -
       vrstice o najvecji izgubi zavihek namenoma nima. */
    public record Vzpon(Oseba oseba, int pridobil, int odigranih, int koncni) {}

    /* Zmaga z najvecjo razliko ratingov v prid poraženega favorita. */
    public record Presenecenje(
            Oseba zmagovalec,
            int ratingZmagovalca,
            Oseba porazenec,
            int ratingPorazenca,
            int razlika,
            String izid,
            String kontekst
    ) {}

    /* Najboljse razmerje dobljenih in prejetih nizov. */
    public record Zid(Oseba oseba, int dobljeni, int prejeti, int odigrane) {}

    /* Najvec casa za mizo. Dvojice so tu stete posebej: gre za stevilo
       nastopov (kolikokrat je igralec sedel za mizo), ne za izkupicek, zato
       jih smemo pripisati posamezniku - a v zmage ne stejejo. */
    public record Delavec(Oseba oseba, int odigrane, int zmage, int dvojic) {}

    public record KlubVrstica(String ime, int zmage, int odigrane, int igralcev) {}

    /* Tekma, dobljena po zaostanku 0 : 2 v nizih. "koliko" je stevilo vseh
       takih tekem na tekmovanju - ena vrstica pokaze najbolj borbeno. */
    public record Obrat(
            Oseba zmagovalec,
            Oseba porazenec,
            String izid,
            String nizi,
            String kontekst,
            int koliko
    ) {}

    /* Niz z najvec skupnimi tockami (npr. 18 : 16). "prvi" je tisti, ki je
       niz dobil. */
    public record NajdaljsiNiz(
            Oseba prvi,
            Oseba drugi,
            int tockePrvi,
            int tockeDrugi,
            int zaporedna,
            String kontekst
    ) {}

    /* Tekma z najvec skupnimi tockami. */
    public record NajdaljsaTekma(
            Oseba zmagovalec,
            Oseba porazenec,
            String izid,
            int tock,
            int nizov,
            String kontekst
    ) {}

    /* Zmagovalec dogodka, ki v bazi nima nobenega drugega 1. mesta. Pri
       dvojicah dobi vrstico vsak clan para posebej - naslov je lahko prvi
       samo za enega od njiju. */
    public record PrviNaslov(Oseba oseba, String dogodek) {}

    /* Srecanje, ki ga je odlocila zadnja odigrana tekma. */
    public record NaNoz(
            Long idSrecanje,
            int kolo,
            String domaci,
            String gost,
            int dobljeneDomaci,
            int dobljeneGost,
            Oseba odlocil,
            int koliko
    ) {}

    /* Ekipa in njen izkupicek V GOSTEH. */
    public record Gostovanje(String ekipa, int zmage, int srecanj, int odstotek) {}

    /* Igralec z najvec zmagami za svojo ekipo. */
    public record Nosilec(String ekipa, Oseba oseba, int zmage, int porazi) {}

    /* Najuspesnejsi par tekmovanja. Par je neurejen - ista igralca sta ista
       dvojica ne glede na to, kdo je zapisan prvi. */
    public record Dvojica(Oseba prvi, Oseba drugi, int zmage, int porazi) {}
}
