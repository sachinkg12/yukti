import type {
  OptimizeRequest,
  OptimizeResponseV1,
  GoalsConfigResponse,
  CardsResponse,
  ApiErrorResponse,
} from "../types"

// In dev, use /api proxy (avoids CORS). In prod, use full URL.
const baseUrl = import.meta.env.DEV
  ? "/api"
  : (import.meta.env.VITE_API_BASE_URL ?? "http://localhost:18000")

function genRequestId(): string {
  return crypto.randomUUID()
}

const FETCH_TIMEOUT_MS = 90_000

async function fetchJson<T>(
  url: string,
  opts?: RequestInit & { timeoutMs?: number }
): Promise<{ ok: boolean; data: T; status: number }> {
  const requestId = genRequestId()
  const timeoutMs = opts?.timeoutMs ?? FETCH_TIMEOUT_MS
  const controller = new AbortController()
  const timeoutId = setTimeout(() => controller.abort(), timeoutMs)
  const res = await fetch(url, {
    ...opts,
    signal: controller.signal,
    headers: {
      "Content-Type": "application/json",
      "X-Request-Id": requestId,
      ...opts?.headers,
    },
  })
  clearTimeout(timeoutId)
  const text = await res.text()
  let data: T
  try {
    data = JSON.parse(text || "{}") as T
  } catch {
    throw new Error(res.ok ? "Invalid JSON response" : `Request failed: ${res.status}`)
  }
  return { ok: res.ok, data, status: res.status }
}

/** API error with typed details */
export type ApiError = {
  requestId: string
  errorCode: string
  message: string
  details: Array<{ field: string; issue: string }>
}

export async function getGoalsConfig(): Promise<GoalsConfigResponse> {
  const { ok, data } = await fetchJson<GoalsConfigResponse>(
    `${baseUrl}/v1/config/goals`
  )
  if (!ok) throw new Error("Failed to load goals config")
  return data
}

export async function getCatalogCards(
  catalogVersion?: string
): Promise<CardsResponse> {
  const qs = catalogVersion ? `?catalogVersion=${encodeURIComponent(catalogVersion)}` : ""
  const { ok, data } = await fetchJson<CardsResponse>(
    `${baseUrl}/v1/catalog/cards${qs}`
  )
  if (!ok) throw new Error("Failed to load catalog")
  return data
}

export type OptimizeResult =
  | { ok: true; data: OptimizeResponseV1 }
  | { ok: false; error: ApiError }

export async function optimize(req: OptimizeRequest): Promise<OptimizeResult> {
  const { ok, data } = await fetchJson<OptimizeResponseV1 | ApiErrorResponse>(
    `${baseUrl}/v1/optimize`,
    { method: "POST", body: JSON.stringify(req), timeoutMs: 90_000 }
  )
  if (ok) return { ok: true, data: data as OptimizeResponseV1 }
  const err = data as ApiErrorResponse
  return {
    ok: false,
    error: {
      requestId: err.requestId,
      errorCode: err.errorCode,
      message: err.message,
      details: err.details ?? [],
    },
  }
}
