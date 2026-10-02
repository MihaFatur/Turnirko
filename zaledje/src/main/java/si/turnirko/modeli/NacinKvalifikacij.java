/* Kako se igrajo kvalifikacije med ligama (V41) - izbere organizator, ko jih
   ustvari, ker se pravila razlikujejo od lige do lige (Savinja liga: ena
   tekma; SNTL: serija na dve zmagi).

   PO PARIH (ena tekma, serija): ekipe se zvezejo krizno - najbolje uvrscena
   ekipa visje lige z najslabse uvrsceno nizje (8. A - 4. B, 9. A - 3. B).
   Zmagovalec para igra v visji ligi. Tece po kodi koncnice z enim krogom.

   VSAK Z VSAKIM: vse ekipe obeh lig igrajo malo ligo (eno- ali dvokrozno;
   dvokrozno pri enem paru pomeni "doma in v gosteh"). Zgornja mesta - toliko,
   kolikor jih visja liga odda - igrajo v visji ligi. Tece po kodi rednega
   dela (zreb, lestvica). */
package si.turnirko.modeli;

public enum NacinKvalifikacij {
    ENA_TEKMA(1, false),
    SERIJA_DO_2(2, false),
    SERIJA_DO_3(3, false),
    VSAK_Z_VSAKIM(null, false),
    VSAK_Z_VSAKIM_DVOKROZNO(null, true);

    private final Integer zmagVSeriji;
    private final boolean dvokrozno;

    NacinKvalifikacij(Integer zmagVSeriji, boolean dvokrozno) {
        this.zmagVSeriji = zmagVSeriji;
        this.dvokrozno = dvokrozno;
    }

    /* Koliko zmag potrebuje ekipa v paru; null pri mali ligi. */
    public Integer getZmagVSeriji() { return zmagVSeriji; }

    public boolean isDvokrozno() { return dvokrozno; }

    public boolean poParih() { return zmagVSeriji != null; }
}
