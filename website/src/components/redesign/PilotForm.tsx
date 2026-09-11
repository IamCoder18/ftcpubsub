import { useState, type FormEvent } from "react"
import { CheckCircle2 } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"

export function PilotForm() {
  const [sent, setSent] = useState(false)

  function submit(e: FormEvent) {
    e.preventDefault()
    setSent(true)
  }

  if (sent) {
    return (
      <div className="flex h-full min-h-64 flex-col items-center justify-center gap-3 text-center">
        <div className="flex size-12 items-center justify-center rounded-full bg-primary/15">
          <CheckCircle2 className="size-6 text-primary" />
        </div>
        <p className="font-display text-lg font-semibold">Feedback received</p>
        <p className="max-w-xs text-sm text-muted-foreground">
          Thanks for taking the time — your notes go straight to the
          maintainers and into release planning.
        </p>
      </div>
    )
  }

  return (
    <form onSubmit={submit} className="grid gap-5">
      <div className="grid gap-5 sm:grid-cols-2">
        <div className="grid gap-2.5">
          <Label htmlFor="pilot-name">Name</Label>
          <Input id="pilot-name" placeholder="Ada Lovelace" required />
        </div>
        <div className="grid gap-2.5">
          <Label htmlFor="pilot-team">
            Team number <span className="text-muted-foreground">(optional)</span>
          </Label>
          <Input id="pilot-team" placeholder="23684" />
        </div>
      </div>
      <div className="grid gap-2.5">
        <Label htmlFor="pilot-email">Email</Label>
        <Input id="pilot-email" type="email" placeholder="you@team.org" required />
      </div>
      <div className="grid gap-2.5">
        <Label htmlFor="pilot-about">Your experience so far</Label>
        <Textarea
          id="pilot-about"
          placeholder="The good, the broken, and the missing..."
          className="min-h-24"
        />
      </div>
      <div className="flex flex-wrap items-center gap-3">
        <Button type="submit" size="lg">
          Send feedback
        </Button>
        <p className="text-xs text-muted-foreground">
          Read by the maintainers — no newsletter, no spam.
        </p>
      </div>
    </form>
  )
}
