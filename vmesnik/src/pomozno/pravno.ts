/* Podatki o ponudniku za stran Pogoji in zasebnost (/pogoji).

   Na enem mestu, ker jih bere več strani (pogoji, zasebnost, kasneje računi)
   in se spremenijo hkrati. Prodajalec naročnin je Namiznoteniški klub Žalec
   (odločitev lastnika, 28. 9. 2026). Kar je null, še ni vpisano - stran
   vrstico izpusti, zato jih DOPOLNI pred objavo: brez naslova in e-pošte
   pogoji ne izpolnjujejo zahteve ZEPT po stalno dostopnih podatkih o
   ponudniku, brez kontakta pa igralec ne more uveljavljati pravic. */
export const PONUDNIK: {
  ime: string
  naslov: string | null
  maticna: string | null
  davcna: string | null
  eposta: string | null
  /* Kje teče strežnik (ime ponudnika gostovanja) - obdelovalec podatkov. */
  gostovanje: string | null
} = {
  ime: 'Namiznoteniški klub Žalec',
  naslov: null,
  maticna: null,
  davcna: null,
  eposta: null,
  gostovanje: null,
}

/* Od kdaj veljajo objavljeni pogoji (ISO datum); null = se ne izpiše. */
export const POGOJI_VELJAJO_OD: string | null = null
