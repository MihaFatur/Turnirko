/* Koda popusta v admin seznamu (KodePopustaKontroler). Ugodnost je razlozena
   v poljih (vmesnik jo sestavi v besedilo), stanje pa izracuna streznik, da ga
   vmesnik ne ugiba iz datuma in stevil. */
package si.turnirko.dto;

import java.time.LocalDate;

import si.turnirko.modeli.KodaPopusta;
import si.turnirko.modeli.Paket;
import si.turnirko.modeli.VrstaKodePopusta;
import si.turnirko.modeli.Vloga;

public record KodaPopustaDto(
        Long id,
        String koda,
        String opis,
        VrstaKodePopusta vrsta,
        Integer meseci,
        Integer odstotek,
        Paket paket,
        Vloga zaVlogo,
        LocalDate veljaDo,
        Integer najvecUporab,
        boolean aktivna,
        Stanje stanje,
        /* Racuni, ki so kodo ze uporabili (nastali racun). */
        long uporab,
        /* Placljive registracije, ki kodo drzijo, dokler placilo ni potrjeno. */
        long cakajocih,
        LocalDate ustvarjena
) {

    /* VELJA: se sprejme; IZKLJUCENA: admin jo je ugasnil; POTEKLA: rok je minil;
       IZCRPANA: mest ni vec (uporabe + cakajoce dosegajo mejo). */
    public enum Stanje { VELJA, IZKLJUCENA, POTEKLA, IZCRPANA }

    public static KodaPopustaDto iz(KodaPopusta k, long uporab, long cakajocih, LocalDate danes) {
        Stanje stanje;
        if (!k.isAktivna()) {
            stanje = Stanje.IZKLJUCENA;
        } else if (k.jePotekla(danes)) {
            stanje = Stanje.POTEKLA;
        } else if (k.getNajvecUporab() != null && uporab + cakajocih >= k.getNajvecUporab()) {
            stanje = Stanje.IZCRPANA;
        } else {
            stanje = Stanje.VELJA;
        }
        return new KodaPopustaDto(k.getId(), k.getKoda(), k.getOpis(), k.getVrsta(), k.getMeseci(),
                k.getOdstotek(), k.getPaket(), k.getZaVlogo(), k.getVeljaDo(), k.getNajvecUporab(),
                k.isAktivna(), stanje, uporab, cakajocih, k.getUstvarjenaOb().toLocalDate());
    }
}
