/* Dostop do sifranta klubov. */
package si.turnirko.repozitoriji;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import si.turnirko.modeli.Klub;
import si.turnirko.pomozno.SlovenskaAbeceda;

public interface KlubRepozitorij extends JpaRepository<Klub, Long> {

    /* Vsi klubi po imenu v slovenski abecedi. Ne findAll(Sort.by("ime")): SQLite
       besedilo primerja po kodnih tockah in bi Č, Š, Ž postavil za Z. */
    default List<Klub> najdiVsePoAbecedi() {
        List<Klub> klubi = new ArrayList<>(findAll());
        klubi.sort(Comparator.comparing(Klub::getIme, SlovenskaAbeceda.RED));
        return klubi;
    }
}
