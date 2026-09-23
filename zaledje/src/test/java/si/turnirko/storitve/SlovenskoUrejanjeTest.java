/* Seznami, ki jih zaledje uredi po abecedi, morajo teci po SLOVENSKI abecedi:
   Č za C, Š za S, Ž za Z. SQLite ORDER BY primerja kodne tocke in bi jih vrgel
   na konec seznama, zato repozitoriji urejajo v Javi (SlovenskaAbeceda) - ta
   test varuje, da to ostane res na vsaki poti, ki seznam vrne. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import si.turnirko.dto.EkipaDto;
import si.turnirko.dto.EkipaVnos;
import si.turnirko.dto.KaderVnos;
import si.turnirko.dto.LigaVnos;
import si.turnirko.dto.PrehodiVnos;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.Ekipa;
import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.KaderEkipe;
import si.turnirko.modeli.Klub;
import si.turnirko.modeli.Liga;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.repozitoriji.EkipaRepozitorij;
import si.turnirko.repozitoriji.KaderEkipeRepozitorij;
import si.turnirko.repozitoriji.LigaRepozitorij;

class SlovenskoUrejanjeTest extends IntegracijskiTest {

    @Autowired private LigaStoritev ligaStoritev;
    @Autowired private LigaRepozitorij ligaRepozitorij;
    @Autowired private EkipaRepozitorij ekipaRepozitorij;
    @Autowired private KaderEkipeRepozitorij kaderRepozitorij;

    @Test
    void igralciTecejoPoPriimkuInImenu() {
        Set<Long> nasi = new HashSet<>();
        for (String[] oseba : OSEBE) {
            nasi.add(noviIgralec(oseba[0], oseba[1]).getId());
        }

        List<String> imena = igralecRepozitorij.najdiAktivne().stream()
                .filter(i -> nasi.contains(i.getId()))
                .map(Igralec::polnoIme).toList();

        assertEquals(PRICAKOVANA_IMENA, imena);
    }

    @Test
    void klubiTecejoPoImenu() {
        Set<Long> nasi = new HashSet<>();
        for (String ime : List.of("Zagorje", "Šmartno", "Celje", "Črnuče", "Maribor", "Žalec")) {
            nasi.add(klubRepozitorij.save(new Klub(ime, null)).getId());
        }

        List<String> imena = klubRepozitorij.najdiVsePoAbecedi().stream()
                .filter(k -> nasi.contains(k.getId()))
                .map(Klub::getIme).toList();

        assertEquals(List.of("Celje", "Črnuče", "Maribor", "Šmartno", "Zagorje", "Žalec"), imena);
    }

    /* Klubska ekipa se uvrsti po imenu kluba, prosta po lastnem - v enem seznamu. */
    @Test
    void ekipeLigeTecejoPoAbecediKlubovInProstihImen() {
        Long liga = ustvariLigo("Rekreacijska liga");
        Klub celje = klubRepozitorij.save(new Klub("Celje", null));
        Klub smartno = klubRepozitorij.save(new Klub("Šmartno", null));
        ligaStoritev.dodajEkipo(liga, new EkipaVnos(smartno.getId(), null, null));
        ligaStoritev.dodajEkipo(liga, new EkipaVnos(null, null, "Žabe"));
        ligaStoritev.dodajEkipo(liga, new EkipaVnos(celje.getId(), null, null));
        ligaStoritev.dodajEkipo(liga, new EkipaVnos(null, null, "Čebele"));
        ligaStoritev.dodajEkipo(liga, new EkipaVnos(null, null, "Zajci"));
        ligaStoritev.dodajEkipo(liga, new EkipaVnos(null, null, "Cerkljanke"));
        ligaStoritev.dodajEkipo(liga, new EkipaVnos(null, null, "Šoštanj"));

        List<String> imena = ekipaRepozitorij.najdiZaLigo(liga).stream()
                .map(Ekipa::prikazanoIme).toList();

        assertEquals(List.of("Celje 1", "Cerkljanke", "Čebele", "Šmartno 1", "Šoštanj",
                "Zajci", "Žabe"), imena);
    }

    /* Znotraj kadra brez jakostnega mesta odloca priimek, pri enakem priimku ime. */
    @Test
    void kaderTeceZnotrajMestaPoPriimkuInImenu() {
        Long liga = ustvariLigo("Savinja liga");
        EkipaDto ekipa = ligaStoritev.dodajEkipo(liga, new EkipaVnos(null, null, "Kuhinja"));
        for (String[] oseba : OSEBE) {
            ligaStoritev.dodajVKader(ekipa.id(),
                    new KaderVnos(noviIgralec(oseba[0], oseba[1]).getId(), null));
        }

        List<String> imena = kaderRepozitorij.najdiZaEkipo(ekipa.id()).stream()
                .map(KaderEkipe::getIgralec).map(Igralec::polnoIme).toList();

        assertEquals(PRICAKOVANA_IMENA, imena);
    }

    @Test
    void prijaveDogodkaTecejoPoPriimkuInImenu() {
        Dogodek dogodek = pripraviDogodek(0);
        for (String[] oseba : OSEBE) {
            prijavaRepozitorij.save(new Prijava(dogodek, noviIgralec(oseba[0], oseba[1])));
        }

        List<String> imena = prijavaRepozitorij.najdiZaDogodek(dogodek.getId()).stream()
                .map(Prijava::getIgralec).map(Igralec::polnoIme).toList();

        assertEquals(PRICAKOVANA_IMENA, imena);
    }

    @Test
    void nizjeLigeTecejoPoImenu() {
        Long visja = ustvariLigo("Državna liga");
        List<Long> nizje = List.of("Šentjur", "Celje", "Žalec", "Črnomelj", "Zreče").stream()
                .map(this::ustvariLigo).toList();
        ligaStoritev.nastaviPrehode(visja, new PrehodiVnos(null, nizje, 0, 0));

        List<String> imena = ligaRepozitorij.najdiNizje(visja).stream()
                .map(Liga::getIme).toList();

        assertEquals(List.of("Celje", "Črnomelj", "Šentjur", "Zreče", "Žalec"), imena);
    }

    // ---------- Pomozno ----------

    /* Vrstni red vnosa je namenoma premesan. Kos (4x) in Kosem preverjata ime
       kot drugo merilo in dolzino priimka, Č, Š, Ž pa lego sredi abecede. */
    private static final List<String[]> OSEBE = List.of(
            new String[] {"Ana", "Žagar"},
            new String[] {"Jan", "Zupan"},
            new String[] {"Žan", "Kos"},
            new String[] {"Mia", "Čeh"},
            new String[] {"Tim", "Cerar"},
            new String[] {"Eva", "Šuštar"},
            new String[] {"Špela", "Kos"},
            new String[] {"Luka", "Sever"},
            new String[] {"Črt", "Kos"},
            new String[] {"Nika", "Kosem"},
            new String[] {"Ana", "Kos"});

    private static final List<String> PRICAKOVANA_IMENA = List.of(
            "Tim Cerar", "Mia Čeh", "Ana Kos", "Črt Kos", "Špela Kos", "Žan Kos", "Nika Kosem",
            "Luka Sever", "Eva Šuštar", "Jan Zupan", "Ana Žagar");

    private Long ustvariLigo(String ime) {
        LigaVnos v = new LigaVnos(ime, "25/26", SpolKategorija.MESANO, FormatSrecanja.SAVINJA, 5,
                null, false, 2, 1, 0, true, false, RavenTekmovanja.URADNO, false, null, null, null);
        return ligaStoritev.ustvari(v).id();
    }
}
