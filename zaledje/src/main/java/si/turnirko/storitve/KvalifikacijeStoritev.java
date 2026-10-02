/* Kvalifikacije med ligama (V41): ekipe tik nad izpadom visje lige in tik pod
   napredovanjem nizje igrajo za mesto v visji ligi (Savinja liga: 9. iz A
   proti 3. iz B).

   Koliko ekip jih igra, povesta prehoda obeh lig (st_kvalifikacije_dol visje,
   st_kvalifikacije_gor nizje); cone na lestvici (LestvicaLigeStoritev) so
   edini vir tega, katere ekipe so to.

   Kvalifikacije so SVOJA liga - tako jih je uvozila ze stara stran SNTL.
   Pravila srecanja (format, nizi, prag zmag, raven) prevzame od visje lige,
   ekipe so kopije ekip obeh lig s kadri. Zato zapisniki, termini, lestvica in
   rating tecejo po nespremenjeni kodi, lestvici obeh lig pa ostaneta
   nedotaknjeni.

   Pravila, ki jih ne razbij:
   - Kvalifikacije nastanejo iz KONCNIH lestvic (oba redna dela odigrana) in
     nikoli prej - kot koncnica; pari se sicer lahko zamenjajo pod rokami.
   - PARI so krizni: najbolje uvrscena ekipa visje lige z najslabse uvrsceno
     nizje (odlocitev lastnika, okt 2026). Prva stran serije je vedno ekipa
     visje lige - zato pri eni tekmi igra doma ona, koncnica pa po strani ve,
     iz katere lige je ekipa (KoncnicaStoritev.koncnica).
   - Liga kvalifikacij pripada lastniku VISJE lige (ne nujno tistemu, ki jo
     ustvari - admin jo lahko ustvari organizatorju) in ne steje v mejo lig
     paketa: je podaljsek lig, ki sta ze steli.
   - Razveljavi se v celoti (liga z ekipami), dokler se nobeno srecanje ni
     zacelo - prazna liga brez parov se ne da sestaviti znova. */
package si.turnirko.storitve;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.KvalifikacijePredlogDto;
import si.turnirko.dto.KvalifikacijePredlogDto.Par;
import si.turnirko.dto.KvalifikacijePredlogDto.Udelezenec;
import si.turnirko.dto.KvalifikacijeVnos;
import si.turnirko.dto.LestvicaEkipeDto;
import si.turnirko.dto.LigaDto;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.modeli.Ekipa;
import si.turnirko.modeli.KaderEkipe;
import si.turnirko.modeli.Liga;
import si.turnirko.modeli.NacinKvalifikacij;
import si.turnirko.modeli.SerijaKoncnice;
import si.turnirko.modeli.Srecanje;
import si.turnirko.modeli.StatusSrecanja;
import si.turnirko.modeli.StatusTekmovanja;
import si.turnirko.repozitoriji.EkipaRepozitorij;
import si.turnirko.repozitoriji.KaderEkipeRepozitorij;
import si.turnirko.repozitoriji.LigaRepozitorij;
import si.turnirko.repozitoriji.SerijaKoncniceRepozitorij;
import si.turnirko.repozitoriji.SrecanjeRepozitorij;

@Service
public class KvalifikacijeStoritev {

    private static final String CONA_VISJE = "KVALIFIKACIJE_DOL";
    private static final String CONA_NIZJE = "KVALIFIKACIJE_GOR";
    private static final int NAJDALJSE_IME = 80;

    private final LigaRepozitorij ligaRepozitorij;
    private final EkipaRepozitorij ekipaRepozitorij;
    private final KaderEkipeRepozitorij kaderRepozitorij;
    private final SrecanjeRepozitorij srecanjeRepozitorij;
    private final SerijaKoncniceRepozitorij serijaRepozitorij;
    private final LestvicaLigeStoritev lestvicaLigeStoritev;
    private final KoncnicaStoritev koncnicaStoritev;
    private final LigaStoritev ligaStoritev;
    private final LastnistvoStoritev lastnistvo;

