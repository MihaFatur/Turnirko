/* Javna razlaga Turnirko ratinga: stevilke pravil in dva preizkusa.

   Gledalec, ki je po enem porazu izgubil 113 tock, iz stevilke ne razbere,
   zakaj. Stran z razlago mu to pove in mu da preizkusiti z lastnimi
   stevilkami. Obljuba je ena: preizkus mora dati natanko tisto, kar bi dal
   obracun - zato tu ni lastnega obrazca, ampak isti razredi kot pri obracunu
   (TurnirkoRatingStoritev za korak, UvrstitevNovinca za prvi dan, Neaktivnost
   za odbitke), igralca pa sta izmisljena in ne prideta iz baze. Test
   prvega dne isti dan najprej preizkusi in nato zares odigra.

   Nic se ne zapise in nic ni osebnega - sidra so mediane po starosti. */
package si.turnirko.storitve;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import si.turnirko.dto.IzracunTekmeDto;
import si.turnirko.dto.NapovedTekmeDto;
import si.turnirko.dto.PravilaRatingaDto;
import si.turnirko.dto.PrviDanNovincaDto;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.modeli.Spol;

@Service
public class RazlagaRatingaStoritev {

    /* Ravni, ki rating premaknejo - NE_STEJE v preizkusu nima vrstice. */
    private static final List<RavenTekmovanja> RAVNI = List.of(
            RavenTekmovanja.URADNO, RavenTekmovanja.KLUBSKO, RavenTekmovanja.REKREATIVNO);

    /* Razlike, pri katerih stran pokaze verjetnost zmage. */
    private static final int[] RAZLIKE = { 0, 50, 100, 200, 300, 400 };

    /* Starosti, za katere stran pokaze sidro (od U11 do odraslih). */
    private static final int[] STAROSTI = { 10, 12, 14, 16, 18, 21, 30 };

    /* Prvi dan ima v praksi do deset tekem (turnir s skupino in mrezo);
       meja varuje strezek pred dolgimi naslovi zahtev. */
    private static final int NAJVEC_TEKEM_PRVEGA_DNE = 12;

    /* Najvec tekem, ki jih preizkus sprejme kot "odigrane" - dlje K ne raste. */
    private static final int NAJVEC_TEKEM = 100_000;

    private final TurnirkoRatingStoritev turnirkoRating;
    private final SidroStoritev sidroStoritev;

    public RazlagaRatingaStoritev(TurnirkoRatingStoritev turnirkoRating, SidroStoritev sidroStoritev) {
        this.turnirkoRating = turnirkoRating;
        this.sidroStoritev = sidroStoritev;
    }

    public PravilaRatingaDto pravila() {
        List<PravilaRatingaDto.Raven> ravni = RAVNI.stream()
                .map(r -> new PravilaRatingaDto.Raven(r, r.getTeza()))
                .toList();
        List<PravilaRatingaDto.Odbitek> odbitki = Neaktivnost.stopnje().stream()
                .map(s -> new PravilaRatingaDto.Odbitek(s.mesecev(), s.skupaj()))
                .toList();
        List<PravilaRatingaDto.Napoved> napovedi = new ArrayList<>();
        for (int razlika : RAZLIKE) {
            double p = turnirkoRating.pricakovanaVerjetnost(1000 + razlika, 1000);
            napovedi.add(new PravilaRatingaDto.Napoved(razlika, (int) Math.round(p * 100)));
        }
        List<PravilaRatingaDto.Sidro> sidra = new ArrayList<>();
        for (Spol spol : Spol.values()) {
            for (int starost : STAROSTI) {
                Integer vrednost = sidroStoritev.sidro(spol, starost);
                if (vrednost != null) {
                    sidra.add(new PravilaRatingaDto.Sidro(spol, starost, vrednost));
                }
            }
        }
        return new PravilaRatingaDto(
                TurnirkoRatingStoritev.K_OSNOVNI,
                TurnirkoRatingStoritev.K_PRIBITEK_NEUSTALJEN, TurnirkoRatingStoritev.PRAG_USTALJEN,
                TurnirkoRatingStoritev.K_PRIBITEK_NOVINEC, TurnirkoRatingStoritev.PRAG_PROVIZORICNI,
                TurnirkoRatingStoritev.K_PRIBITEK_VRNITEV, TurnirkoRatingStoritev.MESECEV_ZA_VRNITEV,
                TurnirkoRatingStoritev.TEKEM_PO_VRNITVI,
                ravni, odbitki, Neaktivnost.MESECEV_DO_SKRITJA,
                SidroStoritev.REKREATIVNI_ZACETEK, UvrstitevNovinca.NAVIDEZNE_TEKME,
                RekreativecStoritev.PRAG_TEKEM, TurnirkoRatingStoritev.SPODNJA_MEJA,
                napovedi, sidra);
    }

