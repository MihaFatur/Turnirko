/* Obvestilo administratorju, da racun caka na njegovo potrditev.

   Racun caka na admina, ko je lastnik naslova vpisal kodo (in skrbnik
   soglasje, ce je potrebno) in - pri igralcu - samodejna povezava z igralcem
   ni uspela (RegistracijaStoritev); organizatorja potrdi vedno admin. Takrat
   racun steje tudi nabiralnik v Dostopih (steviloCakajocihNaAdmina). Do
   takrat je racun samo rezervacija naslova in admina ne zadeva, zato obvestila
   ni ze ob registraciji.

   Naslov prejemnika je nastavitev streznika (turnirko.posta.admin); prazna
   pomeni, da obvestil ni. Pot ne sme nikoli ovirati registracije: posiljanje
   gre po potrditvi transakcije (PostaStoritev) in neuspeh posiljatelja se
   le zapise v dnevnik. */
package si.turnirko.storitve;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import si.turnirko.modeli.CiklusPlacila;
import si.turnirko.modeli.Narocnina;
import si.turnirko.modeli.Paket;
import si.turnirko.modeli.StatusNarocnine;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.posta.PostaStoritev;
import si.turnirko.posta.SporociloAdminu;
import si.turnirko.repozitoriji.NarocninaRepozitorij;

@Service
public class ObvestiloAdminuStoritev {

    /* Zakaj racun caka - admin po tem vidi, kaj ga caka. Pri igralcu je to
       razlog, da ga ujemanje po imenu in datumu rojstva ni povezalo samo. */
    public enum Razlog {
        NI_ZAPISA("V šifrantu ni igralca z istim imenom, priimkom in datumom rojstva. Igralec je morda vpisan"
                + " drugače (tipkarska napaka, drugo ime) ali pa ga v šifrantu še ni."),
        VEC_ZAPISOV("V šifrantu je več igralcev z istim imenom, priimkom in datumom rojstva - pravega mora"
                + " izbrati admin."),
        ZAPIS_ZE_IMA_RACUN("Igralec z istim imenom, priimkom in datumom rojstva že ima drug račun."),
        ORGANIZATOR("Organizatorja vedno potrdi admin: s potrditvijo račun dobi pravico ustvarjati tekmovanja,"
                + " po želji tudi pripadnost klubu.");

        private final String besedilo;

        Razlog(String besedilo) {
            this.besedilo = besedilo;
        }
    }

    private final PostaStoritev posta;
    private final NarocninaRepozitorij narocninaRepozitorij;
    private final String prejemnik;
    private final String koren;

    public ObvestiloAdminuStoritev(PostaStoritev posta,
                                   NarocninaRepozitorij narocninaRepozitorij,
                                   @Value("${turnirko.posta.admin:}") String prejemnik,
                                   @Value("${turnirko.stripe.vrni-na}") String koren) {
        this.posta = posta;
        this.narocninaRepozitorij = narocninaRepozitorij;
        this.prejemnik = prejemnik == null ? "" : prejemnik.trim();
        this.koren = koren;
    }

    /* Paket se bere iz narocnine racuna: placljiv paket (Premium igralca,
       Basic/Plus/Pro organizatorja) racun ustvari sele webhook po placilu,
       torej je vrstica ob potrditvi naslova ze tam; brez nje racun nima
       placanega paketa. */
    public void zahtevaCaka(Uporabnik u, Razlog razlog) {
        if (prejemnik.isEmpty()) {
            return;
        }
        Optional<Narocnina> narocnina = narocninaRepozitorij.findByUporabnikId(u.getId())
                .filter(n -> n.getPaket() != Paket.BREZPLACNO && n.getStatus() == StatusNarocnine.AKTIVNA);
        posta.poslji(prejemnik, SporociloAdminu.sestavi(new SporociloAdminu.Zahteva(
                u.getVloga() == Vloga.ORGANIZATOR,
                u.getPrijavljenoIme() + " " + u.getPrijavljeniPriimek(),
                u.getUporabniskoIme(),
                u.getKlubZelja() == null ? null : u.getKlubZelja().getIme(),
                narocnina.map(n -> n.getPaket().kratekNaziv()).orElse(null),
                narocnina.map(n -> n.getCiklus() == CiklusPlacila.LETNO ? "letno" : "mesečno").orElse(null),
                narocnina.map(Narocnina::getCenaObSklenitvi).orElse(null),
                u.getEmailSkrbnika() != null,
                razlog.besedilo,
                u.getUstvarjenOb() == null ? LocalDate.now() : u.getUstvarjenOb().toLocalDate(),
                koren + "/racuni")));
    }
}