    public KvalifikacijeStoritev(LigaRepozitorij ligaRepozitorij,
                                 EkipaRepozitorij ekipaRepozitorij,
                                 KaderEkipeRepozitorij kaderRepozitorij,
                                 SrecanjeRepozitorij srecanjeRepozitorij,
                                 SerijaKoncniceRepozitorij serijaRepozitorij,
                                 LestvicaLigeStoritev lestvicaLigeStoritev,
                                 KoncnicaStoritev koncnicaStoritev,
                                 LigaStoritev ligaStoritev,
                                 LastnistvoStoritev lastnistvo) {
        this.ligaRepozitorij = ligaRepozitorij;
        this.ekipaRepozitorij = ekipaRepozitorij;
        this.kaderRepozitorij = kaderRepozitorij;
        this.srecanjeRepozitorij = srecanjeRepozitorij;
        this.serijaRepozitorij = serijaRepozitorij;
        this.lestvicaLigeStoritev = lestvicaLigeStoritev;
        this.koncnicaStoritev = koncnicaStoritev;
        this.ligaStoritev = ligaStoritev;
        this.lastnistvo = lastnistvo;
    }

    // ---------- Predlog ----------

    @Transactional(readOnly = true)
    public KvalifikacijePredlogDto predlog(Long idVisja, Long idNizja) {
        return sestavi(najdiLigo(idVisja), najdiLigo(idNizja));
    }

    private KvalifikacijePredlogDto sestavi(Liga visja, Liga nizja) {
        List<String> ovire = new ArrayList<>();
        if (nizja.getVisjaLiga() == null || !nizja.getVisjaLiga().getId().equals(visja.getId())) {
            ovire.add(nizja.getIme() + " ni pod ligo " + visja.getIme() + " — povezavo vpiši v prehodih.");
        }
        int izVisje = visja.getStKvalifikacijeDol();
        int izNizje = nizja.getStKvalifikacijeGor();
        if (izVisje == 0) {
            ovire.add(visja.getIme() + " v prehodih nima kvalifikacij za obstanek.");
        }
        if (izNizje == 0) {
            ovire.add(nizja.getIme() + " v prehodih nima kvalifikacij za napredovanje.");
        }

        List<Udelezenec> ekipeVisje = udelezenci(visja.getId(), CONA_VISJE);
        List<Udelezenec> ekipeNizje = udelezenci(nizja.getId(), CONA_NIZJE);
        // cone se pri premajhni ligi prekrijejo s sosednjimi (LestvicaLigeStoritev)
        if (izVisje > 0 && ekipeVisje.size() < izVisje) {
            ovire.add(visja.getIme() + " nima dovolj ekip za " + izVisje + " "
                    + ekip(izVisje) + " v kvalifikacijah.");
        }
        if (izNizje > 0 && ekipeNizje.size() < izNizje) {
            ovire.add(nizja.getIme() + " nima dovolj ekip za " + izNizje + " "
                    + ekip(izNizje) + " v kvalifikacijah.");
        }

        boolean koncanaVisja = redniDelKoncan(visja.getId());
        boolean koncanaNizja = redniDelKoncan(nizja.getId());
        if (!koncanaVisja) {
            ovire.add(neodigran(visja));
        }
        if (!koncanaNizja) {
            ovire.add(neodigran(nizja));
        }

        Long idObstojece = ligaRepozitorij.najdiKvalifikacije(visja.getId(), nizja.getId())
                .map(Liga::getId).orElse(null);
        if (idObstojece != null) {
            ovire.add("Kvalifikacije med tema ligama že obstajajo.");
        }

        return new KvalifikacijePredlogDto(
                visja.getId(), visja.getIme(), nizja.getId(), nizja.getIme(),
                izVisje, izNizje, koncanaVisja && koncanaNizja,
                ekipeVisje, ekipeNizje, krizniPari(ekipeVisje, ekipeNizje),
                ovire, idObstojece, predlaganoIme(visja.getIme(), nizja.getIme()));
    }

