-- ============================================================================
-- Turnirko: tekmovanja iz zunanjih virov (NTZS) - vir, povezave, dnevnik uvoza
--
-- Doslej je zgodovina NTZS prisla v bazo z enkratnim uvozom v PRAZNO bazo.
-- Nova sezona pa tece sproti: turnir se odigra v soboto in admin ga uvozi v
-- bazo, v kateri ze zivijo zgodovina, lokalni turnirji in racuni. Za to so
-- potrebne tri stvari, ki jih shema doslej ni poznala.
--
-- 1. VIR TEKMOVANJA (turnir.vir, liga.vir)
--    Uvozeno tekmovanje je SAMO ZA BRANJE. Vir resnice je zveza: ce bi admin
--    rezultat popravil v Turnirku, bi ga naslednja sinhronizacija povozila,
--    ce pa bi sinhronizacija lokalni popravek spostovala, bi se Turnirko in
--    uradni izid razsla brez sledi. Zato popravek vnese NTZS, uvoz ga prenese.
--    Prazen vir pomeni tekmovanje, ki je nastalo v Turnirku.
--    Varuje ga LastnistvoStoritev - skozi njo gre vsaka mutacija turnirja
--    in lige, zato je pravilo na enem mestu in ne v vsaki storitvi posebej.
--
-- 2. ZUNANJE POVEZAVE (zunanja_povezava)
--    Kateri zapis v Turnirku je kateri zapis pri viru. Brez tega je vsak
--    ponovni uvoz ustvaril dvojnike: stari uvoznik je povezave drzal samo v
--    pomnilniku in je zato zahteval prazno bazo.
--    Ena tabela za vse vrste zapisov in ne stolpec v vsaki tabeli, ker ima
--    ista oseba pri Stupi lahko vec identitet (isti igralec pod dvema
--    user_role_id) - povezava je zato "vec zunanjih na enega lokalnega".
--
-- 3. DNEVNIK UVOZA (uvoz_zagon)
--    Kdo je kdaj uvozil kateri dogodek, iz katerega posnetka (zgostitev) in s
--    kaksnim izidom uskladitve. Brez njega na vprasanje "od kod ta rezultat"
--    ni odgovora.
-- ============================================================================

ALTER TABLE turnir ADD COLUMN vir TEXT
    CHECK (vir IS NULL OR vir IN ('STUPA', 'STARA_NTZS'));

ALTER TABLE liga ADD COLUMN vir TEXT
    CHECK (vir IS NULL OR vir IN ('STUPA', 'STARA_NTZS'));

-- Obstojeci uvozeni turnirji so v opombah oznaceni ze od prvega uvoza. Pri
-- ligah take oznake ni in je ne ugibamo: baza z uvozeno zgodovino se ob
-- prehodu zgradi znova (glej uvoz-stupa/README.md), ugibanje po ravni in
-- lastniku pa bi lahko zaklenilo ligo, ki jo je ustvaril clovek.
UPDATE turnir SET vir = 'STUPA' WHERE opombe LIKE 'Uvoz iz Stupa Events%';
UPDATE turnir SET vir = 'STARA_NTZS' WHERE opombe LIKE 'Uvoz s stara.ntzs.si%';

CREATE TABLE zunanja_povezava (
    id          INTEGER PRIMARY KEY,
    vir         TEXT NOT NULL CHECK (vir IN ('STUPA', 'STARA_NTZS')),
    vrsta       TEXT NOT NULL CHECK (vrsta IN ('IGRALEC', 'KLUB', 'TURNIR', 'DOGODEK',
                                               'PRIJAVA', 'EKIPA', 'SKUPINA', 'TEKMA',
                                               'LIGA', 'SRECANJE')),
    -- identifikator pri viru; besedilo, ker ga vir lahko sestavi (npr. liga
    -- in serija koncnice nimata svojega stevila)
    zunanji_id  TEXT NOT NULL CHECK (length(zunanji_id) BETWEEN 1 AND 80),
    id_lokalni  INTEGER NOT NULL,
    UNIQUE (vir, vrsta, zunanji_id)
);

CREATE INDEX idx_zunanja_povezava_lokalni ON zunanja_povezava (vrsta, id_lokalni);

CREATE TABLE uvoz_zagon (
    id              INTEGER PRIMARY KEY,
    vir             TEXT NOT NULL CHECK (vir IN ('STUPA', 'STARA_NTZS')),
    -- dogodek pri viru, ki se je uvazal
    zunanji_id      TEXT NOT NULL,
    ime             TEXT NOT NULL,
    -- SHA-256 posnetka, iz katerega je tekel uvoz; predogled in uvoz morata
    -- teci nad istim posnetkom
    zgostitev       TEXT NOT NULL,
    izvedel         TEXT,
    zacetek_ob      TEXT NOT NULL,
    konec_ob        TEXT,
    izid            TEXT NOT NULL CHECK (izid IN ('USPEH', 'BREZ_SPREMEMB', 'ZAVRNJENO', 'NAPAKA')),
    -- stevci, ugotovitve in porocilo uskladitve (JSON)
    povzetek        TEXT
);

CREATE INDEX idx_uvoz_zagon_dogodek ON uvoz_zagon (vir, zunanji_id);
