/* Turnir - izpis. */
package si.turnirko.dto;

import java.time.LocalDate;

import si.turnirko.modeli.StatusTekmovanja;
import si.turnirko.modeli.Turnir;

public record TurnirDto(
        Long id,
        String ime,
        KrajDto kraj,
        String dvorana,
        LocalDate datumZacetka,
        LocalDate datumKonca,
        StatusTekmovanja status,
        String opombe,
        // ali tekme turnirja stejejo v klubski ELO
        boolean stejeVElo,
        // Lastnistvo: racun, ki je turnir ustvaril, in klub lastnik. Po njiju
        // vmesnik pokaze urejevalna dejanja le lastniku; streznik je zadnja
        // obramba (LastnistvoStoritev). idLastnik ni obcutljiv podatek.
        Long idLastnik,
        Long idKlubLastnik,
        String klubLastnik,
        // Stevci za pas "Danes v dvorani" in kolofon turnirja. Sodnik med
        // turnirjem isce dvoje: koliko je odigranega in kaj tece prav zdaj -
        // oboje je vsota cez dogodke, zato ju sesteje streznik in ne vmesnik.
        int steviloDogodkov,
        int dogodkovVTeku,
        int dogodkovVPripravi,
        int prijavljenihSkupaj,
        int odigranihTekem,
        int vsehTekem,
        // Kje turnir je in kaj se je nazadnje zgodilo. Vrstica turnirja na
        // domaci strani se bere kot zapisnik ("Ljubljana · 32 igralcev ·
        // cetrtfinale"), zato mora stanje priti s streznika - vmesnik nima
        // tekem pri roki in bi ga sicer moral ugibati.
        String faza,
        String zmagovalec,
        String zadnjiIzid
) {

    /* Stevci enega turnirja; PRAZNI so odgovor mutacije, kjer jih se ni. */
    public record Stevci(
            int steviloDogodkov,
            int dogodkovVTeku,
            int dogodkovVPripravi,
            int prijavljenihSkupaj,
            int odigranihTekem,
            int vsehTekem
    ) {
        public static final Stevci PRAZNI = new Stevci(0, 0, 0, 0, 0, 0);
    }

    /* Besedno stanje turnirja. faza je zapolnjena samo pri turnirju V_TEKU
       ("skupine", "cetrtfinale", "3. kolo"), zmagovalec samo pri
       ZAKLJUCEN; zadnjiIzid je zadnja odigrana tekma, kadar koli obstaja. */
    public record Potek(String faza, String zmagovalec, String zadnjiIzid) {
        public static final Potek PRAZEN = new Potek(null, null, null);
    }

    public static TurnirDto iz(Turnir turnir) {
        return iz(turnir, Stevci.PRAZNI, Potek.PRAZEN);
    }

    public static TurnirDto iz(Turnir turnir, Stevci stevci) {
        return iz(turnir, stevci, Potek.PRAZEN);
    }

    public static TurnirDto iz(Turnir turnir, Stevci stevci, Potek potek) {
        return new TurnirDto(
                turnir.getId(),
                turnir.getIme(),
                turnir.getKraj() != null ? KrajDto.iz(turnir.getKraj()) : null,
                turnir.getDvorana(),
                turnir.getDatumZacetka(),
                turnir.getDatumKonca(),
                turnir.getStatus(),
                turnir.getOpombe(),
                turnir.isStejeVElo(),
                turnir.getUstvaril() != null ? turnir.getUstvaril().getId() : null,
                turnir.getKlubLastnik() != null ? turnir.getKlubLastnik().getId() : null,
                turnir.getKlubLastnik() != null ? turnir.getKlubLastnik().getIme() : null,
                stevci.steviloDogodkov(),
                stevci.dogodkovVTeku(),
                stevci.dogodkovVPripravi(),
                stevci.prijavljenihSkupaj(),
                stevci.odigranihTekem(),
                stevci.vsehTekem(),
                potek.faza(),
                potek.zmagovalec(),
                potek.zadnjiIzid()
        );
    }
}
