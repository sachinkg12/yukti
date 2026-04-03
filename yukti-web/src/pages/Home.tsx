import { useState, useEffect, useRef } from "react"
import { getGoalsConfig, getCatalogCards, optimize, type ApiError } from "../api/client"
import type { ProfileV1, GoalsConfigResponse, CardsResponse, OptimizeResponseV1 } from "../types"
import { loadProfile, saveProfile } from "../storage"
import { defaultProfile, spendPresets } from "../defaults"
import { CATEGORIES } from "../types"

const CATEGORY_LABELS: Record<string, string> = {
  GROCERIES: "Groceries",
  DINING: "Dining",
  GAS: "Gas",
  TRAVEL: "Travel",
  ONLINE: "Online",
  OTHER: "Other",
}

const CATEGORY_ICONS: Record<string, string> = {
  GROCERIES: "\u{1F6D2}",
  DINING: "\u{1F37D}",
  GAS: "\u26FD",
  TRAVEL: "\u2708",
  ONLINE: "\u{1F4BB}",
  OTHER: "\u{1F4B3}",
}

const OPTIMIZER_OPTIONS = [
  { id: "", label: "MILP (Optimal)" },
  { id: "sa-v1", label: "Simulated Annealing" },
  { id: "greedy-v1", label: "Greedy" },
  { id: "lp-relaxation-v1", label: "LP Relaxation" },
  { id: "exhaustive-search-v1", label: "Exhaustive (slow)" },
  { id: "cap-aware-greedy-v1", label: "Cap Aware Greedy" },
  { id: "ahp-mcdm-baseline-v1", label: "AHP/MCDM" },
  { id: "ahp-pairwise-baseline-v1", label: "AHP/Pairwise" },
  { id: "rule-based-recommender-v1", label: "Rule Based" },
  { id: "content-based-top-k-baseline-v1", label: "Content Based Top 3" },
  { id: "single-best-per-category-baseline-v1", label: "Category Winner" },
  { id: "top-k-popular-baseline-v1", label: "Top K Popular" },
  { id: "random-k-baseline-v1", label: "Random K" },
]

const BAR_COLORS = [
  "from-indigo-500/40 to-indigo-500/20",
  "from-emerald-500/40 to-emerald-500/20",
  "from-pink-500/40 to-pink-500/20",
  "from-amber-500/40 to-amber-500/20",
  "from-cyan-500/40 to-cyan-500/20",
  "from-purple-500/40 to-purple-500/20",
]

