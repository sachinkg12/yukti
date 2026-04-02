import type { ProfileV1 } from "./types"

export const defaultProfile: ProfileV1 = {
  schemaVersion: "profile.v1",
  updatedAtIso: new Date().toISOString(),
  catalogVersion: "1.0",
  period: "ANNUAL",
  spendByCategoryUsd: {
    GROCERIES: 0,
    DINING: 0,
    GAS: 0,
    TRAVEL: 0,
    ONLINE: 0,
    OTHER: 0,
  },
  goal: {
    goalType: "CASHBACK",
    primaryCurrency: null,
    preferredCurrencies: [],
  },
  constraints: {
    maxCards: 2,
    maxAnnualFeeUsd: 200,
    allowBusinessCards: true,
  },
  goalPrompt: "",
}

export const sampleSpend: Record<string, number> = {
  GROCERIES: 12000,
  DINING: 8000,
  GAS: 2400,
  TRAVEL: 2000,
  ONLINE: 1200,
  OTHER: 5000,
}

/** Spend presets: Family (sample), Road warrior, Online heavy */
export const spendPresets: Record<string, Record<string, number>> = {
  "Family (sample)": {
    GROCERIES: 12000,
    DINING: 8000,
    GAS: 2400,
    TRAVEL: 2000,
    ONLINE: 1200,
    OTHER: 5000,
  },
  "Road warrior": {
    GROCERIES: 2000,
    DINING: 6000,
    GAS: 4800,
    TRAVEL: 15000,
    ONLINE: 600,
    OTHER: 3000,
  },
  "Online heavy": {
    GROCERIES: 4000,
    DINING: 3000,
    GAS: 1200,
    TRAVEL: 2000,
    ONLINE: 18000,
    OTHER: 8000,
  },
}
