/* Upravljanje igralcev. Igralcev se nikoli ne brise - le arhivira,
   da zgodovina tekem in ratingov ostane popolna. */
package si.turnirko.storitve;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiFunction;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.IgralecDto;
import si.turnirko.dto.IgralecJavniDto;
import si.turnirko.dto.IgralecVnos;
import si.turnirko.dto.PodobenIgralecDto;
import si.turnirko.dto.PodobenIgralecJavniDto;
import si.turnirko.dto.PodobniIgralciVnos;
import si.turnirko.dto.ZunanjaUvrstitevVnos;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.PrimerjavaDatuma;
import si.turnirko.modeli.RatingStanje;
import si.turnirko.modeli.UjemanjeImena;
import si.turnirko.repozitoriji.IgralecRepozitorij;
import si.turnirko.repozitoriji.KlubRepozitorij;
import si.turnirko.repozitoriji.KrajRepozitorij;
import si.turnirko.repozitoriji.RatingStanjeRepozitorij;

@Service
public class IgralciStoritev {

    /* Koliko podobnih igralcev izpis vrne: najmocnejsi zadetki. Vec jih
       vpisovalec ne prebere, pri pogostem priimku (Novak) pa bi sicer
       opozorilo zapolnilo zaslon. */
    static final int NAJVEC_PODOBNIH = 10;

    private final IgralecRepozitorij igralecRepozitorij;
    private final KrajRepozitorij krajRepozitorij;
    private final KlubRepozitorij klubRepozitorij;
    private final RatingStanjeRepozitorij ratingStanjeRepozitorij;
    private final RatingStoritev ratingStoritev;

    public IgralciStoritev(IgralecRepozitorij igralecRepozitorij,
                           KrajRepozitorij krajRepozitorij,
                           KlubRepozitorij klubRepozitorij,
                           RatingStanjeRepozitorij ratingStanjeRepozitorij,
                           RatingStoritev ratingStoritev) {
        this.igralecRepozitorij = igralecRepozitorij;
        this.krajRepozitorij = krajRepozitorij;
        this.klubRepozitorij = klubRepozitorij;
        this.ratingStanjeRepozitorij = ratingStanjeRepozitorij;
        this.ratingStoritev = ratingStoritev;
    }

    /* Seznam aktivnih igralcev s trenutnim Turnirko ratingom, brez
       osebnih podatkov - to je izpis, ki ga vidi tudi neprijavljen gost. */
    @Transactional(readOnly = true)
    public List<IgralecJavniDto> seznam() {
        return izpis(IgralciStoritev::javniDto);
    }

    /* Isti seznam z osebnimi podatki. Klice ga samo koncna tocka, ki jo
       varnostna veriga omeji na ADMIN. */
    @Transactional(readOnly = true)
    public List<IgralecDto> seznamPodrobno() {
        return izpis(IgralciStoritev::dto);
    }

    @Transactional(readOnly = true)
    public IgralecJavniDto najdi(Long id) {
        return javniDto(najdiIgralca(id), stanje(id));
    }

    /* Poln izpis enega igralca - samo za ADMIN (glej seznamPodrobno). */
    @Transactional(readOnly = true)
    public IgralecDto najdiPodrobno(Long id) {
        return dto(najdiIgralca(id), stanje(id));
    }

    /* Skupno branje seznama: entitete in ratingi se naloziju enkrat, oblika
       izpisa (javna ali podrobna) pa je parameter. */
    private <T> List<T> izpis(BiFunction<Igralec, RatingStanje, T> vOblika) {
        List<Igralec> igralci = igralecRepozitorij.najdiAktivne();
        List<Long> idji = igralci.stream().map(Igralec::getId).toList();
        List<RatingStanje> ratingi = ratingStanjeRepozitorij
                .findByIgralecIdInAndSistem(idji, RatingStanje.SISTEM_TURNIRKO);
        return igralci.stream()
                .map(igralec -> vOblika.apply(igralec, najdiStanje(ratingi, igralec.getId())))
                .toList();
    }

    /* Postavitveni (zacetni) rating: dovoljen le za igralca brez odigranih tekem
       (RatingStoritev preveri to pravilo in zabelezi spremembo v dnevnik). */
    /* Vsi trije zapisi vracajo javni izpis: vmesnik odgovora ne bere (po
       shranjevanju osvezi seznam), organizator pa sme ustvariti igralca in
       mu odgovor ne sme vrniti osebnih podatkov nazaj. */
    @Transactional
    public IgralecJavniDto nastaviZacetniRating(Long id, int vrednost) {
        Igralec igralec = najdiIgralca(id);
        ratingStoritev.nastaviZacetniRating(igralec, vrednost);
        return javniDto(igralec, stanje(id));
    }

