/* Skeniran PDF (tiskalnik s podajalnikom: kup zapisnikov kola v eni datoteki) v
   slike JPEG, ena na stran - te gredo nato po isti poti kot fotografije s
   telefona (vrsta, strežnik). Strežnik PDF-ja ne bere: brskalnik ga nariše s
   pdf.js, ki se naloži šele ob prvem PDF-ju (velik je, večina organizatorjev ga
   ne potrebuje). */

/* Največ strani v eni datoteki: kolo ima nekaj srečanj, meja strežnika je 120
   fotografij v 10 minutah. */
export const NAJVEC_STRANI_PDF = 60

export function jePdf(datoteka: Blob & { name?: string }): boolean {
  return datoteka.type === 'application/pdf' || (datoteka.name ?? '').toLowerCase().endsWith('.pdf')
}

/* Vsaka stran kot JPEG, daljša stranica najvecStranica px (sken pri 300 dpi je
   ~3500 px; več ne izboljša branja, le podaljša pošiljanje). */
export async function stranPdfVSlike(datoteka: Blob, najvecStranica: number): Promise<Blob[]> {
  const pdfjs = await import('pdfjs-dist')
  const { default: delavec } = await import('pdfjs-dist/build/pdf.worker.min.mjs?url')
  pdfjs.GlobalWorkerOptions.workerSrc = delavec
  const dokument = await pdfjs.getDocument({ data: new Uint8Array(await datoteka.arrayBuffer()) }).promise
  try {
    if (dokument.numPages > NAJVEC_STRANI_PDF) {
      throw new Error(`PDF ima ${dokument.numPages} strani; naloži največ ${NAJVEC_STRANI_PDF} naenkrat.`)
    }
    const slike: Blob[] = []
    for (let i = 1; i <= dokument.numPages; i++) {
      const stran = await dokument.getPage(i)
      const osnova = stran.getViewport({ scale: 1 })
      const pogled = stran.getViewport({ scale: najvecStranica / Math.max(osnova.width, osnova.height) })
      const platno = document.createElement('canvas')
      platno.width = Math.round(pogled.width)
      platno.height = Math.round(pogled.height)
      const risba = platno.getContext('2d')
      if (!risba) throw new Error('Brskalnik ne more narisati strani PDF.')
      risba.fillStyle = '#fff'
      risba.fillRect(0, 0, platno.width, platno.height)
      await stran.render({ canvas: platno, canvasContext: risba, viewport: pogled }).promise
      slike.push(
        await new Promise<Blob>((razresi, zavrni) =>
          platno.toBlob((b) => (b ? razresi(b) : zavrni(new Error('Strani PDF ni bilo mogoče pripraviti.'))),
            'image/jpeg', 0.9),
        ),
      )
      stran.cleanup()
    }
    return slike
  } finally {
    await dokument.destroy()
  }
}
