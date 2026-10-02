/* Ponovni uvoz stare strani NA MESTU: turnir oz. liga ostaneta (id-ji so v
   naslovih strani, liga je lahko med spremljanimi), vse pod njima pa gre in se
   zapise znova iz popravljene pretvorbe.

   Stara stran za razliko od Stupe nima povezav za dogodke, ekipe in srecanja
   (vir jih ne poimenuje stabilno), zato se pri turnirju pobrisejo tudi
   dogodki, pri ligi pa ekipe in srecanja - zapisani so znova.

   Brise se z neposrednimi stavki SQL v vrstnem redu tujih kljucev (isto kot
   CiscenjeUvoza pri Stupi). Klicatelj mora sejo pred tem izprazniti (flush) in
   po tem pocistiti (clear). Obracuni ratinga izbrisanih tekem gredo z njimi;
   rating se po popravku preracuna od zacetka. */
package si.turnirko.uvoz.stara;

import jakarta.persistence.EntityManager;

public final class CiscenjeStare {

    private CiscenjeStare() {}

    /* Vsi dogodki turnirja z vsebino (turnir ostane). */
    public static void turnir(EntityManager em, long idTurnir) {
        String dogodki = "SELECT id FROM dogodek WHERE id_turnir = ?1";
        String tekme = "SELECT id FROM tekma WHERE id_dogodek IN (" + dogodki + ")";
        String srecanja = "SELECT id FROM srecanje WHERE id_tekma IN (" + tekme + ")";
        String tekmeSrecanj = "SELECT id FROM tekma_srecanja WHERE id_srecanje IN (" + srecanja + ")";
        izvedi(em, idTurnir,
                "DELETE FROM rating_zgodovina WHERE id_tekma IN (" + tekme + ")",
                "DELETE FROM rating_zgodovina WHERE id_tekma_srecanja IN (" + tekmeSrecanj + ")",
                "DELETE FROM niz_srecanja WHERE id_tekma_srecanja IN (" + tekmeSrecanj + ")",
                "DELETE FROM tekma_srecanja WHERE id_srecanje IN (" + srecanja + ")",
                "DELETE FROM postava_srecanja WHERE id_srecanje IN (" + srecanja + ")",
                "DELETE FROM srecanje WHERE id_tekma IN (" + tekme + ")",
                "DELETE FROM niz WHERE id_tekma IN (" + tekme + ")",
                "UPDATE tekma SET id_izvor_tekma_1 = NULL, id_izvor_tekma_2 = NULL, id_prenesena = NULL"
                        + " WHERE id_dogodek IN (" + dogodki + ")",
                "DELETE FROM tekma WHERE id_dogodek IN (" + dogodki + ")",
                "DELETE FROM prijava WHERE id_dogodek IN (" + dogodki + ")",
                "DELETE FROM kader_ekipe WHERE id_ekipa IN (SELECT id FROM ekipa WHERE id_dogodek IN (" + dogodki + "))",
                "DELETE FROM ekipa WHERE id_dogodek IN (" + dogodki + ")",
                "DELETE FROM skupina WHERE id_dogodek IN (" + dogodki + ")",
                "DELETE FROM dogodek WHERE id_turnir = ?1");
    }

    /* Ekipe, kadri in srecanja lige z vsemi tekmami (liga ostane). */
    public static void liga(EntityManager em, long idLiga) {
        String srecanja = "SELECT id FROM srecanje WHERE id_liga = ?1";
        String tekmeSrecanj = "SELECT id FROM tekma_srecanja WHERE id_srecanje IN (" + srecanja + ")";
        izvedi(em, idLiga,
                "DELETE FROM rating_zgodovina WHERE id_tekma_srecanja IN (" + tekmeSrecanj + ")",
                "DELETE FROM niz_srecanja WHERE id_tekma_srecanja IN (" + tekmeSrecanj + ")",
                "DELETE FROM tekma_srecanja WHERE id_srecanje IN (" + srecanja + ")",
                "DELETE FROM postava_srecanja WHERE id_srecanje IN (" + srecanja + ")",
                "UPDATE srecanje SET id_serija = NULL, tekma_v_seriji = NULL WHERE id_liga = ?1",
                "DELETE FROM serija_koncnice WHERE id_liga = ?1",
                "DELETE FROM srecanje WHERE id_liga = ?1",
                "DELETE FROM kader_ekipe WHERE id_ekipa IN (SELECT id FROM ekipa WHERE id_liga = ?1)",
                "DELETE FROM ekipa WHERE id_liga = ?1");
    }

    private static void izvedi(EntityManager em, long id, String... stavki) {
        for (String stavek : stavki) {
            em.createNativeQuery(stavek).setParameter(1, id).executeUpdate();
        }
    }
}
