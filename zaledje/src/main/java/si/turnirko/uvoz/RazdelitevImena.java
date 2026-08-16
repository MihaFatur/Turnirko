/* Razdelitev polnega imena iz Stupe na IME in PRIIMEK.

   Zakaj to sploh potrebujemo: Stupa hrani ime kot eno samo polje in vrstni red
   NI dosleden - v istem turnirju stojita "Brezovnik Aljaz" (priimek prvi) in
   "Maj Murn" (ime prvo). Turnirko pa ima loceni polji, zato je treba za vsako
   ime ugotoviti, kateri del je kaj.

   Trije signali, ki se sestevajo:
    1. SLOVAR pogostih osebnih imen (seme spodaj) - najmocnejsi.
    2. POGOSTOST v registru: osebna imena se ponavljajo (vec Gasperjev, vec Lar),
       priimki skoraj nikoli. Zeton, ki se pojavi veckrat, je verjetno ime.
    3. OBLIKA: koncnice -ic, -sek, -ovec, -avec so priimkoske; osebnih imen
       s takimi koncnicami v slovenscini prakticno ni.

   Kar se razresi zanesljivo (jasna prednost pred drugo moznostjo), doda svoje
   zetone v slovar imen oz. priimkov, zato naslednji obhod razresi se nekaj
   primerov, ki prej niso sli. Kar ostane dvoumno, se NE ugiba kot dejstvo -
   oznaci se kot nezanesljivo in gre v porocilo za rocni pregled.

   Primerjava je brez sumnikov in brez velikih zacetnic ("Aljaz" == "Aljaz"),
   zato je seme spodaj namenoma zapisano v goli latinici. */
