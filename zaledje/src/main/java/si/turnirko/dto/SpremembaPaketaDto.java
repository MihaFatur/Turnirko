/* Odgovor na zamenjavo organizatorskega paketa: posodobljena narocnina in
   znesek, ki ga je Stripe pri tem zaracunal. Doplacilo je zapisano samo pri
   nadgradnji (velja takoj); znizanje ob obnovi danes ne stane nic, zato je
   `doplacilo` takrat null - vmesnik po njem loci "Pro je vklopljen, Stripe je
   zaracunal ..." od "od 14. 10. imas Basic". */
package si.turnirko.dto;

public record SpremembaPaketaDto(NarocninaDto narocnina, Double doplacilo) {}
