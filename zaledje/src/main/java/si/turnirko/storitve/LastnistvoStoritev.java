/* Preverjanje lastnistva turnirjev in lig.

   Varnostna veriga (VarnostneNastavitve) pozna samo VLOGO, ne pa cigav je
   posamezen zapis - zato lastnistvo na ravni zapisa razsodimo tukaj. To je
   namerna izjema od pravila "metodne varnosti ni": ker gre za navadno kodo v
   storitvah (in ne @PreAuthorize), jo storitveni testi normalno sprozijo.

   Pravilo: turnir/ligo sme urejati administrator (vse), organizator pa le
   svoje (ustvaril == on) ali od svojega kluba (klubLastnik == njegov klub).
   Klub je pri organizatorju neobvezen; brez kluba upravlja samo svoje.

   UVOZENO TEKMOVANJE JE SAMO ZA BRANJE (V27) - za vse, tudi za administratorja
   in tudi brez varnostnega konteksta. Vir resnice je zveza: popravek v
   Turnirku bi naslednja sinhronizacija povozila. Zato preverba vira stoji
   PRED preverbo vloge. Sinhronizacija sama pise mimo storitev (repozitoriji),
   zato je to pravilo ne ustavi. Opis lige (prehodi v piramidi) in uredniska
   izbira lig na domaci strani nista pravilo tekmovanja in ostaneta dovoljena
   - glej preveriLigaZaOpis.

   Ce varnostnega konteksta ni (interni klic ali storitveni test brez
   nastavljene prijave), preverba lastnika ne omejuje - v produkciji do
   mutacije brez veljavne prijave sploh ne pride (ustavi jo veriga). */
package si.turnirko.storitve;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import si.turnirko.izjeme.DomenskaIzjema;
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

    /* Sporocilo ob poskusu spremembe uvozenega tekmovanja. */
    static final String SAMO_ZA_BRANJE = "Tekmovanje je uvozeno iz uradnega vira zveze (%s) in je"
            + " samo za branje. Popravek vnese zveza, uvoz ga prenese.";

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
        if (turnir.jeUvozen()) {
            throw new DomenskaIzjema(String.format(SAMO_ZA_BRANJE, turnir.getVir().getOznaka()));
        }
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
        if (liga.jeUvozena()) {
            throw new DomenskaIzjema(String.format(SAMO_ZA_BRANJE, liga.getVir().getOznaka()));
        }
        preveri(liga.getUstvaril(), liga.getKlubLastnik(), "lige");
    }

    public void preveriLigaPoId(Long idLiga) {
        preveriLiga(najdiLigo(idLiga));
    }

    /* Preverba za OPIS lige (mesto v piramidi), ki ni pravilo tekmovanja in ga
       vir ne pozna - zato je dovoljen tudi pri uvozeni ligi. */
    public void preveriLigaZaOpis(Long idLiga) {
        Liga liga = najdiLigo(idLiga);
        preveri(liga.getUstvaril(), liga.getKlubLastnik(), "lige");
    }

    public void preveriLigaPoSeriji(Long idSerija) {
        preveriLiga(ligaRepozitorij.najdiZLastnistvomPoSeriji(idSerija)
                .orElseThrow(() -> new NiNajdenoIzjema("Serija koncnice z id " + idSerija + " ne obstaja.")));
    }

    // ---------- Preverba: ekipa, kader, srecanje (liga ALI ekipni turnir) ----------

    /* Ekipa nastopa v ligi ali na ekipnem dogodku turnirja - preverba velja za
       tekmovanje, kateremu pripada. */
    public void preveriPoEkipi(Long idEkipa) {
        var liga = ligaRepozitorij.najdiZLastnistvomPoEkipi(idEkipa);
        if (liga.isPresent()) {
            preveriLiga(liga.get());
            return;
        }
        preveriTurnir(turnirRepozitorij.najdiZLastnistvomPoEkipi(idEkipa)
                .orElseThrow(() -> new NiNajdenoIzjema("Ekipa z id " + idEkipa + " ne obstaja.")));
    }

    public void preveriPoKadru(Long idKader) {
        var liga = ligaRepozitorij.najdiZLastnistvomPoKadru(idKader);
        if (liga.isPresent()) {
            preveriLiga(liga.get());
            return;
        }
        preveriTurnir(turnirRepozitorij.najdiZLastnistvomPoKadru(idKader)
                .orElseThrow(() -> new NiNajdenoIzjema("Vnos kadra z id " + idKader + " ne obstaja.")));
    }

    public void preveriPoSrecanju(Long idSrecanje) {
        var liga = ligaRepozitorij.najdiZLastnistvomPoSrecanju(idSrecanje);
        if (liga.isPresent()) {
            preveriLiga(liga.get());
            return;
        }
        preveriTurnir(turnirRepozitorij.najdiZLastnistvomPoSrecanju(idSrecanje)
                .orElseThrow(() -> new NiNajdenoIzjema("Srecanje z id " + idSrecanje + " ne obstaja.")));
    }

    public void preveriPoTekmiSrecanja(Long idTekma) {
        var liga = ligaRepozitorij.najdiZLastnistvomPoTekmiSrecanja(idTekma);
        if (liga.isPresent()) {
            preveriLiga(liga.get());
            return;
        }
        preveriTurnir(turnirRepozitorij.najdiZLastnistvomPoTekmiSrecanja(idTekma)
                .orElseThrow(() -> new NiNajdenoIzjema("Tekma srecanja z id " + idTekma + " ne obstaja.")));
    }

    // ---------- Jedro ----------

    private Liga najdiLigo(Long idLiga) {
        return ligaRepozitorij.najdiZLastnistvom(idLiga)
                .orElseThrow(() -> new NiNajdenoIzjema("Liga z id " + idLiga + " ne obstaja."));
    }

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
