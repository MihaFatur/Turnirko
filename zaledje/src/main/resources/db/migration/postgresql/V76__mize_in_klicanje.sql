-- Mize in klicanje tekem (M1) ter predlog naslednje tekme (M2),
-- docs/NACRT-BRANJE-LISTKOV.md in docs/pravila/mize.md.

-- Nastavitve miz turnirja (organizator na strani Mize):
--   stevilo_miz       mize 1..N; NULL = mize niso nastavljene (klic ni mogoc).
--                     Najvec 99, kolikor jih prebere okence za mizo na listku (S1).
--   izklopljene_mize  zacasno izkljucene mize, besedilo "3,7"
--   porazenec_sodi    porazenec zadnje tekme na mizi je predlagan za sodnika naslednje
--   pocitek_min       predlog (M2) igralca, ki je koncal pred manj minutami, postavi nizje
ALTER TABLE turnir
    ADD COLUMN stevilo_miz integer CHECK (stevilo_miz BETWEEN 1 AND 99),
    ADD COLUMN izklopljene_mize text,
    ADD COLUMN porazenec_sodi boolean NOT NULL DEFAULT false,
    ADD COLUMN pocitek_min integer NOT NULL DEFAULT 3 CHECK (pocitek_min BETWEEN 0 AND 30);

-- Klic tekme na mizo. Stolpec miza obstaja od izhodisca, a ga do zdaj ni nastavljalo nic.
--   miza_2        druga miza ekipnega srecanja (izbira ob klicu)
--   poklicana_ob  cas klica (V_IGRI); minute igre na strani Mize
--   koncana_ob    cas prehoda v KONCANA; pocitek in »porazenec sodi« (M2)
--   id_sodnik     kdo sodi (igralec turnirja); zaseden je, dokler je tekma V_IGRI
-- Klic, druga miza in sodnik obstajajo samo pri tekmi v igri ali koncani
-- (Tekma.setStatus jih ob vrnitvi pobrise).
ALTER TABLE tekma
    ADD COLUMN miza_2 integer,
    ADD COLUMN poklicana_ob timestamp(3),
    ADD COLUMN koncana_ob timestamp(3),
    ADD COLUMN id_sodnik bigint REFERENCES igralec (id) ON DELETE SET NULL;

ALTER TABLE tekma ADD CONSTRAINT tekma_miza_2_check
    CHECK (miza_2 IS NULL OR (miza IS NOT NULL AND miza_2 >= 1 AND miza_2 <> miza));
ALTER TABLE tekma ADD CONSTRAINT tekma_klic_check
    CHECK ((poklicana_ob IS NULL AND id_sodnik IS NULL AND miza_2 IS NULL)
           OR status IN ('V_IGRI', 'KONCANA'));
ALTER TABLE tekma ADD CONSTRAINT tekma_koncana_ob_check
    CHECK (koncana_ob IS NULL OR status = 'KONCANA');

-- Zdruzitev igralcev in izbris igralca isceta sodnike po igralcu.
CREATE INDEX idx_tekma_sodnik ON tekma (id_sodnik) WHERE id_sodnik IS NOT NULL;
