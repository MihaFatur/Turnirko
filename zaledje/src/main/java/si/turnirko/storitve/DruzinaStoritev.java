/* Druzina (V59, faza 2 paketa Navijac): navijac s placljivim paketom poveze do
   dva igralca do 21 let in v svojem racunu vidi njuno ZASEBNO statistiko (forma,
   nasprotniki, napoved, nizi in tocke); ce ima igralec svoj racun, dobi Premium
   tudi on (NarocninaStoritev.imaDruzinskiPremium).

   Pravila, ki jih ne razbij:
   - **Povezavo vedno potrdi administrator.** Zasebna statistika tujega otroka
     ne sme priti do nikogar samo z imenom in datumom rojstva (V3 iz
     varnostnega pregleda): zahteva (CAKA) ne odpre nicesar, dokler je admin ne
     potrdi. Zavrnjena vrstica ostane: oseba je ne ponavlja tiho.
   - **Samo mladinski pas** (U11-U21 po StarostniPas, 11. clen PST): meja je
     ista kot pri ceni Premium za mlajse. Igralec brez znanega datuma rojstva
     (zapis 1. 1. 1900 = veteran po letnici) ni primeren: starosti ni mogoce
     ugibati.
   - **Najvec dve povezavi** (CAKA + POTRJENA): pravilo paketa in ne sheme.
   - **Povezava velja, dokler velja paket navijaca.** Po poteku narocnine
     zasebna statistika spet ni vec odprta, povezava pa ostane zapisana (ob
     obnovi spet velja brez nove potrditve).
   - **Preraste pas U21 sredi obdobja**: povezava velja do konca ze placanega
     obdobja (velja_do), nato ne vec. Meja se zapise dnevno (uskladiStaroste),
     ne ob branju: branje ne sme pisati.
   - Admin dobi e-posto ob novi zahtevi (turnirko.posta.admin), navijac ob
     odlocitvi. Osebnih podatkov (datuma rojstva) ni v nobeni pošti. */
package si.turnirko.storitve;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.DruzinaDto;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.izjeme.PrepovedanoIzjema;
import si.turnirko.modeli.DruzinaPovezava;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Narocnina;
import si.turnirko.modeli.StarostniPas;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.posta.PostaStoritev;
import si.turnirko.posta.SporocilaPoste;
import si.turnirko.repozitoriji.DruzinaPovezavaRepozitorij;
import si.turnirko.repozitoriji.IgralecRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

@Service
public class DruzinaStoritev {

    /* Koliko igralcev sme imeti navijac v druzini (aktivne povezave: CAKA in POTRJENA). */
    public static final int NAJVEC_IGRALCEV = 2;

    private static final Set<DruzinaPovezava.Status> AKTIVNE =
            EnumSet.of(DruzinaPovezava.Status.CAKA, DruzinaPovezava.Status.POTRJENA);

    private final DruzinaPovezavaRepozitorij repozitorij;
    private final UporabnikRepozitorij uporabnikRepozitorij;
    private final IgralecRepozitorij igralecRepozitorij;
    private final NarocninaStoritev narocnina;
    private final LastnistvoStoritev lastnistvo;
    private final RacunDnevnikStoritev racunDnevnik;
    private final PostaStoritev posta;
    private final String prejemnikAdmin;
    private final String koren;

    public DruzinaStoritev(DruzinaPovezavaRepozitorij repozitorij,
                           UporabnikRepozitorij uporabnikRepozitorij,
                           IgralecRepozitorij igralecRepozitorij,
                           NarocninaStoritev narocnina,
                           LastnistvoStoritev lastnistvo,
                           RacunDnevnikStoritev racunDnevnik,
                           PostaStoritev posta,
                           @Value("${turnirko.posta.admin:}") String prejemnikAdmin,
                           @Value("${turnirko.stripe.vrni-na}") String koren) {
        this.repozitorij = repozitorij;
        this.uporabnikRepozitorij = uporabnikRepozitorij;
        this.igralecRepozitorij = igralecRepozitorij;
        this.narocnina = narocnina;
        this.lastnistvo = lastnistvo;
        this.racunDnevnik = racunDnevnik;
        this.posta = posta;
        this.prejemnikAdmin = prejemnikAdmin == null ? "" : prejemnikAdmin.trim();
        this.koren = koren.endsWith("/") ? koren.substring(0, koren.length() - 1) : koren;
    }

    // ---------- Pogled racuna ----------

