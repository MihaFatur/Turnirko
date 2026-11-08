/* Izracun enega kroga v skupini: kaj je izenacene razvrstilo in s katerimi
   stevilkami. Gledalec ga odpre z gumbom pod lestvico (IzracunKrogaOkno).

   Nastane SPROTI med razvrscanjem (RazvrstitevStoritev) in se ne racuna znova:
   druga kopija pravil v vmesniku bi se scasoma razsla s tistim, kar je vrstni
   red res dolocilo, razlaga pa bi potem trdila nekaj, cesar lestvica ni storila.

   Clan nosi samo merila, ki so zanj res stela. Zato izpis ostane kratek: kjer
   so odlocile zmage, nizov ni; kjer so odlocili nizi, tock ni. Stevilke veljajo
   za tekme med igralci, ki so bili na tistem koraku se izenaceni, in nikoli za
   celo skupino. */
package si.turnirko.dto;

import java.util.List;

import si.turnirko.modeli.IzidTekme;

public record KrogDto(
        int odMesta,
        int doMesta,
        /* Prvo merilo, ki je CEL krog razbilo; null, ce je med clani vse enako. */
        Merilo odlocilo,
        /* Koliko medsebojnih zmag ima vsak, kadar zmage kroga ne razbijejo. */
        Integer enakeZmage,
        /* Ali so odigrane vse tekme skupine - sicer se izracun lahko se spremeni. */
        boolean vseTekmeOdigrane,
        /* Pri merilu tock je vsaj ena tekma brez vpisanih tock po nizih. */
        boolean tockeManjkajo,
        /* Clani po mestih, od najboljsega navzdol. */
        List<Clan> clani,
        /* Izenaceni, ki jih medsebojne tekme niso razsodile. */
        List<Opomba> opombe,
        /* Vse odigrane tekme med clani - iz njih je izracun nastal. */
        List<TekmaKroga> tekme
) {

    /* TEKMA je izid dvoboja dveh, ki sta ostala izenacena; samo v medNjimi. */
    public enum Merilo { ZMAGE, NIZI, TOCKE, TEKMA }

    public record Izkupicek(int za, int proti) {}

    /* Eden od korakov po prvem razrezu, ko se merilo ponovi samo se med
       izenacenimi (ZMAGE: za = zmage, proti = porazi). Izid ni prazen samo pri
       TEKMA, ki ni bila odigrana do konca (predaja, brez boja): tam zmagovalec
       nima nujno vec nizov in brez oznake bi bil videti kot napaka. */
    public record Korak(Merilo merilo, int za, int proti, IzidTekme izid) {}

    public record Clan(
            Long idPrijave,
            String ime,
            int mesto,
            Integer zmage,
            Izkupicek nizi,
            Izkupicek tocke,
            List<Korak> medNjimi
    ) {}

    /* Merila, ki pridejo v igro sele, ko medsebojne tekme ne razsodijo. */
    public enum ZadnjeMerilo { RAZLIKA_NIZOV, DOBLJENI_NIZI, JAKOSTNO_MESTO, ABECEDA }

    /* Vrednost je prazna pri abecedi in pri igralcu brez jakostnega mesta. */
    public record Vrednost(String ime, Integer vrednost) {}

    public record Odlocitev(ZadnjeMerilo merilo, List<Vrednost> vrednosti) {}

    /* brezTekem: izenaceni med seboj se niso igrali (samo sredi skupine). */
    public record Opomba(boolean brezTekem, List<Odlocitev> odlocitve) {}

    public record TekmaKroga(
            String ime1,
            String ime2,
            int nizi1,
            int nizi2,
            boolean prviZmagal,
            IzidTekme izid
    ) {}
}
