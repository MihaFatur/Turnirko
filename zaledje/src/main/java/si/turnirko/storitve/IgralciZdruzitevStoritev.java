/* Zdruzitev dveh zapisov iste osebe (cistka duplikatov na strani Igralci).

   Dvojnik razbije zgodovino in rating na dva dela, zato se vse, kar kaze na
   ODVECNEGA igralca, preusmeri na OBDRZANEGA, odvecni pa se ARHIVIRA (ne
   izbrise: zapis ostane, da je jasno, kaj se je zgodilo). Isto pravilo kot
   enkratna zdruzitev v PopravekZgodovineUkaz, le da jo sprozi clovek, ki je
   par pregledal, in jo zato varuje strozje.

   Zdruzitev se NE izvede, kadar bi preusmeritev trcila - oba v istem dogodku,
   v isti postavi, v isti tekmi, oba z racunom, ali bi se pomesala rocni
   rating in obracun: izjema pove, zakaj. To ni napaka zapisa, ampak znak, da
   gre morda za dve osebi (ali za odlocitev, ki je ne sme sprejeti stroj).

   Rating je izpeljanka iz izidov. Odvecnemu igralcu se obracuni tekem zato
   POBRISEJO in rating obdrzanega se sestavi znova od dneva njegove prve tekme
   (PreracunRatingaStoritev) - brez tega bi imela tekma obracun pod napacnim
   igralcem. Rocna postavitev ali zunanja uvrstitev je odlocitev cloveka in se
   prenese, a samo, ce obdrzani ratinga se nima: rocne odlocitve ni mogoce
   pomesati z obracunom, ker preracun postavitve ne bere vec kot izhodisce. */
package si.turnirko.storitve;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import si.turnirko.dto.ZdruzitevIgralcevDto;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.modeli.Igralec;
import si.turnirko.repozitoriji.IgralecRepozitorij;

@Service
public class IgralciZdruzitevStoritev {

    /* Zapisi dnevnika, ki jih je naredil clovek in ne obracun: postavitev in
       zunanja uvrstitev (brez tekme, razen odbitka za neaktivnost). Isti
       pogoj kot v PopravekZgodovineUkaz in RatingZgodovinaRepozitorij. */
    private static final String ROCNI_ZAPISI =
            "id_tekma IS NULL AND id_tekma_srecanja IS NULL AND (razlog IS NULL OR razlog <> 'NEAKTIVNOST')";

    private static final String TEKMENI_ZAPISI = "(id_tekma IS NOT NULL OR id_tekma_srecanja IS NOT NULL)";

    private final IgralecRepozitorij igralecRepozitorij;
    private final IgralciStoritev igralciStoritev;
    private final PreracunRatingaStoritev preracunRatingaStoritev;
    private final TransactionTemplate transakcija;

    @PersistenceContext
    private EntityManager em;

    public IgralciZdruzitevStoritev(IgralecRepozitorij igralecRepozitorij,
                                    IgralciStoritev igralciStoritev,
                                    PreracunRatingaStoritev preracunRatingaStoritev,
                                    PlatformTransactionManager upravitelj) {
        this.igralecRepozitorij = igralecRepozitorij;
        this.igralciStoritev = igralciStoritev;
        this.preracunRatingaStoritev = preracunRatingaStoritev;
        this.transakcija = new TransactionTemplate(upravitelj);
    }

    /* Obdrzi `idObdrzan`, `idOdvec` se arhivira. Preusmeritev je ena
       transakcija; preracun ratinga (ce je potreben) tece po njej, ker ima
       lastne transakcije po paketih tekem. */
    public ZdruzitevIgralcevDto zdruzi(Long idObdrzan, Long idOdvec) {
        if (idObdrzan.equals(idOdvec)) {
            throw new NeveljavenVnosIzjema("Igralca ni mogoce zdruziti s samim seboj.");
        }
        LocalDate preracunajOd = transakcija.execute(stanje -> preusmeri(idObdrzan, idOdvec));
        if (preracunajOd != null) {
            preracunRatingaStoritev.preracunajOd(preracunajOd);
        }
        return new ZdruzitevIgralcevDto(igralciStoritev.najdiPodrobno(idObdrzan), preracunajOd != null);
    }

