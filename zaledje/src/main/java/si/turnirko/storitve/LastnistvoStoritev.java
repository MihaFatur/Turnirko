/* Preverjanje lastnistva turnirjev in lig.

   Varnostna veriga (VarnostneNastavitve) pozna samo VLOGO, ne pa cigav je
   posamezen zapis - zato lastnistvo na ravni zapisa razsodimo tukaj. To je
   namerna izjema od pravila "metodne varnosti ni": ker gre za navadno kodo v
   storitvah (in ne @PreAuthorize), jo storitveni testi normalno sprozijo.

   Pravilo: turnir/ligo sme urejati administrator (vse), organizator pa le
   svoje (ustvaril == on) ali od svojega kluba (klubLastnik == njegov klub).
   Klub je pri organizatorju neobvezen; brez kluba upravlja samo svoje.

   Ce varnostnega konteksta ni (interni klic ali storitveni test brez
   nastavljene prijave), preverba ne omejuje - v produkciji do mutacije brez
   veljavne prijave sploh ne pride (ustavi jo veriga). */
package si.turnirko.storitve;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.izjeme.PrepovedanoIzjema;
import si.turnirko.modeli.Klub;
import si.turnirko.modeli.Liga;
import si.turnirko.modeli.Turnir;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.LigaRepozitorij;
import si.turnirko.repozitoriji.TurnirRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

@Service
public class LastnistvoStoritev {

    private final UporabnikRepozitorij uporabnikRepozitorij;
    private final TurnirRepozitorij turnirRepozitorij;
    private final LigaRepozitorij ligaRepozitorij;

    public LastnistvoStoritev(UporabnikRepozitorij uporabnikRepozitorij,
                              TurnirRepozitorij turnirRepozitorij,
                              LigaRepozitorij ligaRepozitorij) {
        this.uporabnikRepozitorij = uporabnikRepozitorij;
        this.turnirRepozitorij = turnirRepozitorij;
        this.ligaRepozitorij = ligaRepozitorij;
    }

    /* Trenutno prijavljeni uporabnik ali null, ce prijave ni (gost, interni
       klic, test). Nalozi tudi njegov klub - preverba ga bere. */
    public Uporabnik trenutni() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated() || a instanceof AnonymousAuthenticationToken) {
            return null;
        }
        String ime = a.getName();
        if (ime == null || ime.isBlank() || "anonymousUser".equals(ime)) {
            return null;
        }
        return uporabnikRepozitorij.najdiZVsem(ime).orElse(null);
    }

    // ---------- Oznaka lastnika ob nastanku ----------

    /* Zabelezi trenutnega uporabnika kot lastnika novega turnirja (in posname
       njegov klub). Brez prijave (test) ostane brez lastnika. */
    public void oznaciLastnika(Turnir turnir) {
        Uporabnik jaz = trenutni();
        if (jaz == null) {
            return;
        }
        turnir.setUstvaril(jaz);
        turnir.setKlubLastnik(jaz.getKlub());
    }

    public void oznaciLastnika(Liga liga) {
        Uporabnik jaz = trenutni();
        if (jaz == null) {
            return;
        }
        liga.setUstvaril(jaz);
        liga.setKlubLastnik(jaz.getKlub());
    }

    // ---------- Preverba: turnir ----------

    public void preveriTurnir(Turnir turnir) {
        preveri(turnir.getUstvaril(), turnir.getKlubLastnik(), "turnirja");
    }

    public void preveriTurnirPoId(Long idTurnir) {
        preveriTurnir(turnirRepozitorij.najdiZLastnistvom(idTurnir)
                .orElseThrow(() -> new NiNajdenoIzjema("Turnir z id " + idTurnir + " ne obstaja.")));
    }

    public void preveriTurnirPoDogodku(Long idDogodek) {
        preveriTurnir(turnirRepozitorij.najdiZLastnistvomPoDogodku(idDogodek)
                .orElseThrow(() -> new NiNajdenoIzjema("Dogodek z id " + idDogodek + " ne obstaja.")));
    }

    public void preveriTurnirPoPrijavi(Long idPrijava) {
        preveriTurnir(turnirRepozitorij.najdiZLastnistvomPoPrijavi(idPrijava)
                .orElseThrow(() -> new NiNajdenoIzjema("Prijava z id " + idPrijava + " ne obstaja.")));
    }

    public void preveriTurnirPoTekmi(Long idTekma) {
        preveriTurnir(turnirRepozitorij.najdiZLastnistvomPoTekmi(idTekma)
                .orElseThrow(() -> new NiNajdenoIzjema("Tekma z id " + idTekma + " ne obstaja.")));
    }

    // ---------- Preverba: liga ----------

    public void preveriLiga(Liga liga) {
        preveri(liga.getUstvaril(), liga.getKlubLastnik(), "lige");
    }

    public void preveriLigaPoId(Long idLiga) {
        preveriLiga(ligaRepozitorij.najdiZLastnistvom(idLiga)
                .orElseThrow(() -> new NiNajdenoIzjema("Liga z id " + idLiga + " ne obstaja.")));
    }

    public void preveriLigaPoEkipi(Long idEkipa) {
        preveriLiga(ligaRepozitorij.najdiZLastnistvomPoEkipi(idEkipa)
                .orElseThrow(() -> new NiNajdenoIzjema("Ekipa z id " + idEkipa + " ne obstaja.")));
    }

    public void preveriLigaPoKadru(Long idKader) {
        preveriLiga(ligaRepozitorij.najdiZLastnistvomPoKadru(idKader)
                .orElseThrow(() -> new NiNajdenoIzjema("Vnos kadra z id " + idKader + " ne obstaja.")));
    }

    public void preveriLigaPoSrecanju(Long idSrecanje) {
        preveriLiga(ligaRepozitorij.najdiZLastnistvomPoSrecanju(idSrecanje)
                .orElseThrow(() -> new NiNajdenoIzjema("Srecanje z id " + idSrecanje + " ne obstaja.")));
    }

    public void preveriLigaPoTekmiSrecanja(Long idTekma) {
        preveriLiga(ligaRepozitorij.najdiZLastnistvomPoTekmiSrecanja(idTekma)
                .orElseThrow(() -> new NiNajdenoIzjema("Tekma srecanja z id " + idTekma + " ne obstaja.")));
    }

    // ---------- Jedro ----------

    private void preveri(Uporabnik lastnik, Klub klubLastnik, String kaj) {
        Uporabnik jaz = trenutni();
        if (jaz == null) {
            return; // ni varnostnega konteksta - produkcijo varuje veriga
        }
        if (jaz.getVloga() == Vloga.ADMIN) {
            return; // administrator sme vse
        }
        if (jaz.getVloga() == Vloga.ORGANIZATOR && smeUrejati(jaz, lastnik, klubLastnik)) {
            return;
        }
        throw new PrepovedanoIzjema("Nimate pravice urejati tega " + kaj + ".");
    }

    /* Organizator sme, ce je zapis njegov (ustvaril) ali od njegovega kluba. */
    private boolean smeUrejati(Uporabnik jaz, Uporabnik lastnik, Klub klubLastnik) {
        if (lastnik != null && lastnik.getId().equals(jaz.getId())) {
            return true;
        }
        return klubLastnik != null && jaz.getKlub() != null
                && klubLastnik.getId().equals(jaz.getKlub().getId());
    }
}
