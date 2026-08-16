/* Prenos sifrantov stare strani NTZS v skupni zbirnik.

   Stara stran je pri imenih boljsi vir od Stupe: ime in priimek hrani LOCENO
   (pretvorba ju razbere iz dveh oblik istega imena) - zato tu ni ugibanja in
   se prek licence NTZS popravijo tudi imena, ki bi jih Stupa sama razdelila
   napacno. Manjka pa dan in mesec rojstva: vir objavi samo letnik, zato
   zapisemo 1. januar in to oznacimo (samoLetnik); ce ima isto osebo Stupa s
   pravim datumom, ta v zbirniku zmaga. */
package si.turnirko.uvoz.stara;

import java.time.LocalDate;

import com.fasterxml.jackson.databind.JsonNode;

import si.turnirko.uvoz.UvozOblike;
import si.turnirko.uvoz.UvozPorocilo;
import si.turnirko.uvoz.ZbirnikSifrantov;

public final class StaraZbirnik {

    private StaraZbirnik() {
    }

    public static void dodaj(StaraArhiv arhiv, ZbirnikSifrantov zbirnik, UvozPorocilo porocilo) {
        for (JsonNode k : arhiv.klubi()) {
            String slug = k.path("slug").asText(null);
            if (slug == null) {
                continue;
            }
            zbirnik.dodajKlub(ZbirnikSifrantov.kljuc(ZbirnikSifrantov.VIR_STARA, slug),
                    k.path("ime").asText(null), k.path("kratica").asText(null));
        }

        for (JsonNode i : arhiv.igralci()) {
            String id = i.path("id").asText(null);
            String polnoIme = UvozOblike.ocisti(i.path("polnoIme").asText(null));
            if (id == null || polnoIme == null) {
                porocilo.prestej("izpusceni igralci stare strani (brez imena)");
                continue;
            }

            Integer letnik = i.path("letnik").isInt() ? i.path("letnik").asInt() : null;
            LocalDate rojstvo = (letnik == null) ? null : LocalDate.of(letnik, 1, 1);
            String slugKluba = i.path("klub").asText(null);

            zbirnik.dodajIgralca(new ZbirnikSifrantov.SurovIgralec(
                    ZbirnikSifrantov.kljuc(ZbirnikSifrantov.VIR_STARA, id),
                    polnoIme,
                    UvozOblike.ocisti(i.path("ime").asText(null)),
                    UvozOblike.ocisti(i.path("priimek").asText(null)),
                    rojstvo,
                    rojstvo != null,
                    UvozOblike.ocisti(i.path("spol").asText(null)),
                    UvozOblike.ocisti(i.path("licenca").asText(null)),
                    UvozOblike.ocisti(i.path("drzava").asText(null)),
                    slugKluba == null || slugKluba.isBlank()
                            ? null : ZbirnikSifrantov.kljuc(ZbirnikSifrantov.VIR_STARA, slugKluba),
                    UvozOblike.datum(i.path("zadnjiNastop").asText(null))));
        }
    }
}
