/* Bajti profilnih slik (V56). */
package si.turnirko.repozitoriji;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import si.turnirko.modeli.ProfilnaSlikaVsebina;

public interface ProfilnaSlikaVsebinaRepozitorij extends JpaRepository<ProfilnaSlikaVsebina, Long> {

    @Query("""
            SELECT v FROM ProfilnaSlikaVsebina v, ProfilnaSlika p
            WHERE v.idProfilnaSlika = p.id AND p.zgostitev = :zgostitev AND v.velikost = :velikost
            """)
    Optional<ProfilnaSlikaVsebina> najdi(String zgostitev, String velikost);

    /* Za izbris prek entitet in ne z množičnim DELETE: SQLite id izbrisane vrstice
       da naslednji (nova slika v isti transakciji), stara entiteta v seji pa bi
       z njo trčila (NonUniqueObjectException). */
    List<ProfilnaSlikaVsebina> findByIdProfilnaSlika(Long idProfilnaSlika);
}
