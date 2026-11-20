/* Meritev bralnika nad zlatim naborom (nacrt F1.5, samo admin): trenutni
   bralnik od celic naprej (razrez, mreza, pravila) prebere vsak listek nabora in
   ga primerja s potrjenim izidom - z istimi merili kot meritev branj
   (MeritevBranja), zato sta stevili primerljivi.

   Uporablja se pri vsaki spremembi bralnika (F2) in pri sprejemu nove mreze
   (F6, metoda izmeri). Admin jo sprozi na strani meritev: tece v ozadju v svoji
   niti (5000 listkov je nekaj minut racunanja), vedno najvec ena; branje
   listkov na turnirju ima svoj izvajalec in ga meritev ne zasede. */
package si.turnirko.storitve;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;

import si.turnirko.branje.MrezaStevk;
import si.turnirko.dto.MeritevBranjaDto;
import si.turnirko.dto.StanjeNaboraDto;
import si.turnirko.izjeme.DomenskaIzjema;

@Service
public class MeritevNabora {

    private static final Logger log = LoggerFactory.getLogger(MeritevNabora.class);

    /* Zadnja meritev (ali tekoca). Ob ponovnem zagonu aplikacije se izgubi -
       meritev je hitro ponoviti, zapis bi le podvajal stevila. */
    private record Zadnja(boolean tece, int obdelanih, int vseh, LocalDateTime zacetek, LocalDateTime konec,
                          String mreza, MeritevBranjaDto.Skupina rezultat, String napaka) {}

    private final ZlatiNabor nabor;
    private final VirMrezeStevk vir;
    private final LastnistvoStoritev lastnistvo;
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

    public MeritevNabora(ZlatiNabor nabor, VirMrezeStevk vir, LastnistvoStoritev lastnistvo) {
        this.nabor = nabor;
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
            return new StanjeNaboraDto(listkov, ZlatiNabor.NAJVEC, false, 0, 0, null, null, null, null, null);
        }
        return new StanjeNaboraDto(listkov, ZlatiNabor.NAJVEC, z.tece(), z.tece() ? obdelanih.get() : z.obdelanih(),
                z.vseh(), z.zacetek(), z.konec(), z.mreza(), z.rezultat(), z.napaka());
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
                    MeritevBranjaDto.Skupina rezultat = izmeri(nalozena.mreza(), n -> obdelanih.set(n));
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
        MeritevBranja.Zbirnik zbirnik = new MeritevBranja.Zbirnik("Zlati nabor", null, null);
        int[] stevec = {0};
        nabor.zaVsak(z -> {
            if (Thread.currentThread().isInterrupted()) {
                throw new IllegalStateException("Meritev je bila prekinjena.");
            }
            try {
                zbirnik.dodaj(vzorec(ZlatiNabor.nalozi(z), mreza));
            } catch (RuntimeException e) {
                log.warn("Zlati listek {} ni berljiv in je izpuscen iz meritve", z.getId(), e);
            }
            napredek.accept(++stevec[0]);
        });
        return zbirnik.dto();
    }

    /* Listek nabora kot vzorec meritve. Popravek je vsaka napacna celica;
       pripravljen je predlog brez opozoril in negotovih celic. */
    static MeritevBranja.Vzorec vzorec(ZlatiNabor.Nalozen n, MrezaStevk mreza) {
        PrebranListek prebrano = LokalniBralnikListka.beri(LokalniBralnikListka.izrezi(n.poravnan()), mreza);
        MeritevBranja.Celice celice = MeritevBranja.primerjaj(prebrano, n.resnica());
        boolean pripravljen = PredlogIzidaListka.sestavi(prebrano, n.zapis().getSteviloNizov(), false).pripravljen();
        return new MeritevBranja.Vzorec(true, false, true, celice.narobe() > 0, pripravljen, false, null, null,
                celice);
    }
}
