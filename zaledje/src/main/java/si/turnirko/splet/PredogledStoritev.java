/* Besedilo predogleda za posamezno stran (PredogledStrani): naslov in en
   stavek opisa, kakrsna se pokazeta v WhatsAppu ob povezavi in v zavihku
   brskalnika. Samo javni podatki, ki jih stran ze tako kaze vsakemu gostu.

   Zapis, ki ga ni (izbrisan ali napacen id), vrne prazno - stran dobi
   splosni predogled iz index.html, aplikacija pa nato sama pove, da zapisa
   ni. */
package si.turnirko.splet;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.LigaDto;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.modeli.Ekipa;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Liga;
import si.turnirko.modeli.RatingStanje;
import si.turnirko.modeli.Srecanje;
import si.turnirko.modeli.StatusSrecanja;
import si.turnirko.modeli.StatusTekmovanja;
import si.turnirko.modeli.Turnir;
import si.turnirko.repozitoriji.DogodekRepozitorij;
import si.turnirko.repozitoriji.EkipaRepozitorij;
import si.turnirko.repozitoriji.IgralecRepozitorij;
import si.turnirko.repozitoriji.RatingStanjeRepozitorij;
import si.turnirko.repozitoriji.SrecanjeRepozitorij;
import si.turnirko.repozitoriji.TurnirRepozitorij;
import si.turnirko.splet.PredogledStrani.Predogled;
import si.turnirko.storitve.LigaStoritev;

@Service
public class PredogledStoritev {

    private final LigaStoritev ligaStoritev;
    private final EkipaRepozitorij ekipaRepozitorij;
    private final TurnirRepozitorij turnirRepozitorij;
    private final DogodekRepozitorij dogodekRepozitorij;
    private final SrecanjeRepozitorij srecanjeRepozitorij;
    private final IgralecRepozitorij igralecRepozitorij;
    private final RatingStanjeRepozitorij stanjeRepozitorij;

    public PredogledStoritev(LigaStoritev ligaStoritev,
                             EkipaRepozitorij ekipaRepozitorij,
                             TurnirRepozitorij turnirRepozitorij,
                             DogodekRepozitorij dogodekRepozitorij,
                             SrecanjeRepozitorij srecanjeRepozitorij,
                             IgralecRepozitorij igralecRepozitorij,
                             RatingStanjeRepozitorij stanjeRepozitorij) {
        this.ligaStoritev = ligaStoritev;
        this.ekipaRepozitorij = ekipaRepozitorij;
        this.turnirRepozitorij = turnirRepozitorij;
        this.dogodekRepozitorij = dogodekRepozitorij;
        this.srecanjeRepozitorij = srecanjeRepozitorij;
        this.igralecRepozitorij = igralecRepozitorij;
        this.stanjeRepozitorij = stanjeRepozitorij;
    }

    /* Stran lige. Z ?ekipa= (okno ekipe - to si igralci delijo v skupini
       ekipe) je naslov ekipa, opis pa pove, kaj okno nosi. */
    @Transactional(readOnly = true)
    public Optional<Predogled> liga(Long id, Long idEkipa) {
        LigaDto liga;
        try {
            liga = ligaStoritev.najdi(id);
        } catch (NiNajdenoIzjema e) {
            return Optional.empty();
        }
        String pot = "/lige/" + id;
        String imeLige = imeLige(liga.ime(), liga.sezona());
        if (idEkipa != null) {
            Optional<Ekipa> ekipa = ekipaRepozitorij.findById(idEkipa)
                    .filter(e -> e.getLiga() != null && id.equals(e.getLiga().getId()));
            if (ekipa.isPresent()) {
                return Optional.of(new Predogled(
                        ekipa.get().prikazanoIme() + " · " + liga.ime(),
                        "Kader, naslednje srečanje in vsi izidi ekipe · " + imeLige,
                        pot + "?ekipa=" + idEkipa, pot));
            }
        }
        List<String> deli = new ArrayList<>();
        deli.add(liga.status() == StatusTekmovanja.ZAKLJUCEN
                ? "Končna lestvica in vsi izidi"
                : "Lestvica, razpored in izidi");
        if (liga.steviloEkip() > 0) {
            deli.add(liga.steviloEkip() + " " + ekip(liga.steviloEkip()));
        }
        if (liga.status() == StatusTekmovanja.V_TEKU && liga.steviloKol() > 0) {
            deli.add("odigrano " + liga.odigranihKol() + " od " + liga.steviloKol()
                    + (liga.steviloKol() == 1 ? " kola" : " kol"));
            if (liga.naslednje() != null && liga.naslednje().datum() != null) {
                deli.add("naslednje kolo " + datum(liga.naslednje().datum()));
            }
        }
        return Optional.of(new Predogled(imeLige, String.join(" · ", deli), pot, pot));
    }

