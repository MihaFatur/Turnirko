/* Sistem tekmovanja dogodka.

   IZLOCILNI         - klasicna izlocilna mreza,
   KROZNI            - vsak z vsakim v enem samem krogu,
   SKUPINE_IZLOCILNI - skupine (nakljucen zreb), nato izlocilni del iz
                       najboljsih dveh vsake skupine,
   SKUPINE           - format TOP: izmed prijavljenih se izbere najboljsih N,
                       ki se jih ZAPOREDNO po jakosti razdeli v skupine
                       (A = najmocnejsa). Izlocilnega dela ni - tekmovanje se
                       konca po zadnjem kolu skupin in vsaka skupina ima svojo
                       lestvico,
   SKUPINE_ZA_MESTA  - predtekmovalne skupine, nato FINALNE SKUPINE ZA MESTA:
                       prvo- in drugouvrsceni vseh skupin igrajo za zgornja
                       mesta, tretje- in cetrtouvrsceni za naslednja, v finalno
                       skupino pa se prenese izid medsebojnega dvoboja iz
                       predtekmovanja (PST 14. clen, ekipna DP mladih),
   SV_REGIJA         - igralci so po jakosti razdeljeni v NIVOJE (privzeto po
                       16), v vsakem nivoju igrajo skupine vsak z vsakim;
                       prvo- in drugouvrsceni gredo v glavni zreb nivoja,
                       tretje- in cetrtouvrsceni v tolazilni zreb. Zreb se igra
                       za VSA mesta: porazenca se srecata med seboj za slabsa
                       mesta, do zadnjega para (glej SvRegijaStoritev). */
package si.turnirko.modeli;

public enum SistemTekmovanja {
    IZLOCILNI,
    SKUPINE_IZLOCILNI,
    KROZNI,
    SKUPINE,
    SKUPINE_ZA_MESTA,
    SV_REGIJA;

    /* Ali sistem sploh igra skupinski del (in torej potrebuje skupine). */
    public boolean imaSkupine() {
        return this == SKUPINE || this == SKUPINE_IZLOCILNI || this == SKUPINE_ZA_MESTA
                || this == SV_REGIJA;
    }
}
