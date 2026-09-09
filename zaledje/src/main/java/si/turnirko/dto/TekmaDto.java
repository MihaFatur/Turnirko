/* Tekma - izpis za prikaz mreze. */
package si.turnirko.dto;

import si.turnirko.modeli.FazaTekme;
import si.turnirko.modeli.IzidTekme;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.Tekma;

public record TekmaDto(
        Long id,
        FazaTekme faza,
        Long idSkupina,
        int kolo,
        int pozicija,
        StatusTekme status,
        IzidTekme izidTip,
        int steviloNizov,
        Udelezenec udelezenec1,
        Udelezenec udelezenec2,
        int dobljeniNizi1,
        int dobljeniNizi2,
        Long idZmagovalcaPrijave,
        Integer miza,
        /* Sprememba klubskega ELO ob tej tekmi (npr. +16 / -16); null,
           ce tekma se ni obracunana (npr. prosti prehod ali nedokoncana). */
        Integer spremembaElo1,
        Integer spremembaElo2,
        /* Rating igralca PRED to tekmo: pri odigrani tekmi zgodovinski
           (pred obracunom), sicer trenutni; null, ce igralec se nima ratinga. */
        Integer ratingPred1,
        Integer ratingPred2
) {

    /* Udelezenec tekme (stran 1 ali 2) - igralec ali PAR.
       polnoIme2/klub2 sta zapolnjena samo pri dvojicah; vmesnik iz njiju
       sestavi dvovrsticni zapis na kartici mreze. */
    public record Udelezenec(Long idPrijave, String polnoIme, String klub,
                             String polnoIme2, String klub2) {

        static Udelezenec iz(Prijava prijava) {
            if (prijava == null) return null;
            return new Udelezenec(
                    prijava.getId(),
                    prijava.getIgralec().polnoIme(),
                    prijava.getKlubObPrijavi() != null ? prijava.getKlubObPrijavi().getIme() : null,
                    prijava.jePar() ? prijava.getIgralec2().polnoIme() : null,
                    prijava.getKlubObPrijavi2() != null ? prijava.getKlubObPrijavi2().getIme() : null);
        }
    }

    public static TekmaDto iz(Tekma tekma) {
        return iz(tekma, null, null, null, null);
    }

    public static TekmaDto iz(Tekma tekma, Integer spremembaElo1, Integer spremembaElo2,
                              Integer ratingPred1, Integer ratingPred2) {
        return new TekmaDto(
                tekma.getId(),
                tekma.getFaza(),
                tekma.getIdSkupina(),
                tekma.getKolo(),
                tekma.getPozicija(),
                tekma.getStatus(),
                tekma.getIzidTip(),
                tekma.getSteviloNizov(),
                Udelezenec.iz(tekma.getPrijava1()),
                Udelezenec.iz(tekma.getPrijava2()),
                tekma.getDobljeniNizi1(),
                tekma.getDobljeniNizi2(),
                tekma.getZmagovalec() != null ? tekma.getZmagovalec().getId() : null,
                tekma.getMiza(),
                spremembaElo1,
                spremembaElo2,
                ratingPred1,
                ratingPred2
        );
    }
}
