/* Mreza stevk, ki jo bralnik trenutno uporablja: zadnja sprejeta iz ucenja, ki je
   admin ni umaknil (mreza_stevk, »Vrni prejsnjo«, F6.4), brez nje vgrajena
   (resources/branje/stevke.bin, naucena na MNIST). Zamenja se v trenutku, ko ucenje
   sprejme novo razlicico ali admin vrne prejsnjo. */
package si.turnirko.storitve;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import si.turnirko.branje.MrezaStevk;
import si.turnirko.repozitoriji.MrezaStevkRepozitorij;

@Component
public class VirMrezeStevk {

    private static final Logger log = LoggerFactory.getLogger(VirMrezeStevk.class);
    static final String VGRAJENA = "/branje/stevke.bin";
    /* Oznaka naucene mreze je PREDPONA_UCENE + id zapisa v mreza_stevk. */
    public static final String PREDPONA_UCENE = "ucena-";

    public record Nalozena(MrezaStevk mreza, String oznaka) {}

    private final MrezaStevkRepozitorij repozitorij;
    private final AtomicReference<Nalozena> trenutna = new AtomicReference<>();

    public VirMrezeStevk(MrezaStevkRepozitorij repozitorij) {
        this.repozitorij = repozitorij;
    }

    @PostConstruct
    void nalozi() {
        try {
            var zapis = repozitorij.findFirstBySprejetaTrueAndUmaknjenaObIsNullOrderByIdDesc();
            if (zapis.isPresent()) {
                trenutna.set(new Nalozena(MrezaStevk.preberi(new ByteArrayInputStream(zapis.get().getUtezi())),
                        PREDPONA_UCENE + zapis.get().getId()));
                return;
            }
        } catch (IOException | RuntimeException e) {
            log.error("Naucene mreze stevk ni bilo mogoce naloziti; uporabim vgrajeno", e);
        }
        trenutna.set(vgrajena());
    }

    static Nalozena vgrajena() {
        try (InputStream vhod = VirMrezeStevk.class.getResourceAsStream(VGRAJENA)) {
            if (vhod == null) {
                return null;
            }
            return new Nalozena(MrezaStevk.preberi(vhod), "vgrajena");
        } catch (IOException e) {
            throw new IllegalStateException("Vgrajena mreza stevk ni berljiva.", e);
        }
    }

    public Nalozena trenutna() {
        return trenutna.get();
    }

    /* Id zapisa v mreza_stevk, ki ga bralnik uporablja; null za vgrajeno. */
    public Long idTrenutne() {
        Nalozena n = trenutna.get();
        if (n == null || !n.oznaka().startsWith(PREDPONA_UCENE)) {
            return null;
        }
        try {
            return Long.parseLong(n.oznaka().substring(PREDPONA_UCENE.length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public void nastavi(MrezaStevk mreza, String oznaka) {
        trenutna.set(new Nalozena(mreza, oznaka));
    }
}
