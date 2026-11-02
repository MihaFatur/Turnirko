/* E-posta po turnirju (V58, paket Navijac in Igralec Premium): ko je turnir
   zakljucen, prejme vsak racun s tema paketoma, ki spremlja igralca, ki je na
   turnirju nastopil, sporocilo z mestom, izkupickom in spremembo ratinga
   (SporociloTurnirja). Gradi na istem okvirju kot e-posta po kolu
   (ObvestilaPoKoluStoritev): isto stikalo in ista odjava (uporabnik.
   obvestila_po_kolu, zeton odjave), isti pogoji za prejemnika.

   Pravila, ki jih ne razbij:
   - **Vsak turnir enkrat** (obvestilo_turnirja): tudi po popravku izida ali
     ponovni zakljucitvi ne gre znova. Turnir brez prejemnikov je obdelan z
     nic, sicer bi ga opravilo vsako uro preverjalo.
   - **Samo pravkar zakljucen turnir**: konec turnirja (datum konca, sicer
     zacetka) ni starejsi od 7 dni. Zakljucenega turnirja namrec ne dobimo kot
     dogodek: uvozena zgodovina in turnirji, zakljuceni pred uvedbo paketa, niso
     novica.
   - **Obvestila dobi tisti, ki ga paket do nje upraviciuje**: ob potekli
     narocnini sporocilo ne gre, spremljanje pa ostane.
   - Ni obvestil v zivo med turnirjem (potisna obvestila so svoja storitev,
     PotisnaObvestilaStoritev); to je povzetek po koncu. */
package si.turnirko.storitve;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.ProfilDto;
import si.turnirko.modeli.ObvestiloTurnirja;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.SpremljanIgralec;
import si.turnirko.modeli.Turnir;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.posta.SporociloTurnirja;
import si.turnirko.posta.PostaStoritev;
import si.turnirko.repozitoriji.ObvestiloTurnirjaRepozitorij;
import si.turnirko.repozitoriji.SpremljanIgralecRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

@Service
public class ObvestilaPoTurnirjuStoritev {

    private static final Logger dnevnik = LoggerFactory.getLogger(ObvestilaPoTurnirjuStoritev.class);

    /* Koliko dni po koncu turnirja se obvestilo se poslje. */
    static final int DNI_NAZAJ = 7;

    /* Tekme, ki jih sporocilo naniza pod igralcem: vec jih e-posta ne prenese. */
    private static final int NAJVEC_TEKEM = 8;

    private static final SecureRandom NAKLJUCJE = new SecureRandom();

    private final ObvestiloTurnirjaRepozitorij obvestila;
    private final SpremljanIgralecRepozitorij spremljani;
    private final DruzinaStoritev druzina;
    private final UporabnikRepozitorij uporabnikRepozitorij;
    private final ProfilStoritev profilStoritev;
    private final NarocninaStoritev narocnina;
    private final PostaStoritev posta;
    private final String koren;

    public ObvestilaPoTurnirjuStoritev(ObvestiloTurnirjaRepozitorij obvestila,
                                       SpremljanIgralecRepozitorij spremljani,
                                       DruzinaStoritev druzina,
                                       UporabnikRepozitorij uporabnikRepozitorij,
                                       ProfilStoritev profilStoritev,
                                       NarocninaStoritev narocnina,
                                       PostaStoritev posta,
                                       @Value("${turnirko.stripe.vrni-na}") String koren) {
        this.obvestila = obvestila;
        this.spremljani = spremljani;
        this.druzina = druzina;
        this.uporabnikRepozitorij = uporabnikRepozitorij;
        this.profilStoritev = profilStoritev;
        this.narocnina = narocnina;
        this.posta = posta;
        this.koren = koren.endsWith("/") ? koren.substring(0, koren.length() - 1) : koren;
    }

    /* Transakcija mora biti na metodi, ki jo klice urnik: klic transakcijske metode
       iz istega razreda gre mimo Springovega posrednika in zeton odjave se ne bi
       zapisal (glej opombo ob ObvestilaPoKoluStoritev.urnoPreverjanje). */
    @Transactional
    @Scheduled(cron = "${turnirko.obvestila.turnir-urnik:0 40 8-20 * * *}", zone = "Europe/Ljubljana")
    public void urnoPreverjanje() {
        int poslanih = poslji(LocalDateTime.now());
        if (poslanih > 0) {
            dnevnik.info("Obvestila po turnirju: poslanih {}.", poslanih);
        }
    }

    /* Obdela zakljucene turnirje, ki jih se ni; vrne stevilo poslanih sporocil. */
    @Transactional
    public int poslji(LocalDateTime zdaj) {
        LocalDate danes = zdaj.toLocalDate();
        int poslanih = 0;
        for (Turnir t : obvestila.neobdelani(danes.minusDays(DNI_NAZAJ), danes)) {
            int n = obdelaj(t, zdaj);
            obvestila.save(new ObvestiloTurnirja(t.getId(), zdaj, n));
            poslanih += n;
        }
        return poslanih;
    }

