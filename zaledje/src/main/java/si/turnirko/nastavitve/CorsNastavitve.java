/* CORS: s katerih naslovov sme brskalnik klicati to zaledje.
   Naslovi se berejo iz nastavitve turnirko.cors.dovoljeni-izvori, da za
   razlicna okolja ni treba spreminjati kode. Vir uporablja tudi Spring
   Security (filter cors()), zato je pravilo definirano na enem mestu.
   Poverilnice (Authorization) posiljamo v glavi, ne prek piskotkov, zato
   allowCredentials ni potreben in "*" glave ostanejo dovoljene. */
package si.turnirko.nastavitve;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsNastavitve {

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${turnirko.cors.dovoljeni-izvori:}") List<String> dovoljeniIzvori) {
        CorsConfiguration konfiguracija = new CorsConfiguration();
        if (!dovoljeniIzvori.isEmpty()) {
            konfiguracija.setAllowedOrigins(dovoljeniIzvori);
        }
        konfiguracija.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        konfiguracija.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource vir = new UrlBasedCorsConfigurationSource();
        vir.registerCorsConfiguration("/api/**", konfiguracija);
        return vir;
    }
}
