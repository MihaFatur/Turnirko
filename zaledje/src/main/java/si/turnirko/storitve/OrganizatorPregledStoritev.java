/* Organizatorski pregled: vse, kar organizator na eni strani potrebuje - kaj
   caka njegovo dejanje, njegova tekmovanja tekoce sezone, prihajajoci termini,
   arhiv sezon in poraba paketa (glej OrganizatorPregledDto).

   MERILA, ki jih ne razbij:
   - »Moja« tekmovanja so tista, ki jih je racun USTVARIL - isto merilo, po
     katerem NarocninaStoritev steje mejo paketa. Klubska tekmovanja drugih
     organizatorjev istega kluba so urejevalcu dostopna (LastnistvoStoritev), a
     v pregledu jih ni: pregled kaze porabo LASTNEGA paketa, in ce bi vrstice
     seznama stele drugo mnozico kot palica poleg njega, se stevili ne bi
     ujemali.
   - Poraba paketa (uporabljeno / meja) pride iz NarocninaStoritev, ne iz
     stetja seznama: to je edina stevilka, ki jo strezniku ob ustvarjanju
     res preveri, in pregled mora pokazati natanko njo. Vrstic seznama je lahko
     vec (tekmovanje, ustvarjeno pred 1. julijem, ki se igra v tej sezoni,
     v mejo ne steje).
   - Tekmovanje je v »tekoci« sezoni, ce se v njej IGRA (datum turnirja oz.
     oznaka sezone lige) ALI je bilo v njej USTVARJENO (steje v mejo) ALI
     tece. Tako nobeno tekmovanje, ki ga organizator placuje ali vodi prav
     zdaj, ne izpade s seznama, arhiv pa ostane pošten.

   Sezona je ista kot pri starostnem pasu in meji paketa (1. julij, Sezona). */
package si.turnirko.storitve;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.LigaDto;
import si.turnirko.dto.OrganizatorPregledDto;
import si.turnirko.dto.OrganizatorPregledDto.Arhiv;
import si.turnirko.dto.OrganizatorPregledDto.Caka;
import si.turnirko.dto.OrganizatorPregledDto.Kvota;
import si.turnirko.dto.OrganizatorPregledDto.Termin;
import si.turnirko.dto.OrganizatorPregledDto.Tekmovanje;
import si.turnirko.dto.OrganizatorPregledDto.VrstaCakanja;
import si.turnirko.dto.OrganizatorPregledDto.VrstaNapredka;
import si.turnirko.dto.OrganizatorPregledDto.VrstaTekmovanja;
import si.turnirko.dto.TurnirDto;
import si.turnirko.izjeme.PrepovedanoIzjema;
import si.turnirko.modeli.Liga;
import si.turnirko.modeli.Paket;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.modeli.Sezona;
import si.turnirko.modeli.Srecanje;
import si.turnirko.modeli.StatusTekmovanja;
import si.turnirko.modeli.Turnir;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.pomozno.SlovenskaAbeceda;
import si.turnirko.repozitoriji.DogodekRepozitorij;
import si.turnirko.repozitoriji.KaderEkipeRepozitorij;
import si.turnirko.repozitoriji.LigaRepozitorij;
import si.turnirko.repozitoriji.PrijavaRepozitorij;
import si.turnirko.repozitoriji.SrecanjeRepozitorij;
import si.turnirko.repozitoriji.TekmaRepozitorij;
import si.turnirko.repozitoriji.TekmaSrecanjaRepozitorij;
import si.turnirko.repozitoriji.TurnirRepozitorij;

@Service
public class OrganizatorPregledStoritev {

    /* Okno bloka »Prihaja« (dnevi od danes naprej). */
    static final int DNI_PRIHAJA = 14;

    /* Turnir v pripravi caka zreb, ko se zacne v tolikih dneh ali prej. Pred tem
       prijave se prihajajo in zreb ni nujen. */
    static final int DNI_PRED_ZACETKOM_ZREBA = 7;

    /* Najvec parov v opisu vrstice »zapisniki cakajo«; vec bi vrstico razdrlo. */
    private static final int NAJVEC_PAROV_V_OPISU = 2;

    /* »2026/27« ali »2026/2027« - oznaka sezone, kot jo vpise organizator. */
    private static final Pattern OZNAKA_SEZONE =
            Pattern.compile("^\\s*(\\d{4})\\s*/\\s*(\\d{2}|\\d{4})\\s*$");