    private int obdelaj(Turnir t, LocalDateTime zdaj) {
        Set<Long> nastopili = new LinkedHashSet<>();
        nastopili.addAll(obvestila.igralciPrijav(t.getId(), Prijava.StatusPrijave.ODJAVLJEN));
        nastopili.addAll(obvestila.soigralciPrijav(t.getId(), Prijava.StatusPrijave.ODJAVLJEN));
        nastopili.addAll(obvestila.igralciKadra(t.getId()));
        if (nastopili.isEmpty()) {
            return 0;
        }

        // racun -> igralci, ki jih spremlja in so nastopili (vrstni red spremljanja)
        Map<Long, List<Long>> poRacunu = new LinkedHashMap<>();
        for (SpremljanIgralec s : spremljevalci(nastopili)) {
            poRacunu.computeIfAbsent(s.getIdRacun(), k -> new ArrayList<>()).add(s.getIdIgralec());
        }

        Map<Long, Optional<SporociloTurnirja.Igralec>> predpomnilnik = new HashMap<>();
        SporociloTurnirja.Turnir turnir = new SporociloTurnirja.Turnir(
                t.getId(), t.getIme(), t.getDatumKonca() != null ? t.getDatumKonca() : t.getDatumZacetka(),
                kraj(t));
        int poslanih = 0;
        for (Map.Entry<Long, List<Long>> vnos : poRacunu.entrySet()) {
            Uporabnik u = uporabnikRepozitorij.findById(vnos.getKey()).orElse(null);
            if (u == null || !ObvestilaPoKoluStoritev.primeren(u) || !narocnina.imaSirokoSpremljanje(u)) {
                continue;
            }
            List<SporociloTurnirja.Igralec> igralci = new ArrayList<>();
            for (Long idIgralec : vnos.getValue()) {
                predpomnilnik.computeIfAbsent(idIgralec, id -> igralecNaTurnirju(id, t.getId()))
                        .ifPresent(igralci::add);
            }
            if (igralci.isEmpty()) {
                continue;
            }
            poslji(u, turnir, igralci);
            poslanih++;
        }
        return poslanih;
    }

    /* Racuni, ki igralca spremljajo ali ga imajo v druzini (V59): zapisi (racun, igralec),
       vsak par enkrat. */
    List<SpremljanIgralec> spremljevalci(Set<Long> nastopili) {
        List<SpremljanIgralec> vsi = new ArrayList<>(spremljani.findByIdIgralecIn(nastopili));
        for (var p : druzina.veljavnePovezaveIgralcev(nastopili)) {
            boolean ze = vsi.stream().anyMatch(s -> s.getIdRacun().equals(p.getIdRacun())
                    && s.getIdIgralec().equals(p.getIdIgralec()));
            if (!ze) {
                vsi.add(new SpremljanIgralec(p.getIdRacun(), p.getIdIgralec()));
            }
        }
        return vsi;
    }

    /* Igralec na turnirju z vidika sporocila. Prazno, ce ni odigral nobene tekme
       na njem (prijava brez nastopa sporocila ne opraviciuje). */
    private Optional<SporociloTurnirja.Igralec> igralecNaTurnirju(Long idIgralec, Long idTurnir) {
        ProfilDto profil = profilStoritev.profil(idIgralec);
        List<ProfilDto.TekmovanjeProfila> kategorije = profil.tekmovanja().stream()
                .filter(k -> !k.ligaska() && idTurnir.equals(k.idTurnir()))
                .toList();
        if (kategorije.isEmpty()) {
            return Optional.empty();
        }
        Set<String> kljuci = new LinkedHashSet<>();
        kategorije.forEach(k -> kljuci.add(k.kljuc()));
        List<SporociloTurnirja.Tekma> tekme = profil.tekme().stream()
                .filter(tk -> kljuci.contains(tk.tekmovanjeKljuc()))
                .limit(NAJVEC_TEKEM)
                .map(tk -> new SporociloTurnirja.Tekma(tk.nasprotnik(), tk.niziZa(), tk.niziProti(),
                        tk.zmaga(), tk.spremembaRatinga()))
                .toList();
        List<SporociloTurnirja.Kategorija> vrstice = kategorije.stream()
                .map(k -> new SporociloTurnirja.Kategorija(k.ime(), k.mesto(), k.mestoDo(), k.udelezencev(),
                        k.zmage(), k.porazi(), k.spremembaRatinga()))
                .toList();
        return Optional.of(new SporociloTurnirja.Igralec(
                idIgralec, profil.glava().polnoIme(), profil.glava().klub(), vrstice, tekme));
    }

    private void poslji(Uporabnik u, SporociloTurnirja.Turnir turnir, List<SporociloTurnirja.Igralec> igralci) {
        if (u.getZetonOdjave() == null) {
            u.setZetonOdjave(novZeton());
        }
        SporociloTurnirja.Prejemnik prejemnik = new SporociloTurnirja.Prejemnik(
                koren + "/odjava-obvestil?z=" + u.getZetonOdjave(),
                koren + "/api/v1/obvestila/odjava?z=" + u.getZetonOdjave(), koren);
        posta.poslji(u.getUporabniskoIme(), SporociloTurnirja.sestavi(turnir, igralci, prejemnik));
    }

    private static String kraj(Turnir t) {
        List<String> deli = new ArrayList<>();
        if (t.getDvorana() != null && !t.getDvorana().isBlank()) {
            deli.add(t.getDvorana());
        }
        if (t.getKraj() != null && t.getKraj().getIme() != null) {
            deli.add(t.getKraj().getIme());
        }
        return String.join(", ", deli);
    }

    private static String novZeton() {
        byte[] bajti = new byte[24];
        NAKLJUCJE.nextBytes(bajti);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bajti);
    }
}
