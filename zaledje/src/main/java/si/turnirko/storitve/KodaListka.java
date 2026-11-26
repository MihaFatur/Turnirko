/* Koda tekme na sodniskem listku: QR koda in natisnjena stevilka ob njej.

   QR koda nosi »TK:<id tekme>:<skrivnost>« (alfanumericni nacin QR: velike crke,
   stevke in dvopicje, zato je koda majhna - verzija 1 - in berljiva tudi na
   stisnjenem listku). Skrivnost (S1, SkrivnostListka) da sodniku pravico poslati
   izid te tekme brez prijave (S2). Listki, natisnjeni pred S1 (22. 11. 2026),
   nosijo samo »TK:<id>«: bralnik jih se vedno prebere, sodniska pot pa jih ne
   sprejme. Natisnjena stevilka »T-<id>« je za cloveka in za bralnika,
   ce QR na fotografiji ni berljiva (zamegljena, prepognjen vogal).

   Koda tekmo doloci deterministicno: brez nje bi jo moral bralnik ugibati po
   imenih, kar pri istem paru v dveh kategorijah ni mogoce. Zato je QR prva pot,
   natisnjena stevilka druga, imena sele tretja (UjemanjeTekmeListka).

   Razred ne pozna baze ne Springa. Vmesnik zapise isto kodo
   (vmesnik/src/pomozno/kodaListka.ts) - ob spremembi zapisa spremeni oba. */
package si.turnirko.storitve;

import java.awt.image.BufferedImage;
import java.util.EnumMap;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.multi.qrcode.QRCodeMultiReader;

import si.turnirko.pomozno.ObdelavaSlik;

public final class KodaListka {

    /* QR: »TK:48213:ABCD2345« (pred S1 samo »TK:48213«); natisnjeno: »T-48213«.
       Bralnik vrne natisnjeno, kakor jo vidi (lahko s presledkom ali brez
       vezaja), zato je vzorec za branje ohlapen. */
    private static final Pattern QR = Pattern.compile("^TK:(\\d{1,12})(?::([A-Z2-7]{8}))?$");
    private static final Pattern NATISNJENA = Pattern.compile("T\\s*[-–:]?\\s*(\\d{1,12})");
    /* List vadnice stevk: »TKV:<razlicica postavitve>:<id lista>« (vadnica.ts v vmesniku). */
    private static final Pattern QR_VADNICE = Pattern.compile("^TKV:(\\d{1,3}):(\\d{1,12})$");
    /* Ligaski zapisnik za bralnik (26. 11. 2026): »TZ:<id natisa>:<skrivnost>«.
       Natis (natis_zapisnika) pove srecanje, obliko in kader, kakor je bil natisnjen. */
    private static final Pattern QR_ZAPISNIKA = Pattern.compile("^TZ:(\\d{1,9}):([A-Z2-7]{8})$");

    /* Telefonska fotografija ima tudi po pomanjsanju v brskalniku do 2000 px; QR
       je na listku ~14 mm, torej dovolj velika tudi v manjsi razlicici, ki se
       prebere hitreje in z manj sumom. */
    private static final int MANJSA_STRANICA = 1200;

    private KodaListka() {}

    /* Zapis brez skrivnosti: listki pred S1 in vhod za HMAC skrivnosti. */
    public static String vsebinaQr(long idTekme) {
        return "TK:" + idTekme;
    }

    /* Zapis novega listka s skrivnostjo (SkrivnostListka). */
    public static String vsebinaQr(long idTekme, String skrivnost) {
        return "TK:" + idTekme + ":" + skrivnost;
    }

    /* Vhod za HMAC skrivnosti zapisnika (SkrivnostListka.zaZapisnik). */
    public static String vsebinaQrZapisnika(long idNatisa) {
        return "TZ:" + idNatisa;
    }

    public static String vsebinaQrZapisnika(long idNatisa, String skrivnost) {
        return "TZ:" + idNatisa + ":" + skrivnost;
    }

    public static String natisnjena(long idTekme) {
        return "T-" + idTekme;
    }

    /* Id tekme iz natisnjene stevilke, kot jo je prebral bralnik. */
    public static Optional<Long> izNatisnjene(String zapis) {
        if (zapis == null) {
            return Optional.empty();
        }
        Matcher m = NATISNJENA.matcher(zapis.toUpperCase());
        return m.find() ? Optional.of(Long.parseLong(m.group(1))) : Optional.empty();
    }

    /* Vse razlicne kode tekem, ki jih je mogoce prebrati s slike. Prazno, ce kode
       ni; vec kot ena pomeni dva listka na eni fotografiji (organizator je
       namenoma omejen na enega, ker manjse stevke pomenijo vec napak).

       Poskusi se nekaj razlicic, ker telefonska fotografija ni skener: polna in
       manjsa slika, dva nacina dvobarvne pretvorbe (HybridBinarizer za neenakomerno
       svetlobo v dvorani, GlobalHistogram za mehko izostreno). */
    public static List<Long> preberiQr(BufferedImage slika) {
        return najdi(slika).stream().map(NajdenaKoda::idTekme).distinct().toList();
    }

