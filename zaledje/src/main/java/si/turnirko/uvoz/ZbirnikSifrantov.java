/* Zbiranje surovih zapisov o klubih in igralcih iz VSEH virov uvoza.

   Zakaj cez cel posnetek in ne po dogodkih sproti: isti igralec nastopa v
   desetih dogodkih in v vsakem je zapisan znova. Sifrant mora nastati enkrat,
   preden se uvozi prvi turnir, sicer bi ista oseba dobila deset zapisov.

   Zakaj SKUPEN zbirnik za oba vira: zgodovina NTZS je razdeljena na dvoje -
   stara stran (sezone 2012-13 do 2023-24) in Stupa Events (od 2024-25) - ista
   oseba pa nastopa v obeh. Ce bi vsak vir postavil svoj sifrant, bi se igralcu
   zgodovina in ELO razdelila na dva profila. Zato je kljuc zapisa OZNACEN Z
   VIROM ("stara:3300", "stupa:18896"), zdruzevanje oseb pa tece po licenci
   NTZS, ki jo imata oba vira v isti obliki ("059/15/16").

   Pri Stupi se igralci pojavljajo na DVEH mestih in obe je treba prebrati:
    * med udelezenci dogodka (turnirji - tam so posamicne prijave),
    * v podrobnostih udelezencev tekem (LIGE - tam ekipa nastopa kot udelezenec,
      posamezni igralci pa so sele v tekmah srecanja).
   Ce bi brali samo prvo, bi manjkali vsi, ki igrajo izkljucno SNTL.

   Enolicni kljuc igralca je pri Stupi user_role_id (v seznamu udelezencev se
   isto stevilo imenuje ref_id), pri stari strani pa njen id igralca. */
package si.turnirko.uvoz;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

public class ZbirnikSifrantov {

    /* Oznaki virov v kljucih. Vira se ne meSata: isti stevilki v Stupi in na
       stari strani pripadata razlicnima osebama. */
    public static final String VIR_STUPA = "stupa";
    public static final String VIR_STARA = "stara";

    public static String kljuc(String vir, Object id) {
        return vir + ":" + id;
    }

    /* Klub. Kljuc je pri Stupi parent_user_role_id vloge "Club", pri stari
       strani oznaka kluba v naslovu (npr. "ntk_arrigoni"). */
    public record SurovKlub(String id, String ime, String kratica) {}

    /* Igralec.

       "ime"/"priimek" sta izpolnjena samo, kadar ju vir hrani LOCENO - stara
       stran ju ima (atribut data-name je "Priimek Ime", besedilo povezave "Ime
       Priimek", iz obojega je razdelitev izracunljiva), Stupa pa ne. Kjer ju
       ni, razdelitev prevzame RazdelitevImena.

       "samoLetnik" pove, da datum rojstva ni pravi datum, ampak 1. januar
       letnika - stara stran dneva in meseca ne objavi. Ob zdruzevanju po
       licenci tak zapis izgubi proti pravemu datumu iz Stupe.

       "zadnjiNastop" sluzi za izbiro kluba: igralec med sezonami prestopa,
       zato v sifrant zapisemo klub iz NAJNOVEJSEGA nastopa. */
    public record SurovIgralec(String id, String polnoIme, String ime, String priimek,
                               LocalDate rojstvo, boolean samoLetnik, String spol,
                               String licenca, String drzava, String idKluba,
                               LocalDate zadnjiNastop) {}

    private final Map<String, SurovKlub> klubi = new LinkedHashMap<>();
    private final Map<String, SurovIgralec> igralci = new LinkedHashMap<>();
    private final Map<String, String> klubPoImenu = new HashMap<>();

    public Map<String, SurovKlub> klubi() {
        return klubi;
    }

    public Map<String, SurovIgralec> igralci() {
        return igralci;
    }

    /* ---------- splosni vhod (uporablja ga uvoz stare strani) ---------- */

    public void dodajKlub(String id, String ime, String kratica) {
        if (id == null || ime == null || ime.isBlank()) {
            return;
        }
        klubi.put(id, new SurovKlub(id, ime.trim(), prazenVNull(kratica)));
    }

    public void dodajIgralca(SurovIgralec nov) {
        if (nov.id() == null || nov.polnoIme() == null || nov.polnoIme().isBlank()) {
            return;
        }
        SurovIgralec obstojec = igralci.get(nov.id());
        igralci.put(nov.id(), obstojec == null ? nov : zdruzi(obstojec, nov));
    }

    /* ---------- vhod iz Stupe ---------- */

    /* Prebere en dogodek Stupe: udelezence in vse tekme (skupaj s podtekmami). */
    public void dodajDogodek(StupaArhiv arhiv, JsonNode dogodek, UvozPorocilo porocilo) {
        LocalDate datum = UvozOblike.datum(dogodek.path("event_start_date").asText(null));

        for (JsonNode u : arhiv.udelezenci(dogodek.path("id").asLong())) {
            if (u.path("participant_type_id").asInt() != 1) {
                continue; // dvojice in ekipe niso osebe
            }
            dodajIzStupe(u.path("ref_id").asLong(0), u.path("participant_name").asText(null),
                    u.path("meta_data"), spolIzPrijave(u.path("event_participant_details")),
                    klubIz(u.path("event_participant_details")), datum, porocilo);
        }

        for (JsonNode t : arhiv.tekme(dogodek.path("id").asLong())) {
            dodajIzTekme(t, datum, porocilo);
            for (JsonNode pod : t.path("sub_matches")) {
                dodajIzTekme(pod, datum, porocilo);
            }
        }
    }

