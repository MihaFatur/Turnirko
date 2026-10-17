/* Razveljavitev zapisnika ekipne tekme turnirja: turnirski del.

   Zapisnik srecanja (postava, posamicne tekme, tocke, obracun ratinga) pocisti
   SrecanjeStoritev.razveljaviZapisnik, isto kot pri ligi. Pri ekipni tekmi
   turnirja pa je izid srecanja postal izid TEKME V MREZI ALI SKUPINI, in ta je
   sprozil posledice (TekmaStoritev.posledice). Tu se te posledice vrnejo, da
   je turnir tak, kot da tekma ni bila nikoli odigrana:

   - tekma ni vec koncana: ni zmagovalca, izida, dobljenih tekem,
   - zmagovalec (oz. porazenec) gre iz naslednje tekme v mrezi; ta spet caka,
   - skupina ni vec odigrana do konca: mesta v skupini gredo,
   - kar je nastalo iz izida skupine, gre: finalne skupine za mesta
     (SKUPINE_ZA_MESTA) in izlocilni del (SKUPINE_IZLOCILNI),
   - dogodek, ki je bil zaradi te tekme zakljucen, spet tece, koncna mesta
     gredo; turnir, zaprt zaradi tega, se spet odpre.

   KJE SE ZAVRNE: kjer je po tej tekmi ze teklo kaj, cesar ni mogoce tiho
   zavreci - naslednja tekma v mrezi, finalna skupina ali izlocilni del, ki je
   ze zacet ali odigran. Organizator najprej razveljavi tisto, nato to
   (obratno kot pri vnosu). Isto merilo kot ZrebStoritev.razveljaviZreb. */
package si.turnirko.storitve;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.FazaTekme;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.SistemTekmovanja;
import si.turnirko.modeli.Skupina;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.StatusTekmovanja;
import si.turnirko.modeli.Tekma;
import si.turnirko.modeli.Turnir;
import si.turnirko.repozitoriji.DogodekRepozitorij;
import si.turnirko.repozitoriji.PrijavaRepozitorij;
import si.turnirko.repozitoriji.SkupinaRepozitorij;
import si.turnirko.repozitoriji.TekmaRepozitorij;

@Service
public class RazveljavitevEkipneTekmeStoritev {

    private final TekmaRepozitorij tekmaRepozitorij;
    private final SkupinaRepozitorij skupinaRepozitorij;
    private final PrijavaRepozitorij prijavaRepozitorij;
    private final DogodekRepozitorij dogodekRepozitorij;
    private final ZrebStoritev zrebStoritev;
    private final EkipneTekmeStoritev ekipneTekme;

    public RazveljavitevEkipneTekmeStoritev(TekmaRepozitorij tekmaRepozitorij,
                                            SkupinaRepozitorij skupinaRepozitorij,
                                            PrijavaRepozitorij prijavaRepozitorij,
                                            DogodekRepozitorij dogodekRepozitorij,
                                            ZrebStoritev zrebStoritev,
                                            EkipneTekmeStoritev ekipneTekme) {
        this.tekmaRepozitorij = tekmaRepozitorij;
        this.skupinaRepozitorij = skupinaRepozitorij;
        this.prijavaRepozitorij = prijavaRepozitorij;
        this.dogodekRepozitorij = dogodekRepozitorij;
        this.zrebStoritev = zrebStoritev;
        this.ekipneTekme = ekipneTekme;
    }

    /* Ali se zapisnik sme razveljaviti. Nic ne spremeni - klicalec
       (SrecanjeStoritev.preveriRazveljavitev) to poklice PRED prvim zapisom,
       ker preklic po zapisu ni vedno takojsen (zunanja transakcija). Tekma, ki
       se igra, nima posledic in se razveljavi vedno. */
    @Transactional(readOnly = true)
    public void preveri(Long idTekme) {
        Tekma tekma = najdi(idTekme);
        if (tekma.getStatus() != StatusTekme.KONCANA) {
            return;
        }
        List<Tekma> tekme = tekmaRepozitorij.najdiZaDogodek(tekma.getDogodek().getId());
        List<Skupina> skupine = skupinaRepozitorij.findByDogodekIdOrderByOznakaAsc(
                tekma.getDogodek().getId());

        for (Tekma odvisna : odvisne(tekma, tekme)) {
            if (ZrebStoritev.zacet(odvisna)) {
                throw new DomenskaIzjema("Po tej tekmi je turnir ze tekel naprej: naslednja tekma"
                        + " (kolo " + odvisna.getKolo() + ") je ze zacela ali odigrana. Najprej"
                        + " razveljavi njen zapisnik, nato zapisnik te tekme.");
            }
        }
        for (Tekma izpeljana : izpeljane(tekma, tekme, skupine)) {
            if (!izpeljana.jePrenesena() && ZrebStoritev.zacet(izpeljana)) {
                throw new DomenskaIzjema("Iz izida te skupine je ze nastal naslednji del turnirja"
                        + " (" + (tekma.getDogodek().getSistemTekmovanja()
                                == SistemTekmovanja.SKUPINE_ZA_MESTA
                                ? "finalne skupine za mesta" : "izlocilni del")
                        + ") in se igra ali je odigran. Najprej razveljavi zapisnike v njem.");
            }
        }
    }

