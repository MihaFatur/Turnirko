-- ============================================================================
-- Turnirko: kode popusta ob registraciji (V53, okt 2026)
--
-- Admin nastavi kodo in ugodnost, ki jo ta koda prinese; oseba jo vpise ob
-- ustvarjanju racuna. Tri vrste ugodnosti (vrsta):
--
--   PREMIUM_MESECI       igralec: preizkus Premium traja `meseci` namesto enega
--                        (brez Stripa in kartice; zacne se ob povezavi racuna z
--                        igralcem, kot navaden preizkus - V49)
--   ODSTOTEK             placljiva registracija (Premium ali organizator):
--                        `odstotek` popusta na PRVO placilo (Stripov kupon z
--                        duration=once); obnova je po polni ceni. Meja 75 %:
--                        Stripe ne sprejme placila pod 0,50 EUR, najcenejsi
--                        paket pa stane 1,99 EUR.
--   ORGANIZATOR_MESECI   organizator: `paket` brez placila za `meseci`
--                        (narocnina v stanju PREIZKUS, zacne se ob potrditvi
--                        organizatorja s strani admina)
--
-- Koda je vedno shranjena v normalizirani obliki (velike crke, brez presledkov).
-- Ugodnost (vrsta, meseci, odstotek, paket) se po nastanku NE spreminja - kdor
-- se je ze registriral, je dobil tisto, kar je koda takrat obljubljala; popravek
-- napake je izklop kode in nova koda. Spremenijo se lahko samo opis, rok,
-- najvecje stevilo uporab in aktivnost.
--
--   za_vlogo        samo pri ODSTOTEK: koda velja za IGRALEC ali ORGANIZATOR;
--                   NULL = za oboje
--   velja_do        zadnji dan, ko se koda se sprejme (vkljucno); NULL = brez roka
--   najvec_uporab   NULL = neomejeno; steje porabe + cakajoce placane registracije
--   stripe_kupon_id nastane ob prvem placilu s kodo (ODSTOTEK) in se uporabi znova
--
-- uporaba_kode_popusta: en racun uporabi najvec eno kodo (UNIQUE). Vrstica gre
-- skupaj z racunom (nepotrjena registracija se po 48 urah izbrise in z njo tudi
-- rezervacija koda) - prosto mesto se tako vrne samo od sebe.
--
-- cakajoca_registracija.id_koda: koda, ki jo drzi placljiva registracija, dokler
-- Stripe ne potrdi placila (rezervacija tako ne dovoli, da bi se najvec uporab
-- prekoracilo med dvema sejama). Ko racun nastane, vrstica gre, koda pa se zapise
-- v uporaba_kode_popusta.
--
-- narocnina: PREIZKUS je do zdaj pomenil samo Premium. Nov pogoj pusti vsak
-- paket, razen BREZPLACNO - preizkus ostane narocnina brez Stripa, ki velja do
-- datuma (Narocnina.jeVeljavna), zato potek organizatorskega paketa ne potrebuje
-- opravila. SQLite pogoja CHECK ne zna spremeniti, zato se tabela prezida (kot
-- v V49); nanjo ne kaze noben tuji kljuc.
-- ============================================================================

CREATE TABLE koda_popusta (
    id               INTEGER PRIMARY KEY,
    koda             TEXT NOT NULL UNIQUE,
    opis             TEXT,
    vrsta            TEXT NOT NULL CHECK (vrsta IN ('PREMIUM_MESECI', 'ODSTOTEK', 'ORGANIZATOR_MESECI')),
    meseci           INTEGER,
    odstotek         INTEGER,
    paket            TEXT,
    za_vlogo         TEXT CHECK (za_vlogo IS NULL OR za_vlogo IN ('IGRALEC', 'ORGANIZATOR')),
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
    CHECK (vrsta <> 'ODSTOTEK'
           OR (odstotek IS NOT NULL AND odstotek BETWEEN 1 AND 75
               AND meseci IS NULL AND paket IS NULL)),
    CHECK (vrsta <> 'ORGANIZATOR_MESECI'
           OR (meseci IS NOT NULL AND meseci BETWEEN 1 AND 36
               AND odstotek IS NULL AND za_vlogo IS NULL
               AND paket IN ('ORGANIZATOR_BASIC', 'ORGANIZATOR_PLUS', 'ORGANIZATOR_PRO')))
);

CREATE TABLE uporaba_kode_popusta (
    id               INTEGER PRIMARY KEY,
    id_koda          INTEGER NOT NULL REFERENCES koda_popusta (id),
    id_uporabnik     INTEGER NOT NULL UNIQUE REFERENCES uporabnik (id) ON DELETE CASCADE,
    uporabljena_ob   TEXT NOT NULL
);

CREATE INDEX idx_uporaba_kode_popusta_koda ON uporaba_kode_popusta (id_koda);

ALTER TABLE cakajoca_registracija
    ADD COLUMN id_koda INTEGER REFERENCES koda_popusta (id) ON DELETE SET NULL;

CREATE INDEX idx_cakajoca_registracija_koda ON cakajoca_registracija (id_koda);

-- ---------------------------------------------------------------------------
-- narocnina: PREIZKUS za vsak paket razen BREZPLACNO
-- ---------------------------------------------------------------------------

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
