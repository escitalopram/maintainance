export const taskFieldHelp = {
  name: 'Short label shown in the task list and on the plan calendar. Required for every task.',
  description:
    'Optional free-text note for your own reference. It is not used by the scheduler or planner.',
  intervalType:
    'How the next due dates are generated. Every-N options repeat on a calendar grid from an anchor. External due runs a script that decides whether the task is due on a given day.',
  intervalN:
    'Spacing for every-N intervals: number of days, weeks, months, or years between scheduled slots. Must be at least 1. Fractional values are allowed for day/week/month/year grids.',
  anchorMode:
    'Epoch anchor keeps the series on the calendar grid from the first scheduled date of the current epoch. Last completion anchor shifts the next due date from when you last marked the task done.',
  catchUp:
    'When enabled, missed past obligations increase catch-up count and can create backlog virtual instances with higher timing pain until you clear them.',
  durationMinutes:
    'Estimated minutes for one instance. Used for daily load totals, hard-cap checks, and daily pain when multiple tasks land on the same day.',
  importanceWeight:
    'Scales timing pain for this task. Higher values make early/late placement more costly in the planner. Minimum is 1.',
  graceEarlyDays:
    'Days before the scheduled date with zero timing pain. Completing or planning within this early window is free from timing penalties.',
  graceLateDays:
    'Days after the scheduled date with zero timing pain. Useful when being a little late is still acceptable.',
  sigmaEarly:
    'Controls how quickly timing pain rises when you plan too early (outside the grace window). Larger values mean a gentler ramp; smaller values punish early placement faster.',
  sigmaLate:
    'Controls how quickly timing pain rises when you plan too late (outside the grace window). Smaller values create a steep penalty for delay.',
  backlogP:
    'Exponent for the backlog multiplier when catch-up count is above 1. Values between 0 and 1 grow sublinearly; 0.5 behaves roughly like a square root.',
  painCurvePreview:
    'Timing pain (Regime A) for this task as a function of day offset from the scheduled date. The flat region is the grace window; outside it pain follows a bell curve scaled by importance.',
  dueScriptPath:
    'Filesystem path to an executable script that answers whether the task is due on a given day. The scheduler invokes it when evaluating external-due tasks.',
  dueScriptArgs:
    'Optional command-line arguments passed to the due script. Not editable yet.',
  archived:
    'Archived tasks are kept for history but excluded from new scheduling and planning.',
  tags: 'Labels to group related tasks. Tag editing is planned for a future release.',
  preferredTime:
    'Optional hint for time-of-day placement within a day. The planner currently works at day granularity only.',
  allowedWeekdays:
    'Restrict which weekdays may receive a planned instance. Backend snapping exists; a weekday picker UI is planned.',
  seasonStart: 'First calendar day each year when the task is active. Full seasonal editing is planned.',
  seasonEnd: 'Last calendar day each year when the task is active. Full seasonal editing is planned.',
  endDate: 'Optional date after which the task should be archived automatically. Manual archive is available today.',
  minDaysBetweenScheduled:
    'Minimum spacing between planned instances of the same task, even when the interval would allow closer dates.',
  useBacklogMultiplier:
    'When enabled, backlog virtual instances multiply timing pain based on catch-up count. Toggle editing is planned.',
  nthWeekday:
    'Interval type for patterns like “3rd Tuesday of the month”. Not available yet.',
  epochStart:
    'The anchor date for epoch-based recurrence. Editing the epoch in the UI is planned.',
} as const
