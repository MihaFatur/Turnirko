-- ============================================================================
-- Turnirko: placilo, po katerem racun ni nastal (V65, nov 2026)
-- Samo PostgreSQL (mapa sqlite/ je zamrznjena).
--
-- Cakajoca registracija (V48) se je doslej izbrisala v dveh primerih, ko je
-- placilo ze prispelo: webhook je vrstico zbrisal tudi, ce racun ni mogel
-- nastati (naslov je medtem zasedel potrjen racun), nocno ciscenje pa po 4
-- dneh, cetudi je obdelava placila ves cas padala. V obeh je placnik ostal
-- brez sledi v bazi - samo opozorilo v dnevniku.
--
-- placano_ob:      Stripe je potrdil placilo te registracije. Taka vrstica se
--                  pri nocnem ciscenju NE izbrise; odstrani jo webhook, ko
--                  racun nastane, ali admin, ko placilo uredi rocno.
-- stripe_seja_id:  seja Checkouta (cs_...), po kateri admin placilo najde v
--                  Stripu.
-- razlog_zastoja:  zakaj racun ni nastal (PlacilaStoritev.Zastoj); po njem
--                  webhook ve, da je admina o tem ze obvestil (Stripe isti
--                  dogodek ponavlja do 3 dni).
-- Obstojece vrstice so neplacane, zato ostanejo NULL.
-- ============================================================================

ALTER TABLE cakajoca_registracija
    ADD COLUMN placano_ob     timestamp(3),
    ADD COLUMN stripe_seja_id text,
    ADD COLUMN razlog_zastoja text;
