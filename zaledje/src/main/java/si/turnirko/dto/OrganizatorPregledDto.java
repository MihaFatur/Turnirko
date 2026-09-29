/* Organizatorski pregled (GET /api/v1/organizator/pregled): vse, kar organizator
   potrebuje na eni strani - kaj caka njegovo dejanje, njegova tekmovanja,
   prihajajoci termini, arhiv sezon in poraba paketa.

   Zakaj ena poizvedba in ne sestavljanje v vmesniku: stran bere turnirje, lige,
   srecanja, prijave in tekme, in to samo lastnika. Javni seznami teh podatkov
   bi vsak imel svoje merilo lastnistva (ustvaril / klub), pregled pa mora imeti
   eno - isto, po katerem strezniku ob ustvarjanju steje meja paketa.

   Besedilo (sklanjanje, »2 zapisnika cakata«) sestavi vmesnik; tu so stevila,
   datumi in imena. Datumi so koledarski dnevi (LocalDate). Podatki o naroncini
   (cena, obnova) NISO tu: nosi jih GET /narocnina in je edini vir zanje. */
package si.turnirko.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import si.turnirko.modeli.Paket;
import si.turnirko.modeli.StatusTekmovanja;

public record OrganizatorPregledDto(
        // ime osebe, kot ga je vpisala ob registraciji; prazno, ce ga ni
        String ime,
        // klub organizatorja (»NTK Savinja«); prazen, ce je brez kluba
        String klub,
        // tekoca sezona (»2026/27«)
        String sezona,
        // sezona najzgodnejsega tekmovanja; prazna, ce jih se nima
        String organiziraOdSezone,
        // vsa tekmovanja, ki jih je kadarkoli ustvaril
        int skupajTekmovanj,
        // veljaven organizatorski paket; null = paketa nima (potekel ali neplacan)
        Paket paket,
        // poraba paketa v tekoci sezoni; null, kadar paketa ni
        Kvota lige,
        Kvota turnirji,
        // dan, ko se meje ponastavijo
        LocalDate naslednjaSezonaOd,
        // stevilke tekoce sezone (pas pod naslovom)
        int udelezencev,
        int odigranihTekem,
        List<Tekmovanje> tekmovanja,
        List<Caka> caka,
        // naslednjih 14 dni, po datumu
        List<Termin> prihaja,
        // najblizji termin, tudi ce je dlje od 14 dni; null, ce ga ni
        Termin naslednje,
        // pretekle sezone, najnovejsa prva
        List<Arhiv> arhiv
) {

    public enum VrstaTekmovanja { LIGA, TURNIR }

    /* uporabljeno / meja paketa. */
    public record Kvota(int uporabljeno, int meja) {}

    /* Kaj se v vrstici tekmovanja bere v stolpcu »caka«: prvi trije pomenijo,
       da organizator nekaj mora (levi rob vrstice je moder), ostali so
       informacija. */
    public enum VrstaCakanja {
        ZAPISNIKI,
        REZULTATI,
        ZREB,
        NASLEDNJE_SRECANJE,
        ROK_PRIJAVE,
        ZACETEK,
        RATING_OBRACUNAN,
        NE_STEJE_V_RATING
    }

    /* Merilo napredka: kolo lige, tekme turnirja, prijave (turnir v pripravi),
       ekipe (liga v pripravi) ali koncano. */
    public enum VrstaNapredka { KOLO, TEKME, PRIJAVE, EKIPE, KONCANO }

    /* Ena vrstica seznama sezone. `sezona` je ključ za filter (»2026/27«),
       delez je 0-100 in sluzi palici. */
    public record Tekmovanje(
            VrstaTekmovanja vrsta,
            Long id,
            String ime,
            StatusTekmovanja status,
            String sezona,
            // turnir: dvorana ali kraj; liga: prazno
            String kraj,
            // turnir: datum(a); liga: prazno
            LocalDate datumZacetka,
            LocalDate datumKonca,
            // turnir: prijavljenih; liga: ekip
            int udelezencev,
            // liga: dvokrozno / enokrozno; turnir: prazno
            Boolean dvokrozno,
            VrstaNapredka napredekVrsta,
            int napredekTrenutno,
            int napredekVseh,
            int napredekDelez,
            VrstaCakanja cakaVrsta,
            // stevilo (zapisnikov, rezultatov) ali null
            Integer cakaStevilo,
            // datum (naslednje srecanje, rok prijave, zacetek) ali null
            LocalDate cakaDatum
    ) {}

    /* Vrstica bloka »Caka te«. */
    public record Caka(
            VrstaCakanja vrsta,
            int stevilo,
            VrstaTekmovanja vrstaTekmovanja,
            Long idTekmovanja,
            String imeTekmovanja,
            // zapisniki: kolo in datum najzgodnejsega srecanja; zreb: zacetek turnirja
            Integer kolo,
            LocalDate datum,
            // rezultati: dogodki s cakajocimi tekmami; zapisniki: »Savinja : Maribor II«
            List<String> podrobnosti,
            // rezultati: kje se turnir igra (»cetrtfinale«); sicer prazno
            String faza,
            // kam vodi gumb: rezultati -> dogodek z najvec cakajocimi tekmami,
            // zapisniki -> najzgodnejse cakajoce srecanje; sicer prazno
            Long idDogodka,
            Long idSrecanja
    ) {}

    /* Prihajajoci termin: turnir ali kolo lige. */
    public record Termin(
            VrstaTekmovanja vrsta,
            Long idTekmovanja,
            String imeTekmovanja,
            LocalDate datum,
            // turnir: dvorana ali kraj
            String kraj,
            // kolo lige: zaporedno kolo in stevilo srecanj tistega dne; ura prvega
            // srecanja (null = ura ni dolocena)
            Integer kolo,
            Integer steviloSrecanj,
            LocalTime ura
    ) {}

    /* Ena pretekla sezona. */
    public record Arhiv(
            String sezona,
            int lig,
            int turnirjev,
            int udelezencev,
            int tekem
    ) {}
}
