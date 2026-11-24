/* Diagnostika branja nad zlatim naborom (24. 11. 2026, samo admin; stran Meritev
   branja): zakaj bralnik na pravih listkih bere slabse, kot kazejo preizkusne
   stevke. Pomen stevil: docs/pravila/branje-listkov.md, razdelek »Diagnostika«.

   preizkusni = listki, katerih stevke NISO v ucenju (vsak peti po id branja, ista
   razdelitev kot UcenjeMreze.jePreizkusna) - pisava, ki je mreza ni videla; ucni =
   ostali. Primerjava obeh pove, koliko meritev nabora olepsa to, da se je mreza
   na vecini listkov ze ucila. */
package si.turnirko.dto;

import java.util.List;

public record DiagnozaNaboraDto(Del preizkusni, Del ucni) {

    /* listkov in celic sta izmerjena; napacnih = celic z drugacno vrednostjo od
       potrjene. vzroki = napacne celice po vzroku (tihe = niso bile oznacene za
       pregled). stevke = mreza na stevkah teh listkov, izrezanih kot za ucenje.
       umerjenost = celice po razredu zaupanja: mreza sama (pred pravili) in koncno
       branje (po pravilih in pogostosti nizov). napake = galerija (najvec
       DiagnozaBranja.NAJVEC_NAPAK, samo pri preizkusnih). */
    public record Del(int listkov, int celic, int napacnih, List<Vzrok> vzroki, Stevke stevke,
                      List<Razred> umerjenostMreze, List<Razred> umerjenostKoncna, List<Napaka> napake) {}

    /* vzrok: NI_CRNILA (razrez v celici ni nasel zapisa), CRNILO_V_PRAZNI (crnilo v
       celici, ki je prazna), RAZREZ (nobena razlaga razreza nima pravega stevila
       stevk), MREZA (mreza je stevko prebrala narobe), IZBIRA_RAZREZA (mreza bi
       prebrala prav, a je obveljala druga razlaga razreza), PRAVILA (mreza je
       celico prebrala prav, pravila ali pogostost nizov so izbrala drugo). */
    public record Vzrok(String vzrok, int tihih, int ujetih) {}

    /* celicZZapisom = celice s potrjeno vrednostjo in crnilom; brezRazreza = od
       teh tiste, ki jih razrez ne razdeli na pravo stevilo stevk (v ucenje in med
       preizkusne stevke ne pridejo nikoli); stevk/prav = stevke ostalih celic. */
    public record Stevke(int celicZZapisom, int brezRazreza, int stevk, int prav, List<PoStevki> poStevkah) {}

    public record PoStevki(int stevka, int vseh, int prav, List<NapakeMrezeDto.Zamenjava> zamenjave) {}

    /* Celice z zaupanjem v [od, doMeje) in koliko jih je bilo prav. */
    public record Razred(double od, double doMeje, int celic, int prav) {}

    /* Napacna celica: niz (od 1), stran levo/desno, slika = PNG izrez celice (base64). */
    public record Napaka(int niz, String stran, Integer napisano, Integer prebrano, String vzrok, boolean tiha,
                         double zaupanje, String slika) {}
}
