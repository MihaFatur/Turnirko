/* Sistem SV_REGIJA: Skupine, skupine, glavni in tolazilni zreb, igra se za VSA mesta.

   POIMENOVANJE. Organizator in gledalec poznata "Skupino 1, Skupino 2 ..."
   (prej "nivo"); v kodi in bazi je to se vedno NIVO, da se ne pomesa s
   skupino A, B ... znotraj njega (odlocitev lastnika 7. 11. 2026).

   POTEK
   1. Igralci se po jakosti (IzborStoritev) razdelijo v NIVOJE. Nivo ima
      privzeto 16 igralcev (4 skupine po 4), zadnji nivo dobi ostanek. Stevilo
      nivojev doloci stevilo prijav, organizator ga lahko nastavi, lahko pa
      velikost vsakega nivoja tudi vpise (Dogodek.velikostiNivojev).
   2. V vsakem nivoju igrajo skupine "vsak z vsakim". Skupine nastanejo po
      jakostnih pasovih kot pri vseh skupinskih sistemih (NosilciStoritev) ali
      pa jih organizator vpise rocno. Skupina stirih igra po vrstnem redu
      1-4, 2-3 | 4-3, 1-2 | 2-4, 3-1 (tekmeSkupine), ostale po krozni metodi.
   3. Ko so odigrane vse skupine NIVOJA (ne celega dogodka - nivoji tecejo
      neodvisno), nastanejo zrebi nivoja. Igralci gredo vanje ZAPOREDOMA po
      mestu v skupini in crki skupine - A1, B1, ..., E1, A2, B2 ... - vsak zreb
      sprejme toliko igralcev, kolikor jih ima mreza izbrane ravni (privzeto
      cetrtfinale = 8), zadnji dobi ostanek. Pri petih skupinah po 4 je tako
      glavni zreb A1-E1, A2, B2, C2, drugi D2, E2, A3-E3, A4, tretji B4-E4.
      Organizator lahko za vsak nivo izbere raven in vpise stevilo zrebov; ce
      zrebi ne sprejmejo vseh igralcev (ali bi kateri ostal prazen), zreba ni.
      Visje mesto v zaporedju je visji nosilec, pasovi se zrebajo, igralca iz
      iste skupine se srecata cim pozneje (NosilciStoritev.vMrezoPoVrsti).
      Razpored mest sme organizator rocno popraviti, dokler se zreb ni zacel.
   4. Zreb se igra za VSA mesta: porazenca vsakega kola se srecata med seboj za
      slabsa mesta, zmagovalca za boljsa, do zadnjega para. Mreza z 8 igralci
      ima 12 tekem (4 + 2 + 1 + 1 + 2 + 1 + 1) in odloci vseh 8 mest.

   DREVO ZA VSA MESTA
   Mreza velikosti n (potenca 2) za mesta p .. p+n-1: n/2 tekem v prvem kolu,
   zmagovalci gredo v mrezo n/2 za mesta p .. p+n/2-1, porazenci v mrezo n/2 za
   mesta p+n/2 .. p+n-1; mreza velikosti 1 je koncno mesto. Porazenca sosednjih
   parov se tako srecata med seboj (poraz proti 1. nosilcu in poraz proti
   nosilcu v istem polovici mreze vodita v isto tekmo za 5.-8. mesto).

   PROSTA MESTA (bye). Ce udelezencev ni potenca 2, ostanejo mesta v mrezi
   prazna in nosilci dobijo prosti prehod. Tekem s praznim nasprotnikom NE
   ustvarimo - igralec gre naprej brez zapisa - in prazno ostane tudi mesto
   porazenca, zato se v drevesu za mesta slabsi par srecata samo, ce sta oba
   porazenca resnicna. Prazna mesta tako vedno pristanejo na dnu razpona in
   koncna mesta ostanejo strnjena (1 .. stevilo igralcev).

   KONCNA MESTA se ne racunajo, ampak berejo: vsaka tekma, ki odloca par mest,
   nosi mestoZmagovalca in mestoPorazenca (ABSOLUTNO mesto na dogodku, zato
   zreb drugega nivoja stoji za zadnjim mestom prvega).

   Nivo z eno samo skupino zrebov nima: razvrstitev v skupini je razvrstitev
   nivoja. */
package si.turnirko.storitve;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.SvPredlogDto;
import si.turnirko.dto.SvRegijaDto;
import si.turnirko.dto.SvVnos;
import si.turnirko.dto.SvZrebPredlogDto;
import si.turnirko.dto.VrsticaLestviceDto;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.FazaTekme;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.SistemTekmovanja;
import si.turnirko.modeli.Skupina;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.StatusTekmovanja;
import si.turnirko.modeli.Tekma;
import si.turnirko.modeli.VlogaIzvora;
import si.turnirko.modeli.Zreb;
import si.turnirko.repozitoriji.DogodekRepozitorij;
import si.turnirko.repozitoriji.PrijavaRepozitorij;
import si.turnirko.repozitoriji.SkupinaRepozitorij;
import si.turnirko.repozitoriji.TekmaRepozitorij;
import si.turnirko.repozitoriji.ZrebRepozitorij;

@Service
public class SvRegijaStoritev {

    /* Privzeta oblika nivoja: 4 skupine po 4 igralci = 16. */
    public static final int PRIVZETO_SKUPIN_NA_NIVO = 4;
    public static final int PRIVZETA_VELIKOST_SKUPINE = 4;

    /* Zgornje meje: nivojev je najvec 9 (oznaka skupine "9A" ostane v treh
       znakih), skupin na nivo 26 (A..Z). */
    public static final int NAJVEC_NIVOJEV = 9;
    public static final int NAJVEC_SKUPIN = 26;

    /* Zadnji nivo je lahko manjsi od polnega, a ne manjsi od ene skupine: ostanek,
       ki bi bil manjsi, se pridruzi prejsnjemu nivoju (16 + 2 ni nivo, ampak 18). */
    static final int NAJMANJ_ZADNJEGA_NIVOJA = 4;

    /* Najmanjsa velikost skupine, ki jo razrez se predlaga (skupina dveh bi bila
       ena sama tekma). Rocni vpis sme biti manjsi (2). */
    static final int NAJMANJ_V_SKUPINI = 3;

    /* Raven, na kateri se zrebi zacnejo, je velikost mreze: zreb sprejme toliko
       igralcev (zadnji ostanek). Privzeto cetrtfinale - v SV regiji se povsod
       igrajo zrebi po 8. Pri 4 skupinah to da isto kot prejsnje pravilo "1.-2. v
       glavni, 3.-4. v tolazilni", pri 8 skupinah isto kot "vsak rang svoj zreb". */
    public static final int PRIVZETA_VELIKOST_ZREBA = 8;
    static final List<Integer> VELIKOSTI_ZREBA = List.of(2, 4, 8, 16, 32);

    private final DogodekRepozitorij dogodekRepozitorij;
    private final PrijavaRepozitorij prijavaRepozitorij;
    private final TekmaRepozitorij tekmaRepozitorij;
    private final SkupinaRepozitorij skupinaRepozitorij;
    private final ZrebRepozitorij zrebRepozitorij;
    private final NosilciStoritev nosilci;
    private final IzborStoritev izborStoritev;
    private final RazvrstitevStoritev razvrstitevStoritev;
    private final LastnistvoStoritev lastnistvo;

    public SvRegijaStoritev(DogodekRepozitorij dogodekRepozitorij,
                            PrijavaRepozitorij prijavaRepozitorij,
                            TekmaRepozitorij tekmaRepozitorij,
                            SkupinaRepozitorij skupinaRepozitorij,
                            ZrebRepozitorij zrebRepozitorij,
                            NosilciStoritev nosilci,
                            IzborStoritev izborStoritev,
                            RazvrstitevStoritev razvrstitevStoritev,
                            LastnistvoStoritev lastnistvo) {
        this.dogodekRepozitorij = dogodekRepozitorij;
        this.prijavaRepozitorij = prijavaRepozitorij;
        this.tekmaRepozitorij = tekmaRepozitorij;
        this.skupinaRepozitorij = skupinaRepozitorij;
        this.zrebRepozitorij = zrebRepozitorij;
        this.nosilci = nosilci;
        this.izborStoritev = izborStoritev;
        this.razvrstitevStoritev = razvrstitevStoritev;
        this.lastnistvo = lastnistvo;
    }

    // ---------------------------------------------------------------------
    // Razrez (cista logika)
    // ---------------------------------------------------------------------

    /* En nivo razreza: koliko igralcev ima, katera mesta zajame, kako se
       razdeli v skupine in koliko igralcev pride v vsak zreb. */
    public record NivoRazrez(int nivo, int velikost, int odMesta,
                             List<Integer> velikostiSkupin, List<Integer> velikostiZrebov) {
        public int doMesta() { return odMesta + velikost - 1; }
    }

    /* Razrez igralcev po nivojih, skupinah in zrebih.

       skupinNaNivo * velikostSkupine je velikost polnega nivoja (privzeto 16).
       steviloNivojev in rocneVelikosti sta neobvezna; rocne velikosti imajo
       prednost in morajo sestati natanko v stevilo igralcev. */
    public static List<NivoRazrez> razrez(int igralcev, int skupinNaNivo, int velikostSkupine,
                                          Integer steviloNivojev, List<Integer> rocneVelikosti) {
        return razrez(igralcev, skupinNaNivo, velikostSkupine, steviloNivojev, rocneVelikosti, null, null);
    }

