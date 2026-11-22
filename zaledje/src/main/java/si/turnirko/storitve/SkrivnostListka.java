/* Skrivnost v QR kodi sodniskega listka (branje listkov, faza S1).

   Sodnik izid poslje s svojim telefonom brez prijave (S2). Pravico mu da
   posest listka: QR nosi »TK:<id>:<skrivnost>«, skrivnost pa zna izracunati
   samo streznik. Kdor ima listek v roki (ali njegovo fotografijo), sme poslati
   izid samo te tekme - enako kot na papirju lahko izid zapise samo, kdor ima
   listek.

   Skrivnost je prvih 40 bitov HMAC-SHA256(kljuc, »TK:<id>«) v base32 (A-Z, 2-7):
   8 znakov, ki ostanejo v alfanumericnem nacinu QR in v verziji 1 (21 modulov,
   pri ECC M do 20 znakov), zato se postavitev listka in poravnava ne
   spremenita. 40 bitov zadosca, ker skrivnost velja za eno tekmo, samo dokler ta
   nima izida, in pod omejitvijo hitrosti (S2).

   Kljuc (32 nakljucnih bajtov) ustvari aplikacija ob prvi uporabi in ga zapise v
   tabelo kljuc_aplikacije (V74); hkratna prva uporaba dveh niti zapise enega,
   ker drugi INSERT pade na primarni kljuc in obe nato prebereta istega. Zapis
   tece v LASTNI transakciji (REQUIRES_NEW): prva uporaba je obicajno tisk kod
   (ListkiStoritev.kode, transakcija samo za branje, v kateri INSERT ne gre), kljuc
   pa mora ostati, tudi ce klicateljeva transakcija pade - sicer bi ga ta instanca
   imela v pomnilniku, baza pa ne.

   Skrivnost ne sme v javne DTO-je, dnevnik ali URL: dobi jo samo organizator za
   tisk (ListkiStoritev.kode). */
package si.turnirko.storitve;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class SkrivnostListka {

    private static final Logger dnevnik = LoggerFactory.getLogger(SkrivnostListka.class);

    /* Ime kljuca v tabeli kljuc_aplikacije. */
    static final String IME_KLJUCA = "listek";
    /* Dolzina skrivnosti v znakih base32 (5 bitov na znak). */
    public static final int DOLZINA = 8;
    private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

    private final JdbcTemplate jdbc;
    private final TransactionTemplate lastnaTransakcija;
    private volatile byte[] kljuc;

    public SkrivnostListka(JdbcTemplate jdbc, PlatformTransactionManager transakcije) {
        this.jdbc = jdbc;
        this.lastnaTransakcija = new TransactionTemplate(transakcije);
        this.lastnaTransakcija.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /* Skrivnost listka tekme. */
    public String za(long idTekme) {
        byte[] hmac;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(kljuc(), "HmacSHA256"));
            hmac = mac.doFinal(KodaListka.vsebinaQr(idTekme).getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 ni na voljo.", e);
        }
        return base32(hmac, DOLZINA);
    }

    /* Ali je skrivnost s fotografije prava za to tekmo. Primerjava v stalnem
       casu, da cas odgovora ne izda, koliko znakov se ujema. */
    public boolean velja(long idTekme, String skrivnost) {
        if (skrivnost == null || skrivnost.length() != DOLZINA) {
            return false;
        }
        return MessageDigest.isEqual(za(idTekme).getBytes(StandardCharsets.US_ASCII),
                skrivnost.getBytes(StandardCharsets.US_ASCII));
    }

    /* Vsebina QR kode novega listka: »TK:<id>:<skrivnost>«. */
    public String vsebinaQr(long idTekme) {
        return KodaListka.vsebinaQr(idTekme, za(idTekme));
    }

    /* Kljuc se nalozi (ali ustvari) ze ob zagonu: prva uporaba bi sicer tekla sredi
       tiska kod, ko klicateljeva transakcija ze drzi povezavo, REQUIRES_NEW pa
       potrebuje drugo - vec hkratnih prvih tiskov bi lahko izcrpalo bazen povezav.
       Neuspeh zagona ne ustavi (branje listkov ni nujno za ostalo aplikacijo); kljuc
       se tedaj nalozi ob prvi uporabi. */
    @EventListener(ApplicationReadyEvent.class)
    public void ogrej() {
        try {
            kljuc();
        } catch (RuntimeException e) {
            dnevnik.warn("Kljuca listkov ob zagonu ni bilo mogoce naloziti: {}", e.getMessage());
        }
    }

    private byte[] kljuc() {
        byte[] k = kljuc;
        if (k == null) {
            synchronized (this) {
                k = kljuc;
                if (k == null) {
                    k = lastnaTransakcija.execute(st -> preberiAliUstvari());
                    kljuc = k;
                }
            }
        }
        return k;
    }

    private byte[] preberiAliUstvari() {
        List<byte[]> obstojeci = preberi();
        if (!obstojeci.isEmpty()) {
            return obstojeci.getFirst();
        }
        byte[] nov = new byte[32];
        new SecureRandom().nextBytes(nov);
        int zapisanih = jdbc.update("INSERT INTO kljuc_aplikacije (ime, vrednost, ustvarjeno_ob) VALUES (?, ?, ?) "
                + "ON CONFLICT (ime) DO NOTHING", IME_KLJUCA, nov, Timestamp.valueOf(LocalDateTime.now()));
        if (zapisanih == 1) {
            // pricakovano enkrat (prvi zagon po V74); kasneje pomeni, da je kljuc izginil
            // (obnova baze brez te tabele) in listki, natisnjeni prej, ne veljajo za sodnika
            dnevnik.warn("Ustvarjen nov kljuc listkov: QR kode listkov, natisnjenih prej, sodniska pot ne sprejme vec.");
        }
        // ce je druga instanca hkrati zapisala svojega, velja tisti iz baze
        List<byte[]> zapisan = preberi();
        if (zapisan.isEmpty()) {
            throw new IllegalStateException("Kljuca listkov ni bilo mogoce zapisati.");
        }
        return zapisan.getFirst();
    }

    private List<byte[]> preberi() {
        return jdbc.query("SELECT vrednost FROM kljuc_aplikacije WHERE ime = ?",
                (vrstica, i) -> vrstica.getBytes(1), IME_KLJUCA);
    }

    /* Prvih »znakov« * 5 bitov podatkov v base32 brez polnila. */
    static String base32(byte[] podatki, int znakov) {
        StringBuilder sb = new StringBuilder(znakov);
        int bitov = 0;
        int zaloga = 0;
        int i = 0;
        while (sb.length() < znakov) {
            if (bitov < 5) {
                zaloga = (zaloga << 8) | (podatki[i++] & 0xFF);
                bitov += 8;
            }
            sb.append(BASE32[(zaloga >> (bitov - 5)) & 31]);
            bitov -= 5;
        }
        return sb.toString();
    }
}
