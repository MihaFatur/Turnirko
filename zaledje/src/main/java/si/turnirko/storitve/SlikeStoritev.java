/* Slike in logotip tekmovanja (V54).

   Nalaganje in izbira sta locena koraka: slika gre najprej v knjiznico
   (POST /slike), nato jo organizator izbere za ligo ali turnir. Tako logotip
   Lige Savinja nalozi enkrat, ligam A-D in naslednji sezoni pa ga izbere.

   Logotip je PRIKAZ, ne pravilo tekmovanja (kot skriti razdelki Zanimivosti):
   sme ga lastnik (ali klubski kolega) in admin, v vsakem stanju in tudi pri
   uvozenem tekmovanju - preverba je LastnistvoStoritev.preveri...ZaPrikazPoId.

   Tujo sliko sme organizator uporabiti samo, ce je ze javna (nosi jo kaksno
   tekmovanje). Id-ji slik so zaporedni in ugibanje ne sme odpreti logotipa, ki
   ga je kdo nalozil, a se ga ni objavil. */
package si.turnirko.storitve;

import java.awt.image.BufferedImage;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.SlikaDto;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.izjeme.PrepovedanoIzjema;
import si.turnirko.izjeme.PrevecZahtevIzjema;
import si.turnirko.modeli.Liga;
import si.turnirko.modeli.NamenSlike;
import si.turnirko.modeli.Slika;
import si.turnirko.modeli.SlikaRazlicica;
import si.turnirko.modeli.Turnir;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.VelikostSlike;
import si.turnirko.modeli.Vloga;
import si.turnirko.pomozno.ObdelavaSlik;
import si.turnirko.pomozno.ObdelavaSlik.Zapis;
import si.turnirko.repozitoriji.LigaRepozitorij;
import si.turnirko.repozitoriji.PokroviteljRepozitorij;
import si.turnirko.repozitoriji.SlikaRazlicicaRepozitorij;
import si.turnirko.repozitoriji.SlikaRepozitorij;
import si.turnirko.repozitoriji.TurnirRepozitorij;

@Service
public class SlikeStoritev {

    /* Nalaganj na racun na uro. Organizator jih potrebuje nekaj (poskusi z belim
       ozadjem in brez); vec je ze poskus, da bi kdo bazo napolnil s slikami. */
    static final int NAJVEC_NALAGANJ_NA_URO = 30;

    private final SlikaRepozitorij slikaRepozitorij;
    private final SlikaRazlicicaRepozitorij razlicicaRepozitorij;
    private final LigaRepozitorij ligaRepozitorij;
    private final TurnirRepozitorij turnirRepozitorij;
    private final PokroviteljRepozitorij pokroviteljRepozitorij;
    private final LastnistvoStoritev lastnistvoStoritev;
    private final OmejevalnikPoskusov omejevalnik;

    public SlikeStoritev(SlikaRepozitorij slikaRepozitorij,
                         SlikaRazlicicaRepozitorij razlicicaRepozitorij,
                         LigaRepozitorij ligaRepozitorij,
                         TurnirRepozitorij turnirRepozitorij,
                         PokroviteljRepozitorij pokroviteljRepozitorij,
                         LastnistvoStoritev lastnistvoStoritev,
                         OmejevalnikPoskusov omejevalnik) {
        this.slikaRepozitorij = slikaRepozitorij;
        this.razlicicaRepozitorij = razlicicaRepozitorij;
        this.ligaRepozitorij = ligaRepozitorij;
        this.turnirRepozitorij = turnirRepozitorij;
        this.pokroviteljRepozitorij = pokroviteljRepozitorij;
        this.lastnistvoStoritev = lastnistvoStoritev;
        this.omejevalnik = omejevalnik;
    }

    // ---------- Knjiznica ----------

