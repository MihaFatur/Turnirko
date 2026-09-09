/* Ena sama vrsta za zahteve, ki v bazo PIŠEJO ob kliku na kvadratek.

   SQLite prenese enega pisca naenkrat — dva hkratna zapisa strežnik vrne kot
   500 —, v oknu za izbor lig pa gledalec odkljuka več lig v sekundi. Vrsta je
   modulska in ne v posameznem kavlju: oken je lahko več (izbor spremljanih
   lig, izbor lig za domačo stran) in vsako je svoj klic svojega kavlja, pisec
   baze pa je vseeno en sam. Prav zato je vrsta TU in ne v enem od njiju.

   (Vgrajeni »scope« TanStack Queryja tu ne pomaga: če se prva zahteva konča,
   preden druga pride do svojega premora, se znak za nadaljevanje izgubi in
   vrsta obstane.) */

let vrsta: Promise<unknown> = Promise.resolve()

export function vVrsto<T>(opravilo: () => Promise<T>): Promise<T> {
  const naVrsti = vrsta.then(opravilo, opravilo)
  /* Napaka ene zahteve ne sme podreti vrste za naslednje. */
  vrsta = naVrsti.catch(() => undefined)
  return naVrsti
}