    private final LastnistvoStoritev lastnistvo;
    private final NarocninaStoritev narocnina;
    private final PovzetkiStoritev povzetki;
    private final LigaStoritev ligaStoritev;
    private final TurnirRepozitorij turnirRepozitorij;
    private final LigaRepozitorij ligaRepozitorij;
    private final DogodekRepozitorij dogodekRepozitorij;
    private final TekmaRepozitorij tekmaRepozitorij;
    private final SrecanjeRepozitorij srecanjeRepozitorij;
    private final TekmaSrecanjaRepozitorij tekmaSrecanjaRepozitorij;
    private final PrijavaRepozitorij prijavaRepozitorij;
    private final KaderEkipeRepozitorij kaderRepozitorij;

    public OrganizatorPregledStoritev(LastnistvoStoritev lastnistvo,
                                      NarocninaStoritev narocnina,
                                      PovzetkiStoritev povzetki,
                                      LigaStoritev ligaStoritev,
                                      TurnirRepozitorij turnirRepozitorij,
                                      LigaRepozitorij ligaRepozitorij,
                                      DogodekRepozitorij dogodekRepozitorij,
                                      TekmaRepozitorij tekmaRepozitorij,
                                      SrecanjeRepozitorij srecanjeRepozitorij,
                                      TekmaSrecanjaRepozitorij tekmaSrecanjaRepozitorij,
                                      PrijavaRepozitorij prijavaRepozitorij,
                                      KaderEkipeRepozitorij kaderRepozitorij) {
        this.lastnistvo = lastnistvo;
        this.narocnina = narocnina;
        this.povzetki = povzetki;
        this.ligaStoritev = ligaStoritev;
        this.turnirRepozitorij = turnirRepozitorij;
        this.ligaRepozitorij = ligaRepozitorij;
        this.dogodekRepozitorij = dogodekRepozitorij;
        this.tekmaRepozitorij = tekmaRepozitorij;
        this.srecanjeRepozitorij = srecanjeRepozitorij;
        this.tekmaSrecanjaRepozitorij = tekmaSrecanjaRepozitorij;
        this.prijavaRepozitorij = prijavaRepozitorij;
        this.kaderRepozitorij = kaderRepozitorij;
    }

    /* Pregled prijavljenega organizatorja. Admin paketa in svojih tekmovanj v tem
       pomenu nima, zato je pregled samo za organizatorja. */
    @Transactional(readOnly = true)
    public OrganizatorPregledDto pregled() {
        Uporabnik jaz = lastnistvo.trenutni();
        if (jaz == null || jaz.getVloga() != Vloga.ORGANIZATOR) {
            throw new PrepovedanoIzjema("Organizatorski pregled je samo za organizatorja.");
        }
        return pregled(jaz, LocalDate.now());
    }

