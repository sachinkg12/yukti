import type { ProfileV1, OptimizeResponseV1 } from "./types"

const PROFILE_KEY = "yukti.profile"
const LAST_RESULT_KEY = "yukti.lastResult"

export function loadProfile(): ProfileV1 | null {
  try {
    const s = localStorage.getItem(PROFILE_KEY)
    if (!s) return null
    const parsed = JSON.parse(s) as Record<string, unknown>
    if (parsed.schemaVersion !== "profile.v1") return null
    return parsed as ProfileV1
  } catch {
    return null
  }
}

export function saveProfile(profile: ProfileV1): void {
  profile.updatedAtIso = new Date().toISOString()
  localStorage.setItem(PROFILE_KEY, JSON.stringify(profile))
}

export function loadLastResult(): OptimizeResponseV1 | null {
  try {
    const s = localStorage.getItem(LAST_RESULT_KEY)
    if (!s) return null
    return JSON.parse(s) as OptimizeResponseV1
  } catch {
    return null
  }
}

export function saveLastResult(result: OptimizeResponseV1): void {
  localStorage.setItem(LAST_RESULT_KEY, JSON.stringify(result))
}
