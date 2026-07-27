/* Sistem tekmovanja dogodka.

   IZLOCILNI         - klasicna izlocilna mreza,
   KROZNI            - vsak z vsakim v enem samem krogu,
   SKUPINE_IZLOCILNI - skupine (nakljucen zreb), nato izlocilni del iz
                       najboljsih dveh vsake skupine,
   SKUPINE           - format TOP: izmed prijavljenih se izbere najboljsih N,
                       ki se jih ZAPOREDNO po jakosti razdeli v skupine
                       (A = najmocnejsa). Izlocilnega dela ni - tekmovanje se
                       konca po zadnjem kolu skupin in vsaka skupina ima svojo
                       lestvico. */
package si.turnirko.modeli;

public enum SistemTekmovanja {
    IZLOCILNI,
    SKUPINE_IZLOCILNI,
    KROZNI,
    SKUPINE;

    /* Ali sistem sploh igra skupinski del (in torej potrebuje skupine). */
    public boolean imaSkupine() {
        return this == SKUPINE || this == SKUPINE_IZLOCILNI;
    }
}
