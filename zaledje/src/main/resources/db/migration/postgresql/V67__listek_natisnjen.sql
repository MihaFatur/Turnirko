-- ============================================================================
-- Turnirko: sled natisnjenih listkov tekem (V67, nov 2026)
-- Samo PostgreSQL (mapa sqlite/ je zamrznjena).
--
-- Organizator listke natisne ze med igro, rezultat pa se vnese sele pozneje.
-- Brez sledi se ob naslednjem tisku v isti kupcek pomesajo listki, ki so ze
-- na mizah, in tisti, ki jih sodniki se nimajo.
--
-- listek_natisnjen_ob   cas, ko je organizator potrdil, da je listek (ali
--                       ekipni zapisnik) te tekme natisnil; NULL = se ni.
--                       Oznaka ne vpliva na potek tekme: tekma ostane
--                       PRIPRAVLJENA / V_IGRI, listek pa se da natisniti znova.
--
-- Stolpec je namenoma na tekmi in ne v loceni tabeli: ena tekma ima en listek,
-- oznaka je lastnost te tekme in izgine skupaj z njo. Verzija tekme (@Version)
-- se ob oznaki ne poveca (skupinska posodobitev), da oznacevanje ne zavrne
-- socasnega vnosa rezultata.
-- ============================================================================

ALTER TABLE tekma ADD COLUMN listek_natisnjen_ob timestamp(3);
