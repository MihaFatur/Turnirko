-- ============================================================================
-- Turnirko: prestavljena srecanja lige
--
-- Srecanje, ki se ne igra v kolu, v katero ga je uvrstil zreb, organizator
-- premakne v drugo kolo (ekipi sta se dogovorili za zamenjavo terminov) ali ga
-- pusti brez termina, dokler novi dan ni znan. Razpored kaze srecanje samo v
-- kolu, v katerem se igra; kolo je zato od zdaj "kje se srecanje igra" in ne
-- vec "kam ga je uvrstil zreb".
--
--   izvirno_kolo        kolo, v katerega je srecanje uvrstil zreb, kadar je
--                       prestavljeno; prazno = srecanje je tam, kamor sodi.
--                       Ni pogoj za preracun: potrebuje ga razpored ekipe
--                       (kolo, v katerem je imela srecanje, ni "prosto kolo")
--                       in prestavitev nazaj.
--   caka_na_termin      1 = srecanje je prestavljeno in novi termin se ni
--                       znan. Tako srecanje ne sodi v nobeno kolo (kolo ostane
--                       izvirno), nima termina in ne steje ne za odigrano kolo
--                       ne za "naslednje".
--   razlog_prestavitve  neobvezen razlog, ki ga organizator vpise ob
--                       prestavitvi; viden vsem, zato brez osebnih podatkov.
-- ============================================================================

ALTER TABLE srecanje ADD COLUMN izvirno_kolo INTEGER
    CHECK (izvirno_kolo IS NULL OR izvirno_kolo >= 1);

ALTER TABLE srecanje ADD COLUMN caka_na_termin INTEGER NOT NULL DEFAULT 0
    CHECK (caka_na_termin IN (0, 1));

ALTER TABLE srecanje ADD COLUMN razlog_prestavitve TEXT
    CHECK (razlog_prestavitve IS NULL OR length(razlog_prestavitve) <= 200);