    @Transactional(readOnly = true)
    public DruzinaDto.Pregled pregled() {
        Uporabnik jaz = zahtevajPrijavo();
        LocalDateTime zdaj = LocalDateTime.now();
        boolean placnik = narocnina.imaNavijaca(jaz);
        List<DruzinaDto.Povezava> povezave = repozitorij.findByIdRacunOrderById(jaz.getId()).stream()
                .map(p -> povezava(p, placnik && p.veljaOb(zdaj)))
                .toList();
        return new DruzinaDto.Pregled(placnik, NAJVEC_IGRALCEV, povezave, narocnina.imaDruzinskiPremium(jaz));
    }

    private DruzinaDto.Povezava povezava(DruzinaPovezava p, boolean veljavna) {
        Igralec i = igralecRepozitorij.findById(p.getIdIgralec()).orElse(null);
        return new DruzinaDto.Povezava(p.getId(), p.getIdIgralec(),
                i == null ? "?" : i.polnoIme(),
                i == null || i.getKlub() == null ? null : i.getKlub().getIme(),
                p.getStatus(), veljavna, dan(p.getVeljaDo()));
    }

    /* Zahteva za povezavo: nastane v stanju CAKA in ne odpre nicesar do odlocitve admina. */
    @Transactional
    public DruzinaDto.Povezava zahtevaj(Long idIgralec) {
        Uporabnik jaz = zahtevajPrijavo();
        if (!narocnina.imaNavijaca(jaz)) {
            throw new PrepovedanoIzjema("Družina je del paketa Navijač. Naročnino najdeš na strani Naročnina.");
        }
        Igralec igralec = igralecRepozitorij.findById(idIgralec)
                .orElseThrow(() -> new NiNajdenoIzjema("Igralec z id " + idIgralec + " ne obstaja."));
        preveriPas(igralec);

        DruzinaPovezava obstojeca = repozitorij.findByIdRacunAndIdIgralec(jaz.getId(), idIgralec).orElse(null);
        if (obstojeca != null) {
            if (obstojeca.getStatus() == DruzinaPovezava.Status.ZAVRNJENA) {
                throw new DomenskaIzjema("Administrator je zahtevo za tega igralca že zavrnil. "
                        + "Če meniš, da je prišlo do pomote, mu piši.");
            }
            return povezava(obstojeca, obstojeca.veljaOb(LocalDateTime.now()));
        }
        long aktivnih = repozitorij.findByIdRacunOrderById(jaz.getId()).stream()
                .filter(p -> AKTIVNE.contains(p.getStatus()))
                .count();
        if (aktivnih >= NAJVEC_IGRALCEV) {
            throw new DomenskaIzjema("Družina ima lahko največ " + NAJVEC_IGRALCEV
                    + " igralca. Najprej umakni enega.");
        }
        DruzinaPovezava nova = repozitorij.save(new DruzinaPovezava(jaz.getId(), idIgralec, LocalDateTime.now()));
        racunDnevnik.zabelezi(jaz.getId(), "Družina: zahteva za povezavo z igralcem " + igralec.polnoIme());
        obvestiAdmina(jaz, igralec);
        return povezava(nova, false);
    }

    /* Racun sam umakne svojo zahtevo ali povezavo. Zavrnjene vrstice ne: odlocitev admina
       oseba ne more zbrisati, da bi zahtevo vlozila znova. */
    @Transactional
    public void umakni(Long id) {
        Uporabnik jaz = zahtevajPrijavo();
        DruzinaPovezava p = repozitorij.findById(id)
                .filter(v -> v.getIdRacun().equals(jaz.getId()))
                .orElseThrow(() -> new NiNajdenoIzjema("Povezave ni."));
        if (p.getStatus() == DruzinaPovezava.Status.ZAVRNJENA) {
            throw new DomenskaIzjema("Zavrnjene zahteve ni mogoče umakniti.");
        }
        repozitorij.delete(p);
        racunDnevnik.zabelezi(jaz.getId(), "Družina: povezava z igralcem " + ime(p.getIdIgralec()) + " umaknjena");
    }

    // ---------- Admin ----------

    /* Zahteve, ki cakajo, in potrjene povezave (po datumu zahteve, najnovejse prve). */
    @Transactional(readOnly = true)
    public List<DruzinaDto.Admin> zaAdmina() {
        return repozitorij.findByStatusInOrderByZahtevanaObDescIdDesc(
                        EnumSet.of(DruzinaPovezava.Status.CAKA, DruzinaPovezava.Status.POTRJENA))
                .stream().map(this::admin).toList();
    }

    @Transactional(readOnly = true)
    public long steviloCakajocih() {
        return repozitorij.countByStatus(DruzinaPovezava.Status.CAKA);
    }

