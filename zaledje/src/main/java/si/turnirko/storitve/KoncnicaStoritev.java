/* Koncnica lige (play-off) po rednem delu.

   Liga ob nastanku pove, ali ima koncnico: koliko najboljsih ekip jo igra
   (2, 4, 8) in koliko zmag potrebuje ekipa v seriji (liga.koncnicaZmag).
   Koncnica nastane, ko je redni del odigran do zadnjega srecanja - iz
   KONCNE lestvice rednega dela in nikoli prej, ker bi se pari sicer lahko
   zamenjali pod rokami.

   Pravila, ki jih ne razbij:
   - VSI KROGI nastanejo naenkrat, visji s praznimi ekipami. Stran lige tako
     ze prvi dan pokaze celotno pot do finala.
   - PARI prvega kroga sledijo nosilskemu vrstnemu redu mreze turnirja
     (NosilciStoritev.seedVrstniRed): pri stirih 1-4 in 3-2, zato se prva in
     druga ekipa lahko srecata sele v finalu. Zmagovalec para p gre v par
     ceil(p/2) naslednjega kroga - isto pravilo kot izlocilna mreza.
   - VSE TEKME SERIJE nastanejo vnaprej (2 x zmag - 1), da jih organizator
     lahko razpise; ko je serija odlocena, se neodigrane zbrisejo - ni jih bilo
     in ne bodo. Zbrise se samo srecanje v stanju RAZPORED (brez postave).
   - DOMACE PRAVICE: prvo tekmo gosti slabse uvrscena ekipa, zadnjo mogoco
     (odlocilno) bolje uvrscena, vmes se izmenjujeta. Organizator jih pred
     zacetkom tekme lahko zamenja (zamenjajDomacina) - pravila zvez se razlikujejo.
   - TERMIN je last TEKME serije in ne kola (za razliko od rednega dela), zato
     ima koncnica svoj vpis termina.
   - Lestvica rednega dela srecanj koncnice ne steje (LestvicaLigeStoritev). */
package si.turnirko.storitve;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.KoncnicaDto;
import si.turnirko.dto.LestvicaEkipeDto;
import si.turnirko.dto.SrecanjeDto;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.modeli.Ekipa;
import si.turnirko.modeli.Liga;
import si.turnirko.modeli.SerijaKoncnice;
import si.turnirko.modeli.Srecanje;
import si.turnirko.modeli.StatusSrecanja;
import si.turnirko.repozitoriji.EkipaRepozitorij;
import si.turnirko.repozitoriji.LigaRepozitorij;
import si.turnirko.repozitoriji.SerijaKoncniceRepozitorij;
import si.turnirko.repozitoriji.SrecanjeRepozitorij;

@Service
public class KoncnicaStoritev {

    private final LigaRepozitorij ligaRepozitorij;
    private final SerijaKoncniceRepozitorij serijaRepozitorij;
    private final SrecanjeRepozitorij srecanjeRepozitorij;
    private final EkipaRepozitorij ekipaRepozitorij;
    private final LestvicaLigeStoritev lestvicaLigeStoritev;
    private final LastnistvoStoritev lastnistvo;

    public KoncnicaStoritev(LigaRepozitorij ligaRepozitorij,
                            SerijaKoncniceRepozitorij serijaRepozitorij,
                            SrecanjeRepozitorij srecanjeRepozitorij,
                            EkipaRepozitorij ekipaRepozitorij,
                            LestvicaLigeStoritev lestvicaLigeStoritev,
                            LastnistvoStoritev lastnistvo) {
        this.ligaRepozitorij = ligaRepozitorij;
        this.serijaRepozitorij = serijaRepozitorij;
        this.srecanjeRepozitorij = srecanjeRepozitorij;
        this.ekipaRepozitorij = ekipaRepozitorij;
        this.lestvicaLigeStoritev = lestvicaLigeStoritev;
        this.lastnistvo = lastnistvo;
    }

    // ---------- Poimenovanje (deli ga zapisnik srecanja) ----------

    /* Stevilo krogov koncnice z danim stevilom ekip (2 -> 1, 4 -> 2, 8 -> 3). */
    public static int steviloKrogov(int ekip) {
        return Integer.numberOfTrailingZeros(ekip);
    }

    /* Krog se imenuje po oddaljenosti od finala - isto kot izlocilna mreza. */
    public static String imeKroga(int krog, int stKrogov) {
        return switch (stKrogov - krog) {
            case 0 -> "finale";
            case 1 -> "polfinale";
            case 2 -> "četrtfinale";
            default -> krog + ". krog";
        };
    }

