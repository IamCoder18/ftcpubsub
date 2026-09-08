// Domain-free sitemap. The official @astrojs/sitemap integration requires
// the `site` config option (which would hard-code a hostname). Instead we
// emit a sitemap that lists paths only — crawlers resolve them against
// the request's Host. Listed routes: every HTML page plus the per-page
// markdown mirrors under /docs/* and the AI-friendly endpoints.

import { getCollection } from 'astro:content';

interface Entry {
  loc: string;
  changefreq?: 'daily' | 'weekly' | 'monthly';
  priority?: number;
}

export async function GET() {
  const entries: Entry[] = [];

  // Top-level pages (matches the routes that exist as static HTML).
  const topLevel = [
    '/',
    '/install/',
    '/changelog/',
    '/community/',
    '/docs/',
    '/404',
  ];
  for (const loc of topLevel) {
    entries.push({ loc, changefreq: 'weekly', priority: loc === '/' ? 1.0 : 0.7 });
  }

  // Docs pages + their .md mirrors.
  const docs = await getCollection('docs');
  for (const entry of docs) {
    const id = entry.id.replace(/\.(md|mdx)$/, '');
    const parts = id.split('/');
    if (parts[parts.length - 1] === 'index') parts.pop();
    if (parts.length === 0) continue;
    const slug = parts.join('/');
    if (!slug) continue;
    entries.push({ loc: `/docs/${slug}/`, changefreq: 'weekly', priority: 0.6 });
    entries.push({ loc: `/docs/${slug}.md`, changefreq: 'weekly', priority: 0.4 });
  }

  // AI-friendly endpoints.
  for (const loc of ['/llms.txt', '/llms-full.txt', '/docs.md']) {
    entries.push({ loc, changefreq: 'weekly', priority: 0.5 });
  }

  const body =
    '<?xml version="1.0" encoding="UTF-8"?>\n' +
    '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n' +
    entries
      .map((e) => {
        const cf = e.changefreq ? `    <changefreq>${e.changefreq}</changefreq>\n` : '';
        const pr = e.priority !== undefined ? `    <priority>${e.priority.toFixed(1)}</priority>\n` : '';
        return `  <url>\n    <loc>${e.loc}</loc>\n${cf}${pr}  </url>\n`;
      })
      .join('') +
    '</urlset>\n';

  return new Response(body, {
    headers: {
      'content-type': 'application/xml; charset=utf-8',
      'cache-control': 'public, max-age=300',
    },
  });
}
