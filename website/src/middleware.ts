import type { MiddlewareHandler } from 'astro';

export const onRequest: MiddlewareHandler = async ({ request }, next) => {
  const response = await next();
  try {
    const url = new URL(request.url);
    response.headers.append('Link', `</llms.txt>; rel="describedby"`);
    if (url.pathname.startsWith('/docs')) {
      const md = `${url.pathname}.md`;
      response.headers.append('Link', `<${md}>; rel="alternate"; type="text/markdown"`);
    }
  } catch {
    /* headers are best-effort */
  }
  return response;
};
