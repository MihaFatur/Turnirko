/* Poslovna logika lig: konfiguracija lige, ekipe, kader in generiranje
   razporeda po kroznem sistemu. Prehodi stanj:
   PRIPRAVA (ureja se konfiguracija, ekipe, kader) -> V_TEKU (razpored
   generiran, srecanja tecejo) -> ZAKLJUCEN.

   Spremembe strukture (ekipe, kader, format) so mozne le v PRIPRAVI, da se
   ze generiran razpored ne razveljavi. */
package si.turnirko.storitve;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.EkipaDto;
import si.turnirko.dto.EkipaVnos;
import si.turnirko.dto.KaderIgralecDto;
import si.turnirko.dto.KaderVnos;
import si.turnirko.dto.LestvicaEkipeDto;
import si.turnirko.dto.LigaDto;
import si.turnirko.dto.LigaVnos;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.modeli.Ekipa;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.KaderEkipe;
import si.turnirko.modeli.Klub;
import si.turnirko.modeli.Liga;
import si.turnirko.modeli.Srecanje;
import si.turnirko.modeli.StatusTekmovanja;
import si.turnirko.repozitoriji.EkipaRepozitorij;
import si.turnirko.repozitoriji.IgralecRepozitorij;
import si.turnirko.repozitoriji.KaderEkipeRepozitorij;
import si.turnirko.repozitoriji.KlubRepozitorij;
import si.turnirko.repozitoriji.LigaRepozitorij;
import si.turnirko.repozitoriji.SrecanjeRepozitorij;

@Service
public class LigaStoritev {

    private final LigaRepozitorij ligaRepozitorij;
    private final EkipaRepozitorij ekipaRepozitorij;
    private final KaderEkipeRepozitorij kaderRepozitorij;
    private final SrecanjeRepozitorij srecanjeRepozitorij;
    private final KlubRepozitorij klubRepozitorij;
    private final IgralecRepozitorij igralecRepozitorij;
    private final RazporedStoritev razporedStoritev;
    private final LestvicaLigeStoritev lestvicaLigeStoritev;
    private final SpremembeEloStoritev spremembeEloStoritev;
    private final LastnistvoStoritev lastnistvo;

    public LigaStoritev(LigaRepozitorij ligaRepozitorij,
                        EkipaRepozitorij ekipaRepozitorij,
                        KaderEkipeRepozitorij kaderRepozitorij,
                        SrecanjeRepozitorij srecanjeRepozitorij,
                        KlubRepozitorij klubRepozitorij,
                        IgralecRepozitorij igralecRepozitorij,
                        RazporedStoritev razporedStoritev,
                        LestvicaLigeStoritev lestvicaLigeStoritev,
                        SpremembeEloStoritev spremembeEloStoritev,
                        LastnistvoStoritev lastnistvo) {
        this.ligaRepozitorij = ligaRepozitorij;
        this.ekipaRepozitorij = ekipaRepozitorij;
        this.kaderRepozitorij = kaderRepozitorij;
        this.srecanjeRepozitorij = srecanjeRepozitorij;
        this.klubRepozitorij = klubRepozitorij;
        this.igralecRepozitorij = igralecRepozitorij;
        this.razporedStoritev = razporedStoritev;
        this.lestvicaLigeStoritev = lestvicaLigeStoritev;
        this.spremembeEloStoritev = spremembeEloStoritev;
        this.lastnistvo = lastnistvo;
    }

    // ---------- Liga ----------

