import { useState } from 'react'
import { format, parseISO } from 'date-fns'
import type { Task, TaskRules } from '../api/types'
import { taskFieldHelp } from '../taskFieldHelp'
import { FieldLabel } from './FieldLabel'
import { FutureFeatureField } from './FutureFeatureField'
import { PainCurvePreview } from './PainCurvePreview'
import { fieldsetFutureClass, inputClass, inputClassFuture } from './formStyles'

function defaultRules(): TaskRules {
  return {
    intervalType: 'EVERY_N_DAYS',
    intervalN: 1,
    anchorMode: 'EPOCH',
    catchUp: true,
    useBacklogMultiplier: true,
    allowedWeekdays: [],
    durationMinutes: 15,
    importanceWeight: 1,
    graceEarlyDays: 0,
    graceLateDays: 0,
    sigmaEarly: 3,
    sigmaLate: 3,
    backlogP: 0.6,
  }
}

interface TaskFormProps {
  initial?: Task
  onSubmit: (values: { name: string; description: string; rules: TaskRules; archived?: boolean }) => void
  onCancel?: () => void
  submitting?: boolean
}

export function TaskForm({ initial, onSubmit, onCancel, submitting }: TaskFormProps) {
  const [name, setName] = useState(initial?.name ?? '')
  const [description, setDescription] = useState(initial?.description ?? '')
  const [archived, setArchived] = useState(initial?.archived ?? false)
  const [rules, setRules] = useState<TaskRules>(initial?.rules ?? defaultRules())

  const setRule = <K extends keyof TaskRules>(key: K, value: TaskRules[K]) =>
    setRules((r) => ({ ...r, [key]: value }))

  return (
    <form
      className="space-y-4 rounded-xl border border-slate-200 bg-white p-5 shadow-sm"
      onSubmit={(e) => {
        e.preventDefault()
        onSubmit({ name, description, rules, archived: initial ? archived : undefined })
      }}
    >
      <div className="grid gap-4 md:grid-cols-2">
        <div className="text-sm">
          <FieldLabel label="Name" help={taskFieldHelp.name} htmlFor="task-name" />
          <input
            id="task-name"
            className={inputClass}
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
          />
        </div>
        <div className="text-sm">
          <FieldLabel label="Description" help={taskFieldHelp.description} htmlFor="task-description" />
          <input
            id="task-description"
            className={inputClass}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
          />
        </div>
      </div>

      <fieldset className="grid gap-4 md:grid-cols-3">
        <legend className="col-span-full text-sm font-semibold text-slate-700">Recurrence</legend>
        <div className="text-sm">
          <FieldLabel label="Interval type" help={taskFieldHelp.intervalType} htmlFor="task-interval-type" />
          <select
            id="task-interval-type"
            className={inputClass}
            value={rules.intervalType}
            onChange={(e) => setRule('intervalType', e.target.value as TaskRules['intervalType'])}
          >
            <option value="EVERY_N_DAYS">Every N days</option>
            <option value="EVERY_N_WEEKS">Every N weeks</option>
            <option value="EVERY_N_MONTHS">Every N months</option>
            <option value="EVERY_N_YEARS">Every N years</option>
            <option value="EXTERNAL_DUE">External due script</option>
          </select>
        </div>
        <div className="text-sm">
          <FieldLabel label="N (≥ 1)" help={taskFieldHelp.intervalN} htmlFor="task-interval-n" />
          <input
            id="task-interval-n"
            type="number"
            min={1}
            step={0.001}
            className={inputClass}
            value={rules.intervalN}
            onChange={(e) => setRule('intervalN', Math.max(1, Number(e.target.value)))}
            disabled={rules.intervalType === 'EXTERNAL_DUE'}
          />
        </div>
        <div className="text-sm">
          <FieldLabel label="Anchor" help={taskFieldHelp.anchorMode} htmlFor="task-anchor-mode" />
          <select
            id="task-anchor-mode"
            className={inputClass}
            value={rules.anchorMode}
            onChange={(e) => setRule('anchorMode', e.target.value as TaskRules['anchorMode'])}
          >
            <option value="EPOCH">Epoch</option>
            <option value="LAST_COMPLETION">Last completion</option>
          </select>
        </div>
        <div className="flex items-center gap-2 text-sm">
          <input
            id="task-catch-up"
            type="checkbox"
            checked={rules.catchUp}
            onChange={(e) => setRule('catchUp', e.target.checked)}
          />
          <FieldLabel label="Catch-up" help={taskFieldHelp.catchUp} htmlFor="task-catch-up" />
        </div>
        <div className="text-sm">
          <FieldLabel label="Duration (min)" help={taskFieldHelp.durationMinutes} htmlFor="task-duration" />
          <input
            id="task-duration"
            type="number"
            min={1}
            className={inputClass}
            value={rules.durationMinutes}
            onChange={(e) => setRule('durationMinutes', Number(e.target.value))}
          />
        </div>
        <div className="text-sm">
          <FieldLabel label="Importance" help={taskFieldHelp.importanceWeight} htmlFor="task-importance" />
          <input
            id="task-importance"
            type="number"
            min={1}
            step={0.1}
            className={inputClass}
            value={rules.importanceWeight}
            onChange={(e) => setRule('importanceWeight', Math.max(1, Number(e.target.value)))}
          />
        </div>
      </fieldset>

      <fieldset className="grid gap-4 md:grid-cols-4">
        <legend className="col-span-full text-sm font-semibold text-slate-700">Flexibility / pain</legend>
        <div className="text-sm">
          <FieldLabel label="Grace early (days)" help={taskFieldHelp.graceEarlyDays} htmlFor="task-grace-early" />
          <input
            id="task-grace-early"
            type="number"
            min={0}
            className={inputClass}
            value={rules.graceEarlyDays}
            onChange={(e) => setRule('graceEarlyDays', Number(e.target.value))}
          />
        </div>
        <div className="text-sm">
          <FieldLabel label="Grace late (days)" help={taskFieldHelp.graceLateDays} htmlFor="task-grace-late" />
          <input
            id="task-grace-late"
            type="number"
            min={0}
            className={inputClass}
            value={rules.graceLateDays}
            onChange={(e) => setRule('graceLateDays', Number(e.target.value))}
          />
        </div>
        <div className="text-sm">
          <FieldLabel label="Sigma early" help={taskFieldHelp.sigmaEarly} htmlFor="task-sigma-early" />
          <input
            id="task-sigma-early"
            type="number"
            min={0.1}
            step={0.1}
            className={inputClass}
            value={rules.sigmaEarly}
            onChange={(e) => setRule('sigmaEarly', Number(e.target.value))}
          />
        </div>
        <div className="text-sm">
          <FieldLabel label="Sigma late" help={taskFieldHelp.sigmaLate} htmlFor="task-sigma-late" />
          <input
            id="task-sigma-late"
            type="number"
            min={0.1}
            step={0.1}
            className={inputClass}
            value={rules.sigmaLate}
            onChange={(e) => setRule('sigmaLate', Number(e.target.value))}
          />
        </div>
        <div className="text-sm">
          <FieldLabel label="Backlog exponent p" help={taskFieldHelp.backlogP} htmlFor="task-backlog-p" />
          <input
            id="task-backlog-p"
            type="number"
            min={0.01}
            max={1}
            step={0.01}
            className={inputClass}
            value={rules.backlogP}
            onChange={(e) => setRule('backlogP', Number(e.target.value))}
          />
        </div>
        <div className="col-span-full md:col-span-2">
          <FieldLabel label="Pain curve preview" help={taskFieldHelp.painCurvePreview} />
          <PainCurvePreview rules={rules} />
        </div>
      </fieldset>

      {rules.intervalType === 'EXTERNAL_DUE' && (
        <div className="space-y-4">
          <div className="text-sm">
            <FieldLabel label="Due script path" help={taskFieldHelp.dueScriptPath} htmlFor="task-due-script" />
            <input
              id="task-due-script"
              className={inputClass}
              value={rules.dueScriptPath ?? ''}
              onChange={(e) => setRule('dueScriptPath', e.target.value)}
            />
          </div>
          <FutureFeatureField label="Due script arguments" hint={taskFieldHelp.dueScriptArgs}>
            <input className={inputClassFuture} disabled placeholder="e.g. --config /path/to.cfg" />
          </FutureFeatureField>
        </div>
      )}

      <fieldset className={fieldsetFutureClass}>
        <legend className="col-span-full px-1 text-sm font-semibold text-amber-900">Planned features</legend>
        <p className="col-span-full -mt-1 text-xs text-amber-900/80">
          Fields below are on the v2 roadmap. They use dashed styling until the feature ships.
        </p>

        <FutureFeatureField label="Tags" hint={taskFieldHelp.tags}>
          <input className={inputClassFuture} disabled placeholder="e.g. home, health" />
        </FutureFeatureField>

        <FutureFeatureField label="Preferred time of day" hint={taskFieldHelp.preferredTime}>
          <select className={inputClassFuture} disabled defaultValue="">
            <option value="">Not set</option>
            <option value="morning">Morning</option>
            <option value="afternoon">Afternoon</option>
            <option value="evening">Evening</option>
          </select>
        </FutureFeatureField>

        <FutureFeatureField label="Allowed weekdays" hint={taskFieldHelp.allowedWeekdays}>
          <input className={inputClassFuture} disabled value="All days (weekday picker planned)" readOnly />
        </FutureFeatureField>

        <FutureFeatureField label="Season start" hint={taskFieldHelp.seasonStart}>
          <input type="date" className={inputClassFuture} disabled />
        </FutureFeatureField>

        <FutureFeatureField label="Season end" hint={taskFieldHelp.seasonEnd}>
          <input type="date" className={inputClassFuture} disabled />
        </FutureFeatureField>

        <FutureFeatureField label="End date" hint={taskFieldHelp.endDate}>
          <input type="date" className={inputClassFuture} disabled />
        </FutureFeatureField>

        <FutureFeatureField label="Min days between scheduled" hint={taskFieldHelp.minDaysBetweenScheduled}>
          <input type="number" min={0} className={inputClassFuture} disabled placeholder="e.g. 7" />
        </FutureFeatureField>

        <FutureFeatureField label="Backlog count multiplier" hint={taskFieldHelp.useBacklogMultiplier}>
          <label className="flex items-center gap-2 text-sm text-slate-600">
            <input type="checkbox" className="accent-amber-600" checked disabled />
            Enabled (editor planned)
          </label>
        </FutureFeatureField>

        <FutureFeatureField label="Nth weekday of month" hint={taskFieldHelp.nthWeekday}>
          <input className={inputClassFuture} disabled placeholder="Not available yet" readOnly />
        </FutureFeatureField>
      </fieldset>

      {initial && (
        <FutureFeatureField label="Epoch start" hint={taskFieldHelp.epochStart}>
          <input
            type="date"
            className={inputClassFuture}
            disabled
            value={initial.epochStart ?? ''}
            readOnly
          />
        </FutureFeatureField>
      )}

      {initial && (
        <div className="flex items-center gap-2 text-sm">
          <input
            id="task-archived"
            type="checkbox"
            checked={archived}
            onChange={(e) => setArchived(e.target.checked)}
          />
          <FieldLabel label="Archived" help={taskFieldHelp.archived} htmlFor="task-archived" />
        </div>
      )}

      {initial && (
        <div className="rounded-md bg-slate-50 p-3 text-xs text-slate-600">
          <div>Next scheduled: {initial.nextScheduled ?? '—'}</div>
          <div>Catch-up count: {initial.catchUpCount}</div>
          {initial.lastMissedScheduledAt && (
            <div>Last missed: {format(parseISO(initial.lastMissedScheduledAt), 'yyyy-MM-dd')}</div>
          )}
        </div>
      )}

      <div className="flex gap-2">
        <button
          type="submit"
          disabled={submitting}
          className="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800 disabled:opacity-50"
        >
          {initial ? 'Save task' : 'Create task'}
        </button>
        {onCancel && (
          <button
            type="button"
            onClick={onCancel}
            className="rounded-md border border-slate-300 px-4 py-2 text-sm hover:bg-slate-50"
          >
            Cancel
          </button>
        )}
      </div>
    </form>
  )
}
