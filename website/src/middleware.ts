import type { MiddlewareHandler } from 'astro';

export const onRequest: MiddlewareHandler = async ({ request }, next) => {
  const response = await next();
  try {
    const url = new URL(request.url);
    response.headers.append('Link', `</llms.txt>; rel="describedby"`);
    const p = url.pathname;
    const isDocs = p === '/docs' || p === '/docs/' || p.startsWith('/docs/');
    if (isDocs && !p.endsWith('.md')) {
      const stripped = p.replace(/\/$/, '');
      response.headers.append('Link', `<${stripped}.md>; rel="alternate"; type="text/markdown"`);
    }
  } catch {
    /* headers are best-effort */
  }
  return response;
};
