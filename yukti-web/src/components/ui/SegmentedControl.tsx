type Option<T> = { value: T; label: string }

export function SegmentedControl<T extends string>({
  value,
  options,
  onChange,
  ariaLabel,
}: {
  value: T
  options: Option<T>[]
  onChange: (v: T) => void
  ariaLabel: string
}) {
  return (
    <div
      role="group"
      aria-label={ariaLabel}
      className="inline-flex rounded-lg border border-slate-200 bg-slate-50 p-1"
    >
      {options.map((opt) => (
        <button
          key={opt.value}
          type="button"
          role="radio"
          aria-checked={value === opt.value}
          onClick={() => onChange(opt.value)}
          className={`
            rounded-md px-4 py-2 text-sm font-medium transition-colors
            focus-visible:ring-2 focus-visible:ring-primary-500 focus-visible:ring-offset-2
            ${value === opt.value ? "bg-white text-slate-900 shadow-sm" : "text-slate-600 hover:text-slate-900"}
          `}
        >
          {opt.label}
        </button>
      ))}
    </div>
  )
}
