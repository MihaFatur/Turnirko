/* Zacetni administrator sme nastati samo z gesla vrednim geslom.

   Pravilo je varnostno in tiho: ce kdo vrne privzetek v application.properties
   ali zniza preverbo, se nic ne pokvari - streznik samo tece z ugotovljivim
   geslom. Zato ga zaklenemo s testom. */
package si.turnirko.nastavitve;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

@ExtendWith(MockitoExtension.class)
class ZacetniAdminTest {

    @Mock UporabnikRepozitorij uporabniki;
    @Mock PasswordEncoder kodirnik;

    private ZacetniAdmin admin(String geslo, String... profili) {
        MockEnvironment okolje = new MockEnvironment();
        okolje.setActiveProfiles(profili);
        return new ZacetniAdmin(uporabniki, kodirnik, okolje, "admin", geslo);
    }

    @Test
    void brezGeslaSeStreznikNeZazene() {
        when(uporabniki.count()).thenReturn(0L);

        IllegalStateException napaka = assertThrows(IllegalStateException.class,
                () -> admin("").run(null));

        assertTrue(napaka.getMessage().contains("turnirko.admin.privzeto-geslo"),
                "napaka mora povedati, katero nastavitev manjka: " + napaka.getMessage());
        verify(uporabniki, never()).save(any());
    }

    @Test
    void prekratkoGesloSeZavrne() {
        when(uporabniki.count()).thenReturn(0L);

        assertThrows(IllegalStateException.class, () -> admin("admin").run(null));
        verify(uporabniki, never()).save(any());
    }

    @Test
    void namizniNacinSmeImetiLokalniPrivzetek() {
        when(uporabniki.count()).thenReturn(0L);
        when(kodirnik.encode("admin")).thenReturn("zgostitev");

        admin("", "namizni").run(null);

        verify(uporabniki).save(any(Uporabnik.class));
    }

    @Test
    void dovoljDolgoGesloUstvariAdmina() {
        when(uporabniki.count()).thenReturn(0L);
        when(kodirnik.encode("dovolj-dolgo-geslo")).thenReturn("zgostitev");

        admin("dovolj-dolgo-geslo").run(null);

        var ujeti = org.mockito.ArgumentCaptor.forClass(Uporabnik.class);
        verify(uporabniki).save(ujeti.capture());
        assertEquals(Vloga.ADMIN, ujeti.getValue().getVloga());
        assertEquals("zgostitev", ujeti.getValue().getGesloHash());
    }

    @Test
    void obObstojecemUporabnikuSeNeZgodiNic() {
        when(uporabniki.count()).thenReturn(3L);

        admin("").run(null); // geslo ni potrebno - admin ze obstaja

        verify(uporabniki, never()).save(any());
    }
}
