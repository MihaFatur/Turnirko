/* Format ekipnega srecanja: doloca stevilo igralcev na strani, ali so v
   srecanju dvojice in - kljucno - VRSTNI RED posamicnih tekem (postavitve).

   Domaca ekipa zaseda mesta A/B/C, gostujoca X/Y/Z. Dvojice uporabijo par,
   ki je v postavi oznacen (v_dvojici) - zato imata mesti dvojic prazni oznaki.

   SNTL: 3 igralci, 2 igrata tudi dvojice; najprej dvojice, nato trije krogi
   posamicnih tekem:
       dvojice
       1. krog:  A-X, B-Y, C-Z
       2. krog:  B-X, A-Z, C-Y
       3. krog:  B-Z, C-X, A-Y
   SNTL_PRVA: kot SNTL, a se odigra samo 6 posamicnih (vsak igralec dva od
       treh nasprotnikov) in srecanje se konca pri 4 zmagah:
       dvojice, B-X, A-Z, C-Y, B-Z, C-X, A-Y. Tako igra 1. SNTL moski.
   SNTL_DVOJICE_SEDMA in SNTL_PRVA_DVOJICE_CETRTA: ista razporeda kot SNTL in
       SNTL_PRVA, le da dvojice ne odprejo srecanja, ampak pridejo na vrsto po
       prvem sklopu posamicnih. Tako sta SNTL igrala sezono 2022/23 (in samo
       njo); pred njo dvojic sploh ni bilo, po njej so spet prve.
   SNTL_BREZ_DVOJIC: kot SNTL, a brez dvojic - samo 9 posamicnih tekem:
       A-X, B-Y, C-Z, B-X, A-Z, C-Y, B-Z, C-X, A-Y. Tako igra 1. SNTL zenske.
   OLIMPIJSKI: 3 igralci, brez dvojic, najvec pet tekem in prvi do treh zmag:
       A-X, B-Y, C-Z, A-Y, B-X. Od SNTL_BREZ_DVOJIC se loci ze pri cetrti
       tekmi, zato ni njegova skrajsava. Tako igrata ekipni DP mladincev in
       kadetov (V13).
   CORBILLON: 2 igralca + dvojice (mlajse kategorije): A-X, B-Y, dvojice,
       A-Y, B-X.
   SAVINJA: kot Corbillon (2 igralca + dvojice, oba igrata tudi par), le da so
       dvojice PRVE in ne na sredini: dvojice, A-X, B-Y, A-Y, B-X. Rekreacijska
       liga tako zacne z obema igralcema na mizi, brez cakanja na svoj nastop.

   Nove formate dodas tako, da dopolnis enum in metodo razpored() (in razsiris
   CHECK omejitev stolpca liga.format_srecanja z novo migracijo). */
package si.turnirko.modeli;

import java.util.List;

public enum FormatSrecanja {

    SNTL(3, true, true),
    SNTL_PRVA(3, true, true),
    SNTL_DVOJICE_SEDMA(3, true, true),
    SNTL_PRVA_DVOJICE_CETRTA(3, true, true),
    SNTL_BREZ_DVOJIC(3, false, false),
    OLIMPIJSKI(3, false, false),
    CORBILLON(2, true, false),
    SAVINJA(2, true, false);

    /* Eno mesto (tekma) v srecanju. Pri dvojicah sta domaci/gost null - par
       se dolobi iz postave (igralci z oznako v_dvojici). */
    public record MestoTekme(TipTekmeSrecanja tip, String domaci, String gost) {
        public String oznaka() {
            return tip == TipTekmeSrecanja.DVOJICE ? "dvojice" : domaci + "-" + gost;
        }
    }

    private final int stIgralcev;
    private final boolean imaDvojice;
    /* Ali je treba par za dvojice izbrati (SNTL: 2 od 3) ali igrata oba
       igralca (CORBILLON: 2 od 2, izbire ni). */
    private final boolean izbiraDvojice;

    FormatSrecanja(int stIgralcev, boolean imaDvojice, boolean izbiraDvojice) {
        this.stIgralcev = stIgralcev;
        this.imaDvojice = imaDvojice;
        this.izbiraDvojice = izbiraDvojice;
    }

    public int getStIgralcev() { return stIgralcev; }
    public boolean imaDvojice() { return imaDvojice; }
    public boolean izbiraDvojice() { return izbiraDvojice; }

