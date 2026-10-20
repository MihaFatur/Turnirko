/* Nocno ciscenje registracij, ki se niso koncale.

   Racun brez potrjenega naslova je rezervacija, ne oseba: po 48 urah se
   izbrise, da tuj ali napacno vpisan naslov ne ostane zaseden. Racun mlajsega
   od 15 let, pri katerem skrbnik soglasja ni dal, se izbrise po 30 dneh
   (stars poste ne bere sproti, a mesec je dovolj). Potekle kode se pobrisejo
   po dnevu - v bazi so samo zgostitve, a tabela ne sme rasti v nedogled.
   Izbris racuna odnese tudi njegove kode (ON DELETE CASCADE); igralca in
   tekem se ne dotakne, ker nepotrjen racun z njimi ni bil nikoli povezan.
   Cakajoca registracija (placljiv paket, V48), ki placila ni dobila v 4 dneh,
   je opuscen nakup: Stripova seja potece v 24 urah, ponovni poskusi dostave
   webhooka trajajo do 3 dni, kasneje placilo ne pride vec. */
package si.turnirko.storitve;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.modeli.Uporabnik;
import si.turnirko.repozitoriji.CakajocaRegistracijaRepozitorij;
import si.turnirko.repozitoriji.PotrditvenaKodaRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

@Service
public class CiscenjeRacunovStoritev {

    private static final Logger dnevnik = LoggerFactory.getLogger(CiscenjeRacunovStoritev.class);

    static final int UR_ZA_POTRDITEV_NASLOVA = 48;
    static final int DNI_ZA_SOGLASJE_SKRBNIKA = 30;
    static final int DNI_ZA_PLACILO_REGISTRACIJE = 4;

    private final UporabnikRepozitorij uporabnikRepozitorij;
    private final PotrditvenaKodaRepozitorij kodaRepozitorij;
    private final CakajocaRegistracijaRepozitorij cakajoce;

    public CiscenjeRacunovStoritev(UporabnikRepozitorij uporabnikRepozitorij,
                                   PotrditvenaKodaRepozitorij kodaRepozitorij,
                                   CakajocaRegistracijaRepozitorij cakajoce) {
        this.uporabnikRepozitorij = uporabnikRepozitorij;
        this.kodaRepozitorij = kodaRepozitorij;
        this.cakajoce = cakajoce;
    }

    /* Vrne stevilo izbrisanih racunov. */
    @Transactional
    public int pocisti(LocalDateTime zdaj) {
        List<Uporabnik> brezNaslova = uporabnikRepozitorij
                .najdiNepotrjeneStarejseOd(zdaj.minusHours(UR_ZA_POTRDITEV_NASLOVA));
        List<Uporabnik> brezSoglasja = uporabnikRepozitorij
                .najdiBrezSoglasjaStarejseOd(zdaj.minusDays(DNI_ZA_SOGLASJE_SKRBNIKA));
        // kode najprej: enota dela mora vedeti zanje, ne le baza (ON DELETE CASCADE)
        for (Uporabnik u : brezNaslova) {
            kodaRepozitorij.deleteAll(kodaRepozitorij.findByRacunId(u.getId()));
        }
        for (Uporabnik u : brezSoglasja) {
            kodaRepozitorij.deleteAll(kodaRepozitorij.findByRacunId(u.getId()));
        }
        kodaRepozitorij.flush();
        uporabnikRepozitorij.deleteAll(brezNaslova);
        uporabnikRepozitorij.deleteAll(brezSoglasja);
        uporabnikRepozitorij.flush();
        int kode = kodaRepozitorij.pobrisiPotekle(zdaj.minusDays(1));
        int neplacane = cakajoce.pobrisiStarejseOd(zdaj.minusDays(DNI_ZA_PLACILO_REGISTRACIJE));
        int racuni = brezNaslova.size() + brezSoglasja.size();
        if (racuni > 0 || kode > 0 || neplacane > 0) {
            dnevnik.info("Ciscenje registracij: {} racunov brez potrditve, {} poteklih kod, "
                    + "{} neplacanih registracij.", racuni, kode, neplacane);
        }
        return racuni;
    }

    /* Dnevno ob 3.45, za odbitkom za neaktivnost (3.15) - takrat nihce
       nicesar ne vnasa. */
    @Scheduled(cron = "0 45 3 * * *")
    public void dnevnoOpravilo() {
        pocisti(LocalDateTime.now());
    }
}
