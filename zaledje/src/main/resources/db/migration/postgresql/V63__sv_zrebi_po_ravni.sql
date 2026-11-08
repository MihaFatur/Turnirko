-- ============================================================================
-- Turnirko: zrebi SV regije po ravni (V63, nov 2026)
-- Samo PostgreSQL (mapa sqlite/ je zamrznjena).
--
-- Igralci Skupine (v kodi in bazi se vedno "nivo") gredo v izlocilne zrebe
-- ZAPOREDOMA po mestu v skupini in crki skupine: A1, B1, ..., E1, A2, B2 ...
-- Vsak zreb sprejme toliko igralcev, kolikor jih ima mreza na izbrani ravni
-- (privzeto cetrtfinale = 8), zadnji dobi ostanek. Organizator lahko za vsako
-- Skupino izbere raven in vpise stevilo zrebov; ce zrebi ne sprejmejo vseh
-- igralcev Skupine (ali bi kateri ostal prazen), zreba ni.
--
--   stevilo_zrebov   po nivojih ("3" ali ",,,3"), prazno = samodejno
--   velikost_zrebov  po nivojih, velikost mreze ravni (2, 4, 8, 16, 32),
--                    prazno = 8 (cetrtfinale)
--
-- rangov_v_zreb (V42: 1 ali 2 ranga iz skupine v en zreb) nadomesti raven:
-- 4 skupine x 2 ranga in 8 skupin x 1 rang sta oba cetrtfinale. Ze izzrebani
-- dogodki imajo velikosti zrebov zapisane v tabeli zreb in iz njih (po istem
-- zaporedju) izpeljejo udelezence, zato stolpca ne potrebujejo vec.
-- ============================================================================

ALTER TABLE dogodek
    ADD COLUMN stevilo_zrebov  text CHECK (stevilo_zrebov IS NULL OR length(stevilo_zrebov) <= 40),
    ADD COLUMN velikost_zrebov text CHECK (velikost_zrebov IS NULL OR length(velikost_zrebov) <= 40);

-- DROP COLUMN odstrani tudi CHECK "nivoji so lastnost sistema SV_REGIJA", ki
-- omenja rangov_v_zreb, zato ga takoj postavimo znova (z novima stolpcema).
ALTER TABLE dogodek DROP COLUMN rangov_v_zreb;

ALTER TABLE dogodek ADD CONSTRAINT dogodek_nivoji_samo_sv CHECK (
    (stevilo_nivojev IS NULL AND velikosti_nivojev IS NULL
     AND stevilo_zrebov IS NULL AND velikost_zrebov IS NULL)
    OR sistem_tekmovanja = 'SV_REGIJA');
