/* Spremljanje igralcev (V57): navijac na domaci strani spremlja ljudi, ne le
   lige. Meja stevila je pravilo paketa (SpremljaniIgralciStoritev.
   NAJVEC_BREZPLACNO), povzetek pa bere samo to, kar je na profilu ze javno. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import si.turnirko.dto.DomovIgralecDto;
import si.turnirko.dto.EkipaVnos;
import si.turnirko.dto.KaderVnos;
import si.turnirko.dto.LigaVnos;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.izjeme.PrepovedanoIzjema;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Klub;
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.Srecanje;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.StatusSrecanja;
import si.turnirko.modeli.Turnir;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.SrecanjeRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

class SpremljaniIgralciTest extends IntegracijskiTest {

    private static final int RAZMIK = 7;

    @Autowired private SpremljaniIgralciStoritev spremljani;
    @Autowired private LigaStoritev ligaStoritev;
    @Autowired private IgralciZdruzitevStoritev zdruzitev;
    @Autowired private SrecanjeRepozitorij srecanjeRepozitorij;
    @Autowired private UporabnikRepozitorij uporabnikRepozitorij;
    @PersistenceContext private EntityManager em;

    @AfterEach
    void pocisti() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void navijacSpremljaIgralcaInGaOdstrani() {
        Igralec ana = noviIgralec("Ana", "Novak");
        prijava(navijac("navijac1@test"));

        assertEquals(List.of(ana.getId()), spremljani.spremljaj(ana.getId()));
        assertEquals(List.of(ana.getId()), spremljani.idji());
        assertEquals(List.of(), spremljani.nehajSpremljati(ana.getId()));
    }

    /* Gumb je preklop: ponoven klik na istega igralca ni napaka in ne porabi
       drugega mesta. */
    @Test
    void ponovnoSpremljanjeIstegaIgralcaNiNapaka() {
        Igralec ana = noviIgralec("Ana", "Novak");
        prijava(navijac("navijac2@test"));

        spremljani.spremljaj(ana.getId());
        assertEquals(List.of(ana.getId()), spremljani.spremljaj(ana.getId()));
    }

    /* Drugega igralca brezplacen racun ne dobi, prvega pa tudi ne izgubi tiho:
       sporocilo pove, koga ze spremlja. */
    @Test
    void drugegaIgralcaZavrneDokJePrviSpremljan() {
        Igralec ana = noviIgralec("Ana", "Novak");
        Igralec bor = noviIgralec("Bor", "Kos");
        prijava(navijac("navijac3@test"));
        spremljani.spremljaj(ana.getId());

        DomenskaIzjema izjema = assertThrows(DomenskaIzjema.class, () -> spremljani.spremljaj(bor.getId()));
        assertTrue(izjema.getMessage().contains("Ana Novak"), izjema.getMessage());
        assertEquals(List.of(ana.getId()), spremljani.idji(), "prvi ostane");

        spremljani.nehajSpremljati(ana.getId());
        assertEquals(List.of(bor.getId()), spremljani.spremljaj(bor.getId()), "po odstranitvi je mesto prosto");
    }

    /* Meja je osebna: dva racuna spremljata istega igralca neodvisno. */
    @Test
    void dvaRacunaSpremljataIstegaIgralca() {
        Igralec ana = noviIgralec("Ana", "Novak");
        prijava(navijac("starsA@test"));
        spremljani.spremljaj(ana.getId());
        prijava(navijac("starsB@test"));
        spremljani.spremljaj(ana.getId());

        assertEquals(List.of(ana.getId()), spremljani.idji());
    }

    @Test
    void gostNeSmeSpremljatiIgralca() {
        Igralec ana = noviIgralec("Ana", "Novak");
        assertThrows(PrepovedanoIzjema.class, () -> spremljani.spremljaj(ana.getId()));
        assertThrows(PrepovedanoIzjema.class, () -> spremljani.povzetki());
    }

    @Test
    void neobstojecegaIgralcaZavrne() {
        prijava(navijac("navijac4@test"));
        assertThrows(NiNajdenoIzjema.class, () -> spremljani.spremljaj(999_999L));
    }

    /* Igralec ima svoj profil pod »Moj profil«; spremljanje samega sebe bi na
       domaci strani pokazalo isto, kar ze ima. */
    @Test
    void igralecSebeNeSpremljaDrugegaPac() {
        Igralec jaz = noviIgralec("Jaz", "Igralec");
        Igralec drug = noviIgralec("Drug", "Igralec");
        Uporabnik u = new Uporabnik("igralec@test", "{bcrypt}x", Vloga.IGRALEC);
        u.setStatus(StatusRacuna.POTRJEN);
        u.setIgralec(jaz);
        uporabnikRepozitorij.save(u);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(u.getUporabniskoIme(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_IGRALEC"))));

        assertThrows(NeveljavenVnosIzjema.class, () -> spremljani.spremljaj(jaz.getId()));
        assertEquals(List.of(drug.getId()), spremljani.spremljaj(drug.getId()));
    }

    // ---------- povzetek ----------

    @Test
    void povzetekNosiImeKlubInRatingBrezOsebnihPodatkov() {
        Klub klub = klubRepozitorij.save(new Klub("Klub povzetka", null));
        Igralec ana = noviIgralec("Ana", "Novak");
        ana.setKlub(klub);
        ana.setEmail("ana@zasebno.si");
        igralecRepozitorij.save(ana);
        prijava(navijac("navijac5@test"));
        spremljani.spremljaj(ana.getId());

        List<DomovIgralecDto> povzetki = spremljani.povzetki();

        assertEquals(1, povzetki.size());
        DomovIgralecDto p = povzetki.get(0);
        assertEquals("Ana Novak", p.polnoIme());
        assertEquals("Klub povzetka", p.klub());
        assertEquals(ana.getId(), p.idIgralec());
        assertTrue(p.zadnje().isEmpty());
        assertNull(p.naslednjeSrecanje());
        assertNull(p.prihajajociTurnir());
        assertFalse(p.toString().contains("zasebno.si"), "e-poste ni v povzetku");
    }

    /* Naslednje srecanje je prvo nekoncano srecanje EKIPE igralca - pravilo
       "naslednje kolo" lige (PotekLige), ne prvo srecanje lige. */
    @Test
    void naslednjeSrecanjeJePrvoNekoncanoSrecanjeIgralcoveEkipe() {
        LocalDate prvoKolo = PotekLige.danes().plusDays(1);
        Liga4 liga = pripraviLigo(prvoKolo);
        prijava(navijac("navijac6@test"));
        spremljani.spremljaj(liga.igralec().getId());

        DomovIgralecDto.NaslednjeSrecanje naslednje = spremljani.povzetki().get(0).naslednjeSrecanje();

        assertNotNull(naslednje);
        assertEquals(liga.idLige(), naslednje.idLiga());
        assertEquals(1, naslednje.kolo());
        assertFalse(naslednje.ekipa().isBlank());
        assertFalse(naslednje.nasprotnik().isBlank());
        Srecanje s = srecanjeRepozitorij.findById(naslednje.idSrecanje()).orElseThrow();
        assertTrue(s.getEkipaDomaci().getId().equals(liga.idEkipe())
                        || s.getEkipaGost().getId().equals(liga.idEkipe()),
                "srecanje je njegove ekipe");

        // odigrano srecanje ni vec "naslednje": naslednje je v poznejsem kolu
        s.setStatus(StatusSrecanja.KONCANO);
        srecanjeRepozitorij.save(s);
        DomovIgralecDto.NaslednjeSrecanje potem = spremljani.povzetki().get(0).naslednjeSrecanje();
        assertNotNull(potem);
        assertTrue(potem.kolo() > 1, "po koncanem srecanju pride naslednje kolo, ne isto");
        assertEquals(prvoKolo.plusDays((long) RAZMIK * (potem.kolo() - 1)), potem.zacetek().toLocalDate());
    }

    /* Liga, katere termini so vsi mimo, ne obljublja srecanja: nevpisan izid
       s starim datumom ni "naslednje" (isto kot pri PotekLige). */
    @Test
    void srecanjeSStarimTerminomNiNaslednje() {
        Liga4 liga = pripraviLigo(PotekLige.danes().minusDays(60));
        prijava(navijac("navijac7@test"));
        spremljani.spremljaj(liga.igralec().getId());

        assertNull(spremljani.povzetki().get(0).naslednjeSrecanje());
    }

    @Test
    void prihajajociTurnirJeNajzgodnejsiNeZakljucenTurnirPrijave() {
        Dogodek dogodek = pripraviDogodek(2);
        Turnir turnir = dogodek.getTurnir();
        turnir.setDatumZacetka(PotekLige.danes().plusDays(10));
        turnirRepozitorij.save(turnir);
        Igralec igralec = prijavePoVrsti(dogodek.getId()).get(0).getIgralec();
        prijava(navijac("navijac8@test"));
        spremljani.spremljaj(igralec.getId());

        DomovIgralecDto.PrihajajociTurnir prihajajoci = spremljani.povzetki().get(0).prihajajociTurnir();

        assertNotNull(prihajajoci);
        assertEquals(turnir.getId(), prihajajoci.idTurnir());
        assertEquals(PotekLige.danes().plusDays(10), prihajajoci.datum());

        turnir.setDatumZacetka(PotekLige.danes().minusDays(1));
        turnirRepozitorij.save(turnir);
        assertNull(spremljani.povzetki().get(0).prihajajociTurnir(), "turnir z datumom mimo ni prihajajoc");
    }

    // ---------- zdruzitev ----------

    /* Zdruzitev preusmeri vse tuje kljuce (CLAUDE.md): racun, ki je spremljal
       odvecni zapis, po zdruzitvi spremlja obdrzanega. */
    @Test
    void zdruzitevIgralcevPreusmeriSpremljanje() {
        Igralec obdrzan = noviIgralec("Ana", "Novak");
        Igralec odvec = noviIgralec("Ana", "Novak");
        Uporabnik racun = navijac("navijac9@test");
        prijava(racun);
        spremljani.spremljaj(odvec.getId());

        zdruzitev.zdruzi(obdrzan.getId(), odvec.getId());
        em.clear();

        assertEquals(List.of(obdrzan.getId()), spremljani.idji());
    }

    /* Racun, ki je spremljal oba zapisa iste osebe, obdrzi enega - primarni
       kljuc (racun, igralec) sicer zdruzitev podre. */
    @Test
    void zdruzitevRacunuKiJeSpremljalObaPustiEnega() {
        Igralec obdrzan = noviIgralec("Ana", "Novak");
        Igralec odvec = noviIgralec("Ana", "Novak");
        Uporabnik racun = navijac("navijac10@test");
        // dva zapisa istega racuna, mimo meje storitve (meja je pravilo paketa, ne sheme)
        vstaviSpremljanje(racun, obdrzan);
        vstaviSpremljanje(racun, odvec);

        zdruzitev.zdruzi(obdrzan.getId(), odvec.getId());
        em.clear();

        prijava(racun);
        assertEquals(List.of(obdrzan.getId()), spremljani.idji());
    }

    // ---------- priprava ----------

    private record Liga4(Long idLige, Igralec igralec, Long idEkipe) {}

    /* Liga s stirimi ekipami po tri igralci, razpored na sedem dni. Spremljani
       igralec je v kadru prve ekipe. */
    private Liga4 pripraviLigo(LocalDate prvoKolo) {
        LigaVnos vnos = new LigaVnos("Test liga igralca", "2026/27", SpolKategorija.MOSKI,
                FormatSrecanja.SNTL, 5, null, false, 2, 1, 0, true, false, RavenTekmovanja.URADNO, false, null,
                prvoKolo.atTime(18, 0), RAZMIK);
        Long idLige = ligaStoritev.ustvari(vnos).id();
        Igralec spremljan = null;
        Long idEkipe = null;
        for (int i = 1; i <= 4; i++) {
            Klub klub = klubRepozitorij.save(new Klub("Klub K" + i, null));
            var ekipa = ligaStoritev.dodajEkipo(idLige, new EkipaVnos(klub.getId(), null, null));
            for (int j = 1; j <= 3; j++) {
                Igralec igralec = noviIgralec("Ime" + i + j, "Priimek" + i + j);
                ligaStoritev.dodajVKader(ekipa.id(), new KaderVnos(igralec.getId(), j));
                if (i == 1 && j == 1) {
                    spremljan = igralec;
                    idEkipe = ekipa.id();
                }
            }
        }
        ligaStoritev.generirajRazpored(idLige);
        return new Liga4(idLige, spremljan, idEkipe);
    }

    private void vstaviSpremljanje(Uporabnik racun, Igralec igralec) {
        em.createNativeQuery("INSERT INTO spremljan_igralec (id_racun, id_igralec, dodano_ob)"
                        + " VALUES (?1, ?2, '2026-10-29')")
                .setParameter(1, racun.getId())
                .setParameter(2, igralec.getId())
                .executeUpdate();
    }

    private Uporabnik navijac(String prijavnoIme) {
        Uporabnik u = new Uporabnik(prijavnoIme, "{bcrypt}x", Vloga.NAVIJAC);
        u.setStatus(StatusRacuna.POTRJEN);
        return uporabnikRepozitorij.save(u);
    }

    private void prijava(Uporabnik u) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(u.getUporabniskoIme(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_NAVIJAC"))));
    }
}
