/* Poskus (ne tece z ostalimi testi): list, ki ga je natisnil VMESNIK (posnetek
   strani zapisnikov iz brezglavega Chroma), izpolni po postavitvi natisa in ga
   »fotografira«. Fotografijo nato nalozi skripta na pravo zaledje - tako se
   preveri, da se vmesnik in GeometrijaZapisnika ujemata do mm.

   -Dposkus.png=<posnetek lista> -Dposkus.natis=<JSON natisa iz /zapisniki/natis>
   -Dposkus.izhod=<predpona izhoda> (.jpg fotografija, .json resnica) -Dposkus.seme=1 */
package si.turnirko.storitve;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import si.turnirko.branje.GeometrijaZapisnika;
import si.turnirko.modeli.FormatSrecanja;

class NatisnjenZapisnikPoskus {

    @Test
    void izpolniInFotografiraj() throws Exception {
        String png = System.getProperty("poskus.png");
        if (png == null) {
            return;
        }
        ObjectMapper json = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        JsonNode natis = json.readTree(new File(System.getProperty("poskus.natis")));
        GeometrijaZapisnika.PostavitevZapisnika p = json.treeToValue(natis.get("postavitev"),
                GeometrijaZapisnika.PostavitevZapisnika.class);
        BufferedImage izvirnik = ImageIO.read(new File(png));
        // na tocno 12 px/mm, kot rise SimulacijaZapisnikaTest
        int w = (int) Math.round(p.sirina() * SimulacijaZapisnikaTest.PX_NA_MM);
        int h = (int) Math.round(p.visina() * SimulacijaZapisnikaTest.PX_NA_MM);
        BufferedImage list = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = list.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(izvirnik, 0, 0, w, h, null);
        g.dispose();

        SimulacijaZapisnikaTest.pripravi();
        Random r = new Random(Long.getLong("poskus.seme", 1));
        int kaderD = natis.get("kaderDomaci").size();
        int kaderG = natis.get("kaderGost").size();
        SimulacijaZapisnikaTest.Resnica res = SimulacijaZapisnikaTest.nakljucnoSrecanje(r, FormatSrecanja.SAVINJA,
                kaderD, kaderG, null);
        SimulacijaZapisnikaTest.napolni(list, p, res, r);
        byte[] foto = SimuliraneFotografijeTest.fotografija(list, r, SimuliraneFotografijeTest.OBICAJNO, 3200, 2400,
                2300, 300);
        String izhod = System.getProperty("poskus.izhod");
        Files.write(Path.of(izhod + ".jpg"), foto);
        Files.writeString(Path.of(izhod + ".json"), json.writeValueAsString(res));
        System.out.println("Fotografija: " + izhod + ".jpg");
    }
}
