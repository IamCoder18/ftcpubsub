import type { APIRoute } from 'astro';
import { collectAllPages, buildLlmsTxt } from '~/lib/ai';

export const GET: APIRoute = async () => {
  const pages = await collectAllPages();
  const body = buildLlmsTxt(pages);
  return new Response(body, {
    headers: {
      'content-type': 'text/plain; charset=utf-8',
      'cache-control': 'public, max-age=300',
    },
  });
};
