/* Osrednje mesto, kjer se izjeme prevedejo v enotne HTTP odgovore
   (standard "problem detail", RFC 9457). Kontrolerji tako ne potrebujejo
   lastne obravnave napak, odjemalec pa vedno dobi enako obliko. */
package si.turnirko.izjeme;

import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalniObravnavalecIzjem {

    @ExceptionHandler(NiNajdenoIzjema.class)
    ProblemDetail niNajdeno(NiNajdenoIzjema izjema) {
        return sestavi(HttpStatus.NOT_FOUND, "Ni najdeno", izjema.getMessage());
    }

    @ExceptionHandler(NeveljavenVnosIzjema.class)
    ProblemDetail neveljavenVnos(NeveljavenVnosIzjema izjema) {
        return sestavi(HttpStatus.BAD_REQUEST, "Neveljaven vnos", izjema.getMessage());
    }

    @ExceptionHandler(PrepovedanoIzjema.class)
    ProblemDetail prepovedano(PrepovedanoIzjema izjema) {
        return sestavi(HttpStatus.FORBIDDEN, "Ni pravice", izjema.getMessage());
    }

    @ExceptionHandler(DomenskaIzjema.class)
    ProblemDetail domenskaNapaka(DomenskaIzjema izjema) {
        return sestavi(HttpStatus.CONFLICT, "Krsitev pravila", izjema.getMessage());
    }

    /* Napake anotacij @NotBlank, @NotNull ... na DTO-jih. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail napakaPreverjanja(MethodArgumentNotValidException izjema) {
        String podrobnosti = izjema.getBindingResult().getFieldErrors().stream()
                .map(napaka -> napaka.getField() + ": " + napaka.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return sestavi(HttpStatus.BAD_REQUEST, "Neveljaven vnos", podrobnosti);
    }

    /* Dva uporabnika sta hkrati spreminjala isti zapis - drugi mora poskusiti znova. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail socasnaSprememba(OptimisticLockingFailureException izjema) {
        return sestavi(HttpStatus.CONFLICT, "Socasna sprememba",
                "Zapis je medtem spremenil nekdo drug. Osvezi podatke in poskusi znova.");
    }

    /* Krsitev omejitve v bazi (UNIQUE, CHECK, tuji kljuc). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail krsitevOmejitve(DataIntegrityViolationException izjema) {
        return sestavi(HttpStatus.CONFLICT, "Krsitev omejitve podatkov",
                "Podatki se podvajajo ali krsijo pravila baze (npr. e-posta ze obstaja).");
    }

    private ProblemDetail sestavi(HttpStatus status, String naslov, String podrobnost) {
        ProblemDetail napaka = ProblemDetail.forStatus(status);
        napaka.setTitle(naslov);
        napaka.setDetail(podrobnost);
        return napaka;
    }
}
