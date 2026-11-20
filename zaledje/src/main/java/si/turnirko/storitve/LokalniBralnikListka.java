/* Brezplacni bralnik listkov: vse tece na strezniku, brez zunanjih storitev.

   1. QR koda (ZXing) in tri oznake v vogalih poravnajo fotografijo (PoravnavaListka);
   2. celice nizov in Rezultata se poiscejo po crtah tabele (CeliceListka);
   3. zapis v vsaki celici se razreze na eno ali dve stevki (RazrezCelice) in
      mreza stevk (MrezaStevk) da verjetnost vsake vrednosti;
   4. pravila namiznega tenisa izberejo najverjetnejsi veljaven izid in oznacijo
      negotove celice (RazlagaListka).
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
import si.turnirko.branje.MrezaStevk;
import si.turnirko.branje.PoravnavaListka;
import si.turnirko.branje.RazlagaListka;
import si.turnirko.branje.RazrezCelice;

@Component
public class LokalniBralnikListka implements BralnikListka {

    /* Zmehcanje verjetnosti mreze: samozavestna napaka bi pravila preglasila. */
    static final double TEMPERATURA = 1.5;
    /* Najmanjsa verjetnost vrednosti, da pravila lahko popravijo tudi zelo zgresen razbor. */
    private static final double TLA = 1e-6;

    private final VirMrezeStevk vir;

    public LokalniBralnikListka(VirMrezeStevk vir) {
        this.vir = vir;
    }

    /* Razrez celic listka: vsebina vsake celice (za branje in za ucenje iz potrditev). */
    public record Izrez(List<RazrezCelice.Vsebina[]> nizi, RazrezCelice.Vsebina[] rezultat) {}

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
        Izrez izrez = izrezi(jpeg);
        List<RazlagaListka.Vrstica> vrstice = new ArrayList<>();
        for (RazrezCelice.Vsebina[] v : izrez.nizi()) {
            vrstice.add(new RazlagaListka.Vrstica(porazdelitev(v[0], nalozena.mreza()), porazdelitev(v[1], nalozena.mreza())));
        }
        RazlagaListka.Izid izid = RazlagaListka.razlozi(vrstice,
                porazdelitev(izrez.rezultat()[0], nalozena.mreza()), porazdelitev(izrez.rezultat()[1], nalozena.mreza()));
        List<PrebranListek.Niz> nizi = izid.nizi().stream()
                .map(n -> new PrebranListek.Niz(n.levo(), n.desno(), n.negotovoLevo(), n.negotovoDesno()))
                .toList();
        PrebranListek listek = new PrebranListek(true, null, null, null, false, null, null, nizi,
                izid.rezultatLevo(), izid.rezultatDesno(), izid.negotovRezultat(), "NI", null, izid.opomba());
        return new Branje(listek, nalozena.oznaka());
    }

    public Izrez izrezi(byte[] jpeg) {
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
            throw new NapakaBranjaListka(e.getMessage());
        }
        CeliceListka.Tabela tabela = CeliceListka.najdi(poravnan.slika());
        if (tabela == null) {
            throw new NapakaBranjaListka("Na fotografiji ni mogoče najti celic za točke. "
                    + "Fotografiraj listek od zgoraj, ostro in brez sence čez tabelo.");
        }
        List<RazrezCelice.Vsebina[]> nizi = new ArrayList<>();
        for (CeliceListka.Vrstica v : tabela.nizi()) {
            nizi.add(new RazrezCelice.Vsebina[] {
                    RazrezCelice.razrezi(poravnan.slika(), v.levo()),
                    RazrezCelice.razrezi(poravnan.slika(), v.desno())});
        }
        RazrezCelice.Vsebina[] rezultat = {
                RazrezCelice.razrezi(poravnan.slika(), tabela.rezultat().levo()),
                RazrezCelice.razrezi(poravnan.slika(), tabela.rezultat().desno())};
        return new Izrez(nizi, rezultat);
    }

    /* Verjetnost vsake vrednosti celice (0-99) in prazne celice, cez vse razlage
       razreza: ena stevka ali dve (vodilna nicla pri tockah ne nastopi). */
    static double[] porazdelitev(RazrezCelice.Vsebina vsebina, MrezaStevk mreza) {
        double[] p = new double[RazlagaListka.VELIKOST];
        if (vsebina.prazna()) {
            p[RazlagaListka.PRAZNO] = 1;
            return p;
        }
        for (RazrezCelice.Razlaga r : vsebina.razlage()) {
            if (r.stevke().size() == 1) {
                float[] q = mreza.verjetnosti(r.stevke().getFirst().vhod(), TEMPERATURA);
                for (int v = 0; v <= 9; v++) {
                    p[v] += r.verjetnost() * q[v];
                }
            } else if (r.stevke().size() == 2) {
                float[] q1 = mreza.verjetnosti(r.stevke().get(0).vhod(), TEMPERATURA);
                float[] q2 = mreza.verjetnosti(r.stevke().get(1).vhod(), TEMPERATURA);
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
}