    public static String opisTekme(int krog, int stKrogov, Integer tekmaVSeriji) {
        return "končnica · " + imeKroga(krog, stKrogov)
                + (tekmaVSeriji != null ? " · " + tekmaVSeriji + ". tekma" : "");
    }

    // ---------- Branje ----------

    @Transactional(readOnly = true)
    public KoncnicaDto koncnica(Long idLiga) {
        Liga liga = najdiLigo(idLiga);
        if (!liga.imaKoncnico()) {
            throw new NiNajdenoIzjema("Liga " + liga.getIme() + " nima koncnice.");
        }
        List<SerijaKoncnice> serije = serijaRepozitorij.najdiZaLigo(idLiga);
        Map<Long, List<SrecanjeDto>> tekmePoSeriji = new HashMap<>();
        for (Srecanje s : srecanjeRepozitorij.najdiKoncnicoLige(idLiga)) {
            tekmePoSeriji.computeIfAbsent(s.getSerija().getId(), k -> new ArrayList<>())
                    .add(SrecanjeDto.iz(s));
        }
        int stKrogov = steviloKrogov(liga.getKoncnicaEkip());
        List<KoncnicaDto.Serija> izpis = serije.stream()
                .map(sk -> new KoncnicaDto.Serija(
                        sk.getId(), sk.getKrog(), sk.getPar(), imeKroga(sk.getKrog(), stKrogov),
                        stran(sk.getEkipa1(), sk.getMesto1(), sk.getZmage1()),
                        stran(sk.getEkipa2(), sk.getMesto2(), sk.getZmage2()),
                        sk.getZmagovalec() != null ? sk.getZmagovalec().getId() : null,
                        tekmePoSeriji.getOrDefault(sk.getId(), List.of())))
                .toList();
        boolean pripravljena = serije.isEmpty() && liga.getVir() == null && redniDelKoncan(idLiga);
        return new KoncnicaDto(liga.getKoncnicaEkip(), liga.getKoncnicaZmag(), pripravljena, izpis);
    }

    private static KoncnicaDto.Stran stran(Ekipa ekipa, Integer mesto, int zmage) {
        return ekipa == null ? null : new KoncnicaDto.Stran(ekipa.getId(), ekipa.prikazanoIme(), mesto, zmage);
    }

    // ---------- Nastanek ----------

    @Transactional
    public KoncnicaDto ustvari(Long idLiga) {
        lastnistvo.preveriLigaPoId(idLiga);
        Liga liga = najdiLigo(idLiga);
        if (!liga.imaKoncnico()) {
            throw new DomenskaIzjema("Liga po pravilih nima koncnice.");
        }
        if (serijaRepozitorij.existsByLigaId(idLiga)) {
            throw new DomenskaIzjema("Koncnica te lige ze obstaja.");
        }
        List<Srecanje> redna = srecanjeRepozitorij.najdiRednaZaLigo(idLiga);
        long neodigranih = redna.stream().filter(s -> s.getStatus() != StatusSrecanja.KONCANO).count();
        if (redna.isEmpty() || neodigranih > 0) {
            throw new DomenskaIzjema("Koncnica nastane po koncu rednega dela"
                    + (redna.isEmpty() ? " - liga razporeda se nima." : " - neodigranih srecanj je se " + neodigranih + "."));
        }

        int ekip = liga.getKoncnicaEkip();
        List<LestvicaEkipeDto> lestvica = lestvicaLigeStoritev.lestvica(idLiga);
        if (lestvica.size() < ekip) {
            throw new DomenskaIzjema("V koncnici igra " + ekip + " ekip, liga pa jih ima " + lestvica.size() + ".");
        }
        Map<Long, Ekipa> ekipe = new HashMap<>();
        for (Ekipa e : ekipaRepozitorij.najdiZaLigo(idLiga)) {
            ekipe.put(e.getId(), e);
        }

        int stKrogov = steviloKrogov(ekip);
        Map<String, SerijaKoncnice> poMestu = new HashMap<>();
        for (int krog = 1; krog <= stKrogov; krog++) {
            int parov = ekip >> krog;
            for (int par = 1; par <= parov; par++) {
                SerijaKoncnice serija = new SerijaKoncnice(liga, krog, par);
                poMestu.put(krog + "|" + par, serija);
            }
        }

        // prvi krog po nosilskem vrstnem redu: mesta 2(p-1) in 2(p-1)+1
        int[] vrstniRed = NosilciStoritev.seedVrstniRed(ekip);
        for (int par = 1; par <= ekip / 2; par++) {
            LestvicaEkipeDto a = lestvica.get(vrstniRed[2 * (par - 1)] - 1);
            LestvicaEkipeDto b = lestvica.get(vrstniRed[2 * (par - 1) + 1] - 1);
            LestvicaEkipeDto boljsa = a.mesto() < b.mesto() ? a : b;
            LestvicaEkipeDto slabsa = boljsa == a ? b : a;
            SerijaKoncnice serija = poMestu.get("1|" + par);
            serija.nastaviStran(1, ekipe.get(boljsa.idEkipa()), boljsa.mesto());
            serija.nastaviStran(2, ekipe.get(slabsa.idEkipa()), slabsa.mesto());
        }
        List<SerijaKoncnice> vse = serijaRepozitorij.saveAll(poMestu.values());
        for (SerijaKoncnice serija : vse) {
            if (serija.imaObeEkipi()) {
                ustvariTekmeSerije(liga, serija);
            }
        }
        return koncnica(idLiga);
    }

