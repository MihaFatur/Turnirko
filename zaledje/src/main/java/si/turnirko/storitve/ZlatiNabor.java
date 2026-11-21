/* Zlati nabor (nacrt F1.4): potrjeni listki, na katerih se meri bralnik.

   Po potrditvi (v izvajalcu ucenja, UcenjeIzListkov) se shrani poravnan listek
   (GeometrijaListka.PX_NA_MM px/mm, sivine, PNG), na katerem so ohranjeni SAMO
   piksli znotraj celic s tockami; imena, glava, QR in noga so beli. Zraven so
   lega celic, potrjen izid v stolpcih listka, stevilo nizov in mreza, ki je
   brala. Imen ne vsebuje, zato se hrani brez roka.

   Papir v celici se shrani kot cisto belo: od temnosti se odsteje raven papirja,
   ki jo vzame razrez (RazrezCelice.papir), in kar je pod SUM_PAPIRJA, je belo.
   Shrani se samo notranjost celice, ki jo bere razrez (brez RazrezCelice.ODMIK
   ob crti): rocno napisano ime, ki sega cez crto v celico, tako ne pride v nabor.
   Razrez nad naborom vidi isto crnilo kot nad fotografijo (prag crnila je vedno
   vsaj 0,12 nad papirjem; razlika je le v zaokrozitvi na 1/255 tik ob pragu), slika pa ima ~15 kB (cel poravnan
   listek v sivinah ~700 kB; izmerjeno na simuliranih fotografijah), nabor
   5000 listkov torej ~75 MB. Kar bralnik dela PRED celicami (poravnava, iskanje crt),
   se na naboru ne da meriti - za to ostanejo simulirane fotografije.

   Vzorec: vsak listek dobi nakljucen kljuc, ostane NAJVEC listkov z najmanjsimi
   kljuci. To je enakomeren nakljucen vzorec vseh potrjenih listkov (enako kot
   »reservoir sampling«), brez stevca videnih listkov. */
package si.turnirko.storitve;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import si.turnirko.branje.CeliceListka;
import si.turnirko.branje.RazrezCelice;
import si.turnirko.branje.Temnost;
import si.turnirko.modeli.ZlatiListek;
import si.turnirko.repozitoriji.ZlatiListekRepozitorij;

@Service
public class ZlatiNabor {

    /* Najvec listkov v naboru (~15 kB vsak). */
    static final int NAJVEC = 5000;
    /* Temnost nad papirjem, pod katero je piksel papir. Manj kot najnizji prag
       crnila v razrezu (0,12), zato se crnilo ne spremeni. */
    static final float SUM_PAPIRJA = 0.06f;
    /* Vec vrstic nizov ni na nobenem listku (najvec 7 nizov): sicer je iskanje
       celic zgresilo in med »celicami« bi lahko bilo kaj drugega. */
    static final int NAJVEC_VRSTIC = 9;
    /* Listkov na stran pri meritvi in izvozu (pomnilnik). */
    static final int STRAN = 50;

    /* Lega celic v px slike: vsaka vrstica je {x0, y0, x1, y1} leve in desne celice. */
    public record Celice(List<int[]> nizi, int[] rezultat) {}

    /* Listek nabora, pripravljen za bralnik. */
    public record Nalozen(ZlatiListek zapis, LokalniBralnikListka.Poravnan poravnan, MeritevBranja.Resnica resnica) {}

    private final ZlatiListekRepozitorij repozitorij;
    private final TransactionTemplate transakcija;

    public ZlatiNabor(ZlatiListekRepozitorij repozitorij, PlatformTransactionManager upraviteljTransakcij) {
        this.repozitorij = repozitorij;
        this.transakcija = new TransactionTemplate(upraviteljTransakcij);
    }

    public long velikost() {
        return repozitorij.count();
    }

    /* Zapise potrjen listek, ce gre v vzorec. resnica je v stolpcih listka.
       Listek brez tock po nizih (samo izid) ali z vec nizi, kot je najdenih
       vrstic, ne pove nic o branju celic in se preskoci. Vrne, ali je zapisan. */
    public boolean zapisi(Long idBranja, LokalniBralnikListka.Poravnan poravnan, MeritevBranja.Resnica resnica,
                          int steviloNizov, String model) {
        if (resnica == null || resnica.nizi().isEmpty()
                || resnica.nizi().size() > poravnan.tabela().nizi().size()
                || poravnan.tabela().nizi().size() > NAJVEC_VRSTIC
                || steviloNizov < 1 || steviloNizov > 9) {
            return false;
        }
        double kljuc = ThreadLocalRandom.current().nextDouble();
        Boolean vVzorcu = transakcija.execute(s -> !repozitorij.existsByIdBranje(idBranja)
                && repozitorij.countByKljucLessThan(kljuc) < NAJVEC);
        if (!Boolean.TRUE.equals(vVzorcu)) {
            return false;
        }
        byte[] png = slikaCelic(poravnan);
        String celice = JsonBranja.zapisi(celice(poravnan.tabela()));
        String zapisResnice = JsonBranja.zapisi(resnica);
        transakcija.executeWithoutResult(s -> {
            repozitorij.save(new ZlatiListek(idBranja, png, celice, zapisResnice, steviloNizov, model, kljuc,
                    LocalDateTime.now()));
            if (repozitorij.count() > NAJVEC) {
                repozitorij.obdrziNajmanjse(NAJVEC);
            }
        });
        return true;
    }

