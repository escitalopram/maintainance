import type {
  Completion,
  PlanResult,
  Settings,
  Task,
  TaskRules,
} from './types'

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    headers: { 'Content-Type': 'application/json', ...init?.headers },
    ...init,
  })
  if (!response.ok) {
    const text = await response.text()
    throw new Error(text || `Request failed: ${response.status}`)
  }
  if (response.status === 204) {
    return undefined as T
  }
  return response.json() as Promise<T>
}

export const api = {
  listTasks: () => request<Task[]>('/api/tasks'),

  getTask: (id: string) => request<Task>(`/api/tasks/${id}`),

  createTask: (body: { name: string; description?: string; rules: TaskRules }) =>
    request<Task>('/api/tasks', { method: 'POST', body: JSON.stringify(body) }),

  updateTask: (id: string, body: {
    name: string
    description?: string
    rules: TaskRules
    archived?: boolean
  }) => request<Task>(`/api/tasks/${id}`, { method: 'PUT', body: JSON.stringify(body) }),

  deleteTask: (id: string) =>
    request<void>(`/api/tasks/${id}`, { method: 'DELETE' }),

  plan: (horizonStart: string, horizonEnd: string) =>
    request<PlanResult>('/api/plan', {
      method: 'POST',
      body: JSON.stringify({ horizonStart, horizonEnd }),
    }),

  getSettings: () => request<Settings>('/api/settings'),

  updateSettings: (body: Settings) =>
    request<Settings>('/api/settings', { method: 'PUT', body: JSON.stringify(body) }),

  completeInstance: (
    taskId: string,
    body: {
      openInstanceId?: string
      scheduledAt: string
      plannedAt?: string
      intervalDeltaPercent?: number
    },
  ) =>
    request<void>(`/api/instances/${taskId}/complete`, {
      method: 'POST',
      body: JSON.stringify(body),
    }),

  snoozeInstance: (taskId: string, snoozeUntil: string) =>
    request<void>(`/api/instances/${taskId}/snooze`, {
      method: 'POST',
      body: JSON.stringify({ snoozeUntil }),
    }),

  listCompletions: (taskId: string) =>
    request<Completion[]>(`/api/tasks/${taskId}/completions`),
}
