import { useState } from "react"
import { Activity, Info, Radio, Waves } from "lucide-react"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Progress } from "@/components/ui/progress"
import { Separator } from "@/components/ui/separator"
import { Skeleton } from "@/components/ui/skeleton"
import { Switch } from "@/components/ui/switch"
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip"

const TOPICS = [
  { name: "g1/left_stick_y", type: "Float", hz: "60 Hz", load: 32 },
  { name: "g1/right_bumper/rising", type: "Boolean", hz: "on edge", load: 8 },
  { name: "drive/motors/amps", type: "Double", hz: "50 Hz", load: 71 },
  { name: "imu/heading", type: "Double", hz: "100 Hz", load: 54 },
]

export function TelemetryMock() {
  const [live, setLive] = useState(true)

  return (
    <TooltipProvider delayDuration={100}>
      <Card className="glow-card bg-card/90 shadow-2xl backdrop-blur">
        <CardHeader className="pb-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2.5">
              <span className="relative flex size-2.5">
                <span
                  className={`absolute inline-flex h-full w-full rounded-full bg-chart-3 ${live ? "animate-ping opacity-60" : "opacity-0"}`}
                />
                <span
                  className={`relative inline-flex size-2.5 rounded-full ${live ? "bg-chart-3" : "bg-muted-foreground"}`}
                />
              </span>
              <CardTitle className="font-mono text-sm font-medium">
                orchestrator · live
              </CardTitle>
              <Tooltip>
                <TooltipTrigger asChild>
                  <Info className="size-3.5 text-muted-foreground" />
                </TooltipTrigger>
                <TooltipContent>
                  Simulated view of a running robot bus
                </TooltipContent>
              </Tooltip>
            </div>
            <div className="flex items-center gap-2">
              <Badge variant="outline" className="font-mono text-[11px]">
                4 pools
              </Badge>
              <Badge className="font-mono text-[11px]">50 Hz</Badge>
            </div>
          </div>
          <CardDescription>
            Every topic, typed and named — watched from one place.
          </CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4">
          {TOPICS.map((t) => (
            <div key={t.name} className="grid gap-1.5">
              <div className="flex items-center justify-between gap-2 text-xs">
                <code className="text-foreground">{t.name}</code>
                <span className="flex items-center gap-2 text-muted-foreground">
                  <Badge variant="secondary" className="font-mono text-[10px] px-1.5 py-0">
                    {t.type}
                  </Badge>
                  {t.hz}
                </span>
              </div>
              <Progress value={live ? t.load : 0} className="h-1.5" />
            </div>
          ))}

          <Separator className="my-1" />

          <div className="flex items-center justify-between gap-4">
            <span className="flex items-center gap-2 text-sm">
              <Radio className="size-4 text-primary" />
              Hardware-thread lock
            </span>
            <Switch checked={live} onCheckedChange={setLive} />
          </div>

          <div className="flex items-center justify-between gap-4">
            <span className="flex items-center gap-2 text-sm">
              <Waves className="size-4 text-chart-2" />
              Backpressure
              <Tooltip>
                <TooltipTrigger asChild>
                  <Info className="size-3.5 text-muted-foreground" />
                </TooltipTrigger>
                <TooltipContent>
                  Callback pool: bounded 256, CallerRunsPolicy
                </TooltipContent>
              </Tooltip>
            </span>
            <code className="font-mono text-xs text-muted-foreground">
              queue 41/256
            </code>
          </div>

          {live ? (
            <div className="flex items-center gap-2 rounded-lg bg-muted/50 px-3 py-2.5 text-xs text-muted-foreground">
              <Activity className="size-3.5 text-chart-3" />
              scheduler 8 threads · callbacks 12 · actions 2 · hw 1
            </div>
          ) : (
            <div className="grid gap-2">
              <Skeleton className="h-4 w-3/4" />
              <Skeleton className="h-4 w-1/2" />
            </div>
          )}
        </CardContent>
      </Card>
    </TooltipProvider>
  )
}
