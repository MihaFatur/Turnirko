/* Koda tekme na listku (KodaListka).

   Domenska pravila, ki jih ta test varuje:
   - QR koda »TK:<id>« se prebere tudi z vecje, zasukane in svetlo-sive slike
     (fotografija v dvorani, ne skener);
   - tuja QR koda (spletni naslov) ni koda tekme;
   - dve kodi na eni fotografiji se obe prebereta (organizator mora
     fotografirati vsakega posebej);
   - natisnjena stevilka se prebere v oblikah, kot jo vrne bralnik. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

class KodaListkaTest {

    @Test
    void kodaSeZapiseInPrebere() throws Exception {
        assertEquals("TK:48213", KodaListka.vsebinaQr(48213));
        assertEquals("T-48213", KodaListka.natisnjena(48213));
        BufferedImage slika = list(List.of(qr("TK:48213")), 0);
        assertEquals(List.of(48213L), KodaListka.preberiQr(slika));
    }

    @Test
    void zasukanaInSivaFotografija() throws Exception {
        BufferedImage slika = list(List.of(qr("TK:7")), Math.toRadians(12));
        assertEquals(List.of(7L), KodaListka.preberiQr(slika));
    }

    @Test
    void tujaKodaNiKodaTekme() throws Exception {
        assertTrue(KodaListka.preberiQr(list(List.of(qr("https://turnirko-nt.si")), 0)).isEmpty());
        assertTrue(KodaListka.preberiQr(new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB)).isEmpty());
    }

    @Test
    void dveKodiNaEniFotografiji() throws Exception {
        List<Long> kode = KodaListka.preberiQr(list(List.of(qr("TK:11"), qr("TK:12")), 0));
        assertEquals(2, kode.size());
    }

    @Test
    void natisnjenaStevilka() {
        assertEquals(Optional.of(48213L), KodaListka.izNatisnjene("T-48213"));
        assertEquals(Optional.of(48213L), KodaListka.izNatisnjene("t 48213"));
        assertEquals(Optional.of(48213L), KodaListka.izNatisnjene("T48213"));
        assertEquals(Optional.empty(), KodaListka.izNatisnjene(null));
        assertEquals(Optional.empty(), KodaListka.izNatisnjene("Miza 4"));
    }

    // ---------- Pomozno ----------

    private static BufferedImage qr(String vsebina) throws WriterException {
        return MatrixToImageWriter.toBufferedImage(new QRCodeWriter().encode(vsebina, BarcodeFormat.QR_CODE,
                180, 180, Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                        EncodeHintType.MARGIN, 1)));
    }

    /* Svetlo siv »papir« 1600 x 1200 s kodami, po zelji zasukan kot v roki. */
    private static BufferedImage list(List<BufferedImage> kode, double zasuk) {
        BufferedImage slika = new BufferedImage(1600, 1200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = slika.createGraphics();
        try {
            g.setColor(new Color(0xE8, 0xE6, 0xE0));
            g.fillRect(0, 0, 1600, 1200);
            g.setTransform(AffineTransform.getRotateInstance(zasuk, 800, 600));
            int x = 300;
            for (BufferedImage koda : kode) {
                g.drawImage(koda, x, 400, null);
                x += 600;
            }
        } finally {
            g.dispose();
        }
        return slika;
    }
}
