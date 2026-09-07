import type { APIRoute } from 'astro';
import { getCollection } from 'astro:content';
import { docsUrlFromId } from '~/lib/source';

export async function getStaticPaths() {
  return [{ params: {} }];
}

export const GET: APIRoute = async () => {
  const entries = await getCollection('docs');
  const pages = entries.flatMap((e) => {
    const url = docsUrlFromId(e.id);
    if (!url) return [];
    const text = (e.body ?? '').replace(/```[\s\S]*?```/g, ' ');
    return [{
      url,
      title: e.data.title ?? e.id,
      description: e.data.description ?? '',
      content: text,
    }];
  });
  return new Response(JSON.stringify(pages), {
    headers: { 'content-type': 'application/json', 'cache-control': 'public, max-age=300' },
  });
};