    /* Ena tekma dveh izmisljenih igralcev na vseh treh ravneh. Igralca sta
       ustaljena (ne novinca v prvem dnevu): to je navaden korak, prvi dan ima
       svoj preizkus. */
    public IzracunTekmeDto izracun(int rating, int nasprotnik, int tekem, int tekemNasprotnika,
                                   boolean vrnitev, boolean vrnitevNasprotnika) {
        preveriRating(rating);
        preveriRating(nasprotnik);
        preveriTekme(tekem);
        preveriTekme(tekemNasprotnika);

        var jaz = new TurnirkoRatingStoritev.StanjeIgralca(rating, tekem, vrnitev);
        var on = new TurnirkoRatingStoritev.StanjeIgralca(nasprotnik, tekemNasprotnika, vrnitevNasprotnika);

        List<NapovedTekmeDto.Raven> ravni = new ArrayList<>(RAVNI.size());
        for (RavenTekmovanja raven : RAVNI) {
            ravni.add(new NapovedTekmeDto.Raven(raven, raven.getTeza(),
                    izid(jaz, on, true, raven.getTeza()),
                    izid(jaz, on, false, raven.getTeza())));
        }
        return new IzracunTekmeDto(
                (int) Math.round(turnirkoRating.pricakovanaVerjetnost(rating, nasprotnik) * 100),
                turnirkoRating.kFaktor(tekem, vrnitev),
                turnirkoRating.kFaktor(tekemNasprotnika, vrnitevNasprotnika),
                ravni);
    }

    private NapovedTekmeDto.Izid izid(TurnirkoRatingStoritev.StanjeIgralca jaz,
                                      TurnirkoRatingStoritev.StanjeIgralca on, boolean zmaga, double teza) {
        TurnirkoRatingStoritev.Izracun i = turnirkoRating.izracunaj(jaz, on, zmaga, teza);
        return new NapovedTekmeDto.Izid(i.sprememba1(), i.ratingPo1(), i.sprememba2(), i.ratingPo2());
    }

    /* Prvi dan novinca, tekma za tekmo - po istem pravilu kot obracun
       (RatingStoritev.uvrstiAliObdrzi): prva tekma je navaden korak od
       izhodisca (K novinca), od druge naprej pa se rating izracuna znova iz
       vseh izidov dneva, potegnjen proti izhodiscu. Nasprotniki so ustaljeni
       igralci z dano stevilko pred tekmo. Teza tekmovanja velja samo za prvi
       korak - uvrstitev je ocena moci, ne korak. */
    public PrviDanNovincaDto prviDan(int izhodisce, RavenTekmovanja raven,
                                     List<Integer> nasprotniki, List<Boolean> zmage) {
        preveriRating(izhodisce);
        if (nasprotniki == null || zmage == null || nasprotniki.size() != zmage.size()) {
            throw new NeveljavenVnosIzjema("Vsaka tekma potrebuje rating nasprotnika in izid.");
        }
        if (nasprotniki.isEmpty() || nasprotniki.size() > NAJVEC_TEKEM_PRVEGA_DNE) {
            throw new NeveljavenVnosIzjema("Preizkus sprejme od 1 do "
                    + NAJVEC_TEKEM_PRVEGA_DNE + " tekem.");
        }
        if (raven == null || raven == RavenTekmovanja.NE_STEJE) {
            throw new NeveljavenVnosIzjema("Raven tekmovanja mora biti uradna, klubska ali rekreativna.");
        }
        nasprotniki.forEach(RazlagaRatingaStoritev::preveriRating);

        List<UvrstitevNovinca.Izid> izidi = new ArrayList<>();
        List<PrviDanNovincaDto.Korak> koraki = new ArrayList<>();
        int rating = izhodisce;
        for (int i = 0; i < nasprotniki.size(); i++) {
            int nasprotnik = nasprotniki.get(i);
            boolean zmaga = zmage.get(i);
            TurnirkoRatingStoritev.Izracun korak = turnirkoRating.izracunaj(
                    new TurnirkoRatingStoritev.StanjeIgralca(rating, i, false),
                    TurnirkoRatingStoritev.StanjeIgralca.ustaljeno(nasprotnik,
                            TurnirkoRatingStoritev.PRAG_USTALJEN),
                    zmaga, raven.getTeza());
            izidi.add(new UvrstitevNovinca.Izid(nasprotnik, zmaga));
            boolean uvrstitev = izidi.size() >= UvrstitevNovinca.NAJMANJ_TEKEM;
            int nov = uvrstitev ? UvrstitevNovinca.izracunaj(izidi, izhodisce) : korak.ratingPo1();
            koraki.add(new PrviDanNovincaDto.Korak(nasprotnik, zmaga, nov, nov - rating, uvrstitev));
            rating = nov;
        }
        return new PrviDanNovincaDto(izhodisce, koraki);
    }

    private static void preveriRating(int rating) {
        if (rating < TurnirkoRatingStoritev.NAJNIZJI_ZACETNI || rating > TurnirkoRatingStoritev.NAJVISJI_ZACETNI) {
            throw new NeveljavenVnosIzjema("Rating mora biti med " + TurnirkoRatingStoritev.NAJNIZJI_ZACETNI
                    + " in " + TurnirkoRatingStoritev.NAJVISJI_ZACETNI + ".");
        }
    }

    private static void preveriTekme(int tekem) {
        if (tekem < 0 || tekem > NAJVEC_TEKEM) {
            throw new NeveljavenVnosIzjema("Število odigranih tekem ne more biti negativno.");
        }
    }
}