    /* steviloZrebov in velikostZrebov: po nivojih, null (ali manjkajoc nivo) =
       samodejno. Vpisano stevilo zrebov, ki ne ustreza igralcem nivoja, ustavi
       razrez (DomenskaIzjema) - zreb takrat ni mogoc. */
    public static List<NivoRazrez> razrez(int igralcev, int skupinNaNivo, int velikostSkupine,
                                          Integer steviloNivojev, List<Integer> rocneVelikosti,
                                          List<Integer> steviloZrebov, List<Integer> velikostZrebov) {
        if (igralcev < 2) {
            throw new DomenskaIzjema("Za žreb sta potrebna vsaj 2 igralca (trenutno: " + igralcev + ").");
        }
        List<Integer> velikosti = rocneVelikosti != null && !rocneVelikosti.isEmpty()
                ? preveriRocneVelikosti(rocneVelikosti, igralcev)
                : samodejneVelikosti(igralcev, skupinNaNivo * velikostSkupine, steviloNivojev);

        List<NivoRazrez> razrez = new ArrayList<>();
        int odMesta = 1;
        for (int i = 0; i < velikosti.size(); i++) {
            List<Integer> skupine = velikostiSkupin(velikosti.get(i), velikostSkupine);
            if (skupine.size() > NAJVEC_SKUPIN) {
                throw new DomenskaIzjema("Skupina " + (i + 1) + " bi imela " + skupine.size()
                        + " skupin, največ jih je " + NAJVEC_SKUPIN + " (A–Z). Povečaj število Skupin"
                        + " ali velikost skupine.");
            }
            razrez.add(new NivoRazrez(i + 1, velikosti.get(i), odMesta, skupine,
                    zrebiNivoja(i + 1, skupine, steviloZrebov, velikostZrebov)));
            odMesta += velikosti.get(i);
        }
        return razrez;
    }

    private static List<Integer> preveriRocneVelikosti(List<Integer> velikosti, int igralcev) {
        if (velikosti.size() > NAJVEC_NIVOJEV) {
            throw new DomenskaIzjema("Skupin 1, 2 … je lahko največ " + NAJVEC_NIVOJEV + ".");
        }
        int vsota = 0;
        for (int velikost : velikosti) {
            if (velikost < 2) {
                throw new DomenskaIzjema("Skupina mora imeti vsaj 2 igralca.");
            }
            vsota += velikost;
        }
        if (vsota != igralcev) {
            throw new DomenskaIzjema("Vpisane velikosti Skupin (skupaj " + vsota
                    + ") se ne ujemajo s številom prijavljenih (" + igralcev + "). Popravi velikosti"
                    + " ali jih izbriši, da jih določi sistem.");
        }
        return velikosti;
    }

    /* Polni nivoji naprej, zadnji dobi ostanek.

       Brez izbranega stevila nivojev: igralcev / polni nivo polnih nivojev, ostanek
       je zadnji nivo (ali, ce je manjsi od ene skupine, se pridruzi prejsnjemu).
       Z izbranim stevilom L: L-1 polnih nivojev in ostanek - razen ce prijav za
       to ni dovolj, takrat se razdelijo enakomerno. */
    static List<Integer> samodejneVelikosti(int igralcev, int poln, Integer steviloNivojev) {
        List<Integer> velikosti = new ArrayList<>();
        if (steviloNivojev == null) {
            int polnih = igralcev / poln;
            int ostanek = igralcev % poln;
            for (int i = 0; i < polnih; i++) {
                velikosti.add(poln);
            }
            if (ostanek > 0) {
                if (polnih == 0 || ostanek >= NAJMANJ_ZADNJEGA_NIVOJA) {
                    velikosti.add(ostanek);
                } else {
                    velikosti.set(velikosti.size() - 1, velikosti.get(velikosti.size() - 1) + ostanek);
                }
            }
            return velikosti;
        }

        int nivojev = Math.max(1, steviloNivojev);
        if (nivojev > NAJVEC_NIVOJEV) {
            throw new DomenskaIzjema("Skupin 1, 2 … je lahko največ " + NAJVEC_NIVOJEV + ".");
        }
        if (igralcev < nivojev * 2) {
            throw new DomenskaIzjema("Za " + nivojev + " Skupin je premalo prijav (" + igralcev
                    + "): vsaka Skupina potrebuje vsaj 2 igralca.");
        }
        if (igralcev >= (nivojev - 1) * poln + NAJMANJ_ZADNJEGA_NIVOJA) {
            for (int i = 0; i < nivojev - 1; i++) {
                velikosti.add(poln);
            }
            velikosti.add(igralcev - (nivojev - 1) * poln);
            return velikosti;
        }
        // premalo prijav za polne nivoje: enakomerno, mocnejsi nivoji so lahko za enega vecji
        int osnova = igralcev / nivojev;
        int visek = igralcev % nivojev;
        for (int i = 0; i < nivojev; i++) {
            velikosti.add(osnova + (i < visek ? 1 : 0));
        }
        return velikosti;
    }

    /* Velikosti skupin nivoja: toliko skupin, da je v vsaki priblizno velikostSkupine
       igralcev in vsaj 3; vecje skupine najprej. Nivo, ki ga ni mogoce razdeliti
       v skupine po vsaj 3, je ena sama skupina (vsak z vsakim). */
    public static List<Integer> velikostiSkupin(int velikostNivoja, int velikostSkupine) {
        int skupin = (velikostNivoja + velikostSkupine - 1) / velikostSkupine;
        while (skupin > 1 && velikostNivoja / skupin < NAJMANJ_V_SKUPINI) {
            skupin--;
        }
        skupin = Math.max(1, skupin);
        int osnova = velikostNivoja / skupin;
        int visek = velikostNivoja % skupin;
        List<Integer> velikosti = new ArrayList<>();
        for (int i = 0; i < skupin; i++) {
            velikosti.add(osnova + (i < visek ? 1 : 0));
        }
        return velikosti;
    }

    /* Koliko igralcev pride v vsak zreb nivoja s privzeto ravnjo (cetrtfinale). */
    public static List<Integer> velikostiZrebov(List<Integer> velikostiSkupin) {
        return velikostiZrebov(velikostiSkupin, PRIVZETA_VELIKOST_ZREBA);
    }

    /* Igralci nivoja gredo v zrebe zaporedoma (A1, B1, ..., A2, B2 ...), vsak
       zreb sprejme velikostZreba igralcev, zadnji ostanek. Nivo z eno skupino
       zrebov nima - mesta doloci razvrstitev v skupini. */
    public static List<Integer> velikostiZrebov(List<Integer> velikostiSkupin, int velikostZreba) {
        if (velikostiSkupin.size() <= 1) {
            return List.of();
        }
        int igralcev = velikostiSkupin.stream().mapToInt(Integer::intValue).sum();
        List<Integer> velikosti = new ArrayList<>();
        for (int ostalo = igralcev; ostalo > 0; ostalo -= velikostZreba) {
            velikosti.add(Math.min(velikostZreba, ostalo));
        }
        return velikosti;
    }

    /* Zrebi nivoja po nastavitvah organizatorja. Vpisano stevilo mora natanko
       ustrezati: ce zrebi ne sprejmejo vseh igralcev ali bi kateri ostal prazen,
       sistem zreba ne dovoli (odlocitev lastnika 7. 11. 2026). */
    static List<Integer> zrebiNivoja(int nivo, List<Integer> velikostiSkupin,
                                     List<Integer> steviloZrebov, List<Integer> velikostZrebov) {
        int velikostZreba = velikostZrebaNivoja(velikostZrebov, nivo);
        List<Integer> zrebi = velikostiZrebov(velikostiSkupin, velikostZreba);
        Integer vpisano = vpisanoZrebov(steviloZrebov, nivo);
        if (vpisano == null || vpisano == zrebi.size()) {
            return zrebi;
        }
        int igralcev = velikostiSkupin.stream().mapToInt(Integer::intValue).sum();
        if (zrebi.isEmpty()) {
            throw new DomenskaIzjema("Skupina " + nivo + " ima eno samo skupino, zato izločilnih"
                    + " žrebov nima (vpisanih: " + vpisano + "). Izbriši število žrebov ali razdeli"
                    + " Skupino v več skupin.");
        }
        String raven = imeRavni(velikostZreba);
        if (vpisano < zrebi.size()) {
            throw new DomenskaIzjema("Skupina " + nivo + " ima " + igralcev(igralcev) + ", "
                    + zrebov(vpisano) + " od " + raven + " " + sprejme(vpisano) + " največ "
                    + vpisano * velikostZreba + ". Potrebno število žrebov: " + zrebi.size()
                    + " (ali izberi zgodnejšo raven).");
        }
        throw new DomenskaIzjema("Skupina " + nivo + " ima samo " + igralcev(igralcev) + ": pri "
                + vpisano + " žrebih od " + raven + " bi zadnji ostal prazen. Potrebno število"
                + " žrebov: " + zrebi.size() + " (ali izberi poznejšo raven).");
    }

    /* "ima 19 igralcev" (tozilnik). */
    private static String igralcev(int n) {
        return n + switch (n % 100) {
            case 1, 2 -> " igralca";
            case 3, 4 -> " igralce";
            default -> " igralcev";
        };
    }

    /* Velikost mreze ravni za nivo (nivoji so oznaceni od 1): vpisana ali privzeta 8. */
    static int velikostZrebaNivoja(List<Integer> velikostZrebov, int nivo) {
        Integer vpisana = vpisanoNaNivoju(velikostZrebov, nivo);
        return vpisana != null ? vpisana : PRIVZETA_VELIKOST_ZREBA;
    }

    /* Stevilo zrebov, ki ga je za nivo vpisal organizator; null = samodejno. */
    static Integer vpisanoZrebov(List<Integer> steviloZrebov, int nivo) {
        return vpisanoNaNivoju(steviloZrebov, nivo);
    }

    private static Integer vpisanoNaNivoju(List<Integer> poNivojih, int nivo) {
        if (poNivojih == null || nivo < 1 || nivo > poNivojih.size()) {
            return null;
        }
        return poNivojih.get(nivo - 1);
    }

    /* Ime ravni v rodilniku: "od cetrtfinala". */
    static String imeRavni(int velikostMreze) {
        return switch (velikostMreze) {
            case 2 -> "finala";
            case 4 -> "polfinala";
            case 8 -> "četrtfinala";
            case 16 -> "osmine finala";
            case 32 -> "šestnajstine finala";
            default -> "mreže " + velikostMreze;
        };
    }

