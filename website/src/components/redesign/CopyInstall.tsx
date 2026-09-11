import { useState } from "react"
import { Check, Copy, Terminal } from "lucide-react"

export function CopyInstall({ cmd }: { cmd: string }) {
  const [copied, setCopied] = useState(false)

  async function copy() {
    try {
      await navigator.clipboard.writeText(cmd)
      setCopied(true)
      setTimeout(() => setCopied(false), 1600)
    } catch {}
  }

  return (
    <button
      type="button"
      onClick={copy}
      className="group flex w-full items-center gap-3 rounded-lg border border-border bg-card/80 px-4 py-3 text-left font-mono text-sm shadow-sm backdrop-blur transition-colors hover:border-primary/40"
    >
      <Terminal className="size-4 shrink-0 text-muted-foreground" />
      <span className="text-muted-foreground select-none">$</span>
      <code className="flex-1 truncate text-foreground">{cmd}</code>
      {copied ? (
        <Check className="size-4 shrink-0 text-chart-3" />
      ) : (
        <Copy className="size-4 shrink-0 text-muted-foreground transition-colors group-hover:text-foreground" />
      )}
    </button>
  )
}
