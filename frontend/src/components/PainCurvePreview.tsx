import { useId, useMemo } from 'react'
import type { TaskRules } from '../api/types'

function timingPainForOffset(rules: TaskRules, delta: number): number {
  if (delta >= -rules.graceEarlyDays && delta <= rules.graceLateDays) {
    return 0
  }
  const deltaEff =
    delta < -rules.graceEarlyDays ? delta + rules.graceEarlyDays : delta - rules.graceLateDays
  const sigma = deltaEff < 0 ? rules.sigmaEarly : rules.sigmaLate
  const safeSigma = sigma > 0 ? sigma : 1
  const acceptability = Math.exp(-(deltaEff * deltaEff) / (2 * safeSigma * safeSigma))
  return rules.importanceWeight * (1 - acceptability)
}

interface PainCurvePreviewProps {
  rules: TaskRules
}

export function PainCurvePreview({ rules }: PainCurvePreviewProps) {
  const gradientId = useId()
  const graceId = useId()

  const { points, maxPain, graceStart, graceEnd, minDelta, maxDelta } = useMemo(() => {
    const span = Math.max(
      14,
      rules.graceEarlyDays + rules.sigmaEarly * 3,
      rules.graceLateDays + rules.sigmaLate * 3,
    )
    const minDelta = -Math.ceil(span)
    const maxDelta = Math.ceil(span)
    const samples: { x: number; y: number }[] = []
    let maxPain = 0.01

    for (let delta = minDelta; delta <= maxDelta; delta++) {
      const pain = timingPainForOffset(rules, delta)
      maxPain = Math.max(maxPain, pain)
      samples.push({ x: delta, y: pain })
    }

    return {
      points: samples,
      maxPain,
      graceStart: -rules.graceEarlyDays,
      graceEnd: rules.graceLateDays,
      minDelta,
      maxDelta,
    }
  }, [rules])

  const width = 360
  const height = 140
  const pad = { top: 12, right: 12, bottom: 28, left: 36 }
  const plotW = width - pad.left - pad.right
  const plotH = height - pad.top - pad.bottom

  const xScale = (delta: number) =>
    pad.left + ((delta - minDelta) / (maxDelta - minDelta)) * plotW
  const yScale = (pain: number) => pad.top + plotH - (pain / maxPain) * plotH

  const path = points
    .map((p, i) => `${i === 0 ? 'M' : 'L'} ${xScale(p.x).toFixed(1)} ${yScale(p.y).toFixed(1)}`)
    .join(' ')

  const graceX1 = xScale(graceStart)
  const graceX2 = xScale(graceEnd)

  return (
    <div className="rounded-md border border-slate-200 bg-slate-50 p-3">
      <svg
        viewBox={`0 0 ${width} ${height}`}
        className="h-36 w-full"
        role="img"
        aria-label="Timing pain curve preview"
      >
        <defs>
          <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#10b981" stopOpacity="0.35" />
            <stop offset="100%" stopColor="#10b981" stopOpacity="0.05" />
          </linearGradient>
        </defs>

        <rect
          id={graceId}
          x={Math.min(graceX1, graceX2)}
          y={pad.top}
          width={Math.abs(graceX2 - graceX1)}
          height={plotH}
          fill="#d1fae5"
          opacity={0.7}
        />

        <line
          x1={pad.left}
          y1={pad.top + plotH}
          x2={width - pad.right}
          y2={pad.top + plotH}
          stroke="#cbd5e1"
        />
        <line
          x1={pad.left}
          y1={pad.top}
          x2={pad.left}
          y2={pad.top + plotH}
          stroke="#cbd5e1"
        />

        <path d={`${path} L ${xScale(maxDelta)} ${yScale(0)} L ${xScale(minDelta)} ${yScale(0)} Z`} fill={`url(#${gradientId})`} />
        <path d={path} fill="none" stroke="#0f766e" strokeWidth="2" />

        <line
          x1={xScale(0)}
          y1={pad.top}
          x2={xScale(0)}
          y2={pad.top + plotH}
          stroke="#94a3b8"
          strokeDasharray="4 3"
        />

        <text x={pad.left} y={height - 8} fill="#64748b" fontSize="10">
          Days from scheduled (− early, + late)
        </text>
        <text
          x={8}
          y={pad.top + plotH / 2}
          fill="#64748b"
          fontSize="10"
          transform={`rotate(-90 8 ${pad.top + plotH / 2})`}
          textAnchor="middle"
        >
          Timing pain
        </text>
        <text x={xScale(0)} y={pad.top + plotH + 14} fill="#64748b" fontSize="9" textAnchor="middle">
          due
        </text>
      </svg>
      <p className="mt-1 text-[11px] text-slate-500">
        Green band: grace window ({rules.graceEarlyDays}d early, {rules.graceLateDays}d late). Peak
        pain ≈ {rules.importanceWeight.toFixed(1)} at large offsets.
      </p>
    </div>
  )
}