    @Transactional
    public DruzinaDto.Admin potrdi(Long id) {
        DruzinaPovezava p = najdi(id);
        Igralec igralec = igralecRepozitorij.findById(p.getIdIgralec())
                .orElseThrow(() -> new NiNajdenoIzjema("Igralec ne obstaja vec."));
        preveriPas(igralec);
        p.setStatus(DruzinaPovezava.Status.POTRJENA);
        p.setOdlocenaOb(LocalDateTime.now());
        p.setVeljaDo(null);
        racunDnevnik.zabelezi(p.getIdRacun(), "Družina: povezava z igralcem " + igralec.polnoIme() + " potrjena");
        obvestiRacun(p, "Povezava z igralcem " + igralec.polnoIme() + " je potrjena. Zasebno statistiko "
                + "vidiš na profilu igralca: " + koren + "/igralci/" + igralec.getId() + "/profil");
        return admin(repozitorij.save(p));
    }

    @Transactional
    public DruzinaDto.Admin zavrni(Long id) {
        DruzinaPovezava p = najdi(id);
        p.setStatus(DruzinaPovezava.Status.ZAVRNJENA);
        p.setOdlocenaOb(LocalDateTime.now());
        racunDnevnik.zabelezi(p.getIdRacun(), "Družina: povezava z igralcem " + ime(p.getIdIgralec()) + " zavrnjena");
        obvestiRacun(p, "Povezave z igralcem " + ime(p.getIdIgralec()) + " administrator ni potrdil. "
                + "Če meniš, da je prišlo do pomote, nam piši.");
        return admin(repozitorij.save(p));
    }

    /* Admin povezavo razveze (npr. starsa ni vec ali je bila napaka); vrstica gre. */
    @Transactional
    public void razveze(Long id) {
        DruzinaPovezava p = najdi(id);
        racunDnevnik.zabelezi(p.getIdRacun(), "Družina: povezava z igralcem " + ime(p.getIdIgralec())
                + " razvezana (administrator)");
        repozitorij.delete(p);
    }

    private DruzinaPovezava najdi(Long id) {
        return repozitorij.findById(id).orElseThrow(() -> new NiNajdenoIzjema("Povezave ni."));
    }

    private DruzinaDto.Admin admin(DruzinaPovezava p) {
        Uporabnik racun = uporabnikRepozitorij.findById(p.getIdRacun()).orElse(null);
        Igralec i = igralecRepozitorij.findById(p.getIdIgralec()).orElse(null);
        LocalDate rojstvo = i == null ? null : i.getDatumRojstva();
        String ime = racun == null ? "?" : (nvl(racun.getPrijavljenoIme()) + " " + nvl(racun.getPrijavljeniPriimek())).trim();
        return new DruzinaDto.Admin(p.getId(), p.getIdRacun(), ime.isEmpty() ? racun.getUporabniskoIme() : ime,
                racun == null ? null : racun.getUporabniskoIme(),
                p.getIdIgralec(), i == null ? "?" : i.polnoIme(),
                i == null || i.getKlub() == null ? null : i.getKlub().getIme(),
                rojstvo, rojstvo == null ? null : StarostniPas.izpelji(rojstvo, LocalDate.now()),
                p.getStatus(), p.getZahtevanaOb().toLocalDate(), dan(p.getVeljaDo()),
                racun != null && narocnina.imaNavijaca(racun));
    }

    // ---------- Kaj odpre povezava (kliceta drugi) ----------

    /* Ali ima racun igralca v druzini ZDAJ: potrjena povezava, ki velja, in paket
       navijaca, ki velja. Edino mesto te odlocitve za dostop do zasebnega profila. */
    @Transactional(readOnly = true)
    public boolean jeClan(Uporabnik racun, Long idIgralec) {
        if (racun == null || racun.getId() == null || !narocnina.imaNavijaca(racun)) {
            return false;
        }
        LocalDateTime zdaj = LocalDateTime.now();
        return repozitorij.findByIdRacunAndIdIgralec(racun.getId(), idIgralec)
                .map(p -> p.veljaOb(zdaj)).orElse(false);
    }

    /* Id-ji igralcev, ki jih racun zdaj ima v druzini. */
    @Transactional(readOnly = true)
    public Set<Long> igralciDruzine(Uporabnik racun) {
        Set<Long> idji = new LinkedHashSet<>();
        if (racun == null || racun.getId() == null || !narocnina.imaNavijaca(racun)) {
            return idji;
        }
        LocalDateTime zdaj = LocalDateTime.now();
        for (DruzinaPovezava p : repozitorij.findByIdRacunOrderById(racun.getId())) {
            if (p.veljaOb(zdaj)) {
                idji.add(p.getIdIgralec());
            }
        }
        return idji;
    }

