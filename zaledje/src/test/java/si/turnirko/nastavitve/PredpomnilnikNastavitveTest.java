/* Kateri stavek SQL steje za zapis in razveljavi predpomnilnik.

   Napaka v eno smer stane en odvecen izracun, v drugo zastarel odgovor - zato
   test drzi predvsem, da noben stavek, ki pise, ne obvelja za branje. */
package si.turnirko.nastavitve;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PredpomnilnikNastavitveTest {

    @Test
    void stavkiKiPisejoSoZapis() {
        assertTrue(PredpomnilnikNastavitve.jeZapis("insert into tekma (id) values (?)"));
        assertTrue(PredpomnilnikNastavitve.jeZapis("update rating_stanje set vrednost=? where id=?"));
        assertTrue(PredpomnilnikNastavitve.jeZapis("delete from rating_zgodovina where id_tekma=?"));
        assertTrue(PredpomnilnikNastavitve.jeZapis("  \n INSERT INTO igralec_ni_duplikat VALUES (?1, ?2) ON CONFLICT DO NOTHING"));
        assertTrue(PredpomnilnikNastavitve.jeZapis("/* komentar */ update liga set ime=? where id=?"));
        assertTrue(PredpomnilnikNastavitve.jeZapis(
                "with nove as (select 1) insert into prijava (id) select * from nove"));
        assertTrue(PredpomnilnikNastavitve.jeZapis(
                "WITH stare AS (DELETE FROM niz WHERE id_tekma = ? RETURNING id) SELECT count(*) FROM stare"));
        assertTrue(PredpomnilnikNastavitve.jeZapis("truncate table niz"));
        assertTrue(PredpomnilnikNastavitve.jeZapis("call obnovi()"));
    }

    @Test
    void branjeNiZapis() {
        assertFalse(PredpomnilnikNastavitve.jeZapis("select t1_0.id from tekma t1_0 where t1_0.id=?"));
        assertFalse(PredpomnilnikNastavitve.jeZapis("SELECT * FROM turnir WHERE id = ? FOR UPDATE"));
        assertFalse(PredpomnilnikNastavitve.jeZapis("select nextval('tekma_id_seq')"));
        assertFalse(PredpomnilnikNastavitve.jeZapis("select pg_advisory_xact_lock(?)"));
        assertFalse(PredpomnilnikNastavitve.jeZapis("/* lestvica */ select 1"));
        assertFalse(PredpomnilnikNastavitve.jeZapis(
                "with zadnje as (select max(id) from tekma) select * from zadnje"));
        // stolpec, ki se imenuje kot stavek, ni stavek
        assertFalse(PredpomnilnikNastavitve.jeZapis("select updated_ob, deleted from zapis"));
    }
}
