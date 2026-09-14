-- ============================================================================
-- Turnirko rating: raven tekmovanja namesto zastavice "steje v ELO"
--
-- Doslej je bilo vprasanje dvojisko: tekma steje ali ne steje. To je premalo.
-- Zmaga na clanskem turnirju NTZS in zmaga na rekreativnem turnirju nista
-- enako vredni informaciji, zato ima zdaj vsako tekmovanje raven, ta pa tezo,
-- s katero se mnozi sprememba ratinga:
--
--   URADNO      1,00   tekmovanja NTZS (DP, SNTL lige, pokal, TOP-8)
--   KLUBSKO     0,75   mocnejsa klubska tekmovanja (Savinja liga/tour)
--   REKREATIVNO 0,50   rekreativna tekmovanja in lige
--   NE_STEJE    0      prijateljski turnirji, treningi
--
-- Polnjenje za nazaj: vse, kar je v bazi, je uvozena zgodovina NTZS
-- (id_ustvaril IS NULL) - torej URADNO. Kar je nastalo v aplikaciji, dobi
-- KLUBSKO: klub si uradnega statusa ne more podeliti sam. Tekmovanja, ki so
-- imela steje_v_elo = 0, postanejo NE_STEJE.
--
-- Stara stolpca se pobriseta. SQLite od 3.35 zna ALTER TABLE DROP COLUMN tudi
-- pri stolpcu s CHECK omejitvijo (preveri se ob 3.50, ki jo uporabljamo), zato
-- prezidava tabel - kot pri V9, V12 in V13 - tokrat ni potrebna in migracija
-- lahko tece v obicajni transakciji.
-- ============================================================================

ALTER TABLE turnir ADD COLUMN raven TEXT NOT NULL DEFAULT 'KLUBSKO'
    CHECK (raven IN ('URADNO', 'KLUBSKO', 'REKREATIVNO', 'NE_STEJE'));

ALTER TABLE liga ADD COLUMN raven TEXT NOT NULL DEFAULT 'KLUBSKO'
    CHECK (raven IN ('URADNO', 'KLUBSKO', 'REKREATIVNO', 'NE_STEJE'));

UPDATE turnir
   SET raven = CASE
        WHEN steje_v_elo = 0      THEN 'NE_STEJE'
        WHEN id_ustvaril IS NULL  THEN 'URADNO'
        ELSE 'KLUBSKO'
       END;

UPDATE liga
   SET raven = CASE
        WHEN steje_v_elo = 0      THEN 'NE_STEJE'
        WHEN id_ustvaril IS NULL  THEN 'URADNO'
        ELSE 'KLUBSKO'
       END;

ALTER TABLE turnir DROP COLUMN steje_v_elo;
ALTER TABLE liga DROP COLUMN steje_v_elo;
