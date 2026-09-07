import type { MiddlewareHandler } from 'astro';

export const onRequest: MiddlewareHandler = async ({ request }, next) => {
  const response = await next();
  try {
    const url = new URL(request.url);
    response.headers.append('Link', `</llms.txt>; rel="describedby"`);
    const p = url.pathname;
    // Per-page mirrors only exist for paths under /docs/<segment>/. The bare
    // `/docs` route is the landing page (no MDX source) and `/docs.md` is the
    // aggregate llms-full endpoint, not a mirror of `/docs`.
    const isDocs = p.startsWith('/docs/') && p !== '/docs/';
    if (isDocs && !p.endsWith('.md')) {
      const stripped = p.replace(/\/$/, '');
      response.headers.append('Link', `<${stripped}.md>; rel="alternate"; type="text/markdown"`);
    }
  } catch {
    /* headers are best-effort */
  }
  return response;
};