    /* Dan je parameter, da se sezonski rez in okna (14 dni, 7 dni do zreba) dajo
       preizkusiti brez vrtenja ure. */
    OrganizatorPregledDto pregled(Uporabnik jaz, LocalDate danes) {
        String sezona = Sezona.oznaka(danes);
        List<Turnir> turnirji = turnirRepozitorij.najdiZaUstvarjalca(jaz.getId());
        List<Liga> lige = ligaRepozitorij.najdiZaUstvarjalca(jaz.getId());
        List<Long> idjiTurnirjev = turnirji.stream().map(Turnir::getId).toList();
        List<Long> idjiLig = lige.stream().map(Liga::getId).toList();

        /* Sezona, pod katero tekmovanje stoji: tekoca, ce sodi na seznam tekoce
           sezone (glej merila zgoraj), sicer sezona, v kateri se igra. */
        Map<Long, String> sezonaTurnirjev = new HashMap<>();
        for (Turnir t : turnirji) {
            boolean tekoce = sezonaIgranja(t).equals(sezona)
                    || Sezona.oznaka(t.getUstvarjenOb().toLocalDate()).equals(sezona)
                    || t.getStatus() == StatusTekmovanja.V_TEKU;
            sezonaTurnirjev.put(t.getId(), tekoce ? sezona : sezonaIgranja(t));
        }
        Map<Long, String> sezonaLig = new HashMap<>();
        for (Liga l : lige) {
            boolean tekoce = sezonaIgranja(l).equals(sezona)
                    || Sezona.oznaka(l.getUstvarjenOb().toLocalDate()).equals(sezona)
                    || l.getStatus() == StatusTekmovanja.V_TEKU;
            sezonaLig.put(l.getId(), tekoce ? sezona : sezonaIgranja(l));
        }

        Map<Long, TurnirDto.Stevci> stevci = idjiTurnirjev.isEmpty()
                ? Map.of() : povzetki.zaVseTurnirje();
        Map<Long, LocalDate> roki = najzgodnejsiRoki(idjiTurnirjev);
        Map<Long, LocalDate> naslednjeSrecanjeLige = naslednjaSrecanjaLig(idjiLig, danes);

        // ---------- Caka te ----------
        List<Long> aktivniTurnirji = turnirji.stream()
                .filter(t -> t.getStatus() != StatusTekmovanja.ZAKLJUCEN).map(Turnir::getId).toList();
        Map<Long, RezultatiTurnirja> rezultati = cakajoRezultati(aktivniTurnirji);
        List<Long> ligeVTeku = lige.stream()
                .filter(l -> l.getStatus() == StatusTekmovanja.V_TEKU).map(Liga::getId).toList();
        Map<Long, ZapisnikiLige> zapisniki = cakajoZapisniki(ligeVTeku, danes);

        List<Caka> caka = new ArrayList<>();
        Map<Long, TurnirDto.Potek> poteki = rezultati.isEmpty() ? Map.of() : povzetki.potekiVsehTurnirjev();
        for (Turnir t : turnirji) {
            RezultatiTurnirja r = rezultati.get(t.getId());
            if (r != null) {
                TurnirDto.Potek potek = poteki.get(t.getId());
                caka.add(new Caka(VrstaCakanja.REZULTATI, r.tekem(), VrstaTekmovanja.TURNIR, t.getId(),
                        t.getIme(), null, null, r.dogodki(), potek == null ? null : potek.faza(),
                        r.idNajzasedenegaDogodka(), null));
            }
        }
        for (Liga l : lige) {
            ZapisnikiLige z = zapisniki.get(l.getId());
            if (z != null) {
                caka.add(new Caka(VrstaCakanja.ZAPISNIKI, z.srecanj(), VrstaTekmovanja.LIGA, l.getId(),
                        l.getIme(), z.kolo(), z.datum(), z.pari(), null, null, z.idPrvegaSrecanja()));
            }
        }
        Set<Long> cakajoZreb = new HashSet<>();
        for (Turnir t : turnirji) {
            TurnirDto.Stevci s = stevci.getOrDefault(t.getId(), TurnirDto.Stevci.PRAZNI);
            if (cakaZreb(t, s, roki.get(t.getId()), danes)) {
                cakajoZreb.add(t.getId());
                caka.add(new Caka(VrstaCakanja.ZREB, s.prijavljenihSkupaj(), VrstaTekmovanja.TURNIR,
                        t.getId(), t.getIme(), null, t.getDatumZacetka(), List.of(), null, null, null));
            }
        }
        caka.sort(Comparator.comparing((Caka c) -> c.vrsta() == VrstaCakanja.ZREB)
                .thenComparing(Comparator.comparingInt(Caka::stevilo).reversed())
                .thenComparing(Caka::imeTekmovanja, SlovenskaAbeceda.RED));

        // ---------- Tekmovanja ----------
        Map<Long, LigaDto> ligaDto = new HashMap<>();
        for (Liga l : lige) {
            ligaDto.put(l.getId(), ligaStoritev.najdi(l.getId()));
        }
        List<Tekmovanje> tekmovanja = new ArrayList<>();
        List<Turnir> turnirjiPoDatumu = new ArrayList<>(turnirji);
        turnirjiPoDatumu.sort(Comparator
                .comparing(Turnir::getDatumZacetka, Comparator.nullsLast(Comparator.<LocalDate>naturalOrder()))
                .thenComparing(Turnir::getId));
        List<Liga> ligePoStanju = new ArrayList<>(lige);
        ligePoStanju.sort(Comparator.comparingInt((Liga l) -> vrstniRedStatusa(l.getStatus()))
                .thenComparing(Liga::getIme, SlovenskaAbeceda.RED));
        for (Liga l : ligePoStanju) {
            tekmovanja.add(vrsticaLige(l, ligaDto.get(l.getId()), zapisniki.get(l.getId()),
                    naslednjeSrecanjeLige.get(l.getId()), sezonaLig.get(l.getId())));
        }
        for (Turnir t : turnirjiPoDatumu) {
            tekmovanja.add(vrsticaTurnirja(t, stevci.getOrDefault(t.getId(), TurnirDto.Stevci.PRAZNI),
                    rezultati.get(t.getId()), cakajoZreb.contains(t.getId()), roki.get(t.getId()), danes,
                    sezonaTurnirjev.get(t.getId())));
        }

        // ---------- Stevilke po sezonah (tekoca in arhiv) ----------
        Map<String, Set<Long>> igralciPoSezonah = new HashMap<>();
        Map<String, Integer> tekmePoSezonah = new HashMap<>();
        Map<String, int[]> stPoSezonah = new HashMap<>(); // [lig, turnirjev]
        for (Turnir t : turnirji) {
            String s = sezonaTurnirjev.get(t.getId());
            stPoSezonah.computeIfAbsent(s, k -> new int[2])[1]++;
            tekmePoSezonah.merge(s, stevci.getOrDefault(t.getId(), TurnirDto.Stevci.PRAZNI).odigranihTekem(),
                    Integer::sum);
        }
        for (Liga l : lige) {
            stPoSezonah.computeIfAbsent(sezonaLig.get(l.getId()), k -> new int[2])[0]++;
        }
        for (Object[] v : idjiLig.isEmpty() ? List.<Object[]>of()
                : tekmaSrecanjaRepozitorij.odigranePoLigah(idjiLig)) {
            tekmePoSezonah.merge(sezonaLig.get(((Number) v[0]).longValue()),
                    ((Number) v[1]).intValue(), Integer::sum);
        }
        dodajIgralce(igralciPoSezonah, sezonaTurnirjev, idjiTurnirjev, sezonaLig, idjiLig);

        int[] tekoca = stPoSezonah.getOrDefault(sezona, new int[2]);
        int udelezencev = igralciPoSezonah.getOrDefault(sezona, Set.of()).size();
        int odigranihTekem = tekmePoSezonah.getOrDefault(sezona, 0);

        List<Arhiv> arhiv = new ArrayList<>();
        stPoSezonah.entrySet().stream()
                .filter(e -> e.getKey().compareTo(sezona) < 0)
                .sorted(Map.Entry.<String, int[]>comparingByKey().reversed())
                .forEach(e -> arhiv.add(new Arhiv(e.getKey(), e.getValue()[0], e.getValue()[1],
                        igralciPoSezonah.getOrDefault(e.getKey(), Set.of()).size(),
                        tekmePoSezonah.getOrDefault(e.getKey(), 0))));

        // ---------- Prihaja ----------
        List<Termin> termini = prihajajoci(turnirji, lige, idjiLig, ligeVTeku, danes);
        Termin naslednje = termini.isEmpty() ? null : termini.get(0);
        List<Termin> prihaja = termini.stream()
                .filter(t -> !t.datum().isAfter(danes.plusDays(DNI_PRIHAJA))).toList();

        // ---------- Paket ----------
        Optional<Paket> paket = narocnina.paketOrganizatorja(jaz);
        Kvota kvotaLig = null;
        Kvota kvotaTurnirjev = null;
        if (paket.isPresent()) {
            Optional<NarocninaStoritev.Omejitev> omejitev = narocnina.omejitev(paket.get());
            if (omejitev.isPresent()) {
                kvotaLig = new Kvota((int) narocnina.steviloLigVSezoni(jaz.getId(), danes),
                        omejitev.get().lig());
                kvotaTurnirjev = new Kvota((int) narocnina.steviloTurnirjevVSezoni(jaz.getId(), danes),
                        omejitev.get().turnirjev());
            }
        }

        String najzgodnejsa = null;
        for (String s : stPoSezonah.keySet()) {
            if (najzgodnejsa == null || s.compareTo(najzgodnejsa) < 0) {
                najzgodnejsa = s;
            }
        }
        // Sezona, v katero je bilo tekmovanje USTVARJENO, je lahko starejsa od
        // sezone, v kateri se igra - »organizira od« pove, kdaj je zacel.
        for (Turnir t : turnirji) {
            String s = Sezona.oznaka(t.getUstvarjenOb().toLocalDate());
            if (najzgodnejsa == null || s.compareTo(najzgodnejsa) < 0) {
                najzgodnejsa = s;
            }
        }
        for (Liga l : lige) {
            String s = Sezona.oznaka(l.getUstvarjenOb().toLocalDate());
            if (najzgodnejsa == null || s.compareTo(najzgodnejsa) < 0) {
                najzgodnejsa = s;
            }
        }

        return new OrganizatorPregledDto(
                imeOrganizatorja(jaz),
                jaz.getKlub() != null ? jaz.getKlub().getIme() : null,
                sezona,
                najzgodnejsa,
                turnirji.size() + lige.size(),
                paket.orElse(null),
                kvotaLig,
                kvotaTurnirjev,
                Sezona.naslednjaOd(danes),
                udelezencev,
                odigranihTekem,
                tekmovanja,
                caka,
                prihaja,
                naslednje,
                arhiv);
    }

