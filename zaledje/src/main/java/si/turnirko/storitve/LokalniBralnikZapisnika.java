/* Bralnik ligaskega zapisnika (26. 11. 2026): isti gradniki kot bralnik listka,
   brez zunanjih storitev.

   1. QR koda »TZ:<natis>:<skrivnost>« pove natis (srecanje, oblika, kader, kakor je
      bil natisnjen); klicatelj ga poisce in preveri skrivnost (NatisZaBranje);
   2. oznake v vogalih in QR poravnajo list A4 (PoravnavaListka z
      GeometrijaZapisnika.POSTAVITEV);
   3. OdcitekZapisnika poisce oznake vrst, kvadratke in celice;
   4. tocke vsake tekme prebere mreza stevk, pravila izberejo veljaven izid tekme
      (RazlagaListka; celice Rezultat zapisnik nima).
   Kdo je igral (koliko krizcev sodi k tekmi) in ali je zapis zanesljiv, odloci
   PredlogZapisnika - tam so pravila lige. */
package si.turnirko.storitve;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Component;

import si.turnirko.branje.GeometrijaZapisnika;
import si.turnirko.branje.MrezaStevk;
import si.turnirko.branje.OdcitekZapisnika;
import si.turnirko.branje.PogostostNizov;
import si.turnirko.branje.PoravnavaListka;
import si.turnirko.branje.RazlagaListka;
import si.turnirko.branje.RazrezCelice;
import si.turnirko.branje.Temnost;
import si.turnirko.dto.CeliceZapisnikaDto;

@Component
public class LokalniBralnikZapisnika implements BralnikZapisnika {

    private final VirMrezeStevk vir;
    private final PogostostNizovStoritev pogostost;

    @org.springframework.beans.factory.annotation.Autowired
    public LokalniBralnikZapisnika(VirMrezeStevk vir, PogostostNizovStoritev pogostost) {
        this.vir = vir;
        this.pogostost = pogostost;
    }

    /* Brez baze (testi, simulacija): vgrajena pogostost nizov. */
    public LokalniBralnikZapisnika(VirMrezeStevk vir) {
        this(vir, null);
    }

    /* Natis, kot ga bralnik potrebuje: postavitev lista in stevilo nizov tekme. */
    public record NatisZaBranje(long id, GeometrijaZapisnika.PostavitevZapisnika postavitev, int nizov) {}

    /* Napaka z razlago za organizatorja (fotografija, koda, poravnava). */
    public static final class NapakaBranjaZapisnika extends RuntimeException {
        public NapakaBranjaZapisnika(String sporocilo) {
            super(sporocilo);
        }
    }

    /* Prepis, mreza, ki je brala, in poravnan list za izreze ob poljih pregleda. */
    public record Branje(PrebranZapisnik zapisnik, String model, byte[] poravnana, CeliceZapisnikaDto celice) {}

    @Override
    public boolean jeNastavljen() {
        return vir.trenutna() != null;
    }

    /* Koda zapisnika na fotografiji (brez branja tock): za sprejem in dvojnike.
       Prazno, ce kode ni. */
    public static List<KodaListka.KodaZapisnika> kode(byte[] jpeg) {
        BufferedImage slika = slika(jpeg);
        return slika == null ? List.of() : KodaListka.najdiZapisnik(slika);
    }

