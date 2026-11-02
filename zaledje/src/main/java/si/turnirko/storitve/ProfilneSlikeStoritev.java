/* Profilna slika igralca (V56) - funkcija paketa Igralec Premium.

   Kdaj je slika JAVNA (vidne): objavljena, račun potrjen in aktiven, povezan z
   igralcem in z veljavnim Premium (NarocninaStoritev.imaPremium - tudi
   preizkus). Ob izteku Premium se slika skrije, ne izbriše; ob obnovi se vrne.

   Kdaj slika ČAKA na admina namesto da bi bila javna takoj:
   - igralec je mlajši od 15 let (ZVOP-2, ista meja kot soglasje skrbnika);
   - račun NI povezal admin: samodejna povezava (ime, priimek, datum rojstva) ni
     dokaz istovetnosti, ker so vsi trije podatki znani ali uganljivi
     (VARNOSTNI-PREGLED V3) - brez tega bi kdorkoli postavil sliko na tuj profil.
   Ostale slike so javne takoj, admin pa dobi e-pošto in jo lahko odstrani
   (naknadna moderacija - pri naši velikosti dovolj, kupec ne čaka).

   Slika je osebni podatek, ki ga je igralec dal sam: izbris (njegov ali adminov)
   izbriše tudi bajte. V IgralecJavniDto je namenoma ni (glej SlikaIgralcaDto). */
package si.turnirko.storitve;

import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.MojaProfilnaSlikaDto;
import si.turnirko.dto.ProfilnaSlikaPregledDto;
import si.turnirko.dto.SlikaIgralcaDto;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.izjeme.PrepovedanoIzjema;
import si.turnirko.izjeme.PrevecZahtevIzjema;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.ProfilnaSlika;
import si.turnirko.modeli.ProfilnaSlikaVsebina;
import si.turnirko.modeli.StanjeProfilneSlike;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.VirPovezave;
import si.turnirko.modeli.Vloga;
import si.turnirko.pomozno.ObdelavaSlik;
import si.turnirko.pomozno.ObdelavaSlik.Zapis;
import si.turnirko.posta.PostaStoritev;
import si.turnirko.repozitoriji.ProfilnaSlikaRepozitorij;
import si.turnirko.repozitoriji.ProfilnaSlikaVsebinaRepozitorij;

@Service
public class ProfilneSlikeStoritev {

    /* Nalaganj na račun na uro - igralec poskusi nekaj izrezov. */
    static final int NAJVEC_NALAGANJ_NA_URO = 20;
    /* Manjša slika bi bila na profilu zamegljena. */
    static final int NAJMANJ_STRANICA = 160;
    /* Koliko igralcev sme vprašati ena zahteva po slikah (stopničke, 1 na 1). */
    static final int NAJVEC_IGRALCEV = 100;
    /* Adminov pregled kaže objavljene slike zadnjih 30 dni (naknadna moderacija). */
    static final int DNI_PREGLEDA = 30;
    /* Datum rojstva 1. 1. 1900 pomeni »ni znan« (uvoz) - tak igralec velja za odraslega. */
    private static final LocalDate NEZNAN_DATUM = LocalDate.of(1900, 1, 1);

    private final ProfilnaSlikaRepozitorij slikaRepozitorij;
    private final ProfilnaSlikaVsebinaRepozitorij vsebinaRepozitorij;
    private final LastnistvoStoritev lastnistvoStoritev;
    private final NarocninaStoritev narocninaStoritev;
    private final OmejevalnikPoskusov omejevalnik;
    private final PostaStoritev posta;
    private final String prejemnikAdmin;
    private final String koren;

    public ProfilneSlikeStoritev(ProfilnaSlikaRepozitorij slikaRepozitorij,
                                 ProfilnaSlikaVsebinaRepozitorij vsebinaRepozitorij,
                                 LastnistvoStoritev lastnistvoStoritev,
                                 NarocninaStoritev narocninaStoritev,
                                 OmejevalnikPoskusov omejevalnik,
                                 PostaStoritev posta,
                                 @Value("${turnirko.posta.admin:}") String prejemnikAdmin,
                                 @Value("${turnirko.stripe.vrni-na}") String koren) {
        this.slikaRepozitorij = slikaRepozitorij;
        this.vsebinaRepozitorij = vsebinaRepozitorij;
        this.lastnistvoStoritev = lastnistvoStoritev;
        this.narocninaStoritev = narocninaStoritev;
        this.omejevalnik = omejevalnik;
        this.posta = posta;
        this.prejemnikAdmin = prejemnikAdmin == null ? "" : prejemnikAdmin.trim();
        this.koren = koren;
    }

    // ---------- Igralec sam ----------

    @Transactional(readOnly = true)
    public MojaProfilnaSlikaDto moja() {
        Uporabnik jaz = prijavljeni();
        Optional<ProfilnaSlika> slika = slikaRepozitorij.findByUporabnikId(jaz.getId());
        String zavrnitev = zakajNeSme(jaz);
        String pojasnilo = zavrnitev != null
                ? zavrnitev
                : slika.filter(s -> s.getStanje() == StanjeProfilneSlike.CAKA)
                        .map(s -> zakajCaka(jaz)).orElse(null);
        return new MojaProfilnaSlikaDto(
                slika.map(ProfilnaSlika::getZgostitev).orElse(null),
                slika.map(ProfilnaSlika::getStanje).orElse(null),
                zavrnitev == null,
                pojasnilo);
    }

