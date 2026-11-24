/* Meritev bralnika nad zlatim naborom (nacrt F1.5, samo admin): trenutni
   bralnik od celic naprej (razrez, mreza, pravila) prebere vsak listek nabora in
   ga primerja s potrjenim izidom - z istimi merili kot meritev branj
   (MeritevBranja), zato sta stevili primerljivi.

   Uporablja se pri vsaki spremembi bralnika (F2) in pri sprejemu nove mreze
   (F6, metoda izmeri). Admin jo sprozi na strani meritev: tece v ozadju v svoji
   niti (5000 listkov je nekaj minut racunanja), vedno najvec ena; branje
   listkov na turnirju ima svoj izvajalec in ga meritev ne zasede. */
package si.turnirko.storitve;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.ToIntFunction;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;

import si.turnirko.branje.CeliceListka;
import si.turnirko.branje.MrezaStevk;
import si.turnirko.branje.PogostostNizov;
import si.turnirko.branje.Temnost;
import si.turnirko.branje.UcenjeStevk;
import si.turnirko.dto.DiagnozaNaboraDto;
import si.turnirko.dto.MeritevBranjaDto;
import si.turnirko.dto.StanjeNaboraDto;
import si.turnirko.izjeme.DomenskaIzjema;

@Service
public class MeritevNabora {

    private static final Logger log = LoggerFactory.getLogger(MeritevNabora.class);

    /* Zadnja meritev (ali tekoca). Ob ponovnem zagonu aplikacije se izgubi -
       meritev je hitro ponoviti, zapis bi le podvajal stevila. */
    private record Zadnja(boolean tece, int obdelanih, int vseh, LocalDateTime zacetek, LocalDateTime konec,
                          String mreza, Podrobno rezultat, String napaka) {}

    /* Meritev nabora: vsi listki, locen preizkusni in ucni del (listek brez branja -
       izbrisano - ni v nobenem) in diagnostika (DiagnozaBranja; null, ce ni zahtevana). */
    record Podrobno(MeritevBranjaDto.Skupina vsi, MeritevBranjaDto.Skupina preizkusni,
                    MeritevBranjaDto.Skupina ucni, DiagnozaNaboraDto diagnoza) {}

