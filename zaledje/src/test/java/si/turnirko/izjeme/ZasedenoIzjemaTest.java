/* Zaseden streznik (ZasedenoIzjema) je HTTP 503 z glavo Retry-After: vrsta
   brez signala v vmesniku in postaja za zajem tak odgovor ponovita, odgovora
   4xx pa ne. */
package si.turnirko.izjeme;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

class ZasedenoIzjemaTest {

    @Test
    void zasedenoJe503ZRetryAfter() {
        ResponseEntity<ProblemDetail> odgovor = new GlobalniObravnavalecIzjem()
                .zasedeno(new ZasedenoIzjema("Poskusi znova.", 5));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, odgovor.getStatusCode());
        assertEquals("5", odgovor.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
        assertEquals("Poskusi znova.", odgovor.getBody().getDetail());
        assertEquals(503, odgovor.getBody().getStatus());
    }
}
