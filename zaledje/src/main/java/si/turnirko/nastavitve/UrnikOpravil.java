/* Urnik je vklopljen zaradi odbitka za neaktivnost (NeaktivnostStoritev):
   igralec, ki je nehal igrati, mora s casom pasti na lestvici tudi takrat,
   kadar nihce nicesar ne vnasa. Isti urnik poganja varovalko ratinga, cistilca
   racunov, obvestila po kolu in opomnike preizkusa.

   Ob ukazu "prenos" (SQLite -> PostgreSQL) urnika ni: opravila bi brala
   polovico prenesene baze, obvestila po kolu pa bi lahko poslala pošto
   na podlagi podatkov, ki se še niso potrdili. */
package si.turnirko.nastavitve;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

import si.turnirko.prenos.PrenosUkaz;

@Configuration
@EnableScheduling
@Profile("!" + PrenosUkaz.PROFIL)
public class UrnikOpravil {
}
