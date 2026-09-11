import { useEffect, useState } from "react"
import { Moon, Sun } from "lucide-react"
import { Button } from "@/components/ui/button"

export function ThemeToggle() {
  const [dark, setDark] = useState(true)

  useEffect(() => {
    const current = document.documentElement.getAttribute("data-theme") ?? "dark"
    setDark(current !== "light")
  }, [])

  function toggle() {
    const next = !dark
    setDark(next)
    const theme = next ? "dark" : "light"
    document.documentElement.setAttribute("data-theme", theme)
    try {
      localStorage.setItem("synapse-theme", theme)
    } catch {}
  }

  return (
    <Button
      variant="ghost"
      size="icon"
      onClick={toggle}
      aria-label="Toggle color theme"
    >
      {dark ? <Sun /> : <Moon />}
    </Button>
  )
}
