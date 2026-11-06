/* Upravljanje dodatnih dostopov organizatorjev do turnirjev in lig (V62) - samo
   admin.

   Brez dodelitve organizator upravlja tekmovanja, ki jih je ustvaril, in
   tekmovanja svojega kluba. Admin mu lahko odobri dostop do katerega koli
   drugega tekmovanja in pri vsaki dodelitvi odloči, ali tekmovanje šteje v mejo
   njegovega paketa (NarocninaStoritev). Isti preklop velja za lastnika: admin
   lahko tekmovanje, ki ga je organizator ustvaril, iz njegove meje izvzame,
   lastništva pa ne spremeni.

   MERILA, ki jih ne razbij:
   - Vrstica o lastniku dostopa ne daje (ima ga vedno), samo prepiše štetje.
     »Šteje«, ki je privzetek lastnika, se ne hrani: preklop nazaj vrstico
     zbriše, da je stanje brez vrstice vedno enako privzetemu.
   - Meja ostane pravilo, ki ga ob ustvarjanju preverja strežnik; admin jo sme
     preseči (dodelitev nikoli ne zavrne zaradi polne meje), zato okno pokaže
     porabo - presežek je odločitev, ne napaka.
   - Dnevnik računa (RacunDnevnikStoritev) zapiše vsako spremembo na račun
     organizatorja: tam jo admin išče, ko organizator vpraša, zakaj mu je meja
     polna ali prosta. */
package si.turnirko.storitve;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.DostopTekmovanjaDto;
import si.turnirko.dto.DostopTekmovanjaDto.Kandidat;
import si.turnirko.dto.DostopTekmovanjaDto.Organizator;
import si.turnirko.dto.DostopTekmovanjaDto.Poraba;
import si.turnirko.dto.DostopTekmovanjaDto.Vir;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.izjeme.PrepovedanoIzjema;
import si.turnirko.modeli.DostopOrganizatorja;
import si.turnirko.modeli.Klub;
import si.turnirko.modeli.Liga;
import si.turnirko.modeli.Narocnina;
import si.turnirko.modeli.Paket;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.Turnir;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.pomozno.SlovenskaAbeceda;
import si.turnirko.repozitoriji.DostopOrganizatorjaRepozitorij;
import si.turnirko.repozitoriji.LigaRepozitorij;
import si.turnirko.repozitoriji.NarocninaRepozitorij;
import si.turnirko.repozitoriji.TurnirRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

@Service
public class DostopOrganizatorjevStoritev {

    private final DostopOrganizatorjaRepozitorij dostopRepozitorij;
    private final TurnirRepozitorij turnirRepozitorij;
    private final LigaRepozitorij ligaRepozitorij;
    private final UporabnikRepozitorij uporabnikRepozitorij;
    private final NarocninaRepozitorij narocninaRepozitorij;
    private final NarocninaStoritev narocnina;
    private final LastnistvoStoritev lastnistvo;
    private final RacunDnevnikStoritev dnevnik;

    public DostopOrganizatorjevStoritev(DostopOrganizatorjaRepozitorij dostopRepozitorij,
                                        TurnirRepozitorij turnirRepozitorij,
                                        LigaRepozitorij ligaRepozitorij,
                                        UporabnikRepozitorij uporabnikRepozitorij,
                                        NarocninaRepozitorij narocninaRepozitorij,
                                        NarocninaStoritev narocnina,
                                        LastnistvoStoritev lastnistvo,
                                        RacunDnevnikStoritev dnevnik) {
        this.dostopRepozitorij = dostopRepozitorij;
        this.turnirRepozitorij = turnirRepozitorij;
        this.ligaRepozitorij = ligaRepozitorij;
        this.uporabnikRepozitorij = uporabnikRepozitorij;
        this.narocninaRepozitorij = narocninaRepozitorij;
        this.narocnina = narocnina;
        this.lastnistvo = lastnistvo;
        this.dnevnik = dnevnik;
    }

    /* Tekmovanje, o katerem odloča klic: turnir ali liga z istimi podatki, ki jih
       potrebujeta pregled in zapis. */
    private record Cilj(boolean turnir, Long id, String ime, Uporabnik lastnik, Klub klub) {

        String vrsta() {
            return turnir ? "turnirja" : "lige";
        }
    }

    // ---------- Branje ----------

    @Transactional(readOnly = true)
    public DostopTekmovanjaDto zaTurnir(Long idTurnir) {
        preveriAdmina();
        return pregled(turnir(idTurnir));
    }

    @Transactional(readOnly = true)
    public DostopTekmovanjaDto zaLigo(Long idLiga) {
        preveriAdmina();
        return pregled(liga(idLiga));
    }

    // ---------- Pisanje ----------

