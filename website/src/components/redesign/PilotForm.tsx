import { useState, type FormEvent } from "react"
import { ArrowUpRight } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"

const REPO = "https://github.com/IamCoder18/synapse"

export function PilotForm() {
  const [name, setName] = useState("")
  const [team, setTeam] = useState("")
  const [experience, setExperience] = useState("")
  const [openFailed, setOpenFailed] = useState(false)

  function issueUrl() {
    const params = new URLSearchParams({
      template: "website-feedback.yml",
      title: `[Website] Feedback${name ? ` from ${name}` : ""}`,
      experience,
    })
    if (name) params.set("name", name)
    if (team) params.set("team", team)
    return `${REPO}/issues/new?${params.toString()}`
  }

  function submit(e: FormEvent) {
    e.preventDefault()
    setOpenFailed(false)
    const win = window.open(issueUrl(), "_blank", "noopener")
    if (!win) setOpenFailed(true)
  }

  return (
    <form onSubmit={submit} className="grid gap-5">
      <div className="grid gap-5 sm:grid-cols-2">
        <div className="grid gap-2.5">
          <Label htmlFor="pilot-name">
            Name <span className="text-muted-foreground">(optional)</span>
          </Label>
          <Input
            id="pilot-name"
            placeholder="Ada Lovelace"
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
        </div>
        <div className="grid gap-2.5">
          <Label htmlFor="pilot-team">
            Team number <span className="text-muted-foreground">(optional)</span>
          </Label>
          <Input
            id="pilot-team"
            placeholder="23684"
            value={team}
            onChange={(e) => setTeam(e.target.value)}
          />
        </div>
      </div>
      <div className="grid gap-2.5">
        <Label htmlFor="pilot-about">Your experience so far</Label>
        <Textarea
          id="pilot-about"
          required
          maxLength={2000}
          placeholder="The good, the broken, and the missing..."
          className="min-h-24"
          value={experience}
          onChange={(e) => setExperience(e.target.value)}
        />
      </div>
      <div className="flex flex-wrap items-center gap-3">
        <Button type="submit" size="lg">
          Send feedback on GitHub
          <ArrowUpRight />
        </Button>
        <p className="text-xs text-muted-foreground">
          Opens a public issue on GitHub — read by the maintainers, no email
          needed.
        </p>
      </div>
      {openFailed && (
        <p className="text-xs text-destructive" role="alert">
          Your browser blocked the pop-up.{" "}
          <a
            href={issueUrl()}
            target="_blank"
            rel="noopener noreferrer"
            className="font-medium underline underline-offset-2"
          >
            Open the issue form here
          </a>{" "}
          instead.
        </p>
      )}
    </form>
  )
}
