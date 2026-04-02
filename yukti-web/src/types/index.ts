/** Category union type matching backend */
export type Category =
  | "GROCERIES"
  | "DINING"
  | "GAS"
  | "TRAVEL"
  | "ONLINE"
  | "OTHER"

/** Goal type matching backend */
export type GoalType = "CASHBACK" | "FLEX_POINTS" | "PROGRAM_POINTS"

/** Currency union type matching backend */
export type Currency =
  | "USD_CASH"
  | "BANK_UR"
  | "BANK_MR"
  | "BANK_TY"
  | "BANK_C1"
  | "AA_MILES"

/** Versioned profile for localStorage */
export type ProfileV1 = {
  schemaVersion: "profile.v1"
  updatedAtIso: string
  catalogVersion?: string
  period: "ANNUAL" | "MONTHLY"
  spendByCategoryUsd: Record<Category, number>
  goal: {
    goalType: GoalType
    primaryCurrency?: Currency | null
    preferredCurrencies?: Currency[]
    cppOverrides?: Partial<Record<Currency, number>>
  }
  constraints: {
    maxCards: 1 | 2 | 3
    maxAnnualFeeUsd: number
    allowBusinessCards: boolean
  }
  goalPrompt?: string
  optimizerId?: string
}

/** Optimize request DTO matching backend */
export type OptimizeRequest = {
  catalogVersion?: string
  period: "ANNUAL" | "MONTHLY"
  spendByCategoryUsd: Record<string, number>
  goal: {
    goalType: GoalType
    primaryCurrency?: string | null
    preferredCurrencies?: string[]
    cppOverrides?: Record<string, number>
  }
  constraints: {
    maxCards: number
    maxAnnualFeeUsd: number
    allowBusinessCards: boolean
  }
  goalPrompt?: string
  optimizerId?: string
}

/** Optimize response DTO matching backend */
export type OptimizeResponseV1 = {
  requestId: string
  catalogVersion: string
  optimizerId?: string
  portfolio: Array<{
    cardId: string
    name: string
    issuer: string
    annualFeeUsd: number
    rewardCurrency: string
  }>
  allocation: Array<{
    category: string
    cardId: string
    earnRatePercent?: number | null
    earnValueUsd?: number | null
  }>
  breakdown: {
    totalEarnValueUsd: number
    totalCreditValueUsd: number
    totalFeesUsd: number
    netValueUsd: number
  }
  explanation: {
    summary: string
    allocationTable: string
    details: string
    assumptions: string
    fullText: string
  }
  evidence: Array<{
    type: string
    cardId: string
    category: string
    content: string
  }>
  evidenceGraphDigest?: string
  evidenceIds?: string[]
  claimsDigest?: string
  verificationStatus?: string
  claimCount?: number
  verifierErrorCount?: number
  /** Present only when user provided goalPrompt (AI assist). */
  goalInterpretation?: {
    userPrompt: string
    interpretedGoalType: string
    primaryCurrency?: string | null
    rationale: string
  } | null
}

/** Goals config response */
export type GoalsConfigResponse = {
  supportedGoals: Array<{
    goalType: string
    requiresPrimaryCurrency: boolean
    allowedCurrencies: string[]
  }>
  defaultCppByCurrency: Record<string, number>
}

/** Catalog cards response */
export type CardsResponse = {
  catalogVersion: string
  cards: Array<{
    cardId: string
    issuer: string
    name: string
    segment: string
    annualFeeUsd: number
    rewardCurrency: string
  }>
}

/** API error response */
export type ApiErrorResponse = {
  requestId: string
  errorCode: "VALIDATION_ERROR" | "BAD_REQUEST" | "INTERNAL_ERROR"
  message: string
  details: Array<{ field: string; issue: string }>
}

export const CATEGORIES: Category[] = [
  "GROCERIES",
  "DINING",
  "GAS",
  "TRAVEL",
  "ONLINE",
  "OTHER",
]
