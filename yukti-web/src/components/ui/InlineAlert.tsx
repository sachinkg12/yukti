import type { ApiError } from "../../api/client"

export function InlineAlert({
  error,
  apiError,
  onDismiss,
}: {
  error?: string | null
  apiError?: ApiError | null
  onDismiss?: () => void
}) {
  const msg = error || (apiError ? `${apiError.errorCode}: ${apiError.message}` : null)
  if (!msg) return null
  return (
    <div
      role="alert"
      className="rounded-lg border border-error/30 bg-red-50 p-4 text-sm text-error"
    >
      <div className="flex items-start justify-between gap-2">
        <div>
          {apiError && (
            <p className="font-semibold">{apiError.errorCode}</p>
          )}
          <p>{apiError ? apiError.message : error}</p>
          {apiError?.details && apiError.details.length > 0 && (
            <ul className="mt-2 list-inside list-disc text-red-700">
              {apiError.details.map((d, i) => (
                <li key={i}>
                  {d.field}: {d.issue}
                </li>
              ))}
            </ul>
          )}
          {apiError?.requestId && (
            <p className="mt-1 text-xs text-red-600">Request ID: {apiError.requestId}</p>
          )}
        </div>
        {onDismiss && (
          <button
            type="button"
            onClick={onDismiss}
            aria-label="Dismiss"
            className="shrink-0 rounded p-1 hover:bg-red-100"
          >
            ×
          </button>
        )}
      </div>
    </div>
  )
}
