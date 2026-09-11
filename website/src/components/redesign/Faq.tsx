import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from "@/components/ui/accordion"

const FAQS = [
  {
    q: "Why a dedicated hardware thread?",
    a: "FTC hardware is not thread-safe. Synapse funnels every motor write, servo command, and sensor read through one dedicated thread, and a runtime assertion in SafeOpMode.loop() fails fast if you touch hardware from anywhere else.",
  },
  {
    q: "How big is the JAR, really?",
    a: "40 KB with zero runtime dependencies, and R8 survival is verified by running the whole test suite through minification. It drops into any FTC project as a single Gradle dependency.",
  },
  {
    q: "Can a slow subscriber starve my control loop?",
    a: "No. Periodic loops and subscription callbacks run on separate pools with bounded queues and CallerRunsPolicy backpressure — a slow consumer slows its publisher instead of dropping messages or blocking your 50 Hz loop.",
  },
  {
    q: "Does it work with my existing OpMode code?",
    a: "Yes. SafeOpMode is a drop-in OpMode base class. Keep your existing teleop and adopt nodes topic-by-topic — gamepad input, intake, drive, and LEDs can migrate independently.",
  },
]

export function Faq() {
  return (
    <Accordion type="single" collapsible className="w-full">
      {FAQS.map((f, i) => (
        <AccordionItem key={i} value={`faq-${i}`}>
          <AccordionTrigger className="text-left text-base">
            {f.q}
          </AccordionTrigger>
          <AccordionContent className="text-muted-foreground">
            {f.a}
          </AccordionContent>
        </AccordionItem>
      ))}
    </Accordion>
  )
}