    // ---------- Sezona tekmovanja ----------

    /* Sezona, v kateri se turnir IGRA; brez datuma velja sezona nastanka. */
    static String sezonaIgranja(Turnir t) {
        LocalDate dan = t.getDatumZacetka() != null ? t.getDatumZacetka() : t.getUstvarjenOb().toLocalDate();
        return Sezona.oznaka(dan);
    }

    /* Liga sezono nosi kot besedilo, ki ga vpise organizator (»2026/27«).
       Kar tega zapisa ne ustreza, se bere iz datuma prvega kola oz. nastanka. */
    static String sezonaIgranja(Liga l) {
        if (l.getSezona() != null) {
            Matcher m = OZNAKA_SEZONE.matcher(l.getSezona());
            if (m.matches()) {
                int leto = Integer.parseInt(m.group(1));
                return leto + "/" + String.format("%02d", (leto + 1) % 100);
            }
        }
        LocalDate dan = l.getZacetekPrvegaKola() != null
                ? l.getZacetekPrvegaKola().toLocalDate() : l.getUstvarjenOb().toLocalDate();
        return Sezona.oznaka(dan);
    }

    private static int vrstniRedStatusa(StatusTekmovanja status) {
        return switch (status) {
            case V_TEKU -> 0;
            case PRIPRAVA -> 1;
            case ZAKLJUCEN -> 2;
        };
    }

