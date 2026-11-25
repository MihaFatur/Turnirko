/* Brezplacni bralnik listkov: vse tece na strezniku, brez zunanjih storitev.

   1. QR koda (ZXing) in tri oznake v vogalih poravnajo fotografijo (PoravnavaListka);
   2. celice nizov in Rezultata se poiscejo po crtah tabele (CeliceListka);
   3. zapis v vsaki celici se razreze na eno ali dve stevki (RazrezCelice) in
      mreza stevk (MrezaStevk) da verjetnost vsake vrednosti;
   4. pravila namiznega tenisa izberejo najverjetnejsi veljaven izid in oznacijo
      negotove celice (RazlagaListka).
   5. okence mize v glavi (S1, MizaListka) prebere ista mreza; pravil zanj ni,
      zato je dvomljiv zapis vedno negotov.
   En listek je ~30 stevk in nekaj sto milisekund racunanja - brez omejitve stevila
   listkov in brez cene.

   Imen in posebnih zapisov (»b.b.«) ne bere: tekmo doloci QR koda, posebni izidi
   pa se pokazejo kot nepopolna tekma, ki jo organizator vpise sam. */
package si.turnirko.storitve;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Component;

import si.turnirko.branje.CeliceListka;
import si.turnirko.branje.MizaListka;
import si.turnirko.branje.MrezaStevk;
import si.turnirko.branje.PogostostNizov;
import si.turnirko.branje.PoravnavaListka;
import si.turnirko.branje.RazlagaListka;
import si.turnirko.branje.RazrezCelice;
import si.turnirko.branje.Temnost;
import si.turnirko.branje.VecBranj;

@Component
public class LokalniBralnikListka implements BralnikListka {

    /* Zmehcanje verjetnosti mreze: samozavestna napaka bi pravila preglasila. */
    static final double TEMPERATURA = 1.5;
    /* Najmanjsa verjetnost vrednosti, da pravila lahko popravijo tudi zelo zgresen razbor. */
    private static final double TLA = 1e-6;

    private final VirMrezeStevk vir;
    private final PogostostNizovStoritev pogostost;
    /* Beta in veckratno branje, kadar pogostosti iz baze ni (meritve brez baze). */
    private final double betaBrezBaze;
    private final boolean vecBranj;

    @org.springframework.beans.factory.annotation.Autowired
    public LokalniBralnikListka(VirMrezeStevk vir, PogostostNizovStoritev pogostost) {
        this(vir, pogostost, PogostostNizov.PRIVZETA_BETA, true);
    }

    /* Brez baze (testi, simulacija): vgrajena pogostost nizov, privzeta beta. */
    public LokalniBralnikListka(VirMrezeStevk vir) {
        this(vir, null, PogostostNizov.PRIVZETA_BETA, true);
    }

    /* Za meritve posameznih korakov (SimulacijaBralnikaPoskus). */
    LokalniBralnikListka(VirMrezeStevk vir, PogostostNizovStoritev pogostost, double betaBrezBaze, boolean vecBranj) {
        this.vir = vir;
        this.pogostost = pogostost;
        this.betaBrezBaze = betaBrezBaze;
        this.vecBranj = vecBranj;
    }

    /* Razrez celic listka: vsebina vsake celice (za branje in za ucenje iz potrditev).
       miza = okence mize (S1) ali null, ce ga listek nima; mizaZanesljiva = okence je
       zanesljivo (sicer je branje mize negotovo, MizaListka). */
    public record Izrez(List<RazrezCelice.Vsebina[]> nizi, RazrezCelice.Vsebina[] rezultat, RazrezCelice.Vsebina miza,
                        boolean mizaZanesljiva) {

        public Izrez(List<RazrezCelice.Vsebina[]> nizi, RazrezCelice.Vsebina[] rezultat) {
            this(nizi, rezultat, null, false);
        }
    }

    /* Listek v stalnih koordinatah in njegove celice (pred razrezom); miza = okence
       mize (S1) ali null. */
    public record Poravnan(Temnost slika, CeliceListka.Tabela tabela, MizaListka.Okence miza) {

        public Poravnan(Temnost slika, CeliceListka.Tabela tabela) {
            this(slika, tabela, null);
        }
    }

    /* Prebrana stevilka mize (S1): null, ce je okence prazno ali ga ni. */
    public record Miza(Integer stevilka, boolean negotova) {}

    /* Zapis v okencu mize je negotov pod tem zaupanjem (enako kot celica tock). */
    static final double ZAUPANJE_MIZE = 0.8;

