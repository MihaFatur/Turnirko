/* Kdo je oseba iz Stupe v registru Turnirka - in kateri klub je njen klub.

   Lestev odlocanja (prva stopnica, ki odloci, obvelja):
    1. odlocitev admina za to osebo (povezi z igralcem / ustvari novega),
    2. zunanja povezava (oseba je ze bila uvozena),
    3. licenca NTZS - a SAMO skupaj z datumom rojstva in spolom. Licenca ni
       kljuc osebe: v Stupi si 047/25/26 delita David Molnar in Luka Rajh,
       049/24/25 pa Eva Sarlah in Sida Narancsik Smotlak. Datum se ujema tudi,
       ce ima igralec v bazi le 1. januar letnika (stara stran NTZS dneva in
       meseca ne objavi) - takrat se dopolni s pravim datumom iz Stupe,
    4. isto ime (brez sumnikov in vrstnega reda) in datum rojstva - to je
       le PREDLOG: pri strogem nacinu odloci admin, pri samodejnem (zgodovinski
       uvoz v prazno bazo) se poveze, ce je kandidat en sam,
    5. nov igralec: ime in priimek razdeli RazdelitevImena ob znanju celotnega
       registra, nezanesljiva razdelitev gre v porocilo.

   Tihe zdruzitve ni nikoli: neujemanje licence z datumom rojstva je pri
   strogem nacinu odlocitev, pri samodejnem nov igralec (licenca ostane pri
   prvem, shema jo drzi enolicno).

   Stupa hrani podatke osebe pri VSAKI prijavi posebej in ne v enem profilu:
   isti igralec je v enem dogodku brez datuma rojstva, v drugem z njim, ime pa
   organizatorji sproti popravljajo ("Ecsy" -> "Ecsy Samuel" -> "Samuel
   Ecsy"). Zgodovinski uvoz zato poda ZNANE OSEBE iz vseh posnetkov
   (ZnaneOsebeStupe) in manjkajoce podatke vzame od tam. Sinhronizacija enega
   dogodka tega nima - osebo brez podatkov, ki jih nov igralec potrebuje (ime
   in priimek, datum rojstva, spol), da v odlocitev, admin pa jih vpise ali
   izbere obstojecega igralca.

   Klub se poveze po id-ju vloge kluba pri Stupi, sicer po poenostavljenem
   imenu (brez vrste drustva in sumnikov), sicer nastane nov. */
package si.turnirko.uvoz.stupa;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

import si.turnirko.dto.PorociloUvozaDto;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Klub;
import si.turnirko.modeli.Spol;
import si.turnirko.modeli.VirTekmovanja;
import si.turnirko.modeli.ZunanjaPovezava;
import si.turnirko.repozitoriji.IgralecRepozitorij;
import si.turnirko.repozitoriji.KaderEkipeRepozitorij;
import si.turnirko.repozitoriji.KlubRepozitorij;
import si.turnirko.repozitoriji.PrijavaRepozitorij;
import si.turnirko.repozitoriji.ZunanjaPovezavaRepozitorij;
import si.turnirko.uvoz.RazdelitevImena;
import si.turnirko.uvoz.UvozOblike;

public class IdentitetaStupe {

    public enum Nacin {
        /* sinhronizacija med sezono: vsak dvom je odlocitev admina */
        STROGO,
        /* zgodovinski uvoz: dvom se razresi varno (nova oseba) in zabelezi */
        SAMODEJNO
    }

    /* Oseba, kot jo zapise Stupa (en zapis participant_details). */
    public record Oseba(long id, String polnoIme, LocalDate rojstvo, Spol spol, String licenca,
                        String drzava, KlubStupe klub) {}

    public record KlubStupe(long id, String ime, String kratica) {}

    /* Odlocitev admina za eno osebo: idIgralec > 0 poveze z igralcem registra,
       0 ustvari novega. Pri novem igralcu smejo biti podani podatki, ki jih
       vir nima ali jih ima narobe (ime in priimek, datum rojstva, spol) -
       podani povozijo vir. */
    public record Odlocitev(long idIgralec, String ime, String priimek, LocalDate rojstvo, Spol spol) {
        public static Odlocitev povezi(long idIgralec) {
            return new Odlocitev(idIgralec, null, null, null, null);
        }

        public static Odlocitev nov() {
            return new Odlocitev(0, null, null, null, null);
        }
    }

