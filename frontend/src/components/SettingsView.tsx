import { useEffect, useState } from 'react'
import type { Settings } from '../api/types'
import { useSettings, useSettingsMutation } from '../hooks/queries'
import { FutureFeatureField } from './FutureFeatureField'
import { fieldsetFutureClass, inputClass, inputClassFuture } from './formStyles'

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
          className={`mt-1 ${inputClass}`}
          value={form.softBudgetMinutes}
          onChange={(e) => set('softBudgetMinutes', Number(e.target.value))}
        />
      </label>

      <label className="block text-sm">
        Hard cap (min/day)
        <input
          type="number"
          min={1}
          className={`mt-1 ${inputClass}`}
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
          className={`mt-1 ${inputClass}`}
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
          className={`mt-1 ${inputClass}`}
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
          className={`mt-1 ${inputClass}`}
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
          className={`mt-1 ${inputClass}`}
          value={form.defaultBacklogP}
          onChange={(e) => set('defaultBacklogP', Number(e.target.value))}
        />
      </label>

      <label className="block text-sm">
        Planning extend factor
        <input
          type="number"
          min={1}
          className={`mt-1 ${inputClass}`}
          value={form.planningExtendFactor}
          onChange={(e) => set('planningExtendFactor', Number(e.target.value))}
        />
      </label>

      <fieldset className={`${fieldsetFutureClass} max-w-xl`}>
        <legend className="col-span-full px-1 text-sm font-semibold text-amber-900">Planned features</legend>
        <p className="col-span-full -mt-1 text-xs text-amber-900/80">
          Planner options not yet exposed in settings.
        </p>

        <FutureFeatureField
          label="Local search iterations"
          hint="Post-greedy improvement pass (planning v2)."
        >
          <input type="number" className={inputClassFuture} disabled value={1000} readOnly />
        </FutureFeatureField>

        <FutureFeatureField
          label="Same-day ordering"
          hint="Define finish-before edges between tasks on the same day."
        >
          <textarea
            className={`${inputClassFuture} min-h-20 resize-none`}
            disabled
            placeholder="No ordering rules editor yet"
            readOnly
          />
        </FutureFeatureField>
      </fieldset>

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
