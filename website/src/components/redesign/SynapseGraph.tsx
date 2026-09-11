import { useCallback, useEffect, useRef, useState } from "react"

type SourceId = "rb" | "lb" | "ls" | "imu"
type TargetId = "drive" | "intake" | "led"

const HUB = { x: 400, y: 200 }

const SOURCES: { id: SourceId; label: string; topic: string; y: number }[] = [
  { id: "rb", label: "RB", topic: "g1/right_bumper/rising", y: 52 },
  { id: "lb", label: "LB", topic: "g1/left_bumper/rising", y: 106 },
  { id: "ls", label: "LS", topic: "g1/left_stick_y", y: 160 },
]

const IMU = { label: "IMU", topic: "imu/heading", y: 258 }

const TARGETS: { id: TargetId; label: string; topic: string; y: number }[] = [
  { id: "drive", label: "DRIVE", topic: "motors/power", y: 58 },
  { id: "intake", label: "INTAKE", topic: "set/power", y: 164 },
  { id: "led", label: "LEDs", topic: "leds/color", y: 270 },
]

interface Edge {
  id: string
  d: string
  label: string
}

const EDGES_TO_HUB: Record<SourceId, Edge> = {
  rb: {
    id: "e-rb",
    d: `M 170 75 C 260 75, 300 166, ${HUB.x - 34} ${HUB.y - 14}`,
    label: "g1/right_bumper/rising",
  },
  lb: {
    id: "e-lb",
    d: `M 170 129 C 260 129, 300 193, ${HUB.x - 34} ${HUB.y - 2}`,
    label: "g1/left_bumper/rising",
  },
  ls: {
    id: "e-ls",
    d: `M 170 183 C 260 183, 300 214, ${HUB.x - 34} ${HUB.y + 10}`,
    label: "g1/left_stick_y",
  },
  imu: {
    id: "e-imu",
    d: `M 170 281 C 260 281, 300 240, ${HUB.x - 34} ${HUB.y + 16}`,
    label: "imu/heading",
  },
}

const EDGES_FROM_HUB: Record<TargetId, Edge> = {
  drive: {
    id: "e-drive",
    d: `M ${HUB.x + 32} ${HUB.y - 16} C 510 ${HUB.y - 44}, 540 80, 636 80`,
    label: "motors/power",
  },
  intake: {
    id: "e-intake",
    d: `M ${HUB.x + 34} ${HUB.y} C 520 ${HUB.y}, 540 186, 636 186`,
    label: "set/power",
  },
  led: {
    id: "e-led",
    d: `M ${HUB.x + 32} ${HUB.y + 16} C 510 ${HUB.y + 48}, 540 292, 636 292`,
    label: "leds/color",
  },
}

const ROUTING: Record<SourceId, TargetId[]> = {
  rb: ["intake", "led"],
  lb: ["intake", "led"],
  ls: ["drive"],
  imu: [],
}

interface Pulse {
  key: number
  path: string
  born: number
}

interface LogEntry {
  key: number
  t: string
  topic: string
  value: string
}