    /* Razveljavi koncnico, dokler se nobena njena tekma ni zacela - za
       koncnico, ustvarjeno prezgodaj ali po popravku rezultata rednega dela. */
    @Transactional
    public void razveljavi(Long idLiga) {
        lastnistvo.preveriLigaPoId(idLiga);
        List<Srecanje> tekme = srecanjeRepozitorij.najdiKoncnicoLige(idLiga);
        for (Srecanje s : tekme) {
            if (s.getStatus() != StatusSrecanja.RAZPORED) {
                throw new DomenskaIzjema("Koncnice ni mogoce razveljaviti: tekma "
                        + s.getEkipaDomaci().prikazanoIme() + " - " + s.getEkipaGost().prikazanoIme()
                        + " se je ze zacela.");
            }
        }
        srecanjeRepozitorij.deleteAll(tekme);
        srecanjeRepozitorij.flush();
        serijaRepozitorij.deleteAll(serijaRepozitorij.najdiZaLigo(idLiga));
    }

    // ---------- Potek ----------

    /* Poklice ga SrecanjeStoritev, ko se tekma serije konca: presteje zmage,
       odloci serijo in zmagovalca postavi v naslednji krog. */
    @Transactional
    public void obKoncanemSrecanju(Long idSrecanje) {
        Srecanje koncano = srecanjeRepozitorij.findById(idSrecanje)
                .orElseThrow(() -> new NiNajdenoIzjema("Srecanje z id " + idSrecanje + " ne obstaja."));
        if (!koncano.jeKoncnica()) {
            return;
        }
        SerijaKoncnice serija = serijaRepozitorij.najdiZLigo(koncano.getSerija().getId()).orElseThrow();
        Liga liga = serija.getLiga();
        prestejZmage(serija);

        int potrebno = liga.getKoncnicaZmag();
        if (serija.jeOdlocena() || (serija.getZmage1() < potrebno && serija.getZmage2() < potrebno)) {
            serijaRepozitorij.save(serija);
            return;
        }
        boolean prva = serija.getZmage1() >= potrebno;
        Ekipa zmagovalec = prva ? serija.getEkipa1() : serija.getEkipa2();
        Integer mesto = prva ? serija.getMesto1() : serija.getMesto2();
        serija.setZmagovalec(zmagovalec);
        serijaRepozitorij.save(serija);

        // tekme, ki jih ni bilo treba odigrati
        List<Srecanje> odvec = srecanjeRepozitorij.najdiZaSerijo(serija.getId()).stream()
                .filter(s -> s.getStatus() == StatusSrecanja.RAZPORED)
                .toList();
        srecanjeRepozitorij.deleteAll(odvec);

        int stKrogov = steviloKrogov(liga.getKoncnicaEkip());
        if (serija.getKrog() >= stKrogov) {
            return; // finale je odloceno
        }
        int naslednjiPar = (serija.getPar() + 1) / 2;
        SerijaKoncnice naslednja = serijaRepozitorij
                .findByLigaIdAndKrogAndPar(liga.getId(), serija.getKrog() + 1, naslednjiPar)
                .orElseThrow();
        naslednja.nastaviStran(serija.getPar() % 2 == 1 ? 1 : 2, zmagovalec, mesto);
        if (naslednja.imaObeEkipi()) {
            uredi(naslednja);
            serijaRepozitorij.save(naslednja);
            ustvariTekmeSerije(liga, naslednja);
        } else {
            serijaRepozitorij.save(naslednja);
        }
    }

