/* Slike za teste nalaganja (V54): logotip na prosojnem in zapis v izbrano
   obliko. Skupne ObdelavaSlikTest, SlikeStoritevTest in SlikeDostopTest. */
package si.turnirko.pomozno;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import javax.imageio.ImageIO;

public final class TestneSlike {

    /* Hisna modra (--barva-glavna-polna) - barva kroga v logotipu. */
    public static final Color MODRA = new Color(0x00, 0x71, 0xAB);

    private TestneSlike() {}

    /* Moder krog na prosojnem - kot logotip kluba. */
    public static BufferedImage logotip(int sirina, int visina) {
        BufferedImage slika = new BufferedImage(sirina, visina, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = slika.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(MODRA);
        int premer = Math.min(sirina, visina) * 3 / 4;
        g.fillOval((sirina - premer) / 2, (visina - premer) / 2, premer, premer);
        g.dispose();
        return slika;
    }

    /* PNG logotipa dane velikosti; vsaka velikost je druga slika. */
    public static byte[] png(int sirina, int visina) {
        return zapisi(logotip(sirina, visina), "png");
    }

    /* Zapis v "png", "jpeg" ali "gif"; obliki brez prosojnosti dobita belo podlago. */
    public static byte[] zapisi(BufferedImage slika, String oblika) {
        BufferedImage zaObliko = slika;
        if (!oblika.equals("png")) {
            zaObliko = new BufferedImage(slika.getWidth(), slika.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = zaObliko.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, slika.getWidth(), slika.getHeight());
            g.drawImage(slika, 0, 0, null);
            g.dispose();
        }
        ByteArrayOutputStream izhod = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(zaObliko, oblika, izhod)) {
                throw new IllegalStateException("Ni pisca za " + oblika);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return izhod.toByteArray();
    }
}
