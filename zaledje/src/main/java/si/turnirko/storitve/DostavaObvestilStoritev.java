/* Dostava mnozicnih obvestil (po kolu, po turnirju) z evidenco po prejemnikih
   (V66, najdba E2 pregleda kakovosti, nov 2026).

   Prej: opravilo je kolo zapisalo kot obdelano v isti transakciji, v kateri je
   posta sele cakala na potrditev; vsako sporocilo je slo posebej v vrsto
   postaIzvajalec (200 mest, zavrnitev z izjemo). Neuspela dostava ali polna
   vrsta je pomenila, da prejemnik kola ne dobi nikoli, vmes pa so izpadle tudi
   potrditvene kode.

   Zdaj:
   - opravilo (ObvestilaPoKoluStoritev, ObvestilaPoTurnirjuStoritev) pripravi
     sporocila samo za prejemnike, ki se cakajo (cakajo), in jih odda (oddaj);
   - dostava tece PO potrditvi transakcije opravila na svojem izvajalcu
     (obvestilaIzvajalec, ena nit), sporocilo za sporocilom, sinhrono
     (PosiljateljPoste.dostavi);
   - vsak prejemnik se zapise v dostava_obvestila SELE po odgovoru streznika:
     poslano ali zavrnjeno (poskus). Izpad streznika se ne steje nikomur in po
     NAJVEC_ZAPOREDNIH_IZPADOV dostavo prekine;
   - opravilo kolo/turnir zapre, ko nihce vec ne caka: vsi so dobili sporocilo
     ali izcrpali NAJVEC_POSKUSOV. Do takrat ga vsako uro poskusi znova, a samo
     za tiste, ki cakajo - kdor je sporocilo dobil, ga ne dobi se enkrat.

   Meja zagotovila: zapis sledi posiljanju. Ce se aplikacija ustavi med
   odgovorom streznika in zapisom, dobi TA prejemnik sporocilo naslednjo uro se
   enkrat. Obratni vrstni red bi isto okno spremenil v izgubljeno sporocilo.

   vTeku je v pomnilniku, tako kot vrsta izvajalca: po ponovnem zagonu sta oba
   prazna in opravilo nedokoncano dostavo odda znova (za tiste brez zapisa).

   S kanalom, ki posiljanja ne odlaga (testni pomnilnik), tece dostava takoj in
   v transakciji klicalca - test jo vidi brez cakanja. */
package si.turnirko.storitve;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import si.turnirko.modeli.DostavaObvestila;
import si.turnirko.posta.IzidDostave;
import si.turnirko.posta.PosiljateljPoste;
import si.turnirko.posta.SporocilaPoste;
import si.turnirko.repozitoriji.DostavaObvestilaRepozitorij;

@Service
public class DostavaObvestilStoritev {

    private static final Logger dnevnik = LoggerFactory.getLogger(DostavaObvestilStoritev.class);

    /* Zavrnjeni poskusi enega prejemnika, preden obupamo (opravilo tece vsako
       uro, torej stiri ure). Trajno napacen naslov sicer ne bi nikoli pustil
       zapreti kola. */
    public static final int NAJVEC_POSKUSOV = 4;

    /* Toliko zaporednih izpadov kanala prekine dostavo: streznik poste ne dela
       in vsak nadaljnji poskus bi samo cakal na iztek povezave. */
    static final int NAJVEC_ZAPOREDNIH_IZPADOV = 3;

    /* Kaj se dostavlja: kolo lige ali turnir. */
    public record Kljuc(Long idLiga, Integer kolo, Long idTurnir) {

        public static Kljuc kolo(Long idLiga, int kolo) {
            return new Kljuc(idLiga, kolo, null);
        }

        public static Kljuc turnir(Long idTurnir) {
            return new Kljuc(null, null, idTurnir);
        }

        @Override
        public String toString() {
            return idTurnir != null ? "turnir " + idTurnir : "liga " + idLiga + ", kolo " + kolo;
        }
    }

    /* Eno pripravljeno sporocilo za en racun. */
    public record Posiljka(Long idRacun, String naslov, SporocilaPoste.Besedilo besedilo) {}

    private final PosiljateljPoste posiljatelj;
    private final DostavaObvestilaRepozitorij repozitorij;
    private final Executor izvajalec;
    private final TransactionTemplate transakcija;

    private final Set<Kljuc> vTeku = ConcurrentHashMap.newKeySet();

    public DostavaObvestilStoritev(PosiljateljPoste posiljatelj,
                                   DostavaObvestilaRepozitorij repozitorij,
                                   @Qualifier("obvestilaIzvajalec") Executor izvajalec,
                                   PlatformTransactionManager upravljalecTransakcij) {
        this.posiljatelj = posiljatelj;
        this.repozitorij = repozitorij;
        this.izvajalec = izvajalec;
        this.transakcija = new TransactionTemplate(upravljalecTransakcij);
    }

    /* Dostava tega kola/turnirja je oddana in se ni koncana: opravilo ga ta
       cas pusti pri miru (ne pripravlja znova in ne zapira). */
    public boolean vTeku(Kljuc kljuc) {
        return vTeku.contains(kljuc);
    }

    /* Kateri od prejemnikov sporocila se cakajo: brez zapisa ali z zavrnjenimi
       poskusi pod mejo. */
    public Set<Long> cakajo(Kljuc kljuc, Set<Long> prejemniki) {
        Set<Long> cakajo = new HashSet<>(prejemniki);
        for (DostavaObvestila d : zapisi(kljuc)) {
            if (d.jePoslano() || d.getPoskusov() >= NAJVEC_POSKUSOV) {
                cakajo.remove(d.getIdRacun());
            }
        }
        return cakajo;
    }

