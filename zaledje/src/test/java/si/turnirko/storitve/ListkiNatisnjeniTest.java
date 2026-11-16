/* Sled natisnjenih listkov tekem (V67).

   Domenska pravila, ki jih ta test varuje:
   - organizator oznaci izbrane tekme kot natisnjene in oznako odstrani,
   - oznaka seje samo tekem TEGA dogodka (tuji id se tiho preskoci),
   - oznacevanje ne poveca verzije tekme: ob istem casu vneseni rezultat ne
     sme dobiti napake optimisticnega zaklepanja,
   - oznaka ne spremeni statusa tekme (listek se da natisniti znova),
   - oznacuje lahko samo lastnik turnirja ali admin. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import si.turnirko.dto.TekmaDto;
import si.turnirko.izjeme.PrepovedanoIzjema;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.Tekma;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

class ListkiNatisnjeniTest extends IntegracijskiTest {

    @Autowired ListkiStoritev listkiStoritev;
    @Autowired UporabnikRepozitorij uporabnikRepozitorij;
    @PersistenceContext EntityManager seja;

    @AfterEach
    void pocisti() {
        SecurityContextHolder.clearContext();
    }

    /* Nova tekma nima oznake; oznacena jo dobi, po odznacitvi je spet nima. Ostale
       tekme dogodka ostanejo, kot so. */
    @Test
    void izbraneTekmeSeOznaciInOdznacijo() {
        Dogodek dogodek = pripraviDogodek(8);
        zrebStoritev.izvediZreb(dogodek.getId());
        List<Tekma> tekme = tekmeDogodka(dogodek.getId());
        Long prva = tekme.get(0).getId();
        Long druga = tekme.get(1).getId();
        Long tretja = tekme.get(2).getId();
        assertNull(oznaka(prva));

        assertEquals(2, listkiStoritev.oznaci(dogodek.getId(), List.of(prva, druga), true));
        assertNotNull(oznaka(prva));
        assertNotNull(oznaka(druga));
        assertNull(oznaka(tretja));

        assertEquals(1, listkiStoritev.oznaci(dogodek.getId(), List.of(prva), false));
        assertNull(oznaka(prva));
        assertNotNull(oznaka(druga));
    }

    /* Id tekme drugega dogodka ne zadane nicesar, tudi ce je poslan skupaj z
       veljavnimi. */
    @Test
    void tujaTekmaSePreskoci() {
        Dogodek moj = pripraviDogodek(4);
        Dogodek tuj = pripraviDogodek(4);
        zrebStoritev.izvediZreb(moj.getId());
        zrebStoritev.izvediZreb(tuj.getId());
        Long mojaTekma = tekmeDogodka(moj.getId()).get(0).getId();
        Long tujaTekma = tekmeDogodka(tuj.getId()).get(0).getId();

        assertEquals(1, listkiStoritev.oznaci(moj.getId(), List.of(mojaTekma, tujaTekma), true));
        assertNotNull(oznaka(mojaTekma));
        assertNull(oznaka(tujaTekma));
    }

    /* Prazen seznam ni napaka in nic ne spremeni. */
    @Test
    void prazenSeznamNicNeSpremeni() {
        Dogodek dogodek = pripraviDogodek(4);
        zrebStoritev.izvediZreb(dogodek.getId());

        assertEquals(0, listkiStoritev.oznaci(dogodek.getId(), List.of(), true));
        assertEquals(0, listkiStoritev.oznaci(dogodek.getId(), null, true));
    }

    /* Oznaka je opomnik: verzija tekme (optimisticno zaklepanje) in status ostaneta,
       zato hkratni vnos rezultata ne pade. */
    @Test
    void oznakaNeSpremeniVerzijeInStatusa() {
        Dogodek dogodek = pripraviDogodek(4);
        zrebStoritev.izvediZreb(dogodek.getId());
        Tekma tekma = tekmeDogodka(dogodek.getId()).get(0);
        Long id = tekma.getId();
        var statusPrej = tekma.getStatus();
        long verzijaPrej = verzija(id);

        listkiStoritev.oznaci(dogodek.getId(), List.of(id), true);

        assertEquals(verzijaPrej, verzija(id));
        assertEquals(statusPrej, tekmaRepozitorij.findById(id).orElseThrow().getStatus());
    }

    /* Oznaka gre v isti DTO, ki ga bere vmesnik. */
    @Test
    void oznakaJeVDtoTekme() {
        Dogodek dogodek = pripraviDogodek(4);
        zrebStoritev.izvediZreb(dogodek.getId());
        Long id = tekmeDogodka(dogodek.getId()).get(0).getId();
        assertNull(TekmaDto.iz(tekmaRepozitorij.findById(id).orElseThrow()).listekNatisnjenOb());

        listkiStoritev.oznaci(dogodek.getId(), List.of(id), true);

        assertNotNull(TekmaDto.iz(tekmaRepozitorij.findById(id).orElseThrow()).listekNatisnjenOb());
    }

    /* Tuj organizator ne sme oznacevati, lastnik in admin smeta. */
    @Test
    void oznacujeLahkoSamoLastnikAliAdmin() {
        Dogodek dogodek = pripraviDogodek(4);
        zrebStoritev.izvediZreb(dogodek.getId());
        Long id = tekmeDogodka(dogodek.getId()).get(0).getId();
        Uporabnik lastnik = racun("lastnik@test", Vloga.ORGANIZATOR);
        racun("tujec@test", Vloga.ORGANIZATOR);
        racun("admin@test", Vloga.ADMIN);
        dogodek.getTurnir().setUstvaril(lastnik);
        turnirRepozitorij.save(dogodek.getTurnir());

        prijava("tujec@test", "ROLE_ORGANIZATOR");
        assertThrows(PrepovedanoIzjema.class,
                () -> listkiStoritev.oznaci(dogodek.getId(), List.of(id), true));

        prijava("lastnik@test", "ROLE_ORGANIZATOR");
        assertDoesNotThrow(() -> listkiStoritev.oznaci(dogodek.getId(), List.of(id), true));

        prijava("admin@test", "ROLE_ADMIN");
        assertDoesNotThrow(() -> listkiStoritev.oznaci(dogodek.getId(), List.of(id), false));
    }

    // ---------- Pomozno ----------

    /* Oznaka, sveze prebrana iz baze (skupinska posodobitev izprazni predpomnilnik seje). */
    private Object oznaka(Long idTekme) {
        return tekmaRepozitorij.findById(idTekme).orElseThrow().getListekNatisnjenOb();
    }

    private long verzija(Long idTekme) {
        seja.flush();
        return ((Number) seja.createNativeQuery("SELECT verzija FROM tekma WHERE id = :id")
                .setParameter("id", idTekme).getSingleResult()).longValue();
    }

    private Uporabnik racun(String prijavnoIme, Vloga vloga) {
        Uporabnik u = new Uporabnik(prijavnoIme, "{bcrypt}x", vloga);
        u.setStatus(StatusRacuna.POTRJEN);
        return uporabnikRepozitorij.save(u);
    }

    private void prijava(String prijavnoIme, String vloga) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(prijavnoIme, null,
                        List.of(new SimpleGrantedAuthority(vloga))));
    }
}