    /* Kaj nov igralec potrebuje, pa ga vir za osebo nima. */
    public static final String MANJKA_IME = "IME";
    public static final String MANJKA_ROJSTVO = "ROJSTVO";
    public static final String MANJKA_SPOL = "SPOL";

    private final Nacin nacin;
    private final Map<Long, Odlocitev> odlocitve;
    private final IgralecRepozitorij igralecRepozitorij;
    private final KlubRepozitorij klubRepozitorij;
    private final PrijavaRepozitorij prijavaRepozitorij;
    private final KaderEkipeRepozitorij kaderRepozitorij;
    private final ZunanjaPovezavaRepozitorij povezave;
    private final PorociloUvoza porocilo;
    private Map<Long, Oseba> znaneOsebe = Map.of();

    private final Map<Long, Igralec> poOsebi = new HashMap<>();
    private final Map<String, Klub> klubi = new HashMap<>();
    private Map<String, Klub> klubiPoImenu;
    private RazdelitevImena razdelitev;
    private final List<String> imenaDogodka = new ArrayList<>();

    public IdentitetaStupe(Nacin nacin, Map<Long, Odlocitev> odlocitve, IgralecRepozitorij igralecRepozitorij,
                           KlubRepozitorij klubRepozitorij, PrijavaRepozitorij prijavaRepozitorij,
                           KaderEkipeRepozitorij kaderRepozitorij, ZunanjaPovezavaRepozitorij povezave,
                           PorociloUvoza porocilo) {
        this.nacin = nacin;
        this.odlocitve = odlocitve == null ? Map.of() : odlocitve;
        this.igralecRepozitorij = igralecRepozitorij;
        this.klubRepozitorij = klubRepozitorij;
        this.prijavaRepozitorij = prijavaRepozitorij;
        this.kaderRepozitorij = kaderRepozitorij;
        this.povezave = povezave;
        this.porocilo = porocilo;
    }

    /* Podatki o osebah iz vseh posnetkov (zgodovinski uvoz), glej uvod. */
    public void upostevajZnaneOsebe(Map<Long, Oseba> znane) {
        this.znaneOsebe = znane == null ? Map.of() : znane;
    }

    // ---------- Branje oseb iz Stupe ----------

    /* Oseba iz zapisa participant_details / event_participant_details. */
    public static Oseba oseba(JsonNode pd) {
        JsonNode meta = pd.path("meta_data");
        Spol spol = UvozOblike.spol(meta.path("gender").asText(null), pd.path("gender_id").asInt(0));
        String ime = UvozOblike.ocisti(pd.path("name").asText(null));
        return new Oseba(pd.path("user_role_id").asLong(0),
                ime == null ? null : ime.replaceAll("\\s+", " "),
                UvozOblike.datum(meta.path("dob").asText(null)),
                spol,
                UvozOblike.ocisti(meta.path("license_id").asText(null)),
                UvozOblike.ocisti(meta.path("country").asText(null)),
                klub(pd.path("parent_details")));
    }

    /* Klub iz seznama starsev (vloga "Club", role_id 6). */
    public static KlubStupe klub(JsonNode starsi) {
        for (JsonNode s : starsi) {
            if (s.path("role_id").asInt() != 6) {
                continue;
            }
            String ime = UvozOblike.ocisti(s.path("parent_name").asText(null));
            if (ime == null) {
                continue;
            }
            JsonNode meta = s.has("parent_meta") ? s.path("parent_meta") : s.path("meta");
            return new KlubStupe(s.path("parent_user_role_id").asLong(0), ime,
                    UvozOblike.ocisti(meta.path("Abbreviation").asText(null)));
        }
        return null;
    }

    /* Imena vseh oseb dogodka - razdelitev imena potrebuje celoten nabor
       naenkrat (signal pogostosti), zato jih preslikava prijavi vnaprej. */
    public void najaviImena(List<String> imena) {
        imenaDogodka.addAll(imena.stream().filter(Objects::nonNull).toList());
        razdelitev = null;
    }

    // ---------- Igralec ----------

