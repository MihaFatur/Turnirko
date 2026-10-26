/* Odgovor predogleda kode popusta. Neveljavna, potekla, ugasnjena in izcrpana
   koda dobijo isti odgovor (veljavna = false, brez podrobnosti): odgovor ne
   sme povedati, katera koda obstaja. Podrobnosti ugodnosti dobi samo kdor
   kodo pozna. `uporabna` = false pomeni: koda obstaja, a ne velja za izbrano
   vlogo oz. paket; `sporocilo` pove, zakaj. */
package si.turnirko.dto;

import si.turnirko.modeli.KodaPopusta;
import si.turnirko.modeli.Paket;
import si.turnirko.modeli.VrstaKodePopusta;

public record PreverjenaKodaDto(
        boolean veljavna,
        boolean uporabna,
        String sporocilo,
        String koda,
        VrstaKodePopusta vrsta,
        Integer meseci,
        Integer odstotek,
        Paket paket
) {

    public static PreverjenaKodaDto neveljavna(String sporocilo) {
        return new PreverjenaKodaDto(false, false, sporocilo, null, null, null, null, null);
    }

    /* `razlog` je null, kadar koda za izbrano pot velja. */
    public static PreverjenaKodaDto iz(KodaPopusta k, String razlog) {
        return new PreverjenaKodaDto(true, razlog == null, razlog, k.getKoda(), k.getVrsta(),
                k.getMeseci(), k.getOdstotek(), k.getPaket());
    }
}
