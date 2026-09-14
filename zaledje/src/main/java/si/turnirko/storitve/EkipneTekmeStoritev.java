/* Srecanja ekipnih tekem turnirja (dogodek EKIPNO, V28).

   Ekipna tekma v mrezi ali skupini ima SRECANJE, cim sta znani obe ekipi -
   tam se vpise postava in odigrajo posamicne tekme po formatu dogodka. Tekma
   in srecanje sta vedno v razmerju ena proti ena (srecanje.id_tekma UNIQUE),
   domaca ekipa srecanja pa je ekipa PRVE prijave tekme - po tem izid srecanja
   najde pravo stran tekme (TekmaStoritev.zakljuciEkipnoTekmo).

   Srecanja NI:
   - pri prenesenem izidu (finalna skupina) - dvoboj je bil ze odigran,
   - pri koncani tekmi brez srecanja (brez boja, prosti prehod),
   - dokler ena od ekip se ni znana (tekma visjega kola). */
package si.turnirko.storitve;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.Srecanje;
import si.turnirko.modeli.StatusSrecanja;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.Tekma;
import si.turnirko.repozitoriji.DogodekRepozitorij;
import si.turnirko.repozitoriji.SrecanjeRepozitorij;
import si.turnirko.repozitoriji.TekmaRepozitorij;

@Service
public class EkipneTekmeStoritev {

    private final DogodekRepozitorij dogodekRepozitorij;
    private final TekmaRepozitorij tekmaRepozitorij;
    private final SrecanjeRepozitorij srecanjeRepozitorij;

    public EkipneTekmeStoritev(DogodekRepozitorij dogodekRepozitorij,
                               TekmaRepozitorij tekmaRepozitorij,
                               SrecanjeRepozitorij srecanjeRepozitorij) {
        this.dogodekRepozitorij = dogodekRepozitorij;
        this.tekmaRepozitorij = tekmaRepozitorij;
        this.srecanjeRepozitorij = srecanjeRepozitorij;
    }

    /* Vsaki ekipni tekmi z obema ekipama, ki se igra, zagotovi srecanje. */
    @Transactional
    public void zagotoviSrecanja(Long idDogodka) {
        Dogodek dogodek = dogodekRepozitorij.najdiSTurnirjem(idDogodka).orElse(null);
        if (dogodek == null || !dogodek.jeEkipno()) {
            return;
        }
        Set<Long> zSrecanjem = new HashSet<>();
        for (Object[] r : srecanjeRepozitorij.srecanjaDogodka(idDogodka)) {
            zSrecanjem.add(((Number) r[0]).longValue());
        }
        List<Tekma> tekme = tekmaRepozitorij.najdiZaDogodek(idDogodka);
        for (Tekma tekma : tekme) {
            if (tekma.jePrenesena() || tekma.getStatus() == StatusTekme.KONCANA
                    || tekma.getPrijava1() == null || tekma.getPrijava2() == null
                    || zSrecanjem.contains(tekma.getId())) {
                continue;
            }
            Srecanje srecanje = new Srecanje(tekma, tekma.getPrijava1().getEkipa(),
                    tekma.getPrijava2().getEkipa());
            // turnirska tekma pove samo dan; ura 00:00 pomeni "ura ni dolocena"
            srecanje.setPredvidenZacetek(tekma.getPredvidenZacetek() != null
                    ? tekma.getPredvidenZacetek()
                    : (dogodek.getTurnir().getDatumZacetka() != null
                            ? dogodek.getTurnir().getDatumZacetka().atStartOfDay() : null));
            srecanjeRepozitorij.save(srecanje);
        }
    }

    /* Ekipna tekma dobi izid brez boja: srecanja ni bilo. Ce je ze nastalo,
       se se ni smelo zaceti - zapisnika z vpisano postavo tihi izbris ne sme
       odnesti. */
    @Transactional
    public void odstraniNezacetoSrecanje(Long idTekma) {
        srecanjeRepozitorij.najdiZaTekmo(idTekma).ifPresent(s -> {
            if (s.getStatus() != StatusSrecanja.RAZPORED) {
                throw new DomenskaIzjema("Srecanje te ekipne tekme ze poteka - izid nastane iz srecanja.");
            }
            srecanjeRepozitorij.delete(s);
            srecanjeRepozitorij.flush();
        });
    }
}
