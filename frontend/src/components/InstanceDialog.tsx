import * as Dialog from '@radix-ui/react-dialog'
import { useState } from 'react'
import type { PlanItem } from '../api/types'
import { useInstanceActions } from '../hooks/queries'

interface InstanceDialogProps {
  item: PlanItem | null
  taskName: string
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function InstanceDialog({ item, taskName, open, onOpenChange }: InstanceDialogProps) {
  const actions = useInstanceActions()
  const [intervalDelta, setIntervalDelta] = useState('')
  const [snoozeUntil, setSnoozeUntil] = useState('')

  if (!item) return null

  const canSnooze = item.placement !== 'unassigned'

  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 bg-black/40" />
        <Dialog.Content className="fixed left-1/2 top-1/2 z-50 w-full max-w-md -translate-x-1/2 -translate-y-1/2 rounded-xl bg-white p-6 shadow-xl">
          <Dialog.Title className="text-lg font-semibold">{taskName}</Dialog.Title>
          <Dialog.Description className="mt-1 text-sm text-slate-500">
            Scheduled {item.scheduledAt}
            {item.plannedAt ? ` · Planned ${item.plannedAt}` : ' · Unplanned'}
          </Dialog.Description>

          <dl className="mt-4 grid grid-cols-2 gap-2 text-sm">
            <div>
              <dt className="text-slate-500">Timing pain</dt>
              <dd className="font-medium">{item.timingPain.toFixed(2)}</dd>
            </div>
            <div>
              <dt className="text-slate-500">Duration</dt>
              <dd className="font-medium">{item.durationMinutes} min</dd>
            </div>
            {item.backlog && (
              <div className="col-span-2 text-amber-700">Backlog virtual instance</div>
            )}
            {item.overdue && (
              <div className="col-span-2 text-red-600">Overdue at horizon start</div>
            )}
          </dl>

          <div className="mt-6 space-y-4">
            <label className="block text-sm">
              Interval adjust on complete (%)
              <input
                type="number"
                step={1}
                placeholder="e.g. 10 or -5"
                className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                value={intervalDelta}
                onChange={(e) => setIntervalDelta(e.target.value)}
              />
            </label>

            {canSnooze && (
              <label className="block text-sm">
                Snooze until
                <input
                  type="date"
                  className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                  value={snoozeUntil}
                  onChange={(e) => setSnoozeUntil(e.target.value)}
                />
              </label>
            )}
          </div>

          <div className="mt-6 flex flex-wrap gap-2">
            <button
              type="button"
              className="rounded-md bg-emerald-600 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-700 disabled:opacity-50"
              disabled={actions.complete.isPending}
              onClick={async () => {
                await actions.complete.mutateAsync({
                  taskId: item.taskId,
                  scheduledAt: item.scheduledAt,
                  plannedAt: item.plannedAt ?? undefined,
                  intervalDeltaPercent: intervalDelta ? Number(intervalDelta) : undefined,
                })
                onOpenChange(false)
              }}
            >
              Mark done
            </button>
            {canSnooze && snoozeUntil && (
              <button
                type="button"
                className="rounded-md border border-slate-300 px-4 py-2 text-sm hover:bg-slate-50 disabled:opacity-50"
                disabled={actions.snooze.isPending}
                onClick={async () => {
                  await actions.snooze.mutateAsync({
                    taskId: item.taskId,
                    snoozeUntil,
                  })
                  onOpenChange(false)
                }}
              >
                Snooze
              </button>
            )}
            <Dialog.Close asChild>
              <button type="button" className="rounded-md px-4 py-2 text-sm text-slate-600 hover:bg-slate-100">
                Close
              </button>
            </Dialog.Close>
          </div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  )
}