package si.turnirko.uvoz;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RazdelitevImena {

    /* Rezultat razdelitve. "zanesljivo" pomeni, da je bila najboljsa moznost
       jasno boljsa od druge - le tem je mogoce verjeti brez pogleda cloveka. */
    public record Razdeljeno(String polno, String ime, String priimek, boolean zanesljivo) {}

    /* Koliko mora najboljsa moznost prekasati drugo, da ji verjamemo. */
    private static final int PRAG_ZANESLJIVOSTI = 3;
    private static final int NAJVEC_OBHODOV = 6;

    private static final Set<String> SEME_IMEN = new HashSet<>(Arrays.asList((
            "ada,adam,adnan,ajan,ajda,aleks,aleksander,aleksandra,aleksej,alen,alenka,ales,alja,aljaz,aljosa,ali,"
          + "alma,amadej,ambroz,ana,anamarija,anastazija,anastasija,andraz,andrej,andreja,anej,aneja,anika,anita,"
          + "anja,anina,anton,antonija,anze,arne,arnesa,artur,asja,ava,azbe,"
          + "barbara,bartol,benjamin,bernard,bine,blaz,blazka,bogdan,bojan,bor,borut,bostjan,bozidar,branko,brin,"
          + "brina,bruno,cene,ciril,colin,crt,"
          + "damir,damjan,damjana,dan,dane,daniel,danijel,danilo,darijan,darija,darinka,dario,darja,darko,david,"
          + "davor,dejan,denis,deni,dennis,dino,ditka,domen,dominik,dorotea,drago,dragan,dunja,dusan,"
          + "edin,edo,edvard,ela,elena,eli,elizabeta,elmir,ema,emil,enej,eneja,enes,enya,eric,erik,erika,erin,"
          + "ervin,eva,evgen,evita,"
          + "fabijan,filip,franc,franci,francka,fran,frenk,"
          + "gaber,gabrijel,gaj,gal,ganei,gasper,gaja,gorazd,goran,gregor,grega,"
          + "hana,hanna,helena,hermina,hugo,"
          + "ida,iga,igor,ines,inja,irena,irma,isa,iva,ivan,ivana,ivo,iza,izabel,izidor,indi,iztok,"
          + "jaka,jakob,jan,jana,janez,janja,janko,jasa,jasmina,jasna,jerca,jernej,jelena,jelka,joze,jozef,jost,"
          + "julija,julijan,jure,jurij,justin,"
          + "kaja,kajetan,karin,karla,karmen,katarina,katja,keja,kevin,kim,klara,klemen,kristian,kristijan,"
          + "kristina,kristjan,ksenija,"
          + "lada,lan,lana,lara,larisa,lars,lea,leja,lena,leon,leonardo,lev,lian,lili,lina,liza,lovro,luka,lucija,"
          + "lucijan,ludvik,luna,"
          + "maj,maja,maks,maksim,manca,mare,marcel,marsel,marija,marijan,marina,mario,marjan,marjeta,mark,marko,"
          + "marta,martin,martina,mateja,matej,matevz,matic,matija,matjaz,marusa,masa,mateo,maxim,melani,melita,"
          + "mia,miha,mihael,mihaela,mija,mijo,milan,milena,mina,minea,mira,miran,mirko,miroslav,mitja,mojca,"
          + "monika,muhamed,murat,"
          + "nace,nada,nadja,nal,nastja,natalija,natasa,neja,nejc,nejka,nela,nena,nika,niko,nikola,nikolaj,nina,"
          + "nino,nives,nik,noa,nolan,nusa,"
          + "ognjen,oliver,olivija,oskar,"
          + "pavel,pavla,patricija,patrik,peter,petra,pia,pika,polona,primoz,"
          + "rado,radovan,rafael,rebeka,rene,renata,rok,roj,roman,romana,robert,rudi,rudolf,ruben,"
          + "sabina,samo,sandi,sandra,sara,sasa,sebastjan,sergej,silva,silvo,simon,simona,slavko,smiljan,sonja,"
          + "spela,stanislav,stanko,stas,stefan,svarun,svit,sven,"
          + "tadej,tadeja,taj,taja,tajda,tamara,tanja,tara,tatjana,teja,teo,teodor,tea,tian,tibor,tilen,tim,timon,"
          + "timotej,tina,tine,tinkara,tit,tjasa,tomaz,tomi,tomislav,tomo,tone,tristan,"
          + "ula,urban,urh,uros,ursa,ursula,"
          + "val,valentin,valentina,vanesa,vasja,veronika,vid,vida,viktor,viktorija,vili,vinko,vita,vito,vladimir,"
          + "vlado,voranc,"
          + "zala,zan,zarja,zdenka,zdravko,ziga,zlatko,zoran,zoja,zvonko,zvone"
    ).split(",")));

    private final Set<String> znanaImena = new HashSet<>(SEME_IMEN);
    private final Set<String> znaniPriimki = new HashSet<>();
    private final Map<String, Integer> pogostost = new HashMap<>();
    private final Map<String, Razdeljeno> resitve = new HashMap<>();

    /* Zgradi razdelitev nad CELOTNIM registrom imen naenkrat. Posamicnega imena
       ni mogoce razdeliti dobro brez ostalih - signal pogostosti in ucenje cez
       obhode obstajata samo, ce vidimo vsa imena skupaj. */
    public RazdelitevImena(List<String> polnaImena) {
        List<String> ocisceno = polnaImena.stream()
                .filter(i -> i != null && !i.isBlank())
                .map(String::trim)
                .distinct()
                .toList();

        for (String polno : ocisceno) {
            for (String zeton : zetoni(polno)) {
                pogostost.merge(kljuc(zeton), 1, Integer::sum);
            }
        }

        for (int obhod = 0; obhod < NAJVEC_OBHODOV; obhod++) {
            int novih = 0;
            for (String polno : ocisceno) {
                if (resitve.containsKey(polno)) {
                    continue;
                }
                Kandidat najboljsi = najboljsi(polno);
                if (najboljsi != null && najboljsi.razlika >= PRAG_ZANESLJIVOSTI) {
                    resitve.put(polno, new Razdeljeno(polno, najboljsi.ime, najboljsi.priimek, true));
                    zetoni(najboljsi.ime).forEach(z -> znanaImena.add(kljuc(z)));
                    zetoni(najboljsi.priimek).forEach(z -> znaniPriimki.add(kljuc(z)));
                    novih++;
                }
            }
            if (novih == 0) {
                break;
            }
        }
    }

    /* Vrne razdelitev imena. Ce zanesljive ni, vrne najboljso domnevo z
       oznako zanesljivo=false - klicalec naj jo da cloveku v pregled. */
    public Razdeljeno razdeli(String polno) {
        String p = polno == null ? "" : polno.trim();
        Razdeljeno znana = resitve.get(p);
        if (znana != null) {
            return znana;
        }
        Kandidat k = najboljsi(p);
        if (k == null) {
            // eno samo besedo ni kam razdeliti; Turnirko zahteva oboje
            return new Razdeljeno(p, p.isEmpty() ? "?" : p, "?", false);
        }
        return new Razdeljeno(p, k.ime, k.priimek, false);
    }

    private record Kandidat(String ime, String priimek, int ocena, int razlika) {
        Kandidat(String ime, String priimek, int ocena) { this(ime, priimek, ocena, 0); }
    }

    /* Preizkusi vse mozne reze in obe usmeritvi ter vrne najbolje ocenjeno
       moznost skupaj z razliko do druge najboljse (to je merilo zanesljivosti). */
    private Kandidat najboljsi(String polno) {
        List<String> z = zetoni(polno);
        if (z.size() < 2) {
            return null;
        }
        List<Kandidat> kandidati = new ArrayList<>();
        for (int k = 1; k < z.size(); k++) {
            String prvi = String.join(" ", z.subList(0, k));
            String drugi = String.join(" ", z.subList(k, z.size()));
            // ime prvo, npr. "Maj Murn"
            kandidati.add(new Kandidat(prvi, drugi, oceni(z.subList(0, k), z.subList(k, z.size()))));
            // priimek prvi, npr. "Brezovnik Aljaz"
            kandidati.add(new Kandidat(drugi, prvi, oceni(z.subList(k, z.size()), z.subList(0, k))));
        }
        kandidati.sort(Comparator.comparingInt(Kandidat::ocena).reversed());

        Kandidat naj = kandidati.get(0);
        int razlika = naj.ocena - kandidati.get(1).ocena;
        return new Kandidat(naj.ime, naj.priimek, naj.ocena, razlika);
    }

    private int oceni(List<String> delIme, List<String> delPriimek) {
        int t = 0;
        for (String zeton : delIme) {
            String k = kljuc(zeton);
            if (znanaImena.contains(k)) {
                t += 3;
            } else if (znaniPriimki.contains(k)) {
                t -= 2;
            }
            if (jePriimkovnaOblika(k)) {
                t -= 3;
            }
            if (pogostost.getOrDefault(k, 0) >= 3) {
                t += 2;
            }
        }
        for (String zeton : delPriimek) {
            String k = kljuc(zeton);
            if (znaniPriimki.contains(k)) {
                t += 2;
            } else if (znanaImena.contains(k)) {
                t -= 3;
            }
            if (jePriimkovnaOblika(k)) {
                t += 3;
            }
            if (pogostost.getOrDefault(k, 0) >= 3) {
                t -= 2;
            }
        }
        // enozetonsko ime je dalec najpogostejsi primer
        if (delIme.size() == 1) {
            t += 1;
        }
        return t;
    }

    private static boolean jePriimkovnaOblika(String kljucZetona) {
        return kljucZetona.endsWith("ic")
                || kljucZetona.endsWith("sek")
                || kljucZetona.endsWith("ovec")
                || kljucZetona.endsWith("avec");
    }

    private static List<String> zetoni(String besedilo) {
        List<String> r = new ArrayList<>();
        for (String d : besedilo.trim().split("\\s+")) {
            if (!d.isBlank()) {
                r.add(d);
            }
        }
        return r;
    }

    /* Primerjalni kljuc: male crke brez sumnikov, da se "Aljaz" in "Aljaz"
       (z zastrešico) ujameta z istim vnosom v slovarju. */
    private static String kljuc(String zeton) {
        return Normalizer.normalize(zeton.toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace("đ", "d");
    }
}
