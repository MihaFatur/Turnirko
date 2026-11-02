-- ============================================================================
-- Turnirko: placljiv paket Navijac (faza 1 predloga »paket Navijac«, okt 2026)
--
-- Navijac (V55) ostane brezplacen; za 19,99 EUR na leto doda: neomejeno
-- spremljanih igralcev, e-posto po turnirju, osebni koledar .ics in napoved
-- dvoboja za kateri koli par. Shema potrebuje stiri stvari:
--
--   narocnina             paket NAVIJAC. SQLite pogoja CHECK ne zna spremeniti,
--                         zato se tabela prezida (kot V49 in V53); nanjo ne
--                         kaze noben tuji kljuc.
--   koda_popusta          kode popusta (V53) veljajo tudi za navijaca: nova vrsta
--                         NAVIJAC_MESECI (placljiv paket za `meseci` brez placila,
--                         od potrditve e-poste) in ODSTOTEK za vlogo NAVIJAC. Tabela
--                         se prezida; nanjo kazeta uporaba_kode_popusta in
--                         cakajoca_registracija.id_koda, zato izklopljeni tuji kljuci
--                         (izven transakcije).
--   cakajoca_registracija placljiva registracija navijaca: zastavica `navijac`, da
--                         webhook racun ustvari z vlogo NAVIJAC, ne IGRALEC.
--   uporabnik             zeton_koledarja: naslov koledarja .ics nima prijave
--                         (Google in Apple koledar ne znata poslati gesla), zato
--                         povezavo varuje nakljucen zeton, ki ga racun sam
--                         zamenja. Isti vzorec kot zeton_odjave (V50).
--   obvestilo_turnirja    kateri zakljucen turnir je ze obdelan (e-posta po
--                         turnirju gre ven najvec enkrat), kot obvestilo_kola.
-- ============================================================================

PRAGMA foreign_keys = OFF;

-- ---------------------------------------------------------------------------
-- narocnina: paket NAVIJAC
-- ---------------------------------------------------------------------------

CREATE TABLE narocnina_nova (
    id                     INTEGER PRIMARY KEY,
    id_uporabnik           INTEGER NOT NULL UNIQUE REFERENCES uporabnik (id) ON DELETE CASCADE,
    paket                  TEXT NOT NULL CHECK (paket IN (
                               'BREZPLACNO', 'PREMIUM', 'NAVIJAC',
                               'ORGANIZATOR_BASIC', 'ORGANIZATOR_PLUS', 'ORGANIZATOR_PRO')),
    ciklus                 TEXT CHECK (ciklus IS NULL OR ciklus IN ('MESECNO', 'LETNO')),
    cena_ob_sklenitvi      REAL,
    starejsi_od_21         INTEGER CHECK (starejsi_od_21 IN (0, 1)),
    stripe_customer_id     TEXT,
    stripe_narocnina_id    TEXT UNIQUE,
    status                 TEXT NOT NULL CHECK (status IN (
                               'CAKA_PLACILO', 'AKTIVNA', 'PREKLICANA', 'ZAPADLA', 'PREIZKUS')),
    zacetek_ob             TEXT,
    trenutno_obdobje_do    TEXT,
    ustvarjena_ob          TEXT NOT NULL,
    posodobljena_ob        TEXT NOT NULL,
    obdobje_od             TEXT,
    naslednji_ciklus       TEXT
        CHECK (naslednji_ciklus IS NULL OR naslednji_ciklus IN ('MESECNO', 'LETNO')),
    naslednji_paket        TEXT
        CHECK (naslednji_paket IS NULL
               OR naslednji_paket IN ('ORGANIZATOR_BASIC', 'ORGANIZATOR_PLUS', 'ORGANIZATOR_PRO')),
    opomnik_poslan_ob      TEXT,
    -- preizkus je paket brez Stripa in vedno s koncem
    CHECK (status <> 'PREIZKUS'
           OR (paket <> 'BREZPLACNO' AND stripe_narocnina_id IS NULL AND trenutno_obdobje_do IS NOT NULL))
);

INSERT INTO narocnina_nova (id, id_uporabnik, paket, ciklus, cena_ob_sklenitvi, starejsi_od_21,
                            stripe_customer_id, stripe_narocnina_id, status, zacetek_ob,
                            trenutno_obdobje_do, ustvarjena_ob, posodobljena_ob, obdobje_od,
                            naslednji_ciklus, naslednji_paket, opomnik_poslan_ob)
