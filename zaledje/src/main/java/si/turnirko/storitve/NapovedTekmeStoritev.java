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

    /* Privzeta dolzina tekme: na tri dobljene nize. Tako se igra vecina
       turnirjev in lig (Liga.steviloNizov ima isto privzeto vrednost). */
    public static final int PRIVZETO_NIZOV = 5;

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
    public NapovedTekmeDto napoved(Long idIgralec, Long idNasprotnika, Integer steviloNizov,
                                   String prijavnoIme) {
        dostop.preveriLastnistvo(idIgralec, prijavnoIme);
        if (idNasprotnika == null || idNasprotnika.equals(idIgralec)) {
            throw new NeveljavenVnosIzjema("Za napoved izberi drugega igralca.");
        }
        int nizov = steviloNizov == null ? PRIVZETO_NIZOV : steviloNizov;
        if (nizov != 3 && nizov != 5 && nizov != 7) {
            throw new NeveljavenVnosIzjema("Tekma se igra na 3, 5 ali 7 nizov.");
        }

        LocalDateTime zdaj = LocalDateTime.now();
        Stran jaz = stran(najdiIgralca(idIgralec), zdaj);
        Stran on = stran(najdiIgralca(idNasprotnika), zdaj);

        List<NapovedTekmeDto.Raven> ravni = new ArrayList<>(RAVNI.size());
        for (RavenTekmovanja raven : RAVNI) {
            ravni.add(new NapovedTekmeDto.Raven(raven, raven.getTeza(),
                    izidi(jaz, on, nizov, raven.getTeza())));
        }

        /* Pricakovani izid je odvisen samo od ratingov, zato ga vzamemo iz
           poljubnega izracuna - tu iz najbolj gladke zmage. */
        double pricakovano = turnirkoRating.izracunaj(jaz.stanje(), on.stanje(),
                true, zaZmago(nizov), 0, nizov, false, 1.0).pricakovanaVerjetnost1();

        return new NapovedTekmeDto(
                vDto(jaz), vDto(on), nizov,
                (int) Math.round(pricakovano * 100),
                ravni);
    }

    /* Vse mozne izide ene tekme, urejene od najbolj prepricljive zmage do
       najhujsega poraza: 3:0, 3:1, 3:2, 2:3, 1:3, 0:3. Ena bralna os namesto
       dveh seznamov - gledalec vidi, kako se stevilka spreminja z izidom. */
    private List<NapovedTekmeDto.Izid> izidi(Stran jaz, Stran on, int nizov, double teza) {
        int zaZmago = zaZmago(nizov);
        List<NapovedTekmeDto.Izid> izidi = new ArrayList<>(2 * zaZmago);
        for (int njegovi = 0; njegovi < zaZmago; njegovi++) {
            izidi.add(izid(jaz, on, zaZmago, njegovi, true, nizov, teza));
        }
        for (int moji = zaZmago - 1; moji >= 0; moji--) {
            izidi.add(izid(jaz, on, moji, zaZmago, false, nizov, teza));
        }
        return izidi;
    }

    private NapovedTekmeDto.Izid izid(Stran jaz, Stran on, int mojiNizi, int njegoviNizi,
                                      boolean zmaga, int nizov, double teza) {
        TurnirkoRatingStoritev.Izracun i = turnirkoRating.izracunaj(
                jaz.stanje(), on.stanje(), zmaga, mojiNizi, njegoviNizi, nizov, false, teza);
        return new NapovedTekmeDto.Izid(
                mojiNizi, njegoviNizi, zmaga, i.margina(),
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

    private static int zaZmago(int steviloNizov) {
        return steviloNizov / 2 + 1;
    }

    private Igralec najdiIgralca(Long id) {
        return igralecRepozitorij.najdiZVsem(id)
                .orElseThrow(() -> new NiNajdenoIzjema("Igralec z id " + id + " ne obstaja."));
    }
}
