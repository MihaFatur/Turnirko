/* Upravljanje igralcev. Igralcev se nikoli ne brise - le arhivira,
   da zgodovina tekem in ratingov ostane popolna. */
package si.turnirko.storitve;

import java.util.List;
import java.util.function.BiFunction;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.IgralecDto;
import si.turnirko.dto.IgralecJavniDto;
import si.turnirko.dto.IgralecVnos;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.RatingStanje;
import si.turnirko.repozitoriji.IgralecRepozitorij;
import si.turnirko.repozitoriji.KlubRepozitorij;
import si.turnirko.repozitoriji.KrajRepozitorij;
import si.turnirko.repozitoriji.RatingStanjeRepozitorij;

@Service
public class IgralciStoritev {

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

    /* Seznam aktivnih igralcev s trenutnim klubskim ELO ratingom, brez
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
                .findByIgralecIdInAndSistem(idji, RatingStanje.SISTEM_KLUBSKI_ELO);
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
                stanje != null ? stanje.getStTekem() : 0);
    }

    private RatingStanje stanje(Long idIgralca) {
        return ratingStanjeRepozitorij
                .findByIgralecIdAndSistem(idIgralca, RatingStanje.SISTEM_KLUBSKI_ELO)
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
