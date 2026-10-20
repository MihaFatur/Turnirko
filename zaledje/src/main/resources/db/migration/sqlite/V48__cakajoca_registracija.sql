-- ============================================================================
-- Turnirko: cakajoca registracija (placljiv paket ob registraciji)
--
-- Racun s placljivim paketom nastane sele, ko Stripe potrdi placilo. Doslej so
-- podatki obrazca do takrat potovali v Stripu kot metapodatki seje - tudi
-- zgosceno geslo, datum rojstva in naslov skrbnika. Te metapodatke pa vidi
-- vsak, ki bere Stripove dogodke: skrbnik Stripovega racuna in Moj Racun, ki
-- iz placil izdaja davcno potrjene racune. Zato podatki obrazca do placila
-- cakajo tu, v Stripe gre samo nakljucen zeton.
--
--   zeton          nakljucen niz v metapodatkih Stripove seje; po njem webhook
--                  najde vrstico. Ni skrivnost (sam ne odpre nicesar), mora pa
--                  biti nepredvidljiv, da ga ne ugane kdo drug.
--   geslo_hash     geslo, zgosceno ob vpisu (kot v uporabnik.geslo_hash);
--                  racun ga prevzame nespremenjenega.
--   ustvarjena_ob  vrstica velja 4 dni (seja 24 ur + Stripovi ponovni poskusi
--                  dostave webhooka do 3 dni), nato jo nocno ciscenje pobrise.
--
-- Vrstica gre, ko racun nastane (ali ne more nastati) - ne ostane kot kopija.
-- ============================================================================

CREATE TABLE cakajoca_registracija (
    id              INTEGER PRIMARY KEY,
    zeton           TEXT NOT NULL UNIQUE,
    ime             TEXT NOT NULL,
    priimek         TEXT NOT NULL,
    -- klub se lahko vmes zdruzi z drugim (V43): racun nastane brez zelje
    id_klub         INTEGER REFERENCES klub (id) ON DELETE SET NULL,
    email           TEXT NOT NULL,
    geslo_hash      TEXT NOT NULL,
    organizator     INTEGER NOT NULL CHECK (organizator IN (0, 1)),
    datum_rojstva   TEXT,
    email_skrbnika  TEXT,
    ustvarjena_ob   TEXT NOT NULL
);

CREATE INDEX idx_cakajoca_registracija_ustvarjena ON cakajoca_registracija (ustvarjena_ob);
