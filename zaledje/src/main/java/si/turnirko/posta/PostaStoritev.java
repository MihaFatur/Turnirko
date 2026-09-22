/* Vstopna tocka storitev za posiljanje e-poste.

   Posiljanje se ODLOZI do potrditve transakcije: koda, ki bi sla ven, nato
   pa bi se zapis racuna razveljavil (npr. krsitev omejitve v bazi), bi
   prejemnika poslala k vpisu kode za racun, ki ne obstaja. Izven transakcije
   (interni klic) se poslje takoj. Sam kanal (SMTP, dnevnik, pomnilnik) je
   stvar PosiljateljPoste. */
package si.turnirko.posta;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class PostaStoritev {

    private final PosiljateljPoste posiljatelj;

    public PostaStoritev(PosiljateljPoste posiljatelj) {
        this.posiljatelj = posiljatelj;
    }

    public void poslji(String prejemnik, SporocilaPoste.Besedilo besedilo) {
        poslji(prejemnik, besedilo.zadeva(), besedilo.telo());
    }

    public void poslji(String prejemnik, String zadeva, String besedilo) {
        if (!posiljatelj.odloziDoPotrditve()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            posiljatelj.poslji(prejemnik, zadeva, besedilo);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                posiljatelj.poslji(prejemnik, zadeva, besedilo);
            }
        });
    }
}
