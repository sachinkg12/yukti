import type { ReactNode } from "react"

export function SectionTitle({
  step,
  title,
  description,
  icon,
}: {
  step?: number
  title: string
  description?: string
  icon?: ReactNode
}) {
  return (
    <div className="mb-4">
      <div className="flex items-center gap-2">
        {step != null && (
          <span
            className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-primary-500 text-sm font-semibold text-white"
            aria-hidden
          >
            {step}
          </span>
        )}
        {icon && <span className="text-slate-400" aria-hidden>{icon}</span>}
        <h2 className="text-lg font-semibold text-slate-900">{title}</h2>
      </div>
      {description && (
        <p className="mt-1.5 text-sm text-slate-500">{description}</p>
      )}
    </div>
  )
}
