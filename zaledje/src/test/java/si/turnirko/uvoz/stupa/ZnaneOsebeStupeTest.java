/* Znane osebe iz vseh posnetkov: podatki osebe so pri Stupi po prijavah, zato
   zbirnik vzame najnovejse polno ime in manjkajoce podatke iz drugih dogodkov. */
package si.turnirko.uvoz.stupa;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import si.turnirko.modeli.Spol;

class ZnaneOsebeStupeTest {

    @TempDir Path koren;

    /* Ecsy -> Ecsy Samuel -> Samuel Ecsy: ime je iz najnovejsega dogodka z
       imenom in priimkom, datum rojstva in licenca pa iz dogodka, ki ju ima -
       ne glede na vrstni red, v katerem so posnetki podani. */
    @Test
    void najnovejsePolnoImeInManjkajociPodatki() throws IOException {
        Path najstarejsi = dogodek("186", oseba(19828, "Ecsy", "\"2016-11-24\"", "\"016/25/26\""));
        Path vmesni = dogodek("193", oseba(19828, "Ecsy Samuel", "null", "null"));
        Path najnovejsi = dogodek("200", oseba(19828, "Samuel Écsy", "null", "null"));

        Map<Long, IdentitetaStupe.Oseba> znane = ZnaneOsebeStupe.zberi(List.of(
                new ZnaneOsebeStupe.Posnetek(najnovejsi, LocalDate.of(2026, 3, 1)),
                new ZnaneOsebeStupe.Posnetek(najstarejsi, LocalDate.of(2025, 11, 15)),
                new ZnaneOsebeStupe.Posnetek(vmesni, LocalDate.of(2026, 1, 11))));

        IdentitetaStupe.Oseba samuel = znane.get(19828L);
        assertEquals("Samuel Écsy", samuel.polnoIme());
        assertEquals(LocalDate.of(2016, 11, 24), samuel.rojstvo());
        assertEquals("016/25/26", samuel.licenca());
        assertEquals(Spol.MOSKI, samuel.spol());
    }

    /* Novejsa prijava z imenom iz ene besede polnega imena ne povozi. */
    @Test
    void enobesednoImeNePovoziPolnega() throws IOException {
        Path starejsi = dogodek("1", oseba(7, "Ana Novak", "\"2010-05-05\"", "null"));
        Path novejsi = dogodek("2", oseba(7, "Ana", "null", "null"));
        Map<Long, IdentitetaStupe.Oseba> znane = ZnaneOsebeStupe.zberi(List.of(
                new ZnaneOsebeStupe.Posnetek(starejsi, LocalDate.of(2025, 1, 1)),
                new ZnaneOsebeStupe.Posnetek(novejsi, LocalDate.of(2025, 2, 1))));
        assertEquals("Ana Novak", znane.get(7L).polnoIme());
    }

    private Path dogodek(String id, String oseba) throws IOException {
        Path mapa = Files.createDirectories(koren.resolve(id));
        Files.writeString(mapa.resolve("udelezenci.json"),
                "{\"data\":{\"participants\":[{\"id\":1,\"event_participant_details\":[" + oseba + "]}]}}");
        return mapa;
    }

    private static String oseba(long id, String ime, String rojstvo, String licenca) {
        return "{\"user_role_id\":" + id + ",\"name\":\"" + ime + "\",\"gender_id\":1,"
                + "\"meta_data\":{\"dob\":" + rojstvo + ",\"gender\":\"Male\",\"license_id\":" + licenca
                + ",\"country\":\"Slovenia\"},\"parent_details\":[]}";
    }
}
