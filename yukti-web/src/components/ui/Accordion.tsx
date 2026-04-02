import { useState, useId } from "react"

export function Accordion({
  title,
  defaultOpen = false,
  children,
}: {
  title: string
  defaultOpen?: boolean
  children: React.ReactNode
}) {
  const [open, setOpen] = useState(defaultOpen)
  const id = useId()
  const panelId = `${id}-panel`
  const buttonId = `${id}-button`
  return (
    <div className="border-b border-slate-200 last:border-0">
      <button
        id={buttonId}
        type="button"
        aria-expanded={open}
        aria-controls={panelId}
        onClick={() => setOpen(!open)}
        className="flex w-full items-center justify-between py-3 text-left text-sm font-medium text-slate-700 hover:text-slate-900 focus-visible:ring-2 focus-visible:ring-primary-500 focus-visible:ring-inset"
      >
        {title}
        <span className="text-slate-400" aria-hidden>
          {open ? "▼" : "▶"}
        </span>
      </button>
      <div
        id={panelId}
        role="region"
        aria-labelledby={buttonId}
        hidden={!open}
        className={open ? "pb-4" : "sr-only"}
      >
        {children}
      </div>
    </div>
  )
}
