/* Varnost: gost (neprijavljen) sme samo brati (GET), vse spremembe
   (POST/PUT/PATCH/DELETE) zahtevajo prijavljenega administratorja.

   Prijava tece prek HTTP Basic (glava Authorization), stanje na strezniku
   ni potrebno (SessionCreationPolicy.STATELESS) - odjemalec ob vsakem
   spreminjanju poslje poverilnice. Ob manjkajoci/napacni prijavi vrnemo
   401 BREZ glave "WWW-Authenticate", da brskalnik ne odpre svojega
   vgrajenega okna za prijavo - to okno prikaze aplikacija sama. */
package si.turnirko.nastavitve;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

@Configuration
@EnableWebSecurity
public class VarnostneNastavitve {

    @Bean
    PasswordEncoder kodirnikGesel() {
        return new BCryptPasswordEncoder();
    }

    /* Uporabnike bere iz baze; vloga postane pravica ROLE_ADMIN oz. ROLE_IGRALEC.

       Racun igralca, ki ga administrator se ni potrdil (ali ga je zavrnil),
       se sme prijaviti - drugace ne bi mogel izvedeti, v kaksnem stanju je -
       vendar dobi le pravico ROLE_CAKAJOCI, ki ne odpira nicesar razen
       lastnega profila racuna (/auth/me). */
    @Bean
    UserDetailsService uporabnikiIzBaze(UporabnikRepozitorij repozitorij) {
        return prijavnoIme -> repozitorij.findByUporabniskoIme(prijavnoIme)
                .filter(Uporabnik::isAktiven)
                .map(u -> User.withUsername(u.getUporabniskoIme())
                        .password(u.getGesloHash())
                        .roles(pravica(u))
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Uporabnik " + prijavnoIme + " ne obstaja."));
    }

    private static String pravica(Uporabnik u) {
        if (u.getVloga() == Vloga.ADMIN) {
            return Vloga.ADMIN.name();
        }
        return u.jePotrjenIgralec() ? Vloga.IGRALEC.name() : "CAKAJOCI";
    }

    /* Ob neuspeli prijavi vrne 401 v obliki problem-detail (kot ostale
       napake), brez glave WWW-Authenticate (brez brskalnikovega okna). */
    @Bean
    AuthenticationEntryPoint vstopnaTockaPrijave() {
        return (zahteva, odgovor, izjema) -> {
            odgovor.setStatus(401);
            odgovor.setContentType("application/json;charset=UTF-8");
            odgovor.getWriter().write(
                    "{\"title\":\"Potrebna je prijava\","
                    + "\"detail\":\"Za ta pogled oziroma dejanje je potrebna prijava.\"}");
        };
    }

    @Bean
    SecurityFilterChain varnostnaVeriga(HttpSecurity http,
                                        AuthenticationEntryPoint vstopnaTocka) throws Exception {
        http
                // uporabi bean CorsConfigurationSource po imenu (corsConfigurationSource)
                .cors(Customizer.withDefaults())
                // odjemalec je SPA/REST z Basic prijavo v glavi - piskotkov ni,
                // zato CSRF zascita ni potrebna
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(seje -> seje.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(dovoljenja -> dovoljenja
                        // predpregled (CORS) mora skozi brez prijave
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // registracija igralca je edina mutacija brez prijave;
                        // racun nastane v stanju CAKA in sam po sebi ne da pravic
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/registracija").permitAll()
                        // /auth/me sluzi za preverbo poverilnic - zahteva veljavno prijavo
                        .requestMatchers("/api/v1/auth/**").authenticated()
                        // zasebni del profila (analize) vidi samo igralec sam ali
                        // administrator; lastnistvo preveri ProfilStoritev
                        .requestMatchers(HttpMethod.GET, "/api/v1/igralci/*/profil/zasebno").authenticated()
                        // seznam racunov vsebuje e-poste, zato ni javen kljub temu, da je GET
                        .requestMatchers(HttpMethod.GET, "/api/v1/racuni/**").hasRole("ADMIN")
                        // gost sme brati vse ostalo
                        .requestMatchers(HttpMethod.GET, "/api/**").permitAll()
                        // vse spremembe sme samo administrator
                        .requestMatchers("/api/**").hasRole("ADMIN")
                        .anyRequest().permitAll())
                .httpBasic(basic -> basic.authenticationEntryPoint(vstopnaTocka))
                .exceptionHandling(obravnava -> obravnava.authenticationEntryPoint(vstopnaTocka));
        return http.build();
    }
}
