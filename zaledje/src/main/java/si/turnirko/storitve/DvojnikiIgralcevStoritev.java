/* Cistka duplikatov: pari igralcev, ki sta si podobna po imenu, in odlocitev
   »ni duplikat«.

   Pravila podobnosti so ISTA kot pri opozorilu ob vpisu (PodobnostImen) -
   cistka je isto vprasanje, le zastavljeno za ves register naenkrat. Zato tudi
   tu odlocajo imena in ne datum rojstva; datum je samo podatek ob paru, da
   cloveku olajsa odlocitev (enak datum = skoraj gotovo ista oseba).

   Par, ki ga je administrator zavrnil (»ni duplikat«), se ne pokaze vec.
   Arhivirani igralci niso del cistke: zdruzitev drugi zapis arhivira, zato bi
   ga sicer par kazal v nedogled. */
package si.turnirko.storitve;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.DvojnikIgralcaDto;
import si.turnirko.dto.IgralecDto;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.PrimerjavaDatuma;
import si.turnirko.modeli.RatingStanje;
import si.turnirko.modeli.UjemanjeImena;
import si.turnirko.pomozno.SlovenskaAbeceda;
import si.turnirko.repozitoriji.IgralecRepozitorij;
import si.turnirko.repozitoriji.RatingStanjeRepozitorij;

@Service
public class DvojnikiIgralcevStoritev {

    /* Datum rojstva, ki ga uvoz zapise, kadar ni znan (odlocitev lastnika,
       1. 10. 2026). Dva igralca z »enakim« 1. 1. 1900 sta enaka samo po
       neznanju, zato to ni dokaz za isto osebo. */
    static final LocalDate NEZNAN_DATUM = LocalDate.of(1900, 1, 1);

    private final IgralecRepozitorij igralecRepozitorij;
    private final RatingStanjeRepozitorij ratingStanjeRepozitorij;

    @PersistenceContext
    private EntityManager em;

    public DvojnikiIgralcevStoritev(IgralecRepozitorij igralecRepozitorij,
                                    RatingStanjeRepozitorij ratingStanjeRepozitorij) {
        this.igralecRepozitorij = igralecRepozitorij;
        this.ratingStanjeRepozitorij = ratingStanjeRepozitorij;
    }

    /* Vsi nerazreseni pari, najverjetnejsi najprej (isto ime in enak datum). */
    @Transactional(readOnly = true)
    public List<DvojnikIgralcaDto> pari() {
        List<Igralec> igralci = igralecRepozitorij.najdiAktivne();
        Set<String> zavrnjeni = zavrnjeniPari();

        List<PodobnostImen.Ime> imena = new ArrayList<>(igralci.size());
        for (Igralec igralec : igralci) {
            imena.add(PodobnostImen.Ime.iz(igralec.getIme(), igralec.getPriimek()));
        }

        List<Par> pari = new ArrayList<>();
        for (int i = 0; i < igralci.size(); i++) {
            for (int j = i + 1; j < igralci.size(); j++) {
                Igralec a = igralci.get(i);
                Igralec b = igralci.get(j);
                UjemanjeImena ujemanje = PodobnostImen.ujemanje(imena.get(i), a.getSpol(),
                        imena.get(j), b.getSpol());
                if (ujemanje != null && !zavrnjeni.contains(kljuc(a.getId(), b.getId()))) {
                    pari.add(new Par(a, b, ujemanje, primerjajDatum(a, b)));
                }
            }
        }

        List<Long> idji = new ArrayList<>();
        for (Par par : pari) {
            idji.add(par.a().getId());
            idji.add(par.b().getId());
        }
        List<RatingStanje> ratingi = idji.isEmpty() ? List.of()
                : ratingStanjeRepozitorij.findByIgralecIdInAndSistem(idji, RatingStanje.SISTEM_TURNIRKO);

        return pari.stream()
                .map(par -> sestavi(par, ratingi))
                .sorted(Comparator
                        .comparingInt(DvojnikiIgralcevStoritev::moc)
                        .thenComparing(d -> d.levi().priimek(), SlovenskaAbeceda.RED)
                        .thenComparing(d -> d.levi().ime(), SlovenskaAbeceda.RED))
                .toList();
    }

