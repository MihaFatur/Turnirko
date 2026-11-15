/* ESLint za vmesnik: namenoma SAMO pravili React Hooks.

   Tipe in neuporabljene spremenljivke že preverja `tsc` (npm run build), slog
   pa ni predmet te varovalke. Pravili hookov lovita napake, ki jih prevajalnik
   ne vidi:
   - `rules-of-hooks` (NAPAKA): hook v pogoju, zanki ali za zgodnjim `return`
     pomeni, da se stanje komponente med izrisi pomeša. Ustavi `npm run lint`
     in s tem CI.
   - `exhaustive-deps` (OPOZORILO): učinek ali memo bere vrednost, ki je ni med
     odvisnostmi, torej lahko dela z zastarelo. Obstoječa opozorila se ne
     popravljajo na zalogo (vsako je treba presoditi posebej); nova koda naj
     jih ne dodaja.

   Širših naborov (`recommended`, pravila React Compilerja v vtičniku od
   različice 6 naprej) ne vklapljaj brez odločitve: v 66 tisoč vrsticah bi
   dali na stotine zadetkov in zakrili ta dva. */
import tsParser from '@typescript-eslint/parser'
import reactHooks from 'eslint-plugin-react-hooks'

export default [
  { ignores: ['dist/**', 'node_modules/**'] },
  {
    files: ['src/**/*.{ts,tsx}'],
    languageOptions: {
      parser: tsParser,
      parserOptions: { ecmaVersion: 'latest', sourceType: 'module', ecmaFeatures: { jsx: true } },
    },
    plugins: { 'react-hooks': reactHooks },
    rules: {
      'react-hooks/rules-of-hooks': 'error',
      'react-hooks/exhaustive-deps': 'warn',
    },
  },
]
