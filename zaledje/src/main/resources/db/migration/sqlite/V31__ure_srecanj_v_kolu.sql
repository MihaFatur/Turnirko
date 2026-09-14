-- ============================================================================
-- Turnirko: ure srecanj v kolu
--
-- Do zdaj je bilo kolo krog kroznega sistema: vsaka ekipa v njem odigra eno
-- srecanje in vsa srecanja kola se zacnejo ob isti uri (V10). Lige, ki se
-- zberejo en vecer na teden, pa v kolu odigrajo VEC krogov zapored - npr. ob
-- 18.30 prvega in ob 19.45 drugega - in vsaka ekipa ta vecer igra dvakrat.
-- Kol je zato pol manj.
--
-- Liga zato dobi seznam ur: koliko ur je, toliko krogov kroznega sistema se
-- odigra v kolu (in toliko srecanj odigra vsaka ekipa), i-ti krog se zacne ob
-- i-ti uri. Zapis je besedilo "18:30,19:45"; ura 00:00 pomeni "ura ni
-- dolocena" (isto kot pri terminih kol). NULL pomeni dosedanje kolo - to je
-- enakovredna izbira in ne manjkajoca vrednost.
--
-- Ure so pravilo tekmovanja, ker je od njih odvisen zreb (koliko kol liga ima
-- in kdo igra v katerem), zato se po zrebu zaklenejo skupaj z ostalimi
-- pravili.
--
-- Srecanje si zapomni, ob kateri uri kola se igra (ura_v_kolu, 0 = prva).
-- Zacetek sam tega ne pove zanesljivo: liga brez datuma prvega kola ob zrebu
-- zacetkov nima, organizator pa sme uro posameznemu srecanju prestaviti -
-- polnilo terminov mora vseeno vedeti, katero uro lige srecanju vrne. Pri kolu
-- kroznega sistema je stolpec prazen.
-- ============================================================================

ALTER TABLE liga ADD COLUMN ure_srecanj TEXT;

ALTER TABLE srecanje ADD COLUMN ura_v_kolu INTEGER
    CHECK (ura_v_kolu IS NULL OR ura_v_kolu >= 0);
