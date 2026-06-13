import { useState } from 'react'
import { format, parseISO } from 'date-fns'
import { useTaskMutations, useTasks } from '../hooks/queries'
import type { Task } from '../api/types'
import { TaskForm } from './TaskForm'

export function TasksView() {
  const { data: tasks, isLoading, error } = useTasks()
  const mutations = useTaskMutations()
  const [editing, setEditing] = useState<Task | null>(null)
  const [creating, setCreating] = useState(false)

  if (isLoading) return <p className="text-sm text-slate-500">Loading tasks…</p>
  if (error) return <p className="text-sm text-red-600">{(error as Error).message}</p>

  return (
    <div className="grid gap-6 lg:grid-cols-2">
      <section>
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-lg font-semibold">Tasks</h2>
          <button
            type="button"
            onClick={() => {
              setCreating(true)
              setEditing(null)
            }}
            className="rounded-md bg-slate-900 px-3 py-1.5 text-sm text-white hover:bg-slate-800"
          >
            New task
          </button>
        </div>

        <ul className="space-y-2">
          {(tasks ?? []).map((task) => (
            <li
              key={task.id}
              className={`cursor-pointer rounded-lg border px-4 py-3 transition hover:border-slate-400 ${
                editing?.id === task.id ? 'border-slate-900 bg-white shadow' : 'border-slate-200 bg-white'
              }`}
              onClick={() => {
                setEditing(task)
                setCreating(false)
              }}
            >
              <div className="flex items-start justify-between gap-2">
                <div>
                  <div className="font-medium">{task.name}</div>
                  <div className="text-xs text-slate-500">
                    every {task.rules.intervalN} {task.rules.intervalType.replace('EVERY_N_', '').toLowerCase()}
                    {task.rules.catchUp && task.catchUpCount > 0 && (
                      <span className="ml-2 rounded bg-amber-100 px-1.5 py-0.5 text-amber-800">
                        backlog ×{task.catchUpCount}
                      </span>
                    )}
                  </div>
                </div>
                {task.archived && (
                  <span className="rounded bg-slate-100 px-2 py-0.5 text-xs text-slate-600">archived</span>
                )}
              </div>
              {task.nextScheduled && (
                <div className="mt-1 text-xs text-slate-500">
                  Next: {format(parseISO(task.nextScheduled), 'yyyy-MM-dd')}
                </div>
              )}
            </li>
          ))}
          {(tasks ?? []).length === 0 && (
            <li className="rounded-lg border border-dashed border-slate-300 p-6 text-center text-sm text-slate-500">
              No tasks yet. Create one to get started.
            </li>
          )}
        </ul>
      </section>

      <section>
        {creating && (
          <TaskForm
            submitting={mutations.create.isPending}
            onCancel={() => setCreating(false)}
            onSubmit={async (values) => {
              await mutations.create.mutateAsync(values)
              setCreating(false)
            }}
          />
        )}
        {editing && !creating && (
          <div className="space-y-3">
            <TaskForm
              initial={editing}
              submitting={mutations.update.isPending}
              onSubmit={async (values) => {
                await mutations.update.mutateAsync({ id: editing.id, ...values })
                setEditing({ ...editing, ...values, archived: values.archived ?? editing.archived })
              }}
            />
            <button
              type="button"
              className="text-sm text-red-600 hover:underline"
              onClick={async () => {
                if (confirm(`Delete "${editing.name}"?`)) {
                  await mutations.remove.mutateAsync(editing.id)
                  setEditing(null)
                }
              }}
            >
              Delete task
            </button>
          </div>
        )}
        {!creating && !editing && (
          <div className="rounded-xl border border-dashed border-slate-300 p-8 text-center text-sm text-slate-500">
            Select a task to edit, or create a new one.
          </div>
        )}
      </section>
    </div>
  )
}
