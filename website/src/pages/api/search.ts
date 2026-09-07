import type { APIRoute } from 'astro';
import { getCollection } from 'astro:content';
import { buildSearchIndex } from '~/lib/source';

export const GET: APIRoute = async () => {
  const entries = await getCollection('docs');
  const pages = await Promise.all(
    entries.map(async (e) => {
      const text = (e.body ?? '').replace(/```[\s\S]*?```/g, ' ');
      return {
        url: `/docs/${e.id.replace(/\.(md|mdx)$/, '')}`,
        title: e.data.title ?? e.id,
        description: e.data.description,
        content: text,
      };
    }),
  );
  const exported = await buildSearchIndex(pages);
  return new Response(exported, {
    headers: {
      'content-type': 'application/octet-stream',
      'cache-control': 'public, max-age=300',
    },
  });
};