    /* Igralec za osebo; null, ce osebe ni mogoce zapisati (brez datuma rojstva
       ali spola) - to je v porocilu. datum je dan nastopa (za klub igralca). */
    public Igralec igralec(Oseba o, LocalDate datum) {
        if (o == null || o.id() == 0) {
            return null;
        }
        Igralec znan = poOsebi.get(o.id());
        if (znan != null) {
            return znan;
        }
        // oseba, ki je ni mogoce zapisati, se v porocilu ne ponovi pri vsaki tekmi
        if (poOsebi.containsKey(o.id())) {
            return null;
        }
        Igralec i = razresi(dopolni(o), datum);
        poOsebi.put(o.id(), i);
        return i;
    }

    /* Oseba, dopolnjena s tem, kar vir o njej ve iz drugih dogodkov (glej
       uvod): manjkajoci datum rojstva, spol, licenca in drzava ter ime iz
       najnovejse prijave, ki ima ime IN priimek. */
    private Oseba dopolni(Oseba o) {
        Oseba z = znaneOsebe.get(o.id());
        if (z == null) {
            return o;
        }
        String ime = z.polnoIme() != null && besed(z.polnoIme()) >= 2 ? z.polnoIme() : o.polnoIme();
        return new Oseba(o.id(), ime,
                o.rojstvo() != null ? o.rojstvo() : z.rojstvo(),
                o.spol() != null ? o.spol() : z.spol(),
                o.licenca() != null ? o.licenca() : z.licenca(),
                o.drzava() != null ? o.drzava() : z.drzava(),
                o.klub());
    }

    static int besed(String ime) {
        return ime == null ? 0 : (int) Arrays.stream(ime.trim().split("\\s+")).filter(b -> !b.isEmpty()).count();
    }

    private Igralec razresi(Oseba o, LocalDate datum) {
        Odlocitev odlocitev = odlocitve.get(o.id());
        if (odlocitev != null) {
            if (odlocitev.idIgralec() > 0) {
                Optional<Igralec> izbran = igralecRepozitorij.findById(odlocitev.idIgralec());
                if (izbran.isEmpty()) {
                    porocilo.napaka("odlocitev kaze na igralca, ki ne obstaja", opis(o) + " -> id " + odlocitev.idIgralec());
                    return null;
                }
                povezi(o, izbran.get());
                osvezi(izbran.get(), o, datum);
                return izbran.get();
            }
            return ustvari(o, datum, odlocitev);
        }

        Optional<ZunanjaPovezava> povezava = povezave.findByVirAndVrstaAndZunanjiId(
                VirTekmovanja.STUPA, ZunanjaPovezava.Vrsta.IGRALEC, String.valueOf(o.id()));
        if (povezava.isPresent()) {
            Optional<Igralec> povezan = igralecRepozitorij.findById(povezava.get().getIdLokalni());
            if (povezan.isPresent()) {
                osvezi(povezan.get(), o, datum);
                return povezan.get();
            }
        }

        if (o.licenca() != null) {
            Optional<Igralec> poLicenci = igralecRepozitorij.findByNtzsLicenca(o.licenca());
            if (poLicenci.isPresent()) {
                Igralec i = poLicenci.get();
                if (istaOseba(i, o)) {
                    povezi(o, i);
                    osvezi(i, o, datum);
                    return i;
                }
                if (nacin == Nacin.STROGO) {
                    odlocitev(o, List.of(i), "Licenca " + o.licenca() + " v registru pripada igralcu"
                            + " z drugim datumom rojstva ali spolom.");
                    return ustvari(o, datum, null);
                }
                porocilo.opozori("licenca NTZS pripada drugi osebi (nov igralec brez licence)",
                        opis(o) + " / v registru: " + i.polnoIme() + " r. " + i.getDatumRojstva());
                return ustvari(o, datum, null);
            }
        }

        List<Igralec> kandidati = kandidati(o);
        if (!kandidati.isEmpty()) {
            if (nacin == Nacin.STROGO) {
                odlocitev(o, kandidati, "V registru je igralec z enakim imenom in datumom rojstva"
                        + (o.licenca() == null ? "." : ", a brez licence " + o.licenca() + "."));
                return ustvari(o, datum, null);
            }
            if (kandidati.size() == 1) {
                Igralec i = kandidati.get(0);
                povezi(o, i);
                osvezi(i, o, datum);
                porocilo.prestej("igralcev povezanih po imenu in datumu rojstva");
                return i;
            }
            porocilo.opozori("vec igralcev z enakim imenom in datumom rojstva (nov igralec)", opis(o));
        }
        return ustvari(o, datum, null);
    }