    /* Prebrana koda tekme in sredisca njenih treh iskalnih vzorcev v koordinatah
       fotografije: [spodaj levo, zgoraj levo, zgoraj desno] (vrstni red ZXing).
       Bralnik po njih poravna listek (PoravnavaListka). Skrivnost je null pri
       listku, natisnjenem pred S1; ali velja, pove SkrivnostListka. */
    public record NajdenaKoda(long idTekme, String skrivnost, double[][] tocke) {

        /* Skrivnost ne sme v dnevnik: izpis jo samo omeni. */
        @Override
        public String toString() {
            return "NajdenaKoda[idTekme=" + idTekme + ", skrivnost=" + (skrivnost == null ? "ne" : "da") + "]";
        }
    }

    /* Prebrana koda lista vadnice stevk (razlicica postavitve, id lista) in
       sredisca iskalnih vzorcev kot pri NajdenaKoda. */
    public record KodaVadnice(int razlicica, long idLista, double[][] tocke) {}

    public static List<NajdenaKoda> najdi(BufferedImage slika) {
        Map<Long, NajdenaKoda> poId = new java.util.LinkedHashMap<>();
        for (Zadetek z : najdi(slika, QR, MANJSA_STRANICA)) {
            long id = Long.parseLong(z.skupine().group(1));
            poId.putIfAbsent(id, new NajdenaKoda(id, z.skupine().group(2), z.tocke()));
        }
        return List.copyOf(poId.values());
    }

    /* Kode vadnice na fotografiji. List A4 je vecji od listka, zato je tudi
       manjsa razlicica slike vecja (QR ostane dovolj velika). */
    public static List<KodaVadnice> najdiVadnico(BufferedImage slika) {
        return najdi(slika, QR_VADNICE, 2 * MANJSA_STRANICA).stream()
                .map(z -> new KodaVadnice(Integer.parseInt(z.skupine().group(1)),
                        Long.parseLong(z.skupine().group(2)), z.tocke()))
                .toList();
    }

    /* Prebrana koda ligaskega zapisnika: id natisa, skrivnost in sredisca iskalnih
       vzorcev kot pri NajdenaKoda. Skrivnost ne sme v dnevnik. */
    public record KodaZapisnika(long idNatisa, String skrivnost, double[][] tocke) {
        @Override
        public String toString() {
            return "KodaZapisnika[idNatisa=" + idNatisa + "]";
        }
    }

    /* Kode zapisnikov na fotografiji. List je A4 kot vadnica: manjsa razlicica slike
       je vecja kot pri listku, da QR ostane berljiva. */
    public static List<KodaZapisnika> najdiZapisnik(BufferedImage slika) {
        return najdi(slika, QR_ZAPISNIKA, 2 * MANJSA_STRANICA).stream()
                .map(z -> new KodaZapisnika(Long.parseLong(z.skupine().group(1)), z.skupine().group(2), z.tocke()))
                .toList();
    }

    private record Zadetek(Matcher skupine, double[][] tocke) {}

    /* Vse razlicne kode z danim vzorcem (po vsebini); prva razlicica slike, ki
       kaj najde, zadosca. */
    private static List<Zadetek> najdi(BufferedImage slika, Pattern vzorec, int manjsaStranica) {
        Map<String, Zadetek> najdene = new java.util.LinkedHashMap<>();
        BufferedImage manjsa = ObdelavaSlik.pomanjsaj(slika, manjsaStranica);
        for (BufferedImage razlicica : List.of(slika, manjsa)) {
            double merilo = (double) slika.getWidth() / razlicica.getWidth();
            LuminanceSource vir = new BufferedImageLuminanceSource(razlicica);
            for (BinaryBitmap bitna : List.of(
                    new BinaryBitmap(new HybridBinarizer(vir)),
                    new BinaryBitmap(new GlobalHistogramBinarizer(vir)))) {
                for (Result zadetek : beri(bitna)) {
                    String besedilo = zadetek.getText().trim();
                    Matcher m = vzorec.matcher(besedilo);
                    if (!m.matches()) {
                        continue;
                    }
                    var tocke = zadetek.getResultPoints();
                    double[][] t = tocke == null || tocke.length < 3 ? null : new double[][] {
                            {tocke[0].getX() * merilo, tocke[0].getY() * merilo},
                            {tocke[1].getX() * merilo, tocke[1].getY() * merilo},
                            {tocke[2].getX() * merilo, tocke[2].getY() * merilo}};
                    najdene.putIfAbsent(besedilo, new Zadetek(m, t));
                }
                if (!najdene.isEmpty()) {
                    return List.copyOf(najdene.values());
                }
            }
        }
        return List.copyOf(najdene.values());
    }

    private static List<Result> beri(BinaryBitmap bitna) {
        Map<DecodeHintType, Object> namigi = new EnumMap<>(DecodeHintType.class);
        namigi.put(DecodeHintType.POSSIBLE_FORMATS, List.of(BarcodeFormat.QR_CODE));
        namigi.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        try {
            return List.of(new QRCodeMultiReader().decodeMultiple(bitna, namigi));
        } catch (NotFoundException e) {
            return List.of();
        } catch (RuntimeException e) {
            // ZXing ob poskodovani kodi vcasih vrze tudi kaj drugega; koda je tedaj neberljiva
            return List.of();
        }
    }
}
