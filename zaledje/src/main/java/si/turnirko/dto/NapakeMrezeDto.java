/* Napake mreze stevk po stevkah (F6.6, samo admin; stran Meritev branja).

   vUporabi: id razlicice, ki bere listke; null = vgrajena (stolpca »vgrajena« in
   »v uporabi« sta tedaj enaka). poStevkah: za vsako stevko 0-9 preizkusne stevke
   (teh se mreza ni ucila), koliko jih vsaka mreza prebere prav in s cim jih mreza v
   uporabi najpogosteje zamenja. napake: narobe prebrane preizkusne stevke (najvec
   NapakeMreze.NAJVEC_NA_STEVKO za vsako napisano stevko). sumljive: ucne stevke, ki
   jih mreza kljub ucenju odlocno bere drugace - pogosto napacna oznaka ali narobe
   izrezano okence. */
package si.turnirko.dto;

import java.util.List;

public record NapakeMrezeDto(
        Long vUporabi,
        int preizkusnih,
        int ucnih,
        List<PoStevki> poStevkah,
        int napakVseh,
        int najvecNaStevko,
        List<Stevka> napake,
        int drugacnihUcnih,
        float pragSumljive,
        List<Stevka> sumljive) {

    public record PoStevki(int stevka, int preizkusnih, int pravVgrajena, int pravVUporabi,
                           List<Zamenjava> zamenjave) {}

    /* Napisana stevka je bila prebrana kot `prebrano` toliko krat. */
    public record Zamenjava(int prebrano, int krat) {}

    /* Stevka za ogled: sivine 28 x 28 (base64, 784 bajtov, 0 = papir), kaj je
       napisano, kaj mreza v uporabi prebere in s kaksno verjetnostjo; vir je list
       vadnice (idVadnica) ali potrjen listek. */
    public record Stevka(Long id, int vrednost, int prebrano, float verjetnost, String slika, Long idVadnica,
                         boolean listek) {}
}
