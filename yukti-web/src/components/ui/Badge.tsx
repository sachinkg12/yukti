const CURRENCY_LABELS: Record<string, string> = {
  USD_CASH: "Cash",
  BANK_UR: "Chase UR",
  BANK_MR: "Amex MR",
  BANK_TY: "Citi TYP",
  BANK_C1: "Cap1 Venture",
  AA_MILES: "AA Miles",
}

export function Badge({
  currency,
  label,
}: {
  currency?: string
  label?: string
}) {
  const text = label ?? (currency ? CURRENCY_LABELS[currency] ?? currency : "")
  if (!text) return null
  return (
    <span className="inline-flex items-center rounded-md bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-700">
      {text}
    </span>
  )
}