    /* natisPoKodi: poisce natis in preveri skrivnost; ob napaki vrze
       NapakaBranjaZapisnika z razlago. */
    @Override
    public Branje preberi(byte[] jpeg, Function<KodaListka.KodaZapisnika, NatisZaBranje> natisPoKodi) {
        VirMrezeStevk.Nalozena nalozena = vir.trenutna();
        if (nalozena == null) {
            throw new NapakaBranjaZapisnika("Branje zapisnikov na tem strežniku ni nastavljeno (manjka mreža števk).");
        }
        BufferedImage slika = slika(jpeg);
        if (slika == null) {
            throw new NapakaBranjaZapisnika("Fotografije ni bilo mogoče prebrati.");
        }
        List<KodaListka.KodaZapisnika> kode = KodaListka.najdiZapisnik(slika);
        if (kode.isEmpty()) {
            if (!KodaListka.najdi(slika).isEmpty()) {
                throw new NapakaBranjaZapisnika("To je sodniški listek turnirja, ne ligaški zapisnik. "
                        + "Fotografiraj ga na strani Branje listkov turnirja.");
            }
            throw new NapakaBranjaZapisnika("Na fotografiji ni mogoče prebrati kode QR zapisnika (zgoraj desno). "
                    + "Fotografiraj cel list od zgoraj, ostro, brez sence čez kodo. Branje deluje samo za "
                    + "zapisnike, natisnjene iz Turnirka z obliko »za bralnik«.");
        }
        if (kode.size() > 1) {
            throw new NapakaBranjaZapisnika("Na fotografiji sta dva zapisnika. Fotografiraj vsakega posebej.");
        }
        KodaListka.KodaZapisnika koda = kode.getFirst();
        NatisZaBranje natis = natisPoKodi.apply(koda);
        PoravnavaListka.PoravnavaSPreslikavo prva;
        try {
            prva = PoravnavaListka.poravnajSPreslikavo(slika, koda.tocke(), GeometrijaZapisnika.POSTAVITEV);
        } catch (PoravnavaListka.NapakaPoravnave e) {
            throw new NapakaBranjaZapisnika("Na fotografiji ni mogoče najti treh črnih kvadratov v vogalih zapisnika. "
                    + "Fotografiraj cel list od zgoraj, da se vidijo vsi trije.");
        }
        // A4 je velik: poravnava se izboljsa se po oznakah vrst
        Temnost list = OdcitekZapisnika.izboljsaj(prva, natis.postavitev(), GeometrijaZapisnika.POSTAVITEV);
        PogostostNizov p = pogostost == null ? PogostostNizov.VGRAJENA : pogostost.trenutna();
        double beta = pogostost == null ? PogostostNizov.PRIVZETA_BETA : pogostost.beta();
        OdcitekZapisnika.Odcitek odcitek = OdcitekZapisnika.odcitaj(list, natis.postavitev());
        PrebranZapisnik zapisnik = beri(natis, odcitek, nalozena.mreza(), p, beta);
        return new Branje(zapisnik, nalozena.oznaka(), jpeg(list), celice(list, odcitek));
    }

    /* Od odcitka naprej: tocke po pravilih in krizci po tekmah. */
    public static PrebranZapisnik beri(NatisZaBranje natis, OdcitekZapisnika.Odcitek odcitek, MrezaStevk mreza,
                                       PogostostNizov pogostost, double beta) {
        GeometrijaZapisnika.PostavitevZapisnika p = natis.postavitev();
        int tekem = p.celice().stream().mapToInt(GeometrijaZapisnika.Celica::tekma).max().orElse(-1) + 1;
        List<PrebranZapisnik.Tekma> tekme = new ArrayList<>();
        for (int t = 0; t < tekem; t++) {
            List<PrebranZapisnik.Krizec> domaci = new ArrayList<>();
            List<PrebranZapisnik.Krizec> gost = new ArrayList<>();
            for (OdcitekZapisnika.Kvadratek k : odcitek.kvadratki()) {
                if (k.mesto().tekma() != t) {
                    continue;
                }
                PrebranZapisnik.Krizec kr = new PrebranZapisnik.Krizec(k.mesto().igralec(),
                        Math.round(k.crnilo() * 1000) / 1000.0, k.stanje().name());
                (k.mesto().domaci() ? domaci : gost).add(kr);
            }
            RazrezCelice.Vsebina[][] vsebine = new RazrezCelice.Vsebina[natis.nizov()][2];
            for (OdcitekZapisnika.Celica c : odcitek.celice()) {
                if (c.mesto().tekma() == t && c.mesto().niz() < natis.nizov()) {
                    vsebine[c.mesto().niz()][c.mesto().domaci() ? 0 : 1] = c.vsebina();
                }
            }
            tekme.add(tekma(domaci, gost, vsebine, mreza, pogostost, beta));
        }
        return new PrebranZapisnik(natis.id(), odcitek.najdenihOznak(), odcitek.vsehOznak(), tekme);
    }