    /* Ekipe lige v dani coni, po mestu (najbolje uvrscena prva). */
    private List<Udelezenec> udelezenci(Long idLiga, String cona) {
        return lestvicaLigeStoritev.lestvica(idLiga).stream()
                .filter(v -> cona.equals(v.cona()))
                .map(v -> new Udelezenec(v.idEkipa(), v.ekipa(), v.mesto()))
                .toList();
    }

    /* Krizni pari: i-ta ekipa visje lige z i-to od spodaj nizje lige
       (8. A - 4. B, 9. A - 3. B). Brez parov, ce lige ne dajo enako ekip. */
    static List<Par> krizniPari(List<Udelezenec> visje, List<Udelezenec> nizje) {
        if (visje.isEmpty() || visje.size() != nizje.size()) {
            return List.of();
        }
        List<Par> pari = new ArrayList<>();
        for (int i = 0; i < visje.size(); i++) {
            pari.add(new Par(visje.get(i), nizje.get(nizje.size() - 1 - i)));
        }
        return pari;
    }

    /* "Kvalifikacije Savinja liga A/B": skupne zacetne besede imen se
       zapisejo enkrat; brez skupnega zacetka "Kvalifikacije 1. SNTL / 2. SNTL". */
    static String predlaganoIme(String visja, String nizja) {
        String[] a = visja.trim().split("\\s+");
        String[] b = nizja.trim().split("\\s+");
        int skupnih = 0;
        while (skupnih < a.length - 1 && skupnih < b.length - 1 && a[skupnih].equals(b[skupnih])) {
            skupnih++;
        }
        String ime;
        if (skupnih > 0) {
            String zacetek = String.join(" ", List.of(a).subList(0, skupnih));
            String ostanekA = String.join(" ", List.of(a).subList(skupnih, a.length));
            String ostanekB = String.join(" ", List.of(b).subList(skupnih, b.length));
            ime = "Kvalifikacije " + zacetek + " " + ostanekA + "/" + ostanekB;
        } else {
            ime = "Kvalifikacije " + visja.trim() + " / " + nizja.trim();
        }
        return ime.length() > NAJDALJSE_IME ? ime.substring(0, NAJDALJSE_IME).trim() : ime;
    }

    // ---------- Nastanek ----------

    @Transactional
    public LigaDto ustvari(Long idVisja, KvalifikacijeVnos v) {
        lastnistvo.preveriLigaPoId(idVisja);
        Liga visja = najdiLigo(idVisja);
        Liga nizja = najdiLigo(v.idNizja());
        if (visja.jeKvalifikacijska() || nizja.jeKvalifikacijska()) {
            throw new NeveljavenVnosIzjema("Kvalifikacije se igrajo med navadnima ligama.");
        }

        KvalifikacijePredlogDto predlog = sestavi(visja, nizja);
        if (!predlog.ovire().isEmpty()) {
            throw new DomenskaIzjema(predlog.ovire().get(0));
        }
        NacinKvalifikacij nacin = v.nacin();
        if (nacin.poParih() && predlog.pari().isEmpty()) {
            throw new DomenskaIzjema("Po parih se kvalifikacije igrajo, ko obe ligi dasta enako ekip ("
                    + visja.getIme() + ": " + predlog.izVisje() + ", " + nizja.getIme() + ": "
                    + predlog.izNizje() + "). Izberi vsak z vsakim ali popravi prehode.");
        }

        String ime = v.ime() != null && !v.ime().isBlank() ? v.ime().trim() : predlog.predlaganoIme();
        if (ime.length() < 3) {
            throw new NeveljavenVnosIzjema("Ime lige mora imeti vsaj 3 znake.");
        }

        Liga kval = novaLiga(visja, nizja, ime, nacin, predlog);
        kval = ligaRepozitorij.save(kval);

        Map<Long, Ekipa> kopije = kopirajEkipe(kval,
                predlog.ekipeVisje().stream().map(Udelezenec::idEkipa).toList(),
                predlog.ekipeNizje().stream().map(Udelezenec::idEkipa).toList());

        if (nacin.poParih()) {
            List<SerijaKoncnice> serije = new ArrayList<>();
            int par = 1;
            for (Par p : predlog.pari()) {
                SerijaKoncnice serija = new SerijaKoncnice(kval, 1, par++);
                serija.nastaviStran(1, kopije.get(p.visja().idEkipa()), p.visja().mesto());
                serija.nastaviStran(2, kopije.get(p.nizja().idEkipa()), p.nizja().mesto());
                serije.add(serija);
            }
            koncnicaStoritev.zapisiPareKvalifikacij(kval, serije);
        }
        return ligaStoritev.najdi(kval.getId());
    }

