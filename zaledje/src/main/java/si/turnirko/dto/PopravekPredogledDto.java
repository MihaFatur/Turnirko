/* Kaj bi popravek izida turnirske tekme naredil - izracunano brez zapisa, da se
   organizator odloci, preden se karkoli spremeni. Izbire so prazne, kadar
   odlocitve ni treba: takrat vmesnik samo potrdi zamenjavo zmagovalca. */
package si.turnirko.dto;

import java.util.List;

import si.turnirko.modeli.IzidTekme;
import si.turnirko.modeli.NacinPopravka;

public record PopravekPredogledDto(
        boolean zamenjanZmagovalec,
        String stariZmagovalec,
        String novZmagovalec,
        List<NacinPopravka> izbire,
        /* Mreza oz. zreb: tekme z vnesenim izidom, ki stojijo na tej tekmi. */
        List<TekmaPosledice> odvisneZIzidom,
        /* Mreza oz. zreb: tekme brez izida, v katerih se igralec samo zamenja. */
        int odvisnihBrezIzida,
        /* Skupina: del, ki je nastal iz njenega vrstnega reda; null, kadar se ga
           popravek ne dotakne. */
        NaslednjiDel naslednjiDel,
        boolean dogodekZakljucen,
        boolean stejeVRating) {

    public record TekmaPosledice(
            Long id,
            String oznaka,
            String udelezenec1,
            String udelezenec2,
            int nizi1,
            int nizi2,
            IzidTekme izidTip) {}

    public enum VrstaDela { ZREBI, IZLOCILNI, FINALNE_SKUPINE }

    public record NaslednjiDel(
            VrstaDela vrsta,
            /* Skupina (nivo) pri sistemu SV regija, sicer null. */
            Integer nivo,
            /* Koliko izidov se izgubi, ce se del podre. */
            int vnesenihTekem,
            List<String> vrstniRedPrej,
            List<String> vrstniRedPotem) {}
}