    /* Nova slika zamenja staro (račun ima največ eno). Brskalnik pošlje izrez, a
       strežnik vseeno vzame kvadrat iz sredine in sliko zapiše znova - EXIF s
       krajem fotografije ne pride v bazo. */
    @Transactional
    public MojaProfilnaSlikaDto nalozi(byte[] vsebina) {
        Uporabnik jaz = prijavljeni();
        String zavrnitev = zakajNeSme(jaz);
        if (zavrnitev != null) {
            throw new PrepovedanoIzjema(zavrnitev);
        }
        if (!omejevalnik.dovoliInZabelezi("profilna:" + jaz.getId(), NAJVEC_NALAGANJ_NA_URO, Duration.ofHours(1))) {
            throw new PrevecZahtevIzjema("Preveč nalaganj. Poskusi čez eno uro.");
        }
        BufferedImage kvadrat = ObdelavaSlik.kvadrat(ObdelavaSlik.preberi(vsebina));
        if (kvadrat.getWidth() < NAJMANJ_STRANICA) {
            throw new NeveljavenVnosIzjema("Slika je premajhna (najmanj " + NAJMANJ_STRANICA + " × "
                    + NAJMANJ_STRANICA + " px).");
        }
        Zapis velika = ObdelavaSlik.zapisiFotografijo(ObdelavaSlik.pomanjsaj(kvadrat, ObdelavaSlik.STRANICA_PROFIL));
        Zapis mala = ObdelavaSlik.zapisiFotografijo(
                ObdelavaSlik.pomanjsaj(kvadrat, ObdelavaSlik.STRANICA_PROFIL_MALA));
        String zgostitev = SlikeStoritev.zgostitev(velika.vsebina());

        Optional<ProfilnaSlika> ista = slikaRepozitorij.findByZgostitev(zgostitev);
        if (ista.isPresent()) {
            if (!ista.get().getUporabnik().getId().equals(jaz.getId())) {
                throw new NeveljavenVnosIzjema("Ta fotografija je že profilna slika drugega računa.");
            }
            return moja();
        }

        pobrisiZaRacun(jaz.getId());
        StanjeProfilneSlike stanje = zakajCaka(jaz) == null
                ? StanjeProfilneSlike.OBJAVLJENA
                : StanjeProfilneSlike.CAKA;
        ProfilnaSlika slika = slikaRepozitorij.save(new ProfilnaSlika(jaz, zgostitev, stanje));
        vsebinaRepozitorij.save(new ProfilnaSlikaVsebina(slika.getId(), ProfilnaSlikaVsebina.VELIKA, velika.vsebina()));
        vsebinaRepozitorij.save(new ProfilnaSlikaVsebina(slika.getId(), ProfilnaSlikaVsebina.MALA, mala.vsebina()));
        obvestiAdmina(jaz, stanje);
        return moja();
    }

    @Transactional
    public void izbrisiMojo() {
        pobrisiZaRacun(prijavljeni().getId());
    }

    // ---------- Javno ----------

    /* Javne slike danih igralcev; igralca brez javne slike v odgovoru ni. */
    @Transactional(readOnly = true)
    public List<SlikaIgralcaDto> vidne(Collection<Long> idjiIgralcev) {
        if (idjiIgralcev == null || idjiIgralcev.isEmpty()) {
            return List.of();
        }
        if (idjiIgralcev.size() > NAJVEC_IGRALCEV) {
            throw new NeveljavenVnosIzjema("Največ " + NAJVEC_IGRALCEV + " igralcev naenkrat.");
        }
        return slikaRepozitorij.objavljeneZaIgralce(idjiIgralcev, StanjeProfilneSlike.OBJAVLJENA,
                        StatusRacuna.POTRJEN).stream()
                .filter(s -> narocninaStoritev.imaPremium(s.getUporabnik()))
                .map(s -> new SlikaIgralcaDto(s.getUporabnik().getIgralec().getId(), s.getZgostitev()))
                .toList();
    }

    /* Bajti po naslovu slike. Tudi slika, ki čaka, se postreže: njen naslov
       (SHA-256) dobita samo lastnik in admin, javni odgovori ga ne nosijo. */
    @Transactional(readOnly = true)
    public Optional<byte[]> vsebina(String zgostitev, String velikost) {
        return vsebinaRepozitorij.najdi(zgostitev, velikost).map(ProfilnaSlikaVsebina::getVsebina);
    }

    // ---------- Admin ----------

    /* Čakajoče in nedavno objavljene, najnovejše zgoraj. */
    @Transactional(readOnly = true)
    public List<ProfilnaSlikaPregledDto> pregled() {
        LocalDateTime od = LocalDateTime.now().minusDays(DNI_PREGLEDA);
        return slikaRepozitorij.vseZRacunom().stream()
                .filter(s -> s.getStanje() == StanjeProfilneSlike.CAKA || s.getNalozenaOb().isAfter(od))
                .map(this::zaPregled)
                .toList();
    }

