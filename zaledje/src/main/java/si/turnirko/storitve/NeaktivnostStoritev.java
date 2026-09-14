/* Uveljavljanje odbitkov za neaktivnost.

   Pravilo samo (koliko in kdaj) zivi v Neaktivnost; tu je le zapis v bazo.
   Odbitek se uveljavi na dveh mestih, ker mora delovati v obeh primerih:

     1) KO SE IGRALEC VRNE - obracun tekme najprej poplaca premor, sele nato
        obracuna tekmo. Tako je rating na tekmi ze popravljen in nasprotnik
        igra proti pravi stevilki.

     2) SPROTI, TUDI CE SE NE VRNE - dnevno opravilo (in gumb v upravljanju)
        uveljavi vse, kar je zapadlo. Brez tega bi igralec, ki je nehal igrati,
        na javni lestvici drzal mesto s stevilko izpred treh let.

   Obe poti gresta skozi isto metodo, zato se odbitek ne more zapisati dvakrat
   in ne more nastati razlika med "lestvica danes" in "lestvica po preracunu". */
package si.turnirko.storitve;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.modeli.RatingStanje;
import si.turnirko.modeli.RatingZgodovina;
import si.turnirko.modeli.RazlogSpremembe;
import si.turnirko.repozitoriji.RatingStanjeRepozitorij;
import si.turnirko.repozitoriji.RatingZgodovinaRepozitorij;

@Service
public class NeaktivnostStoritev {

    /* Rating nikoli ne pade pod to mejo - isto kot pri obracunu tekme. */
    private static final int SPODNJA_MEJA = 100;

    private final RatingStanjeRepozitorij stanjeRepozitorij;
    private final RatingZgodovinaRepozitorij zgodovinaRepozitorij;

    public NeaktivnostStoritev(RatingStanjeRepozitorij stanjeRepozitorij,
                               RatingZgodovinaRepozitorij zgodovinaRepozitorij) {
        this.stanjeRepozitorij = stanjeRepozitorij;
        this.zgodovinaRepozitorij = zgodovinaRepozitorij;
    }

    /* Kaj je opravilo naredilo. */
    public record Porocilo(int prizadetihIgralcev, int zapisanihOdbitkov, int odbitihTock) {}

    /* Uveljavi vse odbitke, ki so zapadli do danega trenutka - za vse igralce.
       Uporabljata ga dnevno opravilo in ponovni preracun. */
    @Transactional
    public Porocilo uveljaviVse(LocalDateTime doKdaj) {
        int igralcev = 0, zapisov = 0, tock = 0;
        for (RatingStanje stanje : stanjeRepozitorij.findBySistem(RatingStanje.SISTEM_TURNIRKO)) {
            int odbito = uveljavi(stanje, doKdaj);
            if (odbito > 0) {
                igralcev++;
                tock += odbito;
                zapisov += Neaktivnost.zapadli(stanje.svezOb(), doKdaj, 0).size();
            }
        }
        return new Porocilo(igralcev, zapisov, tock);
    }

    /* Uveljavi zapadle odbitke enemu igralcu; vrne skupno odbito stevilo tock.
       Klicalec mora stanje shraniti (pri obracunu tekme se to zgodi tako ali
       tako, pri dnevnem opravilu shranimo tu). */
    @Transactional
    public int uveljavi(RatingStanje stanje, LocalDateTime doKdaj) {
        /* Premor se meri od zadnjega PODATKA o igralcu, ne od zadnje tekme:
           zunanja uvrstitev je prav tako sveza informacija in stevilki, ki smo
           jo pravkar prepisali z ITTF lestvice, ni mogoce odbiti 40 tock za
           odsotnost, ki jo ta stevilka ze uposteva (glej RatingStanje.svezOb). */
        LocalDateTime zadnja = stanje.svezOb();
        if (zadnja == null || zadnja.equals(VrstaRatinskeTekme.BREZ_DATUMA)) {
            return 0; // brez znanega datuma zadnjega podatka premora ni mogoce meriti
        }
        int zeUveljavljenih = (int) zgodovinaRepozitorij.steviloOdbitkovPo(
                RatingStanje.SISTEM_TURNIRKO, stanje.getIgralec().getId(), zadnja);
        List<Neaktivnost.Odbitek> odbitki =
                Neaktivnost.zapadli(zadnja, doKdaj, zeUveljavljenih);
        if (odbitki.isEmpty()) {
            return 0;
        }

        int skupaj = 0;
        for (Neaktivnost.Odbitek odbitek : odbitki) {
            int nova = Math.max(SPODNJA_MEJA, stanje.getVrednost() - odbitek.tock());
            int sprememba = nova - stanje.getVrednost();
            stanje.setVrednost(nova);
            skupaj += -sprememba;
            zgodovinaRepozitorij.save(new RatingZgodovina(
                    stanje.getIgralec(), RatingStanje.SISTEM_TURNIRKO,
                    sprememba, nova, odbitek.velja(), RazlogSpremembe.NEAKTIVNOST));
        }
        stanjeRepozitorij.save(stanje);
        return skupaj;
    }

    /* Dnevno ob 3.15 zjutraj: takrat nihce ne vnasa rezultatov, odbitki pa so
       vezani na datum in ne na uro, zato tocna ura ni pomembna. */
    @Scheduled(cron = "0 15 3 * * *")
    public void dnevnoOpravilo() {
        uveljaviVse(LocalDateTime.now());
    }
}
