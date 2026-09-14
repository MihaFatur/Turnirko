/* Uvoz sifrantov stare strani NTZS: klubi in igralci.

   To je prvi korak zgodovinskega uvoza in edini, ki mora biti popoln: vse
   poznejse stopnje (prijave, tekme, postave) se sklicujejo na te zapise. Kar
   tu odpade, odpade povsod, zato vsaka izpustitev pride v porocilo poimensko.

   Osebe iz Stupe tu NE nastajajo vec: te uvaza sinhronizacija (IdentitetaStupe)
   dogodek za dogodkom in jih z igralci stare strani poveze po licenci, datumu
   rojstva in spolu. Prejsnja razlicica je zdruzevala samo po licenci in je
   zlepila tuje osebe (licenca ni kljuc osebe - glej IdentitetaStupe).

   Zapisi z isto licenco se zdruzijo samo ob istem letniku in spolu. Vsak zapis
   dobi zunanjo povezavo (STARA_NTZS), da je pot od igralca nazaj do vira
   znana tudi po uvozu.

   Stara stran objavi samo letnik; kjer ga ni, ga za osebo z licenco dopolni
   Stupa (dopolnilo), sicer igralca ni mogoce zapisati (shema zahteva datum). */
package si.turnirko.uvoz;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Klub;
import si.turnirko.modeli.Spol;
import si.turnirko.modeli.VirTekmovanja;
import si.turnirko.modeli.ZunanjaPovezava;
import si.turnirko.repozitoriji.IgralecRepozitorij;
import si.turnirko.repozitoriji.KlubRepozitorij;
import si.turnirko.repozitoriji.ZunanjaPovezavaRepozitorij;
import si.turnirko.uvoz.stupa.IdentitetaStupe;

public class SifrantiUvoz {

    private static final LocalDate NAJZGODNEJSE_ROJSTVO = LocalDate.of(1900, 1, 1);

    /* Podatek, ki ga za osebo z licenco pozna Stupa, stara stran pa ne. */
    public record Dopolnilo(LocalDate rojstvo, Spol spol) {}

    private final KlubRepozitorij klubRepozitorij;
    private final IgralecRepozitorij igralecRepozitorij;
    private final ZunanjaPovezavaRepozitorij povezave;
    private final UvozPorocilo porocilo;
    private final Function<String, Optional<Dopolnilo>> dopolnilaPoLicenci;

    /* Preslikavi iz kljucev vira ("stara:3300") v shranjene zapise Turnirka -
       uporabljata ju uvoza turnirjev in lig stare strani. */
    private final Map<String, Klub> klubi = new HashMap<>();
    private final Map<String, Igralec> igralci = new HashMap<>();
    /* Klub igralca posebej, ker je Igralec.klub leno nalozen in je entiteta
       po shranjevanju odklopljena (uvoz stare strani ne tece v eni transakciji). */
    private final Map<String, Klub> klubiIgralcev = new HashMap<>();

    public SifrantiUvoz(KlubRepozitorij klubRepozitorij, IgralecRepozitorij igralecRepozitorij,
                        ZunanjaPovezavaRepozitorij povezave, UvozPorocilo porocilo,
                        Function<String, Optional<Dopolnilo>> dopolnilaPoLicenci) {
        this.klubRepozitorij = klubRepozitorij;
        this.igralecRepozitorij = igralecRepozitorij;
        this.povezave = povezave;
        this.porocilo = porocilo;
        this.dopolnilaPoLicenci = dopolnilaPoLicenci;
    }

    public Map<String, Klub> klubi() {
        return klubi;
    }

    public Map<String, Igralec> igralci() {
        return igralci;
    }

    public Klub klubIgralca(String kljuc) {
        return klubiIgralcev.get(kljuc);
    }

    public void uvozi(ZbirnikSifrantov zbirnik) {
        uvoziKlube(zbirnik);
        uvoziIgralce(zbirnik);
    }

    /* Klubi se zdruzujejo po POENOSTAVLJENEM imenu (brez vrste drustva,
       sumnikov in locil) - isto pravilo uporabi sinhronizacija s Stupo, zato
       isti klub obeh virov dobi en zapis. */
    private void uvoziKlube(ZbirnikSifrantov zbirnik) {
        Map<String, Klub> poPoenostavljenem = new HashMap<>();
        for (ZbirnikSifrantov.SurovKlub sk : zbirnik.klubi().values()) {
            String ime = UvozOblike.prirezi(sk.ime(), 50);
            if (ime == null || ime.trim().length() < 2) {
                porocilo.opozori("klub brez uporabnega imena", "id " + sk.id());
                continue;
            }
            String kljucImena = IdentitetaStupe.poenostavljenoImeKluba(ime);
            Klub klub = poPoenostavljenem.get(kljucImena);
            if (klub == null) {
                String kratica = UvozOblike.ocisti(sk.kratica());
                if (kratica != null && (kratica.length() < 2 || kratica.length() > 10)) {
                    kratica = null;
                }
                klub = klubRepozitorij.save(new Klub(ime, kratica));
                poPoenostavljenem.put(kljucImena, klub);
                porocilo.prestej("klubov");
            } else {
                porocilo.prestej("zdruzenih zapisov kluba");
            }
            klubi.put(sk.id(), klub);
            povezi(ZunanjaPovezava.Vrsta.KLUB, sk.id(), klub.getId());
        }
    }

