/* Stevilke, s katerimi racuna Turnirko rating - za javno stran z razlago.

   Stran jih ne zapise na roko: K, pragovi, teze in odbitki se ob umeritvi
   spremenijo (K je bil do oktobra 2026 40), razlaga pa mora vedno govoriti o
   pravilu, po katerem obracun res tece. Zato pridejo iz istih konstant, ki jih
   bere obracun. Osebnih podatkov ni - sidra so mediane po starosti. */
package si.turnirko.dto;

import java.util.List;

import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.modeli.Spol;

public record PravilaRatingaDto(
        // K = osnova + pribitki za negotovost (sestevajo se)
        int kOsnovni,
        int pribitekNeustaljen,
        int pragUstaljen,
        int pribitekNovinec,
        int pragNovinec,
        int pribitekVrnitev,
        int mesecevZaVrnitev,
        int tekemPoVrnitvi,
        List<Raven> ravni,
        List<Odbitek> odbitki,
        int mesecevDoSkritja,
        int rekreativniZacetek,
        int navideznihTekem,
        int pragRekreativca,
        int spodnjaMeja,
        // pricakovana verjetnost zmage pri dani razliki ratingov
        List<Napoved> napovedi,
        // kje zacne novinec dane starosti (starostno sidro)
        List<Sidro> sidra
) {

    public record Raven(RavenTekmovanja raven, double teza) {}

    /* Po toliko mesecih brez tekme je skupaj odbitih toliko tock. */
    public record Odbitek(int mesecev, int skupaj) {}

    public record Napoved(int razlika, int odstotek) {}

    public record Sidro(Spol spol, int starost, int vrednost) {}
}
