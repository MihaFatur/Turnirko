/* Napoved: koliko Turnirko ratinga bi igralec dobil ali izgubil, ce bi ZDAJ
   odigral tekmo proti izbranemu nasprotniku.

   Ni ena stevilka, ampak mreza - in to namenoma. Sprememba je zmnozek
   K x margina x teza x (izid - pricakovano), od cesar sta dve sestavini
   odvisni od tekme, ki je se ni bilo:

     - MARGINA (izid v nizih): gladka zmaga proti mocnejsemu je presenetljiva
       in prinese vec od tesne. Zato je vrstica za vsak mozni izid (3:0, 3:1,
       3:2 in zrcalno), ne ena sama povprecna stevilka, ki je ne bi dala
       nobena prava tekma.
     - TEZA (raven tekmovanja): ista zmaga na uradnem turnirju NTZS premakne
       rating za tretjino vec kot na klubskem. Ker je vsak zmnozek se
       zaokrozen (Math.rint), ravni ni mogoce dobiti z mnozenjem v vmesniku -
       vsaka ima svoje izracunane stevilke.

   K in pricakovani izid sta last IGRALCA in sta zato v "Stran"; margina in
   teza sta last TEKME. Isto delitev pozna dnevnik ratinga (rating_zgodovina).

   Napoved nicesar ne zapise - je pogled na formulo, ne obracun. */
package si.turnirko.dto;

import java.util.List;

import si.turnirko.modeli.RavenTekmovanja;

public record NapovedTekmeDto(
        Stran jaz,
        Stran nasprotnik,
        /* Na koliko dobljenih nizov se tekma igra (3, 5 ali 7 nizov). */
        int steviloNizov,
        /* Pricakovana verjetnost zmage lastnika profila, v odstotkih. To je
           "pricakovano" iz obrazca - razlog, zakaj zmaga proti mocnejsemu
           prinese vec od zmage proti sibkejsemu. */
        int pricakovanOdstotek,
        List<Raven> ravni
) {

    /* En igralec v napovedi. "rating" je stevilka, s katero bi vstopil v
       tekmo, "k" pa njegov K faktor za to tekmo - ta je last igralca in je
       lahko za vsako stran drugacen (novinec ima vecjega od ustaljenega). */
    public record Stran(
            Long idIgralec,
            String polnoIme,
            String klub,
            int rating,
            int stTekem,
            int k,
            /* Ali bi ta tekma stela med tekme po vrnitvi (vecji K). */
            boolean vrnitev,
            List<Opozorilo> opozorila
    ) {}

    /* Vse sestavine napovedi za eno raven tekmovanja. */
    public record Raven(RavenTekmovanja raven, double teza, List<Izid> izidi) {}

    /* Ena mozna tekma: izid v nizih z vidika lastnika profila in kaj bi
       naredil obema ratingoma. Urejeni so od najbolj prepricljive zmage do
       najhujsega poraza - tako se berejo kot ena lestvica in ne kot dva
       seznama. */
    public record Izid(
            int mojiNizi,
            int nizovNasprotnika,
            boolean zmaga,
            double margina,
            int sprememba,
            int rating,
            int spremembaNasprotnika,
            int ratingNasprotnika
    ) {}

    /* Polozaj, v katerem napoved ni cela resnica. Streznik ga poimenuje,
       vmesnik pa iz njega napise poved - ugibanje iz odsotnosti polj je isto
       pravilo kot pri ProfilDto.NacinSpremembe. */
    public enum Opozorilo {
        /* Igralec se nima ratinga: stevilka izhaja iz starostnega sidra in se
           bo po prvih tekmah premaknila bolj, kot kaze napoved. */
        BREZ_RATINGA,
        /* Igralec je danes odigral svoj prvi dan. Takrat se rating ne sesteva
           po korakih, ampak se vsakic znova izracuna iz vseh izidov dneva
           (UvrstitevNovinca), zato obrazec koraka zanj se ne velja. */
        PRVI_DAN
    }
}