    private static String zrebov(int n) {
        return n + switch (n % 100) {
            case 1 -> " žreb";
            case 2 -> " žreba";
            case 3, 4 -> " žrebi";
            default -> " žrebov";
        };
    }

    private static String sprejme(int n) {
        return switch (n % 100) {
            case 1 -> "sprejme";
            case 2 -> "sprejmeta";
            default -> "sprejmejo";
        };
    }

    /* Od kod pridejo udelezenci vsakega zreba, kot oznake "A1", "B1" ... (crka
       skupine in mesto v njej) - po istem zaporedju, kot jih zbere zreb. */
    static List<List<String>> viriZrebov(List<Integer> velikostiSkupin, List<Integer> velikostiZrebov) {
        List<String> zaporedje = new ArrayList<>();
        int najvec = velikostiSkupin.stream().mapToInt(Integer::intValue).max().orElse(0);
        for (int mesto = 1; mesto <= najvec; mesto++) {
            for (int s = 0; s < velikostiSkupin.size(); s++) {
                if (velikostiSkupin.get(s) >= mesto) {
                    zaporedje.add((char) ('A' + s) + String.valueOf(mesto));
                }
            }
        }
        List<List<String>> viri = new ArrayList<>();
        int od = 0;
        for (int velikost : velikostiZrebov) {
            int doIzkljucno = Math.min(od + velikost, zaporedje.size());
            viri.add(List.copyOf(zaporedje.subList(Math.min(od, doIzkljucno), doIzkljucno)));
            od = doIzkljucno;
        }
        return viri;
    }


    public static int skupinNaNivo(Dogodek dogodek) {
        return dogodek.getSteviloSkupin() != null ? dogodek.getSteviloSkupin() : PRIVZETO_SKUPIN_NA_NIVO;
    }

    public static int velikostSkupine(Dogodek dogodek) {
        return dogodek.getVelikostSkupine() != null ? dogodek.getVelikostSkupine() : PRIVZETA_VELIKOST_SKUPINE;
    }

    /* Razrez za dogodek z njegovimi nastavitvami. */
    public static List<NivoRazrez> razrez(Dogodek dogodek, int igralcev) {
        return razrez(igralcev, skupinNaNivo(dogodek), velikostSkupine(dogodek),
                dogodek.getSteviloNivojev(), dogodek.velikostiNivojevSeznam(),
                dogodek.steviloZrebovSeznam(), dogodek.velikostZrebovSeznam());
    }

    /* Isti razrez brez preverbe vpisanega stevila zrebov - za predogled, ki naj
       pokaze Skupine tudi takrat, ko vpisano stevilo ne ustreza (napaka je
       takrat zadrzek). */
    private static List<NivoRazrez> razrezZaPrikaz(Dogodek dogodek, int igralcev) {
        return razrez(igralcev, skupinNaNivo(dogodek), velikostSkupine(dogodek),
                dogodek.getSteviloNivojev(), dogodek.velikostiNivojevSeznam(),
                null, dogodek.velikostZrebovSeznam());
    }

    /* Mesta v mrezi iz seznama nosilcev (poSeedu.get(0) = 1. nosilec): mesto i
       drzi nosilca po standardnem vrstnem redu; mesta, ki jih nosilcev ne
       zadosca, so prosta (null). */
    public static Prijava[] mestaVMrezi(List<Prijava> poSeedu) {
        int velikost = NosilciStoritev.najblizjaPotencaDve(poSeedu.size());
        int[] vrstniRed = NosilciStoritev.seedVrstniRed(velikost);
        Prijava[] mesta = new Prijava[velikost];
        for (int i = 0; i < velikost; i++) {
            if (vrstniRed[i] <= poSeedu.size()) {
                mesta[i] = poSeedu.get(vrstniRed[i] - 1);
            }
        }
        return mesta;
    }

    // ---------------------------------------------------------------------
    // Drevo za vsa mesta
    // ---------------------------------------------------------------------

    /* Od kod pride udelezenec mesta: konkretna prijava, rezultat tekme
       (zmagovalec ali porazenec) ali nic (prosto mesto). */
    private record Vir(Prijava prijava, Tekma tekma, VlogaIzvora vloga) {
        static final Vir PRAZEN = new Vir(null, null, null);

        boolean jePrazen() { return prijava == null && tekma == null; }
    }

    /* Stanje gradnje enega zreba. */
    private static final class Gradnja {
        final Dogodek dogodek;
        final Zreb zreb;
        final Map<Integer, Integer> pozicije = new HashMap<>();
        final List<Tekma> tekme = new ArrayList<>();
        final List<Prijava> samiPrijavljeni = new ArrayList<>();

        Gradnja(Dogodek dogodek, Zreb zreb) {
            this.dogodek = dogodek;
            this.zreb = zreb;
        }
    }

    /* Zgradi tekme zreba iz mest v mrezi (null = prosto). Zbrise morebitne
       stare tekme zreba; klicatelj je preveril, da se ni zacet. */
    private void zgradiZreb(Dogodek dogodek, Zreb zreb, Prijava[] mesta, boolean rocni) {
        odstraniTekmeZreba(zreb);

        Gradnja gradnja = new Gradnja(dogodek, zreb);
        List<Vir> sloji = new ArrayList<>();
        for (Prijava mesto : mesta) {
            sloji.add(mesto == null ? Vir.PRAZEN : new Vir(mesto, null, null));
        }
        razdeli(sloji, zreb.getPrvoMesto(), 1, gradnja);

        tekmaRepozitorij.saveAll(gradnja.tekme);
        if (!gradnja.samiPrijavljeni.isEmpty()) {
            prijavaRepozitorij.saveAll(gradnja.samiPrijavljeni);
        }
        zreb.setStUdelezencev((int) java.util.Arrays.stream(mesta).filter(Objects::nonNull).count());
        zreb.setRazpored(java.util.Arrays.stream(mesta)
                .map(m -> m == null ? "" : String.valueOf(m.getId()))
                .collect(Collectors.joining(",")));
        zreb.setZgrajen(true);
        zreb.setRocni(rocni);
        zrebRepozitorij.save(zreb);
    }

    /* Ena stopnja drevesa: sloji so udelezenci mreze velikosti n po vrsti
       (sosednja dva igrata drug proti drugemu). Zmagovalci gredo v mrezo za
       zgornjo polovico mest, porazenci v mrezo za spodnjo. */
    private void razdeli(List<Vir> sloji, int prvoMesto, int kolo, Gradnja gradnja) {
        int n = sloji.size();
        if (n == 1) {
            dodeliMesto(sloji.get(0), prvoMesto, gradnja);
            return;
        }
        List<Vir> zmagovalci = new ArrayList<>();
        List<Vir> porazenci = new ArrayList<>();
        for (int i = 0; i < n; i += 2) {
            Vir a = sloji.get(i);
            Vir b = sloji.get(i + 1);
            if (a.jePrazen() && b.jePrazen()) {
                zmagovalci.add(Vir.PRAZEN);
                porazenci.add(Vir.PRAZEN);
            } else if (a.jePrazen() || b.jePrazen()) {
                // prosti prehod: tekme ni, porazenca tudi ne
                zmagovalci.add(a.jePrazen() ? b : a);
                porazenci.add(Vir.PRAZEN);
            } else {
                Tekma tekma = novaTekma(gradnja, kolo, prvoMesto, prvoMesto + n - 1);
                postavi(tekma, a, b);
                zmagovalci.add(new Vir(null, tekma, VlogaIzvora.ZMAGOVALEC));
                porazenci.add(new Vir(null, tekma, VlogaIzvora.PORAZENEC));
            }
        }
        razdeli(zmagovalci, prvoMesto, kolo + 1, gradnja);
        razdeli(porazenci, prvoMesto + n / 2, kolo + 1, gradnja);
    }

    /* Udelezenec je na koncu poti in dobi mesto: tekma, ki ga odloca, si ga
       zapomni (iz tega nastane koncna razvrstitev). */
    private void dodeliMesto(Vir vir, int mesto, Gradnja gradnja) {
        if (vir.jePrazen()) {
            return;
        }
        if (vir.prijava() != null) {
            vir.prijava().setKoncnoMesto(mesto);
            gradnja.samiPrijavljeni.add(vir.prijava());
        } else if (vir.vloga() == VlogaIzvora.ZMAGOVALEC) {
            vir.tekma().setMestoZmagovalca(mesto);
        } else {
            vir.tekma().setMestoPorazenca(mesto);
        }
    }

    private Tekma novaTekma(Gradnja gradnja, int kolo, int razponOd, int razponDo) {
        FazaTekme faza = gradnja.zreb.faza();
        int pozicija = gradnja.pozicije.compute(kolo, (k, trenutna) -> (trenutna != null
                ? trenutna
                : tekmaRepozitorij.najvisjaPozicija(gradnja.dogodek.getId(), faza, k)) + 1);

        Tekma tekma = new Tekma();
        tekma.setDogodek(gradnja.dogodek);
        tekma.setFaza(faza);
        tekma.setKolo(kolo);
        tekma.setPozicija(pozicija);
        tekma.setSteviloNizov(gradnja.dogodek.getPrivzetoSteviloNizov());
        tekma.setStatus(StatusTekme.CAKA);
        tekma.setIdZreb(gradnja.zreb.getId());
        tekma.setRazponOd(razponOd);
        tekma.setRazponDo(razponDo);
        // shranimo takoj: naslednja tekma se sklicuje na id te
        tekma = tekmaRepozitorij.save(tekma);
        gradnja.tekme.add(tekma);
        return tekma;
    }

