/* Kanal, po katerem gre e-posta ven. Izvedbo izbere nastavitev
   turnirko.posta.nacin: smtp (pravi streznik), dnevnik (razvoj brez SMTP -
   besedilo v dnevnik) ali pomnilnik (testi berejo poslano). Storitve
   posiljatelja ne klicejo neposredno, ampak prek PostaStoritev, ki
   posiljanje odlozi do potrditve transakcije. */
package si.turnirko.posta;

public interface PosiljateljPoste {

    void poslji(String prejemnik, String zadeva, String besedilo);

    /* Ali naj PostaStoritev posiljanje odlozi do potrditve transakcije. Pravi
       kanali da (koda za razveljavljen racun ne sme ven); testni pomnilnik ne,
       ker testna transakcija nikoli ne potrdi in bi sporocilo ostalo neposlano. */
    default boolean odloziDoPotrditve() {
        return true;
    }
}