    /* Mesta domacih (A, B, C ...) glede na stevilo igralcev. */
    public List<String> pozicijeDomaci() {
        return List.of("A", "B", "C").subList(0, stIgralcev);
    }

    /* Mesta gostov (X, Y, Z ...). */
    public List<String> pozicijeGost() {
        return List.of("X", "Y", "Z").subList(0, stIgralcev);
    }

    /* Koliko igralcev na strani je v paru za dvojice (0, ce dvojic ni). */
    public int stVDvojici() {
        return imaDvojice ? 2 : 0;
    }

    /* Urejen seznam tekem srecanja. */
    public List<MestoTekme> razpored() {
        TipTekmeSrecanja d = TipTekmeSrecanja.DVOJICE;
        TipTekmeSrecanja p = TipTekmeSrecanja.POSAMICNA;
        return switch (this) {
            case SNTL -> List.of(
                    new MestoTekme(d, null, null),
                    new MestoTekme(p, "A", "X"),
                    new MestoTekme(p, "B", "Y"),
                    new MestoTekme(p, "C", "Z"),
                    new MestoTekme(p, "B", "X"),
                    new MestoTekme(p, "A", "Z"),
                    new MestoTekme(p, "C", "Y"),
                    new MestoTekme(p, "B", "Z"),
                    new MestoTekme(p, "C", "X"),
                    new MestoTekme(p, "A", "Y"));
            case SNTL_PRVA -> List.of(
                    new MestoTekme(d, null, null),
                    new MestoTekme(p, "B", "X"),
                    new MestoTekme(p, "A", "Z"),
                    new MestoTekme(p, "C", "Y"),
                    new MestoTekme(p, "B", "Z"),
                    new MestoTekme(p, "C", "X"),
                    new MestoTekme(p, "A", "Y"));
            case SNTL_DVOJICE_SEDMA -> List.of(
                    new MestoTekme(p, "A", "X"),
                    new MestoTekme(p, "B", "Y"),
                    new MestoTekme(p, "C", "Z"),
                    new MestoTekme(p, "B", "X"),
                    new MestoTekme(p, "A", "Z"),
                    new MestoTekme(p, "C", "Y"),
                    new MestoTekme(d, null, null),
                    new MestoTekme(p, "B", "Z"),
                    new MestoTekme(p, "C", "X"),
                    new MestoTekme(p, "A", "Y"));
            case SNTL_PRVA_DVOJICE_CETRTA -> List.of(
                    new MestoTekme(p, "B", "X"),
                    new MestoTekme(p, "A", "Z"),
                    new MestoTekme(p, "C", "Y"),
                    new MestoTekme(d, null, null),
                    new MestoTekme(p, "B", "Z"),
                    new MestoTekme(p, "C", "X"),
                    new MestoTekme(p, "A", "Y"));
            case SNTL_BREZ_DVOJIC -> List.of(
                    new MestoTekme(p, "A", "X"),
                    new MestoTekme(p, "B", "Y"),
                    new MestoTekme(p, "C", "Z"),
                    new MestoTekme(p, "B", "X"),
                    new MestoTekme(p, "A", "Z"),
                    new MestoTekme(p, "C", "Y"),
                    new MestoTekme(p, "B", "Z"),
                    new MestoTekme(p, "C", "X"),
                    new MestoTekme(p, "A", "Y"));
            case OLIMPIJSKI -> List.of(
                    new MestoTekme(p, "A", "X"),
                    new MestoTekme(p, "B", "Y"),
                    new MestoTekme(p, "C", "Z"),
                    new MestoTekme(p, "A", "Y"),
                    new MestoTekme(p, "B", "X"));
            case CORBILLON -> List.of(
                    new MestoTekme(p, "A", "X"),
                    new MestoTekme(p, "B", "Y"),
                    new MestoTekme(d, null, null),
                    new MestoTekme(p, "A", "Y"),
                    new MestoTekme(p, "B", "X"));
            case SAVINJA -> List.of(
                    new MestoTekme(d, null, null),
                    new MestoTekme(p, "A", "X"),
                    new MestoTekme(p, "B", "Y"),
                    new MestoTekme(p, "A", "Y"),
                    new MestoTekme(p, "B", "X"));
        };
    }

    /* Skupno stevilo tekem v srecanju pri tem formatu. */
    public int stTekem() {
        return razpored().size();
    }
}