    /* Vpise obe strani tekme: znan igralec neposredno, drugace povezava na
       tekmo, iz katere pride. Tekma z obema znanima igralcema je pripravljena. */
    private static void postavi(Tekma tekma, Vir a, Vir b) {
        if (a.prijava() != null) {
            tekma.setPrijava1(a.prijava());
        } else {
            tekma.setIdIzvorTekma1(a.tekma().getId());
            tekma.setVlogaIzvora1(a.vloga());
        }
        if (b.prijava() != null) {
            tekma.setPrijava2(b.prijava());
        } else {
            tekma.setIdIzvorTekma2(b.tekma().getId());
            tekma.setVlogaIzvora2(b.vloga());
        }
        if (tekma.getPrijava1() != null && tekma.getPrijava2() != null) {
            tekma.setStatus(StatusTekme.PRIPRAVLJENA);
        }
    }

    /* Izbrise tekme zreba. Povezave se najprej pretrgajo in izperejo, sicer bi
       tuji kljuc ustavil brisanje izvorne tekme; izbris se izpere pred novimi
       vstavki, ker bi sicer UNIQUE (dogodek, faza, kolo, pozicija) trcil ob
       stare vrstice (Hibernate vstavke izvede pred izbrisi). */
    private void odstraniTekmeZreba(Zreb zreb) {
        List<Tekma> stare = tekmaRepozitorij.findByIdZreb(zreb.getId());
        if (stare.isEmpty()) {
            return;
        }
        for (Tekma tekma : stare) {
            tekma.setIdIzvorTekma1(null);
            tekma.setVlogaIzvora1(null);
            tekma.setIdIzvorTekma2(null);
            tekma.setVlogaIzvora2(null);
        }
        tekmaRepozitorij.saveAll(stare);
        tekmaRepozitorij.flush();
        tekmaRepozitorij.deleteAll(stare);
        tekmaRepozitorij.flush();
    }

    // ---------------------------------------------------------------------
    // Zreb skupin
    // ---------------------------------------------------------------------

    /* Nakljucen zreb skupin po jakostnih pasovih; igralci so ze urejeni po
       jakosti (najmocnejsi prvi). Kliče ga ZrebStoritev ob zrebu dogodka. */
    @Transactional
    public List<Tekma> izvediZrebSkupin(Dogodek dogodek, List<Prijava> poJakosti) {
        return zapisiSkupine(dogodek, nakljucneSkupine(dogodek, poJakosti));
    }

    /* Predlog skupin BREZ zapisa: isti zreb, kot bi ga izvedel dogodek. */
    @Transactional(readOnly = true)
    public SvPredlogDto predlogSkupin(Long idDogodka) {
        Dogodek dogodek = najdiDogodek(idDogodka);
        if (dogodek.getStatus() == StatusTekmovanja.ZAKLJUCEN) {
            throw new DomenskaIzjema("Dogodek je zaključen.");
        }
        List<Prijava> poJakosti = izborStoritev.vrstniRed(idDogodka);
        Map<Long, Integer> ratingi = izborStoritev.ratingi(poJakosti);
        List<List<List<Prijava>>> nivoji = nakljucneSkupine(dogodek, poJakosti);

        List<SvPredlogDto.NivoPredlogDto> izid = new ArrayList<>();
        int odMesta = 1;
        for (int i = 0; i < nivoji.size(); i++) {
            List<SvPredlogDto.SkupinaPredlogDto> skupine = new ArrayList<>();
            int velikost = 0;
            for (int j = 0; j < nivoji.get(i).size(); j++) {
                List<SvPredlogDto.ClanDto> clani = nivoji.get(i).get(j).stream()
                        .map(p -> clan(p, ratingi))
                        .toList();
                velikost += clani.size();
                skupine.add(new SvPredlogDto.SkupinaPredlogDto(oznakaSkupine(i + 1, j), clani));
            }
            izid.add(new SvPredlogDto.NivoPredlogDto(i + 1, odMesta, skupine));
            odMesta += velikost;
        }
        return new SvPredlogDto(izid);
    }

    private List<List<List<Prijava>>> nakljucneSkupine(Dogodek dogodek, List<Prijava> poJakosti) {
        List<NivoRazrez> razrez = razrez(dogodek, poJakosti.size());
        List<List<List<Prijava>>> nivoji = new ArrayList<>();
        int od = 0;
        for (NivoRazrez nivo : razrez) {
            List<Prijava> igralci = poJakosti.subList(od, od + nivo.velikost());
            od += nivo.velikost();
            nivoji.add(nosilci.vSkupine(igralci, nivo.velikostiSkupin().size()));
        }
        return nivoji;
    }

    /* Zapise skupine nivojev in njihove tekme "vsak z vsakim" ter prazne zrebe
       nivojev (njihove tekme nastanejo, ko so skupine odigrane). Struktura je
       lahko nakljucna ali rocno vpisana - od tu naprej je vse enako, zato
       rocni vpis in zreb ne moreta delovati razlicno. */
    @Transactional
    public List<Tekma> zapisiSkupine(Dogodek dogodek, List<List<List<Prijava>>> nivoji) {
        preveriStrukturo(nivoji);
        // zrebi vseh nivojev se preverijo, preden se karkoli zapise (rocni vpis ima lahko
        // drugacne velikosti kot predogled, vpisano stevilo zrebov pa mora ustrezati)
        List<List<Integer>> zrebiPoNivojih = new ArrayList<>();
        for (int n = 0; n < nivoji.size(); n++) {
            zrebiPoNivojih.add(zrebiNivoja(n + 1, nivoji.get(n).stream().map(List::size).toList(),
                    dogodek.steviloZrebovSeznam(), dogodek.velikostZrebovSeznam()));
        }

        List<Tekma> vse = new ArrayList<>();
        List<Prijava> vsiClani = new ArrayList<>();
        int globalnaPozicija = 1;
        int odMesta = 1;
        for (int n = 0; n < nivoji.size(); n++) {
            int nivo = n + 1;
            List<List<Prijava>> skupineNivoja = nivoji.get(n);

            List<Skupina> skupine = new ArrayList<>();
            for (int j = 0; j < skupineNivoja.size(); j++) {
                Skupina skupina = new Skupina(dogodek, oznakaSkupine(nivo, j));
                skupina.setNivo(nivo);
                skupine.add(skupina);
            }
            skupinaRepozitorij.saveAll(skupine);

            int velikostNivoja = 0;
            for (int j = 0; j < skupineNivoja.size(); j++) {
                // clani po jakosti: 1 je nosilec skupine (tekmeSkupine steje od tu)
                List<Prijava> clani = new ArrayList<>(skupineNivoja.get(j));
                clani.sort(Comparator.comparing(
                        (Prijava p) -> p.getStNosilca() == null ? Integer.MAX_VALUE : p.getStNosilca()));
                Skupina skupina = skupine.get(j);
                for (Prijava clan : clani) {
                    clan.setIdSkupina(skupina.getId());
                    clan.setMestoVSkupini(null);
                    clan.setKoncnoMesto(null);
                    vsiClani.add(clan);
                }
                for (Tekma tekma : tekmeSkupine(dogodek, clani, skupina.getId())) {
                    tekma.setPozicija(globalnaPozicija++);
                    vse.add(tekma);
                }
                velikostNivoja += clani.size();
            }

            List<Integer> zrebi = zrebiPoNivojih.get(n);
            int mesto = odMesta;
            for (int d = 0; d < zrebi.size(); d++) {
                zrebRepozitorij.save(new Zreb(dogodek, nivo, d, mesto, zrebi.get(d)));
                mesto += zrebi.get(d);
            }
            odMesta += velikostNivoja;
        }
        tekmaRepozitorij.saveAll(vse);
        prijavaRepozitorij.saveAll(vsiClani);
        return vse;
    }

    /* Vrstni red tekem v skupini stirih (odlocitev lastnika 7. 11. 2026), po
       mestih v skupini (1 = nosilec): kolo 1: 1-4, 2-3; kolo 2: 4-3, 1-2;
       kolo 3: 2-4, 3-1. Prvi v paru je prijava1 (na listku levo). */
    private static final int[][][] VRSTNI_RED_STIRIH = {
            {{1, 4}, {2, 3}},
            {{4, 3}, {1, 2}},
            {{2, 4}, {3, 1}},
    };

    /* Tekme "vsak z vsakim" v skupini; clani so urejeni po jakosti. Skupina
       stirih igra po vrstnem redu SV regije, druge velikosti po krozni metodi
       (skupina treh: 2-3, 1-3, 1-2). */
    static List<Tekma> tekmeSkupine(Dogodek dogodek, List<Prijava> clani, Long idSkupina) {
        if (clani.size() != 4) {
            return ZrebStoritev.kroznePare(dogodek, clani, idSkupina);
        }
        List<Tekma> tekme = new ArrayList<>();
        for (int kolo = 1; kolo <= VRSTNI_RED_STIRIH.length; kolo++) {
            int[][] pari = VRSTNI_RED_STIRIH[kolo - 1];
            for (int i = 0; i < pari.length; i++) {
                Tekma tekma = new Tekma();
                tekma.setDogodek(dogodek);
                tekma.setFaza(FazaTekme.SKUPINA);
                tekma.setKolo(kolo);
                tekma.setPozicija(i + 1);
                tekma.setSteviloNizov(dogodek.getPrivzetoSteviloNizov());
                tekma.setIdSkupina(idSkupina);
                tekma.setPrijava1(clani.get(pari[i][0] - 1));
                tekma.setPrijava2(clani.get(pari[i][1] - 1));
                tekma.setStatus(StatusTekme.PRIPRAVLJENA);
                tekme.add(tekma);
            }
        }
        return tekme;
    }

