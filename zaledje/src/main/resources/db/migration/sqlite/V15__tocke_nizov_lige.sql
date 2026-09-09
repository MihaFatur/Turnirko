-- ============================================================================
-- Turnirko: tocke po nizih tudi pri ligaskih tekmah
--
-- Doslej so se tocke posameznega niza (11:7, 9:11 ...) hranile samo za
-- turnirske tekme (tabela niz). Ligaske tekme so imele le stevilo dobljenih
-- nizov, zato je zapisnik ekipnega dvoboja - ki ima na papirju stolpce za
-- vsak niz posebej - ostal prazen. Odslej se vpisujejo enako pri obojih.
--
-- Zakaj SVOJA tabela in ne dodaten stolpec v "niz": niz.id_tekma je NOT NULL,
-- SQLite pa stolpca ne zna omehcati brez prezidave cele tabele. Poleg tega
-- shema ze povsod loci turnirsko in ligasko tekmo (tekma / tekma_srecanja,
-- prijava / postava_srecanja); dve locENI tabeli ohranita obvezno vez na svojo
-- tekmo in poizvedbe brez pogojev "kateri od obeh stolpcev je zapolnjen".
--
-- Vrstice odpadejo s tekmo (ON DELETE CASCADE): ob spremembi postave se tekme
-- srecanja pobrisejo in zgenerirajo znova, njihovi nizi pa brez njih ne
-- pomenijo nicesar.
-- ============================================================================

CREATE TABLE niz_srecanja (
    id                  INTEGER PRIMARY KEY,
    id_tekma_srecanja   INTEGER NOT NULL REFERENCES tekma_srecanja (id) ON DELETE CASCADE,
    zaporedna_st        INTEGER NOT NULL CHECK (zaporedna_st >= 1),
    -- tocke domacih oz. gostov (imenovanje sledi tekma_srecanja, ne niz.tocke_1/2)
    tocke_domaci        INTEGER NOT NULL CHECK (tocke_domaci >= 0),
    tocke_gost          INTEGER NOT NULL CHECK (tocke_gost >= 0),
    UNIQUE (id_tekma_srecanja, zaporedna_st)
);
