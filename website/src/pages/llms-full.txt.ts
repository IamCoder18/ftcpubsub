import type { APIRoute } from 'astro';
import { collectAllPages, buildLlmsFullTxt } from '~/lib/ai';

const MAX_BYTES = 1_000_000;
const TRUNCATION_MARK = '\n\n<!-- truncated at 1 MB -->\n';

export const GET: APIRoute = async () => {
  const pages = await collectAllPages();
  const body = buildLlmsFullTxt(pages);
  const enc = new TextEncoder();
  if (enc.encode(body).length <= MAX_BYTES) {
    return new Response(body, {
      headers: {
        'content-type': 'text/plain; charset=utf-8',
        'cache-control': 'public, max-age=300',
      },
    });
  }
  // Byte-safe truncation that won't split a multi-byte character. `stream: true`
  // suppresses the trailing U+FFFD replacement when the byte slice ends inside
  // an incomplete UTF-8 sequence, so the decoded response stays within the cap.
  const bytes = enc.encode(body);
  let truncated = body;
  if (bytes.length > MAX_BYTES - enc.encode(TRUNCATION_MARK).length) {
    const limit = MAX_BYTES - enc.encode(TRUNCATION_MARK).length;
    truncated = new TextDecoder('utf-8').decode(bytes.subarray(0, limit), { stream: true }) + TRUNCATION_MARK;
  }
  return new Response(truncated, {
    headers: {
      'content-type': 'text/plain; charset=utf-8',
      'cache-control': 'public, max-age=300',
    },
  });
};
