/* "Koliko bi dobil ali izgubil, ce bi zdaj igral proti njemu."

   Storitev nicesar ne zapise: vzame trenutno stanje obeh igralcev in skoznjo
   spusti isti izracun (TurnirkoRatingStoritev), kot bi ga spustil pravi
   obracun tekme. Zato mora povzeti vse, kar RatingStoritev pripravi PRED
   klicem izracuna - sicer bi napoved in obracun dala razlicni stevilki:

     - stevilo ze odigranih tekem doloca K (novinec ga ima vecjega),
     - sledilnik vrnitve pove, ali bi bila ta tekma prva po vec kot letu dni
       odsotnosti (spet vecji K),
     - igralec brez ratinga vstopi pri STAROSTNEM SIDRU in ne pri 1000.

   Cesar napoved namenoma ne posnema:

     - ODBITKA ZA NEAKTIVNOST ne uveljavlja. Pravi obracun premor poplaca pred
       tekmo, a zapadle odbitke vsem igralcem uveljavi ze dnevno opravilo
       (NeaktivnostStoritev ob 3.15), zato je shranjena stevilka najvec en dan
       stara. Odbitek tu bi bil drugi zapis istega odbitka.
     - UVRSTITVE NOVINCA ne racuna. Kdor igra svoj prvi dan, dobi rating znova
       izracunan iz vseh izidov tega dne in ne po korakih - obrazec koraka
       zanj ne velja. Namesto ugibanja tak igralec dobi opozorilo PRVI_DAN in
       vmesnik to pove naravnost. */
package si.turnirko.storitve;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.NapovedTekmeDto;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.RatingStanje;
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.repozitoriji.IgralecRepozitorij;
import si.turnirko.repozitoriji.RatingStanjeRepozitorij;

@Service
public class NapovedTekmeStoritev {

    /* Ravni, ki se sploh obracunajo. NE_STEJE je izpuscen: tekma, ki ratinga
       ne premakne, v napovedi ni vrstica nicel, ampak je ni. */
    private static final List<RavenTekmovanja> RAVNI = List.of(
            RavenTekmovanja.URADNO, RavenTekmovanja.KLUBSKO, RavenTekmovanja.REKREATIVNO);

    private final IgralecRepozitorij igralecRepozitorij;
    private final RatingStanjeRepozitorij stanjeRepozitorij;
    private final TurnirkoRatingStoritev turnirkoRating;
    private final SidroStoritev sidroStoritev;
    private final DostopDoProfila dostop;

    public NapovedTekmeStoritev(IgralecRepozitorij igralecRepozitorij,
                                RatingStanjeRepozitorij stanjeRepozitorij,
                                TurnirkoRatingStoritev turnirkoRating,
                                SidroStoritev sidroStoritev,
                                DostopDoProfila dostop) {
        this.igralecRepozitorij = igralecRepozitorij;
        this.stanjeRepozitorij = stanjeRepozitorij;
        this.turnirkoRating = turnirkoRating;
        this.sidroStoritev = sidroStoritev;
        this.dostop = dostop;
    }

    /* Vse, kar o eni strani potrebuje izracun, in kar je treba o njej povedati
       gledalcu. */
    private record Stran(Igralec igralec,
                         TurnirkoRatingStoritev.StanjeIgralca stanje,
                         List<NapovedTekmeDto.Opozorilo> opozorila) {}

    /* Napoved za lastnika profila proti izbranemu nasprotniku.
       Dostop ima samo igralec sam ali administrator - isto pravilo kot pri
       zasebnih analizah profila. */
    @Transactional(readOnly = true)
    public NapovedTekmeDto napoved(Long idIgralec, Long idNasprotnika, String prijavnoIme) {
        dostop.preveriLastnistvo(idIgralec, prijavnoIme);
        if (idNasprotnika == null || idNasprotnika.equals(idIgralec)) {
            throw new NeveljavenVnosIzjema("Za napoved izberi drugega igralca.");
        }
        LocalDateTime zdaj = LocalDateTime.now();
        Stran jaz = stran(najdiIgralca(idIgralec), zdaj);
        Stran on = stran(najdiIgralca(idNasprotnika), zdaj);

        List<NapovedTekmeDto.Raven> ravni = new ArrayList<>(RAVNI.size());
        for (RavenTekmovanja raven : RAVNI) {
            ravni.add(new NapovedTekmeDto.Raven(raven, raven.getTeza(),
                    izid(jaz, on, true, raven.getTeza()),
                    izid(jaz, on, false, raven.getTeza())));
        }

        /* Pricakovani izid je odvisen samo od ratingov. */
        double pricakovano = turnirkoRating.pricakovanaVerjetnost(
                jaz.stanje().rating(), on.stanje().rating());

        return new NapovedTekmeDto(
                vDto(jaz), vDto(on),
                (int) Math.round(pricakovano * 100),
                ravni);
    }

