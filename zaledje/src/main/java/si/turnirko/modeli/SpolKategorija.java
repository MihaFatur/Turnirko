/* Spolna kategorija tekmovanja (za koga je razpisano).

   MOSKI, ZENSKE - nastopajo samo moski oz. samo zenske,
   MESANO        - STROGO mesan par: en moski in ena zenska. Ker je to
                   lastnost para, je izbira smiselna samo pri disciplini
                   DVOJICE in jo tam omeji tudi CHECK v shemi (V14).
                   Pri ligi ima ista vrednost svoj, starejsi pomen -
                   liga, v kateri igrajo oboji.
   KDORKOLI      - nastopi lahko vsak, ne glede na spol. To je do V14
                   pomenil MESANO pri dogodkih, zato so se ob migraciji
                   obstojeci dogodki prepisali vanj. */
package si.turnirko.modeli;

public enum SpolKategorija {
    MOSKI,
    ZENSKE,
    MESANO,
    KDORKOLI
}
