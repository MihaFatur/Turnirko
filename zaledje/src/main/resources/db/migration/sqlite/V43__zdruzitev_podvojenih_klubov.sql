-- Zdruzitev klubov, ki sta jih uvoza zapisala dvakrat (oktober 2026).
--
-- Stara stran NTZS (sezone do 2023/24) in Stupa (od 2024/25) isti klub
-- imenujeta razlicno ("Namiznoteniški klub Krka" in "NAMIZNOTENIŠKI KLUB KRKA
-- NOVO MESTO"), uvoz Stupe pa klub po imenu najde le, ce se poenostavljeni
-- imeni ujemata. Zato je nastal drugi zapis in filter po klubu na lestvici je
-- klub razbil na dva (Krka 4 in KRKA NOVO MESTO 17 ...).
--
-- Par je dolocen z IDENTIFIKATORJEMA PRI VIROMA (zunanja_povezava), ne z id-ji
-- v tej bazi - zato migracija na vsaki namestitvi zdruzi iste klube, kjer pa
-- para ni (nova namestitev brez uvoza), ne naredi nicesar. Ostane zapis stare
-- strani (starejsi, vec sklicev), povezava Stupe se preusmeri nanj: naslednja
-- sinhronizacija klub najde po povezavi (IdentitetaStupe.klub) in drugega ne
-- ustvari znova.
--
-- Kjer je ime na stari strani zastarelo (sponzor, staro ime), dobi zapis
-- danasnje ime. "NAMIZNOTENIŠKI KLUB SAVINJA" iz Stupe NI zdruzen: igralce
-- deli tako s Savinjo Luce kot s Savinjo Zalec, zato ga mora razvrstiti lastnik.

CREATE TEMP TABLE zdruzitev_klubov (
    zunanji_stara TEXT NOT NULL,
    zunanji_stupa TEXT NOT NULL,
    novo_ime      TEXT
);

INSERT INTO zdruzitev_klubov (zunanji_stara, zunanji_stupa, novo_ime) VALUES
    ('ntk_krka',              '16812', NULL),
    ('ntk_inter_diskont',     '16805', NULL),
    ('ntk_gorica',            '16802', NULL),
    ('ntk_arrigoni',          '16792', NULL),
    ('ntk_b2_center',         '16793', 'Namiznoteniški klub B2'),
    ('ntk_sobota',            '16824', 'Namiznoteniški klub Sobota'),
    ('ntk_ljutomer',          '16815', 'TVD Partizan Ljutomer'),
    ('ntk_muta',              '16825', 'Namiznoteniški klub Muta'),
    ('nts_menges',            '16820', NULL),
    ('ntk_zalec',             '16856', 'Namiznoteniški klub Žalec'),
    ('sd_krka_kg_grosuplje',  '19202', NULL);

-- cilj = klub stare strani, odvec = klub Stupe (samo, ce sta res dva zapisa)
CREATE TEMP TABLE par_klubov AS
SELECT c.id_lokalni AS cilj, o.id_lokalni AS odvec, z.novo_ime AS novo_ime
FROM zdruzitev_klubov z
JOIN zunanja_povezava c ON c.vir = 'STARA_NTZS' AND c.vrsta = 'KLUB' AND c.zunanji_id = z.zunanji_stara
JOIN zunanja_povezava o ON o.vir = 'STUPA' AND o.vrsta = 'KLUB' AND o.zunanji_id = z.zunanji_stupa
WHERE c.id_lokalni <> o.id_lokalni
  AND EXISTS (SELECT 1 FROM klub WHERE id = c.id_lokalni)
  AND EXISTS (SELECT 1 FROM klub WHERE id = o.id_lokalni);

UPDATE igralec SET id_klub = (SELECT cilj FROM par_klubov WHERE odvec = igralec.id_klub)
WHERE id_klub IN (SELECT odvec FROM par_klubov);

UPDATE turnir SET id_klub_lastnik = (SELECT cilj FROM par_klubov WHERE odvec = turnir.id_klub_lastnik)
WHERE id_klub_lastnik IN (SELECT odvec FROM par_klubov);

UPDATE liga SET id_klub_lastnik = (SELECT cilj FROM par_klubov WHERE odvec = liga.id_klub_lastnik)
WHERE id_klub_lastnik IN (SELECT odvec FROM par_klubov);

UPDATE uporabnik SET id_klub = (SELECT cilj FROM par_klubov WHERE odvec = uporabnik.id_klub)
WHERE id_klub IN (SELECT odvec FROM par_klubov);

UPDATE uporabnik SET id_klub_zelja = (SELECT cilj FROM par_klubov WHERE odvec = uporabnik.id_klub_zelja)
WHERE id_klub_zelja IN (SELECT odvec FROM par_klubov);

-- Ekipe: lige in dogodki stare strani imajo samo klube stare strani, tisti
-- iz Stupe samo klube Stupe, zato (liga, klub, zaporedna) ne trci.
UPDATE ekipa SET id_klub = (SELECT cilj FROM par_klubov WHERE odvec = ekipa.id_klub)
WHERE id_klub IN (SELECT odvec FROM par_klubov);

UPDATE prijava SET id_klub_ob_prijavi = (SELECT cilj FROM par_klubov WHERE odvec = prijava.id_klub_ob_prijavi)
WHERE id_klub_ob_prijavi IN (SELECT odvec FROM par_klubov);

UPDATE prijava SET id_klub_ob_prijavi_2 = (SELECT cilj FROM par_klubov WHERE odvec = prijava.id_klub_ob_prijavi_2)
WHERE id_klub_ob_prijavi_2 IN (SELECT odvec FROM par_klubov);

-- Vse povezave odvecnega kluba (pri B2 in Soboti ima Stupa dve) kazejo na cilj.
UPDATE zunanja_povezava SET id_lokalni = (SELECT cilj FROM par_klubov WHERE odvec = zunanja_povezava.id_lokalni)
WHERE vrsta = 'KLUB' AND id_lokalni IN (SELECT odvec FROM par_klubov);

UPDATE klub SET ime = (SELECT novo_ime FROM par_klubov WHERE cilj = klub.id)
WHERE id IN (SELECT cilj FROM par_klubov WHERE novo_ime IS NOT NULL);

DELETE FROM klub WHERE id IN (SELECT odvec FROM par_klubov);

DROP TABLE par_klubov;
DROP TABLE zdruzitev_klubov;