    /* Licenca poveze osebo le ob enakem spolu in datumu rojstva; 1. januar v
       registru je letnik stare strani in se ujema z vsakim datumom tega leta. */
    private static boolean istaOseba(Igralec i, Oseba o) {
        return o.spol() != null && o.spol() == i.getSpol() && datumSeUjema(i.getDatumRojstva(), o.rojstvo());
    }

    private static boolean datumSeUjema(LocalDate vRegistru, LocalDate izVira) {
        if (vRegistru == null || izVira == null) {
            return false;
        }
        return vRegistru.equals(izVira)
                || (vRegistru.getDayOfYear() == 1 && vRegistru.getYear() == izVira.getYear());
    }

    private List<Igralec> kandidati(Oseba o) {
        if (o.rojstvo() == null || o.polnoIme() == null) {
            return List.of();
        }
        String kljuc = kljucImena(o.polnoIme());
        return igralecRepozitorij.najdiPoDatumihRojstva(
                        List.of(o.rojstvo(), LocalDate.of(o.rojstvo().getYear(), 1, 1))).stream()
                .filter(i -> o.spol() == null || o.spol() == i.getSpol())
                .filter(i -> datumSeUjema(i.getDatumRojstva(), o.rojstvo()))
                .filter(i -> kljucImena(i.getIme() + " " + i.getPriimek()).equals(kljuc))
                // igralec, ki ima drugo licenco, ni ista oseba
                .filter(i -> i.getNtzsLicenca() == null || o.licenca() == null)
                .toList();
    }

    /* Igralci registra z enakim imenom (brez sumnikov in vrstnega reda besed). */
    private List<Igralec> kandidatiPoImenu(Oseba o) {
        String kljuc = kljucImena(o.polnoIme());
        List<Igralec> r = new ArrayList<>();
        for (Igralec i : igralecRepozitorij.findAll()) {
            if (kljucImena(i.getIme() + " " + i.getPriimek()).equals(kljuc)
                    && (o.spol() == null || o.spol() == i.getSpol())) {
                r.add(i);
            }
        }
        return r;
    }

