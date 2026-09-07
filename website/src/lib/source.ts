import { glob } from 'astro/loaders';
import { defineCollection, z } from 'astro:content';

const docs = defineCollection({
  loader: glob({ pattern: '**/*.{md,mdx}', base: './src/content/docs' }),
  schema: z.object({
    title: z.string(),
    description: z.string().optional(),
    icon: z.string().optional(),
  }),
});

export const collections = { docs };

/**
 * Normalize a content-collection entry id to its public URL path.
 * - Strips the `.md`/`.mdx` extension.
 * - Drops a trailing `/index` segment so section roots render at `/docs/<section>`,
 *   and the docs root renders at `/docs`.
 * - Adds a leading `/docs/` if no prefix is present.
 *
 * Returns `null` for the docs landing id (e.g. `index`) — that page maps to `/docs`
 * with no slug parameter, which `getStaticPaths` represents as `undefined`.
 */
export function docsUrlFromId(id: string): string | null {
  let path = id.replace(/\.(md|mdx)$/, '');
  const parts = path.split('/');
  if (parts[parts.length - 1] === 'index') parts.pop();
  if (parts.length === 0) return null;
  if (parts.length === 1 && parts[0] === 'index') return null;
  return `/docs/${parts.join('/')}`;
}
