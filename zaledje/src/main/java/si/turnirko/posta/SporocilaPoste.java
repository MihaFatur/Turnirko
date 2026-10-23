/* Besedila e-poste, ki jih Turnirko poslje. Ton kot v vmesniku: stvaren in
   kratek. Koda je v besedilu in ne v povezavi - vpise se v aplikacijo, zato
   v sporocilih s kodo ni ne povezav ne parametrov, ki bi jih bilo mogoce
   podtakniti. Povezave nosijo samo obvestila brez kode: stalne strani
   (/narocnina, /lige/{id}) in odjava od obvestil po kolu, katere zeton ne
   odpre nicesar razen izklopa obvestil (SporociloKola). */
package si.turnirko.posta;

import java.util.Locale;
import java.util.Map;

public final class SporocilaPoste {

    /* Telo je navadno besedilo; html in glave (List-Unsubscribe) ima samo
       obvestilo po kolu - takrat gre sporocilo kot multipart z obema telesoma. */
    public record Besedilo(String zadeva, String telo, String html, Map<String, String> glave) {
        public Besedilo(String zadeva, String telo) {
            this(zadeva, telo, null, Map.of());
        }
    }

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

    /* Nekaj dni pred koncem preizkusa Premium (PreizkusPremiumStoritev).
       Obvestilo o stanju racuna, ne poziv: pove, kdaj se preizkus konca, kaj
       ostane in kje se Premium obdrzi. */
    public static Besedilo konecPreizkusa(String datumKonca, double cenaMesecno, double cenaLetno,
                                          String naslovNarocnine) {
        return new Besedilo(
                "Turnirko: preizkus Premium se konča " + datumKonca,
                "Tvoj mesec Igralca Premium v Turnirku se konča " + datumKonca + ".\n\n"
                + "Potem račun ostane brezplačen: rating, zgodovina tekem, profil, spremljanje "
                + "lig in obvestila po kolu ostanejo, forma, nasprotniki, napoved tekme ter nizi "
                + "in točke pa se zaklenejo. Ničesar ti ni treba narediti.\n\n"
                + "Če želiš Premium obdržati, stane " + cena(cenaMesecno) + " na mesec ali "
                + cena(cenaLetno) + " na leto. Vklopiš ga na strani Naročnina:\n\n"
                + "    " + naslovNarocnine + "\n\n"
                + "Preklic je mogoč kadarkoli.");
    }

    static String cena(double znesek) {
        return String.format(Locale.ROOT, "%.2f", znesek).replace('.', ',') + " €";
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
