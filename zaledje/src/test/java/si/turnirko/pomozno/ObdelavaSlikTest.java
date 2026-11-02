/* Obdelava nalozenih slik (V54): kaj streznik sprejme, kaj zavrne in kaj od
   datoteke pride v bazo. Brez Springa - ObdelavaSlik je cista funkcija. */
package si.turnirko.pomozno;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static si.turnirko.pomozno.TestneSlike.logotip;
import static si.turnirko.pomozno.TestneSlike.zapisi;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.zip.CRC32;

import org.junit.jupiter.api.Test;

import si.turnirko.izjeme.NeveljavenVnosIzjema;

class ObdelavaSlikTest {

    @Test
    void preberePngInJpeg() throws IOException {
        BufferedImage png = ObdelavaSlik.preberi(zapisi(logotip(300, 100), "png"));
        assertEquals(300, png.getWidth());
        assertEquals(100, png.getHeight());
        assertEquals(BufferedImage.TYPE_INT_ARGB, png.getType());

        BufferedImage jpeg = ObdelavaSlik.preberi(zapisi(neprosojna(200, 200), "jpeg"));
        assertEquals(200, jpeg.getWidth());
    }

    /* Vrsto pove vsebina, ne koncnica: besedilo, SVG in GIF niso slike, ki jih
       sprejmemo (SVG lahko nosi skript; brskalnik ga pred posiljanjem pretvori). */
    @Test
    void zavrneVseRazenPngInJpeg() throws IOException {
        assertThrows(NeveljavenVnosIzjema.class,
                () -> ObdelavaSlik.preberi("ni slika".getBytes(StandardCharsets.UTF_8)));
        assertThrows(NeveljavenVnosIzjema.class, () -> ObdelavaSlik.preberi(
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                        .getBytes(StandardCharsets.UTF_8)));
        assertThrows(NeveljavenVnosIzjema.class,
                () -> ObdelavaSlik.preberi(zapisi(logotip(50, 50), "gif")));
        assertThrows(NeveljavenVnosIzjema.class, () -> ObdelavaSlik.preberi(new byte[0]));
    }

    /* »Bomba«: majhna datoteka, ki v glavi trdi 30 000 x 30 000 pik. Meri se
       preberejo PRED dekodiranjem, zato zavrnitev ne porabi pomnilnika. */
    @Test
    void prevelikoSlikoZavrnePoGlavi() throws IOException {
        byte[] bomba = zamenjajMeriPng(zapisi(logotip(40, 40), "png"), 30_000, 30_000);
        NeveljavenVnosIzjema napaka = assertThrows(NeveljavenVnosIzjema.class,
                () -> ObdelavaSlik.preberi(bomba));
        assertTrue(napaka.getMessage().contains("30000 × 30000"), napaka.getMessage());
    }

    @Test
    void premajhnoSlikoZavrne() {
        assertThrows(NeveljavenVnosIzjema.class,
                () -> ObdelavaSlik.preberi(zapisi(logotip(20, 10), "png")));
    }

    @Test
    void pomanjsaDaljsoStranicoInManjseNePoveca() {
        BufferedImage velika = ObdelavaSlik.pomanjsaj(logotip(3000, 1000), 1200);
        assertEquals(1200, velika.getWidth());
        assertEquals(400, velika.getHeight());

        BufferedImage pokoncna = ObdelavaSlik.pomanjsaj(logotip(500, 2000), 480);
        assertEquals(120, pokoncna.getWidth());
        assertEquals(480, pokoncna.getHeight());

        BufferedImage majhna = logotip(300, 200);
        assertEquals(majhna, ObdelavaSlik.pomanjsaj(majhna, 480));
    }

    /* Prosojnost preživi pomanjsanje in ob robu logotipa ne nastane temen obroc
       (predmnozeno risanje). */
    @Test
    void pomanjsanjeOhraniProsojnostBrezTemnegaRoba() {
        BufferedImage pomanjsana = ObdelavaSlik.pomanjsaj(logotip(1600, 1600), 400);
        assertEquals(0, pomanjsana.getRGB(0, 0) >>> 24, "vogal ostane prosojen");
        // pika na robu kroga: delno prosojna, a barva ostane modra, ne siva
        int rob = najdiDelnoProsojno(pomanjsana);
        Color barva = new Color(pomanjsana.getRGB(rob % 400, rob / 400), true);
        assertTrue(barva.getBlue() > 150 && barva.getRed() < 60,
                "rob ostane v barvi logotipa: " + barva);
    }

    @Test
    void slikaZaDeljenjeJeLogotipNaPapirju() {
        BufferedImage deljenje = ObdelavaSlik.zaDeljenje(logotip(1200, 400));
        assertEquals(ObdelavaSlik.DELJENJE_SIRINA, deljenje.getWidth());
        assertEquals(ObdelavaSlik.DELJENJE_VISINA, deljenje.getHeight());
        assertEquals(ObdelavaSlik.PAPIR.getRGB(), deljenje.getRGB(0, 0), "vogal je papir");
        assertEquals(ObdelavaSlik.PAPIR.getRGB(), deljenje.getRGB(100, 315),
                "logotip ne sega v rob, ki ga predogled obreze");
        assertEquals(TestneSlike.MODRA.getRGB(), deljenje.getRGB(600, 315),
                "sredina je logotip");
    }