    /* Vsak nivo ima skupino, vsaka skupina vsaj dva igralca, nihce ni dvakrat. */
    private static void preveriStrukturo(List<List<List<Prijava>>> nivoji) {
        if (nivoji.isEmpty()) {
            throw new NeveljavenVnosIzjema("Vpisana mora biti vsaj ena Skupina.");
        }
        if (nivoji.size() > NAJVEC_NIVOJEV) {
            throw new NeveljavenVnosIzjema("Skupin 1, 2 … je lahko največ " + NAJVEC_NIVOJEV + ".");
        }
        Set<Long> videni = new HashSet<>();
        for (int n = 0; n < nivoji.size(); n++) {
            List<List<Prijava>> skupine = nivoji.get(n);
            if (skupine.isEmpty()) {
                throw new NeveljavenVnosIzjema("Skupina " + (n + 1) + " nima nobene skupine.");
            }
            if (skupine.size() > NAJVEC_SKUPIN) {
                throw new NeveljavenVnosIzjema("Skupina " + (n + 1) + " ima več kot " + NAJVEC_SKUPIN
                        + " skupin.");
            }
            for (int j = 0; j < skupine.size(); j++) {
                if (skupine.get(j).size() < 2) {
                    throw new NeveljavenVnosIzjema("Skupina " + oznakaSkupine(n + 1, j)
                            + " ima manj kot 2 igralca.");
                }
                for (Prijava p : skupine.get(j)) {
                    if (!videni.add(p.getId())) {
                        throw new NeveljavenVnosIzjema("Igralec " + imeIgralca(p)
                                + " nastopa v več skupinah.");
                    }
                }
            }
        }
    }

    /* Oznaka skupine je enolicna na dogodku: "1A" je skupina A prvega nivoja. */
    static String oznakaSkupine(int nivo, int indeks) {
        return nivo + String.valueOf((char) ('A' + indeks));
    }

    /* Razveljavi zreb skupin (in z njim vse zrebe): dogodek se vrne v pripravo.
       Mogoce samo, dokler nobena tekma ni zacela - sicer bi se z zrebom izbrisali
       rezultati in rating. */
    @Transactional
    public void razveljaviZrebSkupin(Long idDogodka) {
        lastnistvo.preveriTurnirPoDogodku(idDogodka);
        Dogodek dogodek = najdiDogodek(idDogodka);
        if (dogodek.getStatus() != StatusTekmovanja.V_TEKU) {
            throw new DomenskaIzjema("Žreb je mogoče razveljaviti samo, dokler dogodek teče.");
        }
        List<Tekma> tekme = tekmaRepozitorij.najdiZaDogodek(idDogodka);
        if (tekme.stream().anyMatch(t -> t.getStatus() == StatusTekme.KONCANA
                || t.getStatus() == StatusTekme.V_IGRI)) {
            throw new DomenskaIzjema("Žreba ni mogoče razveljaviti: nekatere tekme so že začete ali odigrane.");
        }

        for (Tekma tekma : tekme) {
            tekma.setIdIzvorTekma1(null);
            tekma.setVlogaIzvora1(null);
            tekma.setIdIzvorTekma2(null);
            tekma.setVlogaIzvora2(null);
        }
        tekmaRepozitorij.saveAll(tekme);
        tekmaRepozitorij.flush();
        tekmaRepozitorij.deleteAll(tekme);
        tekmaRepozitorij.flush();

        List<Prijava> prijave = prijavaRepozitorij.najdiZaDogodek(idDogodka);
        for (Prijava prijava : prijave) {
            prijava.setIdSkupina(null);
            prijava.setMestoVSkupini(null);
            prijava.setKoncnoMesto(null);
        }
        prijavaRepozitorij.saveAll(prijave);
        prijavaRepozitorij.flush();

        zrebRepozitorij.deleteAll(zrebRepozitorij.findByDogodekIdOrderByNivoAscIndeksAsc(idDogodka));
        skupinaRepozitorij.deleteAll(skupinaRepozitorij.findByDogodekIdOrderByOznakaAsc(idDogodka));
        zrebRepozitorij.flush();

        dogodek.setStatus(StatusTekmovanja.PRIPRAVA);
        // turnir se vrne v pripravo samo, ce noben njegov dogodek ni zacel
        var turnir = dogodek.getTurnir();
        boolean kakDrugZacel = dogodekRepozitorij.findByTurnirIdOrderByIdAsc(turnir.getId()).stream()
                .anyMatch(d -> d.getStatus() != StatusTekmovanja.PRIPRAVA);
        if (!kakDrugZacel && turnir.getStatus() == StatusTekmovanja.V_TEKU) {
            turnir.setStatus(StatusTekmovanja.PRIPRAVA);
        }
    }

    // ---------------------------------------------------------------------
    // Nastavitve
    // ---------------------------------------------------------------------

    /* Nastavitve razreza; samo v pripravi (po zrebu se razrez ne spreminja). */
    @Transactional
    public Dogodek nastavi(Long idDogodka, SvVnos.Nastavitve vnos) {
        lastnistvo.preveriTurnirPoDogodku(idDogodka);
        Dogodek dogodek = najdiDogodek(idDogodka);
        if (dogodek.getStatus() != StatusTekmovanja.PRIPRAVA) {
            throw new DomenskaIzjema("Razrez je mogoče spreminjati samo, dokler je dogodek v pripravi.");
        }
        Integer skupin = vnos.steviloSkupin();
        Integer velikost = vnos.velikostSkupine();
        if (skupin != null && (skupin < 1 || skupin > NAJVEC_SKUPIN)) {
            throw new NeveljavenVnosIzjema("Skupin v polni Skupini mora biti med 1 in " + NAJVEC_SKUPIN + ".");
        }
        if (velikost != null && (velikost < 2 || velikost > 24)) {
            throw new NeveljavenVnosIzjema("Velikost skupine mora biti med 2 in 24.");
        }
        List<Integer> velikosti = vnos.velikostiNivojev() == null ? List.of() : vnos.velikostiNivojev();
        if (velikosti.size() > NAJVEC_NIVOJEV) {
            throw new NeveljavenVnosIzjema("Skupin 1, 2 … je lahko največ " + NAJVEC_NIVOJEV + ".");
        }
        for (Integer v : velikosti) {
            if (v == null || v < 2) {
                throw new NeveljavenVnosIzjema("Vsaka Skupina mora imeti vsaj 2 igralca.");
            }
        }
        Integer nivojev = vnos.steviloNivojev();
        if (nivojev != null && (nivojev < 1 || nivojev > NAJVEC_NIVOJEV)) {
            throw new NeveljavenVnosIzjema("Število Skupin mora biti med 1 in " + NAJVEC_NIVOJEV + ".");
        }
        List<Integer> steviloZrebov = poNivojih(vnos.steviloZrebov());
        steviloZrebov.forEach(SvRegijaStoritev::preveriSteviloZrebov);
        List<Integer> velikostZrebov = poNivojih(vnos.velikostZrebov());
        velikostZrebov.forEach(SvRegijaStoritev::preveriVelikostZreba);

        dogodek.setSteviloSkupin(skupin);
        dogodek.setVelikostSkupine(velikost);
        dogodek.setSteviloZrebov(csvPoNivojih(steviloZrebov));
        dogodek.setVelikostZrebov(csvPoNivojih(velikostZrebov));
        if (velikosti.isEmpty()) {
            dogodek.setVelikostiNivojev(null);
            dogodek.setSteviloNivojev(nivojev);
        } else {
            // rocne velikosti so ze samo stevilo nivojev
            dogodek.setVelikostiNivojev(velikosti.stream().map(String::valueOf)
                    .collect(Collectors.joining(",")));
            dogodek.setSteviloNivojev(null);
        }
        return dogodekRepozitorij.save(dogodek);
    }

    private static void preveriSteviloZrebov(Integer stevilo) {
        if (stevilo != null && (stevilo < 1 || stevilo > 16)) {
            throw new NeveljavenVnosIzjema("Število žrebov v Skupini mora biti med 1 in 16.");
        }
    }

    private static void preveriVelikostZreba(Integer velikost) {
        if (velikost != null && !VELIKOSTI_ZREBA.contains(velikost)) {
            throw new NeveljavenVnosIzjema("Žreb se lahko začne v šestnajstini finala (32),"
                    + " osmini (16), četrtfinalu (8), polfinalu (4) ali finalu (2).");
        }
    }

