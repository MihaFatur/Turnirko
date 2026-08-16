/* Uvoz sifrantov: klubi in igralci.

   To je prvi korak uvoza in edini, ki mora biti popoln: vse poznejse stopnje
   (prijave, tekme, postave) se sklicujejo na te zapise. Kar tu odpade, odpade
   povsod, zato vsaka izpustitev pride v porocilo poimensko.

   Sifrant nastane iz OBEH virov naenkrat (stara stran NTZS in Stupa Events).
   Ista oseba nastopa v obeh, zato se zapisi zdruzujejo po licenci NTZS - ta je
   v obeh virih zapisana enako ("059/15/16") in je edini zanesljiv naravni kljuc
   osebe. Brez zdruzevanja bi se igralcu zgodovina tekem in ELO razdelila na dva
   profila; zdruzevanje po imenu bi zdruzilo soimenjake, zato ga ne delamo.

   Osebnih podatkov namenoma NE prenasamo v celoti: e-postni naslovi v Stupi so
   nadomestni (@yopmail.com) in bi v Turnirku zasedli enolicno polje email,
   telefonov ni. Datum rojstva prenesemo, ker ga shema zahteva in dolocajo
   starostne kategorije - v javne poglede tako ali tako ne gre (IgralecJavniDto). */
package si.turnirko.uvoz;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Klub;
import si.turnirko.modeli.Spol;
import si.turnirko.repozitoriji.IgralecRepozitorij;
import si.turnirko.repozitoriji.KlubRepozitorij;

public class SifrantiUvoz {

    private static final LocalDate NAJZGODNEJSE_ROJSTVO = LocalDate.of(1900, 1, 1);

    private final KlubRepozitorij klubRepozitorij;
    private final IgralecRepozitorij igralecRepozitorij;
    private final UvozPorocilo porocilo;

    /* Preslikavi iz kljucev virov ("stara:3300", "stupa:18896") v shranjene
       zapise Turnirka - uporabljata ju uvoza turnirjev in lig. */
    private final Map<String, Klub> klubi = new HashMap<>();
    private final Map<String, Igralec> igralci = new HashMap<>();
    /* Klub igralca hranimo posebej, ker je Igralec.klub leno nalozen: po
       shranjevanju je entiteta odklopljena in getKlub() bi vrgel
       LazyInitializationException (uvoznik ne tece v eni transakciji). */
    private final Map<String, Klub> klubiIgralcev = new HashMap<>();

    public SifrantiUvoz(KlubRepozitorij klubRepozitorij, IgralecRepozitorij igralecRepozitorij,
                        UvozPorocilo porocilo) {
        this.klubRepozitorij = klubRepozitorij;
        this.igralecRepozitorij = igralecRepozitorij;
        this.porocilo = porocilo;
    }

    public Map<String, Klub> klubi() {
        return klubi;
    }

    public Map<String, Igralec> igralci() {
        return igralci;
    }

    /* Klub igralca po kljucu vira; null, ce ga nima. */
    public Klub klubIgralca(String kljuc) {
        return klubiIgralcev.get(kljuc);
    }

    public void uvozi(ZbirnikSifrantov zbirnik) {
        uvoziKlube(zbirnik);
        uvoziIgralce(zbirnik);
    }

    /* Klubi se zdruzujejo cez vira po POENOSTAVLJENEM imenu: stara stran pise
       "Namiznoteniski klub Arrigoni - STT Arrigoni", Stupa isti klub z veliki-
       mi crkami. Poenostavitev odstrani sumnike, locila in vrsto drustva, tako
       da ostane samo lastno ime. Kjer se sponzorsko ime med viroma razlikuje
       ("NTK Kema" proti "NTK Kema - Murexin"), zdruzitve ni - to je zapisano v
       porocilu in ostaneta dva zapisa, kar je bolje kot napacna zdruzitev. */
    private void uvoziKlube(ZbirnikSifrantov zbirnik) {
        Map<String, Klub> poPoenostavljenem = new HashMap<>();

        for (ZbirnikSifrantov.SurovKlub sk : zbirnik.klubi().values()) {
            String ime = UvozOblike.prirezi(sk.ime(), 50);
            if (ime == null || ime.trim().length() < 2) {
                porocilo.opozori("klub brez uporabnega imena", "id " + sk.id());
                continue;
            }
            if (sk.ime() != null && sk.ime().length() > 50) {
                porocilo.opozori("ime kluba prirezano na 50 znakov", sk.ime());
            }

            String kljucImena = poenostavljenoIme(ime);
            Klub obstojec = poPoenostavljenem.get(kljucImena);
            if (obstojec != null) {
                klubi.put(sk.id(), obstojec);
                porocilo.prestej("zdruzenih zapisov kluba (isti klub v obeh virih)");
                continue;
            }

            // kratica sme biti 2-10 znakov ali prazna; karkoli drugega raje izpustimo
            String kratica = UvozOblike.ocisti(sk.kratica());
            if (kratica != null && (kratica.length() < 2 || kratica.length() > 10)) {
                kratica = null;
            }
            Klub shranjen = klubRepozitorij.save(new Klub(ime, kratica));
            klubi.put(sk.id(), shranjen);
            poPoenostavljenem.put(kljucImena, shranjen);
            porocilo.prestej("klubov");
        }
    }

