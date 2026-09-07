import type { APIRoute } from 'astro';
import { getCollection } from 'astro:content';

export const GET: APIRoute = async () => {
  const entries = await getCollection('docs');
  const docs = await Promise.all(
    entries.map(async (e) => {
      const body = (e.body ?? '').replace(/```[\s\S]*?```/g, ' ');
      return {
        url: `/docs/${e.id.replace(/\.(md|mdx)$/, '')}`,
        title: e.data.title ?? e.id,
        description: e.data.description ?? '',
        content: body.slice(0, 4000),
      };
    }),
  );
  return new Response(JSON.stringify(docs), {
    headers: { 'content-type': 'application/json', 'cache-control': 'public, max-age=300' },
  });
};
