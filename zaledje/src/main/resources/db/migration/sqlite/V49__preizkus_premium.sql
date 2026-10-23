-- ============================================================================
-- Turnirko: preizkus Premium (prvi mesec brez placila)
--
-- Brezplacen racun igralca doslej ni dal nicesar, cesar ne bi imel gost, zato
-- si ga skoraj nihce ni ustvaril. Odlocitev lastnika (23. 10. 2026): racun
-- igralca dobi en mesec Premium brez placila in brez kartice. Preizkus se
-- zacne, ko je racun POVEZAN z zapisom med igralci (samodejno ob potrditvi
-- e-poste ali ga poveze administrator) - prej Premium nima cesa pokazati, ker
-- zasebna statistika in domaca stran po meri potrebujeta igralca.
--
--   status PREIZKUS      Premium brez Stripa; velja do trenutno_obdobje_do,
--                        nato racun preide na Free sam od sebe (brez opravila).
--                        Nakup med preizkusom vrstico prepise v AKTIVNA
--                        (PlacilaStoritev.obdelajNadgradnjo), kot pri Free.
--   opomnik_poslan_ob    kdaj je slo obvestilo, da se preizkus izteka (nocno
--                        opravilo ga poslje enkrat, nekaj dni pred koncem).
--
-- Preizkus je enkraten: racun, ki vrstico ze ima (preizkus, placilo ali
-- potekla narocnina), ga ne dobi znova - tudi ne po razvezavi in novi
-- povezavi.
--
-- SQLite pogoja CHECK ne zna spremeniti, zato se tabela prezida. Nanjo ne
-- kaze noben tuji kljuc, zato prezidava ne potrebuje izklopa kljucev.
-- ============================================================================

CREATE TABLE narocnina_nova (
    id                     INTEGER PRIMARY KEY,
    id_uporabnik           INTEGER NOT NULL UNIQUE REFERENCES uporabnik (id) ON DELETE CASCADE,
    paket                  TEXT NOT NULL CHECK (paket IN (
                               'BREZPLACNO', 'PREMIUM',
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
    -- preizkus je vedno Premium brez Stripa in vedno s koncem
    CHECK (status <> 'PREIZKUS'
           OR (paket = 'PREMIUM' AND stripe_narocnina_id IS NULL AND trenutno_obdobje_do IS NOT NULL))
);

INSERT INTO narocnina_nova (id, id_uporabnik, paket, ciklus, cena_ob_sklenitvi, starejsi_od_21,
                            stripe_customer_id, stripe_narocnina_id, status, zacetek_ob,
                            trenutno_obdobje_do, ustvarjena_ob, posodobljena_ob, obdobje_od,
                            naslednji_ciklus, naslednji_paket)
SELECT id, id_uporabnik, paket, ciklus, cena_ob_sklenitvi, starejsi_od_21,
       stripe_customer_id, stripe_narocnina_id, status, zacetek_ob,
       trenutno_obdobje_do, ustvarjena_ob, posodobljena_ob, obdobje_od,
       naslednji_ciklus, naslednji_paket
FROM narocnina;

DROP TABLE narocnina;
ALTER TABLE narocnina_nova RENAME TO narocnina;

CREATE INDEX idx_narocnina_stripe ON narocnina (stripe_narocnina_id);