    /* Vrne dan, od katerega je treba rating sestaviti znova, ali null. */
    private LocalDate preusmeri(Long obdrzan, Long odvec) {
        najdi(obdrzan);
        najdi(odvec);
        preveriTrke(obdrzan, odvec);

        // isti kader: odvecnikova vrstica odpade (obdrzani je ze v ekipi)
        posodobi("DELETE FROM kader_ekipe WHERE id_igralec = ?1 AND id_ekipa IN"
                + " (SELECT id_ekipa FROM kader_ekipe WHERE id_igralec = ?2)", odvec, obdrzan);
        posodobi("UPDATE kader_ekipe SET id_igralec = ?2 WHERE id_igralec = ?1", odvec, obdrzan);
        posodobi("UPDATE prijava SET id_igralec = ?2 WHERE id_igralec = ?1", odvec, obdrzan);
        posodobi("UPDATE prijava SET id_igralec_2 = ?2 WHERE id_igralec_2 = ?1", odvec, obdrzan);
        posodobi("UPDATE postava_srecanja SET id_igralec = ?2 WHERE id_igralec = ?1", odvec, obdrzan);
        for (String stolpec : new String[] {"id_igralec_domaci", "id_igralec_domaci2",
                "id_igralec_gost", "id_igralec_gost2"}) {
            posodobi("UPDATE tekma_srecanja SET " + stolpec + " = ?2 WHERE " + stolpec + " = ?1",
                    odvec, obdrzan);
        }
        posodobi("UPDATE uporabnik SET id_igralec = ?2 WHERE id_igralec = ?1", odvec, obdrzan);
        // uvoz po povezavi najde obdrzanega in odvecnega ne ustvari znova
        posodobi("UPDATE zunanja_povezava SET id_lokalni = ?2 WHERE vrsta = 'IGRALEC' AND id_lokalni = ?1",
                odvec, obdrzan);
        // odlocitve »ni duplikat« o odvecnem nimajo vec pomena
        posodobi("DELETE FROM igralec_ni_duplikat WHERE id_igralec_a = ?1 OR id_igralec_b = ?1", odvec);

        LocalDate preracunajOd = preusmeriRating(odvec, obdrzan);

        em.clear();
        // ponovno nalozena zapisa: unikatna polja odvecnega se najprej sprostijo
        Igralec ohranjen = najdi(obdrzan);
        Igralec odvecen = najdi(odvec);
        String email = odvecen.getEmail();
        String telefon = odvecen.getTelefonskaSt();
        String licenca = odvecen.getNtzsLicenca();
        odvecen.setEmail(null);
        odvecen.setTelefonskaSt(null);
        odvecen.setNtzsLicenca(null);
        odvecen.setArhiviran(true);
        igralecRepozitorij.saveAndFlush(odvecen);

        // obdrzanemu se izpolnijo samo PRAZNA polja: kar je vpisal clovek, ostane
        if (ohranjen.getEmail() == null) ohranjen.setEmail(email);
        if (ohranjen.getTelefonskaSt() == null) ohranjen.setTelefonskaSt(telefon);
        if (ohranjen.getNtzsLicenca() == null) ohranjen.setNtzsLicenca(licenca);
        if (ohranjen.getIgralnaRoka() == null) ohranjen.setIgralnaRoka(odvecen.getIgralnaRoka());
        if (ohranjen.getNaslov() == null) ohranjen.setNaslov(odvecen.getNaslov());
        if (ohranjen.getKraj() == null) ohranjen.setKraj(odvecen.getKraj());
        if (ohranjen.getKlub() == null) ohranjen.setKlub(odvecen.getKlub());
        if (DvojnikiIgralcevStoritev.NEZNAN_DATUM.equals(ohranjen.getDatumRojstva())
                && !DvojnikiIgralcevStoritev.NEZNAN_DATUM.equals(odvecen.getDatumRojstva())) {
            ohranjen.setDatumRojstva(odvecen.getDatumRojstva());
        }
        igralecRepozitorij.saveAndFlush(ohranjen);
        return preracunajOd;
    }

    /* Rating odvecnega: obracuni tekem se zavrzejo (preracun jih sestavi znova
       pod obdrzanim), rocni zapisi se prenesejo. Vrne dan preracuna ali null. */
    private LocalDate preusmeriRating(Long odvec, Long obdrzan) {
        LocalDate prvaTekma = prviDan("SELECT min(velja_ob) FROM rating_zgodovina WHERE id_igralec = ?1"
                + " AND " + TEKMENI_ZAPISI, odvec);
        boolean imaRocne = stej("SELECT count(*) FROM rating_zgodovina WHERE id_igralec = ?1 AND "
                + ROCNI_ZAPISI, odvec) > 0;

        posodobi("DELETE FROM rating_zgodovina WHERE id_igralec = ?1 AND NOT (" + ROCNI_ZAPISI + ")", odvec);
        if (imaRocne) {
            // obdrzani ob tem nima nobenega zapisa (preveriTrke): rocni zapisi so zdaj njegovi
            posodobi("UPDATE rating_zgodovina SET id_igralec = ?2 WHERE id_igralec = ?1", odvec, obdrzan);
        }
        if (imaRocne && prvaTekma == null) {
            // stanje (vrednost, postavljen, zunanja uvrstitev) se prenese, kot je
            posodobi("DELETE FROM rating_stanje WHERE id_igralec = ?1", obdrzan);
            posodobi("UPDATE rating_stanje SET id_igralec = ?2 WHERE id_igralec = ?1", odvec, obdrzan);
        } else {
            posodobi("DELETE FROM rating_stanje WHERE id_igralec = ?1", odvec);
        }
        return prvaTekma;
    }

