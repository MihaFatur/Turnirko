/* Liga - izpis konfiguracije in stanja. */
package si.turnirko.dto;

import java.time.LocalDate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.Liga;
import si.turnirko.modeli.PredlogaLige;
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.StatusTekmovanja;
import si.turnirko.modeli.VirTekmovanja;

public record LigaDto(
        Long id,
        String ime,
        String sezona,
        SpolKategorija spolKategorija,
        FormatSrecanja formatSrecanja,
        int steviloNizov,
        Integer zmagZaSrecanje,
        boolean dvokrozno,
        int tockeZmaga,
        int tockeNeodloceno,
        int tockePoraz,
        // odbitek tock za poraz brez borbe (V29, Pravila SNTL: 1)
        int odbitekBrezBoja,
        boolean dovoljenoNeodloceno,
        boolean prepovedDvojneRegistracije,
        // raven tekmovanja doloci tezo posamicnih tekem v Turnirko ratingu
        RavenTekmovanja raven,
        // Zreb po parih: ekipe imajo jakostni vrstni red (EkipaDto.stNosilca)
        // in razpored jih zveze v pare - zgornja polovica s spodnjo.
        boolean enakomernaRazvrstitev,
        // Ali je razpored vpisal organizator namesto zreba. Javno polje:
        // stran lige to pove tudi gostu, ker je papirnati razpored ze v
        // rokah igralcev in mora biti razvidno, da gre za isti razpored.
        boolean rocniZreb,
        PredlogaLige predlogaListka,
        // Seme terminov (glej Liga): iz njiju se ob zrebu izracunajo datumi
        // kol. Obrazec lige ju prikaze nazaj, ko se pravila urejajo.
        LocalDateTime zacetekPrvegaKola,
        Integer razmikDni,
        // Ure srecanj v kolu ("18:30"), null = kolo kroznega sistema (V31).
        // Javno: pravila lige jih izpisejo, razpored po njih ve, da kolo
        // nosi vec ur.
        List<String> ureSrecanj,
        Long idVisjaLiga,
        String visjaLigaIme,
        int stNapreduje,
        int stIzpade,
        // kvalifikacije (V41): koliko ekip tik pod napredovanjem oz. tik nad
        // izpadom igra kvalifikacije s sosednjo ligo
        int stKvalifikacijeGor,
        int stKvalifikacijeDol,
        // Kar vidi gledalec: pred zacetkom lige je po razporedu "V pripravi" (glej
        // StatusTekmovanja.zaPrikaz). Dejanski status je v dejanskiStatus.
        StatusTekmovanja status,
        // Stanje, po katerem streznik odloca (razpored, rezultati, kolo). Vmesnik ga
        // bere za kontrole, napis pa za status.
        StatusTekmovanja dejanskiStatus,
        int steviloEkip,
        // Napredek rednega dela: koliko kol ima razpored in koliko jih je za
        // nami. Kolo je odigrano, ko je njegov datum mimo (kolo brez datuma,
        // ko je koncano vsako srecanje) - isto pravilo kot na domaci strani
        // (PotekLige). Brez razporeda sta oba nic.
        int odigranihKol,
        int steviloKol,
        // Stevilke odigranih kol, narascajoce: razpored po njih oznaci trak
        // kol, da se z glavo strani ne razideta.
        List<Integer> odigranaKola,
        // Prvo kolo, ki se ni odigrano in ga termin ni prehitel (srecanje z
        // nevpisanim izidom in starim datumom NI naslednje); null, ko takega
        // ni. Koncnica tu ne steje - glavo po rednem delu nosi ona sama.
        Naslednje naslednje,
        // Zadnji dan, ko je liga nekaj naredila: srecanje se je odigralo (termin je
        // ze ali danes mimo) ali je bil njegov izid vpisan (PotekLige.zadnjaAktivnost).
        // Null, ce takega datuma ni. Javno: seznam lig po njem skrije zakljucene lige
        // za nazaj, ki jih ne zanima.
        LocalDate zadnjaAktivnost,
        // Ali liga stoji v sklopu "Lige" na domaci strani (najvec dve, izbere
        // admin). Javno polje, ker po njem vmesnik oznaci ligo v izboru in ve,
        // kaj naj domaca stran pokaze gostu.
        boolean naDomaci,
        // Lastnistvo (glej TurnirDto) - vmesnik po njiju pokaze urejanje le
        // lastniku; streznik je zadnja obramba (LastnistvoStoritev).
        Long idLastnik,
        Long idKlubLastnik,
        String klubLastnik,
        // Koncnica po rednem delu (V28): koliko ekip in koliko zmag za serijo;
        // oboje prazno = liga koncnice nima.
        Integer koncnicaEkip,
        Integer koncnicaZmag,
        // Vir (V27): uvozena liga je samo za branje - vmesnik urejanja ne
        // ponudi in pod naslovom pove, od kod so podatki.
        VirTekmovanja vir,
        // Liga kvalifikacij (V41): med katerima ligama se igra; pri navadni
        // ligi prazno.
        Long idKvalifikacijeVisja,
        String kvalifikacijeVisjaIme,
        Long idKvalifikacijeNizja,
        String kvalifikacijeNizjaIme,
        // Opomba organizatorja (V52); prazno, ce je ni.
        String opombe,
        // Logotip lige (V54); prazno, ce ga ni. Javno: stoji v glavi strani.
        SlikaDto logotip
) {

    /* Naslednje kolo: stevilka in dan (null, ce kolo termina nima). */
    public record Naslednje(int kolo, LocalDate datum) {}

    /* Liga brez razporeda (nova, urejena, prehodi) - kol se ni. */
    public static LigaDto iz(Liga l, int steviloEkip) {
        return iz(l, steviloEkip, List.of(), 0, null, null);
    }

    public static LigaDto iz(Liga l, int steviloEkip, List<Integer> odigranaKola, int steviloKol,
                             Naslednje naslednje, LocalDate zadnjaAktivnost) {
        return new LigaDto(
                l.getId(), l.getIme(), l.getSezona(), l.getSpolKategorija(),
                l.getFormatSrecanja(), l.getSteviloNizov(), l.getZmagZaSrecanje(),
                l.isDvokrozno(), l.getTockeZmaga(), l.getTockeNeodloceno(), l.getTockePoraz(),
                l.getOdbitekBrezBoja(), l.isDovoljenoNeodloceno(), l.isPrepovedDvojneRegistracije(), l.getRaven(),
                l.isEnakomernaRazvrstitev(), l.isRocniZreb(), l.getPredlogaListka(),
                l.getZacetekPrvegaKola(), l.getRazmikDni(),
                l.getUreSrecanj() != null
                        ? l.getUreSrecanj().stream().map(LocalTime::toString).toList()
                        : null,
                l.getVisjaLiga() != null ? l.getVisjaLiga().getId() : null,
                l.getVisjaLiga() != null ? l.getVisjaLiga().getIme() : null,
                l.getStNapreduje(), l.getStIzpade(),
                l.getStKvalifikacijeGor(), l.getStKvalifikacijeDol(),
                StatusTekmovanja.zaPrikaz(l.getStatus(), l.zacetekDne()), l.getStatus(), steviloEkip,
                odigranaKola.size(), steviloKol, odigranaKola, naslednje, zadnjaAktivnost, l.isNaDomaci(),
                l.getUstvaril() != null ? l.getUstvaril().getId() : null,
                l.getKlubLastnik() != null ? l.getKlubLastnik().getId() : null,
                l.getKlubLastnik() != null ? l.getKlubLastnik().getIme() : null,
                l.getKoncnicaEkip(), l.getKoncnicaZmag(), l.getVir(),
                l.getKvalifikacijeVisja() != null ? l.getKvalifikacijeVisja().getId() : null,
                l.getKvalifikacijeVisja() != null ? l.getKvalifikacijeVisja().getIme() : null,
                l.getKvalifikacijeNizja() != null ? l.getKvalifikacijeNizja().getId() : null,
                l.getKvalifikacijeNizja() != null ? l.getKvalifikacijeNizja().getIme() : null,
                l.getOpombe(),
                SlikaDto.iz(l.getLogotip()));
    }
}
