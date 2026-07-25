import { useEffect, useState } from 'react'
import type { Settings } from '../api/types'
import { useSettings, useSettingsMutation } from '../hooks/queries'

const inputClass =
  'mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm shadow-sm focus:border-slate-500 focus:outline-none'

export function SettingsView() {
  const { data, isLoading, error } = useSettings()
  const mutation = useSettingsMutation()
  const [form, setForm] = useState<Settings | null>(null)

  useEffect(() => {
    if (data) setForm(data)
  }, [data])

  if (isLoading || !form) return <p className="text-sm text-slate-500">Loading settings…</p>
  if (error) return <p className="text-sm text-red-600">{(error as Error).message}</p>

  const set = <K extends keyof Settings>(key: K, value: Settings[K]) =>
    setForm((f) => (f ? { ...f, [key]: value } : f))

  return (
    <form
      className="max-w-xl space-y-4 rounded-xl border border-slate-200 bg-white p-6 shadow-sm"
      onSubmit={async (e) => {
        e.preventDefault()
        await mutation.mutateAsync(form)
      }}
    >
      <h2 className="text-lg font-semibold">Planner settings</h2>

      <label className="block text-sm">
        Soft budget (min/day)
        <input
          type="number"
          min={1}
          className={inputClass}
          value={form.softBudgetMinutes}
          onChange={(e) => set('softBudgetMinutes', Number(e.target.value))}
        />
      </label>

      <label className="block text-sm">
        Hard cap (min/day)
        <input
          type="number"
          min={1}
          className={inputClass}
          value={form.hardCapMinutes}
          onChange={(e) => set('hardCapMinutes', Number(e.target.value))}
        />
      </label>

      <label className="block text-sm">
        Pain threshold P* (reference)
        <input
          type="number"
          min={0}
          step={0.1}
          className={inputClass}
          value={form.painThreshold}
          onChange={(e) => set('painThreshold', Number(e.target.value))}
        />
      </label>

      <label className="block text-sm">
        Pain per minute over threshold
        <input
          type="number"
          min={0}
          step={0.01}
          className={inputClass}
          value={form.painPerMinuteOverThreshold}
          onChange={(e) => set('painPerMinuteOverThreshold', Number(e.target.value))}
        />
      </label>

      <label className="block text-sm">
        Backlog beta
        <input
          type="number"
          min={0}
          step={0.01}
          className={inputClass}
          value={form.beta}
          onChange={(e) => set('beta', Number(e.target.value))}
        />
      </label>

      <label className="block text-sm">
        Default backlog p (new tasks)
        <input
          type="number"
          min={0.01}
          max={1}
          step={0.01}
          className={inputClass}
          value={form.defaultBacklogP}
          onChange={(e) => set('defaultBacklogP', Number(e.target.value))}
        />
      </label>

      <label className="block text-sm">
        Planning extend factor
        <input
          type="number"
          min={1}
          className={inputClass}
          value={form.planningExtendFactor}
          onChange={(e) => set('planningExtendFactor', Number(e.target.value))}
        />
      </label>

      <button
        type="submit"
        disabled={mutation.isPending}
        className="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800 disabled:opacity-50"
      >
        Save settings
      </button>
    </form>
  )
}
