/* Strezenje zgrajenega vmesnika iz istega procesa kot API.

   Vmesnik je enostranska aplikacija: pot /turnirji/1 obstaja samo v
   brskalniku, na strezniku pa ne. Ce gledalec tako stran osvezi ali dobi
   povezavo od nekoga, streznik zanjo nima datoteke in bi vrnil 404. Zato
   vsako pot, ki ni prava datoteka, postrezemo z index.html - usmerjanje
   nato prevzame React.

   Zakaj resource resolver in ne "forward: /index.html" na neki vzorec poti:
   vzorec je treba rocno drzati stran od /api in od datotek s pripono, kar
   se ob prvi novi koncni tocki pozabi. Tu je pravilo obrnjeno in zato
   varno - najprej pogledamo, ali datoteka res obstaja.

   Ce vmesnik ni vgrajen (obicajen razvojni prevod brez profila "splet",
   kjer vmesnik tece na Vite na vratih 5173), se ne zgodi nic: mape static
   ni, index.html ne obstaja in odgovor ostane 404. */
package si.turnirko.nastavitve;

import java.io.IOException;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

@Configuration
public class SpletniVmesnik implements WebMvcConfigurer {

    private static final String IZHODISCE = "classpath:/static/";
    private static final ClassPathResource ZACETNA = new ClassPathResource("static/index.html");

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry register) {
        register.addResourceHandler("/**")
                .addResourceLocations(IZHODISCE)
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String pot, Resource lokacija) throws IOException {
                        Resource datoteka = lokacija.createRelative(pot);
                        if (datoteka.exists() && datoteka.isReadable()) {
                            return datoteka;
                        }
                        /* Neznana pot pod /api je napaka API-ja in mora ostati
                           404 v JSON, ne pa tiho vrniti HTML - sicer odjemalec
                           napacno pot razume kot uspesen odgovor. */
                        if (pot.startsWith("api/")) {
                            return null;
                        }
                        return ZACETNA.exists() ? ZACETNA : null;
                    }
                });
    }
}
