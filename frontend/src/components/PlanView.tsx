import { useMemo, useState } from 'react'
import { Calendar, dateFnsLocalizer, type Event, type View } from 'react-big-calendar'
import {
  addDays,
  endOfMonth,
  endOfWeek,
  format,
  getDay,
  parse,
  parseISO,
  startOfMonth,
  startOfWeek,
} from 'date-fns'
import { enUS } from 'date-fns/locale'
import type { PlanItem } from '../api/types'
import { taskMap, usePlan, useTasks } from '../hooks/queries'
import { InstanceDialog } from './InstanceDialog'

const locales = { 'en-US': enUS }

const localizer = dateFnsLocalizer({
  format,
  parse,
  startOfWeek,
  getDay,
  locales,
})

interface PlanEvent extends Event {
  resource: PlanItem
}

function painColor(pain: number, maxPain: number): string {
  if (pain <= 0) return '#10b981'
  const t = Math.min(1, pain / Math.max(maxPain, 1))
  const r = Math.round(16 + t * 220)
  const g = Math.round(185 - t * 140)
  return `rgb(${r}, ${g}, 80)`
}

export function PlanView() {
  const today = new Date()
  const [cursor, setCursor] = useState(today)
  const [view, setView] = useState<View>('week')
  const [selected, setSelected] = useState<PlanItem | null>(null)

  const horizonStart = useMemo(() => {
    if (view === 'month') return format(startOfMonth(cursor), 'yyyy-MM-dd')
    return format(startOfWeek(cursor, { weekStartsOn: 1 }), 'yyyy-MM-dd')
  }, [cursor, view])

  const horizonEnd = useMemo(() => {
    if (view === 'month') return format(endOfMonth(cursor), 'yyyy-MM-dd')
    return format(endOfWeek(cursor, { weekStartsOn: 1 }), 'yyyy-MM-dd')
  }, [cursor, view])

  const { data: plan, isLoading, error, refetch, isFetching } = usePlan(horizonStart, horizonEnd)
  const { data: tasks } = useTasks()
  const names = taskMap(tasks)

  const maxPain = useMemo(
    () => Math.max(1, ...(plan?.items.map((i) => i.timingPain) ?? [1])),
    [plan],
  )

  const events: PlanEvent[] = useMemo(() => {
    if (!plan) return []
    return plan.items
      .filter((item) => item.plannedAt)
      .map((item) => {
        const start = parseISO(item.plannedAt!)
        const end = addDays(start, 1)
        return {
          title: names.get(item.taskId)?.name ?? 'Task',
          start,
          end,
          allDay: true,
          resource: item,
        }
      })
  }, [plan, names])

  const unassigned = plan?.items.filter((i) => !i.plannedAt) ?? []

  const dayPropGetter = (date: Date) => {
    const key = format(date, 'yyyy-MM-dd')
    const day = plan?.days.find((d) => d.date === key)
    if (day?.overHardCap) {
      return { className: 'over-cap-day' }
    }
    return {}
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-lg font-semibold">Plan</h2>
          <p className="text-sm text-slate-500">
            {horizonStart} → {horizonEnd}
            {isFetching && ' · refreshing…'}
          </p>
        </div>
        <button
          type="button"
          onClick={() => refetch()}
          className="rounded-md border border-slate-300 px-3 py-1.5 text-sm hover:bg-white"
        >
          Refresh plan
        </button>
      </div>

      {plan && (
        <div className="grid gap-3 rounded-xl border border-slate-200 bg-white p-4 text-sm md:grid-cols-4">
          <div>
            <div className="text-slate-500">Total pain</div>
            <div className="text-lg font-semibold">{plan.pTotal.toFixed(1)}</div>
          </div>
          <div>
            <div className="text-slate-500">Timing</div>
            <div>{plan.pTiming.toFixed(1)}</div>
          </div>
          <div>
            <div className="text-slate-500">Daily</div>
            <div>{plan.pDaily.toFixed(1)}</div>
          </div>
          <div>
            <div className="text-slate-500">Unplanned timing</div>
            <div>{plan.pTimingUnassigned.toFixed(1)}</div>
          </div>
          {plan.warnings.length > 0 && (
            <div className="col-span-full text-amber-700">
              Warnings: {plan.warnings.join(', ')}
            </div>
          )}
        </div>
      )}

      {isLoading && <p className="text-sm text-slate-500">Loading plan…</p>}
      {error && <p className="text-sm text-red-600">{(error as Error).message}</p>}

      <div className="grid gap-4 xl:grid-cols-[1fr_280px]">
        <div className="plan-calendar">
        <Calendar
          localizer={localizer}
          events={events}
          view={view}
          onView={setView}
          date={cursor}
          onNavigate={setCursor}
          views={['month', 'week']}
          toolbar
          popup
          showMultiDayTimes={false}
          dayPropGetter={dayPropGetter}
          onSelectEvent={(event) => setSelected((event as PlanEvent).resource)}
          eventPropGetter={(event) => {
            const item = (event as PlanEvent).resource
            return {
              style: {
                backgroundColor: painColor(item.timingPain, maxPain),
                color: '#0f172a',
                fontSize: '0.75rem',
              },
            }
          }}
          components={{
            event: ({ event }) => {
              const item = (event as PlanEvent).resource
              return (
                <div className="leading-tight">
                  <div className="font-medium">{event.title}</div>
                  <div>{item.durationMinutes}m · pain {item.timingPain.toFixed(1)}</div>
                </div>
              )
            },
          }}
        />
        </div>

        <aside className="rounded-xl border border-slate-200 bg-white p-4">
          <h3 className="mb-2 text-sm font-semibold text-slate-700">Unplanned</h3>
          {unassigned.length === 0 && (
            <p className="text-sm text-slate-500">All instances placed in horizon.</p>
          )}
          <ul className="space-y-2">
            {unassigned.map((item) => (
              <li key={item.instanceKey}>
                <button
                  type="button"
                  className="w-full rounded-lg border border-slate-200 px-3 py-2 text-left text-sm hover:border-slate-400"
                  onClick={() => setSelected(item)}
                >
                  <div className="font-medium">{names.get(item.taskId)?.name ?? 'Task'}</div>
                  <div className="text-xs text-slate-500">
                    due {item.scheduledAt} · ref pain {item.timingPain.toFixed(1)}
                  </div>
                </button>
              </li>
            ))}
          </ul>

          <h3 className="mb-2 mt-6 text-sm font-semibold text-slate-700">Daily load</h3>
          <ul className="space-y-1 text-xs text-slate-600">
            {(plan?.days ?? []).map((d) => (
              <li key={d.date} className={d.overHardCap ? 'font-medium text-red-600' : ''}>
                {d.date}: {d.loadMinutes} min · pain {d.dailyPain.toFixed(1)}
                {d.overHardCap && ' · over cap'}
              </li>
            ))}
          </ul>
        </aside>
      </div>

      <InstanceDialog
        item={selected}
        taskName={selected ? (names.get(selected.taskId)?.name ?? 'Task') : ''}
        open={selected !== null}
        onOpenChange={(open) => !open && setSelected(null)}
      />
    </div>
  )
}
