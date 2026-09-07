import type { APIRoute } from 'astro';
import { collectAllPages, buildLlmsFullTxt } from '~/lib/ai';

export const GET: APIRoute = async () => {
  const pages = await collectAllPages();
  const body = buildLlmsFullTxt(pages);
  return new Response(body, {
    headers: {
      'content-type': 'text/markdown; charset=utf-8',
      'cache-control': 'public, max-age=300',
    },
  });
};
