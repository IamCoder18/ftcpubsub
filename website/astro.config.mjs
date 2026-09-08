// @ts-check
import { defineConfig } from 'astro/config';
import react from '@astrojs/react';
import mdx from '@astrojs/mdx';
import tailwindcss from '@tailwindcss/vite';
import { markdownMirrors } from './integrations/markdown-mirrors.ts';

export default defineConfig({
  // No `site` set on purpose. Astro uses `site` to absolute-ize canonical
  // URLs, sitemap entries, og:url, and JSON-LD. We don't want any one
  // domain hard-coded; each request derives its URLs from the request's
  // Host header via `Astro.url` in BaseLayout.astro.
  //
  // The official `@astrojs/sitemap` integration requires `site`, so we
  // omit it and emit our own domain-free sitemap via a custom endpoint
  // at /sitemap.xml (see src/pages/sitemap.xml.ts).
  output: 'static',
  integrations: [
    react(),
    mdx(),
    markdownMirrors(),
  ],
  vite: {
    plugins: [tailwindcss()],
    optimizeDeps: {
      exclude: ['fumadocs-ui'],
    },
  },
  markdown: {
    shikiConfig: {
      theme: 'github-dark-default',
      wrap: true,
    },
  },
});
