import { useId } from "react"

export function MoneyInput({
  label,
  value,
  onChange,
  suffix = "per year",
  min = 0,
  step = 50,
  placeholder = "0",
  disabled,
  error,
}: {
  label: string
  value: number
  onChange: (v: number) => void
  suffix?: string
  min?: number
  step?: number
  placeholder?: string
  disabled?: boolean
  error?: string
}) {
  const id = useId()
  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={id} className="text-sm font-medium text-slate-700">
        {label}
      </label>
      <div className="relative flex items-center">
        <span className="pointer-events-none absolute left-3 text-slate-500">$</span>
        <input
          id={id}
          type="number"
          min={min}
          step={step}
          value={value || ""}
          onChange={(e) => onChange(parseFloat(e.target.value) || 0)}
          placeholder={placeholder}
          disabled={disabled}
          aria-invalid={!!error}
          aria-describedby={error ? `${id}-error` : undefined}
          className={`
            w-full rounded-lg border py-2.5 pl-7 pr-3 text-slate-900
            placeholder:text-slate-400
            focus:border-primary-500 focus:ring-1 focus:ring-primary-500
            disabled:bg-slate-50 disabled:text-slate-500
            ${error ? "border-error" : "border-slate-300"}
          `}
        />
        {suffix && (
          <span className="ml-2 shrink-0 text-sm text-slate-500">{suffix}</span>
        )}
      </div>
      {error && (
        <p id={`${id}-error`} className="text-sm text-error" role="alert">
          {error}
        </p>
      )}
    </div>
  )
}
