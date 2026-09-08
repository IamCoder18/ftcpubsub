import { getCollection } from 'astro:content';
import { docsUrlFromId } from '~/lib/source';

export interface PageMarkdown {
  url: string;
  title: string;
  description?: string;
  markdown: string;
}

export async function collectAllPages(): Promise<PageMarkdown[]> {
  const docs = await getCollection('docs');
  const out: PageMarkdown[] = [];
  for (const entry of docs) {
    const url = docsUrlFromId(entry.id);
    if (!url) continue;
    out.push({
      url,
      title: entry.data.title ?? entry.id,
      description: entry.data.description,
      markdown: entry.body ?? '',
    });
  }
  return out;
}

export function buildLlmsTxt(pages: PageMarkdown[]): string {
  const lines: string[] = [];
  lines.push('# Synapse');
  lines.push('');
  lines.push(
    '> A tiny, annotation-driven pub/sub bus for FIRST Tech Challenge robot code. One hardware thread, two-pool isolation, zero runtime dependencies.',
  );
  lines.push('');
  lines.push('## Docs');
  lines.push('');
  for (const p of [...pages].sort((a, b) => a.url.localeCompare(b.url))) {
    lines.push(`- [${p.title}](${p.url}.md): ${p.description ?? ''}`.trim());
  }
  lines.push('');
  lines.push('## Pages');
  lines.push('');
  // Use relative URLs so llms.txt works regardless of the deployment host.
  lines.push('- [Home](/)');
  lines.push('- [Install](/install)');
  lines.push('- [Changelog](/changelog)');
  lines.push('- [Community](/community)');
  lines.push('- [llms-full.txt](/llms-full.txt)');
  lines.push('');
  return lines.join('\n');
}

export function buildLlmsFullTxt(pages: PageMarkdown[]): string {
  const out: string[] = [];
  for (const p of [...pages].sort((a, b) => a.url.localeCompare(b.url))) {
    out.push(`\n\n# ${p.title}\n\nURL: ${p.url}\n\n${p.markdown}\n`);
  }
  return out.join('\n');
}
