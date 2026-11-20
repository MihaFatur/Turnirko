/* Stalna mesta oznak in QR kode na natisnjenem listu, po katerih se fotografija
   poravna (PoravnavaListka): sodniski listek (GeometrijaListka) ali list
   vadnice stevk (GeometrijaVadnice). Vse v mm od zgornjega levega roba.

   najvecOdmikQr: koliko sme iskalni vzorec QR zgoraj desno odstopati od lege, ki
   jo napove afina preslikava iz treh oznak, da je se ista koda (sicer je tuja).
   Afina napoved se pri nagnjenem telefonu zmoti tem bolj, cim vecji je list. */
package si.turnirko.branje;

public record Postavitev(double sirina, double visina,
                         double[] zgorajLevo, double[] spodajLevo, double[] spodajDesno, double stranicaOznake,
                         double qrX, double qrY, double qrStranica, int qrModulov, double najvecOdmikQr) {

    public static final Postavitev LISTEK = new Postavitev(GeometrijaListka.SIRINA, GeometrijaListka.VISINA,
            GeometrijaListka.ZGORAJ_LEVO, GeometrijaListka.SPODAJ_LEVO, GeometrijaListka.SPODAJ_DESNO,
            GeometrijaListka.STRANICA_OZNAKE, GeometrijaListka.QR_X, GeometrijaListka.QR_Y,
            GeometrijaListka.QR_STRANICA, GeometrijaListka.QR_MODULOV, 15);

    private double modul() {
        return qrStranica / qrModulov;
    }

    /* Sredisca iskalnih vzorcev QR: 3,5 modula od robov kode. */
    public double[] qrZgorajDesno() {
        return new double[] {qrX + qrStranica - 3.5 * modul(), qrY + 3.5 * modul()};
    }

    public double[] qrZgorajLevo() {
        return new double[] {qrX + 3.5 * modul(), qrY + 3.5 * modul()};
    }

    public double[] qrSpodajLevo() {
        return new double[] {qrX + 3.5 * modul(), qrY + qrStranica - 3.5 * modul()};
    }
}
