/* Telo obnove preklicane narocnine (POST /api/v1/narocnina/obnova). Obe polji
   sta neobvezni: brez njiju se obnova nadaljuje z ze zacetim paketom in
   ciklom. `ciklus` je za Premium (preklop placevanja ob obnovi), `paket` za
   organizatorja (drug paket ob obnovi). */
package si.turnirko.dto;

import si.turnirko.modeli.CiklusPlacila;
import si.turnirko.modeli.Paket;

public record NarocninaObnovaVnos(CiklusPlacila ciklus, Paket paket) {}
