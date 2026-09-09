/* Zasebni del profila: analize, ki jih vidi samo igralec sam (in
   administrator). Loceno od javnega dela, ker nasprotniku ni treba vedeti,
   kje ima igralec sibke tocke.

   Statistika tock je na voljo samo za turnirske tekme - ligaska srecanja
   hranijo le nize, ne tock posameznih nizov. */
package si.turnirko.dto;

import java.time.LocalDate;
import java.util.List;

public record ProfilZasebnoDto(
        Nasprotniki nasprotniki,
        NiziInTocke niziInTocke,
        Forma forma,
        PoTekmovanjih poTekmovanjih,
        /* Razsevni graf "ELO nasprotnika proti izidu". Rating nasprotnika ob
           tekmi je sicer javen podatek, a razrez zivi tu, ker pove, proti
           komu je igralec mocan oziroma sibek - to je analiza, ne rezultat. */
        List<RazsevnaTocka> razsevni
) {

    /* Izkupicek ene skupine tekem (npr. "proti desnicarjem", "v gosteh"). */
    public record Delez(
            String oznaka,
            int odigrane,
            int zmage,
            int porazi,
            int odstotek
    ) {

        public static Delez iz(String oznaka, int zmage, int porazi) {
            int odigrane = zmage + porazi;
            return new Delez(oznaka, odigrane, zmage, porazi,
                    odigrane == 0 ? 0 : Math.round(zmage * 100f / odigrane));
        }
    }

    /* Posamezen izpostavljen nasprotnik (najboljsa zmaga, nemesis ...). */
    public record Nasprotnik(
            Long idIgralec,
            String polnoIme,
            String klub,
            Integer rating,
            int zmage,
            int porazi
    ) {}

    public record Nasprotniki(
            Delez protiDesnicarjem,
            Delez protiLevicarjem,
            Delez rokaNeznana,
            /* Razvrstitev po ratingu nasprotnika V TRENUTKU tekme (iz dnevnika
               ratinga); tekme brez zabelezenega ratinga sem ne stejejo. */
            Delez protiMocnejsim,
            Delez protiPodobnim,
            Delez protiSibkejsim,
            int tekemZZnanimRatingom,
            Nasprotnik najboljsaZmaga,
            Nasprotnik nemesis,
            Nasprotnik najpogostejsi,
            List<Delez> poKlubih
    ) {}

    /* Koncni izid po nizih (npr. "3:1") in kolikokrat se je zgodil. */
    public record Razmerje(String oznaka, int stevilo, boolean zmaga) {}

    public record NiziInTocke(
            int dobljeniNizi,
            int prejetiNizi,
            List<Razmerje> razmerja,
            Delez odlocilniNiz,
            /* "Zmage" so tu tekme brez izgubljenega niza, "porazi" vse ostale -
               Delez je nosilec razmerja, ne izida. */
            Delez brezIzgubljenegaNiza,
            Tocke tocke
    ) {}

    /* Samo tekme (turnirske in ligaske), pri katerih so vpisane tocke po
       nizih - vnos je povsod neobvezen. */
    public record Tocke(
            int steviloTekem,
            int tockeZa,
            int tockeProti,
            int odstotekTock,
            double povprecjeNaNiz,
            int najvecTockVNizu,
            /* "Pod pritiskom" = tesni nizi, v katerih sta oba dosegla vsaj 9
               tock. Tock po posamezni izmenjavi ne hranimo, zato je to
               najboljsi priblizek izida 9:9 in vec, ki ga podatki dopuscajo. */
            int nizovPodPritiskom,
            int odstotekTockPodPritiskom
    ) {}

    /* En koledarski mesec: kolikokrat je igralec zmagal in koliko zmag bi
       glede na ELO nasprotnikov pricakovali. "mesec" je oblike "2026-04". */
    public record Mesec(String mesec, int zmage, double pricakovaneZmage) {}

    public record Forma(
            /* Zadnjih deset izidov, najnovejsi prvi (true = zmaga). */
            List<Boolean> zadnjih10,
            int trenutniNiz,
            boolean trenutniNizZmag,
            int najdaljsiNizZmag,
            int najdaljsiNizPorazov,
            Integer spremembaElo30dni,
            Integer najvisjiElo,
            LocalDate najvisjiEloDatum,
            /* Od najstarejsega meseca naprej; steti so samo nastopi, pri
               katerih sta bila znana oba ratinga (sicer pricakovanja ni). */
            List<Mesec> poMesecih
    ) {}

    /* Ena tekma v razsevnem grafu: rating nasprotnika ob tekmi proti
       spremembi lastnega ratinga. */
    public record RazsevnaTocka(int ratingNasprotnika, int sprememba, boolean zmaga) {}

    public record PoTekmovanjih(
            Delez turnirji,
            Delez lige,
            Delez doma,
            Delez vGosteh,
            List<Delez> poPoziciji,
            List<Delez> poFazi,
            /* Ligaske dvojice: ne stejejo v ELO ne v osebne zmage, zato loceno. */
            Delez dvojice
    ) {}
}
