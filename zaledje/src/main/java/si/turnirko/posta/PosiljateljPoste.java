/* Kanal, po katerem gre e-posta ven. Izvedbo izbere nastavitev
   turnirko.posta.nacin: smtp (pravi streznik), dnevnik (razvoj brez SMTP -
   besedilo v dnevnik) ali pomnilnik (testi berejo poslano). Storitve
   posiljatelja ne klicejo neposredno, ampak prek PostaStoritev, ki
   posiljanje odlozi do potrditve transakcije. */
package si.turnirko.posta;

public interface PosiljateljPoste {

    void poslji(String prejemnik, String zadeva, String besedilo);

    /* Sporocilo z morebitnim HTML telesom in glavami. Kanal, ki HTML ne zna,
       poslje samo navadno besedilo. */
    default void poslji(String prejemnik, SporocilaPoste.Besedilo besedilo) {
        poslji(prejemnik, besedilo.zadeva(), besedilo.telo());
    }

    /* Sinhrona dostava z izidom, za mnozicna obvestila (DostavaObvestilStoritev):
       klicalec pocaka na odgovor streznika in prejemnika zapise kot urejenega
       sele po njem. Brez ponavljanja - ponovi klicalec ob naslednjem zagonu
       opravila. Kanal, ki ne more odpovedati (dnevnik), sporocilo samo odda. */
    default IzidDostave dostavi(String prejemnik, SporocilaPoste.Besedilo besedilo) {
        poslji(prejemnik, besedilo);
        return IzidDostave.POSLANO;
    }

    /* Ali naj PostaStoritev posiljanje odlozi do potrditve transakcije. Pravi
       kanali da (koda za razveljavljen racun ne sme ven); testni pomnilnik ne,
       ker testna transakcija nikoli ne potrdi in bi sporocilo ostalo neposlano. */
    default boolean odloziDoPotrditve() {
        return true;
    }
}