    /* Poravnan listek v sivinah, ohranjene samo celice (papir v njih belo). */
    static byte[] slikaCelic(LokalniBralnikListka.Poravnan poravnan) {
        return slikaCelic(poravnan.slika(), vse(poravnan.tabela()));
    }

    /* Isto za poljubne celice (tudi okenca lista vadnice, VadnicaStoritev). */
    static byte[] slikaCelic(Temnost t, List<CeliceListka.Celica> celice) {
        BufferedImage slika = new BufferedImage(t.sirina, t.visina, BufferedImage.TYPE_BYTE_GRAY);
        byte[] piksli = ((DataBufferByte) slika.getRaster().getDataBuffer()).getData();
        Arrays.fill(piksli, (byte) 255);
        for (CeliceListka.Celica c : celice) {
            float papir = RazrezCelice.papir(t, c);
            if (Float.isNaN(papir)) {
                continue;     // premajhno za razrez: tudi bralnik jo vidi prazno
            }
            int o = RazrezCelice.ODMIK;
            for (int y = Math.max(0, c.y0() + o); y <= Math.min(t.visina - 1, c.y1() - o); y++) {
                for (int x = Math.max(0, c.x0() + o); x <= Math.min(t.sirina - 1, c.x1() - o); x++) {
                    float v = t.vrni(x, y) - papir;
                    if (v >= SUM_PAPIRJA) {
                        piksli[y * t.sirina + x] = (byte) (255 - Math.round(255 * Math.min(1f, v)));
                    }
                }
            }
        }
        ByteArrayOutputStream izhod = new ByteArrayOutputStream();
        try {
            ImageIO.write(slika, "png", izhod);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return izhod.toByteArray();
    }

    /* Sivine PNG nazaj v temnost. Vzorci rastra neposredno: getRGB bi sivino
       pretvoril v sRGB (gama) in temnost ne bi bila enaka shranjeni. */
    static Temnost temnost(byte[] png) {
        BufferedImage slika;
        try {
            slika = ImageIO.read(new ByteArrayInputStream(png));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (slika == null || slika.getRaster().getNumBands() != 1) {
            throw new IllegalStateException("Slika zlatega listka ni v sivinah.");
        }
        Raster raster = slika.getRaster();
        Temnost t = new Temnost(slika.getWidth(), slika.getHeight());
        for (int y = 0; y < t.visina; y++) {
            for (int x = 0; x < t.sirina; x++) {
                t.t[y * t.sirina + x] = 1f - raster.getSample(x, y, 0) / 255f;
            }
        }
        return t;
    }

    static Celice celice(CeliceListka.Tabela tabela) {
        List<int[]> nizi = new ArrayList<>();
        for (CeliceListka.Vrstica v : tabela.nizi()) {
            nizi.add(vrstica(v));
        }
        return new Celice(nizi, vrstica(tabela.rezultat()));
    }

    static CeliceListka.Tabela tabela(Celice celice) {
        List<CeliceListka.Vrstica> nizi = new ArrayList<>();
        for (int[] v : celice.nizi()) {
            nizi.add(vrstica(v));
        }
        return new CeliceListka.Tabela(nizi, vrstica(celice.rezultat()));
    }

    private static int[] vrstica(CeliceListka.Vrstica v) {
        return new int[] {v.levo().x0(), v.levo().y0(), v.levo().x1(), v.levo().y1(),
                v.desno().x0(), v.desno().y0(), v.desno().x1(), v.desno().y1()};
    }

    private static CeliceListka.Vrstica vrstica(int[] v) {
        if (v == null || v.length != 8) {
            throw new IllegalStateException("Lega celic zlatega listka ni veljavna.");
        }
        return new CeliceListka.Vrstica(new CeliceListka.Celica(v[0], v[1], v[2], v[3]),
                new CeliceListka.Celica(v[4], v[5], v[6], v[7]));
    }

    private static List<CeliceListka.Celica> vse(CeliceListka.Tabela tabela) {
        List<CeliceListka.Celica> vse = new ArrayList<>();
        for (CeliceListka.Vrstica v : tabela.nizi()) {
            vse.add(v.levo());
            vse.add(v.desno());
        }
        vse.add(tabela.rezultat().levo());
        vse.add(tabela.rezultat().desno());
        return vse;
    }

    public static Nalozen nalozi(ZlatiListek z) {
        return new Nalozen(z,
                new LokalniBralnikListka.Poravnan(temnost(z.getSlika()),
                        tabela(JsonBranja.preberi(z.getCelice(), Celice.class))),
                JsonBranja.preberi(z.getResnica(), MeritevBranja.Resnica.class));
    }

    /* Vsak listek nabora po vrsti, po straneh (v pomnilniku je ena stran). */
    public void zaVsak(Consumer<ZlatiListek> delo) {
        zaVsak(Integer.MAX_VALUE, delo);
    }

    /* Prvih najvec listkov po id. Ker je nabor nakljucen vzorec, je tudi to
       nakljucen vzorec - in pri sprejemu mreze (F6.3) vedno isti, dokler se nabor
       ne spremeni, zato sta meritvi stare in nove mreze primerljivi. */
    public void zaVsak(int najvec, Consumer<ZlatiListek> delo) {
        long zadnji = 0;
        int obdelanih = 0;
        while (obdelanih < najvec) {
            long od = zadnji;
            int velikost = Math.min(STRAN, najvec - obdelanih);
            List<ZlatiListek> stran = transakcija.execute(s ->
                    repozitorij.findByIdGreaterThanOrderByIdAsc(od, PageRequest.of(0, velikost)));
            if (stran == null || stran.isEmpty()) {
                return;
            }
            for (ZlatiListek z : stran) {
                delo.accept(z);
                zadnji = z.getId();
                obdelanih++;
            }
        }
    }

    /* Popravek izida tekme (F6.1): listek ostane v naboru z novo resnico (v
       stolpcih listka), ali se izbrise, ce resnica == null (popravek je listek
       postavil pod vprasaj, npr. potrjen je bil k napacni tekmi). Vrne, ali je bil
       listek v naboru. */
    public boolean popravi(Long idBranja, MeritevBranja.Resnica resnica) {
        return Boolean.TRUE.equals(transakcija.execute(s -> repozitorij.findByIdBranje(idBranja).map(z -> {
            if (resnica == null || resnica.nizi().isEmpty()) {
                repozitorij.delete(z);
            } else {
                z.setResnica(JsonBranja.zapisi(resnica));
            }
            return true;
        }).orElse(false)));
    }

    /* Izvoz za lokalne meritve: zip s PNG in JSON vsakega listka. Brez id branja,
       ki bi listek povezal s tekmo in igralci. */
    public void izvozi(OutputStream izhod) throws IOException {
        int[] listkov = {0};
        try (ZipOutputStream zip = new ZipOutputStream(izhod, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("opis.txt"));
            zip.write(OPIS.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            try {
                zaVsak(z -> {
                    try {
                        zip.putNextEntry(new ZipEntry("listki/" + z.getId() + ".png"));
                        zip.write(z.getSlika());
                        zip.closeEntry();
                        Map<String, Object> opis = new LinkedHashMap<>();
                        opis.put("id", z.getId());
                        opis.put("steviloNizov", z.getSteviloNizov());
                        opis.put("model", z.getModel());
                        opis.put("ustvarjeno", z.getUstvarjenoOb().toLocalDate().toString());
                        opis.put("celice", JsonBranja.preberi(z.getCelice(), Celice.class));
                        opis.put("resnica", JsonBranja.preberi(z.getResnica(), MeritevBranja.Resnica.class));
                        zip.putNextEntry(new ZipEntry("listki/" + z.getId() + ".json"));
                        zip.write(JsonBranja.zapisi(opis).getBytes(StandardCharsets.UTF_8));
                        zip.closeEntry();
                        listkov[0]++;
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            } catch (UncheckedIOException e) {
                throw e.getCause();
            }
            // zadnja datoteka: zip brez nje je bil prekinjen sredi prenosa (odgovor 200
            // se je ze zacel, napake ni mogoce vec sporociti drugace)
            zip.putNextEntry(new ZipEntry("konec.txt"));
            zip.write(("Listkov: " + listkov[0] + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }

    private static final String OPIS = """
            Zlati nabor branja listkov Turnirko

            Vsak listek ima dve datoteki:
            - <id>.png: poravnan listek, 10 px na mm, sivine. Ohranjene so samo
              celice s tockami; papir v celici je belo (od temnosti je odsteta
              raven papirja, ki jo vzame razrez). Vse ostalo je belo.
            - <id>.json: steviloNizov, model (mreza, ki je listek brala),
              celice (lega v px: vsaka vrstica je x0, y0, x1, y1 leve in desne
              celice; zadnja je Rezultat) in resnica (potrjene tocke v stolpcih
              listka: nizi [levo, desno], rezultatLevo, rezultatDesno).
            Imen in podatkov o tekmi ni. Zadnja datoteka je konec.txt s stevilom
            listkov; ce je ni, prenos ni bil celoten.
            """;
}
