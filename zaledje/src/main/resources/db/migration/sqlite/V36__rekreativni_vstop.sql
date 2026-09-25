-- ============================================================================
-- Turnirko rating: rekreativni vstop igralca
--
-- Novinec zacne pri STAROSTNEM SIDRU (V21): mediani registriranih igralcev
-- NTZS iste starosti in spola - pri odraslem moskem 1533. Za igralca, ki pride
-- iz rekreacije (Savinja liga, september 2026: ~80 % novincev), je to 700-900
-- tock previsoko. Uvrstitev prvega dne ga sicer popravi, a le, ce igra proti
-- ze ocenjenim igralcem; kdor prvi vecer igra samo novince, obstane pri 1500+
-- in pristane na lestvici pred vecino ligasev NTZS (izmerjeno: 47. od 459).
--
-- rekreativni_vstop = 1: igralec zacne pri 800 namesto pri sidru; 800 je tudi
-- izhodisce, proti kateremu ga vlece uvrstitev novinca. Oznako doloci clovek
-- ob vpisu igralca (organizator ali admin), ker aplikacija rekreativca ob prvi
-- tekmi ne more prepoznati.
--
-- To NI zastavica lestvice rekreativcev (RekreativecStoritev): tista se
-- izpelje iz stevila tekem na uradnih in klubskih tekmovanjih in se ne hrani.
-- ============================================================================

ALTER TABLE igralec ADD COLUMN rekreativni_vstop INTEGER NOT NULL DEFAULT 0
    CHECK (rekreativni_vstop IN (0, 1));
