/* Osebni koledar .ics (V58, paket Navijac in Igralec Premium): turnirji in
   ligaska srecanja igralcev, ki jih racun spremlja (in igralca samega), v
   koledarju v telefonu. Koledar se osvezuje sam, zato se prestavitev srecanja
   prenese brez dela: dogodek ima stalen UID (srecanje-{id}, turnir-{id}), nov cas
   ga v koledarju samo premakne.

   Naslov koledarja NIMA prijave - Google in Apple koledar ne znata poslati
   gesla -, zato ga varuje nakljucen zeton racuna (uporabnik.zeton_koledarja).
   Zeton odpre samo ta koledar, nikoli racuna; racun ga zamenja z enim klikom
   (stara povezava preneha delovati). Paket se preveri ob VSAKEM branju: po
   poteku narocnine koledar ostane v telefonu, a se neha posodabljati (403).

   V koledar gredo samo javni podatki (kdo igra s kom in kdaj) - isti, kot jih
   vidi vsak gost na strani lige in turnirja. */
package si.turnirko.storitve;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.izjeme.PrepovedanoIzjema;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Liga;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.Srecanje;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.Turnir;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.pomozno.Ics;
import si.turnirko.repozitoriji.IgralecRepozitorij;
import si.turnirko.repozitoriji.KaderEkipeRepozitorij;
import si.turnirko.repozitoriji.LigaRepozitorij;
import si.turnirko.repozitoriji.PrijavaRepozitorij;
import si.turnirko.repozitoriji.SpremljanIgralecRepozitorij;
import si.turnirko.repozitoriji.SrecanjeRepozitorij;
import si.turnirko.repozitoriji.TurnirRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

@Service
public class OsebniKoledarStoritev {

    private static final SecureRandom NAKLJUCJE = new SecureRandom();

    /* Koliko dni nazaj ostane dogodek v koledarju: gledalec rad pogleda, kdaj je
       bila zadnja tekma, naprej pa koledar ni arhiv. */
    private static final int DNI_NAZAJ = 14;

    /* Ligasko srecanje traja okoli treh ur (SNTL: do deset tekem). */
    private static final int URE_SRECANJA = 3;

    private static final int OSVEZITEV_UR = 6;

    private final UporabnikRepozitorij uporabnikRepozitorij;
    private final SpremljanIgralecRepozitorij spremljaniRepozitorij;
    private final IgralecRepozitorij igralecRepozitorij;
    private final LigaRepozitorij ligaRepozitorij;
    private final SrecanjeRepozitorij srecanjeRepozitorij;
    private final KaderEkipeRepozitorij kaderRepozitorij;
    private final PrijavaRepozitorij prijavaRepozitorij;
    private final TurnirRepozitorij turnirRepozitorij;
    private final NarocninaStoritev narocnina;
    private final LastnistvoStoritev lastnistvo;
    private final DruzinaStoritev druzina;
    private final String koren;

    public OsebniKoledarStoritev(UporabnikRepozitorij uporabnikRepozitorij,
                                 SpremljanIgralecRepozitorij spremljaniRepozitorij,
                                 IgralecRepozitorij igralecRepozitorij,
                                 LigaRepozitorij ligaRepozitorij,
                                 SrecanjeRepozitorij srecanjeRepozitorij,
                                 KaderEkipeRepozitorij kaderRepozitorij,
                                 PrijavaRepozitorij prijavaRepozitorij,
                                 TurnirRepozitorij turnirRepozitorij,
                                 NarocninaStoritev narocnina,
                                 LastnistvoStoritev lastnistvo,
                                 DruzinaStoritev druzina,
                                 @Value("${turnirko.javni-naslov:https://turnirko-nt.si}") String koren) {
        this.uporabnikRepozitorij = uporabnikRepozitorij;
        this.spremljaniRepozitorij = spremljaniRepozitorij;
        this.igralecRepozitorij = igralecRepozitorij;
        this.ligaRepozitorij = ligaRepozitorij;
        this.srecanjeRepozitorij = srecanjeRepozitorij;
        this.kaderRepozitorij = kaderRepozitorij;
        this.prijavaRepozitorij = prijavaRepozitorij;
        this.turnirRepozitorij = turnirRepozitorij;
        this.narocnina = narocnina;
        this.lastnistvo = lastnistvo;
        this.druzina = druzina;
        this.koren = koren.endsWith("/") ? koren.substring(0, koren.length() - 1) : koren;
    }

