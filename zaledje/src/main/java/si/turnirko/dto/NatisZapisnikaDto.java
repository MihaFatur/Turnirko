/* Ligaski zapisnik za bralnik, pripravljen za tisk (NatisZapisnikovStoritev):
   postavitev lista (GeometrijaZapisnika - vmesnik po njej narise list do mm
   natancno), koda QR s skrivnostjo, kadra po vrsticah in podatki za glavo. Kode
   dobi samo organizator tekmovanja in admin (kot kode listkov, S1). */
package si.turnirko.dto;

import java.time.LocalDateTime;
import java.util.List;

import si.turnirko.branje.GeometrijaZapisnika;

public record NatisZapisnikaDto(Long idSrecanje, Long idNatis, String koda, String oznaka,
                                GeometrijaZapisnika.Oblika oblika, GeometrijaZapisnika.PostavitevZapisnika postavitev,
                                List<GeometrijaZapisnika.TekmaRazporeda> tekme, List<Igralec> kaderDomaci,
                                List<Igralec> kaderGost, String domaci, String gost, String tekmovanje, String sezona,
                                String opis, LocalDateTime termin, Long idLiga) {

    public record Igralec(Long id, String ime, String priimek) {}
}