    @Transactional
    public DostopTekmovanjaDto nastaviZaTurnir(Long idTurnir, Long idRacun, boolean steje) {
        preveriAdmina();
        Cilj cilj = turnir(idTurnir);
        nastavi(cilj, idRacun, steje);
        return pregled(cilj);
    }

    @Transactional
    public DostopTekmovanjaDto nastaviZaLigo(Long idLiga, Long idRacun, boolean steje) {
        preveriAdmina();
        Cilj cilj = liga(idLiga);
        nastavi(cilj, idRacun, steje);
        return pregled(cilj);
    }

    @Transactional
    public DostopTekmovanjaDto odstraniZaTurnir(Long idTurnir, Long idRacun) {
        preveriAdmina();
        Cilj cilj = turnir(idTurnir);
        odstrani(cilj, idRacun);
        return pregled(cilj);
    }

    @Transactional
    public DostopTekmovanjaDto odstraniZaLigo(Long idLiga, Long idRacun) {
        preveriAdmina();
        Cilj cilj = liga(idLiga);
        odstrani(cilj, idRacun);
        return pregled(cilj);
    }

    // ---------- Jedro ----------

    /* Veriga omeji pot na ADMIN; to je druga obramba (kot pri LastnistvoStoritev
       brez konteksta, interni klic in test ne omejuje). */
    private void preveriAdmina() {
        Uporabnik jaz = lastnistvo.trenutni();
        if (jaz != null && jaz.getVloga() != Vloga.ADMIN) {
            throw new PrepovedanoIzjema("Dostope organizatorjev ureja samo administrator.");
        }
    }

    private void nastavi(Cilj cilj, Long idRacun, boolean steje) {
        Uporabnik racun = uporabnikRepozitorij.findById(idRacun)
                .orElseThrow(() -> new NiNajdenoIzjema("Račun z id " + idRacun + " ne obstaja."));
        if (racun.getVloga() != Vloga.ORGANIZATOR) {
            throw new NeveljavenVnosIzjema("Dostop do tekmovanja je mogoče dodeliti samo organizatorju.");
        }
        boolean jeLastnik = cilj.lastnik() != null && cilj.lastnik().getId().equals(idRacun);
        Optional<DostopOrganizatorja> obstojec = vrstica(cilj, idRacun);

        if (jeLastnik && steje) {
            // privzetek lastnika: brez vrstice (glej merila zgoraj)
            if (obstojec.isEmpty()) {
                return;
            }
            dostopRepozitorij.delete(obstojec.get());
            dostopRepozitorij.flush();
            dnevnik.zabelezi(idRacun, "Dostop " + cilj.vrsta() + " »" + cilj.ime() + "«: šteje v omejitev paketa");
            return;
        }
        if (obstojec.isPresent()) {
            if (obstojec.get().isStejeVMejo() == steje) {
                return;
            }
            obstojec.get().setStejeVMejo(steje);
            dostopRepozitorij.saveAndFlush(obstojec.get());
            dnevnik.zabelezi(idRacun, "Dostop " + cilj.vrsta() + " »" + cilj.ime() + "«: "
                    + (steje ? "šteje" : "ne šteje") + " v omejitev paketa");
            return;
        }
        if (!jeLastnik && (racun.getStatus() != StatusRacuna.POTRJEN || !racun.isAktiven())) {
            throw new NeveljavenVnosIzjema("Dostop je mogoče dodeliti samo potrjenemu in aktivnemu organizatorju.");
        }
        LocalDateTime zdaj = LocalDateTime.now();
        dostopRepozitorij.saveAndFlush(cilj.turnir()
                ? DostopOrganizatorja.zaTurnir(cilj.id(), idRacun, steje, zdaj)
                : DostopOrganizatorja.zaLigo(cilj.id(), idRacun, steje, zdaj));
        // lastniku se nič ne »dodeli«: prvič se mu samo izvzame štetje
        dnevnik.zabelezi(idRacun, jeLastnik
                ? "Dostop " + cilj.vrsta() + " »" + cilj.ime() + "«: ne šteje v omejitev paketa"
                : "Dostop " + cilj.vrsta() + " »" + cilj.ime() + "« dodeljen, "
                        + (steje ? "šteje" : "ne šteje") + " v omejitev paketa");
    }

    private void odstrani(Cilj cilj, Long idRacun) {
        DostopOrganizatorja vrstica = vrstica(cilj, idRacun).orElseThrow(() -> new NiNajdenoIzjema(
                "Organizator nima dodeljenega dostopa do tega tekmovanja."));
        dostopRepozitorij.delete(vrstica);
        dostopRepozitorij.flush();
        boolean jeLastnik = cilj.lastnik() != null && cilj.lastnik().getId().equals(idRacun);
        dnevnik.zabelezi(idRacun, jeLastnik
                ? "Dostop " + cilj.vrsta() + " »" + cilj.ime() + "«: šteje v omejitev paketa"
                : "Dostop " + cilj.vrsta() + " »" + cilj.ime() + "« odvzet");
    }

