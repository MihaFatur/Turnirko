/* Barvna znacka statusa turnirja/dogodka. */
import type { StatusTekmovanja } from '../api/tipi'
import { OZNAKE_STATUS_TEKMOVANJA } from '../api/tipi'

export function ZnackaStatusa({ status }: { status: StatusTekmovanja }) {
  return (
    <span className={`znacka znacka--${status}`}>
      {OZNAKE_STATUS_TEKMOVANJA[status]}
    </span>
  )
}