    /* Liga kvalifikacij s pravili srecanja visje lige. Po parih tece po kodi
       koncnice brez rednega dela (zato je takoj V_TEKU); mala liga ostane v
       pripravi, da ji organizator vpise termine in izzreba razpored. */
    private Liga novaLiga(Liga visja, Liga nizja, String ime, NacinKvalifikacij nacin,
                          KvalifikacijePredlogDto predlog) {
        Liga k = new Liga();
        k.setIme(LigaStoritev.zVelikoZacetnico(ime));
        k.setSezona(visja.getSezona());
        k.setSpolKategorija(visja.getSpolKategorija());
        k.setFormatSrecanja(visja.getFormatSrecanja());
        k.setSteviloNizov(visja.getSteviloNizov());
        k.setZmagZaSrecanje(visja.getZmagZaSrecanje());
        k.setTockeZmaga(visja.getTockeZmaga());
        k.setTockeNeodloceno(visja.getTockeNeodloceno());
        k.setTockePoraz(visja.getTockePoraz());
        k.setDovoljenoNeodloceno(visja.isDovoljenoNeodloceno());
        k.setOdbitekBrezBoja(visja.getOdbitekBrezBoja());
        k.setRaven(visja.getRaven());
        k.setPredlogaListka(visja.getPredlogaListka());
        k.nastaviKvalifikacije(visja, nizja);
        // lastnik visje lige - kvalifikacije so del njenega tekmovanja
        k.setUstvaril(visja.getUstvaril());
        k.setKlubLastnik(visja.getKlubLastnik());
        if (nacin.poParih()) {
            k.setKoncnicaEkip(2);
            k.setKoncnicaZmag(nacin.getZmagVSeriji());
            k.setDvokrozno(false);
            k.setStatus(StatusTekmovanja.V_TEKU);
        } else {
            k.setDvokrozno(nacin.isDvokrozno());
            // zgornja mesta male lige igrajo v visji ligi, ostala v nizji
            k.setStNapreduje(predlog.ekipeVisje().size());
            k.setStIzpade(predlog.ekipeNizje().size());
        }
        return k;
    }