    /* Prosojen logotip ostane PNG; neprosojna fotografija, ki bi bila v PNG
       velika, gre v JPEG. */
    @Test
    void vrstoIzbereVsebina() {
        assertEquals(ObdelavaSlik.PNG, ObdelavaSlik.zapisi(logotip(800, 400)).vrsta());
        ObdelavaSlik.Zapis sum = ObdelavaSlik.zapisi(sum(1000, 700));
        assertEquals(ObdelavaSlik.JPEG, sum.vrsta());
        assertEquals(1000, sum.sirina());
    }

    /* Vse, kar datoteka nosi poleg pik (EXIF s krajem, komentarji), ostane za
       vrati: slika se zapise znova. */
    @Test
    void metapodatkiNePridejoVZapis() throws IOException {
        byte[] jpeg = zExif(zapisi(neprosojna(400, 300), "jpeg"));
        assertTrue(vsebuje(jpeg, "Exif"), "preizkusna datoteka ima EXIF");

        ObdelavaSlik.Zapis zapis = ObdelavaSlik.zapisi(ObdelavaSlik.preberi(jpeg));
        assertFalse(vsebuje(zapis.vsebina(), "Exif"));
        assertFalse(vsebuje(zapis.vsebina(), "GPS"));
    }

    @Test
    void istaSlikaDaIsteBajte() throws IOException {
        byte[] vhod = zapisi(logotip(900, 300), "png");
        byte[] prvi = ObdelavaSlik.zapisi(ObdelavaSlik.preberi(vhod)).vsebina();
        byte[] drugi = ObdelavaSlik.zapisi(ObdelavaSlik.preberi(vhod)).vsebina();
        assertArrayEquals(prvi, drugi, "ponovno nalaganje iste datoteke je ista zgostitev");
    }

    // ---------- Pomozno ----------

    private static BufferedImage neprosojna(int sirina, int visina) {
        BufferedImage slika = new BufferedImage(sirina, visina, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = slika.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, sirina, visina);
        g.setColor(new Color(0x7B, 0xB9, 0x00));
        g.fillRect(sirina / 4, visina / 4, sirina / 2, visina / 2);
        g.dispose();
        return slika;
    }

    /* Sum se v PNG ne stisne - nadomestek za fotografijo. */
    private static BufferedImage sum(int sirina, int visina) {
        BufferedImage slika = new BufferedImage(sirina, visina, BufferedImage.TYPE_INT_RGB);
        Random nakljucje = new Random(7);
        for (int y = 0; y < visina; y++) {
            for (int x = 0; x < sirina; x++) {
                slika.setRGB(x, y, nakljucje.nextInt(0xFFFFFF));
            }
        }
        return slika;
    }

    /* Prepise sirino in visino v glavi IHDR (s pravo kontrolno vsoto), pike
       ostanejo iste - datoteka je majhna, glava pa trdi drugo. */
    private static byte[] zamenjajMeriPng(byte[] png, int sirina, int visina) {
        byte[] kopija = png.clone();
        // podpis 8 B, dolzina 4 B, "IHDR" 4 B, nato sirina in visina
        ByteBuffer.wrap(kopija, 16, 8).putInt(sirina).putInt(visina);
        CRC32 crc = new CRC32();
        crc.update(kopija, 12, 4 + 13);
        ByteBuffer.wrap(kopija, 12 + 4 + 13, 4).putInt((int) crc.getValue());
        return kopija;
    }

    /* Vstavi segment APP1 z EXIF (in nizom "GPS") za segmentom APP0 (JFIF mora
       ostati prvi, sicer ga bralnik zavrne). */
    private static byte[] zExif(byte[] jpeg) {
        byte[] podatki = "Exif\0\0MM\0*GPSLatitude 46.25 N GPSLongitude 15.16 E".getBytes(StandardCharsets.ISO_8859_1);
        int zaApp0 = 4 + (((jpeg[4] & 0xFF) << 8) | (jpeg[5] & 0xFF));
        ByteBuffer izhod = ByteBuffer.allocate(jpeg.length + 4 + podatki.length);
        izhod.put(jpeg, 0, zaApp0);
        izhod.put((byte) 0xFF).put((byte) 0xE1).putShort((short) (podatki.length + 2)).put(podatki);
        izhod.put(jpeg, zaApp0, jpeg.length - zaApp0);
        return izhod.array();
    }

    private static boolean vsebuje(byte[] bajti, String niz) {
        return new String(bajti, StandardCharsets.ISO_8859_1).contains(niz);
    }

    private static int najdiDelnoProsojno(BufferedImage slika) {
        for (int y = 0; y < slika.getHeight(); y++) {
            for (int x = 0; x < slika.getWidth(); x++) {
                int a = slika.getRGB(x, y) >>> 24;
                if (a > 60 && a < 200) {
                    return y * slika.getWidth() + x;
                }
            }
        }
        throw new AssertionError("ni delno prosojne pike");
    }
}
