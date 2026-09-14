-- ============================================================================
-- Turnirko rating: sistem se ne imenuje vec "klubski ELO"
--
-- Stolpec "sistem" v obeh ratinskih tabelah je odprto besedilo in nosi ime
-- sistema, po katerem je vrednost izracunana - zato, da jih zna baza hraniti
-- vec (kasneje npr. tocke NTZS). Doslej je bila edina vrednost 'KLUBSKI_ELO'.
--
-- Ime je zdaj "Turnirko rating": sistem ni vec navaden Elo (ta je le osnova),
-- ampak ima svoje dodatke - tezo tekmovanja, starostno sidro, uvrstitev
-- novinca, odbitek za neaktivnost. Vrednost je zato 'TURNIRKO'.
--
-- Preimenovanje je zgolj preimenovanje: nobena stevilka se ne spremeni in
-- ponovni preracun ni potreben.
-- ============================================================================

UPDATE rating_stanje    SET sistem = 'TURNIRKO' WHERE sistem = 'KLUBSKI_ELO';
UPDATE rating_zgodovina SET sistem = 'TURNIRKO' WHERE sistem = 'KLUBSKI_ELO';
