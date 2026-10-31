-- ============================================================================
-- Turnirko: druzina (faza 2 predloga »paket Navijac«, okt 2026)
--
-- Navijac s placljivim paketom poveze do dva igralca do 21 let (po starostnem
-- pasu) in v svojem racunu vidi njuno ZASEBNO statistiko (forma, nasprotniki,
-- napoved, nizi in tocke); ce ima otrok svoj racun, dobi Premium tudi on. Brez
-- potrditve ne gre nic: zasebna statistika tujega otroka ne sme priti do nikogar
-- samo z imenom in datumom rojstva (V3 iz varnostnega pregleda), zato povezavo
-- potrdi administrator - kot povezavo racuna z igralcem.
--
--   status       CAKA (zahteva, admin se ni odlocil), POTRJENA, ZAVRNJENA. Zavrnjena
--                vrstica ostane: ista oseba zahteve za istega otroka ne ponavlja
--                tiho in admin vidi, kaj je ze odlocil.
--   velja_do     konec obdobja naracnine, ko je igralec prerasel pas U21 (potrjena
--                povezava velja do konca ze placanega obdobja; DruzinaStoritev.
--                uskladiStaroste). NULL = brez roka.
--
-- Ob izbrisu racuna ali igralca povezava odpade z njim (ON DELETE CASCADE).
-- Najvec dve aktivni povezavi na racun varuje storitev (pravilo paketa, ne
-- sheme: ob placljivem paketu z vec igralci shema ostane enaka).
-- ============================================================================

CREATE TABLE druzina_povezava (
    id            INTEGER PRIMARY KEY,
    id_racun      INTEGER NOT NULL REFERENCES uporabnik (id) ON DELETE CASCADE,
    id_igralec    INTEGER NOT NULL REFERENCES igralec (id) ON DELETE CASCADE,
    status        TEXT NOT NULL CHECK (status IN ('CAKA', 'POTRJENA', 'ZAVRNJENA')),
    zahtevana_ob  TEXT NOT NULL,
    odlocena_ob   TEXT,
    velja_do      TEXT,
    UNIQUE (id_racun, id_igralec)
);

CREATE INDEX idx_druzina_povezava_igralec ON druzina_povezava (id_igralec);
CREATE INDEX idx_druzina_povezava_status ON druzina_povezava (status);
