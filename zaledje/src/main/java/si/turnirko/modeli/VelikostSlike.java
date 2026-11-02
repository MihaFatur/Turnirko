/* Razlicica slike (V54, tabela slika_razlicica). Vsaka nastane ob nalaganju,
   ker bi sprotno pomanjsevanje ob vsakem ogledu telefona v dvorani stalo vec
   kot nekaj deset kilobajtov v bazi. */
package si.turnirko.modeli;

public enum VelikostSlike {
    /* Daljsa stranica do 1200 px: zapisnik in zreb za tisk (24 mm visok
       logotip je tako nad 300 dpi). */
    TISK,
    /* Daljsa stranica do 480 px: glava strani (do ~240 px siroka na namizju,
       z dvojno gostoto zaslona). */
    ZASLON,
    /* 1200 x 630 na papirni podlagi: og:image ob deljenju povezave. */
    DELJENJE
}
