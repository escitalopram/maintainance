export type IntervalType =
  | 'EVERY_N_DAYS'
  | 'EVERY_N_WEEKS'
  | 'EVERY_N_MONTHS'
  | 'EVERY_N_YEARS'
  | 'EXTERNAL_DUE'

export type AnchorMode = 'EPOCH' | 'LAST_COMPLETION'

export interface TaskRules {
  intervalType: IntervalType
  intervalN: number
  anchorMode: AnchorMode
  catchUp: boolean
  useBacklogMultiplier: boolean
  allowedWeekdays: number[]
  seasonStart?: string | null
  seasonEnd?: string | null
  endDate?: string | null
  minDaysBetweenScheduled?: number | null
  durationMinutes: number
  importanceWeight: number
  graceEarlyDays: number
  graceLateDays: number
  sigmaEarly: number
  sigmaLate: number
  backlogP: number
  dueScriptPath?: string | null
  dueScriptArgs?: string | null
}

export interface OpenInstance {
  id: string
  taskId: string
  scheduledAt: string
  snoozeUntil?: string | null
}

export interface Task {
  id: string
  name: string
  description?: string | null
  archived: boolean
  rules: TaskRules
  epochStart?: string | null
  nextScheduled?: string | null
  lastMissedScheduledAt?: string | null
  catchUpCount: number
  openInstance?: OpenInstance | null
}

export interface PlanDay {
  date: string
  loadMinutes: number
  overHardCap: boolean
  dailyPain: number
}

export interface PlanItem {
  instanceKey: string
  taskId: string
  scheduledAt: string
  plannedAt?: string | null
  placement?: string | null
  timingPain: number
  durationMinutes: number
  withinDayOrder?: number | null
  virtual?: boolean
  backlog?: boolean
  overdue?: boolean
}

export interface PlanResult {
  horizonStart: string
  horizonEnd: string
  pTotal: number
  pTiming: number
  pTimingUnassigned: number
  pDaily: number
  pStar: number
  days: PlanDay[]
  items: PlanItem[]
  warnings: string[]
}

export interface Settings {
  softBudgetMinutes: number
  hardCapMinutes: number
  painThreshold: number
  painPerMinuteOverThreshold: number
  beta: number
  defaultBacklogP: number
  planningExtendFactor: number
}

export interface Completion {
  id: string
  taskId: string
  scheduledAt: string
  plannedAt?: string | null
  completedAt: string
}

export const defaultTaskRules = (): TaskRules => ({
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
})