    private void uvoziIgralce(ZbirnikSifrantov zbirnik) {
        List<ZbirnikSifrantov.SurovIgralec> surovi = List.copyOf(zbirnik.igralci().values());

        // Razdelitev imen mora videti CEL register naenkrat - signal pogostosti
        // in ucenje cez obhode brez tega ne delujeta. Uporabi se samo za
        // igralce, ki jim ime in priimek nista prisla ze locena.
        RazdelitevImena razdelitev = new RazdelitevImena(
                surovi.stream().map(ZbirnikSifrantov.SurovIgralec::polnoIme).toList());

        for (Map.Entry<String, List<ZbirnikSifrantov.SurovIgralec>> skupina
                : poOsebah(surovi).entrySet()) {
            ustvariIgralca(skupina.getValue(), razdelitev);
        }
    }

    /* Zapisi ene osebe. Kdor ima licenco, se zdruzi po njej (tudi cez vira in
       tudi znotraj enega vira - Stupa isto osebo vcasih vodi dvakrat, npr.
       "Brin Vovk Petrovski" in "Vovk Petrovski Brin" z isto licenco). Kdor je
       nima, ostane sam zase: zdruzevanje po imenu bi zlepilo soimenjaka. */
    private Map<String, List<ZbirnikSifrantov.SurovIgralec>> poOsebah(
            List<ZbirnikSifrantov.SurovIgralec> surovi) {

        Map<String, List<ZbirnikSifrantov.SurovIgralec>> skupine = new LinkedHashMap<>();
        for (ZbirnikSifrantov.SurovIgralec si : surovi) {
            String licenca = UvozOblike.ocisti(si.licenca());
            String kljuc = (licenca == null) ? ("brez-licence|" + si.id()) : ("licenca|" + licenca);
            skupine.computeIfAbsent(kljuc, k -> new ArrayList<>()).add(si);
        }
        return skupine;
    }

    private void ustvariIgralca(List<ZbirnikSifrantov.SurovIgralec> zapisi,
                                RazdelitevImena razdelitev) {
        ZbirnikSifrantov.SurovIgralec zdruzen = zdruzi(zapisi);

        String ime = zdruzen.ime();
        String priimek = zdruzen.priimek();
        if (ime == null || priimek == null) {
            // Vir imena ni imel locenega (Stupa) - razdelitev je ocena, zato
            // gre dvomljiva poimensko v porocilo, da jo clovek pregleda.
            RazdelitevImena.Razdeljeno r = razdelitev.razdeli(zdruzen.polnoIme());
            ime = r.ime();
            priimek = r.priimek();
            if (!r.zanesljivo()) {
                porocilo.opozori("ime za rocni pregled",
                        zdruzen.polnoIme() + " -> ime='" + ime + "' priimek='" + priimek + "'");
            }
        }

        ime = UvozOblike.prirezi(ime, 30);
        priimek = UvozOblike.prirezi(priimek, 40);
        if (ime == null || ime.trim().length() < 2 || priimek == null || priimek.trim().length() < 2) {
            porocilo.opozori("igralec brez uporabnega imena (izpuscen)",
                    zdruzen.id() + " '" + zdruzen.polnoIme() + "'");
            return;
        }

        Spol spol = UvozOblike.spol(zdruzen.spol(), 0);
        if (spol == null) {
            porocilo.opozori("igralec brez spola (izpuscen)", zdruzen.polnoIme());
            return;
        }
        if (zdruzen.rojstvo() == null || zdruzen.rojstvo().isBefore(NAJZGODNEJSE_ROJSTVO)) {
            porocilo.opozori("igralec brez datuma rojstva (izpuscen)", zdruzen.polnoIme());
            return;
        }
        if (zdruzen.samoLetnik()) {
            // Stara stran objavi samo letnik. Shema zahteva datum, zato vpisemo
            // 1. januar tega leta: starostne kategorije v namiznem tenisu tecejo
            // po LETNICI, zato igralec ostane v pravi kategoriji.
            porocilo.opozori("datum rojstva le iz letnika (1. januar)",
                    zdruzen.polnoIme() + " -> " + zdruzen.rojstvo());
            porocilo.prestej("igralcev z rojstvom le iz letnika");
        }

        Igralec igralec = new Igralec();
        igralec.setIme(ime);
        igralec.setPriimek(priimek);
        igralec.setSpol(spol);
        igralec.setDatumRojstva(zdruzen.rojstvo());
        igralec.setDrzavljanstvo(UvozOblike.drzavljanstvo(zdruzen.drzava()));
        igralec.setNtzsLicenca(UvozOblike.ocisti(zdruzen.licenca()));

        Klub klub = (zdruzen.idKluba() == null) ? null : klubi.get(zdruzen.idKluba());
        igralec.setKlub(klub);

        Igralec shranjen = igralecRepozitorij.save(igralec);
        porocilo.prestej("igralcev");
        if (zapisi.size() > 1) {
            porocilo.opozori("zdruzeni zapisi iste osebe po licenci NTZS",
                    zdruzen.licenca() + ": " + zapisi.stream().map(ZbirnikSifrantov.SurovIgralec::id).toList());
            porocilo.prestej("zdruzenih zapisov igralcev", zapisi.size() - 1);
        }

        // vsi kljuci obeh virov kazejo na istega igralca Turnirka
        for (ZbirnikSifrantov.SurovIgralec si : zapisi) {
            igralci.put(si.id(), shranjen);
            klubiIgralcev.put(si.id(), klub);
        }
    }

