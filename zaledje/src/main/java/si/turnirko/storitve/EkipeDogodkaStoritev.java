/* Ekipe in kadri ekipnega dogodka turnirja (disciplina EKIPNO, V28).

   Ekipa se na dogodek prijavi kot celota: nastane ekipa dogodka in njena
   PRIJAVA - ta je tekmovalna enota, ki gre v zreb, mrezo in skupine. Pravila
   imena so ista kot pri ligi (klubska ekipa z zaporedno, prosta z lastnim
   imenom, prikazano ime na dogodku enolicno), ker ju vmesnik izpise enako.

   Kader pove, kdo sme igrati. Igralec na dogodku nastopa za eno samo ekipo:
   postava srecanja se polni iz kadra, in igralec, ki bi bil v dveh kadrih,
   bi lahko v istem krogu igral za obe. Kader se sme dopolnjevati tudi med
   tekmovanjem (ekipa pripelje rezervo), odstraniti pa ni mogoce igralca, ki
   je za ekipo ze nastopil - njegove tekme bi ostale brez clana kadra. */
package si.turnirko.storitve;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.EkipaDto;
import si.turnirko.dto.EkipaVnos;
import si.turnirko.dto.KaderIgralecDto;
import si.turnirko.dto.KaderVnos;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.Ekipa;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.KaderEkipe;
import si.turnirko.modeli.Klub;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.Spol;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.StatusTekmovanja;
import si.turnirko.repozitoriji.DogodekRepozitorij;
import si.turnirko.repozitoriji.EkipaRepozitorij;
import si.turnirko.repozitoriji.IgralecRepozitorij;
import si.turnirko.repozitoriji.KaderEkipeRepozitorij;
import si.turnirko.repozitoriji.KlubRepozitorij;
import si.turnirko.repozitoriji.PrijavaRepozitorij;
import si.turnirko.repozitoriji.TekmaSrecanjaRepozitorij;
import si.turnirko.storitve.LestvicaLigeStoritev.Bilanca;
import si.turnirko.storitve.LestvicaLigeStoritev.BilanceLige;

@Service
public class EkipeDogodkaStoritev {

    private final DogodekRepozitorij dogodekRepozitorij;
    private final EkipaRepozitorij ekipaRepozitorij;
    private final PrijavaRepozitorij prijavaRepozitorij;
    private final KaderEkipeRepozitorij kaderRepozitorij;
    private final KlubRepozitorij klubRepozitorij;
    private final IgralecRepozitorij igralecRepozitorij;
    private final TekmaSrecanjaRepozitorij tekmaSrecanjaRepozitorij;
    private final SpremembeRatingaStoritev spremembeRatinga;
    private final LestvicaLigeStoritev lestvicaLigeStoritev;
    private final LastnistvoStoritev lastnistvo;

    public EkipeDogodkaStoritev(DogodekRepozitorij dogodekRepozitorij,
                                EkipaRepozitorij ekipaRepozitorij,
                                PrijavaRepozitorij prijavaRepozitorij,
                                KaderEkipeRepozitorij kaderRepozitorij,
                                KlubRepozitorij klubRepozitorij,
                                IgralecRepozitorij igralecRepozitorij,
                                TekmaSrecanjaRepozitorij tekmaSrecanjaRepozitorij,
                                SpremembeRatingaStoritev spremembeRatinga,
                                LestvicaLigeStoritev lestvicaLigeStoritev,
                                LastnistvoStoritev lastnistvo) {
        this.dogodekRepozitorij = dogodekRepozitorij;
        this.ekipaRepozitorij = ekipaRepozitorij;
        this.prijavaRepozitorij = prijavaRepozitorij;
        this.kaderRepozitorij = kaderRepozitorij;
        this.klubRepozitorij = klubRepozitorij;
        this.igralecRepozitorij = igralecRepozitorij;
        this.tekmaSrecanjaRepozitorij = tekmaSrecanjaRepozitorij;
        this.spremembeRatinga = spremembeRatinga;
        this.lestvicaLigeStoritev = lestvicaLigeStoritev;
        this.lastnistvo = lastnistvo;
    }