    @Transactional
    public ProfilnaSlikaPregledDto objavi(Long idSlika) {
        ProfilnaSlika slika = slikaRepozitorij.findById(idSlika)
                .orElseThrow(() -> new NiNajdenoIzjema("Profilne slike z id " + idSlika + " ni."));
        slika.objavi();
        return zaPregled(slika);
    }

    @Transactional
    public void odstrani(Long idSlika) {
        ProfilnaSlika slika = slikaRepozitorij.findById(idSlika)
                .orElseThrow(() -> new NiNajdenoIzjema("Profilne slike z id " + idSlika + " ni."));
        pobrisi(slika);
    }

    // ---------- Pravila ----------

    /* Zakaj račun slike ne sme naložiti; null = sme. */
    String zakajNeSme(Uporabnik u) {
        if (u.getVloga() != Vloga.IGRALEC || !u.jePotrjenIgralec()) {
            return "Profilno sliko doda igralec, katerega račun je povezan z njegovim zapisom igralca.";
        }
        if (!narocninaStoritev.imaPremium(u)) {
            return "Profilna slika je del paketa Igralec Premium.";
        }
        return null;
    }

    /* Zakaj slika čaka na admina; null = javna takoj. */
    String zakajCaka(Uporabnik u) {
        if (mlajsiOd15(u.getIgralec())) {
            return "Slika igralca, mlajšega od 15 let, je javna, ko jo potrdi administrator.";
        }
        if (u.getVirPovezave() != VirPovezave.ADMIN) {
            return "Slika bo javna, ko administrator potrdi, da je račun res tvoj.";
        }
        return null;
    }

    static boolean mlajsiOd15(Igralec igralec) {
        LocalDate rojen = igralec == null ? null : igralec.getDatumRojstva();
        if (rojen == null || !rojen.isAfter(NEZNAN_DATUM)) {
            return false;
        }
        return Period.between(rojen, LocalDate.now()).getYears() < RegistracijaStoritev.STAROST_SKRBNIKA;
    }

    // ---------- Pomozno ----------

    private Uporabnik prijavljeni() {
        Uporabnik jaz = lastnistvoStoritev.trenutni();
        if (jaz == null) {
            throw new PrepovedanoIzjema("Za profilno sliko se prijavi.");
        }
        return jaz;
    }

    private void pobrisiZaRacun(Long idUporabnik) {
        slikaRepozitorij.findByUporabnikId(idUporabnik).ifPresent(this::pobrisi);
    }

    /* Prek entitet in izprano takoj: Hibernate vstavke izvede pred izbrisi (nova
       slika bi trčila ob staro, UNIQUE id_uporabnik), SQLite pa id izbrisane
       vrstice da naslednji. */
    private void pobrisi(ProfilnaSlika slika) {
        vsebinaRepozitorij.deleteAll(vsebinaRepozitorij.findByIdProfilnaSlika(slika.getId()));
        slikaRepozitorij.delete(slika);
        slikaRepozitorij.flush();
    }

    private ProfilnaSlikaPregledDto zaPregled(ProfilnaSlika s) {
        Uporabnik u = s.getUporabnik();
        Igralec igralec = u.getIgralec();
        boolean javna = s.getStanje() == StanjeProfilneSlike.OBJAVLJENA
                && u.getStatus() == StatusRacuna.POTRJEN && u.isAktiven() && igralec != null
                && narocninaStoritev.imaPremium(u);
        return new ProfilnaSlikaPregledDto(s.getId(),
                igralec != null ? igralec.getId() : null,
                igralec != null ? igralec.polnoIme() : u.getUporabniskoIme(),
                s.getZgostitev(), s.getStanje(), s.getNalozenaOb(),
                s.getStanje() == StanjeProfilneSlike.CAKA ? zakajCaka(u) : null,
                javna);
    }

    /* Ena kratka e-pošta na naslov admina - ista nastavitev kot obvestilo o
       čakajočem računu (turnirko.posta.admin; prazno = brez obvestil). */
    private void obvestiAdmina(Uporabnik u, StanjeProfilneSlike stanje) {
        if (prejemnikAdmin.isEmpty()) {
            return;
        }
        String ime = u.getIgralec().polnoIme();
        boolean caka = stanje == StanjeProfilneSlike.CAKA;
        String zadeva = (caka ? "Profilna slika čaka na potrditev: " : "Nova profilna slika: ") + ime;
        String besedilo = ime + " (" + u.getUporabniskoIme() + ") je naložil profilno sliko.\n\n"
                + (caka
                        ? "Slika ni javna, dokler je ne potrdiš. " + zakajCaka(u) + "\n\n"
                        : "Slika je že javna na profilu. Če ni primerna, jo odstrani.\n\n")
                + "Pregled slik: " + koren + "/igralci?slike=1\n";
        posta.poslji(prejemnikAdmin, zadeva, besedilo);
    }
}
