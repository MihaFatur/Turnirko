/* Enoten prikaz napake iz API-ja (zaledje vraca slovenska sporocila). */
import { opisNapake } from '../api/odjemalec'

export function SporociloNapake({ napaka }: { napaka: unknown }) {
  if (!napaka) return null
  return <div className="napaka">{opisNapake(napaka)}</div>
}