    /* Zapisi iste osebe iz razlicnih virov. Vzamemo najboljsi podatek vsakega
       polja: loceno ime pred ugibanjem, pravi datum rojstva pred letnikom,
       klub iz najnovejsega nastopa. */
    private ZbirnikSifrantov.SurovIgralec zdruzi(List<ZbirnikSifrantov.SurovIgralec> zapisi) {
        ZbirnikSifrantov.SurovIgralec izbran = zapisi.get(0);
        for (int i = 1; i < zapisi.size(); i++) {
            ZbirnikSifrantov.SurovIgralec drugi = zapisi.get(i);
            boolean novejsi = drugi.zadnjiNastop() != null
                    && (izbran.zadnjiNastop() == null || drugi.zadnjiNastop().isAfter(izbran.zadnjiNastop()));
            boolean boljsiDatum = izbran.rojstvo() == null
                    || (izbran.samoLetnik() && drugi.rojstvo() != null && !drugi.samoLetnik());

            izbran = new ZbirnikSifrantov.SurovIgralec(
                    izbran.id(),
                    izbran.polnoIme() != null && izbran.polnoIme().split("\\s+").length >= 2
                            ? izbran.polnoIme() : drugi.polnoIme(),
                    izbran.ime() != null ? izbran.ime() : drugi.ime(),
                    izbran.priimek() != null ? izbran.priimek() : drugi.priimek(),
                    boljsiDatum ? drugi.rojstvo() : izbran.rojstvo(),
                    boljsiDatum ? drugi.samoLetnik() : izbran.samoLetnik(),
                    izbran.spol() != null ? izbran.spol() : drugi.spol(),
                    izbran.licenca() != null ? izbran.licenca() : drugi.licenca(),
                    izbran.drzava() != null ? izbran.drzava() : drugi.drzava(),
                    (novejsi && drugi.idKluba() != null) ? drugi.idKluba() : izbran.idKluba(),
                    novejsi ? drugi.zadnjiNastop() : izbran.zadnjiNastop());
        }
        return izbran;
    }

    /* Ime brez sumnikov, locil in vrste drustva - ostane lastno ime kluba. */
    private static String poenostavljenoIme(String ime) {
        String brezSumnikov = Normalizer.normalize(ime, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase();
        return brezSumnikov
                .replaceAll("\\bNAMIZNOTENISK[AI]\\b|\\bNAMIZNI TENIS\\b", " ")
                .replaceAll("\\bSPORTN[OI]\\b|\\bDRUSTVO\\b|\\bKLUB\\b|\\bZVEZA\\b", " ")
                .replaceAll("\\b(NTK|NTD|NTS|ZNTK|SD|PPK|PPR|TTC|STT)\\b", " ")
                .replaceAll("[^A-Z0-9]", "");
    }
}
