/* Katere stevke mreza bere narobe (F6.6, samo admin; stran Meritev branja).

   Ucenje zapise samo skupni delez prav na preizkusnih stevkah; admin pa mora videti,
   KATERE stevke mrezo motijo (evropska enica z nosom, sedmica s precko ...), da ve,
   kaj naj ljudje pisejo na vadnice, in da najde napacne oznake. Racuna se ob
   ogledu, nad istimi stevkami kot ucenje (najnovejsih UcenjeMreze.NAJVEC_STEVK
   odobrenih) in z isto razdelitvijo na ucne in preizkusne (UcenjeMreze.jePreizkusna):
   - preizkusne: mreza v uporabi in vgrajena, po stevkah, s seznamom napak;
   - ucne: mreza se je na njih ucila; ce katero se vedno odlocno (>= PRAG_SUMLJIVE)
     bere drugace, je oznaka pogosto napacna (napisana druga stevka) ali je okence
     narobe izrezano. Admin tako stevko izloci iz ucenja (izloci).

   Izlocanje velja tudi za preizkusne stevke, a samo za napacne oznake: grdo
   napisana prava stevka mora ostati, sicer je preizkus videti boljsi, kot je. */
package si.turnirko.storitve;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import si.turnirko.branje.MrezaStevk;
import si.turnirko.branje.UcenjeStevk;
import si.turnirko.dto.NapakeMrezeDto;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.modeli.StevkaListka;
import si.turnirko.repozitoriji.StevkaListkaRepozitorij;
import si.turnirko.repozitoriji.VadnicaRepozitorij;

@Service
public class NapakeMreze {

    private static final Logger log = LoggerFactory.getLogger(NapakeMreze.class);

    /* Narobe prebranih preizkusnih stevk na pregledu najvec toliko za vsako napisano
       stevko (po stevkah, da ena tezavna stevka ne izrine ostalih). */
    static final int NAJVEC_NA_STEVKO = 60;
    /* Sumljivih ucnih stevk najvec toliko, najbolj odlocne najprej. */
    static final int NAJVEC_SUMLJIVIH = 60;
    /* Ucna stevka je sumljiva, ce jo mreza po ucenju bere drugace s to verjetnostjo. */
    static final float PRAG_SUMLJIVE = 0.9f;
    /* Zamenjav ob vsaki stevki (najpogostejse). */
    static final int ZAMENJAV = 3;
    /* Razredi mreze: stevke 0-9. */
    private static final int STEVK = 10;
    /* Vir preizkusne stevke: potrjen listek, list vadnice, ostalo (branje izbrisano). */
    static final String[] VIRI = {"LISTEK", "VADNICA", "OSTALO"};

    private final StevkaListkaRepozitorij stevke;
    private final VadnicaRepozitorij vadnice;
    private final VirMrezeStevk vir;
    private final LastnistvoStoritev lastnistvo;
    private final TransactionTemplate transakcija;

    public NapakeMreze(StevkaListkaRepozitorij stevke, VadnicaRepozitorij vadnice, VirMrezeStevk vir,
                       LastnistvoStoritev lastnistvo, PlatformTransactionManager upraviteljTransakcij) {
        this.stevke = stevke;
        this.vadnice = vadnice;
        this.vir = vir;
        this.lastnistvo = lastnistvo;
        this.transakcija = new TransactionTemplate(upraviteljTransakcij);
    }

    private record Prebrana(StevkaListka stevka, int prebrano, float verjetnost) {}

