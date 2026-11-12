/* Naslov odjemalca, po katerem stejejo vsi omejevalniki poskusov (prijava,
   kode, predogled zapisa, registracija) - V1 varnostnega pregleda.

   Do novembra 2026 je bil na spletu vklopljen Springov filter ("framework"),
   ki najprej verjame glavi Forwarded. Caddy te glave od odjemalca ne odstrani,
   zato je bil vsak zahtevek lahko "nov naslov IP" in meje po IP niso veljale.
   Zdaj naslov doloca Tomcatov ventil ("native"): glave Forwarded ne pozna,
   glavi X-Forwarded-For pa verjame samo od posrednika na zasebnem naslovu
   (v produkciji Caddy v Dockerjevem omrezju, tu test sam na 127.0.0.1).

   Test tece na PRAVEM strezniku (nakljucna vrata): ventil je del Tomcata in
   ga MockMvc ne pozene. */
package si.turnirko.nastavitve;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.test.context.ActiveProfiles;

import jakarta.servlet.Filter;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        // iste tri vrstice kot v application-splet.properties (glej nastavitevNaSpletuJeNative)
        "server.forward-headers-strategy=native",
        "server.tomcat.remoteip.remote-ip-header=X-Forwarded-For",
        "server.tomcat.remoteip.protocol-header=X-Forwarded-Proto"
})
@ActiveProfiles("test")
@Import(NaslovOdjemalcaTest.Lovilec.class)
class NaslovOdjemalcaTest {

    private static final AtomicReference<String> ZADNJI_NASLOV = new AtomicReference<>();

    /* Prvi filter v verigi si zapomni naslov, kakrsnega vidi aplikacija -
       istega prebere OmejitevPrijavFilter z getRemoteAddr(). */
    @TestConfiguration
    static class Lovilec {
        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE)
        Filter lovilecNaslova() {
            return (zahteva, odgovor, veriga) -> {
                ZADNJI_NASLOV.set(zahteva.getRemoteAddr());
                veriga.doFilter(zahteva, odgovor);
            };
        }
    }

    @Value("${local.server.port}")
    int vrata;

    private final HttpClient odjemalec = HttpClient.newHttpClient();

    @BeforeEach
    void pocisti() {
        ZADNJI_NASLOV.set(null);
    }

    @Test
    void glavaForwardedNaslovaNeSpremeni() throws Exception {
        poslji("Forwarded", "for=6.6.6.6");

        assertEquals("127.0.0.1", ZADNJI_NASLOV.get(),
                "glava Forwarded pride od odjemalca in ne sme dolociti naslova");
    }

    @Test
    void naslovPovePosrednikZGlavoXForwardedFor() throws Exception {
        poslji("X-Forwarded-For", "203.0.113.9");

        assertEquals("203.0.113.9", ZADNJI_NASLOV.get(),
                "X-Forwarded-For od posrednika na zasebnem naslovu je naslov odjemalca");
    }

    @Test
    void obeGlaviSkupajVeljaSamoPosrednikova() throws Exception {
        poslji("Forwarded", "for=6.6.6.6", "X-Forwarded-For", "203.0.113.9");

        assertEquals("203.0.113.9", ZADNJI_NASLOV.get());
    }

    /* Vedenje zgoraj velja za nastavitve tega testa; ta test drzi, da jih ima
       tudi datoteka, ki gre na streznik. */
    @Test
    void nastavitevNaSpletuJeNative() throws Exception {
        Properties splet = new Properties();
        try (InputStream vhod = getClass().getResourceAsStream("/application-splet.properties")) {
            splet.load(vhod);
        }

        assertEquals("native", splet.getProperty("server.forward-headers-strategy"),
                "\"framework\" verjame glavi Forwarded od odjemalca (V1)");
        assertEquals("X-Forwarded-For", splet.getProperty("server.tomcat.remoteip.remote-ip-header"));
    }

    private void poslji(String... glave) throws Exception {
        HttpRequest zahteva = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + vrata + "/api/v1/klubi"))
                .headers(glave)
                .GET()
                .build();
        odjemalec.send(zahteva, HttpResponse.BodyHandlers.discarding());
    }
}
