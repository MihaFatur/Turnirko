/* Branje fotografije lista vadnice stevk (nacrt F3.2).

   1. QR koda »TKV:<razlicica>:<id>« pove, kateri list je to; brez nje lista ni
      mogoce pripisati enemu piscu (deljenje ucnih in preizkusnih stevk).
   2. Poravnava po oznakah in QR (kot listek, druga Postavitev) in okenca po
      crtah (CeliceVadnice).
   3. Vsako okence se razreze kot celica listka (RazrezCelice). Vrednost ni
      prebrana, ampak znana: vrstica lista. Stevke dobijo oznake po istem pravilu
      kot pri potrjenem listku (UcenjeIzListkov.stevkeCelice): dvomestno stevilo
      mora razrez dati kot dve stevki, sicer se okence preskoci in presteje.

   Mreza tu samo oceni, ali stevko ze zdaj prebere prav (mrezaPrav, za pregled
   in meritev); oznako vedno da vrstica. */
package si.turnirko.storitve;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import si.turnirko.branje.CeliceListka;
import si.turnirko.branje.CeliceVadnice;
import si.turnirko.branje.GeometrijaVadnice;
import si.turnirko.branje.MrezaStevk;
import si.turnirko.branje.PoravnavaListka;
import si.turnirko.branje.RazrezCelice;
import si.turnirko.branje.Temnost;
import si.turnirko.storitve.BralnikListka.NapakaBranjaListka;

public final class BranjeVadnice {

    private BranjeVadnice() {}

    /* Stevka z oznako: okence lista, prava vrednost, vhod mreze 28 x 28. */
    public record Stevka(int okence, int vrednost, float[] vhod, boolean mrezaPrav) {}

    /* praznih: okenca brez zapisa; preskocenih: zapis, ki ga razrez ne da kot
       pricakovano stevilo stevk. slika: poravnan list s samimi okenci (PNG). */
    public record Prebrana(long idLista, int razlicica, List<Stevka> stevke, int praznih, int preskocenih,
                           byte[] slika) {}

    public static Prebrana preberi(BufferedImage fotografija, MrezaStevk mreza) {
        List<KodaListka.KodaVadnice> kode = KodaListka.najdiVadnico(fotografija);
        if (kode.isEmpty()) {
            if (!KodaListka.najdi(fotografija).isEmpty()) {
                throw new NapakaBranjaListka("To je listek tekme, ne list vadnice. "
                        + "Fotografiraj ga na strani branja listkov turnirja.");
            }
            throw new NapakaBranjaListka("Na fotografiji ni mogoče prebrati kode QR lista vadnice. "
                    + "Fotografiraj cel list od zgoraj in ostro, da se vidi koda zgoraj desno.");
        }
        if (kode.size() > 1) {
            throw new NapakaBranjaListka("Na fotografiji je več listov vadnice. Fotografiraj po enega.");
        }
        KodaListka.KodaVadnice koda = kode.getFirst();
        GeometrijaVadnice.Okenca postavitev = GeometrijaVadnice.za(koda.razlicica());
        if (postavitev == null) {
            throw new NapakaBranjaListka("List je natisnjen v drugi različici vadnice (" + koda.razlicica()
                    + "), ki je ta strežnik ne pozna. Natisni nove liste.");
        }
        if (koda.idLista() <= 0 || koda.tocke() == null) {
            throw new NapakaBranjaListka("Koda QR lista vadnice ni veljavna. Natisni nove liste.");
        }
        PoravnavaListka.PoravnanListek poravnan;
        try {
            poravnan = PoravnavaListka.poravnaj(fotografija, koda.tocke(), GeometrijaVadnice.POSTAVITEV);
        } catch (PoravnavaListka.NapakaPoravnave e) {
            throw new NapakaBranjaListka("Na fotografiji ni mogoče najti črnih kvadratov v vogalih lista. "
                    + "Fotografiraj cel list od zgoraj, da se vidijo vsi trije.");
        }
        Temnost list = poravnan.slika();
        List<CeliceListka.Celica> okenca = CeliceVadnice.najdi(list, postavitev);
        List<Stevka> stevke = new ArrayList<>();
        int praznih = 0;
        int preskocenih = 0;
        for (int i = 0; i < okenca.size(); i++) {
            int vrednost = postavitev.vrednost(i);
            RazrezCelice.Vsebina vsebina = RazrezCelice.razreziOkence(list, okenca.get(i));
            if (vsebina.prazna()) {
                praznih++;
                continue;
            }
            List<float[]> vhodi = UcenjeIzListkov.stevkeCelice(vsebina, vrednost, mreza);
            if (vhodi == null) {
                preskocenih++;
                continue;
            }
            String zapis = String.valueOf(vrednost);
            for (int j = 0; j < vhodi.size(); j++) {
                int prava = zapis.charAt(j) - '0';
                stevke.add(new Stevka(i, prava, vhodi.get(j), UcenjeIzListkov.mrezaPrav(mreza, vhodi.get(j), prava)));
            }
        }
        return new Prebrana(koda.idLista(), koda.razlicica(), stevke, praznih, preskocenih,
                ZlatiNabor.slikaCelic(list, okenca));
    }
}
