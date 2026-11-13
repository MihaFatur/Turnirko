/* Meritev javnih bralnih poti nad KOPIJO prave baze - ni redni test.

   Pregled kakovosti (nov. 2026, najdbi C1 in C3) je javnim potem ocital, da
   ob vsakem obisku racunajo cez vso zgodovino. Trditev o hitrosti je vredna
   toliko kot meritev, zato ta poskus za vsako pot izpise tri stevilke:
   mediano casa odgovora, stevilo stavkov SQL na zahtevek in zgostitev telesa
   odgovora. Zgostitev je dokaz, da popravek hitrosti ni spremenil tega, kar
   gledalec dobi: pred popravkom in po njem mora biti enaka (izjema je
   nakljucni par, ki je vsakic drug).

   Meri celo pot skozi varnostno verigo, kontroler in zapis v JSON (MockMvc),
   ne le storitve - del poti (GET /turnirji/{id}) sestavlja kontroler.

   Zagon (baze ne spreminja, razen da Flyway ob zagonu uveljavi manjkajoce
   migracije - zato vedno nad kopijo):

     mvnw test -Dtest=HitrostJavnihPotiPoskus
         -Dturnirko.hitrost.baza=jdbc:postgresql://localhost:5433/turnirko_hitrost
         [-Dturnirko.hitrost.izpis=pot/do/datoteke.txt]

   Brez lastnosti turnirko.hitrost.baza se ne izvede. */
package si.turnirko.kontrolerji;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Supplier;

import jakarta.persistence.EntityManagerFactory;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import si.turnirko.storitve.DvojnikiIgralcevStoritev;
import si.turnirko.storitve.IgralciStoritev;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "turnirko.hitrost.baza", matches = ".+")
class HitrostJavnihPotiPoskus {

    /* Prvi klici po zagonu so zaradi prevajalnika JIT nekajkrat pocasnejsi. */
    private static final int OGREVANJE = 5;
    private static final int PONOVITEV = 15;

