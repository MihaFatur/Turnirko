-- Rocni vrstni red pripravljenih tekem na strani Mize (24. 11. 2026, docs/pravila/mize.md).
-- Organizator tekmo v vrsti »Na vrsti« povlece na drugo mesto; zapise se celoten
-- vrstni red vrste, kot ga je videl (id-ji tekem, besedilo "12,45,7"). Predlog tekem
-- (PredlogTekem) tekme s tega seznama postavi naprej v tem vrstnem redu, nove tekme za
-- njimi po samodejnem vrstnem redu. NULL = samodejni vrstni red.
ALTER TABLE turnir
    ADD COLUMN vrstni_red_tekem text;
