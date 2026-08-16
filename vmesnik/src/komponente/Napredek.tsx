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

/* Napredek v enovrsticni vrstici telefona: samo 4 px palica, brez mono
   oznake - stevilka "42/61" je ze del mono vrstice pod imenom in bi jo palica
   z oznako podvojila. Izrise se samo pri tekmovanju, ki tece; pri pripravi in
   zakljucku palica ne pove nic, kar ne bi povedal ze status. */
export function PalicaMobi({
  odigranih,
  vseh,
  naModri = false,
}: {
  odigranih: number
  vseh: number
  /* Pas "Danes v dvorani" stoji na modri ploskvi - tam je podlaga palice
     bela, ker bi se sivo polnilo z njo zlilo. */
  naModri?: boolean
}) {
  const delez = vseh > 0 ? Math.round((odigranih / vseh) * 100) : 0
  return (
    <span className={'palica palica--tanka' + (naModri ? ' palica--na-modri' : '')}>
      <span className="palica__polnilo" style={{ width: `${delez}%` }} />
    </span>
  )
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