    /* Raven in stevilo izlocilnih zrebov ene Skupine PO zrebu skupin (odlocitev
       lastnika 8. 11. 2026). Organizator pogosto sele med igranjem skupin vidi,
       da zrebe potrebuje drugace, razveljavitev zreba pa takrat ne gre vec (tudi
       odstop igralca zapise tekme brez boja) in bi skupine izzrebala na novo.
       Zato se zamenjajo SAMO zrebi te Skupine: skupine, tekme in izidi ostanejo.
       Mogoce je, dokler se nobena tekma zrebov te Skupine ni zacela - zgrajen,
       a nezacet zreb se podre in caka na organizatorja kot ob koncu skupin.
       Pravilo stevila je isto kot pred zrebom (zrebiNivoja: ce zrebi ne
       sprejmejo vseh igralcev ali bi kateri ostal prazen, sistem ne dovoli). */
    @Transactional
    public void nastaviZrebeNivoja(Long idDogodka, int nivo, SvVnos.ZrebiSkupine vnos) {
        lastnistvo.preveriTurnirPoDogodku(idDogodka);
        Dogodek dogodek = najdiDogodek(idDogodka);
        if (dogodek.getStatus() == StatusTekmovanja.PRIPRAVA) {
            throw new DomenskaIzjema("Skupine še niso izžrebane: žrebe nastaviš v nastavitvah razreza.");
        }
        if (dogodek.getStatus() != StatusTekmovanja.V_TEKU) {
            throw new DomenskaIzjema("Dogodek je zaključen, žrebov ni več mogoče spremeniti.");
        }
        preveriSteviloZrebov(vnos.steviloZrebov());
        preveriVelikostZreba(vnos.velikostZreba());

        List<Skupina> skupine = skupinaRepozitorij.findByDogodekIdOrderByOznakaAsc(idDogodka);
        List<Skupina> skupineNivoja = predtekmovalnePoNivojih(skupine).get(nivo);
        if (skupineNivoja == null) {
            throw new NiNajdenoIzjema("Skupina " + nivo + " na tem dogodku ne obstaja.");
        }
        List<Prijava> prijave = prijavaRepozitorij.najdiZaDogodek(idDogodka);
        // velikosti skupin stejejo vse clane, tudi odstopljene: v zaporedju zrebov
        // ostanejo (zadnji v skupini), isto kot ob zrebu skupin
        List<Integer> velikostiSkupin = skupineNivoja.stream()
                .map(s -> (int) prijave.stream().filter(p -> s.getId().equals(p.getIdSkupina())).count())
                .toList();

        List<Zreb> stari = zrebRepozitorij.findByDogodekIdAndNivoOrderByIndeksAsc(idDogodka, nivo);
        boolean zacet = stari.stream()
                .flatMap(z -> tekmaRepozitorij.findByIdZreb(z.getId()).stream())
                .anyMatch(t -> t.getStatus() == StatusTekme.KONCANA || t.getStatus() == StatusTekme.V_IGRI);
        if (zacet) {
            throw new DomenskaIzjema("Žrebov Skupine " + nivo + " ni več mogoče spremeniti:"
                    + " tekme izločilnih žrebov so že začete.");
        }

        List<Integer> steviloZrebov = naNivoju(dogodek.steviloZrebovSeznam(), nivo, vnos.steviloZrebov());
        List<Integer> velikostZrebov = naNivoju(dogodek.velikostZrebovSeznam(), nivo, vnos.velikostZreba());
        List<Integer> novi = zrebiNivoja(nivo, velikostiSkupin, steviloZrebov, velikostZrebov);

        dogodek.setSteviloZrebov(csvPoNivojih(steviloZrebov));
        dogodek.setVelikostZrebov(csvPoNivojih(velikostZrebov));
        dogodekRepozitorij.save(dogodek);

        if (novi.equals(stari.stream().map(Zreb::getStUdelezencev).toList())) {
            return; // isti zrebi: zgrajenih ne podiramo
        }
        for (Zreb zreb : stari) {
            odstraniTekmeZreba(zreb);
        }
        // koncno mesto ima ob gradnji samo edini udelezenec zreba; nivo pa se ni odigran
        Set<Long> idjiSkupin = skupineNivoja.stream().map(Skupina::getId).collect(Collectors.toSet());
        List<Prijava> claniNivoja = prijave.stream()
                .filter(p -> p.getIdSkupina() != null && idjiSkupin.contains(p.getIdSkupina()))
                .toList();
        claniNivoja.forEach(p -> p.setKoncnoMesto(null));
        prijavaRepozitorij.saveAll(claniNivoja);
        // UNIQUE (dogodek, nivo, indeks): izbris mora v bazo pred novimi vrsticami
        zrebRepozitorij.deleteAll(stari);
        zrebRepozitorij.flush();

        int mesto = zacetkiNivojev(skupine, prijave).get(nivo);
        for (int d = 0; d < novi.size(); d++) {
            zrebRepozitorij.save(new Zreb(dogodek, nivo, d, mesto, novi.get(d)));
            mesto += novi.get(d);
        }
    }

    /* Zgrajene zrebe Skupine podre, tudi z odigranimi tekmami: popravljen izid v
       skupini je spremenil vrstni red, iz katerega so nastali, organizator pa je
       izbral, naj gredo (PoslediceIzidaStoritev). Vrstice zreba ostanejo, velikosti so
       iz zreba skupin - zrebi spet cakajo nanj kot ob koncu skupin. Obracune ratinga je
       klicalec ze pobrisal. */
    @Transactional
    public void podriZrebeNivoja(Long idDogodka, int nivo) {
        for (Zreb zreb : zrebRepozitorij.findByDogodekIdAndNivoOrderByIndeksAsc(idDogodka, nivo)) {
            odstraniTekmeZreba(zreb);
            zreb.setZgrajen(false);
            zreb.setRocni(false);
            zreb.setRazpored(null);
        }
        List<Skupina> skupineNivoja = predtekmovalnePoNivojih(
                skupinaRepozitorij.findByDogodekIdOrderByOznakaAsc(idDogodka)).getOrDefault(nivo, List.of());
        Set<Long> idjiSkupin = skupineNivoja.stream().map(Skupina::getId).collect(Collectors.toSet());
        // koncno mesto ima zdaj samo, kdor je bil edini v zrebu; ta ga dobi znova ob gradnji
        prijavaRepozitorij.najdiZaDogodek(idDogodka).stream()
                .filter(p -> p.getIdSkupina() != null && idjiSkupin.contains(p.getIdSkupina()))
                .forEach(p -> p.setKoncnoMesto(null));
    }

    /* Seznam po nivojih z novo vrednostjo na mestu nivoja (od 1), brez praznega repa. */
    private static List<Integer> naNivoju(List<Integer> seznam, int nivo, Integer vrednost) {
        List<Integer> nov = seznam == null ? new ArrayList<>() : new ArrayList<>(seznam);
        while (nov.size() < nivo) {
            nov.add(null);
        }
        nov.set(nivo - 1, vrednost);
        return poNivojih(nov);
    }

    /* Vnos po nivojih brez praznega repa ([8, null, null] -> [8]); najvec 9 nivojev. */
    private static List<Integer> poNivojih(List<Integer> vnos) {
        List<Integer> seznam = vnos == null ? new ArrayList<>() : new ArrayList<>(vnos);
        while (!seznam.isEmpty() && seznam.get(seznam.size() - 1) == null) {
            seznam.remove(seznam.size() - 1);
        }
        if (seznam.size() > NAJVEC_NIVOJEV) {
            throw new NeveljavenVnosIzjema("Žrebe je mogoče nastaviti za največ " + NAJVEC_NIVOJEV
                    + " Skupin.");
        }
        return seznam;
    }

    /* [8, null, 16] -> "8,,16"; prazen seznam -> null. */
    private static String csvPoNivojih(List<Integer> seznam) {
        if (seznam.isEmpty()) {
            return null;
        }
        return seznam.stream().map(v -> v == null ? "" : String.valueOf(v)).collect(Collectors.joining(","));
    }

    // ---------------------------------------------------------------------
    // Zrebi nivojev
    // ---------------------------------------------------------------------

    /* Zrebi nivojev ne nastanejo sami, ko so odigrane skupine nivoja: organizator izbere
       racunalniski zreb (znovaZrebaj) ali rocno razporeditev (nastaviMesta). Dokler se
       nobena tekma zreba ni zacela, ga lahko popravi. Dogodek se ne zakljuci, dokler
       zrebi niso zgrajeni (vsiZrebiZgrajeni).

       Predtekmovalne skupine po nivojih, urejene po oznaki (A, B, C ...). */
    private static Map<Integer, List<Skupina>> predtekmovalnePoNivojih(List<Skupina> skupine) {
        Map<Integer, List<Skupina>> poNivojih = new TreeMap<>();
        for (Skupina skupina : skupine) {
            if (skupina.getStopnja() == 1) {
                poNivojih.computeIfAbsent(skupina.getNivo(), k -> new ArrayList<>()).add(skupina);
            }
        }
        for (List<Skupina> seznam : poNivojih.values()) {
            seznam.sort(Comparator.comparing(Skupina::getOznaka));
        }
        return poNivojih;
    }

    /* Razvrstitev v vsaki skupini nivoja (skupine po vrsti A, B, ...): igralci od
       prvega mesta navzdol. */
    private List<List<Prijava>> uvrstitveNivoja(List<Skupina> skupine, List<Prijava> prijave,
                                                List<Tekma> tekme) {
        Map<Long, Prijava> poId = new HashMap<>();
        for (Prijava p : prijave) {
            poId.put(p.getId(), p);
        }
        List<List<Prijava>> uvrstitve = new ArrayList<>();
        for (Skupina skupina : skupine) {
            List<Tekma> tekmeSkupine = tekme.stream()
                    .filter(t -> t.getFaza() == FazaTekme.SKUPINA && skupina.getId().equals(t.getIdSkupina()))
                    .toList();
            List<Prijava> poVrsti = new ArrayList<>();
            for (VrsticaLestviceDto vrstica : razvrstitevStoritev.lestvica(
                    SkupineStoritev.clani(skupina, prijave, tekme), tekmeSkupine)) {
                poVrsti.add(poId.get(vrstica.idPrijave()));
            }
            uvrstitve.add(poVrsti);
        }
        return uvrstitve;
    }

    /* Udelezenec zreba: igralec in indeks njegove skupine (0 = A). */
    private record Udelezenec(Prijava prijava, int skupina) {}

    /* Igralci nivoja v zaporedju, v katerem gredo v zrebe: po mestu v skupini,
       znotraj mesta po crki skupine (A1, B1, ..., A2, B2 ...). */
    private static List<Udelezenec> zaporedjeNivoja(List<List<Prijava>> uvrstitve) {
        List<Udelezenec> zaporedje = new ArrayList<>();
        int najvec = uvrstitve.stream().mapToInt(List::size).max().orElse(0);
        for (int mesto = 0; mesto < najvec; mesto++) {
            for (int s = 0; s < uvrstitve.size(); s++) {
                if (uvrstitve.get(s).size() > mesto) {
                    zaporedje.add(new Udelezenec(uvrstitve.get(s).get(mesto), s));
                }
            }
        }
        return zaporedje;
    }

    /* Udelezenci zreba: zaporedje nivoja od mesta, kjer se konca prejsnji zreb,
       toliko, kolikor jih zreb sprejme. Velikosti zrebov so zapisane ob zrebu
       skupin, zato veljajo tudi za dogodke, izzrebane po prejsnjem pravilu
       ("1.-2. v glavni, 3.-4. v tolazilni"): njegovi zrebi so isti odseki istega
       zaporedja. */
    private List<Udelezenec> udelezenci(Zreb zreb, List<List<Prijava>> uvrstitve) {
        int od = 0;
        for (Zreb drug : zrebRepozitorij.findByDogodekIdAndNivoOrderByIndeksAsc(
                zreb.getDogodek().getId(), zreb.getNivo())) {
            if (drug.getIndeks() < zreb.getIndeks()) {
                od += drug.getStUdelezencev();
            }
        }
        List<Udelezenec> zaporedje = zaporedjeNivoja(uvrstitve);
        int doIzkljucno = Math.min(od + zreb.getStUdelezencev(), zaporedje.size());
        return new ArrayList<>(zaporedje.subList(Math.min(od, doIzkljucno), doIzkljucno));
    }

