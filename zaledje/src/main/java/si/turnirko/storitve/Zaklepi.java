/* Zaklepi med socasnimi zahtevami (prenova baze, faza 2; docs/PRENOVA-BAZE.md,
   razdelek 5).

   SQLite je vse zapise izvajal enega za drugim, zato dve zahtevi, ki sta
   prebrali isto stanje in nato pisali, nista mogli obe uspeti - druga je
   dobila napako 500 in pravilo je ostalo celo. PostgreSQL (READ COMMITTED) ju
   spusti obe: pravilo, ki ga varuje storitev po vzorcu "preberi -> preveri ->
   zapisi" (najvec uporab kode, kvota paketa, en obracun na tekmo), bi bilo
   prekrseno brez napake. Zato taka zahteva najprej vzame svetovalni zaklep
   (pg_advisory_xact_lock), ki ga baza sama sprosti ob koncu transakcije.

   Kljuc je par (vrsta, id): vrsta je prostor kljucev, id pa zapis, na katerega
   se pravilo nanasa (turnir, liga, koda popusta, racun). Zaklep zadrzi samo
   zahteve, ki vzamejo ISTI kljuc - branje ga ne caka.

   Vrstni red jemanja je vedno: tekmovanje -> rating -> preracun. Tako se dve
   zahtevi nikoli ne cakata navzkriz (glej zakleniRating).

   RATING je ena casovna vrsta, zato je zaporedno pisanje njegov pravi pomen in
   ne ozko grlo (obracun tekme traja milisekunde). Preracun pa traja minute:
   med njim se rezultat shrani BREZ obracuna, preracun ga ob koncu doobracuna
   (PreracunRatingaStoritev.obracunajManjkajoce). */
package si.turnirko.storitve;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import jakarta.persistence.EntityManager;

@Component
public class Zaklepi {

    /* Prostor kljucev (prvi argument pg_advisory_xact_lock). Stevilk ne
       spreminjaj: dve razlicici aplikacije med posodobitvijo morata zaklepati
       isto. */
    public enum Vrsta {
        RATING(1),
        PRERACUN(2),
        TURNIR(3),
        LIGA(4),
        KODA_POPUSTA(5),
        KVOTA_ORGANIZATORJA(6),
        DOMACA_STRAN(7),
        POTRDITVENA_KODA(8),
        STRIPE(9);

        final int prostor;

        Vrsta(int prostor) {
            this.prostor = prostor;
        }
    }

    private final JdbcTemplate jdbc;
    private final EntityManager em;
    private final DataSource podatkovniVir;

    public Zaklepi(JdbcTemplate jdbc, EntityManager em, DataSource podatkovniVir) {
        this.jdbc = jdbc;
        this.em = em;
        this.podatkovniVir = podatkovniVir;
    }

    /* Zaklene kljuc do konca tekoce transakcije; caka, ce ga drzi kdo drug. */
    public void zakleni(Vrsta vrsta, long id) {
        zahtevajTransakcijo();
        jdbc.query("SELECT pg_advisory_xact_lock(?, ?)", rs -> null, vrsta.prostor, objekt(id));
    }

    /* --- tekmovanje: vnos rezultatov, postave, odstopi ---

       Vse spremembe enega tekmovanja tecejo ena za drugo. Kljuc se izpelje s
       skalarno poizvedbo, ki entitet NE nalozi: tekma, nalozena pred zaklepom,
       bi bila po njem lahko zastarela (sosednja tekma je medtem napredovala v
       isto naslednjo) in zapis bi padel na @Version. */

    public void zakleniTurnirPoTekmi(Long idTekme) {
        zakleniTurnir(prvi("SELECT d.turnir.id FROM Tekma t JOIN t.dogodek d WHERE t.id = :id", idTekme));
    }

    public void zakleniTurnirPoPrijavi(Long idPrijave) {
        zakleniTurnir(prvi("SELECT d.turnir.id FROM Prijava p JOIN p.dogodek d WHERE p.id = :id", idPrijave));
    }

    /* Ligasko srecanje zaklene ligo, srecanje ekipne tekme turnirja pa turnir
       (njegov izid postane izid tekme v mrezi). */
    public void zakleniTekmovanjePoSrecanju(Long idSrecanje) {
        zakleniTekmovanje(em.createQuery("""
                SELECT s.liga.id, d.turnir.id FROM Srecanje s
                LEFT JOIN s.tekma t LEFT JOIN t.dogodek d WHERE s.id = :id""", Object[].class)
                .setParameter("id", idSrecanje).getResultList());
    }

    public void zakleniTekmovanjePoTekmiSrecanja(Long idTekmaSrecanja) {
        zakleniTekmovanje(em.createQuery("""
                SELECT s.liga.id, d.turnir.id FROM TekmaSrecanja ts JOIN ts.srecanje s
                LEFT JOIN s.tekma t LEFT JOIN t.dogodek d WHERE ts.id = :id""", Object[].class)
                .setParameter("id", idTekmaSrecanja).getResultList());
    }