    @Transactional(readOnly = true)
    public List<EkipaDto> ekipe(Long idDogodek) {
        Map<Long, Integer> velikostKadra = new HashMap<>();
        for (Object[] r : kaderRepozitorij.steviloPoEkipahDogodka(idDogodek)) {
            velikostKadra.put(((Number) r[0]).longValue(), ((Number) r[1]).intValue());
        }
        return ekipaRepozitorij.najdiZaDogodek(idDogodek).stream()
                .map(e -> EkipaDto.iz(e, velikostKadra.getOrDefault(e.getId(), 0)))
                .toList();
    }

    /* Prijava ekipe na dogodek: ekipa in njena prijava nastaneta skupaj. */
    @Transactional
    public EkipaDto dodajEkipo(Long idDogodek, EkipaVnos v) {
        lastnistvo.preveriTurnirPoDogodku(idDogodek);
        Dogodek dogodek = ekipniDogodek(idDogodek);
        if (dogodek.getStatus() != StatusTekmovanja.PRIPRAVA) {
            throw new DomenskaIzjema("Ekipe je mogoce prijavljati samo, dokler je dogodek v pripravi.");
        }

        String ime = v.ime() != null && !v.ime().isBlank() ? v.ime().trim() : null;
        List<Ekipa> obstojece = ekipaRepozitorij.najdiZaDogodek(idDogodek);
        Klub klub = null;
        int zaporedna = 1;
        if (v.idKlub() != null) {
            klub = klubRepozitorij.findById(v.idKlub())
                    .orElseThrow(() -> new NiNajdenoIzjema("Klub z id " + v.idKlub() + " ne obstaja."));
            Long idKluba = klub.getId();
            zaporedna = v.zaporedna() != null ? v.zaporedna()
                    : obstojece.stream()
                            .filter(e -> !e.jeProsta() && e.getKlub().getId().equals(idKluba))
                            .mapToInt(Ekipa::getZaporedna).max().orElse(0) + 1;
            if (zaporedna < 1) {
                throw new NeveljavenVnosIzjema("Zaporedna stevilka ekipe mora biti vsaj 1.");
            }
        } else if (ime == null) {
            throw new NeveljavenVnosIzjema("Ekipa brez kluba mora imeti ime.");
        }
        // v mrezi in skupinah se ekipi locita samo po prikazanem imenu
        String prikazano = ime != null ? ime : klub.getIme() + " " + zaporedna;
        if (obstojece.stream().anyMatch(e -> e.prikazanoIme().equalsIgnoreCase(prikazano))) {
            throw new DomenskaIzjema("Ekipa \"" + prikazano + "\" je na tem dogodku ze prijavljena.");
        }

        Ekipa ekipa = ekipaRepozitorij.save(new Ekipa(dogodek, klub, zaporedna, ime));
        prijavaRepozitorij.save(new Prijava(dogodek, ekipa));
        return EkipaDto.iz(ekipaRepozitorij.najdiZKlubomInDogodkom(ekipa.getId()).orElseThrow(), 0);
    }

    /* Odjava ekipe pred zrebom - kot pri igralcu, a tu gre izbris do konca:
       ekipa zunaj dogodka ne obstaja, njen kader pa ni nikjer drugje. */
    @Transactional
    public void odstraniEkipo(Long idEkipa) {
        lastnistvo.preveriPoEkipi(idEkipa);
        Ekipa ekipa = najdiEkipo(idEkipa);
        if (ekipa.getDogodek().getStatus() != StatusTekmovanja.PRIPRAVA) {
            throw new DomenskaIzjema("Po zrebu ekipe ni mogoce odstraniti - uporabi odstop.");
        }
        prijavaRepozitorij.najdiZaEkipo(idEkipa).ifPresent(prijavaRepozitorij::delete);
        kaderRepozitorij.deleteAll(kaderRepozitorij.najdiZaEkipo(idEkipa));
        ekipaRepozitorij.delete(ekipa);
    }

