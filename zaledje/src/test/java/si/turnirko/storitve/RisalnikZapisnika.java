/* Risba ligaskega zapisnika za bralnik v testih: isti list, kot ga vmesnik
   natisne iz GeometrijaZapisnika (oznake, QR, oznake vrst, kvadratki in celice s
   crto 0,45 mm znotraj pravokotnika), z nekaj besedila za realen sum (imena ob
   oznakah vrst). Vmesnik z njo primerja SimulacijaZapisnikaTest.natisnjenListSeUjema. */
package si.turnirko.storitve;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Random;

import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.ByteMatrix;
import com.google.zxing.qrcode.encoder.Encoder;

import si.turnirko.branje.GeometrijaZapisnika;

final class RisalnikZapisnika {

    private RisalnikZapisnika() {}

    static BufferedImage narisi(GeometrijaZapisnika.PostavitevZapisnika p, String qr, double pxNaMm) {
        int w = (int) Math.round(p.sirina() * pxNaMm);
        int h = (int) Math.round(p.visina() * pxNaMm);
        BufferedImage slika = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = slika.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
            g.scale(pxNaMm, pxNaMm);
            g.setColor(Color.BLACK);
            double so = p.stranicaOznake();
            for (double[] o : p.oznake()) {
                g.fill(new Rectangle2D.Double(o[0] - so / 2, o[1] - so / 2, so, so));
            }
            narisiQr(g, qr, p.qr());
            double sv = p.stranicaOznakeVrste();
            for (GeometrijaZapisnika.Pas pas : p.pasovi()) {
                g.fill(new Rectangle2D.Double(pas.levo()[0] - sv / 2, pas.levo()[1] - sv / 2, sv, sv));
                g.fill(new Rectangle2D.Double(pas.desno()[0] - sv / 2, pas.desno()[1] - sv / 2, sv, sv));
            }
            g.setStroke(new BasicStroke((float) p.crta()));
            double pol = p.crta() / 2;
            for (GeometrijaZapisnika.Kvadratek k : p.kvadratki()) {
                g.draw(new Rectangle2D.Double(k.p().x() + pol, k.p().y() + pol, k.p().sirina() - p.crta(),
                        k.p().visina() - p.crta()));
            }
            for (GeometrijaZapisnika.Celica c : p.celice()) {
                g.draw(new Rectangle2D.Double(c.p().x() + pol, c.p().y() + pol, c.p().sirina() - p.crta(),
                        c.p().visina() - p.crta()));
            }
            Random r = new Random(1);
            for (GeometrijaZapisnika.Besedilo b : p.besedila()) {
                String besedilo = switch (b.vrsta()) {
                    case "IME" -> "Priimek" + (char) ('A' + r.nextInt(20)) + " I.";
                    case "OZNAKA_TEKME" -> "A-X";
                    case "NIZ" -> (b.indeks() + 1) + ".";
                    case "DVOPICJE" -> ":";
                    case "EKIPA" -> "NTK Ekipa";
                    case "NASLOV" -> "LIGA SAVINJA B · 6. KOLO";
                    case "EKIPE" -> "NTK Domači : NTK Gostje";
                    case "NAVODILO" -> "Igralca tekme prekrižaj. Napačen križec zapolni do črnega.";
                    case "NOV" -> "Nov igralec: ______________";
                    default -> "";
                };
                if (besedilo.isEmpty()) {
                    continue;
                }
                double velikost = Math.min(b.vrsta().equals("EKIPE") ? 7 : 3.2,
                        b.kot() == 0 ? b.p().visina() * 0.75 : b.p().sirina() * 0.6);
                g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10).deriveFont((float) velikost));
                AffineTransform prej = g.getTransform();
                if (b.vrsta().equals("DVOPICJE")) {
                    // kot v vmesniku: dvopicje na sredini razmika med celicama
                    float sirina = (float) g.getFontMetrics().getStringBounds(besedilo, g).getWidth();
                    g.drawString(besedilo, (float) (b.p().sredinaX() - sirina / 2),
                            (float) (b.p().sredinaY() + velikost * 0.3));
                } else if (b.kot() != 0) {
                    g.translate(b.p().sredinaX() + velikost * 0.35, b.p().y() + b.p().visina() - 0.5);
                    g.rotate(Math.toRadians(b.kot()));
                    g.drawString(besedilo, 0f, 0f);
                } else {
                    g.drawString(besedilo, (float) b.p().x() + 0.5f, (float) (b.p().y() + b.p().visina() * 0.75));
                }
                g.setTransform(prej);
            }
        } finally {
            g.dispose();
        }
        return slika;
    }

    /* QR verzija 1, ECC M, brez tihe cone (cona je prazen papir okrog). */
    private static void narisiQr(Graphics2D g, String vsebina, GeometrijaZapisnika.Pravokotnik p) {
        ByteMatrix m;
        try {
            m = Encoder.encode(vsebina, ErrorCorrectionLevel.M, Map.of(EncodeHintType.QR_VERSION, 1)).getMatrix();
        } catch (com.google.zxing.WriterException e) {
            throw new IllegalStateException(e);
        }
        double modul = p.sirina() / m.getWidth();
        for (int y = 0; y < m.getHeight(); y++) {
            for (int x = 0; x < m.getWidth(); x++) {
                if (m.get(x, y) == 1) {
                    g.fill(new Rectangle2D.Double(p.x() + x * modul, p.y() + y * modul, modul + 0.01, modul + 0.01));
                }
            }
        }
    }

    /* Krizec s kemicnim svincnikom (temno modra) v kvadratek: dve posevni potezi
       z nakljucnim odmikom; kljukica = kratka in dolga poteza. */
    static void krizec(BufferedImage slika, GeometrijaZapisnika.Pravokotnik k, double pxNaMm, Random r, boolean kljukica) {
        Graphics2D g = slika.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.scale(pxNaMm, pxNaMm);
            g.setColor(new Color(25, 35, 110));
            g.setStroke(new BasicStroke((float) (0.3 + r.nextDouble() * 0.2), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            double x0 = k.x() + 0.8 + r.nextGaussian() * 0.3;
            double y0 = k.y() + 0.8 + r.nextGaussian() * 0.3;
            double x1 = k.x() + k.sirina() - 0.8 + r.nextGaussian() * 0.3;
            double y1 = k.y() + k.visina() - 0.8 + r.nextGaussian() * 0.3;
            if (kljukica) {
                double mx = (x0 + x1) / 2 - 0.5;
                g.draw(new Line2D.Double(x0, (y0 + y1) / 2, mx, y1));
                g.draw(new Line2D.Double(mx, y1, x1 + 0.3, y0 - 0.3));
            } else {
                g.draw(new Line2D.Double(x0, y0, x1, y1));
                g.draw(new Line2D.Double(x0 + r.nextGaussian() * 0.2, y1, x1, y0 + r.nextGaussian() * 0.2));
            }
        } finally {
            g.dispose();
        }
    }

    /* Preklican krizec: kvadratek zapolnjen do crnega (gostim pisanjem). */
    static void pocrni(BufferedImage slika, GeometrijaZapisnika.Pravokotnik k, double pxNaMm, Random r) {
        Graphics2D g = slika.createGraphics();
        try {
            g.scale(pxNaMm, pxNaMm);
            g.setColor(new Color(25, 35, 110));
            double rob = 0.5 + r.nextDouble() * 0.3;
            g.fill(new Rectangle2D.Double(k.x() + rob, k.y() + rob, k.sirina() - 2 * rob, k.visina() - 2 * rob));
        } finally {
            g.dispose();
        }
    }
}
