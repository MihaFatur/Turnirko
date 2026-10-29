/* Branje, pomanjsevanje in ponovni zapis nalozenih slik (V54).

   Brskalnik sliko pripravi ze sam (obreze rob, odstrani belo ozadje, pomanjsa,
   poslje PNG), a strezniku ne zaupamo nicesar od tega:
   - vrsto doloci vsebina datoteke (ImageIO), ne koncnica ali glava zahteve;
   - meri se preberejo iz glave PRED dekodiranjem - majhna datoteka se lahko
     razsiri v gigapiksle (»bomba«) in pomnilnik bi pocil, preden bi karkoli
     preverili;
   - vsaka slika se dekodira in zapise znova, zato v bazo ne pride nic od tega,
     kar je datoteka nosila poleg pik (EXIF s krajem in napravo, komentarji,
     morebitna navlaka za koncem datoteke).

   Razred ne pozna baze ne Springa - isti postopek je tako v testu natanko to,
   kar dela storitev. */
package si.turnirko.pomozno;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.Locale;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import si.turnirko.izjeme.NeveljavenVnosIzjema;

public final class ObdelavaSlik {

    /* Najvecja datoteka, ki jo sploh beremo. Brskalnik poslje PNG do 1200 px
       (logotip je redko nad 500 kB); meja je velikodusna za fotografijo. */
    public static final int NAJVEC_BAJTOV = 8 * 1024 * 1024;
    /* Meri vhodne slike. Pregledana telefonska fotografija ima ~12 Mpx; vec
       pomeni bombo ali skeniran plakat, ki ga ni treba sprejeti. */
    static final int NAJVEC_STRANICA = 8000;
    static final long NAJVEC_PIKSLOV = 40_000_000L;
    /* Manjsa slika na zaslonu in papirju ni prepoznavna. */
    static final int NAJMANJ_STRANICA = 32;

    /* Daljsa stranica razlicic (glej VelikostSlike). */
    public static final int STRANICA_TISK = 1200;
    public static final int STRANICA_ZASLON = 480;
    public static final int DELJENJE_SIRINA = 1200;
    public static final int DELJENJE_VISINA = 630;
    /* Logotip na sliki za deljenje: v predogledu WhatsApp in Facebook obrezeta
       robove (kvadrat iz sredine), zato logotip ne sme seci do roba. */
    static final int DELJENJE_LOGOTIP_SIRINA = 900;
    static final int DELJENJE_LOGOTIP_VISINA = 420;
    /* Papir iz DESIGN.md (--barva-papir). */
    static final Color PAPIR = new Color(0xFB, 0xF9, 0xF5);

    /* Neprosojna slika je v PNG lahko velika (fotografija); nad to mejo gre v
       JPEG, ki prosojnosti ne potrebuje. Logotip z ravnimi ploskvami ostane PNG. */
    static final int MEJA_PNG = 350 * 1024;
    static final float KAKOVOST_JPEG = 0.9f;

    public static final String PNG = "image/png";
    public static final String JPEG = "image/jpeg";

    /* Zapisana slika: bajti, vrsta in meri. */
    public record Zapis(byte[] vsebina, String vrsta, int sirina, int visina) {}

    private ObdelavaSlik() {}

    /* Prebere PNG ali JPEG in vrne sliko v ARGB. Vse ostalo (GIF, BMP, SVG,
       besedilo, prevelike meri) zavrne z razlago. */
    public static BufferedImage preberi(byte[] vsebina) {
        if (vsebina == null || vsebina.length == 0) {
            throw new NeveljavenVnosIzjema("Datoteka je prazna.");
        }
        if (vsebina.length > NAJVEC_BAJTOV) {
            throw new NeveljavenVnosIzjema("Datoteka je prevelika (največ 8 MB).");
        }
        try (ImageInputStream vhod = ImageIO.createImageInputStream(new ByteArrayInputStream(vsebina))) {
            Iterator<ImageReader> bralci = ImageIO.getImageReaders(vhod);
            if (!bralci.hasNext()) {
                throw new NeveljavenVnosIzjema("Datoteka ni slika PNG ali JPEG.");
            }
            ImageReader bralec = bralci.next();
            try {
                String oblika = bralec.getFormatName().toLowerCase(Locale.ROOT);
                if (!oblika.equals("png") && !oblika.equals("jpeg") && !oblika.equals("jpg")) {
                    throw new NeveljavenVnosIzjema("Datoteka ni slika PNG ali JPEG.");
                }
                bralec.setInput(vhod, true, true);
                int sirina = bralec.getWidth(0);
                int visina = bralec.getHeight(0);
                if (sirina > NAJVEC_STRANICA || visina > NAJVEC_STRANICA
                        || (long) sirina * visina > NAJVEC_PIKSLOV) {
                    throw new NeveljavenVnosIzjema("Slika je prevelika (" + sirina + " × " + visina
                            + " px). Pomanjšaj jo pod " + NAJVEC_STRANICA + " px.");
                }
                if (Math.max(sirina, visina) < NAJMANJ_STRANICA) {
                    throw new NeveljavenVnosIzjema("Slika je premajhna (" + sirina + " × " + visina
                            + " px).");
                }
                return vArgb(bralec.read(0));
            } finally {
                bralec.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof NeveljavenVnosIzjema n) {
                throw n;
            }
            // CMYK JPEG, prirezana datoteka, napaka v kodiranju ...
            throw new NeveljavenVnosIzjema("Slike ni bilo mogoče prebrati. Shrani jo kot PNG in poskusi znova.");
        }
    }

