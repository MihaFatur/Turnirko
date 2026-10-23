/* Preizkus Premium: prvi mesec Premium brez placila (V49, odlocitev lastnika
   23. 10. 2026).

   Zakaj: brezplacen racun igralca ni dal nicesar, cesar ne bi imel gost, zato
   si ga skoraj nihce ni ustvaril - in kdor racuna nima, Premium tudi ne kupi.
   Preizkus je razlog za racun in hkrati pokaze, kaj Premium je.

   Pravila:
   - Preizkus se zacne, ko je racun igralca POVEZAN z zapisom (samodejno ob
     potrditvi e-poste ali ga poveze administrator). Prej Premium nima cesa
     pokazati: zasebna statistika in domaca stran po meri potrebujeta igralca,
     in mesec, ki bi tekel med cakanjem na admina, bi bil izgubljen.
   - Enkrat na racun: kdor vrstico v narocnina ze ima (preizkus, placilo,
     potekla narocnina), ga ne dobi znova - tudi po razvezavi ne.
   - Brez Stripa in brez kartice; konec je samo datum (Narocnina.jeVeljavna),
     zato potek ne potrebuje opravila. Nakup med preizkusom vrstico prepise
     (PlacilaStoritev.obdelajNadgradnjo) - placano obdobje zacne teci takoj.
   - Nekaj dni pred koncem gre ENO obvestilo po posti. Racun mlajsega od
     15 let ga ne dobi: obvestilo navaja ceno, otroka pa k nakupu ne
     nagovarjamo (racun se po preizkusu tiho vrne na Free). */
package si.turnirko.storitve;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.modeli.CiklusPlacila;
import si.turnirko.modeli.Narocnina;
import si.turnirko.modeli.Paket;
import si.turnirko.modeli.StatusNarocnine;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.posta.PostaStoritev;
import si.turnirko.posta.SporocilaPoste;
import si.turnirko.repozitoriji.NarocninaRepozitorij;

@Service
public class PreizkusPremiumStoritev {

    private static final Logger dnevnik = LoggerFactory.getLogger(PreizkusPremiumStoritev.class);

    /* Dolzina preizkusa. Mesec in ne 14 dni: kola lig so na ~3 tedne, v 14
       dneh igralec Premium pogosto ne bi videl niti po enem kolu. */
    static final int MESECEV_PREIZKUSA = 1;

    /* Koliko dni pred koncem gre obvestilo. */
    static final int DNI_PRED_KONCEM = 3;

    private final NarocninaRepozitorij narocninaRepozitorij;
    private final CenikStoritev cenik;
    private final PostaStoritev posta;
    private final RacunDnevnikStoritev racunDnevnik;
    private final String koren;

    public PreizkusPremiumStoritev(NarocninaRepozitorij narocninaRepozitorij,
                                   CenikStoritev cenik,
                                   PostaStoritev posta,
                                   RacunDnevnikStoritev racunDnevnik,
                                   @Value("${turnirko.stripe.vrni-na}") String koren) {
        this.narocninaRepozitorij = narocninaRepozitorij;
        this.cenik = cenik;
        this.posta = posta;
        this.racunDnevnik = racunDnevnik;
        this.koren = koren;
    }

    /* Zacne preizkus racunu, ki je pravkar dobil igralca. Vrne vrstico, ce je
       preizkus nastal; prazno, ce racun ni igralec, ni povezan ali je
       narocnino (katerokoli) ze imel. Klicatelj (povezava racuna) tece v
       svoji transakciji. */
    public Optional<Narocnina> zacni(Uporabnik u, LocalDateTime zdaj) {
        if (u == null || u.getId() == null || u.getVloga() != Vloga.IGRALEC || u.getIgralec() == null) {
            return Optional.empty();
        }
        if (narocninaRepozitorij.findByUporabnikId(u.getId()).isPresent()) {
            return Optional.empty();
        }
        Narocnina n = new Narocnina(u, Paket.PREMIUM);
        n.setStatus(StatusNarocnine.PREIZKUS);
        n.setZacetekOb(zdaj);
        n.setObdobjeOd(zdaj);
        n.setTrenutnoObdobjeDo(zdaj.plusMonths(MESECEV_PREIZKUSA));
        narocninaRepozitorij.save(n);
        racunDnevnik.zabelezi(u.getId(), "Preizkus Premium do " + oblikuj(dan(n.getTrenutnoObdobjeDo())));
        return Optional.of(n);
    }

    /* Obvestila o koncu preizkusa; vrne stevilo poslanih. Vsak preizkus dobi
       najvec eno, ko je do konca DNI_PRED_KONCEM dni ali manj. Potek obdobja
       je zapisan kot besedilo, zato ga primerjamo tu in ne v poizvedbi (kot
       NarocninaRepozitorij.najdiPremiumIgralce). */
    @Transactional
    public int posljiOpomnike(LocalDateTime zdaj) {
        List<Narocnina> preizkusi = narocninaRepozitorij
                .findByStatusAndOpomnikPoslanObIsNull(StatusNarocnine.PREIZKUS);
        int poslanih = 0;
        for (Narocnina n : preizkusi) {
            LocalDateTime konec = n.getTrenutnoObdobjeDo();
            if (konec == null || !konec.isAfter(zdaj) || konec.isAfter(zdaj.plusDays(DNI_PRED_KONCEM))) {
                continue;
            }
            Uporabnik u = n.getUporabnik();
            n.setOpomnikPoslanOb(zdaj);
            if (u.getEmailSkrbnika() != null || !u.jeEmailPotrjen()) {
                continue; // zabelezeno kot obdelano, a brez poste (glej glavo razreda)
            }
            boolean starejsi = u.getPrijavljeniDatumRojstva() == null
                    || cenik.stariEnaindvajset(u.getPrijavljeniDatumRojstva());
            posta.poslji(u.getUporabniskoIme(), SporocilaPoste.konecPreizkusa(
                    oblikuj(dan(konec)),
                    cenik.cena(Paket.PREMIUM, CiklusPlacila.MESECNO, starejsi),
                    cenik.cena(Paket.PREMIUM, CiklusPlacila.LETNO, starejsi),
                    koren + "/narocnina"));
            racunDnevnik.zabelezi(u.getId(), "Obvestilo o koncu preizkusa poslano");
            poslanih++;
        }
        if (poslanih > 0) {
            dnevnik.info("Obvestila o koncu preizkusa Premium: {}.", poslanih);
        }
        return poslanih;
    }

    /* Dnevno ob 10.00: obvestilo pride sredi dneva, ne ponoci. */
    @Scheduled(cron = "0 0 10 * * *")
    public void dnevniOpomniki() {
        posljiOpomnike(LocalDateTime.now());
    }

    /* Zadnji dan veljavnega preizkusa racuna (za odgovor ob potrditvi kode);
       null, ce preizkusa nima ali je potekel. */
    public LocalDate veljaDo(Uporabnik u, LocalDateTime zdaj) {
        if (u == null || u.getId() == null) {
            return null;
        }
        return narocninaRepozitorij.findByUporabnikId(u.getId())
                .filter(n -> n.jePreizkus() && n.jeVeljavna(zdaj))
                .map(n -> dan(n.getTrenutnoObdobjeDo()))
                .orElse(null);
    }

    private static LocalDate dan(LocalDateTime cas) {
        return UpravljanjeNarocnineStoritev.dan(cas);
    }

    private static String oblikuj(LocalDate d) {
        return d.getDayOfMonth() + ". " + d.getMonthValue() + ". " + d.getYear();
    }
}