    /* »Ni duplikat«: par se ne pokaze vec. Ponovna odlocitev je neskodna. */
    @Transactional
    public void niDuplikat(Long idA, Long idB) {
        if (idA.equals(idB)) {
            throw new NeveljavenVnosIzjema("Igralec ne more biti duplikat samega sebe.");
        }
        for (Long id : List.of(idA, idB)) {
            if (!igralecRepozitorij.existsById(id)) {
                throw new NiNajdenoIzjema("Igralec z id " + id + " ne obstaja.");
            }
        }
        em.createNativeQuery("INSERT OR IGNORE INTO igralec_ni_duplikat "
                        + "(id_igralec_a, id_igralec_b) VALUES (?1, ?2)")
                .setParameter(1, Math.min(idA, idB))
                .setParameter(2, Math.max(idA, idB))
                .executeUpdate();
    }

    private Set<String> zavrnjeniPari() {
        Set<String> zavrnjeni = new HashSet<>();
        for (Object vrstica : em.createNativeQuery(
                "SELECT id_igralec_a, id_igralec_b FROM igralec_ni_duplikat").getResultList()) {
            Object[] stolpci = (Object[]) vrstica;
            zavrnjeni.add(kljuc(((Number) stolpci[0]).longValue(), ((Number) stolpci[1]).longValue()));
        }
        return zavrnjeni;
    }

    private static String kljuc(long a, long b) {
        return Math.min(a, b) + "-" + Math.max(a, b);
    }

    /* Neznan datum (1. 1. 1900) pri enem ali obeh ni »enak« ali »podoben«:
       o datumu ne vemo nicesar. */
    private static PrimerjavaDatuma primerjajDatum(Igralec a, Igralec b) {
        if (NEZNAN_DATUM.equals(a.getDatumRojstva()) || NEZNAN_DATUM.equals(b.getDatumRojstva())) {
            return PrimerjavaDatuma.DRUG;
        }
        return PodobnostImen.primerjajDatum(a.getDatumRojstva(), b.getDatumRojstva());
    }

    private static DvojnikIgralcaDto sestavi(Par par, List<RatingStanje> ratingi) {
        IgralecDto a = dto(par.a(), ratingi);
        IgralecDto b = dto(par.b(), ratingi);
        // levi je tisti z vec tekmami, pri enakem z manjsim id-jem
        boolean obrni = b.steviloTekem() > a.steviloTekem();
        IgralecDto levi = obrni ? b : a;
        IgralecDto desni = obrni ? a : b;
        return new DvojnikIgralcaDto(kljuc(a.id(), b.id()), levi, desni, par.ujemanje(), par.datum());
    }

    private static IgralecDto dto(Igralec igralec, List<RatingStanje> ratingi) {
        RatingStanje stanje = ratingi.stream()
                .filter(r -> r.getIgralec().getId().equals(igralec.getId()))
                .findFirst()
                .orElse(null);
        return IgralecDto.iz(igralec,
                stanje != null ? stanje.getVrednost() : null,
                stanje != null ? stanje.getStTekem() : 0);
    }

    /* Manj = verjetnejsi par: stopnja imena in stopnja datuma, kot pri
       IgralciStoritev.Zadetek.moc (enak datum steje najvec, drug najmanj). */
    private static int moc(DvojnikIgralcaDto dvojnik) {
        int zaDatum = switch (dvojnik.datum()) {
            case ENAK -> 0;
            case PODOBEN -> 1;
            case DRUG -> 3;
        };
        return dvojnik.ujemanje().ordinal() + zaDatum;
    }

    private record Par(Igralec a, Igralec b, UjemanjeImena ujemanje, PrimerjavaDatuma datum) {}
}