    public NapakeMrezeDto pregled() {
        MeritevBranjaStoritev.preveriAdmina(lastnistvo.trenutni());
        // obe mrezi enkrat: ce se med ogledom zamenja (ucenje), velja ogled za staro
        VirMrezeStevk.Nalozena trenutna = vir.trenutna();
        if (trenutna == null) {
            throw new DomenskaIzjema("Mreža števk ni naložena.");
        }
        boolean ucena = trenutna.oznaka().startsWith(VirMrezeStevk.PREDPONA_UCENE);
        VirMrezeStevk.Nalozena vgrajenaZapis = ucena ? VirMrezeStevk.vgrajena() : null;
        MrezaStevk vgrajena = vgrajenaZapis == null ? trenutna.mreza() : vgrajenaZapis.mreza();
        Long idVUporabi = ucena ? vir.idTrenutne() : null;

        List<StevkaListka> vse = transakcija.execute(s ->
                stevke.najnovejse(PageRequest.of(0, UcenjeMreze.NAJVEC_STEVK)));
        int[][] zmedaVUporabi = new int[STEVK][STEVK];
        int[] pravVgrajena = new int[STEVK];
        List<Prebrana> napake = new ArrayList<>();
        List<Prebrana> sumljive = new ArrayList<>();
        int preizkusnih = 0;
        int ucnih = 0;
        int drugacnihUcnih = 0;
        // preizkusne po viru: listki in vadnica locena (diagnostika 24. 11. 2026) - vadnica
        // je skrbno pisana v okenca, listek med tekmo; skupni delez skrije razliko
        int[][] poViru = new int[VIRI.length][3];
        for (StevkaListka s : vse) {
            int v = s.getVrednost();
            if (v < 0 || v >= STEVK) {
                continue;
            }
            float[] vhod = UcenjeStevk.vhod(s.getSlika());
            float[] p = trenutna.mreza().verjetnosti(vhod, 1.0);
            int prebrano = najverjetnejsa(p);
            if (UcenjeMreze.jePreizkusna(s)) {
                preizkusnih++;
                zmedaVUporabi[v][prebrano]++;
                int poVgrajeni = vgrajena == trenutna.mreza() ? prebrano
                        : najverjetnejsa(vgrajena.verjetnosti(vhod, 1.0));
                if (poVgrajeni == v) {
                    pravVgrajena[v]++;
                }
                int[] vir = poViru[s.getIdBranje() != null ? 0 : s.getIdVadnica() != null ? 1 : 2];
                vir[0]++;
                if (prebrano == v) {
                    vir[1]++;
                }
                if (poVgrajeni == v) {
                    vir[2]++;
                }
                if (prebrano != v) {
                    napake.add(new Prebrana(s, prebrano, p[prebrano]));
                }
            } else {
                ucnih++;
                if (prebrano != v) {
                    drugacnihUcnih++;
                    if (p[prebrano] >= PRAG_SUMLJIVE) {
                        sumljive.add(new Prebrana(s, prebrano, p[prebrano]));
                    }
                }
            }
        }

        List<NapakeMrezeDto.PoStevki> poStevkah = new ArrayList<>();
        for (int v = 0; v < STEVK; v++) {
            int vseh = 0;
            List<NapakeMrezeDto.Zamenjava> zamenjave = new ArrayList<>();
            for (int o = 0; o < STEVK; o++) {
                vseh += zmedaVUporabi[v][o];
                if (o != v && zmedaVUporabi[v][o] > 0) {
                    zamenjave.add(new NapakeMrezeDto.Zamenjava(o, zmedaVUporabi[v][o]));
                }
            }
            zamenjave.sort(Comparator.comparingInt(NapakeMrezeDto.Zamenjava::krat).reversed()
                    .thenComparingInt(NapakeMrezeDto.Zamenjava::prebrano));
            poStevkah.add(new NapakeMrezeDto.PoStevki(v, vseh, pravVgrajena[v], zmedaVUporabi[v][v],
                    List.copyOf(zamenjave.subList(0, Math.min(ZAMENJAV, zamenjave.size())))));
        }

        // napake po napisani stevki, znotraj nje po zamenjavi, najbolj odlocne najprej
        napake.sort(Comparator.comparingInt((Prebrana n) -> n.stevka().getVrednost())
                .thenComparingInt(Prebrana::prebrano)
                .thenComparing(Comparator.comparingDouble(Prebrana::verjetnost).reversed()));
        int[] naStevko = new int[STEVK];
        List<NapakeMrezeDto.Stevka> napakeDto = new ArrayList<>();
        for (Prebrana n : napake) {
            if (naStevko[n.stevka().getVrednost()]++ < NAJVEC_NA_STEVKO) {
                napakeDto.add(dto(n));
            }
        }
        sumljive.sort(Comparator.comparingDouble(Prebrana::verjetnost).reversed());
        List<NapakeMrezeDto.Stevka> sumljiveDto = sumljive.stream().limit(NAJVEC_SUMLJIVIH)
                .map(NapakeMreze::dto).toList();

        List<NapakeMrezeDto.PoViru> viri = new ArrayList<>();
        for (int i = 0; i < VIRI.length; i++) {
            viri.add(new NapakeMrezeDto.PoViru(VIRI[i], poViru[i][0], poViru[i][1], poViru[i][2]));
        }
        return new NapakeMrezeDto(idVUporabi, preizkusnih, ucnih, poStevkah, napake.size(), NAJVEC_NA_STEVKO, napakeDto,
                drugacnihUcnih, PRAG_SUMLJIVE, sumljiveDto, viri);
    }

    /* Izloci stevko iz ucenja (napacna oznaka ali narobe izrezano okence). Ze naucena
       mreza ostane; stevke ni vec v naslednjem ucenju in v preizkusu. */
    public void izloci(Long id) {
        transakcija.executeWithoutResult(t -> {
            MeritevBranjaStoritev.preveriAdmina(lastnistvo.trenutni());
            StevkaListka s = stevke.findById(id)
                    .orElseThrow(() -> new NiNajdenoIzjema("Števke ni (morda je že izločena)."));
            stevke.delete(s);
            if (s.getIdVadnica() != null) {
                vadnice.odstejStevko(s.getIdVadnica());
            }
            log.info("Stevka {} (vrednost {}, {}) izlocena iz ucenja", id, s.getVrednost(),
                    s.getIdVadnica() != null ? "vadnica " + s.getIdVadnica() : "listek " + s.getIdBranje());
        });
    }

    private static int najverjetnejsa(float[] p) {
        int naj = 0;
        for (int o = 1; o < p.length; o++) {
            if (p[o] > p[naj]) {
                naj = o;
            }
        }
        return naj;
    }

    private static NapakeMrezeDto.Stevka dto(Prebrana n) {
        StevkaListka s = n.stevka();
        return new NapakeMrezeDto.Stevka(s.getId(), s.getVrednost(), n.prebrano(), n.verjetnost(),
                Base64.getEncoder().encodeToString(s.getSlika()), s.getIdVadnica(), s.getIdBranje() != null);
    }
}
