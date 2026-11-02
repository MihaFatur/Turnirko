/* Kode popusta ob registraciji: besedila ugodnosti in znesek po popustu.

   Pravila (kaj koda velja, kje) so na strežniku (KodePopustaStoritev); to tu je samo
   branje odgovora za prikaz v povzetku registracije in v administratorjevem seznamu.
   Znesek po popustu mora biti enak kot na strežniku in v Stripu, zato isto zaokrožanje:
   popust se zaokroži, ostanek je plačilo. */
import { OZNAKE_PAKET, type Paket, type VrstaKodePopusta } from '../api/tipi'

/* Kar o ugodnosti pove odgovor predogleda in vrstica seznama. */
interface Ugodnost {
  vrsta: VrstaKodePopusta | null
  meseci: number | null
  odstotek: number | null
  paket: Paket | null
}

/* »1 mesec«, »2 meseca«, »3 meseci«, »6 mesecev« (ednina, dvojina, množina). */
export function mesecev(n: number): string {
  const beseda = n === 1 ? 'mesec' : n === 2 ? 'meseca' : n === 3 || n === 4 ? 'meseci' : 'mesecev'
  return `${n} ${beseda}`
}

/* Ugodnost v besedilu, kot jo bere oseba v povzetku registracije in admin v seznamu. */
export function opisUgodnosti(u: Ugodnost): string {
  switch (u.vrsta) {
    case 'PREMIUM_MESECI':
      return `Premium brez plačila: ${mesecev(u.meseci ?? 1)}`
    case 'NAVIJAC_MESECI':
      return `Navijač brez plačila: ${mesecev(u.meseci ?? 1)}`
    case 'ORGANIZATOR_MESECI':
      return `${u.paket ? OZNAKE_PAKET[u.paket] : 'Organizator'} brez plačila: ${mesecev(u.meseci ?? 1)}`
    case 'ODSTOTEK':
      return `−${u.odstotek ?? 0} % na prvo plačilo`
    default:
      return ''
  }
}

/* Prvo plačilo po popustu v evrih (isto kot KodePopustaStoritev.prvoPlacilo). */
export function prvoPlacilo(cena: number, odstotek: number): number {
  const centi = Math.round(cena * 100)
  return (centi - Math.round((centi * odstotek) / 100)) / 100
}
