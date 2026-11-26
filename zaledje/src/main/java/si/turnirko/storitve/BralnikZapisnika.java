/* Bralnik ligaskega zapisnika (LokalniBralnikZapisnika); vmesnik zato, da testi
   storitve (BranjeZapisnikovStoritev) lahko podtaknejo pripravljen prepis. */
package si.turnirko.storitve;

import java.util.function.Function;

public interface BralnikZapisnika {

    boolean jeNastavljen();

    /* natisPoKodi poisce natis in preveri skrivnost ter dostop; ob napaki vrze
       LokalniBralnikZapisnika.NapakaBranjaZapisnika z razlago. */
    LokalniBralnikZapisnika.Branje preberi(byte[] jpeg,
                                          Function<KodaListka.KodaZapisnika, LokalniBralnikZapisnika.NatisZaBranje> natisPoKodi);
}
