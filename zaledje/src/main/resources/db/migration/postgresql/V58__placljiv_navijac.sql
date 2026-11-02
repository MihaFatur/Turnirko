-- ============================================================================
-- Turnirko: placljiv paket Navijac (V58, okt 2026)
-- Dvojnica db/migration/sqlite/V58__placljiv_navijac.sql za PostgreSQL. Razlaga
-- je v SQLite razlicici.
--
-- SQLite pogojev CHECK ne zna spremeniti, zato tam tabeli narocnina in
-- koda_popusta prezida (z izklopljenimi tujimi kljuci); PostgreSQL pogoje
-- zamenja neposredno, brez prezidave: stari pogoj se odstrani, nov z razsirjenim
-- seznamom vrednosti se doda. Imena pogojev so ista, kot jih PostgreSQL da
-- vgrajenim pogojem stolpca v izhodiscu V53 (<tabela>_<stolpec>_check).
-- ============================================================================

-- ---------------------------------------------------------------------------
-- narocnina: paket NAVIJAC
-- ---------------------------------------------------------------------------

ALTER TABLE narocnina DROP CONSTRAINT narocnina_paket_check;
ALTER TABLE narocnina ADD CONSTRAINT narocnina_paket_check CHECK (paket IN (
    'BREZPLACNO', 'PREMIUM', 'NAVIJAC',
    'ORGANIZATOR_BASIC', 'ORGANIZATOR_PLUS', 'ORGANIZATOR_PRO'));

-- ---------------------------------------------------------------------------
-- koda_popusta: vrsta NAVIJAC_MESECI in vloga NAVIJAC
-- ---------------------------------------------------------------------------

ALTER TABLE koda_popusta DROP CONSTRAINT koda_popusta_vrsta_check;
ALTER TABLE koda_popusta ADD CONSTRAINT koda_popusta_vrsta_check CHECK (vrsta IN (
    'PREMIUM_MESECI', 'ODSTOTEK', 'ORGANIZATOR_MESECI', 'NAVIJAC_MESECI'));

ALTER TABLE koda_popusta DROP CONSTRAINT koda_popusta_za_vlogo_check;
ALTER TABLE koda_popusta ADD CONSTRAINT koda_popusta_za_vlogo_check
    CHECK (za_vlogo IS NULL OR za_vlogo IN ('IGRALEC', 'ORGANIZATOR', 'NAVIJAC'));

-- vsaka vrsta nosi natanko svoja polja (kot pri ostalih vrstah v izhodiscu)
ALTER TABLE koda_popusta ADD CONSTRAINT koda_popusta_navijac_meseci_check
    CHECK (vrsta <> 'NAVIJAC_MESECI'
           OR (meseci IS NOT NULL AND meseci BETWEEN 1 AND 36
               AND odstotek IS NULL AND paket IS NULL AND za_vlogo IS NULL));

-- ---------------------------------------------------------------------------
-- placljiva registracija navijaca, koledar .ics, obvestilo po turnirju
-- ---------------------------------------------------------------------------

ALTER TABLE cakajoca_registracija ADD COLUMN navijac boolean NOT NULL DEFAULT false;

ALTER TABLE uporabnik ADD COLUMN zeton_koledarja text;

CREATE UNIQUE INDEX idx_uporabnik_zeton_koledarja ON uporabnik (zeton_koledarja)
    WHERE zeton_koledarja IS NOT NULL;

CREATE TABLE obvestilo_turnirja (
    id_turnir    bigint NOT NULL PRIMARY KEY REFERENCES turnir (id) ON DELETE CASCADE,
    obdelano_ob  timestamp(3) NOT NULL,
    prejemnikov  integer NOT NULL DEFAULT 0
);
