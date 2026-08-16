/* Obracun klubskega ELO za vse uvozene tekme.

   Zakaj poseben korak in ne sproti med uvozom: ELO je zaporedna kolicina -
   izid vsake tekme je odvisen od stanja obeh igralcev v tistem trenutku. Uvoz
   gre po dogodkih (najprej cel turnir, potem cela liga), kar NI casovno
   zaporedje; ce bi racunali sproti, bi igralec dobil majske tekme pred
   oktobrskimi in vse vrednosti bi bile napacne. Zato tu vse uvozene tekme -
   turnirske in ligaske skupaj - zlozimo v eno casovno vrsto.

   To je hkrati razlog, da ta korak tece ZADNJI, in razlog, da vrsta nosi samo
   identifikatorje: entitete iz uvoza so odklopljene, RatingStoritev pa bere
   leno nalozene povezave (tekma -> dogodek -> turnir). Zato jih tu naloZimo
   znova, znotraj transakcije.

   Merila, katera tekma steje, so ista kot pri rednem vnosu rezultata
   (TekmaStoritev / SrecanjeStoritev) in se tu namenoma ponovijo, ker uvoznik
   teh storitev ne uporablja:
    * samo dejansko odigrane tekme (IGRANO ali PREDAJA - w.o. in
      diskvalifikacija ne stejeta),
    * samo ce tekmovanje steje v ELO (turnir.steje_v_elo / liga.steje_v_elo),
    * ligaske dvojice nikoli (dva igralca na strani nimata enega ratinga). */
package si.turnirko.uvoz;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.modeli.IzidTekme;
import si.turnirko.modeli.Tekma;
import si.turnirko.modeli.TekmaSrecanja;
import si.turnirko.modeli.TipTekmeSrecanja;
import si.turnirko.repozitoriji.TekmaRepozitorij;
import si.turnirko.repozitoriji.TekmaSrecanjaRepozitorij;
import si.turnirko.storitve.RatingStoritev;

@Component
@Profile(UvozUkaz.PROFIL)
public class EloUvoz {

    /* Ena tekma v casovni vrsti.

       "cas" je datum in - kadar ga vir pove - ura. Uro imajo ligaska srecanja
       (zapisnik jo zabelezi), turnirske tekme pa ne: vir pove samo dan
       tekmovanja. Turnirska tekma zato dobi polnoc, kar jo na isti dan postavi
       PRED ligaska srecanja - turnirji se zacnejo zjutraj, ligaska srecanja pa
       so praviloma dopoldne ali popoldne. Igralec istega dne tako ali tako ne
       igra obojega, zato je vpliv te izbire na ELO zanemarljiv.

       "zaporedje" uredi tekme, ki imajo isti cas - torej znotraj enega
       tekmovanja. Sestavljeno je iz faze (skupinski del pred izlocilnim; brez
       tega bi prvo kolo finalnega dela padlo pred drugo kolo skupin), kola in
       mesta. Pri ligah je to kolo in zaporedje tekme v srecanju. */
    public record VrstaTekme(boolean ligaska, long id, LocalDateTime cas, long zaporedje) {

        public static VrstaTekme turnirska(long id, LocalDate datum, boolean izlocilna,
                                           int kolo, int pozicija) {
            long faza = izlocilna ? 1 : 0;
            return new VrstaTekme(false, id, datum == null ? null : datum.atStartOfDay(),
                    faza * 1_000_000_000L + (long) kolo * 1_000_000 + pozicija);
        }

        public static VrstaTekme ligaska(long id, LocalDateTime cas, int kolo, int zaporedjeVSrecanju) {
            return new VrstaTekme(true, id, cas, (long) kolo * 1_000_000 + zaporedjeVSrecanju);
        }
    }

    /* Tekme, ki jim datuma ne poznamo, gredo na zacetek - tam najmanj skodijo:
       igralec je takrat se brez zgodovine in K faktor je tako ali tako najvisji. */
    private static final LocalDateTime BREZ_DATUMA = LocalDate.of(1900, 1, 1).atStartOfDay();

    /* Koliko tekem obracunamo v eni transakciji. Dovolj veliko, da SQLite ne
       placuje potrditve za vsako tekmo, dovolj majhno, da seja ne zraste. */
    private static final int VELIKOST_PAKETA = 500;

    private final TekmaRepozitorij tekmaRepozitorij;
    private final TekmaSrecanjaRepozitorij tekmaSrecanjaRepozitorij;
    private final RatingStoritev ratingStoritev;

    public EloUvoz(TekmaRepozitorij tekmaRepozitorij,
                   TekmaSrecanjaRepozitorij tekmaSrecanjaRepozitorij,
                   RatingStoritev ratingStoritev) {
        this.tekmaRepozitorij = tekmaRepozitorij;
        this.tekmaSrecanjaRepozitorij = tekmaSrecanjaRepozitorij;
        this.ratingStoritev = ratingStoritev;
    }

    /* Casovno zaporedje vseh uvozenih tekem. Pakete nato zazene UvozUkaz -
       zanka namenoma NI tukaj, ker klic metode iz iste fasete obide Springov
       ovoj in @Transactional na obracunajPaket ne bi ucinkoval. */
    public List<VrstaTekme> uredi(List<VrstaTekme> vrsta) {
        List<VrstaTekme> urejene = new ArrayList<>(vrsta);
        urejene.sort(Comparator
                .comparing((VrstaTekme v) -> v.cas() == null ? BREZ_DATUMA : v.cas())
                .thenComparingLong(VrstaTekme::zaporedje)
                .thenComparingLong(VrstaTekme::id));
        return urejene;
    }

    public int velikostPaketa() {
        return VELIKOST_PAKETA;
    }

    @Transactional
    public void obracunajPaket(List<VrstaTekme> paket, UvozPorocilo porocilo) {
        for (VrstaTekme v : paket) {
            if (v.ligaska()) {
                tekmaSrecanjaRepozitorij.findById(v.id()).ifPresent(t -> obracunajLigasko(t, porocilo));
            } else {
                tekmaRepozitorij.findById(v.id()).ifPresent(t -> obracunajTurnirsko(t, porocilo));
            }
        }
    }

    private void obracunajTurnirsko(Tekma tekma, UvozPorocilo porocilo) {
        if (!steje(tekma.getIzidTip())
                || !tekma.getDogodek().getTurnir().isStejeVElo()
                || tekma.getPrijava1() == null
                || tekma.getPrijava2() == null
                || tekma.getZmagovalec() == null) {
            return;
        }
        ratingStoritev.obracunajKlubskiElo(tekma);
        porocilo.prestej("tekem v obracunu ELO");
    }

    private void obracunajLigasko(TekmaSrecanja tekma, UvozPorocilo porocilo) {
        if (tekma.getTip() != TipTekmeSrecanja.POSAMICNA
                || !steje(tekma.getIzidTip())
                || !tekma.getSrecanje().getLiga().isStejeVElo()
                || tekma.getIgralecDomaci() == null
                || tekma.getIgralecGost() == null
                || tekma.getZmagovalecStran() == null) {
            return;
        }
        ratingStoritev.obracunajZaLigasko(tekma);
        porocilo.prestej("tekem v obracunu ELO");
    }

    private boolean steje(IzidTekme izid) {
        return izid != null && izid.jeOdigrana();
    }
}