    private static String imeOrganizatorja(Uporabnik jaz) {
        String ime = jaz.getPrijavljenoIme() == null ? "" : jaz.getPrijavljenoIme().trim();
        String priimek = jaz.getPrijavljeniPriimek() == null ? "" : jaz.getPrijavljeniPriimek().trim();
        String polno = (ime + " " + priimek).trim();
        return polno.isEmpty() ? null : polno;
    }

    // ---------- Vrstice seznama ----------

    private Tekmovanje vrsticaTurnirja(Turnir t, TurnirDto.Stevci s, RezultatiTurnirja rezultati,
                                       boolean cakaZreb, LocalDate rok, LocalDate danes, String sezona) {
        String kraj = t.getDvorana() != null && !t.getDvorana().isBlank()
                ? t.getDvorana() : (t.getKraj() != null ? t.getKraj().getIme() : null);
        VrstaNapredka napredek;
        int trenutno;
        int vseh;
        int delez;
        VrstaCakanja cakaVrsta = null;
        Integer cakaStevilo = null;
        LocalDate cakaDatum = null;

        switch (t.getStatus()) {
            case ZAKLJUCEN -> {
                napredek = VrstaNapredka.KONCANO;
                trenutno = s.odigranihTekem();
                vseh = s.vsehTekem();
                delez = 100;
                cakaVrsta = t.getRaven() == RavenTekmovanja.NE_STEJE
                        ? VrstaCakanja.NE_STEJE_V_RATING : VrstaCakanja.RATING_OBRACUNAN;
            }
            case V_TEKU -> {
                napredek = VrstaNapredka.TEKME;
                trenutno = s.odigranihTekem();
                vseh = s.vsehTekem();
                delez = vseh > 0 ? (int) Math.round(trenutno * 100.0 / vseh) : 0;
                if (rezultati != null) {
                    cakaVrsta = VrstaCakanja.REZULTATI;
                    cakaStevilo = rezultati.tekem();
                }
            }
            default -> {
                /* Turnir nima omejitve mest, zato ni »41 / 48«: palica meri, koliko
                   okna za prijave je minilo (od nastanka do roka prijave, brez
                   njega do zacetka), stevilka ob njej so prijavljeni. */
                napredek = VrstaNapredka.PRIJAVE;
                trenutno = s.prijavljenihSkupaj();
                vseh = 0;
                LocalDate konec = rok != null ? rok : t.getDatumZacetka();
                delez = delezOkna(t.getUstvarjenOb().toLocalDate(), konec, danes);
                if (cakaZreb) {
                    cakaVrsta = VrstaCakanja.ZREB;
                } else if (rok != null) {
                    cakaVrsta = VrstaCakanja.ROK_PRIJAVE;
                    cakaDatum = rok;
                } else if (t.getDatumZacetka() != null) {
                    cakaVrsta = VrstaCakanja.ZACETEK;
                    cakaDatum = t.getDatumZacetka();
                }
            }
        }
        return new Tekmovanje(VrstaTekmovanja.TURNIR, t.getId(), t.getIme(), t.getStatus(), sezona,
                kraj, t.getDatumZacetka(), t.getDatumKonca(), s.prijavljenihSkupaj(), null,
                napredek, trenutno, vseh, delez, cakaVrsta, cakaStevilo, cakaDatum);
    }