    /* Pomanjsa tako, da daljsa stranica ne presega meje; manjse slike ne poveca.
       Polovicenje v korakih (in ne en skok) obdrzi tanke crte in drobno
       besedilo logotipa - en bikubicni skok s 3000 na 480 px jih razdrobi. */
    public static BufferedImage pomanjsaj(BufferedImage vir, int najvecStranica) {
        int sirina = vir.getWidth();
        int visina = vir.getHeight();
        double k = (double) najvecStranica / Math.max(sirina, visina);
        if (k >= 1.0) {
            return vir;
        }
        int ciljSirina = Math.max(1, (int) Math.round(sirina * k));
        int ciljVisina = Math.max(1, (int) Math.round(visina * k));
        BufferedImage trenutna = vir;
        int s = sirina;
        int v = visina;
        do {
            s = Math.max(ciljSirina, s / 2);
            v = Math.max(ciljVisina, v / 2);
            trenutna = narisi(trenutna, s, v);
        } while (s != ciljSirina || v != ciljVisina);
        return vArgb(trenutna);
    }

    /* Slika za deljenje: logotip, vpisan v sredino papirne ploskve 1200 x 630. */
    public static BufferedImage zaDeljenje(BufferedImage logotip) {
        double k = Math.min(1.0, Math.min(
                (double) DELJENJE_LOGOTIP_SIRINA / logotip.getWidth(),
                (double) DELJENJE_LOGOTIP_VISINA / logotip.getHeight()));
        BufferedImage pomanjsan = k < 1.0
                ? pomanjsaj(logotip, (int) Math.round(Math.max(logotip.getWidth(), logotip.getHeight()) * k))
                : logotip;
        BufferedImage ploskev = new BufferedImage(DELJENJE_SIRINA, DELJENJE_VISINA, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = ploskev.createGraphics();
        try {
            g.setColor(PAPIR);
            g.fillRect(0, 0, DELJENJE_SIRINA, DELJENJE_VISINA);
            g.drawImage(pomanjsan,
                    (DELJENJE_SIRINA - pomanjsan.getWidth()) / 2,
                    (DELJENJE_VISINA - pomanjsan.getHeight()) / 2, null);
        } finally {
            g.dispose();
        }
        return ploskev;
    }

    /* Zapise PNG; neprosojno sliko, ki bi bila v PNG velika, pa JPEG. */
    public static Zapis zapisi(BufferedImage slika) {
        byte[] png = zapisiPng(slika);
        if (png.length > MEJA_PNG && jeNeprosojna(slika)) {
            byte[] jpeg = zapisiJpeg(slika);
            if (jpeg.length < png.length) {
                return new Zapis(jpeg, JPEG, slika.getWidth(), slika.getHeight());
            }
        }
        return new Zapis(png, PNG, slika.getWidth(), slika.getHeight());
    }

    static boolean jeNeprosojna(BufferedImage slika) {
        if (!slika.getColorModel().hasAlpha()) {
            return true;
        }
        for (int y = 0; y < slika.getHeight(); y++) {
            for (int x = 0; x < slika.getWidth(); x++) {
                if ((slika.getRGB(x, y) >>> 24) != 0xFF) {
                    return false;
                }
            }
        }
        return true;
    }

    private static byte[] zapisiPng(BufferedImage slika) {
        ByteArrayOutputStream izhod = new ByteArrayOutputStream();
        try {
            ImageIO.write(slika, "png", izhod);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return izhod.toByteArray();
    }

    private static byte[] zapisiJpeg(BufferedImage slika) {
        // JPEG nima prosojnosti: pike prepisemo na RGB (neprosojno ze je)
        BufferedImage rgb = new BufferedImage(slika.getWidth(), slika.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.drawImage(slika, 0, 0, null);
        } finally {
            g.dispose();
        }
        ImageWriter pisec = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream izhod = new ByteArrayOutputStream();
        try (ImageOutputStream tok = ImageIO.createImageOutputStream(izhod)) {
            pisec.setOutput(tok);
            ImageWriteParam nastavitve = pisec.getDefaultWriteParam();
            nastavitve.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            nastavitve.setCompressionQuality(KAKOVOST_JPEG);
            pisec.write(null, new IIOImage(rgb, null, null), nastavitve);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            pisec.dispose();
        }
        return izhod.toByteArray();
    }

    /* Risanje v predmnozeno ARGB: pri navadnem ARGB bikubicno povprecje
       pomesa barvo prosojnih pik (pogosto crno) v rob logotipa in okoli njega
       ostane temen obroc. */
    private static BufferedImage narisi(BufferedImage vir, int sirina, int visina) {
        BufferedImage cilj = new BufferedImage(sirina, visina, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = cilj.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,
                    RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
            g.drawImage(vir, 0, 0, sirina, visina, null);
        } finally {
            g.dispose();
        }
        return cilj;
    }

    private static BufferedImage vArgb(BufferedImage vir) {
        if (vir.getType() == BufferedImage.TYPE_INT_ARGB) {
            return vir;
        }
        BufferedImage cilj = new BufferedImage(vir.getWidth(), vir.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = cilj.createGraphics();
        try {
            g.drawImage(vir, 0, 0, null);
        } finally {
            g.dispose();
        }
        return cilj;
    }
}
