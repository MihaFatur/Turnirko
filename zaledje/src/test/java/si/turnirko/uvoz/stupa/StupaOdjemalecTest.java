/* Prehodna napaka vira ne sme podreti posnetka.

   Vir (Stupa) obcasno vrne 502 s svojega posrednika; ker je posnetek enega
   dogodka osem zahtev, je verjetnost, da katera od njih zadene tak trenutek,
   precejsnja. Test zato postavi majhen streznik, ki najprej vraca 502. */
package si.turnirko.uvoz.stupa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.sun.net.httpserver.HttpServer;

class StupaOdjemalecTest {

    private static final String PRAZEN_ODGOVOR = "{\"total\":0,\"data\":[]}";

    private HttpServer streznik;
    private final AtomicInteger zahtev = new AtomicInteger();

    @AfterEach
    void ustavi() {
        if (streznik != null) {
            streznik.stop(0);
        }
    }

    @Test
    void prehodnaNapakaSePoskusiZnova() throws IOException {
        StupaOdjemalec odjemalec = odjemalec(2);

        assertEquals(0, odjemalec.dogodki().size());
        assertEquals(3, zahtev.get(), "dva zavrnjena poskusa in tretji uspesen");
    }

    @Test
    void trajnaNapakaSePovePoZadnjemPoskusu() throws IOException {
        StupaOdjemalec odjemalec = odjemalec(Integer.MAX_VALUE);

        IOException e = assertThrows(IOException.class, odjemalec::dogodki);
        assertTrue(e.getMessage().contains("502"), e.getMessage());
        assertEquals(3, zahtev.get(), "poskusi se ne ponavljajo v nedogled");
    }

    /* Streznik, ki prvih `napak` zahtev zavrne z 502, nato vrne prazen seznam. */
    private StupaOdjemalec odjemalec(int napak) throws IOException {
        streznik = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        streznik.createContext("/", izmenjava -> {
            byte[] telo = PRAZEN_ODGOVOR.getBytes(StandardCharsets.UTF_8);
            boolean napaka = zahtev.incrementAndGet() <= napak;
            izmenjava.sendResponseHeaders(napaka ? 502 : 200, napaka ? -1 : telo.length);
            if (!napaka) {
                try (var izhod = izmenjava.getResponseBody()) {
                    izhod.write(telo);
                }
            }
            izmenjava.close();
        });
        streznik.start();

        StupaOdjemalec odjemalec = new StupaOdjemalec();
        ReflectionTestUtils.setField(odjemalec, "naslov", "http://127.0.0.1:" + streznik.getAddress().getPort());
        ReflectionTestUtils.setField(odjemalec, "tenant", "ntzs");
        ReflectionTestUtils.setField(odjemalec, "premor", Duration.ofMillis(10));
        return odjemalec;
    }
}
