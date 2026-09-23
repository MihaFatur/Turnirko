/* Registracija s placljivim paketom (Premium igralec ali kateri koli
   organizatorski paket). Racun v tabeli uporabnik ne nastane tu - nastane
   sele, ko Stripe webhook potrdi placilo (PlacilaStoritev.zacniRegistracijskoPlacilo,
   RegistracijaStoritev.registrirajPoPlacilu). BREZPLACNO gre po obicajni poti
   (authApi.registracija) in te poti ne uporablja. */
package si.turnirko.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import si.turnirko.modeli.CiklusPlacila;
import si.turnirko.modeli.Paket;

public record PlacanaRegistracijaVnos(
        @Valid @NotNull RegistracijaVnos racun,

        @NotNull(message = "paket je obvezen") Paket paket,

        /* Obvezen pri PREMIUM (mesecno/letno); organizatorski paketi so samo
           letni, zato ga CenikStoritev tam lahko tudi ignorira. */
        CiklusPlacila ciklus
) {}