    /* Mesta v mrezi po pravilu zreba (nosilci po zaporedju, locitev skupin in klubov). */
    private Prijava[] nakljucnaMesta(Zreb zreb, List<List<Prijava>> uvrstitve) {
        List<Udelezenec> udelezenci = udelezenci(zreb, uvrstitve);
        return mestaVMrezi(nosilci.vMrezoPoVrsti(
                udelezenci.stream().map(Udelezenec::prijava).toList(),
                udelezenci.stream().map(Udelezenec::skupina).toList()));
    }

    /* Predlog razporeditve v mrezi BREZ zapisa (isti zreb, kot bi ga naredil
       sistem), da ga organizator popravi in vrne kot rocni vpis. */
    @Transactional(readOnly = true)
    public SvZrebPredlogDto predlogZreba(Long idZreba) {
        Zreb zreb = najdiZreb(idZreba);
        lastnistvo.preveriTurnirPoDogodku(zreb.getDogodek().getId());
        preveriUredljiv(zreb);
        PodatkiNivoja podatki = podatkiNivoja(zreb);
        Prijava[] mesta = nakljucnaMesta(zreb, podatki.uvrstitve());
        Map<Long, Integer> ratingi = izborStoritev.ratingi(podatki.prijave());
        List<SvPredlogDto.ClanDto> clani = new ArrayList<>();
        for (Prijava mesto : mesta) {
            clani.add(mesto == null ? null : clan(mesto, ratingi));
        }
        return new SvZrebPredlogDto(mesta.length, clani);
    }

    /* Rocna razporeditev mest v mrezi: id prijav po mestih od vrha navzdol, null
       za prosto mesto. Udelezenci so tisti, ki so prisli iz skupin - razporeditev
       jih samo premesti, nikogar ne doda ali odvzame. */
    @Transactional
    public void nastaviMesta(Long idZreba, List<Long> idjiPoMestih) {
        Zreb zreb = najdiZreb(idZreba);
        lastnistvo.preveriTurnirPoDogodku(zreb.getDogodek().getId());
        preveriUredljiv(zreb);
        PodatkiNivoja podatki = podatkiNivoja(zreb);

        Map<Long, Prijava> poId = new HashMap<>();
        for (Udelezenec u : udelezenci(zreb, podatki.uvrstitve())) {
            poId.put(u.prijava().getId(), u.prijava());
        }
        int velikost = NosilciStoritev.najblizjaPotencaDve(poId.size());
        if (idjiPoMestih == null || idjiPoMestih.size() != velikost) {
            throw new NeveljavenVnosIzjema("Mrežo sestavlja " + velikost + " mest, prejetih je "
                    + (idjiPoMestih == null ? 0 : idjiPoMestih.size()) + ".");
        }
        Prijava[] mesta = new Prijava[velikost];
        Set<Long> videni = new HashSet<>();
        for (int i = 0; i < velikost; i++) {
            Long id = idjiPoMestih.get(i);
            if (id == null) {
                continue;
            }
            Prijava prijava = poId.get(id);
            if (prijava == null) {
                throw new NeveljavenVnosIzjema("Prijava " + id + " ne sodi v ta žreb.");
            }
            if (!videni.add(id)) {
                throw new NeveljavenVnosIzjema("Igralec " + imeIgralca(prijava) + " je v mreži dvakrat.");
            }
            mesta[i] = prijava;
        }
        if (videni.size() != poId.size()) {
            throw new NeveljavenVnosIzjema("V mreži mora biti vseh " + poId.size()
                    + " udeležencev žreba (vpisanih: " + videni.size() + ").");
        }
        zgradiZreb(zreb.getDogodek(), zreb, mesta, true);
    }

    /* Ponovno nakljucno razporedi zreb (dokler se ni zacet). */
    @Transactional
    public void znovaZrebaj(Long idZreba) {
        Zreb zreb = najdiZreb(idZreba);
        lastnistvo.preveriTurnirPoDogodku(zreb.getDogodek().getId());
        preveriUredljiv(zreb);
        PodatkiNivoja podatki = podatkiNivoja(zreb);
        zgradiZreb(zreb.getDogodek(), zreb, nakljucnaMesta(zreb, podatki.uvrstitve()), false);
    }

    private record PodatkiNivoja(List<Prijava> prijave, List<List<Prijava>> uvrstitve) {}

    private PodatkiNivoja podatkiNivoja(Zreb zreb) {
        Long idDogodka = zreb.getDogodek().getId();
        List<Skupina> skupine = predtekmovalnePoNivojih(
                skupinaRepozitorij.findByDogodekIdOrderByOznakaAsc(idDogodka)).get(zreb.getNivo());
        if (skupine == null) {
            throw new NiNajdenoIzjema("Skupina " + zreb.getNivo() + " nima skupin.");
        }
        List<Prijava> prijave = prijavaRepozitorij.najdiZaDogodek(idDogodka);
        List<Tekma> tekme = tekmaRepozitorij.najdiZaDogodek(idDogodka);
        return new PodatkiNivoja(prijave, uvrstitveNivoja(skupine, prijave, tekme));
    }

    /* Zreb se sme vpisati ali popraviti, ko so odigrane skupine nivoja (tudi ce se se ni
       zgradil), dokler se nobena njegova tekma ni zacela: sicer bi popravek unicil ze
       odigrane rezultate. */
    private void preveriUredljiv(Zreb zreb) {
        if (zreb.getDogodek().getStatus() != StatusTekmovanja.V_TEKU) {
            throw new DomenskaIzjema("Žreb je mogoče urejati samo, dokler dogodek teče.");
        }
        if (!zreb.isZgrajen() && !skupineNivojaKoncane(zreb)) {
            throw new DomenskaIzjema("Žreb še ni pripravljen: počakaj, da so odigrane vse tekme v"
                    + " skupinah Skupine " + zreb.getNivo() + ".");
        }
        boolean zacet = tekmaRepozitorij.findByIdZreb(zreb.getId()).stream()
                .anyMatch(t -> t.getStatus() == StatusTekme.KONCANA || t.getStatus() == StatusTekme.V_IGRI);
        if (zacet) {
            throw new DomenskaIzjema("Žreba ni mogoče spremeniti: nekatere njegove tekme so že začete.");
        }
    }

    /* Ali so vse skupinske tekme danih skupin (nivoja) odigrane. Takrat se zreb nivoja sme
       zgraditi. */
    private static boolean skupineKoncane(List<Skupina> skupineNivoja, List<Tekma> tekme) {
        Set<Long> idjiSkupin = skupineNivoja.stream().map(Skupina::getId).collect(Collectors.toSet());
        return tekme.stream()
                .filter(t -> t.getFaza() == FazaTekme.SKUPINA && idjiSkupin.contains(t.getIdSkupina()))
                .allMatch(t -> t.getStatus() == StatusTekme.KONCANA);
    }

    private boolean skupineNivojaKoncane(Zreb zreb) {
        Long idDogodka = zreb.getDogodek().getId();
        List<Skupina> skupine = predtekmovalnePoNivojih(
                        skupinaRepozitorij.findByDogodekIdOrderByOznakaAsc(idDogodka))
                .getOrDefault(zreb.getNivo(), List.of());
        return skupineKoncane(skupine, tekmaRepozitorij.najdiZaDogodek(idDogodka));
    }

    // ---------------------------------------------------------------------
    // Zakljucek
    // ---------------------------------------------------------------------

    /* Ali imajo vsi zrebi dogodka zgrajene tekme. Dogodek se brez tega ne sme
       zakljuciti, ceprav so vse obstojece tekme odigrane (skupine vseh nivojev
       so, zrebov pa se ni). */
    @Transactional(readOnly = true)
    public boolean vsiZrebiZgrajeni(Long idDogodka) {
        return zrebRepozitorij.countByDogodekIdAndZgrajenFalse(idDogodka) == 0;
    }

    /* Koncna mesta: iz tekem zrebov (vsaka nosi mesto zmagovalca in porazenca)
       in iz razvrstitve v skupini za nivoje z eno samo skupino. */
    @Transactional
    public void dodeliKoncnaMesta(Long idDogodka) {
        List<Tekma> tekme = tekmaRepozitorij.najdiZaDogodek(idDogodka);
        for (Tekma tekma : tekme) {
            if (tekma.getStatus() != StatusTekme.KONCANA || tekma.getZmagovalec() == null) {
                continue;
            }
            if (tekma.getMestoZmagovalca() != null) {
                tekma.getZmagovalec().setKoncnoMesto(tekma.getMestoZmagovalca());
            }
            Prijava porazenec = tekma.porazenec();
            if (tekma.getMestoPorazenca() != null && porazenec != null) {
                porazenec.setKoncnoMesto(tekma.getMestoPorazenca());
            }
        }

        List<Skupina> skupine = skupinaRepozitorij.findByDogodekIdOrderByOznakaAsc(idDogodka);
        List<Prijava> prijave = prijavaRepozitorij.najdiZaDogodek(idDogodka);
        Set<Integer> nivojiZZrebi = zrebRepozitorij.findByDogodekIdOrderByNivoAscIndeksAsc(idDogodka).stream()
                .map(Zreb::getNivo).collect(Collectors.toSet());
        Map<Integer, Integer> zacetki = zacetkiNivojev(skupine, prijave);
        for (Map.Entry<Integer, List<Skupina>> nivo : predtekmovalnePoNivojih(skupine).entrySet()) {
            if (nivojiZZrebi.contains(nivo.getKey())) {
                continue;
            }
            Set<Long> idjiSkupin = nivo.getValue().stream().map(Skupina::getId).collect(Collectors.toSet());
            for (Prijava prijava : prijave) {
                if (prijava.getIdSkupina() != null && idjiSkupin.contains(prijava.getIdSkupina())
                        && prijava.getMestoVSkupini() != null) {
                    prijava.setKoncnoMesto(zacetki.get(nivo.getKey()) + prijava.getMestoVSkupini() - 1);
                }
            }
        }
        prijavaRepozitorij.saveAll(prijave);
        tekmaRepozitorij.saveAll(tekme);
    }

