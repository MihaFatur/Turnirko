/* Branje listkov na simuliranih fotografijah (LokalniBralnikListka od fotografije
   do prepisa).

   Na listek, kot ga natisne vmesnik (resources/branje/listek-5-nizov.png, posnetek
   razvojne strani /dev/listek v brezglavem Chromu, 11,34 px/mm; od S1 z okencem
   mize in QR s skrivnostjo), se v celice narisejo rocne stevke iz preizkusnega
   dela MNIST - teh mreza pri ucenju ni videla. Listek se nato »fotografira«:
   zasuk, perspektiva telefona, neenakomerna svetloba, sum in stiskanje JPEG.
   Zahtevne fotografije imajo se stevke, ki se dotikajo ali silijo cez celico,
   vecji zasuk in perspektivo ter zamegljeno sliko. Tretja raven (neugodne, F2.7)
   doda prepognjen listek, prst cez oznako, precrtane in prepisane celice ter
   enice z nosom in precrtane sedmice. Vecji vzorec meri SimulacijaBralnikaPoskus.

   Domenska pravila, ki jih ta test varuje:
   - bralnik najde oznake in celice tudi na zasukani fotografiji s perspektivo;
   - prazen listek nima nobenega prebranega niza;
   - velika vecina celic je prebrana prav, napacna celica pa je skoraj vedno
     oznacena kot negotova (organizator jo pogleda) - tiha napaka je redka;
   - stevilka mize iz okenca (S1) je prebrana prav ali oznacena kot negotova;
     listek pred S1 (listek-5-nizov-pred-s1.png) se prebere kot prej in mize nima;
   - hrbtna stran lista s kodo za sodnika (S2), ki prosojno sije skozi papir,
     branja ne pokvari.
   Meje so postavljene pod izmerjene vrednosti (pisava iz MNIST ni pisava v
   dvorani); pravo merilo je stevilo potrjenih listkov brez popravka na strani
   Branje listkov. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import si.turnirko.branje.CeliceListka;
import si.turnirko.branje.GeometrijaListka;
import si.turnirko.branje.MizaListka;
import si.turnirko.branje.PoravnavaListka;
import si.turnirko.branje.Preslikava;
import si.turnirko.branje.Temnost;
import si.turnirko.branje.UcenjeStevk;

class SimuliraneFotografijeTest {

    /* Pogoji simulacije: razmik med stevkama (mm, negativno = prekrivanje),
       visina stevke (delez celice), zasuk (st.), perspektiva (delez), zamegljenost;
       tretja raven (F2.7) se: prepognjen listek, verjetnost prsta cez eno oznako,
       verjetnost precrtane in popravljene celice, verjetnost evropske enice z nosom
       oziroma precrtane sedmice. Pri prvih dveh ravneh so dodatki izklopljeni in ne
       porabijo nakljucnih stevil (drugi testi z istim semenom dobijo iste slike). */
    record Pogoji(double razmikOd, double razmikDo, double visinaOd, double visinaDo,
                  double kot, double perspektiva, boolean zamegli,
                  boolean pregib, double prst, double popravek, double evropske) {
        Pogoji(double razmikOd, double razmikDo, double visinaOd, double visinaDo,
               double kot, double perspektiva, boolean zamegli) {
            this(razmikOd, razmikDo, visinaOd, visinaDo, kot, perspektiva, zamegli, false, 0, 0, 0);
        }
    }

    static final Pogoji OBICAJNO = new Pogoji(0.1, 1.0, 0.6, 0.85, 12, 0.08, false);
    static final Pogoji ZAHTEVNO = new Pogoji(-0.3, 0.8, 0.6, 1.05, 20, 0.14, true);
    /* Tretja raven: zahtevna fotografija prepognjenega listka; pri polovici listkov
       prst cez eno od oznak, 5 % celic precrtanih in popravljenih, polovica enic in
       sedmic po evropsko (nos, precka). */
    static final Pogoji NEUGODNO = new Pogoji(-0.3, 0.8, 0.6, 1.05, 20, 0.14, true, true, 0.5, 0.05, 0.5);

    /* Oznake na listku kot delez sirine in visine slike (za prst cez oznako). */
    private static final double[][] OZNAKE_LISTKA = {
            {GeometrijaListka.ZGORAJ_LEVO[0] / GeometrijaListka.SIRINA, GeometrijaListka.ZGORAJ_LEVO[1] / GeometrijaListka.VISINA},
            {GeometrijaListka.SPODAJ_LEVO[0] / GeometrijaListka.SIRINA, GeometrijaListka.SPODAJ_LEVO[1] / GeometrijaListka.VISINA},
            {GeometrijaListka.SPODAJ_DESNO[0] / GeometrijaListka.SIRINA, GeometrijaListka.SPODAJ_DESNO[1] / GeometrijaListka.VISINA}};

    /* mizaPrav/mizaTihe: delez prebranih listkov s pravo mizo oziroma z napacno, ki ni
       oznacena kot negotova (S1). */
    record Meritev(int listkov, int neprebranih, double prav, double tihe, int brezPopravka, double msNaListek,
                   double mizaPrav, double mizaTihe) {}

    static BufferedImage listek;
    static double pxNaMm;
    private static List<UcenjeStevk.Vzorec> stevke;
    static CeliceListka.Tabela celice;
    /* Okence mize na cistem listku (S1). */
    static CeliceListka.Celica miza;
    static boolean mizaZanesljiva;
    static LokalniBralnikListka bralnik;

    /* Fotografija listka z danim izidom (za druge teste: ucenje iz potrjenih listkov). */
    static byte[] fotografijaIzida(int[][] izid, Random r) throws IOException {
        if (listek == null) {
            pripravi();
        }
        BufferedImage izpolnjen = kopija(listek);
        for (int i = 0; i < izid.length; i++) {
            napisi(izpolnjen, celice.nizi().get(i).levo(), izid[i][0], r, OBICAJNO);
            napisi(izpolnjen, celice.nizi().get(i).desno(), izid[i][1], r, OBICAJNO);
        }
        int[] d = dobljeni(izid);
        napisi(izpolnjen, celice.rezultat().levo(), d[0], r, OBICAJNO);
        napisi(izpolnjen, celice.rezultat().desno(), d[1], r, OBICAJNO);
        return fotografija(izpolnjen, r, OBICAJNO);
    }

    @BeforeAll
    static void pripravi() throws IOException {
        try (InputStream v = SimuliraneFotografijeTest.class.getResourceAsStream("/branje/listek-5-nizov.png")) {
            listek = ImageIO.read(v);
        }
        pxNaMm = listek.getWidth() / GeometrijaListka.SIRINA;
        try (InputStream v = SimuliraneFotografijeTest.class.getResourceAsStream("/branje/mnist-preizkus.bin.gz")) {
            stevke = UcenjeStevk.beriIzbor(v);
        }
        // lega celic na cistem listku (iz istega postopka, ki ga uporablja bralnik)
        double[][] qr = KodaListka.najdi(listek).getFirst().tocke();
        Temnost poravnan = PoravnavaListka.poravnaj(listek, qr).slika();
        celice = CeliceListka.najdi(poravnan);
        MizaListka.Okence okence = MizaListka.najdi(poravnan);
        miza = okence == null ? null : okence.celica();
        mizaZanesljiva = okence != null && okence.zanesljivo();
        VirMrezeStevk vir = new VirMrezeStevk(null);
        VirMrezeStevk.Nalozena vgrajena = VirMrezeStevk.vgrajena();
        vir.nastavi(vgrajena.mreza(), vgrajena.oznaka());
        bralnik = new LokalniBralnikListka(vir);
    }

    @Test
    void cistListekImaPetVrsticInRezultat() {
        assertNotNull(celice);
        assertEquals(5, celice.nizi().size());
    }

    @Test
    void prazenListekNimaPrebranihNizov() throws IOException {
        Random r = new Random(1);
        PrebranListek p = bralnik.preberi(fotografija(kopija(listek), r, OBICAJNO)).listek();
        assertEquals(5, p.nizi().size());
        assertTrue(p.nizi().stream().allMatch(n -> n.levo() == null && n.desno() == null), p.nizi().toString());
        assertNull(p.rezultatLevo());
        assertNull(p.miza());
        assertFalse(p.negotovaMiza(), "prazno okence mize ni negotovo");
    }

    /* S1: okence mize lezi na stalnem mestu glave (GeometrijaListka.MIZA). */
    @Test
    void cistListekImaOkenceMize() {
        assertNotNull(miza, "okence mize ni najdeno");
        assertTrue(mizaZanesljiva, "okence na cistem listku je zanesljivo");
        int r = GeometrijaListka.PX_NA_MM;
        assertTrue(Math.abs(miza.x0() - GeometrijaListka.MIZA[0] * r) < 15, "x0 " + miza.x0());
        assertTrue(Math.abs(miza.x1() - GeometrijaListka.MIZA[2] * r) < 15, "x1 " + miza.x1());
        assertTrue(Math.abs(miza.y0() - GeometrijaListka.MIZA[1] * r) < 15, "y0 " + miza.y0());
        assertTrue(Math.abs(miza.y1() - GeometrijaListka.MIZA[3] * r) < 15, "y1 " + miza.y1());
    }

    /* Listki, natisnjeni pred S1 (crta »Miza st.« namesto okenca, QR brez skrivnosti),
       se se vedno preberejo; mize nimajo, tudi ce je na crti napisana stevilka. */
    @Test
    void listekPredS1SePrebereBrezMize() throws IOException {
        BufferedImage star;
        try (InputStream v = SimuliraneFotografijeTest.class.getResourceAsStream("/branje/listek-5-nizov-pred-s1.png")) {
            star = ImageIO.read(v);
        }
        double[][] qr = KodaListka.najdi(star).getFirst().tocke();
        Temnost poravnan = PoravnavaListka.poravnaj(star, qr).slika();
        assertNull(MizaListka.najdi(poravnan), "listek pred S1 nima okenca mize");
        CeliceListka.Tabela stareCelice = CeliceListka.najdi(poravnan);
        Random r = new Random(26112026L);
        int[][] izid = {{11, 4}, {7, 11}, {11, 9}};
        BufferedImage izpolnjen = kopija(star);
        for (int i = 0; i < izid.length; i++) {
            napisi(izpolnjen, stareCelice.nizi().get(i).levo(), izid[i][0], r, OBICAJNO);
            napisi(izpolnjen, stareCelice.nizi().get(i).desno(), izid[i][1], r, OBICAJNO);
        }
        // stevilka na crti »Miza st.« (x 60-80 mm, nad crto pri ~9,7 mm)
        napisi(izpolnjen, new CeliceListka.Celica(640, 40, 760, 92), 7, r, OBICAJNO);
        PrebranListek p = bralnik.preberi(fotografija(izpolnjen, r, OBICAJNO)).listek();
        assertEquals(11, p.nizi().get(0).levo());
        assertEquals(4, p.nizi().get(0).desno());
        assertNull(p.miza());
        assertFalse(p.negotovaMiza());
    }

    @Test
    void obicajneFotografijeSoPrebranePrav() throws IOException {
        Meritev m = izmeri(new Random(19112026L), 30, OBICAJNO);
        assertEquals(0, m.neprebranih(), "vsak listek mora biti poravnan");
        assertTrue(m.prav() >= 0.95, "premalo pravilnih celic: " + m.prav());
        assertTrue(m.tihe() <= 0.01, "prevec tihih napak: " + m.tihe());
        // S1: miza iz okenca (izmerjeno 22. 11. 2026: 100 % prav, 0 tihih)
        assertTrue(m.mizaPrav() >= 0.95, "premalo pravilnih miz: " + m.mizaPrav());
        assertEquals(0, m.mizaTihe(), "tiha napaka mize");
    }

    @Test
    void zahtevneFotografijeImajoRedkeTiheNapake() throws IOException {
        Meritev m = izmeri(new Random(20112026L), 30, ZAHTEVNO);
        assertEquals(0, m.neprebranih(), "vsak listek mora biti poravnan");
        assertTrue(m.prav() >= 0.85, "premalo pravilnih celic: " + m.prav());
        assertTrue(m.tihe() <= 0.03, "prevec tihih napak: " + m.tihe());
        // S1: izmerjeno 100 % prav, 0 tihih
        assertTrue(m.mizaPrav() >= 0.9, "premalo pravilnih miz: " + m.mizaPrav());
        assertTrue(m.mizaTihe() <= 0.034, "prevec tihih napak mize: " + m.mizaTihe());
    }

    /* F4.1: bralnik vrne poravnan listek (JPEG v sivinah, 10 px/mm) in lego celic;
       vsaka celica lezi na sliki, slika je dovolj majhna za seznam na telefonu. */
    @Test
    void poravnanListekZaIzrezCelic() throws IOException {
        BralnikListka.Branje b = bralnik.preberi(fotografijaIzida(new int[][] {{11, 4}, {7, 11}, {11, 9}},
                new Random(25112026L)));
        BralnikListka.Poravnava p = b.poravnava();
        assertNotNull(p);
        assertEquals(1050, p.celice().sirina());
        assertEquals(990, p.celice().visina());
        assertEquals(5, p.celice().nizi().size());
        assertTrue(p.jpeg().length < 250_000, "poravnan listek je prevelik: " + p.jpeg().length);
        BufferedImage slika = ImageIO.read(new java.io.ByteArrayInputStream(p.jpeg()));
        assertEquals(1050, slika.getWidth());
        for (int[] v : p.celice().nizi()) {
            assertTrue(v[0] >= 0 && v[2] < 1050 && v[1] >= 0 && v[3] < 990 && v[0] < v[2] && v[4] > v[2],
                    java.util.Arrays.toString(v));
        }
    }

    /* F2.6: prst cez eno od treh oznak ni vec napaka - listek se poravna po QR in
       preostalih dveh oznakah. */
    @Test
    void prstCezOznakoNiNapaka() throws IOException {
        Pogoji o = OBICAJNO;
        Meritev m = izmeri(new Random(22112026L), 12, new Pogoji(o.razmikOd(), o.razmikDo(), o.visinaOd(),
                o.visinaDo(), o.kot(), o.perspektiva(), o.zamegli(), false, 1, 0, 0));
        assertEquals(0, m.neprebranih(), "listek s prstom cez oznako mora biti poravnan");
        assertTrue(m.prav() >= 0.95, "premalo pravilnih celic: " + m.prav());
    }

    /* F2.6: na prepognjenem listku so stolpca tabele lahko zamaknjena in nagnjena;
       celice se se vedno najdejo. */
    @Test
    void prepognjenListekSePrebere() throws IOException {
        Pogoji o = OBICAJNO;
        Meritev m = izmeri(new Random(23112026L), 12, new Pogoji(o.razmikOd(), o.razmikDo(), o.visinaOd(),
                o.visinaDo(), o.kot(), o.perspektiva(), o.zamegli(), true, 0, 0, 0));
        assertEquals(0, m.neprebranih(), "prepognjen listek mora biti prebran");
        assertTrue(m.prav() >= 0.95, "premalo pravilnih celic: " + m.prav());
        // S1: pregib cez okence mize ne sme dati tihe napake (senca pregiba = »1«)
        assertEquals(0, m.mizaTihe(), "tiha napaka mize na prepognjenem listku");
    }

    /* F2.5: precrtana in prepisana vrednost je vedno negotova in ima opombo;
       tiha napaka v taki celici ni dovoljena. */
    @Test
    void popravljenaCelicaJeNegotovaZOpombo() throws IOException {
        if (listek == null) {
            pripravi();
        }
        Random r = new Random(24112026L);
        int[][] izid = {{11, 4}, {7, 11}, {11, 9}};
        BufferedImage izpolnjen = kopija(listek);
        for (int i = 0; i < izid.length; i++) {
            napisi(izpolnjen, celice.nizi().get(i).levo(), izid[i][0], r, OBICAJNO);
            if (i == 1) {
                napisiPopravljeno(izpolnjen, celice.nizi().get(i).desno(), izid[i][1], r, OBICAJNO);
            } else {
                napisi(izpolnjen, celice.nizi().get(i).desno(), izid[i][1], r, OBICAJNO);
            }
        }
        PrebranListek p = bralnik.preberi(fotografija(izpolnjen, r, OBICAJNO)).listek();
        assertTrue(p.nizi().get(1).negotovoDesno(), "popravljena celica mora biti negotova");
        assertNotNull(p.opomba());
        assertTrue(p.opomba().contains("Popravljena celica (2. niz)"), p.opomba());
        assertTrue(p.nizi().get(0).levo() == 11 && !p.nizi().get(0).negotovoLevo(), "navadna celica ostane gotova");
    }

    /* Tretja raven (F2.7): prepognjen listek, prst cez oznako, popravljene celice,
       evropske enice in sedmice. Meje pod izmerjenimi (SimulacijaBralnikaPoskus). */
    @Test
    void neugodneFotografijeSoPrebraneInNapakeOznacene() throws IOException {
        Meritev m = izmeri(new Random(21112026L), 30, NEUGODNO);
        assertTrue(m.neprebranih() <= 2, "prevec neprebranih listkov: " + m.neprebranih());
        assertTrue(m.prav() >= 0.88, "premalo pravilnih celic: " + m.prav());
        assertTrue(m.tihe() <= 0.02, "prevec tihih napak: " + m.tihe());
        // S1: izmerjeno 93 % prav, 0 tihih (pregib cez okence: miza negotova)
        assertTrue(m.mizaPrav() >= 0.85, "premalo pravilnih miz: " + m.mizaPrav());
        assertTrue(m.mizaTihe() <= 0.034, "prevec tihih napak mize: " + m.mizaTihe());
    }

    /* S2: hrbtna stran lista (koda do strani za sodnika, HrbetListka) prosojno sije
       skozi papir za tabelo tock. Listek na mizi: papir 80 g/m2 je pod natisom hrbta
       za ~8 % temnejsi (neprosojnost ~92 %); meja 15 % je z rezervo (tanjsi papir,
       svetla miza). Branje mora ostati kot na cistem listku: koda tekme najdena,
       celice prav, miza brez tihe napake. Izmerjeno 22. 11. 2026: pri 15 % 100 %
       celic prav (cist listek 99,6 %); pri 30 % (listek proti luci) 85 % prav in
       0,4 % tihih - zato hrbet sodniku naroci, naj listek polozi na mizo. */
    @Test
    void hrbetNeMotiBranja() throws IOException {
        BufferedImage zHrbtom = sProsojnimHrbtom(listek, 0.15);
        Meritev m = izmeri(new Random(19112026L), 30, OBICAJNO, bralnik, zHrbtom);
        assertEquals(0, m.neprebranih(), "listek s prosojnim hrbtom mora biti poravnan");
        assertTrue(m.prav() >= 0.95, "premalo pravilnih celic: " + m.prav());
        assertTrue(m.tihe() <= 0.01, "prevec tihih napak: " + m.tihe());
        assertEquals(0, m.mizaTihe(), "tiha napaka mize");
    }

    /* Listek, skozi katerega sije hrbet: zrcaljena vsebina HrbetListka (naslov, QR
       26 mm do /s, naslov strani, trije koraki) na sredini mesta, kot jo natisne
       obojestranski tisk z obratom po dolgem robu; »temnost« = za koliko natis
       hrbta zatemni papir. Brez zameglitve - ostri robovi so za iskanje crt
       tabele strozji od pravih. */
    static BufferedImage sProsojnimHrbtom(BufferedImage cist, double temnost) {
        int w = cist.getWidth();
        int h = cist.getHeight();
        double mm = w / GeometrijaListka.SIRINA;
        BufferedImage hrbet = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        java.awt.Graphics2D g = hrbet.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setColor(java.awt.Color.BLACK);
        g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
                java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        // obrat po dolgem robu: hrbet je vodoravno zrcaljen
        g.translate(w, 0);
        g.scale(-1, 1);
        double sredina = GeometrijaListka.SIRINA / 2;
        // naslov (11 pt krepko) nad kodo
        g.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, (int) Math.round(3.9 * mm)));
        sredinsko(g, "Sodnik? Izid pošlji s telefonom.", sredina * mm, 27 * mm);
        // QR 26 mm (mirna cona 1 mm nad in pod)
        com.google.zxing.qrcode.encoder.ByteMatrix qr;
        try {
            qr = com.google.zxing.qrcode.encoder.Encoder.encode("https://turnirko-nt.si/s",
                    com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M).getMatrix();
        } catch (com.google.zxing.WriterException e) {
            throw new IllegalStateException(e);
        }
        double modul = 26.0 / qr.getWidth();
        double x0 = sredina - 13;
        double y0 = 30;
        for (int y = 0; y < qr.getHeight(); y++) {
            for (int x = 0; x < qr.getWidth(); x++) {
                if (qr.get(x, y) == 1) {
                    g.fill(new java.awt.geom.Rectangle2D.Double((x0 + x * modul) * mm, (y0 + y * modul) * mm,
                            modul * mm, modul * mm));
                }
            }
        }
        // naslov strani in trije koraki (8 pt)
        g.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.BOLD, (int) Math.round(3.3 * mm)));
        sredinsko(g, "turnirko-nt.si/s", sredina * mm, 62 * mm);
        g.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, (int) Math.round(2.8 * mm)));
        String[] koraki = {"1. Po tekmi skeniraj to kodo s kamero telefona.",
                "2. Obrni listek, polozi ga na mizo in ga fotografiraj od zgoraj, celega.",
                "3. Preveri tocke in pritisni Potrdi izid. Listek oddaj na pultu."};
        for (int i = 0; i < koraki.length; i++) {
            g.drawString(koraki[i], (float) (18 * mm), (float) ((68 + 3.8 * i) * mm));
        }
        g.dispose();
        BufferedImage rezultat = kopija(cist);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double crnilo = 1 - (hrbet.getRaster().getSample(x, y, 0) / 255.0);
                if (crnilo <= 0) {
                    continue;
                }
                double faktor = 1 - temnost * crnilo;
                int rgb = rezultat.getRGB(x, y);
                int rr = (int) Math.round(((rgb >> 16) & 0xff) * faktor);
                int gg = (int) Math.round(((rgb >> 8) & 0xff) * faktor);
                int bb = (int) Math.round((rgb & 0xff) * faktor);
                rezultat.setRGB(x, y, (rr << 16) | (gg << 8) | bb);
            }
        }
        return rezultat;
    }

    private static void sredinsko(java.awt.Graphics2D g, String besedilo, double cx, double y) {
        double sirina = g.getFontMetrics().stringWidth(besedilo);
        g.drawString(besedilo, (float) (cx - sirina / 2), (float) y);
    }

    /* Tekme meritve po pogostosti izidov iz zgodovine (PogostostNizov.VGRAJENA)
       namesto enakomerno; nastavi SimulacijaBralnikaPoskus pred meritvijo. */
    static volatile boolean tekmePoZgodovini = false;

    static Meritev izmeri(Random r, int listkov, Pogoji pogoji) throws IOException {
        if (listek == null) {
            pripravi();
        }
        return izmeri(r, listkov, pogoji, bralnik);
    }

    static LokalniBralnikListka vgrajenaMreza(double beta, boolean vecBranj) throws IOException {
        if (listek == null) {
            pripravi();
        }
        VirMrezeStevk vir = new VirMrezeStevk(null);
        VirMrezeStevk.Nalozena vgrajena = VirMrezeStevk.vgrajena();
        vir.nastavi(vgrajena.mreza(), vgrajena.oznaka());
        return new LokalniBralnikListka(vir, null, beta, vecBranj);
    }

    static Meritev izmeri(Random r, int listkov, Pogoji pogoji, LokalniBralnikListka bralnik) throws IOException {
        return izmeri(r, listkov, pogoji, bralnik, listek);
    }

    /* »osnova«: natisnjen listek, na katerega se pise (cist ali s prosojnim hrbtom). */
    static Meritev izmeri(Random r, int listkov, Pogoji pogoji, LokalniBralnikListka bralnik,
                          BufferedImage osnova) throws IOException {
        int celic = 0;
        int prav = 0;
        int tihih = 0;
        int brezPopravka = 0;
        int neprebranih = 0;
        long nanosekund = 0;
        long bajtov = 0;
        int mizaPrav = 0;
        int mizaTihih = 0;
        int prebranih = 0;
        for (int k = 0; k < listkov; k++) {
            int[][] izid = tekmePoZgodovini ? tekmaPoZgodovini(r, 3) : nakljucnaTekma(r, 3);
            BufferedImage izpolnjen = kopija(osnova);
            for (int i = 0; i < izid.length; i++) {
                napisiCelico(izpolnjen, celice.nizi().get(i).levo(), izid[i][0], r, pogoji);
                napisiCelico(izpolnjen, celice.nizi().get(i).desno(), izid[i][1], r, pogoji);
            }
            int[] dobljeni = dobljeni(izid);
            if (r.nextDouble() < 0.8) {
                napisi(izpolnjen, celice.rezultat().levo(), dobljeni[0], r, pogoji);
                napisi(izpolnjen, celice.rezultat().desno(), dobljeni[1], r, pogoji);
            }
            // miza (S1) z locenim virom nakljucnosti: zaporedje r (in s tem vse meritve
            // celic) ostane, kot je bilo pred S1; okence ostane prazno pri desetini listkov
            Integer pisanaMiza = null;
            if (miza != null) {
                Random rm = new Random(1_000_003L * k + listkov);
                if (rm.nextDouble() < 0.9) {
                    pisanaMiza = 1 + rm.nextInt(30);
                    napisi(izpolnjen, miza, pisanaMiza, rm, pogoji);
                }
            }
            PrebranListek p;
            byte[] foto = fotografija(izpolnjen, r, pogoji);
            bajtov += foto.length;
            // za ogled simulacije: -Dsimulacija.slike=<mapa> shrani prvih pet fotografij ravni
            String mapa = System.getProperty("simulacija.slike");
            if (mapa != null && k < 5) {
                java.nio.file.Files.write(java.nio.file.Path.of(mapa,
                        String.format("%s-%d.jpg", pogoji == NEUGODNO ? "neugodno" : "raven" + pogoji.hashCode(), k)), foto);
            }
            long zacetek = System.nanoTime();
            try {
                p = bralnik.preberi(foto).listek();
            } catch (BralnikListka.NapakaBranjaListka e) {
                neprebranih++;
                System.out.println("Listek " + k + " ni prebran: " + e.getMessage());
                continue;
            } finally {
                nanosekund += System.nanoTime() - zacetek;
            }
            prebranih++;
            if (java.util.Objects.equals(p.miza(), pisanaMiza)) {
                mizaPrav++;
            } else if (!p.negotovaMiza()) {
                mizaTihih++;
                System.out.printf("TIHA NAPAKA MIZE listek %d: %s prebrano kot %s%n", k, pisanaMiza, p.miza());
            }
            boolean vsePrav = true;
            for (int i = 0; i < izid.length; i++) {
                PrebranListek.Niz n = p.nizi().get(i);
                for (int s = 0; s < 2; s++) {
                    Integer prebrano = s == 0 ? n.levo() : n.desno();
                    boolean negotovo = s == 0 ? n.negotovoLevo() : n.negotovoDesno();
                    celic++;
                    if (prebrano != null && prebrano == izid[i][s]) {
                        prav++;
                    } else {
                        vsePrav = false;
                        if (!negotovo) {
                            tihih++;
                            // za ogled: -Dsimulacija.slike=<mapa> shrani tudi listke s tiho napako
                            if (mapa != null) {
                                String vrsta = pogoji.pregib() ? "pregib" : pogoji.evropske() > 0 ? "evropske" : "druge";
                                System.out.printf("TIHA NAPAKA %s listek %d, %d. niz %s: %d prebrano kot %s%n",
                                        vrsta, k, i + 1, s == 0 ? "levo" : "desno", izid[i][s], prebrano);
                                java.nio.file.Files.write(java.nio.file.Path.of(mapa, "tiha-" + vrsta + "-" + k + ".jpg"), foto);
                            }
                        }
                    }
                }
            }
            if (vsePrav && PredlogIzidaListka.sestavi(p, 5, false).pripravljen()) {
                brezPopravka++;
            }
        }
        Meritev m = new Meritev(listkov, neprebranih, (double) prav / Math.max(1, celic),
                (double) tihih / Math.max(1, celic), brezPopravka, nanosekund / 1e6 / Math.max(1, listkov),
                (double) mizaPrav / Math.max(1, prebranih), (double) mizaTihih / Math.max(1, prebranih));
        System.out.printf("Simulirane fotografije %s (%d kB na listek): %s%n",
                pogoji == NEUGODNO ? "neugodne" : pogoji == ZAHTEVNO ? "zahtevne" : "obicajne",
                bajtov / 1024 / Math.max(1, listkov), m);
        return m;
    }

    // ---------- Simulacija ----------

    /* Nakljucna veljavna tekma na »zaZmago« nizov (vecinoma 11:x, obcasno podaljsek). */
    private static int[][] nakljucnaTekma(Random r, int zaZmago) {
        List<int[]> nizi = new ArrayList<>();
        int a = 0;
        int b = 0;
        while (a < zaZmago && b < zaZmago) {
            boolean prvi = r.nextBoolean();
            int porazenec;
            int zmagovalec;
            if (r.nextDouble() < 0.2) {
                zmagovalec = 12 + r.nextInt(4);
                porazenec = zmagovalec - 2;
            } else {
                zmagovalec = 11;
                porazenec = r.nextInt(10);
            }
            nizi.add(prvi ? new int[] {zmagovalec, porazenec} : new int[] {porazenec, zmagovalec});
            if (prvi) {
                a++;
            } else {
                b++;
            }
        }
        return nizi.toArray(new int[0][]);
    }

    /* Nakljucna tekma z nizi po pogostosti iz zgodovine (vgrajena porazdelitev). */
    private static int[][] tekmaPoZgodovini(Random r, int zaZmago) {
        List<int[]> izidi = new ArrayList<>();
        List<Double> kumulativa = new ArrayList<>();
        double vsota = 0;
        for (int z = 11; z <= si.turnirko.branje.PogostostNizov.NAJVEC_PODALJSEK; z++) {
            for (int p = 0; p <= z - 2; p++) {
                double v = si.turnirko.branje.PogostostNizov.VGRAJENA.verjetnost(z, p);
                if (v > 0) {
                    vsota += v;
                    izidi.add(new int[] {z, p});
                    kumulativa.add(vsota);
                }
            }
        }
        List<int[]> nizi = new ArrayList<>();
        int a = 0;
        int b = 0;
        while (a < zaZmago && b < zaZmago) {
            double x = r.nextDouble() * vsota;
            int i = 0;
            while (kumulativa.get(i) < x) {
                i++;
            }
            int[] niz = izidi.get(i);
            boolean prvi = r.nextBoolean();
            nizi.add(prvi ? new int[] {niz[0], niz[1]} : new int[] {niz[1], niz[0]});
            if (prvi) {
                a++;
            } else {
                b++;
            }
        }
        return nizi.toArray(new int[0][]);
    }

    private static int[] dobljeni(int[][] izid) {
        int[] d = new int[2];
        for (int[] n : izid) {
            d[n[0] > n[1] ? 0 : 1]++;
        }
        return d;
    }

    /* Celica niza: obicajno samo vrednost; pri tretji ravni sodnik vcasih najprej
       napise napacno vrednost, jo precrta in pravo napise desno od nje. */
    private static void napisiCelico(BufferedImage slika, CeliceListka.Celica c, int vrednost, Random r, Pogoji pogoji) {
        if (pogoji.popravek() > 0 && r.nextDouble() < pogoji.popravek()) {
            napisiPopravljeno(slika, c, vrednost, r, pogoji);
            return;
        }
        napisi(slika, c, vrednost, r, pogoji);
    }

    /* Napacna vrednost levo, precrtana, prava desno od nje. */
    static void napisiPopravljeno(BufferedImage slika, CeliceListka.Celica c, int vrednost, Random r, Pogoji pogoji) {
        int napacna;
        do {
            napacna = r.nextInt(14);
        } while (napacna == vrednost);
        double[] okvir = napisi(slika, c, napacna, r, pogoji, 0.04);
        precrtaj(slika, okvir, r);
        napisi(slika, c, vrednost, r, pogoji, 0.5);
    }

    static void napisi(BufferedImage slika, CeliceListka.Celica c, int vrednost, Random r, Pogoji pogoji) {
        napisi(slika, c, vrednost, r, pogoji, 0.3);
    }

    /* Vrednost z rocnimi stevkami MNIST v celico (kemicni svincnik: temno modra),
       zacetek na deležu »xOd« do xOd + 0,2 sirine celice; vrne okvir zapisa (x0, y0, x1, y1). */
    private static double[] napisi(BufferedImage slika, CeliceListka.Celica c, int vrednost, Random r, Pogoji pogoji,
                                   double xOd) {
        String zapis = String.valueOf(vrednost);
        double r10 = GeometrijaListka.PX_NA_MM;
        double x0 = c.x0() / r10 * pxNaMm;
        double y0 = c.y0() / r10 * pxNaMm;
        double w = c.sirina() / r10 * pxNaMm;
        double h = c.visina() / r10 * pxNaMm;
        double visina = h * (pogoji.visinaOd() + r.nextDouble() * (pogoji.visinaDo() - pogoji.visinaOd()));
        double razmik = pxNaMm * (pogoji.razmikOd() + r.nextDouble() * (pogoji.razmikDo() - pogoji.razmikOd()));
        double x = x0 + w * (xOd + r.nextDouble() * 0.2);
        double y = y0 + (h - visina) / 2 + (r.nextDouble() - 0.5) * h * 0.1;
        double zacetek = x;
        for (char znak : zapis.toCharArray()) {
            int stevka = znak - '0';
            UcenjeStevk.Vzorec v;
            do {
                v = stevke.get(r.nextInt(stevke.size()));
            } while (v.oznaka() != stevka);
            byte[] oblika = v.slika();
            if (pogoji.evropske() > 0 && (stevka == 1 || stevka == 7) && r.nextDouble() < pogoji.evropske()) {
                oblika = evropska(oblika, stevka, r);
            }
            x += narisiStevko(slika, oblika, x, y, visina) + razmik;
        }
        return new double[] {zacetek, y, x - razmik, y + visina};
    }

    /* Precrtan zapis: dve do stiri poteze kemicnega svincnika cik-cak cez okvir. */
    private static void precrtaj(BufferedImage slika, double[] okvir, Random r) {
        java.awt.Graphics2D g = slika.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new java.awt.Color(25, 35, 110));
        g.setStroke(new java.awt.BasicStroke((float) (pxNaMm * (0.3 + r.nextDouble() * 0.15)),
                java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
        int poteze = 2 + r.nextInt(3);
        double x0 = okvir[0] - pxNaMm * 0.5;
        double x1 = okvir[2] + pxNaMm * 0.5;
        double xp = x0;
        double yp = okvir[1] + r.nextDouble() * (okvir[3] - okvir[1]) * 0.3;
        for (int i = 0; i < poteze; i++) {
            double xn = i % 2 == 0 ? x1 : x0;
            double yn = okvir[1] + (okvir[3] - okvir[1]) * (0.2 + 0.8 * (i + 1) / poteze) + (r.nextDouble() - 0.5) * pxNaMm;
            g.draw(new java.awt.geom.Line2D.Double(xp, yp, xn, yn));
            xp = xn;
            yp = yn;
        }
        g.dispose();
    }

    /* Enica z nosom (poteza z vrha stebla levo navzdol) ali precrtana sedmica
       (precka cez sredino stebla), kot ju pisejo v Sloveniji; MNIST ju skoraj nima. */
    static byte[] evropska(byte[] mnist, int stevka, Random r) {
        byte[] s = mnist.clone();
        int minY = 28;
        int maxY = -1;
        for (int i = 0; i < 784; i++) {
            if ((s[i] & 0xFF) > 50) {
                minY = Math.min(minY, i / 28);
                maxY = Math.max(maxY, i / 28);
            }
        }
        if (maxY < 0) {
            return s;
        }
        double h = maxY - minY + 1;
        if (stevka == 1) {
            double xt = sredinaVrstice(s, minY);
            double dolzina = h * (0.3 + r.nextDouble() * 0.15);
            double kot = Math.toRadians(30 + r.nextDouble() * 25);
            crta(s, xt, minY + 0.5, xt - dolzina * Math.cos(kot), minY + 0.5 + dolzina * Math.sin(kot), 1.8);
        } else {
            int yc = (int) Math.round(minY + h * (0.5 + r.nextDouble() * 0.1));
            double xc = sredinaVrstice(s, yc);
            double pol = h * (0.15 + r.nextDouble() * 0.08);
            crta(s, xc - pol, yc, xc + pol, yc + (r.nextDouble() - 0.5), 1.8);
        }
        return s;
    }

    private static double sredinaVrstice(byte[] s, int y) {
        double vsota = 0;
        double x = 0;
        for (int i = 0; i < 28; i++) {
            int v = s[y * 28 + i] & 0xFF;
            vsota += v;
            x += v * i;
        }
        return vsota > 0 ? x / vsota : 14;
    }

    /* Poteza debeline »debelina« px v sliko 28 x 28 (najvecja vrednost, mehki rob). */
    private static void crta(byte[] s, double x0, double y0, double x1, double y1, double debelina) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double d2 = dx * dx + dy * dy;
        for (int y = 0; y < 28; y++) {
            for (int x = 0; x < 28; x++) {
                double t = d2 == 0 ? 0 : Math.max(0, Math.min(1, ((x - x0) * dx + (y - y0) * dy) / d2));
                double razdalja = Math.hypot(x - (x0 + t * dx), y - (y0 + t * dy));
                double a = Math.max(0, Math.min(1, debelina / 2 + 0.5 - razdalja));
                int v = (int) Math.round(255 * a);
                if (v > (s[y * 28 + x] & 0xFF)) {
                    s[y * 28 + x] = (byte) v;
                }
            }
        }
    }

    /* Narise stevko (obrezano na crnilo) z visino »visina«; vrne njeno sirino. */
    static double narisiStevko(BufferedImage slika, byte[] mnist, double x, double y, double visina) {
        int minX = 28;
        int maxX = -1;
        int minY = 28;
        int maxY = -1;
        for (int i = 0; i < 784; i++) {
            if ((mnist[i] & 0xFF) > 50) {
                minX = Math.min(minX, i % 28);
                maxX = Math.max(maxX, i % 28);
                minY = Math.min(minY, i / 28);
                maxY = Math.max(maxY, i / 28);
            }
        }
        double merilo = visina / (maxY - minY + 1);
        double sirina = (maxX - minX + 1) * merilo;
        for (int py = (int) y; py < y + visina; py++) {
            for (int px = (int) x; px < x + sirina; px++) {
                double mx = minX + (px - x) / merilo;
                double my = minY + (py - y) / merilo;
                int ix = (int) mx;
                int iy = (int) my;
                if (ix < 0 || iy < 0 || ix >= 27 || iy >= 27 || px < 0 || py < 0
                        || px >= slika.getWidth() || py >= slika.getHeight()) {
                    continue;
                }
                double fx = mx - ix;
                double fy = my - iy;
                double a = ((mnist[iy * 28 + ix] & 0xFF) * (1 - fx) + (mnist[iy * 28 + ix + 1] & 0xFF) * fx) * (1 - fy)
                        + ((mnist[(iy + 1) * 28 + ix] & 0xFF) * (1 - fx) + (mnist[(iy + 1) * 28 + ix + 1] & 0xFF) * fx) * fy;
                a /= 255;
                if (a <= 0.02) {
                    continue;
                }
                int p = slika.getRGB(px, py);
                int rr = (int) (((p >> 16) & 0xFF) * (1 - a) + 25 * a);
                int gg = (int) (((p >> 8) & 0xFF) * (1 - a) + 35 * a);
                int bb = (int) ((p & 0xFF) * (1 - a) + 110 * a);
                slika.setRGB(px, py, (rr << 16) | (gg << 8) | bb);
            }
        }
        return sirina;
    }

    /* Fotografija telefona: listek v platnu 2000 x 1500 na mizi, zasukan, s
       perspektivo, svetlobnim prelivom, sumom (po zelji zamegljen) in JPEG 85 %. */
    static byte[] fotografija(BufferedImage listek, Random r, Pogoji pogoji) throws IOException {
        return fotografija(listek, r, pogoji, 2000, 1500, 950, 300, OZNAKE_LISTKA);
    }

    static byte[] fotografija(BufferedImage listek, Random r, Pogoji pogoji, int W, int H,
                              double sirinaOd, double razpon) throws IOException {
        return fotografija(listek, r, pogoji, W, H, sirinaOd, razpon, null);
    }

    /* Platno W x H; sirina lista na sliki je sirinaOd + do razpon px (list A4
       vadnice je pokoncen: VadnicaTest). »oznake« (delez slike) so mesta, kamor
       lahko seze prst; null = brez prsta. */
    static byte[] fotografija(BufferedImage listek, Random r, Pogoji pogoji, int W, int H,
                              double sirinaOd, double razpon, double[][] oznake) throws IOException {
        double w = listek.getWidth();
        double h = listek.getHeight();
        // organizator posname cel listek: zasede vecino slike, a ne cez rob
        double sirina = sirinaOd + r.nextDouble() * razpon;
        double visina = sirina * h / w;
        double kot = Math.toRadians((r.nextDouble() * 2 - 1) * pogoji.kot());
        double cx = W / 2.0 + (r.nextDouble() - 0.5) * 150;
        double cy = H / 2.0 + (r.nextDouble() - 0.5) * 100;
        double[][] koti = {{-sirina / 2, -visina / 2}, {sirina / 2, -visina / 2}, {sirina / 2, visina / 2}, {-sirina / 2, visina / 2}};
        double[][] platno = new double[4][];
        for (int i = 0; i < 4; i++) {
            double px = koti[i][0] * (1 + (r.nextDouble() - 0.5) * pogoji.perspektiva());
            double py = koti[i][1] * (1 + (r.nextDouble() - 0.5) * pogoji.perspektiva());
            platno[i] = new double[] {cx + px * Math.cos(kot) - py * Math.sin(kot), cy + px * Math.sin(kot) + py * Math.cos(kot)};
        }
        Preslikava vListek = Preslikava.homografija(platno,
                new double[][] {{0, 0}, {w - 1, 0}, {w - 1, h - 1}, {0, h - 1}});
        double svetloba = 0.7 + r.nextDouble() * 0.2;
        Pregib pregib = pogoji.pregib() ? Pregib.nakljucen(r, w, h) : null;
        Prst prst = oznake != null && pogoji.prst() > 0 && r.nextDouble() < pogoji.prst()
                ? Prst.nakljucen(r, oznake[r.nextInt(oznake.length)], w, h) : null;
        int[] rgb = new int[W * H];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                double[] p = vListek.uporabi(x, y);
                double luc = svetloba + (1 - svetloba) * x / W;
                double sencaPregiba = 1;
                if (pregib != null) {
                    sencaPregiba = pregib.senca(p);
                    p = pregib.premakni(p);
                }
                boolean naListku = p[0] >= 0 && p[1] >= 0 && p[0] < w - 1 && p[1] < h - 1;
                int c = naListku ? listek.getRGB((int) p[0], (int) p[1]) : (120 << 16) | (96 << 8) | 70;
                if (naListku) {
                    luc *= sencaPregiba;
                }
                if (prst != null) {
                    c = prst.barva(p, c);
                }
                double sum = r.nextGaussian() * 5;
                rgb[y * W + x] = (omeji(((c >> 16) & 0xFF) * luc + sum) << 16)
                        | (omeji(((c >> 8) & 0xFF) * luc + sum) << 8) | omeji((c & 0xFF) * luc + sum);
            }
        }
        if (pogoji.zamegli()) {
            rgb = zamegli(rgb, W, H);
        }
        BufferedImage foto = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        foto.setRGB(0, 0, W, H, rgb, 0, W);
        ByteArrayOutputStream izhod = new ByteArrayOutputStream();
        Iterator<ImageWriter> pisci = ImageIO.getImageWritersByFormatName("jpeg");
        ImageWriter pisec = pisci.next();
        ImageWriteParam nastavitve = pisec.getDefaultWriteParam();
        nastavitve.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        // -Dsimulacija.kakovost=0.82: kakovost JPEG, kot jo zapise brskalnik (F5.3)
        nastavitve.setCompressionQuality(Float.parseFloat(System.getProperty("simulacija.kakovost", "0.85")));
        try (ImageOutputStream o = ImageIO.createImageOutputStream(izhod)) {
            pisec.setOutput(o);
            pisec.write(null, new IIOImage(foto, null, null), nastavitve);
        } finally {
            pisec.dispose();
        }
        return izhod.toByteArray();
    }

    /* Prepognjen in znova poravnan listek (koordinate slike listka): del za pregibom
       je na fotografiji nekoliko skrajsan (dvignjen proti telefonu) in zamaknjen
       vzdolz pregiba, z drugacno svetlobo; na pregibu je tanka senca. */
    private record Pregib(boolean vodoraven, double lega, double skrajsanje, double strig, double svetloba,
                          double sirinaSence) {
        static Pregib nakljucen(Random r, double w, double h) {
            boolean vodoraven = r.nextBoolean();
            double lega = (vodoraven ? h : w) * (0.35 + r.nextDouble() * 0.3);
            return new Pregib(vodoraven, lega, 0.01 + r.nextDouble() * 0.025, (r.nextDouble() - 0.5) * 0.03,
                    0.82 + r.nextDouble() * 0.13, w / GeometrijaListka.SIRINA * 0.4);
        }

        double senca(double[] p) {
            double u = (vodoraven ? p[1] : p[0]) - lega;
            double s = u > 0 ? svetloba : 1;
            return Math.abs(u) < sirinaSence ? s * 0.8 : s;
        }

        double[] premakni(double[] p) {
            double u = (vodoraven ? p[1] : p[0]) - lega;
            if (u <= 0) {
                return p;
            }
            double pravokotno = lega + u * (1 + skrajsanje);
            double vzdolz = (vodoraven ? p[0] : p[1]) + u * strig;
            return vodoraven ? new double[] {vzdolz, pravokotno} : new double[] {pravokotno, vzdolz};
        }
    }

    /* Prst, ki drzi listek (koordinate slike listka): konica (polmer 3,4-4,6 mm)
       pokrije oznako, prst se od nje siri do 7-9 mm in gre navzven cez rob
       listka; barva koze z zaobljenostjo in senca prsta na papirju. */
    private record Prst(double tx, double ty, double sx, double sy, double konica, double telo, double mm) {
        static Prst nakljucen(Random r, double[] oznaka, double w, double h) {
            double mm = w / GeometrijaListka.SIRINA;
            double mx = oznaka[0] * w;
            double my = oznaka[1] * h;
            double dx = mx - w / 2;
            double dy = my - h / 2;
            double d = Math.hypot(dx, dy);
            dx /= d;
            dy /= d;
            double kot = (r.nextDouble() - 0.5) * Math.toRadians(40);
            double sx = dx * Math.cos(kot) - dy * Math.sin(kot);
            double sy = dx * Math.sin(kot) + dy * Math.cos(kot);
            double zamik = (r.nextDouble() - 0.5) * mm;
            return new Prst(mx - sx * zamik, my - sy * zamik, sx, sy, (3.4 + r.nextDouble() * 1.2) * mm,
                    (7 + r.nextDouble() * 2) * mm, mm);
        }

        int barva(double[] p, int papir) {
            // s = razdalja vzdolz prsta od konice, polmer narasca do telesa v 18 mm
            double s = (p[0] - tx) * sx + (p[1] - ty) * sy;
            double precno = Math.abs(-(p[0] - tx) * sy + (p[1] - ty) * sx);
            double polmer = konica + (telo - konica) * Math.max(0, Math.min(1, s / (18 * mm)));
            double razdalja = s >= 0 ? precno : Math.hypot(precno, s);
            double senca = 1.5 * mm;
            if (razdalja <= polmer) {
                double f = 0.6 + 0.4 * Math.sqrt(1 - Math.pow(razdalja / polmer, 2));
                return (omeji(215 * f) << 16) | (omeji(165 * f) << 8) | omeji(140 * f);
            }
            if (razdalja <= polmer + senca) {
                double f = 0.75 + 0.25 * (razdalja - polmer) / senca;
                return (omeji(((papir >> 16) & 0xFF) * f) << 16) | (omeji(((papir >> 8) & 0xFF) * f) << 8)
                        | omeji((papir & 0xFF) * f);
            }
            return papir;
        }
    }

    /* Povprecje 3 x 3 (rahlo neostra fotografija). */
    private static int[] zamegli(int[] rgb, int W, int H) {
        int[] izhod = rgb.clone();
        for (int y = 1; y < H - 1; y++) {
            for (int x = 1; x < W - 1; x++) {
                int rr = 0;
                int gg = 0;
                int bb = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int c = rgb[(y + dy) * W + x + dx];
                        rr += (c >> 16) & 0xFF;
                        gg += (c >> 8) & 0xFF;
                        bb += c & 0xFF;
                    }
                }
                izhod[y * W + x] = ((rr / 9) << 16) | ((gg / 9) << 8) | (bb / 9);
            }
        }
        return izhod;
    }

    private static int omeji(double v) {
        return (int) Math.max(0, Math.min(255, Math.round(v)));
    }

    static BufferedImage kopija(BufferedImage izvor) {
        BufferedImage k = new BufferedImage(izvor.getWidth(), izvor.getHeight(), BufferedImage.TYPE_INT_RGB);
        k.getGraphics().drawImage(izvor, 0, 0, null);
        return k;
    }
}