    /* Zunanja uvrstitev - dovoljena tudi igralcu s tekmami (glej
       RatingStoritev.zunanjaUvrstitev). */
    @Transactional
    public IgralecJavniDto zunanjaUvrstitev(Long id, ZunanjaUvrstitevVnos vnos) {
        Igralec igralec = najdiIgralca(id);
        ratingStoritev.zunanjaUvrstitev(igralec, vnos.vrednost(), vnos.vir(), vnos.pojasnilo());
        return javniDto(igralec, stanje(id));
    }

    /* Obstojeci igralci, ki so podobni vpisu - za organizatorja. Odlocajo
       samo imena (glej PodobnostImen): datum rojstva v zahtevi je za ta izpis
       NAMENOMA nepomemben, sicer bi organizator s poskusanjem datumov iz
       tega, ali se zadetek pojavi, izvedel datum rojstva znanega igralca.
       Isto ime lahko imata dve osebi, zato je izpis opozorilo in ne zavrnitev. */
    @Transactional(readOnly = true)
    public List<PodobenIgralecJavniDto> najdiPodobne(PodobniIgralciVnos vnos) {
        List<Zadetek> zadetki = najdiZadetke(vnos).stream()
                .sorted(Comparator.comparing((Zadetek z) -> z.ujemanje())
                        .thenComparing(Zadetek::igralec, Igralec.PO_ABECEDI))
                .limit(NAJVEC_PODOBNIH)
                .toList();
        List<RatingStanje> ratingi = ratingiZadetkov(zadetki);
        return zadetki.stream()
                .map(z -> new PodobenIgralecJavniDto(
                        javniDto(z.igralec(), najdiStanje(ratingi, z.igralec().getId())),
                        z.ujemanje(), z.igralec().isArhiviran()))
                .toList();
    }

    /* Isto za administratorja: poln zapis in primerjava datuma rojstva. Moc
       zadetka je vsota stopnje imena in stopnje datuma (Zadetek.moc), zato
       »Mihael Fatur« z enakim datumom stoji pred »Miha Fatur« z drugim. */
    @Transactional(readOnly = true)
    public List<PodobenIgralecDto> najdiPodobnePodrobno(PodobniIgralciVnos vnos) {
        List<Zadetek> zadetki = najdiZadetke(vnos).stream()
                .map(z -> vnos.datumRojstva() == null ? z : z.sDatumom(
                        PodobnostImen.primerjajDatum(vnos.datumRojstva(), z.igralec().getDatumRojstva())))
                .sorted(Comparator
                        .comparingInt((Zadetek z) -> z.moc())
                        .thenComparing(z -> z.ujemanje())
                        .thenComparing(Zadetek::igralec, Igralec.PO_ABECEDI))
                .limit(NAJVEC_PODOBNIH)
                .toList();
        List<RatingStanje> ratingi = ratingiZadetkov(zadetki);
        return zadetki.stream()
                .map(z -> new PodobenIgralecDto(
                        dto(z.igralec(), najdiStanje(ratingi, z.igralec().getId())),
                        z.ujemanje(), z.datum(), z.igralec().isArhiviran()))
                .toList();
    }

    /* Vsi, ki se imenu ujemajo - brez omejitve in brez razvrstitve (tisto je
       stvar izpisa). Preleti ves register v pomnilniku: poizvedba je redka
       (ob vsakem vpisu enega igralca), register pa tisoc zapisov. */
    private List<Zadetek> najdiZadetke(PodobniIgralciVnos vnos) {
        PodobnostImen.Ime vpis = PodobnostImen.Ime.iz(vnos.ime(), vnos.priimek());
        List<Zadetek> zadetki = new ArrayList<>();
        for (Igralec obstojec : igralecRepozitorij.najdiVseSKlubom()) {
            UjemanjeImena ujemanje = PodobnostImen.ujemanje(vpis, vnos.spol(),
                    PodobnostImen.Ime.iz(obstojec.getIme(), obstojec.getPriimek()), obstojec.getSpol());
            if (ujemanje != null) {
                zadetki.add(new Zadetek(obstojec, ujemanje, null));
            }
        }
        return zadetki;
    }

    private List<RatingStanje> ratingiZadetkov(List<Zadetek> zadetki) {
        if (zadetki.isEmpty()) {
            return List.of();
        }
        return ratingStanjeRepozitorij.findByIgralecIdInAndSistem(
                zadetki.stream().map(z -> z.igralec().getId()).toList(),
                RatingStanje.SISTEM_TURNIRKO);
    }

    /* Obstojec igralec, ki se ujema z vpisom; datum je izpolnjen samo v
       administratorjevem izpisu. */
    private record Zadetek(Igralec igralec, UjemanjeImena ujemanje, PrimerjavaDatuma datum) {