    /* Prvo mesto vsakega nivoja: nivoji si sledijo od najmocnejsega. */
    private static Map<Integer, Integer> zacetkiNivojev(List<Skupina> skupine, List<Prijava> prijave) {
        Map<Long, Integer> nivoPoSkupini = new HashMap<>();
        for (Skupina skupina : skupine) {
            nivoPoSkupini.put(skupina.getId(), skupina.getNivo());
        }
        Map<Integer, Integer> velikosti = new TreeMap<>();
        for (Prijava prijava : prijave) {
            Integer nivo = prijava.getIdSkupina() == null ? null : nivoPoSkupini.get(prijava.getIdSkupina());
            if (nivo != null) {
                velikosti.merge(nivo, 1, Integer::sum);
            }
        }
        Map<Integer, Integer> zacetki = new HashMap<>();
        int mesto = 1;
        for (Map.Entry<Integer, Integer> nivo : velikosti.entrySet()) {
            zacetki.put(nivo.getKey(), mesto);
            mesto += nivo.getValue();
        }
        return zacetki;
    }

    // ---------------------------------------------------------------------
    // Pregled za vmesnik
    // ---------------------------------------------------------------------

    /* Nivoji, skupine in zrebi dogodka za izpis. Pred zrebom skupin je to
       predogled razreza, po njem stanje iz baze. */
    @Transactional(readOnly = true)
    public SvRegijaDto pregled(Dogodek dogodek, List<Prijava> prijave, List<Skupina> skupine,
                               List<Tekma> tekme) {
        Map<Integer, List<Skupina>> poNivojih = predtekmovalnePoNivojih(skupine);
        boolean zrebane = !poNivojih.isEmpty();
        boolean zacete = tekme.stream().anyMatch(
                t -> t.getStatus() == StatusTekme.KONCANA || t.getStatus() == StatusTekme.V_IGRI);
        int skupin = skupinNaNivo(dogodek);
        int velikost = velikostSkupine(dogodek);
        List<Integer> rocne = dogodek.velikostiNivojevSeznam();

        List<Integer> vpisanoZrebov = dogodek.steviloZrebovSeznam();
        List<Integer> velikostZrebov = dogodek.velikostZrebovSeznam();

        List<SvRegijaDto.NivoDto> nivoji = new ArrayList<>();
        String zadrzek = null;
        if (!zrebane) {
            int prijavljenih = (int) prijave.stream()
                    .filter(p -> p.getStatus() == Prijava.StatusPrijave.PRIJAVLJEN).count();
            List<NivoRazrez> razrez = List.of();
            try {
                razrez = razrez(dogodek, prijavljenih);
            } catch (DomenskaIzjema e) {
                zadrzek = e.getMessage();
                // vpisano stevilo zrebov, ki ne ustreza, ne skrije Skupin - organizator mora
                // videti, koliko igralcev ima vsaka, da ga lahko popravi
                try {
                    razrez = razrezZaPrikaz(dogodek, prijavljenih);
                } catch (DomenskaIzjema drugaNapaka) {
                    razrez = List.of();
                }
            }
            for (NivoRazrez nivo : razrez) {
                List<List<String>> viri = viriZrebov(nivo.velikostiSkupin(), nivo.velikostiZrebov());
                List<SvRegijaDto.ZrebDto> zrebi = new ArrayList<>();
                int mesto = nivo.odMesta();
                for (int d = 0; d < nivo.velikostiZrebov().size(); d++) {
                    int st = nivo.velikostiZrebov().get(d);
                    zrebi.add(zrebDto(null, d, mesto, st, false, false, false, List.of(), viri.get(d)));
                    mesto += st;
                }
                nivoji.add(new SvRegijaDto.NivoDto(nivo.nivo(), nivo.velikost(), nivo.odMesta(),
                        nivo.doMesta(), nivo.velikostiSkupin(),
                        velikostZrebaNivoja(velikostZrebov, nivo.nivo()),
                        vpisanoZrebov(vpisanoZrebov, nivo.nivo()), zrebi, false));
            }
        } else {
            Map<Long, Long> clanov = prijave.stream()
                    .filter(p -> p.getIdSkupina() != null)
                    .collect(Collectors.groupingBy(Prijava::getIdSkupina, Collectors.counting()));
            Map<Long, List<Tekma>> tekmePoZrebu = tekme.stream()
                    .filter(t -> t.getIdZreb() != null)
                    .collect(Collectors.groupingBy(Tekma::getIdZreb));
            boolean vTeku = dogodek.getStatus() == StatusTekmovanja.V_TEKU;
            int odMesta = 1;
            for (Map.Entry<Integer, List<Skupina>> nivo : poNivojih.entrySet()) {
                List<Integer> velikosti = nivo.getValue().stream()
                        .map(s -> clanov.getOrDefault(s.getId(), 0L).intValue()).toList();
                int velikostNivoja = velikosti.stream().mapToInt(Integer::intValue).sum();
                boolean skupineKoncane = skupineKoncane(nivo.getValue(), tekme);
                List<Zreb> zrebiNivoja = zrebRepozitorij
                        .findByDogodekIdAndNivoOrderByIndeksAsc(dogodek.getId(), nivo.getKey());
                List<List<String>> viri = viriZrebov(velikosti,
                        zrebiNivoja.stream().map(Zreb::getStUdelezencev).toList());
                List<SvRegijaDto.ZrebDto> zrebi = new ArrayList<>();
                for (int d = 0; d < zrebiNivoja.size(); d++) {
                    Zreb z = zrebiNivoja.get(d);
                    zrebi.add(zrebDto(z.getId(), z.getIndeks(), z.getPrvoMesto(), z.getStUdelezencev(),
                            z.isZgrajen(), z.isRocni(),
                            // nezgrajen zreb se sme zgraditi, ko so skupine nivoja odigrane;
                            // zgrajen se sme popraviti, dokler se nobena njegova tekma ni zacela
                            vTeku && (z.isZgrajen()
                                    ? tekmePoZrebu.getOrDefault(z.getId(), List.of()).stream()
                                            .allMatch(t -> t.getStatus() == StatusTekme.CAKA
                                                    || t.getStatus() == StatusTekme.PRIPRAVLJENA)
                                    : skupineKoncane),
                            z.razporedKotSeznam(), viri.get(d)));
                }
                // raven po zrebu pove prvi zreb (tudi pri dogodkih, izzrebanih po prejsnjem pravilu)
                int velikostZreba = zrebiNivoja.isEmpty()
                        ? velikostZrebaNivoja(velikostZrebov, nivo.getKey())
                        : zrebiNivoja.get(0).velikostMreze();
                // zrebe Skupine sme organizator spremeniti, dokler se nobena njihova tekma ni zacela
                boolean zrebiZaceti = zrebiNivoja.stream()
                        .flatMap(z -> tekmePoZrebu.getOrDefault(z.getId(), List.of()).stream())
                        .anyMatch(t -> t.getStatus() == StatusTekme.KONCANA
                                || t.getStatus() == StatusTekme.V_IGRI);
                nivoji.add(new SvRegijaDto.NivoDto(nivo.getKey(), velikostNivoja, odMesta,
                        odMesta + velikostNivoja - 1, velikosti, velikostZreba,
                        vpisanoZrebov(vpisanoZrebov, nivo.getKey()), zrebi,
                        vTeku && velikosti.size() > 1 && !zrebiZaceti));
                odMesta += velikostNivoja;
            }
        }
        return new SvRegijaDto(nivoji, zadrzek, zrebane,
                zrebane && dogodek.getStatus() == StatusTekmovanja.V_TEKU && !zacete,
                skupin, velikost, dogodek.getSteviloNivojev(), rocne, vpisanoZrebov, velikostZrebov);
    }

    private static SvRegijaDto.ZrebDto zrebDto(Long id, int indeks, int prvoMesto, int stUdelezencev,
                                              boolean zgrajen, boolean rocni, boolean uredljiv,
                                              List<Long> razpored, List<String> viri) {
        return new SvRegijaDto.ZrebDto(id, indeks, Zreb.faza(indeks), Zreb.ime(indeks), prvoMesto,
                prvoMesto + stUdelezencev - 1, stUdelezencev, Zreb.velikostMreze(stUdelezencev),
                zgrajen, rocni, uredljiv, razpored, viri);
    }

    // ---------------------------------------------------------------------
    // Pomozno
    // ---------------------------------------------------------------------

    private Dogodek najdiDogodek(Long idDogodka) {
        Dogodek dogodek = dogodekRepozitorij.najdiSTurnirjem(idDogodka)
                .orElseThrow(() -> new NiNajdenoIzjema("Dogodek z id " + idDogodka + " ne obstaja."));
        if (dogodek.getSistemTekmovanja() != SistemTekmovanja.SV_REGIJA) {
            throw new DomenskaIzjema("Dogodek ne igra sistema SV regija.");
        }
        return dogodek;
    }

    private Zreb najdiZreb(Long idZreba) {
        return zrebRepozitorij.findById(idZreba)
                .orElseThrow(() -> new NiNajdenoIzjema("Žreb z id " + idZreba + " ne obstaja."));
    }

    private static SvPredlogDto.ClanDto clan(Prijava prijava, Map<Long, Integer> ratingi) {
        return new SvPredlogDto.ClanDto(
                prijava.getId(),
                imeIgralca(prijava),
                prijava.getKlubObPrijavi() != null ? prijava.getKlubObPrijavi().getIme() : null,
                prijava.getIgralec() != null ? ratingi.get(prijava.getIgralec().getId()) : null,
                prijava.getStNosilca());
    }

    private static String imeIgralca(Prijava prijava) {
        return prijava.jeEkipa() ? prijava.getEkipa().prikazanoIme() : prijava.getIgralec().polnoIme();
    }
}