    private Tekmovanje vrsticaLige(Liga l, LigaDto d, ZapisnikiLige zapisniki, LocalDate naslednje,
                                   String sezona) {
        VrstaNapredka napredek;
        int trenutno;
        int vseh;
        int delez;
        VrstaCakanja cakaVrsta = null;
        Integer cakaStevilo = null;
        LocalDate cakaDatum = null;

        switch (l.getStatus()) {
            case ZAKLJUCEN -> {
                napredek = VrstaNapredka.KONCANO;
                trenutno = d.odigranihKol();
                vseh = d.steviloKol();
                delez = 100;
            }
            case V_TEKU -> {
                /* »Kolo 3 / 18« je kolo, ki tece: odigranih je za eno manj. Palica
                   sledi napisu, ne odigranim kolom - obe sta iste stevilke. */
                napredek = VrstaNapredka.KOLO;
                vseh = d.steviloKol();
                trenutno = vseh == 0 ? 0 : Math.min(d.odigranihKol() + 1, vseh);
                delez = vseh > 0 ? (int) Math.round(trenutno * 100.0 / vseh) : 0;
                if (zapisniki != null) {
                    cakaVrsta = VrstaCakanja.ZAPISNIKI;
                    cakaStevilo = zapisniki.srecanj();
                } else if (naslednje != null) {
                    cakaVrsta = VrstaCakanja.NASLEDNJE_SRECANJE;
                    cakaDatum = naslednje;
                }
            }
            default -> {
                napredek = VrstaNapredka.EKIPE;
                trenutno = d.steviloEkip();
                vseh = 0;
                delez = 0;
                if (l.getZacetekPrvegaKola() != null) {
                    cakaVrsta = VrstaCakanja.ZACETEK;
                    cakaDatum = l.getZacetekPrvegaKola().toLocalDate();
                }
            }
        }
        return new Tekmovanje(VrstaTekmovanja.LIGA, l.getId(), l.getIme(), l.getStatus(), sezona,
                null, null, null, d.steviloEkip(), l.isDvokrozno(),
                napredek, trenutno, vseh, delez, cakaVrsta, cakaStevilo, cakaDatum);
    }

    /* Delez okna med `od` in `konec`, ki je do danes ze minil (0-100). */
    static int delezOkna(LocalDate od, LocalDate konec, LocalDate danes) {
        if (konec == null) {
            return 0;
        }
        long skupaj = ChronoUnit.DAYS.between(od, konec);
        if (skupaj <= 0) {
            return danes.isBefore(konec) ? 0 : 100;
        }
        long minilo = ChronoUnit.DAYS.between(od, danes);
        return (int) Math.max(0, Math.min(100, Math.round(minilo * 100.0 / skupaj)));
    }

    // ---------- Caka te ----------

    /* Turnir v pripravi caka zreb, ko ima vsaj dve prijavi, dogodek v pripravi in
       je prijav konec: rok je potekel ali se turnir zacne v tednu dni. Prijave
       vpisuje organizator sam (potrjevanja prijav ni), zato je to edino, kar
       pri turnirju v pripravi res caka njega. */
    static boolean cakaZreb(Turnir t, TurnirDto.Stevci s, LocalDate rok, LocalDate danes) {
        if (t.getStatus() != StatusTekmovanja.PRIPRAVA || s.dogodkovVPripravi() == 0
                || s.prijavljenihSkupaj() < 2) {
            return false;
        }
        boolean zacetekBlizu = t.getDatumZacetka() != null
                && !t.getDatumZacetka().isAfter(danes.plusDays(DNI_PRED_ZACETKOM_ZREBA));
        boolean rokPotekel = rok != null && rok.isBefore(danes);
        return zacetekBlizu || rokPotekel;
    }

    /* Dogodek z najvec cakajocimi tekmami je cilj gumba »Vnesi rezultate«. */
    private record RezultatiTurnirja(int tekem, List<String> dogodki, Long idNajzasedenegaDogodka,
                                     int tekemVNjem) {}