export function SynapseGraph() {
  const [pulses, setPulses] = useState<Pulse[]>([])
  const [flashes, setFlashes] = useState<Set<string>>(new Set())
  const [log, setLog] = useState<LogEntry[]>([])
  const [messages, setMessages] = useState(0)
  const [intakeOn, setIntakeOn] = useState(false)
  const [ledOn, setLedOn] = useState(false)
  const [drivePower, setDrivePower] = useState(0)
  const [heading, setHeading] = useState(42)
  const seq = useRef(0)
  const headingRef = useRef(42)
  const timers = useRef<ReturnType<typeof setTimeout>[]>([])
  const t0 = useRef<number>(0)

  const later = useCallback((fn: () => void, ms: number) => {
    timers.current.push(setTimeout(fn, ms))
  }, [])

  useEffect(() => () => timers.current.forEach(clearTimeout), [])

  const pushLog = useCallback((topic: string, value: string) => {
    const t = ((performance.now() - t0.current) / 1000).toFixed(1)
    setLog((l) => [{ key: ++seq.current, t: `T+${t}s`, topic, value }, ...l].slice(0, 4))
  }, [])

  const fire = useCallback(
    (src: SourceId) => {
      setMessages((m) => m + 1)

      setPulses((p) => [...p, { key: ++seq.current, path: EDGES_TO_HUB[src].d, born: Date.now() }])

      if (src === "imu") {
        later(() => {
          const h =
            Math.round(((headingRef.current + 3 + Math.random() * 5) % 360) * 10) / 10
          headingRef.current = h
          setHeading(h)
          pushLog(EDGES_TO_HUB.imu.label, `${h.toFixed(1)}°`)
        }, 560)
      }

      ROUTING[src].forEach((t, i) => {
        later(() => {
          setPulses((p) => [...p, { key: ++seq.current, path: EDGES_FROM_HUB[t].d, born: Date.now() }])
          later(() => {
            setFlashes((f) => new Set(f).add(t))
            if (t === "intake") {
              setIntakeOn(src === "rb")
              pushLog(EDGES_FROM_HUB.intake.label, src === "rb" ? "1.0" : "0.0")
            }
            if (t === "led") {
              setLedOn(src === "rb")
              pushLog(EDGES_FROM_HUB.led.label, src === "rb" ? '"cyan"' : '"off"')
            }
            if (t === "drive") {
              const p = Math.round((0.35 + Math.random() * 0.55) * 100) / 100
              setDrivePower(p)
              pushLog(EDGES_FROM_HUB.drive.label, p.toFixed(2))
            }
            later(
              () =>
                setFlashes((f) => {
                  const n = new Set(f)
                  n.delete(t)
                  return n
                }),
              750,
            )
          }, 560)
        }, 520 + i * 90)
      })

      later(() => setPulses((p) => p.filter((x) => Date.now() - x.born < 1300)), 1400)
    },
    [later, pushLog],
  )

  // the bus is always alive: auto-traffic runs continuously, user clicks fire on top of it
  useEffect(() => {
    t0.current = performance.now()
    const cycle: SourceId[] = ["rb", "ls", "imu", "lb", "ls", "rb", "imu"]
    let i = 0
    const id = setInterval(() => {
      fire(cycle[i % cycle.length])
      i++
    }, 2800)
    return () => clearInterval(id)
  }, [fire])

  const pulseDot = (p: Pulse) => (
    <circle key={p.key} r={4.5} className="sim-pulse-dot">
      <animateMotion
        dur="0.55s"
        path={p.path}
        calcMode="spline"
        keyPoints="0;1"
        keyTimes="0;1"
        keySplines="0.35 0 0.25 1"
        fill="freeze"
      />
      <animate
        attributeName="opacity"
        values="0;1;1;0"
        keyTimes="0;0.08;0.7;1"
        dur="0.55s"
        fill="freeze"
      />
    </circle>
  )

  interface ChipOpts {
    topic?: string | null
    value?: string | null
    bar?: number | null
    onClick?: () => void
    active?: boolean
    center?: boolean
  }

  const chip = (
    id: string,
    x: number,
    y: number,
    w: number,
    label: string,
    { topic = null, value = null, bar = null, onClick, active = false, center = false }: ChipOpts,
  ) => {
    const h = 46
    const interactive = !!onClick
    return (
      <g
        key={id}
        className={`sim-chip${flashes.has(id) ? " flashing" : ""}${interactive ? "" : " static"}`}
        onClick={onClick}
        onKeyDown={
          onClick
            ? (e) => {
                if (e.key === "Enter" || e.key === " ") {
                  e.preventDefault()
                  onClick()
                }
              }
            : undefined
        }
        role={interactive ? "button" : undefined}
        tabIndex={interactive ? 0 : undefined}
        aria-label={interactive ? `Publish ${topic ?? label}` : undefined}
      >
        <rect x={x} y={y} width={w} height={h} rx={10} />
        <circle
          cx={x + 14}
          cy={y + h / 2}
          r={3.5}
          className={`sim-status-dot${active ? "" : " off"}`}
        />
        <text
          x={x + 24}
          y={center ? y + h / 2 + 4 : y + 18}
          className="sim-label"
        >
          {label}
        </text>
        {topic && (
          <text x={x + 24} y={y + 32} className="sim-topic">
            {topic}
          </text>
        )}
        {value && (
          <text
            key={value}
            x={x + w - 10}
            y={y + 18}
            textAnchor="end"
            className="sim-value sim-value-flash"
          >
            {value}
          </text>
        )}
        {bar !== null && bar !== undefined && (
          <g>
            <rect x={x + 24} y={y + h - 10} width={w - 48} height={3} rx={1.5} className="sim-bar-bg" />
            <rect
              x={x + 24}
              y={y + h - 10}
              width={Math.max(2, (w - 48) * bar)}
              height={3}
              rx={1.5}
              className="sim-bar-fill"
            />
          </g>
        )}
      </g>
    )
  }

  return (
    <div className="sim-console overflow-hidden rounded-2xl border border-[#1d2a38] bg-[#0b121b] shadow-2xl">
      {/* title bar */}
      <div className="flex items-center justify-between border-b border-[#1d2a38] bg-[#0a1119] px-4 py-2.5">
        <span className="flex items-center gap-2.5 font-mono text-xs text-[#7d92a6]">
          <span className="relative flex size-2">
            <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-[#34d399] opacity-60" />
            <span className="relative inline-flex size-2 rounded-full bg-[#34d399]" />
          </span>
          orchestrator — live simulation
        </span>
        <span className="flex items-center gap-3 font-mono text-xs">
          <span className="rounded-full border border-[#22d3ee]/30 bg-[#22d3ee]/10 px-2 py-0.5 text-[#22d3ee]">
            60 Hz
          </span>
          <span className="text-[#7d92a6]">
            messages <span className="font-semibold text-[#67e8f9]">{messages}</span>
          </span>
        </span>
      </div>

      <svg viewBox="0 0 880 356" className="w-full" aria-label="Interactive pub/sub bus simulation">
        {/* rail headers */}
        <text x={22} y={26} className="sim-rail-label">
          CONTROLS
        </text>
        <text x={638} y={26} className="sim-rail-label">
          SUBSCRIBERS
        </text>

        {/* edges: base track + drifting flow + topic label riding the wire */}
        {Object.values(EDGES_TO_HUB).map((e) => (
          <g key={e.id}>
            <path id={e.id} d={e.d} className="sim-edge" />
            <path d={e.d} className="sim-flow" />
            <text className="sim-edge-label" dy={-5}>
              <textPath href={`#${e.id}`} startOffset="50%" textAnchor="middle">
                {e.label}
              </textPath>
            </text>
          </g>
        ))}
        {Object.values(EDGES_FROM_HUB).map((e) => (
          <g key={e.id}>
            <path id={e.id} d={e.d} className="sim-edge" />
            <path d={e.d} className="sim-flow" />
            <text className="sim-edge-label" dy={-5}>
              <textPath href={`#${e.id}`} startOffset="50%" textAnchor="middle">
                {e.label}
              </textPath>
            </text>
          </g>
        ))}

        {/* hub */}
        <circle className="sim-ping" cx={HUB.x} cy={HUB.y} r={32} strokeWidth={1.2} />
        <circle className="sim-ping d2" cx={HUB.x} cy={HUB.y} r={32} strokeWidth={1.2} />
        <circle className="sim-hub-ring" cx={HUB.x} cy={HUB.y} r={44} strokeWidth={1.4} />
        <circle cx={HUB.x} cy={HUB.y} r={32} className="sim-hub-core" />
        <circle cx={HUB.x} cy={HUB.y} r={10} className="sim-hub-dot" />
        <text x={HUB.x} y={HUB.y + 54} textAnchor="middle" className="sim-hub-caption">
          ORCHESTRATOR
        </text>

        {/* gamepad group */}
        <rect x={14} y={36} width={156} height={176} rx={12} className="sim-group" />
        <text x={22} y={48} className="sim-group-label">
          gamepad1
        </text>
        {SOURCES.map((s) =>
          chip(s.id, 24, s.y, 136, s.label, {
            onClick: () => fire(s.id),
            active: s.id !== "ls" ? intakeOn : drivePower > 0,
            center: true,
          }),
        )}

        {/* imu */}
        {chip("imu", 14, IMU.y, 156, IMU.label, {
          topic: IMU.topic,
          value: `${heading.toFixed(1)}°`,
          onClick: () => fire("imu"),
          active: true,
        })}

        {/* subscribers */}
        {TARGETS.map((t) =>
          chip(t.id, 636, t.y, 176, t.label, {
            topic: t.topic,
            value:
              t.id === "intake"
                ? intakeOn
                  ? "1.0"
                  : "0.0"
                : t.id === "led"
                  ? ledOn
                    ? "ON"
                    : "OFF"
                  : drivePower.toFixed(2),
            bar: t.id === "led" ? null : t.id === "intake" ? (intakeOn ? 1 : 0) : drivePower,
            active:
              t.id === "intake" ? intakeOn : t.id === "led" ? ledOn : drivePower > 0,
          }),
        )}

        {pulses.map(pulseDot)}
      </svg>

      {/* message log */}
      <div className="border-t border-[#1d2a38] bg-[#0a1119] px-4 py-3">
        <p className="pb-2 font-mono text-[10px] uppercase tracking-widest text-[#51677c]">
          topic log — newest first
        </p>
        <div className="grid min-h-20 content-start gap-1.5 font-mono text-xs">
          {log.length === 0 && <p className="text-[#51677c]">waiting for messages…</p>}
          {log.map((l) => (
            <p key={l.key} className="sim-log-line">
              <span className="text-[#51677c]">{l.t}</span>{" "}
              <span className="text-[#34d399]">→</span>{" "}
              <span className="text-[#d7e3f0]">{l.topic}</span>{" "}
              <span className="text-[#51677c]">=</span>{" "}
              <span className="font-semibold text-[#67e8f9]">{l.value}</span>
            </p>
          ))}
        </div>
      </div>

      <div className="border-t border-[#1d2a38] px-4 py-2.5 text-center">
        <p className="text-xs text-[#7d92a6]">
          The bus never sleeps — click any control to publish your own message.
        </p>
      </div>
    </div>
  )
}