    @Override
    public boolean jeNastavljen() {
        return vir.trenutna() != null;
    }

    @Override
    public Branje preberi(byte[] jpeg) {
        VirMrezeStevk.Nalozena nalozena = vir.trenutna();
        if (nalozena == null) {
            throw new NapakaBranjaListka("Branje listkov na tem strežniku ni nastavljeno (manjka mreža števk).");
        }
        PogostostNizov p = pogostost == null ? PogostostNizov.VGRAJENA : pogostost.trenutna();
        double beta = pogostost == null ? betaBrezBaze : pogostost.beta();
        Poravnan poravnan = poravnaj(jpeg);
        PrebranListek listek = beri(izrezi(poravnan), nalozena.mreza(), p, beta, vecBranj);
        return new Branje(listek, nalozena.oznaka(), poravnava(poravnan));
    }

    /* Poravnan listek za izrez celic ob poljih pregleda (F4.1): sivine, JPEG ~100 kB.
       Neuspeh zapisa slike ne pokvari branja - izrezov takrat ni. */
    static Poravnava poravnava(Poravnan poravnan) {
        Temnost t = poravnan.slika();
        java.awt.image.BufferedImage slika = new java.awt.image.BufferedImage(t.sirina, t.visina,
                java.awt.image.BufferedImage.TYPE_BYTE_GRAY);
        byte[] piksli = ((java.awt.image.DataBufferByte) slika.getRaster().getDataBuffer()).getData();
        for (int i = 0; i < piksli.length; i++) {
            piksli[i] = (byte) (255 - Math.round(255 * Math.max(0f, Math.min(1f, t.t[i]))));
        }
        java.io.ByteArrayOutputStream izhod = new java.io.ByteArrayOutputStream();
        try {
            if (!ImageIO.write(slika, "jpeg", izhod)) {
                return null;
            }
        } catch (IOException e) {
            return null;
        }
        CeliceListka.Tabela tabela = poravnan.tabela();
        List<int[]> nizi = new ArrayList<>();
        for (CeliceListka.Vrstica v : tabela.nizi()) {
            nizi.add(vrstica(v));
        }
        CeliceListka.Celica m = poravnan.miza() == null ? null : poravnan.miza().celica();
        int[] miza = m == null ? null : new int[] {m.x0(), m.y0(), m.x1(), m.y1()};
        return new Poravnava(izhod.toByteArray(),
                new si.turnirko.dto.CeliceListkaDto(t.sirina, t.visina, nizi, vrstica(tabela.rezultat()), miza));
    }

    private static int[] vrstica(CeliceListka.Vrstica v) {
        return new int[] {v.levo().x0(), v.levo().y0(), v.levo().x1(), v.levo().y1(),
                v.desno().x0(), v.desno().y0(), v.desno().x1(), v.desno().y1()};
    }

    public static PrebranListek beri(Izrez izrez, MrezaStevk mreza) {
        return beri(izrez, mreza, PogostostNizov.VGRAJENA, PogostostNizov.PRIVZETA_BETA, true);
    }

    /* Branje od celic naprej: mreza stevk in pravila (s pogostostjo nizov). Isto
       tece nad zlatim naborom (MeritevNabora), zato sta meritvi primerljivi. */
    public static PrebranListek beri(Izrez izrez, MrezaStevk mreza, PogostostNizov pogostost, double beta) {
        return beri(izrez, mreza, pogostost, beta, true);
    }

    static PrebranListek beri(Izrez izrez, MrezaStevk mreza, PogostostNizov pogostost, double beta, boolean vecBranj) {
        return beri(izrez, porazdelitve(izrez, mreza, vecBranj), mreza, pogostost, beta, vecBranj).listek();
    }

    /* Porazdelitve vrednosti vseh celic listka (mreza na vsaki celici posebej, pred
       pravili): nizi[i] = {levo, desno}, rezultat = {levo, desno}. */
    public record Porazdelitve(List<double[][]> nizi, double[][] rezultat) {}

    public static Porazdelitve porazdelitve(Izrez izrez, MrezaStevk mreza, boolean vecBranj) {
        List<double[][]> nizi = new ArrayList<>();
        for (RazrezCelice.Vsebina[] v : izrez.nizi()) {
            nizi.add(new double[][] {porazdelitev(v[0], mreza, vecBranj), porazdelitev(v[1], mreza, vecBranj)});
        }
        return new Porazdelitve(nizi, new double[][] {porazdelitev(izrez.rezultat()[0], mreza, vecBranj),
                porazdelitev(izrez.rezultat()[1], mreza, vecBranj)});
    }

