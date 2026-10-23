-- ============================================================================
-- Turnirko: obvestilo po e-posti po vsakem kolu lige (23. 10. 2026)
--
-- Gost pride pogledat izide svoje lige in odide; racun mu doslej ni dal
-- razloga, da bi se vrnil. Po vsakem odigranem kolu zato racun dobi e-posto
-- z izidi kola, lestvico in svojimi tekmami - za lige, ki jih spremlja, in
-- lige, v katerih igra (ObvestilaPoKoluStoritev).
--
-- uporabnik.obvestila_po_kolu: ali racun obvestila zeli. Privzeto DA - racun
--   ligo spremlja oz. v njej igra, obvestilo je del te storitve. Izklopi jih v
--   meniju racuna ali z odjavo iz same e-poste.
-- uporabnik.zeton_odjave: nakljucen zeton za odjavo brez prijave (povezava v
--   e-posti in glava List-Unsubscribe). Nastane ob prvem obvestilu; ne odpre
--   nicesar razen izklopa obvestil.
-- obvestilo_kola: katero kolo katere lige je bilo ze obdelano, da gre vsako
--   kolo ven najvec enkrat. Vrstica nastane tudi za kolo, ki ga preskocimo
--   (preskoceno = 1): starejse kolo, ki se je dopolnilo za novejsim, ni vec
--   novica.
-- ============================================================================

ALTER TABLE uporabnik ADD COLUMN obvestila_po_kolu INTEGER NOT NULL DEFAULT 1
    CHECK (obvestila_po_kolu IN (0, 1));

ALTER TABLE uporabnik ADD COLUMN zeton_odjave TEXT;

CREATE UNIQUE INDEX idx_uporabnik_zeton_odjave ON uporabnik (zeton_odjave)
    WHERE zeton_odjave IS NOT NULL;

CREATE TABLE obvestilo_kola (
    id_liga      INTEGER NOT NULL REFERENCES liga (id) ON DELETE CASCADE,
    kolo         INTEGER NOT NULL,
    obdelano_ob  TEXT NOT NULL,
    prejemnikov  INTEGER NOT NULL DEFAULT 0,
    preskoceno   INTEGER NOT NULL DEFAULT 0 CHECK (preskoceno IN (0, 1)),
    PRIMARY KEY (id_liga, kolo)
);
