/* Pravila za tocke po nizih. Loceno od obeh storitev zato, ker za turnirsko
   (TekmaStoritev) in ligasko tekmo (SrecanjeStoritev) veljajo ISTA pravila -
   niz je niz, ne glede na to, v kaksnem tekmovanju se igra. Dve kopiji
   preverbe bi se scasoma razsli in bi ligaski zapisnik sprejel vnos, ki ga
   turnirski zavrne. */
package si.turnirko.storitve;

import java.util.List;

import si.turnirko.dto.NizVnos;
import si.turnirko.izjeme.NeveljavenVnosIzjema;

public final class NiziPravila {

    private NiziPravila() {}

    /* Preveri vnesene nize glede na koncni izid tekme (dobljeni nizi obeh
       strani). Ne shrani nicesar: preveri se VSE, sele nato klicatelj pise,
       da ob napacnem vnosu v bazi ne ostanejo delni zapisi.

       "zaZmago" je stevilo nizov, potrebnih za zmago (npr. 3 pri "najboljsi
       od 5"); iz njega izhaja pravilo o vrstnem redu nizov. */
    public static void preveri(List<NizVnos> nizi, int dobljeni1, int dobljeni2, int zaZmago) {
        if (nizi.size() != dobljeni1 + dobljeni2) {
            throw new NeveljavenVnosIzjema("Stevilo vnesenih nizov (" + nizi.size()
                    + ") se ne ujema z rezultatom " + dobljeni1 + ":" + dobljeni2 + ".");
        }

        int steti1 = 0;
        int steti2 = 0;
        int zaporedna = 1;
        for (NizVnos niz : nizi) {
            preveriTockeNiza(niz, zaporedna);
            // Tekma se konca v trenutku, ko eden dobi dovolj nizov - noben niz
            // se ne igra po tem. Vnos kot 11:4, 11:7, 11:8, 8:11 pri izidu 3:1
            // je torej nemogoc: zmagovalec je imel 3 nize ze po tretjem nizu
            // (izid 3:0) in cetrti niz se ne bi igral.
            if (steti1 == zaZmago || steti2 == zaZmago) {
                throw new NeveljavenVnosIzjema("Tekma je bila odlocena ze po " + (zaporedna - 1)
                        + " nizih (izid " + steti1 + ":" + steti2 + "), zato se " + zaporedna
                        + ". niz ne bi igral. Popravi vnos nizov.");
            }
            if (niz.tocke1() > niz.tocke2()) {
                steti1++;
            } else {
                steti2++;
            }
            zaporedna++;
        }
        if (steti1 != dobljeni1 || steti2 != dobljeni2) {
            throw new NeveljavenVnosIzjema("Tocke po nizih dajo rezultat " + steti1 + ":"
                    + steti2 + ", vnesen pa je " + dobljeni1 + ":" + dobljeni2 + ".");
        }
    }

    /* Pravila namiznoteniskega niza: do 11 tock, z razliko vsaj 2;
       pri podaljsani igri (nad 11) je razlika natanko 2. */
    private static void preveriTockeNiza(NizVnos niz, int zaporedna) {
        int vec = Math.max(niz.tocke1(), niz.tocke2());
        int manj = Math.min(niz.tocke1(), niz.tocke2());
        boolean veljaven = manj >= 0
                && ((vec == 11 && manj <= 9) || (vec > 11 && vec - manj == 2));
        if (!veljaven) {
            throw new NeveljavenVnosIzjema("Niz " + zaporedna + " (" + niz.tocke1() + ":"
                    + niz.tocke2() + ") ni veljaven namiznoteniski rezultat.");
        }
    }
}