    /* Stevilo prejemnikov, ki jim je bilo sporocilo poslano. */
    public int poslanih(Kljuc kljuc) {
        return (int) zapisi(kljuc).stream().filter(DostavaObvestila::jePoslano).count();
    }

    private List<DostavaObvestila> zapisi(Kljuc kljuc) {
        return kljuc.idTurnir() != null
                ? repozitorij.findByIdTurnir(kljuc.idTurnir())
                : repozitorij.findByIdLigaAndKolo(kljuc.idLiga(), kljuc.kolo());
    }

    /* Odda sporocila v dostavo; vrne stevilo oddanih. Dostava se zacne po
       potrditvi transakcije klicalca (zeton odjave v sporocilu mora biti takrat
       ze zapisan); ce se ta razveljavi, se ne poslje nic. */
    public int oddaj(Kljuc kljuc, List<Posiljka> posiljke) {
        if (posiljke.isEmpty() || !vTeku.add(kljuc)) {
            return 0;
        }
        List<Posiljka> kopija = List.copyOf(posiljke);
        if (!posiljatelj.odloziDoPotrditve()) {
            dostaviVse(kljuc, kopija);
        } else if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int stanje) {
                    if (stanje == STATUS_COMMITTED) {
                        vIzvajalec(kljuc, kopija);
                    } else {
                        vTeku.remove(kljuc);
                    }
                }
            });
        } else {
            vIzvajalec(kljuc, kopija);
        }
        return kopija.size();
    }

    private void vIzvajalec(Kljuc kljuc, List<Posiljka> posiljke) {
        try {
            izvajalec.execute(() -> dostaviVse(kljuc, posiljke));
        } catch (RuntimeException napaka) {
            // izvajalec se ustavlja (zaustavitev aplikacije): opravilo po zagonu odda znova
            vTeku.remove(kljuc);
            dnevnik.warn("Dostave obvestil ({}) ni bilo mogoce oddati: {}", kljuc,
                    napaka.getClass().getSimpleName());
        }
    }

    private void dostaviVse(Kljuc kljuc, List<Posiljka> posiljke) {
        int poslanih = 0;
        int zavrnjenih = 0;
        int izpadov = 0;
        boolean prekinjeno = false;
        try {
            for (Posiljka p : posiljke) {
                IzidDostave izid = dostavi(p);
                if (izid == IzidDostave.KANAL_NE_DELA) {
                    if (++izpadov >= NAJVEC_ZAPOREDNIH_IZPADOV) {
                        prekinjeno = true;
                        break;
                    }
                    continue;
                }
                izpadov = 0;
                zabelezi(kljuc, p.idRacun(), izid == IzidDostave.POSLANO);
                if (izid == IzidDostave.POSLANO) {
                    poslanih++;
                } else {
                    zavrnjenih++;
                }
            }
        } finally {
            vTeku.remove(kljuc);
        }
        if (prekinjeno) {
            dnevnik.warn("Obvestila ({}): streznik poste ne dela, dostava prekinjena. Poslanih {} od {}; "
                    + "ostali pridejo na vrsto ob naslednjem zagonu opravila.", kljuc, poslanih, posiljke.size());
        } else if (zavrnjenih > 0 || poslanih < posiljke.size()) {
            dnevnik.warn("Obvestila ({}): poslanih {} od {}, zavrnjenih {}. Neposlana se poskusijo znova "
                    + "(najvec {} poskusov na prejemnika).", kljuc, poslanih, posiljke.size(), zavrnjenih,
                    NAJVEC_POSKUSOV);
        } else {
            dnevnik.info("Obvestila ({}): poslanih {}.", kljuc, poslanih);
        }
    }

    /* Napaka posiljatelja, ki ni napaka poste, zadeva to sporocilo: steje kot
       zavrnitev, da ena pokvarjena posiljka ne ustavi vseh za njo. */
    private IzidDostave dostavi(Posiljka p) {
        try {
            return posiljatelj.dostavi(p.naslov(), p.besedilo());
        } catch (RuntimeException napaka) {
            dnevnik.warn("Obvestila za racun {} ni bilo mogoce poslati: {}", p.idRacun(),
                    napaka.getClass().getSimpleName());
            return IzidDostave.ZAVRNJENO;
        }
    }

    /* Vsak zapis v svoji transakciji (v testu v transakciji klicalca): prekinjena
       dostava obdrzi, kar je ze poslala. Neuspel zapis (racun je bil vmes
       izbrisan) dostave ne ustavi. */
    private void zabelezi(Kljuc kljuc, Long idRacun, boolean poslano) {
        try {
            transakcija.executeWithoutResult(stanje -> {
                Optional<DostavaObvestila> obstojeca = kljuc.idTurnir() != null
                        ? repozitorij.findByIdTurnirAndIdRacun(kljuc.idTurnir(), idRacun)
                        : repozitorij.findByIdLigaAndKoloAndIdRacun(kljuc.idLiga(), kljuc.kolo(), idRacun);
                DostavaObvestila d = obstojeca.orElseGet(
                        () -> new DostavaObvestila(kljuc.idLiga(), kljuc.kolo(), kljuc.idTurnir(), idRacun));
                d.zabeleziPoskus(poslano, LocalDateTime.now());
                repozitorij.save(d);
            });
        } catch (RuntimeException napaka) {
            dnevnik.error("Dostave obvestila ({}) racunu {} ni bilo mogoce zabeleziti (poslano: {}): {}",
                    kljuc, idRacun, poslano, napaka.getClass().getSimpleName());
        }
    }
}
