import { QueryClient, QueryClientProvider, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client'
import type { Settings, Task, TaskRules } from '../api/types'

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: { staleTime: 10_000, retry: 1 },
  },
})

export function QueryProvider({ children }: { children: React.ReactNode }) {
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
}

export function useTasks() {
  return useQuery({ queryKey: ['tasks'], queryFn: api.listTasks })
}

export function useTaskMutations() {
  const qc = useQueryClient()
  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ['tasks'] })
    qc.invalidateQueries({ queryKey: ['plan'] })
  }
  return {
    create: useMutation({
      mutationFn: (body: { name: string; description?: string; rules: TaskRules }) =>
        api.createTask(body),
      onSuccess: invalidate,
    }),
    update: useMutation({
      mutationFn: ({ id, ...body }: {
        id: string
        name: string
        description?: string
        rules: TaskRules
        archived?: boolean
      }) => api.updateTask(id, body),
      onSuccess: invalidate,
    }),
    remove: useMutation({
      mutationFn: (id: string) => api.deleteTask(id),
      onSuccess: invalidate,
    }),
  }
}

export function usePlan(horizonStart: string, horizonEnd: string) {
  return useQuery({
    queryKey: ['plan', horizonStart, horizonEnd],
    queryFn: () => api.plan(horizonStart, horizonEnd),
    enabled: Boolean(horizonStart && horizonEnd),
  })
}

export function useSettings() {
  return useQuery({ queryKey: ['settings'], queryFn: api.getSettings })
}

export function useSettingsMutation() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: Settings) => api.updateSettings(body),
    onSuccess: (data) => {
      qc.setQueryData(['settings'], data)
      qc.invalidateQueries({ queryKey: ['plan'] })
    },
  })
}

export function useInstanceActions() {
  const qc = useQueryClient()
  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ['tasks'] })
    qc.invalidateQueries({ queryKey: ['plan'] })
  }
  return {
    complete: useMutation({
      mutationFn: ({
        taskId,
        ...body
      }: {
        taskId: string
        openInstanceId?: string
        scheduledAt: string
        plannedAt?: string
        intervalDeltaPercent?: number
      }) => api.completeInstance(taskId, body),
      onSuccess: invalidate,
    }),
    snooze: useMutation({
      mutationFn: ({ taskId, snoozeUntil }: { taskId: string; snoozeUntil: string }) =>
        api.snoozeInstance(taskId, snoozeUntil),
      onSuccess: invalidate,
    }),
  }
}

export function taskMap(tasks: Task[] | undefined): Map<string, Task> {
  return new Map((tasks ?? []).map((t) => [t.id, t]))
}
