import { useState, useEffect, useRef } from "react"
import { useNavigate } from "react-router-dom"
import { getGoalsConfig, getCatalogCards, optimize, type ApiError } from "../api/client"
import type { ProfileV1, GoalsConfigResponse, CardsResponse, Currency } from "../types"
import { loadProfile, saveProfile } from "../storage"
import { defaultProfile, spendPresets } from "../defaults"
import { CATEGORIES } from "../types"
import {
  Accordion,
  Badge,
  Card,
  CardBody,
  CardHeader,
  GoalPicker,
  InlineAlert,
  MoneyInput,
  SectionTitle,
  SegmentedControl,
} from "../components/ui"

const CATEGORY_LABELS: Record<string, string> = {
  GROCERIES: "Groceries",
  DINING: "Dining",
  GAS: "Gas",
  TRAVEL: "Travel",
  ONLINE: "Online",
  OTHER: "Other",
}

export default function Home() {
  const navigate = useNavigate()
  const [profile, setProfile] = useState<ProfileV1>(defaultProfile)
  const [goalsConfig, setGoalsConfig] = useState<GoalsConfigResponse | null>(null)
  const [catalogCards, setCatalogCards] = useState<CardsResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const importInputRef = useRef<HTMLInputElement>(null)
  const [apiError, setApiError] = useState<ApiError | null>(null)
  const [loading, setLoading] = useState(false)
  const [cardsSearch, setCardsSearch] = useState("")
  const [cardsFilterCurrency, setCardsFilterCurrency] = useState<string>("")
  const [aiAssistOpen, setAiAssistOpen] = useState(false)
  const errorBannerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    loadProfile()?.updatedAtIso && setProfile(loadProfile() ?? defaultProfile)
  }, [])

  useEffect(() => {
    getGoalsConfig()
      .then(setGoalsConfig)
      .catch(() => setError("Backend unreachable"))
    getCatalogCards(profile.catalogVersion ?? "1.0")
      .then(setCatalogCards)
      .catch(() => {})
  }, [])

  useEffect(() => {
    saveProfile(profile)
  }, [profile])

  const handleSpendChange = (cat: string, val: number) => {
    setProfile((p) => ({
      ...p,
      spendByCategoryUsd: {
        ...p.spendByCategoryUsd,
        [cat]: Math.max(0, val),
      },
    }))
  }

  const applyPreset = (presetName: string) => {
    const preset = spendPresets[presetName]
    if (preset) {
      setProfile((p) => ({
        ...p,
        spendByCategoryUsd: { ...preset } as ProfileV1["spendByCategoryUsd"],
      }))
    }
  }

  const totalSpend = Object.values(profile.spendByCategoryUsd).reduce((a, b) => a + (b || 0), 0)
  const isValid =
    Object.values(profile.spendByCategoryUsd).every((v) => v >= 0) &&
    totalSpend > 0 &&
    profile.goal.goalType &&
    (profile.goal.goalType !== "PROGRAM_POINTS" || profile.goal.primaryCurrency)

  const handleSubmit = async () => {
    setError(null)
    setApiError(null)
    setLoading(true)
    try {
      const maxFee = Number(profile.constraints.maxAnnualFeeUsd)
      const req = {
        catalogVersion: profile.catalogVersion ?? "1.0",
        period: profile.period,
        spendByCategoryUsd: profile.spendByCategoryUsd,
        goal: {
          goalType: profile.goal.goalType,
          primaryCurrency:
            profile.goal.goalType === "PROGRAM_POINTS"
              ? profile.goal.primaryCurrency ?? undefined
              : null,
          preferredCurrencies: profile.goal.preferredCurrencies ?? [],
          cppOverrides:
            profile.goal.cppOverrides && Object.keys(profile.goal.cppOverrides).length > 0
              ? profile.goal.cppOverrides
              : undefined,
        },
        constraints: {
          maxCards: profile.constraints.maxCards,
          maxAnnualFeeUsd: Number.isFinite(maxFee) && maxFee >= 0 ? maxFee : 200,
          allowBusinessCards: profile.constraints.allowBusinessCards,
        },
        goalPrompt: profile.goalPrompt?.trim() || undefined,
        optimizerId: profile.optimizerId || undefined,
      }
      const result = await optimize(req)
      setLoading(false)
      if (result.ok) {
        sessionStorage.setItem("yukti.result", JSON.stringify(result.data))
        navigate("/results")
      } else {
        setApiError(result.error)
        errorBannerRef.current?.scrollIntoView({ behavior: "smooth", block: "nearest" })
      }
    } catch (e) {
      setLoading(false)
      const msg =
        e instanceof Error && e.name === "AbortError"
          ? "Request timed out (90s). Backend may be slow on first run."
          : e instanceof Error
            ? e.message
            : "Backend unreachable"
      setError(msg)
      errorBannerRef.current?.scrollIntoView({ behavior: "smooth", block: "nearest" })
    }
  }

  const cppCurrencies =
    profile.goal.goalType === "CASHBACK"
      ? ["USD_CASH"]
      : profile.goal.goalType === "FLEX_POINTS"
        ? ["BANK_UR", "BANK_MR", "BANK_TY", "BANK_C1"]
        : profile.goal.goalType === "PROGRAM_POINTS"
          ? ["AA_MILES"]
          : []

  const filteredCards =
    catalogCards?.cards.filter((c) => {
      const matchSearch =
        !cardsSearch ||
        c.name.toLowerCase().includes(cardsSearch.toLowerCase()) ||
        c.issuer.toLowerCase().includes(cardsSearch.toLowerCase())
      const matchCurrency =
        !cardsFilterCurrency || c.rewardCurrency === cardsFilterCurrency
      return matchSearch && matchCurrency
    }) ?? []

  return (
    <div className="min-h-screen bg-slate-50">
      <div className="mx-auto max-w-6xl px-4 py-8 sm:px-6 lg:px-8" role="main">
        <header className="mb-8">
          <h1 className="text-2xl font-bold text-slate-900 sm:text-3xl">Yukti</h1>
          <p className="mt-1 text-slate-600">
            AI-first rewards optimization (grounded + explainable)
          </p>
        </header>

        {(error || apiError) && (
          <div ref={errorBannerRef} className="mb-6">
            <InlineAlert
              error={error ?? undefined}
              apiError={apiError ?? undefined}
              onDismiss={() => {
                setError(null)
                setApiError(null)
              }}
            />
          </div>
        )}

        <div className="flex flex-col gap-8 lg:flex-row lg:items-start">
          {/* LEFT: Primary flow */}
          <div className="flex-1 min-w-0 space-y-6">
            <Card>
              <SectionTitle
                step={1}
                title="How much do you spend per year?"
                description="Rough estimates are fine. Yukti works even if you only fill 1–2 categories."
              />
              <div className="mb-4">
                <SegmentedControl
                  value={profile.period}
                  options={[
                    { value: "ANNUAL" as const, label: "Annual" },
                    { value: "MONTHLY" as const, label: "Monthly" },
                  ]}
                  onChange={(v) => setProfile((p) => ({ ...p, period: v }))}
                  ariaLabel="Spend period"
                />
              </div>
              <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                {CATEGORIES.map((cat) => (
                  <MoneyInput
                    key={cat}
                    label={CATEGORY_LABELS[cat] ?? cat.replace(/_/g, " ")}
                    value={profile.spendByCategoryUsd[cat] || 0}
                    onChange={(v) => handleSpendChange(cat, v)}
                    suffix={profile.period === "ANNUAL" ? "per year" : "per month"}
                  />
                ))}
              </div>
              <div className="mt-4 flex flex-wrap items-center gap-2">
                <span className="text-sm text-slate-500">Quick presets:</span>
                {Object.keys(spendPresets).map((name) => (
                  <button
                    key={name}
                    type="button"
                    onClick={() => applyPreset(name)}
                    className="rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-50 focus-visible:ring-2 focus-visible:ring-primary-500 focus-visible:ring-offset-2"
                  >
                    {name}
                  </button>
                ))}
              </div>
              <div className="mt-4 flex items-baseline gap-2 border-t border-slate-100 pt-4">
                <span className="text-sm font-medium text-slate-700">Total:</span>
                <span className="text-lg font-semibold text-slate-900">
                  ${totalSpend.toLocaleString()}
                </span>
                <span className="text-sm text-slate-500">
                  {profile.period === "ANNUAL" ? "/year" : "/month"}
                </span>
              </div>
            </Card>

            <Card>
              <SectionTitle
                step={2}
                title="What do you want to maximize?"
                description="Choose your reward goal. Yukti will optimize your portfolio for it."
              />
              <GoalPicker
                value={profile.goal.goalType}
                onChange={(gt) =>
                  setProfile((p) => ({
                    ...p,
                    goal: {
                      ...p.goal,
                      goalType: gt,
                      primaryCurrency: gt === "PROGRAM_POINTS" ? "AA_MILES" : null,
                    },
                  }))
                }
              />
              {profile.goal.goalType === "PROGRAM_POINTS" && (
                <div className="mt-4">
                  <label htmlFor="primary-currency" className="text-sm font-medium text-slate-700">
                    Primary currency
                  </label>
                  <select
                    id="primary-currency"
                    value={profile.goal.primaryCurrency ?? "AA_MILES"}
                    onChange={(e) =>
                      setProfile((p) => ({
                        ...p,
                        goal: {
                          ...p.goal,
                          primaryCurrency: e.target.value as ProfileV1["goal"]["primaryCurrency"],
                        },
                      }))
                    }
                    className="mt-1 block w-full rounded-lg border border-slate-300 py-2 pl-3 pr-10 text-slate-900 focus:border-primary-500 focus:ring-1 focus:ring-primary-500"
                  >
                    {goalsConfig?.supportedGoals
                      .find((g) => g.goalType === "PROGRAM_POINTS")
                      ?.allowedCurrencies.map((c) => (
                        <option key={c} value={c}>
                          {c.replace(/_/g, " ")}
                        </option>
                      )) ?? <option value="AA_MILES">AA Miles</option>}
                  </select>
                </div>
              )}

              <div className="mt-6">
                <button
                  type="button"
                  onClick={() => setAiAssistOpen(!aiAssistOpen)}
                  className="text-sm font-medium text-primary-600 hover:text-primary-700"
                  aria-expanded={aiAssistOpen}
                >
                  {aiAssistOpen ? "−" : "+"} AI assist (optional)
                </button>
                <p className="mt-0.5 text-xs text-slate-500">
                  Type what you care about. We'll convert it into settings you can review.
                </p>
                {aiAssistOpen && (
                  <input
                    type="text"
                    placeholder="e.g. future travel, AA miles"
                    value={profile.goalPrompt ?? ""}
                    onChange={(e) =>
                      setProfile((p) => ({ ...p, goalPrompt: e.target.value }))
                    }
                    className="mt-2 w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 placeholder:text-slate-400 focus:border-primary-500 focus:ring-1 focus:ring-primary-500"
                  />
                )}
              </div>
            </Card>

            <Card>
              <Accordion title="Step 3: Advanced" defaultOpen={false}>
                <div className="space-y-4">
                  <div className="grid gap-4 sm:grid-cols-3">
                    <div>
                      <label className="text-sm font-medium text-slate-700">Max cards</label>
                      <select
                        value={profile.constraints.maxCards}
                        onChange={(e) =>
                          setProfile((p) => ({
                            ...p,
                            constraints: {
                              ...p.constraints,
                              maxCards: Number(e.target.value) as 1 | 2 | 3,
                            },
                          }))
                        }
                        className="mt-1 block w-full rounded-lg border border-slate-300 py-2 text-slate-900 focus:border-primary-500 focus:ring-1 focus:ring-primary-500"
                      >
                        <option value={1}>1</option>
                        <option value={2}>2</option>
                        <option value={3}>3</option>
                      </select>
                    </div>
                    <div>
                      <label className="text-sm font-medium text-slate-700">
                        Max annual fee (USD)
                      </label>
                      <input
                        type="number"
                        min={0}
                        value={profile.constraints.maxAnnualFeeUsd}
                        onChange={(e) =>
                          setProfile((p) => ({
                            ...p,
                            constraints: {
                              ...p.constraints,
                              maxAnnualFeeUsd: parseFloat(e.target.value) || 200,
                            },
                          }))
                        }
                        className="mt-1 block w-full rounded-lg border border-slate-300 py-2 text-slate-900 focus:border-primary-500 focus:ring-1 focus:ring-primary-500"
                      />
                    </div>
                  </div>
                  <label className="flex items-center gap-2">
                    <input
                      type="checkbox"
                      checked={profile.constraints.allowBusinessCards}
                      onChange={(e) =>
                        setProfile((p) => ({
                          ...p,
                          constraints: {
                            ...p.constraints,
                            allowBusinessCards: e.target.checked,
                          },
                        }))
                      }
                      className="h-4 w-4 rounded border-slate-300 text-primary-600 focus:ring-primary-500"
                    />
                    <span className="text-sm text-slate-700">Allow business cards</span>
                  </label>

                  <div>
                    <label className="text-sm font-medium text-slate-700">Optimizer</label>
                    <select
                      value={profile.optimizerId ?? ""}
                      onChange={(e) =>
                        setProfile((p) => ({
                          ...p,
                          optimizerId: e.target.value || undefined,
                        }))
                      }
                      className="mt-1 block w-full rounded-lg border border-slate-300 py-2 text-slate-900 focus:border-primary-500 focus:ring-1 focus:ring-primary-500"
                    >
                      <option value="">MILP (default)</option>
                      <option value="greedy-v1">Greedy v1</option>
                      <option value="cap-aware-greedy-v1">Cap Aware Greedy</option>
                      <option value="lp-relaxation-v1">LP Relaxation</option>
                      <option value="simulated-annealing-v1">Simulated Annealing</option>
                      <option value="exhaustive-search-v1">Exhaustive Search</option>
                      <option value="ahp-mcdm-baseline-v1">AHP/MCDM</option>
                      <option value="ahp-pairwise-baseline-v1">AHP/Pairwise</option>
                      <option value="rule-based-recommender-v1">Rule Based</option>
                      <option value="content-based-top-k-baseline-v1">Content Based Top 3</option>
                      <option value="single-best-per-category-baseline-v1">Category Winner</option>
                      <option value="top-k-popular-baseline-v1">Top K Popular</option>
                      <option value="random-k-baseline-v1">Random K</option>
                    </select>
                    <p className="mt-1 text-xs text-slate-500">
                      Choose which optimization algorithm to use. MILP provides optimal results.
                    </p>
                  </div>

                  {cppCurrencies.length > 0 && goalsConfig && (
                    <div className="pt-4 border-t border-slate-100">
                      <p className="text-sm font-medium text-slate-700">
                        Value per point (USD)
                      </p>
                      <p className="text-xs text-slate-500">
                        CPP is an assumption. Change it if you redeem differently. Example:
                        0.013 = 1.3 cents per point.
                      </p>
                      <div className="mt-2 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
                        {cppCurrencies.map((curr) => (
                          <div key={curr}>
                            <label className="text-xs text-slate-600">{curr.replace(/_/g, " ")}</label>
                            <input
                              type="number"
                              step={0.001}
                              min={0}
                              readOnly={curr === "USD_CASH"}
                              value={
                                profile.goal.cppOverrides?.[curr as Currency] ??
                                goalsConfig.defaultCppByCurrency[curr] ??
                                0
                              }
                              onChange={(e) =>
                                setProfile((p) => ({
                                  ...p,
                                  goal: {
                                    ...p.goal,
                                    cppOverrides: {
                                      ...p.goal.cppOverrides,
                                      [curr]: parseFloat(e.target.value) || 0,
                                    } as Partial<Record<Currency, number>>,
                                  },
                                }))
                              }
                              className="mt-0.5 block w-full rounded-lg border border-slate-300 py-1.5 text-sm text-slate-900 disabled:bg-slate-50 focus:border-primary-500 focus:ring-1 focus:ring-primary-500"
                            />
                          </div>
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              </Accordion>
            </Card>

            <div className="lg:hidden">
              <button
                onClick={handleSubmit}
                disabled={!isValid || loading}
                className="w-full rounded-xl bg-primary-500 py-3.5 text-base font-semibold text-white shadow-sm hover:bg-primary-600 focus-visible:ring-2 focus-visible:ring-primary-500 focus-visible:ring-offset-2 disabled:opacity-60 disabled:cursor-not-allowed"
              >
                {loading ? "Optimizing… (first run may take 30–60s)" : "Optimize"}
              </button>
            </div>
          </div>

          {/* RIGHT: Context / Guidance */}
          <aside className="w-full shrink-0 space-y-6 lg:w-80 lg:sticky lg:top-8">
            <Card hover>
              <CardHeader>
                <h3 className="font-semibold text-slate-900">How Yukti works</h3>
              </CardHeader>
              <CardBody>
                <p className="text-sm text-slate-600">
                  Enter your yearly spend by category, pick a goal (cashback, points, or airline
                  miles), and Yukti recommends 1–3 cards that maximize your net value. All outputs
                  are deterministic—no AI hallucination.
                </p>
              </CardBody>
            </Card>

            <Card>
              <CardHeader>
                <h3 className="font-semibold text-slate-900">Optimize</h3>
              </CardHeader>
              <CardBody>
                <button
                  onClick={handleSubmit}
                  disabled={!isValid || loading}
                  className="w-full rounded-xl bg-primary-500 py-3.5 text-base font-semibold text-white shadow-sm hover:bg-primary-600 focus-visible:ring-2 focus-visible:ring-primary-500 focus-visible:ring-offset-2 disabled:opacity-60 disabled:cursor-not-allowed"
                >
                  {loading ? "Optimizing…" : "Optimize"}
                </button>
                {!isValid && totalSpend === 0 && (
                  <p className="mt-2 text-sm text-slate-500">
                    Enter spend in at least one category, or use a preset.
                  </p>
                )}
              </CardBody>
            </Card>

            {goalsConfig && (
              <Card>
                <CardHeader>
                  <h3 className="font-semibold text-slate-900">Assumptions</h3>
                </CardHeader>
                <CardBody>
                  <p className="text-xs text-slate-500 mb-2">
                    Default value per point (CPP). Change in Advanced if you redeem differently.
                  </p>
                  <div className="space-y-1 text-sm">
                    {Object.entries(goalsConfig.defaultCppByCurrency).map(([curr, val]) =>
                      val > 0 ? (
                        <div key={curr} className="flex justify-between">
                          <span className="text-slate-600">{curr.replace(/_/g, " ")}</span>
                          <span className="font-medium text-slate-900">{val}</span>
                        </div>
                      ) : null
                    )}
                  </div>
                </CardBody>
              </Card>
            )}

            {catalogCards && catalogCards.cards.length > 0 && (
              <Card>
                <CardHeader>
                  <h3 className="font-semibold text-slate-900">Available cards</h3>
                </CardHeader>
                <CardBody>
                  <input
                    type="search"
                    placeholder="Search cards…"
                    value={cardsSearch}
                    onChange={(e) => setCardsSearch(e.target.value)}
                    aria-label="Search cards"
                    className="mb-3 w-full rounded-lg border border-slate-300 py-2 px-3 text-sm text-slate-900 placeholder:text-slate-400 focus:border-primary-500 focus:ring-1 focus:ring-primary-500"
                  />
                  <div className="mb-3 flex flex-wrap gap-1">
                    <button
                      type="button"
                      onClick={() => setCardsFilterCurrency("")}
                      className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${
                        !cardsFilterCurrency
                          ? "bg-primary-500 text-white"
                          : "bg-slate-100 text-slate-600 hover:bg-slate-200"
                      }`}
                    >
                      All
                    </button>
                    {["USD_CASH", "BANK_UR", "BANK_MR", "AA_MILES"].map((c) => (
                      <button
                        key={c}
                        type="button"
                        onClick={() =>
                          setCardsFilterCurrency(cardsFilterCurrency === c ? "" : c)
                        }
                        className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${
                          cardsFilterCurrency === c
                            ? "bg-primary-500 text-white"
                            : "bg-slate-100 text-slate-600 hover:bg-slate-200"
                        }`}
                      >
                        <Badge currency={c} />
                      </button>
                    ))}
                  </div>
                  <ul className="max-h-64 space-y-2 overflow-y-auto pr-1">
                    {filteredCards.map((c) => (
                      <li
                        key={c.cardId}
                        className="rounded-lg border border-slate-100 bg-slate-50 px-3 py-2"
                      >
                        <div className="flex items-center justify-between gap-2">
                          <span className="font-medium text-slate-900 text-sm">{c.name}</span>
                          <Badge currency={c.rewardCurrency} />
                        </div>
                        <div className="mt-0.5 flex items-center gap-2 text-xs text-slate-500">
                          <span>{c.issuer}</span>
                          <span>·</span>
                          <span>${c.annualFeeUsd}/yr</span>
                        </div>
                      </li>
                    ))}
                  </ul>
                </CardBody>
              </Card>
            )}

            <div>
              <label className="block text-xs text-slate-500">
                <input
                  ref={importInputRef}
                  type="file"
                  accept=".json"
                  className="sr-only"
                  onChange={(e) => {
                    const f = e.target.files?.[0]
                    if (!f) return
                    const r = new FileReader()
                    r.onload = () => {
                      try {
                        const parsed = JSON.parse(r.result as string) as unknown
                        if (
                          parsed &&
                          typeof parsed === "object" &&
                          "schemaVersion" in parsed &&
                          (parsed as { schemaVersion: string }).schemaVersion === "profile.v1"
                        ) {
                          setProfile(parsed as ProfileV1)
                          setError(null)
                        } else {
                          setError("Invalid profile: schemaVersion must be profile.v1")
                        }
                      } catch {
                        setError("Invalid JSON")
                      }
                      e.target.value = ""
                    }
                    r.readAsText(f)
                  }}
                />
                <button
                  type="button"
                  onClick={() => importInputRef.current?.click()}
                  className="text-primary-600 hover:text-primary-700"
                >
                  Import profile
                </button>
              </label>
            </div>
          </aside>
        </div>

        <footer className="mt-12 border-t border-slate-200 pt-6 text-center text-sm text-slate-500">
          Yukti · Grounded rewards optimization
        </footer>
      </div>
    </div>
  )
}