    /* Prebran listek in razlaga pravil (z zaupanjem vsake celice) - diagnostika
       branja (DiagnozaBranja) potrebuje oboje, branje samo listek. */
    public record Razlozen(PrebranListek listek, RazlagaListka.Izid razlaga) {}

    /* Branje od ze izracunanih porazdelitev naprej (pravila, popravljene celice, miza). */
    public static Razlozen beri(Izrez izrez, Porazdelitve porazdelitve, MrezaStevk mreza, PogostostNizov pogostost,
                                double beta, boolean vecBranj) {
        List<RazlagaListka.Vrstica> vrstice = new ArrayList<>();
        for (double[][] v : porazdelitve.nizi()) {
            vrstice.add(new RazlagaListka.Vrstica(v[0], v[1]));
        }
        RazlagaListka.Izid izid = RazlagaListka.razlozi(vrstice, porazdelitve.rezultat()[0],
                porazdelitve.rezultat()[1], pogostost, beta);
        // popravljena celica (precrtano in prepisano) je vedno negotova (F2.5)
        List<PrebranListek.Niz> nizi = new ArrayList<>();
        List<String> popravljene = new ArrayList<>();
        for (int i = 0; i < izid.nizi().size(); i++) {
            RazlagaListka.Niz n = izid.nizi().get(i);
            boolean levo = izrez.nizi().get(i)[0].popravljena();
            boolean desno = izrez.nizi().get(i)[1].popravljena();
            if (levo || desno) {
                popravljene.add((i + 1) + ". niz");
            }
            nizi.add(new PrebranListek.Niz(n.levo(), n.desno(), n.negotovoLevo() || levo, n.negotovoDesno() || desno));
        }
        boolean rezultatPopravljen = izrez.rezultat()[0].popravljena() || izrez.rezultat()[1].popravljena();
        if (rezultatPopravljen) {
            popravljene.add("rezultat");
        }
        Miza miza = izrez.miza() == null ? new Miza(null, false)
                : miza(porazdelitev(izrez.miza(), mreza, vecBranj),
                        izrez.miza().popravljena() || !izrez.mizaZanesljiva());
        return new Razlozen(new PrebranListek(true, null, null, null, false, null, null, nizi,
                izid.rezultatLevo(), izid.rezultatDesno(), izid.negotovRezultat() || rezultatPopravljen, "NI", null,
                opomba(izid.opomba(), popravljene), miza.stevilka(), miza.negotova()), izid);
    }

    /* Stevilka mize iz porazdelitve okenca: najverjetnejsa med 1-99 in prazno (mize 0
       ni, zato nicla ni kandidat - steje pa v vsoto: kar mreza vidi kot 0, ni
       zanesljiva druga stevilka). Pravil, ki bi mizo popravila, ni - negotova je,
       kadar je mreza ne prebere zanesljivo, je zapis precrtan in prepisan ali okence
       ni zanesljivo (vedno negotova = true). */
    static Miza miza(double[] p, boolean vednoNegotova) {
        double vsota = p[0];
        int najboljsa = RazlagaListka.PRAZNO;
        for (int v = 1; v <= RazlagaListka.PRAZNO; v++) {
            vsota += p[v];
            if (p[v] > p[najboljsa]) {
                najboljsa = v;
            }
        }
        boolean negotova = vednoNegotova || vsota <= 0 || p[najboljsa] / vsota < ZAUPANJE_MIZE;
        return new Miza(najboljsa == RazlagaListka.PRAZNO ? null : najboljsa, negotova);
    }

    /* Opomba razlage in seznam popravljenih celic v enem stavku za organizatorja. */
    static String opomba(String opombaRazlage, List<String> popravljene) {
        if (popravljene.isEmpty()) {
            return opombaRazlage;
        }
        String o = popravljene.size() == 1
                ? "Popravljena celica (" + popravljene.getFirst() + "). Preveri jo."
                : "Popravljene celice (" + String.join(", ", popravljene) + "). Preveri jih.";
        return opombaRazlage == null ? o : opombaRazlage + " " + o;
    }

    public Izrez izrezi(byte[] jpeg) {
        return izrezi(poravnaj(jpeg));
    }

