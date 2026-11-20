/* Iz prebranega listka sestavi predlog izida tekme in pove, zakaj mu ne gre
   zaupati.

   Bralnik (umetna inteligenca) se pri rocno napisanih stevkah zmoti - 7 in 1,
   1 in 4, popravljena celica. Pravila namiznega tenisa so zato sito, skozi
   katerega gre vsak predlog:
   - niz mora biti veljaven (11 proti najvec 9, nad 10:10 razlika natanko 2);
   - zmagovalec mora dobiti natanko toliko nizov, kolikor jih je za zmago, in
     po odlocitvi se noben niz ne igra vec;
   - stevilo dobljenih nizov iz tock se mora ujemati s celico »Rezultat«, ki jo
     sodnik izpolni posebej (dva neodvisna zapisa istega izida).
   Kar ne gre skozi, je opozorilo; predlog z opozorilom ali negotovo celico ni
   »pripravljen« in ga organizator pred potrditvijo pogleda. Potrditev gre v
   vsakem primeru skozi TekmaStoritev, ki pravila preveri se enkrat.

   »obrnjeno« pomeni, da levi stolpec listka ni prijava 1 tekme (prazen listek, na
   katerega je sodnik imeni napisal v drugem vrstnem redu). Razred ne pozna baze. */
package si.turnirko.storitve;

import java.util.ArrayList;
import java.util.List;

import si.turnirko.dto.PredlogIzidaDto;
import si.turnirko.dto.PredlogIzidaDto.NizPredloga;
import si.turnirko.modeli.IzidTekme;

public final class PredlogIzidaListka {

    private PredlogIzidaListka() {}

    public static PredlogIzidaDto sestavi(PrebranListek listek, int steviloNizov, boolean obrnjeno) {
        int zaZmago = steviloNizov / 2 + 1;
        List<String> opozorila = new ArrayList<>();
        if (!listek.jeListek()) {
            opozorila.add("Na fotografiji ni prepoznan sodniški listek.");
        }

        List<PrebranListek.Niz> vrstice = new ArrayList<>(listek.niziAliPrazno());
        // prazne vrstice na koncu so neodigrani nizi (tekma 3:0 na listku za 5 nizov)
        while (!vrstice.isEmpty() && jePrazna(vrstice.getLast())) {
            vrstice.removeLast();
        }

        List<NizPredloga> nizi = new ArrayList<>();
        boolean negotovo = false;
        boolean nepopolno = false;
        int dobljeni1 = 0;
        int dobljeni2 = 0;
        for (int i = 0; i < vrstice.size(); i++) {
            PrebranListek.Niz vrstica = vrstice.get(i);
            Integer tocke1 = obrnjeno ? vrstica.desno() : vrstica.levo();
            Integer tocke2 = obrnjeno ? vrstica.levo() : vrstica.desno();
            boolean negotovo1 = obrnjeno ? vrstica.negotovoDesno() : vrstica.negotovoLevo();
            boolean negotovo2 = obrnjeno ? vrstica.negotovoLevo() : vrstica.negotovoDesno();
            boolean neveljaven = false;
            if (tocke1 == null || tocke2 == null) {
                nepopolno = true;
                opozorila.add((i + 1) + ". niz ni v celoti prebran.");
            } else if (!veljavenNiz(tocke1, tocke2)) {
                neveljaven = true;
                nepopolno = true;
                opozorila.add((i + 1) + ". niz (" + tocke1 + ":" + tocke2
                        + ") ni veljaven namiznoteniški rezultat.");
            } else if (tocke1 > tocke2) {
                dobljeni1++;
            } else {
                dobljeni2++;
            }
            negotovo |= negotovo1 || negotovo2;
            nizi.add(new NizPredloga(tocke1, tocke2, negotovo1, negotovo2, neveljaven));
        }

        Integer rezultat1 = obrnjeno ? listek.rezultatDesno() : listek.rezultatLevo();
        Integer rezultat2 = obrnjeno ? listek.rezultatLevo() : listek.rezultatDesno();
        Integer zmagovalecStran = stranZmagovalca(listek.posebnostZmagovalec(), obrnjeno);
        if (listek.opomba() != null && !listek.opomba().isBlank()) {
            opozorila.add("Opomba bralnika: " + listek.opomba().trim());
        }

        IzidTekme posebnost = posebnost(listek.posebnost());
        if (posebnost == IzidTekme.BREZ_BOJA || posebnost == IzidTekme.DISKVALIFIKACIJA) {
            opozorila.add(posebnost == IzidTekme.BREZ_BOJA
                    ? "Na listku je zapisan izid brez boja. Preveri, kdo je zmagal."
                    : "Na listku je zapisana diskvalifikacija. Preveri, kdo je zmagal.");
            if (zmagovalecStran == null) {
                opozorila.add("Zmagovalca ni bilo mogoče razbrati.");
            }
            return new PredlogIzidaDto(posebnost, null, null, zmagovalecStran, List.of(), opozorila, false);
        }
        if (posebnost == IzidTekme.PREDAJA) {
            opozorila.add("Na listku je zapisana predaja. Preveri delni izid in zmagovalca.");
            if (zmagovalecStran == null) {
                opozorila.add("Zmagovalca ni bilo mogoče razbrati.");
            }
            return new PredlogIzidaDto(IzidTekme.PREDAJA, dobljeni1, dobljeni2, zmagovalecStran,
                    nizi, opozorila, false);
        }

        Integer niz1;
        Integer niz2;
        if (nizi.isEmpty()) {
            // turnir brez tock po nizih: sodnik je izpolnil samo rezultat
            if (rezultat1 != null && rezultat2 != null) {
                niz1 = rezultat1;
                niz2 = rezultat2;
                negotovo |= listek.negotovRezultat();
                opozorila.add(preveriIzidVNizih(rezultat1, rezultat2, zaZmago, steviloNizov));
            } else {
                niz1 = null;
                niz2 = null;
                opozorila.add("Na listku ni prebranih točk ne rezultata.");
            }
        } else {
            niz1 = dobljeni1;
            niz2 = dobljeni2;
            if (!nepopolno) {
                opozorila.add(preveriPotekTekme(nizi, zaZmago, steviloNizov));
                if (rezultat1 != null && rezultat2 != null
                        && (rezultat1 != dobljeni1 || rezultat2 != dobljeni2)) {
                    opozorila.add("Rezultat na listku (" + rezultat1 + ":" + rezultat2
                            + ") se ne ujema s točkami po nizih (" + dobljeni1 + ":" + dobljeni2 + ").");
                }
            }
        }
        opozorila.removeIf(o -> o == null);
        boolean pripravljen = opozorila.isEmpty() && !negotovo && niz1 != null;
        return new PredlogIzidaDto(IzidTekme.IGRANO, niz1, niz2, null, nizi, opozorila, pripravljen);
    }

