import type { ReactNode } from "react"

export function Card({
  children,
  className = "",
  hover,
}: {
  children: ReactNode
  className?: string
  hover?: boolean
}) {
  return (
    <div
      className={`
        rounded-xl border border-slate-200 bg-white p-6 shadow-card
        ${hover ? "transition-shadow hover:shadow-card-hover" : ""}
        ${className}
      `}
    >
      {children}
    </div>
  )
}

export function CardHeader({
  children,
  className = "",
}: {
  children: ReactNode
  className?: string
}) {
  return (
    <div className={`mb-4 border-b border-slate-100 pb-4 ${className}`}>
      {children}
    </div>
  )
}

export function CardBody({ children, className = "" }: { children: ReactNode; className?: string }) {
  return <div className={className}>{children}</div>
}
