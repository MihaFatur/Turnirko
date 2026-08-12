/* Napredek tekmovanja: mono oznaka "Odigranih 84 / 181" nad 6 px palico.

   Palica je edino, kar loci dogodek z 12 igralci od dogodka s 100, zato stoji
   v vrstici in ne v kolofonu. Pred zrebom tekem se ni - takrat oznako
   zamenja besedilo "zreb se ni izveden", palica pa ostane prazna, da vrstica
   ohrani visino in poravnavo s sosednjimi. */

interface Lastnosti {
  odigranih: number
  vseh: number
  /* Zakljuceno tekmovanje: palica je polna in zelena (napredovanje je
     doseglo konec). */
  koncan?: boolean
}

export function Napredek({ odigranih, vseh, koncan = false }: Lastnosti) {
  const delez = vseh > 0 ? Math.round((odigranih / vseh) * 100) : 0
  return (
    <span className="napredek">
      <span className="napredek__oznaka">
        {vseh > 0 ? `Odigranih ${odigranih} / ${vseh}` : 'žreb še ni izveden'}
      </span>
      <span className="palica">
        <span
          className={'palica__polnilo' + (koncan ? ' palica__polnilo--koncan' : '')}
          style={{ width: `${delez}%` }}
        />
      </span>
    </span>
  )
}
