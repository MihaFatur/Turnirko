-- ============================================================================
-- Turnirko: stran "Narocnina" igralca - zacetek obdobja in zabelezen preklop
-- placevanja
--
-- Nova stran pokaze, kje v placanem obdobju je narocnik (od kdaj do kdaj, koliko
-- dni se velja) in omogoca preklop mesecno <-> letno ob NASLEDNJI obnovi.
-- Konec obdobja (trenutno_obdobje_do) je tabela ze imela; manjkata:
--
--   obdobje_od        zacetek TEKOCEGA placanega obdobja (Stripe
--                     current_period_start). Brez njega bi vmesnik zacetek
--                     ugibal iz cikla, letnica in dolzina meseca pa ugibanje
--                     pokvarita. NULL pri vrsticah izpred te migracije - branje
--                     ga takrat izpelje iz konca obdobja in cikla, prvi webhook
--                     ali obnova pa ga zapise prav.
--
--   naslednji_ciklus  cikel, ki zacne veljati ob naslednji obnovi (Stripe
--                     subscription schedule). NULL = preklopa ni. To je
--                     ZABELEZENA NAMERA uporabnika, ne cikel, ki ga Stripe
--                     zaracunava zdaj (ta ostane v stolpcu ciklus, dokler
--                     obdobje ne potece).
-- ============================================================================

ALTER TABLE narocnina ADD COLUMN obdobje_od TEXT;

ALTER TABLE narocnina ADD COLUMN naslednji_ciklus TEXT
    CHECK (naslednji_ciklus IS NULL OR naslednji_ciklus IN ('MESECNO', 'LETNO'));