    /* Kopije ekip s kadrom. Ime ekipe mora biti v ligi enolicno (v razporedu
       in zapisniku je edino, kar ju loci) - trk dveh lig se zavrne, ker ga
       mora razresiti clovek (preimenovati eno od ekip). */
    private Map<Long, Ekipa> kopirajEkipe(Liga kval, List<Long> izVisje, List<Long> izNizje) {
        List<Long> vse = new ArrayList<>(izVisje);
        vse.addAll(izNizje);
        Map<Long, Ekipa> izvirne = vse.stream()
                .map(id -> ekipaRepozitorij.najdiZKlubomInLigo(id)
                        .orElseThrow(() -> new NiNajdenoIzjema("Ekipa z id " + id + " ne obstaja.")))
                .collect(Collectors.toMap(Ekipa::getId, Function.identity()));

        Map<String, Ekipa> poImenu = new HashMap<>();
        for (Long id : vse) {
            Ekipa e = izvirne.get(id);
            Ekipa prej = poImenu.putIfAbsent(e.prikazanoIme().toLowerCase(Locale.ROOT), e);
            if (prej != null) {
                throw new DomenskaIzjema("Ekipi \"" + e.prikazanoIme() + "\" iz lig "
                        + prej.getLiga().getIme() + " in " + e.getLiga().getIme()
                        + " imata isto ime — eno preimenuj, da ju bo v kvalifikacijah mogoče ločiti.");
            }
        }

        Map<Long, Ekipa> kopije = new HashMap<>();
        int nosilec = 1;
        for (Long id : vse) {
            Ekipa izvirna = izvirne.get(id);
            Ekipa kopija = new Ekipa(kval, izvirna.getKlub(), izvirna.getZaporedna(), izvirna.getIme());
            // vrstni red: ekipe visje lige najprej (po mestu), nato nizje
            kopija.setStNosilca(nosilec++);
            kopija = ekipaRepozitorij.save(kopija);
            for (KaderEkipe k : kaderRepozitorij.najdiZaEkipo(izvirna.getId())) {
                kaderRepozitorij.save(new KaderEkipe(kopija, k.getIgralec(), k.getVrstniRed()));
            }
            kopije.put(id, kopija);
        }
        return kopije;
    }

    // ---------- Razveljavitev ----------

    /* Kvalifikacije gredo v celoti (srecanja, pari, ekipe s kadri, liga),
       dokler se nobeno srecanje ni zacelo - za kvalifikacije, ustvarjene s
       prenagljenim nacinom ali pred popravkom rezultata rednega dela. */
    @Transactional
    public void razveljavi(Long idKvalifikacije) {
        lastnistvo.preveriLigaPoId(idKvalifikacije);
        Liga kval = najdiLigo(idKvalifikacije);
        if (!kval.jeKvalifikacijska()) {
            throw new NeveljavenVnosIzjema("Liga " + kval.getIme() + " ni liga kvalifikacij.");
        }
        List<Srecanje> srecanja = srecanjeRepozitorij.najdiZaLigo(idKvalifikacije);
        for (Srecanje s : srecanja) {
            if (s.getStatus() != StatusSrecanja.RAZPORED) {
                throw new DomenskaIzjema("Kvalifikacij ni mogoče razveljaviti: srečanje "
                        + s.getEkipaDomaci().prikazanoIme() + " – " + s.getEkipaGost().prikazanoIme()
                        + " se je že začelo.");
            }
        }
        srecanjeRepozitorij.deleteAll(srecanja);
        srecanjeRepozitorij.flush();
        serijaRepozitorij.deleteAll(serijaRepozitorij.najdiZaLigo(idKvalifikacije));
        serijaRepozitorij.flush();
        List<Ekipa> ekipe = ekipaRepozitorij.najdiZaLigo(idKvalifikacije);
        for (Ekipa e : ekipe) {
            kaderRepozitorij.deleteAll(kaderRepozitorij.najdiZaEkipo(e.getId()));
        }
        kaderRepozitorij.flush();
        ekipaRepozitorij.deleteAll(ekipe);
        ekipaRepozitorij.flush();
        ligaRepozitorij.delete(kval);
    }

    // ---------- Pomozno ----------

    private boolean redniDelKoncan(Long idLiga) {
        List<Srecanje> redna = srecanjeRepozitorij.najdiRednaZaLigo(idLiga);
        return !redna.isEmpty() && redna.stream().allMatch(s -> s.getStatus() == StatusSrecanja.KONCANO);
    }

    private static String neodigran(Liga liga) {
        return "Redni del lige " + liga.getIme() + " še ni odigran do konca.";
    }

    private static String ekip(int n) {
        return switch (n) {
            case 1 -> "ekipo";
            case 2 -> "ekipi";
            case 3, 4 -> "ekipe";
            default -> "ekip";
        };
    }

    private Liga najdiLigo(Long idLiga) {
        return ligaRepozitorij.findById(Objects.requireNonNull(idLiga))
                .orElseThrow(() -> new NiNajdenoIzjema("Liga z id " + idLiga + " ne obstaja."));
    }
}
