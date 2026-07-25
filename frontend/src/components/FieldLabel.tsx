import type { ReactNode } from 'react'

interface FieldLabelProps {
  label: string
  help: string
  children?: ReactNode
  className?: string
  htmlFor?: string
}

export function FieldLabel({ label, help, children, className, htmlFor }: FieldLabelProps) {
  const helpId = `${label.replace(/\s+/g, '-').toLowerCase()}-help`

  return (
    <div className={className}>
      <div className="mb-1 flex items-start gap-1.5">
        {htmlFor ? (
          <label htmlFor={htmlFor} className="font-medium text-slate-800">
            {label}
          </label>
        ) : (
          <span className="font-medium text-slate-800">{label}</span>
        )}
        <span className="group relative inline-flex shrink-0">
          <button
            type="button"
            className="flex h-4 w-4 items-center justify-center rounded-full border border-slate-300 bg-slate-50 text-[10px] font-semibold leading-none text-slate-500 hover:border-slate-400 hover:bg-white hover:text-slate-700"
            aria-describedby={helpId}
            aria-label={`Help: ${label}`}
          >
            ?
          </button>
          <span
            id={helpId}
            role="tooltip"
            className="pointer-events-none invisible absolute bottom-full left-1/2 z-50 mb-2 w-72 max-w-[calc(100vw-2rem)] -translate-x-1/2 rounded-lg border border-slate-200 bg-white p-3 text-left text-xs font-normal leading-relaxed text-slate-600 shadow-lg group-hover:visible group-focus-within:visible"
          >
            {help}
          </span>
        </span>
      </div>
      {children}
    </div>
  )
}
