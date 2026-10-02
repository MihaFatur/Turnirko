-- ============================================================================
-- Turnirko: prenesen izid srecanja lige
--
-- Ekipno DP mladih (stara stran NTZS) se igra v predtekmovalnih skupinah in
-- nato v finalnih; dvoboj dveh ekip iz iste predtekmovalne skupine se v
-- finalno skupino PRENESE in se ne igra znova. Vir ga na strani finalne
-- skupine pokaze z istim zapisnikom.
--
-- Uvoz ga je doslej zapisal dvakrat, s posamicnimi tekmami vred: 1.168 tekem
-- je bilo zato v ratingu obracunanih dvakrat. Zdaj je prenesen izid srecanje
-- finalne skupine z izidom ekip (steje v lestvico skupine) in BREZ posamicnih
-- tekem - te ostanejo samo v predtekmovanju, kjer so bile odigrane.
--
--   prenesen   1 = izid je prenesen iz druge lige (predtekmovanja)
-- ============================================================================

ALTER TABLE srecanje ADD COLUMN prenesen INTEGER NOT NULL DEFAULT 0
    CHECK (prenesen IN (0, 1));
