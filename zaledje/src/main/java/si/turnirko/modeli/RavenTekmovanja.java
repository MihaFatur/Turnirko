/* Raven tekmovanja in z njo teza njegovih tekem v Turnirko ratingu.

   Zakaj sploh: prej je bila to zastavica "steje_v_elo" - tekma je stela ali pa
   ne. To je premalo. Zmaga na clanskem turnirju NTZS in zmaga na rekreativnem
   turnirju v isti dvorani nista enako vredni informaciji: na uradnem
   tekmovanju igrajo v polni postavi in za rezultat, na rekreativnem se igra
   tudi za druzbo. Ce oboje steje enako, dober rekreativec prehiti nekoliko
   slabsega igralca, ki hodi na clanske turnirje - to pa ni res.

   Teza mnozi K (in s tem spremembo ratinga) obeh igralcev enako. Ker je enaka
   za oba, ostane vsota sprememb pri enakem K se vedno 0.

   Teza NE vpliva na stevec odigranih tekem: rekreativna tekma steje kot ena
   tekma, le premakne rating za polovico. Tako je razlaga igralcu preprosta
   ("30 tekem" res pomeni 30 tekem), obenem pa nihce ne more pridobiti ali
   izgubiti veliko na sibkejsih tekmovanjih. */
package si.turnirko.modeli;

public enum RavenTekmovanja {

    /* Tekmovanja NTZS: drzavna prvenstva, SNTL lige, pokali, TOP-8. */
    URADNO(1.00, "Uradno tekmovanje (NTZS)"),

    /* Mocnejsa klubska tekmovanja, kjer igrajo tudi rangirani igralci
       (Savinja liga, Savinja tour in podobno). */
    KLUBSKO(0.75, "Klubsko tekmovanje"),

    /* Rekreativna tekmovanja in lige. */
    REKREATIVNO(0.50, "Rekreativno tekmovanje"),

    /* Prijateljski turnirji, treningi, sale - v rating ne stejejo. */
    NE_STEJE(0.00, "Ne steje v rating");

    private final double teza;
    private final String opis;

    RavenTekmovanja(double teza, String opis) {
        this.teza = teza;
        this.opis = opis;
    }

    public double getTeza() { return teza; }

    public String getOpis() { return opis; }

    /* Ali se tekme tega tekmovanja sploh obracunajo. */
    public boolean steje() {
        return teza > 0.0;
    }
}