    private static PrebranZapisnik.Tekma tekma(List<PrebranZapisnik.Krizec> domaci, List<PrebranZapisnik.Krizec> gost,
                                               RazrezCelice.Vsebina[][] vsebine, MrezaStevk mreza,
                                               PogostostNizov pogostost, double beta) {
        List<RazlagaListka.Vrstica> vrstice = new ArrayList<>();
        for (RazrezCelice.Vsebina[] v : vsebine) {
            vrstice.add(new RazlagaListka.Vrstica(
                    LokalniBralnikListka.porazdelitev(v[0], mreza, true),
                    LokalniBralnikListka.porazdelitev(v[1], mreza, true)));
        }
        // celice Rezultat ni: izid tekme je stevilo dobljenih nizov
        RazlagaListka.Izid izid = RazlagaListka.razlozi(vrstice, null, null, pogostost, beta);
        List<PrebranListek.Niz> nizi = new ArrayList<>();
        List<String> popravljene = new ArrayList<>();
        for (int i = 0; i < izid.nizi().size(); i++) {
            RazlagaListka.Niz n = izid.nizi().get(i);
            boolean levo = vsebine[i][0].popravljena();
            boolean desno = vsebine[i][1].popravljena();
            if (levo || desno) {
                popravljene.add((i + 1) + ". niz");
            }
            nizi.add(new PrebranListek.Niz(n.levo(), n.desno(), n.negotovoLevo() || levo, n.negotovoDesno() || desno));
        }
        return new PrebranZapisnik.Tekma(domaci, gost, nizi, LokalniBralnikListka.opomba(izid.opomba(), popravljene));
    }

    /* Lega kvadratkov in celic na poravnanem listu (px pri 10 px/mm) za izreze ob
       poljih pregleda. */
    static CeliceZapisnikaDto celice(Temnost list, OdcitekZapisnika.Odcitek odcitek) {
        List<CeliceZapisnikaDto.Kvadratek> kvadratki = new ArrayList<>();
        for (OdcitekZapisnika.Kvadratek k : odcitek.kvadratki()) {
            kvadratki.add(new CeliceZapisnikaDto.Kvadratek(k.mesto().tekma(), k.mesto().domaci(), k.mesto().igralec(),
                    k.celica().x0(), k.celica().y0(), k.celica().x1(), k.celica().y1()));
        }
        List<CeliceZapisnikaDto.Celica> celice = new ArrayList<>();
        for (OdcitekZapisnika.Celica c : odcitek.celice()) {
            celice.add(new CeliceZapisnikaDto.Celica(c.mesto().tekma(), c.mesto().niz(), c.mesto().domaci(),
                    c.celica().x0(), c.celica().y0(), c.celica().x1(), c.celica().y1()));
        }
        return new CeliceZapisnikaDto(list.sirina, list.visina, kvadratki, celice);
    }

    /* Poravnan list v sivinah (JPEG) za izreze; null, ce zapis ne uspe. */
    static byte[] jpeg(Temnost t) {
        BufferedImage slika = new BufferedImage(t.sirina, t.visina, BufferedImage.TYPE_BYTE_GRAY);
        byte[] piksli = ((java.awt.image.DataBufferByte) slika.getRaster().getDataBuffer()).getData();
        for (int i = 0; i < piksli.length; i++) {
            piksli[i] = (byte) (255 - Math.round(255 * Math.max(0f, Math.min(1f, t.t[i]))));
        }
        java.io.ByteArrayOutputStream izhod = new java.io.ByteArrayOutputStream();
        try {
            return ImageIO.write(slika, "jpeg", izhod) ? izhod.toByteArray() : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static BufferedImage slika(byte[] jpeg) {
        try {
            return ImageIO.read(new ByteArrayInputStream(jpeg));
        } catch (IOException e) {
            return null;
        }
    }
}
