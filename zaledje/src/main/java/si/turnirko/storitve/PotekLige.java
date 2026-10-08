/* Potek rednega dela lige: koliko kol je za nami in kdaj se igra naslednjic.

   ENO pravilo za vse, ki to izpisujejo - domaco stran, seznam lig, glavo
   strani lige in organizatorski pregled. Prej sta stran lige in seznam lig
   stela kolo za odigrano sele, ko je bilo koncano VSAKO njegovo srecanje,
   domaca stran pa po datumu; ob enem samem nevpisanem izidu je stran lige
   tedne trdila "0. od 6 kol · naslednje 24. 9." (datum je bil ze mimo), domaca
   stran pa za isto ligo "1. od 6 kol · naslednje 15. 10." Gledalec je dobil
   dva odgovora na isto vprasanje.

   Kolo je ODIGRANO, ko je njegov datum ze mimo (danasnji dan se ne steje:
   kolo je se "naslednje"). Datum kola je najzgodnejsi predvideni zacetek
   njegovih srecanj - isti kot v razporedu lige.

   Zakaj ne po koncanih srecanjih: ekipe se neuradno dogovorijo za menjavo
   terminov in odigrajo srecanje, ki spada v pozno kolo, ze zdaj; izid kaksnega
   srecanja pa obvisi nevpisan. Gledalec sprasuje, koliko sezone je za nami in
   kdaj se igra naslednjic - ne, ali je organizator vpisal vse zapisnike (to se
   vidi v razporedu, kjer srecanje brez izida ostane brez izida). Kolo brez
   datuma (organizator termina ni vpisal) datuma nima, zato je odigrano, ko je
   koncano vsako njegovo srecanje.

   Koncnica ni kolo rednega dela (njena "kola" so krogi serij) in v stevcu ne
   steje; v "naslednje" pa steje, ce jo klicatelj poda (domaca stran po rednem
   delu obljublja naslednjo tekmo serije). */
package si.turnirko.storitve;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import si.turnirko.modeli.StatusSrecanja;

public final class PotekLige {

    /* Dan je dan v dvorani: kolo ob 00.30 po poletnem casu je na strezniku v
       UTC se prejsnji dan. */
    private static final ZoneId SLOVENIJA = ZoneId.of("Europe/Ljubljana");

    /* Kar o srecanju potrebujemo: kolo, stanje, termin in ali je tekma
       koncnice. */
    public record Termin(int kolo, StatusSrecanja status, LocalDateTime zacetek, boolean koncnica) {}

    /* Naslednje kolo: njegova stevilka in dan (null, ce termina ni). */
    public record Naslednje(int kolo, LocalDate datum) {}

    /* odigranaKola so stevilke odigranih kol rednega dela, narascajoce -
       razpored po njih oznaci trak kol, glava pa jih presteje. */
    public record Potek(List<Integer> odigranaKola, int vseh, Naslednje naslednje) {
        public static final Potek PRAZEN = new Potek(List.of(), 0, null);

        public int odigranih() {
            return odigranaKola.size();
        }
    }

    private PotekLige() {}

    public static LocalDate danes() {
        return LocalDate.now(SLOVENIJA);
    }

    public static Potek izracunaj(List<Termin> srecanja, LocalDate danes) {
        Map<Integer, List<Termin>> poKolih = new TreeMap<>();
        for (Termin s : srecanja) {
            if (!s.koncnica()) {
                poKolih.computeIfAbsent(s.kolo(), k -> new ArrayList<>()).add(s);
            }
        }
        List<Integer> odigrana = new ArrayList<>();
        poKolih.forEach((kolo, vKolu) -> {
            LocalDate datumKola = vKolu.stream()
                    .map(PotekLige::datum)
                    .filter(Objects::nonNull)
                    .min(Comparator.naturalOrder())
                    .orElse(null);
            boolean odigrano = datumKola != null
                    ? datumKola.isBefore(danes)
                    : vKolu.stream().allMatch(s -> s.status() == StatusSrecanja.KONCANO);
            if (odigrano) {
                odigrana.add(kolo);
            }
        });
        return new Potek(List.copyOf(odigrana), poKolih.size(), naslednje(srecanja, danes));
    }

    /* "Naslednje kolo" je prvo nekoncano srecanje, ki ga termin se ni prehitel.
       Srecanje, ki je ostalo neodigrano (igralca sta se dogovorila za drug
       dan, izida ni nihce vpisal), bi sicer s starim datumom ostalo
       "naslednje" cele tedne, vrstica pa obljublja, kdaj se igra NASLEDNJIC.
       Danasnji dan se steje, srecanje brez termina ni ze mimo (ne vemo, da
       je), zato ostane kandidat.

       Srecanja gredo po kolu in zacetku, zato prvi kandidat ostane na mestu -
       razen ce za njim pride srecanje z datumom, ki je prej (prestavljeno
       srecanje starejsega kola je lahko pozneje od naslednjega). */
    private static Naslednje naslednje(List<Termin> srecanja, LocalDate danes) {
        List<Termin> poVrsti = srecanja.stream()
                .sorted(Comparator.comparingInt(Termin::kolo)
                        .thenComparing(Termin::zacetek,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        Termin izbran = null;
        for (Termin s : poVrsti) {
            if (s.status() == StatusSrecanja.KONCANO) {
                continue;
            }
            LocalDate datum = datum(s);
            if (datum != null && datum.isBefore(danes)) {
                continue;
            }
            LocalDate izbranDatum = izbran == null ? null : datum(izbran);
            if (izbran == null || (datum != null && izbranDatum != null && datum.isBefore(izbranDatum))) {
                izbran = s;
            }
        }
        return izbran == null ? null : new Naslednje(izbran.kolo(), datum(izbran));
    }

    private static LocalDate datum(Termin s) {
        return s.zacetek() == null ? null : s.zacetek().toLocalDate();
    }
}
