/* Ponovni uvoz: vsebina pod dogodkom oz. ligo se zapise znova iz vira.

   Dogodek, liga, ekipe lige in srecanja lige ostanejo (id-ji so v naslovih
   strani), vse pod njimi pa gre: prijave, skupine, tekme, nizi, postave,
   posamicne tekme srecanj, serije koncnice in kadri - skupaj z obracuni
   ratinga teh tekem. Rating se po uvozu preracuna od dneva tekmovanja, zato
   izbrisani obracuni ne pustijo sledi.

   Brise se z neposrednimi stavki SQL v vrstnem redu tujih kljucev, ker bi
   nalaganje entitet za brisanje pri velikem dogodku pomenilo tisoce
   poizvedb. Klicatelj mora sejo pred tem izprazniti (flush) in po tem
   pocistiti (clear) - entitete v seji bi sicer kazale na izbrisane vrstice. */
package si.turnirko.uvoz.stupa;

import jakarta.persistence.EntityManager;

final class CiscenjeUvoza {

    private CiscenjeUvoza() {}

    /* Tekme, prijave, skupine in ekipe dogodka (dogodek ostane). */
    static void vsebinaDogodka(EntityManager em, long idDogodek) {
        String tekme = "SELECT id FROM tekma WHERE id_dogodek = ?1";
        String srecanja = "SELECT id FROM srecanje WHERE id_tekma IN (" + tekme + ")";
        String tekmeSrecanj = "SELECT id FROM tekma_srecanja WHERE id_srecanje IN (" + srecanja + ")";
        izvedi(em, idDogodek,
                "DELETE FROM rating_zgodovina WHERE id_tekma IN (" + tekme + ")",
                "DELETE FROM rating_zgodovina WHERE id_tekma_srecanja IN (" + tekmeSrecanj + ")",
                "DELETE FROM niz_srecanja WHERE id_tekma_srecanja IN (" + tekmeSrecanj + ")",
                "DELETE FROM tekma_srecanja WHERE id_srecanje IN (" + srecanja + ")",
                "DELETE FROM postava_srecanja WHERE id_srecanje IN (" + srecanja + ")",
                "DELETE FROM srecanje WHERE id_tekma IN (" + tekme + ")",
                "DELETE FROM niz WHERE id_tekma IN (" + tekme + ")",
                "UPDATE tekma SET id_izvor_tekma_1 = NULL, id_izvor_tekma_2 = NULL, id_prenesena = NULL"
                        + " WHERE id_dogodek = ?1",
                "DELETE FROM tekma WHERE id_dogodek = ?1",
                "DELETE FROM prijava WHERE id_dogodek = ?1",
                "DELETE FROM kader_ekipe WHERE id_ekipa IN (SELECT id FROM ekipa WHERE id_dogodek = ?1)",
                "DELETE FROM ekipa WHERE id_dogodek = ?1",
                "DELETE FROM skupina WHERE id_dogodek = ?1");
    }

    /* Dogodek, ki ga vir ne pozna vec: vsebina, vrstica in povezava. */
    static void dogodek(EntityManager em, long idDogodek) {
        vsebinaDogodka(em, idDogodek);
        izvedi(em, idDogodek,
                "DELETE FROM zunanja_povezava WHERE vrsta = 'DOGODEK' AND id_lokalni = ?1",
                "DELETE FROM dogodek WHERE id = ?1");
    }

    /* Vsebina srecanj lige, serije koncnice in kadri (liga, ekipe in srecanja
       ostanejo). */
    static void vsebinaLige(EntityManager em, long idLiga) {
        String srecanja = "SELECT id FROM srecanje WHERE id_liga = ?1";
        String tekmeSrecanj = "SELECT id FROM tekma_srecanja WHERE id_srecanje IN (" + srecanja + ")";
        izvedi(em, idLiga,
                "DELETE FROM rating_zgodovina WHERE id_tekma_srecanja IN (" + tekmeSrecanj + ")",
                "DELETE FROM niz_srecanja WHERE id_tekma_srecanja IN (" + tekmeSrecanj + ")",
                "DELETE FROM tekma_srecanja WHERE id_srecanje IN (" + srecanja + ")",
                "DELETE FROM postava_srecanja WHERE id_srecanje IN (" + srecanja + ")",
                "UPDATE srecanje SET id_serija = NULL, tekma_v_seriji = NULL WHERE id_liga = ?1",
                "DELETE FROM serija_koncnice WHERE id_liga = ?1",
                "DELETE FROM kader_ekipe WHERE id_ekipa IN (SELECT id FROM ekipa WHERE id_liga = ?1)");
    }

    /* Srecanje lige, ki ga vir ne pozna vec (vsebina je ze izbrisana). */
    static void srecanjeLige(EntityManager em, long idSrecanje) {
        izvedi(em, idSrecanje,
                "DELETE FROM zunanja_povezava WHERE vrsta = 'SRECANJE' AND id_lokalni = ?1",
                "DELETE FROM srecanje WHERE id = ?1");
    }

    /* Ekipa lige, ki je vir ne pozna vec (brez srecanj in kadra). */
    static void ekipaLige(EntityManager em, long idEkipa) {
        izvedi(em, idEkipa,
                "DELETE FROM zunanja_povezava WHERE vrsta = 'EKIPA' AND id_lokalni = ?1",
                "DELETE FROM ekipa WHERE id = ?1");
    }

    private static void izvedi(EntityManager em, long id, String... stavki) {
        for (String stavek : stavki) {
            em.createNativeQuery(stavek).setParameter(1, id).executeUpdate();
        }
    }
}
