import { useState } from "react"

export interface CodeSample {
  id: string
  label: string
  file: string
  html: string
}

export function CodeTabs({ samples }: { samples: CodeSample[] }) {
  const [activeId, setActiveId] = useState(samples[0]?.id)
  const active = samples.find((s) => s.id === activeId) ?? samples[0]

  return (
    <div className="w-full" data-code-tabs>
      <div className="overflow-hidden rounded-xl border border-[#1d2a38] shadow-xl">
        <div className="flex items-center gap-2 border-b border-[#1d2a38] bg-[#0a1119] px-4 py-2.5">
          <span className="size-2.5 rounded-full bg-[#2a3a4c]" />
          <span className="size-2.5 rounded-full bg-[#2a3a4c]" />
          <span className="size-2.5 rounded-full bg-[#34d399]/70" />
          <span className="ml-3 font-mono text-xs text-[#7d92a6]">{active?.file}</span>
        </div>
        <div className="flex gap-1 border-b border-[#1d2a38] bg-[#0a1119] px-3 pt-1.5">
          {samples.map((s) => (
            <button
              key={s.id}
              type="button"
              onClick={() => setActiveId(s.id)}
              className={`rounded-t-md border-b-2 px-3.5 py-2 font-mono text-xs transition-colors ${
                s.id === activeId
                  ? "border-[#22d3ee] bg-[#0d151f] text-[#67e8f9]"
                  : "border-transparent text-[#7d92a6] hover:text-[#d7e3f0]"
              }`}
            >
              {s.label}
            </button>
          ))}
        </div>
        {samples.map((s) => (
          <div
            key={s.id}
            className="code-block hidden data-[active=true]:block"
            data-active={s.id === activeId}
            dangerouslySetInnerHTML={{ __html: s.html }}
          />
        ))}
      </div>
    </div>
  )
}
