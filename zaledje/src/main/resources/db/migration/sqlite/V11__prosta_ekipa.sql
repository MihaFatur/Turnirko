-- ============================================================================
-- Turnirko: prosta ekipa (ekipa v ligi brez zapisa v registru klubov)
--
-- Doslej je bila ekipa vedno nastop KLUBA: id_klub je bil obvezen, zato je
-- bilo ekipo mogoce prijaviti sele, ko je klub obstajal v sifrantu. Za
-- rekreacijske in medpodjetniske lige to ne drzi - tam nastopi zasedba, ki
-- kluba nima in ga tudi ne bo dobila ("Kuhinja", "Gasilci Sempeter"). Vpis
-- takih zasedb v sifrant klubov bi ga zasul z enkratnimi zapisi in jih
-- ponudil vsem drugim tekmovanjem.
--
-- Zato postane id_klub NEOBVEZEN. Prosta ekipa zivi samo v svoji ligi
-- (ekipa.id_liga) in nikjer drugje; kader ostane nespremenjen - igralci so
-- se naprej iz skupnega registra igralcev, zato tudi ELO in profili tecejo
-- kot doslej.
--
-- Ime: pri klubski ekipi je ime se vedno neobvezno (sestavi se iz kluba in
-- zaporedne, npr. "Savinja 2"), pri prosti pa je edini vir imena in zato
-- obvezno - to varuje nova omejitev CHECK.
--
-- SQLite omejitve NOT NULL ne zna odstraniti, zato tabelo prezidamo. Nanjo
-- kazeta tuja kljuca (kader_ekipe.id_ekipa in obe strani srecanja), zato
-- migracija tece izven transakcije z zacasno izklopljenimi tujimi kljuci -
-- enako kot V5 in V9 in po uradnem postopku iz dokumentacije SQLite.
-- Glej V11__prosta_ekipa.sql.conf.
-- ============================================================================

PRAGMA foreign_keys = OFF;

CREATE TABLE ekipa_nov (
    id              INTEGER PRIMARY KEY,
    id_liga         INTEGER NOT NULL REFERENCES liga (id),
    -- NEOBVEZEN: prazen pomeni prosto ekipo, ki nastopa samo v tej ligi
    id_klub         INTEGER REFERENCES klub (id),
    -- zaporedna ekipa kluba v ligi (1 = prva mostvo, npr. "Savinja 1");
    -- prosta ekipa je vedno 1, ker je nima s cim steti
    zaporedna       INTEGER NOT NULL DEFAULT 1 CHECK (zaporedna >= 1),
    -- prikazano ime; pri klubski ekipi neobvezno (sestavi se iz kluba in
    -- zaporedne), pri prosti obvezno
    ime             TEXT CHECK (ime IS NULL OR length(trim(ime)) BETWEEN 2 AND 60),
    verzija         INTEGER NOT NULL DEFAULT 0,
    UNIQUE (id_liga, id_klub, zaporedna),
    -- ekipa brez kluba brez imena ne bi imela nobenega poimenovanja
    CHECK (id_klub IS NOT NULL OR ime IS NOT NULL)
);

INSERT INTO ekipa_nov (id, id_liga, id_klub, zaporedna, ime, verzija)
SELECT id, id_liga, id_klub, zaporedna, ime, verzija FROM ekipa;

DROP TABLE ekipa;
ALTER TABLE ekipa_nov RENAME TO ekipa;

-- indeks je padel skupaj s staro tabelo (V3 ga je ustvaril nad njo)
CREATE INDEX idx_ekipa_liga ON ekipa (id_liga);

-- Dve prosti ekipi z istim imenom bi bili v razporedu in na lestvici
-- nelocljivi. UNIQUE (id_liga, id_klub, zaporedna) tega ne ujame, ker SQLite
-- steje NULL-e za razlicne med sabo - zato delni indeks samo nad prostimi
-- ekipami. Enakost velikih in malih crk ujame se LigaStoritev.dodajEkipo, ki
-- primerja prikazana imena vseh ekip lige.
CREATE UNIQUE INDEX idx_ekipa_prosta_ime ON ekipa (id_liga, ime) WHERE id_klub IS NULL;

-- Preveri, da prezidava ni pustila osirotelih vrstic (tuji kljuci so bili
-- izklopljeni, zato tega ni preverjal nihce drug).
PRAGMA foreign_key_check;

PRAGMA foreign_keys = ON;
