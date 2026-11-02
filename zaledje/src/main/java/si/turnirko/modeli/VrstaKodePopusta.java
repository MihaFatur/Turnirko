/* Kaj koda popusta prinese (V53). Vrsta doloci, katera polja kode so
   izpolnjena in na kateri poti registracije koda velja:
   - PREMIUM_MESECI: brezplacna registracija igralca; preizkus Premium traja
     `meseci` namesto enega;
   - ODSTOTEK: placljiva registracija (Premium ali organizator); popust na
     prvo placilo;
   - ORGANIZATOR_MESECI: registracija organizatorja brez placila; paket `paket`
     za `meseci`, od potrditve organizatorja;
   - NAVIJAC_MESECI: brezplacna registracija navijaca; paket Navijac za `meseci`
     brez placila, od potrditve racuna (V58). */
package si.turnirko.modeli;

public enum VrstaKodePopusta {
    PREMIUM_MESECI,
    ODSTOTEK,
    ORGANIZATOR_MESECI,
    NAVIJAC_MESECI
}