    /* Nov igralec za osebo. podatki so odlocitev admina (lahko null): podano
       ime in priimek, datum rojstva in spol povozijo vir. */
    private Igralec ustvari(Oseba o, LocalDate datum, Odlocitev podatki) {
        LocalDate rojstvo = podatki != null && podatki.rojstvo() != null ? podatki.rojstvo() : o.rojstvo();
        Spol spol = podatki != null && podatki.spol() != null ? podatki.spol() : o.spol();
        String ime = null;
        String priimek = null;
        boolean zanesljivo = true;
        if (podatki != null && UvozOblike.ocisti(podatki.ime()) != null && UvozOblike.ocisti(podatki.priimek()) != null) {
            ime = UvozOblike.prirezi(UvozOblike.ocisti(podatki.ime()), 30);
            priimek = UvozOblike.prirezi(UvozOblike.ocisti(podatki.priimek()), 40);
        } else if (o.polnoIme() != null) {
            RazdelitevImena.Razdeljeno r = razdelitev().razdeli(o.polnoIme());
            ime = UvozOblike.prirezi(r.ime(), 30);
            priimek = UvozOblike.prirezi(r.priimek(), 40);
            zanesljivo = r.zanesljivo();
        }
        boolean imeVeljavno = ime != null && ime.length() >= 2 && priimek != null && priimek.length() >= 2;

        if (!imeVeljavno || rojstvo == null || spol == null) {
            /* Nove osebe brez imena in priimka, datuma rojstva ali spola shema ne
               sprejme. Stupa pa isto osebo vcasih vodi pod drugim user_role_id
               brez osebnih podatkov (Brin Vovk Petrovski v 1. SNTL 2025/26) - ta
               je v registru najverjetneje pod istim imenom. */
            List<String> manjka = new ArrayList<>();
            if (!imeVeljavno) {
                manjka.add(MANJKA_IME);
            }
            if (rojstvo == null) {
                manjka.add(MANJKA_ROJSTVO);
            }
            if (spol == null) {
                manjka.add(MANJKA_SPOL);
            }
            List<Igralec> kandidati = kandidatiNepopolne(o, rojstvo, spol);
            if (nacin == Nacin.STROGO) {
                odlocitev(o, kandidati, "Vir za osebo nima " + opisManjka(manjka)
                        + " - izberi igralca iz registra ali vpisi manjkajoce za novega igralca.", manjka);
                return null;
            }
            if (rojstvo == null || spol == null) {
                if (o.polnoIme() != null && besed(o.polnoIme()) >= 2 && kandidati.size() == 1) {
                    povezi(o, kandidati.get(0));
                    porocilo.prestej("oseb brez osebnih podatkov, povezanih po imenu");
                    return kandidati.get(0);
                }
                porocilo.opozori("oseba brez " + opisManjka(manjka.stream().filter(m -> !m.equals(MANJKA_IME)).toList())
                        + " (izpuscena)", opis(o));
                return null;
            }
            porocilo.napaka("imena ni mogoce razdeliti na ime in priimek", opis(o));
            return null;
        }

        Igralec i = new Igralec();
        i.setIme(ime);
        i.setPriimek(priimek);
        i.setSpol(spol);
        i.setDatumRojstva(rojstvo);
        i.setDrzavljanstvo(UvozOblike.drzavljanstvo(o.drzava()));
        if (o.licenca() != null) {
            if (igralecRepozitorij.existsByNtzsLicenca(o.licenca())) {
                porocilo.opozori("licenca NTZS je ze pri drugem igralcu (zapisan brez licence)", opis(o));
            } else {
                i.setNtzsLicenca(o.licenca());
            }
        }
        i.setKlub(klub(o.klub()));
        Igralec shranjen = igralecRepozitorij.save(i);
        povezi(o, shranjen);
        porocilo.prestej("novih igralcev");
        if (!zanesljivo) {
            porocilo.opozori("razdelitev imena za pregled", o.polnoIme() + " -> ime '" + ime + "', priimek '" + priimek + "'");
        }
        porocilo.novIgralec(new PorociloUvozaDto.NovIgralec(o.id(), o.polnoIme(), ime, priimek, zanesljivo,
                rojstvo, shranjen.getNtzsLicenca(), shranjen.getKlub() == null ? null : shranjen.getKlub().getIme()));
        return shranjen;
    }

    /* Kandidati za osebo, ki ji manjka kaj od imena, datuma rojstva ali spola:
       pri polnem imenu igralci z enakim imenom, pri imenu iz ene besede
       igralci z enakim datumom rojstva in spolom (najprej tisti, ki to besedo
       v imenu imajo), sicer tisti, katerih ime ali priimek je ta beseda. */
    private List<Igralec> kandidatiNepopolne(Oseba o, LocalDate rojstvo, Spol spol) {
        if (o.polnoIme() != null && besed(o.polnoIme()) >= 2) {
            return kandidatiPoImenu(o);
        }
        String beseda = o.polnoIme() == null ? null : kljucImena(o.polnoIme());
        if (rojstvo != null) {
            List<Igralec> poRojstvu = igralecRepozitorij.najdiPoDatumihRojstva(
                            List.of(rojstvo, LocalDate.of(rojstvo.getYear(), 1, 1))).stream()
                    .filter(i -> spol == null || spol == i.getSpol())
                    .filter(i -> datumSeUjema(i.getDatumRojstva(), rojstvo))
                    .toList();
            List<Igralec> zBesedo = beseda == null || beseda.isEmpty() ? List.of() : poRojstvu.stream()
                    .filter(i -> List.of(kljucImena(i.getIme() + " " + i.getPriimek()).split(" ")).contains(beseda))
                    .toList();
            return (zBesedo.isEmpty() ? poRojstvu : zBesedo).stream().limit(10).toList();
        }
        if (beseda == null || beseda.isEmpty()) {
            return List.of();
        }
        List<Igralec> r = new ArrayList<>();
        for (Igralec i : igralecRepozitorij.findAll()) {
            if ((kljucImena(i.getIme()).equals(beseda) || kljucImena(i.getPriimek()).equals(beseda))
                    && (spol == null || spol == i.getSpol())) {
                r.add(i);
                if (r.size() == 10) {
                    break;
                }
            }
        }
        return r;
    }

    private static String opisManjka(List<String> manjka) {
        return String.join(", ", manjka.stream().map(m -> switch (m) {
            case MANJKA_IME -> "imena in priimka";
            case MANJKA_ROJSTVO -> "datuma rojstva";
            default -> "spola";
        }).toList());
    }