    private Optional<DostopOrganizatorja> vrstica(Cilj cilj, Long idRacun) {
        return cilj.turnir()
                ? dostopRepozitorij.findByIdTurnirAndIdRacun(cilj.id(), idRacun)
                : dostopRepozitorij.findByIdLigaAndIdRacun(cilj.id(), idRacun);
    }

    private Cilj turnir(Long id) {
        Turnir t = turnirRepozitorij.findById(id)
                .orElseThrow(() -> new NiNajdenoIzjema("Turnir z id " + id + " ne obstaja."));
        return new Cilj(true, t.getId(), t.getIme(), t.getUstvaril(), t.getKlubLastnik());
    }

    private Cilj liga(Long id) {
        Liga l = ligaRepozitorij.findById(id)
                .orElseThrow(() -> new NiNajdenoIzjema("Liga z id " + id + " ne obstaja."));
        return new Cilj(false, l.getId(), l.getIme(), l.getUstvaril(), l.getKlubLastnik());
    }

    // ---------- Izpis ----------

    private DostopTekmovanjaDto pregled(Cilj cilj) {
        List<DostopOrganizatorja> vrstice = cilj.turnir()
                ? dostopRepozitorij.findByIdTurnir(cilj.id())
                : dostopRepozitorij.findByIdLiga(cilj.id());
        LocalDate danes = LocalDate.now();

        List<Organizator> organizatorji = new ArrayList<>();
        Set<Long> vkljuceni = new HashSet<>();
        if (cilj.lastnik() != null && cilj.lastnik().getVloga() == Vloga.ORGANIZATOR) {
            Uporabnik lastnik = cilj.lastnik();
            boolean steje = vrstice.stream()
                    .noneMatch(v -> v.getIdRacun().equals(lastnik.getId()) && !v.isStejeVMejo());
            organizatorji.add(organizator(lastnik, Vir.LASTNIK, steje, cilj, danes));
            vkljuceni.add(lastnik.getId());
        }
        List<Organizator> dodeljeni = new ArrayList<>();
        for (DostopOrganizatorja v : vrstice) {
            if (vkljuceni.contains(v.getIdRacun())) {
                continue;
            }
            Uporabnik racun = uporabnikRepozitorij.findById(v.getIdRacun()).orElse(null);
            if (racun == null) {
                continue;
            }
            dodeljeni.add(organizator(racun, Vir.DODELJEN, v.isStejeVMejo(), cilj, danes));
            vkljuceni.add(racun.getId());
        }
        dodeljeni.sort(Comparator.comparing(Organizator::ime, SlovenskaAbeceda.RED));
        organizatorji.addAll(dodeljeni);

        List<Kandidat> kandidati = uporabnikRepozitorij.najdiAktivneOrganizatorje().stream()
                .filter(u -> !vkljuceni.contains(u.getId()))
                .map(u -> new Kandidat(u.getId(), ime(u), u.getUporabniskoIme(),
                        u.getKlub() != null ? u.getKlub().getIme() : null))
                .sorted(Comparator.comparing(Kandidat::ime, SlovenskaAbeceda.RED))
                .toList();

        return new DostopTekmovanjaDto(
                cilj.klub() != null ? cilj.klub().getIme() : null, organizatorji, kandidati);
    }

    private Organizator organizator(Uporabnik u, Vir vir, boolean steje, Cilj cilj, LocalDate danes) {
        Narocnina n = narocninaRepozitorij.findByUporabnikId(u.getId()).orElse(null);
        Paket paket = n != null ? n.getPaket() : null;
        Integer meja = null;
        if (paket != null) {
            meja = narocnina.omejitev(paket)
                    .map(o -> cilj.turnir() ? o.turnirjev() : o.lig())
                    .orElse(null);
        }
        int uporabljeno = (int) (cilj.turnir()
                ? narocnina.steviloTurnirjevVSezoni(u.getId(), danes)
                : narocnina.steviloLigVSezoni(u.getId(), danes));
        return new Organizator(u.getId(), ime(u), u.getUporabniskoIme(),
                u.getKlub() != null ? u.getKlub().getIme() : null, vir, steje,
                new Poraba(paket, uporabljeno, meja));
    }

    /* Ime in priimek, kot ju je oseba navedla ob registraciji; brez njiju
       e-pošta, da vrstica nikoli ni prazna. */
    private static String ime(Uporabnik u) {
        String ime = u.getPrijavljenoIme() == null ? "" : u.getPrijavljenoIme().trim();
        String priimek = u.getPrijavljeniPriimek() == null ? "" : u.getPrijavljeniPriimek().trim();
        String polno = (ime + " " + priimek).trim();
        return polno.isEmpty() ? u.getUporabniskoIme() : polno;
    }
}