export default function Home() {
  const [profile, setProfile] = useState<ProfileV1>(defaultProfile)
  const [goalsConfig, setGoalsConfig] = useState<GoalsConfigResponse | null>(null)
  const [catalogCards, setCatalogCards] = useState<CardsResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [apiError, setApiError] = useState<ApiError | null>(null)
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState<OptimizeResponseV1 | null>(null)
  const [showAdvanced, setShowAdvanced] = useState(false)
  const resultRef = useRef<HTMLDivElement>(null)
  const [goalPrompt, setGoalPrompt] = useState("")
  const [showCatalog, setShowCatalog] = useState(false)
  const [catalogSearch, setCatalogSearch] = useState("")

  useEffect(() => {
    const saved = loadProfile()
    if (saved?.updatedAtIso) setProfile(saved)
  }, [])

  useEffect(() => {
    getGoalsConfig().then(setGoalsConfig).catch(() => {})
    getCatalogCards().then(setCatalogCards).catch(() => {})
  }, [])

  useEffect(() => {
    saveProfile({ ...profile, updatedAtIso: new Date().toISOString() })
  }, [profile])

  const totalSpend = Object.values(profile.spendByCategoryUsd).reduce((a, b) => a + b, 0)
  const isValid = totalSpend > 0 && profile.goal.goalType &&
    (profile.goal.goalType !== "PROGRAM_POINTS" || profile.goal.primaryCurrency)

  const handleSubmit = async () => {
    setError(null)
    setApiError(null)
    setLoading(true)
    setResult(null)
    try {
      const maxFee = Number(profile.constraints.maxAnnualFeeUsd)
      const req = {
        catalogVersion: profile.catalogVersion ?? "1.0",
        period: profile.period,
        spendByCategoryUsd: profile.spendByCategoryUsd,
        goal: {
          goalType: profile.goal.goalType,
          primaryCurrency: profile.goal.goalType === "PROGRAM_POINTS"
            ? profile.goal.primaryCurrency ?? undefined : null,
          preferredCurrencies: profile.goal.preferredCurrencies ?? [],
          cppOverrides: profile.goal.cppOverrides && Object.keys(profile.goal.cppOverrides).length > 0
            ? profile.goal.cppOverrides : undefined,
        },
        constraints: {
          maxCards: profile.constraints.maxCards,
          maxAnnualFeeUsd: Number.isFinite(maxFee) && maxFee >= 0 ? maxFee : 200,
          allowBusinessCards: profile.constraints.allowBusinessCards,
        },
        goalPrompt: goalPrompt.trim() || undefined,
        optimizerId: profile.optimizerId || undefined,
      }
      const res = await optimize(req)
      setLoading(false)
      if (res.ok) {
        setResult(res.data)
        sessionStorage.setItem("yukti.result", JSON.stringify(res.data))
        setTimeout(() => resultRef.current?.scrollIntoView({ behavior: "smooth", block: "start" }), 100)
      } else {
        setApiError(res.error)
      }
    } catch (e: any) {
      setLoading(false)
      setError(e.message || "Something went wrong")
    }
  }

  const applyPreset = (preset: Record<string, number>) => {
    setProfile(p => ({ ...p, spendByCategoryUsd: preset as any }))
  }

  const cardColorMap = new Map<string, number>()
  if (result) {
    result.portfolio.forEach((c, i) => cardColorMap.set(c.cardId, i))
  }

  return (
    <div className="max-w-[1400px] mx-auto px-4 sm:px-6 lg:px-8 py-6">
      {/* Nav */}
      <nav className="flex items-center justify-between py-4 mb-6">
        <div className="flex items-center gap-2">
          <span className="text-2xl font-extrabold bg-gradient-to-r from-indigo-400 to-emerald-400 bg-clip-text text-transparent">yukti</span>
          <span className="text-sm text-zinc-400 hidden sm:inline">smart card optimizer</span>
        </div>
        <button
          onClick={() => setShowCatalog(!showCatalog)}
          className="text-xs text-zinc-400 px-3 py-1.5 rounded-lg border border-white/[0.08] bg-white/[0.04] hover:border-indigo-400/30 hover:text-indigo-400 transition-all cursor-pointer"
        >
          {catalogCards ? `${catalogCards.cards.length} cards` : "..."} {showCatalog ? "\u2715" : "\u2193"}
        </button>
      </nav>

      {/* Card Catalog Panel */}
      {showCatalog && catalogCards && (
        <div className="glass p-5 mb-6 animate-fade-in rounded-2xl">
          <div className="flex items-center justify-between mb-4">
            <div className="text-xs uppercase tracking-widest text-zinc-400">
              Card Catalog &middot; {catalogCards.cards.length} cards
            </div>
            <input
              type="text"
              value={catalogSearch}
              onChange={e => setCatalogSearch(e.target.value)}
              placeholder="Search cards..."
              className="bg-white/[0.04] border border-white/[0.08] rounded-lg px-3 py-1.5 text-sm text-white outline-none focus:border-indigo-400/30 w-48 placeholder:text-zinc-600"
            />
          </div>
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-2 max-h-80 overflow-y-auto pr-1">
            {catalogCards.cards
              .filter(c => {
                if (!catalogSearch) return true
                const q = catalogSearch.toLowerCase()
                return c.name.toLowerCase().includes(q) || c.issuer.toLowerCase().includes(q) || c.rewardCurrency.toLowerCase().includes(q)
              })
              .sort((a, b) => a.issuer.localeCompare(b.issuer) || a.name.localeCompare(b.name))
              .map(card => (
                <div key={card.cardId} className="bg-white/[0.02] border border-white/[0.06] rounded-xl p-3 hover:border-white/[0.12] transition-all">
                  <div className="text-xs font-semibold text-[#e4e4e7] truncate">{card.name}</div>
                  <div className="text-[0.65rem] text-zinc-500 mt-0.5">{card.issuer}</div>
                  <div className="flex items-center justify-between mt-2">
                    <span className="text-[0.6rem] bg-indigo-500/10 text-indigo-400 px-1.5 py-0.5 rounded-full">{card.rewardCurrency.replace(/_/g, " ")}</span>
                    <span className="text-[0.65rem] text-zinc-500">{card.annualFeeUsd > 0 ? `$${card.annualFeeUsd}/yr` : "No fee"}</span>
                  </div>
                </div>
              ))}
          </div>
        </div>
      )}

      {/* Hero */}
      <div className="text-center py-8 sm:py-12">
        <h1 className="text-4xl sm:text-5xl font-extrabold tracking-tight gradient-text leading-tight">
          Find your perfect<br className="hidden sm:block" />card portfolio
        </h1>
        <p className="text-zinc-400 text-lg mt-3 max-w-xl mx-auto">
          MILP-optimized card selection across {catalogCards?.cards.length ?? 70} US credit cards. Solver-certified optimal.
        </p>
      </div>

      {/* AI Prompt Bar */}
      <div className="max-w-2xl mx-auto mb-2">
        <div className="glass flex items-center p-1 focus-within:border-indigo-400/30 focus-within:shadow-glow transition-all">
          <span className="px-3 text-lg opacity-50">{"\u2728"}</span>
          <input
            type="text"
            value={goalPrompt}
            onChange={e => setGoalPrompt(e.target.value)}
            onKeyDown={e => e.key === "Enter" && isValid && handleSubmit()}
            placeholder='Tell us your goal \u2014 "I fly American Airlines and spend $800/mo on groceries"'
            className="flex-1 bg-transparent border-none outline-none text-[#e4e4e7] text-base py-3 font-sans placeholder:text-zinc-500"
          />
          <button
            onClick={handleSubmit}
            disabled={!isValid || loading}
            className="bg-gradient-to-r from-indigo-500 to-purple-500 text-white px-5 py-2.5 rounded-xl text-sm font-semibold whitespace-nowrap disabled:opacity-40 hover:opacity-90 transition-opacity"
          >
            {loading ? "Optimizing..." : "Optimize"}
          </button>
        </div>
        <p className="text-center text-zinc-500 text-xs mt-2">AI interprets your goal and optimizes instantly. Or configure manually below.</p>
      </div>

      {/* Divider */}
      <div className="flex items-center gap-4 max-w-2xl mx-auto my-8 text-zinc-500 text-xs uppercase tracking-widest">
        <div className="flex-1 h-px bg-white/[0.06]" />
        <span>or configure manually</span>
        <div className="flex-1 h-px bg-white/[0.06]" />
      </div>

      {/* Error */}
      {(error || apiError) && (
        <div className="max-w-2xl mx-auto mb-6 glass border-error/30 bg-error/5 p-4 rounded-2xl">
          <p className="text-red-400 text-sm font-medium">{error || apiError?.message}</p>
          {apiError?.details?.map((d, i) => <p key={i} className="text-zinc-500 text-xs mt-1">{d.field}: {d.issue}</p>)}
        </div>
      )}

      {/* Main Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Spend Card */}
        <div className="glass p-6">
          <div className="flex items-center gap-2 text-xs uppercase tracking-widest text-zinc-400 mb-5">
            <span className="w-1.5 h-1.5 rounded-full bg-indigo-500/50" /> Spend Profile
          </div>
          <div className="flex gap-1 bg-white/[0.04] rounded-lg p-0.5 mb-4">
            {(["MONTHLY", "ANNUAL"] as const).map(p => (
              <button key={p} onClick={() => setProfile(pr => ({ ...pr, period: p }))}
                className={`flex-1 py-2 rounded-md text-sm font-medium transition-all ${profile.period === p ? "bg-indigo-500/20 text-indigo-400" : "text-zinc-400 hover:text-[#e4e4e7]"}`}>
                {p === "MONTHLY" ? "Monthly" : "Annual"}
              </button>
            ))}
          </div>
          <div className="flex gap-2 mb-4 flex-wrap">
            {Object.entries(spendPresets).map(([label, spend]) => (
              <button key={label} onClick={() => applyPreset(spend)}
                className="border border-white/[0.08] bg-white/[0.04] rounded-full px-3 py-1.5 text-xs text-zinc-400 hover:text-indigo-400 hover:border-indigo-400/30 hover:bg-indigo-500/5 transition-all">
                {label}
              </button>
            ))}
          </div>
          <div className="grid grid-cols-2 gap-3">
            {CATEGORIES.map(cat => (
              <div key={cat}>
                <label className="block text-xs text-zinc-400 mb-1 font-medium">{CATEGORY_ICONS[cat]} {CATEGORY_LABELS[cat]}</label>
                <div className="relative">
                  <span className="absolute left-3 top-1/2 -translate-y-1/2 text-zinc-500 text-sm">$</span>
                  <input type="number" min={0} value={profile.spendByCategoryUsd[cat] || ""}
                    onChange={e => setProfile(p => ({ ...p, spendByCategoryUsd: { ...p.spendByCategoryUsd, [cat]: parseFloat(e.target.value) || 0 } }))}
                    className="w-full bg-white/[0.04] border border-white/[0.08] rounded-xl py-2.5 pl-7 pr-3 text-white text-sm font-sans outline-none focus:border-indigo-400/50 focus:shadow-glow transition-all" placeholder="0" />
                </div>
              </div>
            ))}
          </div>
          <div className="flex justify-between items-center mt-4 pt-4 border-t border-white/[0.08]">
            <span className="text-zinc-400 text-sm">Total {profile.period === "MONTHLY" ? "monthly" : "annual"} spend</span>
            <span className="text-xl font-bold text-emerald-400">${totalSpend.toLocaleString()}</span>
          </div>
        </div>

        {/* Goal Card */}
        <div className="glass p-6">
          <div className="flex items-center gap-2 text-xs uppercase tracking-widest text-zinc-400 mb-5">
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-400" /> Reward Goal
          </div>
          <div className="grid grid-cols-3 gap-3">
            {([
              { type: "CASHBACK" as const, icon: "\u{1F4B0}", name: "Cashback", desc: "Maximize USD cash" },
              { type: "FLEX_POINTS" as const, icon: "\u2728", name: "Flex Points", desc: "Chase UR, Amex MR" },
              { type: "PROGRAM_POINTS" as const, icon: "\u2708\uFE0F", name: "Program", desc: "AA, United, Marriott" },
            ]).map(g => (
              <button key={g.type} onClick={() => setProfile(p => ({ ...p, goal: { ...p.goal, goalType: g.type } }))}
                className={`relative overflow-hidden rounded-2xl p-4 text-center transition-all cursor-pointer border ${
                  profile.goal.goalType === g.type ? "border-indigo-400/30 bg-indigo-500/10" : "border-white/[0.08] bg-white/[0.02] hover:border-white/[0.15] hover:-translate-y-0.5"}`}>
                {profile.goal.goalType === g.type && <div className="absolute inset-0 bg-gradient-to-b from-indigo-500/15 to-transparent" />}
                <div className="relative">
                  <div className="text-2xl mb-1">{g.icon}</div>
                  <div className="text-sm font-semibold text-[#e4e4e7]">{g.name}</div>
                  <div className="text-xs text-zinc-400 mt-0.5">{g.desc}</div>
                </div>
              </button>
            ))}
          </div>
          {profile.goal.goalType === "PROGRAM_POINTS" && goalsConfig && (
            <div className="mt-4">
              <label className="text-xs text-zinc-400 block mb-2">Primary currency</label>
              <select value={profile.goal.primaryCurrency ?? ""}
                onChange={e => setProfile(p => ({ ...p, goal: { ...p.goal, primaryCurrency: e.target.value as any } }))}
                className="w-full bg-white/[0.04] border border-white/[0.08] rounded-xl py-2.5 px-3 text-[#e4e4e7] text-sm font-sans outline-none focus:border-indigo-400/30">
                <option value="">Select currency...</option>
                {goalsConfig.supportedGoals.find(g => g.goalType === "PROGRAM_POINTS")?.allowedCurrencies.map(c => (
                  <option key={c} value={c}>{c.replace(/_/g, " ")}</option>
                ))}
              </select>
            </div>
          )}
          <div className="mt-6 pt-5 border-t border-white/[0.08]">
            <div className="text-xs text-zinc-400 mb-3 font-medium">Constraints</div>
            <div className="flex gap-4">
              <div className="flex-1">
                <label className="text-[0.65rem] text-zinc-500 block mb-1.5">Max cards</label>
                <div className="flex gap-1">
                  {([1, 2, 3] as const).map(n => (
                    <button key={n} onClick={() => setProfile(p => ({ ...p, constraints: { ...p.constraints, maxCards: n } }))}
                      className={`flex-1 py-2 rounded-lg text-sm font-medium transition-all border ${
                        profile.constraints.maxCards === n ? "bg-indigo-500/20 border-indigo-400/30 text-indigo-400 font-semibold" : "bg-white/[0.04] border-white/[0.08] text-zinc-400"}`}>
                      {n}
                    </button>
                  ))}
                </div>
              </div>
              <div className="flex-1">
                <label className="text-[0.65rem] text-zinc-500 block mb-1.5">Max annual fee</label>
                <div className="relative">
                  <span className="absolute left-3 top-1/2 -translate-y-1/2 text-zinc-500 text-sm">$</span>
                  <input type="number" min={0} value={profile.constraints.maxAnnualFeeUsd}
                    onChange={e => setProfile(p => ({ ...p, constraints: { ...p.constraints, maxAnnualFeeUsd: parseFloat(e.target.value) || 0 } }))}
                    className="w-full bg-white/[0.04] border border-white/[0.08] rounded-lg py-2 pl-7 pr-3 text-[#e4e4e7] text-sm font-sans outline-none focus:border-indigo-400/30" />
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* CTA */}
        <div className="lg:col-span-2 text-center py-6">
          <button onClick={handleSubmit} disabled={!isValid || loading}
            className="bg-gradient-to-r from-indigo-500 to-purple-500 text-white px-10 py-4 rounded-2xl text-lg font-bold shadow-glow-lg hover:-translate-y-0.5 transition-all disabled:opacity-40">
            {loading ? "Optimizing..." : "\u26A1 Find My Optimal Portfolio"}
          </button>
          <p className="text-zinc-500 text-xs mt-3">MILP solver finds the provably optimal combination in &lt;10ms</p>
        </div>

        {/* Advanced */}
        <div className="lg:col-span-2 text-center">
          <button onClick={() => setShowAdvanced(!showAdvanced)} className="text-zinc-500 text-sm hover:text-zinc-400 transition-colors inline-flex items-center gap-1">
            {"\u2699"} Advanced settings <span className={`text-[0.5rem] transition-transform ${showAdvanced ? "rotate-180" : ""}`}>{"\u25BC"}</span>
          </button>
        </div>
        {showAdvanced && (
          <div className="lg:col-span-2 grid grid-cols-1 sm:grid-cols-3 gap-4 animate-fade-in">
            <div className="glass p-5">
              <div className="flex items-center gap-2 text-xs uppercase tracking-widest text-zinc-400 mb-3">
                <span className="w-1.5 h-1.5 rounded-full bg-amber-400" /> Optimizer
              </div>
              <select value={profile.optimizerId ?? ""} onChange={e => setProfile(p => ({ ...p, optimizerId: e.target.value || undefined }))}
                className="w-full bg-white/[0.04] border border-white/[0.08] rounded-xl py-2.5 px-3 text-[#e4e4e7] text-sm font-sans outline-none focus:border-indigo-400/30">
                {OPTIMIZER_OPTIONS.map(o => <option key={o.id} value={o.id}>{o.label}</option>)}
              </select>
              <p className="text-zinc-500 text-[0.65rem] mt-2">MILP guarantees the mathematically optimal solution.</p>
            </div>
            <div className="glass p-5">
              <div className="flex items-center gap-2 text-xs uppercase tracking-widest text-zinc-400 mb-3">
                <span className="w-1.5 h-1.5 rounded-full bg-pink-400" /> Options
              </div>
              <label className="flex items-center gap-2 cursor-pointer">
                <input type="checkbox" checked={profile.constraints.allowBusinessCards}
                  onChange={e => setProfile(p => ({ ...p, constraints: { ...p.constraints, allowBusinessCards: e.target.checked } }))}
                  className="w-4 h-4 rounded border-white/[0.1] text-indigo-500 focus:ring-primary-500" />
                <span className="text-sm text-zinc-400">Allow business cards</span>
              </label>
            </div>
            <div className="glass p-5">
              <div className="flex items-center gap-2 text-xs uppercase tracking-widest text-zinc-400 mb-3">
                <span className="w-1.5 h-1.5 rounded-full bg-emerald-400" /> Verification
              </div>
              <p className="text-xs text-zinc-400">Every result passes a 4-gate structural verifier ensuring explanations cite only solver-emitted evidence.</p>
            </div>
          </div>
        )}

        {/* ═══ RESULTS ═══ */}
        {result && (
          <div ref={resultRef} className="lg:col-span-2 mt-4 space-y-6 animate-slide-up">
            <div className="glass relative overflow-hidden p-8 sm:p-10 text-center rounded-3xl">
              <div className="absolute inset-0 bg-gradient-to-b from-emerald-500/10 to-transparent pointer-events-none" />
              <div className="relative">
                <div className="text-xs uppercase tracking-widest text-zinc-400">Your optimal annual reward</div>
                <div className="text-5xl sm:text-6xl font-extrabold text-emerald-400 mt-2 tracking-tight">
                  ${result.breakdown.netValueUsd.toLocaleString()}
                </div>
                <div className="text-zinc-500 text-sm mt-2">
                  ${result.breakdown.totalEarnValueUsd.toLocaleString()} earned
                  {result.breakdown.totalCreditValueUsd > 0 && ` + $${result.breakdown.totalCreditValueUsd.toLocaleString()} credits`}
                  {" "}&minus; ${result.breakdown.totalFeesUsd.toLocaleString()} fees &middot; {result.portfolio.length} cards &middot; {profile.goal.goalType.replace(/_/g, " ")}
                </div>
                {result.verificationStatus === "PASS" && (
                  <div className="inline-flex items-center gap-2 mt-4 bg-emerald-500/10 border border-emerald-500/20 rounded-full px-4 py-2 text-xs text-emerald-400">
                    {"\u2713"} Solver-certified optimal &middot; Verified by 4-gate ClaimVerifier
                  </div>
                )}
              </div>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
              {result.portfolio.map((card, i) => {
                const cardEarn = result.allocation.filter(a => a.cardId === card.cardId).reduce((sum, a) => sum + (a.earnValueUsd ?? 0), 0)
                return (
                  <div key={card.cardId} className="glass p-5 relative">
                    <div className="absolute -top-2 right-4 bg-zinc-900 border border-white/[0.1] rounded-lg px-2 py-0.5 text-[0.6rem] text-zinc-400">
                      #{i + 1}{i === 0 ? " Primary" : ""}
                    </div>
                    <div className="text-base font-semibold text-[#e4e4e7]">{card.name}</div>
                    <div className="text-xs text-zinc-400">{card.issuer}</div>
                    <div className="text-2xl font-bold text-emerald-400 mt-3">${cardEarn.toLocaleString()}</div>
                    <div className="text-[0.65rem] text-zinc-500">annual earn value</div>
                    <div className="text-xs text-zinc-400 mt-2">${card.annualFeeUsd}/year fee</div>
                    <span className="inline-block mt-3 bg-indigo-500/10 text-indigo-400 px-2.5 py-0.5 rounded-full text-[0.65rem] font-medium">
                      {card.rewardCurrency.replace(/_/g, " ")}
                    </span>
                  </div>
                )
              })}
            </div>

            <div className="glass p-6">
              <div className="text-xs uppercase tracking-widest text-zinc-400 mb-4">Spend Allocation</div>
              <div className="space-y-3">
                {result.allocation.sort((a, b) => (b.earnValueUsd ?? 0) - (a.earnValueUsd ?? 0)).map((alloc) => {
                  const maxEarn = Math.max(...result.allocation.map(a => a.earnValueUsd ?? 0), 1)
                  const pct = Math.max(((alloc.earnValueUsd ?? 0) / maxEarn) * 100, 8)
                  const colorIdx = cardColorMap.get(alloc.cardId) ?? 0
                  const cardName = result.portfolio.find(c => c.cardId === alloc.cardId)?.name ?? alloc.cardId
                  return (
                    <div key={`${alloc.category}-${alloc.cardId}`} className="flex items-center gap-3">
                      <div className="w-20 text-right text-xs text-zinc-400 font-medium shrink-0">{CATEGORY_LABELS[alloc.category] ?? alloc.category}</div>
                      <div className="flex-1 h-8 bg-white/[0.02] rounded-lg overflow-hidden">
                        <div className={`h-full rounded-lg flex items-center px-3 text-[0.65rem] font-semibold text-white/80 bg-gradient-to-r ${BAR_COLORS[colorIdx % BAR_COLORS.length]} animate-bar`}
                          style={{ width: `${pct}%` }}>
                          {cardName.split(" ").slice(0, 2).join(" ")}{alloc.earnRatePercent != null ? ` \u00B7 ${alloc.earnRatePercent}%` : ""}
                        </div>
                      </div>
                      <div className="w-14 text-right text-sm text-emerald-400 font-semibold shrink-0">${(alloc.earnValueUsd ?? 0).toLocaleString()}</div>
                    </div>
                  )
                })}
              </div>
            </div>

            {result.explanation && (
              <div className="glass p-6">
                <div className="text-xs uppercase tracking-widest text-zinc-400 mb-3">Why This Portfolio</div>
                <p className="text-sm text-zinc-400 leading-relaxed whitespace-pre-line">{result.explanation.summary}</p>
                {result.explanation.details && (
                  <details className="mt-4">
                    <summary className="text-xs text-zinc-500 cursor-pointer hover:text-zinc-400">Show detailed explanation</summary>
                    <pre className="mt-2 text-xs text-zinc-500 whitespace-pre-wrap font-sans leading-relaxed">{result.explanation.details}</pre>
                  </details>
                )}
              </div>
            )}

            {result.goalInterpretation && (
              <div className="glass p-6 border-indigo-400/20">
                <div className="text-xs uppercase tracking-widest text-indigo-400 mb-3">{"\u2728"} AI Goal Interpretation</div>
                <p className="text-sm text-zinc-400"><span className="text-zinc-500">You said:</span> &ldquo;{result.goalInterpretation.userPrompt}&rdquo;</p>
                <p className="text-sm text-zinc-400 mt-1">
                  <span className="text-zinc-500">Interpreted as:</span> {result.goalInterpretation.interpretedGoalType?.replace(/_/g, " ")}
                  {result.goalInterpretation.primaryCurrency && ` (${result.goalInterpretation.primaryCurrency})`}
                </p>
                {result.goalInterpretation.rationale && <p className="text-xs text-zinc-500 mt-2">{result.goalInterpretation.rationale}</p>}
              </div>
            )}

            <div className="flex flex-wrap gap-3 justify-center">
              <button onClick={() => {
                const text = `Yukti Optimization\nNet: $${result.breakdown.netValueUsd}\nCards: ${result.portfolio.map(c => c.name).join(", ")}\n${result.explanation?.summary ?? ""}`
                navigator.clipboard.writeText(text)
              }} className="text-xs text-zinc-400 border border-white/[0.08] rounded-full px-4 py-2 hover:text-[#e4e4e7] hover:border-white/[0.15] transition-all">
                Copy Summary
              </button>
              <button onClick={() => {
                const blob = new Blob([JSON.stringify(result, null, 2)], { type: "application/json" })
                const url = URL.createObjectURL(blob)
                const a = document.createElement("a"); a.href = url; a.download = `yukti-result-${new Date().toISOString().slice(0, 10)}.json`
                a.click(); URL.revokeObjectURL(url)
              }} className="text-xs text-zinc-400 border border-white/[0.08] rounded-full px-4 py-2 hover:text-[#e4e4e7] hover:border-white/[0.15] transition-all">
                Download JSON
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Disclaimers */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mt-16 mb-8 max-w-4xl mx-auto">
        <div className="glass p-4 rounded-xl">
          <div className="text-[0.65rem] uppercase tracking-widest text-zinc-500 mb-2">Not Financial Advice</div>
          <p className="text-xs text-zinc-500 leading-relaxed">Yukti is an optimization tool for educational and informational purposes only. Results are not financial advice. Consult a qualified financial advisor before making credit card decisions.</p>
        </div>
        <div className="glass p-4 rounded-xl">
          <div className="text-[0.65rem] uppercase tracking-widest text-zinc-500 mb-2">No Affiliation</div>
          <p className="text-xs text-zinc-500 leading-relaxed">Yukti is not affiliated with, endorsed by, or sponsored by any credit card issuer, bank, or financial institution. All trademarks and card names belong to their respective owners.</p>
        </div>
        <div className="glass p-4 rounded-xl">
          <div className="text-[0.65rem] uppercase tracking-widest text-zinc-500 mb-2">Data Accuracy</div>
          <p className="text-xs text-zinc-500 leading-relaxed">Card terms, reward rates, and fees are based on publicly available information at catalog snapshot date. Actual terms may vary. Verify all details with the card issuer before applying.</p>
        </div>
      </div>

      <footer className="text-center py-6 text-zinc-600 text-xs">
        <p>Yukti &middot; Solver-certified optimal card portfolios &middot; Apache 2.0</p>
        <p className="mt-1">Results depend on card terms at catalog snapshot date. No personal financial data is collected or stored.</p>
      </footer>
    </div>
  )
}
