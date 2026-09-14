/* Liga - izpis konfiguracije in stanja. */
package si.turnirko.dto;

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
        StatusTekmovanja status,
        int steviloEkip,
        // Napredek lige: koliko kol ima razpored in koliko jih je odigranih
        // (kolo je odigrano, ko je koncano vsako njegovo srecanje). Vrstica
        // lige na telefonu iz tega izpise "7. od 18 kol" in palico; brez
        // razporeda sta oba nic.
        int odigranihKol,
        int steviloKol,
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
        VirTekmovanja vir
) {

    /* Liga brez razporeda (nova, urejena, prehodi) - kol se ni. */
    public static LigaDto iz(Liga l, int steviloEkip) {
        return iz(l, steviloEkip, 0, 0);
    }

    public static LigaDto iz(Liga l, int steviloEkip, int odigranihKol, int steviloKol) {
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
                l.getStNapreduje(), l.getStIzpade(), l.getStatus(), steviloEkip,
                odigranihKol, steviloKol, l.isNaDomaci(),
                l.getUstvaril() != null ? l.getUstvaril().getId() : null,
                l.getKlubLastnik() != null ? l.getKlubLastnik().getId() : null,
                l.getKlubLastnik() != null ? l.getKlubLastnik().getIme() : null,
                l.getKoncnicaEkip(), l.getKoncnicaZmag(), l.getVir());
    }
}