    /* Kaj bi zmaga oziroma poraz lastnika profila naredil obema ratingoma.
       Izid v nizih ne vstopa - zmaga je zmaga. */
    private NapovedTekmeDto.Izid izid(Stran jaz, Stran on, boolean zmaga, double teza) {
        TurnirkoRatingStoritev.Izracun i = turnirkoRating.izracunaj(
                jaz.stanje(), on.stanje(), zmaga, teza);
        return new NapovedTekmeDto.Izid(
                i.sprememba1(), i.ratingPo1(),
                i.sprememba2(), i.ratingPo2());
    }

    /* Stanje igralca, kakrsno bi ga v tekmo poneslo ZDAJ. */
    private Stran stran(Igralec igralec, LocalDateTime zdaj) {
        RatingStanje stanje = stanjeRepozitorij
                .findByIgralecIdAndSistem(igralec.getId(), RatingStanje.SISTEM_TURNIRKO)
                .orElse(null);
        List<NapovedTekmeDto.Opozorilo> opozorila = new ArrayList<>(2);

        if (stanje == null) {
            /* Igralec brez ratinga vstopi pri starostnem sidru - isto izhodisce
               kot v RatingStoritev.najdiAliUstvari. */
            opozorila.add(NapovedTekmeDto.Opozorilo.BREZ_RATINGA);
            return new Stran(igralec,
                    TurnirkoRatingStoritev.StanjeIgralca.ustaljeno(
                            sidroStoritev.zacetniRating(igralec, zdaj.toLocalDate()), 0),
                    opozorila);
        }

        /* Sledilnik vrnitve je tu samo prebran, ne shranjen: pove, ali bi
           naslednja tekma ob tem casu se stela med tekme po vrnitvi. */
        boolean vrnitev = new SledilnikVrnitve(
                stanje.getZadnjaTekmaOb(), stanje.getPreostanekVrnitve()).obracunaj(zdaj);

        if (!stanje.isPostavljen() && vPrvemDnevu(stanje, zdaj.toLocalDate())) {
            opozorila.add(NapovedTekmeDto.Opozorilo.PRVI_DAN);
        }
        return new Stran(igralec,
                new TurnirkoRatingStoritev.StanjeIgralca(
                        stanje.getVrednost(), stanje.getStTekem(), vrnitev),
                opozorila);
    }

    /* Ali igralec danes se vedno igra svoj PRVI dan. Takrat rating ne tece po
       korakih, ampak se izracuna znova iz vseh izidov dneva - isto merilo kot
       v RatingStoritev.uvrstiAliObdrzi. */
    private static boolean vPrvemDnevu(RatingStanje stanje, LocalDate danes) {
        LocalDateTime prva = stanje.getPrvaTekmaOb();
        return prva != null && prva.toLocalDate().equals(danes);
    }

    private NapovedTekmeDto.Stran vDto(Stran stran) {
        Igralec i = stran.igralec();
        return new NapovedTekmeDto.Stran(
                i.getId(), i.polnoIme(),
                i.getKlub() != null ? i.getKlub().getIme() : null,
                stran.stanje().rating(),
                stran.stanje().stTekem(),
                turnirkoRating.kFaktor(stran.stanje().stTekem(), stran.stanje().poVrnitvi()),
                stran.stanje().poVrnitvi(),
                stran.opozorila());
    }

    private Igralec najdiIgralca(Long id) {
        return igralecRepozitorij.najdiZVsem(id)
                .orElseThrow(() -> new NiNajdenoIzjema("Igralec z id " + id + " ne obstaja."));
    }
}
