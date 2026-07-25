import type { ReactNode } from 'react'

interface FutureFeatureFieldProps {
  label: string
  hint?: string
  children: ReactNode
  className?: string
}

export function FutureFeatureField({ label, hint, children, className }: FutureFeatureFieldProps) {
  return (
    <div className={className}>
      <div className="mb-1 flex flex-wrap items-center gap-2">
        <span className="text-sm font-medium text-slate-700">{label}</span>
        <span className="rounded-full bg-amber-100 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-amber-800">
          Planned
        </span>
      </div>
      {hint && <p className="mb-1.5 text-xs text-amber-900/75">{hint}</p>}
      {children}
    </div>
  )
}