    /* Prebere, pomanjsa in zapise vse tri razlicice. Ista datoteka, nalozena
       drugic, je ista slika (zgostitev je enolicna) - knjiznica nima dvojnikov.
       Kdor jo nalozi znova, jo dobi v svojo knjiznico: s tem je pokazal, da
       datoteko ima. */
    @Transactional
    public SlikaDto nalozi(byte[] vsebina) {
        Uporabnik jaz = lastnistvoStoritev.trenutni();
        if (jaz != null && !omejevalnik.dovoliInZabelezi(
                "slika:" + jaz.getId(), NAJVEC_NALAGANJ_NA_URO, Duration.ofHours(1))) {
            throw new PrevecZahtevIzjema("Preveč nalaganj. Poskusi čez eno uro.");
        }
        Long idJaz = jaz != null ? jaz.getId() : null;

        BufferedImage tisk = ObdelavaSlik.pomanjsaj(ObdelavaSlik.preberi(vsebina), ObdelavaSlik.STRANICA_TISK);
        Zapis zapisTisk = ObdelavaSlik.zapisi(tisk);
        String zgostitev = zgostitev(zapisTisk.vsebina());

        Optional<Slika> obstojeca = slikaRepozitorij.findByZgostitev(zgostitev);
        if (obstojeca.isPresent()) {
            Slika slika = obstojeca.get();
            if (idJaz != null) {
                slika.setIdNalozil(idJaz);
            }
            return SlikaDto.iz(slika);
        }

        Slika slika = slikaRepozitorij.save(new Slika(zgostitev, NamenSlike.LOGOTIP,
                zapisTisk.sirina(), zapisTisk.visina(), idJaz));
        shrani(slika, VelikostSlike.TISK, zapisTisk);
        shrani(slika, VelikostSlike.ZASLON,
                ObdelavaSlik.zapisi(ObdelavaSlik.pomanjsaj(tisk, ObdelavaSlik.STRANICA_ZASLON)));
        shrani(slika, VelikostSlike.DELJENJE, ObdelavaSlik.zapisi(ObdelavaSlik.zaDeljenje(tisk)));
        return SlikaDto.iz(slika);
    }

    /* Logotipi, ki jih sme prijavljeni izbrati: admin vse, organizator svoje in
       tiste s tekmovanj, ki jih sme urejati (glej SlikaRepozitorij). */
    @Transactional(readOnly = true)
    public List<SlikaDto> knjiznica() {
        Uporabnik jaz = lastnistvoStoritev.trenutni();
        if (jaz == null) {
            return List.of();
        }
        List<Slika> slike = jaz.getVloga() == Vloga.ADMIN
                ? slikaRepozitorij.findByNamenOrderByIdDesc(NamenSlike.LOGOTIP)
                : slikaRepozitorij.knjiznicaOrganizatorja(NamenSlike.LOGOTIP, jaz.getId(),
                        jaz.getKlub() != null ? jaz.getKlub().getId() : null);
        return slike.stream().map(SlikaDto::iz).toList();
    }

    /* Izbris iz knjiznice: samo kdor jo je nalozil (ali admin) in samo, dokler je
       ne nosi nobeno tekmovanje - sicer bi logotip izginil s tuje strani. */
    @Transactional
    public void izbrisi(Long idSlika) {
        Slika slika = najdi(idSlika);
        Uporabnik jaz = lastnistvoStoritev.trenutni();
        if (jaz != null && jaz.getVloga() != Vloga.ADMIN && !jaz.getId().equals(slika.getIdNalozil())) {
            throw new PrepovedanoIzjema("Izbrišeš lahko samo logotip, ki si ga naložil sam.");
        }
        long uporab = steviloUporab(idSlika);
        if (uporab > 0) {
            throw new DomenskaIzjema("Logotip je v rabi na " + uporab + " " + tekmovanjih(uporab)
                    + ". Najprej ga odstrani s tekmovanj.");
        }
        razlicicaRepozitorij.pobrisiZaSliko(idSlika);
        slikaRepozitorij.delete(slika);
    }

    /* Bajti razlicice za javno pot. Prazno, ce slike ali razlicice ni. */
    @Transactional(readOnly = true)
    public Optional<SlikaRazlicica> vsebina(String zgostitev, VelikostSlike velikost) {
        return razlicicaRepozitorij.najdi(zgostitev, velikost);
    }

