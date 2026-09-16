/* Tekma - izpis za prikaz mreze. */
package si.turnirko.dto;

import java.util.List;

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
        /* Tocke po nizih po vrsti (11:7, 9:11 ...); prazen seznam, kadar jih
           organizator ni vpisal - vnos je neobvezen. tocke1 so strani 1. */
        List<NizVnos> nizi,
        Long idZmagovalcaPrijave,
        Integer miza,
        /* Sprememba Turnirko ratinga ob tej tekmi (npr. +16 / -16); null,
           ce tekma se ni obracunana (npr. prosti prehod ali nedokoncana). */
        Integer spremembaElo1,
        Integer spremembaElo2,
        /* Rating igralca PRED to tekmo: pri odigrani tekmi zgodovinski
           (pred obracunom), sicer trenutni; null, ce igralec se nima ratinga. */
        Integer ratingPred1,
        Integer ratingPred2,
        /* Ekipna tekma (V28): srecanje z zapisnikom; prazno pri tekmi
           posameznikov in pri ekipni tekmi, ki se ni igrala (brez boja). */
        Long idSrecanje,
        /* Prenesen izid iz predtekmovanja (finalna skupina za mesta): tekma,
           katere izid nosi. Vmesnik jo oznaci kot preneseno in ne kot
           odigrano. */
        Long idPrenesena
) {

    /* Udelezenec tekme (stran 1 ali 2) - igralec, PAR ali EKIPA.
       polnoIme2/klub2 sta zapolnjena samo pri dvojicah; vmesnik iz njiju
       sestavi dvovrsticni zapis na kartici mreze. Pri ekipi je polnoIme ime
       ekipe. */
    public record Udelezenec(Long idPrijave, String polnoIme, String klub,
                             String polnoIme2, String klub2) {

        static Udelezenec iz(Prijava prijava) {
            if (prijava == null) return null;
            return new Udelezenec(
                    prijava.getId(),
                    prijava.jeEkipa() ? prijava.getEkipa().prikazanoIme() : prijava.getIgralec().polnoIme(),
                    prijava.getKlubObPrijavi() != null ? prijava.getKlubObPrijavi().getIme() : null,
                    prijava.jePar() ? prijava.getIgralec2().polnoIme() : null,
                    prijava.getKlubObPrijavi2() != null ? prijava.getKlubObPrijavi2().getIme() : null);
        }
    }

    /* Tekma brez vpisanih tock (npr. pravkar izzrebana). */
    public static TekmaDto iz(Tekma tekma) {
        return iz(tekma, List.of());
    }

    public static TekmaDto iz(Tekma tekma, List<NizVnos> nizi) {
        return iz(tekma, null, null, null, null, null, nizi);
    }

    public static TekmaDto iz(Tekma tekma, Integer spremembaElo1, Integer spremembaElo2,
                              Integer ratingPred1, Integer ratingPred2, Long idSrecanje,
                              List<NizVnos> nizi) {
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
                nizi,
                tekma.getZmagovalec() != null ? tekma.getZmagovalec().getId() : null,
                tekma.getMiza(),
                spremembaElo1,
                spremembaElo2,
                ratingPred1,
                ratingPred2,
                idSrecanje,
                tekma.getIdPrenesena()
        );
    }
}