    // ---------- Zeton racuna ----------

    /* Zeton prijavljenega racuna; nastane ob prvi zahtevi. Samo paket, ki koledar
       vsebuje - sicer bi racun dobil povezavo, ki jo koledar takoj zavrne. */
    @Transactional
    public String zeton() {
        Uporabnik jaz = zahtevajPaket();
        if (jaz.getZetonKoledarja() == null) {
            jaz.setZetonKoledarja(novZeton());
            uporabnikRepozitorij.save(jaz);
        }
        return jaz.getZetonKoledarja();
    }

    /* Nov zeton: stara povezava neha delovati (kdor jo je delil ali izgubil telefon). */
    @Transactional
    public String zamenjajZeton() {
        Uporabnik jaz = zahtevajPaket();
        jaz.setZetonKoledarja(novZeton());
        uporabnikRepozitorij.save(jaz);
        return jaz.getZetonKoledarja();
    }

    private Uporabnik zahtevajPaket() {
        Uporabnik jaz = lastnistvo.trenutni();
        if (jaz == null) {
            throw new PrepovedanoIzjema("Za osebni koledar je potrebna prijava.");
        }
        if (!narocnina.imaSirokoSpremljanje(jaz)) {
            throw new PrepovedanoIzjema("Osebni koledar je del paketa Navijač ali Igralec Premium.");
        }
        return jaz;
    }