    // ---------- Logotip tekmovanja ----------

    @Transactional
    public SlikaDto nastaviLogotipLige(Long idLiga, Long idSlika) {
        lastnistvoStoritev.preveriLigaZaPrikazPoId(idLiga);
        Slika slika = zaUporabo(idSlika);
        najdiLigo(idLiga).setLogotip(slika);
        return SlikaDto.iz(slika);
    }

    @Transactional
    public void odstraniLogotipLige(Long idLiga) {
        lastnistvoStoritev.preveriLigaZaPrikazPoId(idLiga);
        najdiLigo(idLiga).setLogotip(null);
    }

    @Transactional
    public SlikaDto nastaviLogotipTurnirja(Long idTurnir, Long idSlika) {
        lastnistvoStoritev.preveriTurnirZaPrikazPoId(idTurnir);
        Slika slika = zaUporabo(idSlika);
        najdiTurnir(idTurnir).setLogotip(slika);
        return SlikaDto.iz(slika);
    }

    @Transactional
    public void odstraniLogotipTurnirja(Long idTurnir) {
        lastnistvoStoritev.preveriTurnirZaPrikazPoId(idTurnir);
        najdiTurnir(idTurnir).setLogotip(null);
    }

    // ---------- Pomozno ----------

    /* Slika, ki jo sme prijavljeni dati na tekmovanje (logotip ali pokrovitelj,
       PokroviteljiStoritev): svojo, ze javno (nosi jo kaksno tekmovanje) ali -
       admin - katerokoli. Brez varnostnega konteksta (interni klic, test) kot
       drugod ne omejuje. */
    Slika zaUporabo(Long idSlika) {
        Slika slika = najdi(idSlika);
        if (slika.getNamen() != NamenSlike.LOGOTIP) {
            throw new NeveljavenVnosIzjema("Ta slika ni logotip.");
        }
        Uporabnik jaz = lastnistvoStoritev.trenutni();
        if (jaz == null || jaz.getVloga() == Vloga.ADMIN
                || jaz.getId().equals(slika.getIdNalozil()) || steviloUporab(idSlika) > 0) {
            return slika;
        }
        throw new PrepovedanoIzjema("Tega logotipa nisi naložil ti. Naloži ga sam.");
    }

    /* Logotip tekmovanja ali logotip pokrovitelja (V56) - oboje je javna raba. */
    private long steviloUporab(Long idSlika) {
        return ligaRepozitorij.countByLogotipId(idSlika) + turnirRepozitorij.countByLogotipId(idSlika)
                + pokroviteljRepozitorij.countBySlikaId(idSlika);
    }

    private void shrani(Slika slika, VelikostSlike velikost, Zapis zapis) {
        razlicicaRepozitorij.save(new SlikaRazlicica(slika.getId(), velikost, zapis.vrsta(),
                zapis.sirina(), zapis.visina(), zapis.vsebina()));
    }

    private Slika najdi(Long idSlika) {
        return slikaRepozitorij.findById(idSlika)
                .orElseThrow(() -> new NiNajdenoIzjema("Slika z id " + idSlika + " ne obstaja."));
    }

    private Liga najdiLigo(Long idLiga) {
        return ligaRepozitorij.findById(idLiga)
                .orElseThrow(() -> new NiNajdenoIzjema("Liga z id " + idLiga + " ne obstaja."));
    }

    private Turnir najdiTurnir(Long idTurnir) {
        return turnirRepozitorij.findById(idTurnir)
                .orElseThrow(() -> new NiNajdenoIzjema("Turnir z id " + idTurnir + " ne obstaja."));
    }

    static String zgostitev(byte[] vsebina) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(vsebina));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /* »na 1 tekmovanju«, »na 2 tekmovanjih« (mestnik je za vsa stevila isti
       razen ednine). */
    private static String tekmovanjih(long n) {
        return n == 1 ? "tekmovanju" : "tekmovanjih";
    }
}
