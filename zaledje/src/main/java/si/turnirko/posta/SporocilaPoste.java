/* Besedila e-poste, ki jih Turnirko poslje. Ton kot v vmesniku: stvaren in
   kratek. Koda je v besedilu in ne v povezavi - vpise se v aplikacijo, zato
   v e-posti ni ne povezav ne parametrov, ki bi jih bilo mogoce podtakniti. */
package si.turnirko.posta;

public final class SporocilaPoste {

    public record Besedilo(String zadeva, String telo) {}

    private SporocilaPoste() {}

    public static Besedilo potrditevEposte(String koda) {
        return new Besedilo(
                "Turnirko: koda za potrditev e-pošte",
                "Tvoja koda za potrditev e-pošte v Turnirku je:\n\n"
                + "    " + koda + "\n\n"
                + "Velja 10 minut. Vpiši jo v okno, kjer si ustvaril račun.\n\n"
                + "Če računa v Turnirku nisi ustvaril ti, to sporočilo prezri - "
                + "brez kode se račun v 48 urah izbriše.");
    }

    public static Besedilo soglasjeSkrbnika(String imeOtroka, String koda) {
        return new Besedilo(
                "Turnirko: soglasje starša oz. skrbnika",
                imeOtroka + " želi ustvariti račun v Turnirku (namiznoteniška tekmovanja, "
                + "lestvica in profil igralca) in je navedel tvoj naslov kot naslov starša "
                + "oz. skrbnika, ker je mlajši od 15 let.\n\n"
                + "Če s tem soglašaš, otroku posreduj kodo:\n\n"
                + "    " + koda + "\n\n"
                + "Koda velja 24 ur. Če ne soglašaš, sporočilo prezri - račun se brez "
                + "kode ne aktivira in se izbriše.\n\n"
                + "V Turnirku so javno vidni le ime, priimek, klub in rezultati tekem; "
                + "datum rojstva in e-pošta nista javna.");
    }

    public static Besedilo obstojeciRacun() {
        return new Besedilo(
                "Turnirko: račun s tem naslovom že obstaja",
                "Nekdo je poskušal ustvariti račun v Turnirku s tvojo e-pošto. Račun s tem "
                + "naslovom že obstaja, zato nov ni nastal.\n\n"
                + "Če si bil to ti, se prijavi z obstoječim geslom ali izberi »Pozabljeno "
                + "geslo«. Če nisi bil ti, sporočilo prezri - nič se ni spremenilo.");
    }

    public static Besedilo pozabljenoGeslo(String koda) {
        return new Besedilo(
                "Turnirko: koda za novo geslo",
                "Koda za nastavitev novega gesla v Turnirku je:\n\n"
                + "    " + koda + "\n\n"
                + "Velja 10 minut. Če novega gesla nisi zahteval ti, sporočilo prezri - "
                + "geslo ostane nespremenjeno.");
    }
}
