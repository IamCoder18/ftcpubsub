import type { APIRoute } from 'astro';
import { collectAllPages, buildLlmsFullTxt } from '~/lib/ai';

export const GET: APIRoute = async () => {
  const pages = await collectAllPages();
  let body = buildLlmsFullTxt(pages);
  if (body.length > 1_000_000) {
    body = body.slice(0, 1_000_000) + '\n\n<!-- truncated at 1 MB -->\n';
  }
  return new Response(body, {
    headers: {
      'content-type': 'text/plain; charset=utf-8',
      'cache-control': 'public, max-age=300',
    },
  });
};
