# Synapse website

Marketing + docs site for [Synapse](https://github.com/IamCoder18/synapse), an annotation-driven pub/sub bus for FIRST Tech Challenge robot code.

## Stack

- Astro 5 (static output)
- Tailwind CSS 4 (`@tailwindcss/vite`)
- React islands for canvas + copy button
- MDX for docs
- `@astrojs/sitemap`

## Develop

```bash
npm install
npm run dev
```

## Build

```bash
npm run build
npm run preview
```

## Docker

```bash
docker compose up --build
```

The site will be available at <http://localhost:8080>.

## AI-friendly endpoints

- `/llms.txt` — site summary for LLMs (llmstxt.org v2).
- `/llms-full.txt` — concatenated Markdown of every page (≤ 1 MB).
- `/docs.md` — Markdown dump of every docs page.
- `/api/search` — Orama search index blob.
- `/search.json` — JSON search index fallback for the in-page search dialog.
- Every docs page also has a `.md` mirror (e.g. `/docs/get-started/install.md`) and a `Link: <…>; rel="alternate"; type="text/markdown"` header.
