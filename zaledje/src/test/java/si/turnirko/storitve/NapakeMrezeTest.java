/* Napake mreze stevk (NapakeMreze, F6.6).

   Domenska pravila, ki jih ta test varuje:
   - pregled loci preizkusne stevke od ucnih enako kot ucenje (po listu vadnice ali
     listku): napake so samo preizkusne, sumljive oznake samo ucne;
   - za vsako stevko 0-9 pove, koliko preizkusnih prebere prav in s cim jo zamenja;
   - ucna stevka z napacno oznako, ki jo mreza odlocno bere drugace, je sumljiva;
   - izlocena stevka izgine iz ucenja, stevilo stevk lista vadnice se zmanjsa;
   - vse je samo za admina. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import si.turnirko.branje.MrezaStevk;
import si.turnirko.branje.UcenjeStevk;
import si.turnirko.dto.NapakeMrezeDto;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.izjeme.PrepovedanoIzjema;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.StevkaListka;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vadnica;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.StevkaListkaRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;
import si.turnirko.repozitoriji.VadnicaRepozitorij;

class NapakeMrezeTest extends IntegracijskiTest {

    /* id % 5 == 0: preizkusni list; ostali so ucni (UcenjeMreze.jePreizkusna). */
    private static final long PREIZKUSNI = 1_000_000_005L;
    private static final long UCNI = 1_000_000_001L;

    @Autowired NapakeMreze napake;
    @Autowired StevkaListkaRepozitorij stevke;
    @Autowired VadnicaRepozitorij vadnice;
    @Autowired VirMrezeStevk vir;
    @Autowired UporabnikRepozitorij uporabniki;

    @AfterEach
    void pospravi() {
        vir.nastavi(VirMrezeStevk.vgrajena().mreza(), "vgrajena");
        SecurityContextHolder.clearContext();
    }

    @Test
    void napakeSoPreizkusneSumljiveSoUcneInIzlocitevJihOdstrani() {
        prijava("admin-napake@test", Vloga.ADMIN);
        List<UcenjeStevk.Vzorec> mnist = mnist();
        MrezaStevk vgrajena = VirMrezeStevk.vgrajena().mreza();
        // jasna stevka (mreza jo bere odlocno), ki ni 7: z oznako 7 je napacno oznacena
        UcenjeStevk.Vzorec jasna = mnist.stream()
                .filter(v -> v.oznaka() != 7 && vgrajena.verjetnosti(UcenjeStevk.vhod(v.slika()), 1.0)[v.oznaka()] > 0.99f)
                .findFirst().orElseThrow();

        List<UcenjeStevk.Vzorec> preizkusne = new ArrayList<>(mnist.subList(0, 200));
        preizkusne.add(new UcenjeStevk.Vzorec(jasna.slika(), 7));
        List<UcenjeStevk.Vzorec> ucne = new ArrayList<>(mnist.subList(200, 300));
        ucne.add(new UcenjeStevk.Vzorec(jasna.slika(), 7));
        List<StevkaListka> naPreizkusnem = list(PREIZKUSNI, preizkusne);
        List<StevkaListka> naUcnem = list(UCNI, ucne);
        long narobeOznacenaPreizkusna = naPreizkusnem.getLast().getId();
        long narobeOznacenaUcna = naUcnem.getLast().getId();

        NapakeMrezeDto p = napake.pregled();
        assertNull(p.vUporabi(), "v uporabi je vgrajena");
        assertEquals(p.preizkusnih(), p.poStevkah().stream().mapToInt(NapakeMrezeDto.PoStevki::preizkusnih).sum());
        assertTrue(p.preizkusnih() >= preizkusne.size());
        // po viru: listki, vadnica, ostalo - skupaj vse preizkusne
        assertEquals(List.of("LISTEK", "VADNICA", "OSTALO"),
                p.poViru().stream().map(NapakeMrezeDto.PoViru::vir).toList());
        assertEquals(p.preizkusnih(), p.poViru().stream().mapToInt(NapakeMrezeDto.PoViru::preizkusnih).sum());
        assertTrue(p.ucnih() >= ucne.size());
        assertEquals(10, p.poStevkah().size());
        for (NapakeMrezeDto.PoStevki s : p.poStevkah()) {
            assertEquals(s.pravVgrajena(), s.pravVUporabi(), "ista mreza");
        }

        // napacno oznacena preizkusna: napisano 7, prebrano prava vrednost
        NapakeMrezeDto.Stevka napaka = p.napake().stream()
                .filter(s -> s.id() == narobeOznacenaPreizkusna).findFirst().orElseThrow();
        assertEquals(7, napaka.vrednost());
        assertEquals(jasna.oznaka(), napaka.prebrano());
        assertEquals(PREIZKUSNI, napaka.idVadnica());
        assertFalse(napaka.listek());
        assertEquals(784, java.util.Base64.getDecoder().decode(napaka.slika()).length);
        assertTrue(p.poStevkah().get(7).zamenjave().stream().anyMatch(z -> z.prebrano() == jasna.oznaka()),
                "med zamenjavami sedmice je prava vrednost");
        assertTrue(p.napake().stream().noneMatch(s -> s.id() == narobeOznacenaUcna), "ucne niso med napakami");

        // napacno oznacena ucna je sumljiva; preizkusne niso med sumljivimi
        assertTrue(p.sumljive().stream().anyMatch(s -> s.id() == narobeOznacenaUcna));
        assertTrue(p.sumljive().stream().noneMatch(s -> s.id() == narobeOznacenaPreizkusna));
        assertTrue(p.drugacnihUcnih() >= 1);

        int stevkPrej = vadnice.findById(UCNI).orElseThrow().getStevk();
        napake.izloci(narobeOznacenaUcna);
        assertFalse(stevke.existsById(narobeOznacenaUcna));
        assertEquals(stevkPrej - 1, vadnice.findById(UCNI).orElseThrow().getStevk());
        assertTrue(napake.pregled().sumljive().stream().noneMatch(s -> s.id() == narobeOznacenaUcna));
        assertThrows(NiNajdenoIzjema.class, () -> napake.izloci(narobeOznacenaUcna), "ze izlocena");
    }

    /* Z naucene mreze se primerja z vgrajeno; id razlicice je v pregledu. */
    @Test
    void naucenaMrezaSePrimerjaZVgrajeno() {
        prijava("admin-napake2@test", Vloga.ADMIN);
        list(PREIZKUSNI, mnist().subList(0, 50));
        vir.nastavi(VirMrezeStevk.vgrajena().mreza(), VirMrezeStevk.PREDPONA_UCENE + 12345);
        NapakeMrezeDto p = napake.pregled();
        assertEquals(12345L, p.vUporabi());
        // iste utezi: obe mrezi bereta enako
        for (NapakeMrezeDto.PoStevki s : p.poStevkah()) {
            assertEquals(s.pravVgrajena(), s.pravVUporabi());
        }
    }

    @Test
    void samoZaAdmina() {
        prijava("organizator-napake@test", Vloga.ORGANIZATOR);
        assertThrows(PrepovedanoIzjema.class, () -> napake.pregled());
        assertThrows(PrepovedanoIzjema.class, () -> napake.izloci(1L));
    }

    // ---------- Pomozno ----------

    /* Odobren list vadnice z danimi stevkami (okenca po vrsti). */
    private List<StevkaListka> list(long id, List<UcenjeStevk.Vzorec> vzorci) {
        LocalDateTime zdaj = LocalDateTime.now();
        Vadnica v = new Vadnica(id, 1);
        v.nastaviBranje(1, new byte[] {1}, vzorci.size(), 0, 0, zdaj);
        v.odobri(vzorci.size(), zdaj);
        vadnice.save(v);
        List<StevkaListka> nove = new ArrayList<>();
        for (int i = 0; i < vzorci.size(); i++) {
            nove.add(StevkaListka.zVadnice(id, i % 140, vzorci.get(i).slika(), vzorci.get(i).oznaka(), true, zdaj));
        }
        List<StevkaListka> shranjene = stevke.saveAll(nove);
        stevke.odobriVadnico(id, zdaj);
        return shranjene;
    }

    private static List<UcenjeStevk.Vzorec> mnist() {
        try (InputStream v = NapakeMrezeTest.class.getResourceAsStream("/branje/mnist-preizkus.bin.gz")) {
            return UcenjeStevk.beriIzbor(v);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void prijava(String prijavnoIme, Vloga vloga) {
        Uporabnik u = new Uporabnik(prijavnoIme, "{bcrypt}x", vloga);
        u.setStatus(StatusRacuna.POTRJEN);
        uporabniki.save(u);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(prijavnoIme,
                null, List.of(new SimpleGrantedAuthority("ROLE_" + vloga.name()))));
    }
}
