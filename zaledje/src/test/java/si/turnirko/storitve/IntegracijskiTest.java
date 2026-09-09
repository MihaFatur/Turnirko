/* Skupna osnova za integracijske teste:
   - zazene celotno aplikacijo s testno bazo (target/turnirko-test.db),
   - vsak test tece v transakciji, ki se ob koncu razveljavi,
     zato testi ne puscajo sledi drug drugemu,
   - ponuja pomozne metode za pripravo testnih podatkov. */
package si.turnirko.storitve;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Klub;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.SistemTekmovanja;
import si.turnirko.modeli.Spol;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.Turnir;
import si.turnirko.repozitoriji.DogodekRepozitorij;
import si.turnirko.repozitoriji.IgralecRepozitorij;
import si.turnirko.repozitoriji.KlubRepozitorij;
import si.turnirko.repozitoriji.NizRepozitorij;
import si.turnirko.repozitoriji.PrijavaRepozitorij;
import si.turnirko.repozitoriji.RatingStanjeRepozitorij;
import si.turnirko.repozitoriji.RatingZgodovinaRepozitorij;
import si.turnirko.repozitoriji.SkupinaRepozitorij;
import si.turnirko.repozitoriji.TekmaRepozitorij;
import si.turnirko.repozitoriji.TurnirRepozitorij;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public abstract class IntegracijskiTest {

    @Autowired protected IgralecRepozitorij igralecRepozitorij;
    @Autowired protected KlubRepozitorij klubRepozitorij;
    @Autowired protected TurnirRepozitorij turnirRepozitorij;
    @Autowired protected DogodekRepozitorij dogodekRepozitorij;
    @Autowired protected PrijavaRepozitorij prijavaRepozitorij;
    @Autowired protected TekmaRepozitorij tekmaRepozitorij;
    @Autowired protected NizRepozitorij nizRepozitorij;
    @Autowired protected RatingStanjeRepozitorij ratingStanjeRepozitorij;
    @Autowired protected RatingZgodovinaRepozitorij ratingZgodovinaRepozitorij;
    @Autowired protected SkupinaRepozitorij skupinaRepozitorij;

    @Autowired protected ZrebStoritev zrebStoritev;
    @Autowired protected NosilciStoritev nosilciStoritev;
    @Autowired protected TekmaStoritev tekmaStoritev;
    @Autowired protected TurnirjiStoritev turnirjiStoritev;
    @Autowired protected StatistikaStoritev statistikaStoritev;
    @Autowired protected IzborStoritev izborStoritev;
    @Autowired protected RazvrstitevStoritev razvrstitevStoritev;

    /* Ustvari izlocilni dogodek z danim stevilom prijavljenih igralcev. */
    protected Dogodek pripraviDogodek(int steviloIgralcev) {
        return pripraviDogodek(steviloIgralcev, SistemTekmovanja.IZLOCILNI);
    }

    /* Dogodek, kjer je jakostni vrstni red enolicen in znan: prvi prijavljeni
       ima najvisji rating. Brez ratingov bi bili vsi "brez ratinga" in bi jih
       IzborStoritev razvrstil po abecedi - kar bi teste zreba naredilo
       neberljive. */
    protected Dogodek pripraviJakostniDogodek(int steviloIgralcev, SistemTekmovanja sistem) {
        Dogodek dogodek = pripraviDogodek(steviloIgralcev, sistem);
        int[] ratingi = new int[steviloIgralcev];
        for (int i = 0; i < steviloIgralcev; i++) {
            ratingi[i] = 2000 - i * 10;
        }
        nastaviRatinge(dogodek, ratingi);
        return dogodek;
    }

    /* Igralcem dogodka po jakosti pripise klube: klubi[i] je klub i-tega
       najmocnejsega. Posname se tudi v prijavo (klubObPrijavi), ker zreb
       klubsko locitev bere od tam. */
    protected void nastaviKlube(Dogodek dogodek, String... klubi) {
        Map<String, Klub> poImenu = new HashMap<>();
        List<Prijava> poJakosti = izborStoritev.vrstniRed(dogodek.getId());
        for (int i = 0; i < klubi.length && i < poJakosti.size(); i++) {
            Klub klub = poImenu.computeIfAbsent(klubi[i],
                    ime -> klubRepozitorij.save(new Klub(ime, null)));
            Prijava prijava = poJakosti.get(i);
            prijava.getIgralec().setKlub(klub);
            prijava.setKlubObPrijavi(klub);
        }
        prijavaRepozitorij.saveAll(poJakosti);
    }

    /* Ustvari dogodek izbranega sistema z danim stevilom prijavljenih igralcev. */
    protected Dogodek pripraviDogodek(int steviloIgralcev, SistemTekmovanja sistem) {
        return pripraviDogodek(steviloIgralcev, sistem, null, null);
    }

    /* Dogodek formata TOP z nastavljenim stevilom in velikostjo skupin. */
    protected Dogodek pripraviSkupinskiDogodek(int steviloIgralcev, int steviloSkupin,
                                               int velikostSkupine) {
        return pripraviDogodek(steviloIgralcev, SistemTekmovanja.SKUPINE,
                steviloSkupin, velikostSkupine);
    }

    /* Dogodek dvojic (vedno izlocilna mreza) s prijavljenimi igralci, ki jih
       je treba se povezati v pare. Kategorija je KDORKOLI - o spolu para
       odloca sele MESANO, ki ga testi nastavijo posebej. */
    protected Dogodek pripraviDogodekDvojic(int steviloIgralcev) {
        Turnir turnir = new Turnir();
        turnir.setIme("Testni turnir dvojic");
        turnirRepozitorij.save(turnir);

        Dogodek dogodek = new Dogodek();
        dogodek.setTurnir(turnir);
        dogodek.setIme("Clani dvojice");
        dogodek.setDisciplina(si.turnirko.modeli.Disciplina.DVOJICE);
        dogodek.setSpolKategorija(SpolKategorija.KDORKOLI);
        dogodek.setPrivzetoSteviloNizov(5);
        dogodek.setSistemTekmovanja(SistemTekmovanja.IZLOCILNI);
        dogodekRepozitorij.save(dogodek);

        for (int i = 1; i <= steviloIgralcev; i++) {
            prijavaRepozitorij.save(new Prijava(dogodek, noviIgralec("Igralec" + i, "Testni" + i)));
        }
        return dogodek;
    }

    /* Prijave dogodka po id-ju narascajoce - ustaljen vrstni red za teste. */
    protected List<Prijava> prijavePoVrsti(Long idDogodka) {
        return prijavaRepozitorij.najdiZaDogodek(idDogodka).stream()
                .sorted(java.util.Comparator.comparing(Prijava::getId))
                .toList();
    }

    /* Vse prijavljene dogodka po vrsti poveze v pare (1-2, 3-4 ...). */
    protected List<Prijava> poveziVsePare(Long idDogodka) {
        List<Prijava> prijave = prijavePoVrsti(idDogodka);
        for (int i = 0; i + 1 < prijave.size(); i += 2) {
            turnirjiStoritev.poveziVPar(idDogodka,
                    prijave.get(i).getId(), prijave.get(i + 1).getId());
        }
        return prijavePoVrsti(idDogodka);
    }

    /* Nastavitvi skupin morata biti dolocena ZE ob prvem shranjevanju: baza
       ne pusti dogodka sistema SKUPINE brez njiju (CHECK v migraciji V5). */
    private Dogodek pripraviDogodek(int steviloIgralcev, SistemTekmovanja sistem,
                                    Integer steviloSkupin, Integer velikostSkupine) {
        Turnir turnir = new Turnir();
        turnir.setIme("Testni turnir");
        turnirRepozitorij.save(turnir);

        Dogodek dogodek = new Dogodek();
        dogodek.setTurnir(turnir);
        dogodek.setIme("Clani posamicno");
        dogodek.setSpolKategorija(SpolKategorija.MOSKI);
        dogodek.setPrivzetoSteviloNizov(5);
        dogodek.setSistemTekmovanja(sistem);
        dogodek.setSteviloSkupin(steviloSkupin);
        dogodek.setVelikostSkupine(velikostSkupine);
        dogodekRepozitorij.save(dogodek);

        for (int i = 1; i <= steviloIgralcev; i++) {
            Igralec igralec = noviIgralec("Igralec" + i, "Testni" + i);
            prijavaRepozitorij.save(new Prijava(dogodek, igralec));
        }
        return dogodek;
    }

    /* Igralcem dogodka doloci klubski ELO po vrstnem redu prijave:
       prvi prijavljeni dobi prvo vrednost in tako naprej. Kdor v seznamu
       nima vrednosti, ostane brez ratinga. */
    protected void nastaviRatinge(Dogodek dogodek, int... vrednosti) {
        List<Prijava> prijave = prijavaRepozitorij.najdiZaDogodekSStatusom(
                dogodek.getId(), Prijava.StatusPrijave.PRIJAVLJEN).stream()
                .sorted(java.util.Comparator.comparing(Prijava::getId))
                .toList();
        for (int i = 0; i < vrednosti.length && i < prijave.size(); i++) {
            ratingStanjeRepozitorij.save(new si.turnirko.modeli.RatingStanje(
                    prijave.get(i).getIgralec(),
                    si.turnirko.modeli.RatingStanje.SISTEM_KLUBSKI_ELO,
                    vrednosti[i]));
        }
    }

    protected Igralec noviIgralec(String ime, String priimek) {
        return noviIgralec(ime, priimek, Spol.MOSKI);
    }

    protected Igralec noviIgralec(String ime, String priimek, Spol spol) {
        Igralec igralec = new Igralec();
        igralec.setIme(ime);
        igralec.setPriimek(priimek);
        igralec.setSpol(spol);
        igralec.setDatumRojstva(LocalDate.of(2000, 1, 1));
        return igralecRepozitorij.save(igralec);
    }

    /* Vse tekme dogodka, sveze prebrane iz baze. */
    protected List<si.turnirko.modeli.Tekma> tekmeDogodka(Long idDogodka) {
        return tekmaRepozitorij.najdiZaDogodek(idDogodka);
    }
}