    /* Razrez vseh celic poravnanega listka. */
    public static Izrez izrezi(Poravnan poravnan) {
        Temnost slika = poravnan.slika();
        CeliceListka.Tabela tabela = poravnan.tabela();
        List<RazrezCelice.Vsebina[]> nizi = new ArrayList<>();
        for (CeliceListka.Vrstica v : tabela.nizi()) {
            nizi.add(new RazrezCelice.Vsebina[] {
                    RazrezCelice.razrezi(slika, v.levo()),
                    RazrezCelice.razrezi(slika, v.desno())});
        }
        RazrezCelice.Vsebina[] rezultat = {
                RazrezCelice.razrezi(slika, tabela.rezultat().levo()),
                RazrezCelice.razrezi(slika, tabela.rezultat().desno())};
        MizaListka.Okence okence = poravnan.miza();
        RazrezCelice.Vsebina miza = okence == null ? null : RazrezCelice.razreziOkence(slika, okence.celica());
        return new Izrez(nizi, rezultat, miza, okence != null && okence.zanesljivo());
    }

    /* QR koda, poravnava in iskanje celic; ob neuspehu NapakaBranjaListka z
       navodilom za organizatorja. */
    public Poravnan poravnaj(byte[] jpeg) {
        BufferedImage slika;
        try {
            slika = ImageIO.read(new ByteArrayInputStream(jpeg));
        } catch (IOException e) {
            slika = null;
        }
        if (slika == null) {
            throw new NapakaBranjaListka("Fotografije ni bilo mogoče prebrati.");
        }
        List<KodaListka.NajdenaKoda> kode = KodaListka.najdi(slika);
        double[][] qr = kode.size() == 1 ? kode.getFirst().tocke() : null;
        PoravnavaListka.PoravnanListek poravnan;
        try {
            poravnan = PoravnavaListka.poravnaj(slika, qr);
        } catch (PoravnavaListka.NapakaPoravnave e) {
            // list vadnice ima drugacno postavitev; pove se, kam sodi (samo ob neuspehu,
            // da obicajno branje ne bere QR dvakrat)
            if (kode.isEmpty() && !KodaListka.najdiVadnico(slika).isEmpty()) {
                throw new NapakaBranjaListka("To je list vadnice števk, ne listek tekme. "
                        + "Fotografiraj ga na strani Vadnica števk.");
            }
            throw new NapakaBranjaListka(e.getMessage());
        }
        CeliceListka.Tabela tabela = CeliceListka.najdi(poravnan.slika());
        if (tabela == null) {
            throw new NapakaBranjaListka("Na fotografiji ni mogoče najti celic za točke. "
                    + "Fotografiraj listek od zgoraj, ostro in brez sence ali prsta čez tabelo.");
        }
        return new Poravnan(poravnan.slika(), tabela, MizaListka.najdi(poravnan.slika()));
    }

    /* Verjetnost vsake vrednosti celice (0-99) in prazne celice, cez vse razlage
       razreza: ena stevka ali dve (vodilna nicla pri tockah ne nastopi). */
    static double[] porazdelitev(RazrezCelice.Vsebina vsebina, MrezaStevk mreza, boolean vecBranj) {
        double[] p = new double[RazlagaListka.VELIKOST];
        if (vsebina.prazna()) {
            p[RazlagaListka.PRAZNO] = 1;
            return p;
        }
        for (RazrezCelice.Razlaga r : vsebina.razlage()) {
            if (r.stevke().size() == 1) {
                float[] q = verjetnosti(mreza, r.stevke().getFirst().vhod(), vecBranj);
                for (int v = 0; v <= 9; v++) {
                    p[v] += r.verjetnost() * q[v];
                }
            } else if (r.stevke().size() == 2) {
                float[] q1 = verjetnosti(mreza, r.stevke().get(0).vhod(), vecBranj);
                float[] q2 = verjetnosti(mreza, r.stevke().get(1).vhod(), vecBranj);
                for (int a = 1; a <= 9; a++) {
                    for (int b = 0; b <= 9; b++) {
                        p[10 * a + b] += r.verjetnost() * q1[a] * q2[b];
                    }
                }
            }
        }
        double vsota = 0;
        for (int i = 0; i < p.length; i++) {
            p[i] = Math.max(p[i], TLA);
            vsota += p[i];
        }
        for (int i = 0; i < p.length; i++) {
            p[i] /= vsota;
        }
        return p;
    }

    /* Verjetnosti stevke: petkratno branje (F2.2) ali eno. */
    private static float[] verjetnosti(MrezaStevk mreza, float[] vhod, boolean vecBranj) {
        return vecBranj ? VecBranj.verjetnosti(mreza, vhod, TEMPERATURA) : mreza.verjetnosti(vhod, TEMPERATURA);
    }
}
