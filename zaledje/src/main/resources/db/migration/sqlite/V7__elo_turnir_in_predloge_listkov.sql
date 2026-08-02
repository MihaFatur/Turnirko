-- ============================================================================
-- Turnirko: ELO-stikalo za turnirje in izbira predloge ligaskega zapisnika
--
--  1) Turnir doslej ni imel izbire, ali njegove tekme stejejo v klubski ELO -
--     obracunale so se vedno. Liga tako stikalo (steje_v_elo) ze ima; zdaj ga
--     dobi tudi turnir. Privzeto STEJE (1), da se obnasanje obstojecih turnirjev
--     ne spremeni.
--  2) Liga izbere predlogo uradnega ekipnega zapisnika (NTZS). Variante se
--     razlikujejo po ligi (1. SNTL oz. 2./3. SNTL); vizualno delijo isto
--     ogrodje. Privzeto 2./3. SNTL kot najpogostejsa.
--
-- Turnir NE dobi izbire predloge: turnirski listek je en sam (uraden sodniski
-- listek), zato zanj poseben stolpec ni potreben.
-- ============================================================================

ALTER TABLE turnir ADD COLUMN steje_v_elo INTEGER NOT NULL DEFAULT 1
    CHECK (steje_v_elo IN (0, 1));

ALTER TABLE liga ADD COLUMN predloga_listka TEXT NOT NULL DEFAULT 'SNTL_23'
    CHECK (predloga_listka IN ('SNTL_1', 'SNTL_23'));