    private void zakleniTekmovanje(java.util.List<Object[]> vrstice) {
        if (vrstice.isEmpty()) {
            return; // zapisa ni - storitev bo vrnila 404
        }
        Object[] v = vrstice.get(0);
        if (v[0] != null) {
            zakleni(Vrsta.LIGA, ((Number) v[0]).longValue());
        } else if (v[1] != null) {
            zakleniTurnir(((Number) v[1]).longValue());
        }
    }

    private void zakleniTurnir(Long idTurnir) {
        if (idTurnir != null) {
            zakleni(Vrsta.TURNIR, idTurnir);
        }
    }

    private Long prvi(String jpql, Long id) {
        return em.createQuery(jpql, Long.class).setParameter("id", id)
                .getResultStream().findFirst().orElse(null);
    }

    /* --- rating ---

       Vsako pisanje ratinga (obracun tekme, postavitev, zunanja uvrstitev,
       odbitek za neaktivnost) najprej vzame RATING - pisci gredo eden za
       drugim, zato preverba "ali je tekma ze obracunana" in zapis ne moreta
       steci vzporedno. Nato POSKUSI deljeni zaklep PRERACUN: ce ga ne dobi,
       preracun tece (drzi ga izkljucno) in vrne false - klicalec rating
       pusti preracunu (glej RatingStoritev).

       Zakaj najprej RATING in sele nato PRERACUN: preracun znotraj transakcije
       vnosa (popravek, srecanje za nazaj) RATING ze drzi in PRERACUN dvigne na
       izkljucnega. Deljenega PRERACUN zato lahko drzi samo, kdor drzi tudi
       RATING - torej nihce drug - in dvig ne caka nikogar. */
    public boolean zakleniRating() {
        zakleni(Vrsta.RATING, 0);
        Boolean dobljen = jdbc.queryForObject("SELECT pg_try_advisory_xact_lock_shared(?, ?)",
                Boolean.class, Vrsta.PRERACUN.prostor, 0);
        return Boolean.TRUE.equals(dobljen);
    }

    /* Preracun znotraj transakcije klicalca: izkljucno do njenega konca. */
    public void zakleniPreracunVTransakciji() {
        zakleni(Vrsta.RATING, 0);
        zakleni(Vrsta.PRERACUN, 0);
    }

    /* Preracun brez transakcije klicalca (administrator, zdruzitev igralcev,
       uvoz): tece v paketih s svojimi transakcijami, zaklep pa mora drzati cez
       vse. Zato ga drzi lastna povezava na ravni SEJE, dokler se ne zapre.
       Caka brez omejitve (lock_timeout 0), ker vlogi aplikacije privzeto
       postavimo 5 s: preracun mora pocakati, da se koncajo vnosi, ki so se
       zaceli pred njim, in morebiten drug preracun. */
    public ZaklepSeje zakleniPreracunNaSeji() {
        try {
            Connection povezava = podatkovniVir.getConnection();
            try (Statement stavek = povezava.createStatement()) {
                povezava.setAutoCommit(true);
                stavek.execute("SET lock_timeout = 0");
                stavek.execute("SELECT pg_advisory_lock(" + Vrsta.PRERACUN.prostor + ", 0)");
            } catch (SQLException napaka) {
                povezava.close();
                throw napaka;
            }
            return new ZaklepSeje(povezava);
        } catch (SQLException napaka) {
            throw new IllegalStateException("Zaklepa preracuna ni bilo mogoce vzeti", napaka);
        }
    }

    /* Zaklep, ki ga drzi povezava; close() ga sprosti in povezavo vrne v bazen
       z izvornimi nastavitvami. */
    public static final class ZaklepSeje implements AutoCloseable {
        private final Connection povezava;

        private ZaklepSeje(Connection povezava) {
            this.povezava = povezava;
        }

        @Override
        public void close() {
            try (Statement stavek = povezava.createStatement()) {
                stavek.execute("SELECT pg_advisory_unlock_all()");
                stavek.execute("RESET lock_timeout");
            } catch (SQLException napaka) {
                // povezava je pokvarjena - baza zaklep sprosti ob njenem zaprtju
            } finally {
                try {
                    povezava.close();
                } catch (SQLException napaka) {
                    // nic vec ne moremo storiti
                }
            }
        }
    }

    /* Zaklep transakcije brez transakcije bi baza sprostila takoj po stavku -
       tak zaklep ne varuje nicesar in je napaka v kodi, ne v podatkih. */
    private static void zahtevajTransakcijo() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Zaklep transakcije brez transakcije ne varuje nicesar.");
        }
    }

    /* id kot drugi (int) argument; dva id-ja, ki se ujameta v spodnjih bitih,
       si delita zaklep - to pomeni le malo vec cakanja, nikoli napake. */
    private static int objekt(long id) {
        return (int) (id ^ (id >>> 32));
    }
}