    private void dodajIzTekme(JsonNode tekma, LocalDate datum, UvozPorocilo porocilo) {
        for (JsonNode udelezenec : tekma.path("participants")) {
            for (JsonNode pd : udelezenec.path("participant_details")) {
                dodajIzStupe(pd.path("user_role_id").asLong(0), pd.path("name").asText(null),
                        pd.path("meta_data"), pd.path("gender_id").asInt(0),
                        klubIzPodrobnosti(pd), datum, porocilo);
            }
        }
    }

    /* Spol iz zapisa prijave (gender_id 1/2). Rezerva za redke igralce, ki
       imajo meta_data brez polja gender - brez spola jih shema ne sprejme in
       z njimi bi odpadle tudi vse njihove tekme. */
    private int spolIzPrijave(JsonNode podrobnosti) {
        for (JsonNode pd : podrobnosti) {
            int spol = pd.path("gender_id").asInt(0);
            if (spol > 0) {
                return spol;
            }
        }
        return 0;
    }

    private void dodajIzStupe(long id, String polnoIme, JsonNode meta, int spolIzPrijave,
                              String idKluba, LocalDate datum, UvozPorocilo porocilo) {
        if (id == 0 || polnoIme == null || polnoIme.isBlank()) {
            return;
        }
        String spol = meta.path("gender").asText(null);
        if (UvozOblike.ocisti(spol) == null && spolIzPrijave > 0) {
            spol = (spolIzPrijave == 1) ? "Male" : "Female";
        }
        dodajIgralca(new SurovIgralec(
                kljuc(VIR_STUPA, id), polnoIme.trim(), null, null,
                UvozOblike.datum(meta.path("dob").asText(null)), false, spol,
                prazenVNull(meta.path("license_id").asText(null)),
                meta.path("country").asText(null), idKluba, datum));
    }

    /* Klub iz seznama udelezencev dogodka. */
    private String klubIz(JsonNode podrobnosti) {
        for (JsonNode pd : podrobnosti) {
            String id = klubIzPodrobnosti(pd);
            if (id != null) {
                return id;
            }
        }
        return null;
    }

    /* Klub iz enega zapisa participant_details; vlogo prepoznamo po role_id 6
       ("Club"), ker so med starsi tudi zveza in reprezentanca. */
    private String klubIzPodrobnosti(JsonNode pd) {
        for (JsonNode s : pd.path("parent_details")) {
            if (s.path("role_id").asInt() != 6) {
                continue;
            }
            String ime = s.path("parent_name").asText(null);
            if (ime == null || ime.isBlank()) {
                continue;
            }
            long stevilka = s.path("parent_user_role_id").asLong(0);
            String id = (stevilka != 0)
                    ? kljuc(VIR_STUPA, stevilka)
                    // brez id-ja se lahko naslonimo le na ime
                    : klubPoImenu.computeIfAbsent(ime,
                            k -> kljuc(VIR_STUPA, "ime-" + (klubPoImenu.size() + 1)));
            klubi.computeIfAbsent(id, k -> new SurovKlub(k, ime.trim(),
                    prazenVNull(s.path("parent_meta").path("Abbreviation").asText(null))));
            return id;
        }
        return null;
    }

    /* ---------- zdruzevanje dveh zapisov istega kljuca ---------- */

    private SurovIgralec zdruzi(SurovIgralec obstojec, SurovIgralec nov) {
        boolean novejsi = nov.zadnjiNastop() != null
                && (obstojec.zadnjiNastop() == null || nov.zadnjiNastop().isAfter(obstojec.zadnjiNastop()));

        // Popolnejsi zapis imena zmaga: isto osebo Stupa ponekod vodi samo s
        // priimkom ("Ecsy"), drugod s polnim imenom ("Ecsy Samuel"). Enobesedno
        // ime shema zavrne, zato bi tak zapis odnesel s sabo vse njegove tekme.
        String polno = obstojec.polnoIme();
        if (steviloBesed(polno) < 2 && steviloBesed(nov.polnoIme()) >= 2) {
            polno = nov.polnoIme();
        }
        // Pravi datum rojstva premaga 1. januar iz letnika.
        boolean vzemiRojstvoNovega = obstojec.rojstvo() == null
                || (obstojec.samoLetnik() && nov.rojstvo() != null && !nov.samoLetnik());

        return new SurovIgralec(
                obstojec.id(),
                polno,
                obstojec.ime() != null ? obstojec.ime() : nov.ime(),
                obstojec.priimek() != null ? obstojec.priimek() : nov.priimek(),
                vzemiRojstvoNovega ? nov.rojstvo() : obstojec.rojstvo(),
                vzemiRojstvoNovega ? nov.samoLetnik() : obstojec.samoLetnik(),
                obstojec.spol() != null ? obstojec.spol() : nov.spol(),
                obstojec.licenca() != null ? obstojec.licenca() : nov.licenca(),
                obstojec.drzava() != null ? obstojec.drzava() : nov.drzava(),
                (novejsi && nov.idKluba() != null) ? nov.idKluba() : obstojec.idKluba(),
                novejsi ? nov.zadnjiNastop() : obstojec.zadnjiNastop());
    }

    private static int steviloBesed(String v) {
        return (v == null || v.isBlank()) ? 0 : v.trim().split("\\s+").length;
    }

    private static String prazenVNull(String v) {
        return (v == null || v.isBlank()) ? null : v.trim();
    }
}