    private static String novZeton() {
        byte[] bajti = new byte[24];
        NAKLJUCJE.nextBytes(bajti);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bajti);
    }

    // ---------- Koledar ----------

    /* Vsebina koledarja .ics za zeton. 404 za neznan zeton, 403 za racun brez
       veljavnega paketa (ali ugasnjen racun). */
    @Transactional(readOnly = true)
    public String koledar(String zeton) {
        Uporabnik u = uporabnikRepozitorij.findByZetonKoledarja(zeton)
                .orElseThrow(() -> new NiNajdenoIzjema("Koledar ne obstaja."));
        if (!u.isAktiven() || u.getStatus() == StatusRacuna.ZAVRNJEN || !narocnina.imaSirokoSpremljanje(u)) {
            throw new PrepovedanoIzjema("Koledar ni na voljo: paket Navijač ali Igralec Premium ni veljaven.");
        }
        return sestavi(u, LocalDateTime.now());
    }

    String sestavi(Uporabnik u, LocalDateTime zdaj) {
        LocalDate od = zdaj.toLocalDate().minusDays(DNI_NAZAJ);
        List<Igralec> igralci = igralci(u);

        Ics ics = Ics.koledar("Turnirko · moji igralci", OSVEZITEV_UR);
        srecanja(igralci, od).values().stream()
                .sorted(Comparator.comparing(s -> s.srecanje().getPredvidenZacetek()))
                .forEach(s -> srecanje(ics, s, zdaj));
        turnirji(igralci, od).values().stream()
                .sorted(Comparator.comparing((TurnirZaKoledar t) -> t.turnir().getDatumZacetka())
                        .thenComparing(t -> t.turnir().getId()))
                .forEach(t -> turnir(ics, t, zdaj));
        return ics.zakljuci();
    }

    /* Igralci, katerih termini gredo v koledar: spremljani in igralec sam. */
    List<Igralec> igralci(Uporabnik u) {
        Set<Long> idji = new LinkedHashSet<>(spremljaniRepozitorij.idjiZaRacun(u.getId()));
        if (u.jePotrjenIgralec()) {
            idji.add(u.getIgralec().getId());
        }
        idji.addAll(dodatniIgralci(u));
        return igralecRepozitorij.findAllById(idji);
    }

    /* Igralci, ki jih racun dobi na drug nacin kot s spremljanjem: druzina (V59). */
    Set<Long> dodatniIgralci(Uporabnik u) {
        return druzina.igralciDruzine(u);
    }

    private record SrecanjeZaKoledar(Srecanje srecanje, Liga liga, Set<String> igralci) {}

    private record TurnirZaKoledar(Turnir turnir, Set<String> igralci) {}

    /* Ligaska srecanja ekip, v katerih nastopajo igralci (nezakljucene lige),
       s predvidenim zacetkom in ne starejsa od `od`. Isto srecanje dveh
       spremljanih igralcev je en dogodek z obema imenoma. */
    private Map<Long, SrecanjeZaKoledar> srecanja(List<Igralec> igralci, LocalDate od) {
        Map<Long, SrecanjeZaKoledar> poId = new LinkedHashMap<>();
        for (Igralec igralec : igralci) {
            for (Liga liga : ligaRepozitorij.najdiNezakljuceneZaIgralca(igralec.getId())) {
                Set<Long> ekipe = new HashSet<>(kaderRepozitorij.ekipeIgralcaVLigi(liga.getId(), igralec.getId()));
                for (Srecanje s : srecanjeRepozitorij.najdiZaLigo(liga.getId())) {
                    boolean njegovo = ekipe.contains(s.getEkipaDomaci().getId())
                            || ekipe.contains(s.getEkipaGost().getId());
                    LocalDateTime zacetek = s.getPredvidenZacetek();
                    if (!njegovo || s.isCakaNaTermin() || zacetek == null
                            || zacetek.toLocalDate().isBefore(od)) {
                        continue;
                    }
                    poId.computeIfAbsent(s.getId(),
                            id -> new SrecanjeZaKoledar(s, liga, new TreeSet<>()))
                            .igralci().add(igralec.polnoIme());
                }
            }
        }
        return poId;
    }

    /* Turnirji, na katere je igralec prijavljen (posamicno, v paru ali v kadru
       ekipe) in imajo znan datum, ki ni starejsi od `od`. */
    private Map<Long, TurnirZaKoledar> turnirji(List<Igralec> igralci, LocalDate od) {
        Map<Long, TurnirZaKoledar> poId = new LinkedHashMap<>();
        for (Igralec igralec : igralci) {
            Set<Long> idji = new HashSet<>(
                    prijavaRepozitorij.turnirjiIgralca(igralec.getId(), Prijava.StatusPrijave.ODJAVLJEN));
            idji.addAll(kaderRepozitorij.ekipniTurnirjiIgralca(igralec.getId()));
            for (Turnir t : turnirRepozitorij.findAllById(idji)) {
                LocalDate konec = t.getDatumKonca() != null ? t.getDatumKonca() : t.getDatumZacetka();
                if (t.getDatumZacetka() == null || konec.isBefore(od)) {
                    continue;
                }
                poId.computeIfAbsent(t.getId(), id -> new TurnirZaKoledar(t, new TreeSet<>()))
                        .igralci().add(igralec.polnoIme());
            }
        }
        return poId;
    }

    /* Ura 00:00 pomeni, da je organizator ni vpisal: dogodek je tedaj cel dan. */
    private void srecanje(Ics ics, SrecanjeZaKoledar s, LocalDateTime zdaj) {
        Srecanje sr = s.srecanje();
        String povzetek = sr.getEkipaDomaci().prikazanoIme() + " – " + sr.getEkipaGost().prikazanoIme();
        String opis = s.liga().getIme() + " · " + sr.getKolo() + ". kolo\nIgralci: "
                + String.join(", ", s.igralci());
        String povezava = koren + "/srecanja/" + sr.getId();
        String uid = "srecanje-" + sr.getId() + "@turnirko-nt.si";
        LocalDateTime zacetek = sr.getPredvidenZacetek();
        if (zacetek.toLocalTime().equals(LocalTime.MIDNIGHT)) {
            ics.celDan(uid, zdaj, zacetek.toLocalDate(), zacetek.toLocalDate(), povzetek, opis, null, povezava);
        } else {
            ics.dogodek(uid, zdaj, zacetek, zacetek.plusHours(URE_SRECANJA), povzetek, opis, null, povezava);
        }
    }

    private void turnir(Ics ics, TurnirZaKoledar t, LocalDateTime zdaj) {
        Turnir turnir = t.turnir();
        LocalDate konec = turnir.getDatumKonca() != null ? turnir.getDatumKonca() : turnir.getDatumZacetka();
        List<String> kraj = new ArrayList<>();
        if (turnir.getDvorana() != null && !turnir.getDvorana().isBlank()) {
            kraj.add(turnir.getDvorana());
        }
        if (turnir.getKraj() != null && turnir.getKraj().getIme() != null) {
            kraj.add(turnir.getKraj().getIme());
        }
        ics.celDan("turnir-" + turnir.getId() + "@turnirko-nt.si", zdaj, turnir.getDatumZacetka(), konec,
                turnir.getIme(), "Prijavljen: " + String.join(", ", t.igralci()),
                String.join(", ", kraj), koren + "/turnirji/" + turnir.getId());
    }
}