    private void preveriTrke(Long obdrzan, Long odvec) {
        Map<String, String> trki = new LinkedHashMap<>();
        trki.put("Igralca sta prijavljena v istem dogodku.",
                "SELECT count(*) FROM prijava p JOIN prijava q ON q.id_dogodek = p.id_dogodek"
                        + " WHERE ?1 IN (p.id_igralec, p.id_igralec_2) AND ?2 IN (q.id_igralec, q.id_igralec_2)");
        trki.put("Igralca sta v isti postavi srecanja.",
                "SELECT count(*) FROM postava_srecanja p JOIN postava_srecanja q ON q.id_srecanje = p.id_srecanje"
                        + " WHERE p.id_igralec = ?1 AND q.id_igralec = ?2");
        trki.put("Igralca sta igrala v isti tekmi srecanja.",
                "SELECT count(*) FROM tekma_srecanja WHERE ?1 IN (id_igralec_domaci, id_igralec_domaci2,"
                        + " id_igralec_gost, id_igralec_gost2) AND ?2 IN (id_igralec_domaci, id_igralec_domaci2,"
                        + " id_igralec_gost, id_igralec_gost2)");
        trki.put("Oba igralca imata uporabniski racun.",
                "SELECT count(*) FROM uporabnik u JOIN uporabnik v ON 1 = 1"
                        + " WHERE u.id_igralec = ?1 AND v.id_igralec = ?2");
        for (Map.Entry<String, String> trk : trki.entrySet()) {
            if (stej(trk.getValue(), obdrzan, odvec) > 0) {
                throw new DomenskaIzjema(trk.getKey() + " To ni videti kot dvojnik, zato zdruzitev ni mogoca.");
            }
        }

        boolean rocniObdrzan = stej("SELECT count(*) FROM rating_zgodovina WHERE id_igralec = ?1 AND "
                + ROCNI_ZAPISI, obdrzan) > 0;
        boolean rocniOdvec = stej("SELECT count(*) FROM rating_zgodovina WHERE id_igralec = ?1 AND "
                + ROCNI_ZAPISI, odvec) > 0;
        boolean zapisiObdrzan = stej("SELECT count(*) FROM rating_zgodovina WHERE id_igralec = ?1", obdrzan) > 0;
        boolean zapisiOdvec = stej("SELECT count(*) FROM rating_zgodovina WHERE id_igralec = ?1", odvec) > 0;
        if ((rocniObdrzan && zapisiOdvec) || (rocniOdvec && zapisiObdrzan)) {
            throw new DomenskaIzjema("Eden od igralcev ima rocno postavljen rating, drugi pa ze zapise ratinga: "
                    + "rocne odlocitve ni mogoce pomesati z obracunom. Obdrzi igralca z rocno postavljenim "
                    + "ratingom in drugemu prej odstrani rating, ali enega od njiju popravi na roke.");
        }
    }

    private Igralec najdi(Long id) {
        return igralecRepozitorij.najdiZVsem(id)
                .orElseThrow(() -> new NiNajdenoIzjema("Igralec z id " + id + " ne obstaja."));
    }

    private int posodobi(String sql, Object... parametri) {
        return poizvedba(sql, parametri).executeUpdate();
    }

    private long stej(String sql, Object... parametri) {
        return ((Number) poizvedba(sql, parametri).getSingleResult()).longValue();
    }

    /* Dan iz stolpca z datumom in uro (»2012-10-06T00:00« ali samo datum);
       null, kadar vrstic ni. */
    private LocalDate prviDan(String sql, Object... parametri) {
        Object vrednost = poizvedba(sql, parametri).getSingleResult();
        if (vrednost == null) {
            return null;
        }
        return LocalDate.parse(vrednost.toString().substring(0, 10));
    }

    private Query poizvedba(String sql, Object... parametri) {
        Query poizvedba = em.createNativeQuery(sql);
        for (int i = 0; i < parametri.length; i++) {
            poizvedba.setParameter(i + 1, parametri[i]);
        }
        return poizvedba;
    }
}