    @DynamicPropertySource
    static void baza(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> System.getProperty("turnirko.hitrost.baza"));
    }

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManagerFactory tovarna;
    @Autowired IgralciStoritev igralciStoritev;
    @Autowired DvojnikiIgralcevStoritev dvojnikiStoritev;

    private final List<String> izpis = new ArrayList<>();

    @Test
    void izmeri() throws Exception {
        /* Najzahtevnejsa primera: igralec z najdaljso zgodovino in turnir z
           najvec tekmami. Izbira je dolocena z bazo, zato sta meritvi pred
           popravkom in po njem nad istima zapisoma. */
        Long idIgralca = jdbc.queryForObject("""
                SELECT id_igralec FROM rating_zgodovina WHERE sistem = 'TURNIRKO'
                GROUP BY id_igralec ORDER BY count(*) DESC, id_igralec LIMIT 1""", Long.class);
        Long idTurnirja = jdbc.queryForObject("""
                SELECT d.id_turnir FROM tekma t JOIN dogodek d ON d.id = t.id_dogodek
                GROUP BY d.id_turnir ORDER BY count(*) DESC, d.id_turnir LIMIT 1""", Long.class);

        izpis.add(String.format("%-34s %9s %9s %7s  %s", "pot", "mediana", "najmanj", "stavkov", "zgostitev"));
        pot("GET /igralci", "/api/v1/igralci", true);
        pot("GET /lestvica", "/api/v1/lestvica", true);
        pot("GET /igralci/{id}/profil", "/api/v1/igralci/" + idIgralca + "/profil", true);
        pot("GET /turnirji", "/api/v1/turnirji", true);
        pot("GET /turnirji/{id}", "/api/v1/turnirji/" + idTurnirja, true);
        pot("GET /dvoboj/nakljucni", "/api/v1/dvoboj/nakljucni", false);
        pot("GET /premium/dokaz", "/api/v1/premium/dokaz", true);
        /* Poti administratorja gredo mimo prijave neposredno v storitev; telo
           nosi osebne podatke, zato se izpise samo zgostitev. */
        izmeri("storitev seznamPodrobno()", () -> igralciStoritev.seznamPodrobno().toString(), true);
        izmeri("storitev duplikati pari()", () -> dvojnikiStoritev.pari().toString(), true);

        /* Enakost na sirini: en sam turnir ali profil ne pokaze roba (turnir v
           pripravi, igralec brez ratinga, skrit z lestvice). Zgostitev tu
           pokrije vse turnirje in vsak dvajseti profil. */
        List<Long> turnirji = jdbc.queryForList("SELECT id FROM turnir ORDER BY id", Long.class);
        skupina("GET /turnirji/{id} za vse (" + turnirji.size() + ")",
                turnirji.stream().map(id -> "/api/v1/turnirji/" + id).toList());
        List<Long> igralci = jdbc.queryForList(
                "SELECT id FROM igralec WHERE id % 20 = 0 ORDER BY id", Long.class);
        skupina("GET profil, vsak 20. (" + igralci.size() + ")",
                igralci.stream().map(id -> "/api/v1/igralci/" + id + "/profil").toList());

        izpis.forEach(System.out::println);
        String datoteka = System.getProperty("turnirko.hitrost.izpis");
        if (datoteka != null && !datoteka.isBlank()) {
            Files.write(Path.of(datoteka), izpis, StandardCharsets.UTF_8);
        }
    }

    private void pot(String ime, String naslov, boolean ponovljiva) {
        izmeri(ime, () -> {
            try {
                return mockMvc.perform(get(naslov)).andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            } catch (Exception napaka) {
                throw new IllegalStateException(naslov, napaka);
            }
        }, ponovljiva);
    }

    /* Vec naslovov po enkrat: skupni cas, povprecje stavkov na zahtevek in ena
       zgostitev cez vsa telesa. */
    private void skupina(String ime, List<String> naslovi) throws Exception {
        Statistics statistika = tovarna.unwrap(SessionFactory.class).getStatistics();
        long stavkovPrej = statistika.getPrepareStatementCount();
        MessageDigest zgostitev = MessageDigest.getInstance("SHA-256");
        long znakov = 0;
        long zacetek = System.nanoTime();
        for (String naslov : naslovi) {
            String telo = mockMvc.perform(get(naslov)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            zgostitev.update(telo.getBytes(StandardCharsets.UTF_8));
            znakov += telo.length();
        }
        long trajanje = System.nanoTime() - zacetek;
        long stavkov = statistika.getPrepareStatementCount() - stavkovPrej;
        izpis.add(String.format("%-34s %6.1f ms %9s %7.1f  %s (%d znakov)",
                ime, trajanje / 1e6 / naslovi.size(), "(povpr.)", (double) stavkov / naslovi.size(),
                HexFormat.of().formatHex(zgostitev.digest(), 0, 8), znakov));
    }

    private void izmeri(String ime, Supplier<String> klic, boolean ponovljiva) {
        Statistics statistika = tovarna.unwrap(SessionFactory.class).getStatistics();
        for (int i = 0; i < OGREVANJE; i++) {
            klic.get();
        }
        long[] casi = new long[PONOVITEV];
        long stavkovPrej = statistika.getPrepareStatementCount();
        String telo = null;
        for (int i = 0; i < PONOVITEV; i++) {
            long zacetek = System.nanoTime();
            telo = klic.get();
            casi[i] = System.nanoTime() - zacetek;
        }
        long stavkov = statistika.getPrepareStatementCount() - stavkovPrej;
        Arrays.sort(casi);
        izpis.add(String.format("%-34s %6.1f ms %6.1f ms %7.1f  %s",
                ime, casi[PONOVITEV / 2] / 1e6, casi[0] / 1e6, (double) stavkov / PONOVITEV,
                ponovljiva ? zgostitev(telo) + " (" + telo.length() + " znakov)" : "(nakljucno)"));
    }

    private static String zgostitev(String besedilo) {
        try {
            byte[] z = MessageDigest.getInstance("SHA-256").digest(besedilo.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(z, 0, 8);
        } catch (Exception napaka) {
            throw new IllegalStateException(napaka);
        }
    }
}