    private final ZlatiNabor nabor;
    private final VirMrezeStevk vir;
    private final LastnistvoStoritev lastnistvo;
    private final PogostostNizovStoritev pogostost;
    private final AtomicBoolean tece = new AtomicBoolean(false);
    private final AtomicInteger obdelanih = new AtomicInteger();
    private final AtomicReference<Zadnja> zadnja = new AtomicReference<>();
    private final ExecutorService nit = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "meritev-nabora");
        t.setDaemon(true);
        // nizja prednost: branje listkov in odgovori strezniku imajo prednost
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    public MeritevNabora(ZlatiNabor nabor, VirMrezeStevk vir, LastnistvoStoritev lastnistvo,
                         PogostostNizovStoritev pogostost) {
        this.nabor = nabor;
        this.pogostost = pogostost;
        this.vir = vir;
        this.lastnistvo = lastnistvo;
    }

    @PreDestroy
    void ustavi() {
        nit.shutdownNow();
    }

    public StanjeNaboraDto stanje() {
        MeritevBranjaStoritev.preveriAdmina(lastnistvo.trenutni());
        Zadnja z = zadnja.get();
        long listkov = nabor.velikost();
        if (z == null) {
            return new StanjeNaboraDto(listkov, ZlatiNabor.NAJVEC, false, 0, 0, null, null, null, null, null,
                    null, null, null);
        }
        Podrobno r = z.rezultat();
        return new StanjeNaboraDto(listkov, ZlatiNabor.NAJVEC, z.tece(), z.tece() ? obdelanih.get() : z.obdelanih(),
                z.vseh(), z.zacetek(), z.konec(), z.mreza(), r == null ? null : r.vsi(), z.napaka(),
                r == null ? null : r.preizkusni(), r == null ? null : r.ucni(), r == null ? null : r.diagnoza());
    }

    /* Zazene meritev trenutne mreze v ozadju; ce ze tece, 409. */
    public StanjeNaboraDto zazeni() {
        MeritevBranjaStoritev.preveriAdmina(lastnistvo.trenutni());
        VirMrezeStevk.Nalozena nalozena = vir.trenutna();
        if (nalozena == null) {
            throw new DomenskaIzjema("Mreža števk ni naložena.");
        }
        // velikost pred zastavico: napaka baze tu ne sme pustiti »meritev ze tece«
        int vseh = (int) Math.min(Integer.MAX_VALUE, nabor.velikost());
        if (!tece.compareAndSet(false, true)) {
            throw new DomenskaIzjema("Meritev nabora že teče.");
        }
        Zadnja prejsnja = zadnja.get();
        LocalDateTime zacetek = LocalDateTime.now();
        obdelanih.set(0);
        zadnja.set(new Zadnja(true, 0, vseh, zacetek, null, nalozena.oznaka(), null, null));
        try {
            nit.execute(() -> {
                try {
                    Podrobno rezultat = izmeri(nalozena.mreza(), Integer.MAX_VALUE, () -> false,
                            n -> obdelanih.set(n), true);
                    zadnja.set(new Zadnja(false, obdelanih.get(), vseh, zacetek, LocalDateTime.now(),
                            nalozena.oznaka(), rezultat, null));
                    log.info("Meritev zlatega nabora ({} listkov, mreza {}) koncana", obdelanih.get(),
                            nalozena.oznaka());
                } catch (RuntimeException | Error e) {
                    // tudi Error (pomnilnik): stanje ne sme ostati »merim«
                    log.error("Meritev zlatega nabora ni uspela", e);
                    zadnja.set(new Zadnja(false, obdelanih.get(), vseh, zacetek, LocalDateTime.now(),
                            nalozena.oznaka(), null, "Meritev ni uspela. Podrobnosti so v dnevniku strežnika."));
                } finally {
                    tece.set(false);
                }
            });
        } catch (RejectedExecutionException e) {
            tece.set(false);
            zadnja.set(prejsnja);
            throw new DomenskaIzjema("Strežnik se ustavlja. Poskusi znova.");
        }
        return stanje();
    }

    /* Meritev dane mreze nad celotnim naborom (sinhrono). Listek, ki ga ni
       mogoce nalozi (poskodovan zapis), se preskoci in zapise v dnevnik. */
    public MeritevBranjaDto.Skupina izmeri(MrezaStevk mreza, IntConsumer napredek) {
        return izmeri(mreza, Integer.MAX_VALUE, () -> false, napredek);
    }

    /* Prvih najvec listkov nabora (sprejem mreze, F6.3). prekini se vprasa pred
       vsakim listkom; ob true meritev vrze UcenjeStevk.Prekinjeno. */
    public MeritevBranjaDto.Skupina izmeri(MrezaStevk mreza, int najvec, BooleanSupplier prekini,
                                           IntConsumer napredek) {
        return izmeri(mreza, najvec, prekini, napredek, false).vsi();
    }

    /* Meritev nabora; z diagnozo se za vsak listek z znanim branjem izracuna se
       DiagnozaBranja (vzroki napak, stevke, umerjenost, galerija preizkusnih). */
    Podrobno izmeri(MrezaStevk mreza, int najvec, BooleanSupplier prekini, IntConsumer napredek, boolean diagnoza) {
        MeritevBranja.Zbirnik vsi = new MeritevBranja.Zbirnik("Zlati nabor", null, null);
        MeritevBranja.Zbirnik preizkusni = new MeritevBranja.Zbirnik("Preizkusni listki", null, null);
        MeritevBranja.Zbirnik ucni = new MeritevBranja.Zbirnik("Učni listki", null, null);
        ToIntFunction<float[]> stevka = vhod -> najverjetnejsa(mreza.verjetnosti(vhod, 1.0));
        DiagnozaBranja.Zbirnik diagnozaPreizkusnih = new DiagnozaBranja.Zbirnik(stevka);
        DiagnozaBranja.Zbirnik diagnozaUcnih = new DiagnozaBranja.Zbirnik(stevka);
        int[] stevec = {0};
        nabor.zaVsak(najvec, z -> {
            // najprej prekini: pri sprejemu mreze je to tudi ustavitev streznika (UcenjeMreze)
            if (prekini.getAsBoolean()) {
                throw new UcenjeStevk.Prekinjeno();
            }
            if (Thread.currentThread().isInterrupted()) {
                throw new IllegalStateException("Meritev je bila prekinjena.");
            }
            try {
                ZlatiNabor.Nalozen n = ZlatiNabor.nalozi(z);
                LokalniBralnikListka.Izrez izrez = LokalniBralnikListka.izrezi(n.poravnan());
                LokalniBralnikListka.Porazdelitve p = LokalniBralnikListka.porazdelitve(izrez, mreza, true);
                // vgrajena pogostost, ne nocno presteta: meritvi dveh mrez na razlicne dni
                // morata biti primerljivi (sprejem mreze, F6); beta je nastavljena
                LokalniBralnikListka.Razlozen r = LokalniBralnikListka.beri(izrez, p, mreza,
                        PogostostNizov.VGRAJENA, pogostost.beta(), true);
                MeritevBranja.Vzorec v = vzorec(r.listek(), n);
                vsi.dodaj(v);
                Boolean preizkusen = z.getIdBranje() == null ? null : UcenjeMreze.jePreizkusenListek(z.getIdBranje());
                if (preizkusen != null) {
                    (preizkusen ? preizkusni : ucni).dodaj(v);
                    if (diagnoza) {
                        List<DiagnozaBranja.Celica> celice = DiagnozaBranja.celice(izrez, p, r, n.resnica());
                        if (preizkusen) {
                            diagnozaPreizkusnih.dodaj(celice, (niz, stran) -> izrezCelice(n.poravnan(), niz, stran));
                        } else {
                            diagnozaUcnih.dodaj(celice, null);
                        }
                    }
                }
            } catch (RuntimeException e) {
                log.warn("Zlati listek {} ni berljiv in je izpuscen iz meritve", z.getId(), e);
            }
            napredek.accept(++stevec[0]);
        });
        return new Podrobno(vsi.dto(), preizkusni.dto(), ucni.dto(),
                diagnoza ? new DiagnozaNaboraDto(diagnozaPreizkusnih.dto(), diagnozaUcnih.dto()) : null);
    }

    /* Listek nabora kot vzorec meritve. Popravek je vsaka napacna celica;
       pripravljen je predlog brez opozoril in negotovih celic. */
    static MeritevBranja.Vzorec vzorec(PrebranListek prebrano, ZlatiNabor.Nalozen n) {
        MeritevBranja.Celice celice = MeritevBranja.primerjaj(prebrano, n.resnica());
        boolean pripravljen = PredlogIzidaListka.sestavi(prebrano, n.zapis().getSteviloNizov(), false).pripravljen();
        return new MeritevBranja.Vzorec(true, false, true, celice.narobe() > 0, pripravljen, false, null, null,
                celice);
    }

    private static int najverjetnejsa(float[] p) {
        int m = 0;
        for (int i = 1; i < p.length; i++) {
            if (p[i] > p[m]) {
                m = i;
            }
        }
        return m;
    }

    /* Izrez celice poravnanega zlatega listka za galerijo napak (PNG v sivinah, base64);
       null, ce zapis ne uspe - galerija je pomoc, ne pogoj meritve. */
    static String izrezCelice(LokalniBralnikListka.Poravnan poravnan, int niz, int stran) {
        CeliceListka.Vrstica vrstica = poravnan.tabela().nizi().get(niz);
        CeliceListka.Celica c = stran == 0 ? vrstica.levo() : vrstica.desno();
        Temnost t = poravnan.slika();
        int x0 = Math.max(0, c.x0());
        int y0 = Math.max(0, c.y0());
        int x1 = Math.min(t.sirina - 1, c.x1());
        int y1 = Math.min(t.visina - 1, c.y1());
        if (x1 < x0 || y1 < y0) {
            return null;
        }
        BufferedImage slika = new BufferedImage(x1 - x0 + 1, y1 - y0 + 1, BufferedImage.TYPE_BYTE_GRAY);
        byte[] piksli = ((DataBufferByte) slika.getRaster().getDataBuffer()).getData();
        int w = x1 - x0 + 1;
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                float v = Math.max(0f, Math.min(1f, t.t[y * t.sirina + x]));
                piksli[(y - y0) * w + (x - x0)] = (byte) (255 - Math.round(255 * v));
            }
        }
        ByteArrayOutputStream izhod = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(slika, "png", izhod)) {
                return null;
            }
        } catch (IOException e) {
            return null;
        }
        return Base64.getEncoder().encodeToString(izhod.toByteArray());
    }
}
