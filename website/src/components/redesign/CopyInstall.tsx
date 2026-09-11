import { useEffect, useRef, useState } from "react"
import { Check, Copy, Terminal, X } from "lucide-react"

export function CopyInstall({ cmd }: { cmd: string }) {
  const [status, setStatus] = useState<"idle" | "copied" | "failed">("idle")
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null)

  useEffect(
    () => () => {
      if (timer.current) clearTimeout(timer.current)
    },
    [],
  )

  async function copy() {
    try {
      await navigator.clipboard.writeText(cmd)
      setStatus("copied")
    } catch {
      setStatus("failed")
    }
    if (timer.current) clearTimeout(timer.current)
    timer.current = setTimeout(() => setStatus("idle"), 2400)
  }

  return (
    <div
      title={
        status === "failed"
          ? "Copy failed — select the command and copy manually"
          : undefined
      }
      className="group flex w-full items-center gap-3 rounded-lg border border-border bg-card/80 px-4 py-3 text-left font-mono text-sm shadow-sm backdrop-blur transition-colors hover:border-primary/40"
    >
      <Terminal className="size-4 shrink-0 text-muted-foreground" />
      <span className="text-muted-foreground select-none">$</span>
      <code className="flex-1 truncate text-foreground">{cmd}</code>
      <button
        type="button"
        onClick={copy}
        aria-label={
          status === "failed" ? "Copy failed — try again" : "Copy command"
        }
        className="rounded-sm p-0.5 outline-none focus-visible:ring-2 focus-visible:ring-ring/50"
      >
        {status === "copied" ? (
          <Check className="size-4 shrink-0 text-chart-3" aria-hidden />
        ) : status === "failed" ? (
          <X className="size-4 shrink-0 text-destructive" aria-hidden />
        ) : (
          <Copy className="size-4 shrink-0 text-muted-foreground transition-colors group-hover:text-foreground" aria-hidden />
        )}
      </button>
      <span className="sr-only" role="status" aria-live="polite">
        {status === "copied"
          ? "Copied to clipboard"
          : status === "failed"
            ? "Copy failed — select the command and copy manually"
            : ""}
      </span>
    </div>
  )
}