    private Map<Long, RezultatiTurnirja> cakajoRezultati(List<Long> idjiTurnirjev) {
        Map<Long, RezultatiTurnirja> rezultat = new LinkedHashMap<>();
        if (idjiTurnirjev.isEmpty()) {
            return rezultat;
        }
        for (Object[] v : tekmaRepozitorij.cakajoceNaRezultat(idjiTurnirjev)) {
            Long idTurnir = ((Number) v[0]).longValue();
            Long idDogodka = ((Number) v[1]).longValue();
            String dogodek = (String) v[2];
            int tekem = ((Number) v[3]).intValue();
            RezultatiTurnirja prej = rezultat.get(idTurnir);
            List<String> dogodki = prej == null ? new ArrayList<>() : new ArrayList<>(prej.dogodki());
            dogodki.add(dogodek);
            boolean vecji = prej == null || tekem > prej.tekemVNjem();
            rezultat.put(idTurnir, new RezultatiTurnirja((prej == null ? 0 : prej.tekem()) + tekem, dogodki,
                    vecji ? idDogodka : prej.idNajzasedenegaDogodka(),
                    vecji ? tekem : prej.tekemVNjem()));
        }
        return rezultat;
    }

    private record ZapisnikiLige(int srecanj, int kolo, LocalDate datum, List<String> pari,
                                 Long idPrvegaSrecanja) {}

    /* Srecanja, ki bi morala biti do vceraj odigrana in nimajo izida. »Danes« ne
       steje: srecanje danes je se prihodnost (ali v teku) in stoji v »Prihaja«. */
    private Map<Long, ZapisnikiLige> cakajoZapisniki(List<Long> idjiLig, LocalDate danes) {
        Map<Long, ZapisnikiLige> rezultat = new LinkedHashMap<>();
        if (idjiLig.isEmpty()) {
            return rezultat;
        }
        Map<Long, List<Srecanje>> poLigah = new LinkedHashMap<>();
        for (Srecanje s : srecanjeRepozitorij.cakajoNaZapisnik(idjiLig, danes.atStartOfDay())) {
            // zapisi brez ure so krajsi od meje in bi zajeli tudi danasnji dan
            if (s.getPredvidenZacetek().toLocalDate().isBefore(danes)) {
                poLigah.computeIfAbsent(s.getLiga().getId(), k -> new ArrayList<>()).add(s);
            }
        }
        poLigah.forEach((idLiga, srecanja) -> {
            int prvoKolo = srecanja.stream().mapToInt(Srecanje::getKolo).min().orElse(0);
            List<Srecanje> vPrvemKolu = srecanja.stream().filter(s -> s.getKolo() == prvoKolo).toList();
            List<String> pari = vPrvemKolu.stream()
                    .limit(NAJVEC_PAROV_V_OPISU)
                    .map(s -> s.getEkipaDomaci().prikazanoIme() + " : " + s.getEkipaGost().prikazanoIme())
                    .toList();
            LocalDate datum = vPrvemKolu.stream()
                    .map(s -> s.getPredvidenZacetek().toLocalDate()).min(LocalDate::compareTo).orElse(danes);
            rezultat.put(idLiga, new ZapisnikiLige(srecanja.size(), prvoKolo, datum, pari,
                    vPrvemKolu.get(0).getId()));
        });
        return rezultat;
    }

    // ---------- Roki in termini ----------

    private Map<Long, LocalDate> najzgodnejsiRoki(List<Long> idjiTurnirjev) {
        Map<Long, LocalDate> roki = new HashMap<>();
        if (idjiTurnirjev.isEmpty()) {
            return roki;
        }
        for (Object[] v : dogodekRepozitorij.rokiPrijavePoTurnirjih(idjiTurnirjev)) {
            roki.merge(((Number) v[0]).longValue(), (LocalDate) v[1],
                    (a, b) -> a.isBefore(b) ? a : b);
        }
        return roki;
    }

    /* Prvo prihodnje srecanje vsake lige: »Naslednje 11. 10.« v vrstici seznama. */
    private Map<Long, LocalDate> naslednjaSrecanjaLig(List<Long> idjiLig, LocalDate danes) {
        Map<Long, LocalDate> naslednje = new HashMap<>();
        for (Object[] v : prihodnjaKola(idjiLig, danes)) {
            LocalDate dan = ((LocalDateTime) v[2]).toLocalDate();
            naslednje.merge(((Number) v[0]).longValue(), dan, (a, b) -> a.isBefore(b) ? a : b);
        }
        return naslednje;
    }

    /* [idLiga, kolo, predvidenZacetek] od danes naprej. Meja poizvedbe je za dan
       siroka, natancno jo poravna ta metoda (glej SrecanjeRepozitorij). */
    private List<Object[]> prihodnjaKola(List<Long> idjiLig, LocalDate danes) {
        if (idjiLig.isEmpty()) {
            return List.of();
        }
        return srecanjeRepozitorij.prihajajocaKola(idjiLig, danes.minusDays(1).atStartOfDay()).stream()
                .filter(v -> !((LocalDateTime) v[2]).toLocalDate().isBefore(danes))
                .toList();
    }