        Zadetek sDatumom(PrimerjavaDatuma datum) {
            return new Zadetek(igralec, ujemanje, datum);
        }

        /* Manj = verjetnejsi dvojnik. Drug datum steje vec kot podobno ime:
           isto ime imata pogosto dve osebi, enak datum rojstva pa le redko. */
        int moc() {
            int zaDatum = datum == null ? 0 : switch (datum) {
                case ENAK -> 0;
                case PODOBEN -> 1;
                case DRUG -> 3;
            };
            return ujemanje.ordinal() + zaDatum;
        }
    }

    @Transactional
    public IgralecJavniDto ustvari(IgralecVnos vnos) {
        Igralec igralec = new Igralec();
        prepisi(igralec, vnos);
        return javniDto(igralecRepozitorij.save(igralec), null);
    }

    @Transactional
    public IgralecJavniDto posodobi(Long id, IgralecVnos vnos) {
        Igralec igralec = najdiIgralca(id);
        prepisi(igralec, vnos);
        return javniDto(igralec, stanje(id));
    }

    /* Namesto brisanja - arhiviranje (zgodovina tekem ostane). */
    @Transactional
    public void arhiviraj(Long id) {
        najdiIgralca(id).setArhiviran(true);
    }

    private void prepisi(Igralec igralec, IgralecVnos vnos) {
        igralec.setIme(vnos.ime().trim());
        igralec.setPriimek(vnos.priimek().trim());
        igralec.setSpol(vnos.spol());
        igralec.setDatumRojstva(vnos.datumRojstva());
        igralec.setEmail(ocisti(vnos.email()));
        igralec.setTelefonskaSt(ocisti(vnos.telefonskaSt()));
        igralec.setIgralnaRoka(vnos.igralnaRoka());
        igralec.setNtzsLicenca(ocisti(vnos.ntzsLicenca()));
        igralec.setDrzavljanstvo(vnos.drzavljanstvo() == null || vnos.drzavljanstvo().isBlank()
                ? "SLO" : vnos.drzavljanstvo().trim());
        igralec.setNaslov(ocisti(vnos.naslov()));
        /* Velja ob igralcevi PRVI tekmi (SidroStoritev.zacetniRating). Kdor
           tekme ze ima, dobi nov zacetek sele s preracunom od dneva prve tekme
           - samodejno ga ne sprozimo, ker admin oznaci vec igralcev naenkrat
           in je en preracun na koncu dovolj. */
        igralec.setRekreativniVstop(Boolean.TRUE.equals(vnos.rekreativniVstop()));

        if (vnos.postnaSt() == null) {
            igralec.setKraj(null);
        } else {
            igralec.setKraj(krajRepozitorij.findById(vnos.postnaSt())
                    .orElseThrow(() -> new NeveljavenVnosIzjema(
                            "Kraj s postno stevilko " + vnos.postnaSt() + " ne obstaja.")));
        }
        if (vnos.idKlub() == null) {
            igralec.setKlub(null);
        } else {
            igralec.setKlub(klubRepozitorij.findById(vnos.idKlub())
                    .orElseThrow(() -> new NeveljavenVnosIzjema(
                            "Klub z id " + vnos.idKlub() + " ne obstaja.")));
        }
    }

    /* Prazen niz shrani kot null, da UNIQUE omejitve delujejo pravilno. */
    private String ocisti(String vrednost) {
        return vrednost == null || vrednost.isBlank() ? null : vrednost.trim();
    }

    private static IgralecDto dto(Igralec igralec, RatingStanje stanje) {
        return IgralecDto.iz(igralec,
                stanje != null ? stanje.getVrednost() : null,
                stanje != null ? stanje.getStTekem() : 0);
    }

    private static IgralecJavniDto javniDto(Igralec igralec, RatingStanje stanje) {
        return IgralecJavniDto.iz(igralec,
                stanje != null ? stanje.getVrednost() : null,
                stanje != null ? stanje.getStTekem() : 0,
                LocalDate.now());
    }

    private RatingStanje stanje(Long idIgralca) {
        return ratingStanjeRepozitorij
                .findByIgralecIdAndSistem(idIgralca, RatingStanje.SISTEM_TURNIRKO)
                .orElse(null);
    }

    private RatingStanje najdiStanje(List<RatingStanje> ratingi, Long idIgralca) {
        return ratingi.stream()
                .filter(r -> r.getIgralec().getId().equals(idIgralca))
                .findFirst()
                .orElse(null);
    }

    private Igralec najdiIgralca(Long id) {
        return igralecRepozitorij.najdiZVsem(id)
                .orElseThrow(() -> new NiNajdenoIzjema("Igralec z id " + id + " ne obstaja."));
    }
}