    /* Racuni, ki imajo katerega od igralcev zdaj v druzini (pot e-poste po turnirju). */
    @Transactional(readOnly = true)
    public List<DruzinaPovezava> veljavnePovezaveIgralcev(Set<Long> idjiIgralcev) {
        if (idjiIgralcev.isEmpty()) {
            return List.of();
        }
        LocalDateTime zdaj = LocalDateTime.now();
        return repozitorij.findByIdIgralecInAndStatus(idjiIgralcev, DruzinaPovezava.Status.POTRJENA).stream()
                .filter(p -> p.veljaOb(zdaj))
                .filter(p -> uporabnikRepozitorij.findById(p.getIdRacun()).map(narocnina::imaNavijaca).orElse(false))
                .toList();
    }

    // ---------- Starost ----------

    /* Igralec, ki je prerasel pas U21, je v druzini do konca placanega obdobja: ko to
       opazimo, se zapise velja_do (konec obdobja navijaca, sicer takoj). Dnevno ob 4.15,
       ker branje ne sme pisati; povezava, ki ji je meja ze zapisana, se ne premika. */
    @Transactional
    @Scheduled(cron = "${turnirko.druzina.urnik:0 15 4 * * *}", zone = "Europe/Ljubljana")
    public void dnevnoUskladiStaroste() {
        uskladiStaroste(LocalDateTime.now());
    }

    @Transactional
    public int uskladiStaroste(LocalDateTime zdaj) {
        int spremenjenih = 0;
        for (DruzinaPovezava p : repozitorij.findByStatusInOrderByZahtevanaObDescIdDesc(
                EnumSet.of(DruzinaPovezava.Status.POTRJENA))) {
            if (p.getVeljaDo() != null) {
                continue;
            }
            Igralec i = igralecRepozitorij.findById(p.getIdIgralec()).orElse(null);
            if (i == null || jeMladinski(i, zdaj.toLocalDate())) {
                continue;
            }
            Uporabnik racun = uporabnikRepozitorij.findById(p.getIdRacun()).orElse(null);
            LocalDateTime konec = racun == null ? null : narocnina.veljavna(racun)
                    .map(Narocnina::getTrenutnoObdobjeDo).orElse(null);
            p.setVeljaDo(konec != null && konec.isAfter(zdaj) ? konec : zdaj);
            racunDnevnik.zabelezi(p.getIdRacun(), "Družina: igralec " + i.polnoIme()
                    + " je prerasel pas U21, povezava velja do konca plačanega obdobja");
            spremenjenih++;
        }
        return spremenjenih;
    }

    // ---------- Pomozno ----------

    private static boolean jeMladinski(Igralec igralec, LocalDate danes) {
        if (igralec.getDatumRojstva() == null) {
            return false;
        }
        StarostniPas pas = StarostniPas.izpelji(igralec.getDatumRojstva(), danes);
        return pas != null && pas != StarostniPas.CLANI && pas != StarostniPas.VETERANI;
    }

    private static void preveriPas(Igralec igralec) {
        if (!jeMladinski(igralec, LocalDate.now())) {
            throw new NeveljavenVnosIzjema("Družina velja za igralce do 21 let (po starostnem pasu). "
                    + "Ta igralec ni v mladinskem pasu ali nima znanega datuma rojstva.");
        }
    }

    private Uporabnik zahtevajPrijavo() {
        Uporabnik jaz = lastnistvo.trenutni();
        if (jaz == null) {
            throw new PrepovedanoIzjema("Za družino je potrebna prijava.");
        }
        return jaz;
    }

    private void obvestiAdmina(Uporabnik racun, Igralec igralec) {
        if (prejemnikAdmin.isEmpty()) {
            return;
        }
        String klub = igralec.getKlub() == null ? "brez kluba" : igralec.getKlub().getIme();
        posta.poslji(prejemnikAdmin, new SporocilaPoste.Besedilo(
                "Turnirko: družinska zahteva za potrditev",
                "Račun " + racun.getUporabniskoIme() + " (paket Navijač) želi povezati igralca "
                        + igralec.polnoIme() + " (" + klub + ") v družino: vidi njegovo zasebno statistiko, "
                        + "njegov račun pa dobi Premium.\n\nPotrdi ali zavrni v Dostopih: " + koren + "/racuni\n"));
    }

    private void obvestiRacun(DruzinaPovezava p, String besedilo) {
        uporabnikRepozitorij.findById(p.getIdRacun())
                .filter(u -> u.getUporabniskoIme() != null && u.getUporabniskoIme().contains("@"))
                .ifPresent(u -> posta.poslji(u.getUporabniskoIme(),
                        new SporocilaPoste.Besedilo("Turnirko: družina", besedilo + "\n")));
    }

    private String ime(Long idIgralec) {
        return igralecRepozitorij.findById(idIgralec).map(Igralec::polnoIme).orElse("?");
    }

    private static String nvl(String s) {
        return s == null ? "" : s.trim();
    }

    private static LocalDate dan(LocalDateTime t) {
        return t == null ? null : t.toLocalDate();
    }
}
