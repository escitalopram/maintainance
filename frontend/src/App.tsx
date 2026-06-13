import * as Tabs from '@radix-ui/react-tabs'
import { useState } from 'react'
import { PlanView } from './components/PlanView'
import { SettingsView } from './components/SettingsView'
import { TasksView } from './components/TasksView'

export default function App() {
  const [tab, setTab] = useState('plan')

  return (
    <div className="min-h-screen bg-slate-50 text-slate-900">
      <header className="border-b border-slate-200 bg-white px-6 py-4 shadow-sm">
        <h1 className="text-xl font-semibold tracking-tight">Maintenance Planner</h1>
        <p className="text-sm text-slate-500">Local schedule & plan for recurring tasks</p>
      </header>

      <Tabs.Root value={tab} onValueChange={setTab} className="mx-auto max-w-7xl px-4 py-6">
        <Tabs.List className="mb-6 flex gap-2 rounded-lg bg-slate-200/60 p-1">
          {[
            ['plan', 'Plan'],
            ['tasks', 'Tasks'],
            ['settings', 'Settings'],
          ].map(([value, label]) => (
            <Tabs.Trigger
              key={value}
              value={value}
              className="rounded-md px-4 py-2 text-sm font-medium text-slate-600 data-[state=active]:bg-white data-[state=active]:text-slate-900 data-[state=active]:shadow"
            >
              {label}
            </Tabs.Trigger>
          ))}
        </Tabs.List>

        <Tabs.Content value="plan">
          <PlanView />
        </Tabs.Content>
        <Tabs.Content value="tasks">
          <TasksView />
        </Tabs.Content>
        <Tabs.Content value="settings">
          <SettingsView />
        </Tabs.Content>
      </Tabs.Root>
    </div>
  )
}
