/* Vsi repozitoriji, ki jih uvoz dogodka potrebuje, na enem mestu - preslikave
   nastajajo za vsak zagon posebej in bi sicer vsaka nosila dvajset parametrov
   konstruktorja. */
package si.turnirko.uvoz.stupa;

import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManager;

import si.turnirko.repozitoriji.DogodekRepozitorij;
import si.turnirko.repozitoriji.EkipaRepozitorij;
import si.turnirko.repozitoriji.IgralecRepozitorij;
import si.turnirko.repozitoriji.KaderEkipeRepozitorij;
import si.turnirko.repozitoriji.KlubRepozitorij;
import si.turnirko.repozitoriji.LigaRepozitorij;
import si.turnirko.repozitoriji.NizRepozitorij;
import si.turnirko.repozitoriji.NizSrecanjaRepozitorij;
import si.turnirko.repozitoriji.PostavaSrecanjaRepozitorij;
import si.turnirko.repozitoriji.PrijavaRepozitorij;
import si.turnirko.repozitoriji.SerijaKoncniceRepozitorij;
import si.turnirko.repozitoriji.SkupinaRepozitorij;
import si.turnirko.repozitoriji.SrecanjeRepozitorij;
import si.turnirko.repozitoriji.TekmaRepozitorij;
import si.turnirko.repozitoriji.TekmaSrecanjaRepozitorij;
import si.turnirko.repozitoriji.TurnirRepozitorij;
import si.turnirko.repozitoriji.ZunanjaPovezavaRepozitorij;

@Component
public record RepozitorijiUvoza(
        EntityManager em,
        TurnirRepozitorij turnirji,
        DogodekRepozitorij dogodki,
        SkupinaRepozitorij skupine,
        PrijavaRepozitorij prijave,
        TekmaRepozitorij tekme,
        NizRepozitorij nizi,
        IgralecRepozitorij igralci,
        KlubRepozitorij klubi,
        LigaRepozitorij lige,
        EkipaRepozitorij ekipe,
        KaderEkipeRepozitorij kadri,
        SrecanjeRepozitorij srecanja,
        PostavaSrecanjaRepozitorij postave,
        TekmaSrecanjaRepozitorij tekmeSrecanj,
        NizSrecanjaRepozitorij niziSrecanj,
        SerijaKoncniceRepozitorij serije,
        ZunanjaPovezavaRepozitorij povezave
) {}