    /* Vsi prihodnji termini po datumu: turniri (ki se niso koncani) in kola lig v
       teku. Kolo je en dan srecanj: skupina po (liga, kolo), datum je prvega
       srecanja, stevilo pa srecanj tistega dne. */
    private List<Termin> prihajajoci(List<Turnir> turnirji, List<Liga> lige, List<Long> idjiLig,
                                     List<Long> ligeVTeku, LocalDate danes) {
        List<Termin> termini = new ArrayList<>();
        for (Turnir t : turnirji) {
            if (t.getStatus() != StatusTekmovanja.ZAKLJUCEN && t.getDatumZacetka() != null
                    && !t.getDatumZacetka().isBefore(danes)) {
                String kraj = t.getDvorana() != null && !t.getDvorana().isBlank()
                        ? t.getDvorana() : (t.getKraj() != null ? t.getKraj().getIme() : null);
                termini.add(new Termin(VrstaTekmovanja.TURNIR, t.getId(), t.getIme(), t.getDatumZacetka(),
                        kraj, null, null, null));
            }
        }
        Map<Long, String> imenaLig = new HashMap<>();
        lige.forEach(l -> imenaLig.put(l.getId(), l.getIme()));

        Map<String, List<LocalDateTime>> kola = new LinkedHashMap<>();
        for (Object[] v : ligeVTeku.isEmpty() ? List.<Object[]>of() : prihodnjaKola(ligeVTeku, danes)) {
            kola.computeIfAbsent(((Number) v[0]).longValue() + ":" + ((Number) v[1]).intValue(),
                    k -> new ArrayList<>()).add((LocalDateTime) v[2]);
        }
        kola.forEach((kljuc, zacetki) -> {
            String[] deli = kljuc.split(":");
            Long idLiga = Long.valueOf(deli[0]);
            LocalDateTime prvo = zacetki.stream().min(LocalDateTime::compareTo).orElseThrow();
            LocalDate dan = prvo.toLocalDate();
            int srecanj = (int) zacetki.stream().filter(z -> z.toLocalDate().equals(dan)).count();
            LocalTime ura = prvo.toLocalTime().equals(LocalTime.MIDNIGHT) ? null : prvo.toLocalTime();
            termini.add(new Termin(VrstaTekmovanja.LIGA, idLiga, imenaLig.get(idLiga), dan, null,
                    Integer.valueOf(deli[1]), srecanj, ura));
        });
        termini.sort(Comparator.comparing(Termin::datum)
                .thenComparing(t -> t.vrsta() == VrstaTekmovanja.LIGA ? 1 : 0)
                .thenComparing(Termin::imeTekmovanja, SlovenskaAbeceda.RED));
        return termini;
    }

    // ---------- Udelezenci ----------

    /* Razlicni igralci po sezonah: prijave turnirjev (nosilec in soigralec), kadri
       ekipnih dogodkov in kadri lig. Igralec, ki nastopa v dveh tekmovanjih iste
       sezone, se steje enkrat. */
    private void dodajIgralce(Map<String, Set<Long>> poSezonah, Map<Long, String> sezonaTurnirjev,
                              List<Long> idjiTurnirjev, Map<Long, String> sezonaLig, List<Long> idjiLig) {
        List<Object[]> turnirske = new ArrayList<>();
        if (!idjiTurnirjev.isEmpty()) {
            turnirske.addAll(prijavaRepozitorij.igralciTurnirjev(idjiTurnirjev, Prijava.StatusPrijave.ODJAVLJEN));
            turnirske.addAll(prijavaRepozitorij.soigralciTurnirjev(idjiTurnirjev, Prijava.StatusPrijave.ODJAVLJEN));
            turnirske.addAll(kaderRepozitorij.igralciEkipnihTurnirjev(idjiTurnirjev));
        }
        for (Object[] v : turnirske) {
            poSezonah.computeIfAbsent(sezonaTurnirjev.get(((Number) v[0]).longValue()), k -> new HashSet<>())
                    .add(((Number) v[1]).longValue());
        }
        List<Object[]> ligaske = idjiLig.isEmpty() ? List.of() : kaderRepozitorij.igralciLig(idjiLig);
        for (Object[] v : ligaske) {
            poSezonah.computeIfAbsent(sezonaLig.get(((Number) v[0]).longValue()), k -> new HashSet<>())
                    .add(((Number) v[1]).longValue());
        }
    }
}
