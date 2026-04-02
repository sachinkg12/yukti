import { useState, useEffect } from "react"
import { useNavigate } from "react-router-dom"
import type { OptimizeResponseV1, ProfileV1 } from "../types"
import { loadProfile, loadLastResult, saveLastResult } from "../storage"
import {
  Accordion,
  Badge,
  Card,
  CardBody,
  CardHeader,
  ResultsSkeleton,
} from "../components/ui"

function downloadJson(data: unknown, filename: string) {
  const blob = new Blob([JSON.stringify(data, null, 2)], {
    type: "application/json",
  })
  const url = URL.createObjectURL(blob)
  const a = document.createElement("a")
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

function formatGoalLabel(goalType: string, primaryCurrency?: string | null): string {
  if (goalType === "CASHBACK") return "Cashback"
  if (goalType === "FLEX_POINTS") return "Flexible points"
  if (goalType === "PROGRAM_POINTS") return primaryCurrency ? `${primaryCurrency.replace(/_/g, " ")}` : "Program points"
  return goalType
}

/** Parse explanation.details: split by ## into sections, each with heading + bullet points */
function parseDetailsSections(details: string): Array<{ heading: string; bullets: string[] }> {
  if (!details?.trim()) return []
  const sections: Array<{ heading: string; bullets: string[] }> = []
  const blocks = details.split(/(?=^## )/m)
  for (const block of blocks) {
    const trimmed = block.trim()
    if (!trimmed) continue
    const match = trimmed.match(/^## (.+?)(?:\n|$)/)
    const heading = (match?.[1] ?? "").trim()
    const body = match ? trimmed.slice(match[0].length).trim() : trimmed
    const bullets = body
      .split("\n")
      .map((l) => l.replace(/^-\s*/, "").trim())
      .filter(Boolean)
    if (heading || bullets.length) sections.push({ heading, bullets })
  }
  return sections
}

/** Render sections as headings + bullet lists */
function DetailsSections({ details }: { details: string }) {
  const sections = parseDetailsSections(details)
  if (sections.length === 0) return <p className="text-sm text-slate-600">Not available</p>
  return (
    <div className="space-y-6">
      {sections.map((s) => (
        <div key={s.heading || "section"}>
          {s.heading && (
            <h4 className="mb-2 text-sm font-semibold text-slate-900">{s.heading}</h4>
          )}
          {s.bullets.length > 0 ? (
            <ul className="list-inside list-disc space-y-1 text-sm text-slate-600">
              {s.bullets.map((b, i) => (
                <li key={i}>{b}</li>
              ))}
            </ul>
          ) : null}
        </div>
      ))}
    </div>
  )
}

/** Render assumptions as bullet list */
function AssumptionsContent({ assumptions }: { assumptions: string }) {
  if (!assumptions?.trim()) return <p className="text-sm text-slate-600">Not available</p>
  const lines = assumptions
    .split("\n")
    .map((l) => l.replace(/^-\s*/, "").trim())
    .filter(Boolean)
  return (
    <ul className="list-inside list-disc space-y-1 text-sm text-slate-600">
      {lines.map((l, i) => (
        <li key={i}>{l}</li>
      ))}
    </ul>
  )
}

export default function Results() {
  const navigate = useNavigate()
  const [result, setResult] = useState<OptimizeResponseV1 | null>(null)
  const [profile, setProfile] = useState<ProfileV1 | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const fromSession = sessionStorage.getItem("yukti.result")
    if (fromSession) {
      try {
        const r = JSON.parse(fromSession) as OptimizeResponseV1
        setResult(r)
        saveLastResult(r)
      } catch {
        setResult(loadLastResult())
      }
    } else {
      setResult(loadLastResult())
    }
    setProfile(loadProfile())
    setLoading(false)
  }, [])

  const handleBack = () => navigate("/")

  const handleCopyFull = () => {
    if (result?.explanation.fullText) {
      navigator.clipboard.writeText(result.explanation.fullText)
    }
  }

  const handleExportResult = () => {
    if (result) {
      const date = new Date().toISOString().slice(0, 10).replace(/-/g, "")
      downloadJson(result, `yukti-result-v1-${date}.json`)
    }
  }

  const handleExportProfile = () => {
    if (profile) {
      const date = new Date().toISOString().slice(0, 10).replace(/-/g, "")
      downloadJson(profile, `yukti-profile-v1-${date}.json`)
    }
  }

  const handleCopySummary = () => {
    if (!result) return
    const { portfolio, allocation, breakdown, explanation } = result
    const lines = [
      "Yukti recommendation",
      "",
      `Portfolio: ${portfolio.map((c) => c.name).join(", ")}`,
      `Net value: $${breakdown.netValueUsd.toFixed(2)}`,
      "",
      "Allocation:",
      ...allocation.map((a) => `  ${a.category}: ${a.cardId}`),
      "",
      explanation.summary,
    ]
    navigator.clipboard.writeText(lines.join("\n"))
  }

  if (loading) {
    return (
      <div className="min-h-screen bg-slate-50">
        <div className="mx-auto max-w-3xl px-4 py-8 sm:px-6">
          <ResultsSkeleton />
        </div>
      </div>
    )
  }

  if (!result) {
    return (
      <div className="min-h-screen bg-slate-50">
        <div className="mx-auto max-w-3xl px-4 py-12 text-center sm:px-6">
          <h1 className="text-2xl font-bold text-slate-900">Results</h1>
          <p className="mt-2 text-slate-600">No results. Run an optimization first.</p>
          <button
            onClick={handleBack}
            className="mt-6 rounded-xl bg-primary-500 px-6 py-3 font-semibold text-white hover:bg-primary-600 focus-visible:ring-2 focus-visible:ring-primary-500 focus-visible:ring-offset-2"
          >
            Back to Home
          </button>
        </div>
      </div>
    )
  }

  const { breakdown, portfolio, allocation, explanation } = result
  const goalLabel = formatGoalLabel(
    profile?.goal.goalType ?? "CASHBACK",
    profile?.goal.primaryCurrency
  )

  return (
    <div className="min-h-screen bg-slate-50">
      <div className="mx-auto max-w-4xl px-4 py-8 sm:px-6 lg:px-8" role="main">
        <header className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h1 className="text-2xl font-bold text-slate-900 sm:text-3xl">Recommendation</h1>
            <p className="text-sm text-slate-500">Request ID: {result.requestId}</p>
          </div>
          <button
            onClick={handleBack}
            className="rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 focus-visible:ring-2 focus-visible:ring-primary-500 focus-visible:ring-offset-2"
          >
            ← Back
          </button>
        </header>

        {/* Recommendation summary card */}
        <Card className="mb-8">
          <CardHeader>
            <h2 className="text-lg font-semibold text-slate-900">Your optimized portfolio</h2>
          </CardHeader>
          <CardBody>
            <div className="flex flex-col gap-6 sm:flex-row sm:items-start sm:justify-between">
              <div>
                <div className="text-3xl font-bold text-success sm:text-4xl">
                  ${breakdown.netValueUsd.toFixed(2)}
                </div>
                <p className="mt-1 text-sm text-slate-600">Expected net value (USD)</p>
              </div>
              <div className="space-y-2 border-t border-slate-100 pt-4 sm:border-t-0 sm:border-l sm:border-slate-200 sm:pl-6 sm:pt-0">
                <p className="text-sm text-slate-600">
                  <span className="font-medium text-slate-900">Portfolio:</span>{" "}
                  {portfolio.length} card{portfolio.length !== 1 ? "s" : ""}
                </p>
                <p className="text-sm text-slate-600">
                  <span className="font-medium text-slate-900">Fees:</span> $
                  {breakdown.totalFeesUsd.toFixed(2)}/yr
                </p>
                <p className="text-sm text-slate-600">
                  <span className="font-medium text-slate-900">Goal:</span> {goalLabel}
                </p>
              </div>
            </div>
            <ul className="mt-6 flex flex-wrap gap-3">
              {portfolio.map((c) => (
                <li
                  key={c.cardId}
                  className="flex items-center gap-2 rounded-lg border border-slate-200 bg-slate-50 px-4 py-2"
                >
                  <span className="font-medium text-slate-900">{c.name}</span>
                  <Badge currency={c.rewardCurrency} />
                  <span className="text-sm text-slate-500">${c.annualFeeUsd}/yr</span>
                </li>
              ))}
            </ul>
          </CardBody>
        </Card>

        {/* Goal interpretation: only when user provided AI-assist goal prompt */}
        {result.goalInterpretation && (
          <Card className="mb-8">
            <CardHeader>
              <h2 className="text-lg font-semibold text-slate-900">How we interpreted your goal</h2>
            </CardHeader>
            <CardBody>
              <dl className="space-y-3 text-sm">
                <div>
                  <dt className="font-medium text-slate-700">What you asked for</dt>
                  <dd className="mt-0.5 text-slate-600">&ldquo;{result.goalInterpretation.userPrompt}&rdquo;</dd>
                </div>
                <div>
                  <dt className="font-medium text-slate-700">How we interpreted it</dt>
                  <dd className="mt-0.5 text-slate-600">{result.goalInterpretation.rationale}</dd>
                </div>
                <div>
                  <dt className="font-medium text-slate-700">Goal we optimized for</dt>
                  <dd className="mt-0.5 text-slate-600">
                    {result.goalInterpretation.interpretedGoalType.replace(/_/g, " ")}
                    {result.goalInterpretation.primaryCurrency
                      ? ` (${result.goalInterpretation.primaryCurrency.replace(/_/g, " ")})`
                      : ""}
                  </dd>
                </div>
              </dl>
            </CardBody>
          </Card>
        )}

        {/* How to use these cards */}
        <Card className="mb-8">
          <CardHeader>
            <h2 className="text-lg font-semibold text-slate-900">How to use these cards</h2>
          </CardHeader>
          <CardBody>
            <p className="mb-4 text-sm text-slate-600">
              Use the recommended card for each category. &ldquo;Spend up to&rdquo; is your annual spend in that category—put this amount on the recommended card to maximize benefit (switch to another card after if the explanation says so).
            </p>
            <div className="overflow-x-auto">
              <table className="w-full" role="table" aria-label="Category to card allocation">
                <thead>
                  <tr className="border-b border-slate-200">
                    <th scope="col" className="pb-3 pr-4 text-left text-sm font-medium text-slate-700">
                      Category
                    </th>
                    <th scope="col" className="pb-3 pr-4 text-left text-sm font-medium text-slate-700">
                      Recommended card
                    </th>
                    <th scope="col" className="pb-3 pr-4 text-left text-sm font-medium text-slate-700">
                      Spend up to
                    </th>
                    <th scope="col" className="pb-3 pr-4 text-left text-sm font-medium text-slate-700">
                      Earn rate %
                    </th>
                    <th scope="col" className="pb-3 text-left text-sm font-medium text-slate-700">
                      Earn value
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {allocation.map((a) => {
                    const spendUsd = profile?.spendByCategoryUsd?.[a.category as keyof typeof profile.spendByCategoryUsd]
                    return (
                      <tr key={a.category} className="border-b border-slate-100">
                        <td className="py-3 pr-4 text-slate-900">{a.category.replace(/_/g, " ")}</td>
                        <td className="py-3 pr-4 font-medium text-slate-900">{a.cardId}</td>
                        <td className="py-3 pr-4 text-slate-600">
                          {spendUsd != null && Number.isFinite(spendUsd)
                            ? `$${Number(spendUsd).toLocaleString("en-US", { minimumFractionDigits: 0, maximumFractionDigits: 0 })}/yr`
                            : "—"}
                        </td>
                        <td className="py-3 pr-4 text-slate-600">
                          {a.earnRatePercent != null && Number.isFinite(a.earnRatePercent)
                            ? `${Number(a.earnRatePercent).toFixed(2)}%`
                            : "—"}
                        </td>
                        <td className="py-3 text-slate-600">
                          {a.earnValueUsd != null && Number.isFinite(a.earnValueUsd)
                            ? `$${Number(a.earnValueUsd).toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
                            : "—"}
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          </CardBody>
        </Card>

        {/* Why this is best - collapsible explanation */}
        <Card className="mb-8">
          <CardHeader>
            <h2 className="text-lg font-semibold text-slate-900">Why this is best</h2>
          </CardHeader>
          <CardBody>
            <div className="space-y-1 divide-y divide-slate-100">
              <Accordion title="Why these cards" defaultOpen={true}>
                {(() => {
                  const sections = parseDetailsSections(explanation.details || "")
                  const whySection = sections.find(
                    (s) => s.heading.toLowerCase().includes("why these cards")
                  )
                  if (whySection?.bullets.length) {
                    return (
                      <ul className="list-inside list-disc space-y-1 text-sm text-slate-600">
                        {whySection.bullets.map((b, i) => (
                          <li key={i}>{b}</li>
                        ))}
                      </ul>
                    )
                  }
                  return (
                    <p className="text-sm text-slate-600">{explanation.summary || "Not available"}</p>
                  )
                })()}
              </Accordion>
              <Accordion title="Assumptions" defaultOpen={false}>
                <AssumptionsContent assumptions={explanation.assumptions || ""} />
              </Accordion>
            </div>
          </CardBody>
        </Card>

        {/* Export actions */}
        <Card>
          <CardHeader>
            <h2 className="text-lg font-semibold text-slate-900">Export</h2>
          </CardHeader>
          <CardBody>
            <div className="flex flex-wrap gap-3">
              <button
                onClick={handleCopySummary}
                className="rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 focus-visible:ring-2 focus-visible:ring-primary-500 focus-visible:ring-offset-2"
              >
                Copy shareable summary
              </button>
              <button
                onClick={handleCopyFull}
                className="rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 focus-visible:ring-2 focus-visible:ring-primary-500 focus-visible:ring-offset-2"
              >
                Copy full explanation
              </button>
              <button
                onClick={handleExportResult}
                className="rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 focus-visible:ring-2 focus-visible:ring-primary-500 focus-visible:ring-offset-2"
              >
                Download result JSON
              </button>
              <button
                onClick={handleExportProfile}
                className="rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 focus-visible:ring-2 focus-visible:ring-primary-500 focus-visible:ring-offset-2"
              >
                Export profile JSON
              </button>
            </div>
          </CardBody>
        </Card>

        {/* Evidence blocks (from solver output) */}
        {result.evidence && result.evidence.length > 0 && (
          <Card className="mt-8">
            <CardHeader>
              <h2 className="text-lg font-semibold text-slate-900">Evidence (advanced)</h2>
              <p className="text-sm text-slate-500">
                Structured evidence blocks backing the narrative; all narrative is from verified claims.
              </p>
            </CardHeader>
            <CardBody>
              <Accordion title="Evidence blocks" defaultOpen={false}>
                <pre className="overflow-x-auto rounded-lg bg-slate-50 p-4 text-xs text-slate-600">
                  {JSON.stringify(result.evidence, null, 2)}
                </pre>
              </Accordion>
            </CardBody>
          </Card>
        )}

        {/* Verification & digests: evidence graph, claims digest, verification status (IEEE / evaluator) */}
        {(result.verificationStatus != null ||
          result.claimCount != null ||
          result.evidenceGraphDigest != null ||
          result.claimsDigest != null ||
          (result.evidenceIds != null && result.evidenceIds.length > 0)) && (
          <Card className="mt-8">
            <CardHeader>
              <h2 className="text-lg font-semibold text-slate-900">Verification & reproducibility</h2>
              <p className="text-sm text-slate-500">
                Claim verification and digests for audit; narrative is rendered from verified claims only.
              </p>
            </CardHeader>
            <CardBody>
              <div className="space-y-4">
                {result.verificationStatus != null && (
                  <div>
                    <span className="text-sm font-medium text-slate-700">Verification status: </span>
                    <span
                      className={
                        result.verificationStatus === "PASS"
                          ? "text-sm font-medium text-success"
                          : "text-sm font-medium text-amber-600"
                      }
                    >
                      {result.verificationStatus}
                    </span>
                  </div>
                )}
                {result.claimCount != null && (
                  <div>
                    <span className="text-sm font-medium text-slate-700">Claim count: </span>
                    <span className="text-sm text-slate-600">{result.claimCount}</span>
                  </div>
                )}
                {result.verifierErrorCount != null && result.verifierErrorCount > 0 && (
                  <div>
                    <span className="text-sm font-medium text-slate-700">Verifier errors: </span>
                    <span className="text-sm text-amber-600">{result.verifierErrorCount}</span>
                  </div>
                )}
                {result.evidenceIds != null && result.evidenceIds.length > 0 && (
                  <div>
                    <span className="text-sm font-medium text-slate-700">Evidence IDs: </span>
                    <span className="text-sm text-slate-600">
                      {result.evidenceIds.length} node{result.evidenceIds.length !== 1 ? "s" : ""}
                    </span>
                  </div>
                )}
                {result.evidenceGraphDigest && (
                  <Accordion title="Evidence graph digest" defaultOpen={false}>
                    <p className="font-mono text-xs text-slate-600 break-all">
                      {result.evidenceGraphDigest}
                    </p>
                  </Accordion>
                )}
                {result.claimsDigest && (
                  <Accordion title="Claims digest" defaultOpen={false}>
                    <p className="font-mono text-xs text-slate-600 break-all">
                      {result.claimsDigest}
                    </p>
                  </Accordion>
                )}
              </div>
            </CardBody>
          </Card>
        )}
      </div>
    </div>
  )
}