SELECT id, id_uporabnik, paket, ciklus, cena_ob_sklenitvi, starejsi_od_21,
       stripe_customer_id, stripe_narocnina_id, status, zacetek_ob,
       trenutno_obdobje_do, ustvarjena_ob, posodobljena_ob, obdobje_od,
       naslednji_ciklus, naslednji_paket, opomnik_poslan_ob
FROM narocnina;

DROP TABLE narocnina;
ALTER TABLE narocnina_nova RENAME TO narocnina;

CREATE INDEX idx_narocnina_stripe ON narocnina (stripe_narocnina_id);

-- ---------------------------------------------------------------------------
-- koda_popusta: vrsta NAVIJAC_MESECI in vloga NAVIJAC
-- ---------------------------------------------------------------------------

CREATE TABLE koda_popusta_nova (
    id               INTEGER PRIMARY KEY,
    koda             TEXT NOT NULL UNIQUE,
    opis             TEXT,
    vrsta            TEXT NOT NULL CHECK (vrsta IN (
                         'PREMIUM_MESECI', 'ODSTOTEK', 'ORGANIZATOR_MESECI', 'NAVIJAC_MESECI')),
    meseci           INTEGER,
    odstotek         INTEGER,
    paket            TEXT,
    za_vlogo         TEXT CHECK (za_vlogo IS NULL OR za_vlogo IN ('IGRALEC', 'ORGANIZATOR', 'NAVIJAC')),
    velja_do         TEXT,
    najvec_uporab    INTEGER CHECK (najvec_uporab IS NULL OR najvec_uporab >= 1),
    aktivna          INTEGER NOT NULL DEFAULT 1 CHECK (aktivna IN (0, 1)),
    stripe_kupon_id  TEXT,
    ustvarjena_ob    TEXT NOT NULL,
    -- vsaka vrsta nosi natanko svoja polja (IS NOT NULL je potreben: pogoj z NULL
    -- ni nikoli napacen, torej bi manjkajoca vrednost mimo njega zdrsnila)
    CHECK (vrsta <> 'PREMIUM_MESECI'
           OR (meseci IS NOT NULL AND meseci BETWEEN 1 AND 36
               AND odstotek IS NULL AND paket IS NULL AND za_vlogo IS NULL)),
    CHECK (vrsta <> 'NAVIJAC_MESECI'
           OR (meseci IS NOT NULL AND meseci BETWEEN 1 AND 36
               AND odstotek IS NULL AND paket IS NULL AND za_vlogo IS NULL)),
    CHECK (vrsta <> 'ODSTOTEK'
           OR (odstotek IS NOT NULL AND odstotek BETWEEN 1 AND 75
               AND meseci IS NULL AND paket IS NULL)),
    CHECK (vrsta <> 'ORGANIZATOR_MESECI'
           OR (meseci IS NOT NULL AND meseci BETWEEN 1 AND 36
               AND odstotek IS NULL AND za_vlogo IS NULL
               AND paket IN ('ORGANIZATOR_BASIC', 'ORGANIZATOR_PLUS', 'ORGANIZATOR_PRO')))
);

INSERT INTO koda_popusta_nova (id, koda, opis, vrsta, meseci, odstotek, paket, za_vlogo, velja_do,
                               najvec_uporab, aktivna, stripe_kupon_id, ustvarjena_ob)
SELECT id, koda, opis, vrsta, meseci, odstotek, paket, za_vlogo, velja_do,
       najvec_uporab, aktivna, stripe_kupon_id, ustvarjena_ob
FROM koda_popusta;

DROP TABLE koda_popusta;
ALTER TABLE koda_popusta_nova RENAME TO koda_popusta;

-- ---------------------------------------------------------------------------
-- placljiva registracija navijaca, koledar .ics, obvestilo po turnirju
-- ---------------------------------------------------------------------------

ALTER TABLE cakajoca_registracija
    ADD COLUMN navijac INTEGER NOT NULL DEFAULT 0 CHECK (navijac IN (0, 1));

ALTER TABLE uporabnik ADD COLUMN zeton_koledarja TEXT;

CREATE UNIQUE INDEX idx_uporabnik_zeton_koledarja ON uporabnik (zeton_koledarja)
    WHERE zeton_koledarja IS NOT NULL;

CREATE TABLE obvestilo_turnirja (
    id_turnir    INTEGER NOT NULL PRIMARY KEY REFERENCES turnir (id) ON DELETE CASCADE,
    obdelano_ob  TEXT NOT NULL,
    prejemnikov  INTEGER NOT NULL DEFAULT 0
);

PRAGMA foreign_key_check;
PRAGMA foreign_keys = ON;