    /* Dopolni povezanega igralca s tem, cesar register se nima: pravi datum
       namesto letnika, licenco (ce je prosta) in klub iz najnovejsega nastopa.
       Imena ne prepisuje - v registru je lahko rocno popravljeno. */
    private void osvezi(Igralec i, Oseba o, LocalDate datum) {
        boolean spremenjen = false;
        if (o.rojstvo() != null && i.getDatumRojstva() != null && !i.getDatumRojstva().equals(o.rojstvo())
                && i.getDatumRojstva().getDayOfYear() == 1 && i.getDatumRojstva().getYear() == o.rojstvo().getYear()) {
            i.setDatumRojstva(o.rojstvo());
            spremenjen = true;
        }
        if (i.getNtzsLicenca() == null && o.licenca() != null && !igralecRepozitorij.existsByNtzsLicenca(o.licenca())) {
            i.setNtzsLicenca(o.licenca());
            spremenjen = true;
        }
        Klub klub = klub(o.klub());
        if (klub != null && datum != null && (i.getKlub() == null || !klub.getId().equals(i.getKlub().getId()))
                && jeNajnovejsiNastop(i.getId(), datum)) {
            i.setKlub(klub);
            spremenjen = true;
        }
        if (spremenjen) {
            igralecRepozitorij.save(i);
        }
    }

    private boolean jeNajnovejsiNastop(Long idIgralec, LocalDate datum) {
        LocalDate zadnji = null;
        for (LocalDate d : prijavaRepozitorij.datumiNastopov(idIgralec)) {
            zadnji = pozneji(zadnji, d);
        }
        for (LocalDate d : kaderRepozitorij.datumiEkipnihTurnirjevIgralca(idIgralec)) {
            zadnji = pozneji(zadnji, d);
        }
        for (var cas : kaderRepozitorij.zacetkiLigIgralca(idIgralec)) {
            zadnji = pozneji(zadnji, cas.toLocalDate());
        }
        return zadnji == null || !datum.isBefore(zadnji);
    }

    private static LocalDate pozneji(LocalDate a, LocalDate b) {
        return a == null || (b != null && b.isAfter(a)) ? b : a;
    }

    private void povezi(Oseba o, Igralec i) {
        String zunanji = String.valueOf(o.id());
        Optional<ZunanjaPovezava> obstojeca = povezave.findByVirAndVrstaAndZunanjiId(
                VirTekmovanja.STUPA, ZunanjaPovezava.Vrsta.IGRALEC, zunanji);
        if (obstojeca.isEmpty()) {
            povezave.save(new ZunanjaPovezava(VirTekmovanja.STUPA, ZunanjaPovezava.Vrsta.IGRALEC, zunanji, i.getId()));
        } else if (!obstojeca.get().getIdLokalni().equals(i.getId())) {
            obstojeca.get().setIdLokalni(i.getId());
            povezave.save(obstojeca.get());
        }
    }

    private void odlocitev(Oseba o, List<Igralec> kandidati, String razlog) {
        odlocitev(o, kandidati, razlog, List.of());
    }

    /* manjka pove, kaj mora admin vpisati, ce izbere novega igralca; predlog
       razdelitve imena mu prihrani tipkanje (in ga lahko popravi). */
    private void odlocitev(Oseba o, List<Igralec> kandidati, String razlog, List<String> manjka) {
        RazdelitevImena.Razdeljeno predlog = o.polnoIme() != null && besed(o.polnoIme()) >= 2
                ? razdelitev().razdeli(o.polnoIme()) : null;
        porocilo.odlocitev(new PorociloUvozaDto.Odlocitev(o.id(), o.polnoIme(), o.rojstvo(),
                o.spol() == null ? null : o.spol().name(), o.licenca(),
                o.klub() == null ? null : o.klub().ime(), razlog,
                kandidati.stream().map(k -> new PorociloUvozaDto.Kandidat(k.getId(), k.polnoIme(),
                        k.getDatumRojstva(), k.getNtzsLicenca(), k.getKlub() == null ? null : k.getKlub().getIme()))
                        .toList(),
                predlog == null ? null : UvozOblike.prirezi(predlog.ime(), 30),
                predlog == null ? null : UvozOblike.prirezi(predlog.priimek(), 40),
                List.copyOf(manjka)));
    }