    @Transactional(readOnly = true)
    public List<LigaDto> vse() {
        return ligaRepozitorij.najdiVse().stream()
                .map(l -> LigaDto.iz(l, (int) ekipaRepozitorij.countByLigaId(l.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public LigaDto najdi(Long id) {
        Liga l = ligaRepozitorij.najdiZVisjo(id)
                .orElseThrow(() -> new NiNajdenoIzjema("Liga z id " + id + " ne obstaja."));
        return LigaDto.iz(l, (int) ekipaRepozitorij.countByLigaId(id));
    }

    @Transactional
    public LigaDto ustvari(LigaVnos v) {
        Liga liga = new Liga();
        uporabiVnos(liga, v);
        // zabelezi lastnika (organizator oz. admin, ki jo ustvarja)
        lastnistvo.oznaciLastnika(liga);
        liga = ligaRepozitorij.save(liga);
        return LigaDto.iz(liga, 0);
    }

    @Transactional
    public LigaDto uredi(Long id, LigaVnos v) {
        lastnistvo.preveriLigaPoId(id);
        Liga liga = ligaRepozitorij.findById(id)
                .orElseThrow(() -> new NiNajdenoIzjema("Liga z id " + id + " ne obstaja."));
        if (liga.getStatus() != StatusTekmovanja.PRIPRAVA) {
            throw new DomenskaIzjema("Ligo je mogoce urejati samo, dokler je v pripravi.");
        }
        uporabiVnos(liga, v);
        liga = ligaRepozitorij.save(liga);
        return LigaDto.iz(liga, (int) ekipaRepozitorij.countByLigaId(id));
    }

    @Transactional
    public void zbrisi(Long id) {
        lastnistvo.preveriLigaPoId(id);
        Liga liga = ligaRepozitorij.findById(id)
                .orElseThrow(() -> new NiNajdenoIzjema("Liga z id " + id + " ne obstaja."));
        if (srecanjeRepozitorij.existsByLigaId(id)) {
            throw new DomenskaIzjema("Lige z generiranim razporedom ni mogoce izbrisati.");
        }
        for (Ekipa e : ekipaRepozitorij.najdiZaLigo(id)) {
            kaderRepozitorij.deleteAll(kaderRepozitorij.najdiZaEkipo(e.getId()));
        }
        ekipaRepozitorij.deleteAll(ekipaRepozitorij.najdiZaLigo(id));
        ligaRepozitorij.delete(liga);
    }

    // ---------- Ekipe ----------

    @Transactional(readOnly = true)
    public List<EkipaDto> ekipe(Long idLiga) {
        return ekipaRepozitorij.najdiZaLigo(idLiga).stream().map(EkipaDto::iz).toList();
    }

    @Transactional
    public EkipaDto dodajEkipo(Long idLiga, EkipaVnos v) {
        lastnistvo.preveriLigaPoId(idLiga);
        Liga liga = ligaRepozitorij.findById(idLiga)
                .orElseThrow(() -> new NiNajdenoIzjema("Liga z id " + idLiga + " ne obstaja."));
        preveriVPripravi(liga);
        Klub klub = klubRepozitorij.findById(v.idKlub())
                .orElseThrow(() -> new NiNajdenoIzjema("Klub z id " + v.idKlub() + " ne obstaja."));

        int zaporedna = v.zaporedna() != null ? v.zaporedna() : naslednjaZaporedna(idLiga, klub.getId());
        if (zaporedna < 1) {
            throw new NeveljavenVnosIzjema("Zaporedna stevilka ekipe mora biti vsaj 1.");
        }
        if (ekipaRepozitorij.existsByLigaIdAndKlubIdAndZaporedna(idLiga, klub.getId(), zaporedna)) {
            throw new DomenskaIzjema("Ekipa " + klub.getIme() + " " + zaporedna + " v tej ligi ze obstaja.");
        }
        Ekipa ekipa = ekipaRepozitorij.save(new Ekipa(liga, klub, zaporedna,
                v.ime() != null && !v.ime().isBlank() ? v.ime().trim() : null));
        // za DTO potrebujemo klub (nalozen); ponovno preberi z join fetch
        return EkipaDto.iz(ekipaRepozitorij.najdiZKlubomInLigo(ekipa.getId()).orElseThrow());
    }

    @Transactional
    public void odstraniEkipo(Long idEkipa) {
        lastnistvo.preveriLigaPoEkipi(idEkipa);
        Ekipa ekipa = ekipaRepozitorij.najdiZKlubomInLigo(idEkipa)
                .orElseThrow(() -> new NiNajdenoIzjema("Ekipa z id " + idEkipa + " ne obstaja."));
        preveriVPripravi(ekipa.getLiga());
        kaderRepozitorij.deleteAll(kaderRepozitorij.najdiZaEkipo(idEkipa));
        ekipaRepozitorij.delete(ekipa);
    }

    // ---------- Kader ----------

    @Transactional(readOnly = true)
    public List<KaderIgralecDto> kader(Long idEkipa) {
        List<KaderEkipe> kader = kaderRepozitorij.najdiZaEkipo(idEkipa);
        Map<Long, Integer> ratingi = spremembeEloStoritev.trenutniRatingi(
                kader.stream().map(k -> k.getIgralec().getId()).toList());
        return kader.stream()
                .map(k -> KaderIgralecDto.iz(k, ratingi.get(k.getIgralec().getId())))
                .toList();
    }

    @Transactional
    public KaderIgralecDto dodajVKader(Long idEkipa, KaderVnos v) {
        lastnistvo.preveriLigaPoEkipi(idEkipa);
        Ekipa ekipa = ekipaRepozitorij.najdiZKlubomInLigo(idEkipa)
                .orElseThrow(() -> new NiNajdenoIzjema("Ekipa z id " + idEkipa + " ne obstaja."));
        Igralec igralec = igralecRepozitorij.najdiZVsem(v.idIgralec())
                .orElseThrow(() -> new NiNajdenoIzjema("Igralec z id " + v.idIgralec() + " ne obstaja."));

        if (kaderRepozitorij.existsByEkipaIdAndIgralecId(idEkipa, igralec.getId())) {
            throw new DomenskaIzjema("Igralec " + igralec.polnoIme() + " je ze v kadru te ekipe.");
        }
        if (ekipa.getLiga().isPrepovedDvojneRegistracije()
                && kaderRepozitorij.steviloVLigi(ekipa.getLiga().getId(), igralec.getId()) > 0) {
            throw new DomenskaIzjema("Igralec " + igralec.polnoIme()
                    + " je v tej ligi ze registriran za drugo ekipo (dvojna registracija ni dovoljena).");
        }
        KaderEkipe vnos = kaderRepozitorij.save(new KaderEkipe(ekipa, igralec, v.vrstniRed()));
        Integer rating = spremembeEloStoritev.trenutniRatingi(List.of(igralec.getId())).get(igralec.getId());
        return KaderIgralecDto.iz(vnos, rating);
    }

    @Transactional
    public void odstraniIzKadra(Long idKader) {
        lastnistvo.preveriLigaPoKadru(idKader);
        KaderEkipe k = kaderRepozitorij.findById(idKader)
                .orElseThrow(() -> new NiNajdenoIzjema("Vnos kadra z id " + idKader + " ne obstaja."));
        kaderRepozitorij.delete(k);
    }

    // ---------- Razpored ----------

    @Transactional
    public void generirajRazpored(Long idLiga) {
        lastnistvo.preveriLigaPoId(idLiga);
        Liga liga = ligaRepozitorij.findById(idLiga)
                .orElseThrow(() -> new NiNajdenoIzjema("Liga z id " + idLiga + " ne obstaja."));
        if (liga.getStatus() != StatusTekmovanja.PRIPRAVA) {
            throw new DomenskaIzjema("Razpored je mogoce generirati samo, ko je liga v pripravi.");
        }
        if (srecanjeRepozitorij.existsByLigaId(idLiga)) {
            throw new DomenskaIzjema("Razpored za to ligo je ze generiran.");
        }
        List<Ekipa> ekipe = new ArrayList<>(ekipaRepozitorij.najdiZaLigo(idLiga));
        ekipe.sort(Comparator.comparing(Ekipa::getId));
        if (ekipe.size() < 2) {
            throw new DomenskaIzjema("Za razpored sta potrebni vsaj dve ekipi.");
        }

        List<Srecanje> srecanja = new ArrayList<>();
        for (RazporedStoritev.Par par : razporedStoritev.razpored(ekipe.size(), liga.isDvokrozno())) {
            srecanja.add(new Srecanje(liga, par.kolo(), ekipe.get(par.domaci()), ekipe.get(par.gost())));
        }
        srecanjeRepozitorij.saveAll(srecanja);

        liga.setStatus(StatusTekmovanja.V_TEKU);
        ligaRepozitorij.save(liga);
    }

    @Transactional(readOnly = true)
    public List<LestvicaEkipeDto> lestvica(Long idLiga) {
        return lestvicaLigeStoritev.lestvica(idLiga);
    }

    // ---------- Pomozno ----------

    private void uporabiVnos(Liga liga, LigaVnos v) {
        if (v.steviloNizov() == null || (v.steviloNizov() != 3 && v.steviloNizov() != 5 && v.steviloNizov() != 7)) {
            throw new NeveljavenVnosIzjema("Stevilo nizov mora biti 3, 5 ali 7.");
        }
        if (v.zmagZaSrecanje() != null) {
            if (v.zmagZaSrecanje() < 1) {
                throw new NeveljavenVnosIzjema("Prag zmag za srecanje mora biti vsaj 1.");
            }
            if (v.zmagZaSrecanje() > v.formatSrecanja().stTekem()) {
                throw new NeveljavenVnosIzjema("Prag zmag za srecanje ne sme presegati stevila tekem ("
                        + v.formatSrecanja().stTekem() + ").");
            }
        }
        liga.setIme(v.ime().trim());
        liga.setSezona(v.sezona() != null && !v.sezona().isBlank() ? v.sezona().trim() : null);
        liga.setSpolKategorija(v.spolKategorija());
        liga.setFormatSrecanja(v.formatSrecanja());
        liga.setSteviloNizov(v.steviloNizov());
        liga.setZmagZaSrecanje(v.zmagZaSrecanje());
        liga.setDvokrozno(v.dvokrozno() == null || v.dvokrozno());
        liga.setTockeZmaga(v.tockeZmaga() != null ? v.tockeZmaga() : 2);
        liga.setTockeNeodloceno(v.tockeNeodloceno() != null ? v.tockeNeodloceno() : 1);
        liga.setTockePoraz(v.tockePoraz() != null ? v.tockePoraz() : 0);
        liga.setDovoljenoNeodloceno(v.dovoljenoNeodloceno() == null || v.dovoljenoNeodloceno());
        liga.setPrepovedDvojneRegistracije(Boolean.TRUE.equals(v.prepovedDvojneRegistracije()));
        liga.setStejeVElo(v.stejeVElo() == null || v.stejeVElo());
        liga.setStNapreduje(v.stNapreduje() != null ? Math.max(0, v.stNapreduje()) : 0);
        liga.setStIzpade(v.stIzpade() != null ? Math.max(0, v.stIzpade()) : 0);
        if (v.idVisjaLiga() != null) {
            Liga visja = ligaRepozitorij.findById(v.idVisjaLiga())
                    .orElseThrow(() -> new NiNajdenoIzjema("Visja liga z id " + v.idVisjaLiga() + " ne obstaja."));
            if (liga.getId() != null && liga.getId().equals(visja.getId())) {
                throw new NeveljavenVnosIzjema("Liga ne more biti sama sebi nadrejena.");
            }
            liga.setVisjaLiga(visja);
        } else {
            liga.setVisjaLiga(null);
        }
    }

    private void preveriVPripravi(Liga liga) {
        if (liga.getStatus() != StatusTekmovanja.PRIPRAVA) {
            throw new DomenskaIzjema("Ekipe in kader je mogoce spreminjati samo, dokler je liga v pripravi.");
        }
    }

    private int naslednjaZaporedna(Long idLiga, Long idKlub) {
        int najvecja = ekipaRepozitorij.najdiZaLigo(idLiga).stream()
                .filter(e -> e.getKlub().getId().equals(idKlub))
                .mapToInt(Ekipa::getZaporedna)
                .max().orElse(0);
        return najvecja + 1;
    }
}
