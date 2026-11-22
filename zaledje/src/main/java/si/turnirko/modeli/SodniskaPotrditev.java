/* Stikalo turnirja »Sodniki slikajo listke« (S2, V75). Izklopljeno = null.
   PREDPREGLED - sodnik izid potrdi, velja pa sele, ko ga potrdi se organizator
                 (en dotik); za prvi turnir, dokler branje ni izmerjeno na pravih listkih.
   VELJA_TAKOJ - sodnikova potrditev vpise izid (mreza napreduje), organizator nadzira. */
package si.turnirko.modeli;

public enum SodniskaPotrditev {
    PREDPREGLED,
    VELJA_TAKOJ
}
