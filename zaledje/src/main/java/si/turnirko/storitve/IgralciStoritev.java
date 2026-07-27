/* Upravljanje igralcev. Igralcev se nikoli ne brise - le arhivira,
   da zgodovina tekem in ratingov ostane popolna. */
package si.turnirko.storitve;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.IgralecDto;
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

    /* Seznam aktivnih igralcev s trenutnim klubskim ELO ratingom. */
    @Transactional(readOnly = true)
    public List<IgralecDto> seznam() {
        List<Igralec> igralci = igralecRepozitorij.najdiAktivne();
        List<Long> idji = igralci.stream().map(Igralec::getId).toList();
        List<RatingStanje> ratingi = ratingStanjeRepozitorij
                .findByIgralecIdInAndSistem(idji, RatingStanje.SISTEM_KLUBSKI_ELO);
        return igralci.stream()
                .map(igralec -> dto(igralec, najdiStanje(ratingi, igralec.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public IgralecDto najdi(Long id) {
        Igralec igralec = najdiIgralca(id);
        RatingStanje stanje = ratingStanjeRepozitorij
                .findByIgralecIdAndSistem(id, RatingStanje.SISTEM_KLUBSKI_ELO)
                .orElse(null);
        return dto(igralec, stanje);
    }

    /* Postavitveni (zacetni) rating: dovoljen le za igralca brez odigranih tekem
       (RatingStoritev preveri to pravilo in zabelezi spremembo v dnevnik). */
    @Transactional
    public IgralecDto nastaviZacetniRating(Long id, int vrednost) {
        Igralec igralec = najdiIgralca(id);
        ratingStoritev.nastaviZacetniRating(igralec, vrednost);
        RatingStanje stanje = ratingStanjeRepozitorij
                .findByIgralecIdAndSistem(id, RatingStanje.SISTEM_KLUBSKI_ELO)
                .orElse(null);
        return dto(igralec, stanje);
    }

    @Transactional
    public IgralecDto ustvari(IgralecVnos vnos) {
        Igralec igralec = new Igralec();
        prepisi(igralec, vnos);
        return IgralecDto.iz(igralecRepozitorij.save(igralec), null);
    }

    @Transactional
    public IgralecDto posodobi(Long id, IgralecVnos vnos) {
        Igralec igralec = najdiIgralca(id);
        prepisi(igralec, vnos);
        RatingStanje stanje = ratingStanjeRepozitorij
                .findByIgralecIdAndSistem(id, RatingStanje.SISTEM_KLUBSKI_ELO)
                .orElse(null);
        return dto(igralec, stanje);
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