    private void uvoziIgralce(ZbirnikSifrantov zbirnik) {
        List<ZbirnikSifrantov.SurovIgralec> surovi = List.copyOf(zbirnik.igralci().values());
        RazdelitevImena razdelitev = new RazdelitevImena(
                surovi.stream().map(ZbirnikSifrantov.SurovIgralec::polnoIme).toList());
        for (List<ZbirnikSifrantov.SurovIgralec> zapisi : poOsebah(surovi).values()) {
            ustvariIgralca(zapisi, razdelitev);
        }
    }

    /* Zapisi ene osebe: ista licenca IN isti letnik IN isti spol. Brez licence
       je vsak zapis oseba zase - zdruzevanje po imenu bi zlepilo soimenjake. */
    private Map<String, List<ZbirnikSifrantov.SurovIgralec>> poOsebah(List<ZbirnikSifrantov.SurovIgralec> surovi) {
        Map<String, List<ZbirnikSifrantov.SurovIgralec>> skupine = new LinkedHashMap<>();
        for (ZbirnikSifrantov.SurovIgralec si : surovi) {
            String licenca = UvozOblike.ocisti(si.licenca());
            String kljuc = licenca == null ? "brez-licence|" + si.id()
                    : "licenca|" + licenca + "|" + (si.rojstvo() == null ? "?" : si.rojstvo().getYear())
                            + "|" + UvozOblike.spol(si.spol(), 0);
            skupine.computeIfAbsent(kljuc, k -> new ArrayList<>()).add(si);
        }
        return skupine;
    }

    private void ustvariIgralca(List<ZbirnikSifrantov.SurovIgralec> zapisi, RazdelitevImena razdelitev) {
        ZbirnikSifrantov.SurovIgralec zdruzen = zapisi.get(0);
        for (int i = 1; i < zapisi.size(); i++) {
            zdruzen = ZbirnikSifrantov.zdruzi(zdruzen, zapisi.get(i));
        }

        String ime = zdruzen.ime();
        String priimek = zdruzen.priimek();
        if (ime == null || priimek == null) {
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
            porocilo.opozori("igralec brez uporabnega imena (izpuscen)", zdruzen.id() + " '" + zdruzen.polnoIme() + "'");
            return;
        }

        Spol spol = UvozOblike.spol(zdruzen.spol(), 0);
        LocalDate rojstvo = zdruzen.rojstvo();
        String licenca = UvozOblike.ocisti(zdruzen.licenca());
        if ((spol == null || rojstvo == null) && licenca != null) {
            Optional<Dopolnilo> dopolnilo = dopolnilaPoLicenci.apply(licenca);
            if (dopolnilo.isPresent()) {
                if (rojstvo == null && dopolnilo.get().rojstvo() != null) {
                    rojstvo = LocalDate.of(dopolnilo.get().rojstvo().getYear(), 1, 1);
                    porocilo.prestej("letnikov stare strani, dopolnjenih iz Stupe");
                }
                if (spol == null) {
                    spol = dopolnilo.get().spol();
                }
            }
        }
        if (spol == null) {
            porocilo.opozori("igralec brez spola (izpuscen)", zdruzen.polnoIme());
            return;
        }
        if (rojstvo == null || rojstvo.isBefore(NAJZGODNEJSE_ROJSTVO)) {
            porocilo.opozori("igralec brez datuma rojstva (izpuscen)", zdruzen.polnoIme());
            return;
        }

        Igralec igralec = new Igralec();
        igralec.setIme(ime);
        igralec.setPriimek(priimek);
        igralec.setSpol(spol);
        // stara stran objavi samo letnik: 1. januar ohrani starostni pas
        // (pasovi tecejo po letnici), Stupa ga ob povezavi dopolni s pravim datumom
        igralec.setDatumRojstva(rojstvo);
        igralec.setDrzavljanstvo(UvozOblike.drzavljanstvo(zdruzen.drzava()));
        if (licenca != null && !igralecRepozitorij.existsByNtzsLicenca(licenca)) {
            igralec.setNtzsLicenca(licenca);
        } else if (licenca != null) {
            porocilo.opozori("licenca NTZS je ze pri drugem igralcu (zapisan brez licence)",
                    zdruzen.polnoIme() + " " + licenca);
        }
        Klub klub = zdruzen.idKluba() == null ? null : klubi.get(zdruzen.idKluba());
        igralec.setKlub(klub);
        Igralec shranjen = igralecRepozitorij.save(igralec);
        porocilo.prestej("igralcev");
        if (zapisi.size() > 1) {
            porocilo.prestej("zdruzenih zapisov igralcev", zapisi.size() - 1);
        }
        for (ZbirnikSifrantov.SurovIgralec si : zapisi) {
            igralci.put(si.id(), shranjen);
            klubiIgralcev.put(si.id(), klub);
            povezi(ZunanjaPovezava.Vrsta.IGRALEC, si.id(), shranjen.getId());
        }
    }

    /* Povezava s kljucem vira brez oznake vira ("stara:3300" -> "3300"). */
    private void povezi(ZunanjaPovezava.Vrsta vrsta, String kljucVira, Long lokalni) {
        String zunanji = kljucVira.startsWith(ZbirnikSifrantov.VIR_STARA + ":")
                ? kljucVira.substring(ZbirnikSifrantov.VIR_STARA.length() + 1) : kljucVira;
        if (povezave.findByVirAndVrstaAndZunanjiId(VirTekmovanja.STARA_NTZS, vrsta, zunanji).isEmpty()) {
            povezave.save(new ZunanjaPovezava(VirTekmovanja.STARA_NTZS, vrsta, UvozOblike.prirezi(zunanji, 80), lokalni));
        }
    }
}