    @Transactional(readOnly = true)
    public Optional<Predogled> turnir(Long id) {
        return turnirRepozitorij.findById(id).map(t -> {
            String pot = "/turnirji/" + id;
            return new Predogled(t.getIme(),
                    zDeli(obdobjeInKraj(t), "izidi, skupine, mreže in uvrstitve"), pot, pot);
        });
    }

    /* Kategorija na turnirju (dogodek): naslov nosi oboje, ker je ime
       kategorije samo ("Moški U19") brez turnirja v predogledu brez pomena. */
    @Transactional(readOnly = true)
    public Optional<Predogled> dogodek(Long id) {
        return dogodekRepozitorij.findById(id).map(d -> {
            String pot = "/dogodki/" + id;
            Turnir t = d.getTurnir();
            return new Predogled(d.getIme() + " · " + t.getIme(),
                    zDeli(obdobjeInKraj(t), "izidi, mreža in uvrstitve kategorije"), pot, pot);
        });
    }

    /* Ligasko srecanje: koncano z izidom v naslovu (to se deli po vecerih),
       sicer kdo s kom in kdaj. */
    @Transactional(readOnly = true)
    public Optional<Predogled> srecanje(Long id) {
        return srecanjeRepozitorij.findById(id).map(s -> {
            String pot = "/srecanja/" + id;
            String domaci = imeEkipe(s.getEkipaDomaci());
            String gost = imeEkipe(s.getEkipaGost());
            boolean koncano = s.getStatus() == StatusSrecanja.KONCANO;
            String naslov = koncano
                    ? domaci + " " + s.getDobljeneDomaci() + " : " + s.getDobljeneGost() + " " + gost
                    : domaci + " – " + gost;
            Liga liga = s.getLiga();
            List<String> deli = new ArrayList<>();
            if (liga != null) {
                deli.add(imeLige(liga.getIme(), liga.getSezona()));
            }
            deli.add(s.getKolo() + ". kolo");
            if (s.getPredvidenZacetek() != null) {
                deli.add(termin(s.getPredvidenZacetek()));
            }
            deli.add(koncano ? "zapisnik srečanja" : "postavi in izidi");
            return new Predogled(naslov, String.join(" · ", deli), pot, pot);
        });
    }

    /* Profil igralca: ime, Turnirko rating in klub - isto, kar profil kaze
       vsakemu gostu. */
    @Transactional(readOnly = true)
    public Optional<Predogled> igralec(Long id) {
        return igralecRepozitorij.findById(id).map(i -> {
            String pot = "/igralci/" + id + "/profil";
            List<String> deli = new ArrayList<>();
            stanjeRepozitorij.findByIgralecIdAndSistem(id, RatingStanje.SISTEM_TURNIRKO)
                    .ifPresent(r -> deli.add("Turnirko rating " + r.getVrednost()));
            if (i.getKlub() != null) {
                deli.add(i.getKlub().getIme());
            }
            deli.add("tekme in statistika");
            return new Predogled(polnoIme(i), String.join(" · ", deli), pot, pot);
        });
    }