    /* Ista pravila niza kot NiziPravila (zaledje) in TockeNizov (vmesnik). */
    static boolean veljavenNiz(int tocke1, int tocke2) {
        int vec = Math.max(tocke1, tocke2);
        int manj = Math.min(tocke1, tocke2);
        return manj >= 0 && ((vec == 11 && manj <= 9) || (vec > 11 && vec - manj == 2));
    }

    /* Tekma se konca, ko eden doseze zaZmago nizov; vrne opozorilo ali null. */
    private static String preveriPotekTekme(List<NizPredloga> nizi, int zaZmago, int steviloNizov) {
        int s1 = 0;
        int s2 = 0;
        for (int i = 0; i < nizi.size(); i++) {
            if (s1 == zaZmago || s2 == zaZmago) {
                return "Tekma je bila odločena že po " + i + ". nizu (" + s1 + ":" + s2
                        + "), na listku pa je še " + (i + 1) + ". niz.";
            }
            if (nizi.get(i).tocke1() > nizi.get(i).tocke2()) {
                s1++;
            } else {
                s2++;
            }
        }
        if (s1 != zaZmago && s2 != zaZmago) {
            return "Nihče nima " + zaZmago + " dobljenih nizov (" + s1 + ":" + s2
                    + ", tekma na " + steviloNizov + " nizov). Manjka niz ali je napačno prebran.";
        }
        return null;
    }

    private static String preveriIzidVNizih(int s1, int s2, int zaZmago, int steviloNizov) {
        if (Math.max(s1, s2) != zaZmago || Math.min(s1, s2) >= zaZmago) {
            return "Rezultat " + s1 + ":" + s2 + " ni mogoč pri tekmi na " + steviloNizov + " nizov.";
        }
        return null;
    }

    private static boolean jePrazna(PrebranListek.Niz vrstica) {
        return vrstica.levo() == null && vrstica.desno() == null
                && !vrstica.negotovoLevo() && !vrstica.negotovoDesno();
    }

    private static Integer stranZmagovalca(String zapis, boolean obrnjeno) {
        if ("LEVO".equals(zapis)) {
            return obrnjeno ? 2 : 1;
        }
        if ("DESNO".equals(zapis)) {
            return obrnjeno ? 1 : 2;
        }
        return null;
    }

    private static IzidTekme posebnost(String zapis) {
        if (zapis == null) {
            return null;
        }
        return switch (zapis) {
            case "BREZ_BOJA" -> IzidTekme.BREZ_BOJA;
            case "PREDAJA" -> IzidTekme.PREDAJA;
            case "DISKVALIFIKACIJA" -> IzidTekme.DISKVALIFIKACIJA;
            default -> null;
        };
    }
}
