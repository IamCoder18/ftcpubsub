import { useEffect, useState } from 'react';
import { create } from '@orama/orama';

interface Hit {
  url: string;
  title: string;
  description?: string;
}

export default function Search({ trigger }: { trigger?: string }) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [hits, setHits] = useState<Hit[]>([]);
  const [db, setDb] = useState<any>(null);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        setOpen((o) => !o);
      }
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, []);

  useEffect(() => {
    if (!open || db) return;
    fetch('/api/search')
      .then((r) => r.arrayBuffer())
      .then((buf) => {
        // Orama staticClient format: it's a binary blob we need to load with
        // its `load` helper. For simplicity, fall back to fetching raw JSON.
        return fetch('/search.json').then((r) => (r.ok ? r.json() : null));
      })
      .then((json) => {
        if (!json) return;
        const inst: any = create({
          schema: { url: 'string', title: 'string', description: 'string', content: 'string' } as any,
        });
        for (const row of json) inst.insert(row);
        setDb(inst);
      })
      .catch(() => {});
  }, [open, db]);

  useEffect(() => {
    if (!db || !query.trim()) {
      setHits([]);
      return;
    }
    (db as any)
      .search({ term: query, properties: ['title', 'description', 'content'] })
      .then((res: any) => {
        setHits(
          (res.hits ?? []).slice(0, 8).map((h: any) => ({
            url: h.document.url,
            title: h.document.title,
            description: h.document.description,
          })),
        );
      })
      .catch(() => setHits([]));
  }, [query, db]);

  if (!open) return null;

  return (
    <div className="syn-search-backdrop" onClick={() => setOpen(false)}>
      <div className="syn-search" role="dialog" aria-modal="true" aria-label="Search docs" onClick={(e) => e.stopPropagation()}>
        <input
          autoFocus
          type="search"
          placeholder="Search docs (try: SafeOpMode, hardware thread, topic)…"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
        <ul role="list">
          {hits.length === 0 && query && <li className="syn-search__empty">No results.</li>}
          {hits.map((h) => (
            <li key={h.url}>
              <a href={h.url} onClick={() => setOpen(false)}>
                <strong>{h.title}</strong>
                {h.description && <span>{h.description}</span>}
              </a>
            </li>
          ))}
        </ul>
        <p className="syn-search__hint">
          <kbd>Esc</kbd> to close · <kbd>⌘</kbd>/<kbd>Ctrl</kbd>+<kbd>K</kbd> to open
        </p>
      </div>
      <style>{`
        .syn-search-backdrop {
          position: fixed; inset: 0; z-index: 100;
          background: rgba(0,0,0,0.55);
          display: grid; place-items: start center;
          padding-top: 8vh;
        }
        .syn-search {
          width: min(560px, 92vw);
          background: var(--surface-1);
          border: 1px solid var(--border);
          border-radius: 12px;
          padding: 1rem;
          color: var(--text);
        }
        .syn-search input {
          width: 100%; padding: 0.7rem 0.9rem; border-radius: 8px;
          background: var(--surface-2); border: 1px solid var(--border);
          color: var(--text); font-size: 1rem;
        }
        .syn-search ul { list-style: none; padding: 0; margin: 0.6rem 0; max-height: 50vh; overflow: auto; }
        .syn-search li a {
          display: block; padding: 0.55rem 0.7rem; border-radius: 8px;
          color: var(--text); text-decoration: none;
        }
        .syn-search li a:hover { background: var(--surface-2); }
        .syn-search li a span { display: block; color: var(--text-muted); font-size: 0.85rem; }
        .syn-search__empty { color: var(--text-muted); padding: 0.7rem; }
        .syn-search__hint { color: var(--text-muted); font-size: 0.78rem; margin: 0; }
        .syn-search__hint kbd {
          background: var(--surface-2); border: 1px solid var(--border);
          border-radius: 4px; padding: 1px 5px; font-family: var(--font-mono);
        }
      `}</style>
    </div>
  );
}
