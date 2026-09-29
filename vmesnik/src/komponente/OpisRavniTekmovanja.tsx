/* Razlaga ravni tekmovanja pod izbirnikom ravni (obrazec lige in obrazec
   turnirja). Ena kopija besedila, da se obrazca ne razideta. */
export function OpisRavniTekmovanja() {
  return (
    <p className="namig">
      Ravni tekmovanja:<br />
      Uradno tekmovanje - Organizirano s strani NTZS.<br />
      Klubsko tekmovanje - Mešanica registriranih igralcev, ki igrajo tudi uradna tekmovanja in rekreativcev.<br />
      Rekreativno tekmovanje - Igralci, ki se ne udeležujejo uradnih tekmovanj.
    </p>
  )
}