    private RazdelitevImena razdelitev() {
        if (razdelitev == null) {
            List<String[]> pari = new ArrayList<>();
            List<String> polna = new ArrayList<>(imenaDogodka);
            for (Object[] r : igralecRepozitorij.imenaInPriimki()) {
                pari.add(new String[] {(String) r[0], (String) r[1]});
                polna.add(r[0] + " " + r[1]);
            }
            razdelitev = new RazdelitevImena(polna, pari);
        }
        return razdelitev;
    }

    // ---------- Klub ----------

    public Klub klub(KlubStupe k) {
        if (k == null) {
            return null;
        }
        String kljucId = "id:" + k.id();
        if (k.id() != 0 && klubi.containsKey(kljucId)) {
            return klubi.get(kljucId);
        }
        Klub klub = null;
        if (k.id() != 0) {
            klub = povezave.findByVirAndVrstaAndZunanjiId(VirTekmovanja.STUPA, ZunanjaPovezava.Vrsta.KLUB,
                            String.valueOf(k.id()))
                    .flatMap(p -> klubRepozitorij.findById(p.getIdLokalni()))
                    .orElse(null);
        }
        String poenostavljeno = poenostavljenoImeKluba(k.ime());
        if (klub == null) {
            klub = klubiPoImenu().get(poenostavljeno);
        }
        if (klub == null) {
            String ime = UvozOblike.prirezi(k.ime(), 50);
            String kratica = k.kratica() != null && k.kratica().length() >= 2 && k.kratica().length() <= 10
                    ? k.kratica() : null;
            klub = klubRepozitorij.save(new Klub(ime, kratica));
            klubiPoImenu().put(poenostavljeno, klub);
            porocilo.prestej("novih klubov");
        }
        if (k.id() != 0) {
            klubi.put(kljucId, klub);
            if (povezave.findByVirAndVrstaAndZunanjiId(VirTekmovanja.STUPA, ZunanjaPovezava.Vrsta.KLUB,
                    String.valueOf(k.id())).isEmpty()) {
                povezave.save(new ZunanjaPovezava(VirTekmovanja.STUPA, ZunanjaPovezava.Vrsta.KLUB,
                        String.valueOf(k.id()), klub.getId()));
            }
        }
        return klub;
    }

    private Map<String, Klub> klubiPoImenu() {
        if (klubiPoImenu == null) {
            klubiPoImenu = new HashMap<>();
            for (Klub k : klubRepozitorij.findAll()) {
                klubiPoImenu.putIfAbsent(poenostavljenoImeKluba(k.getIme()), k);
            }
        }
        return klubiPoImenu;
    }

    // ---------- Pomozno ----------

    /* Ime brez sumnikov, locil in vrste drustva - ostane lastno ime kluba
       (isto pravilo kot pri zgodovinskem uvozu, SifrantiUvoz). */
    public static String poenostavljenoImeKluba(String ime) {
        String brezSumnikov = Normalizer.normalize(ime, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase();
        return brezSumnikov
                .replaceAll("\\bNAMIZNOTENISK[AIO]\\b|\\bNAMIZNI TENIS\\b", " ")
                .replaceAll("\\bSPORTN[OI]\\b|\\bDRUSTVO\\b|\\bKLUB\\b|\\bZVEZA\\b", " ")
                .replaceAll("\\b(NTK|NTD|NTS|ZNTK|SD|PPK|PPR|TTC|STT)\\b", " ")
                .replaceAll("[^A-Z0-9]", "");
    }

    /* Ime kot neurejen nabor besed brez sumnikov: "Brezovnik Aljaz" in
       "Aljaz Brezovnik" sta isto ime. */
    static String kljucImena(String polno) {
        String[] besede = Normalizer.normalize(polno.toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace("đ", "d")
                .split("[^a-z]+");
        return String.join(" ", Arrays.stream(besede).filter(b -> !b.isEmpty()).sorted().toList());
    }

    private static String opis(Oseba o) {
        return (o.polnoIme() == null ? "(brez imena)" : o.polnoIme())
                + (o.rojstvo() == null ? "" : ", r. " + o.rojstvo())
                + (o.licenca() == null ? "" : ", licenca " + o.licenca())
                + " [Stupa " + o.id() + "]";
    }
}
