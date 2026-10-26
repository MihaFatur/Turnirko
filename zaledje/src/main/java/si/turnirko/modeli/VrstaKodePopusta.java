/* Kaj koda popusta prinese (V53). Vrsta doloci, katera polja kode so
   izpolnjena in na kateri poti registracije koda velja:
   - PREMIUM_MESECI: brezplacna registracija igralca; preizkus Premium traja
     `meseci` namesto enega;
   - ODSTOTEK: placljiva registracija (Premium ali organizator); popust na
     prvo placilo;
   - ORGANIZATOR_MESECI: registracija organizatorja brez placila; paket `paket`
     za `meseci`, od potrditve organizatorja. */
package si.turnirko.modeli;

public enum VrstaKodePopusta {
    PREMIUM_MESECI,
    ODSTOTEK,
    ORGANIZATOR_MESECI
}