    /* Vrne turnir v stanje pred to tekmo. Klicalec je srecanje ze vrnil v
       razpored in preveril `preveri`. */
    @Transactional
    public void razveljavi(Long idTekme) {
        Tekma tekma = najdi(idTekme);
        Long idDogodka = tekma.getDogodek().getId();
        boolean bilaKoncana = tekma.getStatus() == StatusTekme.KONCANA;

        if (bilaKoncana) {
            List<Tekma> tekme = tekmaRepozitorij.najdiZaDogodek(idDogodka);
            List<Skupina> skupine = skupinaRepozitorij.findByDogodekIdOrderByOznakaAsc(idDogodka);
            vrniNapredovanje(tekma, tekme);
            odstraniIzpeljano(tekma, tekme, skupine);
            pocistiMestaVSkupini(tekma, tekme, skupine, prijavaRepozitorij.najdiZaDogodek(idDogodka));
        }

        // spet pripravljena, kot pred postavo (postava jo je dala "v igro")
        tekma.setZmagovalec(null);
        tekma.setIzidTip(null);
        tekma.setDobljeniNizi1(0);
        tekma.setDobljeniNizi2(0);
        tekma.setStatus(StatusTekme.PRIPRAVLJENA);
        tekmaRepozitorij.save(tekma);
        tekmaRepozitorij.flush();

        if (bilaKoncana) {
            ponovnoOdpriDogodek(idDogodka);
        }
    }

    // ---------- Napredovanje ----------

    /* Tekme, v katere je ta tekma napredovala. */
    private static List<Tekma> odvisne(Tekma tekma, List<Tekma> tekme) {
        return tekme.stream()
                .filter(t -> tekma.getId().equals(t.getIdIzvorTekma1())
                        || tekma.getId().equals(t.getIdIzvorTekma2()))
                .toList();
    }

    /* Iz odvisnih tekem pobere udelezenca, ki je prisel iz te tekme; tekma
       spet caka na drugi del para, njeno (prazno) srecanje pa gre - nastane
       znova, ko bosta znani obe ekipi (EkipneTekmeStoritev.zagotoviSrecanja). */
    private void vrniNapredovanje(Tekma tekma, List<Tekma> tekme) {
        List<Tekma> odvisne = odvisne(tekma, tekme);
        for (Tekma odvisna : odvisne) {
            if (tekma.getId().equals(odvisna.getIdIzvorTekma1())) {
                odvisna.setPrijava1(null);
            }
            if (tekma.getId().equals(odvisna.getIdIzvorTekma2())) {
                odvisna.setPrijava2(null);
            }
            odvisna.setStatus(StatusTekme.CAKA);
        }
        tekmaRepozitorij.saveAll(odvisne);
        tekmaRepozitorij.flush();
        for (Tekma odvisna : odvisne) {
            ekipneTekme.odstraniNezacetoSrecanje(odvisna.getId());
        }
    }

    // ---------- Skupine ----------

    /* Tekme, ki so nastale iz izida skupine, v kateri je ta tekma: finalne
       skupine za mesta oz. izlocilni del. Samo predtekmovalna skupina jih
       rodi; tekma finalne skupine ali izlocilnega dela jih nima. */
    private static List<Tekma> izpeljane(Tekma tekma, List<Tekma> tekme, List<Skupina> skupine) {
        if (!jePredtekmovalna(tekma, skupine)) {
            return List.of();
        }
        return switch (tekma.getDogodek().getSistemTekmovanja()) {
            case SKUPINE_ZA_MESTA -> {
                Set<Long> finalne = finalneSkupine(skupine).stream()
                        .map(Skupina::getId).collect(Collectors.toSet());
                yield tekme.stream().filter(t -> finalne.contains(t.getIdSkupina())).toList();
            }
            case SKUPINE_IZLOCILNI -> tekme.stream()
                    .filter(t -> t.getFaza() == FazaTekme.GLAVNI || t.getFaza() == FazaTekme.TOLAZILNI)
                    .toList();
            default -> List.of();
        };
    }

    private static List<Skupina> finalneSkupine(List<Skupina> skupine) {
        return skupine.stream().filter(s -> s.getStopnja() > 1).toList();
    }

    /* Tekma predtekmovalne skupine (stopnja 1): iz njenega izida nastane vse
       naslednje. */
    private static boolean jePredtekmovalna(Tekma tekma, List<Skupina> skupine) {
        return tekma.getFaza() == FazaTekme.SKUPINA
                && skupine.stream().anyMatch(s -> s.getId().equals(tekma.getIdSkupina())
                        && s.getStopnja() == 1);
    }