    /* Strani brez zapisa (seznami, lestvica, razlage). */
    public static Optional<Predogled> stalna(String pot) {
        String[] besedilo = switch (pot) {
            case "/lige" -> new String[] {"Lige",
                    "Lestvice, razporedi in izidi namiznoteniških lig."};
            case "/turnirji" -> new String[] {"Turnirji",
                    "Turnirji namiznega tenisa: izidi v živo, skupine, mreže in uvrstitve."};
            case "/lestvica" -> new String[] {"Lestvica igralcev",
                    "Turnirko rating igralcev namiznega tenisa: člani, mladinci, veterani in rekreativci."};
            case "/koledar" -> new String[] {"Koledar tekmovanj",
                    "Turnirji in ligaška kola po dnevih na enem mestu."};
            case "/o-ratingu" -> new String[] {"Kako se računa Turnirko rating",
                    "Pravila Turnirko ratinga z razlago in preizkusom: zakaj rating zraste, pade ali po prvem dnevu skoči."};
            case "/pogoji" -> new String[] {"Pogoji uporabe",
                    "Pogoji uporabe Turnirka, naročnine in varstvo osebnih podatkov."};
            case "/dvoboj" -> new String[] {"1 na 1",
                    "Medsebojni izidi dveh igralcev namiznega tenisa prek vseh tekmovanj."};
            default -> null;
        };
        return besedilo == null
                ? Optional.empty()
                : Optional.of(new Predogled(besedilo[0], besedilo[1], pot, pot));
    }

    // ---------- zapis ----------

    /* Ime lige s sezono, kot ga kaze glava strani (ime zgoraj, sezona pod
       njim) - v eni vrsti locena s piko. */
    static String imeLige(String ime, String sezona) {
        return sezona == null || sezona.isBlank() ? ime : ime + " · " + sezona;
    }

    private static String imeEkipe(Ekipa e) {
        return e == null ? "prosto" : e.prikazanoIme();
    }

    private static String polnoIme(Igralec i) {
        return (i.getIme() + " " + i.getPriimek()).trim();
    }

    private static String obdobjeInKraj(Turnir t) {
        List<String> deli = new ArrayList<>();
        if (t.getDatumZacetka() != null) {
            deli.add(t.getDatumKonca() != null && !t.getDatumKonca().equals(t.getDatumZacetka())
                    ? datum(t.getDatumZacetka()) + "–" + datum(t.getDatumKonca())
                    : datum(t.getDatumZacetka()));
        }
        if (t.getKraj() != null) {
            deli.add(t.getKraj().getIme());
        }
        return String.join(" · ", deli);
    }

    private static String zDeli(String prvi, String drugi) {
        return prvi.isEmpty() ? zVelikoZacetnico(drugi) : prvi + " · " + drugi;
    }

    private static String zVelikoZacetnico(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /* Slovenski zapis datuma: "3. 10. 2026". */
    static String datum(LocalDate d) {
        return d.getDayOfMonth() + ". " + d.getMonthValue() + ". " + d.getYear();
    }

    /* "15. 10. 2026 ob 18.30"; ura 00:00 pomeni, da ura ni dolocena. */
    static String termin(LocalDateTime t) {
        String dan = datum(t.toLocalDate());
        if (t.getHour() == 0 && t.getMinute() == 0) {
            return dan;
        }
        return dan + " ob " + t.getHour() + "." + String.format("%02d", t.getMinute());
    }

    /* 1 ekipa, 2 ekipi, 3/4 ekipe, 5+ ekip (z dvomestnimi izjemami 11-14). */
    static String ekip(int n) {
        int zadnji = n % 100;
        if (zadnji == 1) return "ekipa";
        if (zadnji == 2) return "ekipi";
        if (zadnji == 3 || zadnji == 4) return "ekipe";
        return "ekip";
    }
}