    /* Termin posamezne tekme koncnice (redni del ima termine po kolih). */
    @Transactional
    public SrecanjeDto nastaviTermin(Long idSrecanje, LocalDateTime zacetek) {
        lastnistvo.preveriPoSrecanju(idSrecanje);
        Srecanje s = tekmaKoncnice(idSrecanje);
        s.setPredvidenZacetek(zacetek);
        return SrecanjeDto.iz(srecanjeRepozitorij.save(s));
    }

    /* Zamenja domacina in gosta tekme koncnice, ki se se ni zacela. */
    @Transactional
    public SrecanjeDto zamenjajDomacina(Long idSrecanje) {
        lastnistvo.preveriPoSrecanju(idSrecanje);
        Srecanje s = tekmaKoncnice(idSrecanje);
        if (s.getStatus() != StatusSrecanja.RAZPORED) {
            throw new DomenskaIzjema("Domacina je mogoce zamenjati samo pred zacetkom tekme.");
        }
        s.zamenjajStrani();
        return SrecanjeDto.iz(srecanjeRepozitorij.save(s));
    }

    // ---------- Pomozno ----------

    private Srecanje tekmaKoncnice(Long idSrecanje) {
        Srecanje s = srecanjeRepozitorij.najdiPodrobno(idSrecanje)
                .orElseThrow(() -> new NiNajdenoIzjema("Srecanje z id " + idSrecanje + " ne obstaja."));
        if (!s.jeKoncnica()) {
            throw new NeveljavenVnosIzjema("Srecanje ni tekma koncnice - termin rednega dela je last kola.");
        }
        return s;
    }

    /* Bolje uvrscena ekipa je na prvi strani (domace pravice odlocilne tekme). */
    private static void uredi(SerijaKoncnice serija) {
        Integer m1 = serija.getMesto1();
        Integer m2 = serija.getMesto2();
        if (m1 != null && m2 != null && m2 < m1) {
            Ekipa e1 = serija.getEkipa1();
            Ekipa e2 = serija.getEkipa2();
            serija.nastaviStran(1, e2, m2);
            serija.nastaviStran(2, e1, m1);
        }
    }

    /* Vse mogoce tekme serije z domacimi pravicami po pravilu iz uvoda. */
    private void ustvariTekmeSerije(Liga liga, SerijaKoncnice serija) {
        int stTekem = 2 * liga.getKoncnicaZmag() - 1;
        List<Srecanje> tekme = new ArrayList<>();
        for (int i = 1; i <= stTekem; i++) {
            boolean boljsaDoma = i == stTekem || i % 2 == 0;
            Ekipa domaci = boljsaDoma ? serija.getEkipa1() : serija.getEkipa2();
            Ekipa gost = boljsaDoma ? serija.getEkipa2() : serija.getEkipa1();
            Srecanje s = new Srecanje(liga, serija.getKrog(), domaci, gost);
            s.nastaviSerijo(serija, i);
            tekme.add(s);
        }
        srecanjeRepozitorij.saveAll(tekme);
    }

    private void prestejZmage(SerijaKoncnice serija) {
        int z1 = 0;
        int z2 = 0;
        Long id1 = serija.getEkipa1() != null ? serija.getEkipa1().getId() : null;
        for (Srecanje s : srecanjeRepozitorij.najdiZaSerijo(serija.getId())) {
            if (s.getStatus() != StatusSrecanja.KONCANO || s.getDobljeneDomaci() == s.getDobljeneGost()) {
                continue;
            }
            Ekipa zmagala = s.getDobljeneDomaci() > s.getDobljeneGost() ? s.getEkipaDomaci() : s.getEkipaGost();
            if (zmagala.getId().equals(id1)) {
                z1++;
            } else {
                z2++;
            }
        }
        serija.setZmage1(z1);
        serija.setZmage2(z2);
    }

    private boolean redniDelKoncan(Long idLiga) {
        List<Srecanje> redna = srecanjeRepozitorij.najdiRednaZaLigo(idLiga);
        return !redna.isEmpty() && redna.stream().allMatch(s -> s.getStatus() == StatusSrecanja.KONCANO);
    }

    private Liga najdiLigo(Long idLiga) {
        return ligaRepozitorij.findById(idLiga)
                .orElseThrow(() -> new NiNajdenoIzjema("Liga z id " + idLiga + " ne obstaja."));
    }
}