    /* Finalne skupine oz. izlocilni del, nastale iz te skupine, gredo. Prenesene
       tekme nimajo srecanja; ostale imajo prazno (preveri je zagotovil, da se
       nobena ni zacela). */
    private void odstraniIzpeljano(Tekma tekma, List<Tekma> tekme, List<Skupina> skupine) {
        if (!jePredtekmovalna(tekma, skupine)) {
            return; // tekma finalne skupine ali izlocilnega dela ne rodi nicesar
        }
        List<Tekma> izpeljane = izpeljane(tekma, tekme, skupine);
        for (Tekma izpeljana : izpeljane) {
            if (!izpeljana.jePrenesena()) {
                ekipneTekme.odstraniNezacetoSrecanje(izpeljana.getId());
            }
        }
        if (!izpeljane.isEmpty()) {
            zrebStoritev.izbrisiTekme(izpeljane);
        }
        if (tekma.getDogodek().getSistemTekmovanja() == SistemTekmovanja.SKUPINE_ZA_MESTA) {
            List<Skupina> finalne = finalneSkupine(skupine);
            if (!finalne.isEmpty()) {
                skupinaRepozitorij.deleteAll(finalne);
                skupinaRepozitorij.flush();
                /* Ekipa, ki je ostala sama v svojem pasu, je mesto dobila ze ob
                   nastanku finalnih skupin; s skupinami gre tudi to. */
                List<Prijava> prijave = prijavaRepozitorij.najdiZaDogodek(tekma.getDogodek().getId());
                prijave.forEach(p -> p.setKoncnoMesto(null));
                prijavaRepozitorij.saveAll(prijave);
            }
        }
    }

    /* Mesta v skupini veljajo, ko je skupina odigrana do konca (SkupineStoritev.
       obKoncaniSkupinski); ta tekma je spet odprta, zato mest ni. */
    private void pocistiMestaVSkupini(Tekma tekma, List<Tekma> tekme, List<Skupina> skupine,
                                      List<Prijava> prijave) {
        if (!jePredtekmovalna(tekma, skupine)) {
            return;
        }
        Skupina skupina = skupine.stream()
                .filter(s -> s.getId().equals(tekma.getIdSkupina())).findFirst().orElseThrow();
        List<Prijava> clani = SkupineStoritev.clani(skupina, prijave, tekme);
        clani.forEach(p -> p.setMestoVSkupini(null));
        prijavaRepozitorij.saveAll(clani);
    }

    // ---------- Dogodek in turnir ----------

    /* Dogodek, ki se je zakljucil z to tekmo, spet tece: koncna mesta (nastala
       ob zakljucku) gredo, turnir, ki je bil zaprt zaradi njega, se odpre. Ne
       gre za novo odlocitev - tak je turnir bil, ko ta tekma se ni bila
       odigrana; organizator ga po novem vnosu zakljuci znova. */
    private void ponovnoOdpriDogodek(Long idDogodka) {
        Dogodek dogodek = dogodekRepozitorij.najdiSTurnirjem(idDogodka)
                .orElseThrow(() -> new NiNajdenoIzjema("Dogodek z id " + idDogodka + " ne obstaja."));
        if (dogodek.getStatus() != StatusTekmovanja.ZAKLJUCEN) {
            return;
        }
        List<Prijava> prijave = prijavaRepozitorij.najdiZaDogodek(idDogodka);
        switch (dogodek.getSistemTekmovanja()) {
            // format TOP koncnih mest sploh ne dodeli (skupine so rangi)
            case SKUPINE -> { }
            // mesta iz finalnih skupin; kdor je bil sam v pasu, jih obdrzi
            case SKUPINE_ZA_MESTA -> {
                List<Tekma> tekme = tekmaRepozitorij.najdiZaDogodek(idDogodka);
                for (Skupina finalna : finalneSkupine(
                        skupinaRepozitorij.findByDogodekIdOrderByOznakaAsc(idDogodka))) {
                    SkupineStoritev.clani(finalna, prijave, tekme)
                            .forEach(p -> p.setKoncnoMesto(null));
                }
            }
            default -> prijave.forEach(p -> p.setKoncnoMesto(null));
        }
        prijavaRepozitorij.saveAll(prijave);

        dogodek.setStatus(StatusTekmovanja.V_TEKU);
        Turnir turnir = dogodek.getTurnir();
        if (turnir.getStatus() == StatusTekmovanja.ZAKLJUCEN) {
            turnir.setStatus(StatusTekmovanja.V_TEKU);
        }
    }

    private Tekma najdi(Long idTekme) {
        return tekmaRepozitorij.najdiZVsem(idTekme)
                .orElseThrow(() -> new NiNajdenoIzjema("Tekma z id " + idTekme + " ne obstaja."));
    }
}