    /* Kader ekipe z ratingom in izkupickom posamicnih tekem za to ekipo na
       tem dogodku. */
    @Transactional(readOnly = true)
    public List<KaderIgralecDto> kader(Long idEkipa) {
        Ekipa ekipa = najdiEkipo(idEkipa);
        List<KaderEkipe> kader = kaderRepozitorij.najdiZaEkipo(idEkipa);
        Map<Long, Integer> ratingi = spremembeRatinga.trenutniRatingi(
                kader.stream().map(k -> k.getIgralec().getId()).toList());
        BilanceLige bilance = lestvicaLigeStoritev.bilancePosamicnihDogodka(ekipa.getDogodek().getId());
        return kader.stream()
                .map(k -> {
                    Bilanca b = bilance.za(idEkipa, k.getIgralec().getId());
                    return KaderIgralecDto.iz(k, ratingi.get(k.getIgralec().getId()), b.zmage(), b.porazi());
                })
                .sorted(Comparator.comparingInt(KaderIgralecDto::zmage).reversed()
                        .thenComparingInt(KaderIgralecDto::porazi))
                .toList();
    }

    @Transactional
    public KaderIgralecDto dodajVKader(Long idEkipa, KaderVnos v) {
        lastnistvo.preveriPoEkipi(idEkipa);
        Ekipa ekipa = najdiEkipo(idEkipa);
        Dogodek dogodek = ekipa.getDogodek();
        if (dogodek.getStatus() == StatusTekmovanja.ZAKLJUCEN) {
            throw new DomenskaIzjema("Dogodek je zakljucen - kadra ni mogoce spreminjati.");
        }
        Igralec igralec = igralecRepozitorij.najdiZVsem(v.idIgralec())
                .orElseThrow(() -> new NiNajdenoIzjema("Igralec z id " + v.idIgralec() + " ne obstaja."));
        if (igralec.isArhiviran()) {
            throw new DomenskaIzjema("Igralec " + igralec.polnoIme() + " je arhiviran.");
        }
        preveriSpol(igralec, dogodek.getSpolKategorija());
        kaderRepozitorij.vKadruDogodka(dogodek.getId(), igralec.getId()).stream().findFirst()
                .ifPresent(k -> {
                    throw new DomenskaIzjema("Igralec " + igralec.polnoIme()
                            + " je na tem dogodku ze v kadru ekipe " + k.getEkipa().prikazanoIme() + ".");
                });
        KaderEkipe vnos = kaderRepozitorij.save(new KaderEkipe(ekipa, igralec, v.vrstniRed()));
        Integer rating = spremembeRatinga.trenutniRatingi(List.of(igralec.getId())).get(igralec.getId());
        return KaderIgralecDto.iz(vnos, rating, 0, 0);
    }

    @Transactional
    public void odstraniIzKadra(Long idKader) {
        lastnistvo.preveriPoKadru(idKader);
        KaderEkipe k = kaderRepozitorij.findById(idKader)
                .orElseThrow(() -> new NiNajdenoIzjema("Vnos kadra z id " + idKader + " ne obstaja."));
        if (tekmaSrecanjaRepozitorij.steviloNastopovZaEkipo(k.getEkipa().getId(), k.getIgralec().getId()) > 0) {
            throw new DomenskaIzjema("Igralec je za ekipo ze nastopil - iz kadra ga ni mogoce odstraniti.");
        }
        kaderRepozitorij.delete(k);
    }

    private Dogodek ekipniDogodek(Long idDogodek) {
        Dogodek dogodek = dogodekRepozitorij.najdiSTurnirjem(idDogodek)
                .orElseThrow(() -> new NiNajdenoIzjema("Dogodek z id " + idDogodek + " ne obstaja."));
        if (!dogodek.jeEkipno()) {
            throw new DomenskaIzjema("Ekipe se prijavljajo samo na ekipni dogodek.");
        }
        return dogodek;
    }

    private Ekipa najdiEkipo(Long idEkipa) {
        return ekipaRepozitorij.najdiZKlubomInDogodkom(idEkipa)
                .orElseThrow(() -> new NiNajdenoIzjema("Ekipa dogodka z id " + idEkipa + " ne obstaja."));
    }

    /* Na ekipni dogodek za moske smejo samo moski, za zenske samo zenske. */
    private static void preveriSpol(Igralec igralec, SpolKategorija kategorija) {
        boolean ustreza = switch (kategorija) {
            case MOSKI -> igralec.getSpol() == Spol.MOSKI;
            case ZENSKE -> igralec.getSpol() == Spol.ZENSKI;
            case MESANO, KDORKOLI -> true;
        };
        if (!ustreza) {
            throw new DomenskaIzjema("Igralec " + igralec.polnoIme()
                    + " po spolu ne ustreza kategoriji " + kategorija + ".");
        }
    }
}
