-- Skupne mize vec turnirjev v isti dvorani (lastnik 25. 11. 2026, docs/pravila/mize.md).
-- Na isti dan v isti dvorani pogosto igrata dva turnirja istega organizatorja (npr. U15
-- in U21, vsak z moskimi in zenskami) na istih mizah. Organizator ju na strani Mize
-- poveze: stran Mize, tisk listkov, branje listkov in zaslon za igralce ju kazejo kot
-- celoto, gledalec pa vidi turnirja kot prej (povezava ni javna).
--
--   id_skupne_mize       glavni turnir skupine, katere mize si turnir deli. NULL = turnir
--                        je sam ali je glavni. Skupina je globoka en nivo: glavni turnir
--                        ni nikoli clan druge skupine (MizeStoritev). Nastavitve miz,
--                        rocni vrstni red in vrstni red kategorij skupine nosi glavni.
--   vrstni_red_dogodkov  vrstni red kategorij v kolu (id-ji dogodkov, besedilo "12,15,13"),
--                        ki ga nastavi organizator; predlog tekem gre po kolih, v kolu po
--                        tem redu. NULL = po turnirju in dogodku.
ALTER TABLE turnir
    ADD COLUMN id_skupne_mize bigint REFERENCES turnir (id) ON DELETE SET NULL,
    ADD COLUMN vrstni_red_dogodkov text;

ALTER TABLE turnir ADD CONSTRAINT turnir_skupne_mize_check
    CHECK (id_skupne_mize IS NULL OR id_skupne_mize <> id);

CREATE INDEX idx_turnir_skupne_mize ON turnir (id_skupne_mize) WHERE id_skupne_mize IS NOT NULL;
