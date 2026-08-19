/** Tailwind CSS v4 usa um plugin PostCSS dedicado; nao ha tailwind.config.js.
 *  Os tokens de design ficam em src/app/globals.css, no bloco @theme. */
const config = {
  plugins: {
    "@tailwindcss/postcss": {},
  },
};

export default config;
