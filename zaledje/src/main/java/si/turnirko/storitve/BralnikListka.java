/* Prepis rocno izpolnjenega sodniskega listka s fotografije.

   Izvedba je LokalniBralnikListka: lastna nevronska mreza na strezniku, brez
   zunanjih storitev in brez cene na listek (odlocitev lastnika, 19. 11. 2026).
   Vmesnik obstaja zato, da storitveni testi tecejo s pripravljenim prepisom. */
package si.turnirko.storitve;

public interface BralnikListka {

    /* Ali bralnik lahko bere (nalozena mreza stevk). Brez tega je branje izklopljeno. */
    boolean jeNastavljen();

    /* Prepise listek z JPEG fotografije. Ob napaki vrze NapakaBranjaListka z
       razlago, ki jo vidi organizator. */
    Branje preberi(byte[] jpeg);

    /* Prepis in razlicica mreze, ki je brala (merilo natancnosti po razlicicah). */
    record Branje(PrebranListek listek, String model) {}

    /* Napaka branja, ki jo organizator vidi ob fotografiji. */
    class NapakaBranjaListka extends RuntimeException {
        public NapakaBranjaListka(String sporocilo) {
            super(sporocilo);
        }

        public NapakaBranjaListka(String sporocilo, Throwable vzrok) {
            super(sporocilo, vzrok);
        }
    }
}
