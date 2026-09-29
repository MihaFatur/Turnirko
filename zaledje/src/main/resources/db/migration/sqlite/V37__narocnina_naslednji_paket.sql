-- ============================================================================
-- Turnirko: stran "Narocnina" organizatorja - zabelezeno znizanje paketa
--
-- Organizator ima tri obsegovne pakete (Basic, Plus, Pro). Nadgradnja v visji
-- paket velja TAKOJ (Stripe obracuna sorazmerno doplacilo), znizanje pa sele
-- ob NASLEDNJI obnovi: placano obdobje je ze placano po visji ceni, zato se
-- danes ne spremeni nic in se nic ne vrne.
--
--   naslednji_paket   paket, ki zacne veljati ob naslednji obnovi (Stripe
--                     subscription schedule z dvema fazama, isto kot pri
--                     preklopu placevanja igralca). NULL = znizanja ni. To je
--                     ZABELEZENA NAMERA uporabnika: stolpec paket ostane tak,
--                     kot velja zdaj, dokler obdobje ne potece.
-- ============================================================================

ALTER TABLE narocnina ADD COLUMN naslednji_paket TEXT
    CHECK (naslednji_paket IS NULL
           OR naslednji_paket IN ('ORGANIZATOR_BASIC', 'ORGANIZATOR_PLUS', 'ORGANIZATOR_PRO'));
