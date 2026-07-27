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
        PoTekmovanjih poTekmovanjih
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
            Tocke tocke
    ) {}

    /* Samo turnirske tekme, pri katerih so vpisane tocke po nizih. */
    public record Tocke(
            int steviloTekem,
            int tockeZa,
            int tockeProti,
            int odstotekTock,
            double povprecjeNaNiz,
            int najvecTockVNizu
    ) {}

    public record Forma(
            /* Zadnjih deset izidov, najnovejsi prvi (true = zmaga). */
            List<Boolean> zadnjih10,
            int trenutniNiz,
            boolean trenutniNizZmag,
            int najdaljsiNizZmag,
            int najdaljsiNizPorazov,
            Integer spremembaElo30dni,
            Integer najvisjiElo,
            LocalDate najvisjiEloDatum
    ) {}

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
