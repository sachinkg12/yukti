import type { GoalType } from "../../types"

type GoalOption = {
  id: GoalType
  title: string
  subtitle: string
  bestFor: string
}

const GOALS: GoalOption[] = [
  {
    id: "CASHBACK",
    title: "Cashback",
    subtitle: "Simple value",
    bestFor: "Best if you want cash back with minimal complexity.",
  },
  {
    id: "FLEX_POINTS",
    title: "Flexible points",
    subtitle: "Transferable points",
    bestFor: "Best if you want transferable points for future travel.",
  },
  {
    id: "PROGRAM_POINTS",
    title: "Airline / hotel program",
    subtitle: "AA Miles",
    bestFor: "Best if you specifically want American Airlines miles.",
  },
]

export function GoalPicker({
  value,
  onChange,
}: {
  value: GoalType
  onChange: (v: GoalType) => void
}) {
  return (
    <div className="grid gap-3 sm:grid-cols-3" role="radiogroup" aria-label="Select reward goal">
      {GOALS.map((g) => (
        <button
          key={g.id}
          type="button"
          role="radio"
          aria-checked={value === g.id}
          onClick={() => onChange(g.id)}
          className={`
            rounded-xl border-2 p-4 text-left transition-all
            focus-visible:ring-2 focus-visible:ring-primary-500 focus-visible:ring-offset-2
            ${
              value === g.id
                ? "border-primary-500 bg-primary-50 shadow-sm"
                : "border-slate-200 bg-white hover:border-slate-300 hover:bg-slate-50"
            }
          `}
        >
          <div className="font-semibold text-slate-900">{g.title}</div>
          <div className="mt-0.5 text-sm text-slate-500">{g.subtitle}</div>
          <div className="mt-2 text-xs text-slate-600">{g.bestFor}</div>
        </button>
      ))}
    </div>
  )
}
