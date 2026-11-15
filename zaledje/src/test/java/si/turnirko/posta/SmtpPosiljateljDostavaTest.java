/* Sinhrona dostava prek SMTP (SmtpPosiljatelj.dostavi, najdba E2): izid mora
   lociti sprejeto sporocilo, zavrnjeno sporocilo in izpad streznika, ker po
   njem DostavaObvestilStoritev prejemnika zapise kot urejenega, mu steje
   poskus ali dostavo prekine.

   Namesto pravega streznika poste tece majhen lazni SMTP na lokalnih vratih,
   ki govori ravno toliko protokola, kot ga potrebuje JavaMail. Naslov z
   "zavrnjen" zavrne s 550. To preveri razvrscanje napak pravega odjemalca
   (JavaMailSenderImpl), ne pa obnasanja ponudnika poste v produkciji. */
package si.turnirko.posta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class SmtpPosiljateljDostavaTest {

    private static final SporocilaPoste.Besedilo OBVESTILO = new SporocilaPoste.Besedilo(
            "Savinja test: 1. kolo", "Izidi kola", "<p>Izidi kola</p>",
            Map.of("List-Unsubscribe", "<https://turnirko.test/api/v1/obvestila/odjava?z=abc>"));

    private ServerSocket vtic;
    private Thread nit;
    private final List<String> sprejeta = new CopyOnWriteArrayList<>();
    /* Streznik sprejme povezavo in jo takoj zapre (kot ob preobremenitvi). */
    private volatile boolean prekiniOdZacetka;

    @BeforeEach
    void zazeniStreznik() throws IOException {
        vtic = new ServerSocket(0, 10, InetAddress.getLoopbackAddress());
        nit = new Thread(this::streznik, "lazni-smtp");
        nit.setDaemon(true);
        nit.start();
    }

    @AfterEach
    void ustaviStreznik() throws IOException {
        vtic.close();
    }

    @Test
    void sprejetoSporociloJePoslano() {
        assertEquals(IzidDostave.POSLANO, posiljatelj(vtic.getLocalPort()).dostavi("ana@test.si", OBVESTILO));

        assertEquals(1, sprejeta.size());
        assertTrue(sprejeta.get(0).contains("Subject: Savinja test: 1. kolo"), sprejeta.get(0));
        assertTrue(sprejeta.get(0).contains("List-Unsubscribe:"), "glava odjave gre s sporocilom");
        assertTrue(sprejeta.get(0).contains("To: ana@test.si"), sprejeta.get(0));
    }

    /* Streznik je odgovoril in naslov zavrnil: zadeva tega prejemnika. */
    @Test
    void zavrnjenNaslovJeZavrnjeno() {
        assertEquals(IzidDostave.ZAVRNJENO,
                posiljatelj(vtic.getLocalPort()).dostavi("zavrnjen@test.si", OBVESTILO));
        assertTrue(sprejeta.isEmpty());
        // naslednji prejemnik gre po isti poti normalno
        assertEquals(IzidDostave.POSLANO, posiljatelj(vtic.getLocalPort()).dostavi("ana@test.si", OBVESTILO));
    }

    /* Na vratih ne poslusa nihce: izpad, ki ni krivda prejemnika. */
    @Test
    void nedosegljivStreznikJeIzpadKanala() throws IOException {
        int vrata = vtic.getLocalPort();
        vtic.close();

        assertEquals(IzidDostave.KANAL_NE_DELA, posiljatelj(vrata).dostavi("ana@test.si", OBVESTILO));
    }

    @Test
    void pretrganaPovezavaJeIzpadKanala() {
        prekiniOdZacetka = true;

        assertEquals(IzidDostave.KANAL_NE_DELA,
                posiljatelj(vtic.getLocalPort()).dostavi("ana@test.si", OBVESTILO));
    }

    @Test
    void neuspelaPrijavaNaStreznikJeIzpadKanala() {
        assertEquals(IzidDostave.KANAL_NE_DELA,
                SmtpPosiljatelj.razvrsti(new MailAuthenticationException("535 napacno geslo")));
    }

    // ---------- Pomozno ----------

    private static SmtpPosiljatelj posiljatelj(int vrata) {
        JavaMailSenderImpl posta = new JavaMailSenderImpl();
        posta.setHost("127.0.0.1");
        posta.setPort(vrata);
        Properties lastnosti = new Properties();
        lastnosti.setProperty("mail.smtp.connectiontimeout", "3000");
        lastnosti.setProperty("mail.smtp.timeout", "3000");
        lastnosti.setProperty("mail.smtp.writetimeout", "3000");
        posta.setJavaMailProperties(lastnosti);
        ObjectProvider<JavaMailSender> ponudnik = new ObjectProvider<>() {
            @Override
            public JavaMailSender getObject() {
                return posta;
            }

            @Override
            public JavaMailSender getIfAvailable() {
                return posta;
            }
        };
        return new SmtpPosiljatelj(ponudnik, "Turnirko <posta@turnirko.test>", "", "127.0.0.1");
    }

    private void streznik() {
        while (!vtic.isClosed()) {
            try (Socket povezava = vtic.accept()) {
                if (prekiniOdZacetka) {
                    continue;
                }
                pogovor(povezava);
            } catch (IOException napaka) {
                // vtic zaprt ob koncu testa ali odjemalec je odnehal
            }
        }
    }

    private void pogovor(Socket povezava) throws IOException {
        BufferedReader branje = new BufferedReader(
                new InputStreamReader(povezava.getInputStream(), StandardCharsets.UTF_8));
        Writer pisanje = new OutputStreamWriter(povezava.getOutputStream(), StandardCharsets.UTF_8);
        odgovori(pisanje, "220 lazni ESMTP");
        String vrstica;
        while ((vrstica = branje.readLine()) != null) {
            String ukaz = vrstica.toUpperCase();
            if (ukaz.startsWith("EHLO") || ukaz.startsWith("HELO")) {
                odgovori(pisanje, "250 lazni");
            } else if (ukaz.startsWith("RCPT TO") && ukaz.contains("ZAVRNJEN")) {
                odgovori(pisanje, "550 5.1.1 Naslov ne obstaja");
            } else if (ukaz.startsWith("DATA")) {
                odgovori(pisanje, "354 Konec s piko");
                StringBuilder telo = new StringBuilder();
                while ((vrstica = branje.readLine()) != null && !vrstica.equals(".")) {
                    telo.append(vrstica).append('\n');
                }
                sprejeta.add(telo.toString());
                odgovori(pisanje, "250 Sprejeto");
            } else if (ukaz.startsWith("QUIT")) {
                odgovori(pisanje, "221 Adijo");
                return;
            } else {
                odgovori(pisanje, "250 OK"); // MAIL FROM, RSET, NOOP
            }
        }
    }

    private static void odgovori(Writer pisanje, String vrstica) throws IOException {
        pisanje.write(vrstica + "\r\n");
        pisanje.flush();
    }
}
