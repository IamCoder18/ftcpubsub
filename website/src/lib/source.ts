import { glob } from 'astro/loaders';
import { defineCollection, z } from 'astro:content';
import { create, insertMultiple, save } from '@orama/orama';

const docs = defineCollection({
  loader: glob({ pattern: '**/*.{md,mdx}', base: './src/content/docs' }),
  schema: z.object({
    title: z.string(),
    description: z.string().optional(),
    icon: z.string().optional(),
  }),
});

export { docs };

export async function buildSearchIndex(
  pages: Array<{ url: string; title: string; description?: string; content: string }>,
) {
  const db = create({
    schema: {
      url: 'string',
      title: 'string',
      description: 'string',
      content: 'string',
    } as const,
  });
  await insertMultiple(
    db,
    pages.map((p) => ({
      url: p.url,
      title: p.title,
      description: p.description ?? '',
      content: p.content.slice(0, 4000),
    })),
  );
  return save(db);
}
