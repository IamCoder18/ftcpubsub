import type { AstroIntegration } from 'astro';
import { readFile, readdir, writeFile, mkdir } from 'node:fs/promises';
import { dirname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

interface ParsedFrontmatter {
  title?: string;
  description?: string;
}

const HEADER_RE = /^---\s*\n([\s\S]*?)\n---\s*\n([\s\S]*)$/;

function parseFrontmatter(md: string): { fm: ParsedFrontmatter; body: string } {
  const m = md.match(HEADER_RE);
  if (!m) return { fm: {}, body: md };
  const yaml = m[1];
  const body = m[2];
  const fm: ParsedFrontmatter = {};
  for (const line of yaml.split('\n')) {
    const kv = line.match(/^(\w+):\s*(.*)$/);
    if (!kv) continue;
    const key = kv[1];
    let val: string = kv[2].trim();
    if (val.startsWith('"') && val.endsWith('"')) val = JSON.parse(val) as string;
    if (key === 'title' || key === 'description') (fm as any)[key] = val;
  }
  return { fm, body };
}

async function walk(dir: string): Promise<string[]> {
  const out: string[] = [];
  let entries: string[] = [];
  try {
    entries = await readdir(dir);
  } catch {
    return out;
  }
  for (const name of entries) {
    const p = join(dir, name);
    let st: any;
    try {
      st = await import('node:fs').then((m) => m.statSync(p));
    } catch {
      continue;
    }
    if (st.isDirectory()) out.push(...(await walk(p)));
    else if (st.isFile() && /\.(md|mdx)$/.test(name)) out.push(p);
  }
  return out;
}

function normalizeSlug(id: string): string | null {
  let path = id.replace(/\.(md|mdx)$/, '');
  const parts = path.split('/');
  if (parts[parts.length - 1] === 'index') parts.pop();
  if (parts.length === 0) return null;
  if (parts.length === 1 && parts[0] === 'index') return null;
  return parts.join('/');
}

export function markdownMirrors(contentDir = './src/content/docs'): AstroIntegration {
  return {
    name: 'synapse-markdown-mirrors',
    hooks: {
      'astro:build:done': async ({ dir }) => {
        const outDir = fileURLToPath(dir);
        const files = await walk(contentDir);
        for (const file of files) {
          const id = relative(contentDir, file).replace(/\\/g, '/');
          const slug = normalizeSlug(id);
          if (!slug) continue;
          const raw = await readFile(file, 'utf8');
          const { fm, body } = parseFrontmatter(raw);
          const lines: string[] = [];
          if (fm.title) lines.push(`title: ${JSON.stringify(fm.title)}`);
          if (fm.description) lines.push(`description: ${JSON.stringify(fm.description)}`);
          const content = lines.length ? `---\n${lines.join('\n')}\n---\n\n${body}` : body;
          const outPath = join(outDir, 'docs', `${slug}.md`);
          await mkdir(dirname(outPath), { recursive: true });
          await writeFile(outPath, content, 'utf8');
        }
      },
    },
  };
}
