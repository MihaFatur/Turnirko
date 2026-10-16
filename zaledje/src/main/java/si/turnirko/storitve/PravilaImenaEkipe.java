/* Pravila imena ekipe pri PREIMENOVANJU - ista za ligo (LigaStoritev) in ekipni
   dogodek turnirja (EkipeDogodkaStoritev), ker ju vmesnik izpise enako.

   Meji 2 in 60 znakov nista izbira: stojita v shemi (CHECK stolpca ekipa.ime) in
   ju je treba zavrniti pred zapisom, sicer organizator namesto sporocila dobi
   napako baze. */
package si.turnirko.storitve;

import java.util.List;

import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.modeli.Ekipa;

final class PravilaImenaEkipe {

    static final int NAJMANJ_ZNAKOV = 2;
    static final int NAJVEC_ZNAKOV = 60;

    private PravilaImenaEkipe() {}

    /* Vneseno besedilo v obliko, ki gre v bazo: odrezano, prazno = brez lastnega
       imena. Prosta ekipa lastnega imena ne more izgubiti - brez kluba bi ostala
       brez poimenovanja. */
    static String preberi(Ekipa ekipa, String vnos) {
        String ime = vnos != null && !vnos.isBlank() ? vnos.trim() : null;
        if (ime == null) {
            if (ekipa.jeProsta()) {
                throw new NeveljavenVnosIzjema("Ekipa brez kluba mora imeti ime.");
            }
            return null;
        }
        if (ime.length() < NAJMANJ_ZNAKOV || ime.length() > NAJVEC_ZNAKOV) {
            throw new NeveljavenVnosIzjema("Ime ekipe mora imeti od " + NAJMANJ_ZNAKOV
                    + " do " + NAJVEC_ZNAKOV + " znakov.");
        }
        return ime;
    }

    /* Ime, kot bi ga ekipa imela z danim lastnim imenom (null = klub in
       zaporedna). */
    static String prikazano(Ekipa ekipa, String lastnoIme) {
        return lastnoIme != null ? lastnoIme : ekipa.getKlub().getIme() + " " + ekipa.getZaporedna();
    }

    /* V razporedu, na lestvici in v zapisniku se ekipi locita samo po
       prikazanem imenu - dve enaki bi bili gledalcu nelocljivi. Primerja se z
       VSEMI drugimi ekipami tekmovanja (tudi cez klubski in prosti tip, brez
       razlike med velikimi in malimi crkami), same ekipe pa ne: ime, ki ga ima
       ekipa ze zdaj, ni trk. */
    static void preveriEnolicno(Ekipa ekipa, String prikazano, List<Ekipa> vse, String kje) {
        boolean trk = vse.stream()
                .filter(e -> !e.getId().equals(ekipa.getId()))
                .anyMatch(e -> e.prikazanoIme().equalsIgnoreCase(prikazano));
        if (trk) {
            throw new DomenskaIzjema("Ekipa \"" + prikazano + "\" " + kje + " ze obstaja.");
        }
    }
}
