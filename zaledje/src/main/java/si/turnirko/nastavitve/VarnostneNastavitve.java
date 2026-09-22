/* Varnost: gost (neprijavljen) sme samo brati (GET), vse spremembe
   (POST/PUT/PATCH/DELETE) zahtevajo prijavljenega administratorja.

   Prijava tece prek HTTP Basic (glava Authorization), stanje na strezniku
   ni potrebno (SessionCreationPolicy.STATELESS) - odjemalec ob vsakem
   spreminjanju poslje poverilnice. Ob manjkajoci/napacni prijavi vrnemo
   401 BREZ glave "WWW-Authenticate", da brskalnik ne odpre svojega
   vgrajenega okna za prijavo - to okno prikaze aplikacija sama.

   Pred Basic filtrom stoji OmejitevPrijavFilter: racun ali naslov IP s
   prevec neuspelimi prijavami dobi 429, se preden se geslo preveri. */
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
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.UporabnikRepozitorij;
import si.turnirko.storitve.OmejevalnikPoskusov;

@Configuration
@EnableWebSecurity
public class VarnostneNastavitve {

    @Bean
    PasswordEncoder kodirnikGesel() {
        return new BCryptPasswordEncoder();
    }

    /* Uporabnike bere iz baze; vloga postane pravica ROLE_ADMIN, ROLE_ORGANIZATOR
       oz. ROLE_IGRALEC.

       Racun, ki ga administrator se ni potrdil (ali ga je zavrnil) - naj bo
       igralec ali organizator -, se sme prijaviti, da izve, v kaksnem stanju
       je, vendar dobi le pravico ROLE_CAKAJOCI, ki ne odpira nicesar razen
       lastnega profila racuna (/auth/me). Isto velja za racun, ki naslova se
       ni potrdil: vmesnik mu tako lahko ponudi vpis kode. */
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
        if (u.getVloga() == Vloga.ORGANIZATOR) {
            return u.jePotrjenOrganizator() ? Vloga.ORGANIZATOR.name() : "CAKAJOCI";
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
                                        AuthenticationEntryPoint vstopnaTocka,
                                        OmejevalnikPoskusov omejevalnik) throws Exception {
        http
                // uporabi bean CorsConfigurationSource po imenu (corsConfigurationSource)
                .cors(Customizer.withDefaults())
                // odjemalec je SPA/REST z Basic prijavo v glavi - piskotkov ni,
                // zato CSRF zascita ni potrebna
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(seje -> seje.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // HSTS postavi Caddy, ne aplikacija - ta HTTPS sploh ne prekine
                // in ne ve, katere poddomene obstajajo. Privzetek Spring
                // Security doda "includeSubDomains", kar brskalniku za CELO LETO
                // vsili HTTPS tudi na webmail. in mail., ki nista na tem
                // strezniku in imata certifikat gostitelja domene. Brskalnik bi
                // ju zavrnil, opozorila pod HSTS ni mogoce obiti, posta pa bi
                // postala nedosegljiva. Izmerjeno 22. 9. 2026 na turnirko-nt.si.
                .headers(glave -> glave.httpStrictTransportSecurity(hsts -> hsts.disable()))
                // blokada po prevec neuspelih prijavah - pred preverbo gesla
                .addFilterBefore(new OmejitevPrijavFilter(omejevalnik), BasicAuthenticationFilter.class)
                .authorizeHttpRequests(dovoljenja -> dovoljenja
                        // predpregled (CORS) mora skozi brez prijave
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // poti brez prijave: registracija, vpis kode s poste (naslov,
                        // skrbnik), ponovno posiljanje kode in pozabljeno geslo. Racun
                        // nastane v stanju CAKA in sam po sebi ne da nobene pravice;
                        // edini dokaz na teh poteh je koda, ki jo je dobil lastnik naslova
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/registracija",
                                "/api/v1/auth/potrdi-eposto",
                                "/api/v1/auth/potrdi-skrbnika",
                                "/api/v1/auth/ponovno-poslji",
                                "/api/v1/auth/pozabljeno-geslo",
                                "/api/v1/auth/novo-geslo").permitAll()
                        // /auth/me sluzi za preverbo poverilnic - zahteva veljavno prijavo
                        .requestMatchers("/api/v1/auth/**").authenticated()
                        // zasebni del profila (analize in napoved tekme) vidi samo
                        // igralec sam ali administrator; lastnistvo preveri
                        // DostopDoProfila - veriga pozna samo vlogo, ne lastnistva
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/igralci/*/profil/zasebno",
                                "/api/v1/igralci/*/profil/napoved").authenticated()
                        // seznam racunov vsebuje e-poste, zato ni javen kljub temu, da je GET
                        .requestMatchers(HttpMethod.GET, "/api/v1/racuni/**").hasRole("ADMIN")
                        // uvoz iz Stupe (tudi branje): predogled nosi datume rojstva
                        // novih igralcev in kandidatov za istovetnost, zato je vsa pot
                        // samo administratorjeva - pravilo mora stati pred "GET je javen"
                        .requestMatchers("/api/v1/uvoz/**").hasRole("ADMIN")
                        // sifrant igralcev z osebnimi podatki (datum rojstva, e-posta,
                        // telefon, naslov, licenca). Organizator jih namenoma NE vidi:
                        // za vodenje tekmovanja zadosca javni izpis, skupni sifrant pa
                        // nosi podatke igralcev vseh klubov. Zato ADMIN, ne urejevalec.
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/igralci/podrobno", "/api/v1/igralci/*/podrobno")
                                .hasRole("ADMIN")
                        // izbor spremljanih lig je osebna nastavitev racuna:
                        // gost ga nima (svojega si zapomni brskalnik), vsak
                        // prijavljen pa sme brati IN spreminjati samo svojega -
                        // zato tu ni vloge, ampak zgolj prijava. Pravilo mora
                        // stati pred splosnim "GET je javen".
                        .requestMatchers("/api/v1/domov/moje-lige",
                                "/api/v1/domov/moje-lige/**").authenticated()
                        // gost sme brati vse ostalo
                        .requestMatchers(HttpMethod.GET, "/api/**").permitAll()
                        // --- od tu naprej samo mutacije (ne-GET) ---
                        // izbor lig za domaco stran je urednistvo in ne
                        // upravljanje tekmovanja: organizator sme svojo ligo,
                        // vhodna stran zveze pa ni njegova. Pravilo mora stati
                        // pred splosnim "/api/v1/lige/** sme tudi organizator".
                        .requestMatchers("/api/v1/lige/*/na-domaci").hasRole("ADMIN")
                        // organizator sme ustvarjati in upravljati turnirje in lige
                        // (na ravni zapisa lastnistvo preveri LastnistvoStoritev)
                        .requestMatchers("/api/v1/turnirji/**", "/api/v1/dogodki/**",
                                "/api/v1/tekme/**", "/api/v1/lige/**", "/api/v1/srecanja/**")
                                .hasAnyRole("ADMIN", "ORGANIZATOR")
                        // organizator sme dodati NOVEGA igralca v skupni sifrant
                        // (samo POST na koren); urejanje/brisanje/rating ostane adminu
                        .requestMatchers(HttpMethod.POST, "/api/v1/igralci").hasAnyRole("ADMIN", "ORGANIZATOR")
                        // vse ostale mutacije (igralci PUT/DELETE, klubi, kraji, racuni)
                        // sme samo administrator
                        .requestMatchers("/api/**").hasRole("ADMIN")
                        .anyRequest().permitAll())
                .httpBasic(basic -> basic.authenticationEntryPoint(vstopnaTocka))
                .exceptionHandling(obravnava -> obravnava.authenticationEntryPoint(vstopnaTocka));
        return http.build();
    }
}
